// src/main/java/com/itheima/ai/config/AiEmbeddingConfig.java

package com.itheima.ai.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AiEmbeddingConfig {

    @Bean
    @Primary
    public EmbeddingModel embeddingModel(OllamaEmbeddingModel ollamaEmbeddingModel) {
        System.out.println("✅ 使用 Ollama 的 mxbai-embed-large 做 embedding！");
        return ollamaEmbeddingModel;
    }
}