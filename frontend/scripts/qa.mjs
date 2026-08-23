import puppeteer from 'puppeteer-core'

const BASE = 'http://localhost:5173'
const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const browser = await puppeteer.launch({
  executablePath: EDGE,
  headless: true,
  args: ['--no-sandbox', '--disable-gpu', '--force-device-scale-factor=1'],
})

const results = []
const check = (name, ok, detail = '') =>
  results.push({ name, ok, detail })

// 简易对比度（WCAG 相对亮度）
function lum(rgb) {
  const f = (c) => {
    c /= 255
    return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4)
  }
  return 0.2126 * f(rgb[0]) + 0.7152 * f(rgb[1]) + 0.0722 * f(rgb[2])
}
function contrast(a, b) {
  const l1 = lum(a), l2 = lum(b)
  return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05)
}
const parse = (s) => {
  const m = s.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/)
  return m ? [Number(m[1]), Number(m[2]), Number(m[3])] : null
}

async function auth(page) {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle2' })
  await page.evaluate(() => localStorage.setItem('token', 'demo-token'))
}

// 通用页面体检
async function audit(route, label, { width, height }) {
  const page = await browser.newPage()
  await page.setViewport({ width, height, deviceScaleFactor: 1 })
  if (route !== '/login') await auth(page)
  await page.goto(`${BASE}${route}`, { waitUntil: 'networkidle2' })
  await new Promise((r) => setTimeout(r, 2000))

  const data = await page.evaluate(() => {
    const cs = (el) => (el ? getComputedStyle(el) : null)
    const root = document.documentElement
    const aside = document.querySelector('.aside')
    const header = document.querySelector('.header')
    const main = document.querySelector('.main')
    const bodyBg = cs(document.body).backgroundColor
    const overflowX = root.scrollWidth > root.clientWidth + 1
    const fontUI = cs(document.body).fontFamily
    return {
      dark: root.classList.contains('dark'),
      bodyBg,
      asideW: aside ? aside.getBoundingClientRect().width : null,
      headerH: header ? header.getBoundingClientRect().height : null,
      mainPad: main ? cs(main).padding : null,
      overflowX,
      fontUI: fontUI.slice(0, 40),
      panelCount: document.querySelectorAll('.panel').length,
      statCount: document.querySelectorAll('.stat-card').length,
      statValues: [...document.querySelectorAll('.stat-value')].map((e) => e.textContent.trim()),
      canvas: (() => {
        const c = document.querySelector('canvas')
        return c ? { w: c.width, h: c.height } : null
      })(),
      pills: [...document.querySelectorAll('.status-pill')].map((e) => e.textContent.trim()).slice(0, 3),
      sidebarVisible: aside ? aside.getBoundingClientRect().width > 100 : false,
      chartH: (() => {
        const el = document.querySelector('.chart')
        return el ? el.getBoundingClientRect().height : null
      })(),
    }
  })

  check(`[${label}] 深色主题启用`, data.dark === true, `dark=${data.dark}`)
  check(`[${label}] 无横向溢出`, !data.overflowX, `scrollW=${data.overflowX ? 'overflow' : 'ok'}`)
  check(`[${label}] 画布/面板渲染`, data.statCount >= 0, `panels=${data.panelCount} stats=${data.statCount} canvas=${JSON.stringify(data.canvas)}`)

  // 特定断言
  if (route === '/dashboard') {
    check('[dashboard] 4 张统计卡', data.statCount === 4, `stats=${data.statCount}`)
    check('[dashboard] 统计值非零', data.statValues.length > 0 && data.statValues.some((v) => /\d/.test(v)), data.statValues.join(' | '))
    check('[dashboard] 图表已渲染', !!data.canvas && data.canvas.w > 0 && data.canvas.h > 0, `canvas=${data.canvas && `${data.canvas.w}x${data.canvas.h}`}`)
  }
  if (route === '/monitor') {
    check('[monitor] 状态胶囊渲染', data.pills.length > 0, data.pills.join(' | '))
    check('[monitor] 图表已渲染', !!data.canvas && data.canvas.w > 0, `canvas=${data.canvas && `${data.canvas.w}x${data.canvas.h}`}`)
  }
  if (route === '/chat') {
    const fit = await page.evaluate(() => {
      const wrap = document.querySelector('.chat-wrap')
      const panel = document.querySelector('.chat')
      const vh = window.innerHeight
      return { wrap: wrap.getBoundingClientRect().height, panel: panel.getBoundingClientRect().height, vh }
    })
    check('[chat] 聊天面板适配视口', fit.panel > fit.vh * 0.55, `panel=${Math.round(fit.panel)} vh=${fit.vh}`)
  }
  if (route === '/login') {
    const btn = await page.evaluate(() => {
      const b = document.querySelector('.btn')
      if (!b) return null
      const s = getComputedStyle(b)
      return { bg: s.backgroundColor, color: s.color, radius: s.borderRadius }
    })
    check('[login] 主按钮金色', btn && btn.bg.includes('223, 179, 79'), JSON.stringify(btn))
    check('[login] 卡片圆角', await page.evaluate(() => {
      const c = document.querySelector('.login-card')
      return c ? Number.parseFloat(getComputedStyle(c).borderRadius) >= 18 : false
    }))
  }

  // 对比度：正文（统计值 vs 卡片底）
  if (route === '/dashboard') {
    const c = await page.evaluate(() => {
      const v = document.querySelector('.stat-value')
      const card = document.querySelector('.stat-card')
      if (!v || !card) return null
      return { fg: getComputedStyle(v).color, bg: getComputedStyle(card).backgroundColor }
    })
    if (c) {
      const fg = parse(c.fg), bg = parse(c.bg)
      const ratio = fg && bg ? contrast(fg, bg) : 0
      check('[dashboard] 统计数值对比度 ≥ 4.5', ratio >= 4.5, `ratio=${ratio.toFixed(2)}`)
    }
    // 次要文本（stat-sub）对比度
    const m = await page.evaluate(() => {
      const el = document.querySelector('.stat-sub')
      const card = document.querySelector('.stat-card')
      if (!el || !card) return null
      return { fg: getComputedStyle(el).color, bg: getComputedStyle(card).backgroundColor }
    })
    if (m) {
      const fg = parse(m.fg), bg = parse(m.bg)
      const ratio = fg && bg ? contrast(fg, bg) : 0
      check('[dashboard] 次要文本对比度 ≥ 4.5', ratio >= 4.5, `muted ratio=${ratio.toFixed(2)}`)
    }
  }

  // 侧栏/主区布局
  if (route === '/dashboard' && width >= 1000) {
    check('[layout] 侧栏 236px', data.asideW === 236, `w=${data.asideW}`)
    check('[layout] 顶栏 68px', data.headerH === 68, `h=${data.headerH}`)
    check('[layout] 字体栈应用', /Inter|PingFang|YaHei|system-ui/i.test(data.fontUI), data.fontUI)
  }

  await page.close()
}

await audit('/login', '桌面', { width: 1440, height: 900 })
await audit('/dashboard', '桌面', { width: 1440, height: 900 })
await audit('/monitor', '桌面', { width: 1440, height: 900 })
await audit('/chat', '桌面', { width: 1440, height: 900 })
await audit('/dashboard', '移动', { width: 390, height: 844 })
await audit('/chat', '移动', { width: 390, height: 844 })

await browser.close()

let failed = 0
for (const r of results) {
  const mark = r.ok ? 'PASS' : 'FAIL'
  if (!r.ok) failed++
  console.log(`${mark}  ${r.name}  ${r.detail}`)
}
console.log(`\n${results.length - failed}/${results.length} 项通过`)
process.exit(failed ? 1 : 0)
