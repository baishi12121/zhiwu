package com.hyf.mallorderservice.dto;

import lombok.Data;

import java.util.List;

/**
 * 订单预览请求 DTO。
 *
 * <p>goods 为空时从购物车取选中商品；非空时为立即购买场景。
 *
 * @author hyf
 */
@Data
public class OrderPreviewRequest {

    /** 商品列表（立即购买时传；为空则从购物车取选中项） */
    private List<GoodsItem> goods;

    /** 指定地址 ID（可选） */
    private Long addressId;

    /** 指定优惠券 ID（可选） */
    private Long couponId;

    /** 商品项 */
    @Data
    public static class GoodsItem {
        /** SKU ID */
        private Long skuId;
        /** 数量 */
        private Integer count;
    }
}
