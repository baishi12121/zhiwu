package com.hyf.mallseckillservice.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀下单 MQ 消息体。
 *
 * <p>携带建单所需的用户、商品、价格快照和地址信息，保证消费者不再依赖入口请求上下文。</p>
 */
@Data
public class SeckillOrderMessageDTO {

    private String messageId;
    private Long userId;
    private Long activityId;
    private Long seckillItemId;
    private Long spuId;
    private Long skuId;
    private BigDecimal seckillPrice;
    private BigDecimal price;
    private Integer quantity;
    private Long addressId;
    private LocalDateTime createTime;
}
