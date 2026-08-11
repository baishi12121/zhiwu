package com.hyf.mallorderservice.service;

import java.util.Map;

/**
 * 支付服务抽象接口 — 屏蔽不同支付渠道（Mock / 微信）的差异。
 *
 * <p>开发阶段使用 {@code MockPayService}（秒成功、无需真实凭证），
 * 上线时切换为 {@code WechatPayService}（调用微信支付 V3 API）。
 * 切换方式：配置 {@code mall.pay.mode=mock|wechat}。
 *
 * <p>本接口仅负责与支付网关交互（下单、退款、回调解析），
 * 订单状态流转由 {@code PayApplicationService} 编排。
 *
 * @author hyf
 */
public interface PayService {

    /**
     * 创建支付订单 — 调用支付网关下单，返回前端调起支付所需参数。
     *
     * @param request 支付请求（订单号、金额、openid 等）
     * @return 支付参数（timeStamp / nonceStr / package / signType / paySign）
     */
    PayResponse createOrder(PayRequest request);

    /**
     * 申请退款 — 调用支付网关退款接口。
     *
     * @param orderNo 业务订单号
     */
    void refund(String orderNo);

    /**
     * 解析支付回调 — 验签 + 解密，返回交易结果。
     *
     * @param body    回调请求体（JSON）
     * @param headers 回调请求头（含签名相关头）
     * @return 解析后的支付通知结果
     */
    PayNotifyResult parseNotify(String body, Map<String, String> headers);

    /**
     * 是否为 Mock 实现（开发模式）。
     *
     * <p>应用服务据此决定是否在下单后立即模拟回调成功。
     *
     * @return true 表示当前为 Mock 模式
     */
    boolean isMock();
}
