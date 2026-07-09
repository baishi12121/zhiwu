package com.hyf.mallorderservice.common;

import java.io.Serializable;

/**
 * 统一接口返回结果封装类
 *
 * @param <T> 数据实体类型
 */
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 状态码：200-成功，500-系统异常
     */
    private Integer code;

    /**
     * 响应消息提示
     */
    private String message;

    /**
     * 具体的业务响应数据
     */
    private T data;

    public Result() {
    }

    public Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 构建成功无数据的响应结果
     *
     * @return Result对象
     */
    public static <T> Result<T> success() {
        return new Result<>(200, "success", null);
    }

    /**
     * 构建成功有数据的响应结果
     *
     * @param data 数据实体
     * @return Result对象
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    /**
     * 构建失败带定制消息的响应结果
     *
     * @param message 失败提示消息
     * @return Result对象
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }

    /**
     * 构建失败带状态码及定制消息的响应结果
     *
     * @param code 状态码
     * @param message 失败提示消息
     * @return Result对象
     */
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null);
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
