package com.itheima.ai.entity.vo;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.messages.Message;

/**
 * 消息视图对象（Value Object）
 * 用于将AI消息对象转换为前端可直接使用的格式
 * 
 * 该类的主要作用是解耦AI内部消息类型与前端展示需求，
 * 提供统一的消息展示格式，隐藏AI消息的复杂内部结构
 * 
 * @author heima-ai
 * @version 1.0.0
 */
@NoArgsConstructor
@Data
public class MessageVO {

    /**
     * 消息角色
     * 用于标识消息的发送者身份，支持以下值：
     * - user: 用户发送的消息
     * - assistant: AI助手回复的消息
     * - system: 系统提示消息
     * - 空字符串: 未知或不支持的消息类型
     */
    private String role;

    /**
     * 消息内容
     * 存储消息的文本内容，是消息的核心数据
     */
    private String content;

    /**
     * 构造函数
     * 将Spring AI的消息对象转换为视图对象
     * 
     * 转换逻辑说明：
     * 1. 根据消息类型（MessageType）确定角色标识
     * 2. 提取消息文本作为内容
     * 3. 忽略元数据（metadata），因为前端通常不需要这些信息
     * 
     * @param message Spring AI的消息对象，不能为null
     */
    public MessageVO(Message message) {
        this.role = switch (message.getMessageType()) {
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case SYSTEM -> "system";
            default -> "";
        };
        this.content = message.getText();
    }
}
