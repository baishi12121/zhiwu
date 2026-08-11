package com.hyf.mallproductservice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hyf.mallcommon.core.page.PageQuery;
import com.hyf.mallcommon.core.page.PageResult;
import com.hyf.mallcommon.mybatis.support.PageQueries;
import com.hyf.mallproductservice.dataobject.BannerDO;
import com.hyf.mallproductservice.dataobject.CategoryDO;
import com.hyf.mallproductservice.dataobject.HomeHotDO;
import com.hyf.mallproductservice.dataobject.HomeSeckillItemDO;
import com.hyf.mallproductservice.dataobject.HotActivityDO;
import com.hyf.mallproductservice.dataobject.HotSubtypeDO;
import com.hyf.mallproductservice.dataobject.HotSubtypeProductDO;
import com.hyf.mallproductservice.dataobject.ProductDO;
import com.hyf.mallproductservice.dataobject.ProductImageDO;
import com.hyf.mallproductservice.mapper.HomeSeckillMapper;
import com.hyf.mallproductservice.repository.BannerRepository;
import com.hyf.mallproductservice.repository.CategoryRepository;
import com.hyf.mallproductservice.repository.HomeRepository;
import com.hyf.mallproductservice.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class HomeApplicationService {

    private final BannerRepository bannerRepository;
    private final CategoryRepository categoryRepository;
    private final HomeRepository homeRepository;
    private final ProductRepository productRepository;
    private final HomeSeckillMapper homeSeckillMapper;

    public HomeApplicationService(BannerRepository bannerRepository,
                                  CategoryRepository categoryRepository,
                                  HomeRepository homeRepository,
                                  ProductRepository productRepository,
                                  HomeSeckillMapper homeSeckillMapper) {
        this.bannerRepository = bannerRepository;
        this.categoryRepository = categoryRepository;
        this.homeRepository = homeRepository;
        this.productRepository = productRepository;
        this.homeSeckillMapper = homeSeckillMapper;
    }

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

    public List<Map<String, Object>> getCategoryMutli() {
        List<CategoryDO> categories = categoryRepository.findTopCategories();
        List<Map<String, Object>> result = new ArrayList<>();

        Map<String, Object> all = new LinkedHashMap<>();
        all.put("id", "all");
        all.put("name", "全部");
        all.put("icon", "");
        all.put("picture", "");
        result.add(all);

        for (CategoryDO c : categories) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId().toString());
            m.put("name", c.getName());
            m.put("icon", c.getIcon());
            m.put("picture", c.getPicture());
            result.add(m);
        }
        return result;
    }

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

    public List<Map<String, Object>> getHomeSeckill() {
        List<HomeSeckillItemDO> items = homeSeckillMapper.selectActiveHomeItems();
        return items.stream().map(item -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", item.getId().toString());
            m.put("activityId", item.getActivityId().toString());
            m.put("activityName", item.getActivityName());
            m.put("startTime", item.getStartTime());
            m.put("endTime", item.getEndTime());
            m.put("spuId", item.getSpuId().toString());
            m.put("skuId", item.getSkuId().toString());
            m.put("name", item.getSpuName());
            m.put("skuCode", item.getSkuCode());
            m.put("picture", item.getPicture());
            m.put("originalPrice", item.getOriginalPrice());
            m.put("seckillPrice", item.getSeckillPrice());
            m.put("seckillStock", item.getSeckillStock());
            m.put("limitPerUser", item.getLimitPerUser());
            return m;
        }).collect(Collectors.toList());
    }

    public PageResult<Map<String, Object>> getGuessLike(PageQuery query) {
        Page<ProductDO> mpPage = PageQueries.toPage(query);
        Page<ProductDO> result = productRepository.findPage(mpPage, null, null, "sales_desc");
        List<Map<String, Object>> items = result.getRecords().stream()
                .map(this::toGoodsItem)
                .collect(Collectors.toList());
        return PageResult.of(items, result.getTotal(),
                Math.toIntExact(result.getCurrent()), Math.toIntExact(result.getSize()));
    }

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

            List<HotSubtypeProductDO> refs = homeRepository.findProductsBySubtypeId(subtype.getId());
            List<Long> productIds = refs.stream().map(HotSubtypeProductDO::getProductId).toList();

            if (productIds.isEmpty()) {
                st.put("goodsItems", PageResult.empty(query.getPage(), query.getPageSize()));
            } else {
                List<Map<String, Object>> goodsItems = productIds.stream()
                        .map(productRepository::findById)
                        .filter(Objects::nonNull)
                        .map(this::toGoodsItem)
                        .collect(Collectors.toList());

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
