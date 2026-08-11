package com.hyf.mallproductservice.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hyf.mallcommon.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 商品主表 DO — 对应 {@code product} 表（SPU）.
 *
 * @author hyf
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product")
public class ProductDO extends BaseEntity {

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
    private Integer status;
}
