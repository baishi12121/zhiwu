package com.hyf.mallproductservice.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hyf.mallproductservice.dataobject.ProductDO;
import com.hyf.mallproductservice.dataobject.ProductImageDO;
import com.hyf.mallproductservice.dataobject.ProductPropertyDO;
import com.hyf.mallproductservice.dataobject.ProductSkuDO;
import com.hyf.mallproductservice.dataobject.SkuSpecValueDO;
import com.hyf.mallproductservice.dataobject.SpecDO;
import com.hyf.mallproductservice.dataobject.SpecValueDO;

import java.util.List;

/**
 * 商品仓储接口（domain 层定义，infrastructure 层实现）.
 *
 * @author hyf
 */
public interface ProductRepository {

    ProductDO findById(Long id);

    /** 分页查询商品（支持筛选） */
    Page<ProductDO> findPage(Page<ProductDO> page, Long categoryId, String keyword, String sort);

    /** 根据商品ID查询主图 */
    List<ProductImageDO> findMainImages(Long productId);

    /** 根据商品ID查询详情图 */
    List<ProductImageDO> findDetailImages(Long productId);

    /** 查询商品详情属性 */
    List<ProductPropertyDO> findProperties(Long productId);

    /** 查询商品SKU列表 */
    List<ProductSkuDO> findSkus(Long productId);

    /** 查询商品规格组 */
    List<SpecDO> findSpecs(Long productId);

    /** 查询规格值 */
    List<SpecValueDO> findSpecValues(Long specId);

    /** 查询SKU关联的规格值 */
    List<SkuSpecValueDO> findSkuSpecValues(Long skuId);

    /** 查询同类商品（同分类，按销量排序） */
    List<ProductDO> findSimilarProducts(Long categoryId, Long excludeProductId, int limit);

    /** 扣减库存（防超卖 SQL 兜底） */
    int decreaseStock(Long productId, int count);
}
