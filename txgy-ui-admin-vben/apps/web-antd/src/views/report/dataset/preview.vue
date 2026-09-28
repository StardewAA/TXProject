<script lang="ts" setup>
import type { ReportDatasetApi } from '#/api/report/dataset';

import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';
import { useAccessStore } from '@vben/stores';
import { downloadFileFromBlobPart } from '@vben/utils';

import { message } from 'ant-design-vue';

import {
  exportReportCenterExcel,
  getReportCenterList,
  queryReportCenterData,
} from '#/api/report/center';
import { executeDataset, getEnabledDatasetList } from '#/api/report/dataset';

/** 表格列定义（直接用于 a-table） */
interface ViewColumn {
  /** 中文表头 */
  title: string;
  /** 列标识：数据集口径为字段名，报表中心口径为 col_序号 */
  dataIndex: string;
  /** 对齐方式（数字列右对齐） */
  align: 'left' | 'right';
  /** 超长内容省略 */
  ellipsis: boolean;
  /** 空值统一显示 '-' */
  customRender: (payload: { text: unknown }) => string;
}

const route = useRoute();
const router = useRouter();
const accessStore = useAccessStore();

/** 当前查看的报表编码 */
const code = computed(() => String(route.params.code ?? ''));
/** 来源入口：center = 生产执行报表；dataset = 报表配置中心（默认） */
const from = computed(() =>
  route.query.from === 'center' ? 'center' : 'dataset',
);

/** 是否具备数据集执行权限（报表配置中心口径） */
const canExecuteDataset = computed(() =>
  accessStore.accessCodes.includes('report:dataset:execute'),
);
/** 是否具备报表中心查询权限（生产执行报表口径） */
const canQueryCenter = computed(() =>
  accessStore.accessCodes.includes('report:center:query'),
);
/** 是否可导出（后端导出接口要求 report:center:export） */
const canExport = computed(() =>
  accessStore.accessCodes.includes('report:center:export'),
);

/**
 * 取数口径
 *
 * 两个入口的数据源与权限码不同：
 *  - 报表配置中心（from=dataset）→ /report/dataset/*（report:dataset:*）
 *  - 生产执行报表（from=center） → /report/center/*（report:center:*）
 * 入口对应权限缺失时回退到另一个可用口径，避免整页不可用。
 */
const useCenterApi = computed(() => {
  if (from.value === 'center') {
    return canQueryCenter.value || !canExecuteDataset.value;
  }
  return !canExecuteDataset.value && canQueryCenter.value;
});

const loading = ref(false);
const exporting = ref(false);
const loadError = ref('');
const notFound = ref(false);

/** 报表名称 */
const reportName = ref('');
/** 报表说明 */
const reportDesc = ref('');
/** 表格列 */
const columns = ref<ViewColumn[]>([]);
/** 全量数据行（属性名与表格列 dataIndex 对应） */
const rows = ref<Record<string, any>[]>([]);
/** 查询耗时（毫秒），0 表示接口未返回 */
const durationMs = ref(0);
/** 该报表是否支持日期范围筛选（由后端参数定义决定） */
const hasDateFilter = ref(false);

/** 分页 */
const page = ref(1);
const pageSize = ref(20);

/** 查询条件：日期范围（后端过滤） */
const dateRange = ref<[string, string] | null>(null);
/** 查询条件：文本列筛选（前端过滤） */
const filters = reactive<Record<string, string>>({});

/**
 * 技术字段黑名单：即使接口返回也不展示给普通用户
 */
const TECH_FIELDS = new Set([
  'tenant_id',
  'id',
  'deleted',
  'deleted_time',
  'del_flag',
  'create_time',
  'update_time',
  'version',
  'creator',
  'updater',
  'create_by',
  'update_by',
]);

/**
 * 常见字段中文映射（兜底，优先使用数据集自带的 columnLabelList）
 */
