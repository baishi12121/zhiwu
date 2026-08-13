package com.hyf.mallseckillservice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hyf.mallcommon.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 订单明细表实体。
 *
 * <p>秒杀建单时写入商品快照，库存回补时通过它获取实际购买数量。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_item")
public class OrderItemDO extends BaseEntity {

    private Long orderId;
    private Long skuId;
    private Long spuId;
    private String name;
    private String image;
    private String attrsText;
    private BigDecimal curPrice;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal subtotal;
    private BigDecimal realPay;
    private String properties;
}
