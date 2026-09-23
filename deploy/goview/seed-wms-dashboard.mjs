/**
 * 生成 / 更新 GoView 大屏项目「WMS 仓储监控大屏（全国仓库地图）」。
 *
 * 布局：正中央一张中国地图（7 个仓库的落点、到上海中心仓的流向线、按省份在库量着色），
 * 四周是国内仓库常用的看板组合：KPI 数字条、仓库库存分布、库存商品占比、
 * 单据类型 × 状态、仓库 × 商品在库热力图、入库 / 出库单据轮播表、入库金额走势。
 *
 * 为什么用脚本而不是在浏览器里手拖：大屏内容存在 report_go_view_project.content 里，
 * 就是一份 JSON（editCanvasConfig + componentList + requestGlobalConfig）。脚本生成便于
 * 评审、改口径、重复部署；数据全部来自现有 WMS 接口，组件用 AJAX 请求 + JS 过滤器把
 * 接口返回转成 ECharts dataset。
 *
 * 用到的接口：
 *   wms/home-statistics/inventory-summary   库存总量 / 商品占比 / 各仓库存
 *   wms/home-statistics/order-summary       4 种单据 × 3 种状态
 *   wms/warehouse/page                      仓库台账（数量）
 *   wms/item/page                           商品台账（数量）
 *   wms/inventory/page?type=warehouse       库存明细（仓库 × 商品，热力图数据源）
 *   wms/receipt-order/page                  入库单
 *   wms/shipment-order/page                 出库单
 *
 * 踩坑记录（与 seed-dashboard.mjs 一致）：
 *   1) requestUrl 用 ${location.origin}/admin-api/** 走大屏自身 Nginx 反代（同源免 CORS），
 *      URL 里必须带 /admin-api，appendTokenAndTenant 才会补上 Authorization / tenant-id。
 *   2) MapBase 的 dataset 约定：point[{name, value:[lng,lat,val]}] 打点、
 *      line[{coords:[[lng,lat],[lng,lat]]}] 飞线、map[{name:'广东省', value:N}] 省份色块
 *      （省份名必须与 mapGeojson/china.json 完全一致，带「省 / 市 / 自治区」后缀）。
 *   3) BarCommon / BarCrossrange / LineCommon 在 dataset.dimensions 条数发生变化时，
 *      会拿内置的 seriesItem 覆盖 option.series（颜色、堆叠、系列名全丢）。所以静态
 *      dataset.dimensions 必须与 filter 返回的 dimensions 完全一致；数值维度名直接起成
 *      业务名，ECharts 会用维度名当系列名，图例里才不会冒出 y1 / y2。
 *   4) Heatmap 的 dataset 是 {xAxis:[], yAxis:[], seriesData:[[xIdx,yIdx,val]]}，用下标不用标签。
 *   5) 轮播表 TableScrollBoard 的 dataset 是二维数组，列名走 option.header。
 *
 * 用法（宿主机已部署 goview 容器时）：
 *   node deploy/goview/seed-wms-dashboard.mjs
 * 可选环境变量：
 *   TXGY_API_BASE  默认 http://127.0.0.1:48080
 *   TXGY_TENANT    默认 1（对应租户名 OPENLAB BS）
 *   TXGY_USER / TXGY_PASSWORD  默认 admin / admin123
 */
const API = process.env.TXGY_API_BASE || 'http://127.0.0.1:48080'
const TENANT = process.env.TXGY_TENANT || '1'
const USER = process.env.TXGY_USER || 'admin'
const PASSWORD = process.env.TXGY_PASSWORD || 'admin123'
const PROJECT_NAME = 'WMS 仓储监控大屏（全国仓库地图）'
const DASH_TITLE = 'WMS 智能仓储监控大屏'
const REMARK = '由 deploy/goview/seed-wms-dashboard.mjs 生成：WMS 仓储监控大屏（全国仓库地图）'

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
  requestDataType, // 0 静态 / 1 AJAX
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
// ${location.origin} 是 GoView 在请求前用 new Function 展开的模板（见 src/api/http.ts），
// 这样大屏不管部署在哪个域名下都能请求到自己那一层 Nginx 的同源 /admin-api。
const url = (p) => '${location.origin}/admin-api/' + p

