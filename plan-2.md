智慧路灯 Agent V2：阶段14–23 Claude Code 开发指令
总目标

前提：你已经完成项目根目录下plan.md中的阶段 0–9。
项目5号成员将逐步给你每阶段任务，当你完成每阶段任务后，请将5号给你的任务内容填写至本文件下。

本轮目标是把现有只读智能体升级为：

用户自然语言
   ↓
Agent理解意图
   ↓
生成候选Action / Tool Call
   ↓
Action Gateway
   ├── 权限检查
   ├── 参数检查
   ├── 设备状态检查
   ├── 风险等级判断
   └── 是否需要用户确认
   ↓
3号成员正式control/config Service
   ↓
4号成员IoT / MQTT模块
   ↓
真实设备 / 模拟设备
   ↓
状态反馈
   ↓
Agent向用户报告真实结果

必须始终禁止：

LLM → MQTT
LLM → ESP32
LLM → 直接修改数据库
LLM → 直接写系统配置文件
Agent Tool → 自己重新实现一套设备控制逻辑

普通网页按钮和 Agent 必须尽量复用同一套后端业务 Service。

---

## 14. 阶段 14：重新阅读最新控制链路，只做设计，不写代码

状态：已执行完成（纯设计分析，未修改任何代码；仅本文件追加记录）

原文记录：

现在开始 Agent V2 的控制能力开发。这一阶段禁止修改任何代码。请重新完整阅读当前仓库真实代码，重点检查：backend/agent/、backend/routes/、backend/services/、backend/store/、backend/models/、backend/iot/、backend/simulator/、docs/api.md、README.md、package.json。重点确认目前普通网页执行以下操作的真实代码调用链：单设备开灯、单设备关灯、修改光照阈值、开启自动控制、关闭自动控制。

请回答：当前单设备开灯/关灯 API 是什么；API Route 最终调用哪个 Service；Service 如何修改设备状态；Service 是否进一步调用 IoT 层；simulator 模式下如何模拟设备控制；MQTT 模式预留或实际控制链路是什么；当前阈值配置在哪里修改；当前自动控制开关在哪里修改；控制以后有没有设备实际状态反馈；当前有哪些 Service 可以直接被 Agent 复用；Agent 为了控制设备还缺少哪些基础后端能力；当前系统是否存在用户、角色、权限机制。

然后基于真实代码设计 Agent → Write Tool → Action Gateway → 已有业务 Service → IoT / Simulator。Agent V2 第一版只考虑 turnOnLight / turnOffLight。暂时不要实现：批量开关所有路灯、删除设备、解绑设备、定时控制、任意 MQTT 命令、复杂自动化、Agent 长期后台控制。

最后只输出：A. 当前普通网页控制调用链 B. 当前阈值/自动控制调用链 C. Agent可以直接复用的 Service D. Agent仍然缺失的基础后端能力 E. 建议 Agent V2 新增哪些文件 F. 建议 Action 数据结构 G. 建议用户确认流程 H. 与3号成员需要协调什么 I. 与4号成员需要协调什么 J. 当前是否适合开始阶段15 K. 确认本阶段没有修改任何代码。不要实现代码。不要自动进入阶段15。完成分析后停止。

（分析结论要点）：
- 开关灯：POST /api/devices/{code}/switch 与 POST /api/devices/{code}/control 两个端点并存（payload 格式不同），均在 Controller 层直拼 MqttPublisherService.publish + DeviceService.updateLampStatus，无 Service 级开关方法
- 阈值/自动控制：GET/PUT /api/config/linkage 读写 SystemConfig(id=1)，但**无任何业务代码消费 autoControl/luxThreshold**——自动控制目前只存配置不产生实际行为
- 控制后无设备真实反馈：cmd 只发不收（MqttMessageListener 仅订阅 data/heartbeat），lampStatus 是后端乐观更新
- Java 侧 simulator 不存在（仅 SystemConfig.simulatorScenario 字段）；权限：有用户+角色字段（admin/municipal/operator）+JWT 认证，但无接口级授权
- Agent 写能力缺口：①3号 下沉 Service 级开关方法 ②4号 设备状态反馈 topic ③接口级角色授权 ④Agent 调用者身份上下文
- 结论：适合开始阶段15，业务执行先接 Mock（明确标记），等 3号 Service 就绪一行替换；4号 反馈链路可后置

---

## 15. 阶段 15：定义 Action 协议、风险等级和安全边界

状态：已执行完成（仅新增 agent/actions 包 8 个主代码文件 + 2 个测试文件，未修改任何已有代码；未接 MQTT、未控制设备）

原文记录：

