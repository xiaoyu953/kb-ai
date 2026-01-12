package com.itheima.ai.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itheima.ai.entity.po.Msg;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

/**
 * 基于Redis的对话记忆实现
 * 
 * 该类实现了Spring AI的ChatMemory接口，使用Redis作为存储后端
 * 管理对话过程中的消息历史记录。
 * 
 * 核心功能：
 * 1. 添加消息到对话记忆中
 * 2. 获取指定会话的最近N条消息
 * 3. 清除指定会话的记忆
 * 
 * 设计目的：
 * - 为AI模型提供对话上下文支持
 * - 实现多轮对话的连贯性
 * - 支持对话历史的持久化和恢复
 * 
 * 技术特点：
 * 1. 使用Redis List数据结构存储消息
     * 支持消息的顺序存储和范围查询
     * 天然支持FIFO（先进先出）语义
 * 2. 使用JSON序列化消息内容
     * 支持消息类型的完整保存
     * 保留消息的元数据信息
 * 3. 自动处理消息转换
     * Message与Msg之间的双向转换
     * 隐藏序列化/反序列化的复杂性
 * 
 * Redis数据结构：
 * - 使用List类型存储会话消息
 * - Key格式：chat:{conversationId}
 * - Value：JSON序列化的消息列表
 * - 新消息从左侧插入（leftPushAll）
 * 
 * @author heima-ai
 * @version 1.0.0
 * @see org.springframework.ai.chat.memory.ChatMemory
 */
@RequiredArgsConstructor
//@Component
public class RedisChatMemory implements ChatMemory {

    /**
     * Redis操作模板
     * 提供对Redis List类型的操作能力
     * 
     * 核心方法说明：
     * - leftPushAll(): 从列表左侧批量插入元素
     * - range(): 获取列表指定范围内的元素
     * - delete(): 删除指定键
     * 
     * 使用场景：
     * - 添加消息时使用leftPushAll
     * - 获取历史消息时使用range
     * - 清除会话时使用delete
     */
    private final StringRedisTemplate redisTemplate;

    /**
     * JSON序列化工具
     * 用于Message对象与JSON字符串之间的转换
     * 
     * 序列化流程：
     * 1. Message → Msg（提取必要字段）
     * 2. Msg → JSON字符串（使用ObjectMapper）
     * 
     * 反序列化流程：
     * 1. JSON字符串 → Msg（使用ObjectMapper）
     * 2. Msg → Message（调用toMessage方法）
     * 
     * 配置特点：
     * - 使用默认的日期格式和编码
     * - 支持复杂嵌套对象的序列化
     * - 自动处理Map类型的字段（metadata）
     */
    private final ObjectMapper objectMapper;

    /**
     * Redis键前缀
     * 用于构建存储对话消息的Redis键名
     * 
     * 键名格式：chat:{conversationId}
     * 
     * 示例：
     * - chat:user123 -> 用户user123的对话消息
     * - chat:session456 -> 会话session456的消息历史
     * - chat:abc123 -> ID为abc123的对话消息
     * 
     * 设计说明：
     * - 简洁的前缀设计，减少键名的存储开销
     * - 明确的命名规范，便于调试和管理
     * - 前缀与实际会话ID拼接形成完整键名
     */
    private final static String PREFIX = "chat:";

    /**
     * 添加消息到对话记忆中
     * 
     * 执行步骤：
     * 1. 检查消息列表是否为空，为空则直接返回
     * 2. 将Message对象转换为Msg对象
     * 3. 将Msg对象序列化为JSON字符串
     * 4. 使用Redis的lpush命令批量插入消息
     * 
     * 存储顺序说明：
     * - 使用leftPushAll从列表左侧插入
     * - 消息列表的最后一个元素会成为列表的第一个元素
     * - 即messages.get(0)最终位于列表的最左端（最新消息）
     * 
     * 数据结构示意：
     * 原始messages列表：[msg1, msg2, msg3]（按时间顺序）
     * 插入后的Redis列表：[msg3, msg2, msg1]（msg3在最左/最新）
     * 
     * @param conversationId 会话ID，用于标识特定的对话
     * @param messages 要保存的消息列表，按时间顺序排列
     */
    @Override
    public void add(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        List<String> list = messages.stream()
            .map(Msg::new)
            .map(msg -> {
                try {
                    return objectMapper.writeValueAsString(msg);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            })
            .toList();
        redisTemplate.opsForList().leftPushAll(PREFIX + conversationId, list);
    }

    /**
     * 获取对话记忆中的消息
     * 
     * 执行步骤：
     * 1. 根据会话ID构建Redis键名
     * 2. 使用lrange命令获取指定范围的消息
     * 3. 检查返回结果是否为空
     * 4. 将JSON字符串反序列化为Msg对象
     * 5. 将Msg对象转换为Message对象
     * 
     * 获取范围说明：
     * - range(key, 0, lastN) 获取从索引0开始的lastN条消息
     * - 索引0对应列表的最左端（最新消息）
     * - lastN=0表示不获取任何消息，返回空列表
     * - lastN=-1可以获取所有消息（这里使用固定逻辑）
     * 
     * 消息顺序：
     * - 返回的消息列表按从新到旧的顺序排列
     * - 即索引0的消息是最新的
     * 
     * @param conversationId 会话ID
     * @param lastN 获取最近N条消息，0表示获取所有消息
     * @return 按时间倒序排列的消息列表，如果无消息则返回空列表
     */
    @Override
    public List<Message> get(String conversationId, int lastN) {
        List<String> list = redisTemplate.opsForList().range(
            PREFIX + conversationId, 
            0, 
            lastN
        );
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        return list.stream()
            .map(s -> {
                try {
                    return objectMapper.readValue(s, Msg.class);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            })
            .map(Msg::toMessage)
            .toList();
    }

    /**
     * 清除指定会话的记忆
     * 删除Redis中该会话对应的所有消息记录
     * 
     * 执行逻辑：
     * 1. 根据会话ID构建完整的Redis键名
     * 2. 使用delete命令删除该键
     * 3. Redis会自动释放该键关联的内存空间
     * 
     * 使用场景：
     * - 用户开始新的对话时
     * - 用户主动要求清除对话历史
     * - 对话超时需要清理时
     * - 管理员手动清除特定对话时
     * 
     * 注意事项：
     * - 该操作不可逆，一旦删除无法恢复
     * - 建议在删除前做好日志记录
     * - 确认用户权限后再执行删除操作
     * 
     * @param conversationId 要清除记忆的会话ID
     */
    @Override
    public void clear(String conversationId) {
        redisTemplate.delete(PREFIX + conversationId);
    }
}
