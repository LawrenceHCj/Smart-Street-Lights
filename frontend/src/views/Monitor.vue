<template>
  <div class="workspace-page monitor" :aria-busy="loading">
    <div class="page-intro">
      <div>
        <h1 class="page-title">实时监控</h1>
        <p class="page-desc">按设备扫描在线状态、当前照度与历史趋势</p>
      </div>
      <div class="refresh" aria-live="polite">
        <span class="status-dot" :class="loadError ? 'offline' : 'online'"></span>
        <span><span class="num">{{ onlineCount }}</span> 在线 · <span class="num">{{ offlineCount }}</span> 离线</span>
        <el-tooltip content="立即刷新设备数据" placement="bottom">
          <el-button
            text
            circle
            :icon="Refresh"
            aria-label="立即刷新设备数据"
            :loading="loading"
            @click="loadDevices(true)"
          />
        </el-tooltip>
      </div>
    </div>

    <div v-if="loadError" class="async-banner" role="alert">
      <el-icon aria-hidden="true"><WarningFilled /></el-icon>
      <span>{{ loadError }}</span>
      <button class="inline-action" type="button" :disabled="loading" @click="loadDevices(true)">重试</button>
    </div>

    <div class="panel">
      <div class="panel-head">
        <div class="panel-title">光照监测 · 设备列表</div>
        <div class="panel-meta search-meta">
          <el-input
            v-model="keyword"
            size="small"
            class="search"
            :prefix-icon="Search"
            placeholder="搜索设备编号 / 位置"
            clearable
          />
          <span class="count">{{ filteredDevices.length }} / {{ devices.length }} 台</span>
        </div>
      </div>
      <div class="table-wrap desktop-device-table">
        <el-table
          v-loading="firstLoading && !devices.length"
          :data="filteredDevices"
          row-key="code"
          highlight-current-row
          @current-change="onSelect"
        >
          <el-table-column label="设备" min-width="200">
            <template #default="{ row }">
              <div class="device-cell">
                <span class="device-avatar" :class="row.status === 'ONLINE' ? 'on' : 'off'">
                  <el-icon><ReadingLamp /></el-icon>
                </span>
                <span class="device-code num">{{ row.code }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="location" label="位置" min-width="160">
            <template #default="{ row }">
              <span class="loc">{{ row.location }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <span class="status-pill" :class="row.status === 'ONLINE' ? 'online' : 'offline'">
                <span class="status-dot" :class="row.status === 'ONLINE' ? 'online' : 'offline'"></span>
                {{ row.status === 'ONLINE' ? '在线' : '离线' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="当前照度" width="140" align="right">
            <template #default="{ row }">
              <span class="lux num">{{ row.latestLux == null ? '—' : row.latestLux }}</span>
              <span v-if="row.latestLux != null" class="lux-unit">Lux</span>
            </template>
          </el-table-column>
          <el-table-column label="最后在线" width="130" align="right">
            <template #default="{ row }">
              <span class="last">{{ timeAgo(row.lastSeen) }}</span>
            </template>
          </el-table-column>
          <template #empty>
            <div class="table-empty">
              {{ firstLoading ? '正在加载设备' : keyword ? '未找到匹配设备' : '暂无接入设备' }}
            </div>
          </template>
        </el-table>
      </div>
      <div class="mobile-device-list" aria-label="设备列表">
        <button
          v-for="device in filteredDevices"
          :key="device.code"
          type="button"
          class="mobile-device-row"
          :class="{ selected: currentCode === device.code }"
          :aria-pressed="currentCode === device.code"
          @click="onSelect(device)"
        >
          <span class="mobile-device-main">
            <span class="device-code num">{{ device.code }}</span>
            <span class="status-pill" :class="device.status === 'ONLINE' ? 'online' : 'offline'">
              <span class="status-dot" :class="device.status === 'ONLINE' ? 'online' : 'offline'"></span>
              {{ device.status === 'ONLINE' ? '在线' : '离线' }}
            </span>
          </span>
          <span class="mobile-device-location">{{ device.location || '位置未标注' }}</span>
          <span class="mobile-device-meta">
            <span>照度 <strong class="num">{{ device.latestLux ?? '—' }}</strong><small v-if="device.latestLux !== null"> Lux</small></span>
            <span>最后在线 {{ timeAgo(device.lastSeen) }}</span>
          </span>
        </button>
        <div v-if="firstLoading && !devices.length" class="empty-state" role="status">正在加载设备</div>
        <div v-else-if="!filteredDevices.length" class="empty-state">{{ keyword ? '未找到匹配设备' : '暂无接入设备' }}</div>
      </div>
    </div>

    <div class="panel">
      <div class="panel-head trend-head">
        <div class="panel-title">历史光照趋势 · {{ currentCode }}</div>
        <div class="trend-controls">
          <div v-if="liveLux.lux != null" class="live-lux" aria-live="polite">
            <span class="live-label">当前照度</span>
            <span class="live-value num">{{ liveLux.lux }}</span>
            <span class="live-unit">Lux</span>
            <span class="live-ts num">· {{ liveTime }}</span>
          </div>
          <span v-else-if="liveUnavailable" class="live-unavailable">实时照度暂不可用</span>
          <div class="range-switch">
            <button
              v-for="r in ranges"
              :key="r.value"
              :class="{ active: range === r.value }"
              @click="setRange(r.value)"
            >
              {{ r.label }}
            </button>
          </div>
        </div>
      </div>
      <div class="panel-body chart-body">
        <div ref="chartRef" class="chart"></div>
        <div v-if="chartLoading && !hasChartData" class="chart-overlay" role="status">正在加载趋势数据</div>
        <div v-else-if="chartError && !hasChartData" class="chart-overlay" role="alert">
          <span>{{ chartError }}</span>
          <button class="inline-action" type="button" @click="loadHistory">重试</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ReadingLamp, Refresh, Search, WarningFilled } from '@element-plus/icons-vue'
import { listDevices, getHistory, getCurrentLight } from '../api/device'
import type { DeviceVO, LightPoint } from '../api/device'
import { initLuxChart, luxLineOption } from '../utils/chart'
import type { ChartInstance } from '../utils/chart'

const devices = ref<DeviceVO[]>([])
const currentCode = ref('SL-001')
const keyword = ref('')
const liveLux = ref<{ lux: number | null; ts: number | null }>({ lux: null, ts: null })
const chartRef = ref<HTMLDivElement>()
const firstLoading = ref(true)
const loadError = ref('')
const chartLoading = ref(false)
const chartError = ref('')
const hasChartData = ref(false)
const liveUnavailable = ref(false)
let chart: ChartInstance | null = null
let timer: ReturnType<typeof setInterval> | null = null
let resizeObserver: ResizeObserver | null = null
const loading = ref(false)
let historyRequestId = 0

type RangeKey = '24h' | '7d'
const range = ref<RangeKey>('24h')
const ranges: Array<{ value: RangeKey; label: string }> = [
  { value: '24h', label: '24 小时' },
  { value: '7d', label: '近 7 天' },
]

const onlineCount = computed(() => devices.value.filter((d) => d.status === 'ONLINE').length)
const offlineCount = computed(() => devices.value.filter((d) => d.status === 'OFFLINE').length)

const filteredDevices = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return devices.value
  return devices.value.filter(
    (d) =>
      d.code.toLowerCase().includes(kw) ||
      (d.location || '').toLowerCase().includes(kw),
  )
})

const pad = (n: number) => String(n).padStart(2, '0')
const liveTime = computed(() => {
  if (!liveLux.value.ts) return ''
  const d = new Date(liveLux.value.ts)
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
})

async function loadDevices(manual = false) {
  if (loading.value) return
  loading.value = true
  try {
    devices.value = await listDevices({ silent: true })
    loadError.value = ''
    // 轮询时不打断用户当前选中；仅当选中设备已不存在时回退到第一台
    if (devices.value.length && !devices.value.some((d) => d.code === currentCode.value)) {
      currentCode.value = devices.value[0].code
    }
    await Promise.allSettled([loadHistory(), loadLiveLight()])
  } catch {
    loadError.value = manual
      ? '刷新失败，请检查网络或后端服务后重试。'
      : '暂时无法获取设备数据，页面会继续自动重试。'
  } finally {
    loading.value = false
    firstLoading.value = false
  }
}

async function loadHistory() {
  const code = currentCode.value
  const requestId = ++historyRequestId
  const now = Date.now()
  const h = 3600_000
  const start = now - (range.value === '24h' ? 24 * h : 7 * 24 * h)
  chartLoading.value = true
  try {
    const hist = await getHistory(code, start, now, { silent: true })
    if (requestId !== historyRequestId || code !== currentCode.value) return
    chartError.value = ''
    renderChart(hist.points)
  } catch {
    if (requestId !== historyRequestId) return
    chartError.value = '趋势数据加载失败'
    if (!hasChartData.value) renderChart([])
  } finally {
    if (requestId === historyRequestId) chartLoading.value = false
  }
}

async function loadLiveLight() {
  const code = currentCode.value
  if (!devices.value.some((d) => d.code === code)) return // 编号未确认存在时跳过，避免"设备不存在"提示
  try {
    const r = await getCurrentLight(code, { silent: true })
    if (code === currentCode.value) {
      liveLux.value = r
      liveUnavailable.value = false
    }
  } catch {
    if (code === currentCode.value) liveUnavailable.value = true
  }
}

function setRange(v: RangeKey) {
  range.value = v
  loadHistory()
}

function onSelect(row: DeviceVO | undefined) {
  if (row) {
    currentCode.value = row.code
    loadHistory()
    loadLiveLight()
  }
}

function renderChart(points: LightPoint[]) {
  if (!chartRef.value) return
  if (!chart) chart = initLuxChart(chartRef.value)
  hasChartData.value = points.length > 0
  chart.setOption(luxLineOption(points.map((p) => p.ts), points.map((p) => p.lux)), true)
}

function timeAgo(ts: number | null) {
  if (!ts) return '—'
  const diff = Date.now() - ts
  if (diff < 60_000) return '刚刚'
  if (diff < 3600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86400_000) return `${Math.floor(diff / 3600_000)} 小时前`
  return `${Math.floor(diff / 86400_000)} 天前`
}

onMounted(() => {
  renderChart([])
  loadDevices()
  timer = setInterval(loadDevices, 5000)
  if (chartRef.value) {
    resizeObserver = new ResizeObserver(() => chart?.resize())
    resizeObserver.observe(chartRef.value)
  }
})
onUnmounted(() => {
  if (timer) clearInterval(timer)
  historyRequestId += 1
  resizeObserver?.disconnect()
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.monitor {
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
  gap: 6px;
  font-size: 12.5px;
  color: var(--text-secondary);
}
.panel { position: relative; overflow: hidden; border-radius: var(--radius-lg); }
.panel-head { min-height: 56px; }
.panel-title { letter-spacing: .02em; }

.search-meta {
  gap: 12px;
}
.search {
  width: 220px;
}
.count {
  color: var(--text-muted);
}

.table-wrap {
  padding: 8px 18px 18px;
}
.mobile-device-list {
  display: none;
}

.device-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}
.device-avatar {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border-radius: 3px;
  flex: none;
}
.device-avatar.on {
  background: var(--info-dim);
  color: var(--info);
}
.device-avatar.off {
  background: var(--danger-dim);
  color: var(--danger);
}
.device-avatar :deep(.el-icon) {
  font-size: 15px;
}
.device-code {
  font-size: 13px;
  color: var(--text-primary);
  font-weight: 550;
}
.device-cell::after { content: '实时'; margin-left: 2px; padding: 1px 5px; border: 1px solid rgba(23, 122, 80, .28); border-radius: 2px; color: var(--ok); font-size: 9px; letter-spacing: .04em; }
.loc {
  color: var(--text-secondary);
}
.lux {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--accent-bright);
}
.lux-unit {
  margin-left: 4px;
  font-size: 11.5px;
  color: var(--text-muted);
}
.last {
  color: var(--text-muted);
  font-size: 12.5px;
}
.table-empty {
  padding: 32px 0;
  color: var(--text-muted);
  font-size: 12.5px;
}

.trend-head {
  flex-wrap: wrap;
  gap: 12px;
}
.trend-controls {
  display: flex;
  align-items: center;
  gap: 14px;
}
.live-lux {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  padding: 5px 12px;
  border-radius: 3px;
  background: var(--accent-dim);
  border: 1px solid rgba(250, 152, 25, 0.3);
}
.live-label {
  font-size: 11.5px;
  color: var(--text-muted);
}
.live-value {
  font-size: 16px;
  font-weight: 650;
  color: var(--accent-bright);
}
.live-unit {
  font-size: 11.5px;
  color: var(--text-muted);
}
.live-ts {
  font-size: 11px;
  color: var(--text-muted);
}
.live-unavailable {
  color: var(--warn);
  font-size: 11.5px;
}

.range-switch {
  display: inline-flex;
  gap: 2px;
  padding: 2px;
  background: var(--bg-surface-2);
  border: 1px solid var(--border-subtle);
  border-radius: 3px;
}
.range-switch button {
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-family: inherit;
  font-size: 12px;
  line-height: 1;
  padding: 6px 10px;
  border-radius: 2px;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}
.range-switch button:hover {
  color: var(--text-secondary);
}
.range-switch button.active {
  background: var(--accent-dim);
  color: var(--accent-bright);
}

.chart {
  height: 340px;
}
.chart-body {
  position: relative;
}
.chart-overlay {
  position: absolute;
  inset: 0;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 9px;
  color: var(--text-muted);
  background: rgba(255, 255, 255, .9);
  font-size: 12.5px;
}

@media (max-width: 700px) {
  .trend-controls {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  .search {
    width: 150px;
  }
  .chart {
    height: 260px;
  }
  .desktop-device-table {
    display: none;
  }
  .mobile-device-list {
    padding: 6px 10px 10px;
    display: grid;
    gap: 7px;
  }
  .mobile-device-row {
    width: 100%;
    min-height: 88px;
    padding: 10px 11px;
    display: grid;
    gap: 5px;
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-md);
    color: var(--text-secondary);
    background: var(--bg-surface-2);
    cursor: pointer;
    text-align: left;
  }
  .mobile-device-row:hover {
    border-color: var(--border);
  }
  .mobile-device-row.selected {
    border-color: rgba(250, 152, 25, .38);
    background: var(--accent-dim);
  }
  .mobile-device-main,
  .mobile-device-meta {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
  }
  .mobile-device-location {
    overflow: hidden;
    color: var(--text-secondary);
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .mobile-device-meta {
    color: var(--text-muted);
    font-size: 10.5px;
  }
  .mobile-device-meta strong {
    color: var(--accent-bright);
    font-weight: 650;
  }
  .mobile-device-meta small {
    margin-left: 2px;
    font-size: 9px;
  }
}
@media (max-width: 560px) {
  .page-intro {
    flex-direction: column;
    align-items: flex-start;
  }
  .panel-head {
    align-items: flex-start;
    flex-direction: column;
  }
  .search-meta {
    width: 100%;
  }
  .search {
    width: auto;
    flex: 1;
  }
}
</style>
