package com.hyf.mallorderservice.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hyf.mallcommon.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 订单明细 DO — 对应 {@code order_item} 表。
 *
 * @author hyf
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_item")
public class OrderItemDO extends BaseEntity {

    /** 订单 ID */
    private Long orderId;
    /** SKU ID */
    private Long skuId;
    /** SPU ID */
    private Long spuId;
    /** 商品名称快照 */
    private String name;
    /** 商品图片快照 */
    private String image;
    /** 规格文字快照（颜色:瓷白色 尺寸：8寸） */
    private String attrsText;
    /** 实付单价 */
    private BigDecimal curPrice;
    /** 原单价 */
    private BigDecimal price;
    /** 数量 */
    private Integer quantity;
    /** 原价小计 */
    private BigDecimal subtotal;
    /** 实付小计 */
    private BigDecimal realPay;
    /** 规格快照（JSON: [{name, valueName}]） */
    private String properties;
}
