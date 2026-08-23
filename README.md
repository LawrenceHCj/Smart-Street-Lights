# 智慧路灯系统

本仓库用于智慧路灯系统的前后端开发与联调。当前 `main` 已合并前端 Vue3 工程、Node.js 联调后端，以及 Java Spring Boot 后端代码。

## 技术栈

| 模块 | 技术 | 目录 |
|---|---|---|
| 前端 | Vue 3、TypeScript、Vite、Element Plus、ECharts | `frontend/` |
| Node 联调后端 | Node.js 原生 HTTP、SSE、本地 JSON 状态、设备模拟器 | `backend/` |
| Java 后端 | Spring Boot、Spring Security、JWT、JPA、MySQL、MQTT | `src/`、`pom.xml` |
| 文档 | 需求、分工、每日任务、架构图、接口说明 | `docs/` |
| 运行数据 | Node 联调后端本地状态文件 | `data/` |

## 推荐启动方式

开发前端时，推荐先使用 Node 联调后端，因为它不依赖 MySQL 和 MQTT，适合快速进入页面和调接口。

### 1. 启动后端

在项目根目录执行：

```powershell
cd "C:\Users\Lawrence\Desktop\Smart Street Lights"
npm run backend
```

后端地址：

```text
http://localhost:8080
```

### 2. 启动前端

另开一个终端执行：

```powershell
cd "C:\Users\Lawrence\Desktop\Smart Street Lights\frontend"
npm run dev
```

前端地址：

```text
http://localhost:5173
```

默认登录账号：

```text
账号：admin
密码：123456
```

前端开发代理已经配置为：

```text
前端 5173 -> /api -> 后端 8080
```

所以浏览器访问 `http://localhost:5173` 即可，不需要直接打开后端地址。

## Java 后端启动方式

Java 后端位于项目根目录的 `src/`，使用 Maven 管理。启动前需要准备：

- JDK 17
- Maven
- MySQL，端口 `3307`
- 数据库名：`smartlamp`
- MySQL 用户名：`root`
- MySQL 密码：`123456`
- 可选 MQTT Broker：`127.0.0.1:1883`

配置文件：

```text
src/main/resources/application.yml
```

启动命令：

```powershell
cd "C:\Users\Lawrence\Desktop\Smart Street Lights"
mvn spring-boot:run
```

注意：Node 后端和 Java 后端都使用 `8080` 端口，不能同时启动。前端只需要后端接口符合 `/api/**` 协议即可。

## 常用命令

### 根目录

```powershell
npm run backend   # 启动 Node 联调后端，端口 8080
npm run dev       # 同 npm run backend
npm run check     # 检查 Node 后端入口语法
```

### 前端目录

```powershell
cd frontend
npm run dev       # 启动 Vue 开发服务器，端口 5173
npm run build     # 类型检查并构建前端
npm run preview   # 预览构建产物
```

### Java 后端

```powershell
mvn spring-boot:run
mvn test
```

## 项目结构

```text
Smart Street Lights/
  frontend/                    Vue3 前端工程，2号负责
    src/
      api/                     前端 API 调用封装
      views/                   页面
      layout/                  布局
      router/                  路由
      styles/                  样式

  backend/                     Node 联调后端
    server.js                  启动入口
    routes/                    API 路由
    services/                  业务逻辑
    store/                     本地状态读写
    simulator/                 IoT 设备模拟器
    iot/                       MQTT Topic 预留
    agent/                     AI 问答服务
    rag/                       RAG 知识库与检索
    realtime/                  SSE 实时推送

  src/main/java/com/smartlamp/ Java Spring Boot 后端
    controller/                控制器
    service/                   业务服务
    repository/                数据访问
    entity/                    数据实体
    dto/                       接口 DTO
    security/                  JWT 与安全配置
    mqtt/                      MQTT 消息处理

  docs/                        项目文档
  data/                        Node 后端运行数据，不作为业务代码提交
  pom.xml                      Java 后端 Maven 配置
  package.json                 Node 联调后端脚本
```

## 团队分工

| 成员 | 角色 | 主要负责区域 |
|---|---|---|
| 1号 | 项目经理与需求文档负责人 | `docs/`、`README.md`、进度协调 |
| 2号 | 前端与数据可视化负责人 | `frontend/` |
| 3号 | 后端与数据库负责人 | `src/main/java/`、数据库、接口实现 |
| 4号 | IoT 模拟设备与 MQTT 通信负责人 | `backend/simulator/`、`backend/iot/`、`src/main/java/com/smartlamp/mqtt/` |
| 5号 | AI 智能体与 RAG 负责人 | `backend/agent/`、`backend/rag/`、`src/main/java/com/smartlamp/service/AgentService.java` |
| 6号 | 系统测试、集成与部署负责人 | `docs/test-cases.md`、`docs/deployment.md`、构建和联调 |

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
docs/functional-architecture.svg
docs/technical-architecture.svg
```

## 当前已实现能力

- 登录与前端路由守卫
- 设备总览与设备列表
- 光照数据展示与历史曲线
- 手动开灯/关灯
- 自动联动阈值配置
- 在线/离线状态模拟
- 告警列表与告警确认
- SSE 实时推送
- AI 维护问答基础接口
- Java 后端基础 API、JWT、JPA、MQTT 代码骨架

## 常用接口

```text
POST /api/auth/login               登录
GET  /api/dashboard/overview       仪表盘总览
GET  /api/devices                  设备列表
POST /api/devices                  新增设备
DELETE /api/devices/:deviceId      解绑设备
GET  /api/devices/:deviceId/light  最新光照
GET  /api/light/history            光照历史
POST /api/devices/:deviceId/switch 开关灯
GET  /api/alarms                   告警列表
POST /api/alarms/:id/ack           确认告警
GET  /api/config/linkage           获取联动策略
PUT  /api/config/linkage           修改联动策略
POST /api/agent/ask                AI 问答
```

完整接口说明：

```text
docs/api.md
API.md
frontend/前端接口协议.md
```

## 开发注意事项

1. 前端日常开发优先启动 `frontend` + `backend`，确保页面可跑通。
2. Java 后端接入时，保持接口路径和返回格式与前端协议一致：`{ code, message, data }`。
3. `data/app-state.json` 是 Node 联调后端运行数据，可能会频繁变化，一般不要提交。
4. Node 后端和 Java 后端都占用 `8080`，启动前确认端口没有被占用。
5. 浏览器提示“拒绝连接”通常是对应服务没有启动，或前端代理指向的后端端口没有运行。

## 验证流程

1. 启动 Node 后端：`npm run backend`。
2. 启动前端：`cd frontend && npm run dev`。
3. 打开 `http://localhost:5173`。
4. 使用 `admin / 123456` 登录。
5. 检查仪表盘、设备监控、灯光控制、告警、AI 问答页面是否能正常请求接口。
