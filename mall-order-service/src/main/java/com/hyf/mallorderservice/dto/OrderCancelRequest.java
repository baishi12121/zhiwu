package com.hyf.mallorderservice.dto;

import lombok.Data;

/**
 * 取消订单请求 DTO。
 *
 * @author hyf
 */
@Data
public class OrderCancelRequest {

    /** 取消原因 */
    private String cancelReason;
}
