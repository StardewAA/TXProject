<script lang="ts" setup>
import type { ReportCenterApi } from '#/api/report/center';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import { message } from 'ant-design-vue';

import {
  exportReportCenterExcel,
  getReportCenterList,
  queryReportCenterData,
} from '#/api/report/center';

/** 展示顺序与白名单（按业务日常使用频次排列，通栏卡片自上而下） */
const REPORT_ORDER = [
  'mes_daily_production', // 生产日报
  'mes_work_order_completion', // 工单完工报表
  'mes_defect_summary', // 不良品统计
  'mes_material_consumption', // 物料耗用报表
  'mes_machinery_efficiency', // 设备运行与维修统计
  'mes_feedback_detail', // 生产报工明细台账
  'mes_wm_stock_flow', // 库存收发存汇总表
];

/** 每张报表卡片独立的查询/表格/分页状态 */
interface CardState {
  loading: boolean;
  headers: string[];
  rows: any[][];
  columns: any[];
  page: number;
  pageSize: number;
}

function ensureCard(code: string): CardState {
  if (!cards[code]) {
    cards[code] = {
      loading: false,
      headers: [],
      rows: [],
      columns: [],
      page: 1,
      pageSize: 10,
    };
  }
  return cards[code];
}

const reports = ref<ReportCenterApi.Report[]>([]);
const loading = ref(false);
/** 正在导出的报表编码 */
const exporting = ref('');
/** 每张报表的筛选条件（仅日期区间，业务人员易懂） */
const filters = reactive<Record<string, { endDate?: string; startDate?: string }>>(
  {},
);
/** 每张报表的查询结果与分页状态（卡片内嵌表格，互不影响） */
const cards = reactive<Record<string, CardState>>({});

/** 排序 + 白名单过滤后的卡片清单 */
const visibleReports = computed(() => {
  const order = new Map(REPORT_ORDER.map((code, index) => [code, index]));
  return reports.value
    .filter((report) => order.has(report.code))
    .sort((a, b) => order.get(a.code)! - order.get(b.code)!);
});

function hasDateParams(report: ReportCenterApi.Report) {
  return report.params?.some(
    (param) => param.name === 'startDate' || param.name === 'endDate',
  );
}

function buildParams(report: ReportCenterApi.Report) {
  const filter = filters[report.code] ?? {};
  const params: Record<string, any> = {};
  if (filter.startDate) {
    params.startDate = filter.startDate;
  }
  if (filter.endDate) {
    params.endDate = filter.endDate;
  }
  return params;
}

/** 卡片内分页：后端一次返回全量行，切片仅作用于本卡片表格 */
function pagedRows(code: string) {
  const card = cards[code];
  if (!card) {
    return [];
  }
  const start = (card.page - 1) * card.pageSize;
  return card.rows.slice(start, start + card.pageSize);
}

/** 加载报表清单 */
async function loadReports() {
  loading.value = true;
  try {
    reports.value = await getReportCenterList();
    for (const code of REPORT_ORDER) {
      ensureCard(code);
      if (!filters[code]) {
        filters[code] = {};
      }
    }
  } finally {
    loading.value = false;
  }
}

