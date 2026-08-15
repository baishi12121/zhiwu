package com.hyf.mallsearchservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 搜索域服务启动类.
 *
 * <p>职责:ES 搜索 / 热门搜索 / 搜索记录 / 联想词 / 推荐.
 * 当前阶段落地:索引管理 + MySQL→ES 全量同步 + 主搜索(分页/高亮/过滤) + facets 聚合 + 自动补全.
 *
 * @author hyf
 */
@EnableDiscoveryClient
@SpringBootApplication
@MapperScan("com.hyf.mallsearchservice.mapper")
public class MallSearchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallSearchServiceApplication.class, args);
    }
}
