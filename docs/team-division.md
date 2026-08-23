# 团队分工与目录对应

## 1号：项目经理与需求文档负责人

负责目录：

```text
docs/
README.md
```

职责：
- 维护需求、验收标准、验收脚本、会议记录。
- 跟进 2-6 号任务进度。
- 统一接口字段、页面范围和交付物清单。

## 2号：前端与数据可视化负责人

负责目录：

```text
frontend/
```

职责：
- 页面布局、组件、交互。
- 数据可视化、设备列表、告警展示。
- 通过 `frontend/src/api/client.js` 调用后端接口。

## 3号：后端与数据库负责人

负责目录：

```text
backend/routes/
backend/services/
backend/store/
backend/models/
data/
```

职责：
- REST API。
- 设备、遥测、控制、告警业务逻辑。
- 数据持久化，后续替换为数据库。

## 4号：IoT 模拟设备与 MQTT 通信负责人

负责目录：

```text
backend/simulator/
backend/iot/
```

职责：
- 模拟设备光照、心跳、在线/离线。
- 设计 MQTT Topic 和消息格式。
- 后续接入真实 MQTT Broker。

## 5号：AI 智能体与 RAG 负责人

负责目录：

```text
backend/agent/
backend/rag/
```

职责：
- 维护问答接口。
- 维护知识库和 Prompt。
- 后续接入 MaxKB、RAGFlow 或大模型 API。

## 6号：系统测试、集成与部署负责人

负责目录：

```text
docs/test-cases.md
docs/deployment.md
package.json
```

职责：
- 测试用例。
- 本地启动、集成联调、部署说明。
- 验证核心联调流程。
