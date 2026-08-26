import type { EChartsOption } from 'echarts'
import { LineChart } from 'echarts/charts'
import { AriaComponent, GraphicComponent, GridComponent, TooltipComponent } from 'echarts/components'
import { init, use } from 'echarts/core'
import type { ECharts } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'

use([LineChart, AriaComponent, GraphicComponent, GridComponent, TooltipComponent, CanvasRenderer])

export type ChartInstance = ECharts

export function initLuxChart(element: HTMLDivElement): ChartInstance {
  return init(element, undefined, { renderer: 'canvas' })
}

/** 图表品牌令牌（与 Straightforward 海军蓝 / 橙色体系对齐） */
export const chartColors = {
  text: '#1e3d59',
  muted: '#48749e',
  axis: 'rgba(30, 61, 89, 0.24)',
  split: 'rgba(30, 61, 89, 0.1)',
  accent: '#fa9819',
  accentBright: '#cd4900',
}

const pad = (n: number) => String(n).padStart(2, '0')

/** 时间轴刻度标签：跨天时带日期，否则只显示 HH:mm */
function axisLabel(ts: number): string {
  const d = new Date(ts)
  const now = new Date()
  const sameDay = d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth() && d.getDate() === now.getDate()
  const hm = `${pad(d.getHours())}:${pad(d.getMinutes())}`
  return sameDay ? hm : `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${hm}`
}

/** 光照趋势折线。timeWindow 用于固定所选时间范围，避免数据稀疏时不同区间看起来完全相同。 */
export function luxLineOption(
  times: number[],
  values: number[],
  timeWindow?: { start: number; end: number },
): EChartsOption {
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  return {
    animation: !reducedMotion,
    aria: {
      enabled: true,
      description: times.length
        ? `光照趋势图，共 ${times.length} 个数据点。`
        : '光照趋势图，当前暂无数据。',
    },
    grid: { left: 48, right: 20, top: 30, bottom: 36 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#1e3d59',
      borderColor: 'rgba(255, 255, 255, 0.18)',
      borderWidth: 1,
      padding: [10, 14],
      textStyle: { color: '#ffffff', fontSize: 12 },
      axisPointer: {
        lineStyle: { color: 'rgba(255, 255, 255, 0.28)' },
        shadowStyle: { color: 'rgba(250, 152, 25, 0.08)' },
      },
      formatter: (params: unknown) => {
        const items = params as Array<{ axisValue: number; value: [number, number] }>
        if (!items.length) return ''
        const d = new Date(items[0].axisValue)
        const stamp = `${pad(d.getFullYear())}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
        const lux = items[0].value[1]
        return `<div style="font-family:ui-monospace,monospace;color:#b6c9cf;font-size:11px;margin-bottom:4px">${stamp}</div><div style="color:#ffffff;font-weight:600">${lux} Lux</div>`
      },
    },
    xAxis: {
      type: 'time',
      min: timeWindow?.start ?? (times.length ? Math.min(...times) : undefined),
      max: timeWindow?.end ?? (times.length ? Math.max(...times) : undefined),
      axisLine: { lineStyle: { color: chartColors.axis } },
      axisLabel: {
        color: chartColors.muted,
        fontSize: 11,
        formatter: (value: number) => axisLabel(value),
        hideOverlap: true,
      },
      axisTick: { show: false },
      splitLine: { show: false },
    },
    yAxis: {
      type: 'value',
      name: 'Lux',
      nameTextStyle: { color: chartColors.muted },
      axisLabel: { color: chartColors.muted, fontSize: 11 },
      splitLine: { lineStyle: { color: chartColors.split } },
    },
    series: [
      {
        type: 'line',
        smooth: true,
        symbol: 'none',
        data: times.map((t, i) => [t, values[i]]),
        lineStyle: {
          color: chartColors.accent,
          width: 2,
          shadowColor: 'rgba(250, 152, 25, 0.22)',
          shadowBlur: 5,
        },
        itemStyle: { color: chartColors.accent },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(250, 152, 25, 0.2)' },
              { offset: 1, color: 'rgba(250, 152, 25, 0)' },
            ],
          },
        },
      },
    ],
    graphic: times.length
      ? undefined
      : {
          type: 'text',
          left: 'center',
          top: 'middle',
          silent: true,
          style: {
            text: '暂无趋势数据',
            fill: chartColors.muted,
            font: '12px Inter, sans-serif',
          },
        },
  }
}
