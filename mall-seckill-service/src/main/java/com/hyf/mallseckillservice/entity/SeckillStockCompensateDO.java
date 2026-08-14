package com.hyf.mallseckillservice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hyf.mallcommon.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 秒杀库存补偿流水。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("seckill_stock_compensate")
public class SeckillStockCompensateDO extends BaseEntity {

    private String messageId;
    private Long activityId;
    private Long seckillItemId;
    private Long userId;
    private Integer quantity;
    private Integer compensateType;
    private Integer status;
}
