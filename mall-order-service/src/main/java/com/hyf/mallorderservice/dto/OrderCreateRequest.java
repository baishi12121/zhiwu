package com.hyf.mallorderservice.dto;

import lombok.Data;

import java.util.List;

/**
 * 创建订单请求 DTO（与前端 OrderCreateParams 对齐）。
 *
 * @author hyf
 */
@Data
public class OrderCreateRequest {

    /** 所选地址 ID */
    private Long addressId;

    /** 配送时间类型：1不限 2工作日 3双休或假日 */
    private Integer deliveryTimeType;

    /** 买家留言 */
    private String buyerMessage;

    /** 商品集合 */
    private List<GoodsItem> goods;

    /** 支付渠道：1支付宝 2微信 */
    private Integer payChannel;

    /** 支付方式：1在线支付 2货到付款 */
    private Integer payType;

    /** 优惠券 ID（可选） */
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
