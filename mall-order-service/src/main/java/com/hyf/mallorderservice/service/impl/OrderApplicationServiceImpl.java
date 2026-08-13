package com.hyf.mallorderservice.service.impl;


import com.hyf.mallorderservice.service.OrderApplicationService;
import com.hyf.mallorderservice.service.PayApplicationService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hyf.mallcommon.core.constant.MallConstants;
import com.hyf.mallcommon.core.exception.BizException;
import com.hyf.mallcommon.core.page.PageQuery;
import com.hyf.mallcommon.core.page.PageResult;
import com.hyf.mallcommon.core.result.ResultCode;
import com.hyf.mallcommon.mybatis.support.PageQueries;
import com.hyf.mallorderservice.repository.OrderRepository;
import com.hyf.mallorderservice.service.OrderDomainService;
import com.hyf.mallorderservice.api.SeckillCancelDTO;
import com.hyf.mallorderservice.api.SeckillCancelFeignClient;
import com.hyf.mallorderservice.dataobject.*;
import com.hyf.mallorderservice.dto.OrderCreateRequest;
import com.hyf.mallorderservice.dto.OrderPreviewRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 订单应用服务 — 编排订单预览、创建、查询、取消、支付、确认、删除等用例。
 *
 * <p>创建订单与取消订单均走本地事务（扣库存/退库存 + 写订单/改状态 + 占券/退券），
 * 不经 Feign，因为订单/商品/优惠券同处 {@code mall} 单库。
 *
 * @author hyf
 */
@Service
public class OrderApplicationServiceImpl implements OrderApplicationService {

    private static final Logger log = LoggerFactory.getLogger(OrderApplicationService.class);

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final BigDecimal DEFAULT_POST_FEE = BigDecimal.ZERO;

    private final OrderRepository orderRepository;
    private final OrderDomainService orderDomainService;
    private final RabbitTemplate rabbitTemplate;
    private final SeckillCancelFeignClient seckillCancelFeignClient;
    public OrderApplicationServiceImpl(OrderRepository orderRepository,
                                   OrderDomainService orderDomainService,
                                   RabbitTemplate rabbitTemplate,
                                   SeckillCancelFeignClient seckillCancelFeignClient) {
        this.orderRepository = orderRepository;
        this.orderDomainService = orderDomainService;
        this.rabbitTemplate = rabbitTemplate;
        this.seckillCancelFeignClient = seckillCancelFeignClient;
    }

    // ========== 3.1 订单预览 ==========

    /**
     * 订单预览 — 计算金额 + 可用优惠券 + 用户地址列表。
     *
     * <p>goods 为空时从购物车取选中商品。
     *
     * @param userId  用户 ID
     * @param request 预览请求
     * @return 预览结果（含商品、结算汇总、地址列表）
     */
    public Map<String, Object> previewOrder(Long userId, OrderPreviewRequest request) {
        // 1. 解析商品列表
        List<SkuQuantity> skuList = resolveGoodsList(userId, request.getGoods());

        // 2. 查询 SKU + 商品信息，构建预览商品项
        List<Map<String, Object>> goodsItems = new ArrayList<>();
        BigDecimal totalPrice = BigDecimal.ZERO;

        for (SkuQuantity sq : skuList) {
            ProductSkuDO sku = orderRepository.findSkuById(sq.skuId);
            if (sku == null) {
                throw new BizException(ResultCode.NOT_FOUND.getCode(), "SKU 不存在: " + sq.skuId);
            }
            ProductDO product = orderRepository.findProductById(sku.getProductId());

            BigDecimal itemTotal = sku.getPrice().multiply(BigDecimal.valueOf(sq.count));
            totalPrice = totalPrice.add(itemTotal);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", sku.getId().toString());
            item.put("skuId", sku.getId().toString());
            item.put("name", product != null ? product.getName() : "");
            item.put("picture", sku.getPicture() != null ? sku.getPicture() : "");
            item.put("price", sku.getPrice());
            item.put("payPrice", sku.getPrice());
            item.put("count", sq.count);
            item.put("attrsText", "");
            item.put("totalPrice", itemTotal);
            item.put("totalPayPrice", itemTotal);
            goodsItems.add(item);
        }

        // 3. 计算优惠金额（如果指定了优惠券）
        BigDecimal discountAmount = BigDecimal.ZERO;
        Long couponId = request.getCouponId();
        if (couponId != null) {
            CouponDO coupon = orderRepository.findCouponById(couponId);
            discountAmount = orderDomainService.calculateDiscount(coupon, totalPrice);
        }

        // 4. 计算应付金额
        BigDecimal postFee = DEFAULT_POST_FEE;
        BigDecimal totalPayPrice = totalPrice.subtract(discountAmount).add(postFee);

        // 5. 构建汇总
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalPrice", totalPrice);
        summary.put("postFee", postFee);
        summary.put("totalPayPrice", totalPayPrice);
        summary.put("discountAmount", discountAmount);

        // 6. 查询用户地址列表
        List<UserAddressDO> addresses = orderRepository.findUserAddresses(userId);
        List<Map<String, Object>> userAddresses = addresses.stream()
                .map(this::toAddressMap)
                .collect(Collectors.toList());

        // 7. 组装结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("goods", goodsItems);
        result.put("summary", summary);
        result.put("userAddresses", userAddresses);
        return result;
    }

