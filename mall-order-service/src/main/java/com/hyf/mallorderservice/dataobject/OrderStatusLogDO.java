package com.hyf.mallorderservice.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hyf.mallcommon.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订单状态流转日志 DO — 对应 {@code order_status_log} 表。
 *
 * @author hyf
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_status_log")
public class OrderStatusLogDO extends BaseEntity {

    /** 订单 ID */
    private Long orderId;
    /** 原状态 */
    private Integer fromState;
    /** 目标状态 */
    private Integer toState;
    /** 操作者：USER / SYSTEM / ADMIN */
    private String operator;
    /** 备注 */
    private String remark;
}
