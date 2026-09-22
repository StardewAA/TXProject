/**
 * 生成 / 更新 GoView 大屏项目「ERP / MES / WMS 综合监控大屏」。
 *
 * 为什么用脚本而不是在浏览器里手拖：大屏内容存在 report_go_view_project.content 里，
 * 就是一份 JSON（editCanvasConfig + componentList + requestGlobalConfig）。脚本生成
 * 便于评审、改口径、重复部署；数据全部来自现有后端统计接口（MES/WMS/ERP），
 * 组件用 AJAX 请求 + JS 过滤器把接口返回转成 ECharts dataset。
 *
 * 用法（宿主机已部署 goview/frontend 容器时）：
 *   node deploy/goview/seed-dashboard.mjs
 * 可选环境变量：
 *   TXGY_API_BASE  默认 http://127.0.0.1:48080
 *   TXGY_TENANT    默认 1（对应租户名 OPENLAB BS）
 *   TXGY_USER / TXGY_PASSWORD  默认 admin / admin123
 */
const API = process.env.TXGY_API_BASE || 'http://127.0.0.1:48080'
const TENANT = process.env.TXGY_TENANT || '1'
const USER = process.env.TXGY_USER || 'admin'
const PASSWORD = process.env.TXGY_PASSWORD || 'admin123'
const PROJECT_NAME = 'ERP / MES / WMS 综合监控大屏'

// ---------------------------------------------------------------- HTTP 封装
async function api(path, { method = 'GET', token, body } = {}) {
  const headers = { 'tenant-id': TENANT }
  if (body) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = 'Bearer ' + token
  const res = await fetch(API + '/admin-api' + path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined
  })
  const text = await res.text()
  let json
  try {
    json = JSON.parse(text)
  } catch (e) {
    throw new Error(path + ' 返回非 JSON：' + text.slice(0, 200))
  }
  if (json.code !== 0) throw new Error(path + ' 调用失败：' + JSON.stringify(json).slice(0, 300))
  return json.data
}

// ---------------------------------------------------------------- 组件外壳
const STYLES = {
  filterShow: false, hueRotate: 0, saturate: 1, contrast: 1, brightness: 1, opacity: 1,
  rotateZ: 0, rotateX: 0, rotateY: 0, skewX: 0, skewY: 0, blendMode: 'normal',
  animations: [], animationsOpen: false, animationsCirculate: false,
  animationsCurve: 'linear', animationsDirection: '', circulatePlayTime: 1,
  circulateDelayTime: 0, styleEnhance: ''
}
const EVENTS = {
  baseEvent: { click: null, dblclick: null, mouseenter: null, mouseleave: null },
  advancedEvents: { vnodeMounted: null, vnodeBeforeMount: null },
  interactEvents: []
}
const REQUEST = (url, dataType) => ({
  requestDataType: dataType, // 0 静态 / 1 AJAX
  requestHttpType: 'get',
  requestUrl: url,
  requestInterval: null,
  requestIntervalUnit: 'second',
  requestContentType: 0,
  requestParamsBodyType: 'none',
  requestSQLContent: { sql: 'select * from  where' },
  requestParams: {
    Body: { 'form-data': {}, 'x-www-form-urlencoded': {}, json: '', xml: '' },
    Header: {},
    Params: {}
  }
})
// 用 ${location.origin} 走大屏自己那一层 Nginx 反代（同源、免 CORS），
// URL 里保留 /admin-api 才会被 appendTokenAndTenant 加上 Authorization / tenant-id。
const url = (p) => '${location.origin}/admin-api/' + p

