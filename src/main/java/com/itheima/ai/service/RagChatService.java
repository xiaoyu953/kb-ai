// src/main/java/com/itheima/ai/service/RagChatService.java

package com.itheima.ai.service;

import com.itheima.ai.entity.dto.RagResponse;
import com.itheima.ai.utils.CacheKeyUtils;
import com.itheima.ai.utils.DocumentUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RAG 聊天服务类
 * 负责：
 * - 从向量库检索相关文档
 * - 构造带约束的 Prompt
 * - 调用大模型生成回答
 * - 将 [1] 等编号替换为真实引用（如 [来源: hr.pdf, p.5]）
 * - 缓存结果（Redis）
 */
@Service
@RequiredArgsConstructor // Lombok 自动生成构造器注入所有 final 字段
public class RagChatService {

    // 向量数据库操作接口（如 Qdrant）
    private final VectorStore vectorStore;

    // Spring AI 聊天客户端，用于调用大模型
    private final ChatClient chatClient;

    // Redis 模板，用于缓存问答结果
    private final RedisTemplate<String, Object> redisTemplate;

    // 从配置文件读取缓存过期时间（单位：小时），默认 1 小时
    @org.springframework.beans.factory.annotation.Value("${rag.cache.expire-hours:1}")
    private int cacheExpireHours;

    /**
     * 核心方法：根据用户问题生成带引用的答案
     *
     * @param prompt 用户输入的问题
     * @param chatId 会话 ID（用于缓存隔离）
     * @return 结构化响应（含答案 + 引用列表）
     */
    public RagResponse answer(String prompt, String chatId) {
        // 1. 生成唯一缓存键（基于 chatId + prompt）
        String cacheKey = CacheKeyUtils.buildRagCacheKey(chatId, prompt);

        // 2. 尝试从 Redis 读取缓存
        RagResponse cached = (RagResponse) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            // 缓存命中，直接返回（生产环境建议用日志代替 System.out）
            System.out.println("✅ 缓存命中: " + cacheKey);
            return cached;
        }

        // 3. 从向量库检索 Top 3 最相似文档
        SearchRequest request = SearchRequest.builder()
                .query(prompt)      // 用户问题作为查询
                .topK(3)            // 返回最相关的 3 个片段
                .build();
        List<Document> similarDocs = vectorStore.similaritySearch(request);

        // 4. 若无检索结果，返回兜底语句并缓存 10 分钟防穿透
        if (similarDocs == null || similarDocs.isEmpty()) {
            RagResponse noAnswer = new RagResponse("根据现有文档，我无法回答该问题。", Collections.emptyList());
            redisTemplate.opsForValue().set(cacheKey, noAnswer, Duration.ofMinutes(10));
            return noAnswer;
        }

        // 5. 构建上下文字符串（带编号）并收集真实引用信息
        List<RagResponse.Citation> validCitations = new ArrayList<>();
        StringBuilder contextBuilder = new StringBuilder();

        for (int i = 0; i < similarDocs.size(); i++) {
            Document doc = similarDocs.get(i);
            String content = doc.getText(); // 获取文本内容（Spring AI 中是 getText()）
            if (content == null || content.trim().isEmpty()) continue; // 跳过空内容

            // 从 metadata 中安全提取文件名（默认 unknown.pdf）
            String source = (String) doc.getMetadata().getOrDefault("source", "unknown.pdf");

            // 安全提取页码（兼容 Integer / Long / String 类型）
            Object pageObj = doc.getMetadata().get("page");
            int page = DocumentUtils.extractPageNumber(pageObj); // 使用工具类方法处理类型转换

            // 构造带编号的上下文片段，例如：[1] 这是内容...
            contextBuilder.append(String.format("[%d] %s\n", i + 1, content.trim()));

            // 记录该片段的真实引用（文件名 + 页码）
            validCitations.add(new RagResponse.Citation(source, page));
        }

        // 6. 如果所有片段都为空，返回兜底答案
        if (contextBuilder.length() == 0) {
            RagResponse noAnswer = new RagResponse("根据现有文档，我无法回答该问题。", Collections.emptyList());
            redisTemplate.opsForValue().set(cacheKey, noAnswer, Duration.ofMinutes(10));
            return noAnswer;
        }

        // 7. 构造强约束 Prompt（防止模型幻觉）
        // 注意：% 需转义为 %%，避免 formatted 报错
        String escapedContext = contextBuilder.toString().trim().replace("%", "%%");
        String escapedPrompt = prompt.replace("%", "%%");

        String finalPrompt = """
                你是一个企业知识助手，请严格根据以下【上下文】回答问题。
                            
                要求：
                1. 如果上下文包含答案，请直接回答，并在句末标注如 [1] 等编号。
                2. 如果上下文没有相关信息，请回答：“根据现有文档，我无法回答该问题。”
                3. 不要编造、不要推测、不要添加上下文以外的内容。
                4. 回答必须简洁，不要复述上下文。
                            
                【上下文】
                %s
                            
                【问题】
                %s
                """.formatted(escapedContext, escapedPrompt);

        // 8. 调用大模型获取原始回答
        String rawAnswer = chatClient.prompt().user(finalPrompt).call().content();

        // 9. 将模型输出中的 [1]、[2] 替换为真实引用格式 [来源: xxx.pdf, p.5]
        Pattern pattern = Pattern.compile("\\[(\\d+)\\]"); // 匹配 [数字]
        Matcher matcher = pattern.matcher(rawAnswer);
        StringBuffer cleanedAnswer = new StringBuffer();
        Set<RagResponse.Citation> actualCitations = new LinkedHashSet<>(); // 用 Set 去重

        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1)) - 1; // 转为 0-based 索引
            if (index >= 0 && index < validCitations.size()) {
                // 找到对应的真实引用
                RagResponse.Citation citation = validCitations.get(index);
                String fullCitation = String.format("[来源: %s, p.%d]", citation.getSource(), citation.getPage());
                matcher.appendReplacement(cleanedAnswer, fullCitation);
                actualCitations.add(citation); // 收集实际用到的引用
            } else {
                // 索引越界，保留原样（如 [999]）
                matcher.appendReplacement(cleanedAnswer, matcher.group(0));
            }
        }
        matcher.appendTail(cleanedAnswer); // 拼接剩余部分

        String finalAnswer = cleanedAnswer.toString().trim();

        // 10. 二次兜底：若模型仍返回模糊答案，强制拦截
        if (finalAnswer.contains("无法回答") ||
                finalAnswer.contains("不知道") ||
                finalAnswer.contains("未提及") ||
                finalAnswer.isEmpty()) {
            RagResponse noAnswer = new RagResponse("根据现有文档，我无法回答该问题。", Collections.emptyList());
            redisTemplate.opsForValue().set(cacheKey, noAnswer, Duration.ofMinutes(10));
            return noAnswer;
        }

        // 11. 构造最终响应对象
        RagResponse finalResponse = new RagResponse(finalAnswer, new ArrayList<>(actualCitations));

        // 12. 写入 Redis 缓存（按配置的小时数过期）
        redisTemplate.opsForValue().set(cacheKey, finalResponse, Duration.ofHours(cacheExpireHours));
        System.out.println("💾 缓存写入: " + cacheKey);

        return finalResponse;
    }


}