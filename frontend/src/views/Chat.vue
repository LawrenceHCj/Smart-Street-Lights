<template>
  <div class="chat-wrap">
    <aside class="sidebar" :class="{ open: sidebarOpen }" aria-label="会话列表">
      <div class="sidebar-head">
        <span class="sidebar-title">会话历史</span>
        <el-button size="small" text type="primary" :disabled="loading" @click="resetChat">
          <el-icon><Plus /></el-icon>
          <span>新建会话</span>
        </el-button>
      </div>
      <div class="sidebar-body">
        <div v-if="sidebarLoading" class="sidebar-state">正在加载会话…</div>
        <div v-else-if="sidebarError" class="sidebar-state error">
          {{ sidebarError }}
          <button class="inline-action" type="button" @click="loadConversations">重试</button>
        </div>
        <div v-else-if="!conversations.length" class="sidebar-state">暂无会话</div>
        <ul v-else class="conv-list">
          <li
            v-for="c in conversations"
            :key="c.conversationId"
            class="conv-item"
            :class="{ active: c.conversationId === currentId }"
            @click="openConversation(c.conversationId)"
          >
            <div class="conv-main">
              <span class="conv-title">{{ c.title || '新会话' }}</span>
              <span class="conv-time num">{{ formatTime(c.lastMessageAt) }}</span>
            </div>
            <button
              class="conv-delete"
              type="button"
              :aria-label="`删除会话 ${c.title || '新会话'}`"
              :disabled="deleting"
              @click.stop="confirmDelete(c)"
            >
              <el-icon><Delete /></el-icon>
            </button>
          </li>
        </ul>
      </div>
    </aside>
    <div v-if="sidebarOpen" class="sidebar-backdrop" @click="sidebarOpen = false"></div>
    <section class="panel chat" aria-labelledby="chat-title" :aria-busy="loading || loadingHistory">
      <div class="chat-head">
        <div class="chat-title">
          <span class="chat-mark"><el-icon><ReadingLamp /></el-icon></span>
          <div>
            <h1 id="chat-title" class="name">维护智能助手</h1>
            <div class="sub">维护知识问答 · RAG</div>
          </div>
        </div>
        <div class="chat-actions">
          <button class="conv-toggle" type="button" aria-label="会话列表" @click="sidebarOpen = true">
            <el-icon><Menu /></el-icon>
          </button>
          <el-button
            text
            size="small"
            class="clear-btn"
            :disabled="loading || messages.length <= 1"
            aria-label="清空当前对话"
            @click="resetChat"
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
            <!-- 【5号代做·需与2号对账】待确认操作卡片：仅 PENDING_CONFIRMATION 且未处理时显示 -->
            <div
              v-if="m.role === 'bot' && m.action && m.action.status === 'PENDING_CONFIRMATION' && !m.actionResult"
              class="action-card"
            >
              <div class="ac-title">AI 请求执行操作</div>
              <div class="ac-rows">
                <div class="ac-row"><span>操作</span>{{ actionLabel(m.action.actionType) }}</div>
                <div v-if="m.action.targetId && m.action.targetId !== 'system'" class="ac-row">
                  <span>设备</span>{{ m.action.targetId === 'all' ? '全部在线设备' : m.action.targetId }}
                </div>
                <div v-if="m.action.originalState" class="ac-row"><span>当前状态</span>{{ m.action.originalState }}</div>
                <div v-if="m.action.targetState" class="ac-row"><span>目标状态</span>{{ m.action.targetState }}</div>
              </div>
              <div class="ac-btns">
                <el-button
                  type="primary"
                  size="small"
                  :loading="m.actionLoading"
                  :disabled="isActionExpired(m)"
                  @click="confirmPending(m)"
                >
                  确认执行
                </el-button>
                <el-button size="small" :disabled="m.actionLoading || isActionExpired(m)" @click="cancelPending(m)">
                  取消
                </el-button>
                <span v-if="isActionExpired(m)" class="ac-expired">已过期，请重新发起</span>
              </div>
            </div>
            <!-- 确认/取消后的结果文案（按后端真实状态如实展示） -->
            <div v-if="m.role === 'bot' && m.actionResult" class="action-result">{{ m.actionResult }}</div>
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
        <div v-if="loadingHistory" class="msg bot">
          <div class="msg-avatar">
            <el-icon><ReadingLamp /></el-icon>
          </div>
          <div class="msg-body">
            <div class="typing"><i></i><i></i><i></i></div>
            <div class="typing-text">正在加载历史记录…</div>
          </div>
        </div>
      </div>

      <div class="input-zone">
        <div v-if="!loading && !loadingHistory && isFresh" class="suggest">
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
import { ref, computed, nextTick, onMounted, watch } from 'vue'
import { Delete, Document, Menu, Plus, Promotion, ReadingLamp, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ask,
  listConversations,
  getConversationMessages,
  deleteConversation,
  parseMessageSources,
  confirmAction,
  cancelAction,
} from '../api/agent'
import type { AgentMessage, AgentActionResult, Conversation, PendingAction, Source } from '../api/agent'

