package com.itheima.ai.service;

import com.itheima.ai.entity.dto.RagResponse;
import com.itheima.ai.entity.vo.MessageVO;
import com.itheima.ai.entity.vo.ChatRequest;
import com.itheima.ai.repository.ChatHistoryRepository;
import com.itheima.ai.utils.CacheKeyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.Media;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.MimeType;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;

/**
 * 聊天会话服务实现类
 */
@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl implements ChatSessionService {

    // Spring AI 聊天客户端，用于调用大模型
    private final ChatClient chatClient;

    // 向量数据库，用于相似性检索
    private final VectorStore vectorStore;

    // 会话历史记录仓库（用于前端展示会话列表）
    private final ChatHistoryRepository chatHistoryRepository;

    // 对话记忆组件：存储每个 chatId 对应的实际消息内容（用户 + AI）
    private final ChatMemory chatMemory;

    // Redis 模板，用于缓存 RAG 结果
    private final RedisTemplate<String, Object> redisTemplate;

    // RAG 缓存过期时间（小时），默认 1 小时
    @Value("${rag.cache.expire-hours:1}")
    private int cacheExpireHours;

    @Override
    public Flux<String> chat(ChatRequest request) {
        String prompt = request.getPrompt();              // 获取用户输入文本
        String chatId = request.getChatId();              // 获取会话 ID
        List<MultipartFile> files = request.getFiles();   // 获取上传的文件列表

        chatHistoryRepository.save("chat", chatId);       // 记录会话 ID 到历史库

        if (files == null || files.isEmpty()) {
            return textChat(prompt, chatId);              // 无图 → 纯文本对话
        } else {
            return multiModalChat(prompt, chatId, files); // 有图 → 多模态对话
        }
    }

    /**
     * 多模态对话：处理带图片的请求
     */
    private Flux<String> multiModalChat(String prompt, String chatId, List<MultipartFile> files) {
        // 将 MultipartFile 转换为 Spring AI 的 Media 对象
        List<Media> medias = files.stream()
                .map(file -> new Media(
                        MimeType.valueOf(Objects.requireNonNull(file.getContentType())), // 设置 MIME 类型
                        file.getResource()                                              // 转为 Resource
                ))
                .toList();

        // 构造带图片和文本的提示，并启用会话记忆
        return chatClient.prompt()
                .user(p -> p.text(prompt).media(medias.toArray(Media[]::new))) // 用户消息含图文
                .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)) // 绑定会话 ID
                .stream()                                                       // 流式输出
                .content();                                                     // 返回文本内容流
    }

    /**
     * 纯文本对话：仅处理文字输入
     */
    private Flux<String> textChat(String prompt, String chatId) {
        // 构造纯文本提示，并绑定会话 ID 实现上下文记忆
        return chatClient.prompt()
                .user(prompt)                                                   // 用户输入文本
                .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)) // 关联会话
                .stream()                                                       // 流式响应
                .content();                                                     // 返回生成内容
    }

    @Override
    public RagResponse ragChat(String prompt, String chatId) {
        // 1. 生成唯一缓存键（基于 chatId + prompt）
        String cacheKey = CacheKeyUtils.buildRagCacheKey(chatId, prompt);

        // 2. 尝试从 Redis 读取缓存结果
        RagResponse cached = (RagResponse) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            System.out.println("✅ 缓存命中: " + cacheKey);
            return cached;
        }

        // 3. 向量库检索 Top 3 相似文档
        SearchRequest request = SearchRequest.builder()
                .query(prompt)
                .topK(3)
                .build();
        List<Document> similarDocs = vectorStore.similaritySearch(request);

        // 4. 若无相关文档，返回兜底回答
        if (similarDocs == null || similarDocs.isEmpty()) {
            RagResponse noAnswer = new RagResponse("根据现有文档，我无法回答该问题。", Collections.emptyList());
            redisTemplate.opsForValue().set(cacheKey, noAnswer, Duration.ofMinutes(10)); // 防穿透缓存
            return noAnswer;
        }

        // 5. 构建带编号的上下文，并提取真实引用信息
        List<RagResponse.Citation> validCitations = new ArrayList<>();
        StringBuilder contextBuilder = new StringBuilder();

        for (int i = 0; i < similarDocs.size(); i++) {
            Document doc = similarDocs.get(i);
            String content = doc.getText(); // 获取文档文本
            if (content == null || content.trim().isEmpty()) continue;

            // 提取元数据：文件名
            String source = (String) doc.getMetadata().getOrDefault("source", "unknown.pdf");

            // 安全提取页码（兼容多种类型）
            Object pageObj = doc.getMetadata().get("page");
            int page = 1;
            if (pageObj instanceof Number) {
                page = ((Number) pageObj).intValue();
            } else if (pageObj instanceof String) {
                try {
                    page = Integer.parseInt((String) pageObj);
                } catch (NumberFormatException ignored) {
                }
            }

            // 添加到上下文（格式：[1] 内容...）
            contextBuilder.append(String.format("[%d] %s\n", i + 1, content.trim()));
            validCitations.add(new RagResponse.Citation(source, page));
        }

        // 6. 若所有文档为空，返回兜底
        if (contextBuilder.length() == 0) {
            RagResponse noAnswer = new RagResponse("根据现有文档，我无法回答该问题。", Collections.emptyList());
            redisTemplate.opsForValue().set(cacheKey, noAnswer, Duration.ofMinutes(10));
            return noAnswer;
        }

        // 7. 构造强约束 Prompt（防止幻觉）
        String escapedContext = contextBuilder.toString().trim().replace("%", "%%"); // 转义 % 防止格式化错误
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

        // 8. 调用大模型获取原始回答
        String rawAnswer = chatClient.prompt().user(finalPrompt).call().content();

        // 9. 将 [1]、[2] 替换为真实引用（如 [来源: xxx.pdf, p.5]）
        Pattern pattern = Pattern.compile("\\[(\\d+)\\]");
        Matcher matcher = pattern.matcher(rawAnswer);
        StringBuffer cleanedAnswer = new StringBuffer();
        Set<RagResponse.Citation> actualCitations = new LinkedHashSet<>();

        while (matcher.find()) {
            int idx = Integer.parseInt(matcher.group(1)) - 1; // 转为 0-based 索引
            if (idx >= 0 && idx < validCitations.size()) {
                RagResponse.Citation citation = validCitations.get(idx);
                String fullCitation = String.format("[来源: %s, p.%d]", citation.getSource(), citation.getPage());
                matcher.appendReplacement(cleanedAnswer, fullCitation);
                actualCitations.add(citation);
            } else {
                matcher.appendReplacement(cleanedAnswer, matcher.group(0)); // 无效编号原样保留
            }
        }
        matcher.appendTail(cleanedAnswer);
        String finalAnswer = cleanedAnswer.toString().trim();

        // 10. 最终兜底：若回答含“无法回答”等关键词，视为无答案
        if (finalAnswer.contains("无法回答") ||
                finalAnswer.contains("不知道") ||
                finalAnswer.contains("未提及") ||
                finalAnswer.isEmpty()) {
            RagResponse noAnswer = new RagResponse("根据现有文档，我无法回答该问题。", Collections.emptyList());
            redisTemplate.opsForValue().set(cacheKey, noAnswer, Duration.ofMinutes(10));
            return noAnswer;
        }

        // 11. 构造最终响应并写入缓存
        RagResponse finalResponse = new RagResponse(finalAnswer, new ArrayList<>(actualCitations));
        redisTemplate.opsForValue().set(cacheKey, finalResponse, Duration.ofHours(cacheExpireHours));
        System.out.println("💾 缓存写入: " + cacheKey);
        return finalResponse;
    }

    @Override
    public List<String> getChatIds(String type) {
        return chatHistoryRepository.getChatIds(type);
    }

    @Override
    public List<MessageVO> getChatHistory(String type, String chatId) {
        // 从 ChatMemory 中读取该 chatId 的全部消息（最多 Integer.MAX_VALUE 条）
        List<Message> messages = chatMemory.get(chatId, Integer.MAX_VALUE);

        // 若无消息记录，返回空列表（避免 NPE）
        if (messages == null) {
            return List.of();
        }

        // 将 Spring AI 的 Message 转换为前端友好的 VO 格式
        return messages.stream()
                .map(MessageVO::new)   // 使用 MessageVO 的构造器转换
                .toList();             // 收集为不可变列表返回
    }

    /**
     * 流式聊天接口：根据是否上传图片，自动选择文本或图文模式
     */
    public Flux<String> streamChat(ChatRequest request) {
        // 1. 获取用户输入的提示文本（问题内容）
        String prompt = request.getPrompt();
        // 2. 获取会话唯一标识，用于上下文记忆和历史记录
        String chatId = request.getChatId();
        // 3. 获取用户上传的文件列表（可能为 null 或空）
        List<MultipartFile> files = request.getFiles();
        // 4. 将当前会话 ID 记录到数据库（用于前端展示会话列表）
        chatHistoryRepository.save("chat", chatId);
        // 5. 判断是否有上传文件：无文件走纯文本，有文件走多模态
        if (files == null || files.isEmpty()) {
            return textChat(prompt, chatId); // 调用纯文本流式对话
        } else {
            return multiModalChat(prompt, chatId, files); // 调用多模态流式对话
        }
    }
}