package com.hyf.mallproductservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 商品服务启动类
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
