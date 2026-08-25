<template>
  <div class="layout">
    <a class="skip-link" href="#main-content">跳到主要内容</a>

    <button
      v-if="mobileNavOpen"
      class="nav-scrim"
      type="button"
      aria-label="关闭导航"
      @click="closeMobileNav()"
    ></button>

    <aside id="app-navigation" class="aside" :class="{ 'is-open': mobileNavOpen }">
      <div class="brand" aria-label="智慧路灯运维控制台">
        <span class="brand-mark" aria-hidden="true">
          <el-icon><ReadingLamp /></el-icon>
        </span>
        <div class="brand-text">
          <div class="brand-name">智慧路灯</div>
          <div class="brand-sub">城市照明运维</div>
        </div>
      </div>

      <nav class="menu" aria-label="主导航">
        <button
          v-for="item in nav"
          :key="item.path"
          type="button"
          class="menu-item"
          :class="{ active: route.path === item.path }"
          :aria-current="route.path === item.path ? 'page' : undefined"
          @click="navigate(item.path)"
        >
          <el-icon class="menu-icon" aria-hidden="true"><component :is="item.icon" /></el-icon>
          <span class="menu-label">{{ item.label }}</span>
        </button>
      </nav>

      <div class="aside-foot" :class="{ offline: !isOnline }" aria-live="polite">
        <span class="status-dot" :class="isOnline ? 'online' : 'offline'"></span>
        <span class="foot-text">{{ isOnline ? '网络连接正常' : '网络连接中断' }}</span>
        <span class="foot-ver num">v0.1</span>
      </div>
    </aside>

    <div class="body" :inert="mobileNavOpen || undefined">
      <header class="header">
        <div class="header-left">
          <button
            ref="navToggleRef"
            class="nav-toggle"
            type="button"
            :aria-expanded="mobileNavOpen"
            aria-controls="app-navigation"
            @click="toggleMobileNav"
          >
            <el-icon aria-hidden="true"><Menu /></el-icon>
            <span>导航</span>
          </button>
          <div class="header-title">
            <span class="header-context">照明运维</span>
            <span class="header-current">{{ route.meta.title }}</span>
          </div>
        </div>

        <div class="header-right">
          <time class="clock num" :datetime="clockIso">{{ fullClock }}</time>
          <span class="divider" aria-hidden="true"></span>
          <div class="live" :class="{ offline: !isOnline }" aria-live="polite">
            <span class="status-dot" :class="isOnline ? 'online' : 'offline'"></span>
            <span>{{ isOnline ? '网络在线' : '网络离线' }}</span>
          </div>
          <span class="divider" aria-hidden="true"></span>
          <el-tooltip content="打开告警中心" placement="bottom">
            <button class="header-icon" type="button" aria-label="打开告警中心" @click="navigate('/alarms')">
              <el-icon aria-hidden="true"><Bell /></el-icon>
            </button>
          </el-tooltip>
          <span class="user-avatar" aria-hidden="true">{{ currentUser[0]?.toUpperCase() ?? 'U' }}</span>
          <span class="user-name">{{ currentUser }}</span>
          <el-tooltip content="退出登录" placement="bottom">
            <button class="header-icon logout" type="button" aria-label="退出登录" @click="logout">
              <el-icon aria-hidden="true"><SwitchButton /></el-icon>
            </button>
          </el-tooltip>
        </div>
      </header>

      <main id="main-content" class="main" tabindex="-1">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Bell,
  ChatDotRound,
  DataBoard,
  Menu,
  Operation,
  ReadingLamp,
  Sunny,
  SwitchButton,
  Tools,
  User,
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const mobileNavOpen = ref(false)
const navToggleRef = ref<HTMLButtonElement>()
const isOnline = ref(navigator.onLine)
const fullClock = ref('')
const clockIso = ref('')
let timer: ReturnType<typeof setInterval> | null = null

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

function tick() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  fullClock.value = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
  clockIso.value = d.toISOString()
}

function updateNetworkStatus() {
  isOnline.value = navigator.onLine
}

