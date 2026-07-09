package com.hyf.mallcouponservice.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class SeckillMessageDto{

    private static final long serialVersionUID = 1L;

    /**
     * 优惠券ID
     */
    private Long couponId;

    /**
     * 用户ID
     */
    private Long userId;

    // --- 构造方法 ---

    public SeckillMessageDto() {
    }

    public SeckillMessageDto(Long couponId, Long userId) {
        this.couponId = couponId;
        this.userId = userId;
    }

    // --- Getters 和 Setters ---

    public Long getCouponId() {
        return couponId;
    }

    public void setCouponId(Long couponId) {
        this.couponId = couponId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    // --- toString 方法（方便打印日志排查问题） ---

    @Override
    public String toString() {
        return "SeckillMessage{" +
                "couponId=" + couponId +
                ", userId=" + userId +
                '}';
    }
}
