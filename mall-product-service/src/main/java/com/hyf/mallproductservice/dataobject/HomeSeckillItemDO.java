package com.hyf.mallproductservice.dataobject;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class HomeSeckillItemDO {

    private Long id;
    private Long activityId;
    private String activityName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long spuId;
    private Long skuId;
    private String spuName;
    private String skuCode;
    private String picture;
    private BigDecimal originalPrice;
    private BigDecimal seckillPrice;
    private Integer seckillStock;
    private Integer limitPerUser;
}
