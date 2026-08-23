# 智慧路灯系统基础开发框架

本项目是智慧路灯系统的基础开发框架，用于后续按小组分工继续开发。当前版本采用前后端分离目录组织：

- 前端：`frontend/`
- 后端：`backend/`
- 文档：`docs/`
- 运行数据：`data/`

当前框架先使用 Node.js 后端和原生前端页面，便于快速启动和联调。后续如果课程要求 Java 后端，可以将 `backend/` 逐步替换为 Spring Boot。

## 快速启动

```powershell
cd "C:\Users\Lawrence\Desktop\Smart Street Lights"
npm run dev
```

浏览器访问：

```text
http://localhost:3000
```

## 项目结构

```text
Smart Street Lights/
  frontend/                    前端代码，2号负责
    index.html
    src/
      main.js                  前端入口
      styles.css               全局样式
      api/
        client.js              后端 API 调用封装
      components/              公共组件
      pages/
        Dashboard.js           当前首页

  backend/                     后端代码
    server.js                  后端启动入口
    routes/                    API 路由，3号负责
    services/                  业务逻辑，3号负责
    store/                     数据读写，3号负责
    models/                    数据模型，3号负责
    simulator/                 IoT 模拟设备，4号负责
    iot/                       MQTT Topic 与通信预留，4号负责
    agent/                     AI 智能体接口，5号负责
    rag/                       RAG 知识库与 Prompt，5号负责
    realtime/                  SSE 实时推送
    config/                    配置
    utils/                     公共工具

  data/                        本地运行数据
  docs/                        项目文档
  package.json                 启动脚本
```

## 团队分工

| 成员 | 角色 | 主要负责目录 |
|---|---|---|
| 1号 | 项目经理与需求文档负责人 | `docs/`、`README.md` |
| 2号 | 前端与数据可视化负责人 | `frontend/` |
| 3号 | 后端与数据库负责人 | `backend/routes/`、`backend/services/`、`backend/store/`、`backend/models/`、`data/` |
| 4号 | IoT 模拟设备与 MQTT 通信负责人 | `backend/simulator/`、`backend/iot/` |
| 5号 | AI 智能体与 RAG 负责人 | `backend/agent/`、`backend/rag/` |
| 6号 | 系统测试、集成与部署负责人 | `docs/test-cases.md`、`docs/deployment.md`、`package.json` |

详细分工见：

```text
docs/team-division.md
```

每日任务清单见：

```text
docs/daily-task-plan.md
```

架构图见：

```text
docs/functional-architecture.md
docs/technical-architecture.md
docs/functional-architecture.svg
docs/technical-architecture.svg
```

## 当前已实现能力

- 设备状态展示
- 光照数据模拟
- 自动开灯/关灯
- 手动开灯/关灯
- 阈值配置
- 在线/离线状态
- 离线告警
- SSE 实时推送
- 维护问答基础接口
- MQTT Topic 预留

## 常用接口

```text
GET  /api/summary                 获取系统总览
GET  /api/devices                 获取设备列表
GET  /api/alerts                  获取告警列表
GET  /api/telemetry               获取光照历史数据
PUT  /api/config                  修改阈值和自动控制配置
POST /api/devices/:id/control     手动开灯/关灯
POST /api/simulator/scenario      切换模拟场景
POST /api/assistant/chat          维护问答
GET  /events                      SSE 实时推送
```

完整接口说明见：

```text
docs/api.md
```

## 开发建议

1. 2号先完善 `frontend/` 页面和数据可视化。
2. 3号继续拆分后端业务，并准备数据库表结构。
3. 4号完善 MQTT Topic、消息格式和模拟设备逻辑。
4. 5号扩展知识库、Prompt 和问答服务。
5. 6号维护测试用例、部署说明和联调记录。
6. 1号同步维护需求文档、进度和答辩材料。

## 运行验证

启动后可以检查：

```text
http://localhost:3000
http://localhost:3000/api/summary
```

核心联调流程：

1. 打开首页。
2. 切换低光场景，观察自动开灯。
3. 切换高光场景，观察自动关灯。
4. 切换离线场景，等待生成离线告警。
5. 输入维护问题，测试 AI 问答。
