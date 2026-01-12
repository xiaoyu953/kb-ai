package com.itheima.ai.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.itheima.ai.entity.po.Msg;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 基于内存的对话历史仓储实现
 * 
 * 该类实现了ChatHistoryRepository接口，提供对话历史的管理功能，
 * 并使用JSON文件进行数据持久化存储。
 * 
 * 核心功能：
 * 1. 管理对话会话ID列表，记录用户的所有对话
 * 2. 在应用关闭时自动将对话数据持久化到JSON文件
 * 3. 在应用启动时从JSON文件恢复对话历史
 * 
 * 设计特点：
 * 1. 使用内存存储（HashMap）保证高速读写
 * 2. 通过Jackson实现JSON序列化/反序列化
 * 3. 自动化的生命周期管理（@PostConstruct和@PreDestroy）
 * 4. 同时持久化对话历史（chatIds）和对话记忆（messages）
 * 
 * 数据文件说明：
 * - chat-history.json: 存储各类型的对话ID列表
 * - chat-memory.json: 存储对话的具体消息内容
 * 
 * @author heima-ai
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InMemoryChatHistoryRepository implements ChatHistoryRepository {

    /**
     * 对话历史数据存储
     * 使用Map结构，按业务类型（type）组织对话ID列表
     * 
     * Map结构说明：
     * - Key: 业务类型（String），如"chat"、"service"、"pdf"等
     * - Value: 该类型下的对话ID列表（List<String>），按时间倒序排列
     * 
     * 存储示例：
     * {
     *   "chat": ["chat_id_1", "chat_id_2", "chat_id_3"],
     *   "service": ["service_id_1", "service_id_2"],
     *   "pdf": ["pdf_id_1"]
     * }
     */
    private Map<String, List<String>> chatHistory;

    /**
     * JSON序列化工具
     * 用于对象与JSON字符串之间的转换，支持复杂的嵌套结构
     * 
     * 配置特点：
     * - 使用默认的Jackson ObjectMapper配置
     * - 支持泛型类型的反序列化（TypeReference）
     * - 支持美化的JSON输出（Pretty Printer）
     */
    private final ObjectMapper objectMapper;

    /**
     * 对话记忆管理
     * 实际存储对话消息内容的核心组件
     * 
     * 职责说明：
     * - 提供消息的添加、获取和清除功能
     * - 在应用启动时从文件恢复历史消息
     * - 在应用关闭时将消息持久化到文件
     * 
     * 依赖注入说明：
     * - 实际类型为InMemoryChatMemory（内存对话记忆）
     * - 通过反射获取其内部conversationHistory字段进行持久化
     */
    private final ChatMemory chatMemory;

    /**
     * 保存对话会话ID
     * 
     * 执行逻辑：
     * 1. 使用computeIfAbsent确保指定类型的列表存在
     * 2. 检查是否已存在该chatId，避免重复添加
     * 3. 将新chatId添加到列表开头（实现倒序排列，最新在前）
     * 
     * 重复处理：
     * - 如果chatId已存在，则不做任何操作
     * - 这样可以保证列表中没有重复的会话ID
     * 
     * @param type 业务类型，如"chat"、"service"、"pdf"等
     * @param chatId 要保存的对话会话ID
     */
    @Override
    public void save(String type, String chatId) {
        List<String> chatIds = chatHistory.computeIfAbsent(type, k -> new ArrayList<>());
        if (chatIds.contains(chatId)) {
            return;
        }
        chatIds.add(0, chatId);
    }

    /**
     * 获取指定类型的所有对话会话ID
     * 
     * 执行逻辑：
     * 1. 从chatHistory中获取指定type的对话ID列表
     * 2. 如果不存在该type，则返回空列表
     * 
     * 返回值说明：
     * - 返回的是列表的引用而非副本
     * - 列表按添加顺序倒序排列（最新的在前面）
     * - 返回的列表不可修改（如果底层集合为空）
     * 
     * @param type 业务类型，如"chat"、"service"、"pdf"等
     * @return 该类型下的对话ID列表，如果不存在则返回空列表
     */
    @Override
    public List<String> getChatIds(String type) {
        return chatHistory.getOrDefault(type, List.of());
    }

    /**
     * 初始化方法
     * 在Bean创建完成后执行，用于加载持久化的对话历史数据
     * 
     * 执行步骤：
     * 1. 初始化chatHistory为空的HashMap
     * 2. 检查chat-history.json文件是否存在
     * 3. 加载对话历史数据到chatHistory
     * 4. 加载对话记忆数据到ChatMemory
     * 
     * 文件不存在处理：
     * - 如果chat-history.json不存在，跳过加载过程
     * - chatHistory保持为空Map，可正常进行后续操作
     * 
     * 异常处理：
     * - 如果读取过程中发生IOException，抛出RuntimeException
     * - 该异常会阻止Spring Boot应用的正常启动
     */
    @PostConstruct
    private void init() {
        this.chatHistory = new HashMap<>();
        FileSystemResource historyResource = new FileSystemResource("chat-history.json");
        FileSystemResource memoryResource = new FileSystemResource("chat-memory.json");
        if (!historyResource.exists()) {
            return;
        }
        try {
            Map<String, List<String>> chatIds = this.objectMapper.readValue(
                historyResource.getInputStream(), 
                new TypeReference<>() {}
            );
            if (chatIds != null) {
                this.chatHistory = chatIds;
            }
            Map<String, List<Msg>> memory = this.objectMapper.readValue(
                memoryResource.getInputStream(), 
                new TypeReference<>() {}
            );
            if (memory != null) {
                memory.forEach(this::convertMsgToMessage);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * 将Msg列表转换为Message列表并添加到对话记忆
     * 
     * 用途说明：
     * - 在应用启动时，将持久化的Msg对象还原为Message对象
     * - 并添加到ChatMemory中，使AI能够访问历史对话
     * 
     * 执行逻辑：
     * - 遍历每个chatId对应的Msg列表
     * - 将Msg转换为Message
     * - 调用chatMemory.add()添加到对应会话的记忆中
     * 
     * @param chatId 会话ID
     * @param messages 该会话的历史消息列表
     */
    private void convertMsgToMessage(String chatId, List<Msg> messages) {
        this.chatMemory.add(chatId, messages.stream().map(Msg::toMessage).toList());
    }

    /**
     * 持久化方法
     * 在Bean销毁前执行，用于将对话数据保存到JSON文件
     * 
     * 执行步骤：
     * 1. 将chatHistory序列化为JSON字符串
     * 2. 从ChatMemory中提取对话记忆并序列化为JSON字符串
     * 3. 将两个JSON字符串分别写入对应文件
     * 
     * 文件操作说明：
     * - 使用FileSystemResource定位文件位置
     * - 使用PrintWriter以UTF-8编码写入
     * - 文件不存在时会自动创建
     * 
     * 异常处理：
     * - IOException: IO操作异常，重新抛出为RuntimeException
     * - SecurityException: 安全权限异常，重新抛出为RuntimeException
     * - NullPointerException: 空指针异常，记录错误日志后重新抛出
     * - 所有异常都会阻止程序继续执行
     */
    @PreDestroy
    private void persistent() {
        String history = toJsonString(this.chatHistory);
        String memory = getMemoryJsonString();
        FileSystemResource historyResource = new FileSystemResource("chat-history.json");
        FileSystemResource memoryResource = new FileSystemResource("chat-memory.json");
        try (
            PrintWriter historyWriter = new PrintWriter(
                historyResource.getOutputStream(), 
                true, 
                StandardCharsets.UTF_8
            );
            PrintWriter memoryWriter = new PrintWriter(
                memoryResource.getOutputStream(), 
                true, 
                StandardCharsets.UTF_8
            )
        ) {
            historyWriter.write(history);
            memoryWriter.write(memory);
        } catch (IOException ex) {
            log.error("保存向量存储文件时发生IOException异常。", ex);
            throw new RuntimeException(ex);
        } catch (SecurityException ex) {
            log.error("保存向量存储文件时发生SecurityException异常。", ex);
            throw new RuntimeException(ex);
        } catch (NullPointerException ex) {
            log.error("保存向量存储文件时发生NullPointerException异常。", ex);
            throw new RuntimeException(ex);
        }
    }

    /**
     * 获取对话记忆的JSON字符串
     * 
     * 实现原理：
     * - 使用反射访问InMemoryChatMemory的私有字段conversationHistory
     * - 将Map<String, List<Message>>转换为Map<String, List<Msg>>
     * - 最后序列化为JSON字符串
     * 
     * 反射操作说明：
     * - 获取conversationHistory字段（private final类型）
     * - 设置Accessible为true以允许访问
     * - 将Message对象转换为Msg对象以便持久化
     * 
     * @return 对话记忆的JSON字符串
     * @throws RuntimeException 如果反射操作失败
     */
    private String getMemoryJsonString() {
        Class<InMemoryChatMemory> clazz = InMemoryChatMemory.class;
        try {
            Field field = clazz.getDeclaredField("conversationHistory");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, List<Message>> memory = (Map<String, List<Message>>) field.get(chatMemory);
            Map<String, List<Msg>> memoryToSave = new HashMap<>();
            memory.forEach((chatId, messages) -> 
                memoryToSave.put(chatId, messages.stream().map(Msg::new).toList())
            );
            return toJsonString(memoryToSave);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 对象转JSON字符串
     * 使用Jackson的美化输出格式，使JSON更易读
     * 
     * 实现逻辑：
     * - 配置ObjectMapper使用默认的美化打印机
     * - 调用writeValueAsString将对象转换为JSON字符串
     * 
     * @param object 要转换的对象
     * @return JSON格式的字符串
     * @throws RuntimeException 如果序列化失败
     */
    private String toJsonString(Object object) {
        ObjectWriter objectWriter = this.objectMapper.writerWithDefaultPrettyPrinter();
        try {
            return objectWriter.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("将documentMap序列化为JSON时发生错误。", e);
        }
    }
}
