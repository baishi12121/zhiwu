package com.hyf.mallproductservice.entity;

import com.hyf.mallcommon.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 商品实体（domain entity，对应 {@code product} 表）
 *
 * <p>实体不含业务逻辑，业务行为放在 {@code ProductAggregate} 聚合根。
 *
 * @author hyf
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Product extends BaseEntity {

    /** 分类 ID */
    private Long categoryId;

    /** 品牌 ID */
    private Long brandId;

    /** SPU 编码 */
    private String spuCode;

    /** 商品名称 */
    private String name;

    /** 卖点/副标题 */
    private String subtitle;

    /** 商品描述 */
    private String description;

    /** 当前价 */
    private BigDecimal price;

    /** 原价 */
    private BigDecimal oldPrice;

    /** 折扣（0.85 = 8.5折） */
    private BigDecimal discount;

    /** SPU 总库存（汇总 SKU） */
    private Integer inventory;

    /** 销量 */
    private Integer salesCount;

    /** 评价数 */
    private Integer commentCount;

    /** 收藏数 */
    private Integer collectCount;

    /** 主图视频集合（JSON） */
    private String mainVideos;

    /** 视频比例：1=1:1或16:9, 2=3:4 */
    private Integer videoScale;

    /** 是否预售 */
    private Integer isPreSale;

    /** 状态：0 下架，1 上架 */
    private Integer status;
}
