package com.hyf.mallseckillservice.dto;

import lombok.Data;

/**
 * 秒杀结果查询响应。
 *
 * <p>对外暴露 pending、failed、ordered 等状态；建单成功时附带订单信息。</p>
 */
@Data
public class SeckillResultDTO {

    private String status;
    private Long orderId;
    private String orderNo;
}
