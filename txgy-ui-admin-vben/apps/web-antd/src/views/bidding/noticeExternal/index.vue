<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { NoticeExternalApi } from '#/api/bidding/noticeExternal';

import { Page, useVbenModal } from '@vben/common-ui';

import { message, Tag } from 'ant-design-vue';

import { TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  aiExtractNotice,
  approveNotice,
  bodyStructureNotice,
  getManagePage,
  rejectNotice,
  takeDownNotice,
} from '#/api/bidding/noticeExternal';

import {
  AUDIT_MAP,
  BODY_MAP,
  EXTRACTION_MAP,
  INDUSTRY_MAP,
  STATUS_MAP,
  useGridColumns,
  useGridFormSchema,
} from './data';
import DetailModal from './modules/detail.vue';

const [Detail, detailModalApi] = useVbenModal({
  connectedComponent: DetailModal,
  destroyOnClose: true,
});

function refresh() {
  gridApi.query();
}

function tagOf(map: Record<string, { text: string; color: string }>, v?: string) {
  const c = v ? map[v] : undefined;
  return c ? { text: c.text, color: c.color } : null;
}

const statusTagOf = (row: NoticeExternalApi.ManageItem) => tagOf(STATUS_MAP, row.status);
const auditTagOf = (row: NoticeExternalApi.ManageItem) => tagOf(AUDIT_MAP, row.auditStatus);
const industryTagOf = (row: NoticeExternalApi.ManageItem) => tagOf(INDUSTRY_MAP, row.industryFlag);
const extractionTagOf = (row: NoticeExternalApi.ManageItem) =>
  tagOf(EXTRACTION_MAP, row.extractionStatus);
const bodyTagOf = (row: NoticeExternalApi.ManageItem) => tagOf(BODY_MAP, row.bodyBlocksStatus);

async function runAction(label: string, fn: () => Promise<unknown>) {
  const hideLoading = message.loading({ content: `${label}处理中…`, duration: 0 });
  try {
    await fn();
    message.success(`${label}成功`);
    refresh();
  } catch (e) {
    message.error(e instanceof Error ? e.message : String(e));
  } finally {
    hideLoading();
  }
}

/** 审核通过：仅 NEW（NEW→ACTIVE，触发关注扇出 + ES 建索引） */
function handleApprove(row: NoticeExternalApi.ManageItem) {
  if (row.status !== 'NEW') {
    message.warning('仅“待审核(NEW)”公告可执行审核通过');
    return;
  }
  void runAction('审核通过', () => approveNotice(row.id!));
}

/** 驳回：仅 NEW（不展示） */
function handleReject(row: NoticeExternalApi.ManageItem) {
  if (row.status !== 'NEW') {
    message.warning('仅“待审核(NEW)”公告可驳回');
    return;
  }
  void runAction('驳回', () => rejectNotice(row.id!));
}

/** 下线：仅 ACTIVE（ACTIVE→INACTIVE，大厅展示随即消失） */
function handleTakeDown(row: NoticeExternalApi.ManageItem) {
  if (row.status !== 'ACTIVE') {
    message.warning('仅“已生效(ACTIVE)”公告可下线');
    return;
  }
  void runAction('下线', () => takeDownNotice(row.id!));
}

/** AI 结构化抽取（含行业判定回写 industryFlag/置信度） */
function handleAiExtract(row: NoticeExternalApi.ManageItem) {
  void runAction('AI 抽取', () => aiExtractNotice(row.id!));
}

/** 正文结构化（HTML 解析或 AI 兜底排版 → 结构化块） */
function handleBodyStructure(row: NoticeExternalApi.ManageItem) {
  void runAction('正文结构化', () => bodyStructureNotice(row.id!));
}

/** 管理预览（manage/get 全状态：读完正文再操作） */
function handlePreview(row: NoticeExternalApi.ManageItem) {
  detailModalApi.setData(row).open();
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
  },
  gridOptions: {
    columns: useGridColumns(),
    height: 'auto',
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          return await getManagePage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
          });
        },
      },
    },
    rowConfig: {
      keyField: 'id',
      isHover: true,
    },
    toolbarConfig: {
      refresh: true,
      search: true,
    },
  } as VxeTableGridOptions<NoticeExternalApi.ManageItem>,
});
</script>

<template>
  <Page auto-content-height>
    <Detail />
    <Grid table-title="外部公告审核管理（招投标）">
      <template #col_industry="{ row }">
        <Tag v-if="industryTagOf(row)" :color="industryTagOf(row)!.color">{{ industryTagOf(row)!.text }}</Tag>
        <span v-else>—</span>
      </template>
      <template #col_status="{ row }">
        <div class="flex flex-wrap items-center justify-center gap-1">
          <Tag v-if="statusTagOf(row)" :color="statusTagOf(row)!.color">{{ statusTagOf(row)!.text }}</Tag>
          <Tag v-if="auditTagOf(row)" :color="auditTagOf(row)!.color">{{ auditTagOf(row)!.text }}</Tag>
          <span v-if="!statusTagOf(row) && !auditTagOf(row)">—</span>
        </div>
      </template>
      <template #col_proc="{ row }">
        <div class="flex flex-wrap items-center justify-center gap-1">
          <Tag v-if="extractionTagOf(row)" :color="extractionTagOf(row)!.color">抽：{{ extractionTagOf(row)!.text }}</Tag>
          <Tag v-if="bodyTagOf(row)" :color="bodyTagOf(row)!.color">正文：{{ bodyTagOf(row)!.text }}</Tag>
          <span v-if="!extractionTagOf(row) && !bodyTagOf(row)">—</span>
        </div>
      </template>
      <template #operation="{ row }">
        <TableAction
          :actions="[
            {
              label: '预览',
              type: 'link',
              auth: ['bidding:notice-external:update'],
              onClick: () => handlePreview(row),
            },
            {
              label: '审核通过',
              type: 'link',
              auth: ['bidding:notice-external:update'],
              popConfirm: { title: '确认审核通过？将对外发布并触发关注提醒。', confirm: () => handleApprove(row) },
            },
            {
              label: '驳回',
              type: 'link',
              danger: true,
              auth: ['bidding:notice-external:update'],
              popConfirm: { title: '确认驳回该公告？（将不对外展示）', confirm: () => handleReject(row) },
            },
            {
              label: '下线',
              type: 'link',
              danger: true,
              auth: ['bidding:notice-external:update'],
              popConfirm: { title: '确认下线？（大厅展示随即消失）', confirm: () => handleTakeDown(row) },
            },
            {
              label: 'AI 抽取',
              type: 'link',
              auth: ['bidding:notice-external:update'],
              popConfirm: { title: '执行 AI 结构化抽取（含行业判定回写）？可能需要一些时间。', confirm: () => handleAiExtract(row) },
            },
            {
              label: '正文结构化',
              type: 'link',
              auth: ['bidding:notice-external:update'],
              popConfirm: { title: '执行正文结构化（解析/兜底排版为段落表格）？', confirm: () => handleBodyStructure(row) },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
