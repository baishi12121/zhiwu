package com.hyf.mallseckillservice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hyf.mallcommon.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 本地消息表实体。
 *
 * <p>记录秒杀请求从 Redis 扣减、MQ 投递到最终建单完成的状态，用于查询结果和失败补偿。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mq_message")
public class MqMessageDO extends BaseEntity {

    private String messageId;
    private Long userId;
    private Long activityId;
    private Long seckillItemId;
    private Long spuId;
    private Long skuId;
    private Integer quantity;
    private Integer status;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
}
