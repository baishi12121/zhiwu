package com.hyf.mallorderservice.domain.model.valueobject;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付信息（值对象）
 *
 * <p>payType: 1 在线支付 / 2 货到付款
 * payChannel: 1 支付宝 / 2 微信
 *
 * @author hyf
 */
@Data
public class Payment {

    private Integer payType;
    private Integer payChannel;
    private LocalDateTime paidAt;
}
