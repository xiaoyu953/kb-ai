package com.itheima.ai.entity.po;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.messages.*;

import java.util.List;
import java.util.Map;

/**
 * 消息持久化对象（Persistent Object）
 * 
 * 该类是Spring AI消息系统与项目持久化层之间的桥梁，
 * 负责消息对象的序列化和反序列化转换。
 * 
 * 设计目的：
 * 1. 将Spring AI的Message对象转换为可序列化的POJO格式
 * 2. 支持消息在Redis等存储介质中的持久化
 * 3. 实现Message与POJO之间的双向转换
 * 
 * @author heima-ai
 * @version 1.0.0
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class Msg {

    /**
     * 消息类型
     * 标识消息的种类，决定消息的处理方式和显示逻辑
     * 
     * 支持的消息类型：
     * - SYSTEM: 系统消息，用于设置AI助手的行为规则和上下文
     * - USER: 用户消息，包含用户的提问和输入
     * - ASSISTANT: 助手消息，包含AI生成的回复内容
     * - 其他: 不支持的类型，转换时会抛出异常
     */
    private MessageType messageType;

    /**
     * 消息文本内容
     * 存储消息的核心文本数据，是消息的主要载体
     * 
     * 对于不同类型的消息：
     * - SYSTEM消息: 包含系统提示词和角色设定
     * - USER消息: 包含用户的提问内容
     * - ASSISTANT消息: 包含AI生成的回复文本
     */
    private String text;

    /**
     * 消息元数据
     * 以键值对形式存储消息的附加信息，如时间戳、来源等
     * 
     * 常见的元数据字段：
     * - timestamp: 消息创建时间
     * - media_type: 媒体类型（用于多媒体消息）
     * - token_usage: 令牌使用统计
     * - model_info: 模型相关信息
     * 
     * 注意：并非所有消息都包含元数据，此字段可能为null或空Map
     */
    private Map<String, Object> metadata;

    /**
     * 构造方法
     * 将Spring AI的Message对象转换为Msg持久化对象
     * 
     * 转换过程：
     * 1. 提取消息类型（messageType）并保存
     * 2. 提取消息文本（text）并保存
     * 3. 复制消息的元数据（metadata）以保留附加信息
     * 
     * 使用场景：
     * - 将AI消息保存到Redis前的序列化操作
     * - 消息对象在不同存储介质间的传输
     * 
     * @param message Spring AI的消息对象，包含原始消息信息，不能为null
     */
    public Msg(Message message) {
        this.messageType = message.getMessageType();
        this.text = message.getText();
        this.metadata = message.getMetadata();
    }

    /**
     * 转换为Message对象
     * 将Msg对象还原为Spring AI的Message对象
     * 
     * 转换逻辑说明：
     * - SYSTEM类型的消息转换为SystemMessage对象
     * - USER类型的消息转换为UserMessage对象，包含空媒体列表和原始元数据
     * - ASSISTANT类型的消息转换为AssistantMessage对象，包含空工具调用和空媒体列表
     * - 其他类型会抛出IllegalArgumentException异常
     * 
     * 使用场景：
     * - 从Redis读取消息后的反序列化操作
     * - 恢复对话历史到AI模型的内存中
     * 
     * @return 对应类型的Spring AI Message对象
     * @throws IllegalArgumentException 当遇到不支持的消息类型时抛出
     */
    public Message toMessage() {
        return switch (messageType) {
            case SYSTEM -> new SystemMessage(text);
            case USER -> new UserMessage(text, List.of(), metadata);
            case ASSISTANT -> new AssistantMessage(text, metadata, List.of(), List.of());
            default -> throw new IllegalArgumentException("Unsupported message type: " + messageType);
        };
    }
}
