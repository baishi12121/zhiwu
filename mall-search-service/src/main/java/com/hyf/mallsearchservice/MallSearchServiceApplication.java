package com.hyf.mallsearchservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 搜索域服务启动类（骨架）
 *
 * <p>职责：ES 搜索 / 热门搜索 / 搜索记录 / 联想词 / 推荐。
 * 当前未引入 Elasticsearch 依赖，待 ES 部署后补全。
 *
 * @author hyf
 */
@EnableDiscoveryClient
@SpringBootApplication
public class MallSearchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallSearchServiceApplication.class, args);
    }
}
