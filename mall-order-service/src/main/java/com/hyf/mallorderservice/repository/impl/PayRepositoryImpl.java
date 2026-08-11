package com.hyf.mallorderservice.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hyf.mallorderservice.repository.PayRepository;
import com.hyf.mallorderservice.dataobject.PayRecordDO;
import com.hyf.mallorderservice.mapper.PayRecordMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付仓储实现。
 *
 * @author hyf
 */
@Repository
public class PayRepositoryImpl implements PayRepository {

    private final PayRecordMapper payRecordMapper;

    public PayRepositoryImpl(PayRecordMapper payRecordMapper) {
        this.payRecordMapper = payRecordMapper;
    }

    @Override
    public PayRecordDO findByOrderId(Long orderId) {
        return payRecordMapper.selectOne(
                new LambdaQueryWrapper<PayRecordDO>()
                        .eq(PayRecordDO::getOrderId, orderId)
                        .last("LIMIT 1"));
    }

    @Override
    public PayRecordDO findByOrderNo(String orderNo) {
        return payRecordMapper.selectOne(
                new LambdaQueryWrapper<PayRecordDO>()
                        .eq(PayRecordDO::getOrderNo, orderNo)
                        .last("LIMIT 1"));
    }

    @Override
    public PayRecordDO findByTransactionId(String transactionId) {
        return payRecordMapper.selectOne(
                new LambdaQueryWrapper<PayRecordDO>()
                        .eq(PayRecordDO::getTransactionId, transactionId)
                        .last("LIMIT 1"));
    }

    @Override
    public int insert(PayRecordDO record) {
        return payRecordMapper.insert(record);
    }

    @Override
    public int update(PayRecordDO record) {
        return payRecordMapper.updateById(record);
    }

    @Override
    public int updatePayStatus(Long id, int payStatus, String transactionId, LocalDateTime paidAt) {
        return payRecordMapper.update(null,
                new LambdaUpdateWrapper<PayRecordDO>()
                        .eq(PayRecordDO::getId, id)
                        .set(PayRecordDO::getPayStatus, payStatus)
                        .set(transactionId != null, PayRecordDO::getTransactionId, transactionId)
                        .set(paidAt != null, PayRecordDO::getPaidAt, paidAt));
    }

    @Override
    public int updateRefundStatus(Long id, int refundStatus, String refundNo, String refundId,
                                  BigDecimal refundAmount, LocalDateTime refundedAt) {
        return payRecordMapper.update(null,
                new LambdaUpdateWrapper<PayRecordDO>()
                        .eq(PayRecordDO::getId, id)
                        .set(PayRecordDO::getRefundStatus, refundStatus)
                        .set(refundNo != null, PayRecordDO::getRefundNo, refundNo)
                        .set(refundId != null, PayRecordDO::getRefundId, refundId)
                        .set(refundAmount != null, PayRecordDO::getRefundAmount, refundAmount)
                        .set(refundedAt != null, PayRecordDO::getRefundedAt, refundedAt));
    }
}
