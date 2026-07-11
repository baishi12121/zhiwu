package com.hyf.mallcommon.web.handler;

import com.hyf.mallcommon.core.exception.BizException;
import com.hyf.mallcommon.core.exception.UnauthorizedException;
import com.hyf.mallcommon.core.result.Result;
import com.hyf.mallcommon.core.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * <p>所有服务依赖 mall-common-web 后自动生效。
 *
 * @author hyf
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 未认证异常 —— HTTP 401。
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Result<Void>> handleUnauthorized(UnauthorizedException e, HttpServletRequest request) {
        log.warn("[auth] {} -> {}", request.getRequestURI(), e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.error(ResultCode.UNAUTHORIZED.getCode(), e.getMessage()));
    }

    /**
     * 业务异常 —— 按声明的 code 返回，HTTP 状态码与业务码对齐。
     * <p>例如 auth 失败 code=1001 → HTTP 401，库存不足 code=2001 → HTTP 409 等。
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBizException(BizException e, HttpServletRequest request) {
        log.warn("[biz] {} -> code={}, msg={}", request.getRequestURI(), e.getCode(), e.getMessage());
        int httpStatus = mapToHttpStatus(e.getCode());
        return ResponseEntity.status(httpStatus)
                .body(Result.error(e.getCode(), e.getMessage()));
    }

    /**
     * 将业务错误码映射为 HTTP 状态码。
     */
    private static int mapToHttpStatus(int bizCode) {
        return switch (bizCode) {
            case 401, 1003 -> 401;         // 未登录 / token 失效 → 前端清理登录态
            case 403 -> 403;
            case 404, 2002 -> 404;         // 商品下架
            case 2001 -> 409;              // 库存不足 → Conflict
            case 429 -> 429;
            default -> 400;                // 其他业务错误（含 1001 密码错误） → Bad Request
        };
    }

    /**
     * 参数校验失败 @Valid / @RequestBody
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("[valid] {}", msg);
        return Result.error(ResultCode.BAD_REQUEST.getCode(), msg);
    }

    /**
     * 表单参数校验失败
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return Result.error(ResultCode.BAD_REQUEST.getCode(), msg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.error(ResultCode.BAD_REQUEST.getCode(),
                "缺少必填参数: " + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.error(ResultCode.BAD_REQUEST.getCode(),
                "参数类型错误: " + e.getName());
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(NoHandlerFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(ResultCode.NOT_FOUND.getCode(), ResultCode.NOT_FOUND.getMessage()));
    }

    /**
     * 兜底
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("[unhandled] {} ", request.getRequestURI(), e);
        return Result.error(ResultCode.INTERNAL_ERROR.getCode(),
                ResultCode.INTERNAL_ERROR.getMessage());
    }
}
