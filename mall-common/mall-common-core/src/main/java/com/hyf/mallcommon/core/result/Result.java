package com.hyf.mallcommon.core.result;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一 API 响应包装类。
 *
 * <p>所有 Controller 方法均返回 {@code Result<T>}，对外保证响应结构统一为
 * {@code { "code": int, "message": string, "data": T }}：前端按 {@link #code}
 * 判断成败、按 {@link #data} 取业务数据、按 {@link #message} 展示提示。
 *
 * <p>构造方式：
 * <ul>
 *   <li>正常返回 —— {@link #success(Object)} / {@link #success()}；</li>
 *   <li>异常返回 —— 由 mall-common-web 的 {@code GlobalExceptionHandler} 拦截异常后
 *       调用 {@link #error(int, String)} 构造，业务代码无需手动把异常转成响应；</li>
 *   <li>需要直接返回错误时（如参数前置校验未通过）也可调用 {@link #error(ErrorCode)}。</li>
 * </ul>
 *
 * <p>响应码语义见 {@link ResultCode}：200 成功，4xx 客户端错误，5xx 服务端错误，
 * 1xxx~4xxx 为业务域错误码。判断是否成功请使用 {@link #isSuccess()} 而非裸比较。
 *
 * @param <T> 业务数据类型
 * @author hyf
 * @see ResultCode
 * @see ErrorCode
 */
@Data
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 成功码 */
    public static final int CODE_SUCCESS = 200;
    /** 参数校验失败码 */
    public static final int CODE_BAD_REQUEST = 400;
    /** 未登录码 */
    public static final int CODE_UNAUTHORIZED = 401;
    /** 无权限码 */
    public static final int CODE_FORBIDDEN = 403;
    /** 资源不存在码 */
    public static final int CODE_NOT_FOUND = 404;
    /** 限流码 */
    public static final int CODE_TOO_MANY_REQUESTS = 429;
    /** 内部异常码 */
    public static final int CODE_INTERNAL_ERROR = 500;

    /** 业务结果码，200 表示成功 */
    private int code;
    /** 提示消息，成功为 "ok"，失败为可对外展示的错误描述 */
    private String message;
    /** 业务数据，失败时通常为 null */
    private T data;

    public Result() {
    }

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // ---------- 工厂方法：成功 ----------

    /** 构造无数据的成功响应 */
    public static <T> Result<T> success() {
        return new Result<>(CODE_SUCCESS, "ok", null);
    }

    /** 构造带数据的成功响应 */
    public static <T> Result<T> success(T data) {
        return new Result<>(CODE_SUCCESS, "ok", data);
    }

    /** 构造带数据与自定义消息的成功响应 */
    public static <T> Result<T> success(T data, String message) {
        return new Result<>(CODE_SUCCESS, message, data);
    }

    // ---------- 工厂方法：失败 ----------

    /** 按指定错误码与消息构造失败响应 */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    /** 按 {@link ErrorCode} 构造失败响应，消息取自错误码自身 */
    public static <T> Result<T> error(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /** 按 {@link ErrorCode} 构造失败响应，并覆盖默认消息 */
    public static <T> Result<T> error(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), message, null);
    }

    /** 以 500 内部错误构造失败响应 */
    public static <T> Result<T> error(String message) {
        return new Result<>(CODE_INTERNAL_ERROR, message, null);
    }

    /** {@code code == 200} 判定成功 */
    public boolean isSuccess() {
        return code == CODE_SUCCESS;
    }
}
