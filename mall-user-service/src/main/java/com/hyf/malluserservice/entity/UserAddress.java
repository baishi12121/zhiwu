package com.hyf.malluserservice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hyf.mallcommon.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 收货地址实体，映射 {@code user_address} 表。
 *
 * @author hyf
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_address")
public class UserAddress extends BaseEntity {

    /** 用户 ID */
    private Long userId;

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

    /** 完整行政区（由编码派生或前端传入） */
    private String fullLocation;

    /** 详细地址 */
    private String address;

    /** 邮政编码 */
    private String postalCode;

    /** 地址标签（如：家、公司） */
    private String addressTags;

    /** 是否默认：0 否，1 是 */
    private Integer isDefault;
}
