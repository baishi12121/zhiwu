package com.hyf.mallcouponservice.rabbitmq;

import com.hyf.mallcouponservice.dto.SeckillMessageDto;
import com.hyf.mallcouponservice.entity.UserCoupon;
import com.hyf.mallcouponservice.mapper.CouponMapper;
import com.hyf.mallcouponservice.mapper.UserCouponMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


/**
 * 处理消息队列里面的消息
 * 真正落实到数据库中
 */
@Component
@Slf4j
public class CouponMessageListener {
    @Autowired
    private CouponMapper couponMapper;
    @Autowired
    private UserCouponMapper userCouponMapper;

    // 监听消息队列

    @RabbitListener(queues = "coupon.seckill.queue")
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(SeckillMessageDto message) {
        //获取用户ID,优惠卷ID
        Long couponId = message.getCouponId();
        Long userId = message.getUserId();
        try {
            // 1. 扣库存
            int rows = couponMapper.deductStock(couponId);
            if (rows <= 0) {
                log.warn("DB库存扣减失败，可能已售罄。couponId:{}", couponId);
                return; // 库存不足，直接丢弃消息
            }

            // 2. 写记录
            UserCoupon uc = new UserCoupon();
            uc.setUserId(userId);
            uc.setCouponId(couponId);
            uc.setStatus(0); // 初始状态为 0-未使用
            userCouponMapper.insert(uc);

            log.info("用户 {} 领券 {} 落库成功", userId, couponId);

        } catch (DuplicateKeyException e) {
            // 唯一索引防重：如果因为网络原因MQ重复投递，这里会拦截并直接放行
            log.warn("用户 {} 重复落库券 {}，触发唯一索引拦截", userId, couponId);
        } catch (Exception e) {
            log.error("异步落库发生未知异常，准备触发MQ重试机制", e);
            throw e; // 抛出异常，让 RocketMQ/RabbitMQ 重新投递该消息
        }
    }



}
