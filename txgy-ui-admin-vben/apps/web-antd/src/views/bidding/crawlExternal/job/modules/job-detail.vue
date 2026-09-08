<!--
  采集批次详情档（两档视图之“舒张”层，独立 Grid 页配套）：
  业务：采集批次列表只留克制检索行；点「详情」进入本档——批次概要 → 该批爬虫任务 N 条(KPI+克制行) →
        选中任务过程区域（getCrawlTask Descriptions 就地展开，不嵌套 Drawer）。
  理由：后端异步队列化(2026-09-10 设计)后 RUNNING 批次=消费中，task 全程可见，批次下钻是该批完整行迹入口；
        与 console/source 同口径“克制行 + 整页分层”。
  后端契约：crawl-job page/get=04-全链路 §5.10/5.11；批次下钻任务=携 jobId 调 crawl-task/page 见 crawl-batch §三.1；
        crawl-task get=§5.8；job RUNNING 期间可看消费进度(中间 summaryJson)=2026-09-10-crawl-async-queue-design §九。
-->
<script lang="ts" setup>
import type { CrawlExternalApi } from '#/api/bidding/crawlExternal';

import { ref } from 'vue';

import { Descriptions, Drawer, message, Tag } from 'ant-design-vue';

import { fetchCrawlTaskPage, getCrawlJob, getCrawlTask } from '#/api/bidding/crawlExternal';

import { JOB_STATUS_MAP, TASK_STATUS_MAP, TRIGGER_MAP, fmtDt, jobSummaryText, tagOf } from '../../shared';
import TaskChainView from '../../console/modules/task-chain.vue';

const open = ref(false);
const loading = ref(false);
const row = ref<CrawlExternalApi.JobItem | null>(null);
const full = ref<CrawlExternalApi.JobItem | null>(null);
/** 该批爬虫任务（jobId 过滤） */
const tasks = ref<CrawlExternalApi.TaskItem[]>([]);
const tasksLoading = ref(false);
const selTask = ref<CrawlExternalApi.TaskItem | null>(null);
const selTaskFull = ref<CrawlExternalApi.TaskItem | null>(null);
const selTaskLoading = ref(false);

async function openJob(j: CrawlExternalApi.JobItem) {
  row.value = j;
  full.value = null;
  tasks.value = [];
  selTask.value = null;
  selTaskFull.value = null;
  open.value = true;
  loading.value = true;
  try {
    if (j.id != null) {
      full.value = (await getCrawlJob(Number(j.id))) as unknown as CrawlExternalApi.JobItem;
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : String(e));
  } finally {
    loading.value = false;
  }
  if (j.id != null) void loadTasks(Number(j.id));
}

/** 该批任务：携 jobId 调 crawl-task/page（crawl-batch §三.1），克制行展示 */
async function loadTasks(jobId: number) {
  tasksLoading.value = true;
  tasks.value = [];
  try {
    const res = (await fetchCrawlTaskPage({
      jobId,
      pageNo: 1,
      pageSize: 200,
    })) as unknown as { list?: CrawlExternalApi.TaskItem[] };
    tasks.value = res.list || [];
  } catch {
    tasks.value = [];
  } finally {
    tasksLoading.value = false;
  }
}

/** 选中任务 → 就地展开该任务过程（回读 get，同批内点击切换；再次点击同一条收起） */
async function pickTask(t: CrawlExternalApi.TaskItem) {
  if (selTask.value && selTask.value.id === t.id) {
    selTask.value = null;
    selTaskFull.value = null;
    return;
  }
  selTask.value = t;
  selTaskFull.value = null;
  if (t.id == null) return;
  selTaskLoading.value = true;
  try {
    selTaskFull.value = (await getCrawlTask(Number(t.id))) as unknown as CrawlExternalApi.TaskItem;
  } catch (e) {
    message.error(e instanceof Error ? e.message : String(e));
  } finally {
    selTaskLoading.value = false;
  }
}

/* 汇总明细（summaryJson 全量分行展示） */
function summaryLines(j?: CrawlExternalApi.JobItem | null): string[] {
  const s = (full.value || j)?.summaryJson;
  if (!s) return [];
  try {
    const v = JSON.parse(s) as Record<string, unknown>;
    const out: string[] = [];
    for (const k of ['totalFetched', 'totalIngested', 'totalFailed', 'categories']) {
      if (v[k] == null) continue;
      out.push(k + ': ' + (typeof v[k] === 'string' ? v[k] : JSON.stringify(v[k])));
    }
    return out;
  } catch {
    return [String(s).slice(0, 500)];
  }
}

const show = (): CrawlExternalApi.JobItem => full.value || row.value || ({} as CrawlExternalApi.JobItem);

defineExpose({ openJob });
</script>

