<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CrawlExternalApi } from '#/api/bidding/crawlExternal';

import { Page } from '@vben/common-ui';

import { ref } from 'vue';

import { message, Tag } from 'ant-design-vue';

import { TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { fetchCrawlTaskPage, retryCrawlTask } from '#/api/bidding/crawlExternal';

import { useGridColumns, useGridFormSchema } from './data';
import TaskDetail from './modules/task-detail.vue';
import { TASK_STATUS_MAP, tagOf } from '../shared';

function refresh() {
  gridApi.query();
}

const taskDetailRef = ref<{ openTask: (t: CrawlExternalApi.TaskItem) => void } | null>(null);

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

/** 行操作（克制）：全部行可「详情」（详情档分层铺开过程/同批/重试），FAILED 行另保留行内重试入口 */
function rowActs(row: CrawlExternalApi.TaskItem): any[] {
  const acts: Array<Record<string, unknown>> = [
    {
      label: '详情',
      type: 'link',
      auth: ['bidding:crawl-task:query'],
      onClick: () => taskDetailRef.value?.openTask(row),
    },
  ];
  if (row.status === 'FAILED') {
    acts.push({
      label: '重试',
      type: 'link',
      auth: ['bidding:crawl-task:update'],
      popConfirm: {
        title: '确认人工重试该死信任务？attempt 将归零开启新一轮。',
        confirm: () => void runAction('重试', () => retryCrawlTask(row.id!)), // FAILED 行必有主键
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
<!--
  采集任务管理（平台职能：任务行迹与死信重试）
  业务：查看一次采集的“源→任务(通道/解析器/尝试)→提取→结果→明细”任务条；FAILED 死信可人工重试（attempt 归零开启新一轮）。
  理由：平台需要任务留痕以排查采集异常；重试仅限 FAILED（死信），不重复触发未完成批次（契约语义）。
    业务边界：本页属平台职能（G 轨，无租户语义）——职能角色矩阵（后端执行清单 §7）：爬虫管理/外部公告审核=平台职能，
        勿授企业租户；企业租户只使用发布/档案/跟踪等 T 轨功能。
    依据（法规/政府文件）：10号令 第12条（部门规章）；电子办法 第9条（部门规章）——采集公开信息的可追溯性。
    后端契约：爬虫任务工作台-前后端契约（crawl-task page/get/retry）；retry 仅 FAILED → PENDING。
-->

  <Page auto-content-height class="bid-ind">
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
    <TaskDetail ref="taskDetailRef" />
  </Page>
</template>
