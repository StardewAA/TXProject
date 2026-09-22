/**
 * 生成 / 更新 GoView 大屏项目「MES 生产车间数字孪生大屏」。
 *
 * 版面：顶部 8 个 KPI 数字条；主体三栏 —— 左栏两张饼图 + 工单状态横向柱，
 * 正中央是 Three.js 车间数字孪生场景（车间地板 / 工位光柱 / 设备状态色块 /
 * 悬浮工序链 + 流向动画），右栏是生产任务轮播表、各工序合格产出、报工质量分布；
 * 中央下方补充「各工序 计划 / 产出 / 合格」对比。
 *
 * 三维场景组件 MesWorkshop3D 自带取数（一次并发拉 7 个 MES 接口并轮询），
 * 不走大屏的数据源配置，所以它的 requestDataType 是静态，option 里只放 routeId / pollInterval。
 * 其余图表沿用 WMS 大屏那套「AJAX + JS 过滤器」的写法。
 *
 * 用到的接口：
 *   mes/md-workshop/page                     车间台账
 *   mes/md-workstation/page                  工位台账（含 workshopName，可做车间维度聚合）
 *   mes/dv/machinery/page                    设备台账（含状态，注意枚举 1 停机 / 2 生产中 / 3 维护中）
 *   mes/pro/task/page                        生产任务（含 processName / workstationName / 数量）
 *   mes/pro/feedback/page                    报工记录（含合格 / 不合格 / 报废数量）
 *   mes/home-statistics/summary              首页汇总（设备、工单、安灯计数）
 *   mes/home-statistics/work-order-status    工单状态分布
 *
 * 踩坑记录：
 *   1) 设备状态枚举是 MesDvMachineryStatusEnum：1 停机、2 生产中、3 维护中 —— 不是「1 运行」，
 *      照字面理解会把 8 台停机设备画成绿灯。
 *   2) home-statistics 下多数接口（production-trend / qc-pass-rate / andon-analysis /
 *      defect-analysis / feedback-user-analysis）在当前演示库里全返回 0 或空数组，
 *      直接拿来画图会是空图表；所以图表数据一律从分页台账接口现算。
 *   3) BarCommon / BarCrossrange 的静态 dataset.dimensions 必须与过滤器返回的 dimensions
 *      完全一致，否则会被内置 seriesItem 覆盖掉系列名与配色。
 *   4) 请求地址必须带 /admin-api，appendTokenAndTenant 才会补 Authorization / tenant-id。
 *
 * 用法：
 *   node deploy/goview/seed-mes-dashboard.mjs
 * 可选环境变量：TXGY_API_BASE / TXGY_TENANT / TXGY_USER / TXGY_PASSWORD
 */