根据阶段14分析结果，现在只实现 Agent 侧最小的"操作协议"和安全边界。不要接 MQTT。不要真正控制设备。请为 Agent 写操作定义统一 Action 数据结构，至少包含：actionId、actionType、targetType、targetId、arguments、riskLevel、status、requestedAt、expiresAt、requestedBy。Action 状态至少支持：PENDING_CONFIRMATION、CONFIRMED、EXECUTING、SUCCESS、FAILED、CANCELLED、EXPIRED。风险等级：READ、LOW_WRITE、HIGH_WRITE。规则：READ→可以直接执行；LOW_WRITE→必须用户确认；HIGH_WRITE→当前 Agent 禁止执行。当前定义：查询设备=READ；单台路灯开灯/关灯=LOW_WRITE；修改阈值/修改自动模式=LOW_WRITE（以后阶段开放）；关闭全部设备/批量修改设备/删除设备/解绑设备=HIGH_WRITE。

要求：所有 Action 参数必须结构化；不允许万能 executeCommand(command)；不允许 LLM 提交任意 shell 命令、任意 SQL、任意 MQTT payload；所有可执行 Action 必须来自明确白名单；LOW_WRITE 必须确认；HIGH_WRITE 直接拒绝；Action 必须有有效期；actionId 必须唯一；未确认 Action 绝不能调用正式 Service。请实现最小：Action 数据模型、Action Manager、Action Gateway 基础检查。本阶段不能：接 controlService、控制设备、发送 MQTT、修改系统配置。

测试：创建关闭 lamp001 Action→PENDING_CONFIRMATION；取消→CANCELLED；过期→EXPIRED；HIGH_WRITE→被拒绝；未确认 Action 尝试执行→被拒绝。

（实施摘要）：新增 com.smartlamp.agent.actions 包——ActionRisk/ActionStatus/ActionType（白名单枚举，含风险与开放标记）/AgentAction（@Data 数据模型，10 字段）/ActionManager（创建/确认/取消/懒过期/参数黑名单校验）/ActionGateway（执行前检查：存在→有效期→风险→确认状态，检查通过才找执行器；执行器注册表本阶段为空，预留 3号 Service 接入点）/ActionExecutor（执行器接口）/ActionRejectedException。安全红线落实：arguments 禁止 command/sql/payload/topic 等万能键、未知键拒绝、targetType 白名单、UUID 唯一、默认 2 分钟有效期、未确认/已取消/已过期/高风险一律拦截且执行器零调用。单测 ActionManagerTest 14 例 + ActionGatewayTest 10 例共 24/24 全绿。

---

## 16. 阶段 16：让 Agent 识别控制意图，但仍不真正执行

状态：已执行完成（新增 AgentActionTools + 测试，扩展 ToolCatalog/AgentService/prompts.md；未接控制 Service、未发 MQTT）

原文记录：

现在让 Agent 能够识别用户的控制意图，但这一阶段仍然不能真正操作设备。目标示例：用户"帮我关闭 lamp001"→ Agent 1.识别为 turnOffLight 2.查询 lamp001 当前状态 3.如果设备存在且在线，生成 Pending Action 4.返回待确认信息 5.不调用控制 Service。新增或扩展 Write Tool 时，只允许明确白名单：turnOnLight、turnOffLight。每个 Tool 必须定义：name、description、input schema、risk level、required confirmation。在生成待确认 Action 前，必须调用现有只读 Tool 检查：设备是否存在、设备是否在线、当前开关状态。规则：设备不存在→不得创建 Action；设备离线→默认不得继续控制，明确告诉用户设备当前离线；"把所有路灯都关掉"→第一版应拒绝执行，并说明批量操作暂未开放给 Agent；"帮我把灯处理一下"等模糊命令→必须追问目标设备和具体动作，不得自己猜。测试至少包含：打开 lamp001、关闭 lamp001、不存在的设备、离线设备、批量关闭所有设备、模糊命令。完成后停止。

（实施摘要）：评估结论为可执行（阶段15 的 ActionManager + 只读工具 + LLM 循环 + SecurityContext 全部就绪；唯一缺口是 DeviceDTO 不含 lampStatus，用 DeviceService.getDeviceByCode 读实体解决）。新增 AgentActionTools（requestTurnOn/requestTurnOff：存在→在线→当前开关状态三步只读检查，通过后 ActionManager.create 生成 PENDING_CONFIRMATION，拒绝时返回 REJECTED_DEVICE_NOT_FOUND/REJECTED_DEVICE_OFFLINE，发起者取自认证上下文）；ToolSpec 记录扩展 riskLevel+requiresConfirmation 元数据，注册 turn_on_light/turn_off_light（source=action）；AgentService sources 支持 action 来源标注；prompts.md 新增「控制意图与待确认操作」章节（不真正执行、批量拒绝、模糊追问、每次只处理一台）。单测 81/81 全绿（新增 AgentActionToolsTest 6 例、AgentServiceTest 4 例含 never() 控制写方法断言、PromptProviderTest 3 例）。真实 DeepSeek 六场景端到端全部符合预期：关/开 lamp001→待确认+actionId+声明未执行；lamp999→不存在未创建请求；lamp003→离线拒绝；批量→拒绝并引导控制界面；模糊→追问编号与动作。

