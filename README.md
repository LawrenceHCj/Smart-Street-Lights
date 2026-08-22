# 智慧路灯软件基础框架

当前项目已经整理为标准的前后端目录，方便后续继续开发。

```text
Smart Street Lights/
  frontend/          前端框架目录
    index.html
    src/
      main.js
      styles.css
      api/
      components/
      pages/
  backend/           后端框架目录
    server.js
  data/              运行数据，后端自动生成
  docs/              架构和接口文档
  package.json       项目启动脚本
```

## 启动

```powershell
cd "C:\Users\Lawrence\Desktop\Smart Street Lights"
npm run dev
```

浏览器打开：

```text
http://localhost:3000
```

## 开发入口

前端入口：

```text
frontend/src/main.js
frontend/src/pages/Dashboard.js
frontend/src/components/
frontend/src/api/client.js
frontend/src/styles.css
```

后端入口：

```text
backend/server.js
```

## 当前框架包含

- 前端页面骨架：仪表盘、设备列表、告警列表、控制参数、维护问答
- 前端 API 封装：统一从 `frontend/src/api/client.js` 调用后端
- 后端接口骨架：设备、遥测、配置、告警、模拟器、问答
- Mock 数据发生器：模拟光照、心跳、离线、低光、高光等场景
- SSE 实时推送：前端自动接收后端状态变化

## 后续可替换方向

- 前端可升级为 Vue / React / Vite。
- 后端可拆分为 Controller / Service / Repository。
- Mock 数据可替换为真实 MQTT。
- JSON 文件存储可替换为 MySQL / PostgreSQL / MongoDB。
