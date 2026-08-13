package com.hyf.mallseckillservice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hyf.mallcommon.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 商品 SPU 简化实体。
 *
 * <p>消费者建单时用于补充订单明细里的商品名称等快照信息。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product")
public class ProductDO extends BaseEntity {

    private String name;
    private BigDecimal price;
}
