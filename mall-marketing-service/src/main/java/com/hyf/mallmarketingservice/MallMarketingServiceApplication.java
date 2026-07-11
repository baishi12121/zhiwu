package com.hyf.mallmarketingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 营销域服务启动类
 *
 * <p>包含：优惠券 / 秒杀 / 拼团 / 积分 / 签到 / 活动。
 * 沿用旧 coupon-service 的 Redis Lua 秒杀 + MQ 异步落库 + Sentinel 限流设计。
 *
 * @author hyf
 */
@EnableScheduling
@EnableDiscoveryClient
@EnableFeignClients
@SpringBootApplication
public class MallMarketingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallMarketingServiceApplication.class, args);
    }
}
