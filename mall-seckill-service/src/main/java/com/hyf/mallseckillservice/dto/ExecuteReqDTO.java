package com.hyf.mallseckillservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 秒杀执行请求参数。
 *
 * <p>由用户入口提交，包含秒杀商品项、购买数量和收货地址。</p>
 */
@Data
public class ExecuteReqDTO {

    @NotNull(message = "秒杀商品项不能为空")
    private Long seckillItemId;

    @Min(value = 1, message = "购买数量不能小于1")
    @Max(value = 99, message = "购买数量不能大于99")
    private Integer quantity = 1;

    @NotNull(message = "收货地址不能为空")
    private Long addressId;
}
