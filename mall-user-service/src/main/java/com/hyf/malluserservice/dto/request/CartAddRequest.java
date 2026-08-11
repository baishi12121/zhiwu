package com.hyf.malluserservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 加入购物车请求。
 *
 * <p>字段对齐前端 {@code postMemberCartAPI} 的请求体 {@code {skuId, count}}。
 *
 * @author hyf
 */
@Data
public class CartAddRequest {

    /** SKU ID */
    @NotNull(message = "skuId 不能为空")
    private Long skuId;

    /** 数量，最小 1 */
    @NotNull(message = "count 不能为空")
    @Min(value = 1, message = "数量至少为 1")
    private Integer count;
}
