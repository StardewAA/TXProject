/**
 * 为 GoView 大屏项目生成缩略图（写回 report_go_view_project.pic_url）。
 *
 * 背景：「我的 - 全部项目」的卡片、以及卡片放大预览，都读项目的 pic_url。
 * 该字段平时由设计器保存时用 html2canvas 截图 → 上传 → 回写生成；
 * 而通过接口/脚本（deploy/goview/seed-*.mjs）直接建出来的项目没走过这一步，
 * pic_url 为空，卡片上就只有一个占位。
 * 本脚本用无头浏览器打开大屏预览页截图，再走文件上传接口写回 pic_url，
 * 结果与在设计器里保存一次一致。
 *
 * 依赖 playwright（仓库前端已在使用）：
 *   1) `npm i -D playwright`（或全局安装）
 *   2) 或 playwright-core + 系统已有的 Chrome / Edge，用 TXGY_BROWSER 指定可执行文件
 * 需要隔离环境时，可用 TXGY_PLAYWRIGHT_ROOT 指定 playwright 所在的 node_modules 目录。
 *
 * 用法：
 *   node deploy/goview/seed-thumbnails.mjs                  # 处理「我的项目」里所有项目
 *   node deploy/goview/seed-thumbnails.mjs 11 12 13         # 只处理指定项目 id
 *   node deploy/goview/seed-thumbnails.mjs --dry-run        # 只截图不写库，图片落到 TXGY_OUT_DIR
 *
 * 可选环境变量：
 *   TXGY_API_BASE        默认 http://127.0.0.1:48080
 *   TXGY_GOVIEW_BASE     默认 http://127.0.0.1:5273
 *   TXGY_TENANT          默认 1（对应租户名 OPENLAB BS）
 *   TXGY_TENANT_NAME     登录界面里填的租户名，默认 OPENLAB BS
 *   TXGY_USER / TXGY_PASSWORD   默认 admin / admin123
 *   TXGY_BROWSER         浏览器可执行文件路径
 *   TXGY_PLAYWRIGHT_ROOT playwright 所在目录（用于 createRequire 解析）
 *   TXGY_SHOT_SIZE       截图视口，默认 1600x900（大屏会等比缩放适配视口）
 *   TXGY_SHOT_WAIT       打开页面后等待毫秒数，默认 9000（等接口返回与图表动效）
 *   TXGY_DPR             截图倍率，默认 1（2 会更清晰，但文件更大）
 *   TXGY_OUT_DIR         截图输出目录，默认 .docker-local/view-thumbs
 */
import fs from 'node:fs'
import path from 'node:path'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.resolve(HERE, '../..')

const API = process.env.TXGY_API_BASE || 'http://127.0.0.1:48080'
const GOVIEW = (process.env.TXGY_GOVIEW_BASE || 'http://127.0.0.1:5273').replace(/\/+$/, '')
const TENANT = process.env.TXGY_TENANT || '1'
const TENANT_NAME = process.env.TXGY_TENANT_NAME || 'OPENLAB BS'
const USER = process.env.TXGY_USER || 'admin'
const PASSWORD = process.env.TXGY_PASSWORD || 'admin123'

const [SHOT_W, SHOT_H] = (process.env.TXGY_SHOT_SIZE || '1600x900').split('x').map(Number)
const SHOT_WAIT = Number(process.env.TXGY_SHOT_WAIT || 9000)
const OUT_DIR = process.env.TXGY_OUT_DIR || path.join(ROOT, '.docker-local/view-thumbs')

const BROWSER_CANDIDATES = [
  process.env.TXGY_BROWSER,
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe'
].filter(Boolean)

// ---------------------------------------------------------------- HTTP 封装
async function readJson(res, label) {
  const text = await res.text()
  let json
  try {
    json = JSON.parse(text)
  } catch {
    throw new Error(label + ' 返回非 JSON：' + text.slice(0, 200))
  }
  if (json.code !== 0) throw new Error(label + ' 调用失败：' + JSON.stringify(json).slice(0, 300))
  return json.data
}

