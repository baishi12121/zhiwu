package com.hyf.malluserservice.dto.request;

import lombok.Data;

/**
 * 购物车选中状态请求。
 *
 * <p>用于单品选中 {@code PUT /cart/{skuId}/selected} 与全选 {@code PUT /cart/selected}。
 *
 * @author hyf
 */
@Data
public class CartSelectedRequest {

    /** 是否选中 */
    private Boolean selected;
}
