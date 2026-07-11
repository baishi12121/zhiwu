package com.hyf.mallmarketingservice.controller;

import com.hyf.mallcommon.core.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 营销域控制器（骨架）
 *
 * <p>目标接口（{@code doc/API接口文档.md} §12）：
 * <ul>
 *   <li>GET  /coupons               可领券列表</li>
 *   <li>GET  /coupons/me            我的优惠券</li>
 *   <li>POST /coupons/{id}/grab     抢券（秒杀，Sentinel + Redis Lua + MQ）</li>
 *   <li>POST /coupons/use           核销（内部，order 调用）</li>
 * </ul>
 *
 * @author hyf
 */
@RestController
@RequestMapping("/coupons")
public class CouponController {

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.success(Map.of(
                "service", "mall-marketing-service",
                "status", "UP",
                "scope", "coupon / seckill / group-buy / points / check-in / activity"
        ));
    }
}
