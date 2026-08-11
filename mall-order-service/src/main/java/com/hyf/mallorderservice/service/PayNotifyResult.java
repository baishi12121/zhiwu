package com.hyf.mallorderservice.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 支付回调解析结果 — 由 {@link PayService#parseNotify} 返回。
 *
 * @author hyf
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayNotifyResult {

    /** 业务订单号 */
    private String outTradeNo;

    /** 微信支付订单号 */
    private String transactionId;

    /** 交易状态：SUCCESS / NOTPAY / CLOSED / REVOKED / USERPAYING / PAYERROR */
    private String tradeState;

    /** 支付金额（分） */
    private Integer amountTotal;
}
