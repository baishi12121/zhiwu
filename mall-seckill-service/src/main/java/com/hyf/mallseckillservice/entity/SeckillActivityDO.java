package com.hyf.mallseckillservice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hyf.mallcommon.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 秒杀活动实体。
 *
 * <p>定义活动启停时间和启用状态，是入口校验与预热扫描的活动维度。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("seckill_activity")
public class SeckillActivityDO extends BaseEntity {

    private String name;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer enabled;
    private String remark;
}
