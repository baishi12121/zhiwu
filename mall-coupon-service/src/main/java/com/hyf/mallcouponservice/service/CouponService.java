package com.hyf.mallcouponservice.service;

import com.hyf.mallcouponservice.common.Result;

public interface CouponService {
    /**
     * 秒杀业务
     * @param couponId
     * @param userId
     * @return
     */
    Result<String> seckillCoupon(Long couponId, Long userId);
}