    // ========== 3.2 创建订单 ==========

    /**
     * 创建订单 — 扣库存 + 写订单 + 占券（本地事务）。
     *
     * @param userId  用户 ID
     * @param request 创建订单请求
     * @return { id: 订单 ID }
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createOrder(Long userId, OrderCreateRequest request) {
        // 1. 校验商品列表
        if (request.getGoods() == null || request.getGoods().isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "商品列表不能为空");
        }

        // 2. 查询 SKU + 商品信息，计算金额
        List<SkuQuantity> skuList = new ArrayList<>();
        BigDecimal totalMoney = BigDecimal.ZERO;

        for (OrderCreateRequest.GoodsItem g : request.getGoods()) {
            ProductSkuDO sku = orderRepository.findSkuById(g.getSkuId());
            if (sku == null) {
                throw new BizException(ResultCode.NOT_FOUND.getCode(), "SKU 不存在: " + g.getSkuId());
            }
            if (sku.getStatus() != null && sku.getStatus() != 1) {
                throw new BizException(ResultCode.PRODUCT_OFFLINE);
            }
            if (sku.getInventory() == null || sku.getInventory() < g.getCount()) {
                throw new BizException(ResultCode.PRODUCT_STOCK_NOT_ENOUGH);
            }
            skuList.add(new SkuQuantity(sku.getId(), g.getCount(), sku));
            totalMoney = totalMoney.add(sku.getPrice().multiply(BigDecimal.valueOf(g.getCount())));
        }

        // 3. 查询地址，生成地址快照
        UserAddressDO address = orderRepository.findUserAddressById(request.getAddressId(), userId);
        if (address == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "收货地址不存在");
        }

        // 4. 计算优惠金额
        BigDecimal discountAmount = BigDecimal.ZERO;
        Long couponId = request.getCouponId();
        UserCouponDO userCoupon = null;
        CouponDO coupon = null;
        if (couponId != null) {
            // 查用户优惠券，确认归属
            List<UserCouponDO> userCoupons = orderRepository.findAvailableUserCoupons(userId);
            userCoupon = userCoupons.stream()
                    .filter(uc -> couponId.equals(uc.getCouponId()))
                    .findFirst()
                    .orElse(null);
            if (userCoupon == null) {
                throw new BizException(ResultCode.NOT_FOUND.getCode(), "优惠券不存在或已使用");
            }
            coupon = orderRepository.findCouponById(couponId);
            discountAmount = orderDomainService.calculateDiscount(coupon, totalMoney);
            if (discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException(ResultCode.BAD_REQUEST.getCode(), "优惠券不满足使用条件");
            }
        }

        // 5. 计算应付金额
        BigDecimal postFee = DEFAULT_POST_FEE;
        BigDecimal payMoney = totalMoney.subtract(discountAmount).add(postFee);

        // 6. 扣库存（防超卖 SQL）
        for (SkuQuantity sq : skuList) {
            int affected = orderRepository.decreaseStock(sq.skuId, sq.count);
            if (affected == 0) {
                throw new BizException(ResultCode.PRODUCT_STOCK_NOT_ENOUGH);
            }
        }

        // 7. 写订单主表
        OrderDO order = new OrderDO();
        order.setOrderNo(orderDomainService.generateOrderNo());
        order.setUserId(userId);
        order.setOrderState(OrderDomainService.STATE_UNPAID);
        order.setTotalMoney(totalMoney);
        order.setPayMoney(payMoney);
        order.setPostFee(postFee);
        order.setDiscountAmount(discountAmount);
        order.setPayType(request.getPayType() != null ? request.getPayType() : 1);
        order.setPayChannel(request.getPayChannel());
        order.setDeliveryTimeType(request.getDeliveryTimeType() != null ? request.getDeliveryTimeType() : 1);
        order.setBuyerMessage(request.getBuyerMessage());
        order.setAddressId(address.getId());
        order.setReceiverContact(address.getReceiver());
        order.setReceiverMobile(address.getContact());
        order.setReceiverAddress(buildFullAddress(address));
        order.setCouponId(couponId);
        order.setPayLatestTime(orderDomainService.calculatePayLatestTime());

        orderRepository.insertOrder(order);

        // 8. 写订单明细
        for (SkuQuantity sq : skuList) {
            ProductSkuDO sku = sq.sku;
            ProductDO product = orderRepository.findProductById(sku.getProductId());

            OrderItemDO item = new OrderItemDO();
            item.setOrderId(order.getId());
            item.setSkuId(sku.getId());
            item.setSpuId(sku.getProductId());
            item.setName(product != null ? product.getName() : "");
            item.setImage(sku.getPicture());
            item.setAttrsText("");
            item.setCurPrice(sku.getPrice());
            item.setPrice(sku.getPrice());
            item.setQuantity(sq.count);
            BigDecimal subtotal = sku.getPrice().multiply(BigDecimal.valueOf(sq.count));
            item.setSubtotal(subtotal);
            item.setRealPay(subtotal);
            orderRepository.insertOrderItem(item);
        }

        // 9. 占用优惠券
        if (userCoupon != null) {
            int affected = orderRepository.occupyCoupon(userCoupon.getId(), order.getId(), LocalDateTime.now());
            if (affected == 0) {
                throw new BizException(ResultCode.COUPON_USED);
            }
            // 回写 userCouponId 到订单
            order.setUserCouponId(userCoupon.getId());
            orderRepository.updateOrder(order);
        }

        // 10. 记录状态日志
        saveStatusLog(order.getId(), null, OrderDomainService.STATE_UNPAID, "USER", "创建订单");

        log.info("订单创建成功: orderNo={}, userId={}, payMoney={}", order.getOrderNo(), userId, payMoney);

        // 清理购物车中已下单的商品（按 skuId 匹配，不影响未选中的商品）
        List<Long> skuIds = skuList.stream().map(SkuQuantity::skuId).collect(Collectors.toList());
        int deleted = orderRepository.deleteCartItemsByUserAndSkuIds(userId, skuIds);
        if (deleted > 0) {
            log.info("已清理购物车 {} 件商品: userId={}", deleted, userId);
        }

        // 发送延迟取消消息（30 分钟后投递到延迟队列）
        sendOrderTimeoutMessage(order.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", order.getId().toString());
        return result;
    }

    // ========== 3.3 订单列表 ==========

    /**
     * 订单列表 — 分页查询当前用户订单。
     *
     * @param userId     用户 ID
     * @param pageQuery  分页参数
     * @param orderState 订单状态（null 或 0 查全部）
     * @return 分页结果
     */
    public PageResult<Map<String, Object>> getOrderList(Long userId, PageQuery pageQuery, Integer orderState) {
        Page<OrderDO> mpPage = PageQueries.toPage(pageQuery);
        Page<OrderDO> result = orderRepository.findPageByUser(mpPage, userId, orderState);

        List<Map<String, Object>> items = result.getRecords().stream()
                .map(order -> buildOrderListMap(order, false))
                .collect(Collectors.toList());

        return PageResult.of(items, result.getTotal(),
                Math.toIntExact(result.getCurrent()), Math.toIntExact(result.getSize()));
    }

