<script lang="ts" setup>
import type { ReportCenterApi } from '#/api/report/center';

import type { CardState } from './types';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import { message } from 'ant-design-vue';

import {
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
    const remembered = localStorage.getItem(STORAGE_KEY) ?? '';
    const exists = reports.value.some((item) => item.code === remembered);
    selectedCode.value = exists ? remembered : (reports.value[0]?.code ?? '');
  } finally {
    loading.value = false;
  }
}

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
            @refresh="loadReports"
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
