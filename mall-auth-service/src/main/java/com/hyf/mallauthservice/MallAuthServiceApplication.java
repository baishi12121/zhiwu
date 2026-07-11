package com.hyf.mallauthservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 认证中心启动类
 *
 * <p>只负责登录、颁发/刷新 token、权限校验，不放用户资料。
 * 用户资料落 {@code mall-user-service}。
 *
 * @author hyf
 */
@EnableDiscoveryClient
@EnableFeignClients
@SpringBootApplication
public class MallAuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallAuthServiceApplication.class, args);
    }
}
