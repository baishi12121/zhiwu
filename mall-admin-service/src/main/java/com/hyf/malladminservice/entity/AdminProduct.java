package com.hyf.malladminservice.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hyf.mallcommon.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品 SPU 实体，映射 {@code product} 表。
 *
 * <p>管理后台详情接口会附带 SKU / 图片 / 属性集合，通过 {@link #skus} / {@link #images} /
 * {@link #properties} 字段承载（非 DB 列，用 {@code @TableField(exist = false)} 标记）。
 *
 * @author hyf
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product")
public class AdminProduct extends BaseEntity {

    private Long categoryId;
    private Long brandId;
    private String spuCode;
    private String name;
    private String subtitle;
    private String description;
    private BigDecimal price;
    private BigDecimal oldPrice;
    private BigDecimal discount;
    private Integer inventory;
    private Integer salesCount;
    private Integer commentCount;
    private Integer collectCount;
    private String mainVideos;
    private Integer videoScale;
    private Integer isPreSale;
    /** 0 下架 1 上架 */
    private Integer status;

    /** SKU 列表（详情接口填充） */
    @TableField(exist = false)
    private List<AdminProductSku> skus;

    /** 主图 + 详情图（详情接口填充） */
    @TableField(exist = false)
    private List<AdminProductImage> images;

    /** 详情属性（详情接口填充） */
    @TableField(exist = false)
    private List<AdminProductProperty> properties;
}
