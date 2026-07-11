package com.hyf.mallproductservice.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 商品详情属性 DO — 对应 {@code product_property} 表.
 *
 * @author hyf
 */
@Data
@TableName("product_property")
public class ProductPropertyDO {

    private Long id;
    private Long productId;
    private String name;
    private String value;
    private Integer sortOrder;
}
