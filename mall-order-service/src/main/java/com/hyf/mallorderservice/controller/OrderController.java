package com.hyf.mallorderservice.controller;

import com.hyf.mallcommon.core.page.PageQuery;
import com.hyf.mallcommon.core.page.PageResult;
import com.hyf.mallcommon.core.result.Result;
import com.hyf.mallcommon.core.result.ResultCode;
import com.hyf.mallorderservice.service.OrderApplicationService;
import com.hyf.mallorderservice.dto.OrderCancelRequest;
import com.hyf.mallorderservice.dto.OrderCreateRequest;
import com.hyf.mallorderservice.dto.OrderPreviewRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 订单域 Controller — 实现订单模块 9 个核心接口。
 *
 * <p>用户 ID 从网关下发的 {@code X-User-Id} 请求头获取。
 *
 * <p>接口清单：
 * <ul>
 *   <li>POST   /orders/preview         预览（金额+可用优惠券）</li>
 *   <li>POST   /orders                 创建订单</li>
 *   <li>GET    /orders                 订单列表</li>
 *   <li>GET    /orders/{id}            订单详情</li>
 *   <li>PUT    /orders/{id}/cancel     取消订单</li>
 *   <li>PUT    /orders/{id}/pay        标记已支付</li>
 *   <li>PUT    /orders/{id}/confirm    确认收货</li>
 *   <li>DELETE /orders/{id}            删除订单</li>
 *   <li>GET    /orders/status/{status} 按状态查</li>
 * </ul>
 *
 * @author hyf
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderApplicationService orderApplicationService;

    public OrderController(OrderApplicationService orderApplicationService) {
        this.orderApplicationService = orderApplicationService;
    }

    /** 健康检查 */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.success(Map.of(
                "service", "mall-order-service",
                "status", "UP",
                "aggregate", "Order -> OrderItem -> OrderAddress -> Payment"
        ));
    }

    // ========== 3.1 订单预览 ==========

    /**
     * 订单预览 — 计算金额 + 可用优惠券 + 用户地址列表。
     *
     * <p>goods 为空时从购物车取选中商品。
     */
    @PostMapping("/preview")
    public Result<Map<String, Object>> preview(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody(required = false) OrderPreviewRequest request) {
        if (request == null) {
            request = new OrderPreviewRequest();
        }
        return Result.success(orderApplicationService.previewOrder(userId, request));
    }

    // ========== 3.2 创建订单 ==========

    /**
     * 创建订单 — 扣库存 + 写订单 + 占券（本地事务）。
     */
    @PostMapping
    public Result<Map<String, Object>> create(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody OrderCreateRequest request) {
        return Result.success(orderApplicationService.createOrder(userId, request));
    }

    // ========== 3.3 订单列表 ==========

    /**
     * 订单列表 — 分页查询当前用户订单。
     *
     * @param orderState 订单状态（不传或 0 查全部）
     */
    @GetMapping
    public Result<PageResult<Map<String, Object>>> list(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) Integer orderState,
            PageQuery pageQuery) {
        return Result.success(orderApplicationService.getOrderList(userId, pageQuery, orderState));
    }

    // ========== 3.4 订单详情 ==========

    /**
     * 订单详情。
     */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        Map<String, Object> detail = orderApplicationService.getOrderDetail(userId, id);
        if (detail == null) {
            return Result.error(ResultCode.NOT_FOUND);
        }
        return Result.success(detail);
    }

    // ========== 3.5 取消订单 ==========

    /**
     * 取消订单 — 退库存 + 退券 + 改状态（仅待付款可取消）。
     */
    @PutMapping("/{id}/cancel")
    public Result<Map<String, Object>> cancel(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id,
            @RequestBody(required = false) OrderCancelRequest request) {
        String reason = (request != null && request.getCancelReason() != null)
                ? request.getCancelReason() : "用户取消";
        return Result.success(orderApplicationService.cancelOrder(userId, id, reason));
    }

    // ========== 3.6 标记已支付 ==========

    /**
     * 标记已支付 — 待付款 → 待发货（Mock 实现，秒成功）。
     */
    @PutMapping("/{id}/pay")
    public Result<Map<String, Object>> pay(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        return Result.success(orderApplicationService.payOrder(userId, id));
    }

    // ========== 3.7 确认收货 ==========

    /**
     * 确认收货 — 待收货 → 待评价。
     */
    @PutMapping("/{id}/confirm")
    public Result<Map<String, Object>> confirm(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        return Result.success(orderApplicationService.confirmOrder(userId, id));
    }

    // ========== 3.8 删除订单 ==========

    /**
     * 删除订单 — 仅待评价/已完成/已取消可删除，物理删除。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        orderApplicationService.deleteOrder(userId, id);
        return Result.success();
    }

    // ========== 3.9 按状态查 ==========

    /**
     * 按状态查订单。
     */
    @GetMapping("/status/{status}")
    public Result<PageResult<Map<String, Object>>> listByStatus(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Integer status,
            PageQuery pageQuery) {
        return Result.success(orderApplicationService.getOrdersByStatus(userId, status, pageQuery));
    }
}
