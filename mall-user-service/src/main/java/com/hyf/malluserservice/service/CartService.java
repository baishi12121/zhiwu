package com.hyf.malluserservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hyf.mallcommon.core.exception.BizException;
import com.hyf.mallcommon.core.result.ResultCode;
import com.hyf.mallcommon.security.context.SecurityContextHolder;
import com.hyf.malluserservice.dto.request.CartAddRequest;
import com.hyf.malluserservice.dto.request.CartSelectedRequest;
import com.hyf.malluserservice.dto.request.CartUpdateRequest;
import com.hyf.malluserservice.dto.response.CartItemResponse;
import com.hyf.malluserservice.entity.UserCart;
import com.hyf.malluserservice.mapper.UserCartMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 购物车服务。
 *
 * <p>按 SKU 维度管理购物车：加车时同 SKU 数量叠加，价格取加入时的 SKU 快照。
 * 列表通过联表查询一次性返回商品名称、图片、现价、库存、规格文字等信息。
 *
 * @author hyf
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final UserCartMapper cartMapper;

    // ==================== 查询 ====================

    /**
     * 购物车列表（含商品信息）。
     *
     * @return 购物车项列表
     */
    public List<CartItemResponse> listCart() {
        Long userId = getCurrentUserId();
        List<CartItemResponse> list = cartMapper.selectCartList(userId, false);
        log.info("[cart] 列表查询: userId={}, 条数={}", userId, list.size());
        return list;
    }

    /**
     * 已选购物车列表（下单用）。
     *
     * @return 选中的购物车项列表
     */
    public List<CartItemResponse> listSelected() {
        Long userId = getCurrentUserId();
        return cartMapper.selectCartList(userId, true);
    }

    // ==================== 写操作 ====================

    /**
     * 加入购物车。
     *
     * <p>同一 SKU 已存在则数量叠加，否则新增。价格取 SKU 当前价作为快照。
     *
     * @param req 加车请求
     */
    @Transactional
    public void addCart(CartAddRequest req) {
        Long userId = getCurrentUserId();
        Long skuId = req.getSkuId();
        int count = req.getCount();

        // 校验 SKU 存在且上架，并取价格快照
        BigDecimal skuPrice = cartMapper.selectSkuPrice(skuId);
        if (skuPrice == null) {
            throw new BizException(ResultCode.NOT_FOUND, "商品规格不存在或已下架");
        }

        // 查是否已在购物车（user_id + sku_id 唯一键）
        UserCart exist = cartMapper.selectOne(
                new LambdaQueryWrapper<UserCart>()
                        .eq(UserCart::getUserId, userId)
                        .eq(UserCart::getSkuId, skuId)
        );

        if (exist != null) {
            // 同 SKU 数量叠加
            exist.setCount(exist.getCount() + count);
            cartMapper.updateById(exist);
        } else {
            // 新增购物车行
            UserCart cart = new UserCart();
            cart.setUserId(userId);
            cart.setSkuId(skuId);
            cart.setCount(count);
            cart.setSelected(1);
            cart.setPrice(skuPrice);
            cartMapper.insert(cart);
        }

        log.info("[cart] 加车成功: userId={}, skuId={}, count={}", userId, skuId, count);
    }

    /**
     * 修改购物车数量。
     *
     * @param skuId SKU ID
     * @param req   数量请求
     */
    @Transactional
    public void updateCount(Long skuId, CartUpdateRequest req) {
        UserCart cart = getOwnCart(skuId);
        cart.setCount(req.getCount());
        cartMapper.updateById(cart);
        log.info("[cart] 修改数量: userId={}, skuId={}, count={}", cart.getUserId(), skuId, req.getCount());
    }

    /**
     * 选中/取消选中单个购物车项。
     *
     * @param skuId SKU ID
     * @param req   选中状态请求
     */
    @Transactional
    public void updateSelected(Long skuId, CartSelectedRequest req) {
        UserCart cart = getOwnCart(skuId);
        int selected = Boolean.TRUE.equals(req.getSelected()) ? 1 : 0;
        cart.setSelected(selected);
        cartMapper.updateById(cart);
        log.info("[cart] 选中/取消: userId={}, skuId={}, selected={}", cart.getUserId(), skuId, selected);
    }

    /**
     * 全选/取消全选。
     *
     * @param req 选中状态请求
     */
    @Transactional
    public void selectAll(CartSelectedRequest req) {
        Long userId = getCurrentUserId();
        int selected = Boolean.TRUE.equals(req.getSelected()) ? 1 : 0;
        cartMapper.update(null,
                new LambdaUpdateWrapper<UserCart>()
                        .eq(UserCart::getUserId, userId)
                        .set(UserCart::getSelected, selected)
        );
        log.info("[cart] 全选/取消全选: userId={}, selected={}", userId, selected);
    }

    /**
     * 删除单个购物车项。
     *
     * @param skuId SKU ID
     */
    @Transactional
    public void deleteItem(Long skuId) {
        UserCart cart = getOwnCart(skuId);
        cartMapper.deleteById(cart.getId());
        log.info("[cart] 删除单项: userId={}, skuId={}", cart.getUserId(), skuId);
    }

    /**
     * 清空购物车。
     */
    @Transactional
    public void clearCart() {
        Long userId = getCurrentUserId();
        cartMapper.delete(
                new LambdaQueryWrapper<UserCart>()
                        .eq(UserCart::getUserId, userId)
        );
        log.info("[cart] 清空购物车: userId={}", userId);
    }

    // ==================== 内部辅助 ====================

    /**
     * 获取当前登录用户 ID，未登录抛 {@link ResultCode#UNAUTHORIZED}。
     */
    private Long getCurrentUserId() {
        Long userId = SecurityContextHolder.getUserId();
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }

    /**
     * 获取属于当前用户的购物车项（按 skuId 定位），不存在则抛 {@link ResultCode#NOT_FOUND}。
     *
     * @param skuId SKU ID
     * @return 购物车项实体
     */
    private UserCart getOwnCart(Long skuId) {
        Long userId = getCurrentUserId();
        UserCart cart = cartMapper.selectOne(
                new LambdaQueryWrapper<UserCart>()
                        .eq(UserCart::getUserId, userId)
                        .eq(UserCart::getSkuId, skuId)
        );
        if (cart == null) {
            throw new BizException(ResultCode.NOT_FOUND, "购物车商品不存在");
        }
        return cart;
    }
}
