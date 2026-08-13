package com.hyf.mallseckillservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 秒杀服务启动类。
 *
 * <p>开启服务注册、定时任务和 MyBatis Mapper 扫描，承载秒杀预热、下单、MQ 消费和超时回补链路。</p>
 */
@EnableDiscoveryClient
@EnableScheduling
@MapperScan("com.hyf.mallseckillservice.mapper")
@SpringBootApplication
public class MallSeckillServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallSeckillServiceApplication.class, args);
    }
}
