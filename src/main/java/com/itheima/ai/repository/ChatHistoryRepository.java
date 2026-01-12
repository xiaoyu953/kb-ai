package com.itheima.ai.repository;

import java.util.List;

/**
 * 会话历史仓储接口
 * 定义会话ID的存储和查询操作
 *
 * 仓储模式说明：
 * - Repository模式，将数据访问逻辑封装在接口后面
 * - 提供统一的数据访问API
 * - 隐藏具体实现细节，便于切换不同的存储方式
 *
 * 与ChatMemory的区别：
 * - ChatHistoryRepository：只存储会话ID（标识有哪些对话）
 * - ChatMemory：存储每个会话的具体消息内容
 * - 两者配合使用：先查ID列表，再查具体消息
 *
 * 实现类：
 * - RedisChatHistory：基于Redis的分布式实现
 * - InMemoryChatHistoryRepository：基于内存的实现（带持久化）
 */
public interface ChatHistoryRepository {

    /**
     * 保存会话记录
     * 当用户开始一次新对话时调用，记录该会话的存在
     *
     * 处理逻辑：
     * 1. 根据type找到对应的会话ID集合
     * 2. 将chatId添加到集合中
     * 3. 同一个chatId不会重复添加（Set自动去重）
     *
     * @param type 业务类型，如：chat、service、pdf
     * @param chatId 会话ID
     */
    void save(String type, String chatId);

    /**
     * 获取会话ID列表
     * 查询指定业务类型下的所有会话ID
     *
     * 返回值说明：
     * - 返回会话ID的列表
     * - 按字母顺序排序
     * - 如果该类型下没有会话，返回空列表
     *
     * @param type 业务类型，如：chat、service、pdf
     * @return 会话ID列表
     */
    List<String> getChatIds(String type);
}
