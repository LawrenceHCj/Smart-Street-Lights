# AI 智能体模块

负责人：5号。

当前是"本地关键词检索 + 可选大模型增强"版本，后续可扩展为完整智能体工作流或替换为 MaxKB、RAGFlow。

## 目录职责

- `assistantService.js`：问答入口，负责校验、知识库检索、调用大模型、降级与响应组装。
- `llmClient.js`：大模型调用封装，与具体厂商解耦（兼容 OpenAI 接口协议即可）。
- `rag/knowledgeBase.js`：本地知识库。
- `rag/prompts.md`：大模型 System Prompt。

## 大模型接入（可选）

通过环境变量配置，兼容 OpenAI 接口协议的厂商均可（OpenAI / DeepSeek / Kimi / 通义千问 等）：

- `LLM_API_KEY`：模型 API Key（必填，未配置时走本地知识库回答）
- `LLM_BASE_URL`：接口地址，默认 `https://api.openai.com/v1`
- `LLM_MODEL`：模型名称（必填）

使用方法二选一：

1. 复制项目根目录 `.env.example` 为 `.env`，填入真实值后照常启动；
2. 启动前在 Shell 中导出上述环境变量（Shell 中已存在的值优先于 `.env`）。

`.env` 已被 Git 忽略。不要把真实 API Key 写进代码或提交到仓库。

## 降级策略

- 大模型不可用（未配置、网络失败、超时、非 200、返回为空）时自动降级为本地知识库回答，接口始终可用，服务器不会因大模型故障崩溃。
- 响应中 `answerSource` 字段标识回答来源：`llm`（大模型生成）或 `local`（本地知识库/未找到）。

## 本地测试

在项目根目录（WSL）执行：

```text
node --test backend/agent/assistantService.test.js
```

测试覆盖：知识命中、无命中提示、空问题校验、响应格式、大模型成功调用、失败降级。
