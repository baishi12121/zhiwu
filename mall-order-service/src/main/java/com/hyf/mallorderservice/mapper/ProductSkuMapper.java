package com.hyf.mallorderservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.mallorderservice.dataobject.ProductSkuDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 商品 SKU Mapper — 订单服务用于查询 SKU 信息与扣减/回退库存。
 *
 * <p>扣库存采用 {@code WHERE inventory >= ?} 防超卖，返回值 0 表示库存不足。
 *
 * @author hyf
 */
@Mapper
public interface ProductSkuMapper extends BaseMapper<ProductSkuDO> {

    /**
     * 扣减 SKU 库存（防超卖）。
     *
     * @param skuId SKU ID
     * @param count 扣减数量
     * @return 受影响行数，0 表示库存不足
     */
    @Update("UPDATE product_sku SET inventory = inventory - #{count} WHERE id = #{skuId} AND inventory >= #{count}")
    int decreaseStock(@Param("skuId") Long skuId, @Param("count") int count);

    /**
     * 回退 SKU 库存（取消订单时调用）。
     *
     * @param skuId SKU ID
     * @param count 回退数量
     * @return 受影响行数
     */
    @Update("UPDATE product_sku SET inventory = inventory + #{count} WHERE id = #{skuId}")
    int increaseStock(@Param("skuId") Long skuId, @Param("count") int count);
}
