# 智慧路灯系统

本仓库用于智慧路灯系统的前后端开发与联调，后端统一使用 Java Spring Boot。

## 技术栈

| 模块 | 技术 | 目录 |
|---|---|---|
| 前端 | Vue 3、TypeScript、Vite、Element Plus、ECharts | `frontend/` |
| 后端 | Spring Boot、Spring Security、JWT、JPA、MySQL、MQTT | `backend/` |
| 文档 | 需求、分工、每日任务、架构图、接口说明 | `docs/` |

## 推荐启动方式

Java Spring Boot 是唯一后端：设备、遥测、告警和联动配置均写入 MySQL，设备控制通过 MQTT 下发。

### 0. 一键下载依赖并启动基础服务

克隆仓库后，在项目根目录执行：

```powershell
.\scripts\install-dependencies.ps1
```

该脚本会通过 `npm ci` 安装前端锁定依赖、通过 Maven 下载后端依赖，并用 `docker compose` 拉取和启动 MySQL（3307）与 MQTT（1883）。如果本机的 MySQL 和 MQTT 已经启动，可跳过容器部分：

```powershell
.\scripts\install-dependencies.ps1 -SkipInfrastructure
```

基础服务也可以单独管理：

```powershell
docker compose up -d
docker compose down
```

### 1. 启动后端

在项目根目录执行：

```powershell
cd "C:\Users\Lawrence\Desktop\Smart Street Lights\backend"
mvn spring-boot:run
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

## 后端启动方式

后端位于 `backend/`，使用 Maven 管理。启动前需要准备：

- JDK 17
- Maven
- MySQL，端口 `3307`
- 数据库名：`smartlamp`
- MySQL 用户名：`root`
- MySQL 密码：`123456`
- 可选 MQTT Broker：`127.0.0.1:1883`

如需启用外部大模型，复制 `backend/.env.example` 为 `backend/.env`，填写 `llm.api-key`。模板默认配置为 DeepSeek V4 Flash；`.env` 不会提交到 Git。

配置文件：

```text
backend/src/main/resources/application.yml
```

启动命令：

```powershell
cd "C:\Users\Lawrence\Desktop\Smart Street Lights\backend"
mvn spring-boot:run
```

前端代理会将 `/api/**` 请求转发到后端的 `8080` 端口。

## 常用命令

### 前端目录

```powershell
cd frontend
npm run dev       # 启动 Vue 开发服务器，端口 5173
npm run build     # 类型检查并构建前端
npm run preview   # 预览构建产物
```

### 后端目录

```powershell
cd backend
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

  backend/                     Java Spring Boot 后端
    pom.xml                    Maven 配置
    src/main/java/com/smartlamp/
    controller/                控制器
    service/                   业务服务
    repository/                数据访问
    entity/                    数据实体
    dto/                       接口 DTO
    security/                  JWT 与安全配置
    mqtt/                      MQTT 消息处理

  docs/                        项目文档
```

## 团队分工

| 成员 | 角色 | 主要负责区域 |
|---|---|---|
| 1号 | 项目经理与需求文档负责人 | `docs/`、`README.md`、进度协调 |
| 2号 | 前端与数据可视化负责人 | `frontend/` |
| 3号 | 后端与数据库负责人 | `backend/src/main/java/`、数据库、接口实现 |
| 4号 | IoT 与 MQTT 通信负责人 | `backend/src/main/java/com/smartlamp/mqtt/` |
| 5号 | AI 智能体与 RAG 负责人 | `backend/src/main/java/com/smartlamp/agent/`、`backend/src/main/java/com/smartlamp/service/AgentService.java` |
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

1. 前端日常开发启动 `frontend` 与 Java `backend`，确保页面可跑通。
2. 后端接口路径和返回格式应与前端协议一致：`{ code, message, data }`。
3. 启动前确认 `8080` 端口没有被占用。
5. 浏览器提示“拒绝连接”通常是对应服务没有启动，或前端代理指向的后端端口没有运行。

## 验证流程

1. 启动后端：`cd backend && mvn spring-boot:run`。
2. 启动前端：`cd frontend && npm run dev`。
3. 打开 `http://localhost:5173`。
4. 使用 `admin / 123456` 登录。
5. 检查仪表盘、设备监控、灯光控制、告警、AI 问答页面是否能正常请求接口。
