package com.hyf.mallorderservice.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 支付下单请求 — 由应用服务构建，传入 {@link PayService#createOrder}。
 *
 * @author hyf
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayRequest {

    /** 业务订单号 */
    private String orderNo;

    /** 订单 ID */
    private Long orderId;

    /** 用户 openid（微信 JSAPI 支付必填） */
    private String openid;

    /** 支付金额（元） */
    private BigDecimal amount;

    /** 商品描述 */
    private String description;
}
