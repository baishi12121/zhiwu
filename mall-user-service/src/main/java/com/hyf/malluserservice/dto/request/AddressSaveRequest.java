package com.hyf.malluserservice.dto.request;

import lombok.Data;

/**
 * 收货地址保存请求（创建 / 修改共用）。
 *
 * <p>字段对齐前端 {@code AddressParams} 类型。
 *
 * @author hyf
 */
@Data
public class AddressSaveRequest {

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

    /** 完整行政区（前端 picker 展示值，如"广东省 深圳市 南山区"） */
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
