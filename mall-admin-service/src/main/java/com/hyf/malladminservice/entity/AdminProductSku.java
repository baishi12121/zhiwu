package com.hyf.malladminservice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hyf.mallcommon.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 商品 SKU 实体，映射 {@code product_sku} 表。
 *
 * @author hyf
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_sku")
public class AdminProductSku extends BaseEntity {

    private Long productId;
    private String skuCode;
    private BigDecimal price;
    private BigDecimal oldPrice;
    private Integer inventory;
    private String picture;
    /** 0 下架 1 上架 */
    private Integer status;
}
