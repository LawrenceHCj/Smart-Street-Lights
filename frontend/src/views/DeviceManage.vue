<template>
  <div class="workspace-page dev-manage">
    <div class="page-intro">
      <div>
        <h1 class="page-title">设备管理</h1>
        <p class="page-desc">统一管理路灯设备：添加、查看、解绑</p>
      </div>
      <div class="refresh">
        <el-button type="primary" :icon="Plus" :disabled="!ready" @click="openAdd">添加设备</el-button>
      </div>
    </div>

    <NotReadyBanner
      v-if="!ready"
      text="设备管理接口当前不可用，添加与解绑操作已暂停；设备列表仍可继续查看。"
    />

    <div v-if="healthLoadFailed" class="health-warning" role="status">
      <el-icon><Warning /></el-icon>
      <span>健康报告暂未载入，设备基础信息不受影响。</span>
      <el-button text type="primary" @click="loadHealth">重试</el-button>
    </div>

    <div class="panel">
      <div class="panel-head">
        <div class="panel-title">路灯设备台账</div>
        <div class="panel-meta">{{ devices.length }} 台设备</div>
      </div>
      <div class="table-wrap">
        <div class="device-search">
          <el-input v-model="deviceQuery" placeholder="查找设备编号或位置" clearable :prefix-icon="Search" style="width: 250px" />
          <span v-if="deviceQuery.trim()" class="search-hits">匹配 {{ filteredDevices.length }} 台</span>
        </div>
        <el-table :data="filteredDevices">
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
          <el-table-column prop="location" label="位置" min-width="180">
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
          <el-table-column label="当前照度" width="120" align="right">
            <template #default="{ row }">
              <span class="lux num">{{ row.latestLux == null ? '—' : row.latestLux }}</span>
              <span v-if="row.latestLux != null" class="lux-unit">Lux</span>
            </template>
          </el-table-column>
          <el-table-column label="健康评分" width="132" align="right">
            <template #default="{ row }">
              <button
                class="health-trigger"
                :class="healthTone(healthByDevice[row.code]?.healthScore)"
                type="button"
                :aria-label="`${row.code} 健康报告：${healthText(healthByDevice[row.code])}`"
                @click="openHealth(row)"
              >
                <template v-if="healthByDevice[row.code]">
                  <span class="health-score num">{{ healthByDevice[row.code].healthScore }}</span>
                  <span>{{ healthLabel(healthByDevice[row.code].healthScore) }}</span>
                </template>
                <template v-else>
                  <span>待巡检</span>
                </template>
              </button>
            </template>
          </el-table-column>
          <el-table-column label="最后在线" width="140" align="right">
            <template #default="{ row }">
              <span class="last num">{{ time(row.lastSeen) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" align="right" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click="openHealth(row)">报告</el-button>
              <el-button
                size="small"
                text
                type="danger"
                :disabled="!ready"
                @click="onUnbind(row)"
              >
                解绑
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <div class="table-empty">暂无接入设备</div>
          </template>
        </el-table>
      </div>
    </div>

    <el-drawer
      v-model="healthVisible"
      :title="`${selectedCode} · 健康报告`"
      size="min(480px, 100%)"
      class="health-drawer"
    >
      <div class="health-detail" aria-live="polite">
        <div v-if="healthLoading" class="health-state">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>正在读取健康报告…</span>
        </div>

        <template v-else-if="selectedReport">
          <section class="health-overview" :class="healthTone(selectedReport.healthScore)" aria-labelledby="health-overview-title">
            <div>
              <div id="health-overview-title" class="section-kicker">综合健康分</div>
              <div class="health-total num">{{ selectedReport.healthScore }}<span>/ 100</span></div>
              <div class="health-grade">{{ healthLabel(selectedReport.healthScore) }}</div>
            </div>
            <div class="report-time">
              <span>报告生成</span>
              <strong class="num">{{ reportTime(selectedReport.createdAt) }}</strong>
            </div>
          </section>

          <section class="report-section" aria-labelledby="telemetry-title">
            <div class="report-section-head">
              <div>
                <div class="section-kicker">评分数据</div>
                <h2 id="telemetry-title">采集指标</h2>
              </div>
              <span class="telemetry-time num">{{ telemetryTime(selectedReport.telemetry?.collectedAt) }}</span>
            </div>
            <div v-if="selectedReport.telemetry" class="telemetry-grid">
              <div v-for="item in telemetryItems(selectedReport.telemetry)" :key="item.label" class="telemetry-item">
                <span>{{ item.label }}</span>
                <strong class="num">{{ item.value }}<small v-if="item.unit"> {{ item.unit }}</small></strong>
              </div>
            </div>
            <div v-else class="telemetry-empty">该历史报告生成于采集快照功能启用前，暂无指标明细。</div>
          </section>

          <section class="report-section" aria-labelledby="anomaly-title">
            <div class="report-section-head">
              <div>
                <div class="section-kicker">诊断依据</div>
                <h2 id="anomaly-title">异常指标</h2>
              </div>
              <span class="anomaly-count num">{{ selectedReport.anomalies.length }} 项</span>
            </div>
            <ul v-if="selectedReport.anomalies.length" class="anomaly-list">
              <li v-for="(item, index) in selectedReport.anomalies" :key="`${item.issue}-${index}`">
                <div class="anomaly-main">
                  <strong>{{ item.issue }}</strong>
                  <span class="deduct num">-{{ item.deduct }}</span>
                </div>
                <p>{{ item.reason }}</p>
              </li>
            </ul>
            <div v-else class="all-clear">
              <el-icon><CircleCheck /></el-icon>
              <div><strong>本次巡检未发现异常</strong><span>设备关键电气指标均在规则阈值内。</span></div>
            </div>
          </section>

          <section class="report-section" aria-labelledby="history-title">
            <div class="report-section-head">
              <div>
                <div class="section-kicker">近一周 · 每 4 小时</div>
                <h2 id="history-title">检测记录</h2>
              </div>
              <span class="anomaly-count num">{{ filteredHealthHistory.length }} 条</span>
            </div>
            <div class="history-filter">
              <el-date-picker
                v-model="histRange"
                type="datetimerange"
                range-separator="至"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                :disabled-date="disableFutureDate"
                :default-time="histDefaultTime"
                size="small"
                style="width: 340px"
              />
              <el-button size="small" type="primary" @click="applyHistRange">查询</el-button>
              <el-button v-if="histApplied" size="small" text @click="resetHistRange">重置</el-button>
            </div>
            <ol class="history-list">
              <li v-for="report in filteredHealthHistory" :key="report.id ?? report.createdAt">
                <span class="num">{{ reportTime(report.createdAt) }}</span>
                <span class="history-grade" :class="healthTone(report.healthScore)">{{ healthLabel(report.healthScore) }}</span>
                <strong class="num">{{ report.healthScore }}</strong>
              </li>
            </ol>
            <div v-if="!filteredHealthHistory.length" class="history-empty">
              {{ histApplied ? '该时间段内暂无检测记录' : '暂无检测记录' }}
            </div>
          </section>
        </template>

        <div v-else class="health-state empty">
          <el-icon><DataAnalysis /></el-icon>
          <strong>{{ healthDetailError ? '健康报告读取失败' : '尚无健康报告' }}</strong>
          <span>{{ healthDetailError ? '请重试或稍后再查看。' : '立即执行一次体检即可生成首份评分。' }}</span>
        </div>
      </div>

      <template #footer>
        <div class="drawer-actions">
          <span>{{ selectedDeviceOnline ? '基于设备最新遥测数据计算' : '设备离线，无法进行健康评分' }}</span>
          <el-button :icon="Refresh" type="primary" :loading="evaluating" :disabled="!selectedDeviceOnline" @click="runEvaluation">立即体检</el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="addVisible" title="添加路灯设备" width="min(760px, calc(100vw - 32px))" class="device-dialog">
      <el-form label-position="top" @submit.prevent="submitAdd">
        <el-form-item label="设备编号" required>
          <el-input v-model="form.code" placeholder="如 SL-020" :prefix-icon="ReadingLamp" />
        </el-form-item>
        <el-form-item label="安装位置">
          <el-input v-model="form.location" placeholder="如 建设路 8 号" />
        </el-form-item>
        <fieldset class="location-picker">
          <legend>地图选点 <span>必填</span></legend>
          <p class="picker-hint">点击地图确定安装点，坐标会自动填写。也可使用键盘在下方精确调整经纬度。</p>
          <div class="picker-map-wrap">
            <div
              ref="pickerMapRef"
              class="picker-map"
              role="region"
              aria-label="设备安装位置地图；鼠标点击地图选点，键盘用户请使用下方经纬度输入框"
            ></div>
            <div v-if="pickerMapLoading" class="picker-map-state" role="status">
              <el-icon class="is-loading" aria-hidden="true"><Loading /></el-icon>
              <span>正在加载地图…</span>
            </div>
            <div v-else-if="pickerMapError" class="picker-map-state picker-map-error" role="alert">
              <el-icon aria-hidden="true"><LocationInformation /></el-icon>
              <strong>地图暂不可用</strong>
              <span>{{ pickerMapError }}</span>
              <el-button size="small" text type="primary" @click="initPickerMap">重新加载</el-button>
            </div>
            <div v-if="hasSelectedPoint" class="picker-selection" role="status" aria-live="polite">
              <el-icon aria-hidden="true"><LocationInformation /></el-icon>
              <span>已选位置</span>
              <strong class="num">{{ form.longitude?.toFixed(6) }}, {{ form.latitude?.toFixed(6) }}</strong>
            </div>
          </div>
        </fieldset>
        <div class="coordinate-grid" aria-describedby="coordinate-help">
          <el-form-item label="经度" required>
            <el-input-number v-model="form.longitude" :min="-180" :max="180" :precision="6" :step="0.000001" controls-position="right" placeholder="如 106.298038" />
          </el-form-item>
          <el-form-item label="纬度" required>
            <el-input-number v-model="form.latitude" :min="-90" :max="90" :precision="6" :step="0.000001" controls-position="right" placeholder="如 29.593390" />
          </el-form-item>
        </div>
        <p id="coordinate-help" class="coordinate-help">坐标采用 GCJ-02。地图不可用或需要精确微调时，可直接填写经纬度。</p>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :loading="adding" @click="submitAdd">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { CircleCheck, DataAnalysis, Loading, LocationInformation, Plus, ReadingLamp, Refresh, Search, Warning } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, ElDatePicker } from 'element-plus'
import {
  evaluateDeviceHealth,
  getDeviceHealthHistory,
  listDevices,
  listLatestDeviceHealth,
} from '../api/device'
import type { DeviceHealthReport, DeviceVO, HealthTelemetry } from '../api/device'
import { addDevice, removeDevice } from '../api/deviceManage'
import { probe } from '../api/helper'
import NotReadyBanner from '../components/NotReadyBanner.vue'
import { loadAMap } from '../utils/amap'
import { compareDeviceCode, compareTimestampDesc } from '../utils/sort'

interface PickerMapInstance {
  add(item: PickerMarkerInstance): void
  destroy(): void
  on(event: 'click', handler: (event: PickerMapClickEvent) => void): void
  setCenter(center: number[]): void
}

interface PickerMarkerInstance {
  setPosition(position: number[]): void
}

interface PickerMapClickEvent {
  lnglat: { getLng(): number; getLat(): number }
}

interface PickerAMapNamespace {
  Map: new (element: HTMLDivElement, options: Record<string, unknown>) => PickerMapInstance
  Marker: new (options: Record<string, unknown>) => PickerMarkerInstance
}

const devices = ref<DeviceVO[]>([])
// 设备查找栏：按设备编号或位置实时过滤
const deviceQuery = ref('')
const filteredDevices = computed(() => {
  const q = deviceQuery.value.trim().toLowerCase()
  const matches = q
    ? devices.value.filter(
        (d) => (d.code || '').toLowerCase().includes(q) || (d.location || '').toLowerCase().includes(q),
      )
    : devices.value
  return [...matches].sort(compareDeviceCode)
})
const ready = ref(true)
const addVisible = ref(false)
const adding = ref(false)
const pickerMapRef = ref<HTMLDivElement>()
const pickerMapLoading = ref(false)
const pickerMapError = ref('')
const hasSelectedPoint = computed(() => Number.isFinite(form.longitude) && Number.isFinite(form.latitude))
let pickerMap: PickerMapInstance | null = null
let pickerMarker: PickerMarkerInstance | null = null
let pickerAMap: PickerAMapNamespace | null = null
const healthByDevice = ref<Record<string, DeviceHealthReport>>({})
const healthLoadFailed = ref(false)
const healthVisible = ref(false)
const healthLoading = ref(false)
const healthDetailError = ref(false)
const evaluating = ref(false)
const selectedCode = ref('')
const healthHistory = ref<DeviceHealthReport[]>([])

// 检测记录展示：默认只显示近一周，按 4 小时窗口聚合（每 4 小时显示一条），支持按时间范围检索
const DAY = 24 * 3600 * 1000
const FOUR_HOUR = 4 * 3600 * 1000
// datetimerange 默认时间：开始 00:00、结束 23:59:59，避免选「当天」时结束时间落到 00:00
const histDefaultTime: [Date, Date] = [new Date(2000, 1, 1, 0, 0, 0), new Date(2000, 1, 1, 23, 59, 59)]
const histRange = ref<[Date, Date] | null>(null)
const histApplied = ref(false)
function disableFutureDate(d: Date) {
  return d.getTime() > Date.now()
}
function applyHistRange() {
  if (!histRange.value?.[0] || !histRange.value?.[1]) {
    ElMessage.warning('请选择时间范围')
    return
  }
  if (histRange.value[1].getTime() <= histRange.value[0].getTime()) {
    ElMessage.warning('结束时间需晚于开始时间')
    return
  }
  histApplied.value = true
}
function resetHistRange() {
  histApplied.value = false
}
// 时间范围过滤 + 4 小时聚合：healthHistory 已按时间倒序（后端 top-30 倒序），每个 4 小时窗口保留最新一条
const filteredHealthHistory = computed(() => {
  let list = healthHistory.value
  if (histApplied.value && histRange.value?.[0] && histRange.value?.[1]) {
    const start = histRange.value[0].getTime()
    const end = histRange.value[1].getTime()
    list = list.filter((r) => {
      const t = new Date(r.createdAt).getTime()
      return !Number.isNaN(t) && t >= start && t <= end
    })
  }
  const buckets = new Map<number, DeviceHealthReport>()
  for (const r of list) {
    const t = new Date(r.createdAt).getTime()
    if (Number.isNaN(t)) continue
    const key = Math.floor(t / FOUR_HOUR)
    if (!buckets.has(key)) buckets.set(key, r)
  }
  return Array.from(buckets.values()).sort((left, right) => compareTimestampDesc(left.createdAt, right.createdAt))
})

const form = reactive<{ code: string; location: string; longitude?: number; latitude?: number }>({ code: '', location: '' })

const selectedReport = computed(() => healthHistory.value[0] ?? healthByDevice.value[selectedCode.value] ?? null)
const selectedDeviceOnline = computed(() => devices.value.find((device) => device.code === selectedCode.value)?.status === 'ONLINE')

const pad = (n: number) => String(n).padStart(2, '0')
function time(ts: number | null) {
  if (!ts) return '—'
  const d = new Date(ts)
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function load() {
  try {
    devices.value = await listDevices()
  } catch {
    // 拦截器已统一提示
  }
}

async function loadHealth() {
  healthLoadFailed.value = false
  try {
    const reports = await listLatestDeviceHealth({ silent: true })
    healthByDevice.value = Object.fromEntries(reports.map((report) => [report.deviceCode, report]))
  } catch {
    healthLoadFailed.value = true
  }
}

function healthTone(score?: number) {
  if (score == null) return 'pending'
  if (score >= 90) return 'healthy'
  if (score >= 75) return 'attention'
  return 'risk'
}

function healthLabel(score: number) {
  if (score >= 90) return '健康'
  if (score >= 75) return '关注'
  return '风险'
}

function healthText(report?: DeviceHealthReport) {
  return report ? `${report.healthScore} 分，${healthLabel(report.healthScore)}` : '尚无评分'
}

function reportTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function telemetryTime(value?: number | null) {
  return value ? `采集于 ${time(value)}` : '采集时间未知'
}

function metric(value: number | null, digits = 1) {
  return value == null ? '—' : Number(value.toFixed(digits)).toString()
}

function telemetryItems(data: HealthTelemetry) {
  return [
    { label: '照度', value: metric(data.lux), unit: 'Lux' },
    { label: '温度', value: metric(data.temperature), unit: '℃' },
    { label: '电压', value: metric(data.voltage), unit: 'V' },
    { label: '电流', value: metric(data.current, 2), unit: 'A' },
    { label: '功率', value: metric(data.power), unit: 'W' },
    { label: '累计电量', value: metric(data.energy, 2), unit: 'kWh' },
    { label: '灯具状态', value: data.lampStatus === 'ON' ? '开启' : data.lampStatus === 'OFF' ? '关闭' : '—', unit: '' },
  ]
}

async function openHealth(row: DeviceVO) {
  selectedCode.value = row.code
  healthVisible.value = true
  healthLoading.value = true
  healthDetailError.value = false
  healthHistory.value = []
  // 默认检索范围：近一周
  const now = Date.now()
  histRange.value = [new Date(now - 7 * DAY), new Date(now)]
  histApplied.value = true
  try {
    healthHistory.value = await getDeviceHealthHistory(row.code, { silent: true })
  } catch {
    healthDetailError.value = true
  } finally {
    healthLoading.value = false
  }
}

async function runEvaluation() {
  if (!selectedCode.value || !selectedDeviceOnline.value) return
  evaluating.value = true
  try {
    const report = await evaluateDeviceHealth(selectedCode.value)
    healthHistory.value = [report, ...healthHistory.value.filter((item) => item.id !== report.id)]
    healthByDevice.value = { ...healthByDevice.value, [report.deviceCode]: report }
    healthDetailError.value = false
    ElMessage.success(`${selectedCode.value} 体检完成：${report.healthScore} 分`)
  } finally {
    evaluating.value = false
  }
}

function openAdd() {
  form.code = ''
  form.location = ''
  form.longitude = undefined
  form.latitude = undefined
  addVisible.value = true
}

function defaultPickerCenter(): number[] {
  const positioned = devices.value.filter(
    (device) => Number.isFinite(device.longitude) && Number.isFinite(device.latitude),
  )
  if (!positioned.length) return [106.5516, 29.5630]
  return [
    positioned.reduce((sum, device) => sum + Number(device.longitude), 0) / positioned.length,
    positioned.reduce((sum, device) => sum + Number(device.latitude), 0) / positioned.length,
  ]
}

function setPickerPoint(longitude: number, latitude: number) {
  form.longitude = Number(longitude.toFixed(6))
  form.latitude = Number(latitude.toFixed(6))
  const position = [form.longitude, form.latitude]
  if (pickerMarker) {
    pickerMarker.setPosition(position)
  } else if (pickerAMap && pickerMap) {
    pickerMarker = new pickerAMap.Marker({ position, anchor: 'bottom-center' })
    pickerMap.add(pickerMarker)
  }
}

async function initPickerMap() {
  if (!pickerMapRef.value || !addVisible.value) return
  pickerMapLoading.value = true
  pickerMapError.value = ''
  pickerMap?.destroy()
  pickerMap = null
  pickerMarker = null
  try {
    pickerAMap = await loadAMap() as PickerAMapNamespace
    if (!pickerMapRef.value || !addVisible.value) return
    const center = hasSelectedPoint.value ? [form.longitude!, form.latitude!] : defaultPickerCenter()
    pickerMap = new pickerAMap.Map(pickerMapRef.value, {
      zoom: 13,
      center,
      resizeEnable: true,
      viewMode: '2D',
    })
    pickerMap.on('click', (event) => {
      setPickerPoint(event.lnglat.getLng(), event.lnglat.getLat())
    })
    if (hasSelectedPoint.value) setPickerPoint(form.longitude!, form.latitude!)
  } catch (error) {
    pickerMapError.value = error instanceof Error ? error.message : '地图加载失败'
  } finally {
    pickerMapLoading.value = false
  }
}

watch(addVisible, async (visible) => {
  if (visible) {
    await nextTick()
    await initPickerMap()
  } else {
    pickerMap?.destroy()
    pickerMap = null
    pickerMarker = null
  }
})

watch([() => form.longitude, () => form.latitude], ([longitude, latitude]) => {
  if (!Number.isFinite(longitude) || !Number.isFinite(latitude) || !pickerMap) return
  const position = [longitude!, latitude!]
  if (pickerMarker) pickerMarker.setPosition(position)
  else setPickerPoint(longitude!, latitude!)
  pickerMap.setCenter(position)
})

async function submitAdd() {
  if (!form.code.trim()) {
    ElMessage.warning('请输入设备编号')
    return
  }
  if (!Number.isFinite(form.longitude) || !Number.isFinite(form.latitude)) {
    ElMessage.warning('请输入设备安装点的经度和纬度')
    return
  }
  adding.value = true
  try {
    const r = await probe(addDevice({ code: form.code.trim(), location: form.location.trim(), longitude: form.longitude!, latitude: form.latitude! }), null)
    if (r.ready) {
      addVisible.value = false
      ElMessage.success(`设备 ${form.code.trim()} 已添加`)
      await load()
    } else {
      ready.value = false
    }
  } finally {
    adding.value = false
  }
}

async function onUnbind(row: DeviceVO) {
  try {
    await ElMessageBox.confirm(
      `解绑后设备 ${row.code} 将不再出现在管理平台，确认解绑？`,
      '解绑设备',
      { type: 'warning', confirmButtonText: '解绑', cancelButtonText: '取消' },
    )
  } catch {
    return // 用户取消
  }
  const r = await probe(removeDevice(row.code), null)
  if (r.ready) {
    devices.value = devices.value.filter((d) => d.code !== row.code)
    ElMessage.success(`设备 ${row.code} 已解绑`)
  } else {
    ready.value = false
  }
}

onMounted(() => {
  void load()
  void loadHealth()
})

onUnmounted(() => pickerMap?.destroy())
</script>

<style scoped>
.dev-manage {
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
}

.table-wrap {
  padding: 6px 16px 16px;
}
.device-search {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 0 12px;
}
.device-search .el-input {
  width: 250px;
}
.search-hits {
  font-size: 12px;
  color: var(--text-muted);
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
  background: var(--accent-dim);
  color: var(--accent-bright);
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
.health-warning {
  min-height: 44px;
  padding: 8px 12px;
  display: flex;
  align-items: center;
  gap: 9px;
  border-left: 3px solid var(--warn);
  color: var(--text-secondary);
  background: var(--warn-dim);
}
.health-warning > span {
  flex: 1;
}
.health-warning > .el-icon {
  color: #765500;
}
.health-warning { color: #765500; }
.health-trigger {
  min-width: 76px;
  min-height: 30px;
  padding: 3px 8px;
  display: inline-flex;
  align-items: baseline;
  justify-content: center;
  gap: 6px;
  border: 1px solid currentColor;
  border-radius: 2px;
  background: transparent;
  cursor: pointer;
  font-size: 11.5px;
}
.health-trigger:hover { filter: brightness(.92); }
.health-trigger.healthy, .history-grade.healthy { color: var(--ok); background: var(--ok-dim); }
.health-trigger.attention, .history-grade.attention { color: #765500; background: var(--warn-dim); }
.health-trigger.risk, .history-grade.risk { color: #b42318; background: var(--danger-dim); }
.health-trigger.pending { color: var(--text-muted); border-style: dashed; background: var(--bg-surface-2); }
.health-score { font-size: 14px; font-weight: 700; }
.health-detail { min-height: 280px; }
.health-state {
  min-height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  color: var(--text-muted);
}
.health-state.empty { flex-direction: column; text-align: center; }
.health-state.empty > .el-icon { font-size: 28px; color: var(--accent-bright); }
.health-state.empty strong { color: var(--text-primary); }
.health-overview {
  min-height: 148px;
  padding: 22px;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  border-left: 5px solid currentColor;
  background: var(--bg-surface-2);
}
.health-overview.healthy { color: var(--ok); background: var(--ok-dim); }
.health-overview.attention { color: #765500; background: var(--warn-dim); }
.health-overview.risk { color: #b42318; background: var(--danger-dim); }
.section-kicker { color: var(--text-muted); font: 10px var(--font-data); letter-spacing: .1em; text-transform: uppercase; }
.health-total { margin-top: 8px; font-size: 48px; font-weight: 720; line-height: 1; }
.health-total span { margin-left: 5px; color: var(--text-muted); font-size: 12px; font-weight: 500; }
.health-grade { margin-top: 7px; font-weight: 650; }
.report-time { display: flex; flex-direction: column; align-items: flex-end; color: var(--text-muted); font-size: 11px; }
.report-time strong { color: var(--text-secondary); font-size: 12px; }
.report-section { padding: 22px 0; border-bottom: 1px solid var(--border-subtle); }
.report-section-head, .anomaly-main { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.report-section h2 { margin: 3px 0 0; font-size: 16px; }
.telemetry-time { color: var(--text-muted); font-size: 11px; text-align: right; }
.telemetry-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-top: 16px; }
.telemetry-item { min-width: 0; padding: 12px 14px; border: 1px solid var(--border-subtle); border-radius: 10px; background: var(--bg-soft); }
.telemetry-item > span { display: block; margin-bottom: 5px; color: var(--text-muted); font-size: 11px; }
.telemetry-item strong { color: var(--text-primary); font-size: 16px; }
.telemetry-item small { color: var(--text-muted); font-size: 10px; font-weight: 500; }
.telemetry-empty { margin-top: 16px; padding: 14px; border-radius: 10px; background: var(--bg-soft); color: var(--text-muted); font-size: 12px; line-height: 1.6; }
.anomaly-count { color: var(--text-muted); font-size: 11px; }
.anomaly-list, .history-list { margin: 16px 0 0; padding: 0; list-style: none; }
.anomaly-list { display: grid; gap: 9px; }
.anomaly-list li { padding: 12px 14px; border-left: 3px solid var(--danger); background: var(--danger-dim); }
.anomaly-list strong { color: var(--text-primary); }
.anomaly-list p { margin: 5px 0 0; color: var(--text-secondary); font-size: 12px; line-height: 1.55; }
.deduct { color: #b42318; font-weight: 700; }
.all-clear { margin-top: 16px; padding: 14px; display: flex; align-items: center; gap: 12px; color: var(--ok); background: var(--ok-dim); }
.all-clear > .el-icon { font-size: 22px; }
.all-clear div { display: flex; flex-direction: column; }
.all-clear strong { color: var(--text-primary); }
.all-clear span { color: var(--text-secondary); font-size: 12px; }
.history-list li { min-height: 38px; display: grid; grid-template-columns: 1fr auto 34px; align-items: center; gap: 12px; border-top: 1px solid var(--border-subtle); color: var(--text-muted); font-size: 11.5px; }
.history-list strong { color: var(--text-primary); text-align: right; }
.history-grade { min-width: 42px; padding: 2px 6px; text-align: center; }
.history-filter { margin-top: 14px; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.history-filter :deep(.el-date-editor--datetimerange) { --el-fill-color-light: var(--bg-surface-2); }
.history-empty { margin-top: 14px; padding: 14px; text-align: center; color: var(--text-muted); font-size: 12px; background: var(--bg-surface-2); }
.drawer-actions { width: 100%; display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.drawer-actions > span { color: var(--text-muted); font-size: 11.5px; }
.drawer-actions :deep(.el-button--primary) {
  --el-button-bg-color: var(--ink);
  --el-button-border-color: var(--ink);
  --el-button-hover-bg-color: var(--accent-deep);
  --el-button-hover-border-color: var(--accent-deep);
}
.coordinate-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.coordinate-grid :deep(.el-input-number) {
  width: 100%;
}
.coordinate-help {
  margin: -4px 0 8px;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.55;
}
.location-picker {
  min-width: 0;
  margin: 0 0 16px;
  padding: 0;
  border: 0;
}
.location-picker legend {
  padding: 0;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 500;
}
.location-picker legend span { margin-left: 4px; color: var(--danger); font-size: 12px; }
.picker-hint { margin: 6px 0 10px; color: var(--text-muted); font-size: 12px; line-height: 1.5; }
.picker-map-wrap { position: relative; min-height: 270px; overflow: hidden; border: 1px solid var(--border-strong); background: var(--bg-surface-2); }
.picker-map { width: 100%; height: 270px; }
.picker-map:focus-visible { outline: 3px solid var(--accent-bright); outline-offset: -3px; }
.picker-map-state { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; gap: 8px; background: var(--bg-surface-2); color: var(--text-muted); font-size: 12px; }
.picker-map-error { flex-direction: column; padding: 24px; text-align: center; }
.picker-map-error > .el-icon { color: var(--warn); font-size: 24px; }
.picker-map-error strong { color: var(--text-primary); }
.picker-selection { position: absolute; right: 12px; bottom: 12px; max-width: calc(100% - 24px); min-height: 40px; padding: 8px 12px; display: flex; align-items: center; gap: 7px; background: rgba(6,34,29,.92); color: #f5fff8; box-shadow: 0 4px 16px rgba(0,0,0,.18); font-size: 11px; backdrop-filter: blur(8px); }
.picker-selection strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
:global(.device-dialog) { max-height: calc(100vh - 32px); display: flex; flex-direction: column; margin-block: 16px !important; }
:global(.device-dialog .el-dialog__header), :global(.device-dialog .el-dialog__footer) { flex: none; }
:global(.device-dialog .el-dialog__body) { min-height: 0; overflow-y: auto; }

@media (max-width: 560px) {
  .page-intro {
    flex-direction: column;
    align-items: flex-start;
  }
  .coordinate-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }
  .picker-map-wrap, .picker-map { min-height: 230px; height: 230px; }
  .picker-selection { left: 10px; right: 10px; justify-content: center; }
  .health-overview { align-items: flex-start; flex-direction: column; }
  .report-time { align-items: flex-start; }
  .drawer-actions > span { display: none; }
  .drawer-actions .el-button { width: 100%; }
}
/* Asset register — continuous ledger, not a card stack */
.dev-manage { max-width: 1640px; }
.dev-manage .page-intro { min-height: 92px; padding: 0 4px 18px; border-bottom: 1px solid var(--border-strong); }
.dev-manage > .panel { border-width: 0 0 1px; background: rgba(255,255,255,.72); }
.dev-manage .panel-head { padding-inline: 0; border-bottom-color: var(--border-strong); }
.dev-manage .table-wrap { border-left: 4px solid var(--signal-strong); }
.dev-manage :deep(.el-table__row:hover td) { background: #eef8e9 !important; }
</style>
