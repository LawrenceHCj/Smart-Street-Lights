# AI 智能体模块

负责人：5号。

当前是本地关键词检索版，后续可替换为 MaxKB、RAGFlow 或大模型 API。

建议职责：
- 维护 `backend/rag/knowledgeBase.js`。
- 维护 `backend/rag/prompts.md`。
- 扩展 `assistantService.js`，支持设备上下文、告警上下文和知识来源。

## 本地测试

在项目根目录（WSL）执行：

```text
node --test backend/agent/assistantService.test.js
```

测试覆盖：知识命中、无命中提示、空问题校验、响应格式。
