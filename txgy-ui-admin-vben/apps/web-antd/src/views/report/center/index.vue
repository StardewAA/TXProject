<script lang="ts" setup>
import type { ReportCenterApi } from '#/api/report/center';

import type { OverviewStat } from './types';

import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';
import type { CardState } from './types';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import { message } from 'ant-design-vue';

import {
  getReportCenterList,
  getReportCenterOverview,
} from '#/api/report/center';

const router = useRouter();

/** 最近查看的报表编码（真实浏览行为记录，非业务数据） */
const RECENT_KEY = 'report-center:recent-codes';
/** 兼容上一版仅记录一张报表的存储键 */
const LEGACY_LAST_KEY = 'report-center:last-code';
/** 「我的报表」最多展示张数 */
const RECENT_LIMIT = 4;
/** 热门报表最多展示张数 */
const HOT_LIMIT = 6;
/* ==================================================================
 * 一、报表概览统计
 *
 * 全部数据来自真实接口 /report/center/list，不使用任何前端 mock：
 *  - 报表总数：报表清单条目数
 *  - 常用报表：近 30 天查询次数 > 0 的报表数量（依据清单字段 queryCount）
 *  - 今日查询：清单仅提供近 30 天累计次数，无今日口径，按「暂无数据」展示
 *  - 数据更新：清单未返回最近更新时间，按「暂无数据」展示
 * ================================================================== */

/** 指标静态元信息（仅图标、单位等展示配置，不含任何业务数值） */
interface OverviewMeta {
  key: string;
  label: string;
  unit: string;
  icon: string;
  iconColor: string;
  iconBg: string;
}

const OVERVIEW_META: Record<
  'dataUpdate' | 'frequent' | 'reportTotal' | 'todayQuery',
  OverviewMeta
> = {
  reportTotal: {
    key: 'reportTotal',
    label: '报表总数',
    unit: '张',
    icon: 'lucide:files',
    iconColor: '#1677ff',
    iconBg: '#e8f2ff',
  },
  todayQuery: {
    key: 'todayQuery',
    label: '今日查询',
    unit: '次',
    icon: 'lucide:activity',
    iconColor: '#16a34a',
    iconBg: '#e9f9ef',
  },
  frequent: {
    key: 'frequent',
    label: '常用报表',
    unit: '张',
    icon: 'lucide:star',
    iconColor: '#f59e0b',
    iconBg: '#fff5e6',
  },
  dataUpdate: {
    key: 'dataUpdate',
    label: '数据更新',
    unit: '',
    icon: 'lucide:refresh-cw',
    iconColor: '#7c3aed',
    iconBg: '#f3ecff',
  },
};

/** 空指标：无真实数据来源时统一展示「暂无数据」 */
function buildStat(
  meta: OverviewMeta,
  value: null | string,
  hint: string,
): OverviewStat {
  return {
    key: meta.key,
    label: meta.label,
    value,
    unit: meta.unit,
    icon: meta.icon,
    iconColor: meta.iconColor,
    iconBg: meta.iconBg,
    trend: null,
    trendUp: true,
    hint,
    missing: value === null,
  };
}

/** 加载中 / 失败时的占位卡片（value 为 null，页面展示「暂无数据」） */
function buildPlaceholderStats(): OverviewStat[] {
  return Object.values(OVERVIEW_META).map((meta) =>
    buildStat(meta, null, '正在加载真实数据…'),
  );
}

const overviewStats = ref<OverviewStat[]>(buildPlaceholderStats());
const overviewLoading = ref(false);

/** 安全取数：接口返回 null/undefined 时统一为 null */
function toCount(value?: null | number): null | string {
  if (value === null || value === undefined) {
    return null;
  }
  const num = Number(value);
  return Number.isFinite(num) ? String(num) : null;
}

