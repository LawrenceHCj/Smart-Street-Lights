# AI 智能体模块

负责人：5号。

当前是"关键词检索 RAG + 可选大模型增强"版本，后续可替换为向量检索（MaxKB/RAGFlow）或完整智能体工作流。

## RAG 流程

用户问题 → retriever 检索 → Top K 相关知识 → 组装上下文 → Prompt → LLM → 回答

- `rag/knowledgeBase.js`：规范化知识条目（id/title/category/content/keywords/source）。
- `rag/retriever.js`：关键词命中评分检索，返回匹配知识、来源与相关度；后续替换为向量检索时调用方接口不变。
- `rag/prompts.md`：大模型 System Prompt（智能体行为约束）。
- `assistantService.js`：问答入口，负责校验、调用检索、组装上下文与 Prompt、调用大模型、降级与响应组装。
- `llmClient.js`：大模型调用封装，与具体厂商解耦（兼容 OpenAI 接口协议即可）。

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
- 响应中 `sources` 字段返回引用的知识条目标题，供前端显示"参考来源"。

## 本地测试

在项目根目录（WSL）执行：

```text
node --test backend/agent/assistantService.test.js backend/rag/retriever.test.js
```

测试覆盖：检索命中/评分排序/limit/无命中、知识命中、无命中提示、空问题校验、响应格式、大模型成功调用、失败降级。
