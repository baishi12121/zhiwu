package com.hyf.mallorderservice.payment;

import com.hyf.mallorderservice.service.PayNotifyResult;
import com.hyf.mallorderservice.service.PayRequest;
import com.hyf.mallorderservice.service.PayResponse;
import com.hyf.mallorderservice.service.PayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

/**
 * Mock 支付服务实现 — 开发阶段使用，无需真实微信支付凭证。
 *
 * <p>特性：
 * <ul>
 *   <li>{@link #createOrder} 返回模拟的支付参数（前端无需调 wx.requestPayment）</li>
 *   <li>{@link #refund} 空实现（退款状态由应用服务直接更新）</li>
 *   <li>{@link #parseNotify} 返回模拟成功（Mock 模式不会收到真实回调）</li>
 *   <li>{@link #isMock} 返回 true，应用服务据此立即模拟回调成功</li>
 * </ul>
 *
 * @author hyf
 */
public class MockPayService implements PayService {

    private static final Logger log = LoggerFactory.getLogger(MockPayService.class);

    @Override
    public PayResponse createOrder(PayRequest request) {
        log.info("[mock-pay] 模拟下单: orderNo={}, amount={}", request.getOrderNo(), request.getAmount());
        return PayResponse.builder()
                .appId("mock_appid")
                .timeStamp(String.valueOf(System.currentTimeMillis() / 1000))
                .nonceStr(UUID.randomUUID().toString().replace("-", ""))
                .packageStr("prepay_id=mock_" + request.getOrderNo())
                .signType("RSA")
                .paySign("mock_sign_" + System.currentTimeMillis())
                .build();
    }

    @Override
    public void refund(String orderNo) {
        log.info("[mock-pay] 模拟退款: orderNo={}", orderNo);
        // Mock 模式下退款直接成功，退款状态由 PayApplicationService 更新
    }

    @Override
    public PayNotifyResult parseNotify(String body, Map<String, String> headers) {
        log.info("[mock-pay] 模拟回调解析（不应被调用）: body={}", body);
        return PayNotifyResult.builder()
                .outTradeNo("mock_order")
                .transactionId("mock_transaction_" + System.currentTimeMillis())
                .tradeState("SUCCESS")
                .amountTotal(0)
                .build();
    }

    @Override
    public boolean isMock() {
        return true;
    }
}
