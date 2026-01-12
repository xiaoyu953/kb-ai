package com.itheima.ai.entity.vo;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * AI对话请求数据传输对象（DTO）
 * 用于封装前端发送到后端的对话请求参数
 *
 * 使用场景：
 * - ChatController.chat()方法的请求参数
 * - 前端通过POST JSON格式发送请求
 *
 * 请求示例：
 * {
 *   "prompt": "你好，请介绍一下自己",
 *   "chatId": "session-123",
 *   "files": [二进制文件数据]
 * }
 */
@Data
public class ChatRequest {

    /**
     * 用户输入的提示词/问题内容
     *
     * 作用：
     * - 存储用户发送的文字消息
     * - 作为AI生成回复的主要依据
     *
     * 数据类型：字符串
     * 示例："今天天气怎么样？"、"帮我写一段代码"
     */
    private String prompt;

    /**
     * 会话ID
     *
     * 作用：
     * - 标识一次独立的对话会话
     * - 用于AI对话记忆系统，关联历史消息
     * - 前端生成并维护，确保同一对话使用相同ID
     *
     * 使用说明：
     * - 首次对话时前端生成UUID作为chatId
     * - 后续对话使用相同的chatId保持对话连续性
     * - 不同对话使用不同的chatId实现多会话隔离
     *
     * 数据类型：字符串
     * 示例："abc123-def456-ghi789"
     */
    private String chatId;

    /**
     * 上传的图片文件列表
     *
     * 作用：
     * - 支持多模态对话，用户可以上传图片让AI分析
     * - 图片会与prompt一起发送给AI模型
     * - AI可以结合图片内容和文字进行综合回答
     *
     * 支持的场景：
     * - 图片问答：上传图片并提问图片内容
     * - 图文分析：上传多张图片进行对比分析
     * - OCR识别：让AI识别图片中的文字
     *
     * 数据类型：MultipartFile列表（Spring文件上传对象）
     * 可选性：可为null或空列表，此时为纯文本对话
     * 示例：[文件1, 文件2, 文件3]
     */
    private List<MultipartFile> files;
}
