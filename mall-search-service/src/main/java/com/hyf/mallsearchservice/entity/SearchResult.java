package com.hyf.mallsearchservice.entity;

import com.hyf.mallcommon.core.page.PageResult;
import lombok.Data;

/**
 * 搜索结果 = 分页商品 + 侧边栏聚合.
 *
 * <p>对齐电商标准:一次请求同时返回商品列表与 facets,
 * 前端构建侧边栏后翻页只刷新商品(withFacets=false → facets 为 null)。
 *
 * @author hyf
 */
@Data
public class SearchResult {

    /** 分页商品(含高亮) */
    private PageResult<ProductDocVO> page;
    /** 侧边栏聚合,withFacets=false 时为 null */
    private Facets facets;
}
