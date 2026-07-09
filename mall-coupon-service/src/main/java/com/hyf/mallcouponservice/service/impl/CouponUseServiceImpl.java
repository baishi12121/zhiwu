package com.hyf.mallcouponservice.service.impl;


import com.hyf.mallcouponservice.mapper.UserCouponMapper;
import com.hyf.mallcouponservice.service.CouponUseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
@Slf4j
@Service
public class CouponUseServiceImpl implements CouponUseService {


    @Autowired
    private UserCouponMapper userCouponMapper;
    @Override
    public void useCoupon(Long userId, Long couponId) {
        // 调用 MyBatis Mapper 执行精准更新
        int rows = userCouponMapper.useCoupon(userId, couponId);
        if (rows == 0) {
            // 更新失败有几种可能：
            // 1. 该用户并没有这张券
            // 2. 券的状态不是 0 (已经被用过了)
            log.error("优惠券核销失败！userId:{}, couponId:{}", userId, couponId);
            throw new RuntimeException("优惠券核销失败或状态异常");
        }

        log.info("用户 {} 成功核销优惠券 {}", userId, couponId);
    }
}
