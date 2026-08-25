<template>
  <div class="workspace-page command-center" :aria-busy="refreshing">
    <header class="page-intro">
      <div>
        <h1 class="page-title">运行概览</h1>
        <p class="page-desc">全网设备状态、空间分布与环境照度汇总</p>
      </div>
      <div class="page-status" aria-live="polite">
        <span class="status-dot" :class="dataError ? 'idle' : 'online'"></span>
        <span>{{ refreshText }}</span>
        <el-tooltip content="立即刷新概览数据" placement="bottom">
          <button
            class="refresh-button"
            type="button"
            aria-label="立即刷新概览数据"
            :disabled="refreshing"
            @click="refresh(true)"
          >
            <el-icon :class="{ spinning: refreshing }" aria-hidden="true"><Refresh /></el-icon>
          </button>
        </el-tooltip>
      </div>
    </header>

    <div v-if="dataError" class="async-banner" role="alert">
      <el-icon aria-hidden="true"><WarningFilled /></el-icon>
      <span>{{ dataError }}</span>
      <button class="inline-action" type="button" :disabled="refreshing" @click="refresh(true)">重试</button>
    </div>

    <section class="command-grid" aria-label="设备概览与地图">
      <div class="metrics-rail">
        <article
          v-for="stat in statCards"
          :key="stat.key"
          class="metric stat-card"
          :class="[stat.tone, { accent: stat.key === 'avg' }]"
        >
          <div class="metric-head">
            <span class="metric-icon" aria-hidden="true">
              <el-icon><component :is="stat.icon" /></el-icon>
            </span>
            <span class="stat-label">{{ stat.label }}</span>
          </div>
          <div class="stat-value num">
            {{ initialLoading ? '—' : stat.value }}<span v-if="stat.unit" class="unit">{{ stat.unit }}</span>
          </div>
          <div class="stat-sub">{{ initialLoading ? '正在加载实时数据' : stat.sub }}</div>
        </article>
      </div>

      <section class="map-panel" aria-labelledby="map-title">
        <div ref="mapRef" class="amap-canvas" aria-label="路灯设备分布地图"></div>
        <div v-if="mapLoading" class="map-state" role="status">
          <el-icon class="state-icon"><Loading /></el-icon>
          <span>正在加载地图</span>
        </div>
        <div v-else-if="mapError" class="map-state map-error" role="alert">
          <el-icon class="state-icon"><LocationInformation /></el-icon>
          <strong>地图暂不可用</strong>
          <span>{{ mapError }}</span>
          <button class="inline-action" type="button" @click="initMap">重新加载</button>
        </div>

        <div class="map-heading">
          <strong id="map-title">设备空间分布</strong>
          <span>点击点位查看设备与照度</span>
        </div>

        <div class="map-tools" aria-label="地图缩放工具">
          <el-tooltip content="回到设备中心" placement="left">
            <button type="button" aria-label="回到设备中心" @click="resetMap">
              <el-icon aria-hidden="true"><Aim /></el-icon>
            </button>
          </el-tooltip>
          <el-tooltip content="放大地图" placement="left">
            <button type="button" aria-label="放大地图" @click="zoomMap(1)">
              <el-icon aria-hidden="true"><Plus /></el-icon>
            </button>
          </el-tooltip>
          <el-tooltip content="缩小地图" placement="left">
            <button type="button" aria-label="缩小地图" @click="zoomMap(-1)">
              <el-icon aria-hidden="true"><Minus /></el-icon>
            </button>
          </el-tooltip>
        </div>

        <div class="map-legend" aria-label="地图设备筛选">
          <button
            v-for="item in mapFilters"
            :key="item.key"
            type="button"
            :class="{ selected: mapFilter === item.key }"
            :aria-pressed="mapFilter === item.key"
            @click="mapFilter = item.key"
          >
            <span class="legend-shape" :class="item.key" aria-hidden="true"></span>
            <span>{{ item.label }}</span>
            <strong class="num">{{ item.count }}</strong>
          </button>
        </div>
      </section>
    </section>

    <section class="lower-grid">
      <section class="panel trend-panel" aria-labelledby="trend-title">
        <div class="panel-head trend-head">
          <div>
            <div id="trend-title" class="panel-title">光照趋势</div>
            <div class="panel-caption">{{ currentCode || '未选择设备' }} · 环境照度（Lux）</div>
          </div>
          <div class="trend-actions">
            <el-select
              v-model="currentCode"
              size="small"
              class="device-select"
              aria-label="选择趋势设备"
              :disabled="!devices.length"
              @change="onChangeDevice"
            >
              <el-option v-for="device in devices" :key="device.code" :label="device.code" :value="device.code" />
            </el-select>
            <div class="range-switch" aria-label="趋势时间范围">
              <button
                v-for="item in ranges"
                :key="item.value"
                type="button"
                :class="{ active: range === item.value }"
                :aria-pressed="range === item.value"
                @click="setRange(item.value)"
              >
                {{ item.label }}
              </button>
            </div>
          </div>
        </div>
        <div class="panel-body chart-wrap">
          <div ref="chartRef" class="chart"></div>
          <div v-if="chartLoading && !hasChartData" class="chart-overlay" role="status">正在加载趋势数据</div>
          <div v-else-if="chartError && !hasChartData" class="chart-overlay chart-error" role="alert">
            <span>{{ chartError }}</span>
            <button class="inline-action" type="button" @click="loadTrend">重试</button>
          </div>
        </div>
      </section>

      <section class="panel device-panel" aria-labelledby="device-state-title">
        <div class="panel-head">
          <div>
            <div id="device-state-title" class="panel-title">设备在线状态</div>
            <div class="panel-caption">最近一次设备上报</div>
          </div>
          <router-link to="/monitor" class="more-link">查看全部<span aria-hidden="true"> →</span></router-link>
        </div>
        <div class="device-table">
          <div class="device-head" aria-hidden="true">
            <span>设备</span><span>位置</span><span>照度</span><span>状态</span>
          </div>
          <div v-for="device in devices.slice(0, 6)" :key="device.code" class="device-row">
            <span class="device-name">
              <span class="status-dot" :class="device.status === 'ONLINE' ? 'online' : 'offline'"></span>
              <strong class="num">{{ device.code }}</strong>
            </span>
            <span class="location" :title="device.location">{{ device.location || '未标注' }}</span>
            <span class="lux num">{{ device.latestLux ?? '—' }}<small v-if="device.latestLux !== null"> Lux</small></span>
            <span class="device-status" :class="device.status === 'ONLINE' ? 'online' : 'offline'">
              {{ device.status === 'ONLINE' ? '在线' : '离线' }}
            </span>
          </div>
          <div v-if="initialLoading && !devices.length" class="empty-state" role="status">正在加载设备列表</div>
          <div v-else-if="!devices.length" class="empty-state">暂无接入设备</div>
        </div>
      </section>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import {
  Aim,
  CircleCheck,
  CircleClose,
  Loading,
  LocationInformation,
  Minus,
  Monitor,
  Plus,
  Refresh,
  Sunny,
  WarningFilled,
} from '@element-plus/icons-vue'
import { getHistory, getOverview, listDevices } from '../api/device'
import type { DashboardOverview, DeviceVO, LightPoint } from '../api/device'
import { initLuxChart, luxLineOption } from '../utils/chart'
import type { ChartInstance } from '../utils/chart'
import { loadAMap } from '../utils/amap'

