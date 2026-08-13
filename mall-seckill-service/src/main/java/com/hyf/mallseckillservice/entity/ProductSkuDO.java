package com.hyf.mallseckillservice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hyf.mallcommon.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 商品 SKU 简化实体。
 *
 * <p>秒杀链路读取 SKU 原价、图片等展示快照，避免订单明细缺失基础商品信息。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_sku")
public class ProductSkuDO extends BaseEntity {

    private Long productId;
    private BigDecimal price;
    private String picture;
}
