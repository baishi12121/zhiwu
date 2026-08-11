package com.hyf.malluserservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 购物车项响应。
 *
 * <p>字段对齐前端 {@code CartItem} 类型，由联表查询直接映射。
 *
 * @author hyf
 */
@Data
@Builder
public class CartItemResponse {

    /** 商品 ID（即 productId，前端用于导航 /goods?id=） */
    private String id;

    /** SKU ID */
    private String skuId;

    /** 商品名称 */
    private String name;

    /** SKU 图片 */
    private String picture;

    /** 数量 */
    private Integer count;

    /** 加入时价格快照 */
    private BigDecimal price;

    /** 当前 SKU 价格 */
    private BigDecimal nowPrice;

    /** SKU 库存 */
    private Integer stock;

    /** 是否选中 */
    private Boolean selected;

    /** 规格文字（如 "瓷白色 S"） */
    private String attrsText;

    /** 是否有效（SKU 与商品均为上架状态） */
    private Boolean isEffective;
}
