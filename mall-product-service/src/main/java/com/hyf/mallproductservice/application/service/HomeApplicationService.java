package com.hyf.mallproductservice.application.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hyf.mallproductservice.domain.repository.BannerRepository;
import com.hyf.mallproductservice.domain.repository.CategoryRepository;
import com.hyf.mallproductservice.domain.repository.HomeRepository;
import com.hyf.mallproductservice.domain.repository.ProductRepository;
import com.hyf.mallproductservice.infrastructure.persistence.dataobject.BannerDO;
import com.hyf.mallproductservice.infrastructure.persistence.dataobject.CategoryDO;
import com.hyf.mallproductservice.infrastructure.persistence.dataobject.HomeHotDO;
import com.hyf.mallproductservice.infrastructure.persistence.dataobject.HotActivityDO;
import com.hyf.mallproductservice.infrastructure.persistence.dataobject.HotSubtypeDO;
import com.hyf.mallproductservice.infrastructure.persistence.dataobject.HotSubtypeProductDO;
import com.hyf.mallproductservice.infrastructure.persistence.dataobject.ProductDO;
import com.hyf.mallproductservice.infrastructure.persistence.dataobject.ProductImageDO;
import com.hyf.mallcommon.core.page.PageQuery;
import com.hyf.mallcommon.core.page.PageResult;
import com.hyf.mallcommon.mybatis.support.PageQueries;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 首页聚合应用服务.
 *
 * @author hyf
 */
@Service
public class HomeApplicationService {

    private final BannerRepository bannerRepository;
    private final CategoryRepository categoryRepository;
    private final HomeRepository homeRepository;
    private final ProductRepository productRepository;

    public HomeApplicationService(BannerRepository bannerRepository,
                                  CategoryRepository categoryRepository,
                                  HomeRepository homeRepository,
                                  ProductRepository productRepository) {
        this.bannerRepository = bannerRepository;
        this.categoryRepository = categoryRepository;
        this.homeRepository = homeRepository;
        this.productRepository = productRepository;
    }

    /** 轮播图 */
    public List<Map<String, Object>> getBanners(Integer distributionSite) {
        List<BannerDO> banners = bannerRepository.findByDistributionSite(distributionSite);
        return banners.stream().map(b -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.getId().toString());
            m.put("imgUrl", b.getImgUrl());
            m.put("hrefUrl", b.getHrefUrl());
            m.put("type", b.getType());
            return m;
        }).collect(Collectors.toList());
    }

    /** 首页前台分类 */
    public List<Map<String, Object>> getCategoryMutli() {
        List<CategoryDO> categories = categoryRepository.findTopCategories();
        List<Map<String, Object>> result = new ArrayList<>();

        // 前端约定：第一个固定为"全部"
        Map<String, Object> all = new LinkedHashMap<>();
        all.put("id", "all");
        all.put("name", "全部");
        all.put("icon", "≡");
        result.add(all);

        for (CategoryDO c : categories) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId().toString());
            m.put("name", c.getName());
            m.put("icon", c.getIcon());
            result.add(m);
        }
        return result;
    }

    /** 首页热门推荐卡 */
    public List<Map<String, Object>> getHotMutli() {
        List<HomeHotDO> cards = homeRepository.findActiveHotCards();
        return cards.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId().toString());
            m.put("title", c.getTitle());
            m.put("alt", c.getAlt());
            m.put("pictures", parseJsonArray(c.getPictures()));
            m.put("target", c.getTarget());
            m.put("type", c.getType());
            return m;
        }).collect(Collectors.toList());
    }

    /** 猜你喜欢（分页） */
    public PageResult<Map<String, Object>> getGuessLike(PageQuery query) {
        Page<ProductDO> mpPage = PageQueries.toPage(query);
        Page<ProductDO> result = productRepository.findPage(mpPage, null, null, "sales_desc");
        List<Map<String, Object>> items = result.getRecords().stream()
                .map(this::toGoodsItem)
                .collect(Collectors.toList());
        return PageResult.of(items, result.getTotal(),
                Math.toIntExact(result.getCurrent()), Math.toIntExact(result.getSize()));
    }

    /** 热门推荐活动页 */
    public Map<String, Object> getHotActivity(String activityKey, PageQuery query, String subType) {
        HotActivityDO activity = homeRepository.findActivityByKey(activityKey);
        if (activity == null) {
            return Collections.emptyMap();
        }

        List<HotSubtypeDO> allSubtypes = homeRepository.findSubtypesByActivityId(activity.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", activity.getId().toString());
        result.put("bannerPicture", activity.getBannerPicture());
        result.put("title", activity.getTitle());

        List<Map<String, Object>> subTypeList = new ArrayList<>();
        for (HotSubtypeDO subtype : allSubtypes) {
            Map<String, Object> st = new LinkedHashMap<>();
            st.put("id", subtype.getId().toString());
            st.put("title", subtype.getTitle());

            // 商品 IDs
            List<HotSubtypeProductDO> refs = homeRepository.findProductsBySubtypeId(subtype.getId());
            List<Long> productIds = refs.stream().map(HotSubtypeProductDO::getProductId).toList();

            if (productIds.isEmpty()) {
                st.put("goodsItems", PageResult.empty(query.getPage(), query.getPageSize()));
            } else {
                // 如果指定了 subType，只返回对应那个的子类商品（带分页）
                List<Map<String, Object>> goodsItems = productIds.stream()
                        .map(productRepository::findById)
                        .filter(Objects::nonNull)
                        .map(this::toGoodsItem)
                        .collect(Collectors.toList());

                // 手动分页
                int total = goodsItems.size();
                int pageSize = query.getPageSize();
                int pages = (int) Math.ceil((double) total / pageSize);
                int page = query.getPage();
                int from = (page - 1) * pageSize;
                int to = Math.min(from + pageSize, total);
                List<Map<String, Object>> pageItems = from < total ? goodsItems.subList(from, to) : List.of();

                Map<String, Object> pageResult = new LinkedHashMap<>();
                pageResult.put("counts", total);
                pageResult.put("page", page);
                pageResult.put("pages", pages);
                pageResult.put("pageSize", pageSize);
                pageResult.put("items", pageItems);
                st.put("goodsItems", pageResult);
            }

            subTypeList.add(st);
        }
        result.put("subTypes", subTypeList);
        return result;
    }

    /** DO → 前端 GoodsItem 形状 */
    private Map<String, Object> toGoodsItem(ProductDO p) {
        List<ProductImageDO> mainImages = productRepository.findMainImages(p.getId());
        String picture = mainImages.isEmpty() ? "" : mainImages.get(0).getImageUrl();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId().toString());
        m.put("name", p.getName());
        m.put("desc", p.getSubtitle() != null ? p.getSubtitle() : "");
        m.put("price", p.getPrice());
        m.put("discount", p.getDiscount() != null ? p.getDiscount() : BigDecimal.ONE);
        m.put("orderNum", p.getSalesCount());
        m.put("picture", picture);
        return m;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, List.class);
        } catch (Exception e) {
            return List.of(json);
        }
    }
}
