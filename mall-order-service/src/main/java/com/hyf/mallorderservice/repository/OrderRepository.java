package com.hyf.mallorderservice.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hyf.mallorderservice.dataobject.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单仓储接口（domain 层定义，infrastructure 层实现）。
 *
 * <p>由于订单服务与商品/优惠券/用户地址同处 {@code mall} 单库，扣库存与查询商品/券/地址
 * 均走本地 Mapper，不经 Feign。本接口聚合了订单流程所需的全部持久化操作。
 *
 * @author hyf
 */
public interface OrderRepository {

    // ---------- 订单主表 ----------

    /** 根据订单 ID 查询（不限用户，用于内部校验） */
    OrderDO findById(Long id);

    /** 根据订单 ID + 用户 ID 查询（确保用户只能操作自己的订单） */
    OrderDO findByIdAndUserId(Long id, Long userId);

    /** 分页查询用户订单（orderState=null 查全部） */
    Page<OrderDO> findPageByUser(Page<OrderDO> page, Long userId, Integer orderState);

    /** 插入订单主表，回写 id */
    int insertOrder(OrderDO order);

    /** 更新订单（全字段） */
    int updateOrder(OrderDO order);

    /** 删除订单主表（物理删除） */
    int deleteOrderById(Long id);

    // ---------- 订单明细 ----------

    /** 查询订单明细列表 */
    List<OrderItemDO> findOrderItems(Long orderId);

    /** 插入单条订单明细 */
    int insertOrderItem(OrderItemDO item);

    /** 删除订单的全部明细（物理删除） */
    int deleteOrderItemsByOrderId(Long orderId);

    // ---------- 订单状态日志 ----------

    /** 记录状态流转日志 */
    int insertStatusLog(OrderStatusLogDO log);

    // ---------- 商品 / SKU（同库只读 + 扣库存） ----------

    /** 查询 SKU 信息 */
    ProductSkuDO findSkuById(Long skuId);

    /** 查询商品信息 */
    ProductDO findProductById(Long productId);

    /** 扣减 SKU 库存（防超卖 SQL），返回 0 表示库存不足 */
    int decreaseStock(Long skuId, int count);

    /** 回退 SKU 库存（取消订单） */
    int increaseStock(Long skuId, int count);

    // ---------- 优惠券 ----------

    /** 查询优惠券模板 */
    CouponDO findCouponById(Long couponId);

    /** 查询用户优惠券 */
    UserCouponDO findUserCouponById(Long userCouponId);

    /** 查询用户可用优惠券（未使用 + 在有效期内 + 满足门槛） */
    List<UserCouponDO> findAvailableUserCoupons(Long userId);

    /** 占用用户优惠券（下单时），返回 0 表示券已被占用 */
    int occupyCoupon(Long userCouponId, Long orderId, LocalDateTime now);

    /** 释放用户优惠券（取消订单时） */
    int releaseCoupon(Long userCouponId);

    // ---------- 用户地址 ----------

    /** 查询用户所有地址 */
    List<UserAddressDO> findUserAddresses(Long userId);

    /** 查询用户指定地址 */
    UserAddressDO findUserAddressById(Long id, Long userId);

    // ---------- 购物车 ----------

    /** 查询用户选中的购物车商品 */
    List<UserCartDO> findSelectedCartItems(Long userId);

    /** 删除用户购物车中指定 SKU 的商品（下单成功后清理） */
    int deleteCartItemsByUserAndSkuIds(Long userId, List<Long> skuIds);
}