const CHART_CONFIG = {
  BarCommon: { key: 'BarCommon', chartKey: 'VBarCommon', conKey: 'VCBarCommon', title: '柱状图', category: 'Bars', categoryName: '柱状图', package: 'Charts', chartFrame: 'echarts', image: 'bar_x.png' },
  BarCrossrange: { key: 'BarCrossrange', chartKey: 'VBarCrossrange', conKey: 'VCBarCrossrange', title: '横向柱状图', category: 'Bars', categoryName: '柱状图', package: 'Charts', chartFrame: 'echarts', image: 'bar_y.png' },
  LineCommon: { key: 'LineCommon', chartKey: 'VLineCommon', conKey: 'VCLineCommon', title: '折线图', category: 'Lines', categoryName: '折线图', package: 'Charts', chartFrame: 'echarts', image: 'line.png' },
  PieCommon: { key: 'PieCommon', chartKey: 'VPieCommon', conKey: 'VCPieCommon', title: '饼图', category: 'Pies', categoryName: '饼图', package: 'Charts', chartFrame: 'echarts', image: 'pie.png' },
  Number: { key: 'Number', chartKey: 'VNumber', conKey: 'VCNumber', title: '数字计数', category: 'Mores', categoryName: '更多', package: 'Decorates', chartFrame: 'common', image: 'number.png' },
  TextCommon: { key: 'TextCommon', chartKey: 'VTextCommon', conKey: 'VCTextCommon', title: '文字', category: 'Texts', categoryName: '文本', package: 'Informations', chartFrame: 'common', image: 'text_static.png' }
}

let seq = 0
const uid = () => 'erpmeswms' + (++seq).toString().padStart(3, '0') + '000'

function component(key, attr, option, filter, requestUrl, dataType = 1) {
  const spec = {
    id: uid(),
    isGroup: false,
    attr: { x: attr[0], y: attr[1], w: attr[2], h: attr[3], offsetX: 0, offsetY: 0, zIndex: -1 },
    styles: JSON.parse(JSON.stringify(STYLES)),
    preview: { overFlowHidden: false },
    status: { lock: false, hide: false },
    request: REQUEST(requestUrl, dataType),
    filter: filter || undefined,
    events: JSON.parse(JSON.stringify(EVENTS)),
    key,
    chartConfig: JSON.parse(JSON.stringify(CHART_CONFIG[key])),
    option
  }
  return spec
}

const chartTitle = (text) => ({
  show: true,
  text,
  left: 'center',
  top: 10,
  textStyle: { color: '#CFE3FFFF', fontSize: 16, fontWeight: 'bold' }
})

