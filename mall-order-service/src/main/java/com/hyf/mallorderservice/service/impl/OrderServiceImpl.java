package com.hyf.mallorderservice.service.impl;

import com.hyf.mallorderservice.api.ProductClient;
import com.hyf.mallorderservice.entity.Order;
import com.hyf.mallorderservice.entity.ProductScoreMessage;
import com.hyf.mallorderservice.mapper.OrderMapper;
import com.hyf.mallorderservice.service.OrderService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * 订单服务实现类，处理订单核心业务逻辑
 */
@Service
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;

    /**
     * 构造器注入 OrderMapper
     *
     * @param orderMapper 订单数据库操作接口
     */
    @Autowired
    public OrderServiceImpl(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }
    @Autowired
    private ProductClient productClient;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 创建订单，默认为未支付状态（status=0）
     *
     * @param order 订单实体，应包含 userId, totalAmount, realAmount, productId 等字段
     * @return 创建成功并回填自增 ID 后的订单实体
     */
    @Override
    public Order createOrder(Order order) {

        if (order == null) {
            throw new IllegalArgumentException("订单数据不能为空");
        }
        if (order.getUserId() == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        if (order.getTotalAmount() == null || order.getRealAmount() == null) {
            throw new IllegalArgumentException("订单金额不能为空");
        }
        productClient.decreaseStock(order.getProductId(),1);
        // 默认设置为未支付状态
        if (order.getStatus() == null) {
            order.setStatus(0);
        }
        
        orderMapper.insert(order);

        // 发送下单消息到 RabbitMQ，更新商品热榜（+5分）
        ProductScoreMessage message = new ProductScoreMessage();
        message.setProductId(order.getProductId());
        message.setActionType("ORDER");
        message.setTimestamp(System.currentTimeMillis());
        rabbitTemplate.convertAndSend("exchange.product.rank", "routing.product.order", message);

        return order;
    }

    /**
     * 根据主键ID删除订单
     *
     * @param id 订单主键ID
     * @return 删除成功返回 true，否则返回 false
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteOrder(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("订单ID不能为空");
        }
        return orderMapper.deleteById(id) > 0;
    }

    /**
     * 更新订单属性（只更新传入实体中非空的属性）
     *
     * @param order 包含主键ID及需要更新属性的实体对象
     * @return 更新成功返回 true，否则返回 false
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateOrder(Order order) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("更新数据且主键ID不能为空");
        }
        return orderMapper.update(order) > 0;
    }

    /**
     * 根据订单ID获取详细信息
     *
     * @param id 订单主键ID
     * @return 对应的订单实体，未找到返回 null
     */
    @Override
    public Order getOrderById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("订单ID不能为空");
        }
        return orderMapper.selectById(id);
    }

    /**
     * 获取所有订单列表
     *
     * @return 订单实体集合
     */
    @Override
    public List<Order> getAllOrders() {
        return orderMapper.selectAll();
    }

    /**
     * 获取指定用户的所有订单列表
     *
     * @param userId 用户主键ID
     * @return 该用户的订单实体集合
     */
    @Override
    public List<Order> getOrdersByUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        return orderMapper.selectByUserId(userId);
    }
}