interface Msg {
  role: 'user' | 'bot'
  content: string
  sources?: Source[]
  /** 【5号代做·需与2号对账】待确认操作与处理状态 */
  action?: PendingAction | null
  actionResult?: string | null
  actionLoading?: boolean
}

const KEY = 'chatConversationId'
const GREETING: Msg = { role: 'bot', content: '您好，我是路灯维护助手，可以协助您排查故障、查询维护知识。' }

const messages = ref<Msg[]>([GREETING])
const input = ref('')
const loading = ref(false)
const listRef = ref<HTMLDivElement>()
const sendError = ref('')
const failedQuestion = ref('')

const conversations = ref<Conversation[]>([])
const currentId = ref('')
const sidebarOpen = ref(false)
const sidebarLoading = ref(false)
const sidebarError = ref('')
const deleting = ref(false)
const loadingHistory = ref(false)
const isFresh = computed(() => !currentId.value)

watch(currentId, (v) => {
  if (v) localStorage.setItem(KEY, v)
  else localStorage.removeItem(KEY)
})

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

function formatTime(iso?: string | null) {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const p = (n: number) => String(n).padStart(2, '0')
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

function toMsg(m: AgentMessage): Msg {
  return m.role === 'user'
    ? { role: 'user', content: m.content }
    : { role: 'bot', content: m.content, sources: parseMessageSources(m.metadata) }
}

async function loadConversations() {
  sidebarLoading.value = true
  try {
    conversations.value = await listConversations()
    sidebarError.value = ''
  } catch {
    sidebarError.value = '会话列表加载失败'
  } finally {
    sidebarLoading.value = false
  }
}

/** 发送/切换后静默刷新会话列表，失败时保持当前列表 */
async function refreshConversations() {
  try {
    conversations.value = await listConversations()
  } catch {
    /* 保持当前列表 */
  }
}

async function openConversation(id: string) {
  if (deleting.value || loading.value) return
  currentId.value = id
  messages.value = []
  loadingHistory.value = true
  sidebarError.value = ''
  try {
    const list = await getConversationMessages(id)
    messages.value = list.length ? list.map(toMsg) : [GREETING]
  } catch {
    // 会话可能已被删除或不属于当前用户
    messages.value = [GREETING]
    currentId.value = ''
    if (!sidebarLoading.value) ElMessage.warning('会话不可用，已切换为新对话')
  } finally {
    loadingHistory.value = false
    refreshConversations()
  }
  scrollBottom()
}

/** 新建会话/清空对话：懒重置，下次发送时后端自动创建真实会话 */
function resetChat() {
  if (loading.value) return
  messages.value = [GREETING]
  currentId.value = ''
  sendError.value = ''
  failedQuestion.value = ''
  sidebarOpen.value = false
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
    const r = await ask(q, currentId.value || undefined)
    // 【5号代做·需与2号对账】携带待确认操作：卡片在消息下方渲染确认/取消按钮
    messages.value.push({ role: 'bot', content: r.answer, sources: r.sources, action: r.action ?? null })
    if (r.conversationId) currentId.value = r.conversationId
    failedQuestion.value = ''
    refreshConversations()
  } catch {
    sendError.value = '问题发送失败，请检查服务连接后重试。'
  } finally {
    loading.value = false
    scrollBottom()
  }
}

