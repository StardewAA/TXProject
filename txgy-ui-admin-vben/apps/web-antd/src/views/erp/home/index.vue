<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import type { ErpPurchaseStatisticsApi } from '#/api/erp/statistics/purchase';
import type { ErpSaleStatisticsApi } from '#/api/erp/statistics/sale';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';
import { EchartsUI, useEcharts } from '@vben/plugins/echarts';
import { useUserStore } from '@vben/stores';

import { Button, Card, Input, message, Tag } from 'ant-design-vue';

import {
  getPurchaseSummary,
  getPurchaseTimeSummary,
} from '#/api/erp/statistics/purchase';
import { getSaleSummary, getSaleTimeSummary } from '#/api/erp/statistics/sale';

import { assistantDemoData } from './modules/assistant-demo-data';

defineOptions({ name: 'ErpHome' });

type SaleSummary = ErpSaleStatisticsApi.SaleSummaryRespVO;
type PurchaseSummary = ErpPurchaseStatisticsApi.PurchaseSummaryRespVO;
type TimeSummary = { price: number; time: string };

interface NavigationTarget {
  label: string;
  path: string;
  routeNames: string[];
  titleKeywords: string[];
}

interface QuickAction extends NavigationTarget {
  color: string;
  icon: string;
  note: string;
}

interface CrossSystemAction extends QuickAction {
  system: string;
}

type NavigationTargetKey = keyof typeof navigationTargets;

interface AiModelMessage {
  content: string;
  role: 'assistant' | 'system' | 'user';
}

interface AiModelResult {
  answer: string;
  query?: Record<string, string>;
  target?: NavigationTargetKey;
}

const router = useRouter();
const userStore = useUserStore();
const loading = ref(false);
const statTime = ref('');
const saleSummary = ref<SaleSummary>({
  monthPrice: 0,
  todayPrice: 0,
  yearPrice: 0,
  yesterdayPrice: 0,
});
const purchaseSummary = ref<PurchaseSummary>({
  monthPrice: 0,
  todayPrice: 0,
  yearPrice: 0,
  yesterdayPrice: 0,
});
const saleTrend = ref<TimeSummary[]>([]);
const purchaseTrend = ref<TimeSummary[]>([]);

const navigationTargets = {
  approval: {
    label: '审批中心',
    path: '/bpm/task/todo',
    routeNames: ['BpmTodoTask'],
    titleKeywords: ['待办任务', '流程待办', '审批中心'],
  },
  crmCustomer: {
    label: 'CRM 客户',
    path: '/crm/customer',
    routeNames: ['CrmCustomer'],
    titleKeywords: ['客户管理', 'CRM 客户'],
  },
  financeReceipt: {
    label: '收款管理',
    path: '/erp/finance/receipt',
    routeNames: ['ErpFinanceReceipt'],
    titleKeywords: ['收款单', '收款管理'],
  },
  hrmEmployee: {
    label: 'HRM 人员',
    path: '/hrm/employee/list',
    routeNames: ['HrmEmployee'],
    titleKeywords: ['员工档案', '人员管理', 'HRM 人员'],
  },
  logistics: {
    label: '物流跟踪',
    path: '/erp/logistics',
    routeNames: ['ErpLogistics'],
    titleKeywords: ['物流任务', '物流跟踪'],
  },
  mallOrder: {
    label: '商城订单',
    path: '/mall/trade/order',
    routeNames: ['TradeOrder', 'MallTradeOrder'],
    titleKeywords: ['交易订单', '商城订单'],
  },
  product: {
    label: '产品资料',
    path: '/erp/product/product',
    routeNames: ['ErpProduct'],
    titleKeywords: ['产品信息', '产品资料'],
  },
  purchaseOrder: {
    label: '采购订单',
    path: '/erp/purchase/order',
    routeNames: ['ErpPurchaseOrder'],
    titleKeywords: ['采购订单'],
  },
  saleOrder: {
    label: '销售订单',
    path: '/erp/sale/order',
    routeNames: ['ErpSaleOrder'],
    titleKeywords: ['销售订单'],
  },
  stock: {
    label: '库存查询',
    path: '/erp/stock/stock',
    routeNames: ['ErpStock'],
    titleKeywords: ['产品库存', '库存查询'],
  },
  warehouse: {
    label: '仓库管理',
    path: '/erp/stock/warehouse',
    routeNames: ['ErpWarehouse'],
    titleKeywords: ['仓库信息', '仓库管理'],
  },
} satisfies Record<string, NavigationTarget>;

