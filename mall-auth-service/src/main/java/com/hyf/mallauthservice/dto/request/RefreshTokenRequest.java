package com.hyf.mallauthservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷新 Token 请求体。
 *
 * <p>对应 {@code POST /auth/refreshToken}：
 * <pre>{@code
 * { "refreshToken": "eyJhbGciOi..." }
 * }</pre>
 *
 * @author hyf
 */
@Data
public class RefreshTokenRequest {

    /** 刷新令牌 */
    @NotBlank(message = "refreshToken 不能为空")
    private String refreshToken;
}
