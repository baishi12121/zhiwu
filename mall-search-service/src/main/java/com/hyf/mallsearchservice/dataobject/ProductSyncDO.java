package com.hyf.mallsearchservice.dataobject;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品同步 DO — 全量/增量同步时一次性查出 product + brand + category 的关联结果.
 *
 * <p>对应 SQL(见 {@code SyncMapper#selectPage}):
 * <pre>SELECT p.*, b.name AS brand_name, c.name AS category_name
 * FROM product p LEFT JOIN brand b ON p.brand_id=b.id LEFT JOIN category c ON p.category_id=c.id</pre>
 *
 * <p>依赖 {@code mybatis.configuration.map-underscore-to-camel-case: true},
 * 下划线列名自动映射到 camelCase 字段。
 *
 * @author hyf
 */
@Data
public class ProductSyncDO {

    private Long id;
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
    private Integer isPreSale;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** JOIN brand.name 冗余 */
    private String brandName;
    /** JOIN category.name 冗余 */
    private String categoryName;
}
