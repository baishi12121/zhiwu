package com.hyf.malluserservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.malluserservice.dto.response.CartItemResponse;
import com.hyf.malluserservice.entity.UserCart;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/**
 * 购物车 Mapper。
 *
 * <p>标准 CRUD 由 {@link BaseMapper} 提供；联表查询（含商品信息）由
 * {@code resources/mapper/CartMapper.xml} 实现；SKU 价格校验用 {@code @Select} 注解。
 *
 * @author hyf
 */
@Mapper
public interface UserCartMapper extends BaseMapper<UserCart> {

    /**
     * 查询当前用户的购物车列表（联查商品/SKU/规格信息）。
     *
     * @param userId       用户 ID
     * @param onlySelected 是否只返回选中的（下单用）
     * @return 购物车项响应列表
     */
    List<CartItemResponse> selectCartList(@Param("userId") Long userId,
                                          @Param("onlySelected") boolean onlySelected);

    /**
     * 查询 SKU 当前价格（仅上架的 SKU）。
     *
     * <p>用于加车时校验 SKU 存在且上架，并取价格快照。
     *
     * @param skuId SKU ID
     * @return SKU 价格；SKU 不存在或已下架时返回 {@code null}
     */
    @Select("SELECT price FROM product_sku WHERE id = #{skuId} AND status = 1")
    BigDecimal selectSkuPrice(@Param("skuId") Long skuId);
}
