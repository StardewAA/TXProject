<!--
  信息源详情档（两档视图之“舒张”层）：
  业务：从信息源列表点「详情」打开——源配置 / 获取批次 / 批内任务 / 任务过程 逐层铺开；
        启停开关与立即获取等操作收在本档（列表行只留克制摘要 + 详情入口）。
  实现理由：同源信息做两档视图——概览档克制（行=源 code/名称/状态/间隔/时间），详情档整页分层舒张，
        避免把开关与明细全塞进小行卡片；分层顺序=源配置→批次(job)→任务(crawl-task by jobId)→任务详情。
  后端契约：爬虫任务工作台-前后端契约 + 2026-09-08-crawl-contract-batch.md §三.1（批次下钻任务=携 jobId 调 crawl-task/page，无需新端点）；
        源 get/run/enabled 见 04-全链路 §5.2/5.3/5.5；job page 见 §5.10；task page/get 见 §5.7/5.8。
-->
<script lang="ts" setup>
import type { CrawlExternalApi } from '#/api/bidding/crawlExternal';

import { computed, h, ref } from 'vue';

import { Button, Descriptions, Drawer, message, Modal, Tag } from 'ant-design-vue';

import { fetchCrawlJobPage, fetchCrawlTaskPage, getCrawlSource, getCrawlTask, runCrawlSource, setCrawlSourceEnabled } from '#/api/bidding/crawlExternal';

import { fmtDt, JOB_STATUS_MAP, jobSummaryText, tagOf, TASK_STATUS_MAP } from '../../shared';

import '../../taskbar.css';

const open = ref(false);
const loading = ref(false);
const busy = ref(false);
const row = ref<CrawlExternalApi.SourceItem | null>(null);

/* ---------- 层2：该源获取批次 ---------- */
const jobs = ref<CrawlExternalApi.JobItem[]>([]);
const jobsLoading = ref(false);
const jobsError = ref('');
/* ---------- 层3：选中批次下的爬虫任务（jobId 过滤） ---------- */
const selJob = ref<CrawlExternalApi.JobItem | null>(null);
const tasks = ref<CrawlExternalApi.TaskItem[]>([]);
const tasksLoading = ref(false);
const tasksError = ref('');
/* ---------- 层4：选中任务的过程详情 ---------- */
const selTask = ref<CrawlExternalApi.TaskItem | null>(null);
const taskDetail = ref<CrawlExternalApi.TaskItem | null>(null);

const enabled = computed(() => !!row.value?.enabled);

async function openSource(src: CrawlExternalApi.SourceItem) {
  row.value = src;
  jobs.value = [];
  selJob.value = null;
  tasks.value = [];
  selTask.value = null;
  taskDetail.value = null;
  open.value = true;
  loading.value = true;
  try {
    const detail = (await getCrawlSource(src.id!)) as unknown as CrawlExternalApi.SourceItem; // 行必有主键
    if (detail && detail.sourceCode) row.value = detail;
  } catch {
    // 失败提示由 requestClient 全局拦截器统一弹出，页面层不重复 toast
  } finally {
    loading.value = false;
  }
  void loadJobs();
}

/* 批次记录：按源拉最近 20 批（克制行），点选后展开该批任务 */
async function loadJobs() {
  if (!row.value?.sourceCode) return;
  jobsLoading.value = true;
  jobsError.value = '';
  try {
    const res = (await fetchCrawlJobPage({
      sourceCode: row.value.sourceCode,
      pageNo: 1,
      pageSize: 20,
    })) as unknown as { list?: CrawlExternalApi.JobItem[] };
    jobs.value = res.list || [];
  } catch (e) {
    jobs.value = [];
    // 区分"真的没有"与"加载失败"：静默置空会把 403/网络错误显示成误导性空态
    jobsError.value = e instanceof Error ? e.message : String(e);
  } finally {
    jobsLoading.value = false;
  }
}

/* 选中批次 → 该批跑过的爬虫任务（携 jobId 调 crawl-task/page；crawl-batch §三.1） */
async function pickJob(j: CrawlExternalApi.JobItem) {
  selJob.value = j;
  selTask.value = null;
  taskDetail.value = null;
  tasksLoading.value = true;
  tasksError.value = '';
  tasks.value = [];
  try {
    const res = (await fetchCrawlTaskPage({
      jobId: j.id,
      pageNo: 1,
      pageSize: 200,
    })) as unknown as { list?: CrawlExternalApi.TaskItem[] };
    tasks.value = res.list || [];
  } catch (e) {
    tasks.value = [];
    tasksError.value = e instanceof Error ? e.message : String(e);
  } finally {
    tasksLoading.value = false;
  }
}

