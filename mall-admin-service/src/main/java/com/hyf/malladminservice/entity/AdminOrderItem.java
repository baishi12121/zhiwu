package com.hyf.malladminservice.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hyf.mallcommon.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 管理后台订单明细实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_item")
public class AdminOrderItem extends BaseEntity {

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

    @TableField(exist = false)
    private String skuCode;
}
