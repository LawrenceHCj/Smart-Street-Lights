import puppeteer from 'puppeteer-core'

const BASE = process.env.QA_BASE || 'http://127.0.0.1:5173'
const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const browser = await puppeteer.launch({
  executablePath: EDGE,
  headless: true,
  args: ['--no-sandbox', '--disable-gpu', '--force-device-scale-factor=1'],
})

const results = []
const check = (name, ok, detail = '') => results.push({ name, ok, detail })
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
    // 后端不可用时仍可用占位 token 验证前端错误态与布局。
  }
  return 'demo-token'
}

const qaToken = await getQaToken()

function luminance(rgb) {
  const channel = (value) => {
    const normalized = value / 255
    return normalized <= 0.04045 ? normalized / 12.92 : ((normalized + 0.055) / 1.055) ** 2.4
  }
  return 0.2126 * channel(rgb[0]) + 0.7152 * channel(rgb[1]) + 0.0722 * channel(rgb[2])
}

function contrast(a, b) {
  const first = luminance(a)
  const second = luminance(b)
  return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05)
}

function parseRgb(value) {
  const match = value.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/)
  return match ? [Number(match[1]), Number(match[2]), Number(match[3])] : null
}

async function authorize(page) {
  await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' })
  await page.evaluate((token) => localStorage.setItem('token', token), qaToken)
}

