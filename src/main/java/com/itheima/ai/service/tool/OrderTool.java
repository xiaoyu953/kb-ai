package com.itheima.ai.service.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.itheima.ai.annotation.AiTool;
import com.itheima.ai.model.ToolExecutor;
import com.itheima.ai.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 查询订单状态的 AI 工具。
 * <p>
 * - 通过 @Component 注册为 Spring Bean
 * - 通过 @AiTool 声明为 AI 可调用工具
 * - 实现 ToolExecutor 接口提供执行逻辑
 */
@Component
@AiTool(
        name = "queryOrder",
        description = "根据订单ID查询订单当前状态",
        requiredPermissions = {"order:read"}
)
public class OrderTool implements ToolExecutor {
    @Autowired
    private OrderService orderService;

    /**
     * 执行订单查询逻辑（AI 调用入口）
     *
     * @param params AI 传入的参数，必须包含 "orderId" 字段（值可能是 "12345"、"OP12345"、"订单12345" 等）
     * @param userId 当前用户ID（可用于权限校验，本例暂未启用）
     * @param chatId 会话ID（可用于限流或日志，本例暂未使用）
     * @return 自然语言结果，始终为字符串，永不抛异常
     */
    @Override
    public String execute(JsonNode params, String userId, String chatId) {

        // === 1. 安全获取 orderId 字段 ===
        if (params == null || !params.has("orderId")) {
            return "缺少订单号。请提供您的订单编号，例如 OP12345。";
        }
        String rawOrderId = params.get("orderId").asText();
        if (rawOrderId == null || rawOrderId.trim().isEmpty()) {
            return "订单号不能为空。请提供有效的订单编号。";
        }
        // === 2. 清洗输入：只保留数字 ===
        String digitsOnly = rawOrderId.replaceAll("\\D+", "");// \D 表示非数字字符
        // === 3. 校验是否为 5 位数字 ===
        if (digitsOnly.length() != 5) {
            return "订单号格式不正确。请提供 OP 加 5 位数字的完整订单号，例如 OP12345。";
        }
        // === 4. 构造标准订单ID ===
        String standardOrderId = "OP" + digitsOnly;
        // === 5. （可选）权限校验（示例，按需启用）===
        // if (!orderService.canAccess(userId, standardOrderId)) {
        //     return "您无权查看订单 " + standardOrderId + " 的信息。";
        // }

        // === 6. 调用真实服务并返回结果 ===
        try {
            String status = orderService.getOrderStatus(standardOrderId);
            return "订单 " + standardOrderId + " 当前状态为【" + status + "】。";
        } catch (Exception e) {
            // 捕获所有异常，避免中断对话
            return "系统暂时无法查询订单 " + standardOrderId + "，请稍后再试。";
        }
    }
}
