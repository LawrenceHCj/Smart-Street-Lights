<template>
  <div class="atlas-shell">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <header class="command-bar">
      <button class="wordmark" type="button" aria-label="返回运行概览" @click="navigate('/dashboard')">
        <span class="wordmark-symbol" aria-hidden="true"><el-icon><ReadingLamp /></el-icon></span>
        <span><strong>夜航图谱</strong><small>SMART LIGHT ATLAS</small></span>
      </button>
      <nav class="desktop-nav" aria-label="主导航">
        <button v-for="item in nav" :key="item.path" type="button" :class="{ active: route.path === item.path }" :aria-current="route.path === item.path ? 'page' : undefined" @click="navigate(item.path)">
          <span class="nav-index num">{{ item.index }}</span><span>{{ item.label }}</span>
        </button>
      </nav>
      <div class="command-meta">
        <div class="network-state" :class="{ offline: !isOnline }" aria-live="polite"><span class="status-dot" :class="isOnline ? 'online' : 'offline'"></span><span>{{ isOnline ? '系统在线' : '网络离线' }}</span></div>
        <time class="clock num" :datetime="clockIso">{{ fullClock }}</time>
        <button class="meta-action" type="button" aria-label="打开告警中心" @click="navigate('/alarms')"><el-icon><Bell /></el-icon></button>
        <button class="logout-action" type="button" aria-label="退出登录" @click="logout"><el-icon><SwitchButton /></el-icon><span>退出</span></button>
        <div ref="accountMenuRef" class="account-menu">
          <button class="user-trigger" type="button" aria-label="打开用户菜单" aria-controls="account-menu-panel" :aria-expanded="accountMenuOpen" @click="accountMenuOpen = !accountMenuOpen">
            <span class="avatar">{{ currentUser[0]?.toUpperCase() ?? 'U' }}</span>
            <span class="user-copy"><strong>{{ currentUser }}</strong><small>运维席位</small></span><el-icon class="account-arrow" :class="{ open: accountMenuOpen }"><ArrowDown /></el-icon>
          </button>
          <div v-if="accountMenuOpen" id="account-menu-panel" class="account-menu-panel" role="menu">
            <div class="account-menu-head"><strong>{{ currentUser }}</strong><span>当前运维席位</span></div>
            <button type="button" role="menuitem" @click="navigate('/users')"><el-icon><User /></el-icon><span>用户与权限</span></button>
            <button type="button" role="menuitem" class="danger" @click="logout"><el-icon><SwitchButton /></el-icon><span>退出登录</span></button>
          </div>
        </div>
      </div>
    </header>
    <div class="context-strip">
      <div><span class="context-kicker">CITY LIGHTING / {{ route.name }}</span><strong>{{ route.meta.title }}</strong></div>
      <div class="context-coordinate num" aria-hidden="true">29.5934° N · 106.2980° E</div>
    </div>
    <main id="main-content" ref="mainRef" class="atlas-main" tabindex="-1"><router-view /></main>
    <nav class="mobile-dock" aria-label="移动端主导航">
      <button v-for="item in mobileNav" :key="item.path" type="button" :class="{ active: route.path === item.path }" :aria-current="route.path === item.path ? 'page' : undefined" @click="navigate(item.path)">
        <el-icon><component :is="item.icon" /></el-icon><span>{{ item.shortLabel }}</span>
      </button>
      <el-dropdown trigger="click" placement="top-end" @command="onMoreCommand">
        <button type="button" :class="{ active: moreActive }" aria-label="更多功能"><el-icon><MoreFilled /></el-icon><span>更多</span></button>
        <template #dropdown><el-dropdown-menu><el-dropdown-item v-for="item in moreNav" :key="item.path" :command="item.path">{{ item.label }}</el-dropdown-item><el-dropdown-item divided command="logout">退出登录</el-dropdown-item></el-dropdown-menu></template>
      </el-dropdown>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, Bell, ChatDotRound, DataBoard, MoreFilled, Operation, ReadingLamp, Sunny, SwitchButton, Tools, User } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const isOnline = ref(navigator.onLine)
const fullClock = ref('')
const clockIso = ref('')
const currentUser = localStorage.getItem('username') || '用户'
const mainRef = ref<HTMLElement>()
const accountMenuRef = ref<HTMLElement>()
const accountMenuOpen = ref(false)
let timer: ReturnType<typeof setInterval> | null = null

const nav = [
  { index: '01', path: '/dashboard', label: '全域态势', shortLabel: '态势', icon: DataBoard },
  { index: '02', path: '/monitor', label: '光照脉络', shortLabel: '监测', icon: Sunny },
  { index: '03', path: '/control', label: '策略控制', shortLabel: '控制', icon: Operation },
  { index: '04', path: '/alarms', label: '事件中枢', shortLabel: '事件', icon: Bell },
  { index: '05', path: '/devices', label: '资产编目', shortLabel: '设备', icon: Tools },
  { index: '06', path: '/chat', label: '智能研判', shortLabel: '助手', icon: ChatDotRound },
  { index: '07', path: '/users', label: '席位权限', shortLabel: '用户', icon: User },
]
const mobileNav = nav.slice(0, 4)
const moreNav = nav.slice(4)
const moreActive = computed(() => moreNav.some((item) => item.path === route.path))

