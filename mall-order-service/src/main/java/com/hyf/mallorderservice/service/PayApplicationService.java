package com.hyf.mallorderservice.service;

import com.hyf.mallcommon.core.exception.BizException;
import com.hyf.mallcommon.core.result.ResultCode;
import com.hyf.mallorderservice.repository.OrderRepository;
import com.hyf.mallorderservice.repository.PayRepository;
import com.hyf.mallorderservice.service.OrderDomainService;
import com.hyf.mallorderservice.service.PayNotifyResult;
import com.hyf.mallorderservice.service.PayRequest;
import com.hyf.mallorderservice.service.PayResponse;
import com.hyf.mallorderservice.service.PayService;
import com.hyf.mallorderservice.dataobject.OrderDO;
import com.hyf.mallorderservice.dataobject.OrderItemDO;
import com.hyf.mallorderservice.dataobject.OrderStatusLogDO;
import com.hyf.mallorderservice.dataobject.PayRecordDO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 支付应用服务 — 编排微信下单、回调入账、退款、状态查询等用例。
 *
 * <p>通过 {@link PayService} 抽象屏蔽 Mock / 微信支付差异：
 * <ul>
 *   <li>Mock 模式：下单后立即模拟回调成功（秒到账）</li>
 *   <li>微信模式：下单后等待微信异步回调入账</li>
 * </ul>
 *
 * <p>订单状态流转由本服务负责，{@link PayService} 仅与支付网关交互。
 *
 * @author hyf
 */
@Service
public class PayApplicationService {

    private static final Logger log = LoggerFactory.getLogger(PayApplicationService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PayService payService;
    private final PayRepository payRepository;
    private final OrderRepository orderRepository;
    private final OrderDomainService orderDomainService;

    public PayApplicationService(PayService payService,
                                 PayRepository payRepository,
                                 OrderRepository orderRepository,
                                 OrderDomainService orderDomainService) {
        this.payService = payService;
        this.payRepository = payRepository;
        this.orderRepository = orderRepository;
        this.orderDomainService = orderDomainService;
    }

    // ========== 4.1 微信下单 ==========

    /**
     * 微信下单 — 创建支付记录 + 调用支付网关下单。
     *
     * <p>Mock 模式下下单后立即模拟回调成功（订单变为待发货）。
     * 微信模式下返回支付参数，前端调 {@code wx.requestPayment} 后等待回调。
     *
     * @param userId  用户 ID
     * @param orderId 订单 ID
     * @param openid  用户 openid（微信模式必填，Mock 模式可空）
     * @return 支付参数（前端调起支付所需）
     */
    @Transactional(rollbackFor = Exception.class)
    public PayResponse createWxPayOrder(Long userId, Long orderId, String openid) {
        // 1. 查询订单，校验归属和状态
        OrderDO order = orderRepository.findByIdAndUserId(orderId, userId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "订单不存在");
        }
        if (order.getOrderState() != null && order.getOrderState() != OrderDomainService.STATE_UNPAID) {
            throw new BizException(ResultCode.PAY_ORDER_ALREADY_PAID);
        }

        // 2. 创建或复用支付记录
        PayRecordDO record = payRepository.findByOrderId(orderId);
        if (record != null && record.getPayStatus() != null && record.getPayStatus() == 1) {
            throw new BizException(ResultCode.PAY_ORDER_ALREADY_PAID);
        }
        if (record == null) {
            record = createPayRecord(order);
        }

        // 3. 构建支付请求
        String description = buildOrderDescription(order.getId());
        PayRequest payRequest = PayRequest.builder()
                .orderNo(order.getOrderNo())
                .orderId(order.getId())
                .openid(openid)
                .amount(order.getPayMoney())
                .description(description)
                .build();

        // 4. 调用支付网关下单
        PayResponse response = payService.createOrder(payRequest);

        // 5. 保存 prepayId（微信模式有值，Mock 模式为 mock_xxx）
        record.setPrepayId(response.getPackageStr());
        payRepository.update(record);

        log.info("支付下单成功: orderId={}, orderNo={}, mode={}", orderId, order.getOrderNo(),
                payService.isMock() ? "mock" : "wechat");

        // 6. Mock 模式：立即模拟回调成功
        if (payService.isMock()) {
            String mockTransactionId = "mock_tx_" + System.currentTimeMillis();
            handlePaySuccess(order, record, mockTransactionId);
        }

        return response;
    }

