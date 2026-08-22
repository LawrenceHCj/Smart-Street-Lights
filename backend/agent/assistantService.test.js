const test = require("node:test");
const assert = require("node:assert/strict");
const { answerMaintenanceQuestion } = require("./assistantService");

test("路灯离线排查问题返回知识库回答", () => {
  const result = answerMaintenanceQuestion("路灯离线应该怎么排查？");

  assert.equal(typeof result.answer, "string");
  assert.ok(result.answer.includes("心跳"), "回答应包含离线排查内容");
  assert.ok(result.sources.includes("设备离线排查"), "来源应包含离线排查条目");
  assert.ok(result.generatedAt.endsWith("Z"), "generatedAt 应为 ISO 时间");
});

test("阈值相关问题返回光照联动控制知识", () => {
  const result = answerMaintenanceQuestion("光照阈值怎么设置？");

  assert.ok(result.sources.includes("光照联动控制"));
});

test("手动控制问题返回控制回执知识", () => {
  const result = answerMaintenanceQuestion("手动控制没有回执怎么办？");

  assert.ok(result.sources.includes("手动控制回执"));
});

test("无命中问题返回空来源和明确提示", () => {
  const result = answerMaintenanceQuestion("今天天气怎么样？");

  assert.deepEqual(result.sources, []);
  assert.ok(result.answer.includes("未找到"), "无命中时应明确提示未找到");
});

test("空问题抛出 400 错误", () => {
  assert.throws(() => answerMaintenanceQuestion(""), (error) => error.statusCode === 400);
});

test("非字符串问题抛出 400 错误", () => {
  assert.throws(() => answerMaintenanceQuestion({ foo: "bar" }), (error) => error.statusCode === 400);
});

test("响应格式字段稳定", () => {
  const result = answerMaintenanceQuestion("设备断开怎么办？");

  assert.deepEqual(Object.keys(result).sort(), ["answer", "generatedAt", "sources"]);
  assert.ok(Array.isArray(result.sources));
  assert.ok(result.sources.every((source) => typeof source === "string"));
});
