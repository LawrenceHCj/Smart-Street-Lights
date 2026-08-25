# 智能路灯后端 —— 接口文档

> 版本：0.0.1-SNAPSHOT ｜ 基于源码实际实现整理（非设计稿）
> 技术栈：Spring Boot 4.1.1 + Spring Data JPA + MySQL + Spring Integration MQTT (Paho)

---

## 1. 通用约定

### 1.1 服务地址

| 项 | 值 |
|---|---|
| Base URL | `http://localhost:8080` |
| 端口 | `8080`（`application.yml` → `server.port`） |
| 数据库 | MySQL `localhost:3307/smartlamp` |
| MQTT Broker | `tcp://127.0.0.1:1883` |

### 1.2 统一响应体

所有接口返回 `ApiResponse<T>` 包装结构（`dto/ApiResponse.java`）：

```json
{
  "code": 0,
  "message": "ok",
  "data": { }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | int | `0` = 成功；非 0 = 业务错误码 |
| `message` | string | 成功固定为 `"ok"`，失败为中文错误描述 |
| `data` | T | 业务数据，失败时为 `null` |

**⚠️ 重要：HTTP 状态码恒为 200。** 业务错误（如 401 登录失败、400 设备不存在）只体现在响应体的 `code` 字段里，不体现在 HTTP 状态行。前端必须判断 `body.code`，不能只判断 `res.status`。

### 1.3 当前已使用的错误码

| code | 出现位置 | message |
|---|---|---|
| `0` | 全部接口 | `ok` |
| `400` | `GET /api/devices/{deviceId}/light` | `设备不存在` |
| `401` | `POST /api/auth/login` | `用户名或密码错误` |

### 1.4 鉴权说明

**目前后端没有任何鉴权拦截。** 登录接口返回的是写死的字符串 `"mock-jwt-token"`，且不存在 Security 配置、过滤器或拦截器去校验它。**除登录外的所有接口当前均可匿名直接访问**，请求头带不带 token 都一样。详见 `FEATURES.md` 的"待办事项"。

### 1.5 时间戳约定

所有 `ts` / `start` / `end` / `lastSeen` 均为 **毫秒级 Unix 时间戳**（`Long`），例如 `1755835200000`。

---

## 2. 接口清单

| # | 方法 | 路径 | 说明 | 数据来源 |
|---|---|---|---|---|
| 1 | POST | `/api/auth/login` | 用户登录 | 硬编码 |
| 2 | GET | `/api/dashboard/overview` | 大屏总览统计 | `device` 表聚合 |
| 3 | GET | `/api/devices` | 设备列表 | `device` 表 |
| 4 | GET | `/api/devices/{deviceId}/light` | 设备当前光照 | `device` 表 |
| 5 | GET | `/api/light/history` | 光照历史曲线 | `light_point` 表 |
| 6 | POST | `/api/agent/ask` | AI 运维问答 | 硬编码（桩） |

---

## 3. 接口详情

### 3.1 用户登录

`POST /api/auth/login`

代码位置：`controller/AuthController.java:12`

**请求体**

```json
{
  "username": "admin",
  "password": "123456"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `username` | string | 是 | 用户名 |
| `password` | string | 是 | 密码 |

**成功响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "token": "mock-jwt-token",
    "username": "admin",
    "role": "municipal"
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `token` | string | 当前恒为 `"mock-jwt-token"` |
| `username` | string | 恒为 `"admin"` |
| `role` | string | 恒为 `"municipal"`（市政角色） |

**失败响应**

```json
{ "code": 401, "message": "用户名或密码错误", "data": null }
```

**实现说明**：账号密码硬编码为 `admin` / `123456`，未查库、未加密、未签发真实 JWT。

---

### 3.2 大屏总览统计

`GET /api/dashboard/overview`

代码位置：`controller/DashboardController.java:19` → `service/DashboardService.java:16`

**请求参数**：无

**成功响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "totalDevices": 12,
    "onlineCount": 10,
    "offlineCount": 0,
    "avgLux": 235.67
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `totalDevices` | long | `device` 表总记录数 |
| `onlineCount` | long | `status == "ONLINE"` 的设备数 |
| `offlineCount` | long | `status == "OFFLINE"` 的设备数 |
| `avgLux` | double | 所有 `latestLux` 非空设备的算术平均值；无数据时返回 `0.0` |

设备超过 90 秒未上报时，`DeviceOfflineTask` 每 30 秒检查并将其标记为 `OFFLINE`，同时生成离线告警。

---

### 3.3 设备列表

`GET /api/devices`

代码位置：`controller/DeviceController.java:20` → `service/DeviceService.java:23`

**请求参数**：无（未做分页，全表返回）

**成功响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "id": 1,
      "code": "SL-001",
      "location": "人民路 12 号",
      "status": "ONLINE",
      "latestLux": 320.5,
      "lastSeen": 1755835200000
    }
  ]
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | long | 数据库主键 |
| `code` | string | 设备编号（唯一），如 `SL-001`。**这是各接口 `deviceId` 参数使用的值** |
| `location` | string | 安装位置；MQTT 自动创建的设备默认为 `"未知位置"` |
| `status` | string | `ONLINE` / `OFFLINE` |
| `latestLux` | double | 最近一次上报的光照值（lux），可能为 `null` |
| `lastSeen` | long | 最近一次上报/心跳的毫秒时间戳，可能为 `null` |

