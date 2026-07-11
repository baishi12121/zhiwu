package com.hyf.mallproductservice.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 商品图片 DO — 对应 {@code product_image} 表.
 *
 * @author hyf
 */
@Data
@TableName("product_image")
public class ProductImageDO {

    private Long id;
    private Long productId;
    /** 1=主图, 2=详情图 */
    private Integer imageType;
    private String imageUrl;
    private Integer sortOrder;
}
