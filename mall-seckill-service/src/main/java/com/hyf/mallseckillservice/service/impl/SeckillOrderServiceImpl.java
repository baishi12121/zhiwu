package com.hyf.mallseckillservice.service.impl;


import com.hyf.mallseckillservice.service.MqMessageService;
import com.hyf.mallseckillservice.service.SeckillApplicationService;
import com.hyf.mallseckillservice.service.SeckillCompensateService;
import com.hyf.mallseckillservice.service.SeckillOrderService;
import com.hyf.mallseckillservice.service.SeckillTask;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyf.mallcommon.core.constant.MallConstants;
import com.hyf.mallcommon.core.exception.BizException;
import com.hyf.mallcommon.core.result.ResultCode;
import com.hyf.mallseckillservice.constant.SeckillConstants;
import com.hyf.mallseckillservice.dto.SeckillOrderMessageDTO;
import com.hyf.mallseckillservice.dto.SeckillOrderResultDTO;
import com.hyf.mallseckillservice.entity.*;
import com.hyf.mallseckillservice.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 秒杀订单创建服务。
 *
 * <p>消费者调用该服务在一个数据库事务内写订单、订单明细、扣 DB 库存并标记本地消息完成。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillOrderServiceImpl implements SeckillOrderService {

    public static final int PAY_TIMEOUT_MINUTES = 15;
    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final UserAddressMapper userAddressMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;
    private final SeckillItemMapper seckillItemMapper;
    private final MqMessageService mqMessageService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public SeckillOrderResultDTO createSeckillOrder(SeckillOrderMessageDTO dto) {
        // Redis 已完成预占，这里做数据库最终确认；任何异常都会回滚订单和消息完成状态。
        BigDecimal total = dto.getSeckillPrice().multiply(BigDecimal.valueOf(dto.getQuantity()));

        UserAddressDO address = userAddressMapper.selectByIdAndUserId(dto.getAddressId(), dto.getUserId());
        ProductDO product = productMapper.selectById(dto.getSpuId());
        ProductSkuDO sku = productSkuMapper.selectById(dto.getSkuId());

        OrderDO order = new OrderDO();
        order.setOrderNo(generateOrderNo());
        order.setUserId(dto.getUserId());
        order.setOrderState(SeckillConstants.ORDER_STATE_PENDING_PAY);
        order.setTotalMoney(total);
        order.setPayMoney(total);
        order.setPostFee(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setPayType(1);
        order.setDeliveryTimeType(1);
        order.setAddressId(dto.getAddressId());
        fillAddress(order, address, dto.getAddressId());
        order.setPayLatestTime(LocalDateTime.now().plusMinutes(PAY_TIMEOUT_MINUTES));
        order.setOrderSource(SeckillConstants.ORDER_SOURCE_SECKILL);
        order.setActivityId(dto.getActivityId());
        order.setSeckillItemId(dto.getSeckillItemId());
        orderMapper.insert(order);

        OrderItemDO item = new OrderItemDO();
        item.setOrderId(order.getId());
        item.setSkuId(dto.getSkuId());
        item.setSpuId(dto.getSpuId());
        item.setName(product != null ? product.getName() : "秒杀商品-" + dto.getSeckillItemId());
        item.setImage(sku != null ? sku.getPicture() : null);
        item.setAttrsText("");
        item.setCurPrice(dto.getSeckillPrice());
        item.setPrice(dto.getPrice());
        item.setQuantity(dto.getQuantity());
        item.setSubtotal(dto.getPrice().multiply(BigDecimal.valueOf(dto.getQuantity())));
        item.setRealPay(total);
        item.setProperties("[]");
        orderItemMapper.insert(item);

        int affected = seckillItemMapper.deductStock(dto.getSeckillItemId(), dto.getQuantity());
        if (affected == 0) {
            // DB 条件扣减失败说明最终库存不足，事务回滚后由消费者做 Redis 回补和消息失败标记。
            throw new BizException(ResultCode.PRODUCT_STOCK_NOT_ENOUGH);
        }
        mqMessageService.markDone(dto.getMessageId());
        registerTimeoutMessage(order.getId());
        log.info("[seckill-order] created, messageId={}, orderNo={}", dto.getMessageId(), order.getOrderNo());
        return new SeckillOrderResultDTO(order.getId(), order.getOrderNo());
    }

    private void registerTimeoutMessage(Long orderId) {
        Runnable send = () -> rabbitTemplate.convertAndSend(
                SeckillConstants.SECKILL_DELAY_EXCHANGE,
                SeckillConstants.SECKILL_DELAY_ROUTING,
                orderId.toString(),
                message -> {
                    message.getMessageProperties().setHeader(MallConstants.MQ_X_DELAY_HEADER,
                            PAY_TIMEOUT_MINUTES * 60L * 1000L);
                    return message;
                });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // 订单事务提交后再发超时消息，避免消息先到但订单尚未落库。
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    private void fillAddress(OrderDO order, UserAddressDO address, Long addressId) {
        if (address == null) {
            // 地址必须属于当前用户，不能用兜底快照绕过地址归属校验。
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "address is invalid");
        }
        order.setReceiverContact(address.getReceiver());
        order.setReceiverMobile(address.getContact());
        order.setReceiverAddress((address.getFullLocation() == null ? "" : address.getFullLocation())
                + (address.getAddress() == null ? "" : address.getAddress()));
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("addressId", address.getId());
        snapshot.put("receiver", address.getReceiver());
        snapshot.put("contact", address.getContact());
        snapshot.put("fullLocation", address.getFullLocation());
        snapshot.put("address", address.getAddress());
        try {
            order.setAddressSnapshot(objectMapper.writeValueAsString(snapshot));
        } catch (JsonProcessingException e) {
            order.setAddressSnapshot("{\"addressId\":" + address.getId() + "}");
        }
    }

    private String generateOrderNo() {
        return "SECKILL" + LocalDateTime.now().format(ORDER_NO_FORMATTER)
                + ThreadLocalRandom.current().nextInt(1000, 10000);
    }
}
