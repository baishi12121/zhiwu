package com.hyf.malladminservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 管理后台服务启动类
 *
 * <p>面向运营端：商品上下架 / 订单管理 / 营销活动配置 / 用户运营。
 * 通过 Feign 调用各业务域的 internal 接口，不直接写库。
 *
 * @author hyf
 */
@EnableDiscoveryClient
@EnableFeignClients
@SpringBootApplication
public class MallAdminServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallAdminServiceApplication.class, args);
    }
}