// 组件注册靠 chartConfig.key 找 ./components/{package}/{category}/{key}/index.vue，
// 靠 chartConfig.chartKey 拿到全局注册名的组件，两个都不能写错。
const CHART_CONFIG = {
  BarCommon: { key: 'BarCommon', chartKey: 'VBarCommon', conKey: 'VCBarCommon', title: '柱状图', category: 'Bars', categoryName: '柱状图', package: 'Charts', chartFrame: 'echarts', image: 'bar_x.png' },
  BarCrossrange: { key: 'BarCrossrange', chartKey: 'VBarCrossrange', conKey: 'VCBarCrossrange', title: '横向柱状图', category: 'Bars', categoryName: '柱状图', package: 'Charts', chartFrame: 'echarts', image: 'bar_y.png' },
  LineCommon: { key: 'LineCommon', chartKey: 'VLineCommon', conKey: 'VCLineCommon', title: '折线图', category: 'Lines', categoryName: '折线图', package: 'Charts', chartFrame: 'echarts', image: 'line.png' },
  PieCommon: { key: 'PieCommon', chartKey: 'VPieCommon', conKey: 'VCPieCommon', title: '饼图', category: 'Pies', categoryName: '饼图', package: 'Charts', chartFrame: 'echarts', image: 'pie.png' },
  Heatmap: { key: 'Heatmap', chartKey: 'VHeatmap', conKey: 'VCHeatmap', title: '热力图', category: 'Mores', categoryName: '更多', package: 'Charts', chartFrame: 'echarts', image: 'heatmap.png' },
  MapBase: { key: 'MapBase', chartKey: 'VMapBase', conKey: 'VCMapBase', title: '地图(可选省份)', category: 'Maps', categoryName: '地图', package: 'Charts', chartFrame: 'common', image: 'map.png' },
  TableScrollBoard: { key: 'TableScrollBoard', chartKey: 'VTableScrollBoard', conKey: 'VCTableScrollBoard', title: '轮播列表', category: 'Tables', categoryName: '表格', package: 'Tables', chartFrame: 'common', image: 'table_scrollboard.png' },
  Number: { key: 'Number', chartKey: 'VNumber', conKey: 'VCNumber', title: '数字计数', category: 'Mores', categoryName: '更多', package: 'Decorates', chartFrame: 'common', image: 'number.png' },
  TextCommon: { key: 'TextCommon', chartKey: 'VTextCommon', conKey: 'VCTextCommon', title: '文字', category: 'Texts', categoryName: '文本', package: 'Informations', chartFrame: 'common', image: 'text_static.png' },
  TimeCommon: { key: 'TimeCommon', chartKey: 'VTimeCommon', conKey: 'VCTimeCommon', title: '通用时间', category: 'Mores', categoryName: '更多', package: 'Decorates', chartFrame: 'static', image: 'time.png' },
  Border13: { key: 'Border13', chartKey: 'VBorder13', conKey: 'VCBorder13', title: '边框-13', category: 'Borders', categoryName: '边框', package: 'Decorates', chartFrame: 'static', image: 'border13.png' }
}

let seq = 0
const uid = () => 'wmsdash' + (++seq).toString().padStart(3, '0') + '000'

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
// 透明的图表底：面板边框（Border13）画在图表下面，图表自己不能有背景色挡住它
const TRANSPARENT = 'rgba(0,0,0,0)'

