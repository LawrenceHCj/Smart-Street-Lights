# 智能路灯后端 —— 功能实现说明

> 基于源码逐文件扫描整理，如实反映**当前已实现**与**尚未实现**的部分。
> 代码规模：25 个 Java 文件 / 649 行。

---

## 一、项目定位

面向市政场景的**智能路灯监控后端**。核心链路是：

```
路灯设备 ──MQTT上报──> 后端订阅入库 ──REST API──> 前端大屏/管理台
                                    └──> AI 运维问答（桩）
```

本质上是一个**只读的光照遥测采集与展示系统**：设备单向上报，后端存储 + 统计 + 对外查询。**尚不具备反向下发控制指令的能力**（不能远程开关灯、调亮度）。

---

## 二、技术栈

| 层 | 选型 |
|---|---|
| 框架 | Spring Boot 4.1.1（Java 17） |
| Web | spring-boot-starter-webmvc |
| 持久层 | Spring Data JPA + Hibernate（`ddl-auto: update` 自动建表） |
| 数据库 | MySQL（`localhost:3307/smartlamp`） |
| 物联网通信 | Spring Integration MQTT + Eclipse Paho 1.2.5 |
| JSON | Jackson 3.x（注意包名是 `tools.jackson.databind`，Boot 4 起的新坐标） |
| 简化代码 | Lombok（`@Data`） |
| 参数校验 | spring-boot-starter-validation（**已引入依赖，但代码中一处未用**） |

---

## 三、代码结构

```
com.smartlamp
├── SmartlampApplication.java      启动类
├── config/
│   └── MqttConfig.java            MQTT 连接工厂 + 入站通道 + 订阅适配器
├── mqtt/
│   └── MqttMessageListener.java   MQTT 消息消费与入库（核心业务）
├── controller/                    REST 层，4 个控制器共 6 个端点
│   ├── AuthController.java        登录
│   ├── DashboardController.java   大屏总览
│   ├── DeviceController.java      设备列表 / 当前光照
│   ├── LightController.java       历史曲线
│   └── AgentController.java       AI 问答
├── service/                       业务层
│   ├── DashboardService.java      内存流式聚合统计
│   ├── DeviceService.java         设备查询 + 实体→DTO 转换
│   ├── LightService.java          历史区间查询
│   └── AgentService.java          AI 问答（桩实现）
├── repository/                    JPA 接口
│   ├── DeviceRepository.java      findByCode
│   └── LightPointRepository.java  findByDeviceCodeAndTsBetweenOrderByTsAsc
├── entity/                        JPA 实体
│   ├── Device.java                device 表
│   └── LightPoint.java            light_point 表
└── dto/                           10 个传输对象（含统一响应包装 ApiResponse）
```

分层清晰，标准的 `Controller → Service → Repository → Entity` 三层结构，Controller 不直接碰实体，对外一律返回 DTO。

---

## 四、已实现的功能

### ✅ 功能 1：MQTT 设备数据接入（核心）

**代码**：`config/MqttConfig.java` + `mqtt/MqttMessageListener.java`

后端以 `smartlamp-backend` 为 ClientId 连接 Broker，用通配符订阅两类主题：

| 主题 | 用途 |
|---|---|
| `device/+/data` | 光照数据上报 |
| `device/+/heartbeat` | 设备心跳 |

**实现细节：**

- **设备免注册自动纳管** —— 收到未知 `deviceId` 的消息时，自动 `INSERT` 一条设备记录（`location="未知位置"`，`status="ONLINE"`），新路灯上线即插即用，无需事先在后台录入。
- **设备 ID 双通道解析** —— 优先取 payload 里的 `deviceId` 字段；没有则从 topic 路径第 2 段兜底提取（`device/SL-001/data` → `SL-001`），兼容两种设备固件实现。
- **时间戳兜底** —— payload 无 `ts` 时用服务器当前时间，容忍设备未对时。
- **双写策略** —— `data` 消息同时做两件事：更新 `device` 表的快照字段（`latestLux` / `lastSeen` / `status`）+ 向 `light_point` 追加一条历史。**这个冗余设计是有意义的**：查"当前光照"走快照 O(1)，查"历史曲线"走明细表，两条读路径互不干扰。
- **心跳轻量处理** —— `heartbeat` 只刷新 `lastSeen` 和在线状态，不写历史表，避免心跳污染曲线数据。
- **异常兜底** —— 整段包 try-catch，脏消息不会打断监听链路。

### ✅ 功能 2：设备管理查询

**代码**：`DeviceController` + `DeviceService`

- `GET /api/devices` —— 返回全部设备列表，实体转 `DeviceDTO` 后输出，隔离了数据库结构。
- `GET /api/devices/{deviceId}/light` —— 按设备编号查当前光照，设备不存在时返回业务错误码 400 + `"设备不存在"`。

### ✅ 功能 3：光照历史曲线查询

**代码**：`LightController` + `LightService` + `LightPointRepository`

