package com.hyf.mallcouponservice.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.hyf.mallcouponservice.common.Result;
import com.hyf.mallcouponservice.service.CouponService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/coupon")
@Slf4j
public class CouponController {

    private final CouponService couponService;
    @Autowired
    public CouponController(CouponService couponService){
        this.couponService=couponService;
    }

    /**
     * 高并发秒杀任务
     * @param couponId
     * @param userId
     * @return
     */
    @PostMapping("/grab")
    @SentinelResource(value = "/coupon/grab", blockHandler = "handleGrabBlock")
    public Result<String> grabCoupon(@RequestParam Long couponId,
                                     @RequestHeader("UserId") Long userId) {
        // 1. 参数校验、基础防刷拦截等...

        // 2. 调用秒杀服务
        return couponService.seckillCoupon(couponId, userId);
    }

    /**
     * Sentinel 限流或熔断降级后的兜底方法
     * 注意：方法参数列表必须与原方法完全一致，且最后要加一个 BlockException 参数
     */
    public Result<String> handleGrabBlock(Long couponId, Long userId, BlockException ex) {
        // 打印降级日志
        log.warn("触发Sentinel流控/熔断保护！couponId: {}, userId: {}, 异常原因: {}", couponId, userId, ex.getClass().getSimpleName());

        // 平滑降级返回：不让前端报错，而是温柔地提示用户
        return Result.error(429, "抢购火爆，排队人数过多，请稍后再试！");
    }
}
