import http from 'node:http'

const now = Date.now()
const periods = [
  ['深夜节能', 50, 9.10, 160], ['清晨照明', 80, 1.92, 88], ['日间待机', 100, 0, 20],
  ['傍晚照明', 100, 0, 210], ['夜间节能', 70, 5.11, 236.8],
]
const report = {
  generatedAt: now, startTs: now - 7 * 86400000, endTs: now, days: 7,
  coveredDeviceCount: 24, sampleBucketCount: 8462, coverageHours: 714.8,
  baselineEnergyKwh: 68.42, estimatedEnergyKwh: 49.87, savedEnergyKwh: 18.55,
  savingRatePercent: 27.11, averageBrightnessPercent: 72.89,
  estimatedCostSavingYuan: 11.13, estimatedCarbonReductionKg: 10.57,
  electricityPriceYuanPerKwh: 0.60, carbonFactorKgPerKwh: 0.57,
  calculationMethod: 'ESTIMATED_FROM_REFERENCE_POWER_AND_SCHEDULE',
  trend: Array.from({ length: 7 }, (_, i) => ({ ts: now - (6 - i) * 86400000, baselineEnergyKwh: 8.2 + i * .45, estimatedEnergyKwh: 6 + i * .32, savedEnergyKwh: 2.2 + i * .13 })),
  devices: Array.from({ length: 8 }, (_, i) => ({ deviceId: `SIM-HUXI-${String(i + 1).padStart(3, '0')}`, baselineEnergyKwh: 3.4 - i * .12, estimatedEnergyKwh: 2.4 - i * .08, savedEnergyKwh: 1 - i * .04, savingRatePercent: 29.4 - i * .5, coverageHours: 31.2 - i * .7 })),
  periods: periods.map(([name, brightnessPercent, savedEnergyKwh, coverageHours]) => ({ name, brightnessPercent, savedEnergyKwh, coverageHours, baselineEnergyKwh: 10, estimatedEnergyKwh: 10 - savedEnergyKwh })),
}

const send = (response, data) => {
  response.writeHead(200, { 'content-type': 'application/json; charset=utf-8' })
  response.end(JSON.stringify({ code: 0, message: 'ok', data }))
}

http.createServer((request, response) => {
  if (request.url?.startsWith('/api/auth/login')) return send(response, { token: 'qa-token', username: 'admin', role: 'admin' })
  if (request.url?.startsWith('/api/energy-savings')) return send(response, report)
  if (request.url?.startsWith('/api/config/linkage')) return send(response, { enabled: true, threshold: 150, hysteresis: 1, brightnessPeriods: periods.map(([name, brightnessPercent], i) => ({ name, startTime: ['00:00','05:00','07:00','18:00','23:00'][i], brightnessPercent })), currentBrightnessPercent: 100, currentBrightnessPeriod: '日间待机' })
  if (request.url?.startsWith('/api/devices')) return send(response, [])
  send(response, [])
}).listen(18080, '127.0.0.1', () => console.log('mock energy API listening on 127.0.0.1:18080'))
