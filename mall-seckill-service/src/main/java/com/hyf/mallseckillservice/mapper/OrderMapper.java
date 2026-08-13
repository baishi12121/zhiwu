package com.hyf.mallseckillservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.mallseckillservice.entity.OrderDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单表 Mapper。
 *
 * <p>秒杀服务只使用订单表中和秒杀结果查询、取消回补相关的最小 SQL 集合。</p>
 */
@Mapper
public interface OrderMapper extends BaseMapper<OrderDO> {

    /**
     * 按用户、活动、秒杀商品定位唯一秒杀订单。
     */
    OrderDO selectByUserActivityItem(@Param("userId") Long userId,
                                     @Param("activityId") Long activityId,
                                     @Param("seckillItemId") Long seckillItemId);

    OrderDO selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 只取消待支付订单，避免已支付订单被超时消息误取消。
     */
    int cancelPendingOrder(@Param("id") Long id,
                           @Param("cancelReason") String cancelReason,
                           @Param("cancelledAt") LocalDateTime cancelledAt);

    /**
     * 定时兜底扫描已过支付截止时间的秒杀订单。
     */
    List<OrderDO> selectExpiredPendingSeckillOrders(@Param("now") LocalDateTime now,
                                                    @Param("limit") int limit);
}