---

### 3.4 查询设备当前光照

`GET /api/devices/{deviceId}/light`

代码位置：`controller/DeviceController.java:26` → `service/DeviceService.java:45`

**路径参数**

| 参数 | 类型 | 说明 |
|---|---|---|
| `deviceId` | string | 设备编号，对应 `device.code`，如 `SL-001` |

**请求示例**

```
GET /api/devices/SL-001/light
```

**成功响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "deviceId": "SL-001",
    "lux": 320.5,
    "ts": 1755835200000
  }
}
```

**失败响应**（设备编号不存在）

```json
{ "code": 400, "message": "设备不存在", "data": null }
```

**实现说明**：读的是 `device` 表上冗余的 `latestLux` / `lastSeen` 快照字段，不查 `light_point` 历史表，所以是 O(1) 查询。

---

### 3.5 查询光照历史

`GET /api/light/history`

代码位置：`controller/LightController.java:17` → `service/LightService.java:19`

**Query 参数**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `deviceId` | string | 是 | 设备编号，对应 `light_point.device_code` |
| `start` | long | 是 | 起始毫秒时间戳（闭区间） |
| `end` | long | 是 | 结束毫秒时间戳（闭区间） |

**请求示例**

```
GET /api/light/history?deviceId=SL-001&start=1755748800000&end=1755835200000
```

**成功响应**（结果按 `ts` 升序排列，可直接喂给 ECharts）

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "deviceId": "SL-001",
    "points": [
      { "ts": 1755748800000, "lux": 12.3 },
      { "ts": 1755752400000, "lux": 45.8 },
      { "ts": 1755756000000, "lux": 210.0 }
    ]
  }
}
```

**⚠️ 注意事项**
- 三个参数**都是必填**，缺任意一个会由 Spring 抛 `MissingServletRequestParameterException`，因为项目没有全局异常处理器，前端会收到 Spring 默认的 500/400 错误页而**不是** `ApiResponse` 结构。
- 设备不存在或区间内无数据时，返回 `points: []`（空数组），不报错。
- **没有做条数上限和降采样**，时间区间跨度过大会一次性把全部原始点返回，需注意性能。

---

### 3.6 AI 运维问答

`POST /api/agent/ask`

代码位置：`controller/AgentController.java:18` → `service/AgentService.java:12`

**请求体**

```json
{ "question": "路灯不亮可能是什么原因？" }
```

**成功响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "answer": "常见原因：1. 供电异常；2. 通信模块故障；3. 传感器损坏；4. 网关离线。请先检查供电和网络。",
    "sources": [
      {
        "title": "路灯常见故障排查手册",
        "section": "第 3 节",
        "score": 0.92
      }
    ]
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `answer` | string | 回答正文 |
| `sources` | array | 引用来源列表（RAG 召回片段） |
| `sources[].title` | string | 文档标题 |
| `sources[].section` | string | 章节定位 |
| `sources[].score` | double | 相关度得分 |

**⚠️ 当前为桩实现**：`AgentService.ask()` **完全忽略入参 `question`**，无论问什么都返回上面这段固定文本和固定来源。接口契约（`AskRequest` / `AskResponse` / `SourceItem`）已定型，前端可照此联调；后续接入真实 RAG + 大模型时响应结构不变。

---

## 4. MQTT 上行接口（设备 → 后端）

后端作为 MQTT **订阅方**接入 Broker，接收设备上报。配置见 `config/MqttConfig.java`，消费逻辑见 `mqtt/MqttMessageListener.java`。

| 项 | 值 |
|---|---|
| Broker | `tcp://127.0.0.1:1883` |
| ClientId | `smartlamp-backend` |
| 用户名/密码 | 空（`application.yml` 中未配置，匿名连接） |
| CleanSession | `false`（自动重连，QoS 1） |
| 订阅主题 | `device/+/data`、`device/+/heartbeat` |

### 4.1 光照数据上报

