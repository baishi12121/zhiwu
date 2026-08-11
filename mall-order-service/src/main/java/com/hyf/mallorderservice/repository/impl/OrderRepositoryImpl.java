package com.hyf.mallorderservice.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hyf.mallorderservice.repository.OrderRepository;
import com.hyf.mallorderservice.dataobject.*;
import com.hyf.mallorderservice.mapper.*;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单仓储实现 — 聚合订单/商品/优惠券/地址/购物车的持久化操作。
 *
 * <p>所有跨域查询均走本地 Mapper（同库），不经 Feign。
 *
 * @author hyf
 */
@Repository
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusLogMapper orderStatusLogMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductMapper productMapper;
    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;
    private final UserAddressMapper userAddressMapper;
    private final UserCartMapper userCartMapper;

    public OrderRepositoryImpl(OrderMapper orderMapper,
                               OrderItemMapper orderItemMapper,
                               OrderStatusLogMapper orderStatusLogMapper,
                               ProductSkuMapper productSkuMapper,
                               ProductMapper productMapper,
                               CouponMapper couponMapper,
                               UserCouponMapper userCouponMapper,
                               UserAddressMapper userAddressMapper,
                               UserCartMapper userCartMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.orderStatusLogMapper = orderStatusLogMapper;
        this.productSkuMapper = productSkuMapper;
        this.productMapper = productMapper;
        this.couponMapper = couponMapper;
        this.userCouponMapper = userCouponMapper;
        this.userAddressMapper = userAddressMapper;
        this.userCartMapper = userCartMapper;
    }

    // ---------- 订单主表 ----------

    @Override
    public OrderDO findById(Long id) {
        return orderMapper.selectById(id);
    }

    @Override
    public OrderDO findByIdAndUserId(Long id, Long userId) {
        LambdaQueryWrapper<OrderDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderDO::getId, id).eq(OrderDO::getUserId, userId);
        return orderMapper.selectOne(wrapper);
    }

    @Override
    public Page<OrderDO> findPageByUser(Page<OrderDO> page, Long userId, Integer orderState) {
        LambdaQueryWrapper<OrderDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderDO::getUserId, userId);
        if (orderState != null && orderState > 0) {
            wrapper.eq(OrderDO::getOrderState, orderState);
        }
        wrapper.orderByDesc(OrderDO::getCreateTime);
        return orderMapper.selectPage(page, wrapper);
    }

    @Override
    public int insertOrder(OrderDO order) {
        return orderMapper.insert(order);
    }

    @Override
    public int updateOrder(OrderDO order) {
        return orderMapper.updateById(order);
    }

    @Override
    public int deleteOrderById(Long id) {
        return orderMapper.deleteById(id);
    }

    // ---------- 订单明细 ----------

    @Override
    public List<OrderItemDO> findOrderItems(Long orderId) {
        LambdaQueryWrapper<OrderItemDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderItemDO::getOrderId, orderId);
        return orderItemMapper.selectList(wrapper);
    }

    @Override
    public int insertOrderItem(OrderItemDO item) {
        return orderItemMapper.insert(item);
    }

    @Override
    public int deleteOrderItemsByOrderId(Long orderId) {
        LambdaQueryWrapper<OrderItemDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderItemDO::getOrderId, orderId);
        return orderItemMapper.delete(wrapper);
    }

    // ---------- 订单状态日志 ----------

    @Override
    public int insertStatusLog(OrderStatusLogDO log) {
        return orderStatusLogMapper.insert(log);
    }

    // ---------- 商品 / SKU ----------

    @Override
    public ProductSkuDO findSkuById(Long skuId) {
        return productSkuMapper.selectById(skuId);
    }

    @Override
    public ProductDO findProductById(Long productId) {
        return productMapper.selectById(productId);
    }

    @Override
    public int decreaseStock(Long skuId, int count) {
        return productSkuMapper.decreaseStock(skuId, count);
    }

    @Override
    public int increaseStock(Long skuId, int count) {
        return productSkuMapper.increaseStock(skuId, count);
    }

    // ---------- 优惠券 ----------

    @Override
    public CouponDO findCouponById(Long couponId) {
        return couponMapper.selectById(couponId);
    }

    @Override
    public UserCouponDO findUserCouponById(Long userCouponId) {
        return userCouponMapper.selectById(userCouponId);
    }

    @Override
    public List<UserCouponDO> findAvailableUserCoupons(Long userId) {
        LambdaQueryWrapper<UserCouponDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCouponDO::getUserId, userId)
                .eq(UserCouponDO::getStatus, 0);
        return userCouponMapper.selectList(wrapper);
    }

    @Override
    public int occupyCoupon(Long userCouponId, Long orderId, LocalDateTime now) {
        return userCouponMapper.occupyCoupon(userCouponId, orderId, now);
    }

    @Override
    public int releaseCoupon(Long userCouponId) {
        return userCouponMapper.releaseCoupon(userCouponId);
    }

    // ---------- 用户地址 ----------

    @Override
    public List<UserAddressDO> findUserAddresses(Long userId) {
        LambdaQueryWrapper<UserAddressDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserAddressDO::getUserId, userId)
                .orderByDesc(UserAddressDO::getIsDefault)
                .orderByDesc(UserAddressDO::getUpdateTime);
        return userAddressMapper.selectList(wrapper);
    }

    @Override
    public UserAddressDO findUserAddressById(Long id, Long userId) {
        LambdaQueryWrapper<UserAddressDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserAddressDO::getId, id).eq(UserAddressDO::getUserId, userId);
        return userAddressMapper.selectOne(wrapper);
    }

    // ---------- 购物车 ----------

    @Override
    public List<UserCartDO> findSelectedCartItems(Long userId) {
        LambdaQueryWrapper<UserCartDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCartDO::getUserId, userId)
                .eq(UserCartDO::getSelected, 1);
        return userCartMapper.selectList(wrapper);
    }

    @Override
    public int deleteCartItemsByUserAndSkuIds(Long userId, List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return 0;
        }
        LambdaQueryWrapper<UserCartDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCartDO::getUserId, userId)
                .in(UserCartDO::getSkuId, skuIds);
        return userCartMapper.delete(wrapper);
    }
}
