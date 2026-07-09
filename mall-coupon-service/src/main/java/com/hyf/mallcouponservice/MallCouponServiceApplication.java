package com.hyf.mallcouponservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MallCouponServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallCouponServiceApplication.class, args);
    }

}