**Topic**：`device/{deviceId}/data`

**Payload**

```json
{
  "deviceId": "SL-001",
  "lux": 320.5,
  "temperature": 26.3,
  "voltage": 220.1,
  "current": 0.42,
  "power": 92.4,
  "energy": 14.8,
  "lampStatus": "ON",
  "ts": 1755835200000
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `deviceId` | string | 否 | 缺省时从 topic 第 2 段自动提取；提供时必须与 Topic 一致 |
| `lux` | double | **是** | 非负光照值 |
| `ts` | long | 否 | 缺省时用服务器当前时间 `System.currentTimeMillis()` |
| `temperature` | double | 否 | 温度 |
| `voltage` | double | 否 | 电压 |
| `current` | double | 否 | 电流 |
| `power` | double | 否 | 功率 |
| `energy` | double | 否 | 累计电量 |
| `lampStatus` | string | 否 | `ON` / `OFF` |

**后端处理**
1. 按 `deviceId` 查 `device` 表；**不存在则自动创建**（`location="未知位置"`，`status="ONLINE"`）
2. 在同一事务中更新设备最新遥测快照、`lastSeen`、`status="ONLINE"`
3. 向 `light_point` 表写入结构化字段和原始 Payload；相同设备和时间戳的 QoS 重投消息不会重复入库

### 4.2 心跳上报

**Topic**：`device/{deviceId}/heartbeat`

**Payload**

```json
{
  "deviceId": "SL-001",
  "ts": 1755835200000
}
```

**后端处理**：查/建设备记录，更新 `lastSeen` 与 `status="ONLINE"`，**不写**历史表。

### 4.3 错误处理

Topic、JSON、设备编号、时间戳和数值字段会在事务写入前校验。失败消息不会更新设备或历史数据，而是记录到 `mqtt_dead_letter` 表（Topic、原始 Payload、错误原因、接收时间），便于运维定位和后续补偿。

---

## 5. 数据库表结构

由 JPA `ddl-auto: update` 自动建表/更新。

### 5.1 `device` —— 设备表

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | 主键 |
| `code` | VARCHAR | UNIQUE, NOT NULL | 设备编号 |
| `location` | VARCHAR | | 安装位置 |
| `status` | VARCHAR | | `ONLINE` / `OFFLINE` |
| `latest_lux` | DOUBLE | | 最新光照值（冗余快照） |
| `latest_temperature` | DOUBLE | | 最新温度 |
| `latest_voltage` | DOUBLE | | 最新电压 |
| `latest_current` | DOUBLE | | 最新电流 |
| `latest_power` | DOUBLE | | 最新功率 |
| `latest_energy` | DOUBLE | | 最新累计电量 |
| `last_seen` | BIGINT | | 最后上报毫秒时间戳 |
| `created_at` | DATETIME | | 创建时间 |

### 5.2 `light_point` —— 光照历史表

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | 主键 |
| `device_code` | VARCHAR | NOT NULL, UNIQUE 组合键 | 设备编号（**逻辑关联 `device.code`，无外键**） |
| `lux` | DOUBLE | NOT NULL | 光照值 |
| `ts` | BIGINT | NOT NULL, UNIQUE 组合键 | 采集毫秒时间戳 |
| `temperature` / `voltage` / `current` | DOUBLE | | 环境与电气遥测 |
| `power` / `energy` | DOUBLE | | 功率与累计电量 |
| `lamp_status` | VARCHAR | | 灯具状态 |
| `raw_payload` | LONGTEXT | | 原始设备报文，用于审计和重放 |
| `created_at` | DATETIME | | 入库时间 |

`(device_code, ts)` 已建立复合索引和唯一约束，用于时间范围查询与消息幂等。

### 5.3 `mqtt_dead_letter` —— MQTT 拒绝消息表

保存 `topic`、`payload`、`error_message`、`received_at`，不让格式错误或契约不一致的消息静默消失。

---

## 6. 前端对接注意事项

1. **判 `code` 不判 HTTP 状态** —— 业务失败也返回 HTTP 200。
2. **没有配置 CORS** —— 前端独立端口（如 Vite 5173）直连会被浏览器拦截，需要后端加 `@CrossOrigin`/全局 CORS 配置，或前端配代理。
3. **token 无实际作用** —— 后端不校验，前端自行决定是否在 header 里带。
4. **设备标识统一用 `code`** —— 各接口路径/参数里的 `deviceId` 指的都是 `device.code`（如 `SL-001`），**不是**数据库主键 `id`。
5. **参数缺失不会返回统一格式** —— 无全局异常处理器，参数校验失败/服务器异常会返回 Spring 默认错误结构，前端需容错。