type RangeKey = '24h' | '7d' | '30d'
type MapFilter = 'online' | 'offline' | 'all'

interface MapInstance {
  add(items: MarkerInstance[]): void
  destroy(): void
  getZoom(): number
  remove(items: MarkerInstance[]): void
  setZoom(zoom: number): void
  setZoomAndCenter(zoom: number, center: number[]): void
}

interface MarkerInstance {
  getPosition(): unknown
  on(event: 'click', handler: () => void): void
}

interface InfoWindowInstance {
  open(map: MapInstance, position: unknown): void
  setContent(content: HTMLElement): void
}

interface AMapNamespace {
  InfoWindow: new (options: Record<string, unknown>) => InfoWindowInstance
  Map: new (element: HTMLDivElement, options: Record<string, unknown>) => MapInstance
  Marker: new (options: Record<string, unknown>) => MarkerInstance
  Pixel: new (x: number, y: number) => unknown
}

const overview = ref<DashboardOverview>({ totalDevices: 0, onlineCount: 0, offlineCount: 0, avgLux: 0 })
const devices = ref<DeviceVO[]>([])
const currentCode = ref('SL-001')
const range = ref<RangeKey>('24h')
const mapFilter = ref<MapFilter>('all')
const chartRef = ref<HTMLDivElement>()
const mapRef = ref<HTMLDivElement>()
const initialLoading = ref(true)
const refreshing = ref(false)
const chartLoading = ref(false)
const hasChartData = ref(false)
const dataError = ref('')
const chartError = ref('')
const mapLoading = ref(true)
const mapError = ref('')
const lastUpdated = ref<number | null>(null)

