package com.smartlamp.agent.actions;

import java.util.Set;

// Action 白名单：Agent 一切可执行操作的唯一来源。
//  - risk 字段定义风险等级（READ / LOW_WRITE / HIGH_WRITE）
//  - allowed=false 表示已登记但当前阶段不开放（创建即拒绝）
//  - allowedArgumentKeys 定义该 Action 的 arguments 允许键（结构化参数，杜绝万能命令）
// 安全红线：任何未在此枚举登记的操作都不存在，LLM 无法"发明"新命令。
public enum ActionType {

    // ============ 只读（可直接执行） ============
    QUERY_DEVICES("查询设备", ActionRisk.READ, true, Set.of()),

    // ============ 低风险写（必须用户确认） ============
    TURN_ON_LIGHT("单台路灯开灯", ActionRisk.LOW_WRITE, true, Set.of()),
    TURN_OFF_LIGHT("单台路灯关灯", ActionRisk.LOW_WRITE, true, Set.of()),
    // 批量开/关（权限调整后开放）：低风险写，必须用户二次确认后执行（确认入口在聊天卡片）
    TURN_OFF_ALL("关闭全部设备", ActionRisk.LOW_WRITE, true, Set.of()),
    TURN_ON_ALL("打开全部设备", ActionRisk.LOW_WRITE, true, Set.of()),
    // 配置类（阶段20 开放）：只开放光照阈值与自动模式，其余配置仍不开放
    UPDATE_LUX_THRESHOLD("修改光照阈值", ActionRisk.LOW_WRITE, true, Set.of("value")),
    UPDATE_AUTO_MODE("修改自动模式", ActionRisk.LOW_WRITE, true, Set.of("enabled")),

    // ============ 高风险写（Agent 禁止执行） ============
    BULK_UPDATE_DEVICES("批量修改设备", ActionRisk.HIGH_WRITE, false, Set.of()),
    DELETE_DEVICE("删除设备", ActionRisk.HIGH_WRITE, false, Set.of()),
    UNBIND_DEVICE("解绑设备", ActionRisk.HIGH_WRITE, false, Set.of());

    private final String displayName;
    private final ActionRisk risk;
    private final boolean allowed;
    private final Set<String> allowedArgumentKeys;

    ActionType(String displayName, ActionRisk risk, boolean allowed, Set<String> allowedArgumentKeys) {
        this.displayName = displayName;
        this.risk = risk;
        this.allowed = allowed;
        this.allowedArgumentKeys = allowedArgumentKeys;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ActionRisk getRisk() {
        return risk;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public Set<String> getAllowedArgumentKeys() {
        return allowedArgumentKeys;
    }
}
