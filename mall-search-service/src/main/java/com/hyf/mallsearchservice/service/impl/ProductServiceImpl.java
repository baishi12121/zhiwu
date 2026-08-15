package com.hyf.mallsearchservice.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StatsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.CompletionSuggestOption;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.Suggestion;
import co.elastic.clients.json.JsonData;
import com.hyf.mallcommon.core.page.PageResult;
import com.hyf.mallsearchservice.document.ProductDoc;
import com.hyf.mallsearchservice.entity.FacetItem;
import com.hyf.mallsearchservice.entity.Facets;
import com.hyf.mallsearchservice.entity.PriceStats;
import com.hyf.mallsearchservice.entity.ProductDocVO;
import com.hyf.mallsearchservice.entity.RequestParams;
import com.hyf.mallsearchservice.entity.SearchResult;
import com.hyf.mallsearchservice.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 商品搜索实现 — bool query + filter + 高亮 + 分页 + 排序 + facets 聚合.
 *
 * <p>查询结构:
 * <pre>
 * bool:
 *   must:   [ multi_match(key, name^3, subtitle^2, description) ]   # key 非空时
 *   filter: [ term(status=1),                                  # 永远只搜上架
 *             terms(categoryId),  terms(brandId),               # 选了才加
 *             range(price, gte/lte), range(discount, lte),
 *             range(inventory, gt 0),  term(isPreSale=1) ]      # 仅看有货/预售
 * aggs(withFacets=true 时):
 *   brands:     terms(brandId, size 20) + sub terms(brandName, size 1)
 *   categories: terms(categoryId, size 20) + sub terms(categoryName, size 1)
 *   priceStats: stats(price) → min/max/avg
 * </pre>
 *
 * @author hyf
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final int MAX_PAGE_SIZE = 50;
    /** ES 深分页上限(from + size ≤ 10000,超过用 search_after) */
    private static final int MAX_DEEP_PAGING = 10000;
    /** 聚合桶数上限 */
    private static final int FACET_SIZE = 20;
    /** 自动补全默认返回条数 */
    private static final int DEFAULT_SUGGEST_LIMIT = 10;
    /** 自动补全返回条数硬上限 */
    private static final int MAX_SUGGEST_LIMIT = 20;
    /** completion suggester 名称,响应里用它取结果 */
    private static final String SUGGEST_NAME = "product-suggest";

    private final ElasticsearchClient esClient;

    @Override
    public SearchResult search(RequestParams params) {
        int page = normalizePage(params.getPage());
        int size = normalizeSize(params.getPageSize());

        SearchResult result = new SearchResult();

        // 深分页保护
        int from = (page - 1) * size;
        if (from >= MAX_DEEP_PAGING) {
            log.warn("[搜索] 深分页超限 page={}, size={}, from={}", page, size, from);
            result.setPage(PageResult.empty(page, size));
            return result;
        }

        try {
            SearchRequest request = buildRequest(params, from, size);
            SearchResponse<ProductDoc> resp = esClient.search(request, ProductDoc.class);
            long total = resp.hits().total() == null ? 0 : resp.hits().total().value();
            List<ProductDocVO> items = parseHits(resp.hits().hits());
            result.setPage(PageResult.of(items, total, page, size));

            // 仅在首次请求返回聚合(翻页 withFacets=false 跳过,节省 ES 计算)
            if (Boolean.TRUE.equals(params.getWithFacets())) {
                result.setFacets(parseFacets(resp.aggregations()));
            }
            return result;
        } catch (Exception e) {
            log.error("[搜索] 查询失败: {}", e.getMessage(), e);
            result.setPage(PageResult.empty(page, size));
            return result;
        }
    }

    /**
     * 构造 SearchRequest:bool query + 高亮 + 分页 + 排序 + 可选聚合.
     */
    private SearchRequest buildRequest(RequestParams params, int from, int size) {
        return SearchRequest.of(s -> {
            s.index(ProductDoc.INDEX_NAME)
                    .from(from)
                    .size(size)
                    .query(buildQuery(params))
                    .highlight(h -> h
                            .fields("name", f -> f.preTags("<em>").postTags("</em>"))
                            .fields("subtitle", f -> f.preTags("<em>").postTags("</em>"))
                            .requireFieldMatch(false));

            List<SortOptions> sorts = buildSort(params.getSortBy());
            if (!sorts.isEmpty()) {
                s.sort(sorts);
            }

            // 侧边栏聚合:withFacets=true 才加
            if (Boolean.TRUE.equals(params.getWithFacets())) {
                buildFacetsAgg(s);
            }
            return s;
        });
    }

    /**
     * 聚合:brands(terms brandId + sub terms brandName)、categories、priceStats(stats).
     */
    private void buildFacetsAgg(SearchRequest.Builder s) {
        s.aggregations("brands", a -> a
                        .terms(t -> t.field("brandId").size(FACET_SIZE))
                        // 子聚合取品牌名(brandId→brandName 一对一)
                        .aggregations("name", sub -> sub.terms(t -> t.field("brandName").size(1))))
                .aggregations("categories", a -> a
                        .terms(t -> t.field("categoryId").size(FACET_SIZE))
                        .aggregations("name", sub -> sub.terms(t -> t.field("categoryName").size(1))))
                .aggregations("priceStats", a -> a.stats(st -> st.field("price")));
    }

    private Query buildQuery(RequestParams p) {
        return Query.of(q -> q.bool(b -> {
            if (p.getKey() != null && !p.getKey().isBlank()) {
                b.must(m -> m.multiMatch(mm -> mm
                        .query(p.getKey())
                        .fields("name^3", "subtitle^2", "description")
                        .operator(Operator.Or)));
            }
            b.filter(f -> f.term(t -> t.field("status").value(1)));
            if (p.getCategoryIds() != null && !p.getCategoryIds().isEmpty()) {
                b.filter(f -> f.terms(t -> t.field("categoryId").terms(tv -> tv.value(
                        p.getCategoryIds().stream().map(id -> FieldValue.of(id)).toList()))));
            }
            if (p.getBrandIds() != null && !p.getBrandIds().isEmpty()) {
                b.filter(f -> f.terms(t -> t.field("brandId").terms(tv -> tv.value(
                        p.getBrandIds().stream().map(id -> FieldValue.of(id)).toList()))));
            }
            if (p.getMinPrice() != null || p.getMaxPrice() != null) {
                b.filter(f -> f.range(r -> r.untyped(u -> {
                    u.field("price");
                    if (p.getMinPrice() != null) u.gte(JsonData.of(p.getMinPrice()));
                    if (p.getMaxPrice() != null) u.lte(JsonData.of(p.getMaxPrice()));
                    return u;
                })));
            }
            if (p.getMinDiscount() != null) {
                b.filter(f -> f.range(r -> r.untyped(u -> u
                        .field("discount").lte(JsonData.of(p.getMinDiscount())))));
            }
            if (Boolean.TRUE.equals(p.getOnlyInStock())) {
                b.filter(f -> f.range(r -> r.untyped(u -> u.field("inventory").gt(JsonData.of(0)))));
            }
            if (Boolean.TRUE.equals(p.getOnlyPreSale())) {
                b.filter(f -> f.term(t -> t.field("isPreSale").value(1)));
            }
            return b;
        }));
    }

    private List<SortOptions> buildSort(String sortBy) {
        if (sortBy == null || sortBy.isBlank() || "default".equals(sortBy)) {
            return List.of();
        }
        SortOptions sort = switch (sortBy) {
            case "price_asc"  -> sort("price", SortOrder.Asc);
            case "price_desc" -> sort("price", SortOrder.Desc);
            case "sales_desc" -> sort("salesCount", SortOrder.Desc);
            case "newest"     -> sort("createTime", SortOrder.Desc);
            default           -> null;
        };
        return sort == null ? List.of() : List.of(sort);
    }

    private SortOptions sort(String field, SortOrder order) {
        return SortOptions.of(s -> s.field(f -> f.field(field).order(order)));
    }

    private List<ProductDocVO> parseHits(List<Hit<ProductDoc>> hits) {
        return hits.stream().map(hit -> {
            ProductDoc d = hit.source();
            if (d == null) {
                return null;
            }
            ProductDocVO vo = new ProductDocVO();
            BeanUtils.copyProperties(d, vo);
            if (hit.highlight() != null) {
                List<String> nameHl = hit.highlight().get("name");
                if (nameHl != null && !nameHl.isEmpty()) {
                    vo.setName(nameHl.get(0));
                }
                List<String> subHl = hit.highlight().get("subtitle");
                if (subHl != null && !subHl.isEmpty()) {
                    vo.setSubtitle(subHl.get(0));
                }
            }
            return vo;
        }).filter(java.util.Objects::nonNull).toList();
    }

    /**
     * 解析聚合:brands / categories / priceStats.
     * 每个聚合解析用 try-catch 隔离,单个聚合异常不影响其他。
     */
    private Facets parseFacets(Map<String, Aggregate> aggs) {
        Facets facets = new Facets();
        if (aggs == null) {
            return facets;
        }
        facets.setBrands(parseTermsFacet(aggs, "brands", "name"));
        facets.setCategories(parseTermsFacet(aggs, "categories", "name"));
        facets.setPriceStats(parsePriceStats(aggs));
        return facets;
    }

    /**
     * 解析 terms(brandId/categoryId) + sub terms(name) 聚合.
     * brandId 是 long 字段 → lterms();brandName 是 keyword → sterms() 子聚合.
     */
    private List<FacetItem> parseTermsFacet(Map<String, Aggregate> aggs, String aggName, String subName) {
        Aggregate agg = aggs.get(aggName);
        if (agg == null || !agg.isLterms()) {
            return List.of();
        }
        List<FacetItem> items = new ArrayList<>();
        for (LongTermsBucket b : agg.lterms().buckets().array()) {
            Long id = b.key();
            Long count = b.docCount();
            String name = "";
            Aggregate nameAgg = b.aggregations().get(subName);
            if (nameAgg != null && nameAgg.isSterms()) {
                List<StringTermsBucket> nameBuckets = nameAgg.sterms().buckets().array();
                if (!nameBuckets.isEmpty()) {
                    // key() 返回 FieldValue,keyword 字段用 stringValue() 取字符串
                    name = nameBuckets.get(0).key().stringValue();
                }
            }
            items.add(new FacetItem(id, name, count));
        }
        return items;
    }

    /**
     * 解析 stats(price) 聚合:min/max/avg(数值以字符串形式返回,需 parseDouble).
     */
    private PriceStats parsePriceStats(Map<String, Aggregate> aggs) {
        Aggregate agg = aggs.get("priceStats");
        if (agg == null || !agg.isStats()) {
            return null;
        }
        StatsAggregate stats = agg.stats();
        PriceStats ps = new PriceStats();
        // min()/max()/avg() 返回 double,空字段时可能为 Infinity,过滤掉
        ps.setMin(finiteOrNull(stats.min()));
        ps.setMax(finiteOrNull(stats.max()));
        ps.setAvg(finiteOrNull(stats.avg()));
        return ps;
    }

    private Double finiteOrNull(double v) {
        return Double.isFinite(v) ? v : null;
    }

    private int normalizePage(Integer p) {
        return (p == null || p < 1) ? 1 : p;
    }

    private int normalizeSize(Integer s) {
        if (s == null || s < 1) return 10;
        return Math.min(s, MAX_PAGE_SIZE);
    }

    // ===================== 自动补全(completion suggester) =====================

    @Override
    public List<String> suggest(String prefix, int limit) {
        // 空白前缀直接返回,避免无意义 ES 调用
        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }
        int size = normalizeSuggestLimit(limit);

        try {
            SearchRequest request = SearchRequest.of(s -> s
                    .index(ProductDoc.INDEX_NAME)
                    // 只取 suggest,不需要 hits → size=0
                    .size(0)
                    .suggest(sg -> sg
                            // suggesters(name, builder) 注册一个名为 product-suggest 的建议器
                            .suggesters(SUGGEST_NAME, su -> su
                                    .prefix(prefix)
                                    .completion(c -> c
                                            .field("suggestion")
                                            .skipDuplicates(true)
                                            .size(size)))));
            // 用 Void 类型避免 Jackson 反序列化 source(suggest 不返回 source)
            SearchResponse<Void> resp = esClient.search(request, Void.class);

            return parseSuggest(resp.suggest(), size);
        } catch (Exception e) {
            log.error("[suggest] 自动补全失败, prefix={}, err={}", prefix, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 解析 suggest 响应:从 named suggester 取 completion options,
     * 用 LinkedHashSet 去重并保留 ES 返回顺序。
     */
    private List<String> parseSuggest(Map<String, List<Suggestion<Void>>> suggestMap, int limit) {
        if (suggestMap == null || suggestMap.isEmpty()) {
            return List.of();
        }
        List<Suggestion<Void>> list = suggestMap.get(SUGGEST_NAME);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        // LinkedHashSet 保留首次出现的顺序,同时去重(skipDuplicates=true 也已去重,双保险)
        Set<String> uniq = new LinkedHashSet<>();
        for (Suggestion<Void> s : list) {
            // 一个 suggester entry 对应一个 prefix completion 结果
            if (!s.isCompletion()) {
                continue;
            }
            for (CompletionSuggestOption<Void> opt : s.completion().options()) {
                String text = opt.text();
                if (text != null && !text.isBlank()) {
                    uniq.add(text);
                }
                // 提前达上限即停止,避免遍历无谓的桶
                if (uniq.size() >= limit) {
                    return new ArrayList<>(uniq);
                }
            }
        }
        return new ArrayList<>(uniq);
    }

    private int normalizeSuggestLimit(int limit) {
        if (limit <= 0) return DEFAULT_SUGGEST_LIMIT;
        return Math.min(limit, MAX_SUGGEST_LIMIT);
    }
}