let chart: ChartInstance | null = null
let map: MapInstance | null = null
let amap: AMapNamespace | null = null
let markers: MarkerInstance[] = []
let infoWindow: InfoWindowInstance | null = null
let timer: ReturnType<typeof setInterval> | null = null
let resizeObserver: ResizeObserver | null = null
let trendRequestId = 0

const ranges: Array<{ value: RangeKey; label: string }> = [
  { value: '24h', label: '24 小时' },
  { value: '7d', label: '近 7 天' },
  { value: '30d', label: '近 30 天' },
]

const onlineCount = computed(() => devices.value.filter((device) => device.status === 'ONLINE').length)
const offlineCount = computed(() => devices.value.filter((device) => device.status === 'OFFLINE').length)
const onlineRate = computed(() => devices.value.length ? Math.round((onlineCount.value / devices.value.length) * 100) : 0)
const rangeMs = computed(() => ({ '24h': 86_400_000, '7d': 604_800_000, '30d': 2_592_000_000 })[range.value])
const refreshText = computed(() => {
  if (refreshing.value) return '正在刷新'
  if (!lastUpdated.value) return '每 5 秒自动刷新'
  return `更新于 ${new Date(lastUpdated.value).toLocaleTimeString('zh-CN', { hour12: false })}`
})
const mapFilters = computed(() => [
  { key: 'all' as const, label: '全部', count: devices.value.length },
  { key: 'online' as const, label: '在线', count: onlineCount.value },
  { key: 'offline' as const, label: '离线', count: offlineCount.value },
])
const statCards = computed(() => [
  { key: 'total', label: '接入设备', value: overview.value.totalDevices, unit: '', sub: '全网设备总数', icon: Monitor, tone: 'neutral' },
  { key: 'online', label: '在线设备', value: overview.value.onlineCount, unit: '', sub: `在线率 ${onlineRate.value}%`, icon: CircleCheck, tone: 'success' },
  { key: 'offline', label: '离线设备', value: overview.value.offlineCount, unit: '', sub: overview.value.offlineCount ? '需要人工排查' : '全部运行正常', icon: CircleClose, tone: 'danger' },
  { key: 'avg', label: '平均照度', value: Math.round(overview.value.avgLux), unit: 'Lux', sub: '当前全网均值', icon: Sunny, tone: 'accent' },
])

async function refresh(manual = false) {
  if (refreshing.value) return
  refreshing.value = true
  const config = { silent: true }
  try {
    const [overviewResult, devicesResult] = await Promise.allSettled([
      getOverview(config),
      listDevices(config),
    ])

    let succeeded = false
    const failedRegions: string[] = []
    if (overviewResult.status === 'fulfilled') {
      overview.value = overviewResult.value
      succeeded = true
    } else {
      failedRegions.push('汇总指标')
    }
    if (devicesResult.status === 'fulfilled') {
      devices.value = devicesResult.value
      succeeded = true
      if (devices.value.length && !devices.value.some((device) => device.code === currentCode.value)) {
        currentCode.value = devices.value[0].code
      }
    } else {
      failedRegions.push('设备列表')
    }

    dataError.value = failedRegions.length
      ? succeeded
        ? `${failedRegions.join('、')}刷新失败，已保留其余最新数据。`
        : manual
          ? '数据刷新失败，请检查网络或后端服务后重试。'
          : '暂时无法获取运行数据，页面会继续自动重试。'
      : ''

    if (succeeded) lastUpdated.value = Date.now()
    if (devices.value.length) await loadTrend()
    else renderChart([])
  } finally {
    initialLoading.value = false
    refreshing.value = false
  }
}

async function loadTrend() {
  const code = currentCode.value
  if (!code) {
    renderChart([])
    return
  }
  const requestId = ++trendRequestId
  const now = Date.now()
  chartLoading.value = true
  try {
    const history = await getHistory(code, now - rangeMs.value, now, { silent: true })
    if (requestId !== trendRequestId || code !== currentCode.value) return
    chartError.value = ''
    renderChart(history.points)
  } catch {
    if (requestId !== trendRequestId) return
    chartError.value = '趋势数据加载失败'
    if (!hasChartData.value) renderChart([])
  } finally {
    if (requestId === trendRequestId) chartLoading.value = false
  }
}

