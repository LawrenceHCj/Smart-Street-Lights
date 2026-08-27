package com.smartlamp.exception;

// 业务参数类错误（如 AI 问答空问题），由 GlobalExceptionHandler 统一返回 code=400
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
