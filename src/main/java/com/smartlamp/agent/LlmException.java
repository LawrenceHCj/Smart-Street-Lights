package com.smartlamp.agent;

// 大模型调用相关错误：未配置、超时、非 200、返回为空等
public class LlmException extends RuntimeException {
    public LlmException(String message) {
        super(message);
    }
}
