package com.hyf.mallorderservice.api;

import lombok.Data;

@Data
public class SeckillCancelDTO {

    private Long activityId;
    private Long seckillItemId;
    private Long userId;
    private Integer quantity;
}