const quickActions: QuickAction[] = [
  { ...navigationTargets.saleOrder, color: '#2563eb', icon: 'lucide:file-plus-2', label: '新建销售订单', note: '录入客户需求' },
  { ...navigationTargets.purchaseOrder, color: '#f97316', icon: 'lucide:shopping-cart', note: '查看采购进度' },
  { ...navigationTargets.stock, color: '#0f766e', icon: 'lucide:boxes', note: '掌握可用库存' },
  { ...navigationTargets.logistics, color: '#7c3aed', icon: 'lucide:truck', note: '查看运输节点' },
  { ...navigationTargets.approval, color: '#dc2626', icon: 'lucide:clipboard-check', note: '处理待办单据' },
  { ...navigationTargets.product, color: '#0891b2', icon: 'lucide:package-search', note: '维护产品档案' },
  { ...navigationTargets.financeReceipt, color: '#16a34a', icon: 'lucide:wallet-cards', note: '跟进回款记录' },
  { ...navigationTargets.warehouse, color: '#475569', icon: 'lucide:warehouse', note: '查看仓库信息' },
];

const crossSystemActions: CrossSystemAction[] = [
  { ...navigationTargets.mallOrder, color: '#ea580c', icon: 'lucide:store', note: '订单与缺货协同', system: '商城' },
  { ...navigationTargets.crmCustomer, color: '#0891b2', icon: 'lucide:users-round', note: '客户与销售线索', system: 'CRM' },
  { ...navigationTargets.approval, color: '#7c3aed', icon: 'lucide:git-pull-request', label: '流程待办', note: '跨模块审批处理', system: 'BPM' },
  { ...navigationTargets.hrmEmployee, color: '#16a34a', icon: 'lucide:id-card', note: '人员与组织信息', system: 'HRM' },
];

const aiPrompt = ref('');
const aiSending = ref(false);
type AssistantMessage = { content: string; role: 'assistant' | 'user' };

const aiMessages = ref<AssistantMessage[]>([
  { content: '您好，我可以协助查询 ERP 业务并打开商城、CRM、BPM、HRM 相关工作台。', role: 'assistant' },
]);

const overviewItems = computed(() => [
  { color: '#2563eb', label: '今日销售额', value: saleSummary.value.todayPrice, detail: `昨日 ${formatAmount(saleSummary.value.yesterdayPrice)} 元` },
  { color: '#f97316', label: '今日采购额', value: purchaseSummary.value.todayPrice, detail: `昨日 ${formatAmount(purchaseSummary.value.yesterdayPrice)} 元` },
  { color: '#0f766e', label: '本月销售额', value: saleSummary.value.monthPrice, detail: `年度 ${formatAmount(saleSummary.value.yearPrice)} 元` },
  { color: '#7c3aed', label: '本月采购额', value: purchaseSummary.value.monthPrice, detail: `年度 ${formatAmount(purchaseSummary.value.yearPrice)} 元` },
]);

const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);

function formatAmount(value?: number) {
  return Number(value || 0).toLocaleString('zh-CN', { maximumFractionDigits: 2, minimumFractionDigits: 2 });
}

function formatStatTime() {
  return new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit' }).format(new Date());
}

async function refreshDashboard() {
  loading.value = true;
  const [sale, purchase, saleTimes, purchaseTimes] = await Promise.allSettled([
    getSaleSummary(),
    getPurchaseSummary(),
    getSaleTimeSummary(),
    getPurchaseTimeSummary(),
  ]);
  if (sale.status === 'fulfilled') saleSummary.value = sale.value;
  if (purchase.status === 'fulfilled') purchaseSummary.value = purchase.value;
  if (saleTimes.status === 'fulfilled') saleTrend.value = saleTimes.value || [];
  if (purchaseTimes.status === 'fulfilled') purchaseTrend.value = purchaseTimes.value || [];
  if ([sale, purchase, saleTimes, purchaseTimes].some((item) => item.status === 'rejected')) {
    message.warning('部分经营统计暂时无法加载，快捷操作仍可使用');
  }
  statTime.value = formatStatTime();
  loading.value = false;
}