function lineOption(dims, seriesNames, lineColor) {
  return {
    backgroundColor: TRANSPARENT,
    tooltip: { show: true, trigger: 'axis' },
    legend: { show: true, top: 36, textStyle: { color: '#B9B8CE' } },
    grid: { show: false, left: 76, right: 30, top: 78, bottom: 44, containLabel: true },
    xAxis: { show: true, type: 'category', axisLabel: { show: true, fontSize: 11, interval: 0 } },
    yAxis: { show: true, type: 'value', axisLabel: { show: true, fontSize: 11 } },
    dataset: { dimensions: dims, source: [] },
    series: seriesNames.map((n) => ({
      name: n,
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 7,
      areaStyle: { opacity: 0.18 },
      label: { show: false },
      itemStyle: { color: lineColor || null, borderRadius: 0 },
      lineStyle: { type: 'solid', width: 3, color: lineColor || null }
    }))
  }
}
function barOption(dims, seriesNames) {
  return {
    backgroundColor: TRANSPARENT,
    tooltip: { show: true, trigger: 'axis', axisPointer: { show: true, type: 'shadow' } },
    legend: { show: true, top: 36, textStyle: { color: '#B9B8CE' } },
    grid: { show: false, left: 56, right: 26, top: 78, bottom: 34, containLabel: true },
    xAxis: { show: true, type: 'category', axisLabel: { show: true, fontSize: 12, interval: 0 } },
    yAxis: { show: true, type: 'value', minInterval: 1 },
    dataset: { dimensions: dims, source: [] },
    series: seriesNames.map((n) => ({
      name: n,
      type: 'bar',
      barWidth: 22,
      label: { show: true, position: 'top', color: '#fff', fontSize: 12 },
      itemStyle: { color: null, borderRadius: 3 }
    }))
  }
}
function barCrossOption(dims, seriesNames) {
  return {
    backgroundColor: TRANSPARENT,
    tooltip: { show: true, trigger: 'axis', axisPointer: { show: true, type: 'shadow' } },
    legend: { show: false, top: 36, textStyle: { color: '#B9B8CE' } },
    grid: { show: false, left: 92, right: 74, top: 74, bottom: 26, containLabel: true },
    xAxis: { show: true, type: 'value' },
    yAxis: { show: true, type: 'category', axisLabel: { show: true, fontSize: 12 } },
    dataset: { dimensions: dims, source: [] },
    series: seriesNames.map((n) => ({
      name: n,
      type: 'bar',
      barWidth: 15,
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
    // 环形图放左边、图例竖排在右边：面板是「宽而矮」的，标签放外侧一圈会和图例打架。
    // 用 left 而不是 right 定位：ECharts 主题里带了 legend.left=center，同时给 right 时 left 会赢。
    legend: { show: true, orient: 'vertical', left: '64%', top: 'middle', itemGap: 10, textStyle: { color: '#B9B8CE', fontSize: 12 } },
    dataset: { dimensions: ['product', 'data1'], source: [] },
    series: [
      {
        type: 'pie',
        // dataset 形式的饼图里 {c} 拿到的是整行对象，取值必须用 {@维度名} 模板
        encode: { itemName: 'product', value: 'data1' },
        radius: ['34%', '54%'],
        center: ['30%', '56%'],
        roseType: false,
        // 扇区多了外侧标签会互相压，交给 ECharts 自己错开
        avoidLabelOverlap: true,
        itemStyle: { show: true, borderRadius: 8, borderColor: '#041632FF', borderWidth: 2 },
        label: { show: true, position: 'outer', formatter: '{@data1} 件', color: '#CFE3FFFF', fontSize: 11 },
        labelLine: { show: true, length: 8, length2: 8 },
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

// ---------------------------------------------------------------- 地图底稿
// 仓库 → 省份 / 经纬度。wms/warehouse 接口的 address、city 都是 null（演示库没维护地址字段），
// 所以坐标按仓库名硬编码；省份名必须与 mapGeojson/china.json 的 properties.name 完全一致。
const WAREHOUSE_GEO = {
  '上海仓': { p: '上海市', c: [121.4737, 31.2304], off: [16, 8], r: '' },
  '北京仓': { p: '北京市', c: [116.4053, 39.905], off: [14, -4], r: '' },
  '广州仓': { p: '广东省', c: [113.2644, 23.1291], off: [-16, -6], r: '华南区域仓' },
  '深圳仓': { p: '广东省', c: [114.0579, 22.5431], off: [14, 12], r: '珠三角前置仓' },
  '成都仓': { p: '四川省', c: [104.0665, 30.5723], off: [-16, 0], r: '西南区域仓' },
  '武汉仓': { p: '湖北省', c: [114.3055, 30.5928], off: [-16, -10], r: '华中区域仓' },
  '杭州仓': { p: '浙江省', c: [120.1551, 30.2741], off: [-16, 14], r: '华东电商仓' }
}
// off 是每个仓库标签的像素偏移（上海 / 杭州 / 武汉在地图上挨得近，不偏移会叠成一团）；
// r 是仓库台账里的备注（区域定位），统计接口不返回，挂在打点的 name 上当作地址补充。
// 上海仓在库量最高，作为飞线的中心枢纽
const HUB_NAME = '上海仓'
const HUB_COORD = WAREHOUSE_GEO[HUB_NAME].c
// visualMap 的分段：由高到低，颜色同步由亮到暗（pieces[0] 取 inRange.color[0]）
const MAP_PIECES = [
  { gte: 500, label: '500 件以上' },
  { gte: 300, lte: 499, label: '300 - 499 件' },
  { gte: 200, lte: 299, label: '200 - 299 件' },
  { gte: 100, lte: 199, label: '100 - 199 件' },
  { lte: 99, label: '100 件以下' }
]
const MAP_COLORS = ['#7fd8f7', '#4fb8e8', '#2f90c9', '#21689c', '#16496e']
// 设计器里首次打开时的静态数据（与当前演示库一致），接口回来后会被真实数据覆盖
const MAP_STATIC = {
  // 打点带 per-item 的 label：标签文本和偏移都按仓库预先算好，
  // ECharts 会用数据项上的 label 覆盖系列级的 label，避免华东几个仓的标签叠在一起。
  point: [
    { name: '上海仓 在库 608 件', value: [121.4737, 31.2304, 608], label: { offset: [16, 8], formatter: '上海仓  608 件' } },
    { name: '广州仓 在库 350 件 · 华南区域仓', value: [113.2644, 23.1291, 350], label: { offset: [-16, -6], formatter: '广州仓  350 件' } },
    { name: '杭州仓 在库 322 件 · 华东电商仓', value: [120.1551, 30.2741, 322], label: { offset: [-16, 14], formatter: '杭州仓  322 件' } },
    { name: '北京仓 在库 298 件', value: [116.4053, 39.905, 298], label: { offset: [14, -4], formatter: '北京仓  298 件' } },
    { name: '武汉仓 在库 209 件 · 华中区域仓', value: [114.3055, 30.5928, 209], label: { offset: [-16, -10], formatter: '武汉仓  209 件' } },
    { name: '成都仓 在库 156 件 · 西南区域仓', value: [104.0665, 30.5723, 156], label: { offset: [-16, 0], formatter: '成都仓  156 件' } },
    { name: '深圳仓 在库 72 件 · 珠三角前置仓', value: [114.0579, 22.5431, 72], label: { offset: [14, 12], formatter: '深圳仓  72 件' } }
  ],
  map: [
    { name: '上海市', value: 608 },
    { name: '广东省', value: 422 },
    { name: '浙江省', value: 322 },
    { name: '北京市', value: 298 },
    { name: '湖北省', value: 209 },
    { name: '四川省', value: 156 }
  ]
}
function mapOption() {
  return {
    backgroundColor: TRANSPARENT,
    dataset: {
      point: MAP_STATIC.point,
      line: Object.keys(WAREHOUSE_GEO).filter((n) => n !== HUB_NAME).map((n) => ({ coords: [WAREHOUSE_GEO[n].c, HUB_COORD] })),
      map: MAP_STATIC.map
    },
    // adcode china = 全国地图；enter:false 关掉下钻，避免误点后地图跳到某个省
    mapRegion: { adcode: 'china', showHainanIsLands: true, enter: false, backSize: 20, backColor: '#ffffff' },
    tooltip: { show: true, trigger: 'item' },
    visualMap: {
      show: true,
      // 只给 series[1]（省份色块）上色：series[0] 是打点，series[2] 是飞线
      seriesIndex: 1,
      orient: 'vertical',
      left: 24,
      bottom: 36,
      pieces: MAP_PIECES,
      inRange: { color: MAP_COLORS },
      textStyle: { color: '#CFE3FFFF', fontSize: 12 }
    },
    geo: { show: false, type: 'map', roam: false, map: 'china', selectedMode: false, zoom: 1.05 },
    series: [
      {
        name: '',
        type: 'effectScatter',
        coordinateSystem: 'geo',
        zlevel: 3,
        symbolSize: 10,
        legendHoverLink: true,
        showEffectOn: 'render',
        rippleEffect: { scale: 5, color: '#7fd8f7', brushType: 'fill' },
        tooltip: {
          show: true,
          backgroundColor: 'rgba(0,0,0,.6)',
          borderColor: 'rgba(147, 235, 248, .8)',
          textStyle: { color: '#FFF' },
          formatter: '{b}'
        },
        label: {
          formatter: '{b}',
          fontSize: 12,
          fontWeight: 'bold',
          offset: [10, 0],
          position: 'right',
          textBorderColor: '#041632FF',
          textShadowColor: '#000',
          textShadowBlur: 6,
          textBorderWidth: 2,
          color: '#FFFFFF',
          show: true
        },
        itemStyle: {
          color: '#FFFFFF',
          borderColor: 'rgba(225,255,255,2)',
          borderWidth: 4,
          shadowColor: '#E1FFFF',
          shadowBlur: 10
        },
        data: MAP_STATIC.point,
        encode: { value: 2 }
      },
      {
        name: '区域',
        type: 'map',
        map: 'china',
        data: MAP_STATIC.map,
        selectedMode: false,
        zoom: 1,
        geoIndex: 1,
        tooltip: {
          show: true,
          backgroundColor: '#00000060',
          borderColor: 'rgba(147, 235, 248, 0.8)',
          textStyle: { color: '#FFFFFF', fontSize: 12 }
        },
        label: { show: true, color: '#9FC7E8FF', fontSize: 10 },
        emphasis: {
          disabled: false,
          label: { color: '#FFFFFF', fontSize: 12 },
          itemStyle: { areaColor: '#389BB7', shadowColor: '#389BB7', borderWidth: 1 }
        },
        itemStyle: {
          borderColor: '#3D8FC0FF',
          borderWidth: 1,
          areaColor: '#0B2B47FF',
          shadowColor: '#80D9F842',
          shadowOffsetX: -2,
          shadowOffsetY: 2,
          shadowBlur: 10
        }
      },
      {
        type: 'lines',
        zlevel: 2,
        effect: { show: true, period: 5, trailLength: 0.4, symbol: 'arrow', symbolSize: 7 },
        // ECharts 5 已经不再读 lineStyle.normal，两个都写上保证飞线有颜色 / 粗细 / 弯曲度
        lineStyle: { color: '#4FB6D2FF', width: 1.6, opacity: 0.7, curveness: 0.25, normal: { color: '#4FB6D2FF', width: 1.6, opacity: 0.7, curveness: 0.25 } },
        data: []
      }
    ]
  }
}

function heatmapOption() {
  return {
    backgroundColor: TRANSPARENT,
    title: chartTitle('仓库 × 商品 在库量热力图（WMS）'),
    tooltip: { show: true, position: 'top' },
    grid: { show: false, left: 82, right: 24, top: 46, bottom: 68, containLabel: true },
    xAxis: { show: true, type: 'category', data: [], splitArea: { show: true }, axisLabel: { show: true, fontSize: 10, interval: 0, rotate: 22 } },
    yAxis: { show: true, type: 'category', data: [], splitArea: { show: true }, axisLabel: { show: true, fontSize: 11 } },
    visualMap: {
      show: true,
      min: 0,
      max: 400,
      calculable: false,
      orient: 'horizontal',
      left: 'center',
      bottom: 12,
      itemWidth: 14,
      itemHeight: 100,
      text: ['400', '0'],
      textStyle: { color: '#B9B8CE', fontSize: 11 },
      inRange: { color: ['#123f5a', '#1d6f96', '#2fa3d4', '#7fd8f7'] }
    },
    dataset: { xAxis: [], yAxis: [], seriesData: [] },
    series: [
      {
        name: '在库量',
        type: 'heatmap',
        data: [],
        label: { show: true, fontSize: 10, color: '#EAF7FF' },
        emphasis: { itemStyle: { borderColor: '#93EBF8', borderWidth: 1, shadowBlur: 10, shadowColor: 'rgba(0, 0, 0, 0.5)' } },
        progressive: 1000,
        animation: false
      }
    ]
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
    rowNum: 5,
    waitTime: 4,
    headerHeight: 36,
    carousel: 'single',
    hoverPause: true,
    headerBGC: '#0F4C75FF',
    oddRowBGC: '#08243AFF',
    evenRowBGC: '#0B2E49FF'
  }
}

const borderOption = (a, b) => ({ colors: [a, b], backgroundColor: '#00000000' })

// ---------------------------------------------------------------- 数据源
const WMS = 'wms/home-statistics/'
const RECEIPT_PAGE = 'wms/receipt-order/page?pageNo=1&pageSize=100'
const SHIPMENT_PAGE = 'wms/shipment-order/page?pageNo=1&pageSize=100'
// 单据状态：见 WmsOrderStatusEnum（0 草稿 / 4 已完成 / 5 已作废）
const STATUS_TEXT = "var st = { 0: '草稿', 4: '已完成', 5: '已作废' };"

const components = []

// ---------------------------------------------------------------- 顶部标题条
components.push(component('TextCommon', [400, 12, 1120, 50], textOption(DASH_TITLE, { fontSize: 32, fontColor: '#4A9EF8FF', fontWeight: 'bold', letterSpacing: 4 }), undefined, '', 0))
components.push(component('TextCommon', [24, 20, 360, 34], textOption('仓储总量 / 全国仓库分布 / 出入库作业', { fontSize: 14, fontColor: '#5A87B8FF', letterSpacing: 1 }), undefined, '', 0))
components.push(component('TimeCommon', [1520, 22, 376, 40], { timeSize: 20, timeLineHeight: 34, timeTextIndent: 1, timeColor: '#9FD3FFFF', fontWeight: 'normal', showShadow: true, hShadow: 0, vShadow: 0, blurShadow: 8, colorShadow: '#0075ff' }, undefined, '', 0))

// ---------------------------------------------------------------- KPI 数字条
const KPI_Y = 62
const KPI_H = 102
const kpis = [
  { label: '仓库总数', path: 'wms/warehouse/page?pageNo=1&pageSize=1', filter: 'return Number(data.total) || 0', suffix: ' 个' },
  { label: '库存总量', path: WMS + 'inventory-summary', filter: 'return Math.round(Number(data.totalQuantity) || 0)', suffix: ' 件', color: '#7FD8F7FF' },
  { label: '在库商品', path: 'wms/item/page?pageNo=1&pageSize=1', filter: 'return Number(data.total) || 0', suffix: ' 种' },
  { label: '库存记录', path: 'wms/inventory/page?pageNo=1&pageSize=1&type=warehouse', filter: 'return Number(data.total) || 0', suffix: ' 条' },
  { label: '入库单量', path: 'wms/receipt-order/page?pageNo=1&pageSize=1', filter: 'return Number(data.total) || 0', suffix: ' 单', color: '#4ADFA8FF' },
  { label: '出库单量', path: 'wms/shipment-order/page?pageNo=1&pageSize=1', filter: 'return Number(data.total) || 0', suffix: ' 单', color: '#FFC85CFF' },
  { label: '入库总金额', path: RECEIPT_PAGE, filter: "var s = 0;(data.list || []).forEach(function (it) { if (it.status === 5) return; s += Number(it.totalPrice) || 0 });return Math.round(s * 100) / 100", prefix: '￥', precision: 2, size: 24, color: '#4ADFA8FF' },
  { label: '出库总金额', path: SHIPMENT_PAGE, filter: "var s = 0;(data.list || []).forEach(function (it) { if (it.status === 5) return; s += Number(it.totalPrice) || 0 });return Math.round(s * 100) / 100", prefix: '￥', precision: 2, size: 24, color: '#FFC85CFF' }
]
kpis.forEach((k, i) => {
  const x = 8 + i * 240
  components.push(component('Border13', [x, KPI_Y, 224, KPI_H], borderOption('#1D4F8AFF', '#2F7FC9FF'), undefined, '', 0))
})
kpis.forEach((k, i) => {
  const x = 8 + i * 240
  components.push(component('TextCommon', [x, KPI_Y + 6, 224, 26], textOption(k.label, { fontSize: 15, fontColor: '#8FB6E8FF', letterSpacing: 1 }), undefined, '', 0))
  components.push(component('Number', [x, KPI_Y + 32, 224, 64], numberOption({
    dataset: 0,
    prefixText: k.prefix || '',
    suffixText: k.suffix || '',
    precision: k.precision || 0,
    size: k.size || 28,
    color: k.color || '#4A9EF8FF'
  }), k.filter, url(k.path)))
})

// ---------------------------------------------------------------- 版面坐标
// 1920 × 1080：顶部 KPI 条 + 主体三栏（左数据 / 中地图 / 右数据）+ 底部三块作业明细。
// 地图给到 616 高是关键：GoView 的 china.json 带南海诸岛，整幅地图的长宽比接近 1:1
// （ECharts geo 还有 aspectScale=0.75），面板越「高」地图才越大，横向留白是必然的。
const L_L = [8, 576]
const C_MAP = [592, 648]
const R_R = [1248, 664]
const MID_Y = 172
const ROW_A = [MID_Y, 326]
const ROW_B = [MID_Y + 334, 330]
const MAP_Y = MID_Y
const MAP_H = 664
const BOT_Y = 848
const BOT_H = 224
const BOT_L = [8, 616]
const BOT_M = [632, 616]
const BOT_R = [1256, 656]

// 面板边框先入列，保证图表画在边框之上（预览是按 componentList 顺序叠 DOM 的）
const PANELS = [
  [L_L[0], ROW_A[0], L_L[1], ROW_A[1]],
  [L_L[0], ROW_B[0], L_L[1], ROW_B[1]],
  [R_R[0], ROW_A[0], R_R[1], ROW_A[1]],
  [R_R[0], ROW_B[0], R_R[1], ROW_B[1]],
  [BOT_L[0], BOT_Y, BOT_L[1], BOT_H],
  [BOT_M[0], BOT_Y, BOT_M[1], BOT_H],
  [BOT_R[0], BOT_Y, BOT_R[1], BOT_H]
]
components.push(component('Border13', [C_MAP[0], MAP_Y, C_MAP[1], MAP_H], borderOption('#2F7FC9FF', '#5FD0FFFF'), undefined, '', 0))
PANELS.forEach((p) => components.push(component('Border13', p, borderOption('#1D4F8AFF', '#2F7FC9FF'), undefined, '', 0)))

// ---------------------------------------------------------------- 中央：全国仓库地图
components.push(component('TextCommon', [C_MAP[0], MAP_Y + 8, C_MAP[1], 32], textOption('全国仓库分布与在库库存（WMS）', { fontSize: 17, fontColor: '#CFE3FFFF', fontWeight: 'bold', letterSpacing: 2 }), undefined, '', 0))
components.push(component('MapBase', [C_MAP[0], MAP_Y + 40, C_MAP[1], MAP_H - 48], mapOption(),
  "var G = " + JSON.stringify(WAREHOUSE_GEO) + ";" +
  "var HUB = " + JSON.stringify(HUB_COORD) + ";" +
  "var list = (data && data.warehouseDistributionList) || [];" +
  "var point = [], line = [], prov = {};" +
  "list.forEach(function (it) {" +
  "  var g = G[it.name]; if (!g) return;" +
  "  var q = Math.round(Number(it.quantity) || 0);" +
  "  point.push({" +
  "    name: it.name + ' 在库 ' + q + ' 件' + (g.r ? ' · ' + g.r : '')," +
  "    value: [g.c[0], g.c[1], q]," +
  "    label: { offset: g.off, formatter: it.name + '  ' + q + ' 件' }" +
  "  });" +
  "  if (it.name !== '" + HUB_NAME + "') line.push({ coords: [[g.c[0], g.c[1]], HUB] });" +
  "  prov[g.p] = (prov[g.p] || 0) + q;" +
  "});" +
  "var map = Object.keys(prov).map(function (k) { return { name: k, value: prov[k] } });" +
  "return { point: point, line: line, map: map, pieces: " + JSON.stringify(MAP_PIECES) + " }",
  url(WMS + 'inventory-summary')))

// ---------------------------------------------------------------- 左上：仓库库存分布
components.push(component('BarCrossrange', [L_L[0], ROW_A[0], L_L[1], ROW_A[1]], Object.assign(barCrossOption(['x', '在库量'], ['在库量']), { title: chartTitle('仓库在库量分布（WMS）') }),
  "var list = (data && data.warehouseDistributionList) || [];" +
  "var arr = list.map(function (it) { return { x: it.name, 在库量: Math.round(Number(it.quantity) || 0) } });" +
  "arr.sort(function (a, b) { return a.在库量 - b.在库量 });" +
  "return { dimensions: ['x', '在库量'], source: arr }",
  url(WMS + 'inventory-summary')))

// ---------------------------------------------------------------- 左下：库存商品占比
components.push(component('PieCommon', [L_L[0], ROW_B[0], L_L[1], ROW_B[1]], Object.assign(pieOption(), { title: chartTitle('在库商品占比 Top 5（WMS）') }),
  "var list = (data && data.goodsShareList) || [];" +
  "return { dimensions: ['product', 'data1'], source: list.map(function (it) { return { product: it.name, data1: Math.round(Number(it.quantity) || 0) } }) }",
  url(WMS + 'inventory-summary')))

// ---------------------------------------------------------------- 右上：单据类型 × 状态
components.push(component('BarCommon', [R_R[0], ROW_A[0], R_R[1], ROW_A[1]], Object.assign(barOption(['x', '已完成', '草稿', '已作废'], ['已完成', '草稿', '已作废']), { title: chartTitle('单据类型与状态分布（WMS）') }),
  "var nt = { 1: '入库单', 2: '出库单', 3: '移库单', 4: '盘库单' };" +
  "var list = data || [];" +
  "var source = list.map(function (it) {" +
  "  var c = {}; (it.statuses || []).forEach(function (s) { c[s.status] = s.count });" +
  "  return { x: nt[it.type] || ('类型' + it.type), 已完成: c[4] || 0, 草稿: c[0] || 0, 已作废: c[5] || 0 };" +
  "});" +
  "return { dimensions: ['x', '已完成', '草稿', '已作废'], source: source }",
  url(WMS + 'order-summary')))

// ---------------------------------------------------------------- 右下：仓库 × 商品 热力图
// 取在库量最高的 6 个商品做横轴、7 个仓库做纵轴，缺货的格子补 0。
components.push(component('Heatmap', [R_R[0], ROW_B[0], R_R[1], ROW_B[1]], heatmapOption(),
  "var rows = (data && data.list) || [];" +
  "var im = {};" +
  "rows.forEach(function (it) { im[it.itemName] = (im[it.itemName] || 0) + (Number(it.quantity) || 0) });" +
  "var items = Object.keys(im).sort(function (a, b) { return im[b] - im[a] }).slice(0, 6);" +
  "var wm = {};" +
  "rows.forEach(function (it) { wm[it.warehouseName] = (wm[it.warehouseName] || 0) + (Number(it.quantity) || 0) });" +
  "var whs = Object.keys(wm).sort(function (a, b) { return wm[b] - wm[a] });" +
  "var cell = {};" +
  "rows.forEach(function (it) {" +
  "  var xi = items.indexOf(it.itemName), yi = whs.indexOf(it.warehouseName);" +
  "  if (xi < 0 || yi < 0) return;" +
  "  var k = xi + '-' + yi;" +
  "  cell[k] = (cell[k] || 0) + Math.round(Number(it.quantity) || 0);" +
  "});" +
  "var seriesData = [];" +
  "for (var y = 0; y < whs.length; y++) {" +
  "  for (var x = 0; x < items.length; x++) seriesData.push([x, y, cell[x + '-' + y] || 0]);" +
  "}" +
  "return { xAxis: items, yAxis: whs, seriesData: seriesData }",
  url('wms/inventory/page?pageNo=1&pageSize=100&type=warehouse')))

// ---------------------------------------------------------------- 底部左：入库单明细
components.push(component('TextCommon', [BOT_L[0], BOT_Y + 8, BOT_L[1], 28], textOption('入库单明细（最近 12 单）', { fontSize: 16, fontColor: '#CFE3FFFF', fontWeight: 'bold' }), undefined, '', 0))
components.push(component('TableScrollBoard', [BOT_L[0], BOT_Y + 40, BOT_L[1], BOT_H - 40], tableOption(
  ['单号', '仓库', '供应商', '数量', '金额', '状态'],
  [46, 136, 64, 156, 50, 84],
  ['center', 'center', 'center', 'left', 'center', 'right', 'center']
),
  STATUS_TEXT +
  "var list = (data && data.list) || [];" +
  "list.sort(function (a, b) { return (b.orderTime || 0) - (a.orderTime || 0) });" +
  "return list.map(function (it) { return [" +
  "  it.no || ''," +
  "  it.warehouseName || ''," +
  "  (it.merchantName || '').replace(/有限公司$/, '')," +
  "  Math.round(Number(it.totalQuantity) || 0)," +
  "  '￥' + (Number(it.totalPrice) || 0).toFixed(2)," +
  "  st[it.status] || ('状态' + it.status)" +
  "] })",
  url(RECEIPT_PAGE)))

// ---------------------------------------------------------------- 底部中：出库单明细
components.push(component('TextCommon', [BOT_M[0], BOT_Y + 8, BOT_M[1], 28], textOption('出库单明细（最近 11 单）', { fontSize: 16, fontColor: '#CFE3FFFF', fontWeight: 'bold' }), undefined, '', 0))
components.push(component('TableScrollBoard', [BOT_M[0], BOT_Y + 40, BOT_M[1], BOT_H - 40], tableOption(
  ['单号', '仓库', '客户', '数量', '金额', '状态'],
  [46, 136, 64, 156, 50, 84],
  ['center', 'center', 'center', 'left', 'center', 'right', 'center']
),
  STATUS_TEXT +
  "var list = (data && data.list) || [];" +
  "list.sort(function (a, b) { return (b.orderTime || 0) - (a.orderTime || 0) });" +
  "return list.map(function (it) { return [" +
  "  it.no || ''," +
  "  it.warehouseName || ''," +
  "  (it.merchantName || '').replace(/有限公司$/, '')," +
  "  Math.round(Number(it.totalQuantity) || 0)," +
  "  '￥' + (Number(it.totalPrice) || 0).toFixed(2)," +
  "  st[it.status] || ('状态' + it.status)" +
  "] })",
  url(SHIPMENT_PAGE)))

// ---------------------------------------------------------------- 底部右：入库金额走势
components.push(component('LineCommon', [BOT_R[0], BOT_Y, BOT_R[1], BOT_H], Object.assign(lineOption(['x', '入库金额'], ['入库金额'], '#4ADFA8FF'), {
  title: chartTitle('入库金额走势（按单据日期）', 12),
  legend: { show: false },
  grid: { show: false, left: 70, right: 26, top: 56, bottom: 34, containLabel: true }
}),
  "var list = (data && data.list) || [];" +
  "var m = {};" +
  "list.forEach(function (it) {" +
  "  if (it.status === 5) return;" +
  "  var d = new Date(Number(it.orderTime) || 0);" +
  "  var k = ('0' + (d.getMonth() + 1)).slice(-2) + '-' + ('0' + d.getDate()).slice(-2);" +
  "  m[k] = (m[k] || 0) + (Number(it.totalPrice) || 0);" +
  "});" +
  "var keys = Object.keys(m).sort();" +
  "var source = keys.map(function (k) { return { x: k, 入库金额: Math.round(m[k] * 100) / 100 } });" +
  "return { dimensions: ['x', '入库金额'], source: source }",
  url(RECEIPT_PAGE)))

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

// 只测算体积、不写库：report_go_view_project.content 是 text（65535 字节），
// 中文按 3 字节算，组件一多很容易顶到上限，改版前先用 TXGY_DRY_RUN=1 量一下。
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