function renderChart(points: LightPoint[]) {
  if (!chartRef.value) return
  if (!chart) chart = initLuxChart(chartRef.value)
  hasChartData.value = points.length > 0
  chart.setOption(luxLineOption(points.map((point) => point.ts), points.map((point) => point.lux)), true)
}

function campusCenter() {
  const values = (import.meta.env.VITE_AMAP_CENTER || '116.397428,39.90923').split(',').map(Number)
  return values.length === 2 && values.every(Number.isFinite) ? values : [116.397428, 39.90923]
}

function devicePosition(device: DeviceVO, index: number) {
  if (device.longitude !== null && device.latitude !== null) return [device.longitude, device.latitude]
  const [lng, lat] = campusCenter()
  const offsets = [[0, 0], [0.0015, 0.0007], [-0.0012, 0.001], [0.0008, -0.0012], [-0.0016, -0.0009], [0.002, -0.0016]]
  const [x, y] = offsets[index % offsets.length]
  return [lng + x, lat + y]
}

function createMarkerContent(device: DeviceVO, online: boolean) {
  const element = document.createElement('span')
  element.className = `lamp-marker ${online ? 'is-online' : 'is-offline'}`
  element.title = `${device.code} · ${online ? '在线' : '离线'}`
  element.setAttribute('aria-hidden', 'true')
  return element
}

function createInfoContent(device: DeviceVO, online: boolean) {
  const root = document.createElement('div')
  root.className = 'map-info'
  const title = document.createElement('strong')
  title.textContent = device.code
  const location = document.createElement('span')
  location.textContent = device.location || '位置未标注'
  const state = document.createElement('span')
  state.textContent = `${online ? '在线' : '离线'} · ${device.latestLux === null ? '暂无照度' : `${device.latestLux} Lux`}`
  root.append(title, location, state)
  return root
}

function updateMarkers() {
  if (!map || !amap) return
  const mapInstance = map
  const api = amap
  mapInstance.remove(markers)
  const shown = devices.value.filter((device) => (
    mapFilter.value === 'all'
    || (mapFilter.value === 'online' ? device.status === 'ONLINE' : device.status === 'OFFLINE')
  ))
  markers = shown.map((device) => {
    const index = devices.value.findIndex((item) => item.code === device.code)
    const online = device.status === 'ONLINE'
    const marker = new api.Marker({
      position: devicePosition(device, index),
      offset: new api.Pixel(-13, -13),
      content: createMarkerContent(device, online),
    })
    marker.on('click', () => {
      currentCode.value = device.code
      loadTrend()
      if (map && infoWindow) {
        infoWindow.setContent(createInfoContent(device, online))
        infoWindow.open(map, marker.getPosition())
      }
    })
    return marker
  })
  mapInstance.add(markers)
}

async function initMap() {
  mapError.value = ''
  mapLoading.value = true
  try {
    amap = await loadAMap() as AMapNamespace
    await nextTick()
    if (!mapRef.value) return
    map?.destroy()
    map = new amap.Map(mapRef.value, {
      viewMode: '2D',
      zoom: 16,
      center: campusCenter(),
    })
    infoWindow = new amap.InfoWindow({ offset: new amap.Pixel(0, -22), closeWhenClickMap: true })
    updateMarkers()
  } catch (error) {
    mapError.value = error instanceof Error
      ? `${error.message}。请检查地图密钥配置。`
      : '地图加载失败，请稍后重试。'
  } finally {
    mapLoading.value = false
  }
}

function resetMap() {
  map?.setZoomAndCenter(16, campusCenter())
}

function zoomMap(delta: number) {
  if (map) map.setZoom(map.getZoom() + delta)
}

function setRange(value: RangeKey) {
  if (range.value === value) return
  range.value = value
  loadTrend()
}

function onChangeDevice() {
  loadTrend()
}

watch([devices, mapFilter], updateMarkers)

onMounted(() => {
  renderChart([])
  refresh()
  initMap()
  timer = setInterval(refresh, 5_000)
  if (chartRef.value) {
    resizeObserver = new ResizeObserver(() => chart?.resize())
    resizeObserver.observe(chartRef.value)
  }
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  trendRequestId += 1
  resizeObserver?.disconnect()
  chart?.dispose()
  chart = null
  map?.destroy()
  map = null
})
</script>

<style scoped>
.command-grid {
  min-width: 0;
  min-height: 404px;
  display: grid;
  grid-template-columns: 232px minmax(0, 1fr);
  gap: 16px;
}

