package com.smartlamp.agent.actions;

// Action 被安全规则拒绝时抛出（高风险、未开放、未确认、已过期、参数非法等）
public class ActionRejectedException extends RuntimeException {

    public ActionRejectedException(String message) {
        super(message);
    }

    public ActionRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
