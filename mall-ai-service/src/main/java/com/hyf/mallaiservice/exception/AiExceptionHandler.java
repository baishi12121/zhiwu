package com.hyf.mallaiservice.exception;

import com.hyf.mallcommon.core.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * AI 服务对接层异常处理。
 */
@RestControllerAdvice(basePackages = "com.hyf.mallaiservice")
public class AiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AiExceptionHandler.class);

    @ExceptionHandler(PythonServiceException.class)
    public Result<Void> handlePythonServiceException(PythonServiceException e,
                                                     HttpServletRequest request) {
        String traceId = MDC.get("trace_id");
        log.warn("[trace_id={}] Python 服务调用失败: path={}, msg={}",
                traceId, request.getRequestURI(), e.getMessage());
        return Result.error(Result.CODE_INTERNAL_ERROR, e.getMessage());
    }
}
