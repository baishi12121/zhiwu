package com.hyf.malladminservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hyf.malladminservice.dto.request.CategorySaveRequest;
import com.hyf.malladminservice.dto.request.ProductSaveRequest;
import com.hyf.malladminservice.dto.request.SkuSaveRequest;
import com.hyf.malladminservice.dto.request.StockAdjustRequest;
import com.hyf.malladminservice.entity.AdminCategory;
import com.hyf.malladminservice.entity.AdminProduct;
import com.hyf.malladminservice.entity.AdminProductImage;
import com.hyf.malladminservice.entity.AdminProductProperty;
import com.hyf.malladminservice.entity.AdminProductSku;
import com.hyf.malladminservice.mapper.AdminCategoryMapper;
import com.hyf.malladminservice.mapper.AdminProductImageMapper;
import com.hyf.malladminservice.mapper.AdminProductMapper;
import com.hyf.malladminservice.mapper.AdminProductPropertyMapper;
import com.hyf.malladminservice.mapper.AdminProductSkuMapper;
import com.hyf.mallcommon.core.exception.BizException;
import com.hyf.mallcommon.core.page.PageQuery;
import com.hyf.mallcommon.core.page.PageResult;
import com.hyf.mallcommon.core.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 商品管理业务逻辑。
 *
 * <p>支持商品 SPU / SKU / 图片 / 属性的 CRUD、上下架、库存调整。
 * 商品库存汇总字段 {@code product.inventory} 由 SKU 库存求和派生（写入时维护）。
 *
 * @author hyf
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminProductService {

    private final AdminProductMapper productMapper;
    private final AdminProductSkuMapper skuMapper;
    private final AdminProductImageMapper imageMapper;
    private final AdminProductPropertyMapper propertyMapper;
    private final AdminCategoryMapper categoryMapper;

    // ==================== 商品 SPU ====================

    /**
     * 商品分页查询，支持按分类 / 关键词 / 上下架状态筛选。
     */
    public PageResult<AdminProduct> listProducts(PageQuery query, Long categoryId, String keyword, Integer status) {
        LambdaQueryWrapper<AdminProduct> wrapper = new LambdaQueryWrapper<>();
        if (categoryId != null) {
            wrapper.eq(AdminProduct::getCategoryId, categoryId);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(AdminProduct::getName, keyword);
        }
        if (status != null) {
            wrapper.eq(AdminProduct::getStatus, status);
        }
        wrapper.orderByDesc(AdminProduct::getCreateTime);

        IPage<AdminProduct> page = new Page<>(query.getPage(), query.getPageSize());
        IPage<AdminProduct> result = productMapper.selectPage(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), query.getPage(), query.getPageSize());
    }

    /**
     * 商品详情：主表 + SKU 列表 + 图片列表 + 属性列表。
     *
     * @param id 商品 ID
     * @return 商品实体（含子集合）
     * @throws BizException 商品不存在
     */
    public AdminProduct getProductDetail(Long id) {
        AdminProduct product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException(ResultCode.NOT_FOUND, "商品不存在");
        }
        product.setSkus(listSkus(id));
        product.setImages(listImages(id));
        product.setProperties(listProperties(id));
        return product;
    }

    /**
     * 新建商品 + 一并写入 SKU / 图片 / 属性。
     *
     * @param req 保存请求
     * @return 新生成的商品 ID
     */
    @Transactional
    public Long createProduct(ProductSaveRequest req) {
        // 1. 校验分类存在
        validateCategory(req.getCategoryId());

        // 2. 写主表
        AdminProduct product = new AdminProduct();
        BeanUtils.copyProperties(req, product, "skus", "images", "properties");
        if (product.getStatus() == null) {
            product.setStatus(1);
        }
        if (product.getIsPreSale() == null) {
            product.setIsPreSale(0);
        }
        if (product.getInventory() == null) {
            product.setInventory(0);
        }
        product.setSalesCount(0);
        product.setCommentCount(0);
        product.setCollectCount(0);
        productMapper.insert(product);

        // 3. 写 SKU + 汇总库存
        int totalInventory = 0;
        if (!CollectionUtils.isEmpty(req.getSkus())) {
            for (ProductSaveRequest.AdminProductSku skuReq : req.getSkus()) {
                AdminProductSku sku = new AdminProductSku();
                BeanUtils.copyProperties(skuReq, sku);
                sku.setProductId(product.getId());
                if (sku.getStatus() == null) {
                    sku.setStatus(1);
                }
                if (sku.getInventory() == null) {
                    sku.setInventory(0);
                }
                skuMapper.insert(sku);
                totalInventory += sku.getInventory();
            }
        }
        // 4. 写图片
        if (!CollectionUtils.isEmpty(req.getImages())) {
            for (ProductSaveRequest.AdminProductImage imgReq : req.getImages()) {
                AdminProductImage image = new AdminProductImage();
                BeanUtils.copyProperties(imgReq, image);
                image.setProductId(product.getId());
                if (image.getImageType() == null) {
                    image.setImageType(1);
                }
                if (image.getSortOrder() == null) {
                    image.setSortOrder(0);
                }
                imageMapper.insert(image);
            }
        }
        // 5. 写属性
        if (!CollectionUtils.isEmpty(req.getProperties())) {
            for (ProductSaveRequest.AdminProductProperty propReq : req.getProperties()) {
                AdminProductProperty prop = new AdminProductProperty();
                BeanUtils.copyProperties(propReq, prop);
                prop.setProductId(product.getId());
                if (prop.getSortOrder() == null) {
                    prop.setSortOrder(0);
                }
                propertyMapper.insert(prop);
            }
        }
        // 6. 回填总库存
        if (totalInventory > 0) {
            product.setInventory(totalInventory);
            productMapper.updateById(product);
        }
        log.info("[admin-product] 新建商品成功: id={}, name={}", product.getId(), product.getName());
        return product.getId();
    }

    /**
     * 修改商品基本信息（不含子集合）。
     */
    @Transactional
    public void updateProduct(Long id, ProductSaveRequest req) {
        AdminProduct exist = productMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "商品不存在");
        }
        validateCategory(req.getCategoryId());
        BeanUtils.copyProperties(req, exist, "skus", "images", "properties", "salesCount", "commentCount", "collectCount");
        productMapper.updateById(exist);
        log.info("[admin-product] 修改商品成功: id={}", id);
    }

    /**
     * 商品上下架。
     */
    @Transactional
    public void updateStatus(Long id, Integer status) {
        AdminProduct exist = productMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "商品不存在");
        }
        exist.setStatus(status);
        productMapper.updateById(exist);
        log.info("[admin-product] 上下架: id={}, status={}", id, status);
    }

    /**
     * 删除商品 + 关联的 SKU / 图片 / 属性。
     *
     * <p>注意：秒杀活动可能引用了该商品的 SKU，调用方应在秒杀项管理中先清理。
     *
     * @param id 商品 ID
     */
    @Transactional
    public void deleteProduct(Long id) {
        AdminProduct exist = productMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "商品不存在");
        }
        productMapper.deleteById(id);
        skuMapper.delete(new LambdaQueryWrapper<AdminProductSku>().eq(AdminProductSku::getProductId, id));
        imageMapper.delete(new LambdaQueryWrapper<AdminProductImage>().eq(AdminProductImage::getProductId, id));
        propertyMapper.delete(new LambdaQueryWrapper<AdminProductProperty>().eq(AdminProductProperty::getProductId, id));
        log.info("[admin-product] 删除商品: id={}", id);
    }

    /**
     * 调整商品 SPU 总库存（直接设置）。
     */
    @Transactional
    public void adjustProductStock(Long id, StockAdjustRequest req) {
        AdminProduct exist = productMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "商品不存在");
        }
        int target = calcTarget(exist.getInventory(), req);
        exist.setInventory(target);
        productMapper.updateById(exist);
        log.info("[admin-product] 调整 SPU 库存: id={}, before={}, after={}", id, exist.getInventory(), target);
    }

    // ==================== SKU ====================

    /**
     * 列出商品下的所有 SKU。
     */
    public List<AdminProductSku> listSkus(Long productId) {
        return skuMapper.selectList(new LambdaQueryWrapper<AdminProductSku>()
                .eq(AdminProductSku::getProductId, productId)
                .orderByAsc(AdminProductSku::getId));
    }

    /**
     * 新增 SKU，并同步刷新 SPU 总库存。
     */
    @Transactional
    public Long addSku(Long productId, SkuSaveRequest req) {
        AdminProduct product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(ResultCode.NOT_FOUND, "商品不存在");
        }
        AdminProductSku sku = new AdminProductSku();
        BeanUtils.copyProperties(req, sku);
        sku.setProductId(productId);
        if (sku.getStatus() == null) {
            sku.setStatus(1);
        }
        if (sku.getInventory() == null) {
            sku.setInventory(0);
        }
        skuMapper.insert(sku);
        refreshProductInventory(productId);
        log.info("[admin-product] 新增 SKU: id={}, productId={}", sku.getId(), productId);
        return sku.getId();
    }

    /**
     * 修改 SKU，并同步刷新 SPU 总库存。
     */
    @Transactional
    public void updateSku(Long skuId, SkuSaveRequest req) {
        AdminProductSku exist = skuMapper.selectById(skuId);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "SKU 不存在");
        }
        BeanUtils.copyProperties(req, exist, "productId");
        skuMapper.updateById(exist);
        refreshProductInventory(exist.getProductId());
        log.info("[admin-product] 修改 SKU: id={}", skuId);
    }

    /**
     * 删除 SKU，并同步刷新 SPU 总库存。
     */
    @Transactional
    public void deleteSku(Long skuId) {
        AdminProductSku exist = skuMapper.selectById(skuId);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "SKU 不存在");
        }
        skuMapper.deleteById(skuId);
        refreshProductInventory(exist.getProductId());
        log.info("[admin-product] 删除 SKU: id={}, productId={}", skuId, exist.getProductId());
    }

    /**
     * SKU 上下架。
     */
    @Transactional
    public void updateSkuStatus(Long skuId, Integer status) {
        AdminProductSku exist = skuMapper.selectById(skuId);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "SKU 不存在");
        }
        exist.setStatus(status);
        skuMapper.updateById(exist);
    }

    /**
     * 调整 SKU 库存。
     */
    @Transactional
    public void adjustSkuStock(Long skuId, StockAdjustRequest req) {
        AdminProductSku exist = skuMapper.selectById(skuId);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "SKU 不存在");
        }
        int target = calcTarget(exist.getInventory(), req);
        exist.setInventory(target);
        skuMapper.updateById(exist);
        refreshProductInventory(exist.getProductId());
        log.info("[admin-product] 调整 SKU 库存: id={}, after={}", skuId, target);
    }

    // ==================== 分类 ====================

    /**
     * 全量分类列表，供管理后台筛选下拉用。
     */
    public List<AdminCategory> listAllCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<AdminCategory>()
                .orderByAsc(AdminCategory::getSortOrder));
    }

    @Transactional
    public void updateCategory(Long id, CategorySaveRequest req) {
        AdminCategory exist = categoryMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "分类不存在");
        }
        BeanUtils.copyProperties(req, exist, "id");
        categoryMapper.updateById(exist);
    }

    // ==================== 内部工具 ====================

    private List<AdminProductImage> listImages(Long productId) {
        return imageMapper.selectList(new LambdaQueryWrapper<AdminProductImage>()
                .eq(AdminProductImage::getProductId, productId)
                .orderByAsc(AdminProductImage::getImageType)
                .orderByAsc(AdminProductImage::getSortOrder));
    }

    private List<AdminProductProperty> listProperties(Long productId) {
        return propertyMapper.selectList(new LambdaQueryWrapper<AdminProductProperty>()
                .eq(AdminProductProperty::getProductId, productId)
                .orderByAsc(AdminProductProperty::getSortOrder));
    }

    private void validateCategory(Long categoryId) {
        if (categoryId == null) {
            return;
        }
        if (categoryMapper.selectById(categoryId) == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "分类不存在: " + categoryId);
        }
    }

    /**
     * 重新汇总 SKU 库存到 SPU 的 inventory 字段。
     */
    private void refreshProductInventory(Long productId) {
        List<AdminProductSku> skus = skuMapper.selectList(
                new LambdaQueryWrapper<AdminProductSku>().eq(AdminProductSku::getProductId, productId));
        int sum = skus == null ? 0 : skus.stream().mapToInt(s -> s.getInventory() == null ? 0 : s.getInventory()).sum();
        AdminProduct update = new AdminProduct();
        update.setId(productId);
        update.setInventory(sum);
        productMapper.updateById(update);
    }

    /**
     * 根据 absolute 标志计算目标库存值。
     */
    private int calcTarget(Integer current, StockAdjustRequest req) {
        int base = current == null ? 0 : current;
        boolean absolute = req.getAbsolute() == null || req.getAbsolute();
        int target = absolute ? req.getInventory() : base + req.getInventory();
        if (target < 0) {
            throw new BizException(ResultCode.PRODUCT_STOCK_NOT_ENOUGH, "库存不能为负");
        }
        return target;
    }

    /**
     * 兼容 List null 防御（占位，避免 import 未使用告警）。
     */
    @SuppressWarnings("unused")
    private List<AdminProductSku> emptyIfNull(List<AdminProductSku> list) {
        return list == null ? new ArrayList<>() : list;
    }
}
