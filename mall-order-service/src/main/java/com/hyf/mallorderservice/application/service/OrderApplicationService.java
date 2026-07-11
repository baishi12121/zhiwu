package com.hyf.mallorderservice.application.service;

/**
 * 订单应用服务（占位）
 *
 * <p>编排下单流程：
 * <ol>
 *   <li>校验地址、库存、优惠券（{@code @Transactional}）</li>
 *   <li>扣库存（本地 mapper 直调，不经 Feign）</li>
 *   <li>写 order + order_item</li>
 *   <li>占 user_coupon</li>
 *   <li>事务提交后发 ORDER 热度消息（{@code TransactionSynchronizationManager}）</li>
 * </ol>
 *
 * @author hyf
 */
public class OrderApplicationService {
}
