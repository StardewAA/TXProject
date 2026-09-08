<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CrawlExternalApi } from '#/api/bidding/crawlExternal';

import { ref } from 'vue';

import { Page, useVbenModal } from '@vben/common-ui';

import { Modal, message, Switch } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteCrawlSource,
  fetchCrawlSourcePage,
  runCrawlSource,
  setCrawlSourceEnabled,
} from '#/api/bidding/crawlExternal';

import { useGridColumns, useGridFormSchema } from './data';
import FormModal from './modules/form.vue';

const [FormModalWrap, formModalApi] = useVbenModal({
  connectedComponent: FormModal,
  destroyOnClose: true,
});

function refresh() {
  gridApi.query();
}

const togglingId = ref<number | null>(null);

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

/** 启停开关：二次确认后调后端（取消则回读刷新还原开关态） */
function handleToggle(row: CrawlExternalApi.SourceItem, checked: boolean | string | number) {
  const enable = Boolean(checked);
  Modal.confirm({
    title: enable ? '启用该采集源？' : '停用该采集源？',
    content: enable
      ? '启用后将进入外部链接 /go 白名单，定时调度将按抓取间隔执行采集。'
      : '停用后不再参与调度；若有已生效(ACTIVE)公告请先下线再停用。',
    okText: '确认',
    cancelText: '取消',
    onOk: async () => {
      togglingId.value = row.id ?? null;
      try {
        await setCrawlSourceEnabled(row.id!, enable);
        message.success(enable ? '已启用' : '已停用');
        refresh();
      } catch (e) {
        message.error(e instanceof Error ? e.message : String(e));
        refresh();
      } finally {
        togglingId.value = null;
      }
    },
    onCancel: () => refresh(),
  });
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
        title: '确认删除采集源「' + (row.sourceName || row.sourceCode) + '」？须无进行中任务且无公告引用。',
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
  <Page auto-content-height class="bid-ind">
    <FormModalWrap @success="refresh" />
    <Grid table-title="采集源管理（外部公告爬虫）">
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
        <Switch
          :checked="!!row.enabled"
          :loading="togglingId === row.id"
          checked-children="启用"
          un-checked-children="停用"
          @change="(v: any) => handleToggle(row, v)"
        />
      </template>
      <template #operation="{ row }">
        <TableAction :actions="rowActs(row)" />
      </template>
    </Grid>
  </Page>
</template>
