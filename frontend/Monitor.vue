<template>
  <div class="monitor">
    <div class="page-intro">
      <div>
        <h2 class="page-title">设备状态</h2>
        <p class="page-desc">选择设备查看实时照度与光照趋势</p>
      </div>
      <div class="refresh">
        <span class="status-dot idle"></span>
        <span class="num">{{ onlineCount }}</span> 在线 ·
        <span class="num">{{ offlineCount }}</span> 离线
      </div>
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
      <div class="table-wrap">
        <el-table :data="filteredDevices" highlight-current-row @current-change="onSelect">
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
              {{ keyword ? '未找到匹配设备' : '暂无设备 —— 设备上线后自动出现在这里' }}
            </div>
          </template>
        </el-table>
      </div>
    </div>

    <div class="panel">
      <div class="panel-head trend-head">
        <div class="panel-title">历史光照趋势 · {{ currentCode }}</div>
        <div class="trend-controls">
          <div v-if="liveLux.lux != null" class="live-lux">
            <span class="live-label">当前照度</span>
            <span class="live-value num">{{ liveLux.lux }}</span>
            <span class="live-unit">Lux</span>
            <span class="live-ts num">· {{ liveTime }}</span>
          </div>
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
      <div class="panel-body">
        <div ref="chartRef" class="chart"></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { ReadingLamp, Search } from '@element-plus/icons-vue'
import { listDevices, getHistory, getCurrentLight } from '../api/device'
import type { DeviceVO, LightPoint } from '../api/device'
import { luxLineOption } from '../utils/chart'

const devices = ref<DeviceVO[]>([])
const currentCode = ref('SL-001')
const keyword = ref('')
const liveLux = ref<{ lux: number | null; ts: number | null }>({ lux: null, ts: null })
const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null
let timer: ReturnType<typeof setInterval> | null = null
let loading = false

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

async function loadDevices() {
  if (loading) return
  loading = true
  try {
    devices.value = await listDevices()
    // 轮询时不打断用户当前选中；仅当选中设备已不存在时回退到第一台
    if (devices.value.length && !devices.value.some((d) => d.code === currentCode.value)) {
      currentCode.value = devices.value[0].code
    }
    await Promise.all([loadHistory(), loadLiveLight()])
  } catch {
    // 拦截器已统一提示
  } finally {
    loading = false
  }
}

async function loadHistory() {
  const code = currentCode.value
  const now = Date.now()
  const h = 3600_000
  const start = now - (range.value === '24h' ? 24 * h : 7 * 24 * h)
  const hist = await getHistory(code, start, now)
  // 过期检查：期间用户切换了设备则丢弃本次结果，避免旧数据覆盖新图表
  if (code === currentCode.value) renderChart(hist.points)
}

async function loadLiveLight() {
  const code = currentCode.value
  if (!devices.value.some((d) => d.code === code)) return // 编号未确认存在时跳过，避免"设备不存在"提示
  try {
    const r = await getCurrentLight(code)
    if (code === currentCode.value) liveLux.value = r
  } catch {
    // 拦截器已统一提示
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
  if (!chart) chart = echarts.init(chartRef.value)
  chart.setOption(luxLineOption(points.map((p) => p.ts), points.map((p) => p.lux)))
}

function timeAgo(ts: number | null) {
  if (!ts) return '—'
  const diff = Date.now() - ts
  if (diff < 60_000) return '刚刚'
  if (diff < 3600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86400_000) return `${Math.floor(diff / 3600_000)} 小时前`
  return `${Math.floor(diff / 86400_000)} 天前`
}

function resize() {
  chart?.resize()
}

onMounted(() => {
  loadDevices()
  timer = setInterval(loadDevices, 5000)
  window.addEventListener('resize', resize)
})
onUnmounted(() => {
  if (timer) clearInterval(timer)
  window.removeEventListener('resize', resize)
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.monitor {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.page-intro {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}
.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 650;
  letter-spacing: 0.01em;
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
  padding: 6px 16px 16px;
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
  border-radius: 9px;
  flex: none;
}
.device-avatar.on {
  background: var(--accent-dim);
  color: var(--accent-bright);
}
.device-avatar.off {
  background: rgba(226, 98, 90, 0.12);
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
  border-radius: 9px;
  background: var(--accent-dim);
  border: 1px solid rgba(223, 179, 79, 0.22);
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

.range-switch {
  display: inline-flex;
  gap: 2px;
  padding: 2px;
  background: var(--bg-surface-2);
  border: 1px solid var(--border-subtle);
  border-radius: 9px;
}
.range-switch button {
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-family: inherit;
  font-size: 12px;
  line-height: 1;
  padding: 6px 10px;
  border-radius: 7px;
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
  height: 320px;
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
}
@media (max-width: 560px) {
  .page-intro {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
