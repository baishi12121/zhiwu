package com.hyf.mallorderservice.dto;

import lombok.Data;

/**
 * 微信下单请求 DTO。
 *
 * @author hyf
 */
@Data
public class PayCreateRequest {

    /** 订单 ID */
    private Long orderId;

    /** 用户 openid（微信模式必填，Mock 模式可空） */
    private String openid;
}
