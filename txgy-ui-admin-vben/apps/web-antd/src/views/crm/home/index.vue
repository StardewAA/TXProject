<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import type { QuickActionDef } from './data';

import { onActivated, onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';
import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import { Card } from 'ant-design-vue';

import { getBusinessPage } from '#/api/crm/business';
import { getContractPage } from '#/api/crm/contract';
import { getCustomerPage } from '#/api/crm/customer';
import { getReceivablePage } from '#/api/crm/receivable';
import AiAssistantIcon from '#/views/crm/ai-chat/components/ai-assistant-icon.vue';

import AiAssistantModal from './modules/ai-assistant-modal.vue';
import { QUICK_ACTIONS } from './data';

defineOptions({ name: 'CrmHome' });

const { push } = useRouter();

// ========== 一、快捷新建（跳转到对应业务模块）==========

/**
 * 点击快捷「新建」→ 跳到对应模块并在该页打开新建表单
 *
 * 目标页通过 ?action=create 自动打开新建弹窗（见各模块 index.vue 的 checkCreateAction）。
 */
function handleQuick(action: QuickActionDef) {
  push({ path: action.path, query: { action: 'create' } });
}

// ========== 二、AI 智能助手（首页仅保留搜索栏，问答在弹窗内进行）==========

const aiModalRef = ref<InstanceType<typeof AiAssistantModal> | null>(null);

/** 搜索栏输入内容 */
const aiQuery = ref('');

/**
 * 提交搜索栏问题
 *
 * 首页卡片只保留搜索栏这一入口，提问后复用弹窗内的完整助手
 * （多会话 / 上下文记忆 / 数据查询表格 / 写操作二次确认 / OCR 建单等能力不变），
 * 这样首页保持极简，又不会丢失任何 AI 能力。
 */
function submitAiSearch() {
  const question = aiQuery.value.trim();
  if (!question) {
    return;
  }
  aiQuery.value = '';
  aiModalRef.value?.open(question);
}

/** 仅打开助手（不带问题，用于标题栏入口） */
function openAiAssistant(question?: string) {
  aiModalRef.value?.open(question);
}

// ========== 三、数据可视化（业务趋势 + 项目状态分布）==========

const trendChartRef = ref<EchartsUIType>();
const pieChartRef = ref<EchartsUIType>();
const { renderEcharts: renderTrend } = useEcharts(trendChartRef);
const { renderEcharts: renderPie } = useEcharts(pieChartRef);

/** 趋势图统计跨度（周） */
const TREND_WEEKS = 8;
/** 分页上限（后端限制 pageSize <= 200）；首页只要近 8 周概览，数据量小，一次拉完在前端按周分桶 */
const FETCH_PAGE_SIZE = 200;

/**
 * 图表配色（简约商务风：低饱和度，蓝 / 青绿 / 橙 三色为主）
 *
 * 折线：新增客户=蓝、成交订单=青绿、回款金额=橙；
 * 环形：已完成=蓝、进行中=青绿、已逾期=橙、待处理=浅灰、已丢单=中性灰。
 */
const CHART_COLOR = {
  // 折线（业务趋势）
  lineCustomer: '#2563eb',
  lineContract: '#10b981',
  lineReceivable: '#ea580c',
  // 环形（项目状态）
  statusFinished: '#2563eb',
  statusDoing: '#10b981',
  statusOverdue: '#ea580c',
  statusPending: '#9ca3af',
  statusLost: '#cbd5e1',
  // 通用
  splitLine: '#eef2f7',
  axisLabel: '#9ca3af',
  text: '#6b7280',
};

/**
 * 项目状态分类定义（顺序即图例与扇区顺序）
 *
 * 五类状态全部常驻图例：即使某类当前数量为 0 也展示，
 * 便于一眼看出「进行中 / 待处理 / 已丢单」等维度的存在与占比。
 */
const PROJECT_STATUS_META = [
  { key: 'finished', name: '已完成', color: CHART_COLOR.statusFinished },
  { key: 'doing', name: '进行中', color: CHART_COLOR.statusDoing },
  { key: 'overdue', name: '已逾期', color: CHART_COLOR.statusOverdue },
  { key: 'pending', name: '待处理', color: CHART_COLOR.statusPending },
  { key: 'lost', name: '已丢单', color: CHART_COLOR.statusLost },
] as const;

interface WeekBucket {
  label: string;
  start: number;
  end: number;
}

/**
 * 生成最近 N 周的周桶（以周一为起点）
 *
 * 为什么在前端分桶：后端统计接口中「新增客户」支持按周间隔，
 * 但「合同汇总」固定按月返回，混用会导致 x 轴粒度不一致，
 * 因此统一按记录的创建时间在前端按周聚合，保证三条曲线口径一致。
 */
function buildWeekBuckets(): WeekBucket[] {
  const now = new Date();
  const weekday = now.getDay() === 0 ? 7 : now.getDay(); // 1=周一 ... 7=周日
  const monday = new Date(
    now.getFullYear(),
    now.getMonth(),
    now.getDate() - (weekday - 1),
  );
  const buckets: WeekBucket[] = [];
  for (let i = TREND_WEEKS - 1; i >= 0; i--) {
    const start = new Date(
      monday.getFullYear(),
      monday.getMonth(),
      monday.getDate() - i * 7,
    );
    const end = new Date(
      start.getFullYear(),
      start.getMonth(),
      start.getDate() + 7,
    );
    buckets.push({
      label: `${start.getMonth() + 1}/${start.getDate()}`,
      start: start.getTime(),
      end: end.getTime(),
    });
  }
  return buckets;
}

/** 取记录时间（兼容时间戳与日期字符串） */
function timeOf(row: any): number {
  const value = row?.createTime ?? row?.orderDate ?? row?.signTime;
  if (!value) {
    return 0;
  }
  const time = new Date(value).getTime();
  return Number.isNaN(time) ? 0 : time;
}

/** 按周统计条数 */
function countByWeek(list: any[], buckets: WeekBucket[]): number[] {
  return buckets.map(
    (bucket) =>
      list.filter(
        (row) => timeOf(row) >= bucket.start && timeOf(row) < bucket.end,
      ).length,
  );
}

/** 按周汇总金额 */
function sumByWeek(
  list: any[],
  buckets: WeekBucket[],
  field: string,
): number[] {
  return buckets.map((bucket) =>
    list
      .filter(
        (row) => timeOf(row) >= bucket.start && timeOf(row) < bucket.end,
      )
      .reduce((sum, row) => sum + Number(row?.[field] ?? 0), 0),
  );
}

/**
 * 业务趋势：新增客户 / 成交订单（左轴，个）+ 回款金额（右轴，元），按周
 *
 * 视觉：加粗曲线、隐藏静态节点（hover 才显点）、浅灰细虚线网格弱化网格、
 * 坐标轴文字缩小且弱化轴线，让曲线走势成为视觉主体。
 */
function buildTrendOption(
  buckets: WeekBucket[],
  customers: any[],
  contracts: any[],
  receivables: any[],
) {
  const axisCommon = {
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: CHART_COLOR.axisLabel, fontSize: 11 },
  };
  return {
    color: [
      CHART_COLOR.lineCustomer,
      CHART_COLOR.lineContract,
      CHART_COLOR.lineReceivable,
    ],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line', lineStyle: { color: '#e5e7eb' } },
      backgroundColor: 'rgba(255, 255, 255, 0.97)',
      borderColor: '#eef2f7',
      borderWidth: 1,
      padding: [8, 12],
      textStyle: { color: '#374151', fontSize: 12 },
      extraCssText:
        'border-radius:8px;box-shadow:0 6px 16px rgba(15,23,42,.08);',
    },
    legend: {
      data: ['新增客户', '成交订单', '回款金额'],
      top: 0,
      left: 0,
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      itemGap: 18,
      textStyle: { color: CHART_COLOR.text, fontSize: 12 },
    },
    grid: { bottom: 0, containLabel: true, left: 0, right: 4, top: 40 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: buckets.map((bucket) => bucket.label),
      ...axisCommon,
    },
    yAxis: [
      {
        type: 'value',
        minInterval: 1,
        ...axisCommon,
        splitLine: {
          lineStyle: { color: CHART_COLOR.splitLine, type: 'dashed', width: 1 },
        },
      },
      {
        type: 'value',
        ...axisCommon,
        splitLine: { show: false },
        axisLabel: {
          color: CHART_COLOR.axisLabel,
          fontSize: 11,
          formatter: (value: number) =>
            value >= 10_000 ? `${(value / 10_000).toFixed(0)}万` : `${value}`,
        },
      },
    ],
    series: [
      {
        name: '新增客户',
        type: 'line',
        smooth: true,
        showSymbol: false,
        symbol: 'circle',
        symbolSize: 7,
        lineStyle: { width: 3 },
        emphasis: { focus: 'series' },
        data: countByWeek(customers, buckets),
      },
      {
        name: '成交订单',
        type: 'line',
        smooth: true,
        showSymbol: false,
        symbol: 'circle',
        symbolSize: 7,
        lineStyle: { width: 3 },
        emphasis: { focus: 'series' },
        data: countByWeek(contracts, buckets),
      },
      {
        name: '回款金额',
        type: 'line',
        smooth: true,
        showSymbol: false,
        symbol: 'circle',
        symbolSize: 7,
        lineStyle: { width: 3 },
        yAxisIndex: 1,
        emphasis: { focus: 'series' },
        data: sumByWeek(receivables, buckets, 'price'),
      },
    ],
  };
}

