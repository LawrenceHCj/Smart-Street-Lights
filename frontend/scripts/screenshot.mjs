import puppeteer from 'puppeteer-core'
import { mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const BASE = 'http://localhost:5173'
const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const OUT = fileURLToPath(new URL('../.impeccable/review/', import.meta.url))
mkdirSync(OUT, { recursive: true })

const browser = await puppeteer.launch({
  executablePath: EDGE,
  headless: true,
  args: ['--no-sandbox', '--disable-gpu', '--force-device-scale-factor=1'],
})

async function shot(name, route, { width, height, auth = true, fullPage = true, wait = 2200 }) {
  const page = await browser.newPage()
  await page.setViewport({ width, height, deviceScaleFactor: 1 })
  // 注入 token 绕过登录守卫（demo 后端不校验 JWT）
  if (auth) {
    await page.goto(`${BASE}/login`, { waitUntil: 'networkidle2' })
    await page.evaluate(() => localStorage.setItem('token', 'demo-token'))
  }
  await page.goto(`${BASE}${route}`, { waitUntil: 'networkidle2' })
  await new Promise((r) => setTimeout(r, wait))
  // 滚动到底部再回顶，确保懒渲染区域稳定
  await page.evaluate(async () => {
    window.scrollTo(0, document.body.scrollHeight)
    await new Promise((r) => setTimeout(r, 150))
    window.scrollTo(0, 0)
  })
  await page.screenshot({ path: `${OUT}${name}.png`, fullPage })
  await page.close()
  console.log('captured', name, `${width}x${height}`)
}

// 桌面
await shot('login', '/login', { width: 1440, height: 900, auth: false })
await shot('dashboard', '/dashboard', { width: 1440, height: 900 })
await shot('monitor', '/monitor', { width: 1440, height: 900 })
await shot('chat', '/chat', { width: 1440, height: 900 })
// 移动
await shot('dashboard-mobile', '/dashboard', { width: 390, height: 844 })
await shot('chat-mobile', '/chat', { width: 390, height: 844 })

await browser.close()
console.log('done →', OUT)
