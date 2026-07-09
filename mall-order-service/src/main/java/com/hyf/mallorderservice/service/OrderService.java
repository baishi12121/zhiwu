package com.hyf.mallorderservice.service;

import com.hyf.mallorderservice.entity.Order;
import java.util.List;

/**
 * 订单服务接口，提供对外的订单业务层能力
 */
public interface OrderService {

    /**
     * 创建订单
     *
     * @param order 订单实体，应包含 userId, totalAmount, realAmount, productId 等字段
     * @return 创建成功并回填自增 ID 后的订单实体
     */
    Order createOrder(Order order);

    /**
     * 根据主键ID删除订单
     *
     * @param id 订单主键ID
     * @return 删除成功返回 true，否则返回 false
     */
    boolean deleteOrder(Long id);

    /**
     * 更新订单属性（只更新传入实体中非空的属性）
     *
     * @param order 包含主键ID及需要更新属性的实体对象
     * @return 更新成功返回 true，否则返回 false
     */
    boolean updateOrder(Order order);

    /**
     * 根据订单ID获取详细信息
     *
     * @param id 订单主键ID
     * @return 对应的订单实体，未找到返回 null
     */
    Order getOrderById(Long id);

    /**
     * 获取所有订单列表
     *
     * @return 订单实体集合
     */
    List<Order> getAllOrders();

    /**
     * 获取指定用户的所有订单列表
     *
     * @param userId 用户主键ID
     * @return 该用户的订单实体集合
     */
    List<Order> getOrdersByUserId(Long userId);
}