/**
 * 把一条商机归类为项目状态
 *
 * businessState：1 未开始、2 进行中、3 已赢单、4 已丢单。
 * 「进行中」再按下次联系时间判断是否逾期（应跟进而未跟进）。
 */
function classifyProject(row: any): string {
  const state = Number(row?.businessState);
  if (state === 3) {
    return 'finished';
  }
  if (state === 4) {
    return 'lost';
  }
  if (state === 1) {
    return 'pending';
  }
  const next = row?.contactNextTime
    ? new Date(row.contactNextTime).getTime()
    : 0;
  if (next > 0 && next < Date.now()) {
    return 'overdue';
  }
  return 'doing';
}

/**
 * 项目状态分布（环形）
 *
 * 环形线条粗细适中、配色柔和；图例置于图表右侧并带数量与占比，
 * 中心显示项目总数；五类状态常驻图例（含数量为 0 的维度）。
 */
function buildProjectPieOption(list: any[]) {
  const rows = Array.isArray(list) ? list : [];
  const counter = new Map<string, number>();
  for (const row of rows) {
    const key = classifyProject(row);
    counter.set(key, (counter.get(key) ?? 0) + 1);
  }
  // 不做 value > 0 过滤：数量为 0 的状态也保留在图例中
  const data = PROJECT_STATUS_META.map((meta) => ({
    name: meta.name,
    value: counter.get(meta.key) ?? 0,
    itemStyle: { color: meta.color },
  }));
  const total = data.reduce((sum, item) => sum + item.value, 0);
  const countOf = new Map(data.map((item) => [item.name, item.value]));
  const percentOf = new Map(
    data.map((item) => [
      item.name,
      total > 0 ? Math.round((item.value / total) * 100) : 0,
    ]),
  );
  return {
    color: data.map((item) => item.itemStyle.color),
    tooltip: {
      trigger: 'item',
      formatter: '{b}：{c} 个（{d}%）',
      backgroundColor: 'rgba(255, 255, 255, 0.97)',
      borderColor: '#eef2f7',
      borderWidth: 1,
      padding: [8, 12],
      textStyle: { color: '#374151', fontSize: 12 },
      extraCssText:
        'border-radius:8px;box-shadow:0 6px 16px rgba(15,23,42,.08);',
    },
    legend: {
      orient: 'vertical',
      right: 0,
      top: 'middle',
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      itemGap: 12,
      // 三列对齐：名称 / 数量 / 占比（长短不一也不会显得凌乱）
      textStyle: {
        fontSize: 12,
        rich: {
          name: { width: 46, color: '#4b5563', fontSize: 12 },
          count: {
            width: 24,
            align: 'right',
            color: '#1f2937',
            fontSize: 12,
            fontWeight: 600,
          },
          percent: {
            width: 38,
            align: 'right',
            color: '#9ca3af',
            fontSize: 12,
          },
        },
      },
      formatter: (name: string) =>
        `{name|${name}}{count|${countOf.get(name) ?? 0}}{percent|${
          percentOf.get(name) ?? 0
        }%}`,
    },
    title: {
      text: String(total),
      subtext: '项目总数',
      left: '30%',
      top: '40%',
      textAlign: 'center',
      textStyle: { fontSize: 24, fontWeight: 700, color: '#1f2937' },
      subtextStyle: { fontSize: 11, color: '#9ca3af' },
    },
    series: [
      {
        name: '项目状态',
        type: 'pie',
        radius: ['58%', '80%'],
        center: ['30%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: { borderColor: '#fff', borderWidth: 3, borderRadius: 6 },
        label: { show: false },
        labelLine: { show: false },
        emphasis: { scale: true, scaleSize: 5 },
        data,
      },
    ],
  };
}

/**
 * 加载图表数据（失败隔离：任一接口异常都不影响另一个图）
 *
 * 权限：客户 / 合同 / 回款 / 商机各自走对应模块的查询权限。
 * 无权限或数据为空时保持空图，不阻塞页面其它板块。
 */
async function loadCharts() {
  const buckets = buildWeekBuckets();
  await Promise.all([
    (async () => {
      try {
        const [customerPage, contractPage, receivablePage] = await Promise.all([
          getCustomerPage({ pageNo: 1, pageSize: FETCH_PAGE_SIZE } as any),
          getContractPage({ pageNo: 1, pageSize: FETCH_PAGE_SIZE } as any),
          getReceivablePage({ pageNo: 1, pageSize: FETCH_PAGE_SIZE } as any),
        ]);
        await renderTrend(
          buildTrendOption(
            buckets,
            (customerPage as any)?.list ?? [],
            (contractPage as any)?.list ?? [],
            (receivablePage as any)?.list ?? [],
          ),
        );
      } catch (error) {
        console.error('[crm-home] 业务趋势加载失败', error);
      }
    })(),
    (async () => {
      try {
        // 项目状态由商机列表在前端归类（状态组 + 跟进逾期判定）：
        // 不用 statistics-funnel 接口，它要求强制传 times/deptId，且返回的是细碎阶段而非业务状态
        const businessPage = await getBusinessPage({
          pageNo: 1,
          pageSize: FETCH_PAGE_SIZE,
        } as any);
        await renderPie(buildProjectPieOption((businessPage as any)?.list ?? []));
      } catch (error) {
        console.error('[crm-home] 项目状态分布加载失败', error);
      }
    })(),
  ]);
}

/** 自动刷新间隔：保证当天新增的数据能自动出现在图表上 */
const AUTO_REFRESH_INTERVAL_MS = 60_000;
let autoRefreshTimer: ReturnType<typeof setInterval> | undefined;

function startAutoRefresh() {
  stopAutoRefresh();
  autoRefreshTimer = setInterval(loadCharts, AUTO_REFRESH_INTERVAL_MS);
}

function stopAutoRefresh() {
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer);
    autoRefreshTimer = undefined;
  }
}

