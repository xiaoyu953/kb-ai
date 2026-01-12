package com.itheima.ai.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * JSON Schema 校验器，用于验证 AI 输出的参数是否合法。
 *
 * 使用 networknt/json-schema-validator 库，支持 Draft 7。
 */
@Component
public class JsonSchemaValidator {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

    /**
     * 校验 JSON 字符串是否符合指定的 Schema。
     *
     * @param json   待校验的 JSON 字符串
     * @param schema JSON Schema 字符串
     * @throws IllegalArgumentException 如果校验失败
     */
    public void validate(String json, String schema) {
        try {
            JsonNode jsonNode = objectMapper.readTree(json);
            JsonNode schemaNode = objectMapper.readTree(schema);
            JsonSchema jsonSchema = factory.getSchema(schemaNode);
            Set<ValidationMessage> errors = jsonSchema.validate(jsonNode);
            if (!errors.isEmpty()) {
                // 只返回第一个错误（简化）
                throw new IllegalArgumentException("参数校验失败: " + errors.iterator().next().getMessage());
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 格式错误: " + e.getMessage());
        }
    }

}
