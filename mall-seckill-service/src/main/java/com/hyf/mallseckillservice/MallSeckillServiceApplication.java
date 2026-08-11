package com.hyf.mallseckillservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableDiscoveryClient
@SpringBootApplication
public class MallSeckillServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallSeckillServiceApplication.class, args);
    }
}
