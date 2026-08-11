package com.hyf.malladminservice.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hyf.mallcommon.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 秒杀活动主表实体，映射 {@code seckill_activity} 表。
 *
 * @author hyf
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("seckill_activity")
public class SeckillActivity extends BaseEntity {

    /** 活动名称 */
    private String name;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 0 禁用 1 启用 */
    private Integer enabled;

    /** 备注 */
    private String remark;

    @TableField(exist = false)
    private Long itemCount;
}
