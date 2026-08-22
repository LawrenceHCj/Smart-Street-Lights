const { nowIso } = require("../utils/date");
const { knowledgeBase } = require("../rag/knowledgeBase");

function answerMaintenanceQuestion(question) {
  const text = String(question || "").trim();
  const matches = knowledgeBase.filter((item) => item.keywords.some((keyword) => text.includes(keyword)));
  const sources = (matches.length ? matches : knowledgeBase).slice(0, 2);

  return {
    answer: sources.map((source) => source.content).join(" "),
    sources: sources.map((source) => source.title),
    generatedAt: nowIso(),
  };
}

module.exports = {
  answerMaintenanceQuestion,
};
