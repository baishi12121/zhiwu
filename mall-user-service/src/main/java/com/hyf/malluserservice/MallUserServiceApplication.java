package com.hyf.malluserservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 用户域服务启动类
 *
 * <p>负责：用户 / 地址 / 收藏 / 足迹 / 消息 / 积分 / 购物车
 * （不含登录，登录归 mall-auth-service）。
 *
 * @author hyf
 */
@EnableDiscoveryClient
@EnableFeignClients
@SpringBootApplication
public class MallUserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallUserServiceApplication.class, args);
    }
}
