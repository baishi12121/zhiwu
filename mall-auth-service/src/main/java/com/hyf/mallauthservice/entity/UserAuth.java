package com.hyf.mallauthservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户登录凭证实体，映射 {@code user_auth} 表。
 *
 * <p>支持多端登录：USERNAME（密码）、PHONE（短信）、WECHAT（微信 openid）。
 * 该表无 {@code update_time} 列，因此不继承 {@link com.hyf.mallcommon.mybatis.entity.BaseEntity}。
 *
 * @author hyf
 */
@Data
@TableName("user_auth")
public class UserAuth implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID，关联 {@code user.id} */
    private Long userId;

    /** 认证类型：USERNAME / PHONE / WECHAT */
    private String identityType;

    /** 认证标识：用户名 / 手机号 / 微信 openid */
    private String identifier;

    /** 凭证：密码 MD5 / 短信验证码 / 微信会话密钥 */
    private String credential;

    /** 创建时间 */
    private LocalDateTime createTime;
}
