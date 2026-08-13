package com.hyf.mallproductservice.service.impl;


import com.hyf.mallproductservice.service.CategoryApplicationService;
import com.hyf.mallproductservice.service.HomeApplicationService;
import com.hyf.mallproductservice.service.ProductApplicationService;
import com.hyf.mallproductservice.service.ProductDomainService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hyf.mallproductservice.entity.SkuVO;
import com.hyf.mallproductservice.entity.SpecVO;
import com.hyf.mallproductservice.repository.ProductRepository;
import com.hyf.mallproductservice.dataobject.*;
import com.hyf.mallcommon.core.page.PageQuery;
import com.hyf.mallcommon.core.page.PageResult;
import com.hyf.mallcommon.mybatis.support.PageQueries;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 商品应用服务.
 *
 * @author hyf
 */
@Service
public class ProductApplicationServiceImpl implements ProductApplicationService {

    private final ProductRepository productRepository;
    public ProductApplicationServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /** 商品分页列表 */
    public PageResult<Map<String, Object>> getProductList(PageQuery query, Long categoryId, String keyword, String sort) {
        Page<ProductDO> mpPage = PageQueries.toPage(query);
        Page<ProductDO> result = productRepository.findPage(mpPage, categoryId, keyword, sort);
        List<Map<String, Object>> items = result.getRecords().stream()
                .map(this::toProductListItem)
                .collect(Collectors.toList());
        return PageResult.of(items, result.getTotal(),
                Math.toIntExact(result.getCurrent()), Math.toIntExact(result.getSize()));
    }

    /** 商品详情 */
    public Map<String, Object> getProductDetail(Long id) {
        ProductDO product = productRepository.findById(id);
        if (product == null) {
            return null;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", product.getId().toString());
        result.put("name", product.getName());
        result.put("desc", product.getSubtitle() != null ? product.getSubtitle() : "");
        result.put("price", product.getPrice());
        result.put("oldPrice", product.getOldPrice() != null ? product.getOldPrice() : product.getPrice());

        // 主图
        List<ProductImageDO> mainImages = productRepository.findMainImages(id);
        List<String> mainPictures = mainImages.stream().map(ProductImageDO::getImageUrl).collect(Collectors.toList());
        result.put("mainPictures", mainPictures);

        // 详情
        Map<String, Object> details = new LinkedHashMap<>();
        List<ProductPropertyDO> properties = productRepository.findProperties(id);
        List<Map<String, String>> propList = properties.stream().map(prop -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("name", prop.getName());
            m.put("value", prop.getValue());
            return m;
        }).collect(Collectors.toList());
        details.put("properties", propList);

        List<ProductImageDO> detailImages = productRepository.findDetailImages(id);
        List<String> detailPics = detailImages.stream().map(ProductImageDO::getImageUrl).collect(Collectors.toList());
        details.put("pictures", detailPics);
        result.put("details", details);

        // SKU 列表
        List<SkuVO> skuVOs = getSkuVOs(id);
        List<Map<String, Object>> skuList = skuVOs.stream().map(sku -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", sku.getId());
            m.put("price", sku.getPrice());
            m.put("oldPrice", sku.getOldPrice());
            m.put("inventory", sku.getInventory());
            m.put("picture", sku.getPicture());
            m.put("skuCode", sku.getSkuCode());
            m.put("specs", sku.getSpecs().stream().map(spec -> {
                Map<String, String> sm = new LinkedHashMap<>();
                sm.put("name", spec.getName());
                sm.put("valueName", spec.getValueName());
                return sm;
            }).collect(Collectors.toList()));
            return m;
        }).collect(Collectors.toList());
        result.put("skus", skuList);

        // 规格树（specs）
        List<SpecVO> specVOs = getSpecVOs(id);
        result.put("specs", specVOs.stream().map(s -> {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("name", s.getName());
            sm.put("values", s.getValues().stream().map(v -> {
                Map<String, Object> vm = new LinkedHashMap<>();
                vm.put("name", v.getName());
                vm.put("available", v.getAvailable());
                vm.put("desc", v.getDesc());
                vm.put("picture", v.getPicture());
                return vm;
            }).collect(Collectors.toList()));
            return sm;
        }).collect(Collectors.toList()));

