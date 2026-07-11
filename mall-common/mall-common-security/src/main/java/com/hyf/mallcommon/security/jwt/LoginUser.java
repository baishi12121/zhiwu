package com.hyf.mallcommon.security.jwt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 待签发 token 的登录用户信息。
 *
 * <p>auth-service 校验账号密码/短信/微信成功后，把用户身份封装为本对象交给
 * {@link JwtTokenService} 签发 token；token 中只携带 userId、nickname 等非敏感信息，
 * 不放密码/手机号。后续业务服务解析 token 时把这些字段还原到登录上下文。
 *
 * @author hyf
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser {

    /** 用户 ID（sub claim） */
    private Long userId;
    /** 昵称（写入 token 供下游展示） */
    private String nickname;
    /** 头像 URL */
    private String avatar;
    /** 会员等级：NORMAL/SILVER/GOLD/DIAMOND */
    private String memberLevel;
    /** 登录客户端：miniapp / app */
    private String client;
}