- `GET /api/light/history?deviceId=&start=&end=` —— 按设备编号 + 毫秒时间戳区间查询。
- 利用 JPA 方法名派生查询 `findByDeviceCodeAndTsBetweenOrderByTsAsc`，**结果已按时间升序**，前端拿到即可直接渲染 ECharts 折线图，无需二次排序。
- 输出结构为 `{ deviceId, points: [{ts, lux}] }`，无数据时返回空数组而非报错。

### ✅ 功能 4：大屏总览统计

**代码**：`DashboardController` + `DashboardService`

- `GET /api/dashboard/overview` —— 一次性返回设备总数、在线数、离线数、全网平均光照四项指标。
- 实现方式是 `findAll()` 拉全表后用 Java Stream 在内存中聚合。
- 平均光照会跳过 `latestLux` 为 null 的设备，全空时返回 `0.0`，不会出现 `NaN`。

### ✅ 功能 5：统一响应格式

**代码**：`dto/ApiResponse.java`

泛型包装类 `ApiResponse<T>`，提供 `success(data)` / `error(code, message)` 两个静态工厂。所有 6 个端点统一返回该结构，前端可以写一个通用的响应拦截器处理。

### ⚠️ 功能 6：用户登录（硬编码，非生产可用）

**代码**：`AuthController`

- `POST /api/auth/login`，账号密码**写死**为 `admin` / `123456`。
- 校验通过返回固定字符串 token `"mock-jwt-token"` 和角色 `"municipal"`。
- **接口契约（`LoginRequest` / `LoginResponse`）已定型**，前端可以先按此联调登录流程，后续替换实现不影响前端。

### ⚠️ 功能 7：AI 运维问答（桩实现）

**代码**：`AgentController` + `AgentService`

- `POST /api/agent/ask`，请求体 `{question}`，响应 `{answer, sources[]}`。
- **`AgentService.ask()` 完全忽略入参**，无论问什么都返回同一段"路灯不亮常见原因"的固定文本 + 一条固定引用来源。
- 但 `AskRequest` / `AskResponse` / `SourceItem` 三个 DTO 已按 **RAG 检索增强生成**的标准形态设计好（回答正文 + 带相关度得分的引用片段列表），后续接入向量库和大模型时，**只需替换 Service 内部实现，接口结构完全不用改**。

---

## 五、尚未实现 / 需要注意的问题

按优先级排序：

### 🔴 P0 —— 安全：所有接口完全裸奔

登录接口发的是假 token，且**全项目没有任何 Spring Security 配置、过滤器或拦截器去校验它**。这意味着：

> `/api/devices`、`/api/dashboard/overview`、`/api/light/history` 等**全部接口都可以不登录直接匿名访问**。

同时账号密码硬编码在源码里，密码明文对比、未加盐哈希。

**建议**：引入 `spring-boot-starter-security` + JWT（jjwt / java-jwt），把用户表落库、密码用 BCrypt 存储，加一个 JWT 校验过滤器。

### 🔴 P0 —— `offlineCount` 恒为 0

`MqttMessageListener` 里只有 3 处 `setStatus("ONLINE")`，**全项目没有任何一行代码把设备置为 `"OFFLINE"`**（`grep OFFLINE` 只命中 `DashboardService` 里的那次统计比较）。

后果：设备断电、掉网后 `status` 永远停留在 `ONLINE`，大屏上离线数永远显示 0，**监控功能实际是失效的**。

**建议**：加一个 `@Scheduled` 定时任务（如每分钟一次），扫描 `lastSeen` 超过阈值（如 3 分钟）的设备，置为 `OFFLINE`：

```java
@Scheduled(fixedRate = 60_000)
public void markOfflineDevices() {
    long threshold = System.currentTimeMillis() - 3 * 60 * 1000;
    // UPDATE device SET status='OFFLINE' WHERE last_seen < threshold AND status='ONLINE'
}
```
（需在启动类加 `@EnableScheduling`）

### 🟠 P1 —— 未配置 CORS

没有 `@CrossOrigin`，也没有全局 CORS 配置。前端如果跑在独立端口（Vue/React 开发服务器），**浏览器会直接拦截所有请求**。联调第一步就会卡在这里。

### 🟠 P1 —— 没有全局异常处理器

缺少 `@RestControllerAdvice`。后果是一旦发生参数缺失（如 `/api/light/history` 少传 `start`）、类型转换失败或任何未捕获异常，**返回的是 Spring 默认错误 JSON，而不是统一的 `ApiResponse` 结构**，前端的通用响应处理逻辑会解析失败。

### 🟠 P1 —— 引入了 validation 依赖但完全没用

`pom.xml` 里有 `spring-boot-starter-validation`，但所有 `@RequestBody` 参数上**没有一个 `@Valid`**，DTO 字段上也没有任何约束注解。`LoginRequest.username` 传 null 会直接进业务逻辑（这里恰好被 `"admin".equals(...)` 的写法挡住了 NPE，但属于侥幸）。

