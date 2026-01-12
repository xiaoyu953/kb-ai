package com.itheima.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 黑马AI应用启动类
 * 项目的入口点，负责启动整个Spring Boot应用程序
 *
 * 应用程序功能概述：
 * 1. 智能对话系统 - 基于AI模型的对话功能
 * 2. 会话管理 - 支持多会话的记忆功能
 * 3. 向量检索 - 使用Qdrant向量数据库进行RAG检索增强
 */
@SpringBootApplication
public class HeimaAiApplication {

    /**
     * 程序主入口方法
     * 通过SpringApplication.run()启动Spring Boot应用上下文
     * Spring Boot会自动扫描当前包及其子包下的所有组件进行自动配置
     *
     * @param args 命令行参数，传递给Spring Boot应用
     */
    public static void main(String[] args) {
        SpringApplication.run(HeimaAiApplication.class, args);
    }

}
