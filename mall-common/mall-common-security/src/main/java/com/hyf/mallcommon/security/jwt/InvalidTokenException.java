package com.hyf.mallcommon.security.jwt;

/**
 * token 非法异常 —— token 缺失、过期、签名错误或类型不符。
 *
 * <p>由 {@link JwtTokenService} 在解析/校验失败时抛出；上层拦截器捕获后转成 401 响应。
 *
 * <p>不直接继承 {@code BizException}，因为本异常发生在 security 模块，
 * 而 {@code BizException} 位于 mall-common-core 的 exception 包；
 * 为保持 security 模块对 token 语义的自包含描述，这里独立定义，
 * 由拦截器统一转成 {@code UnauthorizedException} 再交给全局异常处理器。
 *
 * @author hyf
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
