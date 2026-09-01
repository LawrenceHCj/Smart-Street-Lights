<template>
  <div class="workspace-page energy-page">
    <div class="page-intro">
      <div>
        <h1 class="page-title">节能分析</h1>
        <p class="page-desc">以路灯全功率运行作为基线，量化分时亮度策略带来的节电、费用与减碳收益</p>
      </div>
      <div class="intro-actions">
        <el-radio-group v-model="days" size="small" aria-label="统计时间范围" @change="load">
          <el-radio-button :label="1">近24小时</el-radio-button>
          <el-radio-button :label="7">近7天</el-radio-button>
          <el-radio-button :label="30">近30天</el-radio-button>
        </el-radio-group>
        <el-button size="small" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="estimate-note" role="note">
      <el-icon aria-hidden="true"><InfoFilled /></el-icon>
      <div>
        <strong>估算口径</strong>
        <span>以每台设备统计期内最高稳定亮灯功率作为100%参考，再按当前各时段亮度百分比回算策略能耗；不改变原有 Lux 开关结果。</span>
      </div>
      <span v-if="report" class="freshness num">更新于 {{ formatTime(report.generatedAt) }}</span>
    </div>

    <div v-if="errorMessage" class="error-state" role="alert">
      <span>{{ errorMessage }}</span>
      <el-button size="small" @click="load">重新加载</el-button>
    </div>

    <div v-else v-loading="loading" class="report-body" aria-live="polite">
      <template v-if="report && report.sampleBucketCount > 0">
        <section class="metric-grid" aria-label="节能核心指标">
          <article class="metric primary">
            <span class="metric-index num">01 / ENERGY</span>
            <span class="metric-label">预计节省电量</span>
            <strong class="metric-value num">{{ energyValue(report.savedEnergyKwh) }}<small>{{ energyUnit(report.savedEnergyKwh) }}</small></strong>
            <span class="metric-foot">基线 {{ formatKwh(report.baselineEnergyKwh) }} kWh</span>
          </article>
          <article class="metric">
            <span class="metric-index num">02 / RATE</span>
            <span class="metric-label">综合节电率</span>
            <strong class="metric-value num">{{ fixed(report.savingRatePercent, 1) }}<small>%</small></strong>
            <span class="metric-foot">平均亮度 {{ fixed(report.averageBrightnessPercent, 1) }}%</span>
          </article>
          <article class="metric">
            <span class="metric-index num">03 / COST</span>
            <span class="metric-label">预计节省费用</span>
            <strong class="metric-value num">{{ fixed(report.estimatedCostSavingYuan, 2) }}<small>元</small></strong>
            <span class="metric-foot">按 {{ fixed(report.electricityPriceYuanPerKwh, 2) }} 元/kWh</span>
          </article>
          <article class="metric">
            <span class="metric-index num">04 / CARBON</span>
            <span class="metric-label">预计减少碳排放</span>
            <strong class="metric-value num">{{ carbonValue(report.estimatedCarbonReductionKg) }}<small>{{ carbonUnit(report.estimatedCarbonReductionKg) }}</small></strong>
            <span class="metric-foot">系数 {{ fixed(report.carbonFactorKgPerKwh, 2) }} kg/kWh</span>
          </article>
        </section>

        <section class="analysis-grid">
          <div class="panel trend-panel">
            <div class="panel-head">
              <div>
                <div class="panel-title">能耗基线对比</div>
                <div class="panel-caption">同样开关时长下，100%全功率基线与分时策略估算</div>
              </div>
              <div class="panel-meta"><span class="num">{{ report.coveredDeviceCount }}</span> 台设备 · <span class="num">{{ fixed(report.coverageHours, 1) }}</span> 设备小时</div>
            </div>
            <div ref="trendChartRef" class="trend-chart" role="img" :aria-label="trendAriaLabel"></div>
          </div>

          <div class="panel schedule-panel">
            <div class="panel-head">
              <div>
                <div class="panel-title">时段贡献</div>
                <div class="panel-caption">亮度越低，单位亮灯时长节能越多</div>
              </div>
            </div>
            <div class="period-list">
              <div v-for="period in report.periods" :key="period.name" class="period-row">
                <div class="period-head">
                  <span>{{ period.name }}</span>
                  <strong class="num">{{ period.brightnessPercent }}%</strong>
                </div>
                <div class="period-track" aria-hidden="true"><span :style="{ width: `${period.brightnessPercent}%` }"></span></div>
                <div class="period-meta">
                  <span>节省 {{ formatKwh(period.savedEnergyKwh) }} kWh</span>
                  <span>{{ fixed(period.coverageHours, 1) }} 设备小时</span>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section class="panel device-panel">
          <div class="panel-head">
            <div>
              <div class="panel-title">设备节能贡献</div>
              <div class="panel-caption">按预计节电量从高到低排列，便于识别主要收益来源</div>
            </div>
            <div class="panel-meta">统计覆盖 {{ report.coveredDeviceCount }} 台</div>
          </div>
          <div class="table-wrap">
            <el-table :data="report.devices" row-key="deviceId">
              <el-table-column prop="deviceId" label="设备" min-width="150">
                <template #default="{ row }"><span class="num device-code">{{ row.deviceId }}</span></template>
              </el-table-column>
              <el-table-column label="全功率基线" min-width="130" align="right">
                <template #default="{ row }"><span class="num">{{ formatKwh(row.baselineEnergyKwh) }}</span> kWh</template>
              </el-table-column>
              <el-table-column label="策略能耗" min-width="130" align="right">
                <template #default="{ row }"><span class="num">{{ formatKwh(row.estimatedEnergyKwh) }}</span> kWh</template>
              </el-table-column>
              <el-table-column label="预计节电" min-width="130" align="right">
                <template #default="{ row }"><strong class="num saved">{{ formatKwh(row.savedEnergyKwh) }}</strong> kWh</template>
              </el-table-column>
              <el-table-column label="节电率" min-width="170">
                <template #default="{ row }">
                  <div class="rate-cell"><span><i :style="{ width: `${Math.min(100, row.savingRatePercent)}%` }"></i></span><b class="num">{{ fixed(row.savingRatePercent, 1) }}%</b></div>
                </template>
              </el-table-column>
              <el-table-column label="有效设备时长" min-width="130" align="right">
                <template #default="{ row }"><span class="num">{{ fixed(row.coverageHours, 1) }}</span> h</template>
              </el-table-column>
            </el-table>
          </div>
        </section>

        <footer class="method-foot">
          统计仅纳入灯态为 ON、功率有效且同一5分钟窗口至少包含2个样本的数据；数据间断不会被整段外推。
        </footer>
      </template>

      <div v-else-if="report" class="empty-state">
        <el-icon aria-hidden="true"><DataAnalysis /></el-icon>
        <strong>暂无足够的节能统计样本</strong>
        <span>请确认设备在亮灯时持续上报 power、lampStatus 和时间戳。单个孤立样本不会被外推为整段能耗。</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { DataAnalysis, InfoFilled } from '@element-plus/icons-vue'
