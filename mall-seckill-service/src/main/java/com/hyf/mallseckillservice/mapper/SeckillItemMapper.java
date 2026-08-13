package com.hyf.mallseckillservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.mallseckillservice.entity.SeckillItemDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 秒杀商品 Mapper。
 *
 * <p>负责活动商品扫描、数据库库存扣减和取消后的数据库库存回补。</p>
 */
@Mapper
public interface SeckillItemMapper extends BaseMapper<SeckillItemDO> {

    List<SeckillItemDO> selectEnabledByActivityId(@Param("activityId") Long activityId);

    /**
     * 数据库最终库存扣减，SQL 内带库存充足条件，作为 Redis 预占后的最终防线。
     */
    int deductStock(@Param("id") Long id, @Param("quantity") int quantity);

    /**
     * 订单取消或消费失败后的数据库库存回补。
     */
    int restoreStock(@Param("id") Long id, @Param("quantity") int quantity);
}
