// src/main/java/com/itheima/ai/controller/RagController.java

package com.itheima.ai.controller;

import com.itheima.ai.entity.dto.RagResponse;
import com.itheima.ai.entity.vo.ChatRequest;
import com.itheima.ai.repository.ChatHistoryRepository;
import com.itheima.ai.service.DocumentIngestionService;
import com.itheima.ai.service.RagChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.Media;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import reactor.core.publisher.Flux;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * RAG 控制器（精简版）
 * 仅负责：
 * - HTTP 参数接收
 * - 调用 Service
 * - 返回 ResponseEntity
 */
@RestController
@RequestMapping("/rag")
@RequiredArgsConstructor
public class RagController {

    // 注入文档摄入服务（处理 PDF 上传）
    private final DocumentIngestionService ingestionService;

    // 注入 RAG 聊天服务（处理问答）
    private final RagChatService ragChatService;

    // 注入向量库（仅用于 debug 接口）
    private final VectorStore vectorStore;

    private final ChatClient chatClient;

    // 会话历史记录仓库（用于前端展示会话列表）
    private final ChatHistoryRepository chatHistoryRepository;


    /**
     * 上传 PDF 并存入向量库
     */
    @PostMapping("/upload")
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file) {
        try {
            ingestionService.ingestPdf(file);
            return ResponseEntity.ok("✅ 已成功解析并存储 " + file.getOriginalFilename());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (RuntimeException e) {
            // 捕获所有由 ingestPdf 抛出的运行时异常（包括文档处理失败）
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /**
     * RAG 聊天接口（带引用溯源）
     */
    @GetMapping("/chat")
    public RagResponse ragChat(@RequestParam String prompt,
                               @RequestParam(defaultValue = "default") String chatId) {
        // 直接委托给 Service 处理
        return ragChatService.answer(prompt, chatId);
    }

    // ========== 以下为调试接口（可选保留） ==========

    @GetMapping("/search")
    public List<Document> search(@RequestParam String query) {
        return vectorStore.similaritySearch(query);
    }

    @GetMapping("/debug-search")
    public List<Map<String, Object>> debugSearch(@RequestParam String query) {
        return vectorStore.similaritySearch(query).stream()
                .map(doc -> Map.of(
                        "content", doc.getText(),
                        "metadata", doc.getMetadata()
                )).toList();
    }

    /**
     * 主聊天接口：根据是否上传图片，自动选择文本或图文模式
     * 使用 Flux<String> 实现服务端推送（SSE 流式响应）
     */
    @PostMapping(value = "/stream-chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody ChatRequest request) {
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

    /**
     * 多模态对话：处理带图片的请求（支持多图）
     * 注意：Media 类来自 org.springframework.ai.model（Spring AI 0.7.x）
     */
    private Flux<String> multiModalChat(String prompt, String chatId, List<MultipartFile> files) {
        // 1. 过滤掉空文件，并将有效文件转换为 Spring AI 的 Media 对象
        List<Media> medias = files.stream()
                .filter(file -> !file.isEmpty()) //跳过空文件
                .map(file -> {
                    try {
                        // 安全获取文件 MIME 类型（如 image/png）
                        String contentType = Objects.requireNonNull(
                                file.getContentType(),
                                "文件类型不能为空");
                        // 构造 MimeType 对象（Spring 内置类型解析）
                        MimeType mimeType = MimeType.valueOf(contentType);
                        // 将 MultipartFile 转换为 Resource（供模型读取）
                        Resource resource = file.getResource();
                        // 创建 Media 对象（Spring AI 0.7.x 的多模态载体）
                        return new Media(mimeType, resource);
                    } catch (Exception e) {
                        // 若单个文件转换失败，记录日志并返回 null（后续过滤）
                        return null;
                    }
                })
                .filter(Objects::nonNull) //移除转换失败的项
                .toList();  //收集为不可变列表
        // 2. 如果所有文件都无效，则退化为纯文本对话（避免模型报错）
        if (medias.isEmpty()) {
            return textChat(prompt, chatId);
        }
        // 3. 构造用户消息：包含文本 + 所有有效图片
        return chatClient.prompt()
                .user(userSpec ->
                        userSpec.text(prompt) // 设置用户提问文本
                                .media(medias.toArray(Media[]::new)) // 附加所有图片媒体
                )
                .advisors(advisorSpec ->
                        advisorSpec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)) // 绑定会话ID，启用上下文记忆
                .stream() // 启用流式生成（逐 token 返回）
                .content(); // 提取生成的文本内容流（Flux<String>）
    }

    /**
     * 纯文本对话：仅处理文字输入，启用会话记忆
     */
    private Flux<String> textChat(String prompt, String chatId) {
        // 1. 构造纯文本用户消息
        return chatClient.prompt()
                .user(prompt) //直接传入用户输入文本
                .advisors(advisorSpec ->
                        advisorSpec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId) // 关联会话ID，实现多轮对话记忆
                )
                .stream() // 开启流式响应模式
                .content(); // 返回模型生成的文本流（每块为一个 token 或片段）
    }
}