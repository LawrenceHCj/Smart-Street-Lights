package com.smartlamp.exception;

import com.smartlamp.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);


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

    // 处理业务参数类错误，例如 AI 问答空问题
    @ExceptionHandler(BadRequestException.class)
    public ApiResponse<Object> handleBadRequest(BadRequestException ex) {
        return ApiResponse.error(400, ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Object> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ApiResponse.error(400, "请求体缺失或格式错误");
    }

    // 处理其他所有异常
    @ExceptionHandler(Exception.class)
    public ApiResponse<Object> handleException(Exception ex) {
        log.error("未处理的服务器异常", ex);
        return ApiResponse.error(500, "服务器内部错误");
    }
}
