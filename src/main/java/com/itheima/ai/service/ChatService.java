// src/main/java/com/itheima/ai/service/ChatService.java
package com.itheima.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itheima.ai.entity.dto.RagResponse;
import com.itheima.ai.model.ToolCallRequest;
import com.itheima.ai.model.ToolDefinition;
import com.itheima.ai.service.tool.ToolRegistry;
import com.itheima.ai.validator.JsonSchemaValidator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatClient chatClient;
    private final ToolRegistry toolRegistry;
    private final AuthService authService;
    private final RagChatService ragService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final JsonSchemaValidator schemaValidator; // ✅ 名称与你 Controller 一致！

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${tool.rate-limit.window-minutes:1}")
    private int windowMinutes;

    @Value("${tool.rate-limit.max-calls:5}")
    private int maxCalls;

    public String handleUserMessage(String prompt, String chatId) {
        try {
            // === Step 1: 决策路由 ===
            String decisionPrompt = buildDecisionPrompt(prompt);
            String rawOutput = chatClient.prompt().user(decisionPrompt).call().content().trim();

            // === Step 2: 尝试工具调用 ===
            if (rawOutput.startsWith("{") || rawOutput.startsWith("[")) {
                try {
                    ToolCallRequest req = parseToolCall(rawOutput);
                    if (req != null && isValidToolName(req.getTool())) {
                        return executeTool(req, chatId);
                    }
                } catch (Exception e) {
                    log.warn("工具调用异常，fallback 到 RAG: {}", e.getMessage());
                }
            }

            // === Step 3: 走 RAG ===
            return fallbackToRag(prompt, chatId);

        } catch (Exception e) {
            log.error("ChatService 处理消息异常", e);
            throw new RuntimeException(e); // 由 Controller 捕获并返回友好提示
        }
    }

    private String buildDecisionPrompt(String prompt) {
        return """
                你是一个路由助手，请严格按以下规则响应：

                可用工具：
                - queryOrder: 查询订单状态（参数: {"orderId": "原始订单号，如OP12345、12345、订单12345"})

                规则：
                1. 只要用户问题中**包含订单相关关键词+任意数字**（如订单、OP、单号、查单），无论数字是几位，都必须输出工具调用 JSON。
                   - 示例：OP1234、OP12345、1234、12345、订单1234、查单OP67890 → 全部调用工具
                   - 提取规则：把用户问题中的「原始订单内容」原样传入orderId，不要做任何清洗/删减，例如用户写OP1234就传OP1234

                2. 其他所有情况（问报销、年假、打卡、规章制度、无数字无订单关键词），
                   → **禁止输出任何中文！禁止回答问题！**
                   → 必须输出空 JSON 对象：{}

                3. 输出必须是合法 JSON，且仅包含以下两种形式之一，无任何多余内容：
                   - {"tool": "queryOrder", "params": {"orderId": "用户输入的原始订单内容"}}
                   - {}

                用户问题：%s
                """.formatted(prompt);
    }

    private boolean isValidToolName(String toolName) {
        return toolName != null && !toolName.trim().isEmpty() && toolRegistry.getTool(toolName) != null;
    }

    private String executeTool(ToolCallRequest req, String chatId) throws Exception {
        ToolDefinition toolDef = toolRegistry.getTool(req.getTool());

        // Schema 校验
        String validationInput = objectMapper.writeValueAsString(req);
        schemaValidator.validate(validationInput, toolDef.jsonSchema());

        // 限流
        String rateKey = "tool:rate:" + chatId + ":" + req.getTool();
        Long count = (Long) redisTemplate.opsForValue().increment(rateKey);
        if (count == null) count = 1L;
        if (count == 1) {
            redisTemplate.expire(rateKey, windowMinutes, TimeUnit.MINUTES);
        }
        if (count > maxCalls) {
            return "操作太频繁，请" + windowMinutes + "分钟后再试。";
        }

        // 执行
        String userId = authService.getUserId(chatId);
        JsonNode params = objectMapper.convertValue(req.getParams(), JsonNode.class);
        return toolDef.executor().execute(params, userId, chatId);
    }

    private String fallbackToRag(String prompt, String chatId) {
        RagResponse response = ragService.answer(prompt, chatId);
        if (response != null && response.getAnswer() != null && !response.getAnswer().trim().isEmpty()) {
            return response.getAnswer();
        }
        return "抱歉，该问题暂未收录在企业知识库中，请咨询 HR 或相关负责人。";
    }

    // 👇 完全复用你 Controller 中的逻辑
    private ToolCallRequest parseToolCall(String raw) throws Exception {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        // 新增：判断是否是空JSON对象
        if ("{}".equals(raw.trim())) {
            return null;
        }
        if (raw.startsWith("[")) {
            List<ToolCallRequest> list = objectMapper.readValue(
                    raw,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ToolCallRequest.class)
            );
            return list.isEmpty() ? null : list.get(0);
        } else {
            return objectMapper.readValue(raw, ToolCallRequest.class);
        }
    }
}