<!--
  采集任务详情档（两档视图之“舒张”层，独立 Grid 页配套）：
  业务：采集任务列表只留克制检索行；点「详情」进入本档——任务概要（含批次归属 jobId）→ 同批任务(该行有 jobId 时)
        → 任务过程；FAILED 死信可在此档人工重试（attempt 归零开新一轮），操作收进详情层不再堆在行内。
  理由：与 console/source 同口径的“克制行 + 整页分层”展示语言；后端异步队列化(2026-09-10 设计)后
        task 全程 PENDING/RUNNING/SUCCESS/FAILED 可见，详情档是排查单条采集链路的落点。
  后端契约：crawl-task get(回读明细) 见 04-全链路 §5.8；同批任务=携 jobId 调 crawl-task/page 见 crawl-batch §三.1；
        retry(仅 FAILED) 见 §5.9；task payloadJson 见 2026-09-10-crawl-async-queue-design §三/§九（本期仅展示预留，不依赖）。
-->
<script lang="ts" setup>
import type { CrawlExternalApi } from '#/api/bidding/crawlExternal';

import { ref } from 'vue';

import { Button, Descriptions, Drawer, message, Modal, Tag } from 'ant-design-vue';

import { fetchCrawlTaskPage, getCrawlTask, retryCrawlTask } from '#/api/bidding/crawlExternal';

import { TASK_STATUS_MAP, fmtDt, tagOf } from '../../shared';

const open = ref(false);
const loading = ref(false);
const busy = ref(false);
const row = ref<CrawlExternalApi.TaskItem | null>(null);
const full = ref<CrawlExternalApi.TaskItem | null>(null);
const sameBatch = ref<CrawlExternalApi.TaskItem[]>([]);
const sameBatchLoading = ref(false);

async function openTask(t: CrawlExternalApi.TaskItem) {
  row.value = t;
  full.value = null;
  sameBatch.value = [];
  open.value = true;
  loading.value = true;
  try {
    if (t.id != null) {
      full.value = (await getCrawlTask(Number(t.id))) as unknown as CrawlExternalApi.TaskItem;
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : String(e));
  } finally {
    loading.value = false;
  }
  if (t.jobId != null) void loadSameBatch(Number(t.jobId), String(t.id));
}

/** 同批任务（批次上下文下钻用；crawl-batch §三.1 携 jobId 拉取） */
async function loadSameBatch(jobId: number, selfId: string) {
  sameBatchLoading.value = true;
  try {
    const res = (await fetchCrawlTaskPage({
      jobId,
      pageNo: 1,
      pageSize: 200,
    })) as unknown as { list?: CrawlExternalApi.TaskItem[] };
    sameBatch.value = (res.list || []).filter((s) => String(s.id) !== selfId);
  } catch {
    sameBatch.value = [];
  } finally {
    sameBatchLoading.value = false;
  }
}

/** 同批点另一条 → 就地切换当前任务（回读明细），保持批次上下文 */
async function switchTask(s: CrawlExternalApi.TaskItem) {
  if (s.id == null || String(s.id) === String(row.value?.id)) return;
  row.value = s;
  full.value = null;
  loading.value = true;
  try {
    full.value = (await getCrawlTask(Number(s.id))) as unknown as CrawlExternalApi.TaskItem;
  } catch (e) {
    message.error(e instanceof Error ? e.message : String(e));
  } finally {
    loading.value = false;
  }
}

/** 死信重试：attempt 归零开启新一轮（仅 FAILED），成功后在详情内回读刷新 */
function handleRetry() {
  const t = row.value;
  if (!t || t.id == null) return;
  Modal.confirm({
    title: '确认人工重试该死信任务？',
    content: 'attempt 将归零，任务回到 PENDING 队列开启新一轮抓取。',
    okText: '确认重试',
    cancelText: '取消',
    onOk: async () => {
      busy.value = true;
      try {
        await retryCrawlTask(Number(t.id));
        message.success('已重新入队');
        await openTask(row.value!);
      } catch (e) {
        message.error(e instanceof Error ? e.message : String(e));
      } finally {
        busy.value = false;
      }
    },
  });
}

/** 详情展示用对象：回读字段优先，未回读前用列表行字段兜底 */
const dt = (): CrawlExternalApi.TaskItem => full.value || row.value || ({} as CrawlExternalApi.TaskItem);

defineExpose({ openTask });
</script>

