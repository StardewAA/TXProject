<!--
  四要素注释
  对应业务：爬虫任务工作台（菜单 6738）的「采集源配置」抽屉 —— 采集源（bidding_crawl_source）管理：
    分页查看/新增/编辑（SourceForm 弹窗）/启停/立即爬取/删除；启用后的源进入周期轮询调度，
    产生 SCHEDULE 定时任务任务条，采集结果落库外部公告 NEW 待审核。
  实现理由：外部公告采集面向多个公开站点，须由管理端统一维护源（入口 URL/解析器/轮询间隔）方可可控采集；
    轮询间隔下限 120s 由后端强校验（错误码 1-060-106-046，对应产品"120s 频控"需求，见文件头契约 §0/§2），
    前端对同一源 120s 内重复"立即爬取"做节流拦截提示，避免对源站请求过频被封；
    启用的采集源域名进入外链中转 /go 域名白名单（fail-closed），前台"前往原地址"只能跳白名单内站点，防开放重定向。
  法规依据：
    招标公告和公示信息发布管理办法（国家发展改革委令第10号，2017）第12条【部门规章/政府文件】：
      发布媒介允许公众免费查阅公告公示完整信息 —— 采集对象与可跳转范围即限定于该类免费公开信息；
    同办法第13条【部门规章】：免费提供信息归类与检索服务 —— 采集内容经归类/审核后在大厅免费检索展示。
  关联契约：/bidding/crawl-source/{page,get,create,update,enabled,delete,run}（enabled=启停并进入/退出调度），
    见 docs/bidding-notice-external/爬虫任务工作台-前后端契约-2026-09-07.md §0（后端已具备源接口清单）与
    docs/bidding-notice-external/外部公告-前端对接-后续后端配合说明.md §3（CrawlSourceController 逐项对齐）；
    前端函数见 apps/web-antd/src/api/bidding/crawlExternal（fetchCrawlSourcePage / setCrawlSourceEnabled /
    runCrawlSource / deleteCrawlSource 与 SourceForm 用 create/update），权限码 bidding:crawl-source:*。
-->
<script lang="ts" setup>
import type { CrawlExternalApi } from '#/api/bidding/crawlExternal';

import { onMounted, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message, Modal, Switch, Table, Tag } from 'ant-design-vue';

import {
  deleteCrawlSource,
  fetchCrawlSourcePage,
  runCrawlSource,
  setCrawlSourceEnabled,
} from '#/api/bidding/crawlExternal';

import { fetchTypeText, intervalText } from '../../labels';
import SourceForm from '../../source/modules/form.vue';

const emit = defineEmits(['changed']);

