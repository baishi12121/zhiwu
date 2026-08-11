package com.hyf.malladminservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 秒杀商品项保存请求（加入秒杀 / 修改共用）。
 *
 * @author hyf
 */
@Data
public class SeckillItemSaveRequest {

    @NotNull(message = "spuId 不能为空")
    private Long spuId;

    @NotNull(message = "skuId 不能为空")
    private Long skuId;

    @NotNull(message = "秒杀价不能为空")
    @DecimalMin(value = "0.01", message = "秒杀价必须大于 0")
    private BigDecimal seckillPrice;

    @NotNull(message = "秒杀库存不能为空")
    @Min(value = 0, message = "秒杀库存不能小于 0")
    private Integer seckillStock;

    @Min(value = 1, message = "限购数量至少为 1")
    private Integer limitPerUser;

    private Integer sortOrder;

    /** 0 下架 1 上架 */
    private Integer status;
}
