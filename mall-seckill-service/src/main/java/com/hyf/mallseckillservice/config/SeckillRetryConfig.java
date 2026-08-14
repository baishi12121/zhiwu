package com.hyf.mallseckillservice.config;

import com.hyf.mallseckillservice.mq.SeckillConsumerRetryExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SeckillRetryConfig {

    @Bean
    public SeckillConsumerRetryExecutor.RetrySleeper seckillConsumerRetrySleeper() {
        return millis -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            }
        };
    }
}
