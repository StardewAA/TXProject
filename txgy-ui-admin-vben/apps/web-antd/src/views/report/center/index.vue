<script lang="ts" setup>
import type { ReportCenterApi } from '#/api/report/center';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import { message } from 'ant-design-vue';

import {
  exportReportCenterExcel,
  getReportCenterList,
  queryReportCenterData,
} from '#/api/report/center';

const reports = ref<ReportCenterApi.Report[]>([]);
const loading = ref(false);
/** 每个报表的筛选条件（仅日期区间，业务人员易懂） */
const filters = reactive<Record<string, { endDate?: string; startDate?: string }>>(
  {},
);
/** 正在导出的报表编码 */
const exporting = ref('');
/** 数据弹窗 */
const viewOpen = ref(false);
const viewTitle = ref('');
const viewLoading = ref(false);
const viewHeaders = ref<string[]>([]);
const viewRows = ref<any[][]>([]);
const viewColumns = ref<any[]>([]);

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

/** 加载报表清单 */
async function loadReports() {
  loading.value = true;
  try {
    reports.value = await getReportCenterList();
    reports.value.forEach((report) => {
      filters[report.code] = {};
    });
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

/** 查看数据（导出前先看一眼） */
async function handleView(report: ReportCenterApi.Report) {
  viewTitle.value = report.name;
  viewOpen.value = true;
  viewLoading.value = true;
  viewHeaders.value = [];
  viewRows.value = [];
  viewColumns.value = [];
  try {
    const data = await queryReportCenterData(
      report.code,
      buildParams(report),
    );
    viewHeaders.value = data.headers;
    viewRows.value = data.rows;
    viewColumns.value = data.headers.map((header, index) => ({
      title: header,
      dataIndex: String(index),
      key: String(index),
      ellipsis: true,
      minWidth: 120,
    }));
    message.success(`共 ${data.rowCount} 行数据`);
  } catch (error: any) {
    message.error(error?.message ?? '查询失败，请联系管理员');
  } finally {
    viewLoading.value = false;
  }
}

onMounted(loadReports);
</script>

<template>
  <Page auto-content-height>
    <div class="p-6">
      <div class="mb-2 text-2xl font-semibold">MES 报表中心</div>
      <div class="mb-6 text-base text-gray-500">
        选择下面的报表，点一下按钮就能下载 Excel 表格
      </div>

      <a-spin :spinning="loading">
        <a-row :gutter="[16, 16]">
          <a-col
            v-for="report in reports"
            :key="report.code"
            :lg="8"
            :md="12"
            :sm="24"
            :xs="24"
          >
            <a-card :title="report.name" size="default">
              <div class="min-h-12 text-base text-gray-600">
                {{ report.description }}
              </div>

              <div
                v-if="hasDateParams(report)"
                class="mt-4 flex flex-wrap items-center gap-2"
              >
                <span class="text-base">日期：</span>
                <a-date-picker
                  v-model:value="filters[report.code].startDate"
                  placeholder="开始日期"
                  value-format="YYYY-MM-DD"
                />
                <span class="text-base">至</span>
                <a-date-picker
                  v-model:value="filters[report.code].endDate"
                  placeholder="结束日期"
                  value-format="YYYY-MM-DD"
                />
              </div>
              <div v-else class="mt-4 text-sm text-gray-400">
                无需选择条件，直接导出全部数据
              </div>

              <div class="mt-5 flex items-center gap-3">
                <a-button
                  :loading="exporting === report.code"
                  size="large"
                  type="primary"
                  @click="handleExport(report)"
                >
                  导出 Excel
                </a-button>
                <a-button size="large" @click="handleView(report)">
                  查看数据
                </a-button>
              </div>
            </a-card>
          </a-col>
        </a-row>
      </a-spin>
    </div>

    <a-drawer v-model:open="viewOpen" :title="viewTitle" width="1000">
      <a-table
        v-if="viewColumns.length > 0"
        :columns="viewColumns"
        :data-source="viewRows"
        :loading="viewLoading"
        :pagination="{ pageSize: 20 }"
        :row-key="(_record: any, index: number) => String(index)"
        :scroll="{ x: 'max-content' }"
        size="small"
      />
      <a-empty v-else-if="!viewLoading" description="暂无数据" />
    </a-drawer>
  </Page>
</template>
