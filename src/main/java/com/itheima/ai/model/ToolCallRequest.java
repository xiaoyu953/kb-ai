package com.itheima.ai.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * AI 模型输出的工具调用请求结构体。
 * <p>
 * 用于解析大模型（如 DeepSeek）返回的 JSON 字符串，
 * 例如：{"tool": "queryOrder", "params": {"orderId": "OP12345"}}
 * <p>
 * 注意：
 * - 该类仅用于反序列化 AI 的原始输出
 * - params 字段在运行时通常是 LinkedHashMap（Jackson 默认行为）
 */
@Data
public class ToolCallRequest {
    /**
     * 要调用的工具名称，必须与 @AiTool 注解中的 name 一致
     * 例如："queryOrder"
     */
    @JsonProperty("tool")
    private String tool;

    /**
     * 工具所需的参数对象。
     * <p>
     * 虽然类型是 Object，但实际反序列化后是 Map<String, Object>，
     * 后续会通过 ObjectMapper 转为 JsonNode 进行 Schema 校验。
     */
    @JsonProperty("params")
    private Object params; // 反序列化时为 LinkedHashMap 或 JsonNode
}
