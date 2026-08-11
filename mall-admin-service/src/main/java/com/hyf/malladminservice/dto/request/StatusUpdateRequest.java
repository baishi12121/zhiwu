package com.hyf.malladminservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 商品上下架 / SKU 上下架请求。
 *
 * @author hyf
 */
@Data
public class StatusUpdateRequest {

    /** 0 下架 1 上架 */
    @NotNull(message = "status 不能为空")
    private Integer status;
}