function toggleMobileNav() {
  mobileNavOpen.value ? closeMobileNav() : openMobileNav()
}

async function openMobileNav() {
  mobileNavOpen.value = true
  await nextTick()
  document.querySelector<HTMLButtonElement>('.menu-item')?.focus()
}

function closeMobileNav({ restoreFocus = true } = {}) {
  mobileNavOpen.value = false
  if (restoreFocus) nextTick(() => navToggleRef.value?.focus())
}

function onKeydown(event: KeyboardEvent) {
  if (!mobileNavOpen.value) return
  if (event.key === 'Escape') {
    closeMobileNav()
    return
  }
  if (event.key !== 'Tab') return
  const controls = Array.from(document.querySelectorAll<HTMLButtonElement>('#app-navigation button:not(:disabled)'))
  if (!controls.length) return
  const first = controls[0]
  const last = controls[controls.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

function navigate(path: string) {
  if (route.path !== path) router.push(path)
  if (mobileNavOpen.value) closeMobileNav({ restoreFocus: false })
}

function logout() {
  localStorage.removeItem('token')
  localStorage.removeItem('username')
  router.push('/login')
}

watch(
  () => route.fullPath,
  () => {
    mobileNavOpen.value = false
  },
)

onMounted(() => {
  tick()
  timer = setInterval(tick, 30_000)
  window.addEventListener('online', updateNetworkStatus)
  window.addEventListener('offline', updateNetworkStatus)
  window.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  window.removeEventListener('online', updateNetworkStatus)
  window.removeEventListener('offline', updateNetworkStatus)
  window.removeEventListener('keydown', onKeydown)
})
</script>

<style scoped>
.layout {
  height: 100dvh;
  display: grid;
  grid-template-columns: var(--sidebar-width) minmax(0, 1fr);
  overflow: hidden;
}

.aside {
  min-height: 0;
  display: flex;
  flex-direction: column;
  color: #d9dbe1;
  border-right: 0;
  background: var(--bg-sidebar);
}

.brand {
  height: 82px;
  padding: 0 22px;
  display: flex;
  align-items: center;
  gap: 12px;
  flex: none;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.brand-mark {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  flex: none;
  border: 0;
  border-radius: 10px 3px 10px 3px;
  color: #ffffff;
  background: var(--accent);
}

.brand-mark :deep(.el-icon) {
  font-size: 22px;
}

.brand-text {
  min-width: 0;
  line-height: 1.25;
}

.brand-name {
  color: #ffffff;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.01em;
}

.brand-sub {
  margin-top: 3px;
  color: #abbed1;
  font-size: 10.5px;
  letter-spacing: 0.09em;
}

.menu {
  min-height: 0;
  padding: 20px 14px;
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  overflow-y: auto;
}

.menu-item {
  position: relative;
  width: 100%;
  min-height: 46px;
  padding: 0 14px;
  display: flex;
  align-items: center;
  gap: 11px;
  border: 1px solid transparent;
  border-radius: 8px;
  color: #d9dbe1;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: color 0.16s ease, border-color 0.16s ease, background-color 0.16s ease;
}

.menu-item:hover {
  color: #ffffff;
  background: rgba(255, 255, 255, 0.08);
}

.menu-item:active {
  background: rgba(255, 255, 255, 0.12);
}

.menu-item.active {
  color: #ffffff;
  border-color: rgba(76, 175, 79, 0.4);
  background: #4caf4f;
}

.menu-item.active::before {
  position: absolute;
  top: 12px;
  bottom: 12px;
  left: -14px;
  width: 3px;
  border-radius: 0 4px 4px 0;
  background: #c8e6c9;
  content: '';
}

.menu-icon {
  flex: none;
  font-size: 17px;
}

.menu-label {
  overflow: hidden;
  font-size: 13px;
  font-weight: 520;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.aside-foot {
  min-height: 52px;
  padding: 12px 16px;
  display: flex;
  align-items: center;
  gap: 9px;
  flex: none;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  color: #abbed1;
  font-size: 11.5px;
}

.aside-foot.offline {
  color: var(--danger);
}

.foot-text {
  flex: 1;
}

.foot-ver {
  color: #abbed1;
  font-size: 10.5px;
}

.body {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.header {
  height: var(--header-height);
  padding: 0 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex: none;
  border-bottom: 1px solid var(--border-subtle);
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 1px 8px rgba(171, 190, 209, 0.12);
  backdrop-filter: blur(12px);
}

.header-left,
.header-right {
  min-width: 0;
  display: flex;
  align-items: center;
}

.header-left {
  gap: 12px;
}

.header-right {
  gap: 11px;
}

.nav-toggle {
  min-height: 34px;
  padding: 0 10px;
  display: none;
  align-items: center;
  gap: 7px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text-primary);
  background: var(--bg-surface);
  cursor: pointer;
}

.nav-toggle:hover {
  border-color: var(--border-strong);
  background: var(--bg-surface-2);
}

.header-title {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 9px;
}

.header-context {
  color: var(--text-muted);
  font-size: 11.5px;
}

.header-context::after {
  margin-left: 9px;
  color: var(--border-strong);
  content: '/';
}

.header-current {
  overflow: hidden;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 620;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.clock {
  color: var(--text-secondary);
  font-size: 11.5px;
  letter-spacing: 0.015em;
  white-space: nowrap;
}

.divider {
  width: 1px;
  height: 18px;
  flex: none;
  background: var(--border-subtle);
}

.live {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--text-secondary);
  font-size: 11.5px;
  white-space: nowrap;
}

.live.offline {
  color: var(--danger);
}

.header-icon {
  position: relative;
  width: 34px;
  height: 34px;
  padding: 0;
  display: grid;
  place-items: center;
  flex: none;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  background: transparent;
  cursor: pointer;
}

.header-icon:hover {
  color: var(--text-primary);
  border-color: var(--border-subtle);
  background: var(--bg-surface-2);
}

.header-icon:active {
  background: var(--bg-hover);
}

.header-icon.logout:hover {
  color: var(--danger);
  border-color: rgba(180, 35, 24, 0.2);
  background: var(--danger-dim);
}

.user-avatar {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  flex: none;
  border: 1px solid #388e3b;
  border-radius: 50%;
  color: #ffffff;
  background: #4caf4f;
  font-size: 11px;
  font-weight: 700;
}

.user-name {
  max-width: 96px;
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 11.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.main {
  min-width: 0;
  min-height: 0;
  padding: 28px 30px 36px;
  flex: 1;
  overflow-x: hidden;
  overflow-y: auto;
  scroll-behavior: smooth;
}

.main:focus {
  outline: none;
}

.nav-scrim {
  display: none;
}

@media (max-width: 900px) {
  .layout {
    display: block;
  }

  .aside {
    position: fixed;
    z-index: 1001;
    inset: 0 auto 0 0;
    width: min(280px, 84vw);
    box-shadow: var(--shadow-lift);
    transform: translateX(-102%);
    visibility: hidden;
    transition: transform 0.2s ease, visibility 0.2s;
  }

  .aside.is-open {
    transform: translateX(0);
    visibility: visible;
  }

  .nav-scrim {
    position: fixed;
    z-index: 1000;
    inset: 0;
    width: 100%;
    height: 100%;
    padding: 0;
    display: block;
    border: 0;
    background: rgba(30, 61, 89, 0.58);
    cursor: default;
  }

  .nav-toggle {
    display: inline-flex;
  }

  .header {
    height: 56px;
    padding: 0 14px;
  }

  .main {
    padding: 16px 16px 22px;
  }
}

@media (max-width: 700px) {
  .clock,
  .user-name,
  .header-right .divider {
    display: none;
  }

  .header-right {
    gap: 5px;
  }

  .live > span:last-child {
    display: none;
  }

  .main {
    padding: 14px 12px 20px;
  }
}

@media (max-width: 430px) {
  .header-context {
    display: none;
  }

  .nav-toggle span {
    font-size: 12px;
  }

  .user-avatar {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .aside,
  .menu-item,
  .main {
    scroll-behavior: auto;
    transition-duration: 0.01ms;
  }
}
</style>
