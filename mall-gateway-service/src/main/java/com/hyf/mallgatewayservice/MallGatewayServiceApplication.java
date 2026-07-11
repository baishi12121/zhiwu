package com.hyf.mallgatewayservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * zhiwu-mall 网关启动类
 *
 * <p>统一入口 8080，负责路由转发 / 全局 CORS / token 校验。
 *
 * @author hyf
 */
@EnableDiscoveryClient
@SpringBootApplication
public class MallGatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallGatewayServiceApplication.class, args);
    }
}