.metrics-rail {
  min-width: 0;
  display: grid;
  grid-template-rows: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.metric.stat-card {
  min-height: 0;
  padding: 15px 17px;
}

.metric-head {
  display: flex;
  align-items: center;
  gap: 9px;
}

.metric-icon {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  flex: none;
  border: 1px solid var(--border-subtle);
  border-radius: 12px 4px 12px 4px;
  color: var(--accent-bright);
  background: var(--accent-dim);
}

.metric.success .metric-icon {
  color: var(--ok);
  border-color: rgba(23, 122, 80, 0.2);
  background: var(--ok-dim);
}

.metric.danger .metric-icon {
  color: var(--danger);
  border-color: rgba(180, 35, 24, 0.2);
  background: var(--danger-dim);
}

.metric.accent .metric-icon {
  color: var(--accent-bright);
  border-color: rgba(76, 175, 79, 0.28);
  background: var(--accent-dim);
}

.metric .stat-value {
  min-height: 31px;
  margin-top: 8px;
  font-size: 27px;
}

.metric .stat-sub {
  margin-top: 4px;
}

.refresh-button {
  width: 30px;
  height: 30px;
  padding: 0;
  display: grid;
  place-items: center;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  background: var(--bg-surface);
  cursor: pointer;
}

.refresh-button:hover:not(:disabled) {
  color: var(--accent-bright);
  border-color: rgba(76, 175, 79, 0.36);
  background: var(--accent-dim);
}

.refresh-button:disabled {
  cursor: wait;
  opacity: 0.65;
}

.spinning {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.map-panel {
  position: relative;
  min-width: 0;
  min-height: 404px;
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: #263238;
  box-shadow: var(--shadow-soft);
}

.amap-canvas {
  position: absolute;
  inset: 0;
}

.map-state {
  position: absolute;
  z-index: 4;
  inset: 0;
  padding: 28px;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 9px;
  color: #ffffff;
  background: #263238;
  font-size: 12.5px;
  text-align: center;
}

.map-error span {
  max-width: 460px;
  color: #abbed1;
}

.state-icon {
  color: var(--accent);
  font-size: 22px;
}

.map-heading {
  position: absolute;
  z-index: 2;
  top: 14px;
  left: 14px;
  padding: 10px 13px;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--border-subtle);
  border-radius: 10px;
  color: var(--text-primary);
  background: rgba(255, 255, 255, 0.94);
  box-shadow: var(--shadow-soft);
}

.map-heading strong {
  font-size: 13px;
  font-weight: 650;
}

.map-heading span {
  margin-top: 2px;
  color: var(--text-muted);
  font-size: 10.5px;
}

.map-tools {
  position: absolute;
  z-index: 2;
  top: 14px;
  right: 14px;
  display: grid;
  overflow: hidden;
  border: 1px solid var(--border-subtle);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: var(--shadow-soft);
}

.map-tools button {
  width: 34px;
  height: 34px;
  padding: 0;
  display: grid;
  place-items: center;
  border: 0;
  border-bottom: 1px solid var(--border-subtle);
  color: var(--text-secondary);
  background: transparent;
  cursor: pointer;
}

.map-tools button:last-child {
  border-bottom: 0;
}

.map-tools button:hover {
  color: var(--accent-bright);
  background: var(--accent-dim);
}

.map-legend {
  position: absolute;
  z-index: 2;
  right: 14px;
  bottom: 14px;
  padding: 5px;
  display: grid;
  gap: 2px;
  border: 1px solid var(--border-subtle);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: var(--shadow-soft);
}

.map-legend button {
  min-width: 100px;
  min-height: 30px;
  padding: 4px 7px;
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  background: transparent;
  cursor: pointer;
  font-size: 11.5px;
  text-align: left;
}

.map-legend button:hover,
.map-legend button.selected {
  color: var(--accent-bright);
  border-color: rgba(76, 175, 79, 0.24);
  background: var(--accent-dim);
}

.map-legend strong {
  margin-left: auto;
  font-size: 11px;
}

.legend-shape {
  width: 8px;
  height: 8px;
  flex: none;
  border: 1px solid currentColor;
  border-radius: 50%;
  color: var(--text-secondary);
  background: currentColor;
}

.legend-shape.online {
  color: var(--ok);
}

.legend-shape.offline {
  color: var(--danger);
  border-radius: 2px;
}

.legend-shape.all {
  color: var(--accent);
}

.lower-grid {
  min-width: 0;
  display: grid;
  grid-template-columns: minmax(380px, 0.9fr) minmax(500px, 1.1fr);
  gap: 16px;
}

.trend-head,
.trend-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.trend-actions {
  justify-content: flex-end;
}

.device-select {
  width: 108px;
}

.range-switch {
  padding: 2px;
  display: inline-flex;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--bg-surface-2);
}