<template>
  <Drawer v-model:open="open" :width="920" :title="row ? '批次 #' + String(row.id) + ' 详情' : '批次详情'">
    <div v-if="loading" class="jd-hint">明细回读中…</div>
    <template v-else-if="row">
      <!-- 层1：批次概要 -->
      <div class="ind-panel">
        <div class="ind-panel-h">
          <span class="ind-panel-t">批次概要</span>
          <span v-if="show().sourceCode" class="ind-panel-meta">源 {{ show().sourceCode }}</span>
        </div>
        <div class="ind-panel-b">
          <Descriptions :column="2" size="small" bordered>
            <Descriptions.Item label="状态">
              <Tag v-if="tagOf(JOB_STATUS_MAP, show().status)" :color="tagOf(JOB_STATUS_MAP, show().status)!.color">{{ tagOf(JOB_STATUS_MAP, show().status)!.text }}</Tag>
              <span v-else>—</span>
            </Descriptions.Item>
            <Descriptions.Item label="触发类型">
              <Tag v-if="tagOf(TRIGGER_MAP, show().triggerType)" :color="tagOf(TRIGGER_MAP, show().triggerType)!.color">{{ tagOf(TRIGGER_MAP, show().triggerType)!.text }}</Tag>
              <span v-else>—</span>
            </Descriptions.Item>
            <Descriptions.Item label="源 code">{{ show().sourceCode || '（全源）' }}</Descriptions.Item>
            <Descriptions.Item label="范围">{{ show().scope || '—' }}</Descriptions.Item>
            <Descriptions.Item label="创建 / 完成">{{ fmtDt(show().createTime) }} / {{ fmtDt(show().finishedAt) }}</Descriptions.Item>
            <Descriptions.Item label="更新">{{ fmtDt(show().updateTime) }}</Descriptions.Item>
            <Descriptions.Item v-if="summaryLines().length" label="汇总明细" :span="2">
              <div v-for="(ln, i) in summaryLines()" :key="i" class="jd-sum ln">{{ ln }}</div>
            </Descriptions.Item>
            <Descriptions.Item label="摘要" :span="2">{{ jobSummaryText(show().summaryJson) || '—' }}</Descriptions.Item>
          </Descriptions>
        </div>
      </div>

      <!-- 层2：该批爬虫任务 N 条（克制行，点击就地展开过程；后端异步化后 RUNNING 批次可轮询看进度） -->
      <div class="ind-panel" style="margin-top: 14px">
        <div class="ind-panel-h">
          <span class="ind-panel-t">该批爬虫任务</span>
          <span class="ind-panel-meta">{{ tasks.length }} 条（jobId #{{ String(row.id) }}；点击任务行查看过程）</span>
        </div>
        <div class="ind-panel-b">
          <div v-if="tasksLoading" class="jd-hint">任务加载中…</div>
          <div v-else-if="!tasks.length" class="jd-hint">该批暂无任务行（一次空跑/纯列表发现未投递；或消费尚未落库）</div>
          <div v-else class="jd-tasks">
            <div v-for="t in tasks" :key="String(t.id)" class="ind-row jd-row" :class="{ on: selTask && selTask.id === t.id }" @click="pickTask(t)">
              <Tag v-if="tagOf(TASK_STATUS_MAP, t.status)" :color="tagOf(TASK_STATUS_MAP, t.status)!.color">{{ tagOf(TASK_STATUS_MAP, t.status)!.text }}</Tag>
              <span v-else>—</span>
              <span class="rw main truncate" :title="t.url || ''">{{ t.url || '（无 URL）' }}</span>
              <span class="rw">解析器：{{ t.parserCode || '—' }}</span>
              <span class="rw">attempt {{ t.attempt ?? '—' }}</span>
              <span class="rw time">{{ fmtDt(t.finishedAt || t.createTime) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 层3：纵向数据链（点选批内任务 → 就地完整链 ①URL→②获取方式→③原始载荷→④解析→⑤执行→⑥产出；复用 TaskChainView，非静态字段陈列） -->
      <div v-if="selTask" class="ind-panel" style="margin-top: 14px">
        <div class="ind-panel-h">
          <span class="ind-panel-t">纵向数据链</span>
          <span class="ind-panel-meta">任务 #{{ String(selTask.id) }} · payload/API配置原始数据可全屏</span>
        </div>
        <div class="ind-panel-b">
          <TaskChainView :task="selTask" :show-same-batch="false" :allow-retry="false" />
        </div>
      </div>
    </template>
    <div v-else class="jd-hint">未选择批次</div>
  </Drawer>
</template>

<style scoped>
.jd-hint { color: var(--ind-weak-text, #6b7280); font-size: 13px; padding: 10px 2px; }
.jd-tasks { display: flex; flex-direction: column; gap: 2px; }
.jd-row { cursor: pointer; padding: 7px 10px; border-radius: 6px; transition: background-color 0.15s; }
.jd-row:hover { background: var(--ind-row-hover, #f5f7fa); }
.jd-row.on { background: var(--ind-row-active, #e6f4ff); }
.jd-sum { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; line-height: 1.8; }
.ln { display: block; }
</style>