### 🟡 P2 —— 无法下发控制指令

MQTT 目前**只配了入站**（`MqttPahoMessageDrivenChannelAdapter`），没有出站的 `MqttPahoMessageHandler`。所以后端只能"看"，不能"控"——**无法远程开关灯、调节亮度、重启设备**。对一个"智能路灯"系统来说，这是功能地图上最大的一块空白。

### 🟡 P2 —— 查询无分页、无降采样

- `GET /api/devices` 全表返回，设备量上千后响应体会很大。
- `GET /api/light/history` 不限制返回条数。若设备 10 秒上报一次，查一天就是 **8640 个点**，查一个月就是 26 万个点，一次性全返回会同时压垮后端内存和前端图表。
- `DashboardService` 用 `findAll()` 拉全表在内存里聚合，应改为数据库侧的 `COUNT` / `AVG` 聚合 SQL。

### 🟡 P2 —— `light_point` 表缺索引

历史查询条件是 `(device_code, ts)`，但表上无任何索引，数据量增长后会全表扫描。建议补：

```sql
CREATE INDEX idx_lightpoint_code_ts ON light_point (device_code, ts);
```

### 🟡 P2 —— 敏感配置明文入库

`application.yml` 里数据库密码 `123456` 明文硬编码。建议改为环境变量占位：`password: ${DB_PASSWORD}`。

### ⚪ P3 —— 其他

- **日志用 `System.out.println`** —— `MqttMessageListener` 里用的是标准输出而非 SLF4J，无法按级别过滤、无法输出到文件。建议换成 `@Slf4j` + `log.info/error`。
- **测试形同虚设** —— 只有一个空的 `contextLoads()`，无任何业务用例。且该测试依赖真实 MySQL 和 MQTT Broker 可用，CI 环境下大概率跑不起来。
- **MQTT 消息静默丢弃** —— 解析失败只打印一行错误，无重试、无死信队列，数据会真的丢。
- **`lux` 字段无空值保护** —— `json.get("lux").asDouble()` 未做 `has()` 判断（`deviceId` 和 `ts` 都做了），设备漏传 `lux` 会抛 NPE，该条数据丢失。
- **`light_point` 与 `device` 无外键关联** —— 靠 `device_code` 字符串逻辑关联，删除设备不会级联清理历史数据。
- **`static/` 和 `templates/` 目录为空** —— 纯后端 API 服务，无内嵌前端页面。

---

## 六、功能完成度总览

| 模块 | 状态 | 说明 |
|---|---|---|
| MQTT 数据接入 | ✅ 完成 | 订阅、解析、自动纳管、双写入库均已实现 |
| 设备列表查询 | ✅ 完成 | 缺分页 |
| 当前光照查询 | ✅ 完成 | 走快照字段，性能好 |
| 历史曲线查询 | ✅ 完成 | 缺条数限制和降采样 |
| 大屏统计 | ⚠️ 部分 | 接口通，但离线数恒为 0 |
| 统一响应格式 | ✅ 完成 | — |
| 用户登录 | ⚠️ 桩 | 硬编码账号 + 假 token |
| 鉴权拦截 | ❌ 未做 | **所有接口可匿名访问** |
| AI 运维问答 | ⚠️ 桩 | 返回固定文本，接口契约已定 |
| 设备控制下发 | ❌ 未做 | 无 MQTT 出站通道 |
| 离线检测 | ❌ 未做 | 无定时任务 |
| 全局异常处理 | ❌ 未做 | — |
| CORS | ❌ 未做 | 前后端分离联调会被拦 |
| 参数校验 | ❌ 未做 | 依赖已引入未使用 |
| 单元测试 | ❌ 未做 | 仅一个空壳测试 |

**整体判断**：数据采集主链路（MQTT → MySQL → REST）已经完整跑通，架构分层规范、DTO 设计合理，是一个结构健康的骨架。当前主要缺口集中在**安全**（无鉴权）、**监控闭环**（无离线检测）、**控制能力**（无指令下发）三块。

---

## 七、建议的下一步实施顺序

1. **加 CORS 配置** —— 5 分钟的事，不做前端根本连不上，优先级最高。
2. **加全局异常处理器** —— 保证任何情况下响应结构一致。
3. **实现离线检测定时任务** —— 让大屏的在线/离线统计真正有意义。
4. **接入 Spring Security + 真实 JWT** —— 用户表落库、BCrypt 加密、Token 校验过滤器。
5. **补 MQTT 出站通道** —— 实现远程开关灯 / 调光，补齐"智能"的控制闭环。
6. **历史查询加降采样与条数上限** —— 按时间粒度聚合（分钟/小时均值），避免大区间打爆。
7. **补数据库索引 + 分页 + 聚合 SQL 优化**。
8. **补业务单元测试** —— Service 层用 Mock Repository，避免依赖真实中间件。