onMounted(() => {
  loadCharts();
  startAutoRefresh();
});

onActivated(() => {
  loadCharts();
});

onUnmounted(stopAutoRefresh);
</script>

<template>
  <Page auto-content-height>
    <!-- 单屏铺满：整页不产生垂直滚动，各卡片按剩余高度弹性分配 -->
    <div class="crm-home">
      <!-- 1. 顶部快捷区（一行，固定高度） -->
      <Card :bordered="false" class="crm-card crm-card--fixed">
        <div class="flex flex-wrap items-center gap-3">
          <span class="crm-section-title">
            <IconifyIcon icon="lucide:zap" :width="15" class="text-[#2563eb]" />
            快捷新建
          </span>
          <button
            v-for="action in QUICK_ACTIONS"
            :key="action.key"
            class="quick-btn"
            @click="handleQuick(action)"
          >
            <IconifyIcon :icon="action.icon" :width="15" />
            {{ action.label }}
          </button>
          <span class="ml-auto hidden text-xs text-[#9ca3af] xl:block">
            点击后进入对应业务模块，并自动打开新建表单
          </span>
        </div>
      </Card>

      <!-- 2. 业务趋势（整宽，弹性高度） -->
      <Card :bordered="false" class="crm-card crm-card--trend">
        <template #title>
          <div class="crm-card-title">
            <IconifyIcon
              icon="lucide:trending-up"
              :width="16"
              class="text-[#2563eb]"
            />
            <span>业务趋势</span>
            <span class="crm-pill">近 8 周</span>
          </div>
        </template>
        <EchartsUI ref="trendChartRef" class="crm-chart" />
      </Card>

      <!-- 3. 下排：左项目状态分布 + 右 AI 智能助手（并排，弹性高度） -->
      <div class="crm-bottom">
        <Card :bordered="false" class="crm-card">
          <template #title>
            <div class="crm-card-title">
              <IconifyIcon
                icon="lucide:pie-chart"
                :width="16"
                class="text-[#2563eb]"
              />
              <span>项目状态分布</span>
            </div>
          </template>
          <EchartsUI ref="pieChartRef" class="crm-chart" />
        </Card>

        <!-- AI 助手：首页只保留搜索栏，提问后进入弹窗内完整对话 -->
        <Card :bordered="false" class="crm-card">
          <template #title>
            <div class="crm-card-title">
              <AiAssistantIcon :size="22" />
              <span>AI 智能助手</span>
            </div>
          </template>

          <div class="ai-panel">
            <div class="ai-search">
              <IconifyIcon
                icon="lucide:sparkles"
                :width="16"
                class="ai-search__icon"
              />
              <input
                v-model="aiQuery"
                class="ai-search__input"
                placeholder="输入您的问题"
                @keydown.enter.prevent="submitAiSearch"
              />
              <button
                class="ai-search__btn"
                :disabled="!aiQuery.trim()"
                title="提问"
                @click="submitAiSearch"
              >
                <IconifyIcon icon="lucide:arrow-up" :width="16" />
              </button>
            </div>
            <p class="ai-search__hint">
              按回车或点击右侧按钮提问，将在助手窗口中继续对话
            </p>
          </div>
        </Card>
      </div>
    </div>

    <!-- 弹窗区（完整 AI 能力：多会话 / 上下文 / 数据查询 / 建单 / OCR） -->
    <AiAssistantModal ref="aiModalRef" />
  </Page>
