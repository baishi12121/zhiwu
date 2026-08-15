package com.hyf.mallsearchservice.service;

import com.hyf.mallsearchservice.entity.RequestParams;
import com.hyf.mallsearchservice.entity.SearchResult;

import java.util.List;

public interface ProductService {

    /**
     * 商品搜索(分页 + 高亮 + 过滤 + 排序 + 聚合).
     *
     * <p>withFacets=true 时返回侧边栏聚合(品牌/分类/价格统计),false 时 facets 为 null。
     *
     * @param params 搜索参数
     * @return 搜索结果(分页商品 + 可选 facets)
     */
    SearchResult search(RequestParams params);

    /**
     * 搜索自动补全(基于 ES completion suggester).
     *
     * <p>用户输入前缀,从 product 索引的 suggestion(completion 类型) 字段
     * 返回最多 {@code limit} 条去重后的提示词。
     *
     * @param prefix 用户输入的前缀(空串/空白返回空列表,不走 ES)
     * @param limit  返回条数上限(<=0 走默认 10,>20 截断到 20)
     * @return 提示词列表(已去重,按 ES suggest score 降序)
     */
    List<String> suggest(String prefix, int limit);
}