.range-switch button {
  min-height: 26px;
  padding: 4px 8px;
  border: 0;
  border-radius: var(--radius-sm);
  color: var(--text-muted);
  background: transparent;
  cursor: pointer;
  font-size: 11px;
}

.range-switch button:hover {
  color: var(--text-primary);
}

.range-switch button.active {
  color: var(--accent-bright);
  background: var(--accent-dim);
}

.chart-wrap {
  position: relative;
  padding: 6px 8px 3px;
}

.chart {
  width: 100%;
  height: 260px;
}

.chart-overlay {
  position: absolute;
  inset: 0;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 9px;
  color: var(--text-muted);
  background: rgba(255, 255, 255, 0.9);
  font-size: 12px;
}

.device-table {
  padding: 4px 14px 10px;
}

.device-head,
.device-row {
  min-width: 0;
  display: grid;
  grid-template-columns: 1.2fr 1fr 0.66fr 0.54fr;
  align-items: center;
  column-gap: 10px;
}

.device-head {
  height: 35px;
  border-bottom: 1px solid var(--border-subtle);
  color: var(--text-muted);
  font-size: 10.5px;
  letter-spacing: 0.03em;
}

.device-row {
  min-height: 38px;
  border-bottom: 1px solid var(--border-subtle);
  color: var(--text-secondary);
  font-size: 11.5px;
}

.device-row:last-child {
  border-bottom: 0;
}

.device-name {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-primary);
}

.device-name strong {
  overflow: hidden;
  font-weight: 580;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.location {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lux {
  color: var(--accent-bright);
  white-space: nowrap;
}

.lux small {
  color: var(--text-muted);
  font-family: var(--font-ui);
  font-size: 9px;
}

.device-status {
  font-size: 11px;
}

.device-status.online {
  color: var(--ok);
}

.device-status.offline {
  color: var(--danger);
}

.more-link {
  color: var(--text-secondary);
  font-size: 11.5px;
}

:deep(.lamp-marker) {
  width: 22px;
  height: 22px;
  display: block;
  box-sizing: border-box;
  border: 3px solid rgba(237, 245, 252, 0.95);
  border-radius: 50% 50% 50% 0;
  box-shadow: 0 2px 9px rgba(0, 0, 0, 0.5);
  transform: rotate(-45deg);
}

:deep(.lamp-marker.is-online) {
  background: var(--ok);
  box-shadow: 0 0 0 4px var(--ok-glow), 0 2px 9px rgba(0, 0, 0, 0.5);
}

:deep(.lamp-marker.is-offline) {
  background: var(--danger);
  box-shadow: 0 0 0 4px var(--danger-glow), 0 2px 9px rgba(0, 0, 0, 0.5);
}

:deep(.map-info) {
  min-width: 150px;
  display: grid;
  gap: 4px;
  color: #182635;
  font-size: 12px;
}

:deep(.map-info strong) {
  font-size: 13px;
}

:deep(.map-info span:last-child) {
  color: #5d7084;
}

@media (max-width: 1180px) {
  .command-grid {
    grid-template-columns: 1fr;
  }

  .metrics-rail {
    grid-template-columns: repeat(4, minmax(0, 1fr));
    grid-template-rows: none;
  }

  .metric.stat-card {
    min-height: 118px;
  }

  .map-panel {
    min-height: 360px;
  }

  .lower-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 700px) {
  .metrics-rail {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .metric.stat-card {
    min-height: 112px;
  }

  .map-panel {
    min-height: 320px;
  }

  .trend-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .trend-actions {
    width: 100%;
    justify-content: space-between;
  }

  .chart {
    height: 240px;
  }

  .device-head,
  .device-row {
    grid-template-columns: 1.2fr 0.7fr 0.55fr;
  }

  .device-head span:nth-child(2),
  .device-row .location {
    display: none;
  }
}

@media (max-width: 430px) {
  .map-heading span {
    display: none;
  }

  .map-legend button {
    min-width: 86px;
  }

  .trend-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .device-select {
    width: 100%;
  }

  .range-switch button {
    flex: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .spinning {
    animation: none;
  }
}
</style>
