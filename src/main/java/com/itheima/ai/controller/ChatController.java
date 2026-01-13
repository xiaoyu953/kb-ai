package com.itheima.ai.controller;

import com.itheima.ai.entity.dto.RagResponse;
import com.itheima.ai.entity.vo.MessageVO;
import com.itheima.ai.entity.vo.ChatRequest;
import com.itheima.ai.service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI对话控制器：支持普通聊天、多模态（图文）聊天和RAG增强问答
 */
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    // 聊天会话服务
    private final ChatSessionService chatSessionService;

    /**
     * 主聊天接口：根据是否上传图片，自动选择文本或图文模式
     */
    @PostMapping(value = "/chat", produces = "text/html;charset=utf-8")
    public Flux<String> chat(@RequestBody ChatRequest request) {
        return chatSessionService.chat(request);
    }

    /**
     * RAG 增强问答接口：结合向量检索 + 大模型 + Redis 缓存
     */
    @GetMapping("/rag-chat")
    public RagResponse ragChat(@RequestParam String prompt,
                               @RequestParam(defaultValue = "default") String chatId) {
        return chatSessionService.ragChat(prompt, chatId);
    }

    /**
     * 获取指定业务类型下的所有会话ID列表
     *
     * @param type 业务类型（如 "chat" 表示普通对话，"pdf" 表示文档问答）
     * @return 该类型下所有会话ID的列表（按字典序排序，无则返回空列表）
     */
    @GetMapping("/history/{type}")
    public List<String> getChatIds(@PathVariable("type") String type) {
        return chatSessionService.getChatIds(type);
    }

    /**
     * 获取指定会话的完整消息历史记录
     *
     * @param type   业务类型
     * @param chatId 会话唯一标识
     * @return 消息历史列表（按时间顺序：最早 → 最新）
     */
    @GetMapping("/history/{type}/{chatId}")
    public List<MessageVO> getChatHistory(
            @PathVariable("type") String type,
            @PathVariable("chatId") String chatId) {
        return chatSessionService.getChatHistory(type, chatId);
    }
}