/* 选中任务 → 单任务过程详情（失败原因/尝试/worker） */
async function pickTask(t: CrawlExternalApi.TaskItem) {
  selTask.value = t;
  taskDetail.value = null;
  if (t.id == null) return;
  try {
    taskDetail.value = (await getCrawlTask(t.id)) as unknown as CrawlExternalApi.TaskItem;
  } catch {
    taskDetail.value = null;
  }
}

/* 启停：操作收进详情档（列表行不再放小开关），确认后调用后端并回读刷新 */
function toggleEnabled() {
  const s = row.value;
  if (!s || s.id == null) return;
  const enable = !enabled.value;
  Modal.confirm({
    title: enable ? '启用该信息源？' : '停用该信息源？',
    content: enable
      ? '启用后进入外链 /go 白名单，定时调度按抓取间隔执行采集。'
      : '停用后不再参与调度；若有已生效(ACTIVE)公告请先下线再停用。',
    okText: '确认',
    cancelText: '取消',
    onOk: async () => {
      busy.value = true;
      try {
        await setCrawlSourceEnabled(s.id as number, enable);
        message.success(enable ? '已启用' : '已停用');
        await openSource(s);
      } catch (e) {
        message.error(e instanceof Error ? e.message : String(e));
      } finally {
        busy.value = false;
      }
    },
  });
}

/* 立即获取一轮：同步执行，汇总弹窗；受 120s 频控约束；running 锁防双击重入 */
const running = ref(false);
async function handleRun() {
  const s = row.value;
  if (!s?.sourceCode || running.value) return;
  running.value = true;
  const hide = message.loading({ content: '正在执行采集：' + (s.sourceName || s.sourceCode) + '…', duration: 0 });
  try {
    const res = (await runCrawlSource(s.sourceCode)) as unknown as {
      categories?: Array<{ error?: string; failed?: number; fetched?: number; ingested?: number; name?: string; }>;
    };
    const nodes = ((res.categories || []) as Array<{ error?: string; failed?: number; fetched?: number; ingested?: number; name?: string; }>).map(
      (c) => '• ' + (c.name || '') + '：抓取 ' + (c.fetched ?? 0) + '，入库 ' + (c.ingested ?? 0) + '，失败 ' + (c.failed ?? 0) + (c.error ? '（' + c.error + '）' : ''),
    );
    // antd Modal content 为纯文本渲染，'<br/>'/'\n' 均不换行：改 VNode 逐行
    Modal.info({ title: '本轮采集完成', content: nodes.length ? h('div', nodes.map((l) => h('div', l))) : '无栏目产出', width: 520 });
    void loadJobs();
  } catch {
    // 失败提示由 requestClient 全局拦截器统一弹出，页面层不重复 toast
  } finally {
    hide();
    running.value = false;
  }
}

/* 入口 URL JSON → 展示列表（栏目对象可带 lookbackDays/fullLookbackDays/maxPages） */
function fmtListUrl(raw?: string): string[] {
  if (!raw) return [];
  try {
    const v = JSON.parse(raw);
    const arr = Array.isArray(v) ? v : [];
    return arr.map((x: unknown) =>
      typeof x === 'string' ? x : typeof x === 'object' && x && (x as { url?: unknown }).url ? String((x as { url: unknown }).url) : String(x),
    );
  } catch {
    return [];
  }
}

