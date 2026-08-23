一、长期总控指令
以下规则在整个开发过程中始终有效。每次开始新的 Claude Code 会话时，都可以先把这一部分告诉你。
你正在协助 5号 开发一个多人合作的“智慧路灯系统”。
5号成员负责：AI 智能体与 RAG 模块。
项目已经存在，不允许重新创建一个新的独立项目，也不要擅自替换组长确定的技术栈。
当前项目基本情况
项目当前采用：

Node.js 后端
原生前端
前后端分离
Git 多人分支协作

项目核心目录包括：

frontend/

backend/
  routes/
  services/
  store/
  models/
  simulator/
  iot/
  agent/
  rag/
  realtime/
  config/
  utils/

data/
docs/

5号的职责范围主要是：

backend/agent/
backend/rag/

目前已经存在：

POST /api/assistant/chat

维护智能问答基础接口。

当前 AI 模块还是较基础的本地关键词知识检索版本。

5号的目标是在现有项目基础上逐步实现：

用户问题
   ↓
现有 /api/assistant/chat
   ↓
Agent / assistantService
   ↓
判断需要哪些信息
   │
   ├── 知识库检索
   ├── 系统设备数据
   ├── 系统告警数据
   ├── 光照/历史数据
   └── 后期可选 Web Search
   ↓
大模型 API
   ↓
基于真实上下文生成回答
   ↓
返回前端
最终智能体目标

第一阶段智能体应支持：

普通知识库问答。
路灯维护知识检索。
根据真实系统数据回答设备状态问题。
根据真实告警记录回答设备异常问题。
根据光照等系统数据辅助分析。
回答中区分知识库信息和系统实时数据。
不确定的信息不能伪装成真实事实。
对设备控制只提供建议，不执行真实控制。
后期预留互联网搜索能力。
后期预留真正向量 RAG 的升级能力。
系统信息优先级

智能体回答问题时应优先使用：

① 系统真实数据
② 项目内部知识库
③ Web Search（后期加入）
④ 大模型自身已有知识

不得使用大模型自身知识猜测：

某台真实设备是否在线
某台路灯当前是否打开
当前真实光照值
实际告警数量
实际系统配置

这些事实必须来源于系统后端。

权限原则

当前 Agent 所有系统工具必须是：

READ ONLY
只读

允许：

查询设备列表
查询设备状态
查询最新光照数据
查询历史光照
查询告警
搜索知识库

禁止：

打开路灯
关闭路灯
修改阈值
删除设备
修改数据库
发送 MQTT 控制指令
直接操作硬件

即使用户说：

“帮我打开1号路灯”

智能体目前也只能：

说明如何操作
或者提示用户通过系统控制界面完成

不能真正执行设备控制。

二、多人协作边界

3号成员负责：

backend/routes/
backend/services/
backend/store/
backend/models/
data/

4号成员负责：

backend/simulator/
backend/iot/

5号负责：

backend/agent/
backend/rag/

因此，请遵守以下规则。

规则1

不要为了方便直接重写：

backend/services/
backend/store/
backend/models/
backend/iot/
backend/simulator/
规则2

Agent 查询系统数据时，优先调用 3号成员已经存在的 Service 或公开数据接口。

推荐关系：

Agent
 ↓
Agent Tool
 ↓
3号成员 Service
 ↓
Store / Database

不要设计：

Agent
 ↓
自己写 SQL / 自己读取数据库内部实现
规则3

Agent 不直接访问 MQTT。

禁止：

Agent
 ↓
MQTT
 ↓
ESP32
规则4

如果当前基础后端还没有提供 Agent 所需的数据能力：

不要擅自大规模修改 3号成员代码。

请先告诉 5号：

Agent需要哪个能力
建议3号成员提供什么函数
输入是什么
输出是什么

5号会与3号成员协调。

开发期间允许在 Agent 层使用 Mock 数据暂时完成联调，但必须明确标记为 Mock。

三、开发环境规则

项目 Windows 路径：

D:\users\wawanan\projects\smart-street-lights

WSL 路径：

/mnt/d/users/wawanan/projects/smart-street-lights

Claude Code 当前运行在 Windows，可以直接修改 Windows 项目文件。

但是：

Node.js
npm
测试
Git
项目运行
Docker

原则上统一在 WSL Ubuntu 环境执行。

如果你需要执行 Linux 命令，可以使用：

wsl bash -lc "..."

不要擅自使用 Windows 下另外一套 Node/Python/Git 环境制造重复环境。

当前 Git 开发分支：

wwn-agent

不得自动：

切换 main
merge
rebase
commit
push

除非 5号 明确要求。

Git 提交由 5号 自己完成。

四、Claude Code 工作方式

你每次只完成我要求的一个阶段。

禁止：

“一次性实现整个智能体系统。”

每个阶段必须按照：

1. 先检查现有代码
2. 告诉我现状
3. 给出本阶段修改方案
4. 只修改本阶段必要文件
5. 运行测试
6. 汇报测试结果
7. 列出修改文件
8. 停止

不要在一个阶段结束后自动开始下一阶段。

如果发现现有代码结构和我的描述不同：

以仓库当前真实代码为准。

不要自行重构整个项目。

五、技术栈与编码规范

以下为项目当前真实技术栈，写代码、做代码审查、使用 code-simplifier 等工具时都必须遵守：

- 后端：Node.js（CommonJS 模块，require / module.exports），不使用 ES modules。
- 依赖：纯 Node 内置模块，零 npm 依赖（HTTP 服务用内置 http 模块，无 Express 等框架）。
- Node 版本：>= 18（WSL Ubuntu 中已安装 Node 24）。
- 前端：原生 HTML/CSS/JS，无框架、无构建工具、不使用 React/TypeScript。
- 通信：前后端分离，REST API + SSE。
- 测试：使用 Node 内置 node:test 测试运行器与 assert，不引入第三方测试框架。
- 编码风格：与现有代码保持一致——小函数、职责单一、CommonJS 导出、中文注释与日志；服务层抛错使用 error.statusCode 约定，路由统一捕获返回 { error } 格式。
- 大模型调用：使用 Node 内置 fetch，走 llmClient 封装，不引入 SDK。

当前不使用 ES modules、TypeScript、React。如未来团队正式决定迁移技术栈，需先更新本节内容，再让相关工具按新规范工作。