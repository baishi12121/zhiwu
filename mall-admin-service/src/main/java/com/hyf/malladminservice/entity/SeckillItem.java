package com.hyf.malladminservice.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hyf.mallcommon.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 秒杀活动商品项实体，映射 {@code seckill_item} 表。
 *
 * <p>{@code seckillStock} 是独立于 SKU 原库存的秒杀配额（运营从 SKU 总库存中切一部分出来供秒杀），
 * 下单时由秒杀逻辑原子扣减。{@code spuName}/{@code skuCode} 等是详情接口 join 出来的展示字段。
 *
 * @author hyf
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("seckill_item")
public class SeckillItem extends BaseEntity {

    private Long activityId;
    private Long spuId;
    private Long skuId;
    private BigDecimal seckillPrice;
    private Integer seckillStock;
    private Integer limitPerUser;
    private Integer sortOrder;
    /** 0 下架 1 上架 */
    private Integer status;

    // ---- 展示字段（join 出来） ----

    /** 商品名（join product.name） */
    @TableField(exist = false)
    private String spuName;

    /** SKU 编码（join product_sku.sku_code） */
    @TableField(exist = false)
    private String skuCode;

    /** SKU 原价（join product_sku.price） */
    @TableField(exist = false)
    private BigDecimal originalPrice;
}
