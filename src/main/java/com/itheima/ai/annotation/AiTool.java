package com.itheima.ai.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个类为 AI 可调用的工具。
 * <p>
 * 使用示例：
 *
 * @Component
 * @AiTool(name = "queryOrder", description = "查询订单状态")
 * public class OrderTool implements ToolExecutor { ... }
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AiTool {
    /**
     * 工具的唯一标识名，必须与 AI 输出中的 "tool" 字段一致
     */
    String name();  // 工具名，如 "queryOrder"

    /**
     * 工具的简要描述，可用于动态生成 Prompt
     */
    String description();  // 描述（用于未来 Prompt 生成）

    /**
     * 调用此工具所需的权限列表（预留，当前简化实现）
     */
    String[] requiredPermissions() default {}; // 所需权限
}