// ===== 【5号代做·需与2号对账】待确认操作卡片交互：必须按 actionId 调确认/取消接口 =====

function actionLabel(type: string): string {
  const map: Record<string, string> = {
    TURN_ON_LIGHT: '打开路灯',
    TURN_OFF_LIGHT: '关闭路灯',
    TURN_OFF_ALL: '关闭全部设备',
    UPDATE_LUX_THRESHOLD: '修改光照阈值',
    UPDATE_AUTO_MODE: '修改自动模式',
  }
  return map[type] || type
}

function isActionExpired(m: Msg): boolean {
  return !!m.action?.expiresAt && m.action.expiresAt <= Date.now()
}

/** 按后端真实状态生成结果文案（COMMAND_ACCEPTED 绝不显示"成功"） */
function actionResultText(r: AgentActionResult): string {
  const map: Record<string, string> = {
    SUCCESS: r.message || '已执行成功',
    DEVICE_CONFIRMED: r.message || '设备已确认执行',
    COMMAND_ACCEPTED: r.message || '控制指令已发送，但当前尚未获得设备执行确认',
    FAILED: r.message || '执行失败',
    CANCELLED: '已取消',
    EXPIRED: '已过期，请重新发起',
  }
  return map[r.status] || r.message || r.status
}

async function confirmPending(m: Msg) {
  if (!m.action || m.actionLoading || isActionExpired(m)) return
  m.actionLoading = true
  try {
    const r = await confirmAction(m.action.actionId)
    m.action = null
    m.actionResult = actionResultText(r)
  } catch (e) {
    // 业务拒绝（已过期/重复操作/设备状态变化等）按后端 message 如实展示
    m.action = null
    m.actionResult = (e as { message?: string })?.message || '操作失败，请重试'
  } finally {
    m.actionLoading = false
  }
}

async function cancelPending(m: Msg) {
  if (!m.action || m.actionLoading || isActionExpired(m)) return
  m.actionLoading = true
  try {
    await cancelAction(m.action.actionId)
    m.action = null
    m.actionResult = '已取消'
  } catch (e) {
    m.action = null
    m.actionResult = (e as { message?: string })?.message || '取消失败，请重试'
  } finally {
    m.actionLoading = false
  }
}

