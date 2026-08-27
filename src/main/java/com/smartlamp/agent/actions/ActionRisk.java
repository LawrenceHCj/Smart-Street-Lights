package com.smartlamp.agent.actions;

// Action 风险等级：
// READ        —— 只读查询，可以直接执行，无需确认
// LOW_WRITE   —— 低风险写操作，必须用户确认后才能执行
// HIGH_WRITE  —— 高风险写操作，当前 Agent 一律禁止执行
public enum ActionRisk {
    READ("只读查询，可直接执行"),
    LOW_WRITE("低风险写操作，必须用户确认"),
    HIGH_WRITE("高风险写操作，Agent 禁止执行");

    private final String description;

    ActionRisk(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
