package com.hyf.malluserservice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hyf.mallcommon.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 购物车实体，映射 {@code user_cart} 表。
 *
 * <p>按 SKU 维度存储，{@code (user_id, sku_id)} 有唯一键约束，同一用户同一 SKU 只有一行。
 *
 * @author hyf
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_cart")
public class UserCart extends BaseEntity {

    /** 用户 ID */
    private Long userId;

    /** SKU ID（购物车行主键维度） */
    private Long skuId;

    /** 数量 */
    private Integer count;

    /** 是否选中：1 选中，0 未选中 */
    private Integer selected;

    /** 加入时价格快照（取 product_sku.price） */
    private BigDecimal price;
}
