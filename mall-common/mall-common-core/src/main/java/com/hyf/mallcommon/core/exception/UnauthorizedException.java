package com.hyf.mallcommon.core.exception;

import com.hyf.mallcommon.core.result.ResultCode;
import lombok.Getter;

/**
 * 全局未认证异常 —— token 缺失或失效。
 *
 * <p>{@link BizException} 的特化子类，固定使用 {@link ResultCode#UNAUTHORIZED}(401) 错误码。
 * 供 {@code mall-common-security} 的鉴权拦截器在 token 校验失败时抛出，
 * 全局异常处理器会把它转成 {@code Result.error(401, ...)} 响应。
 *
 * <p>需要表达“已登录但无权限”的场景应使用 {@link ResultCode#FORBIDDEN}，
 * 通过抛 {@code new BizException(ResultCode.FORBIDDEN)} 表达，而非本类。
 *
 * @author hyf
 * @see BizException
 * @see ResultCode#UNAUTHORIZED
 */
@Getter
public class UnauthorizedException extends BizException {

    /** 带自定义提示消息的未认证异常 */
    public UnauthorizedException(String message) {
        super(ResultCode.UNAUTHORIZED, message);
    }

    /** 使用默认未登录提示的未认证异常 */
    public UnauthorizedException() {
        super(ResultCode.UNAUTHORIZED, ResultCode.UNAUTHORIZED.getMessage());
    }
}
