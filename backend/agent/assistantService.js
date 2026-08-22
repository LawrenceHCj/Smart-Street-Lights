const fs = require("fs");
const path = require("path");
const { nowIso } = require("../utils/date");
const { knowledgeBase } = require("../rag/knowledgeBase");
const { completeChat, getConfig } = require("./llmClient");

const PROMPTS_FILE = path.join(__dirname, "..", "rag", "prompts.md");
let systemPromptCache;

function loadSystemPrompt() {
  if (systemPromptCache === undefined) {
    try {
      systemPromptCache = fs.readFileSync(PROMPTS_FILE, "utf8").trim();
    } catch (error) {
      console.warn("[assistantService] 读取 prompts.md 失败:", error.message);
      systemPromptCache = "";
    }
  }
  return systemPromptCache;
}

function buildUserPrompt(question, matches) {
  const context = matches.length
    ? matches.slice(0, 2).map((item) => `《${item.title}》\n${item.content}`).join("\n\n")
    : "（知识库未检索到相关内容）";

  return `【知识库内容】\n${context}\n\n【用户问题】\n${question}`;
}

function localAnswer(matches) {
  if (!matches.length) {
    return {
      answer: "知识库中暂未找到与该问题相关的内容，请补充更多细节后重试。",
      sources: [],
    };
  }

  const sources = matches.slice(0, 2);
  return {
    answer: sources.map((source) => source.content).join(" "),
    sources: sources.map((source) => source.title),
  };
}

async function answerMaintenanceQuestion(question) {
  if (typeof question !== "string" || !question.trim()) {
    const error = new Error("question 不能为空");
    error.statusCode = 400;
    throw error;
  }

  const text = question.trim();
  const matches = knowledgeBase.filter((item) => item.keywords.some((keyword) => text.includes(keyword)));
  const fallback = () => ({ ...localAnswer(matches), answerSource: "local", generatedAt: nowIso() });

  // 未配置大模型或 Prompt 缺失时，直接走本地知识库回答
  if (!getConfig() || !loadSystemPrompt()) return fallback();

  try {
    const answer = await completeChat({
      system: loadSystemPrompt(),
      user: buildUserPrompt(text, matches),
    });
    return {
      answer,
      sources: matches.slice(0, 2).map((item) => item.title),
      answerSource: "llm",
      generatedAt: nowIso(),
    };
  } catch (error) {
    // 大模型不可用时降级，绝不让调用失败影响接口可用性
    console.warn("[assistantService] 大模型调用失败，降级为本地知识库回答:", error.message);
    return fallback();
  }
}

module.exports = {
  answerMaintenanceQuestion,
};
