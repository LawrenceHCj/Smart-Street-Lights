# IoT 与 MQTT 模块

负责人：4号。

当前代码仍使用内置模拟器，后续可以在这里接入真实 MQTT Broker。

建议职责：
- 定义 MQTT Topic。
- 定义遥测、心跳、控制命令、控制回执 JSON 格式。
- 将 `simulator/deviceSimulator.js` 中的模拟数据替换为 MQTT 订阅数据。
- 将 `controlService.sendControlCommand` 的执行逻辑替换为 MQTT 发布。
