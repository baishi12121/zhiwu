package com.hyf.mallseckillservice.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 秒杀商品 Redis 元数据快照。
 *
 * <p>预热后缓存到 Redis，入口链路优先读取它，减少数据库查询压力。</p>
 */
@Data
public class SeckillItemMetaDTO {

    private Long activityId;
    private Long seckillItemId;
    private Long spuId;
    private Long skuId;
    private BigDecimal seckillPrice;
    private BigDecimal price;
    private Integer limitPerUser;
}
