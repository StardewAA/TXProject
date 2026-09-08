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
