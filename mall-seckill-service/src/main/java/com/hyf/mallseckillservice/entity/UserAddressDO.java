package com.hyf.mallseckillservice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hyf.mallcommon.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户收货地址实体。
 *
 * <p>建单时按 userId + addressId 校验归属，并生成订单地址快照。</p>
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
    private Integer isDefault;
}
