package com.itheima.ai.service.tool;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 订单查询参数
 * - 字段校验注解将用于运行时验证
 */
public class OrderParams {
    @Pattern(regexp = "^OP\\d{5}$", message = "订单ID格式应为 OP12345")
    @NotBlank(message = "订单ID不能为空")
    private String orderId;
}
