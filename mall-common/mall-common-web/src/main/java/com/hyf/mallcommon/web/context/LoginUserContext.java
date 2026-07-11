package com.hyf.mallcommon.web.context;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 当前登录用户上下文（线程内传递）
 *
 * <p>由 {@code mall-common-security} 的拦截器写入，业务层只读。
 *
 * @author hyf
 */
@Data
public class LoginUserContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;
    private String username;
    private String nickname;
    private String avatar;
    /** NORMAL/SILVER/GOLD/DIAMOND */
    private String memberLevel;
    /** 客户端：miniapp / app */
    private String client;

    public static LoginUserContext of(Long userId, String nickname) {
        LoginUserContext ctx = new LoginUserContext();
        ctx.userId = userId;
        ctx.nickname = nickname;
        return ctx;
    }
}