/** 一键导出 Excel */
async function handleExport(report: ReportCenterApi.Report) {
  exporting.value = report.code;
  try {
    const blob = await exportReportCenterExcel(
      report.code,
      buildParams(report),
    );
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

/** 查询数据并展示在当前卡片内部下方 */
async function handleView(report: ReportCenterApi.Report) {
  const card = ensureCard(report.code);
  card.loading = true;
  try {
    const data = await queryReportCenterData(
      report.code,
      buildParams(report),
    );
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
    if (data.rowCount === 0) {
      message.info(`${report.name}：查询成功，暂无数据`);
    }
  } catch (error: any) {
    message.error(error?.message ?? '查询失败，请联系管理员');
  } finally {
    card.loading = false;
  }
}

/** 分页切换（仅作用于本卡片） */
function handlePageChange(code: string, page: number) {
  const card = ensureCard(code);
  card.page = page;
}

function handlePageSizeChange(code: string, current: number, size: number) {
  const card = ensureCard(code);
  card.pageSize = size;
  card.page = 1;
}

onMounted(loadReports);
</script>

<template>
  <Page auto-content-height>
    <div class="report-center p-5">
      <div class="rc-page-head">
        <div class="rc-page-title">MES 报表中心</div>
        <div class="rc-page-sub">选择报表，设置日期后查询预览，或一键导出 Excel</div>
      </div>

      <a-spin :spinning="loading">
        <div
          v-for="report in visibleReports"
          :key="report.code"
          class="rc-card"
        >
          <!-- 卡片上半部：标题 / 描述 / 查询条件 / 操作 -->
          <div class="rc-card-head">
            <div class="rc-card-title-wrap">
              <div class="rc-card-title">
                <span class="rc-title-bar"></span>
                {{ report.name }}
              </div>
              <div class="rc-card-desc">{{ report.description }}</div>
            </div>

            <div class="rc-card-actions">
              <template v-if="hasDateParams(report)">
                <span class="rc-field-label">日期</span>
                <a-date-picker
                  v-model:value="filters[report.code].startDate"
                  placeholder="开始日期"
                  size="large"
                  value-format="YYYY-MM-DD"
                />
                <span class="rc-field-sep">至</span>
                <a-date-picker
                  v-model:value="filters[report.code].endDate"
                  placeholder="结束日期"
                  size="large"
                  value-format="YYYY-MM-DD"
                />
              </template>
              <span v-else class="rc-no-param">全量数据，无需筛选</span>

              <a-button
                size="large"
                @click="handleView(report)"
              >
                查看数据
              </a-button>
              <a-button
                :loading="exporting === report.code"
                size="large"
                type="primary"
                @click="handleExport(report)"
              >
                导出 Excel
              </a-button>
            </div>
          </div>

          <!-- 卡片下半部：查询后在本卡片内展示表格 + 独立分页 -->
          <div
            v-if="cards[report.code]?.rows.length > 0 || cards[report.code]?.loading"
            class="rc-card-table"
          >
            <a-table
              :columns="cards[report.code]?.columns"
              :data-source="pagedRows(report.code)"
              :loading="cards[report.code]?.loading"
              :pagination="false"
              :row-key="(_record: any, index: number) => String(index)"
              :scroll="{ x: 'max-content' }"
              size="small"
            />
            <div class="rc-table-footer">
              <span class="rc-total">
                共 {{ cards[report.code]?.rows.length ?? 0 }} 行
              </span>
              <a-pagination
                :current="cards[report.code]?.page"
                :page-size="cards[report.code]?.pageSize"
                :page-size-options="['10', '20']"
                :show-size-changer="true"
                :total="cards[report.code]?.rows.length ?? 0"
                size="small"
                @change="(page: number) => handlePageChange(report.code, page)"
                @showSizeChange="(current: number, size: number) => handlePageSizeChange(report.code, current, size)"
              />
            </div>
          </div>
          <div
            v-else-if="!cards[report.code]?.loading"
            class="rc-card-empty"
          >
            设置条件后点击「查看数据」，结果将显示在此处
          </div>
        </div>
      </a-spin>
    </div>
  </Page>
</template>

<style scoped>
/* ---- 工业蓝灰后台风格：稳重、留白、层级清晰 ---- */
.report-center {
  max-width: 1400px;
  margin: 0 auto;
}

.rc-page-head {
  margin-bottom: 16px;
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

/* 通栏卡片：上下堆叠 */
.rc-card {
  padding: 18px 22px;
  margin-bottom: 16px;
  background: #fff;
  border: 1px solid #e3e9f0;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(36, 52, 77, 0.04);
  transition: box-shadow 0.2s ease;
}

.rc-card:hover {
  box-shadow: 0 2px 10px rgba(36, 52, 77, 0.08);
}

/* 卡片头：左标题右操作，底部对齐 */
.rc-card-head {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: flex-end;
  justify-content: space-between;
}

.rc-card-title-wrap {
  min-width: 220px;
}

.rc-card-title {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 16px;
  font-weight: 600;
  color: #24344d;
}

/* 标题左侧蓝灰竖条，工业风层级锚点 */
.rc-title-bar {
  display: inline-block;
  width: 4px;
  height: 16px;
  background: #2f5480;
  border-radius: 2px;
}

.rc-card-desc {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.5;
  color: #7b8794;
}

.rc-card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.rc-field-label {
  font-size: 13px;
  color: #4e5d6c;
}

.rc-field-sep {
  font-size: 13px;
  color: #9aa5b1;
}

.rc-no-param {
  font-size: 13px;
  color: #9aa5b1;
}

/* 卡片内嵌表格区 */
.rc-card-table {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px dashed #e3e9f0;
}

.rc-table-footer {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: flex-end;
  margin-top: 10px;
}

.rc-total {
  font-size: 12px;
  color: #7b8794;
}

/* 表头蓝灰底，稳重层级 */
.rc-card-table :deep(.ant-table-thead > tr > th) {
  color: #344356;
  background: #f3f6fa;
  font-weight: 600;
}

.rc-card-empty {
  padding: 18px 0 4px;
  margin-top: 14px;
  font-size: 13px;
  color: #a5afbb;
  border-top: 1px dashed #eef1f5;
}
</style>
