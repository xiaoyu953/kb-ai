package com.itheima.ai.controller;

import com.itheima.ai.entity.vo.MessageVO;
import com.itheima.ai.repository.ChatHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 对话历史控制器：提供会话ID列表和消息历史查询接口
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/history")
public class ChatHistoryController {

    // 会话ID存储仓库：记录各业务类型下存在的会话（如 chat, pdf 等）
    private final ChatHistoryRepository chatHistoryRepository;

    // 对话记忆组件：存储每个 chatId 对应的实际消息内容（用户 + AI）
    private final ChatMemory chatMemory;

    /**
     * 获取指定业务类型下的所有会话ID列表
     *
     * @param type 业务类型（如 "chat" 表示普通对话，"pdf" 表示文档问答）
     * @return 该类型下所有会话ID的列表（按字典序排序，无则返回空列表）
     */
    @GetMapping("/{type}")
    public List<String> getChatIds(@PathVariable("type") String type) {
        return chatHistoryRepository.getChatIds(type); // 从 Redis/ZSet 等中读取会话ID集合
    }

    /**
     * 获取指定会话的完整消息历史记录
     *
     * @param type   业务类型（路径参数，用于路由，实际未用于查询逻辑）
     * @param chatId 会话唯一标识
     * @return 消息历史列表（按时间顺序：最早 → 最新），每条消息转为前端友好的 VO 格式
     */
    @GetMapping("/{type}/{chatId}")
    public List<MessageVO> getChatHistory(
            @PathVariable("type") String type,
            @PathVariable("chatId") String chatId) {

        // 从 ChatMemory 中读取该 chatId 的全部消息（最多 Integer.MAX_VALUE 条）
        List<Message> messages = chatMemory.get(chatId, Integer.MAX_VALUE);

        // 若无消息记录，返回空列表（避免 NPE）
        if (messages == null) {
            return List.of();
        }

        // 将 Spring AI 的 Message 转换为前端使用的 MessageVO（含角色、内容等字段）
        return messages.stream()
                .map(MessageVO::new)   // 使用 MessageVO 的构造器转换
                .toList();             // 收集为不可变列表返回
    }
}