const COMMON_FIELD_LABELS: Record<string, string> = {
  status: '状态',
  status_name: '状态',
  quantity: '数量',
  remark: '备注',
  work_order_code: '工单号',
  work_order_name: '工单名称',
  process_name: '工序',
  workstation_id: '工作站',
  workstation_name: '工位',
  record_count: '报工笔数',
  feedback_code: '报工单号',
  feedback_quantity: '报工数量',
  feedback_time: '报工时间',
  qualified_quantity: '合格数量',
  unqualified_quantity: '不合格数量',
  work_order_count: '工单数',
  item_code: '物料编码',
  item_name: '物料名称',
  consume_date: '领料日期',
  consume_quantity: '耗用数量',
  plan_quantity: '计划数量',
  produced_quantity: '已生产数量',
  progress_percent: '完成进度(%)',
  request_date: '计划日期',
  finish_date: '完工日期',
  report_date: '日期',
  labor_scrap_quantity: '人工报废数量',
  material_scrap_quantity: '物料报废数量',
  other_scrap_quantity: '其他废品数量',
  machinery_code: '设备编码',
  machinery_name: '设备名称',
  repair_count: '维修次数',
  repair_hours: '维修工时(小时)',
  last_check_time: '最近点检时间',
  last_mainten_time: '最近保养时间',
  qc_type: '检验类型',
  qc_code: '检验单号',
  partner_name: '供应商/工单/客户/批次',
  check_quantity: '检验数量',
  pass_rate: '合格率(%)',
  check_result_name: '检验结果',
  inspect_date: '检验日期',
  in_quantity: '期间入库数量',
  out_quantity: '期间出库数量',
  stock_quantity: '当前在库数量',
  doc_type: '单据类型',
  doc_code: '单据编号',
  doc_date: '单据日期',
};

/** 后端对未填写备注的自定义报表返回的占位说明，不作为报表说明展示 */
const CUSTOM_REPORT_PLACEHOLDER = '自定义报表';

/** 是否为日期列（用于区分查询条件与数字右对齐） */
function isDateColumn(column: ViewColumn): boolean {
  return (
    /(日期|时间)/.test(column.title) ||
    /(_date|_time|date|time)$/i.test(column.dataIndex)
  );
}

/** 是否为有效数值（决定列是否右对齐） */
function isNumber(value: unknown): boolean {
  return typeof value === 'number' && Number.isFinite(value);
}

/** 单元格格式化：空值显示 '-' */
function formatCell(value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  return String(value);
}

/** 来源名称（返回按钮文案） */
const fromName = computed(() =>
  from.value === 'center' ? '生产执行报表' : '报表配置中心',
);

/** 可筛选的文本列：取前 3 个非日期列 */
const filterFields = computed(() =>
  columns.value.filter((column) => !isDateColumn(column)).slice(0, 3),
);

/** 文本筛选后的数据 */
const filteredRows = computed(() => {
  let list = rows.value;
  for (const column of filterFields.value) {
    const keyword = (filters[column.dataIndex] ?? '').trim();
    if (!keyword) {
      continue;
    }
    list = list.filter((row) =>
      String(row[column.dataIndex] ?? '').includes(keyword),
    );
  }
  return list;
});

/** 分页切片后的数据 */
const pagedSource = computed(() => {
  const start = (page.value - 1) * pageSize.value;
  return filteredRows.value.slice(start, start + pageSize.value);
});

/** 总条数（过滤后） */
const total = computed(() => filteredRows.value.length);

