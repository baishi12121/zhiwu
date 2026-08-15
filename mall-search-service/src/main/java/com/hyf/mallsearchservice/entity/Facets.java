package com.hyf.mallsearchservice.entity;

import lombok.Data;

import java.util.List;

/**
 * 侧边栏聚合结果(品牌/分类/价格统计).
 *
 * <p>第一次搜索(withFacets=true)返回完整 facets;翻页时前端传 withFacets=false,
 * 后端跳过聚合计算,facets 为 null(响应体不含 facets 字段)。
 *
 * @author hyf
 */
@Data
public class Facets {

    /** 品牌聚合(terms brandId + sub terms brandName) */
    private List<FacetItem> brands;
    /** 分类聚合(terms categoryId + sub terms categoryName) */
    private List<FacetItem> categories;
    /** 价格统计(stats price:min/max/avg) */
    private PriceStats priceStats;
}
