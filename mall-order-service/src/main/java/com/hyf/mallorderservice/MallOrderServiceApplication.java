package com.hyf.mallorderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 订单域服务启动类（DDD 分层）
 *
 * <p>订单是一个聚合，聚合内含：Order → OrderItem → OrderAddress → Payment。
 * 与商品域同处 {@code mall} 单库，扣库存走本地事务，不再经 Feign + Seata。
 *
 * @author hyf
 */
@EnableDiscoveryClient
@EnableFeignClients
@SpringBootApplication
public class MallOrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallOrderServiceApplication.class, args);
    }
}
