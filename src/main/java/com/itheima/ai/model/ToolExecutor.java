package com.itheima.ai.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 所有 AI 工具必须实现的执行接口。
 *
 * execute 方法接收：
 * - params: 经过 Schema 校验的参数（JsonNode 格式）
 * - userId: 当前用户 ID（用于权限控制）
 * - chatId: 会话 ID（用于限流、日志）
 *
 * 返回值：工具执行结果（纯文本，将被用于生成最终回答）
 */
@FunctionalInterface
public interface ToolExecutor {
    String execute(JsonNode params, String userId, String chatId);
}
