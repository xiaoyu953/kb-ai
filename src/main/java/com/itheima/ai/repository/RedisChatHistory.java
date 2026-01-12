package com.itheima.ai.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 基于Redis的对话历史仓储实现
 * 
 * 该类实现了ChatHistoryRepository接口，使用Redis作为存储后端
 * 管理对话会话ID列表。
 * 
 * 核心功能：
 * 1. 保存对话会话ID到Redis集合
 * 2. 获取指定业务类型下的所有对话会话ID
 * 
 * 设计优势：
 * 1. Redis提供高性能的读写能力，适合高并发场景
 * 2. 使用Set数据结构天然保证会话ID不重复
 * 3. 数据持久化在Redis中，应用重启后仍然保留
 * 4. 支持分布式部署，多个应用实例共享会话数据
 * 
 * Redis数据结构：
 * - 使用Set类型存储会话ID列表
 * - Key格式：chat:history:{type}
 * - Value：会话ID的集合
 * 
 * @author heima-ai
 * @version 1.0.0
 */
@RequiredArgsConstructor
//@Component
public class RedisChatHistory implements ChatHistoryRepository{

    /**
     * Redis操作模板
     * 提供对Redis的各种操作方法，是操作Redis的核心工具
     * 
     * 注入说明：
     * - 由Spring Boot自动配置并注入
     * - 已配置好Redis连接信息和序列化方式
     * - 支持String类型的操作（StringRedisTemplate）
     * 
     * 使用方式：
     * - opsForSet(): 获取Set操作接口
     * - 用于添加和查询会话ID集合
     */
    private final StringRedisTemplate redisTemplate;

    /**
     * Redis键前缀
     * 用于构建存储对话历史的Redis键名
     * 
     * 键名格式：chat:history:{type}
     * 
     * 示例：
     * - chat:history:chat -> 存储普通聊天的会话ID
     * - chat:history:service -> 存储客服对话的会话ID
     * - chat:history:pdf -> 存储PDF相关对话的会话ID
     */
    private final static String CHAT_HISTORY_KEY_PREFIX = "chat:history:";

    /**
     * 保存对话会话ID
     * 将指定业务类型和会话ID保存到Redis集合中
     * 
     * 执行逻辑：
     * 1. 构建完整的Redis键名：chat:history:{type}
     * 2. 使用sadd命令将chatId添加到Set集合中
     * 3. Redis的Set特性自动处理重复ID（不会重复添加）
     * 
     * 原子性保证：
     * - Redis的sadd命令是原子操作
     * - 即使多个请求同时添加同一个ID，也不会出现重复
     * 
     * @param type 业务类型，如"chat"、"service"、"pdf"等
     * @param chatId 要保存的对话会话ID
     */
    @Override
    public void save(String type, String chatId) {
        redisTemplate.opsForSet().add(CHAT_HISTORY_KEY_PREFIX + type, chatId);
    }

    /**
     * 获取对话会话ID列表
     * 查询指定业务类型下的所有会话ID并返回排序后的列表
     * 
     * 执行逻辑：
     * 1. 构建完整的Redis键名
     * 2. 使用smembers命令获取Set中的所有元素
     * 3. 将Set转换为List
     * 4. 按字符串字典序排序
     * 
     * 返回值说明：
     * - 返回的是新的ArrayList，不是原Set的引用
     * - 列表按字符串升序排列
     * - 如果Set为空或不存在，返回空列表（不可修改）
     * 
     * 性能考虑：
     * - smembers返回Set中的所有元素
     * - 如果会话ID数量非常大，可能会有性能问题
     * - 建议在大量数据场景下使用分页查询
     * 
     * @param type 业务类型，如"chat"、"service"、"pdf"等
     * @return 排序后的会话ID列表，如果不存在则返回空列表
     */
    @Override
    public List<String> getChatIds(String type) {
        Set<String> chatIds = redisTemplate.opsForSet().members(CHAT_HISTORY_KEY_PREFIX + type);
        if (chatIds == null || chatIds.isEmpty()) {
            return Collections.emptyList();
        }
        return chatIds.stream().sorted(String::compareTo).toList();
    }
}
