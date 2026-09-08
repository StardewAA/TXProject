<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CrawlExternalApi } from '#/api/bidding/crawlExternal';

import { Page } from '@vben/common-ui';

import { Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { fetchCrawlJobPage } from '#/api/bidding/crawlExternal';

import { useGridColumns, useGridFormSchema } from './data';
import { JOB_STATUS_MAP, TRIGGER_MAP, tagOf } from '../shared';

const [Grid] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
  },
  gridOptions: {
    columns: useGridColumns(),
    height: 'auto',
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          return await fetchCrawlJobPage({
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
  } as VxeTableGridOptions<CrawlExternalApi.JobItem>,
});
</script>

<template>
<!--
  采集批次管理（平台职能：一次完整爬取 = 一个批次）
  业务：查看采集批次（crawl-job）的归属源/触发方式/汇总/状态，作为“一次完整爬取”的行迹参照。
  理由：批次为平台审计维度：一次轮询或一次 run 产生一个批次，汇总 summaryJson 便于运营核对采集健康度。
    业务边界：本页属平台职能（G 轨，无租户语义）——职能角色矩阵（后端执行清单 §7）：爬虫管理/外部公告审核=平台职能，
        勿授企业租户；企业租户只使用发布/档案/跟踪等 T 轨功能。
    依据（法规/政府文件）：10号令 第12条（部门规章）；电子办法 第9条（部门规章）——采集留痕与可追溯。
    后端契约：爬虫任务工作台-前后端契约（crawl-job page/get，只读参照）。
-->

  <Page auto-content-height class="bid-ind">
    <Grid table-title="采集批次">
      <template #col_trigger="{ row }">
        <Tag v-if="tagOf(TRIGGER_MAP, row.triggerType)" :color="tagOf(TRIGGER_MAP, row.triggerType)!.color">
          {{ tagOf(TRIGGER_MAP, row.triggerType)!.text }}
        </Tag>
        <span v-else>—</span>
      </template>
      <template #col_status="{ row }">
        <Tag v-if="tagOf(JOB_STATUS_MAP, row.status)" :color="tagOf(JOB_STATUS_MAP, row.status)!.color">
          {{ tagOf(JOB_STATUS_MAP, row.status)!.text }}
        </Tag>
        <span v-else>—</span>
      </template>
    </Grid>
  </Page>
</template>
