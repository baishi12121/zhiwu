package com.hyf.mallproductservice.service;

import com.hyf.mallproductservice.entity.Product;
import java.util.List;

/**
 * 商品服务接口，提供对外的商品业务层能力
 */
public interface ProductService {

    /**
     * 保存/创建新商品
     *
     * @param product 商品实体，应包含 name, price, totalStock 等字段
     * @return 创建成功并回填自增 ID 后的商品实体
     */
    Product saveProduct(Product product);

    /**
     * 根据主键ID删除商品
     *
     * @param id 商品主键ID
     * @return 删除成功返回 true，否则返回 false
     */
    boolean deleteProduct(Long id);

    /**
     * 更新商品属性（只更新传入实体中非空的属性）
     *
     * @param product 包含主键ID及需要更新属性的实体对象
     * @return 更新成功返回 true，否则返回 false
     */
    boolean updateProduct(Product product);

    /**
     * 根据商品ID获取详细信息
     *
     * @param id 商品主键ID
     * @return 对应的商品实体，未找到返回 null
     */
    Product getProductById(Long id);

    /**
     * 获取所有商品列表
     *
     * @return 商品实体集合
     */
    List<Product> getAllProducts();

    /**
     * 扣减商品库存（在微服务下单场景中常被订单服务通过远程调用调用）
     *
     * @param id 商品ID
     * @param count 扣减数量
     * @return 扣减成功返回 true，若库存不足或商品不存在返回 false
     */
    boolean decreaseStock(Long id, Integer count);

    /**
     * 获取商品热度排行榜 TOP N
     * 使用 Redis ZREVRANGE 高效获取有序集合中分数最高的 N 个成员
     *
     * @param topN 获取前 N 名
     * @return 商品 ID 列表，按热度从高到低排序
     */
    List<Long> getHotProductRank(int topN);
}
