package com.itheima.ai.controller;

import com.itheima.ai.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final ChatService chatService;

    @GetMapping("/chat")
    public String chat(@RequestParam String prompt, @RequestParam String chatId) {
        try {
            return chatService.handleUserMessage(prompt, chatId);
        } catch (Exception e) {
            log.error("AI 服务异常，prompt={}, chatId={}", prompt, chatId, e);
            return "抱歉，服务暂时不可用，请稍后再试。";
        }
    }
}