function resolveNavigationTarget(target: NavigationTarget) {
  for (const name of target.routeNames) {
    if (router.hasRoute(name)) {
      return { name };
    }
  }

  const routes = router.getRoutes().filter((route) => !route.path.includes(':'));
  const exactRoute = routes.find((route) => route.path === target.path);
  if (exactRoute) {
    return exactRoute.name ? { name: exactRoute.name } : { path: exactRoute.path };
  }

  const titleRoute = routes.find((route) => {
    const title = String(route.meta?.title ?? '');
    return target.titleKeywords.some((keyword) => title.includes(keyword));
  });
  if (titleRoute) {
    return titleRoute.name ? { name: titleRoute.name } : { path: titleRoute.path };
  }

  return undefined;
}

async function goTo(
  target: NavigationTarget,
  query?: Record<string, string>,
) {
  const destination = resolveNavigationTarget(target);
  if (!destination) {
    message.warning(`当前账号未加载“${target.label}”菜单，请联系管理员配置权限`);
    return;
  }
  try {
    await router.push({ ...destination, query });
  } catch (error) {
    console.error('ERP 工作台跳转失败:', error);
    message.error(`“${target.label}”页面打开失败，请稍后重试`);
  }
}

function buildChart() {
  const times = [
    ...new Set(
      [...saleTrend.value, ...purchaseTrend.value].map((item) => item.time),
    ),
  ];
  if (times.length === 0) return;
  const saleMap = new Map(saleTrend.value.map((item) => [item.time, item.price]));
  const purchaseMap = new Map(purchaseTrend.value.map((item) => [item.time, item.price]));
  renderEcharts({
    animation: true,
    color: ['#2563eb', '#f97316'],
    grid: { bottom: 24, containLabel: true, left: 16, right: 16, top: 32 },
    legend: { bottom: 0, icon: 'roundRect', itemHeight: 6, itemWidth: 12 },
    series: [
      { areaStyle: { opacity: 0.08 }, data: times.map((time) => saleMap.get(time) || 0), name: '销售额', smooth: true, type: 'line' },
      { areaStyle: { opacity: 0.08 }, data: times.map((time) => purchaseMap.get(time) || 0), name: '采购额', smooth: true, type: 'line' },
    ],
    tooltip: { axisPointer: { type: 'cross' }, trigger: 'axis' },
    xAxis: { axisLabel: { color: '#64748b' }, axisLine: { lineStyle: { color: '#e2e8f0' } }, boundaryGap: false, data: times, type: 'category' },
    yAxis: { axisLabel: { color: '#64748b' }, splitLine: { lineStyle: { color: '#eef2f6' } }, type: 'value' },
  });
}

function formatAssistantAmount(value: number) {
  return `¥${formatAmount(value)}`;
}