defineExpose({ openSource });
</script>
<template>
  <Drawer v-model:open="open" class="bid-ind" :width="960" :title="row ? `信息源详情 · ${ row.sourceName || row.sourceCode}` : '信息源详情'">
    <div v-if="loading" class="sd-hint">加载中…</div>
    <template v-else-if="row">
      <!-- 层1：源概要 + 操作（启停/立即获取收在本档，不进列表行） -->
      <div class="sd-head">
        <div class="sd-head-l">
          <div class="sd-name">{{ row.sourceName || row.sourceCode }}</div>
          <div class="sd-code">sourceCode：{{ row.sourceCode }} · {{ row.channelCode || '—' }}</div>
        </div>
        <Tag v-if="enabled" color="green">已启用</Tag>
        <Tag v-else color="default">停用</Tag>
      </div>
      <div class="sd-ops">
        <Button :type="enabled ? 'default' : 'primary'" :loading="busy" @click="toggleEnabled">{{ enabled ? '停用该源' : '启用该源' }}</Button>
        <Button type="primary" :loading="running" @click="handleRun">立即获取一轮</Button>
      </div>

      <!-- 层2：配置明细 -->
      <div class="sd-sec">
        <div class="sd-sec-t">配置</div>
        <Descriptions :column="2" size="small" bordered>
          <Descriptions.Item label="入口站点">{{ row.baseUrl || '—' }}</Descriptions.Item>
          <Descriptions.Item label="获取方式">{{ row.fetchType === 'API' ? 'API · 接口直取(JSON)' : 'CRAWLER · 爬虫(列表HTML)' }}</Descriptions.Item>
          <Descriptions.Item label="抓取间隔">{{ row.fetchIntervalSeconds != null ? `${row.fetchIntervalSeconds } 秒` : '—' }}</Descriptions.Item>
          <Descriptions.Item label="默认解析/策略码">{{ row.parserCodeDefault || '（按 URL 规则自动匹配）' }}</Descriptions.Item>
          <Descriptions.Item label="robots">{{ row.robotsOk ? '允许按 robots 抓取' : '禁止' }}</Descriptions.Item>
          <Descriptions.Item label="额外放行域名" :span="2">{{ row.allowedHosts || '（仅 baseUrl 域）' }}</Descriptions.Item>
          <Descriptions.Item label="备注" :span="2">{{ row.remark || '—' }}</Descriptions.Item>
        </Descriptions>
        <div v-if="row.fetchType === 'API'" class="sd-sec-t" style="margin-top: 14px">API 接口配置（api_config_json）</div>
        <pre v-if="row.fetchType === 'API' && row.apiConfigJson" class="sd-json">{{ row.apiConfigJson }}</pre>
        <div v-else-if="row.fetchType === 'API'" class="sd-hint">（API 源未配置 api_config_json）</div>
        <div class="sd-sec-t" style="margin-top: 14px">入口 / 栏目 URL</div>
        <div v-if="fmtListUrl(row.entryUrlsJson).length" class="sd-urls">
          <div v-for="(u, i) in fmtListUrl(row.entryUrlsJson)" :key="i" class="sd-url">{{ u }}</div>
        </div>
        <div v-else class="sd-hint">（留空=从首页链接发现）</div>
      </div>

      <!-- 层3：获取批次（统一任务条 fp-tbl：状态/时间/批次#/触发/摘要；行点击下钻该批任务） -->
      <div class="sd-sec">
        <div class="sd-sec-t">获取批次 <span class="sd-muted">（最近 {{ jobs.length }} 批；一次采集 = 一个批次）</span></div>
        <div v-if="jobsLoading" class="sd-hint">批次加载中…</div>
        <div v-else-if="jobsError" class="sd-hint">批次加载失败：{{ jobsError }}（可关闭后重开重试）</div>
        <div v-else-if="!jobs.length" class="sd-hint">暂无批次记录 —— 点上方「立即获取一轮」或等待定时调度</div>
        <div v-else class="fp-tbl">
          <div class="fp-tr fp-th">
            <span class="fp-td w-status">状态</span>
            <span class="fp-td w-time">时间</span>
            <span class="fp-td w-id">批次 #</span>
            <span class="fp-td flex-1">源 / 触发</span>
            <span class="fp-td w-msg">摘要</span>
          </div>
          <div v-for="j in jobs" :key="String(j.id)" class="fp-tr fp-row-click" :class="{ 'fp-row-on': selJob && selJob.id === j.id }" @click="pickJob(j)">
            <span class="fp-td w-status">
              <Tag :color="tagOf(JOB_STATUS_MAP, j.status)?.color || 'default'">{{ tagOf(JOB_STATUS_MAP, j.status)?.text || j.status || '—' }}</Tag>
            </span>
            <span class="fp-td w-time">{{ fmtDt(j.finishedAt || j.createTime) || '—' }}</span>
            <span class="fp-td w-id">#{{ String(j.id) }}</span>
            <span class="fp-td flex-1 fp-url">{{ j.sourceCode || row.sourceCode || '—' }}<template v-if="j.triggerType"> · {{ j.triggerType }}</template></span>
            <span class="fp-td w-msg fp-msg" :title="jobSummaryText(j.summaryJson)">{{ jobSummaryText(j.summaryJson) || '—' }}</span>
          </div>
        </div>
      </div>

      <!-- 层4：选中批次 → 该批爬虫任务 N 条（统一任务条 fp-tbl：状态/时间/任务#/URL/消息，与工作台同款） -->
      <div v-if="selJob" class="sd-sec">
        <div class="sd-sec-t">该批爬虫任务 <span class="sd-muted">（jobId={{ selJob.id }} · {{ tasks.length }} 条 · 点击行查看过程）</span></div>
        <div v-if="tasksLoading" class="sd-hint">任务加载中…</div>
        <div v-else-if="tasksError" class="sd-hint">任务加载失败：{{ tasksError }}（可重选批次重试）</div>
        <div v-else-if="!tasks.length" class="sd-hint">该批暂无任务行（批次落库见 crawl-batch 契约）</div>
        <div v-else class="fp-tbl">
          <div class="fp-tr fp-th">
            <span class="fp-td w-status">状态</span>
            <span class="fp-td w-time">时间</span>
            <span class="fp-td w-id">任务 #</span>
            <span class="fp-td flex-1">URL / 来源</span>
            <span class="fp-td w-msg">消息 / 明细</span>
          </div>
          <div v-for="t in tasks" :key="String(t.id)" class="fp-tr fp-row-click" :class="{ 'fp-row-on': selTask && selTask.id === t.id }" @click="pickTask(t)">
            <span class="fp-td w-status">
              <Tag :color="tagOf(TASK_STATUS_MAP, t.status)?.color || 'default'">{{ tagOf(TASK_STATUS_MAP, t.status)?.text || t.status || '—' }}</Tag>
            </span>
            <span class="fp-td w-time">{{ fmtDt(t.finishedAt || t.createTime) || '—' }}</span>
            <span class="fp-td w-id">#{{ String(t.id) }}</span>
            <span class="fp-td flex-1 fp-url" :title="t.url || ''">{{ t.url || '（无 URL）' }}</span>
            <span class="fp-td w-msg fp-msg" :title="t.message || ''">
              <template v-if="t.message">{{ t.message }}</template>
              <template v-else>解析器 {{ t.parserCode || '—' }} · 尝试 {{ t.attempt ?? '—' }}</template>
            </span>
          </div>
        </div>
      </div>

      <!-- 层5：选中任务 → 过程详情（失败原因/尝试/worker/时间线） -->
      <div v-if="selTask" class="sd-sec">
        <div class="sd-sec-t">任务过程 <span class="sd-muted">（任务 #{{ String(selTask.id) }}）</span></div>
        <Descriptions :column="1" size="small" bordered>
          <Descriptions.Item label="URL">{{ taskDetail?.url || selTask.url || '—' }}</Descriptions.Item>
          <Descriptions.Item label="状态">{{ tagOf(TASK_STATUS_MAP, taskDetail?.status || selTask.status)?.text || selTask.status || '—' }}</Descriptions.Item>
          <Descriptions.Item label="源 / 通道">{{ taskDetail?.sourceCode || selTask.sourceCode || '—' }} / {{ taskDetail?.channelCode || selTask.channelCode || '—' }}</Descriptions.Item>
          <Descriptions.Item label="尝试次数">{{ taskDetail?.attempt ?? selTask.attempt ?? '—' }}</Descriptions.Item>
          <Descriptions.Item label="worker">{{ taskDetail?.workerId || selTask.workerId || '—' }}</Descriptions.Item>
          <Descriptions.Item label="开始 / 完成">{{ fmtDt(taskDetail?.startedAt || selTask.startedAt) }} / {{ fmtDt(taskDetail?.finishedAt || selTask.finishedAt) }}</Descriptions.Item>
          <Descriptions.Item label="消息 / 错误">{{ taskDetail?.message || selTask.message || '—' }}</Descriptions.Item>
        </Descriptions>
      </div>
    </template>
    <div v-else class="sd-hint">未选择信息源</div>
  </Drawer>