/** 时间展示：后端返回 yyyy-MM-dd HH:mm:ss，截取到分钟（MM-DD HH:mm） */
function formatUpdateTime(value?: null | string): null | string {
  if (!value) {
    return null;
  }
  return value.slice(5, 16);
}

/** 加载报表中心概览统计（真实接口 /report/center/overview） */
async function loadOverview() {
  overviewLoading.value = true;
  try {
    const data = await getReportCenterOverview();
    overviewStats.value = [
      buildStat(
        OVERVIEW_META.reportTotal,
        toCount(data?.reportTotal),
        '口径：当前账号可查看的报表总数（/report/center/overview）',
      ),
      buildStat(
        OVERVIEW_META.todayQuery,
        toCount(data?.todayQueryCount),
        '口径：当日零点起的成功查询次数，由报表执行日志 create_time 统计',
      ),
      buildStat(
        OVERVIEW_META.frequent,
        toCount(data?.frequentCount),
        '口径：近 30 天查询次数 > 0 的报表数量',
      ),
      buildStat(
        OVERVIEW_META.dataUpdate,
        formatUpdateTime(data?.lastUpdateTime),
        '口径：最近一次成功查询的时间；无查询记录时显示暂无数据',
      ),
    ];
  } catch (error: any) {
    // 接口失败不回退到写死数字，保留占位卡片展示「暂无数据」
    overviewStats.value = buildPlaceholderStats();
    message.error(error?.message ?? '报表概览加载失败，请稍后重试');
  } finally {
    overviewLoading.value = false;
  }
}

/* ==================================================================
 * 二、页面时间（系统当前时间，每分钟自动刷新）
 * ================================================================== */
const now = ref(new Date());
let timer: ReturnType<typeof setInterval> | undefined;

