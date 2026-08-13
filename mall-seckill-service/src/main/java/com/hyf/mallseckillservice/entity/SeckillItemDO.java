package com.hyf.mallseckillservice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hyf.mallcommon.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 秒杀商品项实体。
 *
 * <p>承载 SKU、秒杀价、活动库存和限购配置，是 Redis 库存预热与数据库扣减的核心表。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("seckill_item")
public class SeckillItemDO extends BaseEntity {

    private Long activityId;
    private Long spuId;
    private Long skuId;
    private BigDecimal seckillPrice;
    private Integer seckillStock;
    private Integer limitPerUser;
    private Integer sortOrder;
    private Integer status;
}
