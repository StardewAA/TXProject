<!--
  招标文件下载名单（抽屉）—— 项目台账 6771 的子功能入口（按钮权限点 bidding:tender-doc:query）
  契约：后端变更通知-获取文件与资格预审-2026-09-28（TenderDocDownloadController，前缀 /bidding/tender-doc）
  展示口径：downloadTime 为 epoch 毫秒 → fmtDateTime；下载名单按招标项目/标段聚合（downloaders 接口）。
  说明：前端「记录下载」通常在文件下载流程内调用（recordTenderDocDownload），此处提供名单查询视图。
-->
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';

import { message } from 'ant-design-vue';

import {
  type TenderDocApi,
  countTenderDocDownloaders,
  getTenderDocDownloaders,
} from '#/api/bidding/tenderDoc';

import { fmtDateTime } from '../../certificate/format';

const props = withDefaults(
  defineProps<{
    open: boolean;
    tenderProjectCode?: string;
    bidSectionCode?: string;
  }>(),
  { tenderProjectCode: undefined, bidSectionCode: undefined },
);
const emit = defineEmits<{ (e: 'update:open', v: boolean): void }>();

const rows = ref<TenderDocApi.Record[]>([]);
const loading = ref(false);
const downloadCount = ref<null | number>(null);
const keyword = reactive({ projectCode: '' });

async function load() {
  const projectCode = keyword.projectCode.trim() || props.tenderProjectCode;
  if (!projectCode) {
    message.warning('请填写招标项目编号后查询（下载名单按招标项目聚合）');
    rows.value = [];
    downloadCount.value = null;
    return;
  }
  loading.value = true;
  try {
    rows.value = await getTenderDocDownloaders(projectCode, props.bidSectionCode);
    if (props.bidSectionCode) {
      try {
        downloadCount.value = Number((await countTenderDocDownloaders(props.bidSectionCode)) ?? 0);
      } catch {
        downloadCount.value = null;
      }
    } else {
      downloadCount.value = null;
    }
  } catch {
    rows.value = [];
    downloadCount.value = null;
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  if (props.tenderProjectCode) {
    keyword.projectCode = props.tenderProjectCode;
    void load();
  }
});

const columns = [
  { title: '招标项目', dataIndex: 'tenderProjectCode', key: 'tenderProjectCode', width: 180, ellipsis: true },
  { title: '标段', dataIndex: 'bidSectionCode', key: 'bidSectionCode', width: 170, ellipsis: true },
  { title: '下载主体 ID', dataIndex: 'subjectId', key: 'subjectId', width: 110 },
  { title: '下载用户 ID', dataIndex: 'userId', key: 'userId', width: 110 },
  { title: '附件 ID', dataIndex: 'attachmentId', key: 'attachmentId', width: 100 },
  { title: '文件 ID', dataIndex: 'fileId', key: 'fileId', width: 100 },
  { title: '下载时间', dataIndex: 'downloadTime', key: 'downloadTime', width: 160 },
  { title: 'IP', dataIndex: 'ip', key: 'ip', width: 130 },
  { title: '备注', dataIndex: 'remark', key: 'remark', ellipsis: true },
];
</script>

<template>
  <a-drawer
    :open="open"
    :title="'招标文件下载名单' + (keyword.projectCode ? ' · ' + keyword.projectCode : '')"
    :width="1150"
    destroy-on-close
    @close="emit('update:open', false)"
  >
    <div class="ct-page">
      <div class="ct-head">
        <div>
          <div class="ct-title">招标文件下载名单</div>
          <div class="ct-sub">
            阶段四「获取招标文件」下载记录（澄清/异议定向通知的事实基础）；按招标项目/标段聚合查询。
            <template v-if="downloadCount !== null">当前标段下载人数：<b>{{ downloadCount }}</b></template>
          </div>
        </div>
        <div class="ct-actions">
          <a-button type="primary" :loading="loading" @click="load">查询</a-button>
        </div>
      </div>

      <div class="ct-panel">
        <div class="mb-3 rounded-md border border-border bg-card px-3.5 pt-1.5 pb-2.5">
          <div class="flex flex-wrap items-center gap-2">
            <label class="text-sm text-muted-foreground">招标项目编号</label>
            <a-input v-model:value="keyword.projectCode" placeholder="TENDER_PROJECT_CODE" style="width: 260px" allow-clear @press-enter="load" />
            <label class="text-sm text-muted-foreground">标段编号</label>
            <a-input :value="props.bidSectionCode" placeholder="可选" disabled style="width: 220px" />
          </div>
        </div>
      </div>

      <div class="ct-panel-b">
        <a-table
          class="ct-table"
          :columns="columns"
          :data-source="rows"
          :loading="loading"
          :pagination="false"
          :scroll="{ x: 1200 }"
          :row-key="(r: any) => r?.id"
          size="middle"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'downloadTime'">{{ record.downloadTime ? fmtDateTime(record.downloadTime) : '—' }}</template>
          </template>
          <template #emptyText><div class="ct-empty">暂无下载记录</div></template>
        </a-table>
      </div>
    </div>
  </a-drawer>
</template>
