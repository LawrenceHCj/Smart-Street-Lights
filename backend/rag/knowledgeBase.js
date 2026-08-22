// 知识条目字段说明（以后可继续扩展条目）：
// id       唯一标识（kb- 前缀 + 短横线命名）
// title    条目标题（响应 sources 引用标题）
// category 分类，当前固定四类：路灯维护 / 设备状态 / 告警处理 / 光照联动控制
// content  知识正文（本地回答与大模型上下文共用）
// keywords 检索关键词（retriever 按命中数量评分）
// source   知识来源标识（当前均为内部知识库）
const knowledgeBase = [
  // ============ 告警处理 ============
  {
    id: "kb-offline-troubleshooting",
    title: "设备离线排查",
    category: "告警处理",
    content:
      "先确认设备供电与网关连接，再检查最近心跳时间（系统默认心跳超时15秒）、设备绑定关系和通信链路；超过心跳超时时间未上报心跳的设备会被标记为离线并生成离线告警。",
    keywords: ["离线", "心跳", "断开", "不上线", "告警"],
    source: "内部知识库",
  },
  {
    id: "kb-alert-flow",
    title: "告警处理流程",
    category: "告警处理",
    content:
      "设备超过心跳超时时间（默认15秒）未上报心跳后，系统自动标记离线并生成OFFLINE类型告警（HIGH严重级）；设备心跳恢复后告警自动变为RECOVERED。处理流程：查看告警列表定位设备 → 检查供电与网关 → 恢复通信 → 确认告警状态变为已恢复。",
    keywords: ["告警", "处理流程", "OFFLINE", "RECOVERED", "恢复", "严重"],
    source: "内部知识库",
  },

  // ============ 光照联动控制 ============
  {
    id: "kb-lux-auto-control",
    title: "光照联动控制",
    category: "光照联动控制",
    content:
      "自动控制依据光照阈值判断：低于阈值开灯，高于阈值加滞回值后关灯。系统默认光照阈值为120、滞回值为35，可在配置界面调整。",
    keywords: ["阈值", "自动", "开灯", "关灯", "光照"],
    source: "内部知识库",
  },
  {
    id: "kb-auto-control-abnormal",
    title: "自动开关异常",
    category: "光照联动控制",
    content:
      "自动开关不生效时按顺序检查：①自动控制开关（autoControl）是否开启；②设备是否在线且已绑定（离线或未绑定设备不参与自动控制）；③当前光照值与阈值的关系：低于阈值应开灯，高于阈值加滞回值后应关灯；④控制记录中是否生成了AUTO模式指令。",
    keywords: ["自动", "开关", "不生效", "autoControl", "联动"],
    source: "内部知识库",
  },
  {
    id: "kb-threshold-config",
    title: "阈值配置问题",
    category: "光照联动控制",
    content:
      "光照阈值（luxThreshold）允许范围10-500，滞回值（hysteresis）允许范围0-200，心跳超时（heartbeatTimeoutMs）允许范围5000-120000毫秒，超出范围的配置会被自动限制到边界值。光照值在阈值附近波动导致设备频繁开关时，可适当增大滞回值。",
    keywords: ["阈值", "配置", "滞回", "范围", "hysteresis", "luxThreshold"],
    source: "内部知识库",
  },
  {
    id: "kb-lux-sensor-abnormal",
    title: "光照传感器异常",
    category: "光照联动控制",
    content:
      "系统未指定具体光照传感器型号，按通用方式排查：①观察该设备光照值是否长期为0或恒定不变；②与同区域其他设备光照值对比，偏差明显时怀疑传感器故障或安装位置遮挡；③确认传感器表面清洁、无遮挡；④确认为硬件故障时按实际型号联系供应商处理。",
    keywords: ["光照", "传感器", "异常", "读数", "采集"],
    source: "内部知识库",
  },

  // ============ 设备状态 ============
  {
    id: "kb-device-state-abnormal",
    title: "设备状态异常",
    category: "设备状态",
    content:
      "设备状态异常指在线状态或开关状态与预期不符。检查顺序：①设备详情中的在线状态（online）与最近心跳时间（lastSeenAt）；②绑定状态（bound）与网关绑定信息（binding）；③灯状态（lampStatus）与最近一次控制记录；④结合光照值与自动控制逻辑判断当前状态是否合理。",
    keywords: ["状态", "异常", "在线", "离线", "lampStatus", "bound"],
    source: "内部知识库",
  },
  {
    id: "kb-lamp-not-on",
    title: "路灯不亮",
    category: "设备状态",
    content:
      "路灯不亮时按顺序排查：①设备是否在线且已绑定；②当前是否为自动控制且光照值高于阈值（系统判断无需开灯）；③查看控制记录中最近一次指令是否下发成功；④通过系统控制界面手动下发开灯指令测试；⑤手动指令也无效时检查设备供电与灯具本身。",
    keywords: ["不亮", "灯不亮", "熄灭", "不发光"],
    source: "内部知识库",
  },
  {
    id: "kb-lamp-flicker",
    title: "路灯频繁闪烁",
    category: "设备状态",
    content:
      "系统模拟态未单独定义闪烁状态（灯具只有开/关两种状态）。若观察到灯光频繁明灭，最常见原因是光照值在阈值附近波动，导致自动控制在开灯和关灯之间反复切换；处理方式：观察该设备近期光照曲线，若确实在阈值附近波动，适当增大滞回值。",
    keywords: ["闪烁", "频繁", "忽明忽暗", "抖动"],
    source: "内部知识库",
  },

  // ============ 路灯维护 ============
  {
    id: "kb-manual-control-ack",
    title: "手动控制回执",
    category: "路灯维护",
    content:
      "手动控制应生成 commandId，并记录下发时间、执行状态和完成时间；控制指令要求设备已绑定，可在控制记录中查看下发与执行结果。",
    keywords: ["手动", "控制", "命令", "回执"],
    source: "内部知识库",
  },
  {
    id: "kb-mqtt-abnormal",
    title: "MQTT连接异常",
    category: "路灯维护",
    content:
      "本项目当前为模拟态，未接入真实MQTT Broker，仅预留Topic：遥测 street-light/+/telemetry、心跳 street-light/+/heartbeat、指令 street-light/{deviceId}/command、回执 street-light/{deviceId}/command-reply。通用排查：①确认Broker地址、端口与认证信息配置正确；②检查设备端MQTT客户端连接与保活；③观察设备是否正常发布遥测与心跳消息；④指令下发后关注command-reply回执。",
    keywords: ["MQTT", "mqtt", "broker", "topic", "发布", "订阅", "连接失败"],
    source: "内部知识库",
  },
  {
    id: "kb-network-abnormal",
    title: "网络异常",
    category: "路灯维护",
    content:
      "系统未指定具体网关与网络制式，按通用方式排查：①确认网关网络接入正常；②网络不稳定会导致设备心跳中断，进而被系统标记为离线并生成告警；③区分单设备离线与整片区域离线：单设备离线优先查设备侧，整片离线优先查网关与网络侧。",
    keywords: ["网络", "网络异常", "WiFi", "wifi", "信号", "掉线", "整片"],
    source: "内部知识库",
  },
  {
    id: "kb-power-abnormal",
    title: "供电异常",
    category: "路灯维护",
    content:
      "系统当前未采集供电电压等电源数据。通用排查：①确认路灯供电回路与电源模块工作正常；②供电中断会导致设备完全离线（无心跳、无遥测）；③供电不稳可能导致设备反复重启；④涉及强电操作必须断电并遵守安全规程。",
    keywords: ["供电", "电源", "断电", "电压", "功耗", "功率"],
    source: "内部知识库",
  },
  {
    id: "kb-esp32-faults",
    title: "ESP32常见故障",
    category: "路灯维护",
    content:
      "目标设备采用ESP32，具体型号与引脚定义以团队硬件文档为准，本条目不提供具体型号参数。常见故障与通用处理：①供电不足或不稳导致复位重启；②Wi-Fi连接失败：检查热点/路由配置与信号强度；③程序异常可通过串口日志与看门狗复位信息定位；④无法烧录：检查烧录接线、供电与烧录参数。",
    keywords: ["ESP32", "esp32", "复位", "烧录", "串口", "看门狗", "重启"],
    source: "内部知识库",
  },
  {
    id: "kb-maintenance-safety",
    title: "维护安全注意事项",
    category: "路灯维护",
    content:
      "维护作业安全通用要求：①涉及供电部分先断电并验电再操作；②使用绝缘工具，湿手不操作电气设备；③高空或灯杆作业做好防护并遵守作业规范；④雷雨天气停止户外电气作业；⑤多人作业保持沟通，防止误送电。",
    keywords: ["安全", "注意事项", "绝缘", "验电", "断电", "高空", "雷雨", "带电作业"],
    source: "内部知识库",
  },
];

module.exports = {
  knowledgeBase,
};