    // ========== 4.2 支付回调 ==========

    /**
     * 处理微信支付回调 — 验签 + 解密 + 入账。
     *
     * <p>幂等：若支付记录已为"已支付"状态，直接返回成功。
     *
     * @param body    回调请求体（JSON）
     * @param headers 回调请求头
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleNotify(String body, Map<String, String> headers) {
        if (payService.isMock()) {
            log.warn("[pay-notify] Mock 模式不应收到回调，忽略");
            return;
        }

        // 1. 解析回调
        PayNotifyResult notifyResult = payService.parseNotify(body, headers);
        log.info("[pay-notify] 收到回调: orderNo={}, tradeState={}",
                notifyResult.getOutTradeNo(), notifyResult.getTradeState());

        // 2. 查询支付记录
        PayRecordDO record = payRepository.findByOrderNo(notifyResult.getOutTradeNo());
        if (record == null) {
            log.warn("[pay-notify] 支付记录不存在: orderNo={}", notifyResult.getOutTradeNo());
            return;
        }

        // 3. 幂等：已支付则跳过
        if (record.getPayStatus() != null && record.getPayStatus() == 1) {
            log.info("[pay-notify] 支付记录已入账，跳过: orderNo={}", notifyResult.getOutTradeNo());
            return;
        }

        // 4. 仅 SUCCESS 状态才入账
        if (!"SUCCESS".equals(notifyResult.getTradeState())) {
            log.warn("[pay-notify] 交易状态非 SUCCESS: {}, orderNo={}",
                    notifyResult.getTradeState(), notifyResult.getOutTradeNo());
            payRepository.updatePayStatus(record.getId(), 3, null, null);
            return;
        }

        // 5. 查询订单并入账
        OrderDO order = orderRepository.findById(record.getOrderId());
        if (order == null) {
            log.error("[pay-notify] 订单不存在: orderId={}", record.getOrderId());
            return;
        }

        handlePaySuccess(order, record, notifyResult.getTransactionId());
    }

    // ========== 4.3 支付状态查询 ==========

    /**
     * 查询支付状态。
     *
     * @param userId  用户 ID
     * @param orderId 订单 ID
     * @return 支付状态信息
     */
    public Map<String, Object> getPayStatus(Long userId, Long orderId) {
        OrderDO order = orderRepository.findByIdAndUserId(orderId, userId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "订单不存在");
        }

        PayRecordDO record = payRepository.findByOrderId(orderId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderId.toString());
        result.put("orderNo", order.getOrderNo());
        result.put("orderState", order.getOrderState());
        result.put("payMoney", order.getPayMoney());

        if (record != null) {
            result.put("payStatus", record.getPayStatus());
            result.put("transactionId", record.getTransactionId() != null ? record.getTransactionId() : "");
            result.put("paidAt", record.getPaidAt() != null
                    ? record.getPaidAt().format(TIME_FORMATTER) : "");
        } else {
            result.put("payStatus", 0);
            result.put("transactionId", "");
            result.put("paidAt", "");
        }