const nowText = computed(() => {
  const value = now.value;
  const week = ['日', '一', '二', '三', '四', '五', '六'][value.getDay()];
  const pad = (num: number) => String(num).padStart(2, '0');
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(
    value.getDate(),
  )} 星期${week} ${pad(value.getHours())}:${pad(value.getMinutes())}`;
});

/* ==================================================================
 * 三、报表清单（全部来自 /report/center/list 真实接口）
 * ================================================================== */
const reports = ref<ReportCenterApi.Report[]>([]);
const loading = ref(false);
/** 全部报表当前分类 */
const activeCategory = ref('all');
/** 全部报表搜索关键词 */
const keyword = ref('');
/** 全部报表面板（「更多」跳转用） */
const allSectionRef = ref<HTMLElement>();

/**
 * 全部报表分类
 *
 * 说明：分类仅做前端过滤，不新增/修改任何后端接口。
 * 现有后端业务分组值为「生产 / 品质 / 设备 / 库存 / 自定义」，
 * 此处按业务口径映射：质量 ← 品质，物料 ← 库存（或名称/字段含「物料」）。
 */
const CATEGORY_TABS: Array<{
  key: string;
  label: string;
  test: (report: ReportCenterApi.Report) => boolean;
}> = [
  { key: 'all', label: '全部', test: () => true },
  {
    key: 'produce',
    label: '生产',
    test: (report) => (report.groups ?? []).includes('生产'),
  },
  {
    key: 'quality',
    label: '质量',
    test: (report) => (report.groups ?? []).includes('品质'),
  },
  {
    key: 'device',
    label: '设备',
    test: (report) => (report.groups ?? []).includes('设备'),
  },
  {
    key: 'material',
    label: '物料',
    test: (report) =>
      (report.groups ?? []).includes('库存') ||
      report.name.includes('物料') ||
      (report.columnLabels ?? []).some((label) => label.includes('物料')),
  },
];

/** 关键词匹配：报表名称 / 用途描述 / 业务分组 / 报表内字段 */
function matchesKeyword(report: ReportCenterApi.Report, kw: string) {
  if (!kw) {
    return true;
  }
  return [
    report.name,
    report.description,
    ...(report.groups ?? []),
    ...(report.columnLabels ?? []),
  ]
    .filter(Boolean)
    .some((text) => String(text).toLowerCase().includes(kw));
}

/** 当前分类 + 搜索条件下的报表 */
const filteredReports = computed(() => {
  const tab = CATEGORY_TABS.find((item) => item.key === activeCategory.value);
  const kw = keyword.value.trim().toLowerCase();
  return reports.value.filter(
    (report) => (tab?.test(report) ?? true) && matchesKeyword(report, kw),
  );
});

/**
 * 热门报表
 *
 * 排序依据为后端真实返回的近 30 天查询次数（queryCount）：
 * 有查询记录的按次数降序优先，其余按报表配置顺序补足。
 * 不展示任何伪造的访问次数。
 */
const hotReports = computed(() => {
  const used: ReportCenterApi.Report[] = [];
  const unused: ReportCenterApi.Report[] = [];
  for (const report of reports.value) {
    if ((report.queryCount ?? 0) > 0) {
      used.push(report);
    } else {
      unused.push(report);
    }
  }
  const sortedUsed = used.toSorted(
    (a, b) => (b.queryCount ?? 0) - (a.queryCount ?? 0),
  );
  return [...sortedUsed, ...unused].slice(0, HOT_LIMIT);
});

/**
 * 我的报表
 *
 * 系统暂无报表级「收藏 / 订阅」接口，此处只展示本机真实浏览记录
 * （用户点击查看过的报表，记录在浏览器本地），不写死任何报表名称。
 */
const recentCodes = ref<string[]>([]);

const myReports = computed(() => {
  const result: ReportCenterApi.Report[] = [];
  for (const code of recentCodes.value) {
    const report = reports.value.find((item) => item.code === code);
    if (report) {
      result.push(report);
    }
  }
  return result;
});

function loadRecentCodes() {
  try {
    const raw = localStorage.getItem(RECENT_KEY);
    const list: string[] = raw ? JSON.parse(raw) : [];
    if (Array.isArray(list)) {
      recentCodes.value = list.filter((code) => typeof code === 'string');
      return;
    }
  } catch {
    // 本地记录损坏时忽略，按无记录处理
  }
  const legacy = localStorage.getItem(LEGACY_LAST_KEY);
  recentCodes.value = legacy ? [legacy] : [];
}

/** 记录浏览行为（最多保留 RECENT_LIMIT 张，最新在前） */
function rememberReport(code: string) {
  const next = [code, ...recentCodes.value.filter((item) => item !== code)];
  recentCodes.value = next.slice(0, RECENT_LIMIT);
  localStorage.setItem(RECENT_KEY, JSON.stringify(recentCodes.value));
  localStorage.setItem(LEGACY_LAST_KEY, code);
}

/** 加载报表清单（全部来自 /report/center/list 真实接口） */
  exportReportCenterExcel,
  getReportCenterList,
  queryReportCenterData,
} from '#/api/report/center';

import ReportPanel from './modules/ReportPanel.vue';
import ReportSidebar from './modules/ReportSidebar.vue';

/** 记住上次查看的报表 */
const STORAGE_KEY = 'report-center:last-code';

/** 默认每页条数 */
const DEFAULT_PAGE_SIZE = 6;

const reports = ref<ReportCenterApi.Report[]>([]);
const loading = ref(false);
/** 当前选中的报表编码 */
const selectedCode = ref('');
/** 正在导出的报表编码 */
const exporting = ref('');
/** 每张报表独立的查询状态：切走再切回不丢失，也不重复请求 */
const cards = reactive<Record<string, CardState>>({});

const selectedReport = computed(
  () => reports.value.find((item) => item.code === selectedCode.value) ?? null,
);

const currentCard = computed(() => {
  const code = selectedCode.value;
  return code ? ensureCard(code) : null;
});

function ensureCard(code: string): CardState {
  if (!cards[code]) {
    cards[code] = {
      loading: false,
      headers: [],
      rows: [],
      columns: [],
      page: 1,
      pageSize: DEFAULT_PAGE_SIZE,
      collapsed: false,
    };
  }
  return cards[code];
}

/** 加载报表清单，并恢复上次查看的报表 */
async function loadReports() {
  loading.value = true;
  try {
    reports.value = await getReportCenterList();
  } catch (error: any) {
    message.error(error?.message ?? '报表清单加载失败，请稍后重试');
    const remembered = localStorage.getItem(STORAGE_KEY) ?? '';
    const exists = reports.value.some((item) => item.code === remembered);
    selectedCode.value = exists ? remembered : (reports.value[0]?.code ?? '');
  } finally {
    loading.value = false;
  }
}

/**
 * 打开某张报表：记录浏览行为后跳转到独立报表查看页
 *
 * 结果不再渲染在本页面下方或抽屉里，统一由独立页面
 * /mes/report-dataset/preview/:code 承载（from=center 表示从本页进入）。
 */
function openReport(code: string) {
  if (!code) {
    return;
  }
  rememberReport(code);
  router.push({
    path: `/mes/report-dataset/preview/${code}`,
    query: { from: 'center' },
  });
}

/** 跳转到「报表配置中心」页面，用于新增报表 */
function handleCreateReport() {
  router.push('/mes/report-dataset');
}

/** 「更多」：滚动到全部报表区域 */
function scrollToAll() {
  allSectionRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

onMounted(() => {
  loadRecentCodes();
  loadOverview();
  loadReports();
  timer = setInterval(() => {
    now.value = new Date();
  }, 30_000);
});

onBeforeUnmount(() => {
  if (timer) {
    clearInterval(timer);
  }
});
watch(selectedCode, (code) => {
  if (code) {
    localStorage.setItem(STORAGE_KEY, code);
    ensureCard(code);
  }
});

function buildParams(code: string) {
  const card = cards[code];
  const params: Record<string, any> = {};
  if (card?.startDate) {
    params.startDate = card.startDate;
  }
  if (card?.endDate) {
    params.endDate = card.endDate;
  }
  return params;
}

/** 查询数据，结果写入该报表自己的状态里 */
async function handleView(code: string) {
  const report = reports.value.find((item) => item.code === code);
  if (!report) {
    return;
  }
  const card = ensureCard(code);
  card.loading = true;
  try {
    const data = await queryReportCenterData(code, buildParams(code));
    card.headers = data.headers;
    card.rows = data.rows;
    card.columns = data.headers.map((header, index) => ({
      title: header,
      dataIndex: String(index),
      key: String(index),
      ellipsis: true,
      minWidth: 120,
    }));
    card.page = 1;
    card.collapsed = false;
    if (data.rowCount === 0) {
      message.info(`${report.name}：查询成功，暂无数据`);
    }
  } catch (error: any) {
    message.error(error?.message ?? '查询失败，请联系管理员');
  } finally {
    card.loading = false;
  }
}

/** 一键导出 Excel */
async function handleExport(code: string) {
  const report = reports.value.find((item) => item.code === code);
  if (!report) {
    return;
  }
  exporting.value = code;
  try {
    const blob = await exportReportCenterExcel(code, buildParams(code));
    downloadFileFromBlobPart({
      fileName: `${report.name}.xlsx`,
      source: blob,
    });
    message.success(`${report.name} 已开始下载，请查看浏览器下载目录`);
  } catch (error: any) {
    message.error(error?.message ?? '导出失败，请联系管理员');
  } finally {
    exporting.value = '';
  }
}

/** 收起 / 展开该报表的结果表格 */
function handleToggle(code: string) {
  const card = ensureCard(code);
  card.collapsed = !card.collapsed;
}

function handlePageChange(payload: { code: string; page: number }) {
  ensureCard(payload.code).page = payload.page;
}

function handlePageSizeChange(payload: { code: string; size: number }) {
  const card = ensureCard(payload.code);
  card.pageSize = payload.size;
  card.page = 1;
}

function handleDateChange(payload: {
  code: string;
  field: 'endDate' | 'startDate';
  value?: string;
}) {
  const card = ensureCard(payload.code);
  card[payload.field] = payload.value ?? undefined;
}

function handleSelect(code: string) {
  selectedCode.value = code;
}

onMounted(loadReports);
</script>

<template>
  <Page auto-content-height>
    <div class="prt-page">
      <!-- ===================== 顶部区域 ===================== -->
      <header class="prt-head">
        <div class="prt-head-main">
          <h1 class="prt-title">生产执行报表</h1>
          <p class="prt-subtitle">数据驱动生产，报表赋能管理</p>
        </div>
        <!-- 新增报表：权限编码保持 report:dataset:create 不变 -->
        <a-button
          v-access:code="['report:dataset:create']"
          type="primary"
          @click="handleCreateReport"
        >
          <IconifyIcon class="prt-btn-icon" icon="lucide:plus" />
          新增报表
        </a-button>
      </header>

      <!-- ===================== 报表概览 ===================== -->
      <section class="prt-card">
        <div class="prt-card-head">
          <span class="prt-card-icon">
            <IconifyIcon icon="lucide:bar-chart-3" />
          </span>
          <h2 class="prt-card-title">报表概览</h2>
          <span class="prt-card-time">
            <IconifyIcon icon="lucide:calendar-days" />
            {{ nowText }}
          </span>
        </div>
        <div class="prt-stat-grid">
          <div v-for="stat in overviewStats" :key="stat.key" class="prt-stat">
            <div
              class="prt-stat-icon"
              :style="{ background: stat.iconBg, color: stat.iconColor }"
            >
              <IconifyIcon class="size-5" :icon="stat.icon" />
            </div>
            <div class="prt-stat-body">
              <a-tooltip :title="stat.hint">
                <div class="prt-stat-label">{{ stat.label }}</div>
              </a-tooltip>
              <div
                class="prt-stat-value"
                :class="{ 'is-empty': overviewLoading || stat.value === null }"
              >
                <template v-if="overviewLoading">—</template>
                <template v-else-if="stat.value !== null">
                  {{ stat.value }}
                  <span class="prt-stat-unit">{{ stat.unit }}</span>
                </template>
                <template v-else>暂无数据</template>
              </div>
              <div
                v-if="!overviewLoading && stat.value !== null"
                class="prt-stat-trend"
                :class="{
                  'is-up': stat.trend && stat.trendUp,
                  'is-down': stat.trend && !stat.trendUp,
                }"
              >
                {{ stat.trend ?? '暂无对比数据' }}
              </div>
            </div>
          </div>
          <div v-if="overviewStats.length === 0" class="prt-empty">
            暂无统计数据
          </div>
        </div>
      </section>

      <a-spin :spinning="loading">
        <div class="prt-two-col">
          <!-- ===================== 热门报表 ===================== -->
          <section class="prt-card">
            <div class="prt-card-head">
              <span class="prt-card-icon">
                <IconifyIcon icon="lucide:flame" />
              </span>
              <h2 class="prt-card-title">热门报表</h2>
              <button class="prt-more" type="button" @click="scrollToAll">
                更多
                <IconifyIcon icon="lucide:chevron-right" />
              </button>
            </div>
            <div v-if="hotReports.length > 0" class="prt-mini-grid">
              <div
                v-for="report in hotReports"
                :key="report.code"
                class="prt-mini-card"
                @click="openReport(report.code)"
              >
                <div class="prt-mini-icon">
                  <IconifyIcon class="size-5" icon="lucide:file-bar-chart" />
                </div>
                <div class="prt-mini-main">
                  <div class="prt-mini-name">{{ report.name }}</div>
                  <div class="prt-mini-desc">
                    {{ report.description || '—' }}
                  </div>
                </div>
                <IconifyIcon
                  class="prt-mini-arrow"
                  icon="lucide:chevron-right"
                />
              </div>
            </div>
            <div v-else class="prt-empty">暂无可查看的报表</div>
          </section>

          <!-- ===================== 我的报表 ===================== -->
          <section class="prt-card">
            <div class="prt-card-head">
              <span class="prt-card-icon">
                <IconifyIcon icon="lucide:user" />
              </span>
              <h2 class="prt-card-title">我的报表</h2>
              <button class="prt-more" type="button" @click="scrollToAll">
                更多
                <IconifyIcon icon="lucide:chevron-right" />
              </button>
            </div>
            <div v-if="myReports.length > 0" class="prt-list">
              <div
                v-for="report in myReports"
                :key="report.code"
                class="prt-list-item"
                @click="openReport(report.code)"
              >
                <div class="prt-mini-icon">
                  <IconifyIcon class="size-5" icon="lucide:file-text" />
                </div>
                <div class="prt-mini-main">
                  <div class="prt-mini-name">{{ report.name }}</div>
                  <div class="prt-mini-desc">
                    {{ report.description || '—' }}
                  </div>
                </div>
                <IconifyIcon
                  class="prt-mini-arrow"
                  icon="lucide:chevron-right"
                />
              </div>
            </div>
            <div v-else class="prt-empty">
              暂无浏览记录，查看报表后将在此展示
            </div>
          </section>
        </div>

        <!-- ===================== 全部报表 ===================== -->
        <section ref="allSectionRef" class="prt-card">
          <div class="prt-card-head">
            <span class="prt-card-icon">
              <IconifyIcon icon="lucide:layout-grid" />
            </span>
            <h2 class="prt-card-title">全部报表</h2>
            <span class="prt-card-extra">
              共 {{ filteredReports.length }} / {{ reports.length }} 张
            </span>
          </div>

          <div class="prt-all-bar">
            <div class="prt-tabs">
              <button
                v-for="tab in CATEGORY_TABS"
                :key="tab.key"
                class="prt-tab"
                :class="{ 'is-active': activeCategory === tab.key }"
                type="button"
                @click="activeCategory = tab.key"
              >
                {{ tab.label }}
              </button>
            </div>
            <a-input-search
              v-model:value="keyword"
              allow-clear
              class="prt-search"
              placeholder="搜索报表名称或用途"
            />
          </div>

          <div v-if="filteredReports.length > 0" class="prt-grid">
            <div
              v-for="report in filteredReports"
              :key="report.code"
              class="prt-report-card"
              @click="openReport(report.code)"
            >
              <div class="prt-report-top">
                <div class="prt-report-icon">
                  <IconifyIcon class="size-5" icon="lucide:file-text" />
                </div>
              </div>
              <div class="prt-report-name">{{ report.name }}</div>
              <div class="prt-report-desc">
                {{ report.description || '—' }}
              </div>
              <a-button
                class="prt-report-btn"
                size="small"
                type="link"
                @click.stop="openReport(report.code)"
              >
                查看报表
              </a-button>
            </div>
          </div>
          <div v-else class="prt-empty">
            {{
              reports.length === 0
                ? '暂无可查看的报表'
                : '没有匹配的报表，试试「库存」「合格率」「工单」'
            }}
          </div>
        </section>
    <div class="report-center p-5">
      <div class="rc-page-head">
        <div class="rc-page-head-main">
          <div class="rc-page-title">MES 报表中心</div>
          <div class="rc-page-sub">
            选择报表，设置日期后查询预览，或一键导出 Excel
          </div>
        </div>
        <div class="rc-page-count">共 {{ reports.length }} 张报表</div>
      </div>

      <a-spin :spinning="loading">
        <div class="rc-layout">
          <ReportSidebar
            :reports="reports"
            :selected-code="selectedCode"
            @select="handleSelect"
          />
          <ReportPanel
            :card="currentCard"
            :exporting="exporting === selectedCode"
            :report="selectedReport"
            @date-change="handleDateChange"
            @export="handleExport"
            @page-change="handlePageChange"
            @page-size-change="handlePageSizeChange"
            @toggle="handleToggle"
            @view="handleView"
          />
        </div>
      </a-spin>
    </div>
  </Page>
</template>

<style scoped>
/* ===================== 页面骨架：浅灰蓝底 + 白卡片，增强对比 ===================== */
.prt-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: calc(100% + 32px);
  /* 抵消 Page 内容区的 p-4，让页面底色铺满内容区 */
  margin: -16px;
  padding: 20px 20px 28px;
  background: #eef2f7;
}

/* ---------------- 顶部区域 ---------------- */
.prt-head {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.prt-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  line-height: 1.4;
  color: #14263c;
}

.prt-subtitle {
  margin: 4px 0 0;
  font-size: 13px;
  color: #3d5270;
}

.prt-btn-icon {
  margin-right: 4px;
}

/* ---------------- 卡片容器 ---------------- */
.prt-card {
  padding: 16px 18px 18px;
  background: #fff;
  border: 1px solid #c3d3e8;
  border-radius: 10px;
  box-shadow: 0 2px 8px rgb(31 45 61 / 5%);
  transition: border-color 0.15s ease;
}

.prt-card:hover {
  border-color: #a9c3e6;
}

.prt-card-head {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 14px;
}

/* 卡片标题图标：主色浅底，替代原蓝色竖条 */
.prt-card-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  font-size: 14px;
  color: #1677ff;
  background: #e8f2ff;
  border-radius: 6px;
}

.prt-card-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #14263c;
}

.prt-card-time {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  margin-left: auto;
  font-size: 12px;
  color: #647890;
}

.prt-card-extra {
  margin-left: auto;
  font-size: 12px;
  color: #647890;
}

.prt-more {
  display: inline-flex;
  gap: 2px;
  align-items: center;
  padding: 0;
  margin-left: auto;
  font-size: 12px;
  color: #647890;
  cursor: pointer;
  background: transparent;
  border: 0;
  transition: color 0.15s ease;
}

.prt-more:hover {
  color: #1677ff;
}

/* ---------------- 报表概览 ---------------- */
.prt-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.prt-stat {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 14px 16px;
  background: #fff;
  border: 1px solid #c3d3e8;
  border-radius: 10px;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}

.prt-stat:hover {
  border-color: #a9c3e6;
  box-shadow: 0 2px 8px rgb(31 45 61 / 8%);
}

.prt-stat-icon {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
}

.prt-stat-body {
  min-width: 0;
}

.prt-stat-label {
  font-size: 13px;
  font-weight: 500;
  color: #3d5270;
}

.prt-stat-value {
  margin-top: 2px;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
  color: #0e2136;
}

.prt-stat-value.is-empty {
  font-size: 15px;
  font-weight: 500;
  color: #7a8ba3;
}

.prt-stat-unit {
  margin-left: 3px;
  font-size: 12px;
  font-weight: 400;
  color: #647890;
}

.prt-stat-trend {
  margin-top: 2px;
  font-size: 12px;
  color: #647890;
}

.prt-stat-trend.is-up {
  color: #16a34a;
}

.prt-stat-trend.is-down {
  color: #e5484d;
}

/* ---------------- 双列区域：热门报表 / 我的报表 ---------------- */
.prt-two-col {
  display: grid;
  grid-template-columns: minmax(0, 1.12fr) minmax(0, 1fr);
  gap: 14px;
  margin: 14px 0;
}

.prt-mini-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.prt-mini-card,
.prt-list-item {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 11px 12px;
  cursor: pointer;
  background: #fff;
  border: 1px solid #c3d3e8;
  border-radius: 8px;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}

.prt-list-item + .prt-list-item {
  margin-top: 10px;
}

.prt-mini-card:hover,
.prt-list-item:hover {
  border-color: #a9c3e6;
  box-shadow: 0 2px 8px rgb(31 45 61 / 8%);
}

.prt-mini-icon {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  color: #1677ff;
  background: #e8f2ff;
  border-radius: 8px;
}

.prt-mini-main {
  min-width: 0;
  flex: 1;
}

.prt-mini-name {
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  color: #14263c;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.prt-mini-desc {
  display: -webkit-box;
  margin-top: 2px;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.4;
  color: #647890;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.prt-mini-arrow {
  flex: 0 0 auto;
  color: #a8b6c8;
}

/* ---------------- 全部报表 ---------------- */
.prt-all-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.prt-search {
  width: 220px;
}

.prt-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.prt-tab {
  padding: 5px 16px;
  font-size: 13px;
  font-weight: 500;
  color: #3d5270;
  cursor: pointer;
  background: #f4f7fb;
  border: 1px solid transparent;
  border-radius: 16px;
  transition: all 0.15s ease;
}

.prt-tab:hover {
  color: #1677ff;
}

.prt-tab.is-active {
  color: #fff;
  background: #1677ff;
}

/* ---------------- 报表卡片网格 ---------------- */
.prt-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.prt-report-card {
  display: flex;
  flex-direction: column;
  padding: 14px 16px 10px;
  cursor: pointer;
  background: #fff;
  border: 1px solid #c3d3e8;
  border-radius: 10px;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}

.prt-report-card:hover {
  border-color: #a9c3e6;
  box-shadow: 0 2px 8px rgb(31 45 61 / 8%);
}

.prt-report-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.prt-report-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  color: #1677ff;
  background: #e8f2ff;
  border-radius: 8px;
}

.prt-report-name {
  margin-top: 10px;
  overflow: hidden;
  font-size: 14px;
  font-weight: 600;
  color: #14263c;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.prt-report-desc {
  display: -webkit-box;
  min-height: 34px;
  margin-top: 4px;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.5;
  color: #647890;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.prt-report-btn {
  align-self: flex-start;
  height: auto;
  padding: 6px 0 4px;
  font-size: 13px;
}

/* ---------------- 空态 ---------------- */
.prt-empty {
  grid-column: 1 / -1;
  padding: 40px 0;
  font-size: 13px;
  color: #7a8ba3;
  text-align: center;
}

/* ---------------- 响应式：桌面端常见分辨率 ---------------- */
/* 注意：.prt-stat-grid（报表概览 4 卡片）任何宽度下都保持同一行 4 列，不参与换行 */
@media (max-width: 1440px) {
  .prt-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1200px) {
  .prt-two-col {
    grid-template-columns: minmax(0, 1fr);
  }

  .prt-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .prt-mini-grid,
  .prt-grid {
    grid-template-columns: minmax(0, 1fr);
  }
/* ---- 工业蓝灰后台风格：稳重、留白、层级清晰 ---- */
.report-center {
  display: flex;
  flex-direction: column;
  height: 100%;
  max-width: 1400px;
  margin: 0 auto;
}

.rc-page-head {
  display: flex;
  flex: 0 0 auto;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 14px;
}

.rc-page-title {
  font-size: 20px;
  font-weight: 600;
  color: #24344d;
}

.rc-page-sub {
  margin-top: 4px;
  font-size: 13px;
  color: #7b8794;
}

.rc-page-count {
  font-size: 13px;
  color: #7b8794;
}

/* 左右分栏：左侧固定宽度报表目录，右侧详情自适应 */
.rc-layout {
  display: flex;
  gap: 14px;
  align-items: stretch;
  min-height: 560px;
  height: calc(100vh - 190px);
}
</style>
