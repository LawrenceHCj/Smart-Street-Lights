const test = require("node:test");
const assert = require("node:assert/strict");
const { retrieve } = require("./retriever");

test("命中问题返回匹配条目及完整字段", () => {
  const results = retrieve("路灯离线应该怎么排查？");

  assert.equal(results.length, 1);
  const top = results[0];
  assert.equal(top.id, "kb-offline-troubleshooting");
  assert.equal(top.title, "设备离线排查");
  assert.equal(top.category, "告警处理");
  assert.equal(typeof top.content, "string");
  assert.ok(Array.isArray(top.keywords));
  assert.equal(top.source, "内部知识库");
  assert.ok(top.score >= 1, "应带有相关度分数");
});

test("多关键词命中评分更高并排在前面", () => {
  // "手动控制告警"：手动控制回执 命中2个词，设备离线排查 命中1个词
  const results = retrieve("手动控制告警");

  assert.equal(results[0].title, "手动控制回执");
  assert.equal(results[1].title, "设备离线排查");
  assert.ok(results[0].score > results[1].score);
});

test("limit 生效", () => {
  const results = retrieve("手动控制告警", { limit: 1 });

  assert.equal(results.length, 1);
  assert.equal(results[0].title, "手动控制回执");
});

test("无命中返回空数组", () => {
  assert.deepEqual(retrieve("今天天气怎么样？"), []);
});

test("空问题返回空数组", () => {
  assert.deepEqual(retrieve("   "), []);
});
