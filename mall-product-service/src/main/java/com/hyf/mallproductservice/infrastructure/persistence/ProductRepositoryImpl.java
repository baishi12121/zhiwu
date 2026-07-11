package com.hyf.mallproductservice.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hyf.mallproductservice.domain.repository.ProductRepository;
import com.hyf.mallproductservice.infrastructure.persistence.dataobject.ProductDO;
import com.hyf.mallproductservice.infrastructure.persistence.dataobject.ProductImageDO;
import com.hyf.mallproductservice.infrastructure.persistence.dataobject.ProductPropertyDO;
import com.hyf.mallproductservice.infrastructure.persistence.dataobject.ProductSkuDO;
import com.hyf.mallproductservice.infrastructure.persistence.dataobject.SkuSpecValueDO;
import com.hyf.mallproductservice.infrastructure.persistence.dataobject.SpecDO;
import com.hyf.mallproductservice.infrastructure.persistence.dataobject.SpecValueDO;
import com.hyf.mallproductservice.infrastructure.persistence.mapper.ProductImageMapper;
import com.hyf.mallproductservice.infrastructure.persistence.mapper.ProductMapper;
import com.hyf.mallproductservice.infrastructure.persistence.mapper.ProductPropertyMapper;
import com.hyf.mallproductservice.infrastructure.persistence.mapper.ProductSkuMapper;
import com.hyf.mallproductservice.infrastructure.persistence.mapper.SkuSpecValueMapper;
import com.hyf.mallproductservice.infrastructure.persistence.mapper.SpecMapper;
import com.hyf.mallproductservice.infrastructure.persistence.mapper.SpecValueMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductMapper productMapper;
    private final ProductImageMapper productImageMapper;
    private final ProductPropertyMapper productPropertyMapper;
    private final ProductSkuMapper productSkuMapper;
    private final SpecMapper specMapper;
    private final SpecValueMapper specValueMapper;
    private final SkuSpecValueMapper skuSpecValueMapper;

    public ProductRepositoryImpl(ProductMapper productMapper,
                                 ProductImageMapper productImageMapper,
                                 ProductPropertyMapper productPropertyMapper,
                                 ProductSkuMapper productSkuMapper,
                                 SpecMapper specMapper,
                                 SpecValueMapper specValueMapper,
                                 SkuSpecValueMapper skuSpecValueMapper) {
        this.productMapper = productMapper;
        this.productImageMapper = productImageMapper;
        this.productPropertyMapper = productPropertyMapper;
        this.productSkuMapper = productSkuMapper;
        this.specMapper = specMapper;
        this.specValueMapper = specValueMapper;
        this.skuSpecValueMapper = skuSpecValueMapper;
    }

    @Override
    public ProductDO findById(Long id) {
        return productMapper.selectById(id);
    }

    @Override
    public Page<ProductDO> findPage(Page<ProductDO> page, Long categoryId, String keyword, String sort) {
        LambdaQueryWrapper<ProductDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductDO::getStatus, 1);

        if (categoryId != null && categoryId > 0) {
            wrapper.eq(ProductDO::getCategoryId, categoryId);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(ProductDO::getName, keyword)
                    .or().like(ProductDO::getSubtitle, keyword));
        }
        if (sort != null) {
            switch (sort) {
                case "price_asc" -> wrapper.orderByAsc(ProductDO::getPrice);
                case "price_desc" -> wrapper.orderByDesc(ProductDO::getPrice);
                case "sales_desc" -> wrapper.orderByDesc(ProductDO::getSalesCount);
                case "newest" -> wrapper.orderByDesc(ProductDO::getCreateTime);
                default -> wrapper.orderByDesc(ProductDO::getSalesCount);
            }
        } else {
            wrapper.orderByDesc(ProductDO::getSalesCount);
        }

        return productMapper.selectPage(page, wrapper);
    }

    @Override
    public List<ProductImageDO> findMainImages(Long productId) {
        LambdaQueryWrapper<ProductImageDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductImageDO::getProductId, productId)
                .eq(ProductImageDO::getImageType, 1)
                .orderByAsc(ProductImageDO::getSortOrder);
        return productImageMapper.selectList(wrapper);
    }

    @Override
    public List<ProductImageDO> findDetailImages(Long productId) {
        LambdaQueryWrapper<ProductImageDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductImageDO::getProductId, productId)
                .eq(ProductImageDO::getImageType, 2)
                .orderByAsc(ProductImageDO::getSortOrder);
        return productImageMapper.selectList(wrapper);
    }

    @Override
    public List<ProductPropertyDO> findProperties(Long productId) {
        LambdaQueryWrapper<ProductPropertyDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductPropertyDO::getProductId, productId)
                .orderByAsc(ProductPropertyDO::getSortOrder);
        return productPropertyMapper.selectList(wrapper);
    }

    @Override
    public List<ProductSkuDO> findSkus(Long productId) {
        LambdaQueryWrapper<ProductSkuDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductSkuDO::getProductId, productId)
                .eq(ProductSkuDO::getStatus, 1);
        return productSkuMapper.selectList(wrapper);
    }

    @Override
    public List<SpecDO> findSpecs(Long productId) {
        LambdaQueryWrapper<SpecDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SpecDO::getProductId, productId)
                .orderByAsc(SpecDO::getSortOrder);
        return specMapper.selectList(wrapper);
    }

    @Override
    public List<SpecValueDO> findSpecValues(Long specId) {
        LambdaQueryWrapper<SpecValueDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SpecValueDO::getSpecId, specId)
                .orderByAsc(SpecValueDO::getSortOrder);
        return specValueMapper.selectList(wrapper);
    }

    @Override
    public List<SkuSpecValueDO> findSkuSpecValues(Long skuId) {
        LambdaQueryWrapper<SkuSpecValueDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SkuSpecValueDO::getSkuId, skuId)
                .orderByAsc(SkuSpecValueDO::getSortOrder);
        return skuSpecValueMapper.selectList(wrapper);
    }

    @Override
    public List<ProductDO> findSimilarProducts(Long categoryId, Long excludeProductId, int limit) {
        LambdaQueryWrapper<ProductDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductDO::getCategoryId, categoryId)
                .eq(ProductDO::getStatus, 1)
                .ne(ProductDO::getId, excludeProductId)
                .orderByDesc(ProductDO::getSalesCount)
                .last("LIMIT " + limit);
        return productMapper.selectList(wrapper);
    }

    @Override
    public int decreaseStock(Long productId, int count) {
        ProductDO product = productMapper.selectById(productId);
        if (product == null || product.getInventory() < count) {
            return 0;
        }
        product.setInventory(product.getInventory() - count);
        return productMapper.updateById(product);
    }
}