function tick() { const d = new Date(); const p = (n: number) => String(n).padStart(2, '0'); fullClock.value = `${p(d.getHours())}:${p(d.getMinutes())}`; clockIso.value = d.toISOString() }
function navigate(path: string) { accountMenuOpen.value = false; if (route.path !== path) router.push(path) }
function logout() { accountMenuOpen.value = false; localStorage.removeItem('token'); localStorage.removeItem('username'); router.push('/login') }
function onMoreCommand(command: string) { command === 'logout' ? logout() : navigate(command) }
function updateNetworkStatus() { isOnline.value = navigator.onLine }
function onDocumentPointerDown(event: PointerEvent) { if (!accountMenuRef.value?.contains(event.target as Node)) accountMenuOpen.value = false }
function onDocumentKeydown(event: KeyboardEvent) { if (event.key === 'Escape') accountMenuOpen.value = false }
watch(() => route.fullPath, async () => { await nextTick(); mainRef.value?.scrollTo({ top: 0, behavior: 'auto' }) })
onMounted(() => { tick(); timer = setInterval(tick, 30_000); window.addEventListener('online', updateNetworkStatus); window.addEventListener('offline', updateNetworkStatus); document.addEventListener('pointerdown', onDocumentPointerDown); document.addEventListener('keydown', onDocumentKeydown) })
onUnmounted(() => { if (timer) clearInterval(timer); window.removeEventListener('online', updateNetworkStatus); window.removeEventListener('offline', updateNetworkStatus); document.removeEventListener('pointerdown', onDocumentPointerDown); document.removeEventListener('keydown', onDocumentKeydown) })
</script>