// ---------------------------------------------------------------- option 生成
function lineOption(dims, seriesNames) {
  return {
    title: null,
    tooltip: { show: true, trigger: 'axis' },
    legend: { show: true, top: 36, textStyle: { color: '#B9B8CE' } },
    grid: { show: false, left: 60, right: 30, top: 78, bottom: 40, containLabel: true },
    xAxis: { show: true, type: 'category' },
    yAxis: { show: true, type: 'value' },
    dataset: { dimensions: dims, source: [] },
    series: seriesNames.map((n) => ({
      name: n,
      type: 'line',
      smooth: true,
      symbolSize: 6,
      label: { show: true, position: 'top', color: '#fff', fontSize: 11 },
      itemStyle: { color: null, borderRadius: 0 },
      lineStyle: { type: 'solid', width: 3, color: null }
    }))
  }
}
function barOption(dims, seriesNames) {
  return {
    tooltip: { show: true, trigger: 'axis', axisPointer: { show: true, type: 'shadow' } },
    legend: { show: true, top: 36, textStyle: { color: '#B9B8CE' } },
    grid: { show: false, left: 60, right: 30, top: 78, bottom: 40, containLabel: true },
    xAxis: { show: true, type: 'category' },
    yAxis: { show: true, type: 'value' },
    dataset: { dimensions: dims, source: [] },
    series: seriesNames.map((n) => ({
      name: n,
      type: 'bar',
      barWidth: 26,
      label: { show: true, position: 'top', color: '#fff', fontSize: 12 },
      itemStyle: { color: null, borderRadius: 3 }
    }))
  }
}
// 堆叠柱状图：用于「单据类型 × 状态」这类二维结构数据
function stackedBarOption(dims, seriesNames) {
  return {
    tooltip: { show: true, trigger: 'axis', axisPointer: { show: true, type: 'shadow' } },
    legend: { show: true, top: 36, textStyle: { color: '#B9B8CE' } },
    grid: { show: false, left: 60, right: 30, top: 78, bottom: 40, containLabel: true },
    xAxis: { show: true, type: 'category' },
    yAxis: { show: true, type: 'value' },
    dataset: { dimensions: dims, source: [] },
    series: seriesNames.map((n) => ({
      name: n,
      type: 'bar',
      stack: 'total',
      barWidth: 52,
      label: { show: true, position: 'inside', color: '#fff', fontSize: 11 },
      itemStyle: { color: null, borderRadius: 0 }
    }))
  }
}
function barCrossOption(dims, seriesNames) {
  return {
    tooltip: { show: true, trigger: 'axis', axisPointer: { show: true, type: 'shadow' } },
    legend: { show: true, top: 36, textStyle: { color: '#B9B8CE' } },
    grid: { show: false, left: 90, right: 60, top: 78, bottom: 30, containLabel: true },
    xAxis: { show: true, type: 'value' },
    yAxis: { show: true, type: 'category' },
    dataset: { dimensions: dims, source: [] },
    series: seriesNames.map((n) => ({
      name: n,
      type: 'bar',
      barWidth: 14,
      label: { show: true, position: 'right', color: '#fff', fontSize: 12 },
      itemStyle: { color: null, borderRadius: 0 }
    }))
  }
}
function pieOption() {
  return {
    type: 'ring',
    isCarousel: false,
    tooltip: { show: true, trigger: 'item', formatter: '{b}: {@data1} ({d}%)' },
    legend: { show: true, top: 36, textStyle: { color: '#B9B8CE' } },
    dataset: { dimensions: ['product', 'data1'], source: [] },
    series: [
      {
        type: 'pie',
        // dataset 形式的饼图里 {c} 拿到的是整行对象，取值必须用 {@维度名} 模板
        encode: { itemName: 'product', value: 'data1' },
        radius: ['40%', '65%'],
        center: ['50%', '62%'],
        roseType: false,
        avoidLabelOverlap: false,
        itemStyle: { show: true, borderRadius: 10, borderColor: '#fff', borderWidth: 2 },
        label: { show: true, position: 'outer', formatter: '{b}: {@data1} ({d}%)', color: '#B9B8CE', fontSize: 13 },
        labelLine: { show: true },
        emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } }
      }
    ]
  }
}
function numberOption({ dataset, prefixText = '', suffixText = '', color = '#4A9EF8FF', size = 30, precision = 0 }) {
  return {
    dataset,
    from: 0,
    dur: 2,
    precision,
    showSeparator: true,
    numberSize: size,
    numberColor: color,
    prefixText,
    prefixColor: color,
    suffixText,
    suffixColor: color
  }
}
function textOption(dataset, { fontSize = 16, fontColor = '#8FB6E8FF', fontWeight = 'normal', letterSpacing = 2 } = {}) {
  return {
    link: '',
    linkHead: 'http://',
    dataset,
    fontSize,
    fontColor,
    paddingX: 10,
    paddingY: 10,
    textAlign: 'center',
    fontWeight,
    borderWidth: 0,
    borderColor: '#ffffff',
    borderRadius: 5,
    letterSpacing,
    writingMode: 'horizontal-tb',
    backgroundColor: '#00000000'
  }
}

// ---------------------------------------------------------------- 组件清单
const MES = 'mes/home-statistics/'
const WMS = 'wms/home-statistics/'
const ERP = 'erp/'
const components = []

// 标题
components.push(component('TextCommon', [460, 18, 1000, 56], textOption(PROJECT_NAME, { fontSize: 34, fontColor: '#4A9EF8FF', fontWeight: 'bold', letterSpacing: 4 }), undefined, '', 0))

