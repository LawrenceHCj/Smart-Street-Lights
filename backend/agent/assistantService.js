const { nowIso } = require("../utils/date");
const { knowledgeBase } = require("../rag/knowledgeBase");

function answerMaintenanceQuestion(question) {
  if (typeof question !== "string" || !question.trim()) {
    const error = new Error("question 不能为空");
    error.statusCode = 400;
    throw error;
  }

  const text = question.trim();
  const matches = knowledgeBase.filter((item) => item.keywords.some((keyword) => text.includes(keyword)));

  if (!matches.length) {
    return {
      answer: "知识库中暂未找到与该问题相关的内容，请补充更多细节后重试。",
      sources: [],
      generatedAt: nowIso(),
    };
  }

  const sources = matches.slice(0, 2);

  return {
    answer: sources.map((source) => source.content).join(" "),
    sources: sources.map((source) => source.title),
    generatedAt: nowIso(),
  };
}

module.exports = {
  answerMaintenanceQuestion,
};
