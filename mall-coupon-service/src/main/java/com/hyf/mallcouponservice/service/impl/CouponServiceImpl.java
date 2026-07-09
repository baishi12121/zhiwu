package com.hyf.mallcouponservice.service.impl;

import com.hyf.mallcouponservice.common.Result;
import com.hyf.mallcouponservice.dto.SeckillMessageDto;
import com.hyf.mallcouponservice.rabbitmq.MqProducer;
import com.hyf.mallcouponservice.service.CouponService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.util.Arrays;

@Service
@Slf4j
public class CouponServiceImpl implements CouponService {

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private DefaultRedisScript<Long> seckillScript;

    @Autowired
    private MqProducer mqProducer;

    @Override
    public Result<String> seckillCoupon(Long couponId, Long userId) {
        //构造 Redis Key
        String stockKey = "coupon:stock:" + couponId;
        String userSetKey = "coupon:users:" + couponId;
        //1.执行Lua脚本确保原子一致性
        Long result = redisTemplate.execute(
                seckillScript,
                Arrays.asList(stockKey, userSetKey),//将两个 Redis 键（key）封装成 List<String>，作为脚本的 KEYS 数组 传入
                String.valueOf(userId)
        );

        //2.解释Lua的返回值
        if (result == null) {
            return Result.error("系统繁忙，请重试");
        }
        // result: 0-库存不足, 1-重复抢券, 2-抢券成功
        int resultCode = result.intValue();
        switch (resultCode) {
            case 0:
                return Result.error("优惠券已抢完");
            case 1:
                return Result.error("您已经抢过该优惠券了");
            case 2:
                //3.抢购成功，发送异步消息进行数据库落地
                try {
                    SeckillMessageDto message = new SeckillMessageDto(couponId, userId);
                    mqProducer.send("COUPON_SECKILL_TOPIC", message);
                    return Result.success("抢券成功");
                } catch (Exception e) {
                    log.error("异步落库发生异常，准备重试", e);
                    throw e;
                }
            default:
                return Result.error("系统繁忙，请重试");
        }
    }
}
