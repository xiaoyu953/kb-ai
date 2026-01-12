package com.itheima.ai.service;

import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 模拟订单服务，提供订单状态查询。
 * <p>
 * 实际项目中应对接数据库或微服务。
 */
@Service
public class OrderService {

    // 模拟订单数据库
    private final Map<String, String> orderDb = Map.of(
            "OP12345", "已发货",
            "OP67890", "待付款",
            "OP11223", "已取消"
    );
    /**
     * 根据订单ID查询状态。
     *
     * @param orderId 订单ID，格式如 OP12345
     * @return 订单状态文本
     */
    public String getOrderStatus(String orderId){
        return orderDb.getOrDefault(orderId, "订单不存在");
    }
}
