package com.hyf.mallseckillservice.dto;

import lombok.Data;

/**
 * 秒杀库存回补请求参数。
 *
 * <p>order-service 取消秒杀订单时传入，seckill-service 会和订单事实做交叉校验。</p>
 */
@Data
public class StockCompensateDTO {

    private Long activityId;
    private Long seckillItemId;
    private Long userId;
    private Integer quantity;
}
