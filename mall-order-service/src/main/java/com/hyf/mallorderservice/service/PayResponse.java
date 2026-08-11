package com.hyf.mallorderservice.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 支付下单响应 — 返回给前端调起 {@code wx.requestPayment()} 所需的 5 个参数。
 *
 * @author hyf
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayResponse {

    /** 小程序 appId */
    private String appId;

    /** 时间戳（秒） */
    private String timeStamp;

    /** 随机字符串 */
    private String nonceStr;

    /** 订单详情扩展字符串，格式：prepay_id=xxx */
    @JsonProperty("package")
    private String packageStr;

    /** 签名类型：RSA */
    private String signType;

    /** 签名 */
    private String paySign;
}