</template>

<style scoped>
/* 单屏铺满：高度锁死为内容区高度，禁止任何垂直滚动 */
.crm-home {
  display: flex;
  flex-direction: column;
  gap: 14px;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

/* 统一卡片：8px 圆角 + 柔和浅阴影；卡片本身是 flex 容器，内部图表可弹性伸缩 */
.crm-card {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border: none;
  border-radius: 8px;
  box-shadow:
    0 1px 2px rgba(15, 23, 42, 0.04),
    0 6px 16px rgba(15, 23, 42, 0.05);
}

/* 快捷区：按内容高度，不参与弹性分配 */
.crm-card--fixed {
  flex: 0 0 auto;
}

/* 业务趋势：剩余空间里拿得稍多（约 1.15 份），保证下方环形图有足够高度 */
.crm-card--trend {
  flex: 1.15 1 0;
}

/* 下排两栏：各占一半，与折线图按比例分配剩余高度 */
.crm-bottom {
  display: grid;
  flex: 1 1 0;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 1fr);
  gap: 14px;
  min-height: 0;
}

.crm-card :deep(.ant-card-head) {
  flex: 0 0 auto;
  min-height: auto;
  padding: 12px 18px 0;
  border-bottom: none;
}

.crm-card :deep(.ant-card-head-title) {
  padding: 0;
}

