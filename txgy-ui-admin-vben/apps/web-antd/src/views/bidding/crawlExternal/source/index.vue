<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CrawlExternalApi } from '#/api/bidding/crawlExternal';

import { ref } from 'vue';

import { Page, useVbenModal } from '@vben/common-ui';

import { Modal, message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteCrawlSource,
  fetchCrawlSourcePage,
  runCrawlSource,
} from '#/api/bidding/crawlExternal';

import { useGridColumns, useGridFormSchema } from './data';
import FormModal from './modules/form.vue';
import SourceDetail from './modules/source-detail.vue';

const [FormModalWrap, formModalApi] = useVbenModal({
  connectedComponent: FormModal,
  destroyOnClose: true,
});

function refresh() {
  gridApi.query();
}

const detailRef = ref<{ openSource: (s: CrawlExternalApi.SourceItem) => void } | null>(null);

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

/** 新增 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑 */
function handleEdit(row: CrawlExternalApi.SourceItem) {
  formModalApi.setData(row).open();
}


/** 立即采集一轮（同步下载并落库，结果弹窗汇总） */
function runSummaryText(res?: CrawlExternalApi.RunResult): string {
  if (!res) return '';
  const lines: string[] = ['成功落库 ' + (res.totalIngested ?? 0) + ' 条，失败 ' + (res.totalFailed ?? 0) + ' 条'];
  (res.categories || []).forEach((c) => {
    let l = c.name + '：抓到 ' + (c.fetched ?? 0) + '，新增 ' + (c.ingested ?? 0);
    if (c.failed) l += '，失败 ' + c.failed;
    if (c.error) l += '（' + c.error + '）';
    lines.push(l);
  });
  return lines.join('\n');
}
async function handleRun(row: CrawlExternalApi.SourceItem) {
  const hideLoading = message.loading({ content: '正在执行采集，同步下载中…', duration: 0 });
  try {
    const res = await runCrawlSource(row.sourceCode!);
    Modal.info({ title: '采集执行完成', content: runSummaryText(res), width: 520 });
    refresh();
  } catch (e) {
    message.error(e instanceof Error ? e.message : String(e));
  } finally {
    hideLoading();
  }
}

/** 行操作 */
function rowActs(row: CrawlExternalApi.SourceItem): any[] {
  return [
    {
      label: '详情',
      type: 'link',
      auth: ['bidding:crawl-source:query'],
      onClick: () => detailRef.value?.openSource(row),
    },
    {
      label: '编辑',
      type: 'link',
      auth: ['bidding:crawl-source:update'],
      onClick: () => handleEdit(row),
    },
    {
      label: '立即采集',
      type: 'link',
      auth: ['bidding:crawl-source:update'],
      popConfirm: {
        title: '立即执行一轮采集「' + (row.sourceName || row.sourceCode) + '」？将同步下载并落库。',
        confirm: () => void handleRun(row),
      },
    },
    {
      label: '删除',
      type: 'link',
      danger: true,
      auth: ['bidding:crawl-source:delete'],
      popConfirm: {
        title: '确认删除信息源「' + (row.sourceName || row.sourceCode) + '」？须无进行中任务且无公告引用。',
        confirm: () => void runAction('删除', () => deleteCrawlSource(row.id!)),
      },
    },
  ];
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
          return await fetchCrawlSourcePage({
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
  } as VxeTableGridOptions<CrawlExternalApi.SourceItem>,
});
</script>

<template>
<!--
  信息源管理（平台职能：源配置 → 定时轮询/立即获取）
  业务：平台运营维护外部公告信息源（sourceCode 唯一；channel=GENERIC；parserCode 默认解析器；entryUrls 入口 URL 列表；轮询间隔≥120s）；启用后进入外链 /go 白名单。
  理由：信息源=平台级资产（G 轨）；白名单域名防开放重定向；轮询间隔下限防对源站压力（安全评审+10号令免费查阅合规）。
    业务边界：本页属平台职能（G 轨，无租户语义）——职能角色矩阵（后端执行清单 §7）：爬虫管理/外部公告审核=平台职能，
        勿授企业租户；企业租户只使用发布/档案/跟踪等 T 轨功能。
    依据（法规/政府文件）：10号令 第12条（部门规章）免费查阅——采集公开公告合法性基础；电子办法 第9条（部门规章）。
    后端契约：爬虫任务工作台-前后端契约（source create/update/enabled/delete/page）；错误码 106_014 跳转拒绝/106_015 未启用检索/106_046 间隔<120。
-->

  <Page auto-content-height class="bid-ind">
    <FormModalWrap @success="refresh" />
    <SourceDetail ref="detailRef" />
    <Grid table-title="信息源管理（外部公告）">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '新增采集源',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['bidding:crawl-source:create'],
              onClick: handleCreate,
            },
          ]"
        />
      </template>
      <template #col_enabled="{ row }">
        <Tag :color="row.enabled ? 'green' : 'default'">{{ row.enabled ? '已启用' : '停用' }}</Tag>
      </template>
      <template #operation="{ row }">
        <TableAction :actions="rowActs(row)" />
      </template>
    </Grid>
  </Page>
</template>