/** 数据集口径：优先按数据集勾选顺序，回退到查询结果列顺序 */
function buildDatasetColumns(
  labels: ReportDatasetApi.ColumnLabel[] | undefined,
  dimensions: string[] | undefined,
  sample: Record<string, any> | undefined,
): ViewColumn[] {
  const validLabels = (labels ?? []).filter(
    (item) => !TECH_FIELDS.has(item.field),
  );
  if (validLabels.length > 0) {
    return validLabels.map((item) => ({
      title: item.label || COMMON_FIELD_LABELS[item.field] || item.field,
      dataIndex: item.field,
      align: isNumber(sample?.[item.field]) ? 'right' : 'left',
      ellipsis: true,
      customRender: ({ text }: { text: unknown }) => formatCell(text),
    }));
  }
  return (dimensions ?? [])
    .filter((field) => !TECH_FIELDS.has(field))
    .map((field) => ({
      title: COMMON_FIELD_LABELS[field] ?? field,
      dataIndex: field,
      align: isNumber(sample?.[field]) ? 'right' : 'left',
      ellipsis: true,
      customRender: ({ text }: { text: unknown }) => formatCell(text),
    }));
}

/** 报表中心口径：后端已返回中文表头，数据按表头顺序排列 */
function buildCenterColumns(
  headers: string[] | undefined,
  firstRow: any[] | undefined,
): ViewColumn[] {
  return (headers ?? []).map((header, index) => ({
    title: header,
    dataIndex: `col_${index}`,
    align: isNumber(firstRow?.[index]) ? 'right' : 'left',
    ellipsis: true,
    customRender: ({ text }: { text: unknown }) => formatCell(text),
  }));
}

/** 组装查询参数：日期范围由后端过滤，租户与行数上限由服务端强制 */
function buildParams(): Record<string, any> {
  const params: Record<string, any> = {};
  if (hasDateFilter.value && dateRange.value) {
    params.startDate = dateRange.value[0];
    params.endDate = dateRange.value[1];
  }
  return params;
}

/** 报表配置中心口径：读取数据集配置 + 执行数据集查询 */
async function loadFromDataset() {
  const list = await getEnabledDatasetList();
  const dataset = list.find((item) => item.code === code.value);
  if (!dataset) {
    notFound.value = true;
    return;
  }
  reportName.value = dataset.name ?? code.value;
  reportDesc.value = dataset.remark ?? '';
  hasDateFilter.value = (dataset.params ?? []).some(
    (param) => param.name === 'startDate' || param.name === 'endDate',
  );

  const data = await executeDataset(code.value, buildParams());
  const source = data.source ?? [];
  columns.value = buildDatasetColumns(
    dataset.columnLabelList,
    data.dimensions,
    source[0],
  );
  rows.value = source.map((row) => ({ ...row }));
  durationMs.value = data.durationMs ?? 0;
}

/** 生产执行报表口径：报表清单已含名称、说明与筛选参数 */
async function loadFromCenter() {
  const list = await getReportCenterList();
  const report = list.find((item) => item.code === code.value);
  if (!report) {
    notFound.value = true;
    return;
  }
  reportName.value = report.name;
  reportDesc.value =
    report.description === CUSTOM_REPORT_PLACEHOLDER
      ? ''
      : (report.description ?? '');
  hasDateFilter.value = (report.params ?? []).some(
    (param) => param.name === 'startDate' || param.name === 'endDate',
  );

  const data = await queryReportCenterData(code.value, buildParams());
  const headers = data.headers ?? [];
  columns.value = buildCenterColumns(headers, data.rows?.[0]);
  rows.value = (data.rows ?? []).map((row) => {
    const record: Record<string, any> = {};
    headers.forEach((_, index) => {
      record[`col_${index}`] = row[index];
    });
    return record;
  });
  durationMs.value = data.durationMs ?? 0;
}

/** 加载报表配置与真实数据 */
async function load() {
  loading.value = true;
  loadError.value = '';
  notFound.value = false;
  columns.value = [];
  rows.value = [];
  durationMs.value = 0;
  reportDesc.value = '';
  try {
    await (useCenterApi.value ? loadFromCenter() : loadFromDataset());
  } catch (error: any) {
    console.error('[report viewer] 加载失败:', error);
    columns.value = [];
    rows.value = [];
    loadError.value = '报表数据查询失败';
  } finally {
    loading.value = false;
  }
}

