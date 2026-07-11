package com.hyf.mallproductservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 商品域服务启动类（DDD 分层）
 *
 * <p>分包：
 * <ul>
 *   <li>{@code interfaces}  —— Controller，只做协议转换</li>
 *   <li>{@code application} —— 应用服务，编排领域逻辑 + 事务</li>
 *   <li>{@code domain}      —— 聚合 / 实体 / 值对象 / 仓储接口 / 领域服务 / 领域事件</li>
 *   <li>{@code infrastructure} —— 仓储实现 / MQ / Redis / MyBatis</li>
 *   <li>{@code api}         —— 对外 Feign 客户端 / DTO</li>
 * </ul>
 *
 * @author hyf
 */
@EnableDiscoveryClient
@SpringBootApplication
public class MallProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallProductServiceApplication.class, args);
    }
}