        return result;
    }

    // ========== 4.4 申请退款 ==========

    /**
     * 申请退款 — 调用支付网关退款接口。
     *
     * <p>Mock 模式下退款立即成功。微信模式下退款进入"退款中"状态，等待微信回调。
     *
     * @param userId  用户 ID
     * @param orderId 订单 ID
     * @param reason  退款原因（可空）
     * @return 退款信息
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> refund(Long userId, Long orderId, String reason) {
        // 1. 查询订单，校验归属
        OrderDO order = orderRepository.findByIdAndUserId(orderId, userId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "订单不存在");
        }

        // 2. 校验支付记录存在且已支付
        PayRecordDO record = payRepository.findByOrderId(orderId);
        if (record == null) {
            throw new BizException(ResultCode.PAY_REFUND_NOT_FOUND);
        }
        if (record.getPayStatus() == null || record.getPayStatus() != 1) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "订单未支付，无法退款");
        }
        if (record.getRefundStatus() != null && record.getRefundStatus() == 1) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "订单已退款");
        }

        // 3. 设置退款原因
        String refundReason = reason != null ? reason : "用户申请退款";
        record.setRefundReason(refundReason);
        payRepository.update(record);

        // 4. 调用支付网关退款
        payService.refund(order.getOrderNo());

        // 5. Mock 模式：立即标记退款成功
        if (payService.isMock()) {
            String mockRefundNo = "RF" + System.currentTimeMillis();
            payRepository.updateRefundStatus(record.getId(), 1, mockRefundNo, null,
                    record.getPayAmount(), LocalDateTime.now());
            log.info("[pay-refund] Mock 退款成功: orderNo={}", order.getOrderNo());
        }

        // 6. 返回退款信息
        PayRecordDO updated = payRepository.findByOrderId(orderId);
        return buildRefundResultMap(updated);
    }

    // ========== 4.5 退款状态查询 ==========

    /**
     * 查询退款状态。
     *
     * @param userId  用户 ID
     * @param orderId 订单 ID
     * @return 退款状态信息
     */
    public Map<String, Object> getRefundStatus(Long userId, Long orderId) {
        OrderDO order = orderRepository.findByIdAndUserId(orderId, userId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "订单不存在");
        }

        PayRecordDO record = payRepository.findByOrderId(orderId);
        if (record == null) {
            throw new BizException(ResultCode.PAY_REFUND_NOT_FOUND);
        }

        return buildRefundResultMap(record);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 处理支付成功 — 更新订单状态 + 支付记录 + 状态日志。
     *
     * <p>Mock 模式和微信回调共用此方法。
     *
     * @param order          订单 DO
     * @param record         支付记录 DO
     * @param transactionId  交易号
     */
    private void handlePaySuccess(OrderDO order, PayRecordDO record, String transactionId) {
        int fromState = order.getOrderState();
        orderDomainService.validateStateTransition(fromState, OrderDomainService.STATE_UNSHIPPED);

        // 更新订单状态
        order.setOrderState(OrderDomainService.STATE_UNSHIPPED);
        order.setPaidAt(LocalDateTime.now());
        orderRepository.updateOrder(order);

        // 更新支付记录
        payRepository.updatePayStatus(record.getId(), 1, transactionId, LocalDateTime.now());

        // 记录状态日志
        saveStatusLog(order.getId(), fromState, OrderDomainService.STATE_UNSHIPPED, "SYSTEM", "支付成功");

        log.info("支付入账成功: orderId={}, transactionId={}", order.getId(), transactionId);
    }

    /**
     * 创建支付记录。
     */
    private PayRecordDO createPayRecord(OrderDO order) {
        PayRecordDO record = new PayRecordDO();
        record.setOrderId(order.getId());
        record.setOrderNo(order.getOrderNo());
        record.setUserId(order.getUserId());
        record.setPayAmount(order.getPayMoney());
        record.setPayStatus(0);
        payRepository.insert(record);
        return record;
    }

    /**
     * 构建订单商品描述（取第一个商品名）。
     */
    private String buildOrderDescription(Long orderId) {
        List<OrderItemDO> items = orderRepository.findOrderItems(orderId);
        if (items != null && !items.isEmpty()) {
            return items.get(0).getName();
        }
        return "zhiwu-mall订单";
    }

    /**
     * 记录订单状态流转日志。
     */
    private void saveStatusLog(Long orderId, Integer fromState, int toState, String operator, String remark) {
        OrderStatusLogDO log = new OrderStatusLogDO();
        log.setOrderId(orderId);
        log.setFromState(fromState);
        log.setToState(toState);
        log.setOperator(operator);
        log.setRemark(remark);
        orderRepository.insertStatusLog(log);
    }

    /**
     * 构建退款结果 Map。
     */
    private Map<String, Object> buildRefundResultMap(PayRecordDO record) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", record.getOrderId().toString());
        result.put("orderNo", record.getOrderNo());
        result.put("refundNo", record.getRefundNo() != null ? record.getRefundNo() : "");
        result.put("refundId", record.getRefundId() != null ? record.getRefundId() : "");
        result.put("refundAmount", record.getRefundAmount() != null ? record.getRefundAmount() : record.getPayAmount());
        result.put("refundStatus", record.getRefundStatus() != null ? record.getRefundStatus() : -1);
        result.put("refundReason", record.getRefundReason() != null ? record.getRefundReason() : "");
        result.put("refundedAt", record.getRefundedAt() != null
                ? record.getRefundedAt().format(TIME_FORMATTER) : "");
        return result;
    }
}
