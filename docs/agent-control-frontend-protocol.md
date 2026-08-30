# Agent 控制功能前后端联调协议（建议稿）

> 供 2号 前端联调使用。标注"已就绪"的接口可直接联调；标注"待实现"的字段由 5号 在联调前补齐（见 §9）。

## 0. 总览

| 接口 | 方法 | 路径 | 状态 |
|---|---|---|---|
| 聊天（主入口） | POST | /api/agent/ask | 已就绪 |
| 聊天（兼容旧） | POST | /api/assistant/chat | 已就绪 |
| 确认操作 | POST | /api/agent/actions/{actionId}/confirm | 已就绪 |
| 取消操作 | POST | /api/agent/actions/{actionId}/cancel | 已就绪 |
| 聊天响应中的结构化 `action` 字段 | — | — | **已实现**（批量关闭/阈值/自动模式等需确认操作时返回，前端据此渲染聊天确认卡片） |

- 所有 `/api/agent/**` 接口需要 JWT 认证（`Authorization: Bearer <token>`），前端 axios 拦截器已统一携带。
- 统一响应包装：HTTP 恒 200，业务结果看 body：`{ "code": 0, "message": "ok", "data": ... }`；`code=0` 成功，`400` 参数类/业务拒绝，`500` 服务器错误。

## 1. 聊天 API

**请求**（AskRequest）：

```json
{
  "question": "帮我关闭 lamp001",
  "conversationId": "可选；多轮记忆时回传上一轮返回的 conversationId"
}
```

**响应**（data 为 AskResponse）：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "answer": "已生成待确认操作请求：关闭 lamp001（当前在线，当前状态：开启）。请确认后执行。",
    "conversationId": "c6f3...",
    "sources": [
      { "title": "关灯控制请求", "section": "action", "score": 1.0 }
    ],
    "action": {
      "actionId": "4f3e2c1a-...",
      "actionType": "TURN_OFF_LIGHT",
      "targetId": "lamp001",
      "summary": "关闭路灯",
      "riskLevel": "LOW_WRITE",
      "status": "PENDING_CONFIRMATION",
      "expiresAt": 1786662600000,
      "originalState": "ON",
      "targetState": "OFF"
    }
  }
}
```

- `action` 字段**仅在产生待确认操作时返回**；普通问答、知识库回答没有该字段。
- **开灯/关灯为免确认操作**（权限调整后）：admin/operator 提问时系统直接执行，执行结果在 `answer` 中如实转述（不返回 action 字段、无确认卡片）；municipal 或无权限时回答拒绝原因。
- 需确认操作（修改阈值/自动模式、批量开灯/批量关灯）才返回 `action` 字段与确认卡片。
- `expiresAt` 为 epoch 毫秒（创建后 2 分钟有效）。
- `actionType` 取值：`TURN_ON_LIGHT` / `TURN_OFF_LIGHT`（免确认，通常不出现在卡片）/ `TURN_OFF_ALL` / `TURN_ON_ALL` / `UPDATE_LUX_THRESHOLD` / `UPDATE_AUTO_MODE`。
- `summary` 为推荐展示文案（如"关闭路灯"、"修改光照阈值"、"开启自动控制"）；前端也可自行按 actionType 映射文案。
- `originalState` / `targetState` 用于卡片上的"当前状态 / 目标状态"展示（如 ON/OFF）。

## 2. 确认 API

**请求**：`POST /api/agent/actions/{actionId}/confirm`，请求体可为空对象 `{}`。
**必须通过 actionId 调用，绝不发送自然语言"确认"文本。**

**响应**（data 为 AgentAction 全字段）：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "actionId": "4f3e2c1a-...",
    "actionType": "TURN_OFF_LIGHT",
    "targetType": "device",
    "targetId": "lamp001",
    "arguments": {},
    "riskLevel": "LOW_WRITE",
    "status": "COMMAND_ACCEPTED",
    "requestedAt": 1786662480000,
    "expiresAt": 1786662600000,
    "requestedBy": "admin",
    "originalState": "ON",
    "targetState": "OFF",
    "message": "COMMAND_ACCEPTED：控制指令已发送，但当前尚未获得设备执行确认（命令号 CMD-...）"
  }
}
```

前端按 `status` + `message` 展示结果（见 §7、§8）。

## 3. 取消 API

**请求**：`POST /api/agent/actions/{actionId}/cancel`，请求体可为空对象 `{}`。

**响应**：同确认 API，`data.status = "CANCELLED"`，`data.message = "已由用户取消"`。

## 4. 请求格式汇总

