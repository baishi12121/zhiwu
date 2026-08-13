package com.hyf.mallseckillservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.cloud.nacos.discovery.register-enabled=false",
        "mall.security.enabled=false",
        "mall.jwt.secret=seckill-service-test-secret-at-least-32-bytes",
        "seckill.tasks.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class MallSeckillServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
