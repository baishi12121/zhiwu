package com.hyf.mallorderservice.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hyf.mallcommon.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 商品规格 DO — 对应 {@code product_sku} 表（SKU）。
 *
 * <p>订单服务用于查询 SKU 价格/库存、扣减库存。
 *
 * @author hyf
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_sku")
public class ProductSkuDO extends BaseEntity {

    private Long productId;
    private String skuCode;
    private BigDecimal price;
    private BigDecimal oldPrice;
    /** SKU 库存（扣减字段） */
    private Integer inventory;
    private String picture;
    private Integer status;
}
