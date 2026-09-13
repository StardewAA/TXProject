<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CrawlExternalApi } from '#/api/bidding/crawlExternal';

import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Tag } from 'ant-design-vue';

import { TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { fetchCrawlJobPage, fetchCrawlSourcePage } from '#/api/bidding/crawlExternal';

import { JOB_STATUS_MAP, tagOf, TRIGGER_MAP } from '../shared';
import { useGridColumns, useGridFormSchema } from './data';
import JobDetail from './modules/job-detail.vue';

/** 行操作（克制）：批次只读，统一「详情」入口进入详情档分层查看（概要 → 该批 N 条任务 → 任务过程） */
function rowActs(row: CrawlExternalApi.JobItem): any[] {
  return [
    {
      label: '详情',
      type: 'link',
      auth: ['bidding:crawl-job:query'],
      onClick: () => jobDetailRef.value?.openJob(row),
    },
  ];
}

const jobDetailRef = ref<null | { openJob: (j: CrawlExternalApi.JobItem) => void }>(null);

/* 信息源名称映射：后端批次出参只有 sourceCode（CrawlJobRespVO），故用源列表在前端做 code → 名称（契约 16 已请求后端补 sourceName）。
 * 拿不到源列表时**不阻塞页面**：筛选退化为空选项，列照旧显示 code。 */
const sourceNameMap = ref<Record<string, string>>({});
const sourceOptions = ref<Array<{ label: string; value: string }>>([]);
const sourceLabel = (code?: null | string): string => {
  if (!code) return '全源';
  const name = sourceNameMap.value[code];
  return name ? `${name}（${code}）` : code;
};

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema([]),
  },
  gridOptions: {
    columns: useGridColumns(sourceLabel),
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
onMounted(async () => {
  try {
    const res = await fetchCrawlSourcePage({ pageNo: 1, pageSize: 100 });
    const list = res.list ?? [];
    const map: Record<string, string> = {};
    const opts: Array<{ label: string; value: string }> = [];
    for (const s of list) {
      if (!s.sourceCode) continue;
      if (s.sourceName) map[s.sourceCode] = s.sourceName;
      opts.push({ label: `${s.sourceName || s.sourceCode}（${s.sourceCode}）`, value: s.sourceCode });
    }
    sourceNameMap.value = map;
    sourceOptions.value = opts;
    // 异步选项：schema 在 setup 期构建会拿到空下拉，故加载完再注入
    gridApi.formApi.updateSchema(useGridFormSchema(opts));
  } catch {
    /* 源列表加载失败不阻塞批次页：列回落显示 code，筛选项为空 */
  }
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
      <template #operation="{ row }">
        <TableAction :actions="rowActs(row)" />
      </template>
    </Grid>
    <JobDetail ref="jobDetailRef" :source-label="sourceLabel" />
  </Page>
</template>