import { LineChart } from 'echarts/charts'
import { AriaComponent, GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { init, use } from 'echarts/core'
import type { ECharts } from 'echarts/core'
import type { EChartsOption } from 'echarts'
import { CanvasRenderer } from 'echarts/renderers'
import { getEnergySavings, type EnergySavingsReport } from '../api/energy'

use([LineChart, AriaComponent, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const days = ref(7)
const report = ref<EnergySavingsReport | null>(null)
const loading = ref(false)
const errorMessage = ref('')
const trendChartRef = ref<HTMLDivElement>()
let trendChart: ECharts | null = null

const trendAriaLabel = computed(() => report.value
  ? `能耗基线对比图。全功率基线${formatKwh(report.value.baselineEnergyKwh)}千瓦时，分时策略预计${formatKwh(report.value.estimatedEnergyKwh)}千瓦时。`
  : '能耗基线对比图。')

function fixed(value: number | null | undefined, digits = 2): string {
  return Number.isFinite(value) ? Number(value).toFixed(digits) : '0'
}
function formatKwh(value: number): string { return value < 1 ? fixed(value, 3) : fixed(value, 2) }
function energyValue(value: number): string { return value < 1 ? fixed(value * 1000, 1) : fixed(value, 2) }
function energyUnit(value: number): string { return value < 1 ? 'Wh' : 'kWh' }
function carbonValue(value: number): string { return value < 1 ? fixed(value * 1000, 1) : fixed(value, 2) }
function carbonUnit(value: number): string { return value < 1 ? 'g' : 'kg' }
function formatTime(ts: number): string {
  const d = new Date(ts)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}
function trendLabel(ts: number): string {
  const d = new Date(ts)
  const p = (n: number) => String(n).padStart(2, '0')
  return days.value === 1 ? `${p(d.getHours())}:00` : `${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    report.value = await getEnergySavings(days.value)
    await nextTick()
    renderTrend()
  } catch {
    report.value = null
    errorMessage.value = '节能统计暂时无法读取，请检查后端服务后重试。'
  } finally {
    loading.value = false
  }
}

function renderTrend() {
  if (!trendChartRef.value || !report.value?.trend.length) {
    trendChart?.dispose()
    trendChart = null
    return
  }
  trendChart?.dispose()
  trendChart = init(trendChartRef.value, undefined, { renderer: 'canvas' })
  const labels = report.value.trend.map(point => trendLabel(point.ts))
  const option: EChartsOption = {
    animation: !window.matchMedia('(prefers-reduced-motion: reduce)').matches,
    aria: { enabled: true, description: trendAriaLabel.value },
    grid: { left: 58, right: 22, top: 54, bottom: 38 },
    legend: { top: 12, right: 18, itemWidth: 18, itemHeight: 3, textStyle: { color: '#536b63', fontSize: 11 } },
    tooltip: { trigger: 'axis', valueFormatter: (value: unknown) => `${Number(value).toFixed(3)} kWh` },
    xAxis: { type: 'category', boundaryGap: false, data: labels, axisTick: { show: false }, axisLine: { lineStyle: { color: 'rgba(29,70,59,.2)' } }, axisLabel: { color: '#536b63', hideOverlap: true } },
    yAxis: { type: 'value', name: 'kWh', nameTextStyle: { color: '#536b63' }, axisLabel: { color: '#536b63' }, splitLine: { lineStyle: { color: 'rgba(29,70,59,.09)' } } },
    series: [
      { name: '100%全功率基线', type: 'line', smooth: true, symbol: 'none', data: report.value.trend.map(point => point.baselineEnergyKwh), lineStyle: { color: '#536b63', width: 2, type: 'dashed' }, itemStyle: { color: '#536b63' } },
      { name: '分时策略能耗', type: 'line', smooth: true, symbol: 'none', data: report.value.trend.map(point => point.estimatedEnergyKwh), lineStyle: { color: '#287b5d', width: 3 }, itemStyle: { color: '#287b5d' }, areaStyle: { color: 'rgba(110,189,85,.16)' } },
    ],
  }
  trendChart.setOption(option)
}

function resizeChart() { trendChart?.resize() }
watch(days, () => { trendChart?.dispose(); trendChart = null })
onMounted(() => { load(); window.addEventListener('resize', resizeChart) })
onBeforeUnmount(() => { trendChart?.dispose(); window.removeEventListener('resize', resizeChart) })
</script>

<style scoped>
.energy-page { max-width: 1640px; }
.intro-actions { display: flex; align-items: center; gap: 10px; }
.estimate-note { min-height: 58px; padding: 12px 16px; display: flex; align-items: center; gap: 12px; border: 1px solid rgba(40,123,93,.2); background: var(--accent-dim); color: var(--text-secondary); }
.estimate-note > .el-icon { flex: none; color: var(--accent-deep); font-size: 18px; }
.estimate-note > div { display: grid; gap: 2px; }
.estimate-note strong { color: var(--accent-deep); font-size: 12px; }
.estimate-note span { font-size: 12px; }
.estimate-note .freshness { margin-left: auto; white-space: nowrap; color: var(--text-muted); }
.error-state { min-height: 82px; padding: 18px; display: flex; align-items: center; justify-content: space-between; gap: 16px; border: 1px solid rgba(229,56,53,.25); background: var(--danger-dim); color: var(--danger); }
.report-body { min-height: 420px; display: grid; gap: 18px; }
.metric-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); border: 1px solid var(--border-subtle); background: var(--bg-surface); }
.metric { min-width: 0; min-height: 150px; padding: 18px 20px; display: flex; flex-direction: column; border-right: 1px solid var(--border-subtle); }
.metric:last-child { border-right: 0; }
.metric.primary { color: #f4fff9; background: var(--ink); }
.metric-index { color: var(--text-muted); font-size: 9px; letter-spacing: .12em; }
.metric.primary .metric-index, .metric.primary .metric-foot { color: #91aaa1; }
.metric-label { margin-top: 12px; color: var(--text-secondary); font-size: 12px; }
.metric.primary .metric-label { color: #c9d9d3; }
.metric-value { margin-top: 3px; color: var(--text-primary); font-size: clamp(28px, 3vw, 42px); line-height: 1.15; letter-spacing: -.04em; }
.metric.primary .metric-value { color: var(--signal); }
.metric-value small { margin-left: 6px; font-family: var(--font-ui); font-size: 12px; font-weight: 600; letter-spacing: 0; }
.metric-foot { margin-top: auto; color: var(--text-muted); font-size: 11px; }
.analysis-grid { display: grid; grid-template-columns: minmax(0, 1.7fr) minmax(300px, .8fr); gap: 18px; }
.trend-chart { width: 100%; height: 340px; }
.period-list { padding: 8px 16px 16px; display: grid; gap: 15px; }
.period-row { display: grid; gap: 6px; }
.period-head, .period-meta { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.period-head { color: var(--text-secondary); font-size: 12px; }
.period-head strong { color: var(--accent-deep); }
.period-track { height: 5px; overflow: hidden; background: var(--bg-surface-2); }
.period-track span { height: 100%; display: block; background: var(--accent); }
.period-meta { color: var(--text-muted); font-size: 10.5px; }
.table-wrap { overflow-x: auto; }
.device-code { color: var(--text-primary); font-weight: 650; }
.saved { color: var(--ok); }
.rate-cell { min-width: 120px; display: grid; grid-template-columns: minmax(70px, 1fr) 48px; align-items: center; gap: 9px; }
.rate-cell > span { height: 5px; overflow: hidden; background: var(--bg-surface-2); }
.rate-cell i { height: 100%; display: block; background: var(--accent); }
.rate-cell b { color: var(--accent-deep); font-size: 11px; text-align: right; }
.method-foot { padding: 13px 16px; border-left: 3px solid var(--border-strong); color: var(--text-muted); background: rgba(255,255,255,.62); font-size: 11.5px; }
.empty-state { min-height: 380px; padding: 32px; display: grid; place-items: center; align-content: center; gap: 12px; border: 1px solid var(--border-subtle); background: var(--bg-surface); text-align: center; }
.empty-state .el-icon { color: var(--accent); font-size: 38px; }
.empty-state strong { color: var(--text-primary); font-size: 16px; }
.empty-state span { max-width: 560px; color: var(--text-muted); font-size: 12px; }
@media (max-width: 1080px) { .metric-grid { grid-template-columns: repeat(2, minmax(0,1fr)); } .metric:nth-child(2) { border-right: 0; } .metric:nth-child(-n+2) { border-bottom: 1px solid var(--border-subtle); } .analysis-grid { grid-template-columns: 1fr; } }
@media (max-width: 700px) { .intro-actions { width: 100%; align-items: stretch; flex-direction: column; } .intro-actions :deep(.el-radio-group) { display: grid; grid-template-columns: repeat(3, 1fr); } .intro-actions :deep(.el-radio-button__inner) { width: 100%; padding-inline: 8px; } .estimate-note { align-items: flex-start; } .estimate-note .freshness { display: none; } .metric-grid { grid-template-columns: 1fr; } .metric { min-height: 132px; border-right: 0; border-bottom: 1px solid var(--border-subtle); } .metric:last-child { border-bottom: 0; } .metric-value { font-size: 32px; } .trend-chart { height: 280px; } }
</style>
