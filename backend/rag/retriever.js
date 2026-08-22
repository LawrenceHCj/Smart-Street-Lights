const { knowledgeBase } = require("./knowledgeBase");

// 关键词检索器：当前为关键词命中评分，后续可替换为向量检索（接口保持不变）
// 返回：匹配的知识条目（含 id/title/category/content/keywords/source）+ 相关度 score
function retrieve(question, { limit = 2 } = {}) {
  const text = String(question || "").trim();
  if (!text) return [];

  return knowledgeBase
    .map((entry) => ({
      entry,
      score: entry.keywords.filter((keyword) => text.includes(keyword)).length,
    }))
    .filter((item) => item.score > 0)
    .sort((a, b) => b.score - a.score)
    .slice(0, limit)
    .map((item) => ({ ...item.entry, score: item.score }));
}

module.exports = {
  retrieve,
};
