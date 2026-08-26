package com.smartlamp.entity.enums;

public enum CommandStatus {
    DISPATCHED,   // 已下发
    ACKED,        // 设备已收到
    SUCCESS,      // 执行成功
    FAILED,       // 执行失败
    TIMEOUT       // 超时
}