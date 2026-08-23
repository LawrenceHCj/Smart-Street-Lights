const fs = require("fs");
const path = require("path");
const { nowIso } = require("../utils/date");
const { retrieve } = require("../rag/retriever");
const { completeChat, getConfig } = require("./llmClient");

const PROMPTS_FILE = path.join(__dirname, "..", "rag", "prompts.md");
const TOP_K = 2;
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

// Build Context：把检索结果整理为大模型可读的知识上下文
function buildContext(matches) {
  if (!matches.length) return "（知识库未检索到相关内容）";
  return matches.map((item) => `【${item.category}】《${item.title}》\n${item.content}`).join("\n\n");
}

// Prompt：知识上下文 + 用户问题
function buildUserPrompt(question, matches) {
  return `【知识库内容】\n${buildContext(matches)}\n\n【用户问题】\n${question}`;
}

function localAnswer(matches) {
  if (!matches.length) {
    return {
      answer: "知识库中暂未找到与该问题相关的内容，请补充更多细节后重试。",
      sources: [],
    };
  }

  return {
    answer: matches.map((item) => item.content).join(" "),
    sources: matches.map((item) => item.title),
  };
}

async function answerMaintenanceQuestion(question) {
  if (typeof question !== "string" || !question.trim()) {
    const error = new Error("question 不能为空");
    error.statusCode = 400;
    throw error;
  }

  const text = question.trim();
  const matches = retrieve(text, { limit: TOP_K });
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
      sources: matches.map((item) => item.title),
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