const API = process.env.TXGY_API_BASE || 'http://127.0.0.1:48080'
const TENANT = process.env.TXGY_TENANT || '1'
const USER = process.env.TXGY_USER || 'admin'
const PASSWORD = process.env.TXGY_PASSWORD || 'admin123'
const PROJECT_NAME = 'MES 生产车间数字孪生大屏'
const DASH_TITLE = 'MES 智能制造执行监控大屏'
const REMARK = '由 deploy/goview/seed-mes-dashboard.mjs 生成：MES 车间数字孪生大屏'

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
const REQUEST = (requestUrl, requestDataType) => ({
  requestDataType,
  requestHttpType: 'get',
  requestUrl,
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
const url = (p) => '${location.origin}/admin-api/' + p

// 组件注册靠 chartConfig.key 找 ./components/{package}/{category}/{key}/index.vue
const CHART_CONFIG = {
  MesWorkshop3D: { key: 'MesWorkshop3D', chartKey: 'VMesWorkshop3D', conKey: 'VCMesWorkshop3D', title: '车间数字孪生', category: 'Three', categoryName: '三维', package: 'Decorates', chartFrame: 'common', image: 'mesWorkshop3d.png' },
  BarCommon: { key: 'BarCommon', chartKey: 'VBarCommon', conKey: 'VCBarCommon', title: '柱状图', category: 'Bars', categoryName: '柱状图', package: 'Charts', chartFrame: 'echarts', image: 'bar_x.png' },
  BarCrossrange: { key: 'BarCrossrange', chartKey: 'VBarCrossrange', conKey: 'VCBarCrossrange', title: '横向柱状图', category: 'Bars', categoryName: '柱状图', package: 'Charts', chartFrame: 'echarts', image: 'bar_y.png' },
  PieCommon: { key: 'PieCommon', chartKey: 'VPieCommon', conKey: 'VCPieCommon', title: '饼图', category: 'Pies', categoryName: '饼图', package: 'Charts', chartFrame: 'echarts', image: 'pie.png' },
  TableScrollBoard: { key: 'TableScrollBoard', chartKey: 'VTableScrollBoard', conKey: 'VCTableScrollBoard', title: '轮播列表', category: 'Tables', categoryName: '表格', package: 'Tables', chartFrame: 'common', image: 'table_scrollboard.png' },
  Number: { key: 'Number', chartKey: 'VNumber', conKey: 'VCNumber', title: '数字计数', category: 'Mores', categoryName: '更多', package: 'Decorates', chartFrame: 'common', image: 'number.png' },
  TextCommon: { key: 'TextCommon', chartKey: 'VTextCommon', conKey: 'VCTextCommon', title: '文字', category: 'Texts', categoryName: '文本', package: 'Informations', chartFrame: 'common', image: 'text_static.png' },
  TimeCommon: { key: 'TimeCommon', chartKey: 'VTimeCommon', conKey: 'VCTimeCommon', title: '通用时间', category: 'Mores', categoryName: '更多', package: 'Decorates', chartFrame: 'static', image: 'time.png' },
  Border13: { key: 'Border13', chartKey: 'VBorder13', conKey: 'VCBorder13', title: '边框-13', category: 'Borders', categoryName: '边框', package: 'Decorates', chartFrame: 'static', image: 'border13.png' }
}

let seq = 0
const uid = () => 'mesdash' + (++seq).toString().padStart(3, '0') + '000'

function component(key, attr, option, filter, requestUrl, dataType = 1) {
  return {
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
}

const chartTitle = (text, top = 10) => ({
  show: true,
  text,
  left: 'center',
  top,
  textStyle: { color: '#CFE3FFFF', fontSize: 16, fontWeight: 'bold' }
})

// ---------------------------------------------------------------- option 生成
const TRANSPARENT = 'rgba(0,0,0,0)'

function barOption(dims, seriesNames, { showLabel = true } = {}) {
  return {
    backgroundColor: TRANSPARENT,
    tooltip: { show: true, trigger: 'axis', axisPointer: { show: true, type: 'shadow' } },
    legend: { show: true, top: 38, textStyle: { color: '#B9B8CE' } },
    grid: { show: false, left: 62, right: 28, top: 84, bottom: 34, containLabel: true },
    xAxis: { show: true, type: 'category', axisLabel: { show: true, fontSize: 12, interval: 0 } },
    yAxis: { show: true, type: 'value', minInterval: 1 },
    dataset: { dimensions: dims, source: [] },
    series: seriesNames.map((n) => ({
      name: n,
      type: 'bar',
      barWidth: 20,
      label: { show: showLabel, position: 'top', color: '#fff', fontSize: 12 },
      itemStyle: { color: null, borderRadius: 3 }
    }))
  }
}

function barCrossOption(dims, seriesNames) {
  return {
    backgroundColor: TRANSPARENT,
    tooltip: { show: true, trigger: 'axis', axisPointer: { show: true, type: 'shadow' } },
    legend: { show: false },
    grid: { show: false, left: 96, right: 62, top: 76, bottom: 26, containLabel: true },
    xAxis: { show: true, type: 'value' },
    yAxis: { show: true, type: 'category', axisLabel: { show: true, fontSize: 12 } },
    dataset: { dimensions: dims, source: [] },
    series: seriesNames.map((n) => ({
      name: n,
      type: 'bar',
      barWidth: 16,
      label: { show: true, position: 'right', color: '#fff', fontSize: 12 },
      itemStyle: { color: null, borderRadius: 0 }
    }))
  }
}

function pieOption() {
  return {
    backgroundColor: TRANSPARENT,
    type: 'ring',
    isCarousel: false,
    tooltip: { show: true, trigger: 'item', formatter: '{b}: {@data1} ({d}%)' },
    // 图例放顶部、饼图居中：竖直图例 + 外置标签在 380 宽的面板里一定会互相挤
    legend: { show: true, orient: 'horizontal', left: 'center', top: 34, itemGap: 12, itemWidth: 12, itemHeight: 8, textStyle: { color: '#B9B8CE', fontSize: 11 } },
    dataset: { dimensions: ['product', 'data1'], source: [] },
    series: [
      {
        type: 'pie',
        encode: { itemName: 'product', value: 'data1' },
        radius: ['34%', '54%'],
        center: ['50%', '62%'],
        // 小扇区给一个最小角度，否则细到看不见、标签也会叠在一起
        minAngle: 4,
        roseType: false,
        avoidLabelOverlap: true,
        itemStyle: { show: true, borderRadius: 8, borderColor: '#041632FF', borderWidth: 2 },
        label: { show: true, position: 'outer', formatter: '{b} {@data1}', color: '#CFE3FFFF', fontSize: 11 },
        labelLine: { show: true, length: 6, length2: 6 },
        // 小扇区的外侧标签会互相压住，交给 ECharts 自动隐藏重叠项
        labelLayout: { hideOverlap: true },
        emphasis: { label: { show: true, fontSize: 14, fontWeight: 'bold' } }
      }
    ]
  }
}

function numberOption({ dataset, prefixText = '', suffixText = '', color = '#4A9EF8FF', size = 28, precision = 0 }) {
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

function tableOption(header, columnWidth, align) {
  return {
    header,
    dataset: [],
    index: true,
    indexHeader: '#',
    columnWidth,
    align,
    rowNum: 6,
    waitTime: 4,
    headerHeight: 34,
    carousel: 'single',
    hoverPause: true,
    headerBGC: '#0F4C75FF',
    oddRowBGC: '#08243AFF',
    evenRowBGC: '#0B2E49FF'
  }
}

const borderOption = (a, b) => ({ colors: [a, b], backgroundColor: '#00000000' })

// ---------------------------------------------------------------- 数据源
const WORKSHOP_PAGE = 'mes/md-workshop/page?pageNo=1&pageSize=1'
const WORKSTATION_PAGE = 'mes/md-workstation/page?pageNo=1&pageSize=200'
const MACHINERY_PAGE = 'mes/dv/machinery/page?pageNo=1&pageSize=200'
const TASK_PAGE = 'mes/pro/task/page?pageNo=1&pageSize=200'
const FEEDBACK_PAGE = 'mes/pro/feedback/page?pageNo=1&pageSize=200'
const TASK_PAGE_ONE = 'mes/pro/task/page?pageNo=1&pageSize=1'
const SUMMARY = 'mes/home-statistics/summary'
const WO_STATUS = 'mes/home-statistics/work-order-status'

// 状态字典：后端 MesProTaskStatusEnum（复用 MesOrderStatusConstants 的编号）
const TASK_STATUS_TEXT = "var st = {0:'草稿',1:'已确认',4:'已完成',5:'已取消'};"

const components = []

// ---------------------------------------------------------------- 顶部标题
components.push(component('TextCommon', [520, 8, 880, 52], textOption(DASH_TITLE, { fontSize: 32, fontColor: '#4A9EF8FF', fontWeight: 'bold', letterSpacing: 4 }), undefined, '', 0))
components.push(component('TextCommon', [24, 16, 500, 34], textOption('车间 / 工位 / 设备 / 工序 实时孪生', { fontSize: 14, fontColor: '#5A87B8FF', letterSpacing: 1 }), undefined, '', 0))
components.push(component('TimeCommon', [1620, 16, 280, 42], { timeSize: 20, timeLineHeight: 34, timeTextIndent: 1, timeColor: '#9FD3FFFF', fontWeight: 'normal', showShadow: true, hShadow: 0, vShadow: 0, blurShadow: 8, colorShadow: '#0075ff' }, undefined, '', 0))

// ---------------------------------------------------------------- KPI 数字条
const KPI_Y = 62
const KPI_H = 100
const kpis = [
  { label: '车间总数', path: WORKSHOP_PAGE, filter: 'return Number(data.total) || 0', suffix: ' 个' },
  { label: '工位总数', path: 'mes/md-workstation/page?pageNo=1&pageSize=1', filter: 'return Number(data.total) || 0', suffix: ' 个', color: '#7FD8F7FF' },
  { label: '设备总数', path: SUMMARY, filter: 'return Number(data.machineryTotal) || 0', suffix: ' 台' },
  { label: '生产中', path: SUMMARY, filter: 'return Number(data.machineryProducing) || 0', suffix: ' 台', color: '#22D38AFF' },
  { label: '停机', path: SUMMARY, filter: 'return Number(data.machineryStop) || 0', suffix: ' 台', color: '#8FA9C4FF' },
  { label: '在制工单', path: SUMMARY, filter: 'return Number(data.workOrderActiveCount) || 0', suffix: ' 张', color: '#FFC85CFF' },
  { label: '未处置安灯', path: SUMMARY, filter: 'return Number(data.andonActiveCount) || 0', suffix: ' 项', color: '#FF4D5EFF' },
  { label: '生产任务', path: TASK_PAGE_ONE, filter: 'return Number(data.total) || 0', suffix: ' 项', color: '#4ADFA8FF' }
]
kpis.forEach((k, i) => {
  const x = 8 + i * 240
  components.push(component('Border13', [x, KPI_Y, 232, KPI_H], borderOption('#1D4F8AFF', '#2F7FC9FF'), undefined, '', 0))
})
kpis.forEach((k, i) => {
  const x = 8 + i * 240
  components.push(component('TextCommon', [x, KPI_Y + 6, 232, 26], textOption(k.label, { fontSize: 15, fontColor: '#8FB6E8FF', letterSpacing: 1 }), undefined, '', 0))
  components.push(component('Number', [x, KPI_Y + 32, 232, 62], numberOption({
    dataset: 0,
    suffixText: k.suffix || '',
    size: 28,
    color: k.color || '#4A9EF8FF'
  }), k.filter, url(k.path)))
})

// ---------------------------------------------------------------- 版面坐标
const MID_Y = 172
const L_X = [8, 380]
const C_X = [396, 1136]
const R_X = [1540, 380]
const L1 = [MID_Y, 296]
const L2 = [MID_Y + 308, 296]
const L3 = [MID_Y + 616, 276]
const FLOW_Y = MID_Y
const FLOW_H = 660
const FLOW_SUB_Y = MID_Y + 672
const FLOW_SUB_H = 220
const R1 = [MID_Y, 330]
const R2 = [MID_Y + 342, 290]
const R3 = [MID_Y + 644, 248]

// 面板边框先入列，保证图表画在边框之上（预览按 componentList 顺序叠 DOM）
const PANELS = [
  [L_X[0], L1[0], L_X[1], L1[1]],
  [L_X[0], L2[0], L_X[1], L2[1]],
  [L_X[0], L3[0], L_X[1], L3[1]],
  [R_X[0], R1[0], R_X[1], R1[1]],
  [R_X[0], R2[0], R_X[1], R2[1]],
  [R_X[0], R3[0], R_X[1], R3[1]]
]
// 中央孪生场景给最亮的边框，视觉重心落在这里
components.push(component('Border13', [C_X[0], FLOW_Y, C_X[1], FLOW_H], borderOption('#3F9BE0FF', '#7FE3FFFF'), undefined, '', 0))
components.push(component('Border13', [C_X[0], FLOW_SUB_Y, C_X[1], FLOW_SUB_H], borderOption('#1D4F8AFF', '#2F7FC9FF'), undefined, '', 0))
PANELS.forEach((p) => components.push(component('Border13', p, borderOption('#1D4F8AFF', '#2F7FC9FF'), undefined, '', 0)))

// ---------------------------------------------------------------- 中央：车间数字孪生
// 组件自己并发拉 7 个 MES 接口并轮询，所以这里是静态数据源
components.push(component('MesWorkshop3D', [C_X[0], FLOW_Y, C_X[1], FLOW_H], {
  dataset: {},
  routeId: 1,
  pollInterval: 30,
  autoRotate: false
}, undefined, '', 0))
components.push(component('TextCommon', [C_X[0], FLOW_Y + 8, C_X[1], 32], textOption('生产车间数字孪生', { fontSize: 17, fontColor: '#CFE3FFFF', fontWeight: 'bold', letterSpacing: 2 }), undefined, '', 0))
// 操作与图例说明放底部，避免和标题在同一行挤在一起
components.push(component('TextCommon', [C_X[0], FLOW_Y + FLOW_H - 34, C_X[1], 28], textOption('鼠标拖拽旋转 / 滚轮缩放　·　设备配色：停机 / 生产中 / 维护中　·　工位：安灯未处置转红脉冲', { fontSize: 12, fontColor: '#7FA8D0FF', letterSpacing: 0 }), undefined, '', 0))

// ---------------------------------------------------------------- 左栏
components.push(component('PieCommon', [L_X[0], L1[0], L_X[1], L1[1]], Object.assign(pieOption(), { title: chartTitle('设备状态分布') }),
  "var N = {1:'停机',2:'生产中',3:'维护中'};" +
  "var list = (data && data.list) || [];" +
  "var m = {};" +
  "list.forEach(function (it) { var k = N[it.status] || ('状态' + it.status); m[k] = (m[k] || 0) + 1 });" +
  "return { dimensions: ['product', 'data1'], source: Object.keys(m).map(function (k) { return { product: k, data1: m[k] } }) }",
  url(MACHINERY_PAGE)))

components.push(component('PieCommon', [L_X[0], L2[0], L_X[1], L2[1]], Object.assign(pieOption(), { title: chartTitle('各车间工位分布') }),
  "var list = (data && data.list) || [];" +
  "var m = {};" +
  "list.forEach(function (it) { var k = it.workshopName || '未分配'; m[k] = (m[k] || 0) + 1 });" +
  "return { dimensions: ['product', 'data1'], source: Object.keys(m).map(function (k) { return { product: k, data1: m[k] } }) }",
  url(WORKSTATION_PAGE)))

components.push(component('BarCrossrange', [L_X[0], L3[0], L_X[1], L3[1]], Object.assign(barCrossOption(['x', '工单数'], ['工单数']), { title: chartTitle('工单状态分布') }),
  "var list = data || [];" +
  "var src = list.filter(function (it) { return Number(it.count) > 0 })" +
  "  .map(function (it) { return { x: it.statusName || ('状态' + it.status), '工单数': Number(it.count) || 0 } });" +
  "return { dimensions: ['x', '工单数'], source: src }",
  url(WO_STATUS)))

// ---------------------------------------------------------------- 中央下方：工序维度对比
components.push(component('BarCommon', [C_X[0], FLOW_SUB_Y, C_X[1], FLOW_SUB_H],
  // 同一 x 上三根柱子数值相差三个数量级，柱顶数值必然互相压，这里交给 tooltip
  Object.assign(barOption(['x', '计划数量', '产出数量', '合格数量'], ['计划数量', '产出数量', '合格数量'], { showLabel: false }), {
    title: chartTitle('各工序计划 / 产出 / 合格对比', 10),
    grid: { show: false, left: 66, right: 28, top: 84, bottom: 46, containLabel: true }
  }),
  "var list = (data && data.list) || [];" +
  "var m = {};" +
  "list.forEach(function (it) {" +
  "  var k = it.processName || '未指定工序';" +
  "  if (!m[k]) m[k] = { '计划数量': 0, '产出数量': 0, '合格数量': 0 };" +
  "  m[k]['计划数量'] += Number(it.quantity) || 0;" +
  "  m[k]['产出数量'] += Number(it.producedQuantity) || 0;" +
  "  m[k]['合格数量'] += Number(it.qualifyQuantity) || 0;" +
  "});" +
  "var src = Object.keys(m).map(function (k) { return {" +
  "  x: k, '计划数量': Math.round(m[k]['计划数量']), '产出数量': Math.round(m[k]['产出数量']), '合格数量': Math.round(m[k]['合格数量'])" +
  "} });" +
  "return { dimensions: ['x', '计划数量', '产出数量', '合格数量'], source: src }",
  url(TASK_PAGE)))

// ---------------------------------------------------------------- 右栏
// TableScrollBoard 的 index:true 会自己往表头前面插一列序号，header 里不要再写 '#'；
// columnWidth 是「按列序从前往后」生效，剩下的列平分剩余宽度（见组件里的 calcWidths）。
components.push(component('TextCommon', [R_X[0], R1[0] + 6, R_X[1], 28], textOption('生产任务明细', { fontSize: 16, fontColor: '#CFE3FFFF', fontWeight: 'bold' }), undefined, '', 0))
components.push(component('TableScrollBoard', [R_X[0], R1[0] + 36, R_X[1], R1[1] - 36],
  tableOption(['工序', '工位', '计划', '产出', '状态'], [36, 78, 104, 48, 48], ['center', 'center', 'left', 'right', 'right', 'center']),
  TASK_STATUS_TEXT +
  "var list = (data && data.list) || [];" +
  "return list.map(function (it) { return [" +
  "  it.processName || ''," +
  "  (it.workstationName || '').replace('验收报工-', '')," +
  "  Math.round(Number(it.quantity) || 0)," +
  "  Math.round(Number(it.producedQuantity) || 0)," +
  "  st[it.status] || ('状态' + it.status)" +
  "] })",
  url(TASK_PAGE)))

components.push(component('BarCrossrange', [R_X[0], R2[0], R_X[1], R2[1]], Object.assign(barCrossOption(['x', '合格产出'], ['合格产出']), { title: chartTitle('各工序合格产出') }),
  "var list = (data && data.list) || [];" +
  "var m = {};" +
  "list.forEach(function (it) {" +
  "  var k = it.processName || '未指定工序';" +
  "  m[k] = (m[k] || 0) + (Number(it.qualifiedQuantity) || 0);" +
  "});" +
  "var src = Object.keys(m).map(function (k) { return { x: k, '合格产出': Math.round(m[k]) } })" +
  "  .sort(function (a, b) { return b['合格产出'] - a['合格产出'] }).slice(0, 6);" +
  "return { dimensions: ['x', '合格产出'], source: src }",
  url(FEEDBACK_PAGE)))

components.push(component('PieCommon', [R_X[0], R3[0], R_X[1], R3[1]], Object.assign(pieOption(), { title: chartTitle('报工质量分布') }),
  "var list = (data && data.list) || [];" +
  "var ok = 0, ng = 0, labor = 0, material = 0;" +
  "list.forEach(function (it) {" +
  "  ok += Number(it.qualifiedQuantity) || 0;" +
  "  ng += Number(it.unqualifiedQuantity) || 0;" +
  "  labor += Number(it.laborScrapQuantity) || 0;" +
  "  material += Number(it.materialScrapQuantity) || 0;" +
  "});" +
  "var src = [{ product: '合格', data1: Math.round(ok) }, { product: '不合格', data1: Math.round(ng) }," +
  "  { product: '人工报废', data1: Math.round(labor) }, { product: '物料报废', data1: Math.round(material) }]" +
  "  .filter(function (it) { return it.data1 > 0 });" +
  "return { dimensions: ['product', 'data1'], source: src }",
  url(FEEDBACK_PAGE)))

// ---------------------------------------------------------------- 图表主题
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
    filterShow: false, hueRotate: 0, saturate: 1, contrast: 1, brightness: 1, opacity: 1,
    rotateZ: 0, rotateX: 0, rotateY: 0, skewX: 0, skewY: 0, blendMode: 'normal',
    background: '#041632FF',
    backgroundImage: null,
    selectColor: true,
    chartThemeColor: 'dark',
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

if (process.env.TXGY_DRY_RUN === '1') {
  const json = JSON.stringify(content)
  console.log('dry-run：组件数 ' + components.length + '，字符数 ' + json.length + '，字节数 ' + Buffer.byteLength(json, 'utf8'))
  process.exit(0)
}

// ---------------------------------------------------------------- 发布
const token = (await api('/system/auth/login', { method: 'POST', body: { username: USER, password: PASSWORD } })).accessToken
const page = await api('/report/go-view/project/my-page?pageNo=1&pageSize=100', { token })
const exist = (page.list || []).find((it) => it.name === PROJECT_NAME)
const payload = { name: PROJECT_NAME, status: 0, content: JSON.stringify(content), remark: REMARK }
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
