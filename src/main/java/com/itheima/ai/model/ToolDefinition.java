package com.itheima.ai.model;

import com.itheima.ai.annotation.AiTool;

/**
 * 工具的完整定义，包含名称、描述、参数 Schema 和执行逻辑。
 * <p>
 * 使用 record 保证不可变性，适合注册中心存储。
 */
public record ToolDefinition(
        String name,
        String description,
        String[] requiredPermissions,
        String jsonSchema,               // JSON Schema 字符串，用于参数校验
        ToolExecutor executor             // 实际执行逻辑
) {
    /**
     * 从带 @AiTool 注解的类和执行器构建 ToolDefinition。
     *
     * @param clazz    实现了 ToolExecutor 的类
     * @param executor 该类的实例
     * @return 工具定义
     */
    public static ToolDefinition fromClass(Class<?> clazz, ToolExecutor executor) {
        AiTool ann = clazz.getAnnotation(AiTool.class);
        if (ann == null) throw new IllegalArgumentException("类 " + clazz.getSimpleName() + " 未标注 @AiTool");
        return new ToolDefinition(
                ann.name(),
                ann.description(),
                ann.requiredPermissions(),
                getJsonSchema(clazz),
                executor
        );
    }

    /**
     * 根据工具类名返回预定义的 JSON Schema。
     * <p>
     * 未来可扩展为：通过反射自动生成 Schema，或从配置文件加载。
     */
    private static String getJsonSchema(Class<?> clazz) {
        // 简化：直接返回预定义 schema（也可用 Jackson 自动生成）
        if (clazz.getSimpleName().equals("OrderTool")) {
            return """
                    {
                      "type": "object",
                      "properties": {
                        "tool": {
                          "type": "string",
                          "enum": ["queryOrder"]
                        },
                        "params": {
                          "type": "object",
                          "properties": {
                            "orderId": {
                              "type": "string",
                              "minLength": 1,
                              "description": "用户提供的原始订单号（可能包含非数字字符）"
                            }
                          },
                          "required": ["orderId"],
                          "additionalProperties": false
                        }
                      },
                      "required": ["tool", "params"],
                      "additionalProperties": false
                    }
                    """;
        }
        throw new UnsupportedOperationException("Unsupported tool: " + clazz);
    }
}