    // ========== 3.4 订单详情 ==========

    /**
     * 订单详情。
     *
     * @param userId 用户 ID
     * @param id     订单 ID
     * @return 订单详情（含明细），不存在返回 null
     */
    public Map<String, Object> getOrderDetail(Long userId, Long id) {
        OrderDO order = orderRepository.findByIdAndUserId(id, userId);
        if (order == null) {
            return null;
        }
        return buildOrderListMap(order, true);
    }

    // ========== 3.5 取消订单 ==========

    /**
     * 取消订单 — 退库存 + 退券 + 改状态（仅待付款可取消）。
     *
     * @param userId        用户 ID
     * @param id            订单 ID
     * @param cancelReason  取消原因
     * @return 更新后的订单详情
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> cancelOrder(Long userId, Long id, String cancelReason) {
        OrderDO order = orderRepository.findByIdAndUserId(id, userId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "订单不存在");
        }
        OrderDO cancelled = doCancelOrder(order, cancelReason, "USER");
        notifySeckillCancel(cancelled);
        return buildOrderListMap(cancelled, true);
    }

    // ========== 系统超时取消（延迟消息消费者调用） ==========

    /**
     * 系统超时取消 — 无需 userId，由延迟消息消费者调用。
     *
     * <p>幂等：若订单已不存在或已不是待付款状态，直接返回，不重复取消。
     *
     * @param orderId 订单 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrderBySystem(Long orderId) {
        OrderDO order = orderRepository.findById(orderId);
        if (order == null) {
            log.warn("超时取消：订单 {} 不存在，可能已删除", orderId);
            return;
        }
        if (order.getOrderState() != null && order.getOrderState() != OrderDomainService.STATE_UNPAID) {
            log.info("超时取消：订单 {} 当前状态为 {}，非待付款，跳过", orderId, order.getOrderState());
            return;
        }
        doCancelOrder(order, "超时未支付，系统自动取消", "SYSTEM");
        log.info("订单 {} 超时未支付，已自动取消", orderId);
    }

    // ========== 3.6 标记已支付 ==========

    /**
     * 标记已支付 — 待付款 → 待发货（Mock 实现，秒成功）。
     *
     * @param userId 用户 ID
     * @param id     订单 ID
     * @return 更新后的订单详情
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> payOrder(Long userId, Long id) {
        OrderDO order = orderRepository.findByIdAndUserId(id, userId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "订单不存在");
        }

        int fromState = order.getOrderState();
        orderDomainService.validateStateTransition(fromState, OrderDomainService.STATE_UNSHIPPED);

        order.setOrderState(OrderDomainService.STATE_UNSHIPPED);
        order.setPaidAt(LocalDateTime.now());
        orderRepository.updateOrder(order);

        saveStatusLog(id, fromState, OrderDomainService.STATE_UNSHIPPED, "SYSTEM", "支付成功");

        return buildOrderListMap(order, true);
    }

    // ========== 3.7 确认收货 ==========

    /**
     * 确认收货 — 待收货 → 待评价。
     *
     * @param userId 用户 ID
     * @param id     订单 ID
     * @return 更新后的订单详情
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> confirmOrder(Long userId, Long id) {
        OrderDO order = orderRepository.findByIdAndUserId(id, userId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "订单不存在");
        }

        int fromState = order.getOrderState();
        orderDomainService.validateStateTransition(fromState, OrderDomainService.STATE_UNREVIEWED);

        order.setOrderState(OrderDomainService.STATE_UNREVIEWED);
        order.setReceivedAt(LocalDateTime.now());
        orderRepository.updateOrder(order);

        saveStatusLog(id, fromState, OrderDomainService.STATE_UNREVIEWED, "USER", "确认收货");

        return buildOrderListMap(order, true);
    }

    // ========== 3.8 删除订单 ==========

    /**
     * 删除订单 — 仅待评价/已完成/已取消可删除，物理删除订单+明细。
     *
     * @param userId 用户 ID
     * @param id     订单 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrder(Long userId, Long id) {
        OrderDO order = orderRepository.findByIdAndUserId(id, userId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "订单不存在");
        }

        int state = order.getOrderState();
        if (state != OrderDomainService.STATE_UNREVIEWED
                && state != OrderDomainService.STATE_COMPLETED
                && state != OrderDomainService.STATE_CANCELLED) {
            throw new BizException(ResultCode.ORDER_STATUS_ILLEGAL.getCode(),
                    "仅待评价/已完成/已取消的订单可删除");
        }

        // 先删明细，再删主表
        orderRepository.deleteOrderItemsByOrderId(id);
        orderRepository.deleteOrderById(id);

        log.info("订单删除成功: orderId={}, userId={}", id, userId);
    }

    // ========== 3.9 按状态查 ==========

    /**
     * 按状态查订单（等同于 getOrderList 传指定 status）。
     *
     * @param userId     用户 ID
     * @param status     订单状态
     * @param pageQuery  分页参数
     * @return 分页结果
     */
    public PageResult<Map<String, Object>> getOrdersByStatus(Long userId, Integer status, PageQuery pageQuery) {
        return getOrderList(userId, pageQuery, status);
    }

