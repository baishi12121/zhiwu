package com.hyf.mallorderservice.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体类，对应数据库 order 表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 订单号
     */
    private Long id;

    /**
     * 业务订单号（由 SQL 生成：yyyyMMddHHmmss + 6位userId）
     */
    private String orderNo;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 使用的优惠券ID（可为空）
     */
    private Long couponId;

    /**
     * 订单总金额
     */
    private BigDecimal totalAmount;

    /**
     * 实际支付金额
     */
    private BigDecimal realAmount;

    /**
     * 订单状态：0-未支付，1-已支付，2-已取消
     */
    private Integer status;

    /**
     * 商品ID
     */
    private Long productId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
