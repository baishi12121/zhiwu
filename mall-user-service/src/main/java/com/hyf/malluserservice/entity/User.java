package com.hyf.malluserservice.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户实体类，对应数据库 user 表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 加密后的密码
     */
    private String password;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 性别：0未知 1男 2女
     */
    private Integer gender;

    /**
     * 账户余额（用于模拟下单扣款）
     */
    private BigDecimal balance;

    /**
     * 会员等级：NORMAL/SILVER/GOLD/DIAMOND
     */
    private String memberLevel;

    /**
     * 成长值
     */
    private Integer growth;

    /**
     * 帐号状态：0-禁用，1-正常
     */
    private Integer status;

    /**
     * 最近一次登录时间
     */
    private LocalDateTime lastLoginAt;

    /**
     * 注册时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