<style scoped>
.atlas-shell { height: 100%; background: var(--bg-root); }
.command-bar { position: fixed; z-index: 100; inset: 0 0 auto; height: 72px; padding: 0 24px; display: grid; grid-template-columns: 230px minmax(0, 1fr) auto; align-items: stretch; color: #f4fff9; background: var(--ink); border-bottom: 1px solid rgba(208, 255, 111, .2); }
.wordmark { display: flex; align-items: center; gap: 11px; padding: 0; border: 0; color: inherit; background: none; text-align: left; cursor: pointer; }
.wordmark-symbol { width: 35px; height: 35px; display: grid; place-items: center; color: var(--signal); border: 1px solid rgba(208, 255, 111, .45); transform: rotate(45deg); }
.wordmark-symbol :deep(.el-icon) { font-size: 19px; transform: rotate(-45deg); }
.wordmark strong, .wordmark small { display: block; }
.wordmark strong { font-size: 16px; letter-spacing: .08em; }
.wordmark small { margin-top: 2px; color: #91a9a1; font: 9px/1 var(--font-data); letter-spacing: .14em; }
.desktop-nav { min-width: 0; display: flex; justify-content: center; }
.desktop-nav button { position: relative; min-width: 92px; padding: 0 13px; border: 0; color: #9eb2ab; background: transparent; cursor: pointer; }
.desktop-nav button::after { content: ''; position: absolute; right: 13px; bottom: 0; left: 13px; height: 3px; background: var(--signal); transform: scaleX(0); transition: transform .18s ease; }
.desktop-nav button:hover, .desktop-nav button.active { color: #fff; }
.desktop-nav button.active::after { transform: scaleX(1); }
.nav-index { display: block; margin-bottom: 3px; color: #6f877f; font-size: 9px; }
.desktop-nav button.active .nav-index { color: var(--signal); }
.command-meta { display: flex; align-items: center; gap: 14px; }
.network-state { display: flex; align-items: center; gap: 7px; color: #b8c9c3; font-size: 12px; white-space: nowrap; }
.network-state.offline { color: #ffaaa3; }
.clock { color: var(--signal); font-size: 13px; }
.meta-action { width: 36px; height: 36px; border: 1px solid rgba(255,255,255,.14); color: #dce9e4; background: transparent; cursor: pointer; }
.meta-action:hover { color: var(--signal); border-color: rgba(208,255,111,.55); }
.logout-action { height: 36px; padding: 0 10px; display: inline-flex; align-items: center; gap: 6px; border: 1px solid rgba(255,255,255,.14); color: #c6d6d0; background: transparent; cursor: pointer; font-size: 11px; }
.logout-action:hover { color: #fff; border-color: #ff776d; background: rgba(255,119,109,.12); }
.user-trigger { display: flex; align-items: center; gap: 9px; padding: 0; border: 0; color: #f4fff9; background: none; cursor: pointer; }
.account-menu { position: relative; }
.account-arrow { transition: transform .18s ease; }
.account-arrow.open { transform: rotate(180deg); }
.account-menu-panel { position: absolute; z-index: 130; top: calc(100% + 14px); right: 0; width: 218px; padding: 8px; border: 1px solid rgba(208,255,111,.25); color: #eef8f4; background: #0b2c25; box-shadow: 0 22px 50px rgba(0,0,0,.28); }
.account-menu-panel::before { content: ''; position: absolute; top: -8px; right: 14px; width: 14px; height: 14px; border-top: 1px solid rgba(208,255,111,.25); border-left: 1px solid rgba(208,255,111,.25); background: #0b2c25; transform: rotate(45deg); }
.account-menu-head { padding: 10px 11px 12px; display: grid; gap: 2px; border-bottom: 1px solid rgba(255,255,255,.11); }
.account-menu-head strong { color: #fff; font-size: 13px; }
.account-menu-head span { color: #a9bdb6; font-size: 11px; }
.account-menu-panel button { width: 100%; min-height: 40px; padding: 0 11px; display: flex; align-items: center; gap: 9px; border: 0; color: #dbe9e4; background: transparent; cursor: pointer; text-align: left; }
.account-menu-panel button:hover, .account-menu-panel button:focus-visible { color: var(--ink); background: var(--signal); }
.account-menu-panel button.danger { color: #ffaaa3; }
.account-menu-panel button.danger:hover, .account-menu-panel button.danger:focus-visible { color: #fff; background: #9f3129; }
.avatar { width: 34px; height: 34px; display: grid; place-items: center; color: var(--ink); background: var(--signal); font-weight: 800; }
.user-copy { display: grid; text-align: left; }
.user-copy strong { max-width: 80px; overflow: hidden; font-size: 12px; text-overflow: ellipsis; }
.user-copy small { color: #80978f; font-size: 10px; }
.context-strip { position: fixed; z-index: 90; top: 72px; right: 0; left: 0; height: 58px; padding: 0 28px; display: flex; align-items: center; justify-content: space-between; background: rgba(246,249,246,.94); border-bottom: 1px solid var(--border); backdrop-filter: blur(14px); }
.context-strip > div:first-child { display: flex; align-items: baseline; gap: 14px; }
.context-strip strong { color: var(--ink); font-size: 18px; letter-spacing: -.02em; }
.context-kicker { color: var(--text-muted); font: 9px/1 var(--font-data); letter-spacing: .16em; text-transform: uppercase; }
.context-coordinate { color: var(--text-muted); font-size: 10px; letter-spacing: .08em; }
.atlas-main { height: 100vh; padding: 150px 28px 32px; overflow: auto; }
.mobile-dock { display: none; }
@media (max-width: 1180px) { .command-bar { grid-template-columns: 190px minmax(0,1fr) auto; padding-inline: 18px; } .desktop-nav button { min-width: 74px; padding-inline: 7px; font-size: 12px; } .desktop-nav button::after { right: 7px; left: 7px; } .network-state, .user-copy, .logout-action span { display: none; } .logout-action { width: 36px; padding: 0; justify-content: center; } }
@media (max-width: 820px) { .command-bar { height: 62px; grid-template-columns: 1fr auto; padding: 0 16px; } .wordmark strong { font-size: 14px; } .wordmark small, .desktop-nav, .clock, .meta-action { display: none; } .context-strip { top: 62px; height: 52px; padding: 0 16px; color: #fff; border-color: rgba(208,255,111,.14); background: rgba(6,34,29,.96); } .context-strip > div:first-child { display: grid; gap: 3px; } .context-strip strong { color: #fff; font-size: 16px; } .context-kicker { color: #779188; } .context-coordinate { display: none; } .atlas-main { padding: 130px 14px 88px; background: var(--ink); } .atlas-main :deep(.page-title) { color: #f5fff8; } .atlas-main :deep(.page-desc), .atlas-main :deep(.refresh), .atlas-main :deep(.page-status) { color: #8fa79f; } .mobile-dock { position: fixed; z-index: 110; right: 10px; bottom: 10px; left: 10px; height: 66px; display: grid; grid-template-columns: repeat(5, 1fr); padding: 6px; border: 1px solid rgba(208,255,111,.18); background: rgba(6,34,29,.96); box-shadow: 0 18px 50px rgba(3,22,18,.32); backdrop-filter: blur(18px); } .mobile-dock button { width: 100%; height: 54px; display: grid; place-items: center; align-content: center; gap: 3px; border: 0; color: #829a91; background: transparent; font-size: 10px; cursor: pointer; } .mobile-dock button :deep(.el-icon) { font-size: 20px; } .mobile-dock button.active { color: var(--signal); background: rgba(208,255,111,.08); } .user-trigger :deep(.el-icon) { display: none; } }
</style>