async function login() {
  const res = await fetch(API + '/admin-api/system/auth/login', {
    method: 'POST',
    headers: { 'tenant-id': TENANT, 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: USER, password: PASSWORD, tenantId: Number(TENANT), grantType: 'password' })
  })
  return readJson(res, '登录')
}

async function listProjects(token) {
  const res = await fetch(API + '/admin-api/report/go-view/project/my-page?pageNo=1&pageSize=100', {
    headers: { 'tenant-id': TENANT, Authorization: 'Bearer ' + token }
  })
  const data = await readJson(res, '项目列表')
  return data.list || []
}

async function uploadThumb(token, id, buffer) {
  const form = new FormData()
  // 文件名与设计器保存时保持一致。
  // 注意：上传接口的 file 只接受「纯文件名」，目录要单独用 directory 字段传，
  // 写成 `go-view/xxx.png` 会被后端的路径校验拒掉（文件路径不正确）。
  form.append('file', new Blob([buffer], { type: 'image/png' }), `${id}_index_preview.png`)
  form.append('directory', 'go-view')
  const res = await fetch(API + '/admin-api/infra/file/upload', {
    method: 'POST',
    headers: { 'tenant-id': TENANT, Authorization: 'Bearer ' + token },
    body: form
  })
  return readJson(res, '缩略图上传')
}

