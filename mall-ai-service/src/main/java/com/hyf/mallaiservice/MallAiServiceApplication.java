package com.hyf.mallaiservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * AI 域服务启动类（预留）
 *
 * <p>按架构约定，当前不写任何业务代码；后续接 RAG / 客服 / 智能推荐时再补。
 *
 * @author hyf
 */
@EnableDiscoveryClient
@SpringBootApplication
public class MallAiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallAiServiceApplication.class, args);
    }
}
