package com.hyf.malladminservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * SKU 保存请求（新建 / 修改共用）。
 *
 * @author hyf
 */
@Data
public class SkuSaveRequest {

    private Long productId;

    private String skuCode;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.01", message = "价格必须大于 0")
    private BigDecimal price;

    private BigDecimal oldPrice;

    @Min(value = 0, message = "库存不能小于 0")
    private Integer inventory;

    private String picture;

    /** 0 下架 1 上架 */
    private Integer status;
}
