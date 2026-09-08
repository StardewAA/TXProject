<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CrawlExternalApi } from '#/api/bidding/crawlExternal';

import { Page } from '@vben/common-ui';

import { message, Tag } from 'ant-design-vue';

import { TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { fetchCrawlTaskPage, retryCrawlTask } from '#/api/bidding/crawlExternal';

import { useGridColumns, useGridFormSchema } from './data';
import { TASK_STATUS_MAP, tagOf } from '../shared';

function refresh() {
  gridApi.query();
}

async function runAction(label: string, fn: () => Promise<unknown>) {
  const hideLoading = message.loading({ content: label + '处理中…', duration: 0 });
  try {
    await fn();
    message.success(label + '成功');
    refresh();
  } catch (e) {
    message.error(e instanceof Error ? e.message : String(e));
  } finally {
    hideLoading();
  }
}

/** 行操作：仅 FAILED 死信任务可重试 */
function rowActs(row: CrawlExternalApi.TaskItem): any[] {
  const acts: Array<Record<string, unknown>> = [];
  if (row.status === 'FAILED') {
    acts.push({
      label: '重试',
      type: 'link',
      auth: ['bidding:crawl-task:update'],
      popConfirm: {
        title: '确认人工重试该死信任务？attempt 将归零开启新一轮。',
        confirm: () => void runAction('重试', () => retryCrawlTask(Number(row.id))),
      },
    });
  }
  return acts;
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
          return await fetchCrawlTaskPage({
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
  } as VxeTableGridOptions<CrawlExternalApi.TaskItem>,
});
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="采集任务（死信可重试）">
      <template #col_status="{ row }">
        <Tag v-if="tagOf(TASK_STATUS_MAP, row.status)" :color="tagOf(TASK_STATUS_MAP, row.status)!.color">
          {{ tagOf(TASK_STATUS_MAP, row.status)!.text }}
        </Tag>
        <span v-else>—</span>
      </template>
      <template #operation="{ row }">
        <TableAction :actions="rowActs(row)" />
      </template>
    </Grid>
  </Page>
</template>
