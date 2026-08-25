<template>
  <el-container class="layout">
    <el-aside width="236px" class="aside">
      <div class="brand">
        <span class="brand-mark"><el-icon><ReadingLamp /></el-icon></span>
        <div class="brand-text">
          <div class="brand-name">智慧路灯</div>
          <div class="brand-sub">SMART STREET LIGHT</div>
        </div>
      </div>

      <nav class="menu">
        <button
          v-for="item in nav"
          :key="item.path"
          class="menu-item"
          :class="{ active: route.path === item.path }"
          @click="router.push(item.path)"
        >
          <el-icon class="menu-icon"><component :is="item.icon" /></el-icon>
          <span class="menu-label">{{ item.label }}</span>
        </button>
      </nav>

      <div class="aside-foot">
        <span class="status-dot online"></span>
        <span class="foot-text">控制台运行正常</span>
        <span class="foot-ver num">v0.1</span>
      </div>
    </el-aside>

    <el-container class="body">
      <el-header class="header">
        <div class="header-title">
          <el-icon><Menu /></el-icon>
          <span>{{ route.meta.title }}</span>
        </div>
        <div class="header-right">
          <span class="clock num">{{ fullClock }}</span>
          <span class="divider"></span>
          <div class="live">
            <span class="status-dot online"></span>
            <span>系统正常</span>
          </div>
          <span class="divider"></span>
          <el-popover v-model:visible="noticeOpen" placement="bottom-end" trigger="click" :width="320" popper-class="notice-popover">
            <template #reference>
              <el-button text class="notice" aria-label="告警通知">
                <el-icon><Bell /></el-icon>
                <sup v-if="openCount > 0">{{ openCount > 99 ? '99+' : openCount }}</sup>
              </el-button>
            </template>
            <div class="notice-panel">
              <div class="notice-head">告警通知</div>
              <div class="notice-list">
                <div v-for="a in topNotices" :key="a.id" class="notice-item" @click="noticeOpen = false; router.push('/alarms')">
                  <i class="notice-dot" :class="a.level"></i>
                  <div class="notice-info">
                    <div class="notice-msg">{{ a.message }}</div>
                    <div class="notice-meta num">{{ a.deviceId }} · {{ noticeTime(a.ts) }}</div>
                  </div>
                </div>
                <div v-if="!topNotices.length" class="notice-empty">
                  {{ noticeReady ? '暂无告警通知' : '告警接口未就绪' }}
                </div>
              </div>
              <div class="notice-foot" @click="noticeOpen = false; router.push('/alarms')">查看全部告警 ›</div>
            </div>
          </el-popover>
          <div class="user-avatar">{{ currentUser[0]?.toUpperCase() ?? 'U' }}</div>
          <span class="user-name">{{ currentUser }}</span>
          <el-button text class="logout" @click="logout">
            <el-icon><SwitchButton /></el-icon>
          </el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listAlarms } from '../api/alarm'
import type { AlarmVO } from '../api/alarm'
import { probe } from '../api/helper'
import {
  ReadingLamp,
  DataBoard,
  Sunny,
  Operation,
  Bell,
  Tools,
  User,
  ChatDotRound,
  SwitchButton,
  Menu,
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

// 当前登录用户：登录时写入 localStorage，退出时清理
const currentUser = localStorage.getItem('username') || '用户'

const nav = [
  { path: '/dashboard', label: '运行概览', icon: DataBoard },
  { path: '/monitor', label: '实时监控', icon: Sunny },
  { path: '/control', label: '灯控管理', icon: Operation },
  { path: '/alarms', label: '告警中心', icon: Bell },
  { path: '/devices', label: '设备管理', icon: Tools },
  { path: '/chat', label: '智能助手', icon: ChatDotRound },
  { path: '/users', label: '用户管理', icon: User },
]

// 顶部告警通知：弹层展示最近告警，角标 = 未处理数量（接口未就绪时优雅降级）
const notices = ref<AlarmVO[]>([])
const noticeReady = ref(true)
const noticeOpen = ref(false)
const openCount = computed(() => notices.value.filter((a) => a.status === 'OPEN').length)
const topNotices = computed(() => [...notices.value].sort((a, b) => b.ts - a.ts).slice(0, 6))
const pad = (n: number) => String(n).padStart(2, '0')
function noticeTime(ts: number) {
  const d = new Date(ts)
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
let noticeTimer: ReturnType<typeof setInterval> | null = null
async function loadNotices() {
  const r = await probe(listAlarms(), [])
  noticeReady.value = r.ready
  if (r.ready) notices.value = r.data
}

// 实时时钟：控制室的实时感，安静呈现
const fullClock = ref('')
let timer: ReturnType<typeof setInterval> | null = null
function tick() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  fullClock.value = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}
onMounted(() => {
  tick()
  timer = setInterval(tick, 1000)
  loadNotices()
  noticeTimer = setInterval(loadNotices, 5000)
})
onUnmounted(() => {
  if (timer) clearInterval(timer)
  if (noticeTimer) clearInterval(noticeTimer)
})

function logout() {
  localStorage.removeItem('token')
  localStorage.removeItem('username')
  router.push('/login')
}
</script>

<style scoped>
.layout {
  height: 100vh;
}

/* —— 侧栏：安静通透，毛发描边 —— */
.aside {
  display: flex;
  flex-direction: column;
  background: #07131f;
  border-right: 1px solid var(--border-subtle);
  color: var(--text-secondary);
}

.brand {
  height: 76px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 20px;
}
.brand-mark {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: transparent;
  border: none;
  color: var(--accent-bright);
  transition: background 0.2s ease, transform 0.2s ease;
}
.brand-mark :deep(.el-icon) {
  font-size: 27px;
}
.brand-mark:hover {
  background: transparent;
  color: var(--accent-bright);
  transform: translateY(-1px);
}
.brand-text {
  line-height: 1.25;
}
.brand-name {
  font-size: 17px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: 0.01em;
}
.brand-sub {
  display: none;
  letter-spacing: 0.1em;
  color: var(--text-muted);
  margin-top: 2px;
}

.menu {
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding: 8px 12px;
  overflow-y: auto;
}
.menu-item {
  display: flex;
  align-items: center;
  gap: 11px;
  height: 46px;
  padding: 0 13px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-secondary);
  font-family: inherit;
  font-size: 13.5px;
  cursor: pointer;
  text-align: left;
  transition: background 0.18s ease, color 0.18s ease;
}
.menu-item:hover {
  background: var(--bg-surface-2);
  color: var(--text-primary);
}
.menu-item.active {
  background: linear-gradient(90deg, rgba(46, 111, 213, 0.34), rgba(35, 85, 164, 0.22));
  color: #8db8ff;
  box-shadow: inset 2px 0 0 #508cff;
}
.menu-icon {
  font-size: 18px;
}
.menu-item.active .menu-icon {
  color: #75a4ff;
}

