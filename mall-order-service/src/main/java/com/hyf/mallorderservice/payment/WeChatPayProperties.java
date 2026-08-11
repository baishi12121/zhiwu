package com.hyf.mallorderservice.payment;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信支付配置 — 对应 {@code mall.wechat.pay.*} 配置项。
 *
 * <p>仅在 {@code mall.pay.mode=wechat} 时被 {@link WechatPayService} 使用。
 * Mock 模式下无需配置这些字段。
 *
 * @author hyf
 */
@Data
@Component
@ConfigurationProperties(prefix = "mall.wechat.pay")
public class WeChatPayProperties {

    /** 小程序 appId */
    private String appid;

    /** 商户号 */
    private String mchid;

    /** 商户 API 证书序列号 */
    private String mchSerialNo;

    /** 商户私钥文件路径（PEM 格式） */
    private String privateKeyFilePath;

    /** API v3 密钥（用于回调解密） */
    private String apiV3Key;

    /** 支付回调地址 */
    private String notifyUrl;

    /** 退款回调地址 */
    private String refundNotifyUrl;
}
