package com.hyf.mallorderservice.controller;

import com.hyf.mallorderservice.common.Result;
import com.hyf.mallorderservice.entity.Order;
import com.hyf.mallorderservice.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 订单管理控制层，提供订单 CRUD 的 RESTful API 接口
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    /**
     * 构造器注入 OrderService
     *
     * @param orderService 订单服务业务层接口
     */
    @Autowired
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }


//    @GetMapping("/demo")
//    public String test(){
//        return "调用成功";
//    }
    /**
     * 创建订单
     *
     * @param order 订单信息体
     * @return 包含自增主键ID的订单数据
     */
    @PostMapping
    public Result<Order> createOrder(@RequestBody Order order) {
        try {
            Order createdOrder = orderService.createOrder(order);
            return Result.success(createdOrder);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error("创建订单失败: " + e.getMessage());
        }
    }

    /**
     * 根据订单ID删除订单
     *
     * @param id 订单ID
     * @return 操作状态
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteOrder(@PathVariable("id") Long id) {
        try {
            boolean success = orderService.deleteOrder(id);
            if (success) {
                return Result.success("删除订单成功");
            } else {
                return Result.error("删除订单失败，订单可能不存在");
            }
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error("删除订单失败: " + e.getMessage());
        }
    }

    /**
     * 更新订单信息
     *
     * @param order 需要更新的订单数据（必须带id）
     * @return 操作状态
     */
    @PutMapping
    public Result<String> updateOrder(@RequestBody Order order) {
        try {
            boolean success = orderService.updateOrder(order);
            if (success) {
                return Result.success("更新订单成功");
            } else {
                return Result.error("更新订单失败，订单可能不存在");
            }
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error("更新订单失败: " + e.getMessage());
        }
    }

    /**
     * 根据主键ID获取订单详情
     *
     * @param id 订单主键ID
     * @return 订单详细数据
     */
    @GetMapping("/{id}")
    public Result<Order> getOrderById(@PathVariable("id") Long id) {
        try {
            Order order = orderService.getOrderById(id);
            if (order != null) {
                return Result.success(order);
            } else {
                return Result.error(404, "未找到该订单");
            }
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error("查询订单失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有订单列表
     *
     * @return 订单数据集合
     */
    @GetMapping
    public Result<List<Order>> getAllOrders() {
        try {
            List<Order> orders = orderService.getAllOrders();
            return Result.success(orders);
        } catch (Exception e) {
            return Result.error("获取订单列表失败: " + e.getMessage());
        }
    }

    /**
     * 根据用户ID获取该用户的所有订单
     *
     * @param userId 用户主键ID
     * @return 该用户的所有订单集合
     */
    @GetMapping("/user/{userId}")
    public Result<List<Order>> getOrdersByUserId(@PathVariable("userId") Long userId) {
        try {
            List<Order> orders = orderService.getOrdersByUserId(userId);
            return Result.success(orders);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error("查询用户订单失败: " + e.getMessage());
        }
    }
}