</template>

<style scoped>
.sd-head { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.sd-head-l { flex: 1; min-width: 0; }
.sd-name { font-size: 17px; font-weight: 800; color: var(--ind-ink-text, #1f2937); }
.sd-code { font-size: 12.5px; color: var(--ind-weak-text, #6b7280); margin-top: 2px; }
.sd-ops { display: flex; gap: 8px; margin-bottom: 6px; }
.sd-sec { margin-top: 18px; }
.sd-sec-t { font-size: 13.5px; font-weight: 800; color: var(--ind-ink-text, #1f2937); margin-bottom: 8px; display: flex; align-items: center; gap: 8px; }
.sd-muted { font-weight: 500; font-size: 12px; color: var(--ind-weak-text, #6b7280); }
.sd-hint { color: var(--ind-weak-text, #6b7280); font-size: 13px; padding: 12px 4px; }
.sd-urls { display: flex; flex-direction: column; gap: 4px; }
.sd-url { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12.5px; background: hsl(var(--muted) / 50%); border: 1px solid hsl(var(--border)); padding: 4px 8px; border-radius: 4px; }
/* 批次行与批内任务行均已改用统一任务条样式（../../taskbar.css 的 .fp-tbl） */
.sd-json { background: hsl(var(--muted)); color: hsl(var(--foreground)); font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12px; line-height: 1.6; padding: 12px; border-radius: var(--radius); max-height: 260px; overflow: auto; white-space: pre-wrap; word-break: break-all; margin: 8px 0 0; }
</style>
