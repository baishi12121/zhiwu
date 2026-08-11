package com.hyf.mallcommon.core.result;

import lombok.Getter;

/**
 * 业务结果码与消息枚举，实现 {@link ErrorCode}。
 *
 * <p>对应 {@code doc/API接口文档.md} §1.4 错误码表，是全系统错误码的权威定义处。
 * 业务代码通过 {@link BizException#BizException(ErrorCode)} 抛出，
 * 由全局异常处理器转成 {@link Result#error(int, String)} 响应。
 *
 * <p>码段约定：
 * <ul>
 *   <li>2xx/4xx/5xx：沿用 HTTP 语义（成功 / 客户端错误 / 服务端错误）；</li>
 *   <li>1xxx：用户域（认证、账号、Token）；</li>
 *   <li>2xxx：商品域（库存、上下架）；</li>
 *   <li>3xxx：优惠券域（抢券、使用）；</li>
 *   <li>4xxx：订单域（状态流转）；</li>
 *   <li>5xxx：支付域（下单、回调、退款）。</li>
 * </ul>
 *
 * <p>新增业务错误码时请遵守码段约定并补全 message；如该域码段已较多，
 * 可仿照本枚举为该域单独建立实现 {@link ErrorCode} 的枚举，避免单文件膨胀。
 *
 * @author hyf
 * @see ErrorCode
 */
@Getter
public enum ResultCode implements ErrorCode {

    SUCCESS(200, "ok"),
    BAD_REQUEST(400, "参数校验失败"),
    UNAUTHORIZED(401, "未登录"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后重试"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // ---------- 用户域 1xxx ----------
    USER_AUTH_FAILED(1001, "用户名或密码错误"),
    USER_EXISTS(1002, "用户已存在"),
    TOKEN_INVALID(1003, "Token 失效"),

    // ---------- 商品域 2xxx ----------
    PRODUCT_STOCK_NOT_ENOUGH(2001, "库存不足"),
    PRODUCT_OFFLINE(2002, "商品已下架"),

    // ---------- 优惠券域 3xxx ----------
    COUPON_SOLD_OUT(3001, "优惠券已抢完"),
    COUPON_DUPLICATE_GRAB(3002, "已抢过该券"),
    COUPON_USED(3003, "优惠券已使用"),

    // ---------- 订单域 4xxx ----------
    ORDER_STATUS_ILLEGAL(4001, "订单状态非法流转"),

    // ---------- 支付域 5xxx ----------
    PAY_ORDER_NOT_FOUND(5001, "支付订单不存在"),
    PAY_ORDER_ALREADY_PAID(5002, "订单已支付，请勿重复支付"),
    PAY_CREATE_FAILED(5003, "支付下单失败"),
    PAY_NOTIFY_VERIFY_FAILED(5004, "支付回调验签失败"),
    PAY_REFUND_FAILED(5005, "退款申请失败"),
    PAY_REFUND_NOT_FOUND(5006, "退款记录不存在");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