    // ========== 私有辅助方法 ==========

    /**
     * 执行取消订单的核心逻辑（退库存 + 退券 + 改状态 + 记日志）。
     *
     * <p>抽取自原 cancelOrder，供用户取消与系统超时取消复用，保证退库存/退券逻辑一致。
     *
     * @param order    订单 DO（需已查到）
     * @param reason   取消原因
     * @param operator 操作者（USER / SYSTEM）
     * @return 已更新的订单 DO
     */
    private OrderDO doCancelOrder(OrderDO order, String reason, String operator) {
        int fromState = order.getOrderState();
        orderDomainService.validateStateTransition(fromState, OrderDomainService.STATE_CANCELLED);

        // 秒杀订单不扣普通 SKU 库存，取消时只通知 seckill-service 回补秒杀库存。
        List<OrderItemDO> items = orderRepository.findOrderItems(order.getId());
        if (order.getOrderSource() == null || order.getOrderSource() != 2) {
            for (OrderItemDO item : items) {
                if (item.getSkuId() != null) {
                    orderRepository.increaseStock(item.getSkuId(), item.getQuantity());
                }
            }
        }

        // 退优惠券
        if (order.getUserCouponId() != null) {
            orderRepository.releaseCoupon(order.getUserCouponId());
        }

        // 更新订单状态
        order.setOrderState(OrderDomainService.STATE_CANCELLED);
        order.setCancelReason(reason);
        order.setCancelledAt(LocalDateTime.now());
        orderRepository.updateOrder(order);

        // 记录状态日志
        saveStatusLog(order.getId(), fromState, OrderDomainService.STATE_CANCELLED, operator, reason);
        return order;
    }

