package com.hyf.mallcommon.core.result;

/**
 * 错误码抽象接口。
 *
 * <p>统一业务异常与响应结果的“码 + 消息”契约：任何枚举只要实现本接口，即可作为
 * {@link BizException}、{@link Result#error(ErrorCode, String)} 的入参，
 * 调用方不必关心具体类型是 {@link ResultCode} 还是各业务域自定义的错误码枚举。
 *
 * <p>实现示例：
 * <pre>{@code
 * public enum OrderErrorCode implements ErrorCode {
 *     ORDER_NOT_FOUND(4001, "订单不存在");
 *     private final int code;
 *     private final String message;
 *     OrderErrorCode(int code, String message) { this.code = code; this.message = message; }
 *     @Override public int getCode() { return code; }
 *     @Override public String getMessage() { return message; }
 * }
 * }</pre>
 *
 * <p>使用时直接抛出：{@code throw new BizException(OrderErrorCode.ORDER_NOT_FOUND);}
 *
 * <p>设计动机：原本 {@link BizException} 只接收 {@code int code}，错误码散落在调用点难以维护；
 * 引入本接口后，错误码集中在枚举里管理、可被 IDE 联想、可在文档中统一枚举。
 *
 * @author hyf
 * @see ResultCode
 * @see BizException
 */
public interface ErrorCode {

    /**
     * 返回错误码。
     *
     * <p>约定：200 成功；4xx 客户端错误；5xx 服务端错误；1xxx~4xxx 为各业务域错误码。
     *
     * @return 错误码
     */
    int getCode();

    /**
     * 返回面向用户的默认错误消息。
     *
     * <p>调用方可在抛异常时覆盖此消息（{@link BizException#BizException(ErrorCode, String)}）。
     *
     * @return 默认错误消息
     */
    String getMessage();
}
