package com.smartlamp.dto;

import lombok.Data;

// 待确认操作信息（阶段21 联调落地）：聊天响应中的结构化 action 字段。
// 前端据此在对话中渲染确认卡片（[确认执行] [取消]），按钮按 actionId 调确认/取消接口。
// 仅在产生待确认操作时返回（当前：批量关闭、修改阈值/自动模式）；开灯/关灯为自动执行，不返回。
@Data
public class PendingActionInfo {
    private String actionId;       // 确认/取消接口的唯一凭证
    private String actionType;     // 白名单操作类型
    private String targetId;       // 目标标识（设备编号 / all / system）
    private String summary;        // 推荐展示文案（如"关闭全部设备（3 台在线）"）
    private String riskLevel;      // 风险等级
    private Long expiresAt;        // 有效期（epoch 毫秒，过期按钮置灰）
    private String status;         // 当前状态（PENDING_CONFIRMATION）
    private String originalState;  // 操作前状态快照（用于卡片"当前状态"展示）
    private String targetState;    // 目标状态（用于卡片"目标状态"展示）
}
