<template>
  <div class="chat-wrap">
    <section class="panel chat" aria-labelledby="chat-title" :aria-busy="loading">
      <div class="chat-head">
        <div class="chat-title">
          <span class="chat-mark"><el-icon><ReadingLamp /></el-icon></span>
          <div>
            <h1 id="chat-title" class="name">维护智能助手</h1>
            <div class="sub">维护知识问答 · RAG</div>
          </div>
        </div>
        <div class="chat-actions">
          <el-button
            text
            size="small"
            class="clear-btn"
            :disabled="loading || messages.length <= 1"
            aria-label="清空当前对话"
            @click="clearChat"
          >
            <el-icon><Delete /></el-icon>
            <span>清空对话</span>
          </el-button>
          <span class="status-pill online">
            <span class="status-dot online"></span> 就绪
          </span>
        </div>
      </div>

      <div ref="listRef" class="messages" role="log" aria-live="polite" aria-relevant="additions">
        <div v-for="(m, i) in messages" :key="i" :class="['msg', m.role]">
          <div v-if="m.role === 'bot'" class="msg-avatar">
            <el-icon><ReadingLamp /></el-icon>
          </div>
          <div class="msg-body">
            <div class="text">{{ m.content }}</div>
            <div v-if="m.sources && m.sources.length" class="sources">
              <div class="src-label">引用来源</div>
              <div v-for="(s, j) in m.sources" :key="j" class="src">
                <el-icon class="src-icon"><Document /></el-icon>
                <span class="src-text">{{ s.title }} · {{ s.section }}</span>
                <span class="src-score num">{{ Math.round(s.score * 100) }}%</span>
              </div>
            </div>
          </div>
        </div>
        <div v-if="loading" class="msg bot">
          <div class="msg-avatar">
            <el-icon><ReadingLamp /></el-icon>
          </div>
          <div class="msg-body">
            <div class="typing"><i></i><i></i><i></i></div>
            <div class="typing-text">正在检索维护知识…</div>
          </div>
        </div>
      </div>

      <div class="input-zone">
        <div v-if="messages.length <= 1" class="suggest">
          <span class="suggest-label">试试问：</span>
          <button
            v-for="q in suggestions"
            :key="q"
            class="chip"
            type="button"
            :disabled="loading"
            @click="askQuick(q)"
          >
            {{ q }}
          </button>
        </div>
        <div v-if="sendError" class="chat-error" role="alert">
          <el-icon aria-hidden="true"><WarningFilled /></el-icon>
          <span>{{ sendError }}</span>
          <button class="inline-action" type="button" :disabled="loading" @click="retry">重新发送</button>
        </div>
        <div class="input-bar">
          <label class="sr-only" for="chat-question">维护问题</label>
          <el-input
            id="chat-question"
            v-model="input"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 4 }"
            resize="none"
            placeholder="请输入维护问题，例如：路灯离线常见原因？"
            aria-describedby="chat-input-hint"
            @input="sendError = ''"
            @keyup.enter.exact.prevent="send()"
          />
          <el-tooltip content="发送问题" placement="top">
            <el-button
              type="primary"
              circle
              class="send-btn"
              aria-label="发送问题"
              :loading="loading"
              :disabled="!input.trim()"
              @click="send()"
            >
              <el-icon aria-hidden="true"><Promotion /></el-icon>
            </el-button>
          </el-tooltip>
        </div>
        <div id="chat-input-hint" class="input-hint">Enter 发送 · Shift+Enter 换行</div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { Delete, Document, Promotion, ReadingLamp, WarningFilled } from '@element-plus/icons-vue'
import { ask } from '../api/agent'
import type { Source } from '../api/agent'

interface Msg {
  role: 'user' | 'bot'
  content: string
  sources?: Source[]
}

const messages = ref<Msg[]>([
  { role: 'bot', content: '您好，我是路灯维护助手，可以协助您排查故障、查询维护知识。' },
])
const input = ref('')
const loading = ref(false)
const listRef = ref<HTMLDivElement>()
const sendError = ref('')
const failedQuestion = ref('')

const suggestions = [
  '路灯离线常见原因？',
  '路灯不亮如何排查？',
  '如何设置光照阈值？',
  '设备上线需要什么条件？',
]

function askQuick(q: string) {
  input.value = q
  send()
}

function clearChat() {
  messages.value = [
    { role: 'bot', content: '您好，我是路灯维护助手，可以协助您排查故障、查询维护知识。' },
  ]
  sendError.value = ''
  failedQuestion.value = ''
}

function retry() {
  if (!failedQuestion.value || loading.value) return
  send(true)
}

async function scrollBottom() {
  await nextTick()
  if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
}

async function send(retrying = false) {
  const q = retrying ? failedQuestion.value : input.value.trim()
  if (!q || loading.value) return
  if (!retrying) {
    messages.value.push({ role: 'user', content: q })
    input.value = ''
  }
  loading.value = true
  sendError.value = ''
  failedQuestion.value = q
  scrollBottom()
  try {
    const r = await ask(q)
    messages.value.push({ role: 'bot', content: r.answer, sources: r.sources })
    failedQuestion.value = ''
  } catch {
    sendError.value = '问题发送失败，请检查服务连接后重试。'
  } finally {
    loading.value = false
    scrollBottom()
  }
}
</script>

