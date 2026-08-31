<template>
  <div class="workspace-page risk-page">
    <div class="page-intro">
      <div>
        <h1 class="page-title">故障预测</h1>
        <p class="page-desc">7 天滑动窗口特征 × 加权风险模型，预测每台设备未来 7 天故障风险</p>
      </div>
      <div class="intro-actions">
        <template v-if="canOperate">
          <el-button size="small" :loading="seeding" @click="onSeedDemo">生成演示数据</el-button>
          <el-button size="small" type="primary" :loading="predictingAll" @click="onPredictAll">全部预测</el-button>
        </template>
        <el-button size="small" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="stats">
      <div class="stat-card">
        <div class="stat-label"><el-icon class="label-icon"><WarningFilled /></el-icon>高风险设备</div>
        <div class="stat-value risk-high-text">{{ countByLevel.HIGH }}<span class="unit">台</span></div>
        <div class="stat-sub">建议 48 小时内介入检修</div>
      </div>
      <div class="stat-card">
        <div class="stat-label"><el-icon class="label-icon"><Warning /></el-icon>中风险设备</div>
        <div class="stat-value risk-medium-text">{{ countByLevel.MEDIUM }}<span class="unit">台</span></div>
        <div class="stat-sub">纳入下周巡检计划</div>
      </div>
      <div class="stat-card">
        <div class="stat-label"><el-icon class="label-icon"><CircleCheck /></el-icon>低风险设备</div>
        <div class="stat-value risk-low-text">{{ countByLevel.LOW }}<span class="unit">台</span></div>
        <div class="stat-sub">运行平稳，例行巡检</div>
      </div>
      <div class="stat-card accent">
        <div class="stat-label"><el-icon class="label-icon"><DataLine /></el-icon>已预测设备</div>
        <div class="stat-value">{{ predictions.length }}<span class="unit">台</span></div>
        <div class="stat-sub">每日 03:30 自动更新</div>
      </div>
    </div>

    <div class="panel">
      <div class="panel-head">
        <div>
          <div class="panel-title">风险预测列表</div>
          <div class="panel-caption">点击"详情"查看特征贡献度与维护建议</div>
        </div>
        <div class="panel-meta">
          <el-radio-group v-model="filter" size="small">
            <el-radio-button label="all">全部</el-radio-button>
            <el-radio-button label="HIGH">高风险</el-radio-button>
            <el-radio-button label="MEDIUM">中风险</el-radio-button>
            <el-radio-button label="LOW">低风险</el-radio-button>
          </el-radio-group>
        </div>
      </div>
      <div class="table-wrap">
        <el-table :data="filtered" v-loading="loading">
          <el-table-column label="设备" width="130">
            <template #default="{ row }"><span class="num">{{ row.deviceCode }}</span></template>
          </el-table-column>
          <el-table-column label="未来 7 天风险" width="140">
            <template #default="{ row }">
              <span class="risk-pill" :class="row.riskLevel.toLowerCase()">{{ levelLabel(row.riskLevel) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="综合风险分" width="170">
            <template #default="{ row }">
              <div class="score-cell">
                <div class="score-bar"><div class="score-fill" :class="row.riskLevel.toLowerCase()" :style="{ width: `${(row.riskScore * 100).toFixed(0)}%` }"></div></div>
                <span class="num score-num">{{ (row.riskScore * 100).toFixed(0) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="当前健康度" width="110" align="center">
            <template #default="{ row }">
              <span v-if="row.currentHealthScore != null" class="num">{{ row.currentHealthScore }}</span>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="advice" label="维护建议" min-width="280" show-overflow-tooltip />
          <el-table-column label="预测时间" width="160">
            <template #default="{ row }"><span class="num">{{ time(row.predictedAt) }}</span></template>
          </el-table-column>
          <el-table-column label="操作" width="170" align="right" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click="openDetail(row)">详情</el-button>
              <el-button v-if="canOperate" size="small" text :loading="predictingCode === row.deviceCode" @click="onPredictOne(row.deviceCode)">立即预测</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <div class="table-empty">
              暂无预测报告
              <small v-if="canOperate">可点击右上角"生成演示数据"造数，再点"全部预测"</small>
            </div>
          </template>
        </el-table>
      </div>
    </div>

    <el-drawer v-model="detailOpen" :title="detail ? `故障预测详情 · ${detail.deviceCode}` : '故障预测详情'" size="560px">
      <div v-if="detail" class="detail-body">
        <div class="detail-headline">
          <div class="headline-score">
            <span class="risk-pill big" :class="detail.riskLevel.toLowerCase()">{{ levelLabel(detail.riskLevel) }}</span>
            <span class="headline-num num">{{ (detail.riskScore * 100).toFixed(0) }}<small>分</small></span>
          </div>
          <div class="headline-meta">
            <div>预测窗口：未来 {{ detail.horizonDays }} 天</div>
            <div>当前健康度：<span class="num">{{ detail.currentHealthScore ?? '暂无报告' }}</span></div>
            <div>预测时间：<span class="num">{{ time(detail.predictedAt) }}</span></div>
          </div>
        </div>

        <div class="detail-block">
          <div class="block-title">主要原因</div>
          <ul class="reason-list">
            <li v-for="(reason, i) in reasonList" :key="i">{{ reason }}</li>
          </ul>
        </div>

        <div class="detail-block">
          <div class="block-title">维护建议</div>
          <div class="advice-box">{{ detail.advice }}</div>
        </div>

        <div class="detail-block">
          <div class="block-title">特征贡献度（加权风险模型）</div>
          <div ref="chartRef" class="feature-chart"></div>
        </div>

        <div class="detail-block">
          <div class="block-title">特征明细</div>
          <div v-for="f in featureList" :key="f.key" class="feature-row" :class="{ insufficient: f.insufficient }">
            <div class="feature-row-head">
              <strong>{{ f.label }}</strong>
              <span v-if="f.insufficient" class="feature-tag">样本不足</span>
              <span v-else class="feature-tag ok">风险 {{ (f.risk * 100).toFixed(0) }}%</span>
            </div>
            <div class="feature-row-detail">{{ f.detail }}</div>
          </div>
        </div>

        <div class="detail-block">
          <div class="block-title">预测历史（综合风险分）</div>
          <div ref="historyChartRef" class="history-chart"></div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleCheck, DataLine, Warning, WarningFilled } from '@element-plus/icons-vue'
import { init, use } from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { ECharts } from 'echarts/core'
import {
  getRiskHistory, listRiskLatest, parseFeatures, parseReasons, predictAllDevices, predictDevice, seedDemoData,
  type RiskLevel, type RiskPrediction,
} from '../api/risk'
import { getCurrentRole } from '../utils/auth'

use([BarChart, LineChart, GridComponent, TooltipComponent, CanvasRenderer])

const predictions = ref<RiskPrediction[]>([])
const loading = ref(false)
const filter = ref<'all' | RiskLevel>('all')
const canOperate = ['admin', 'operator'].includes(getCurrentRole() ?? '')

const seeding = ref(false)
const predictingAll = ref(false)
const predictingCode = ref('')

const detailOpen = ref(false)
const detail = ref<RiskPrediction | null>(null)
const featureList = ref<ReturnType<typeof parseFeatures>>([])
const reasonList = ref<string[]>([])
const chartRef = ref<HTMLDivElement>()
const historyChartRef = ref<HTMLDivElement>()
let contributionChart: ECharts | null = null
let historyChart: ECharts | null = null

const countByLevel = computed(() => {
  const c = { HIGH: 0, MEDIUM: 0, LOW: 0 }
  for (const p of predictions.value) c[p.riskLevel] = (c[p.riskLevel] ?? 0) + 1
  return c
})
const filtered = computed(() =>
  filter.value === 'all' ? predictions.value : predictions.value.filter((p) => p.riskLevel === filter.value),
)

function levelLabel(level: RiskLevel): string {
  return level === 'HIGH' ? '高风险' : level === 'MEDIUM' ? '中风险' : '低风险'
}

function time(iso: string): string {
  if (!iso) return '—'
  const d = new Date(iso)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

async function load() {
  loading.value = true
  try {
    predictions.value = await listRiskLatest()
  } catch {
    /* 全局拦截器已提示 */
  } finally {
    loading.value = false
  }
}

async function onSeedDemo() {
  seeding.value = true
  try {
    const msg = await seedDemoData()
    ElMessage.success(msg)
  } catch {
    /* 全局拦截器已提示 */
  } finally {
    seeding.value = false
  }
}

async function onPredictAll() {
  predictingAll.value = true
  try {
    const r = await predictAllDevices()
    ElMessage.success(`已生成 ${r.predictedDevices} 台设备的预测报告`)
    await load()
  } catch {
    /* 全局拦截器已提示 */
  } finally {
    predictingAll.value = false
  }
}

async function onPredictOne(code: string) {
  predictingCode.value = code
  try {
    await predictDevice(code)
    ElMessage.success(`${code} 预测完成`)
    await load()
  } catch {
    /* 全局拦截器已提示 */
  } finally {
    predictingCode.value = ''
  }
}

async function openDetail(row: RiskPrediction) {
  detail.value = row
  featureList.value = parseFeatures(row)
  reasonList.value = parseReasons(row)
  detailOpen.value = true
  await nextTick()
  renderContributionChart()
  await renderHistoryChart(row.deviceCode)
}

function renderContributionChart() {
  if (!chartRef.value) return
  contributionChart?.dispose()
  contributionChart = init(chartRef.value)
  const features = featureList.value
  const labels = features.map((f) => f.label)
  const values = features.map((f) => (f.insufficient ? 0 : Math.round(f.riskContribution * 1000) / 10))
  const colors = features.map((f) => (f.insufficient ? '#c9d4d0' : f.risk >= 0.6 ? '#c0392b' : f.risk >= 0.35 ? '#e67e22' : '#27ae60'))
  contributionChart.setOption({
    grid: { left: 110, right: 40, top: 8, bottom: 28 },
    tooltip: {
      trigger: 'item',
      formatter: (params: { dataIndex: number }) => {
        const f = features[params.dataIndex]
        return `<div style="max-width:320px;white-space:normal;font-size:12px"><strong>${f.label}</strong><br/>${f.detail}<br/><span style="color:#b6c9cf">权重 ${(f.weight * 100).toFixed(0)}% · 样本 ${f.sampleCount < 0 ? '—' : f.sampleCount}</span></div>`
      },
    },
    xAxis: { type: 'value', max: 25, axisLabel: { formatter: '{value}%' } },
    yAxis: { type: 'category', data: labels, inverse: true, axisLabel: { fontSize: 11 } },
    series: [{
      type: 'bar',
      data: values.map((v, i) => ({ value: v, itemStyle: { color: colors[i] } })),
      barWidth: 14,
      label: { show: true, position: 'right', formatter: ({ value }: { value: number }) => (value > 0 ? `${value.toFixed(1)}%` : '0') },
    }],
  })
}

async function renderHistoryChart(deviceCode: string) {
  if (!historyChartRef.value) return
  historyChart?.dispose()
  historyChart = init(historyChartRef.value)
  let history: RiskPrediction[] = []
  try {
    history = (await getRiskHistory(deviceCode)).slice().reverse()
  } catch {
    /* 全局拦截器已提示 */
  }
  historyChart.setOption({
    grid: { left: 40, right: 16, top: 12, bottom: 28 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: history.map((h) => time(h.predictedAt)),
      axisLabel: { fontSize: 10 },
    },
    yAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}分' } },
    series: [{
      type: 'line',
      data: history.map((h) => Math.round(h.riskScore * 100)),
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { color: '#cd4900', width: 2 },
      itemStyle: { color: '#fa9819' },
      areaStyle: {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(250, 152, 25, 0.2)' },
            { offset: 1, color: 'rgba(250, 152, 25, 0)' },
          ],
        },
      },
    }],
    graphic: history.length
      ? undefined
      : { type: 'text', left: 'center', top: 'middle', silent: true, style: { text: '暂无历史预测', fill: '#48749e', font: '12px Inter, sans-serif' } },
  })
}

onMounted(load)
onUnmounted(() => {
  contributionChart?.dispose()
  historyChart?.dispose()
})
</script>

<style scoped>
.intro-actions { display: flex; align-items: center; gap: 8px; }
.risk-high-text { color: #c0392b; }
.risk-medium-text { color: #e67e22; }
.risk-low-text { color: #27ae60; }

.risk-pill { display: inline-flex; align-items: center; padding: 3px 10px; border-radius: 999px; font-size: 12px; font-weight: 600; }
.risk-pill.high { color: #fff; background: #c0392b; }
.risk-pill.medium { color: #fff; background: #e67e22; }
.risk-pill.low { color: #fff; background: #27ae60; }
.risk-pill.big { font-size: 14px; padding: 5px 14px; }

.score-cell { display: flex; align-items: center; gap: 8px; }
.score-bar { flex: 1; height: 8px; border-radius: 4px; background: rgba(30, 61, 89, 0.1); overflow: hidden; }
.score-fill { height: 100%; border-radius: 4px; }
.score-fill.high { background: #c0392b; }
.score-fill.medium { background: #e67e22; }
.score-fill.low { background: #27ae60; }
.score-num { min-width: 26px; text-align: right; font-size: 12px; }
.muted { color: var(--text-muted); }

.table-empty { display: grid; gap: 6px; padding: 28px 0; color: var(--text-muted); }
.table-empty small { font-size: 12px; }

.detail-body { display: grid; gap: 18px; }
.detail-headline { display: flex; align-items: center; gap: 16px; padding: 14px; border: 1px solid var(--border-subtle); background: #fbfdfb; }
.headline-score { display: flex; align-items: center; gap: 12px; }
.headline-num { font-size: 34px; font-weight: 800; color: var(--ink); }
.headline-num small { font-size: 13px; font-weight: 400; color: var(--text-muted); margin-left: 2px; }
.headline-meta { display: grid; gap: 4px; color: var(--text-muted); font-size: 12px; }

.detail-block { display: grid; gap: 8px; }
.block-title { font-size: 13px; font-weight: 700; color: var(--ink); }
.reason-list { margin: 0; padding-left: 18px; display: grid; gap: 6px; font-size: 13px; color: var(--text-secondary, #48749e); }
.advice-box { padding: 12px 14px; border-left: 3px solid #fa9819; background: rgba(250, 152, 25, 0.08); font-size: 13px; line-height: 1.6; }

.feature-chart { width: 100%; height: 240px; }
.history-chart { width: 100%; height: 160px; }
.feature-row { padding: 10px 12px; border: 1px solid var(--border-subtle); display: grid; gap: 4px; }
.feature-row.insufficient { opacity: 0.55; }
.feature-row-head { display: flex; align-items: center; gap: 8px; font-size: 13px; }
.feature-row-detail { font-size: 12px; color: var(--text-muted); }
.feature-tag { margin-left: auto; padding: 1px 8px; border-radius: 999px; font-size: 11px; background: rgba(192, 57, 43, 0.12); color: #c0392b; }
.feature-tag.ok { background: rgba(39, 174, 96, 0.12); color: #27ae60; }
</style>
