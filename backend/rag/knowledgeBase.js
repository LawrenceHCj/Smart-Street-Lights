const knowledgeBase = [
  {
    title: "设备离线排查",
    keywords: ["离线", "心跳", "断开", "不上线", "告警"],
    content: "先确认设备供电与网关连接，再检查最近心跳时间、设备绑定关系和通信链路。",
  },
  {
    title: "光照联动控制",
    keywords: ["阈值", "自动", "开灯", "关灯", "光照"],
    content: "自动控制依据光照阈值判断，低于阈值开灯，高于阈值加滞回值后关灯。",
  },
  {
    title: "手动控制回执",
    keywords: ["手动", "控制", "命令", "回执"],
    content: "手动控制应生成 commandId，并记录下发时间、执行状态和完成时间。",
  },
];

module.exports = {
  knowledgeBase,
};
