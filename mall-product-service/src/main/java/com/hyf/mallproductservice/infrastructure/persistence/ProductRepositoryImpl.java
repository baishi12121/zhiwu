package com.hyf.mallproductservice.infrastructure.persistence;

import com.hyf.mallproductservice.domain.model.entity.Product;
import com.hyf.mallproductservice.domain.repository.ProductRepository;
import org.springframework.stereotype.Repository;

/**
 * 商品仓储实现（占位）
 *
 * <p>后续注入 MyBatis Mapper，把 domain entity ↔ DO 互转。
 *
 * @author hyf
 */
@Repository
public class ProductRepositoryImpl implements ProductRepository {

    @Override
    public Product findById(Long id) {
        return null;
    }

    @Override
    public int decreaseStock(Long productId, int count) {
        return 0;
    }
}