async function confirmDelete(c: Conversation) {
  try {
    await ElMessageBox.confirm(
      `删除会话「${c.title || '新会话'}」后其历史记录将无法恢复，确认删除？`,
      '删除会话',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  deleting.value = true
  try {
    await deleteConversation(c.conversationId)
    conversations.value = conversations.value.filter((x) => x.conversationId !== c.conversationId)
    if (c.conversationId === currentId.value) resetChat()
    ElMessage.success('会话已删除')
  } catch {
    ElMessage.error('删除会话失败，请重试')
    refreshConversations()
  } finally {
    deleting.value = false
  }
}

onMounted(async () => {
  currentId.value = localStorage.getItem(KEY) || ''
  await loadConversations()
  if (currentId.value) await openConversation(currentId.value)
})
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
/* 【5号代做·需与2号对账】待确认操作卡片 */
.action-card {
  margin-top: 12px;
  padding: 12px 14px;
  border: 1px solid rgba(208, 255, 111, 0.3);
  border-radius: 4px;
  background: rgba(208, 255, 111, 0.06);
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.ac-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--signal, #d0ff6f);
  letter-spacing: 0.04em;
}
.ac-rows {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.ac-row {
  font-size: 12.5px;
  color: #dcebe5;
  line-height: 1.5;
}
.ac-row span {
  display: inline-block;
  min-width: 64px;
  color: #8ba39a;
}
.ac-btns {
  display: flex;
  align-items: center;
  gap: 10px;
}
.ac-expired {
  font-size: 11.5px;
  color: #ffaaa3;
}
.action-result {
  margin-top: 10px;
  padding: 8px 12px;
  border-radius: 4px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  background: rgba(255, 255, 255, 0.04);
  font-size: 12.5px;
  color: #dcebe5;
  line-height: 1.6;
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
/* Full-height intelligence desk */
.chat-wrap { max-width: 1380px; height: calc(100vh - 182px); margin: 0 auto; display: grid; grid-template-columns: 264px minmax(0, 1fr); position: relative; }
.chat { width: auto; max-width: none; height: 100%; min-width: 0; display: grid; grid-template-rows: auto 1fr auto; border: 1px solid rgba(208,255,111,.18); border-left: 0; border-radius: 0; color: #e5f1ed; background: var(--ink); }
.sidebar { min-height: 0; display: flex; flex-direction: column; background: var(--ink-soft); border: 1px solid rgba(208,255,111,.18); border-right: 0; }
.sidebar-head { min-height: 74px; padding: 0 14px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid rgba(255,255,255,.1); }
.sidebar-title { color: #fff; font-size: 13px; font-weight: 600; }
.sidebar-body { flex: 1; overflow-y: auto; padding: 8px; }
.sidebar-state { padding: 26px 12px; color: #8ba39a; font-size: 12.5px; text-align: center; }
.sidebar-state.error { color: #ffaaa3; }
.conv-list { margin: 0; padding: 0; list-style: none; display: flex; flex-direction: column; gap: 4px; }
.conv-item { display: flex; align-items: center; gap: 6px; padding: 9px 10px; border: 1px solid transparent; cursor: pointer; color: #bad0c8; }
.conv-item:hover { background: rgba(208,255,111,.06); }
.conv-item.active { color: #fff; background: rgba(208,255,111,.1); border-color: rgba(208,255,111,.28); }
.conv-main { flex: 1; min-width: 0; }
.conv-title { display: block; font-size: 12.5px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.conv-time { display: block; margin-top: 2px; color: #6f877f; font-size: 10.5px; }
.conv-delete { flex: none; width: 26px; height: 26px; border: 0; color: #6f877f; background: transparent; cursor: pointer; opacity: 0; transition: opacity .15s ease; display: inline-flex; align-items: center; justify-content: center; }
.conv-item:hover .conv-delete { opacity: 1; }
.conv-delete:hover { color: #ffaaa3; }
.conv-toggle { display: none; }
.sidebar-backdrop { display: none; }
.chat-head { min-height: 74px; border-color: rgba(255,255,255,.1); background: var(--ink-soft); }
.chat .name { color: #fff; }
.chat .sub { color: #8ba39a; }
.chat-mark, .msg-avatar { border-radius: 0; color: var(--ink); background: var(--signal); }
.messages { background-image: linear-gradient(rgba(208,255,111,.035) 1px, transparent 1px), linear-gradient(90deg, rgba(208,255,111,.035) 1px, transparent 1px); background-size: 28px 28px; }
.msg.bot .msg-body { border-color: rgba(255,255,255,.1); border-radius: 0; color: #dcebe5; background: rgba(255,255,255,.055); }
.msg.user .msg-body { border-radius: 0; color: var(--ink); background: var(--signal); }
.input-zone { border-color: rgba(255,255,255,.1); background: #0b2c25; }
.chip { border-radius: 0; color: #bad0c8; border-color: rgba(255,255,255,.18); background: transparent; }
.chip:hover { color: var(--signal); border-color: var(--signal); }

@media (max-width: 900px) {
  .chat-wrap { grid-template-columns: 1fr; }
  .chat { border-left: 1px solid rgba(208,255,111,.18); }
  .sidebar {
    position: fixed;
    z-index: 120;
    top: 0;
    bottom: 0;
    left: 0;
    width: 280px;
    transform: translateX(-100%);
    transition: transform 0.22s ease;
    border-right: 1px solid rgba(208,255,111,.18);
    box-shadow: 0 18px 50px rgba(3, 22, 18, 0.4);
  }
  .sidebar.open { transform: translateX(0); }
  .sidebar-backdrop { position: fixed; z-index: 119; inset: 0; background: rgba(3, 22, 18, 0.5); display: block; }
  .conv-toggle { display: inline-flex; }
}
</style>
