package com.hyf.malluserservice.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户登录会话实体，对应 user_token 表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserToken implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 登录凭证(UUID)
     */
    private String token;

    /**
     * 登录端: H5/MP/APP
     */
    private String client;

    /**
     * 过期时间
     */
    private LocalDateTime expireAt;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
