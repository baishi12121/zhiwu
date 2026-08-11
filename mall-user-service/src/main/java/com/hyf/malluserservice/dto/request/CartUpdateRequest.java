package com.hyf.malluserservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 修改购物车数量请求。
 *
 * <p>对应 {@code PUT /cart/{skuId}}，仅修改数量。
 *
 * @author hyf
 */
@Data
public class CartUpdateRequest {

    /** 数量，最小 1 */
    @NotNull(message = "count 不能为空")
    @Min(value = 1, message = "数量至少为 1")
    private Integer count;
}
