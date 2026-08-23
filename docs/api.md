# API 说明

所有接口返回 JSON，前端页面使用同一批接口。

## 实时与配置

`GET /api/summary`

返回设备、告警、配置、最新光照和历史数据摘要。

`GET /events`

SSE 实时通道。事件类型为 `summary`。

`GET /api/config`

读取自动控制配置。

`PUT /api/config`

请求体：

```json
{
  "autoControl": true,
  "luxThreshold": 120,
  "hysteresis": 35,
  "heartbeatTimeoutMs": 15000
}
```

## 设备

`GET /api/devices`

返回设备列表。

`POST /api/devices`

请求体：

```json
{
  "name": "北门 01",
  "location": "北门主路",
  "binding": "NB-01"
}
```

`PATCH /api/devices/:id`

请求体支持更新 `name`、`location`、`binding`、`bound`。

`POST /api/devices/:id/control`

请求体：

```json
{
  "action": "ON"
}
```

`action` 可为 `ON` 或 `OFF`。

## 遥测与告警

`GET /api/telemetry?deviceId=SL-001&limit=80`

返回历史光照记录。

`GET /api/alerts`

返回告警记录。

`PATCH /api/alerts/:id/resolve`

将告警标记为已确认。

## 模拟器

`POST /api/simulator/scenario`

请求体：

```json
{
  "scenario": "low-light"
}
```

支持 `normal`、`low-light`、`daylight`、`outage`。

## 维护问答

`POST /api/assistant/chat`

请求体：

```json
{
  "question": "设备离线怎么办？"
}
```
