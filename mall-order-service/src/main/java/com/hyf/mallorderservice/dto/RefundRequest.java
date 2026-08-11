package com.hyf.mallorderservice.dto;

import lombok.Data;

/**
 * 退款请求 DTO。
 *
 * @author hyf
 */
@Data
public class RefundRequest {

    /** 订单 ID */
    private Long orderId;

    /** 退款原因（可空，默认"用户申请退款"） */
    private String reason;
}
