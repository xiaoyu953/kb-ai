// src/main/java/com/itheima/ai/service/AuthService.java
package com.itheima.ai.service;

import org.springframework.stereotype.Service;

/**
 * 模拟用户认证服务。
 * <p>
 * 实际项目中应从 JWT 或 Session 中解析用户ID。
 */
@Service
public class AuthService {

    /**
     * 根据会话ID获取用户ID（简化：chatId 即 userId）。
     */
    public String getUserId(String chatId) {
        return chatId;
    }
}