function getDemoAnswer(content: string) {
  const text = content.replaceAll(/\s/g, '');
  const findNo = <T extends { no: string }>(items: T[]) =>
    items.find((item) => text.includes(item.no));

  if (/销售订单|销售单/.test(text) && /查询|查看|列表|状态|金额|订单号/.test(text)) {
    const order =
      findNo(assistantDemoData.sales) ||
      (/最新/.test(text)
        ? assistantDemoData.sales.toSorted(
            (a, b) =>
              new Date(b.orderTime).getTime() - new Date(a.orderTime).getTime(),
          )[0]
        : undefined);
    if (order) {
      return `销售订单 ${order.no}\n客户：${order.customer}\n商品：${order.product}\n金额：${formatAssistantAmount(order.amount)}\n状态：${order.status}`;
    }
    return `当前有 ${assistantDemoData.sales.length} 条脱敏销售订单：\n${assistantDemoData.sales.map((item) => `${item.no}｜${item.customer}｜${formatAssistantAmount(item.amount)}｜${item.status}`).join('\n')}`;
  }
  if (/商城|缺货|采购结果/.test(text) && /查询|查看|列表|状态|缺货|采购/.test(text)) {
    return `商城缺货采购协同（脱敏）：\n${assistantDemoData.mallOrders.map((item) => `${item.no}｜${item.product}｜缺货 ${item.shortage}｜${item.purchaseNo}｜${item.status}`).join('\n')}`;
  }
  if (/采购订单|采购单|采购/.test(text) && /查询|查看|列表|状态|金额|供应商|订单号/.test(text)) {
    const order = findNo(assistantDemoData.purchases);
    if (order) {
      return `采购订单 ${order.no}\n供应商：${order.supplier}\n商品：${order.product}\n金额：${formatAssistantAmount(order.amount)}\n状态：${order.status}`;
    }
    return `当前有 ${assistantDemoData.purchases.length} 条脱敏采购订单：\n${assistantDemoData.purchases.map((item) => `${item.no}｜${item.supplier}｜${formatAssistantAmount(item.amount)}｜${item.status}`).join('\n')}`;
  }
  if (/库存|存量|余量|可用量/.test(text) && /查询|查看|多少|列表|库存|余量/.test(text)) {
    const stock = assistantDemoData.stock.find((item) => text.includes(item.product));
    if (stock) {
      return `${stock.product}\n仓库：${stock.warehouse}\n可用：${stock.available}${stock.unit}\n锁定：${stock.locked}${stock.unit}`;
    }
    return `库存查询结果（脱敏）：\n${assistantDemoData.stock.map((item) => `${item.product}｜${item.warehouse}｜可用 ${item.available}${item.unit}｜锁定 ${item.locked}${item.unit}`).join('\n')}`;
  }
  if (/物流|运输|轨迹|节点/.test(text) && /查询|查看|跟踪|状态|物流|轨迹|节点/.test(text)) {
    const logistics = assistantDemoData.logistics.find((item) => text.includes(item.no) || text.includes(item.orderNo));
    if (logistics) {
      return `${logistics.no}\n关联订单：${logistics.orderNo}\n承运商：${logistics.carrier}\n当前节点：${logistics.currentNode}\n状态：${logistics.status}`;
    }
    return `物流跟踪结果（脱敏）：\n${assistantDemoData.logistics.map((item) => `${item.no}｜${item.orderNo}｜${item.currentNode}｜${item.status}`).join('\n')}`;
  }
  if (/审批|待办/.test(text) && /查询|查看|列表|状态|多少|审批|待办/.test(text)) {
    return `待办审批（脱敏）：\n${assistantDemoData.approvals.map((item) => `${item.no}｜${item.type}｜${item.applicant}｜${item.status}`).join('\n')}`;
  }
  if (/客户|CRM/.test(text) && /查询|查看|列表|跟进|客户/.test(text)) {
    return `CRM 客户信息（脱敏）：\n${assistantDemoData.crmCustomers.map((item) => `${item.name}｜${item.level}｜${item.contact}｜${item.nextAction}`).join('\n')}`;
  }
  if (/员工|人员|人事|HRM/.test(text) && /查询|查看|列表|状态|出勤|员工|人员/.test(text)) {
    return `HRM 人员信息（脱敏）：\n${assistantDemoData.employees.map((item) => `${item.name}｜${item.department}｜${item.status}｜${item.today}`).join('\n')}`;
  }
  return undefined;
}

function shouldOpenSalesOrder(content: string) {
  return (
    /销售订单|销售单/.test(content) &&
    (/最新/.test(content) ||
      assistantDemoData.sales.some((item) => content.includes(item.no)))
  );
}

function getSalesOrderTarget(content: string) {
  const text = content.replaceAll(/\s/g, '');
  return (
    assistantDemoData.sales.find((item) => text.includes(item.no)) ||
    (/最新/.test(text)
      ? assistantDemoData.sales.toSorted(
          (a, b) =>
            new Date(b.orderTime).getTime() - new Date(a.orderTime).getTime(),
        )[0]
      : undefined)
  );
}

interface AiIntent extends NavigationTarget {
  keywords: string[];
}

const aiIntents: AiIntent[] = [
  { ...navigationTargets.mallOrder, keywords: ['商城缺货', '缺货采购', '采购结果', '商城订单', '商城'] },
  { ...navigationTargets.saleOrder, keywords: ['销售订单', '销售单'] },
  { ...navigationTargets.purchaseOrder, keywords: ['采购订单', '采购单', '采购'] },
  { ...navigationTargets.stock, keywords: ['库存', '仓库'] },
  { ...navigationTargets.logistics, keywords: ['物流', '运输', '轨迹'] },
  { ...navigationTargets.approval, keywords: ['审批', '待办'] },
  { ...navigationTargets.crmCustomer, keywords: ['客户', 'CRM'] },
  { ...navigationTargets.hrmEmployee, keywords: ['人事', '员工', 'HRM'] },
];

function getAiIntent(content: string) {
  return aiIntents.find((item) => item.keywords.some((keyword) => content.includes(keyword)));
}

