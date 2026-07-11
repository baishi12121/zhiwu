package com.hyf.mallcommon.core.exception;

import com.hyf.mallcommon.core.result.ErrorCode;
import com.hyf.mallcommon.core.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>用于在 service / domain 层表达“可预期的业务错误”（如库存不足、优惠券已抢完、
 * 订单状态非法流转等）。抛出后由 mall-common-web 的 {@code GlobalExceptionHandler}
 * 拦截并转成 {@code Result.error(code, message)} 响应，因此业务代码只需
 * {@code throw new BizException(ResultCode.XXX)}，无需手写 try/catch 与响应转换。
 *
 * <p>与 {@link ErrorCode} 配合：错误码统一由实现该接口的枚举（如 {@link ResultCode}
 * 或各业务域自定义枚举）提供，避免裸 {@code int} 散落调用点。
 *
 * <p>不携带 cause 链时建议用 {@link #BizException(ErrorCode)}；若需保留底层异常
 * （例如包装 RPC 调用异常），使用 {@link #BizException(ErrorCode, String, Throwable)}。
 *
 * @author hyf
 * @see ErrorCode
 * @see ResultCode
 * @see UnauthorizedException
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    /** 默认按 BAD_REQUEST(400) 抛出，仅给定消息 */
    public BizException(String message) {
        super(message);
        this.code = ResultCode.BAD_REQUEST.getCode();
    }

    /** 按指定错误码与消息抛出 */
    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    /** 按 {@link ErrorCode} 抛出，消息取自错误码自身 */
    public BizException(ErrorCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    /** 按 {@link ErrorCode} 抛出，并覆盖默认消息 */
    public BizException(ErrorCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    /** 按 {@link ErrorCode} 抛出，覆盖消息并保留底层 cause */
    public BizException(ErrorCode resultCode, String message, Throwable cause) {
        super(message, cause);
        this.code = resultCode.getCode();
    }
}
