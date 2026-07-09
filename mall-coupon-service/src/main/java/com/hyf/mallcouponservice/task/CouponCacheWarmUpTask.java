package com.hyf.mallcouponservice.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyf.mallcouponservice.entity.Coupon;
import com.hyf.mallcouponservice.mapper.CouponMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 优惠券缓存预热定时任务：
 * 定期将 tb_coupon 有效数据加载到 Redis，保证缓存可用性
 */
@Slf4j
@Component
public class CouponCacheWarmUpTask {

    private static final String COUPON_INFO_PREFIX = "coupon:info:";
    private static final String COUPON_STOCK_PREFIX = "coupon:stock:";

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private CouponMapper couponMapper;
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 服务启动后立即执行一次预热
     */
    @PostConstruct
    public void init() {
        warmUp();
    }

    /**
     * 每5分钟执行一次缓存预热（fixedDelay 确保上一次结束后再计时）
     */
    @Scheduled(fixedDelay = 300_000)
    public void scheduledWarmUp() {
        warmUp();
    }

    private void warmUp() {
        log.info("开始优惠券缓存预热...");
        try {
            List<Coupon> coupons = couponMapper.selectActiveCoupons();
            if (coupons.isEmpty()) {
                log.info("无有效优惠券需要预热");
                return;
            }
            for (Coupon coupon : coupons) {
                String infoKey = COUPON_INFO_PREFIX + coupon.getId();
                String stockKey = COUPON_STOCK_PREFIX + coupon.getId();

                // 写入优惠券详情 JSON，过期时间 1 小时兜底
                String json = objectMapper.writeValueAsString(coupon);
                redisTemplate.opsForValue().set(infoKey, json, 1, TimeUnit.HOURS);

                // 仅当库存 key 不存在时才写入，避免覆盖秒杀进行中的实时库存
                redisTemplate.opsForValue().setIfAbsent(stockKey, String.valueOf(coupon.getRemainStock()));
            }
            log.info("优惠券缓存预热完成，共加载 {} 条有效优惠券", coupons.size());
        } catch (Exception e) {
            log.error("优惠券缓存预热失败", e);
        }
    }
}
