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
        <div class="stat-sub">待人工确认处理</div>
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
          已处理
        </div>
        <div class="stat-value">{{ ackedCount }}<span class="unit">条</span></div>
        <div class="stat-sub">已确认并派单处理</div>
      </div>
    </div>

    <div class="panel">
      <div class="panel-head">
        <div class="panel-title">告警记录</div>
        <div class="panel-meta">
          <el-radio-group v-model="filter" size="small">
            <el-radio-button label="all">全部</el-radio-button>
            <el-radio-button label="open">未处理</el-radio-button>
          </el-radio-group>
        </div>
      </div>
      <div class="table-wrap">
        <el-table :data="filteredAlarms">
          <el-table-column label="时间" width="180">
            <template #default="{ row }">
              <span class="num">{{ time(row.ts) }}</span>
            </template>
          </el-table-column>
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
          <el-table-column prop="message" label="告警内容" min-width="240" show-overflow-tooltip />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <span class="status-pill" :class="row.status === 'OPEN' ? 'offline' : 'online'">
                <span class="status-dot" :class="row.status === 'OPEN' ? 'offline' : 'online'"></span>
                {{ row.status === 'OPEN' ? '未处理' : '已处理' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" align="right" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'OPEN'"
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
import { Bell, CircleClose, Finished } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { listAlarms, ackAlarm } from '../api/alarm'
import type { AlarmVO, AlarmLevel } from '../api/alarm'
import { listDevices } from '../api/device'
import type { DeviceVO } from '../api/device'
import { probe } from '../api/helper'
import NotReadyBanner from '../components/NotReadyBanner.vue'

const alarms = ref<AlarmVO[]>([])
const devices = ref<DeviceVO[]>([])
const filter = ref<'all' | 'open'>('all')
const ready = ref(true)
const acknowledging = ref(false)
let timer: ReturnType<typeof setInterval> | null = null

const openCount = computed(() => alarms.value.filter((a) => a.status === 'OPEN').length)
const ackedCount = computed(() => alarms.value.filter((a) => a.status === 'ACKED').length)
const offlineCount = computed(() => devices.value.filter((d) => d.status === 'OFFLINE').length)

const filteredAlarms = computed(() =>
  filter.value === 'all' ? alarms.value : alarms.value.filter((a) => a.status === 'OPEN'),
)

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

async function onAck(row: AlarmVO) {
  acknowledging.value = true
  try {
    const r = await probe(ackAlarm(row.id), null)
    if (r.ready) {
      row.status = 'ACKED'
      ElMessage.success(`${row.deviceId} 告警已确认`)
    } else {
      ready.value = false
    }
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
@media (max-width: 800px) { .alarms { display: flex; align-items: stretch; } .alarms > .panel { width: 100%; } .alarms .stats { grid-template-columns: repeat(3, 1fr); } .alarms .stat-card { min-height: 120px; border-right: 1px solid var(--border); border-bottom: 1px solid var(--border); } .alarms .stat-card + .stat-card { border-left: 0; } .alarms .stat-card:last-child { min-height: 120px; } }
@media (max-width: 560px) { .alarms .stats { grid-template-columns: 1fr; } .alarms .stat-card + .stat-card { border-top: 0; border-left: 1px solid var(--border); } }
</style>
