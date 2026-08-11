package com.hyf.mallorderservice.payment;

import com.hyf.mallorderservice.repository.PayRepository;
import com.hyf.mallorderservice.service.PayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 支付服务 Bean 配置 — 根据 {@code mall.pay.mode} 切换 Mock / Wechat 实现。
 *
 * <p>切换规则：
 * <ul>
 *   <li>{@code mall.pay.mode=mock}（默认）→ {@link MockPayService}，开发阶段秒成功</li>
 *   <li>{@code mall.pay.mode=wechat} → {@link WechatPayService}，调用微信支付 V3 API</li>
 * </ul>
 *
 * @author hyf
 */
@Configuration
public class PayServiceConfig {

    private static final Logger log = LoggerFactory.getLogger(PayServiceConfig.class);

    /**
     * Mock 支付服务 — 开发阶段默认使用。
     */
    @Bean
    @ConditionalOnProperty(prefix = "mall.pay", name = "mode", havingValue = "mock", matchIfMissing = true)
    public PayService mockPayService() {
        log.info("[pay-config] 使用 MockPayService（开发模式）");
        return new MockPayService();
    }

    /**
     * 微信支付服务 — 上线时配置 {@code mall.pay.mode=wechat} 启用。
     */
    @Bean
    @ConditionalOnProperty(prefix = "mall.pay", name = "mode", havingValue = "wechat")
    public PayService wechatPayService(WeChatPayProperties props, PayRepository payRepository) {
        log.info("[pay-config] 使用 WechatPayService（生产模式）: mchid={}", props.getMchid());
        return new WechatPayService(props, payRepository);
    }
}
