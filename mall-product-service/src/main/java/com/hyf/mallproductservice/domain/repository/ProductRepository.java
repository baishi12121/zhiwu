package com.hyf.mallproductservice.domain.repository;

import com.hyf.mallproductservice.domain.model.entity.Product;

/**
 * 商品仓储接口（domain 层定义，infrastructure 层实现）
 *
 * @author hyf
 */
public interface ProductRepository {

    Product findById(Long id);

    /**
     * 扣减库存（防超卖 SQL 兜底）。
     *
     * @param productId 商品 id
     * @param count     扣减数量
     * @return 实际影响行数，0 表示库存不足
     */
    int decreaseStock(Long productId, int count);
}