.crm-card :deep(.ant-card-extra) {
  padding: 0;
  font-size: 13px;
}

/* 卡片内容区撑满剩余高度，供内部图表 / 面板自适应 */
.crm-card :deep(.ant-card-body) {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
  padding: 10px 18px 16px;
}

/* 图表容器：跟随卡片高度伸缩，保底高度避免高度为 0 时无法初始化 */
.crm-chart {
  flex: 1 1 auto;
  width: 100%;
  min-height: 130px;
}

/* AI 面板：居中对齐搜索栏，视觉重心稳定 */
.ai-panel {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 0;
}

/* 搜索栏：白底 + 浅灰描边 + 10px 圆角，hover/focus 有柔和的蓝色反馈 */
.ai-search {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  max-width: 520px;
  height: 46px;
  padding: 0 6px 0 14px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.ai-search:hover {
  border-color: #d1d5db;
}

.ai-search:focus-within {
  border-color: #93c5fd;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.ai-search__icon {
  flex: 0 0 auto;
  color: #94a3b8;
}

.ai-search__input {
  flex: 1 1 auto;
  min-width: 0;
  height: 100%;
  font-size: 13px;
  color: #1f2937;
  background: transparent;
  border: none;
  outline: none;
}

.ai-search__input::placeholder {
  color: #cbd5e1;
}

.ai-search__btn {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  color: #fff;
  cursor: pointer;
  background: #2563eb;
  border: none;
  border-radius: 8px;
  transition:
    background-color 0.18s ease,
    transform 0.18s ease;
}

.ai-search__btn:hover:not(:disabled) {
  background: #1d4ed8;
}

.ai-search__btn:active:not(:disabled) {
  transform: scale(0.96);
}

.ai-search__btn:disabled {
  color: #f1f5f9;
  cursor: not-allowed;
  background: #cbd5e1;
}

.ai-search__hint {
  margin: 0;
  font-size: 12px;
  color: #cbd5e1;
}

/* 模块标题：深灰、加粗、字号大于正文 */
.crm-card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}

.crm-section-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-right: 4px;
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}

/* 标题旁的浅灰标签（如「近 8 周」） */
.crm-pill {
  padding: 1px 8px;
  font-size: 12px;
  font-weight: 400;
  color: #6b7280;
  background: #f3f4f6;
  border-radius: 999px;
}

/* 快捷新建按钮：统一样式、色彩柔和、hover 轻微变色 */
.quick-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  font-size: 13px;
  font-weight: 500;
  color: #1d4ed8;
  cursor: pointer;
  background: #eff6ff;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  transition:
    background-color 0.18s ease,
    border-color 0.18s ease,
    color 0.18s ease;
}

.quick-btn:hover {
  color: #1e40af;
  background: #dbeafe;
  border-color: #bfdbfe;
}

.quick-btn:active {
  background: #c7dbfe;
}

/* 窄屏兜底：一屏内放不下时上下堆叠，仍不出现滚动条 */
@media (max-width: 1279px) {
  .crm-bottom {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
