// src/main/java/com/itheima/ai/controller/RagController.java

package com.itheima.ai.controller;

import com.itheima.ai.entity.dto.RagResponse;
import com.itheima.ai.repository.ChatHistoryRepository;
import com.itheima.ai.service.DocumentIngestionService;
import com.itheima.ai.service.RagChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

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
}