<style scoped>
.chat-wrap {
  display: flex;
  justify-content: center;
}
.chat {
  width: 100%;
  max-width: 960px;
  height: calc(100dvh - var(--header-height) - 42px);
  min-height: 520px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-subtle);
}
.chat-title {
  display: flex;
  align-items: center;
  gap: 12px;
}
.chat-actions {
  display: inline-flex;
  align-items: center;
  gap: 12px;
}
.clear-btn {
  color: var(--text-muted);
}
.clear-btn:hover {
  color: var(--danger);
}
.clear-btn :deep(.el-icon) {
  margin-right: 4px;
}
.chat-mark {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 3px;
  background: var(--accent-dim);
  border: 1px solid rgba(250, 152, 25, 0.28);
  color: var(--accent-bright);
}
.chat-mark :deep(.el-icon) {
  font-size: 18px;
}
.name {
  margin: 0;
  font-size: 14.5px;
  font-weight: 600;
  color: var(--text-primary);
}
.sub {
  font-size: 11.5px;
  color: var(--text-muted);
  margin-top: 1px;
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px 22px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.msg {
  display: flex;
  gap: 12px;
  max-width: 100%;
}
.msg.user {
  justify-content: flex-end;
}
.msg-avatar {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border-radius: 3px;
  background: var(--accent-dim);
  color: var(--accent-bright);
  flex: none;
  margin-top: 2px;
}
.msg-avatar :deep(.el-icon) {
  font-size: 15px;
}

.msg-body {
  max-width: 78%;
  padding: 12px 16px;
  border-radius: 5px;
  line-height: 1.7;
  font-size: 14px;
}
.msg.user .msg-body {
  background: var(--accent-dim);
  border: 1px solid rgba(250, 152, 25, 0.24);
  color: var(--text-primary);
  border-bottom-right-radius: 5px;
}
.msg.bot .msg-body {
  background: var(--bg-surface-2);
  border: 1px solid var(--border-subtle);
  color: var(--text-primary);
  border-bottom-left-radius: 5px;
}
.text {
  white-space: pre-wrap;
  word-break: break-word;
}

.sources {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--border);
  display: flex;
  flex-direction: column;
  gap: 7px;
}
.src-label {
  font-size: 11px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-muted);
}
.src {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12.5px;
  color: var(--text-secondary);
  padding: 6px 10px;
  border-radius: 3px;
  background: var(--bg-surface-2);
  border: 1px solid var(--border-subtle);
}
.src-icon {
  color: var(--accent);
  font-size: 14px;
  flex: none;
}
.src-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.src-score {
  color: var(--accent-bright);
  font-weight: 600;
  font-size: 12px;
  flex: none;
}

.typing {
  display: inline-flex;
  gap: 5px;
  align-items: center;
}
.typing i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-muted);
  animation: blink 1.2s infinite ease-in-out;
}
.typing i:nth-child(2) {
  animation-delay: 0.18s;
}
.typing i:nth-child(3) {
  animation-delay: 0.36s;
}
.typing-text {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-muted);
}
@keyframes blink {
  0%,
  80%,
  100% {
    opacity: 0.3;
    transform: translateY(0);
  }
  40% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

.input-zone {
  border-top: 1px solid var(--border-subtle);
  padding: 14px 20px 12px;
}
.chat-error {
  min-height: 36px;
  margin-bottom: 10px;
  padding: 6px 8px 6px 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid rgba(180, 35, 24, .24);
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  background: var(--danger-dim);
  font-size: 12px;
}
.chat-error .el-icon {
  flex: none;
  color: var(--danger);
}
.chat-error span {
  min-width: 0;
  flex: 1;
}
.suggest {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.suggest-label {
  font-size: 12px;
  color: var(--text-muted);
  flex: none;
}
.chip {
  border: 1px solid var(--border);
  background: var(--bg-surface-2);
  color: var(--text-secondary);
  font-family: inherit;
  font-size: 12px;
  line-height: 1;
  padding: 6px 11px;
  border-radius: 3px;
  cursor: pointer;
  transition: border-color 0.15s ease, color 0.15s ease, background 0.15s ease;
}
.chip:hover:not(:disabled) {
  border-color: rgba(250, 152, 25, 0.42);
  color: var(--accent-bright);
  background: var(--accent-dim);
}
.chip:disabled {
  opacity: 0.45;
  cursor: default;
}
.input-bar {
  display: flex;
  align-items: flex-end;
  gap: 10px;
}
.input-bar :deep(.el-textarea__inner) {
  border-radius: 4px;
  padding: 10px 14px;
  line-height: 1.6;
}
.send-btn {
  width: 42px;
  height: 42px;
  flex: none;
  font-size: 17px;
}
.send-btn.is-disabled {
  opacity: 0.35;
}
.input-hint {
  margin-top: 8px;
  font-size: 11px;
  color: var(--text-muted);
  text-align: right;
  user-select: none;
}

@media (max-width: 900px) {
  .chat {
    height: calc(100dvh - 90px);
  }
}

@media (max-width: 600px) {
  .chat {
    min-height: 0;
    border-radius: var(--radius-md);
  }
  .chat-head {
    padding: 12px 13px;
  }
  .clear-btn span {
    display: none;
  }
  .messages {
    padding: 18px 13px;
    gap: 15px;
  }
  .msg-body {
    max-width: 88%;
    padding: 10px 12px;
  }
  .input-zone {
    padding: 11px 12px 9px;
  }
  .suggest {
    max-height: 76px;
    overflow-y: auto;
  }
  .input-hint {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .typing i {
    animation: none;
  }
}
</style>