---

## 17. 阶段 17：实现 Pending Action + 用户确认接口

状态：已执行完成（未提交；Git 提交由 5号 完成）

原文记录：

现在实现真正的二次确认机制，但暂时不要接真实控制 Service。

目标：

POST /api/assistant/chat
   ↓
生成 Pending Action
   ↓
返回 actionId + 待确认操作摘要

再提供确认和取消入口。

优先根据当前项目 API 风格设计，例如：

POST /api/assistant/actions/:actionId/confirm

POST /api/assistant/actions/:actionId/cancel

如果当前项目已有更合适的路由规范，以现有代码为准。

确认时必须再次检查：

Action是否存在
状态是否仍为PENDING_CONFIRMATION
是否已经过期
设备是否仍存在
设备是否仍在线
设备当前状态是否已经变化
Action参数是否合法

禁止仅凭用户输入：

“确认”

重新让 LLM 猜刚才确认的是什么。

真正执行对象必须通过：

actionId

找到。

本阶段可以使用明确标记的 Mock Executor，先验证状态机：

PENDING_CONFIRMATION
→ CONFIRMED
→ EXECUTING
→ SUCCESS / FAILED

测试：

正常确认
用户取消
重复确认
过期确认
不存在的actionId
确认前设备状态发生变化

完成后停止。

（实施摘要）：新增 ActionService（agent.actions 包，确认/取消唯一业务入口）：confirmAndExecute / cancel，确认时二次校验——存在→未过期→仍 PENDING_CONFIRMATION→参数复核（ActionManager.revalidate 新方法）→设备仍存在→仍在线→未处于目标状态；任一不通过置 FAILED 并拒绝（未执行任何控制），全部通过后 PENDING→CONFIRMED→EXECUTING→SUCCESS/FAILED；真正执行对象一律经 actionId 找到，绝不重新让 LLM 猜测。新增 MockDeviceExecutor（明确标记 Mock）：构造时注册开/关灯执行器，仅记日志，不调控制 Service、不发 MQTT、不改数据库，3号正式 Service 就绪后一行替换。AgentController 新增 POST /api/agent/actions/{actionId}/confirm 与 /cancel（路由取 /api/agent——当前前端主前缀，/api/assistant 为兼容旧调用，按“以现有代码为准”）。GlobalExceptionHandler 新增 ActionRejectedException→400（此前落到通用 500）。“设备状态是否已变化”实现：开关仅 ON/OFF 两态，确认时若已处于目标状态（开灯请求时已 ON / 关灯请求时已 OFF）判定为已变化并拒绝，等价覆盖“确认前状态发生变化”场景，且不误拒绝“请求后 ON→OFF、再确认”等仍应执行场景。发起人归属校验不在本阶段清单，未实现（建议与3号接口级授权一并处理）。测试：ActionServiceTest 11 例（正常确认开/关、取消、重复确认、重复取消、过期、不存在 actionId、确认时设备被删、确认时离线、状态已变化开/关）+ MockDeviceExecutorTest 2 例 + ActionManagerTest 新增 revalidate 4 例；全量 153 例 152 绿，唯一失败 SmartlampApplicationTests.contextLoads 为环境问题（MySQL localhost:3307 未启动，与本阶段改动无关）。

（阶段待办）：启动 MySQL（localhost:3307，库 smartlamp，root/123456）后重跑一次全量测试（`mvn test`），确认 SmartlampApplicationTests.contextLoads 转绿。

---

## 18. 阶段 18：接入真实“单设备开/关”控制 Service

状态：已执行完成（破例代 3号 实现：先按指令上报缺口，经 5号 授权后完成 Service 下沉与真实接线）

原文记录：

现在把已经验证过的 Action Gateway 接到项目现有正式控制 Service。

严格遵守：

Agent Write Tool
 ↓
Action Gateway
 ↓
3号成员现有control Service
 ↓
4号成员IoT / simulator

禁止：

Agent Tool 自己 publish MQTT
Agent Tool 自己修改设备数据文件
Agent Tool 自己实现第二套灯控逻辑

只开放：

