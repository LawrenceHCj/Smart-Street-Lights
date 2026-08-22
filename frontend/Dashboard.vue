<template>
  <div class="dash">
    <div class="page-intro">
      <div>
        <h2 class="page-title">运行概览</h2>
        <p class="page-desc">全网设备状态与平均照度，数据实时刷新</p>
      </div>
      <div class="refresh">
        <span class="status-dot idle"></span>
        <span class="num">5s</span> 自动刷新
      </div>
    </div>

    <div class="stats">
      <div
        v-for="s in statCards"
        :key="s.key"
        class="stat-card"
        :class="{ accent: s.key === 'avg' }"
      >
        <div class="stat-label">
          <el-icon class="label-icon"><component :is="s.icon" /></el-icon>
          {{ s.label }}
        </div>
        <div class="stat-value">
          {{ s.value }}<span v-if="s.unit" class="unit">{{ s.unit }}</span>
        </div>
        <div class="stat-sub">{{ s.sub }}</div>
      </div>
    </div>

    <div class="dash-grid">
      <!-- F-03 / F-04 光照趋势 -->
      <div class="panel">
        <div class="panel-head trend-head">
          <div class="panel-title">光照趋势 · {{ currentCode }}</div>
          <div class="trend-controls">
            <el-select v-model="currentCode" size="small" class="dev-select" @change="onChangeDevice">
              <el-option
                v-for="d in devices"
                :key="d.code"
                :label="`${d.code} · ${d.location}`"
                :value="d.code"
              />
            </el-select>
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

      <!-- F-02 / F-04 设备在线状态 -->
      <div class="panel">
        <div class="panel-head">
          <div class="panel-title">设备在线状态</div>
          <div class="panel-meta">
            <router-link to="/monitor" class="more-link">查看全部 →</router-link>
          </div>
        </div>
        <div class="panel-body dev-list">
          <div class="dev-summary">
            <div class="dev-summary-bar">
              <div class="bar-online" :style="{ width: onlineRate + '%' }"></div>
            </div>
            <div class="dev-summary-text">
              <span><i class="dot on"></i>在线 {{ onlineCount }}</span>
              <span><i class="dot off"></i>离线 {{ offlineCount }}</span>
              <span class="rate num">{{ onlineRate }}%</span>
            </div>
          </div>
          <div v-for="d in devices.slice(0, 8)" :key="d.code" class="dev-row">
            <span class="status-dot" :class="d.status === 'ONLINE' ? 'online' : 'offline'"></span>
            <span class="dev-code num">{{ d.code }}</span>
            <span class="dev-loc">{{ d.location }}</span>
            <span class="dev-lux num">{{ d.latestLux == null ? '—' : d.latestLux }}</span>
          </div>
          <div v-if="!devices.length" class="dev-empty">暂无设备</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { Monitor, CircleCheck, CircleClose, Sunny } from '@element-plus/icons-vue'
import { getOverview, getHistory, listDevices } from '../api/device'
import type { DashboardOverview, DeviceVO, LightPoint } from '../api/device'
import { luxLineOption } from '../utils/chart'

const overview = ref<DashboardOverview>({ totalDevices: 0, onlineCount: 0, offlineCount: 0, avgLux: 0 })
const devices = ref<DeviceVO[]>([])
const currentCode = ref('SL-001')
const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null
let timer: ReturnType<typeof setInterval> | null = null
let refreshing = false

type RangeKey = '24h' | '7d' | '30d'
const range = ref<RangeKey>('24h')
const ranges: Array<{ value: RangeKey; label: string }> = [
  { value: '24h', label: '24 小时' },
  { value: '7d', label: '近 7 天' },
  { value: '30d', label: '近 30 天' },
]
const rangeMs = computed(() => {
  const h = 3600_000
  return range.value === '24h' ? 24 * h : range.value === '7d' ? 7 * 24 * h : 30 * 24 * h
})

const onlineCount = computed(() => devices.value.filter((d) => d.status === 'ONLINE').length)
const offlineCount = computed(() => devices.value.filter((d) => d.status === 'OFFLINE').length)
const onlineRate = computed(() =>
  devices.value.length ? Math.round((onlineCount.value / devices.value.length) * 100) : 0,
)

