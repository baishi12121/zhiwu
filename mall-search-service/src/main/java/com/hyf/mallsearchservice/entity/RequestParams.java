package com.hyf.mallsearchservice.entity;

import lombok.Data;

import java.util.List;

/**
 * 搜索请求参数.
 *
 * <p>支持关键词 + 分页 + 排序 + 多维过滤(分类/品牌/价格/折扣/库存/预售).
 *
 * @author hyf
 */
@Data
public class RequestParams {

    /** 关键词,空则走全量+过滤(分类页/品牌页场景) */
    private String key;

    /** 页码,从 1 开始 */
    private Integer page = 1;
    /** 每页条数,Service 端截断到 50 */
    private Integer pageSize = 10;

    /** 排序:default/price_asc/price_desc/sales_desc/newest */
    private String sortBy;

    // ---- 过滤项 ----
    private List<Long> categoryIds;
    private List<Long> brandIds;
    /** 价格区间下限 */
    private Double minPrice;
    /** 价格区间上限 */
    private Double maxPrice;
    /** 折扣上限(如 0.7=7折及以下) */
    private Double minDiscount;
    /** 仅看有货(inventory>0) */
    private Boolean onlyInStock = false;
    /** 仅预售 */
    private Boolean onlyPreSale = false;
    /** 是否返回侧边栏聚合(翻页时前端传 false,第 4 步用) */
    private Boolean withFacets = true;
}