<template>
  <Drawer v-model:open="open" :width="920" :title="row ? '任务 #' + String(row.id) + ' 详情' : '任务详情'">
    <div v-if="loading" class="td-hint">明细回读中…</div>
    <template v-else-if="row">
      <!-- 层1：任务概要（回读 get 明细，未回读用行字段兜底） -->
      <div class="ind-panel">
        <div class="ind-panel-h">
          <span class="ind-panel-t">任务概要</span>
          <span v-if="row.jobId != null" class="ind-panel-meta">批次 jobId #{{ String(row.jobId) }}</span>
          <span class="ml-auto">
            <Button v-if="(full?.status || row.status) === 'FAILED'" danger size="small" :loading="busy" @click="handleRetry">
              重试（attempt 归零）
            </Button>
          </span>
        </div>
        <div class="ind-panel-b">
          <Descriptions :column="2" size="small" bordered>
            <Descriptions.Item label="状态">
              <Tag v-if="tagOf(TASK_STATUS_MAP, dt().status)" :color="tagOf(TASK_STATUS_MAP, dt().status)!.color">{{ tagOf(TASK_STATUS_MAP, dt().status)!.text }}</Tag>
              <span v-else>—</span>
            </Descriptions.Item>
            <Descriptions.Item label="源 / 通道">{{ dt().sourceCode || '—' }} / {{ dt().channelCode || '—' }}</Descriptions.Item>
            <Descriptions.Item label="URL" :span="2"><span class="break-all">{{ dt().url || '—' }}</span></Descriptions.Item>
            <Descriptions.Item label="解析器">{{ dt().parserCode || '（按 URL 规则自动匹配）' }}</Descriptions.Item>
            <Descriptions.Item label="尝试次数">{{ dt().attempt ?? '—' }}</Descriptions.Item>
            <Descriptions.Item label="worker">{{ dt().workerId || '—' }}</Descriptions.Item>
            <Descriptions.Item label="下次重试">{{ fmtDt(dt().nextRetryTime) }}</Descriptions.Item>
            <Descriptions.Item label="开始 / 完成">{{ fmtDt(dt().startedAt) }} / {{ fmtDt(dt().finishedAt) }}</Descriptions.Item>
            <Descriptions.Item label="创建 / 更新">{{ fmtDt(dt().createTime) }} / {{ fmtDt(dt().updateTime) }}</Descriptions.Item>
            <Descriptions.Item label="消息 / 错误" :span="2"><span class="break-all">{{ dt().message || '—' }}</span></Descriptions.Item>
          </Descriptions>
        </div>
      </div>

      <!-- 层2：同批任务（本档自带批次上下文；后端异步化后同批=一次采集的全部任务行） -->
      <div v-if="row.jobId != null" class="ind-panel" style="margin-top: 14px">
        <div class="ind-panel-h">
          <span class="ind-panel-t">同批任务</span>
          <span class="ind-panel-meta">{{ sameBatch.length }} 条（点击切换查看）</span>
        </div>
        <div class="ind-panel-b">
          <div v-if="sameBatchLoading" class="td-hint">加载中…</div>
          <div v-else-if="!sameBatch.length" class="td-hint">该批暂无其它任务行</div>
          <div v-else class="td-batch">
            <div v-for="s in sameBatch" :key="String(s.id)" class="ind-row ind-dl-row" :class="{ on: full && full.id != null && String(full.id) === String(s.id) }" @click="switchTask(s)">
              <Tag v-if="tagOf(TASK_STATUS_MAP, s.status)" :color="tagOf(TASK_STATUS_MAP, s.status)!.color">{{ tagOf(TASK_STATUS_MAP, s.status)!.text }}</Tag>
              <span v-else>—</span>
              <span class="rw main truncate" :title="s.url || ''">{{ s.url || '（无 URL）' }}</span>
              <span class="rw">解析器：{{ s.parserCode || '—' }}</span>
              <span class="rw">attempt {{ s.attempt ?? '—' }}</span>
              <span class="rw time">{{ fmtDt(s.finishedAt || s.createTime) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 层3：任务过程（下载/解析链路说明；steps/logs 落库后在此完整展开） -->
      <div class="ind-panel" style="margin-top: 14px">
        <div class="ind-panel-h"><span class="ind-panel-t">任务过程</span></div>
        <div class="ind-panel-b td-proc">
          下载器 → 解析器 → 中间文件与逐步时间线将在 TaskRunVO（steps_json + logs + raw/extract/render）落库后在此完整呈现；
          当前以任务行字段为准：状态 {{ tagOf(TASK_STATUS_MAP, dt().status)?.text || dt().status || '—' }}，
          尝试 {{ dt().attempt ?? '—' }}，worker {{ dt().workerId || '—' }}。
        </div>
      </div>
    </template>
    <div v-else class="td-hint">未选择任务</div>
  </Drawer>
</template>

<style scoped>
.td-hint { color: var(--ind-weak-text, #6b7280); font-size: 13px; padding: 10px 2px; }
.td-batch { display: flex; flex-direction: column; gap: 2px; }
.ind-dl-row { cursor: pointer; padding: 7px 10px; border-radius: 6px; transition: background-color 0.15s; }
.ind-dl-row:hover { background: var(--ind-row-hover, #f5f7fa); }
.ind-dl-row.on { background: var(--ind-row-active, #e6f4ff); }
.td-proc { color: var(--ind-weak-text, #6b7280); font-size: 12.5px; line-height: 1.7; }
</style>