        // 同类推荐
        List<ProductDO> similar = productRepository.findSimilarProducts(product.getCategoryId(), id, 6);
        List<Map<String, Object>> similarProducts = similar.stream().map(p -> {
            List<ProductImageDO> imgs = productRepository.findMainImages(p.getId());
            String pic = imgs.isEmpty() ? "" : imgs.get(0).getImageUrl();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId().toString());
            m.put("name", p.getName());
            m.put("desc", p.getSubtitle() != null ? p.getSubtitle() : "");
            m.put("price", p.getPrice());
            m.put("discount", p.getDiscount() != null ? p.getDiscount() : BigDecimal.ONE);
            m.put("orderNum", p.getSalesCount());
            m.put("picture", pic);
            return m;
        }).collect(Collectors.toList());
        result.put("similarProducts", similarProducts);

        // 用户地址（前端预留字段）
        result.put("userAddresses", List.of());

        return result;
    }

    /** 商品 SKU 列表 */
    public List<SkuVO> getSkuVOs(Long productId) {
        List<ProductSkuDO> skus = productRepository.findSkus(productId);
        List<SpecDO> specs = productRepository.findSpecs(productId);

        return skus.stream().map(sku -> {
            List<SkuSpecValueDO> refs = productRepository.findSkuSpecValues(sku.getId());
            List<SkuVO.SkuSpecItem> specItems = refs.stream().map(ref -> {
                String specName = specs.stream()
                        .filter(s -> s.getId().equals(ref.getSpecId()))
                        .findFirst().map(SpecDO::getName).orElse("");
                SpecValueDO sv = productRepository.findSpecValues(ref.getSpecId()).stream()
                        .filter(v -> v.getId().equals(ref.getSpecValueId()))
                        .findFirst().orElse(null);
                String valueName = sv != null ? sv.getName() : "";
                return new SkuVO.SkuSpecItem(specName, valueName);
            }).collect(Collectors.toList());

            return new SkuVO(
                    sku.getId().toString(),
                    sku.getPrice(),
                    sku.getOldPrice() != null ? sku.getOldPrice() : sku.getPrice(),
                    sku.getInventory(),
                    sku.getPicture(),
                    sku.getSkuCode(),
                    specItems
            );
        }).collect(Collectors.toList());
    }

    /** 规格树（供前端 SKU 弹窗） */
    public List<SpecVO> getSpecVOs(Long productId) {
        List<SpecDO> specDOs = productRepository.findSpecs(productId);
        List<ProductSkuDO> skus = productRepository.findSkus(productId);

        return specDOs.stream().map(spec -> {
            List<SpecValueDO> values = productRepository.findSpecValues(spec.getId());
            List<SpecVO.SpecValueVO> valueVOs = values.stream().map(v -> {
                // 判断该规格值下是否有可用 SKU
                boolean available = skus.stream().anyMatch(sku -> {
                    List<SkuSpecValueDO> refs = productRepository.findSkuSpecValues(sku.getId());
                    return refs.stream().anyMatch(r ->
                            r.getSpecId().equals(spec.getId()) &&
                                    r.getSpecValueId().equals(v.getId())
                    );
                });
                return new SpecVO.SpecValueVO(
                        v.getName(),
                        available,
                        v.getName(),
                        v.getPicture() != null ? v.getPicture() : ""
                );
            }).collect(Collectors.toList());
            return new SpecVO(spec.getName(), valueVOs);
        }).collect(Collectors.toList());
    }

    /** 商品库存 */
    public Map<String, Object> getStock(Long productId) {
        ProductDO product = productRepository.findById(productId);
        if (product == null) {
            return Map.of("inventory", 0, "skus", List.of());
        }
        List<ProductSkuDO> skus = productRepository.findSkus(productId);
        List<Map<String, Object>> skuStocks = skus.stream().map(sku -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", sku.getId().toString());
            m.put("inventory", sku.getInventory());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("inventory", product.getInventory());
        result.put("skus", skuStocks);
        return result;
    }

    /** 商品列表项 */
    private Map<String, Object> toProductListItem(ProductDO p) {
        List<ProductImageDO> mainImages = productRepository.findMainImages(p.getId());
        String mainImage = mainImages.isEmpty() ? "" : mainImages.get(0).getImageUrl();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId().toString());
        m.put("name", p.getName());
        m.put("subtitle", p.getSubtitle() != null ? p.getSubtitle() : "");
        m.put("price", p.getPrice());
        m.put("originalPrice", p.getOldPrice() != null ? p.getOldPrice() : p.getPrice());
        m.put("sales", p.getSalesCount());
        m.put("mainImage", mainImage);
        m.put("tags", "");
        return m;
    }
}
