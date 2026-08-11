package com.hyf.mallmarketingservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户优惠券 DO — 对应 {@code user_coupon} 表。
 *
 * <p>不继承 {@code BaseEntity}：该表无 create_time/update_time 列（用 grab_time 记录领取时间），
 * 继承会导致 MetaObjectHandler 自动填充时报 "Unknown column 'create_time'"。
 *
 * @author hyf
 */
@Data
@TableName("user_coupon")
public class UserCoupon implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.NONE)
    private Long id;
    private Long userId;
    private Long couponId;
    /** 0未用 1已用 2过期 */
    private Integer status;
    private LocalDateTime grabTime;
    private LocalDateTime useTime;
    private Long orderId;
}
