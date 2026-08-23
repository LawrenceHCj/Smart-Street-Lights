package com.smartlamp.exception;

import com.smartlamp.dto.ApiResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 处理缺少请求参数，例如 /api/light/history 缺少 start 或 end
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResponse<Object> handleMissingParams(MissingServletRequestParameterException ex) {
        return ApiResponse.error(400, "缺少必要参数: " + ex.getParameterName());
    }

    // 处理参数类型错误，例如 start 传了字符串
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResponse<Object> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ApiResponse.error(400, "参数类型错误: " + ex.getName());
    }

    // 处理其他所有异常
    @ExceptionHandler(Exception.class)
    public ApiResponse<Object> handleException(Exception ex) {
        return ApiResponse.error(500, "服务器内部错误: " + ex.getMessage());
    }
}