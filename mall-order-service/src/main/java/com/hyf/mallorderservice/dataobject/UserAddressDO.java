package com.hyf.mallorderservice.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hyf.mallcommon.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 收货地址 DO — 对应 {@code user_address} 表。
 *
 * <p>订单服务用于查询用户地址列表、生成订单地址快照。
 *
 * @author hyf
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_address")
public class UserAddressDO extends BaseEntity {

    private Long userId;
    private String receiver;
    private String contact;
    private String provinceCode;
    private String cityCode;
    private String countyCode;
    private String fullLocation;
    private String address;
    private String postalCode;
    private String addressTags;
    /** 1默认 0否 */
    private Integer isDefault;
}
