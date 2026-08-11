package com.hyf.mallorderservice.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hyf.mallcommon.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付记录 DO — 对应 {@code pay_record} 表。
 *
 * @author hyf
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pay_record")
public class PayRecordDO extends BaseEntity {

    /** 订单 ID */
    private Long orderId;
    /** 业务订单号 */
    private String orderNo;
    /** 用户 ID */
    private Long userId;
    /** 微信支付订单号 */
    private String transactionId;
    /** 预支付交易会话 ID */
    private String prepayId;
    /** 支付金额（元） */
    private BigDecimal payAmount;
    /** 支付状态：0待支付 1已支付 2已关闭 3支付失败 */
    private Integer payStatus;
    /** 退款单号 */
    private String refundNo;
    /** 微信退款单号 */
    private String refundId;
    /** 退款金额（元） */
    private BigDecimal refundAmount;
    /** 退款状态：0退款中 1已退款 2退款异常 */
    private Integer refundStatus;
    /** 退款原因 */
    private String refundReason;
    /** 退款完成时间 */
    private LocalDateTime refundedAt;
    /** 支付完成时间 */
    private LocalDateTime paidAt;
}
