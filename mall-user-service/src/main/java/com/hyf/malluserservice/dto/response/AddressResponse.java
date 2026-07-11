package com.hyf.malluserservice.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 收货地址响应。
 *
 * <p>字段对齐前端 {@code AddressItem} 类型。
 *
 * @author hyf
 */
@Data
@Builder
public class AddressResponse {

    /** 地址 ID（序列化为 String 避免 JavaScript 超长整型精度丢失） */
    private String id;

    /** 收货人 */
    private String receiver;

    /** 联系方式 */
    private String contact;

    /** 省份编码 */
    private String provinceCode;

    /** 城市编码 */
    private String cityCode;

    /** 区/县编码 */
    private String countyCode;

    /** 完整行政区 */
    private String fullLocation;

    /** 详细地址 */
    private String address;

    /** 邮政编码 */
    private String postalCode;

    /** 地址标签 */
    private String addressTags;

    /** 是否默认：0 否，1 是 */
    private Integer isDefault;
}
