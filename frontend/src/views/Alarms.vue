<template>
  <div class="workspace-page alarms">
    <div class="page-intro">
      <div>
        <h1 class="page-title">告警中心</h1>
        <p class="page-desc">集中扫描未处理事件并追溯历史处置记录</p>
      </div>
      <div class="refresh">
        <span class="status-dot idle"></span>
        <span class="num">5s</span> 自动刷新
      </div>
    </div>

    <NotReadyBanner
      v-if="!ready"
      text="告警接口尚未由后端实现（约定 GET /api/alarms），当前展示为空态；接口就绪后自动加载告警。"
    />

    <div class="stats">
      <div class="stat-card">
        <div class="stat-label">
          <el-icon class="label-icon"><Bell /></el-icon>
          未处理告警
        </div>
        <div class="stat-value">{{ openCount }}<span class="unit">条</span></div>
        <div class="stat-sub">设备离线且未确认</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">
          <el-icon class="label-icon"><CircleCheck /></el-icon>
          已恢复
        </div>
        <div class="stat-value">{{ recoveredCount }}<span class="unit">条</span></div>
        <div class="stat-sub">设备已恢复，待确认关闭</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">
          <el-icon class="label-icon"><CircleClose /></el-icon>
          离线设备
        </div>
        <div class="stat-value">{{ offlineCount }}<span class="unit">台</span></div>
        <div class="stat-sub">当前处于离线状态</div>
      </div>
      <div class="stat-card accent">
        <div class="stat-label">
          <el-icon class="label-icon"><Finished /></el-icon>
          已闭环
        </div>
        <div class="stat-value">{{ closedCount }}<span class="unit">条</span></div>
        <div class="stat-sub">已确认并派单处理</div>
      </div>
    </div>

    <div class="panel">
      <div class="panel-head">
        <div class="panel-title">告警记录</div>
        <div class="panel-meta">
          <el-radio-group v-model="filter" size="small">
            <el-radio-button label="all">全部</el-radio-button>
            <el-radio-button label="pending">未处理</el-radio-button>
            <el-radio-button label="closed">已闭环</el-radio-button>
          </el-radio-group>
        </div>
      </div>
      <div class="table-wrap">
        <el-table :data="filteredGroups">
          <el-table-column label="设备" width="120">
            <template #default="{ row }">
              <span class="num">{{ row.deviceId }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="type" label="类型" width="130" />
          <el-table-column label="级别" width="110">
            <template #default="{ row }">
              <span class="level-pill" :class="row.level">{{ levelLabel(row.level) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="首次发生" width="160">
            <template #default="{ row }">
              <span class="num">{{ time(row.firstTs) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="最后发生" width="160">
            <template #default="{ row }">
              <span class="num">{{ time(row.lastTs) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="次数" width="80" align="center">
            <template #default="{ row }">
              <span class="count-pill num">{{ row.count }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="message" label="告警内容" min-width="220" show-overflow-tooltip />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <span class="status-pill" :class="statusClass(row.status as AlarmGroupStatus)">
                <span class="status-dot" :class="statusDotClass(row.status as AlarmGroupStatus)"></span>
                {{ statusLabel[row.status as AlarmGroupStatus] }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" align="right" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.latest.status === 'OPEN'"
                size="small"
                text
                type="primary"
                :disabled="acknowledging"
                @click="onAck(row)"
              >
                确认处理
              </el-button>
              <span v-else class="acked-note">已闭环</span>
            </template>
          </el-table-column>
          <template #empty>
            <div class="table-empty">
              {{ ready ? '暂无告警记录' : '告警服务暂不可用' }}
            </div>
          </template>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { Bell, CircleCheck, CircleClose, Finished } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { listAlarms, ackAlarm } from '../api/alarm'
import type { AlarmGroup, AlarmGroupStatus, AlarmLevel, AlarmVO } from '../api/alarm'
import { listDevices } from '../api/device'
import type { DeviceVO } from '../api/device'
import { aggregateAlarms, buildDeviceStatusMap, statusCounts } from '../utils/alarms'
import { probe } from '../api/helper'
import NotReadyBanner from '../components/NotReadyBanner.vue'

const alarms = ref<AlarmVO[]>([])
const devices = ref<DeviceVO[]>([])
const filter = ref<'all' | 'pending' | 'closed'>('all')
const ready = ref(true)
const acknowledging = ref(false)
let timer: ReturnType<typeof setInterval> | null = null

const deviceStatusMap = computed(() => buildDeviceStatusMap(devices.value))
const groups = computed(() => aggregateAlarms(alarms.value, deviceStatusMap.value))
const counts = computed(() => statusCounts(groups.value))

const openCount = computed(() => counts.value.OPEN)
const recoveredCount = computed(() => counts.value.RECOVERED)
const closedCount = computed(() => counts.value.ACKED + counts.value.CLOSED)
const offlineCount = computed(() => devices.value.filter((d) => d.status === 'OFFLINE').length)

const filteredGroups = computed(() => {
  if (filter.value === 'all') return groups.value
  if (filter.value === 'pending')
    return groups.value.filter((g) => g.status === 'OPEN' || g.status === 'RECOVERED')
  return groups.value.filter((g) => g.status === 'ACKED' || g.status === 'CLOSED')
})

const statusLabel: Record<AlarmGroupStatus, string> = {
  OPEN: '未处理',
  RECOVERED: '已恢复',
  ACKED: '已确认',
  CLOSED: '已关闭',
}
const statusClass = (s: AlarmGroupStatus) =>
  s === 'OPEN' ? 'offline' : s === 'RECOVERED' ? 'recovered' : s === 'ACKED' ? 'acked' : 'closed'
const statusDotClass = (s: AlarmGroupStatus) =>
  s === 'OPEN' ? 'offline' : s === 'RECOVERED' ? 'idle' : s === 'CLOSED' ? 'online' : ''

const levelLabel = (l: AlarmLevel) =>
  ({ info: '提示', warning: '警告', critical: '严重' })[l]

const pad = (n: number) => String(n).padStart(2, '0')
function time(ts: number) {
  const d = new Date(ts)
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

// 每次轮询都重新探针：后端接口就绪后横幅会自动消失
async function load() {
  const [r, devs] = await Promise.all([
    probe(listAlarms(), []),
    listDevices().catch(() => [] as DeviceVO[]),
  ])
  ready.value = r.ready
  if (r.ready) alarms.value = r.data
  if (devs.length) devices.value = devs
}

async function onAck(group: AlarmGroup) {
  if (!group.openIds.length || acknowledging.value) return
  acknowledging.value = true
  try {
    const results = await Promise.all(group.openIds.map((id) => probe(ackAlarm(id), null)))
    const ok = results.filter((r) => r.ready).length
    const total = group.openIds.length
    if (ok === total) {
      ElMessage.success(`${group.deviceId} 的 ${total} 条告警已确认`)
    } else if (ok > 0) {
      ElMessage.warning(`部分告警确认失败（${ok}/${total} 成功）`)
    } else {
      ready.value = false
      return
    }
    await load()
  } finally {
    acknowledging.value = false
  }
}

onMounted(() => {
  load()
  timer = setInterval(load, 5000)
})
onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.alarms {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-intro {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}
.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 680;
  letter-spacing: -0.025em;
  color: var(--text-primary);
}
.page-desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--text-muted);
}
.refresh {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12.5px;
  color: var(--text-secondary);
}

.stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.label-icon {
  font-size: 15px;
  color: var(--text-muted);
  transition: color 0.2s ease;
}
.stat-card:hover .label-icon {
  color: var(--accent-bright);
}

.table-wrap {
  padding: 6px 16px 16px;
}
.level-pill {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 3px;
  font-size: 12px;
  font-weight: 500;
}
.level-pill.info {
  color: var(--text-secondary);
  background: var(--info-dim);
}
.level-pill.warning {
  color: var(--warn);
  background: var(--warn-dim);
}
.level-pill.critical {
  color: var(--danger);
  background: var(--danger-dim);
}
.acked-note {
  font-size: 12px;
  color: var(--text-muted);
}
.status-pill.recovered {
  color: var(--accent-bright);
  border-color: rgba(250, 152, 25, 0.25);
  background: var(--accent-dim);
}
.status-pill.acked {
  color: var(--text-secondary);
  border-color: var(--border);
  background: var(--bg-surface-2);
}
.status-pill.closed {
  color: var(--ok);
  border-color: rgba(23, 122, 80, 0.2);
  background: var(--ok-dim);
}
.count-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 34px;
  padding: 1px 8px;
  border-radius: 3px;
  color: var(--text-secondary);
  background: var(--bg-surface-2);
  border: 1px solid var(--border);
  font-weight: 600;
}
.table-empty {
  padding: 32px 0;
  color: var(--text-muted);
  font-size: 12.5px;
}

@media (max-width: 700px) {
  .stats {
    grid-template-columns: 1fr;
  }
  .page-intro {
    flex-direction: column;
    align-items: flex-start;
  }
}
/* Incident funnel and disposition ledger */
.alarms { max-width: 1640px; display: grid; grid-template-columns: 240px minmax(0, 1fr); align-items: start; gap: 0; }
.alarms > .page-intro, .alarms > .not-ready { grid-column: 1 / -1; margin-bottom: 18px; }
.alarms .stats { grid-template-columns: 1fr; gap: 0; }
.alarms .stat-card { min-height: 150px; border: 1px solid var(--border); border-right: 0; border-bottom: 0; border-radius: 0; box-shadow: none; }
.alarms .stat-card:last-child { min-height: 190px; border-bottom: 1px solid var(--border); background: var(--signal); }
.alarms > .panel { min-height: 490px; border-radius: 0; }
.alarms .stat-value { color: var(--ink); font-size: 38px; }
@media (max-width: 800px) { .alarms { display: flex; align-items: stretch; } .alarms > .panel { width: 100%; } .alarms .stats { grid-template-columns: repeat(2, 1fr); } .alarms .stat-card { min-height: 120px; border-right: 1px solid var(--border); border-bottom: 1px solid var(--border); } .alarms .stat-card + .stat-card { border-left: 0; } .alarms .stat-card:last-child { min-height: 120px; } }
@media (max-width: 560px) { .alarms .stats { grid-template-columns: 1fr; } .alarms .stat-card + .stat-card { border-top: 0; border-left: 1px solid var(--border); } }
</style>
