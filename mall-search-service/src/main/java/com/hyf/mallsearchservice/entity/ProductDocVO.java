package com.hyf.mallsearchservice.entity;

import lombok.Data;

import java.util.List;

/**
 * 搜索结果商品 VO(返回前端的精简结构).
 *
 * <p>从 ES {@link com.hyf.mallsearchservice.document.ProductDoc} 拷贝同名字段,
 * {@code name}/{@code subtitle} 用 highlight 结果覆盖(含 {@code <em>} 标签)。
 *
 * @author hyf
 */
@Data
public class ProductDocVO {

    private Long id;
    /** 高亮后的商品名(含 <em> 标签) */
    private String name;
    /** 高亮后的副标题 */
    private String subtitle;
    private Double price;
    private Double oldPrice;
    private Double discount;
    private String mainImage;
    private List<String> images;
    private Long brandId;
    private String brandName;
    private Long categoryId;
    private String categoryName;
    private Integer salesCount;
    private Integer commentCount;
    private Integer isPreSale;
    private Integer status;
}
