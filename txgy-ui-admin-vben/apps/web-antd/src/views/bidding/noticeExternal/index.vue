<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { NoticeExternalApi } from '#/api/bidding/noticeExternal';

import { Page, useVbenModal } from '@vben/common-ui';

import { Dropdown, message, Modal, Tag } from 'ant-design-vue';

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


/** 下线：仅 ACTIVE（ACTIVE→INACTIVE，大厅展示随即消失） */
function handleTakeDown(row: NoticeExternalApi.ManageItem) {
  if (row.status !== 'ACTIVE') {
    message.warning('仅“已生效(ACTIVE)”公告可下线');
    return;
  }
  void runAction('下线', () => takeDownNotice(row.id!));
}



/** 行主操作：预览 + 当前状态下的关键动作（审核通过/下线）；次要动作全部收进“更多 ▾”避免按钮挤压 */
function rowActions(row: NoticeExternalApi.ManageItem): any[] {
  const acts: Array<Record<string, unknown>> = [
    { label: '预览', type: 'link', auth: ['bidding:notice-external:update'], onClick: () => handlePreview(row) },
  ];
  if (row.auditStatus === 'UNREVIEWED') {
    acts.push({
      label: '审核通过',
      type: 'link',
      auth: ['bidding:notice-external:update'],
      popConfirm: { title: '确认审核通过？将对外发布并触发关注提醒。', confirm: () => handleApprove(row) },
    });
  } else if (row.status === 'ACTIVE') {
    acts.push({
      label: '下线',
      type: 'link',
      danger: true,
      auth: ['bidding:notice-external:update'],
      popConfirm: { title: '确认下线？（大厅展示随即消失）', confirm: () => handleTakeDown(row) },
    });
  }
  return acts;
}

/** 更多菜单项（按行状态裁剪） */
function moreItems(row: NoticeExternalApi.ManageItem): Array<{ key: string; label: string; danger?: boolean }> {
  const items: Array<{ key: string; label: string; danger?: boolean }> = [];
  if (row.auditStatus === 'UNREVIEWED') items.push({ key: 'reject', label: '驳回', danger: true });
  if (row.status === 'ACTIVE') items.push({ key: 'takeDown', label: '下线', danger: true });
  if (row.status !== 'ARCHIVED') {
    items.push({ key: 'aiExtract', label: 'AI 抽取' });
    items.push({ key: 'bodyStructure', label: '正文结构化' });
  }
  return items;
}

/** 更多菜单项点击：二次确认后执行 */
function onMoreAction(row: NoticeExternalApi.ManageItem, key: string): void {
  const title: Record<string, string> = {
    reject: '确认驳回该公告？（将不对外展示）',
    takeDown: '确认下线？（大厅展示随即消失）',
    aiExtract: '执行 AI 结构化抽取（含行业判定回写）？可能需要一些时间。',
    bodyStructure: '执行正文结构化（解析/兜底排版为段落表格）？',
  };
  const label: Record<string, string> = { reject: '驳回', takeDown: '下线', aiExtract: 'AI 抽取', bodyStructure: '正文结构化' };
  const runner: Record<string, () => Promise<unknown>> = {
    reject: () => rejectNotice(row.id!),
    takeDown: () => takeDownNotice(row.id!),
    aiExtract: () => aiExtractNotice(row.id!),
    bodyStructure: () => bodyStructureNotice(row.id!),
  };
  const fn = runner[key];
  if (!fn) return;
  Modal.confirm({ title: title[key] || label[key], okText: '确认', cancelText: '取消', onOk: () => runAction(label[key] ?? key, fn) });
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
        <div class="flex items-center">
          <TableAction :actions="rowActions(row)" />
          <template v-if="moreItems(row).length">
            <Dropdown trigger="click" :menu="{ items: moreItems(row), onClick: ({ key }) => onMoreAction(row, String(key)) }">
              <span class="cx-more-btn">更多 ▾</span>
            </Dropdown>
          </template>
        </div>
      </template>
    </Grid>
  </Page>
</template>

<style scoped>
.cx-more-btn {
  margin-left: 4px;
  padding: 1px 6px;
  font-size: 12px;
  line-height: 20px;
  color: #17539a;
  cursor: pointer;
  white-space: nowrap;
  user-select: none;
}
.cx-more-btn:hover {
  color: #1f6fd6;
}
</style>