/** 查询：按日期范围重新查询，文本筛选在前端完成 */
function handleSearch() {
  page.value = 1;
  void load();
}

/** 重置查询条件 */
function handleReset() {
  dateRange.value = null;
  for (const column of filterFields.value) {
    filters[column.dataIndex] = '';
  }
  page.value = 1;
  void load();
}

/** 导出 Excel：导出后端完整结果，不受前端分页影响 */
async function handleExport() {
  exporting.value = true;
  try {
    const blob = await exportReportCenterExcel(code.value, buildParams());
    downloadFileFromBlobPart({
      fileName: `${reportName.value || code.value}.xlsx`,
      source: blob,
    });
    message.success('已开始下载，请查看浏览器下载目录');
  } catch (error: any) {
    message.error(error?.message ?? '导出失败，请稍后重试');
  } finally {
    exporting.value = false;
  }
}

function handlePageChange(next: number) {
  page.value = next;
}

/** 返回来源页面：报表中心进入回报表中心，配置中心进入回配置中心 */
function goBack() {
  router.push(from.value === 'center' ? '/mes/report-center' : '/mes/report-dataset');
}

watch(code, () => {
  dateRange.value = null;
  for (const key of Object.keys(filters)) {
    filters[key] = '';
  }
  page.value = 1;
  void load();
});

onMounted(load);
</script>

<template>
  <Page auto-content-height>
    <div class="preview-page">
      <!-- 报表基本信息 -->
      <section class="preview-card info-card">
        <div class="info-head">
          <div class="info-title-wrap">
            <h1 class="info-title">{{ reportName || '报表查看' }}</h1>
            <p v-if="reportDesc" class="info-desc">{{ reportDesc }}</p>
          </div>
          <div class="info-actions">
            <a-button v-if="canExport" :loading="exporting" @click="handleExport">
              <IconifyIcon icon="lucide:download" />
              导出 Excel
            </a-button>
            <a-button @click="goBack">
              <IconifyIcon icon="lucide:log-out" />
              退出预览
            </a-button>
          </div>
        </div>
        <div class="info-meta">
          <span class="info-item">
            <span class="info-label">报表编码</span>
            {{ code }}
          </span>
          <span v-if="durationMs > 0" class="info-item">
            <span class="info-label">查询耗时</span>
            {{ durationMs }} ms
          </span>
        </div>
      </section>

      <!-- 查询条件 -->
      <section
        v-if="!loading && (hasDateFilter || filterFields.length > 0)"
        class="preview-card"
      >
        <div class="card-title">查询条件</div>
        <a-form class="filter-form" layout="inline">
          <a-form-item
            v-for="column in filterFields"
            :key="column.dataIndex"
            :label="column.title"
          >
            <a-input
              v-model:value="filters[column.dataIndex]"
              :placeholder="`请输入${column.title}`"
              allow-clear
              style="width: 180px"
              @press-enter="handleSearch"
            />
          </a-form-item>
          <a-form-item v-if="hasDateFilter" label="日期范围">
            <a-range-picker
              v-model:value="dateRange"
              style="width: 240px"
              value-format="YYYY-MM-DD"
            />
          </a-form-item>
          <a-form-item>
            <a-space>
              <a-button type="primary" @click="handleSearch">查询</a-button>
              <a-button @click="handleReset">重置</a-button>
            </a-space>
          </a-form-item>
        </a-form>
      </section>

      <!-- 结果 -->
      <section class="preview-card">
        <!-- 加载 -->
        <div v-if="loading" class="preview-state">
          <a-spin />
          <p class="preview-state-text">正在加载报表数据...</p>
        </div>

        <!-- 报表不存在 -->
        <div v-else-if="notFound" class="preview-state">
          <IconifyIcon class="preview-state-icon" icon="lucide:file-question" />
          <p class="preview-state-title">报表不存在或已被删除</p>
          <div class="preview-state-actions">
            <a-button type="primary" @click="goBack">
              返回{{ fromName }}
            </a-button>
          </div>
        </div>

        <!-- 查询失败 -->
        <div v-else-if="loadError" class="preview-state">
          <IconifyIcon class="preview-state-icon" icon="lucide:circle-alert" />
          <p class="preview-state-title">报表数据查询失败</p>
          <p class="preview-state-text">请稍后重试，或联系系统管理员</p>
          <div class="preview-state-actions">
            <a-button type="primary" @click="load">重新加载</a-button>
            <a-button @click="goBack">返回</a-button>
          </div>
        </div>

        <!-- 空数据 -->
        <div v-else-if="total === 0" class="preview-state">
          <IconifyIcon class="preview-state-icon" icon="lucide:inbox" />
          <p class="preview-state-title">暂无数据</p>
          <p class="preview-state-text">当前报表暂无符合条件的数据</p>
        </div>

        <!-- 表格 -->
        <template v-else>
          <div class="table-head">
            <span class="table-title">{{ reportName || '查询结果' }}</span>
            <span class="table-count">共 {{ total }} 条记录</span>
          </div>
          <a-table
            :columns="columns"
            :data-source="pagedSource"
            :pagination="false"
            :row-key="(_record: any, index: number) => String(index)"
            :scroll="{ x: 'max-content' }"
            size="middle"
          />
          <div class="table-footer">
            <span class="table-total">
              共 {{ total }} 条 · 每页 {{ pageSize }} 条
            </span>
            <a-pagination
              :current="page"
              :page-size="pageSize"
              :show-size-changer="false"
              :total="total"
              @change="handlePageChange"
            />
          </div>
        </template>
      </section>
    </div>
  </Page>
