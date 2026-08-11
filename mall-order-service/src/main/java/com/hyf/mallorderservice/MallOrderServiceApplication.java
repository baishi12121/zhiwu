package com.hyf.mallorderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 订单服务启动类
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
