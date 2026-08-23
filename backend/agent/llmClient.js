const fs = require("fs");
const path = require("path");

// 与具体厂商解耦：只要接口兼容 OpenAI 协议即可（OpenAI / DeepSeek / Kimi / 通义千问 等）
const ENV_FILE = path.resolve(__dirname, "..", "..", ".env");
const DEFAULT_TIMEOUT_MS = 10000;
let envLoaded = false;

// 简单 .env 加载：只设置尚未定义的环境变量，不覆盖 Shell 中已存在的值
function loadEnvFileOnce() {
  if (envLoaded) return;
  envLoaded = true;

  try {
    if (!fs.existsSync(ENV_FILE)) return;
    const content = fs.readFileSync(ENV_FILE, "utf8");
    for (const line of content.split(/\r?\n/)) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith("#")) continue;
      const sep = trimmed.indexOf("=");
      if (sep === -1) continue;
      const key = trimmed.slice(0, sep).trim();
      const value = trimmed.slice(sep + 1).trim();
      if (key && process.env[key] === undefined) process.env[key] = value;
    }
  } catch (error) {
    console.warn("[llmClient] 读取 .env 失败:", error.message);
  }
}

function getConfig() {
  loadEnvFileOnce();
  const apiKey = process.env.LLM_API_KEY;
  const model = process.env.LLM_MODEL;
  if (!apiKey || !model) return null;
  return {
    apiKey,
    baseUrl: process.env.LLM_BASE_URL || "https://api.openai.com/v1",
    model,
  };
}

async function completeChat({ system, user }) {
  const config = getConfig();
  if (!config) throw new Error("LLM not configured");

  const url = `${config.baseUrl.replace(/\/+$/, "")}/chat/completions`;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), DEFAULT_TIMEOUT_MS);

  try {
    const response = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${config.apiKey}`,
      },
      body: JSON.stringify({
        model: config.model,
        messages: [
          { role: "system", content: system },
          { role: "user", content: user },
        ],
        temperature: 0.3,
      }),
      signal: controller.signal,
    });

    if (!response.ok) {
      throw new Error(`LLM API 返回 ${response.status}`);
    }

    const data = await response.json();
    const content = data && data.choices && data.choices[0] && data.choices[0].message && data.choices[0].message.content;
    if (typeof content !== "string" || !content.trim()) {
      throw new Error("LLM API 返回内容为空");
    }
    return content.trim();
  } finally {
    clearTimeout(timer);
  }
}

module.exports = {
  completeChat,
  getConfig,
};