// KPI：标签 + 数字，各 8 个
const kpis = [
  { label: 'MES 在制工单', path: MES + 'summary', filter: 'return Number(data.workOrderActiveCount) || 0', suffix: ' 单' },
  { label: 'MES 已完成工单', path: MES + 'summary', filter: 'return Number(data.workOrderFinishedCount) || 0', suffix: ' 单' },
  { label: 'MES 设备总数', path: MES + 'summary', filter: 'return Number(data.machineryTotal) || 0', suffix: ' 台' },
  { label: 'MES 停机设备', path: MES + 'summary', filter: 'return Number(data.machineryStop) || 0', suffix: ' 台' },
  { label: 'MES 安灯呼叫', path: MES + 'summary', filter: 'return Number(data.andonActiveCount) || 0', suffix: ' 次' },
  { label: 'WMS 库存总量', path: WMS + 'inventory-summary', filter: 'return Math.round(Number(data.totalQuantity) || 0)', suffix: ' 件' },
  { label: 'ERP 今日采购额', path: ERP + 'purchase-statistics/summary', filter: 'return Number(data.todayPrice) || 0', prefix: '￥', precision: 2, suffix: '' },
  { label: 'ERP 今日销售额', path: ERP + 'sale-statistics/summary', filter: 'return Number(data.todayPrice) || 0', prefix: '￥', precision: 2, suffix: '' }
]
kpis.forEach((k, i) => {
  const x = 8 + i * 240
  components.push(component('TextCommon', [x, 92, 224, 34], textOption(k.label, { fontSize: 15, fontColor: '#8FB6E8FF' }), undefined, '', 0))
  components.push(component('Number', [x, 124, 224, 78], numberOption({
    dataset: 0, prefixText: k.prefix || '', suffixText: k.suffix || '', precision: k.precision || 0, size: 28
  }), k.filter, url(k.path)))
})

// 图表区：左 / 中 / 右 三列 + 底部两列
const L = [8, 612], M = [654, 612], R = [1300, 612]
const rowA = 215, rowB = 560, hA = 330, hB = 320

// A 左：MES 工单状态分布
components.push(component('PieCommon', [L[0], rowA, L[1], hA], Object.assign(pieOption(), { title: chartTitle('工单状态分布（MES）') }),
  "var t = { 0: '草稿', 1: '已确认', 2: '已完成', 3: '已取消', 10: '已下发', 11: '生产中', 12: '审批中', 13: '已批准' };" +
  "return { dimensions: ['product', 'data1'], source: (data || []).filter(function (it) { return it.count > 0 }).map(function (it) { return { product: it.statusName || t[it.status] || ('状态' + it.status), data1: it.count } }) }",
  url(MES + 'work-order-status')))

// A 中：WMS 单据类型 × 状态分布
// 原来这里是「近 7 天生产趋势」，但演示库没有生产报工流水（接口返回全 0），
// 折线永远是平的。换成 order-summary 的结构数据：4 种单据 × 3 种状态，共 12 个真实数值。
components.push(component('BarCommon', [M[0], rowA, M[1], hA], Object.assign(stackedBarOption(['x', 'y1', 'y2', 'y3'], ['已完成', '草稿', '已作废']), { title: chartTitle('单据类型与状态分布（WMS）') }),
  "var nt = { 1: '入库单', 2: '出库单', 3: '移库单', 4: '盘库单' };" +
  "return { dimensions: ['x', 'y1', 'y2', 'y3'], source: (data || []).map(function (it) {" +
  "var c = {}; (it.statuses || []).forEach(function (s) { c[s.status] = s.count });" +
  "return { x: nt[it.type] || ('类型' + it.type), y1: c[4] || 0, y2: c[0] || 0, y3: c[5] || 0 } }) }",
  url(WMS + 'order-summary')))

// A 右：WMS 单据量
components.push(component('BarCommon', [R[0], rowA, R[1], hA], Object.assign(barOption(['x', 'y1'], ['单据总数']), { title: chartTitle('单据量汇总（WMS）') }),
  "var t = { 1: '入库单', 2: '出库单', 3: '移库单', 4: '盘库单' };" +
  "return { dimensions: ['x', 'y1'], source: (data || []).map(function (it) { return { x: t[it.type] || ('类型' + it.type), y1: it.total } }) }",
  url(WMS + 'order-summary')))

// B 左：MES 设备状态
components.push(component('PieCommon', [L[0], rowB, L[1], hB], Object.assign(pieOption(), { title: chartTitle('设备状态分布（MES）') }),
  "return { dimensions: ['product', 'data1'], source: (data || []).map(function (it) { return { product: it.statusName || ('状态' + it.status), data1: it.count } }) }",
  url(MES + 'machinery-status')))

