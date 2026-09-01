import puppeteer from 'puppeteer-core'
import { mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const BASE = process.env.QA_BASE || 'http://127.0.0.1:5173'
const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const OUT = fileURLToPath(new URL('../.impeccable/review/', import.meta.url))
mkdirSync(OUT, { recursive: true })

const browser = await puppeteer.launch({
  executablePath: EDGE,
  headless: true,
  args: ['--no-sandbox', '--disable-gpu', '--force-device-scale-factor=1'],
})

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

async function getQaToken() {
  try {
    const response = await fetch(`${BASE}/api/auth/login`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ username: 'admin', password: '123456' }),
    })
    const payload = await response.json()
    if (payload?.code === 0 && payload?.data?.token) return payload.data.token
  } catch {
    // 后端不可用时仍截图前端的可恢复错误态。
  }
  return 'demo-token'
}

const qaToken = await getQaToken()

async function capture({ name, route, width, height, auth = true, waitMs = 1000 }) {
  const page = await browser.newPage()
  await page.setViewport({ width, height, deviceScaleFactor: 1 })
  if (auth) {
    await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' })
    await page.evaluate((token) => localStorage.setItem('token', token), qaToken)
  }
  await page.goto(`${BASE}${route}`, { waitUntil: 'domcontentloaded' })
  await wait(waitMs)
  await page.evaluate(() => {
    const main = document.querySelector('.main')
    if (main) main.scrollTop = 0
  })
  await page.screenshot({ path: `${OUT}${name}.png`, fullPage: false })
  await page.close()
  console.log('captured', name, `${width}x${height}`)
}

const primaryRoutes = [
  ['login', '/login', false],
  ['dashboard', '/dashboard', true],
  ['monitor', '/monitor', true],
  ['control', '/control', true],
  ['energy', '/energy', true],
  ['alarms', '/alarms', true],
  ['devices', '/devices', true],
  ['chat', '/chat', true],
  ['users', '/users', true],
]

for (const [name, route, auth] of primaryRoutes) {
  const waitMs = name === 'dashboard' ? 2200 : name === 'monitor' ? 1300 : 900
  await capture({ name: `${name}-1440`, route, auth, width: 1440, height: 900, waitMs })
  await capture({ name: `${name}-390`, route, auth, width: 390, height: 844, waitMs })
}

for (const [name, route] of [['dashboard', '/dashboard'], ['monitor', '/monitor'], ['chat', '/chat']]) {
  const waitMs = name === 'dashboard' ? 2200 : name === 'monitor' ? 1300 : 900
  await capture({ name: `${name}-1024`, route, width: 1024, height: 768, waitMs })
  await capture({ name: `${name}-768`, route, width: 768, height: 1024, waitMs })
}

await browser.close()
console.log('done →', OUT)
