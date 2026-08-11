package com.hyf.mallorderservice.controller;

import com.hyf.mallcommon.core.result.Result;
import com.hyf.mallorderservice.service.PayApplicationService;
import com.hyf.mallorderservice.service.PayResponse;
import com.hyf.mallorderservice.dto.PayCreateRequest;
import com.hyf.mallorderservice.dto.RefundRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付域 Controller — 实现支付模块 5 个核心接口。
 *
 * <p>用户 ID 从网关下发的 {@code X-User-Id} 请求头获取。
 * 微信回调接口 {@code /pay/wx/notify} 无需登录态（网关白名单放行）。
 *
 * <p>接口清单：
 * <ul>
 *   <li>POST /pay/wx/create         微信下单（返回调起支付参数）</li>
 *   <li>POST /pay/wx/notify         微信支付回调（微信服务器调用）</li>
 *   <li>GET  /pay/status/{orderId}  支付状态查询</li>
 *   <li>POST /pay/refund            申请退款</li>
 *   <li>GET  /pay/refund/{orderId}  退款状态查询</li>
 * </ul>
 *
 * @author hyf
 */
@RestController
@RequestMapping("/pay")
public class PayController {

    private static final Logger log = LoggerFactory.getLogger(PayController.class);

    private final PayApplicationService payApplicationService;

    public PayController(PayApplicationService payApplicationService) {
        this.payApplicationService = payApplicationService;
    }

    // ========== 4.1 微信下单 ==========

    /**
     * 微信下单 — 返回前端调起 {@code wx.requestPayment()} 所需参数。
     *
     * <p>Mock 模式下下单后订单立即变为"已支付"（秒成功）。
     */
    @PostMapping("/wx/create")
    public Result<PayResponse> createOrder(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody PayCreateRequest request) {
        PayResponse response = payApplicationService.createWxPayOrder(
                userId, request.getOrderId(), request.getOpenid());
        return Result.success(response);
    }

    // ========== 4.2 微信支付回调 ==========

    /**
     * 微信支付回调 — 由微信服务器调用，无需登录态。
     *
     * <p>响应格式为微信要求的 {@code {"code":"SUCCESS","message":"成功"}}，
     * 而非标准 {@code Result<T>}。
     */
    @PostMapping("/wx/notify")
    public Map<String, String> notify(
            @RequestBody String body,
            @RequestHeader Map<String, String> headers) {
        try {
            payApplicationService.handleNotify(body, headers);
            return notifyResponse("SUCCESS", "成功");
        } catch (Exception e) {
            log.error("[pay-notify] 回调处理失败: {}", e.getMessage(), e);
            return notifyResponse("FAIL", e.getMessage());
        }
    }

    // ========== 4.3 支付状态查询 ==========

    /**
     * 支付状态查询。
     */
    @GetMapping("/status/{orderId}")
    public Result<Map<String, Object>> getPayStatus(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long orderId) {
        return Result.success(payApplicationService.getPayStatus(userId, orderId));
    }

    // ========== 4.4 申请退款 ==========

    /**
     * 申请退款。
     */
    @PostMapping("/refund")
    public Result<Map<String, Object>> refund(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody RefundRequest request) {
        return Result.success(payApplicationService.refund(userId, request.getOrderId(), request.getReason()));
    }

    // ========== 4.5 退款状态查询 ==========

    /**
     * 退款状态查询。
     */
    @GetMapping("/refund/{orderId}")
    public Result<Map<String, Object>> getRefundStatus(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long orderId) {
        return Result.success(payApplicationService.getRefundStatus(userId, orderId));
    }

    // ==================== 内部方法 ====================

    /**
     * 构建微信回调响应（非标准 Result 格式）。
     */
    private Map<String, String> notifyResponse(String code, String message) {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("code", code);
        response.put("message", message);
        return response;
    }
}