// B 中：MES 工单计划产量 Top 6 产品
// 同样因为演示库没有「近 7 天」的流水（order-trend 返回全 0），改成对工单明细分页接口
// 做一次按产品的数量汇总，取产量最高的 6 个产品。
// 注意：横向柱状图组件（BarCrossrange）在 dataset 变化时会用自己的默认 series 覆盖掉
// 我们存的 series（见 packages/components/Charts/Bars/BarCrossrange/index.vue 里的 watch），
// 所以 series 的 name / stack 都会丢。这里把数值维度的名字直接起成业务名，
// ECharts 用维度名当系列名，图例就不会显示出 "y1" 这种占位符。
components.push(component('BarCrossrange', [M[0], rowB, M[1], hB], Object.assign(barCrossOption(['x', '计划产量'], ['计划产量']), { title: chartTitle('工单计划产量 Top 6 产品（MES）') }),
  "var list = (data && data.list) || [];" +
  "var m = {};" +
  "list.forEach(function (it) { var k = it.productName || ('产品' + it.productId); m[k] = (m[k] || 0) + Number(it.quantity || 0) });" +
  "var arr = Object.keys(m).map(function (k) { var o = { x: k }; o['计划产量'] = Math.round(m[k]); return o });" +
  "arr.sort(function (a, b) { return a['计划产量'] - b['计划产量'] });" +
  "return { dimensions: ['x', '计划产量'], source: arr.slice(-6) }",
  url('mes/pro/work-order/page?pageNo=1&pageSize=100')))

// B 右：WMS 仓库库存分布
components.push(component('BarCrossrange', [R[0], rowB, R[1], hB], Object.assign(barCrossOption(['x', '库存量'], ['库存量']), { title: chartTitle('仓库库存分布（WMS）') }),
  "return { dimensions: ['x', '库存量'], source: ((data && data.warehouseDistributionList) || []).map(function (it) { var o = { x: it.name }; o['库存量'] = it.quantity; return o }) }",
  url(WMS + 'inventory-summary')))

// 底部一行三张「窄高」图：标题/图例/网格都要压缩，否则 170px 高度里没有绘图区
const compactChart = (opt, text) => Object.assign(opt, {
  title: { show: true, text: text, left: 'center', top: 4, textStyle: { color: '#CFE3FFFF', fontSize: 14, fontWeight: 'bold' } },
  legend: { show: false },
  grid: { show: false, left: 52, right: 24, top: 44, bottom: 22, containLabel: true },
  xAxis: { show: true, type: 'category', axisLabel: { show: true, fontSize: 12, interval: 0 } },
  yAxis: { show: true, type: 'value', minInterval: 1 }
})
// 类目多、名字长的窄图：标签斜排，否则 612px 宽度里会挤成一团
const compactBarRotated = (opt, text) => {
  const o = compactChart(opt, text)
  o.grid = { show: false, left: 46, right: 20, top: 44, bottom: 52, containLabel: true }
  o.xAxis = { show: true, type: 'category', axisLabel: { show: true, fontSize: 12, interval: 0, rotate: 26 } }
  return o
}

// C 左：MES 生产资源概览
// 原来的「近 6 个月采购金额」依赖 ERP 采购单，演示库的 ERP 单据表是空的（接口恒为 0），
// 换成 MES summary 里现成的 6 个资源指标：设备三种状态 + 工单三种进度。
components.push(component('BarCommon', [8, 895, 612, 170], compactChart(barOption(['x', 'y1'], ['数量']), '生产资源概览（MES）'),
  "var d = data || {};" +
  "return { dimensions: ['x', 'y1'], source: [" +
  "{ x: '生产设备', y1: d.machineryProducing || 0 }," +
  "{ x: '停机设备', y1: d.machineryStop || 0 }," +
  "{ x: '维护设备', y1: d.machineryMaintenance || 0 }," +
  "{ x: '待生产工单', y1: d.workOrderPrepareCount || 0 }," +
  "{ x: '在制工单', y1: d.workOrderActiveCount || 0 }," +
  "{ x: '已完工工单', y1: d.workOrderFinishedCount || 0 }] }",
  url(MES + 'summary')))

