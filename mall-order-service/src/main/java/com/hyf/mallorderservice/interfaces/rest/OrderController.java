package com.hyf.mallorderservice.interfaces.rest;

import com.hyf.mallcommon.core.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 订单域 Controller（骨架，interfaces 层）
 *
 * <p>目标接口（{@code doc/API接口文档.md} §10~§11）：
 * <ul>
 *   <li>POST /orders/preview           结算预览</li>
 *   <li>POST /orders                    创建订单（本地事务：扣库存 + 插订单 + 占券）</li>
 *   <li>GET  /orders                    订单列表</li>
 *   <li>GET  /orders/{id}               订单详情</li>
 *   <li>PUT  /orders/{id}/pay           支付（mock）</li>
 *   <li>PUT  /orders/{id}/cancel        取消订单（退库存 + 退券）</li>
 *   <li>PUT  /orders/{id}/confirm       确认收货</li>
 *   <li>POST /orders/{id}/review       评价</li>
 * </ul>
 *
 * <p>ORDER 热度消息在事务提交后发送（{@code TransactionSynchronizationManager}）。
 *
 * @author hyf
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.success(Map.of(
                "service", "mall-order-service",
                "status", "UP",
                "aggregate", "Order -> OrderItem -> OrderAddress -> Payment"
        ));
    }
}