    private void notifySeckillCancel(OrderDO order) {
        if (order.getOrderSource() == null || order.getOrderSource() != 2 || order.getSeckillItemId() == null) {
            return;
        }
        try {
            List<OrderItemDO> items = orderRepository.findOrderItems(order.getId());
            int quantity = items.stream().mapToInt(item -> item.getQuantity() == null ? 0 : item.getQuantity()).sum();
            SeckillCancelDTO dto = new SeckillCancelDTO();
            dto.setActivityId(order.getActivityId());
            dto.setSeckillItemId(order.getSeckillItemId());
            dto.setUserId(order.getUserId());
            dto.setQuantity(quantity);
            seckillCancelFeignClient.cancelSeckillOrder(order.getOrderNo(), dto);
            log.info("秒杀订单取消已通知库存回补: orderNo={}", order.getOrderNo());
        } catch (Exception e) {
            log.error("秒杀订单取消通知库存回补失败: orderNo={}", order.getOrderNo(), e);
        }
    }

    /**
     * 发送订单超时取消的延迟消息。
     *
     * <p>延迟时长 = {@link OrderDomainService#PAY_TIMEOUT_MINUTES} 分钟，通过 {@code x-delay} 头指定（毫秒）。
     * 消息体为订单 ID 字符串，消费者用 String 接收。
     *
     * @param orderId 订单 ID
     */
    private void sendOrderTimeoutMessage(Long orderId) {
        long delayMillis = OrderDomainService.PAY_TIMEOUT_MINUTES * 60L * 1000L;
        rabbitTemplate.convertAndSend(
                MallConstants.MQ_ORDER_DELAY_EXCHANGE,
                MallConstants.MQ_ORDER_DELAY_ROUTING_KEY,
                orderId.toString(),
                message -> {
                    message.getMessageProperties().setHeader(MallConstants.MQ_X_DELAY_HEADER, delayMillis);
                    return message;
                });
        log.info("订单 {} 已发送延迟取消消息，延迟 {} 毫秒", orderId, delayMillis);
    }

    /**
     * 解析商品列表 — goods 为空时从购物车取选中商品。
     */
    private List<SkuQuantity> resolveGoodsList(Long userId, List<OrderPreviewRequest.GoodsItem> goods) {
        List<SkuQuantity> result = new ArrayList<>();
        if (goods != null && !goods.isEmpty()) {
            for (OrderPreviewRequest.GoodsItem g : goods) {
                result.add(new SkuQuantity(g.getSkuId(), g.getCount(), null));
            }
        } else {
            // 从购物车取选中商品
            List<UserCartDO> cartItems = orderRepository.findSelectedCartItems(userId);
            if (cartItems.isEmpty()) {
                throw new BizException(ResultCode.BAD_REQUEST.getCode(), "购物车没有选中商品");
            }
            for (UserCartDO cart : cartItems) {
                result.add(new SkuQuantity(cart.getSkuId(), cart.getCount(), null));
            }
        }
        return result;
    }

