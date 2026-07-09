package com.hyf.mallproductservice.service.impl;

import com.hyf.mallproductservice.entity.Product;
import com.hyf.mallproductservice.mapper.ProductMapper;
import com.hyf.mallproductservice.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 商品服务实现类，处理商品核心业务逻辑
 */
@Service
public class ProductServiceImpl implements ProductService {

    private static final String PRODUCT_RANK_KEY = "product:hot:rank";

    private final ProductMapper productMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 构造器注入 ProductMapper
     *
     * @param productMapper 商品数据库操作接口
     */
    @Autowired
    public ProductServiceImpl(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    /**
     * 保存并创建新商品
     *
     * @param product 商品实体，应包含 name, price, totalStock 等字段
     * @return 创建成功并回填自增 ID 后的商品实体
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Product saveProduct(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("商品数据不能为空");
        }
        if (product.getName() == null || product.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("商品名称不能为空");
        }
        if (product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("商品价格不合法");
        }
        if (product.getTotalStock() == null || product.getTotalStock() < 0) {
            throw new IllegalArgumentException("总库存不合法");
        }

        product.setName(product.getName().trim());
        
        // 新增商品时，默认剩余库存等于总库存
        if (product.getRemainStock() == null) {
            product.setRemainStock(product.getTotalStock());
        }
        // 默认设置为上架状态
        if (product.getStatus() == null) {
            product.setStatus(1);
        }

        productMapper.insert(product);
        return product;
    }

    /**
     * 根据主键ID删除商品
     *
     * @param id 商品主键ID
     * @return 删除成功返回 true，否则返回 false
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteProduct(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("商品ID不能为空");
        }
        return productMapper.deleteById(id) > 0;
    }

    /**
     * 更新商品属性（只更新传入实体中非空的属性）
     *
     * @param product 包含主键ID及需要更新属性的实体对象
     * @return 更新成功返回 true，否则返回 false
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateProduct(Product product) {
        if (product == null || product.getId() == null) {
            throw new IllegalArgumentException("更新数据且主键ID不能为空");
        }
        // 若同时修改了总库存，未传剩余库存，做简单的校验补充
        if (product.getTotalStock() != null && product.getTotalStock() >= 0 && product.getRemainStock() == null) {
            Product current = productMapper.selectById(product.getId());
            if (current != null) {
                // 如果调整了总库存，剩余库存做对应的增减
                int diff = product.getTotalStock() - current.getTotalStock();
                int newRemain = current.getRemainStock() + diff;
                product.setRemainStock(Math.max(newRemain, 0));
            }
        }
        return productMapper.update(product) > 0;
    }

    /**
     * 根据商品ID获取详细信息
     *
     * @param id 商品主键ID
     * @return 对应的商品实体，未找到返回 null
     */
    @Override
    public Product getProductById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("商品ID不能为空");
        }
        return productMapper.selectById(id);
    }

    /**
     * 获取所有商品列表
     *
     * @return 商品实体集合
     */
    @Override
    public List<Product> getAllProducts() {
        return productMapper.selectAll();
    }

    /**
     * 扣减商品库存（安全扣减，配合 SQL 中的 AND remain_stock >= count 实现防超卖）
     *
     * @param id 商品ID
     * @param count 扣减数量
     * @return 扣减成功返回 true，若库存不足或商品不存在返回 false
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean decreaseStock(Long id, Integer count) {
        if (id == null) {
            throw new IllegalArgumentException("商品ID不能为空");
        }
        if (count == null || count <= 0) {
            throw new IllegalArgumentException("扣减数量必须大于 0");
        }
        return productMapper.decreaseStock(id, count) > 0;
    }

    /**
     * 获取商品热度排行榜 TOP N
     * 使用 Redis ZREVRANGE 命令高效获取有序集合中分数最高的成员
     * 时间复杂度: O(log(N) + M)，N 为集合基数，M 为返回元素个数
     *
     * @param topN 获取前 N 名
     * @return 商品 ID 列表，按热度从高到低排序
     */
    @Override
    public List<Long> getHotProductRank(int topN) {
        if (topN <= 0) {
            return Collections.emptyList();
        }
        try {
            // 使用 ZREVRANGE 获取分数最高的 topN 个成员（从高到低排序）
            // 参数: key, start, end (0-based, 包含边界)
            Set<ZSetOperations.TypedTuple<String>> rankSet = redisTemplate
                    .opsForZSet()
                    .reverseRangeWithScores(PRODUCT_RANK_KEY, 0, topN - 1);

            if (rankSet == null || rankSet.isEmpty()) {
                return Collections.emptyList();
            }

            List<Long> productIds = new ArrayList<>(rankSet.size());
            for (ZSetOperations.TypedTuple<String> tuple : rankSet) {
                if (tuple.getValue() != null) {
                    productIds.add(Long.valueOf(tuple.getValue()));
                }
            }
            return productIds;
        } catch (Exception e) {
            // 降级处理：Redis 异常时返回空列表，避免影响主流程
            return Collections.emptyList();
        }
    }
}