.aside-foot {
  margin-top: auto;
  padding: 16px 20px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--text-muted);
  border-top: 1px solid var(--border-subtle);
}
.foot-text {
  flex: 1;
}
.foot-ver {
  font-size: 11px;
  color: var(--text-muted);
}

/* —— 顶栏：极简，右侧实时信息 —— */
.body {
  min-width: 0;
}
.header {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 26px;
  background: rgba(7, 17, 29, 0.9);
  border-bottom: 1px solid var(--border-subtle);
}
.header-title {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 0;
  color: var(--text-primary);
}
.header-right {
  display: flex;
  align-items: center;
  gap: 14px;
}
.live {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12.5px;
  color: var(--text-secondary);
}
.clock {
  font-size: 12px;
  color: var(--text-secondary);
  letter-spacing: 0.03em;
}
.divider {
  width: 1px;
  height: 18px;
  background: var(--border-subtle);
}
.logout {
  color: var(--text-secondary);
  padding: 5px;
  border-radius: 8px;
}
.logout:hover {
  color: var(--danger);
}
.notice {
  position: relative;
  padding: 5px;
  color: var(--text-secondary);
}
.notice sup {
  position: absolute;
  top: -2px;
  right: -5px;
  display: grid;
  place-items: center;
  min-width: 15px;
  height: 15px;
  padding: 0 3px;
  border-radius: 9px;
  background: var(--danger);
  color: #fff;
  font-size: 9px;
  line-height: 1;
}
.user-avatar {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #d9e3ef;
  color: #25394e;
  font-size: 12px;
  font-weight: 700;
}
.user-name {
  font-size: 12px;
  color: var(--text-secondary);
}

.main {
  padding: 20px 22px 30px;
  overflow-y: auto;
}

/* —— 窄屏：侧栏收为紧凑图标栏 —— */
@media (max-width: 900px) {
  .aside {
    width: 64px !important;
  }
  .brand {
    justify-content: center;
    padding: 0;
  }
  .brand-text {
    display: none;
  }
  .menu-item {
    justify-content: center;
    padding: 0;
  }
  .menu-label {
    display: none;
  }
  .aside-foot {
    justify-content: center;
    padding: 14px 0;
  }
  .foot-text,
  .foot-ver {
    display: none;
  }
  .header {
    padding: 0 16px;
  }
  .live,
  .divider {
    display: none;
  }
  .main {
    padding: 18px 16px 24px;
  }
}

</style>

<style>
/* 告警通知弹层（el-popover teleport 到 body，需全局样式） */
.notice-popover.el-popover {
  --el-popover-bg-color: #0b1c2e;
  --el-popover-border-color: rgba(137, 170, 207, 0.18);
  padding: 0;
  border-radius: 10px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.45);
}
.notice-panel {
  color: var(--text-primary);
  font-family: var(--font-ui);
}
.notice-head {
  padding: 12px 16px;
  font-size: 13px;
  font-weight: 650;
  border-bottom: 1px solid var(--border-subtle);
}
.notice-list {
  max-height: 300px;
  overflow-y: auto;
}
.notice-item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 10px 16px;
  cursor: pointer;
  border-bottom: 1px solid rgba(137, 170, 207, 0.07);
}
.notice-item:hover {
  background: rgba(46, 111, 213, 0.12);
}
.notice-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  margin-top: 5px;
  flex: none;
}
.notice-dot.info {
  background: #7288a4;
}
.notice-dot.warning {
  background: var(--warn);
  box-shadow: 0 0 7px var(--warn);
}
.notice-dot.critical {
  background: var(--danger);
  box-shadow: 0 0 7px var(--danger);
}
.notice-info {
  min-width: 0;
}
.notice-msg {
  font-size: 12.5px;
  color: var(--text-primary);
  line-height: 1.5;
}
.notice-meta {
  margin-top: 3px;
  font-size: 11px;
  color: var(--text-muted);
}
.notice-empty {
  padding: 26px 16px;
  text-align: center;
  font-size: 12.5px;
  color: var(--text-muted);
}
.notice-foot {
  padding: 10px 16px;
  text-align: center;
  font-size: 12px;
  color: #9cc1ff;
  cursor: pointer;
  border-top: 1px solid var(--border-subtle);
}
.notice-foot:hover {
  color: #b9d3ff;
  background: rgba(46, 111, 213, 0.1);
}
</style>