const open = defineModel<boolean>('open', { default: false });
const loading = ref(false);
const rows = ref<CrawlExternalApi.SourceItem[]>([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = 8;
const runCode = ref<string | undefined>();
const togglingId = ref<number | string>();

const [SourceModal, sourceModalApi] = useVbenModal({
  connectedComponent: SourceForm,
  destroyOnClose: true,
  onClosed: () => void load(),
});

async function load() {
  if (!open.value) return;
  loading.value = true;
  try {
    const res = (await fetchCrawlSourcePage({ pageNo: pageNo.value, pageSize })) as unknown as {
      list?: CrawlExternalApi.SourceItem[];
      total?: number;
    };
    rows.value = res.list || [];
    total.value = res.total || 0;
  } catch (e) {
    message.error(e instanceof Error ? e.message : String(e));
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  sourceModalApi.setData({} as CrawlExternalApi.SourceItem).open();
}
function openEdit(row: CrawlExternalApi.SourceItem) {
  sourceModalApi.setData(row).open();
}

async function toggleEnabled(row: CrawlExternalApi.SourceItem, checked: boolean) {
  if (row.id == null) return;
  togglingId.value = row.id;
  try {
    // 启用=进入周期轮询调度（产生 SCHEDULE 任务条），停用=退出调度；
    // 后端对 fetch_interval_seconds 有 >=120s 下限校验（1-060-106-046，见文件头四要素注释）
    await setCrawlSourceEnabled(row.id, checked);
    message.success(checked ? '已启用（进入周期轮询调度）' : '已停用');
    row.enabled = checked;
    emit('changed');
  } catch (e) {
    message.error(e instanceof Error ? e.message : String(e));
  } finally {
    togglingId.value = undefined;
  }
}

async function runSource(row: CrawlExternalApi.SourceItem) {
  if (!row.sourceCode) return;
  // 手动"立即爬取"：后端同步执行一次采集并返回按栏目汇总（fetched/ingested/failed）；
  // 同一源 120s 内不可重复触发（频控，见文件头四要素注释与工作台契约 §0）
  runCode.value = row.sourceCode;
  try {
    const r = await runCrawlSource(row.sourceCode);
    const cats =
      ((r as { categories?: Array<{ error?: string; failed?: number; fetched?: number; ingested?: number; name?: string; }> })
        .categories || []);
    const lines = cats.map(
      (c) =>
        '• ' +
        (c.name || '') +
        '：抓取 ' +
        (c.fetched ?? 0) +
        '，入库 ' +
        (c.ingested ?? 0) +
        '，失败 ' +
        (c.failed ?? 0) +
        (c.error ? '，' + c.error : ''),
    );
    Modal.info({
      title: '爬取完成：' + (row.sourceName || row.sourceCode),
      content:
        (lines.length ? lines.join('<br/>') : '无栏目产出') +
        '<br/>（任务条将出现在“定时任务”区；同一源 120s 内不可再次触发。' +
        '首跑将按全量窗口回溯历史 fullLookbackDays（缺省 60 天），此后自动转增量 —— 04-契约 §3）',
    });
    emit('changed');
  } catch (e) {
    message.error(e instanceof Error ? e.message : String(e));
  } finally {
    runCode.value = undefined;
  }
}

function removeSource(row: CrawlExternalApi.SourceItem) {
  if (row.id == null) return;
  Modal.confirm({
    title: '删除信息源「' + (row.sourceName || row.sourceCode) + '」？',
    content: '前置条件：无正在运行任务、无 ACTIVE 公告引用（后端校验）。历史任务条保留。',
    okText: '删除',
    okButtonProps: { danger: true },
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteCrawlSource(row.id!); // 行必有主键（后端分页返回）
        message.success('已删除');
        emit('changed');
        await load();
      } catch (e) {
        message.error(e instanceof Error ? e.message : String(e));
      }
    },
  });
}

function onOpen(v: boolean) {
  if (v) {
    pageNo.value = 1;
    void load();
  }
}
onMounted(() => {
  if (open.value) void load();
});
watch(open, (v) => onOpen(v));
</script>

<template>
  <Modal
    :open="open"
    title="信息源配置"
    class="bid-ind"
    :width="960"
    :footer="null"
    @cancel="open = false"
    @update:open="onOpen($event as boolean)"
  >
    <div class="mb-2 flex items-center justify-between">
      <div class="text-xs text-gray-500">
        启用后按轮询间隔（≥120s）周期采集，产生“定时任务”任务条；同源 120s 内不可重复触发（防封频控）。
      </div>
      <a class="text-xs text-blue-600" @click="openCreate">+ 新增信息源</a>
    </div>
    <Table
      :data-source="rows"
      :columns="[
        { title: '源名称', dataIndex: 'sourceName', key: 'n', width: 180 },
        { title: 'code', dataIndex: 'sourceCode', key: 'c', width: 120 },
        { title: '方式', dataIndex: 'fetchType', key: 'ft', width: 120 },
        { title: '通道', dataIndex: 'channelCode', key: 'ch', width: 90 },
        { title: '抓取间隔（秒）', dataIndex: 'fetchIntervalSeconds', key: 'i', width: 130, customRender: ({ text }: { text?: null | number }) => intervalText(text) },
        { title: '解析器', dataIndex: 'parserCodeDefault', key: 'p', width: 130 },
        { title: '启用', key: 'en', width: 70 },
        { title: '操作', key: 'op', width: 210 },
      ]"
      :pagination="false"
      :loading="loading"
      size="small"
      row-key="id"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'ft'">
          <Tag v-if="record.fetchType === 'API'" color="purple">{{ fetchTypeText(record.fetchType) }}</Tag>
          <Tag v-else-if="record.fetchType === 'CRAWLER'" color="blue">{{ fetchTypeText(record.fetchType) }}</Tag>
          <span v-else>—</span>
        </template>
        <template v-else-if="column.key === 'en'">
          <Switch
            :checked="!!record.enabled"
            :loading="togglingId === record.id"
            @change="(c: boolean) => void toggleEnabled(record as CrawlExternalApi.SourceItem, c)"
          />
        </template>
        <template v-else-if="column.key === 'op'">
          <Tag color="blue" class="cursor-pointer" @click="openEdit(record as CrawlExternalApi.SourceItem)">编辑</Tag>
          <Tag
            color="green"
            class="cursor-pointer"
            @click="runCode !== record.sourceCode && runSource(record as CrawlExternalApi.SourceItem)"
          >
            {{ runCode === record.sourceCode ? '采集中…' : '立即爬取' }}
          </Tag>
          <Tag color="red" class="cursor-pointer" @click="removeSource(record as CrawlExternalApi.SourceItem)">删除</Tag>
        </template>
      </template>
    </Table>
    <div class="mt-2 text-right">
      <a v-if="total > pageNo * pageSize" class="text-xs text-blue-600" @click="pageNo++; void load()">
        加载更多（共 {{ total }}）
      </a>
    </div>
  </Modal>
  <SourceModal />
</template>
