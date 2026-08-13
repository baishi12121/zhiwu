package com.hyf.mallseckillservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 秒杀建单成功后的内部结果。
 *
 * <p>由消费者建单流程返回，主要用于日志和后续扩展。</p>
 */
@Data
@AllArgsConstructor
public class SeckillOrderResultDTO {

    private Long orderId;
    private String orderNo;
}