async function audit(route, viewport) {
  const label = `${viewport.width}x${viewport.height} ${route}`
  const page = await browser.newPage()
  const pageErrors = []
  page.on('pageerror', (error) => pageErrors.push(error.message))
  await page.setViewport({ ...viewport, deviceScaleFactor: 1 })
  if (route !== '/login') await authorize(page)
  await page.goto(`${BASE}${route}`, { waitUntil: 'domcontentloaded' })
  await wait(route === '/dashboard' || route === '/monitor' ? 1200 : 650)

  const data = await page.evaluate(() => {
    const visible = (element) => {
      const rect = element.getBoundingClientRect()
      const style = getComputedStyle(element)
      return rect.width > 0 && rect.height > 0 && style.display !== 'none' && style.visibility !== 'hidden'
    }
    const root = document.documentElement
    const main = document.querySelector('.main')
    const aside = document.querySelector('.aside')
    const header = document.querySelector('.header')
    const buttons = [...document.querySelectorAll('button')]
      .filter(visible)
      .filter((button) => !button.closest('.amap-container'))
      .map((button) => {
        const rect = button.getBoundingClientRect()
        return {
          label: button.getAttribute('aria-label') || button.textContent?.trim().slice(0, 24) || '未命名按钮',
          width: Math.round(rect.width),
          height: Math.round(rect.height),
        }
      })
    return {
      brandTheme: root.classList.contains('brand-theme'),
      rootOverflow: root.scrollWidth > root.clientWidth + 1,
      mainOverflow: main ? main.scrollWidth > main.clientWidth + 1 : false,
      asideWidth: aside ? Math.round(aside.getBoundingClientRect().width) : null,
      asideX: aside ? Math.round(aside.getBoundingClientRect().x) : null,
      headerHeight: header ? Math.round(header.getBoundingClientRect().height) : null,
      mainTop: main ? Math.round(main.getBoundingClientRect().top) : null,
      h1Count: document.querySelectorAll('h1').length,
      navToggleVisible: Boolean(document.querySelector('.nav-toggle') && visible(document.querySelector('.nav-toggle'))),
      unnamedButtons: buttons.filter((button) => button.label === '未命名按钮'),
      smallButtons: buttons.filter((button) => button.width < 24 || button.height < 24),
      statCount: document.querySelectorAll('.stat-card').length,
      statValues: [...document.querySelectorAll('.stat-value')].map((element) => element.textContent?.trim() || ''),
      canvasCount: document.querySelectorAll('canvas').length,
      mobileDeviceListVisible: Boolean(document.querySelector('.mobile-device-list') && visible(document.querySelector('.mobile-device-list'))),
      chatRect: (() => {
        const element = document.querySelector('.chat')
        if (!element) return null
        const rect = element.getBoundingClientRect()
        return { top: Math.round(rect.top), bottom: Math.round(rect.bottom), height: Math.round(rect.height) }
      })(),
      loginLabels: document.querySelectorAll('.login-card label[for]').length,
      primaryButton: (() => {
        const element = document.querySelector('.btn')
        if (!element) return null
        const style = getComputedStyle(element)
        return { background: style.backgroundColor, color: style.color }
      })(),
      statContrast: (() => {
        const value = document.querySelector('.stat-value')
        const card = document.querySelector('.stat-card')
        if (!value || !card) return null
        return { foreground: getComputedStyle(value).color, background: getComputedStyle(card).backgroundColor }
      })(),
    }
  })

  check(`[${label}] Straightforward 品牌主题`, data.brandTheme, `brandTheme=${data.brandTheme}`)
  check(`[${label}] 页面无横向滚动`, !data.rootOverflow && !data.mainOverflow, `root=${data.rootOverflow} main=${data.mainOverflow}`)
  check(`[${label}] 单一主标题`, data.h1Count === 1, `h1=${data.h1Count}`)
  check(`[${label}] 图标按钮有名称`, data.unnamedButtons.length === 0, JSON.stringify(data.unnamedButtons))
  check(`[${label}] 按钮目标至少 24px`, data.smallButtons.length === 0, JSON.stringify(data.smallButtons.slice(0, 4)))
  check(`[${label}] 无页面脚本错误`, pageErrors.length === 0, pageErrors.join(' | '))

  if (viewport.width >= 901 && route !== '/login') {
    check(`[${label}] 桌面侧栏 236px`, data.asideWidth === 236 && data.asideX === 0, `x=${data.asideX} w=${data.asideWidth}`)
    check(`[${label}] 桌面顶栏 60px`, data.headerHeight === 60 && data.mainTop === 60, `header=${data.headerHeight} mainTop=${data.mainTop}`)
  }

  if (viewport.width <= 900 && route !== '/login') {
    check(`[${label}] 窄屏导航入口可见`, data.navToggleVisible && data.asideX < 0, `toggle=${data.navToggleVisible} asideX=${data.asideX}`)
    await page.click('.nav-toggle')
    await wait(80)
    const openNav = await page.evaluate(() => ({
      expanded: document.querySelector('.nav-toggle')?.getAttribute('aria-expanded'),
      labels: [...document.querySelectorAll('.menu-label')].filter((element) => {
        const rect = element.getBoundingClientRect()
        return rect.width > 0 && rect.height > 0
      }).length,
    }))
    check(`[${label}] 窄屏导航标签完整`, openNav.expanded === 'true' && openNav.labels === 7, JSON.stringify(openNav))
    await page.keyboard.press('Escape')
  }

  if (route === '/login') {
    check(`[${label}] 登录字段有关联标签`, data.loginLabels === 2, `labels=${data.loginLabels}`)
    check(`[${label}] 主按钮使用品牌橙`, Boolean(data.primaryButton?.background.includes('250, 152, 25')), JSON.stringify(data.primaryButton))
  }

  if (route === '/dashboard') {
    check(`[${label}] 四项关键指标稳定呈现`, data.statCount === 4, `stats=${data.statCount}`)
    check(`[${label}] 指标包含真实数值或加载占位`, data.statValues.length === 4 && data.statValues.every((value) => /\d|—/.test(value)), data.statValues.join(' | '))
    check(`[${label}] 趋势图画布已初始化`, data.canvasCount > 0, `canvas=${data.canvasCount}`)
    if (data.statContrast) {
      const foreground = parseRgb(data.statContrast.foreground)
      const background = parseRgb(data.statContrast.background)
      const ratio = foreground && background ? contrast(foreground, background) : 0
      check(`[${label}] 指标文本对比度 ≥ 4.5`, ratio >= 4.5, `ratio=${ratio.toFixed(2)}`)
    }
  }

  if (route === '/monitor') {
    check(`[${label}] 监控趋势图已初始化`, data.canvasCount > 0, `canvas=${data.canvasCount}`)
    if (viewport.width <= 700) {
      check(`[${label}] 移动端关键设备信息无需横向滚动`, data.mobileDeviceListVisible, `mobileList=${data.mobileDeviceListVisible}`)
    }
  }

  if (route === '/chat' && data.chatRect) {
    check(
      `[${label}] 聊天输入区保持可达`,
      data.chatRect.bottom <= viewport.height && data.chatRect.height >= viewport.height * 0.55,
      JSON.stringify(data.chatRect),
    )
  }

  await page.close()
}

const routes = ['/login', '/dashboard', '/monitor', '/control', '/energy', '/alarms', '/devices', '/chat', '/users']
const viewports = [
  { width: 1440, height: 900 },
  { width: 1024, height: 768 },
  { width: 768, height: 1024 },
  { width: 390, height: 844 },
]

for (const viewport of viewports) {
  for (const route of routes) await audit(route, viewport)
}

await browser.close()

let failed = 0
for (const result of results) {
  const mark = result.ok ? 'PASS' : 'FAIL'
  if (!result.ok) failed += 1
  console.log(`${mark}  ${result.name}  ${result.detail}`)
}
console.log(`\n${results.length - failed}/${results.length} 项通过`)
process.exit(failed ? 1 : 0)