| 接口 | 路径参数 | 请求体 |
|---|---|---|
| 聊天 | — | `{question: string, conversationId?: string}` |
| 确认 | actionId | 无（可空对象） |
| 取消 | actionId | 无（可空对象） |

## 5. 响应格式汇总

- 成功：`{code: 0, message: "ok", data: <见各接口>}`
- 失败：`{code: 400|500, message: "<原因>", data: null}`

## 6. 错误格式与常见错误

| 场景 | code | message 示例 |
|---|---|---|
| actionId 不存在 | 400 | `Action 不存在: xxx` |
| 非发起者确认/取消 | 400 | `Action 不存在或不属于当前用户: xxx`（不泄露他人操作的存在性） |
| 角色无控制权限 | 400 | `当前角色无控制权限，仅 admin/operator 可确认控制操作` |
| 重复确认/取消 | 400 | `只有待确认状态的 Action 才能操作（当前: COMMAND_ACCEPTED），请勿重复操作` |
| 已过期 | 400 | `Action 已过期: xxx` |
| 确认时设备已被删除 | 400 | `确认时校验未通过：设备不存在（可能已被删除）: lamp001` |
| 确认时设备已离线 | 400 | `确认时校验未通过：设备当前离线: lamp001（当前状态: OFFLINE）` |
| 确认时设备状态已变化 | 400 | `确认时校验未通过：设备状态已变化，已处于目标状态（OFF），无需执行` |
| 执行失败 | 400 | `FAILED：MQTT 发布失败: device/lamp001/cmd` |
| 等待回执超时 | 400 | `TIMEOUT：控制指令已发送，但设备在 5000 毫秒内未确认执行` |

前端统一展示 `message` 即可；收到这些错误时同步处理按钮状态（见 §8）。

## 7. Action 状态

| 状态 | 含义 | 前端处理 |
|---|---|---|
| PENDING_CONFIRMATION | 待确认 | 显示确认卡片 + 确认/取消按钮 |
| CONFIRMED | 已确认（瞬态） | 几乎不可见（接口同步执行） |
| EXECUTING | 执行中（瞬态） | 几乎不可见 |
| SUCCESS | 设备已确认执行 | 显示"执行成功" |
| COMMAND_ACCEPTED | 命令已下发，**未收到设备执行确认** | 显示"控制指令已发送，但当前尚未获得设备执行确认"——**不要显示"成功"** |
| FAILED | 执行失败 | 显示失败原因（message） |
| CANCELLED | 用户已取消 | 显示"已取消" |
| EXPIRED | 已过期 | 显示"已过期，请重新发起" |

## 8. 按钮显示 / 隐藏 / 禁用规则

1. **显示**：聊天响应含 `action` 字段且 `action.status == "PENDING_CONFIRMATION"` → 渲染确认卡片：
   ```
   AI请求执行操作
   操作：关闭路灯
   设备：lamp001
   当前状态：开启（originalState）
   目标状态：关闭（targetState）
   [确认执行] [取消]
   ```
2. **隐藏**：响应无 `action` 字段（普通问答）；或状态已进入终态（SUCCESS/COMMAND_ACCEPTED/FAILED/CANCELLED/EXPIRED）。
3. **禁用**：① 点击确认或取消后、HTTP 返回前（防重复点击，后端也会拒绝重复操作）；② 本地时间已超过 `expiresAt`。
4. **确认/取消返回后**：按 `data.status` + `data.message` 更新卡片为结果文案（§7），按钮移除/禁用。
5. **无需轮询**：当前确认接口同步返回终态；待 4号 设备回执链路接入后如改为异步执行，再引入状态查询接口（预留）。

## 9. Action 过期后的显示

- 前端本地判断：`expiresAt <= Date.now()` → 按钮禁用/隐藏，卡片显示"已过期，请重新发起"。
- 即使前端未拦截，后端确认/取消会返回 400 `Action 已过期: ...`，前端收到后同样隐藏按钮并提示用户重新发起。
- 重新发起：用户在聊天中重新提出控制请求即可生成新的 actionId。

## 10. 联调待办

- [x] ~~AskResponse 增加结构化 `action` 字段~~（已实现：需确认操作时返回，含 actionId/actionType/targetId/summary/riskLevel/expiresAt/status/originalState/targetState）
- [ ] **2号（前端）**：Chat.vue 在收到含 `action` 字段的回复时渲染确认卡片（[确认执行][取消]），按 actionId 调 confirm/cancel 接口，按钮状态规则见 §8
- [ ] （可选，2号 提出需求后加）：`GET /api/agent/actions/{actionId}` 查询接口，用于刷新/找回待确认操作。
