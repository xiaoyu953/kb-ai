package com.itheima.ai.service;

import com.itheima.ai.entity.dto.RagResponse;
import com.itheima.ai.utils.CacheKeyUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于检索增强生成（RAG）的回答服务。
 * <p>
 * - 使用 Redis 缓存问答结果，避免重复查询
 * - 实际项目中应对接 Qdrant 向量数据库
 */
@Service
public class RagService {

    @Autowired
    private VectorStore vectorStore;
    @Autowired
    private ChatClient chatClient;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Value("${rag.cache.expire-hours:1}")
    private int cacheExpireHours;

    public RagResponse answer(String prompt, String chatId) {

        // 01. 生成缓存键
        String cacheKey = CacheKeyUtils.buildRagCacheKey(chatId, prompt);
        // 02. 尝试从缓存读取
        RagResponse cached = (RagResponse) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            System.out.println("✅ 缓存命中: " + cacheKey);
            return cached;
        }

        // 1. 检索 Top 3 文档
        SearchRequest request = SearchRequest.builder()
                .query(prompt)
                .topK(3)
                .build();
        List<Document> similarDocs = vectorStore.similaritySearch(request);

        // 2. 若无结果，直接返回兜底语句
        if (similarDocs == null || similarDocs.isEmpty()) {
            return new RagResponse("根据现有文档，我无法回答该问题。", Collections.emptyList());
        }

        // 3. 构建带编号的上下文，并记录真实引用信息
        List<RagResponse.Citation> validCitations = new ArrayList<>();
        StringBuilder contextBuilder = new StringBuilder();

        for (int i = 0; i < similarDocs.size(); i++) {
            Document doc = similarDocs.get(i);
            String content = doc.getText(); // 注意：Spring AI 中是 getText()，不是 getContent()
            if (content == null || content.trim().isEmpty()) continue;

            String source = (String) doc.getMetadata().getOrDefault("source", "unknown.pdf");

            // 安全提取 page 字段（兼容 Long / Integer / String）
            Object pageObj = doc.getMetadata().get("page");
            int page = 1;
            if (pageObj != null) {
                if (pageObj instanceof Number) {
                    page = ((Number) pageObj).intValue();
                } else if (pageObj instanceof String) {
                    try {
                        page = Integer.parseInt((String) pageObj);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            // ✅ 正确写法：
            contextBuilder.append(String.format("[%d] %s\n", i + 1, content.trim()));
            validCitations.add(new RagResponse.Citation(source, page));
        }

        if (contextBuilder.length() == 0) {
            RagResponse noAnswer = new RagResponse("根据现有文档，我无法回答该问题。", Collections.emptyList());
            // 缓存空结果 10 分钟，防穿透
            redisTemplate.opsForValue().set(cacheKey, noAnswer, Duration.ofMinutes(10));
            return noAnswer;
        }

        // 4. 构造强约束 Prompt —— ⚠️ 必须转义 % 字符！
        String escapedContext = contextBuilder.toString().trim().replace("%", "%%");
        String escapedPrompt = prompt.replace("%", "%%");

        String finalPrompt = """
                你是一个企业知识助手，请严格根据以下【上下文】回答问题。
                            
                要求：
                1. 如果上下文包含答案，请直接回答，并在句末标注如 [1]、[2] 等编号。
                2. 如果上下文没有相关信息，请回答：“根据现有文档，我无法回答该问题。”
                3. 不要编造、不要推测、不要添加上下文以外的内容。
                4. 回答必须简洁，不要复述上下文。
                            
                【上下文】
                %s
                            
                【问题】
                %s
                """.formatted(escapedContext, escapedPrompt);

        // 5. 调用大模型
        String rawAnswer = chatClient.prompt().user(finalPrompt).call().content();

        // 6. 将 [1]、[2] 替换为真实引用，并收集 citations
        Pattern pattern = Pattern.compile("\\[(\\d+)\\]");
        Matcher matcher = pattern.matcher(rawAnswer);
        StringBuffer cleanedAnswer = new StringBuffer();
        Set<RagResponse.Citation> actualCitations = new LinkedHashSet<>();

        while (matcher.find()) {
            int idx = Integer.parseInt(matcher.group(1)) - 1;
            if (idx >= 0 && idx < validCitations.size()) {
                RagResponse.Citation citation = validCitations.get(idx);
                // ✅ 这里也要注意：如果 citation.getPage() 是 null 会 NPE，但我们已确保 page 是 int
                String fullCitation = String.format("[来源: %s, p.%d]", citation.getSource(), citation.getPage());
                matcher.appendReplacement(cleanedAnswer, fullCitation);
                actualCitations.add(citation);
            } else {
                matcher.appendReplacement(cleanedAnswer, matcher.group(0));
            }
        }
        matcher.appendTail(cleanedAnswer);

        String finalAnswer = cleanedAnswer.toString().trim();

        // 7. 最终兜底
        if (finalAnswer.contains("无法回答") ||
                finalAnswer.contains("不知道") ||
                finalAnswer.contains("未提及") ||
                finalAnswer.isEmpty()) {
            RagResponse noAnswer = new RagResponse("根据现有文档，我无法回答该问题。", Collections.emptyList());
            redisTemplate.opsForValue().set(cacheKey, noAnswer, Duration.ofMinutes(10));
            return noAnswer;
        }

        // 8. 返回结构化响应
        RagResponse finalResponse = new RagResponse(finalAnswer, new ArrayList<>(actualCitations));
        //写入缓存
        redisTemplate.opsForValue().set(cacheKey, finalResponse, Duration.ofHours(cacheExpireHours));
        System.out.println("💾 缓存写入: " + cacheKey);
        return finalResponse;
    }
}
