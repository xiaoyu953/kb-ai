package com.itheima.ai.service.tool;

import com.itheima.ai.annotation.AiTool;
import com.itheima.ai.model.ToolDefinition;
import com.itheima.ai.model.ToolExecutor;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册中心：启动时自动扫描所有带 @AiTool 的 ToolExecutor Bean，
 * 并按工具名建立索引，供运行时快速查找。
 * <p>
 * 实现 ApplicationContextAware 以获取 Spring 上下文。
 */
@Component
public class ToolRegistry implements ApplicationContextAware {

    // 线程安全的工具映射表：toolName -> ToolDefinition
    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();

    /**
     * Spring 启动完成后自动调用，扫描所有工具 Bean。
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        // 获取所有标注了 @AiTool 的 Spring Bean
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(AiTool.class);
        for (Object bean : beans.values()) {
            if (bean instanceof ToolExecutor executor) {
                Class<?> clazz = bean.getClass();
                ToolDefinition def = ToolDefinition.fromClass(clazz, executor);
                tools.put(def.name(), def);
                System.out.println("✅ 已注册 AI 工具: " + def.name());
            }
        }
    }

    /**
     * 根据工具名获取定义。
     */
    public ToolDefinition getTool(String name) {
        return tools.get(name);
    }

    /**
     * 判断是否存在指定工具。
     */
    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }
}
