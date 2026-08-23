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
        <div class="header-title">{{ route.meta.title }}</div>
        <div class="header-right">
          <div class="live">
            <span class="status-dot online"></span>
            <span>系统在线</span>
          </div>
          <span class="clock num">{{ clock }}</span>
          <span class="divider"></span>
          <el-button text class="logout" @click="logout">
            <el-icon><SwitchButton /></el-icon>
            <span>退出</span>
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
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const nav = [
  { path: '/dashboard', label: '数据大屏', icon: DataBoard },
  { path: '/monitor', label: '光照监测', icon: Sunny },
  { path: '/control', label: '设备控制', icon: Operation },
  { path: '/alarms', label: '告警管理', icon: Bell },
  { path: '/devices', label: '设备管理', icon: Tools },
  { path: '/users', label: '用户权限', icon: User },
  { path: '/chat', label: '智能问答', icon: ChatDotRound },
]

// 实时时钟：控制室的实时感，安静呈现
const clock = ref('')
let timer: ReturnType<typeof setInterval> | null = null
function tick() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  clock.value = `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}
onMounted(() => {
  tick()
  timer = setInterval(tick, 1000)
})
onUnmounted(() => {
  if (timer) clearInterval(timer)
})

function logout() {
  localStorage.removeItem('token')
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
  background: var(--bg-root);
  border-right: 1px solid var(--border-subtle);
  color: var(--text-secondary);
}

.brand {
  height: 68px;
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
  border-radius: 11px;
  background: var(--accent-dim);
  border: 1px solid rgba(223, 179, 79, 0.25);
  color: var(--accent-bright);
  transition: background 0.2s ease, transform 0.2s ease;
}
.brand-mark :deep(.el-icon) {
  font-size: 20px;
}
.brand-mark:hover {
  background: var(--accent);
  color: #221f16;
  transform: translateY(-1px);
}
.brand-text {
  line-height: 1.25;
}
.brand-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: 0.01em;
}
.brand-sub {
  font-size: 9px;
  letter-spacing: 0.1em;
  color: var(--text-muted);
  margin-top: 2px;
}

.menu {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 14px 12px;
  overflow-y: auto;
}
.menu-item {
  display: flex;
  align-items: center;
  gap: 11px;
  height: 42px;
  padding: 0 13px;
  border: none;
  border-radius: 10px;
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
  background: var(--accent-dim);
  color: var(--accent-bright);
}
.menu-icon {
  font-size: 18px;
}
.menu-item.active .menu-icon {
  color: var(--accent);
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
  height: 68px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28px;
  background: var(--bg-root);
  border-bottom: 1px solid var(--border-subtle);
}
.header-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}
.header-right {
  display: flex;
  align-items: center;
  gap: 18px;
}
.live {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12.5px;
  color: var(--text-secondary);
}
.clock {
  font-size: 13px;
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
  padding: 6px 8px;
  border-radius: 8px;
}
.logout:hover {
  color: var(--danger);
}
.logout :deep(.el-icon) {
  margin-right: 6px;
}

.main {
  padding: 24px 28px 32px;
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
