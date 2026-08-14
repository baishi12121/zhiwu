package com.hyf.malladminservice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hyf.mallcommon.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订单状态流转日志。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_status_log")
public class OrderStatusLog extends BaseEntity {

    private Long orderId;
    private Integer fromState;
    private Integer toState;
    private String operator;
    private String remark;
}
