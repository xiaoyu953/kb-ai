package com.itheima.ai.service;

import com.itheima.ai.entity.dto.RagResponse;
import com.itheima.ai.entity.vo.MessageVO;
import com.itheima.ai.entity.vo.ChatRequest;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 聊天会话服务接口
 */
public interface ChatSessionService {

    /**
     * 主聊天接口：根据是否上传图片，自动选择文本或图文模式
     *
     * @param request 聊天请求
     * @return 流式响应
     */
    Flux<String> chat(ChatRequest request);

    /**
     * RAG 增强问答接口
     *
     * @param prompt 用户输入的问题
     * @param chatId 会话ID
     * @return RAG响应
     */
    RagResponse ragChat(String prompt, String chatId);

    /**
     * 获取指定业务类型下的所有会话ID列表
     *
     * @param type 业务类型
     * @return 会话ID列表
     */
    List<String> getChatIds(String type);

    /**
     * 获取指定会话的完整消息历史记录
     *
     * @param type   业务类型
     * @param chatId 会话ID
     * @return 消息历史列表
     */
    List<MessageVO> getChatHistory(String type, String chatId);

    /**
     * 流式聊天接口：根据是否上传图片，自动选择文本或图文模式
     *
     * @param request 聊天请求
     * @return 流式响应
     */
    Flux<String> streamChat(ChatRequest request);
}