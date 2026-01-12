package com.itheima.ai.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class CacheKeyUtils {

    /**
     * 归一化问题：转小写 + 去除多余标点和空格
     */
    private static String normalize(String question) {
        if (question == null) return "";
        return question.trim()
                .toLowerCase()
                .replaceAll("[？?！!。，,\\.\\s]+", " ") // 合并标点/空格为单个空格
                .trim();
    }

    /**
     * 生成缓存键：rag:answer:{chatId}:{sha256(normalizedQuestion)}
     */
    public static String buildRagCacheKey(String chatId, String question) {
        String normalized = normalize(question);
        String hash = sha256(normalized);
        return "rag:answer:" + chatId + ":" + hash;
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : encoded) {
                String hexStr = Integer.toHexString(0xff & b);
                if (hexStr.length() == 1) hex.append('0');
                hex.append(hexStr);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not supported", e);
        }
    }
}
