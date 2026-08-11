package com.hyf.mallorderservice.entity;

import lombok.Data;

/**
 * 收货地址快照（值对象，下单时从 user-service 拷贝，存 order.address_snapshot）
 *
 * @author hyf
 */
@Data
public class OrderAddress {

    private Long addressId;
    private String receiverName;
    private String receiverPhone;
    private String province;
    private String city;
    private String district;
    private String detailAddress;
}