turnOnLight(deviceId)

turnOffLight(deviceId)

如果当前正式 Service 不足以支持：

不要擅自重写3号/4号成员代码。

请先告诉我：

缺少哪个Service
建议函数签名
输入
输出
执行成功标准
失败标准

正式执行后，不能仅因为 Service 返回：

命令已发送

就直接告诉用户：

设备已成功开启

必须区分：

COMMAND_ACCEPTED
DEVICE_CONFIRMED
FAILED
TIMEOUT

如果当前只能确认命令已被后端接受，而不能确认真实硬件反馈，则必须如实回答：

控制指令已发送，但当前尚未获得设备执行确认。

测试：

在线设备开灯成功
在线设备关灯成功
设备离线
重复开灯
重复关灯
Service返回失败
执行超时

完成后停止。

（实施摘要/结论）：先按指令检查并上报缺口——灯控逻辑散落在 DeviceController.control/switch 内联代码（publish device/{id}/cmd + 乐观 updateLampStatus），不存在 Service 级开关方法；MqttIngestionService 只放行 data/heartbeat，无 cmdAck 确认 topic。5号 因暂无法与 3号 交流，**破例授权代做 3号 任务**后实施：新增 dto/CommandStatus（COMMAND_ACCEPTED/DEVICE_CONFIRMED/FAILED/TIMEOUT）+ dto/ControlOutcome{commandId,deviceId,action,status,issuedAt,message}；新增 service/DeviceControlService（turnOnLight/turnOffLight(deviceId)：存在→绑定→在线→MQTT 发布（false/异常→FAILED）→乐观更新→确认等待；无 4号 反馈链路时如实返回 COMMAND_ACCEPTED，注入 waiter 后支持 DEVICE_CONFIRMED/TIMEOUT，默认超时 0 不等待）；新增 service/DeviceConfirmationWaiter（4号 反馈链路接口，约定不得用乐观 lampStatus 判断）；MqttPublisherService.publish void→boolean；DeviceController /control、/switch 委托新 Service（网页与 Agent 共用同一入口；/switch 的 MQTT payload 从 {"on":bool} 统一为 {"action":"ON|OFF"}）。**行为变化点（需与 3号/前端对账）**：/control 设备不存在 404→400；/switch、/control 现在会拒绝离线/未绑定设备（原先 /switch 两者都不查、/control 只查绑定不查在线）；错误消息中文化。Agent 侧：DeviceControlExecutor 替换并删除 MockDeviceExecutor（注册 TURN_ON/TURN_OFF；ACCEPTED/CONFIRMED 写入 action.message 如实标注，FAILED/TIMEOUT 抛异常→Action FAILED）；ActionGateway 成功后采用执行器写入的消息。测试：DeviceControlServiceTest 12 例（在线开/关成功、离线、不存在、未绑定、重复开/关、发布 false/异常、超时、确认、未配置不等待）+ DeviceControlExecutorTest 6 例；全量 169 例 168 绿，唯一失败仍为 SmartlampApplicationTests（MySQL 未启动环境问题）。

（阶段待办）：① 启动 MySQL 后重跑全量测试（阶段17 待办延续）；② 与 3号 对账本阶段破例修改（尤其 /switch payload 统一、404→400、离线/未绑定拒绝、消息中文化）；③ 4号 cmdAck 反馈链路就绪后注入 DeviceConfirmationWaiter 实现并配置超时。

（补充·组长反馈落实）：组长审阅阶段16 提交后要求"接通 Agent Action 执行器：把已确认操作安全地连接到正式控制 Service；未收到设备回执时不能标记成功"。① 前半句已由阶段17+18 完成（确认接口→ActionGateway→DeviceControlExecutor→DeviceControlService）；② 后半句补做：ActionStatus 新增终态 COMMAND_ACCEPTED（命令已下发未收到回执，绝不视为成功），ActionExecutor 接口改为返回 ExecutorResult(CommandStatus,message)，ActionGateway 按结果映射终态——仅 DEVICE_CONFIRMED → SUCCESS，COMMAND_ACCEPTED → COMMAND_ACCEPTED，FAILED/TIMEOUT → FAILED；DeviceControlExecutor 改为返回结果。SUCCESS 现在只会在收到设备回执时出现（当前无 4号 回执链路，故控制类 Action 成功确认后为 COMMAND_ACCEPTED 终态）。测试：ActionGatewayTest 13 例（新增 3 例结果→终态映射）、ActionManagerTest 19 例、DeviceControlExecutorTest 断言更新；全量 173 例 172 绿（唯一失败仍为 MySQL 环境测试）。

---

（后续阶段记录继续追加到本文件末尾）
