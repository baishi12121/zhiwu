package com.hyf.mallaiservice.exception;

/**
 * Spring Boot 调用 Python FastAPI 失败时的统一异常。
 */
public class PythonServiceException extends RuntimeException {

    public PythonServiceException(String message) {
        super(message);
    }

    public PythonServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