function buildAiContext() {
  const routes = router
    .getRoutes()
    .filter((route) => !route.path.includes(':') && route.path !== '/')
    .map((route) => ({
      name: route.name ? String(route.name) : undefined,
      path: route.path,
      title: route.meta?.title ? String(route.meta.title) : undefined,
    }))
    .filter((route, index, all) => all.findIndex((item) => item.path === route.path) === index)
    .slice(0, 160);

  return JSON.stringify(
    {
      data: assistantDemoData,
      navigationTargets: Object.fromEntries(
        Object.entries(navigationTargets).map(([key, target]) => [key, {
          label: target.label,
          pathHint: target.path,
          titleKeywords: target.titleKeywords,
        }]),
      ),
      registeredRoutes: routes,
      rules: [
        '所有业务数据均为脱敏样例，只能依据这些数据回答，不得编造真实企业数据。',
        '查询最新销售订单时，按 orderTime 倒序选择最新记录。',
        '只有确实需要打开页面时才返回 target；target 必须使用 navigationTargets 中的键。',
        'target 为 saleOrder 时，可将订单号放在 query.orderNo 中。',
      ],
    },
    null,
    2,
  );
}

function parseAiModelResult(content: unknown): AiModelResult {
  const raw = typeof content === 'string' ? content.trim() : '';
  const jsonText = raw.replace(/^```(?:json)?\s*/i, '').replace(/\s*```$/, '').trim();
  let parsed: unknown;
  try {
    parsed = JSON.parse(jsonText);
  } catch {
    parsed = undefined;
  }

  if (!parsed || typeof parsed !== 'object') {
    return { answer: raw || '大模型没有返回可用内容。' };
  }

  const result = parsed as Record<string, unknown>;
  const answer = typeof result.answer === 'string' ? result.answer.trim() : '';
  const target = typeof result.target === 'string' && result.target in navigationTargets
    ? (result.target as NavigationTargetKey)
    : undefined;
  const query = result.query && typeof result.query === 'object'
    ? Object.fromEntries(
        Object.entries(result.query as Record<string, unknown>)
          .filter(([, value]) => typeof value === 'string' || typeof value === 'number')
          .map(([key, value]) => [key, String(value)]),
      )
    : undefined;

  return {
    answer: answer || raw || '大模型没有返回可用内容。',
    ...(query && Object.keys(query).length > 0 ? { query } : {}),
    ...(target ? { target } : {}),
  };
}

async function askDeepSeek(content: string): Promise<AiModelResult> {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), 30_000);
  const history: AiModelMessage[] = aiMessages.value.slice(-7, -1).map((item) => ({
    content: item.content,
    role: item.role,
  }));
  const messages: AiModelMessage[] = [
    {
      content: `你是天信管业 ERP 首页业务助手。请使用中文，回答要简洁、明确。\n\n当前业务上下文：\n${buildAiContext()}\n\n请严格返回 JSON，不要使用 Markdown 代码块，格式为：{"answer":"给用户的回答","target":"可选的导航目标键","query":{"可选查询参数":"值"}}。如果只是回答问题，省略 target 和 query。`,
      role: 'system',
    },
    ...history,
    { content, role: 'user' },
  ];

  try {
    const response = await fetch('/api/deepseek/chat/completions', {
      body: JSON.stringify({
        messages,
        model: 'deepseek-chat',
        temperature: 0.2,
      }),
      headers: { 'Content-Type': 'application/json' },
      method: 'POST',
      signal: controller.signal,
    });
    if (!response.ok) {
      const detail = response.status === 401 || response.status === 403
        ? 'DeepSeek API Key 无效或未配置'
        : `服务返回 ${response.status}`;
      throw new Error(detail);
    }
    const payload = (await response.json()) as { choices?: Array<{ message?: { content?: unknown } }> };
    return parseAiModelResult(payload.choices?.[0]?.message?.content);
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new Error('DeepSeek 请求超时', { cause: error });
    }
    throw new Error(error instanceof Error ? error.message : 'DeepSeek 请求失败', { cause: error });
  } finally {
    window.clearTimeout(timeout);
  }
}

