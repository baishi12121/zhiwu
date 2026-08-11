package com.hyf.mallorderservice.repository;

import com.hyf.mallorderservice.dataobject.PayRecordDO;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付仓储接口 — domain 层定义，infrastructure 层实现。
 *
 * @author hyf
 */
public interface PayRepository {

    /** 根据订单 ID 查询支付记录 */
    PayRecordDO findByOrderId(Long orderId);

    /** 根据业务订单号查询支付记录 */
    PayRecordDO findByOrderNo(String orderNo);

    /** 根据微信支付订单号查询支付记录 */
    PayRecordDO findByTransactionId(String transactionId);

    /** 插入支付记录，回写 id */
    int insert(PayRecordDO record);

    /** 更新支付记录（全字段） */
    int update(PayRecordDO record);

    /** 更新支付状态（回调入账时使用） */
    int updatePayStatus(Long id, int payStatus, String transactionId, LocalDateTime paidAt);

    /** 更新退款状态 */
    int updateRefundStatus(Long id, int refundStatus, String refundNo, String refundId,
                           BigDecimal refundAmount, LocalDateTime refundedAt);
}
