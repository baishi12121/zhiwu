package com.hyf.mallauthservice.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "mall.wechat")
@Data
public class WeChatProperties {

    private String appid; //小程序的appid
    private String secret; //小程序的秘钥
    private String code2SessionUrl = "https://api.weixin.qq.com/sns/jscode2session"; //微信 code2Session 接口地址
    /** 微信 access_token 接口（用于调用 getuserphonenumber） */
    private String accessTokenUrl = "https://api.weixin.qq.com/cgi-bin/token";
    /** 微信 getPhoneNumber 接口（用 code + access_token 换真实手机号） */
    private String getPhoneNumberUrl = "https://api.weixin.qq.com/wxa/business/getuserphonenumber";
    /** access_token Redis 缓存 key */
    private String accessTokenCacheKey = "wechat:access_token";
    private String mchid; //商户号
    private String mchSerialNo; //商户API证书的证书序列号
    private String privateKeyFilePath; //商户私钥文件
    private String apiV3Key; //证书解密的密钥
    private String weChatPayCertFilePath; //平台证书
    private String notifyUrl; //支付成功的回调地址
    private String refundNotifyUrl; //退款成功的回调地址

}