// C 中：MES 设备类型分布
// 同样替换掉恒为 0 的「近 6 个月销售金额」，改用设备台账按类型统计台数。
components.push(component('BarCommon', [654, 895, 612, 170], compactBarRotated(barOption(['x', 'y1'], ['设备数']), '设备类型分布（MES）'),
  "var list = (data && data.list) || [];" +
  "var m = {};" +
  "list.forEach(function (it) { var k = it.machineryTypeName || '未分类'; m[k] = (m[k] || 0) + 1 });" +
  "var arr = Object.keys(m).map(function (k) { return { x: k, y1: m[k] } });" +
  "arr.sort(function (a, b) { return b.y1 - a.y1 });" +
  "return { dimensions: ['x', 'y1'], source: arr }",
  url('mes/dv/machinery/page?pageNo=1&pageSize=100')))

// C 右：WMS 库存 Top 商品（唯一有真实数量级的分组数据，用来看库存结构）
components.push(component('BarCommon', [1300, 895, 612, 170], compactChart(barOption(['x', 'y1'], ['库存量']), '库存 Top 5 商品（WMS）'),
  "return { dimensions: ['x', 'y1'], source: ((data && data.goodsShareList) || []).map(function (it) { return { x: it.name, y1: it.quantity } }) }",
  url(WMS + 'inventory-summary')))

// ---------------------------------------------------------------- 图表主题
// 直接读前端工程里的主题文件，避免在脚本里维护一份副本（找不到时退回空对象）。
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
let chartThemeSetting = {}
try {
  const here = dirname(fileURLToPath(import.meta.url))
  chartThemeSetting = JSON.parse(readFileSync(resolve(here, '../../txgy-ui-go-view/src/settings/chartThemes/global.theme.json'), 'utf8'))
} catch (e) {
  console.warn('未读取到 global.theme.json，图表主题使用默认值：' + e.message)
}
// ---------------------------------------------------------------- 画布配置
const content = {
  editCanvasConfig: {
    projectName: PROJECT_NAME,
    width: 1920,
    height: 1080,
    filterShow: false,
    hueRotate: 0,
    saturate: 1,
    contrast: 1,
    brightness: 1,
    opacity: 1,
    rotateZ: 0,
    rotateX: 0,
    rotateY: 0,
    skewX: 0,
    skewY: 0,
    blendMode: 'normal',
    background: '#041632FF',
    backgroundImage: null,
    selectColor: true,
    chartThemeColor: 'dark',
    // 与前端默认值保持一致：GoView 把 global.theme.json 存进 chartThemeSetting，
    // 预览时再 mergeTheme 回 ECharts option（坐标轴/图例配色的兜底来源）。
    chartThemeSetting: chartThemeSetting,
    previewScaleType: 'fit'
  },
  componentList: components,
  requestGlobalConfig: {
    requestDataPond: [],
    requestOriginUrl: '',
    requestInterval: 60,
    requestIntervalUnit: 'second',
    requestParams: {
      Body: { 'form-data': {}, 'x-www-form-urlencoded': {}, json: '', xml: '' },
      Header: {},
      Params: {}
    }
  }
}

// ---------------------------------------------------------------- 发布
const token = (await api('/system/auth/login', { method: 'POST', body: { username: USER, password: PASSWORD } })).accessToken
const page = await api('/report/go-view/project/my-page?pageNo=1&pageSize=100', { token })
const exist = (page.list || []).find((it) => it.name === PROJECT_NAME)
const payload = { name: PROJECT_NAME, status: 0, content: JSON.stringify(content), remark: '由 deploy/goview/seed-dashboard.mjs 生成：ERP/MES/WMS 综合监控大屏' }
let projectId
if (exist) {
  await api('/report/go-view/project/update', { method: 'PUT', token, body: Object.assign({ id: exist.id }, payload) })
  projectId = exist.id
  console.log('已更新项目 id=' + projectId)
} else {
  projectId = await api('/report/go-view/project/create', { method: 'POST', token, body: { name: PROJECT_NAME } })
  await api('/report/go-view/project/update', { method: 'PUT', token, body: Object.assign({ id: projectId }, payload) })
  console.log('已创建项目 id=' + projectId)
}
console.log('组件数：' + components.length + '，content 长度：' + payload.content.length)
console.log('设计器：' + API.replace(':48080', ':5273') + '/#/chart/home/' + projectId)
console.log('预览页：' + API.replace(':48080', ':5273') + '/#/chart/preview/' + projectId)
