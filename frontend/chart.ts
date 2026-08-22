import type { EChartsOption } from 'echarts'

/** 图表深色令牌（与 index.css 的暖调炭黑 / 路灯金对齐） */
export const chartColors = {
  text: '#a3a19a',
  muted: '#87857d',
  axis: 'rgba(255, 255, 255, 0.1)',
  split: 'rgba(255, 255, 255, 0.06)',
  accent: '#dfb34f',
  accentBright: '#efc878',
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

/** 光照趋势折线：暖调炭黑网格 + 金色折线 + 渐变面积。times 为毫秒时间戳。 */
export function luxLineOption(times: number[], values: number[]): EChartsOption {
  return {
    grid: { left: 48, right: 20, top: 30, bottom: 36 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#19191e',
      borderColor: 'rgba(255, 255, 255, 0.1)',
      borderWidth: 1,
      padding: [10, 14],
      textStyle: { color: '#f2f1ed', fontSize: 12 },
      axisPointer: {
        lineStyle: { color: 'rgba(255, 255, 255, 0.18)' },
        shadowStyle: { color: 'rgba(223, 179, 79, 0.06)' },
      },
      formatter: (params: unknown) => {
        const items = params as Array<{ axisValue: number; value: number }>
        if (!items.length) return ''
        const d = new Date(items[0].axisValue)
        const stamp = `${pad(d.getFullYear())}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
        const lux = items[0].value
        return `<div style="font-family:ui-monospace,monospace;color:#87857d;font-size:11px;margin-bottom:4px">${stamp}</div><div style="color:#efc878;font-weight:600">${lux} Lux</div>`
      },
    },
    xAxis: {
      type: 'time',
      min: times.length ? Math.min(...times) : undefined,
      max: times.length ? Math.max(...times) : undefined,
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
          shadowColor: 'rgba(223, 179, 79, 0.35)',
          shadowBlur: 8,
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
              { offset: 0, color: 'rgba(223, 179, 79, 0.24)' },
              { offset: 1, color: 'rgba(223, 179, 79, 0)' },
            ],
          },
        },
      },
    ],
  }
}