interface StatCard {
  key: string
  label: string
  icon: unknown
  value: number
  unit?: string
  sub: string
}

const statCards = computed<StatCard[]>(() => {
  const o = overview.value
  const rate = o.totalDevices ? Math.round((o.onlineCount / o.totalDevices) * 100) : 0
  return [
    { key: 'total', label: '接入设备', icon: Monitor, value: o.totalDevices, sub: '路灯设备总数' },
    { key: 'online', label: '在线设备', icon: CircleCheck, value: o.onlineCount, sub: `在线率 ${rate}%` },
    {
      key: 'offline',
      label: '离线设备',
      icon: CircleClose,
      value: o.offlineCount,
      sub: o.offlineCount ? '需人工排查' : '全部在线',
    },
    { key: 'avg', label: '平均照度', icon: Sunny, value: Math.round(o.avgLux), unit: 'Lux', sub: '当前全网均值' },
  ]
})

// 轮询 / 用户操作都走这里；immediate=true 时即使上一次请求在途也立即发起（用户主动切换设备/时间段）
async function refresh(immediate = false) {
  if (refreshing && !immediate) return
  refreshing = true
  const code = currentCode.value
  const now = Date.now()
  try {
    const [o, devs, hist] = await Promise.all([
      getOverview(),
      listDevices(),
      getHistory(code, now - rangeMs.value, now),
    ])
    overview.value = o
    devices.value = devs
    // 选中设备已不存在时回退到第一台；请求期间切换了设备则丢弃本次图表结果
    if (devs.length && !devs.some((d) => d.code === currentCode.value)) {
      currentCode.value = devs[0].code
    }
    if (code === currentCode.value) renderChart(hist.points)
  } catch {
    // 拦截器已统一提示
  } finally {
    refreshing = false
  }
}

function setRange(v: RangeKey) {
  range.value = v
  refresh(true)
}

function onChangeDevice() {
  refresh(true)
}

function renderChart(points: LightPoint[]) {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  chart.setOption(luxLineOption(points.map((p) => p.ts), points.map((p) => p.lux)))
}

function resize() {
  chart?.resize()
}

onMounted(() => {
  refresh(true)
  timer = setInterval(refresh, 5000)
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
.dash {
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
  gap: 8px;
  font-size: 12.5px;
  color: var(--text-secondary);
}

.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
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

.dash-grid {
  display: grid;
  grid-template-columns: 1.6fr 1fr;
  gap: 20px;
  align-items: start;
}

.trend-head {
  flex-wrap: wrap;
  gap: 12px;
}
.trend-controls {
  display: flex;
  align-items: center;
  gap: 10px;
}
.dev-select {
  width: 180px;
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
  height: 380px;
}

.more-link {
  color: var(--accent-bright);
  font-size: 12px;
  text-decoration: none;
}
.more-link:hover {
  text-decoration: underline;
}
.dev-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.dev-summary {
  margin-bottom: 6px;
}
.dev-summary-bar {
  height: 6px;
  border-radius: 999px;
  background: rgba(226, 98, 90, 0.25);
  overflow: hidden;
}
.bar-online {
  height: 100%;
  border-radius: 999px;
  background: var(--ok);
  transition: width 0.4s ease;
}
.dev-summary-text {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 8px;
}
.dev-summary-text .rate {
  margin-left: auto;
  color: var(--text-muted);
  font-weight: 600;
}
.dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  margin-right: 6px;
}
.dot.on {
  background: var(--ok);
}
.dot.off {
  background: var(--danger);
}
.dev-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid var(--border-subtle);
}
.dev-row:last-child {
  border-bottom: none;
}
.dev-code {
  width: 84px;
  flex: none;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text-primary);
}
.dev-loc {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--text-secondary);
}
.dev-lux {
  font-size: 12.5px;
  color: var(--accent-bright);
}
.dev-empty {
  padding: 24px 0;
  text-align: center;
  font-size: 12.5px;
  color: var(--text-muted);
}

@media (max-width: 1100px) {
  .dash-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 1000px) {
  .stats {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 700px) {
  .trend-controls {
    flex-direction: column;
    align-items: flex-start;
  }
}
@media (max-width: 560px) {
  .stats {
    grid-template-columns: 1fr;
  }
  .page-intro {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
