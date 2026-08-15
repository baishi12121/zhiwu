package com.hyf.mallsearchservice.dataobject;

import lombok.Data;

/**
 * 商品主图同步 DO — 对应 {@code product_image} 表(image_type=1 主图).
 *
 * @author hyf
 */
@Data
public class ProductImageSyncDO {

    private Long productId;
    private String imageUrl;
    private Integer sortOrder;
}
