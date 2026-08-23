const knowledgeBase = [
  {
    id: "kb-offline-troubleshooting",
    title: "设备离线排查",
    category: "告警处理",
    content: "先确认设备供电与网关连接，再检查最近心跳时间、设备绑定关系和通信链路。",
    keywords: ["离线", "心跳", "断开", "不上线", "告警"],
    source: "内部知识库",
  },
  {
    id: "kb-lux-auto-control",
    title: "光照联动控制",
    category: "光照联动控制",
    content: "自动控制依据光照阈值判断，低于阈值开灯，高于阈值加滞回值后关灯。",
    keywords: ["阈值", "自动", "开灯", "关灯", "光照"],
    source: "内部知识库",
  },
  {
    id: "kb-manual-control-ack",
    title: "手动控制回执",
    category: "设备维护",
    content: "手动控制应生成 commandId，并记录下发时间、执行状态和完成时间。",
    keywords: ["手动", "控制", "命令", "回执"],
    source: "内部知识库",
  },
];

module.exports = {
  knowledgeBase,
};
