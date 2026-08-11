package com.hyf.mallproductservice.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 商品规格 SKU 值对象（对应 {@code product_sku} 表）
 *
 * @author hyf
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductSku {

    /** SKU ID */
    private Long id;

    /** 商品 ID */
    private Long productId;

    /** SKU 编码 */
    private String skuCode;

    /** 价格 */
    private BigDecimal price;

    /** 原价 */
    private BigDecimal oldPrice;

    /** SKU 库存 */
    private Integer inventory;

    /** SKU 图片 */
    private String picture;

    /** 状态：0 下架，1 上架 */
    private Integer status;
}