async function handleAiPrompt(value = aiPrompt.value) {
  const content = value.trim();
  if (!content || aiSending.value) return;
  aiPrompt.value = '';
  aiMessages.value.push({ content, role: 'user' });
  const demoAnswer = getDemoAnswer(content);
  if (demoAnswer) {
    aiMessages.value.push({ content: demoAnswer, role: 'assistant' });
    const intent = getAiIntent(content);
    if (intent?.label === '销售订单' && shouldOpenSalesOrder(content)) {
      const target = getSalesOrderTarget(content);
      void goTo(
        navigationTargets.saleOrder,
        target ? { orderNo: target.no } : undefined,
      );
    } else if (intent) {
      void goTo(intent);
    }
    return;
  }
  const intent = getAiIntent(content);
  if (intent) {
    aiMessages.value.push({ content: `已识别为“${intent.label}”操作，正在打开对应工作台。`, role: 'assistant' });
    void goTo(intent);
    return;
  }
  aiSending.value = true;
  try {
    const result = await askDeepSeek(content);
    aiMessages.value.push({ content: result.answer, role: 'assistant' });
    if (result.target) {
      await goTo(navigationTargets[result.target], result.query);
    }
  } catch (error) {
    const detail = error instanceof Error ? error.message : '请求失败';
    aiMessages.value.push({
      content: detail.includes('API Key')
        ? '大模型暂时不可用：请在 apps/web-antd/.env.local 中配置 DEEPSEEK_API_KEY，然后重启前端服务。已支持的 ERP 查询仍可继续使用。'
        : `大模型暂时不可用（${detail}），已支持的 ERP 查询仍可继续使用。`,
      role: 'assistant',
    });
  } finally {
    aiSending.value = false;
  }
}

watch(
  () => [saleTrend.value, purchaseTrend.value],
  async () => {
    await nextTick();
    buildChart();
  },
  { deep: true },
);

onMounted(refreshDashboard);
</script>

<template>
  <Page auto-content-height>
    <div class="erp-home">
      <section class="home-heading">
        <div>
          <div class="eyebrow">天信管业 · 企业资源计划</div>
          <h1>ERP 工作台</h1>
          <p>{{ userStore.userInfo?.nickname || '您好' }}，这里集中处理订单、采购、库存与协同事项。</p>
        </div>
        <div class="heading-actions">
          <Tag color="green" class="status-tag"><span class="status-dot"></span>服务正常</Tag>
          <Button :loading="loading" @click="refreshDashboard"><IconifyIcon icon="lucide:refresh-cw" class="mr-1" />刷新数据</Button>
        </div>
      </section>

      <section class="overview-grid">
        <Card v-for="item in overviewItems" :key="item.label" class="overview-card">
          <div class="overview-accent" :style="{ backgroundColor: item.color }"></div>
          <div class="overview-label">{{ item.label }}</div>
          <div class="overview-value">¥ {{ formatAmount(item.value) }}</div>
          <div class="overview-detail">{{ item.detail }}</div>
        </Card>
      </section>

      <section class="assistant-section">
        <Card class="assistant-card">
          <template #title>
            <div class="assistant-title">
              <span class="assistant-icon"><IconifyIcon icon="tabler:ai" /></span>
              <div><div class="card-title">ERP 智能助手</div><div class="card-subtitle">业务查询 · 模块直达</div></div>
              <Tag color="blue" class="ml-auto">AI</Tag>
            </div>
          </template>
          <div class="assistant-messages">
            <div v-for="(item, index) in aiMessages.slice(-3)" :key="`${item.role}-${index}`" class="message-line" :class="item.role">
              <span v-if="item.role === 'assistant'" class="message-avatar"><IconifyIcon icon="tabler:sparkles" /></span>
              <span class="message-bubble">{{ item.content }}</span>
            </div>
          </div>
          <div class="prompt-list">
            <button type="button" class="prompt-chip" @click="handleAiPrompt('打开销售订单')">打开销售订单</button>
            <button type="button" class="prompt-chip" @click="handleAiPrompt('查看库存')">查看库存</button>
            <button type="button" class="prompt-chip" @click="handleAiPrompt('处理审批待办')">处理审批待办</button>
          </div>
          <div class="assistant-input">
            <Input v-model:value="aiPrompt" :disabled="aiSending" placeholder="输入业务需求，例如：查看商城订单" @press-enter="() => handleAiPrompt()" />
            <Button type="primary" :loading="aiSending" :disabled="!aiPrompt.trim()" aria-label="发送消息" @click="handleAiPrompt()"><IconifyIcon icon="lucide:arrow-up" /></Button>
          </div>
        </Card>
      </section>

      <section class="content-grid">
        <Card class="trend-card">
          <template #title>
            <div class="card-title-row">
              <div><div class="card-title">经营趋势</div><div class="card-subtitle">销售与采购金额变化</div></div>
              <span v-if="statTime" class="update-time">更新于 {{ statTime }}</span>
            </div>
          </template>
          <div class="chart-wrap">
            <EchartsUI ref="chartRef" height="300px" />
            <div v-if="loading" class="chart-loading">正在加载统计数据...</div>
            <div v-else-if="!saleTrend.length && !purchaseTrend.length" class="chart-empty">暂无趋势数据</div>
          </div>
        </Card>
      </section>

      <section class="section-block">
        <div class="section-heading">
          <div><h2>ERP 快捷操作</h2><p>常用业务入口</p></div>
          <button type="button" class="section-link" @click="goTo(navigationTargets.approval)">查看待办 <IconifyIcon icon="lucide:arrow-right" /></button>
        </div>
        <div class="quick-grid">
          <button v-for="action in quickActions" :key="action.label" type="button" class="quick-card" @click="goTo(action)">
            <span class="quick-icon" :style="{ color: action.color, backgroundColor: `${action.color}14` }"><IconifyIcon :icon="action.icon" /></span>
            <span class="quick-copy"><strong>{{ action.label }}</strong><small>{{ action.note }}</small></span>
            <IconifyIcon icon="lucide:chevron-right" class="quick-arrow" />
          </button>
        </div>
      </section>

      <section class="section-block cross-system-block">
        <div class="section-heading">
          <div><h2>跨系统协同</h2><p>从 ERP 进入相关业务系统，保持业务链路连续</p></div>
          <span class="system-count">{{ crossSystemActions.length }} 个系统入口</span>
        </div>
        <div class="cross-grid">
          <button v-for="action in crossSystemActions" :key="action.system" type="button" class="cross-card" @click="goTo(action)">
            <span class="cross-icon" :style="{ color: action.color }"><IconifyIcon :icon="action.icon" /></span>
            <span><small>{{ action.system }}</small><strong>{{ action.label }}</strong><em>{{ action.note }}</em></span>
            <IconifyIcon icon="lucide:external-link" class="cross-arrow" />
          </button>
        </div>
      </section>
    </div>
  </Page>