async function savePicUrl(token, id, picUrl) {
  const res = await fetch(API + '/admin-api/report/go-view/project/update', {
    method: 'PUT',
    headers: { 'tenant-id': TENANT, Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' },
    body: JSON.stringify({ id, picUrl })
  })
  return readJson(res, '项目更新')
}

// ---------------------------------------------------------------- 浏览器解析
async function resolveChromium() {
  const tryImport = async name => {
    try {
      const mod = await import(name)
      return mod.chromium ? mod : null
    } catch {
      return null
    }
  }
  let pw = await tryImport('playwright')
  if (!pw) {
    const root = process.env.TXGY_PLAYWRIGHT_ROOT
    if (root) {
      const require = createRequire(path.join(root, 'noop.js'))
      for (const name of ['playwright', 'playwright-core']) {
        try {
          const mod = require(name)
          if (mod && mod.chromium) { pw = mod; break }
        } catch { /* 继续尝试下一个 */ }
      }
    }
  }
  if (!pw) pw = await tryImport('playwright-core')
  if (!pw) {
    throw new Error(
      '未找到 playwright。请先安装：npm i -D playwright\n' +
      '  或使用已装的 playwright-core + 系统浏览器，并设置 TXGY_PLAYWRIGHT_ROOT 指向 playwright 所在目录。'
    )
  }
  const exe = BROWSER_CANDIDATES.find(p => fs.existsSync(p))
  return { chromium: pw.chromium, exe }
}

// ---------------------------------------------------------------- 主流程
const args = process.argv.slice(2)
const dryRun = args.includes('--dry-run')
const wantIds = args.filter(a => /^\d+$/.test(a))

/**
 * 预览页是 hash 路由，登录态存在 localStorage 里。
 *
 * 路由守卫虽然支持用 `?accessToken=..&refreshToken=..` 自动登录，但它读的是
 * `new URL(location.href).searchParams`，也就是 `#` 之前那一截。把参数写在 `#` 后面
 * （如 `/#/chart/preview/13?accessToken=..`）会被整段忽略，页面停在登录页，
 * 截出来的就是一张登录页的图。
 *
 * 这里直接复用真实用户的操作路径：先用界面登录一次，后续每个大屏都带着这份会话打开，
 * 既不依赖查询串的位置，也顺带把 tenantId 一起写进了 localStorage。
 */
async function loginInBrowser(page) {
  await page.goto(`${GOVIEW}/#/login`, { waitUntil: 'networkidle', timeout: 60000 })
  await page.waitForTimeout(2000)
  await page.fill("input[placeholder='global.form_tenant']", TENANT_NAME)
  await page.waitForTimeout(700)
  await page.fill("input[placeholder='请输入账号']", USER)
  await page.fill("input[placeholder='请输入密码']", PASSWORD)
  await page.click("button:has-text('登录')")
  try {
    await page.waitForFunction(() => !location.hash.includes('login'), null, { timeout: 30000 })
  } catch {
    console.warn('登录后页面没有跳转，继续执行；若截图异常请检查租户名与账号密码')
  }
  await page.waitForTimeout(1500)
}

/**
 * 大屏渲染完成的判定：内容层已经挂载出可见元素，并且等比缩放已经算出来。
 * 大屏尺寸（1920x1080）通常比截图视口大，缩放没生效就截图会被裁掉一大块。
 */
const waitPreviewReady = page => page.waitForFunction(() => {
  const scaleDom = document.querySelector('.go-preview-scale')
  if (!scaleDom) return false
  if (!scaleDom.querySelector('canvas, .go-item, img, svg, video')) return false
  return /scale\(/.test(scaleDom.style.transform || '')
}, null, { timeout: 30000 })

const main = async () => {
  const session = await login()
  const token = session.accessToken
  const refreshToken = session.refreshToken
  if (!token || !refreshToken) throw new Error('登录返回缺少 accessToken / refreshToken')

  let projects = await listProjects(token)
  if (wantIds.length) projects = projects.filter(p => wantIds.includes(String(p.id)))
  if (!projects.length) {
    console.log('没有需要处理的项目')
    return
  }
  console.log(`待处理项目 ${projects.length} 个：` + projects.map(p => `${p.id}(${p.name})`).join('、'))

  const { chromium, exe } = await resolveChromium()
  const browser = await chromium.launch({
    executablePath: exe,
    headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage',
      // headless 下 WebGL（三维组件）需要软件渲染
      '--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader']
  })

  if (!fs.existsSync(OUT_DIR)) fs.mkdirSync(OUT_DIR, { recursive: true })

  const context = await browser.newContext({
    viewport: { width: SHOT_W, height: SHOT_H },
    deviceScaleFactor: Number(process.env.TXGY_DPR || 1)
  })

  let ok = 0
  try {
    // 登录页单独开一个，登录完成后关掉；localStorage 在同一个 context 里是共享的
    const loginPage = await context.newPage()
    await loginInBrowser(loginPage)
    await loginPage.close()

    for (const project of projects) {
      const id = String(project.id)
      // 每个大屏都新开一个页面：预览页是 hash 路由，只改 `#` 后面的部分浏览器不会重新加载应用，
      // 复用同一个页面会一直停在上一个大屏上（截出来的全是第一张）。
      const page = await context.newPage()
      const pageErrors = []
      page.on('console', m => { if (m.type() === 'error') pageErrors.push(m.text().slice(0, 160)) })
      page.on('pageerror', e => pageErrors.push('pageerror ' + String(e).slice(0, 160)))
      try {
        await page.goto(`${GOVIEW}/#/chart/preview/${id}`, { waitUntil: 'networkidle', timeout: 60000 })
        await waitPreviewReady(page)
        await page.waitForTimeout(SHOT_WAIT)
        if (page.url().includes('/login')) throw new Error('预览页跳回了登录页，截出来会是一张登录页')

        const buffer = await page.screenshot({ type: 'png' })
        const file = path.join(OUT_DIR, `${id}_index_preview.png`)
        fs.writeFileSync(file, buffer)

        if (dryRun) {
          console.log(`[dry-run] ${id} ${project.name} → ${path.relative(ROOT, file)}（${Math.round(buffer.length / 1024)} KB）`)
        } else {
          const picUrl = await uploadThumb(token, id, buffer)
          await savePicUrl(token, id, picUrl)
          console.log(`✔ ${id} ${project.name} → ${picUrl}`)
        }
        ok++
      } catch (e) {
        console.error(`✘ ${id} ${project.name} 失败：${e.message}`)
        if (pageErrors.length) console.error('   页面报错：' + pageErrors.slice(0, 3).join(' | '))
      } finally {
        await page.close()
      }
    }
  } finally {
    await browser.close()
  }

  console.log(dryRun ? `截图完成 ${ok}/${projects.length}` : `缩略图写入完成 ${ok}/${projects.length}`)
}

main().catch(e => {
  console.error('执行失败：' + e.message)
  process.exit(1)
})
