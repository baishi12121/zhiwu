package com.hyf.mallproductservice.mapper;

import com.hyf.mallproductservice.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 商品 Mapper 接口，定义 tb_product 表的数据访问操作
 */
@Mapper
public interface ProductMapper {

    /**
     * 插入一条商品数据
     *
     * @param product 商品实体对象
     * @return 影响的行数，插入成功返回 1
     */
    int insert(Product product);

    /**
     * 根据商品ID删除商品
     *
     * @param id 商品主键ID
     * @return 影响的行数，删除成功返回 1
     */
    int deleteById(@Param("id") Long id);

    /**
     * 更新商品信息（动态更新非空字段）
     *
     * @param product 商品实体对象，需包含 id 属性
     * @return 影响的行数，更新成功返回 1
     */
    int update(Product product);

    /**
     * 根据主键ID查询商品详情
     *
     * @param id 商品主键ID
     * @return 商品实体对象，未找到返回 null
     */
    Product selectById(@Param("id") Long id);

    /**
     * 查询所有商品列表
     *
     * @return 商品实体对象集合
     */
    List<Product> selectAll();

    /**
     * 扣减商品库存（防超卖，确保剩余库存足够）
     *
     * @param id 商品主键ID
     * @param count 需要扣减的数量
     * @return 影响的行数，扣减成功（库存足够）返回 1，否则返回 0
     */
    int decreaseStock(@Param("id") Long id, @Param("count") Integer count);
}