</template>

<style scoped>
.erp-home { min-height: 100%; padding: 24px; color: #172033; background: #f6f8fb; }
.home-heading, .section-heading, .heading-actions, .card-title-row, .assistant-title, .assistant-input { display: flex; align-items: center; }
.home-heading, .section-heading, .card-title-row { justify-content: space-between; }
.home-heading { max-width: 1440px; margin: 0 auto 20px; }
.eyebrow { color: #64748b; font-size: 12px; font-weight: 600; letter-spacing: .08em; text-transform: uppercase; }
h1, h2, p { margin: 0; }
h1 { margin-top: 5px; color: #172033; font-size: 28px; line-height: 1.25; }
.home-heading p, .section-heading p { margin-top: 6px; color: #64748b; font-size: 13px; }
.heading-actions { gap: 12px; }
.status-tag { display: inline-flex; align-items: center; height: 32px; margin: 0; border-radius: 4px; }
.status-dot { display: inline-block; width: 6px; height: 6px; margin-right: 6px; border-radius: 50%; background: #16a34a; vertical-align: middle; }
.overview-grid, .quick-grid, .cross-grid { display: grid; gap: 16px; max-width: 1440px; margin: 0 auto; }
.overview-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
.overview-card { position: relative; overflow: hidden; border: 1px solid #e8edf3; }
.overview-card :deep(.ant-card-body) { padding: 19px 20px 18px; }
.overview-accent { position: absolute; inset: 0 auto 0 0; width: 3px; }
.overview-label, .overview-detail { color: #64748b; font-size: 13px; }
.overview-value { margin: 9px 0 7px; color: #172033; font-size: 24px; font-weight: 700; line-height: 1.15; }
.assistant-section, .content-grid { max-width: 1440px; margin: 16px auto 0; }
.assistant-section { width: 100%; }
.content-grid { display: grid; grid-template-columns: minmax(0, 1fr); gap: 16px; }
.trend-card, .assistant-card { min-width: 0; border: 1px solid #e8edf3; }
.trend-card :deep(.ant-card-head), .assistant-card :deep(.ant-card-head) { min-height: 70px; border-bottom-color: #eef2f6; }
.trend-card :deep(.ant-card-body) { padding: 7px 16px 12px; }
.assistant-card :deep(.ant-card-body) { padding: 14px 16px 16px; }
.card-title { color: #172033; font-size: 16px; font-weight: 700; }
.card-subtitle, .update-time { color: #94a3b8; font-size: 12px; font-weight: 400; }
.update-time { align-self: flex-end; padding-bottom: 2px; }
.chart-wrap { position: relative; min-height: 300px; }
.chart-loading, .chart-empty { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; color: #94a3b8; font-size: 13px; }
.assistant-title { width: 100%; gap: 10px; }
.assistant-icon { display: inline-flex; align-items: center; justify-content: center; width: 34px; height: 34px; border-radius: 8px; color: #fff; background: #2563eb; font-size: 19px; }
.assistant-messages { display: flex; min-height: 108px; max-height: 126px; flex-direction: column; gap: 9px; overflow: auto; }
.message-line { display: flex; align-items: flex-start; gap: 7px; color: #475569; font-size: 13px; line-height: 1.6; }
.message-line.user { justify-content: flex-end; }
.message-avatar { display: inline-flex; flex: 0 0 auto; align-items: center; justify-content: center; width: 22px; height: 22px; border-radius: 50%; color: #2563eb; background: #eff6ff; }
.message-bubble { max-width: 86%; padding: 7px 10px; border-radius: 6px; background: #f1f5f9; white-space: pre-line; }
.message-line.user .message-bubble { color: #fff; background: #2563eb; }
.prompt-list { display: flex; gap: 7px; margin: 12px 0; overflow-x: auto; }
.prompt-chip { flex: 0 0 auto; padding: 5px 9px; border: 1px solid #dbe5f0; border-radius: 4px; color: #475569; background: #fff; font-size: 12px; cursor: pointer; }
.prompt-chip:hover, .section-link:hover { color: #2563eb; border-color: #93c5fd; }
.assistant-input { gap: 8px; }
.assistant-input :deep(.ant-input) { height: 34px; }
.assistant-input .ant-btn { display: inline-flex; align-items: center; justify-content: center; width: 34px; height: 34px; padding: 0; }
.section-block { max-width: 1440px; margin: 22px auto 0; }
.section-heading h2 { color: #172033; font-size: 17px; font-weight: 700; }
.section-link { display: inline-flex; align-items: center; gap: 5px; border: 0; color: #64748b; background: transparent; font-size: 13px; cursor: pointer; }
.quick-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); margin-top: 12px; }
.quick-card, .cross-card { display: flex; align-items: center; border: 1px solid #e8edf3; background: #fff; text-align: left; cursor: pointer; transition: transform .15s, box-shadow .15s, border-color .15s; }
.quick-card { min-height: 76px; gap: 11px; padding: 13px 14px; border-radius: 6px; }
.quick-card:hover, .cross-card:hover { border-color: #bfdbfe; box-shadow: 0 5px 18px rgb(15 23 42 / 6%); transform: translateY(-1px); }
.quick-icon { display: inline-flex; flex: 0 0 auto; align-items: center; justify-content: center; width: 36px; height: 36px; border-radius: 8px; font-size: 19px; }
.quick-copy { display: flex; min-width: 0; flex-direction: column; gap: 4px; }
.quick-copy strong, .cross-card strong { overflow: hidden; color: #334155; font-size: 13px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.quick-copy small, .cross-card em { color: #94a3b8; font-size: 12px; font-style: normal; }
.quick-arrow, .cross-arrow { margin-left: auto; color: #cbd5e1; font-size: 16px; }
.cross-system-block { padding-bottom: 12px; }
.system-count { color: #94a3b8; font-size: 12px; }
.cross-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); margin-top: 12px; }
.cross-card { gap: 11px; min-height: 82px; padding: 14px; border-radius: 6px; }
.cross-icon { display: inline-flex; flex: 0 0 auto; align-items: center; justify-content: center; width: 34px; height: 34px; font-size: 20px; }
.cross-card > span:nth-child(2) { display: flex; min-width: 0; flex-direction: column; gap: 3px; }
.cross-card small { color: #94a3b8; font-size: 11px; }
@media (max-width: 1100px) { .assistant-card { min-height: 280px; } }
@media (max-width: 800px) { .erp-home { padding: 16px; } .home-heading { align-items: flex-start; flex-direction: column; gap: 14px; } .overview-grid, .quick-grid, .cross-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 520px) { .overview-grid, .quick-grid, .cross-grid { grid-template-columns: 1fr; } h1 { font-size: 24px; } .heading-actions { width: 100%; justify-content: space-between; } }
</style>
