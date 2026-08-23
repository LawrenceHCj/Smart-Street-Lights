const test = require("node:test");
const assert = require("node:assert/strict");
const http = require("node:http");
const { answerMaintenanceQuestion } = require("./assistantService");

// 测试间隔离：显式置空 LLM 环境变量，避免读到用户本地 .env 影响断言
function disableLlm() {
  process.env.LLM_API_KEY = "";
  process.env.LLM_BASE_URL = "";
  process.env.LLM_MODEL = "";
}

function clearLlm() {
  delete process.env.LLM_API_KEY;
  delete process.env.LLM_BASE_URL;
  delete process.env.LLM_MODEL;
}

function startMockLlmServer(respond) {
  return new Promise((resolve) => {
    const server = http.createServer(respond);
    server.listen(0, "127.0.0.1", () => resolve({ server, port: server.address().port }));
  });
}

test.beforeEach(disableLlm);
test.afterEach(clearLlm);

test("路灯离线排查问题返回知识库回答（本地模式）", async () => {
  const result = await answerMaintenanceQuestion("路灯离线应该怎么排查？");

  assert.equal(typeof result.answer, "string");
  assert.ok(result.answer.includes("心跳"), "回答应包含离线排查内容");
  assert.ok(result.sources.includes("设备离线排查"), "来源应包含离线排查条目");
  assert.equal(result.answerSource, "local");
  assert.ok(result.generatedAt.endsWith("Z"), "generatedAt 应为 ISO 时间");
});

test("阈值相关问题返回光照联动控制知识（本地模式）", async () => {
  const result = await answerMaintenanceQuestion("光照阈值怎么设置？");

  assert.ok(result.sources.includes("光照联动控制"));
  assert.equal(result.answerSource, "local");
});

test("手动控制问题返回控制回执知识（本地模式）", async () => {
  const result = await answerMaintenanceQuestion("手动控制没有回执怎么办？");

  assert.ok(result.sources.includes("手动控制回执"));
});

test("设备频繁离线问题命中离线排查知识（本地模式）", async () => {
  const result = await answerMaintenanceQuestion("设备频繁离线应该检查什么？");

  assert.ok(result.sources.includes("设备离线排查"), "回答应引用离线排查知识");
  assert.ok(result.answer.includes("心跳"));
});

test("光照联动异常问题命中光照联动控制知识（本地模式）", async () => {
  const result = await answerMaintenanceQuestion("光照联动异常应该怎么排查？");

  assert.ok(result.sources.includes("光照联动控制"), "回答应引用光照联动控制知识");
  assert.ok(result.answer.includes("阈值"));
});

test("无命中问题返回空来源和明确提示（本地模式）", async () => {
  const result = await answerMaintenanceQuestion("今天天气怎么样？");

  assert.deepEqual(result.sources, []);
  assert.ok(result.answer.includes("未找到"), "无命中时应明确提示未找到");
  assert.equal(result.answerSource, "local");
});

test("空问题抛出 400 错误", async () => {
  await assert.rejects(() => answerMaintenanceQuestion(""), (error) => error.statusCode === 400);
});

test("非字符串问题抛出 400 错误", async () => {
  await assert.rejects(() => answerMaintenanceQuestion({ foo: "bar" }), (error) => error.statusCode === 400);
});

test("响应格式字段稳定", async () => {
  const result = await answerMaintenanceQuestion("设备断开怎么办？");

  assert.deepEqual(Object.keys(result).sort(), ["answer", "answerSource", "generatedAt", "sources"]);
  assert.ok(Array.isArray(result.sources));
  assert.ok(result.sources.every((source) => typeof source === "string"));
});

test("配置大模型时命中问题由模型生成回答", async (t) => {
  let received = null;
  const { server, port } = await startMockLlmServer((req, res) => {
    let body = "";
    req.on("data", (chunk) => (body += chunk));
    req.on("end", () => {
      received = JSON.parse(body);
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ choices: [{ message: { content: "模拟大模型回答：请先检查供电与网关连接。" } }] }));
    });
  });
  t.after(() => server.close());

  process.env.LLM_API_KEY = "test-key";
  process.env.LLM_BASE_URL = `http://127.0.0.1:${port}/v1`;
  process.env.LLM_MODEL = "mock-model";

  const result = await answerMaintenanceQuestion("路灯离线应该怎么排查？");

  assert.equal(result.answer, "模拟大模型回答：请先检查供电与网关连接。");
  assert.equal(result.answerSource, "llm");
  assert.ok(result.sources.includes("设备离线排查"), "来源仍应包含知识库条目");
  assert.equal(received.model, "mock-model");
  assert.ok(received.messages[0].content.includes("智慧路灯维护助手"), "System Prompt 应包含角色约束");
  assert.ok(received.messages[1].content.includes("《设备离线排查》"), "用户消息应携带知识库上下文");
  assert.ok(received.messages[1].content.includes("告警处理"), "上下文应包含知识分类");
});

test("新增巡检知识命中后仍可由模型结合上下文回答", async (t) => {
  const { server, port } = await startMockLlmServer((req, res) => {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ choices: [{ message: { content: "模拟大模型回答：建议每季度巡检一次。" } }] }));
  });
  t.after(() => server.close());

  process.env.LLM_API_KEY = "test-key";
  process.env.LLM_BASE_URL = `http://127.0.0.1:${port}/v1`;
  process.env.LLM_MODEL = "mock-model";

  const result = await answerMaintenanceQuestion("路灯一般多久需要维护一次？");

  assert.equal(result.answerSource, "llm");
  assert.ok(result.sources.includes("例行巡检清单"));
});

test("模型接口返回 500 时降级为本地知识库回答", async (t) => {
  const { server, port } = await startMockLlmServer((req, res) => {
    res.writeHead(500, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ error: "mock failure" }));
  });
  t.after(() => server.close());

  process.env.LLM_API_KEY = "test-key";
  process.env.LLM_BASE_URL = `http://127.0.0.1:${port}/v1`;
  process.env.LLM_MODEL = "mock-model";

  const result = await answerMaintenanceQuestion("路灯离线应该怎么排查？");

  assert.equal(result.answerSource, "local");
  assert.ok(result.answer.includes("心跳"), "降级后应返回本地知识库内容");
  assert.ok(result.sources.includes("设备离线排查"));
});

test("模型接口不可达时降级为本地知识库回答", async () => {
  process.env.LLM_API_KEY = "test-key";
  process.env.LLM_BASE_URL = "http://127.0.0.1:9/v1";
  process.env.LLM_MODEL = "mock-model";

  const result = await answerMaintenanceQuestion("光照阈值怎么设置？");

  assert.equal(result.answerSource, "local");
  assert.ok(result.sources.includes("光照联动控制"));
});