</template>

<style scoped>
.preview-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 100%;
  padding: 4px 4px 16px;
}

.preview-card {
  padding: 16px 20px 20px;
  background: #fff;
  border: 1px solid #d7e2f0;
  border-radius: 10px;
  box-shadow: 0 2px 8px rgb(31 45 61 / 4%);
}

.info-card {
  padding: 20px 24px;
}

.info-head {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
}

.info-title-wrap {
  flex: 1;
  min-width: 240px;
}

.info-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #1f2d3d;
}

.info-desc {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: #52657a;
}

.info-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.info-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 28px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px dashed #e8eef6;
}

.info-item {
  font-size: 13px;
  color: #52657a;
}

.info-label {
  margin-right: 6px;
  color: #8a9aaf;
}

.card-title {
  margin-bottom: 12px;
  font-size: 15px;
  font-weight: 600;
  color: #1f2d3d;
}

.filter-form {
  row-gap: 12px;
}

.table-head {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.table-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2d3d;
}

.table-count {
  font-size: 13px;
  color: #8a9aaf;
}

.table-footer {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: flex-end;
  margin-top: 14px;
}

.table-total {
  font-size: 13px;
  color: #8a9aaf;
}

.preview-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 260px;
  text-align: center;
}

.preview-state-icon {
  font-size: 40px;
  color: #c0c9d3;
}

.preview-state-title {
  margin: 4px 0 0;
  font-size: 15px;
  font-weight: 600;
  color: #1f2d3d;
}

.preview-state-text {
  margin: 0;
  font-size: 13px;
  color: #8a9aaf;
}

.preview-state-actions {
  display: flex;
  gap: 10px;
  margin-top: 10px;
}

/* 表格斑马纹 + hover */
.preview-card :deep(.ant-table-tbody > tr:nth-child(even) > td) {
  background: #fafbfd;
}

.preview-card :deep(.ant-table-tbody > tr:hover > td) {
  background: #f2f7ff !important;
}

.preview-card :deep(.ant-table-thead > tr > th) {
  color: #344356;
  font-weight: 600;
  background: #f3f6fa;
}
</style>
