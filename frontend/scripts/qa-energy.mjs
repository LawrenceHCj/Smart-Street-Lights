import puppeteer from 'puppeteer-core'
import { mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const BASE = process.env.QA_BASE || 'http://127.0.0.1:5173'
const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const OUT = fileURLToPath(new URL('../.impeccable/review/', import.meta.url))
mkdirSync(OUT, { recursive: true })

const now = Date.now()
const report = {
  generatedAt: now, startTs: now - 7 * 86400000, endTs: now, days: 7,
  coveredDeviceCount: 24, sampleBucketCount: 8462, coverageHours: 714.8,
  baselineEnergyKwh: 68.42, estimatedEnergyKwh: 49.87, savedEnergyKwh: 18.55,
  savingRatePercent: 27.11, averageBrightnessPercent: 72.89,
  estimatedCostSavingYuan: 11.13, estimatedCarbonReductionKg: 10.57,
  electricityPriceYuanPerKwh: 0.60, carbonFactorKgPerKwh: 0.57,
  calculationMethod: 'ESTIMATED_FROM_REFERENCE_POWER_AND_SCHEDULE',
  trend: Array.from({ length: 7 }, (_, i) => ({
    ts: now - (6 - i) * 86400000,
    baselineEnergyKwh: 8.2 + i * 0.45,
    estimatedEnergyKwh: 6.0 + i * 0.32,
    savedEnergyKwh: 2.2 + i * 0.13,
  })),
  devices: Array.from({ length: 8 }, (_, i) => ({
    deviceId: `SIM-HUXI-${String(i + 1).padStart(3, '0')}`,
    baselineEnergyKwh: 3.4 - i * 0.12,
    estimatedEnergyKwh: 2.4 - i * 0.08,
    savedEnergyKwh: 1.0 - i * 0.04,
    savingRatePercent: 29.4 - i * 0.5,
    coverageHours: 31.2 - i * 0.7,
  })),
  periods: [
    { name: '深夜节能', brightnessPercent: 50, baselineEnergyKwh: 18.2, estimatedEnergyKwh: 9.1, savedEnergyKwh: 9.1, coverageHours: 160 },
    { name: '清晨照明', brightnessPercent: 80, baselineEnergyKwh: 9.6, estimatedEnergyKwh: 7.68, savedEnergyKwh: 1.92, coverageHours: 88 },
    { name: '日间待机', brightnessPercent: 100, baselineEnergyKwh: 2.2, estimatedEnergyKwh: 2.2, savedEnergyKwh: 0, coverageHours: 20 },
    { name: '傍晚照明', brightnessPercent: 100, baselineEnergyKwh: 21.4, estimatedEnergyKwh: 21.4, savedEnergyKwh: 0, coverageHours: 210 },
    { name: '夜间节能', brightnessPercent: 70, baselineEnergyKwh: 17.02, estimatedEnergyKwh: 11.91, savedEnergyKwh: 5.11, coverageHours: 236.8 },
  ],
}

const browser = await puppeteer.launch({ executablePath: EDGE, headless: true, args: ['--no-sandbox', '--disable-gpu'] })

async function setup(page) {
  await page.setRequestInterception(true)
  page.on('request', request => {
    if (request.url().includes('/api/energy-savings')) {
      request.respond({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, message: 'ok', data: report }) })
    } else if (request.url().includes('/api/config/linkage')) {
      request.respond({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, message: 'ok', data: { enabled: true, threshold: 150, hysteresis: 1, brightnessPeriods: report.periods.map((p, i) => ({ name: p.name, startTime: ['00:00','05:00','07:00','18:00','23:00'][i], brightnessPercent: p.brightnessPercent })), currentBrightnessPercent: 100, currentBrightnessPeriod: '日间待机' } }) })
    } else if (request.url().includes('/api/devices')) {
      request.respond({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, message: 'ok', data: [] }) })
    } else request.continue()
  })
  await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' })
  await page.evaluate(() => { localStorage.setItem('token', 'qa-token'); localStorage.setItem('username', 'admin'); localStorage.setItem('role', 'admin') })
}

async function capture(route, name, width, height) {
  const page = await browser.newPage()
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.setViewport({ width, height, deviceScaleFactor: 1 })
  await setup(page)
  await page.goto(`${BASE}${route}`, { waitUntil: 'networkidle0' })
  await new Promise(resolve => setTimeout(resolve, 700))
  const audit = await page.evaluate(() => {
    const nav = document.querySelector('.desktop-nav')?.getBoundingClientRect()
    const meta = document.querySelector('.command-meta')?.getBoundingClientRect()
    return {
      overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
      h1: document.querySelectorAll('h1').length,
      metrics: document.querySelectorAll('.metric').length,
      canvas: document.querySelectorAll('canvas').length,
      scheduleToggle: document.querySelectorAll('[aria-label="分时亮度控制开关"]').length,
      headerOverlap: nav && meta ? nav.right > meta.left : false,
    }
  })
  await page.screenshot({ path: `${OUT}${name}.png`, fullPage: false })
  console.log(name, JSON.stringify({ ...audit, errors }))
  await page.close()
}

await capture('/energy', 'energy-1440', 1440, 900)
await capture('/energy', 'energy-390', 390, 844)
await capture('/control', 'control-permanent-1440', 1440, 900)
await browser.close()
