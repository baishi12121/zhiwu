package com.hyf.mallorderservice.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hyf.mallcommon.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 购物车 DO — 对应 {@code user_cart} 表。
 *
 * <p>订单服务用于查询用户选中的购物车商品，生成订单明细。
 *
 * @author hyf
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_cart")
public class UserCartDO extends BaseEntity {

    private Long userId;
    private Long skuId;
    private Integer count;
    /** 1选中 0未选中 */
    private Integer selected;
    private BigDecimal price;
}