    /**
     * 构建订单列表/详情的 Map 响应（贴近前端 OrderResult 结构）。
     *
     * @param order      订单 DO
     * @param withDetail 是否包含完整明细（详情接口传 true）
     */
    private Map<String, Object> buildOrderListMap(OrderDO order, boolean withDetail) {
        List<OrderItemDO> items = orderRepository.findOrderItems(order.getId());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", order.getId().toString());
        m.put("orderState", order.getOrderState());
        m.put("countdown", orderDomainService.calculateCountdown(order.getOrderState(), order.getPayLatestTime()));

        // 商品列表
        List<Map<String, Object>> skus = items.stream().map(item -> {
            Map<String, Object> sku = new LinkedHashMap<>();
            sku.put("id", item.getId().toString());
            sku.put("skuId", item.getSkuId() != null ? item.getSkuId().toString() : "");
            sku.put("spuId", item.getSpuId() != null ? item.getSpuId().toString() : "");
            sku.put("name", item.getName());
            sku.put("attrsText", item.getAttrsText() != null ? item.getAttrsText() : "");
            sku.put("quantity", item.getQuantity());
            sku.put("curPrice", item.getCurPrice());
            sku.put("image", item.getImage() != null ? item.getImage() : "");
            return sku;
        }).collect(Collectors.toList());
        m.put("skus", skus);

        // 总件数
        int totalNum = items.stream().mapToInt(OrderItemDO::getQuantity).sum();
        m.put("totalNum", totalNum);

        m.put("receiverContact", order.getReceiverContact() != null ? order.getReceiverContact() : "");
        m.put("receiverMobile", order.getReceiverMobile() != null ? order.getReceiverMobile() : "");
        m.put("receiverAddress", order.getReceiverAddress() != null ? order.getReceiverAddress() : "");

        m.put("createTime", order.getCreateTime() != null
                ? order.getCreateTime().format(TIME_FORMATTER) : "");

        m.put("totalMoney", order.getTotalMoney());
        m.put("postFee", order.getPostFee());
        m.put("payMoney", order.getPayMoney());

        if (withDetail) {
            m.put("orderNo", order.getOrderNo());
            m.put("payType", order.getPayType());
            m.put("payChannel", order.getPayChannel());
            m.put("deliveryTimeType", order.getDeliveryTimeType());
            m.put("buyerMessage", order.getBuyerMessage() != null ? order.getBuyerMessage() : "");
            m.put("cancelReason", order.getCancelReason() != null ? order.getCancelReason() : "");
            m.put("couponId", order.getCouponId());
        }

        return m;
    }

    /**
     * 构建地址 Map（贴近前端 AddressItem 结构）。
     */
    private Map<String, Object> toAddressMap(UserAddressDO addr) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", addr.getId().toString());
        m.put("receiver", addr.getReceiver());
        m.put("contact", addr.getContact());
        m.put("fullLocation", addr.getFullLocation() != null ? addr.getFullLocation() : "");
        m.put("address", addr.getAddress());
        m.put("isDefault", addr.getIsDefault() != null ? addr.getIsDefault() : 0);
        return m;
    }

    /**
     * 构建完整地址字符串。
     */
    private String buildFullAddress(UserAddressDO addr) {
        String fullLocation = addr.getFullLocation() != null ? addr.getFullLocation() : "";
        String detail = addr.getAddress() != null ? addr.getAddress() : "";
        return fullLocation + detail;
    }

    /**
     * 记录订单状态流转日志。
     */
    private void saveStatusLog(Long orderId, Integer fromState, int toState, String operator, String remark) {
        OrderStatusLogDO log = new OrderStatusLogDO();
        log.setOrderId(orderId);
        log.setFromState(fromState);
        log.setToState(toState);
        log.setOperator(operator);
        log.setRemark(remark);
        orderRepository.insertStatusLog(log);
    }

    /** 内部 SKU + 数量 + DO 容器 */
    private record SkuQuantity(Long skuId, int count, ProductSkuDO sku) {}
}
