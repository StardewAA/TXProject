<!--
  任务“纵向数据链”调试视图（TaskChainView，无外壳纯内容，供 console 任务详情 / task 独立页 / job 页点任务复用）：
  业务：按任务 ID 展示一条采集/接入链路从“请求来源”到“落库结果”的全过程原始数据（调试用途）：
        请求 URL → 获取方式(CRAWLER/API+下载器/接口配置) → 原始载荷黑块(payload_json，含列表原文/全文) →
        解析/映射 → 执行记录/产出；每个原始数据块提供「复制」「全屏」与顶部下拉切换数据源（全屏完整预览）。
  理由：用户口径——详情不是横向铺字段，而是纵向链：上面原数据、下面直到结束；UI 只需方便展示数据 +
        一个下拉支持全屏完整预览，复杂筛选不需要（黑块即可，重点是能看见、能调试）。
  后端契约：task=get 见 04-全链路 §5.8（回读含 payload_json）；fetchType/apiConfigJson 在源(CrawlSourceRespVO，
        2026-09-10 模块更名 fetch/外部信息获取，见设计 v1.0 §4.2) —— task 不直接带获取方式，须按 sourceCode 反查源；
        同批任务=携 jobId 调 crawl-task/page 见 crawl-batch §三.1。
-->
<script lang="ts" setup>
import type { CrawlExternalApi } from '#/api/bidding/crawlExternal';

import { computed, ref, watch } from 'vue';

import { Button, message, Modal, Select, Tag } from 'ant-design-vue';

import { fetchCrawlSourcePage, fetchCrawlTaskPage, getCrawlTask, retryCrawlTask } from '#/api/bidding/crawlExternal';

import { TASK_STATUS_MAP, fmtDt, tagOf } from '../../shared';

const emit = defineEmits<{ (e: 'pick', t: CrawlExternalApi.TaskItem): void }>();

const props = withDefaults(defineProps<{
  /** 任务行对象（列表行即可，组件按 id 回读明细；为 null 显示空态） */
  task?: CrawlExternalApi.TaskItem | null;
  /** 是否内嵌展示同批任务（有 jobId 时），默认 true */
  showSameBatch?: boolean;
  /** 是否允许 FAILED 死信重试按钮，默认 true */
  allowRetry?: boolean;
}>(), { task: null, showSameBatch: true, allowRetry: true });

const loading = ref(false);
const srcLoading = ref(false);
const retryBusy = ref(false);
/** 回读明细（getTask） */
const full = ref<CrawlExternalApi.TaskItem | null>(null);
/** 反查到的源（拿 fetchType/apiConfigJson/channelCode 判断 CRAWLER/API） */
const src = ref<CrawlExternalApi.SourceItem | null>(null);
const sameBatch = ref<CrawlExternalApi.TaskItem[]>([]);
const sameBatchLoading = ref(false);

/** 展示用任务：回读优先，未回读用行字段 */
const t = computed(() => full.value || props.task || ({} as CrawlExternalApi.TaskItem));
/** 获取方式：源.fetchType，兜底按通道启发（CRAWLER）；API 源才展示 api_config 段 */
const fetchType = computed(() => {
  if (src.value?.fetchType) return src.value.fetchType;
  return 'CRAWLER';
});
const isApi = computed(() => fetchType.value === 'API');

async function load() {
  const row = props.task;
  if (!row || row.id == null) {
    full.value = null;
    src.value = null;
    sameBatch.value = [];
    return;
  }
  loading.value = true;
  try {
    const got = (await getCrawlTask(row.id)) as unknown as CrawlExternalApi.TaskItem;
    full.value = got || row;
  } catch (e) {
    full.value = row;
    message.error(e instanceof Error ? e.message : String(e));
  } finally {
    loading.value = false;
  }
  void loadSource();
  if (props.showSameBatch && row.jobId != null) void loadSameBatch(row.jobId);
}

/** 按 sourceCode 反查源（task 不带 fetch_type；源返回 fetchType/apiConfigJson/channelCode/parserCodeDefault） */
async function loadSource() {
  const code = props.task?.sourceCode || full.value?.sourceCode;
  if (!code) { src.value = null; return; }
  srcLoading.value = true;
  try {
    const res = (await fetchCrawlSourcePage({ sourceCode: code, pageNo: 1, pageSize: 1 })) as unknown as {
      list?: CrawlExternalApi.SourceItem[];
    };
    src.value = res.list?.[0] || null;
  } catch {
    src.value = null;
  } finally {
    srcLoading.value = false;
  }
}

async function loadSameBatch(jobId: number | string) {
  sameBatchLoading.value = true;
  try {
    const res = (await fetchCrawlTaskPage({ jobId, pageNo: 1, pageSize: 200 })) as unknown as {
      list?: CrawlExternalApi.TaskItem[];
    };
    const self = t.value?.id;
    sameBatch.value = (res.list || []).filter((s) => s.id == null || String(s.id) !== String(self));
  } catch {
    sameBatch.value = [];
  } finally {
    sameBatchLoading.value = false;
  }
}

watch(() => props.task, () => void load(), { immediate: true });

/** FAILED 死信重试（attempt 归零回 PENDING） */
async function handleRetry() {
  const id = t.value?.id;
  if (id == null) return;
  retryBusy.value = true;
  try {
    await retryCrawlTask(id);
    message.success('已重新入队（attempt 归零）');
    await load();
  } catch (e) {
    message.error(e instanceof Error ? e.message : String(e));
  } finally {
    retryBusy.value = false;
  }
}

/* ===== 全屏预览：下拉切换数据源 + 大 Modal 完整查看 ===== */
const previewOpen = ref(false);
type BlobKey = 'payload' | 'apiConfig' | 'taskFields' | 'sourceFields';
const previewKey = ref<BlobKey>('payload');
/** 可选数据源（只列有内容的） */
const previewOptions = computed(() => {
  const opts: Array<{ value: BlobKey; label: string }> = [];
  if (t.value?.payloadJson) opts.push({ value: 'payload', label: '原始载荷 payload_json' });
  if (isApi.value && src.value?.apiConfigJson) opts.push({ value: 'apiConfig', label: 'API 配置 api_config_json' });
  opts.push({ value: 'taskFields', label: '任务全字段(JSON)' });
  if (src.value) opts.push({ value: 'sourceFields', label: '源配置全字段(JSON)' });
  return opts;
});
const previewText = computed(() => {
  const k = previewKey.value;
  if (k === 'payload') return t.value?.payloadJson || '';
  if (k === 'apiConfig') return src.value?.apiConfigJson || '';
  if (k === 'taskFields') return JSON.stringify(t.value, null, 2);
  if (k === 'sourceFields') return JSON.stringify(src.value, null, 2);
  return '';
});
function openPreview(k: BlobKey) {
  if (previewOptions.value.some((o) => o.value === k)) previewKey.value = k;
  previewOpen.value = true;
}

async function copyText(text: string | undefined) {
  if (!text) return;
  try {
    await navigator.clipboard.writeText(text);
    message.success('已复制');
  } catch {
    message.error('复制失败（浏览器限制）');
  }
}

/** 解析 API 配置 JSON 为可读行（method/url/headers…） */
function apiCfgLines(): string[] {
  const raw = src.value?.apiConfigJson;
  if (!raw) return [];
  try {
    const o = JSON.parse(raw) as Record<string, unknown>;
    const order = ['method', 'url', 'bodyTemplate', 'pageParam', 'sizeParam', 'pageSize', 'rowsPath', 'totalPath', 'urlPrefix'];
    const lines: string[] = [];
    for (const k of order) {
      const v = o[k];
      if (v == null || v === '') continue;
      lines.push(k + ': ' + (typeof v === 'object' ? JSON.stringify(v) : String(v)));
    }
    for (const [k, v] of Object.entries(o)) {
      if (order.includes(k) || v == null) continue;
      lines.push(k + ': ' + (typeof v === 'object' ? JSON.stringify(v) : String(v)));
    }
    return lines;
  } catch {
    return [raw];
  }
}

/** payloadJson → 结构化快照（05 契约：{title,url,date,category,rawFileUrl?,rawSha256?,rawSize?}） */
const payloadObj = computed<{ title?: string; url?: string; date?: string; category?: string; rawFileUrl?: string; rawSha256?: string; rawSize?: number }>(() => {
  try {
    const raw = t.value?.payloadJson;
    if (!raw) return {};
    const o = JSON.parse(raw) as Record<string, unknown>;
    return {
      title: typeof o.title === 'string' ? o.title : undefined,
      url: typeof o.url === 'string' ? o.url : undefined,
      date: typeof o.date === 'string' ? o.date : undefined,
      category: typeof o.category === 'string' ? o.category : undefined,
      rawFileUrl: typeof o.rawFileUrl === 'string' ? o.rawFileUrl : undefined,
      rawSha256: typeof o.rawSha256 === 'string' ? o.rawSha256 : undefined,
      rawSize: typeof o.rawSize === 'number' ? o.rawSize : undefined,
    };
  } catch { return {}; }
});
function openRawFile() {
  const u = payloadObj.value.rawFileUrl;
  if (u) window.open(u, '_blank');
}
/** 字节数可读化 */
function fmtBytes(n: number | undefined): string {
  if (n == null) return '';
  if (n < 1024) return n + ' B';
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB';
  return (n / 1024 / 1024).toFixed(1) + ' MB';
}
</script>

<template>
  <div v-if="loading && !full" class="tc-hint">任务明细回读中…</div>
  <div v-else-if="!t.id" class="tc-hint">（未选择任务）</div>
  <div v-else class="tc-chain">
    <!-- 链头：任务定位（ID/获取方式/状态） -->
    <div class="tc-head">
      <span class="tc-head-id">任务 #{{ String(t.id) }}</span>
      <Tag :color="isApi ? 'purple' : 'blue'">{{ isApi ? 'API · 接口直取' : 'CRAWLER · 爬虫' }}</Tag>
      <Tag v-if="tagOf(TASK_STATUS_MAP, t.status)" :color="tagOf(TASK_STATUS_MAP, t.status)!.color">{{ tagOf(TASK_STATUS_MAP, t.status)!.text }}</Tag>
      <span class="ml-auto tc-ops">
        <Button v-if="allowRetry && t.status === 'FAILED'" danger size="small" :loading="retryBusy" @click="handleRetry">重试（attempt 归零）</Button>
      </span>
    </div>

    <!-- 段0：请求来源 URL（从哪个 URL 拉的） -->
    <div class="tc-step">
      <div class="tc-step-t">① 请求来源 URL</div>
      <div class="tc-step-b">
        <div class="tc-kv"><span class="tc-k">URL</span><span class="tc-v mono break-all">{{ t.url || '—' }}</span></div>
        <div class="tc-kv"><span class="tc-k">URL hash</span><span class="tc-v mono break-all">{{ t.urlHash || '—' }}</span></div>
        <div v-if="t.jobId != null" class="tc-kv"><span class="tc-k">所属批次</span><span class="tc-v">jobId #{{ String(t.jobId) }}</span></div>
      </div>
    </div>

    <!-- 段1：获取方式（CRAWLER=下载器 / API=接口配置） -->
    <div class="tc-step">
      <div class="tc-step-t">② 获取方式</div>
      <div class="tc-step-b">
        <div v-if="srcLoading" class="tc-hint">源信息回读中…</div>
        <template v-else>
          <div class="tc-kv"><span class="tc-k">获取方式</span><span class="tc-v">{{ isApi ? 'API · 接口直取(JSON)' : 'CRAWLER · 爬虫(列表HTML)' }}<span v-if="src?.sourceCode" class="tc-sub">（源 {{ src.sourceCode }}{{ src.sourceName ? ' · ' + src.sourceName : '' }}）</span></span></div>
          <div v-if="!isApi" class="tc-kv"><span class="tc-k">下载器/通道</span><span class="tc-v mono">{{ t.channelCode || src?.channelCode || '—' }}</span></div>
          <div v-else class="tc-kv"><span class="tc-k">接口配置</span>
            <span class="tc-v mono">
              <span v-if="apiCfgLines().length" class="tc-apitxt"><span v-for="(ln, i) in apiCfgLines()" :key="i" class="d-block">{{ ln }}</span></span>
              <span v-else>{{ src?.apiConfigJson || '（API 源未配置 api_config_json）' }}</span>
              <a v-if="src?.apiConfigJson" class="tc-act" href="#" @click.prevent="openPreview('apiConfig')">查看全量 JSON</a>
            </span>
          </div>
        </template>
      </div>
    </div>

    <!-- 段2：原始载荷 payload_json（05 契约：{title,url,date,category,rawFileUrl?,rawSha256?,rawSize?}——全文已文件化，payload 只存引用） -->
    <div class="tc-step">
      <div class="tc-step-t">③ 原始载荷（payload_json）
        <a class="tc-act" href="#" @click.prevent="openPreview('payload')">全屏 ▾</a>
        <a class="tc-act" href="#" @click.prevent="copyText(t.payloadJson)">复制</a>
      </div>
      <div class="tc-step-b">
        <template v-if="t.payloadJson">
          <div v-if="payloadObj.title || payloadObj.category || payloadObj.date" class="tc-kv"><span class="tc-k">快照标题</span><span class="tc-v">{{ payloadObj.title || '—' }}<template v-if="payloadObj.category"><span class="tc-sub">栏目 {{ payloadObj.category }}</span></template><template v-if="payloadObj.date"><span class="tc-sub">{{ payloadObj.date }}</span></template></span></div>
          <div class="tc-kv"><span class="tc-k">原文 URL</span><span class="tc-v mono break-all">{{ payloadObj.url || '—' }}</span></div>
          <div class="tc-kv">
            <span class="tc-k">原文文件</span>
            <span class="tc-v">
              <template v-if="payloadObj.rawFileUrl">
                <a href="#" @click.prevent="openRawFile()">查看原文文件（FileApi 归档）</a>
                <span class="tc-sub">{{ fmtBytes(payloadObj.rawSize) }}<template v-if="payloadObj.rawSha256"> · sha256 {{ String(payloadObj.rawSha256).slice(0, 16) }}…</template></span>
              </template>
              <span v-else>（本任务无原文文件引用——归档由后端在列表阶段完成，见 05 契约 §四）</span>
            </span>
          </div>
          <pre class="tc-blob">{{ t.payloadJson }}</pre>
        </template>
        <div v-else class="tc-blob-empty">（payload_json 为空——任务快照由后端投递端写入；空任务排查用字段，写入后此处即展示该任务“抓的是哪条公告/哪个文件”）</div>
      </div>
    </div>

    <!-- 段3：解析/映射 -->
    <div class="tc-step">
      <div class="tc-step-t">④ 解析 / 映射</div>
      <div class="tc-step-b">
        <div class="tc-kv"><span class="tc-k">解析/策略码</span><span class="tc-v mono">{{ t.parserCode || src?.parserCodeDefault || '（按 URL 规则自动匹配）' }}</span></div>
        <div v-if="isApi" class="tc-hint">API 源字段映射/抽取策略见 ② 接口配置 fieldMapping（全量 JSON 可全屏查看）</div>
      </div>
    </div>

    <!-- 段4：执行记录（尝试/worker/时间/错误） -->
    <div class="tc-step">
      <div class="tc-step-t">⑤ 执行记录 / 结果</div>
      <div class="tc-step-b">
        <div class="tc-kv"><span class="tc-k">状态 / 尝试</span><span class="tc-v">{{ tagOf(TASK_STATUS_MAP, t.status)?.text || t.status || '—' }} · attempt {{ t.attempt ?? '—' }}<template v-if="t.nextRetryTime"> · 下次重试 {{ fmtDt(t.nextRetryTime) }}</template></span></div>
        <div class="tc-kv"><span class="tc-k">worker</span><span class="tc-v mono">{{ t.workerId || '—' }}</span></div>
        <div class="tc-kv"><span class="tc-k">开始 / 完成</span><span class="tc-v">{{ fmtDt(t.startedAt) }} / {{ fmtDt(t.finishedAt) }}</span></div>
        <div class="tc-kv"><span class="tc-k">消息 / 错误</span><span class="tc-v break-all">{{ t.message || '—' }}</span></div>
      </div>
    </div>

    <!-- 段5：落库产出（占位：task → notice 回指待后端补） -->
    <div class="tc-step">
      <div class="tc-step-t">⑥ 落库 / 产出（外部公告关联）</div>
      <div class="tc-step-b tc-hint">
        本条任务抓取/解析后的结构化产出入库外部公告 NEW 待审（notice-external 域）。
        任务 → 公告的产出回指（noticeId / origUrl 反查）由后端在任务链写入后补齐，届时在此展示该条公告的标题/落库时间/审核入口。
      </div>
    </div>

    <!-- 同批任务（内嵌切换，父容器是否展示由 showSameBatch 控制） -->
    <div v-if="showSameBatch && t.jobId != null" class="tc-step">
      <div class="tc-step-t">同批任务（jobId #{{ String(t.jobId) }} · {{ sameBatch.length }} 条）</div>
      <div class="tc-step-b">
        <div v-if="sameBatchLoading" class="tc-hint">加载中…</div>
        <div v-else-if="!sameBatch.length" class="tc-hint">该批暂无其它任务行</div>
        <div v-else class="tc-batch">
          <div v-for="s in sameBatch" :key="String(s.id)" class="ind-row ind-dl-row tc-sib" @click="$emit('pick', s)">
            <Tag v-if="tagOf(TASK_STATUS_MAP, s.status)" :color="tagOf(TASK_STATUS_MAP, s.status)!.color">{{ tagOf(TASK_STATUS_MAP, s.status)!.text }}</Tag>
            <span v-else>—</span>
            <span class="rw main truncate" :title="s.url || ''">{{ s.url || '（无 URL）' }}</span>
            <span class="rw time">{{ fmtDt(s.finishedAt || s.createTime) }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- 全屏完整预览 Modal（下拉切换数据源） -->
  <Modal v-model:open="previewOpen" :width="'92vw'" :body-style="{ maxHeight: '78vh', overflow: 'auto', padding: 0 }" :footer="null">
    <div class="pv-head">
      <Select v-model:value="previewKey" :options="previewOptions" class="pv-sel" size="small" />
      <Button size="small" @click="copyText(previewText)">复制</Button>
    </div>
    <pre v-if="previewText" class="tc-blob pv-blob">{{ previewText }}</pre>
    <div v-else class="tc-hint">（当前数据源无内容）</div>
  </Modal>
</template>

<style scoped>
.tc-hint { color: var(--ind-weak-text, #6b7280); font-size: 13px; padding: 10px 2px; }
.tc-head { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.tc-head-id { font-size: 16px; font-weight: 800; color: var(--ind-ink-text, #1f2937); }
.tc-ops { margin-left: auto; }
.tc-step { border: 1px solid #e5e7eb; border-radius: 6px; margin-bottom: 10px; overflow: hidden; }
.tc-step-t { display: flex; align-items: center; gap: 10px; background: #f8fafc; padding: 7px 12px; font-size: 13px; font-weight: 700; color: #334155; border-bottom: 1px solid #eef2f7; }
.tc-step-b { padding: 10px 12px; }
.tc-kv { display: flex; gap: 10px; padding: 3px 0; font-size: 13px; }
.tc-k { flex-shrink: 0; width: 108px; color: var(--ind-weak-text, #6b7280); }
.tc-v { min-width: 0; }
.tc-sub { color: #94a3b8; font-size: 12px; margin-left: 8px; }
.tc-apitxt { display: block; white-space: pre-wrap; word-break: break-all; }
.tc-blob { background: #0b1220; color: #d1e4ff; font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12px; line-height: 1.6; padding: 12px; border-radius: 4px; max-height: 300px; overflow: auto; white-space: pre-wrap; word-break: break-all; margin: 0; }
.pv-blob { max-height: none; border-radius: 0; }
.tc-blob-empty { border: 1px dashed #cbd5e1; border-radius: 4px; padding: 16px; color: #94a3b8; font-size: 12.5px; background: #f8fafc; }
.tc-act { color: #2563eb; font-size: 12px; margin-left: auto; text-decoration: none; }
.tc-act + .tc-act { margin-left: 6px; }
.tc-batch { display: flex; flex-direction: column; gap: 2px; }
.tc-sib { cursor: pointer; padding: 6px 10px; border-radius: 5px; }
.tc-sib:hover { background: var(--ind-row-hover, #f5f7fa); }
.pv-head { display: flex; align-items: center; gap: 8px; padding: 10px 12px; border-bottom: 1px solid #e5e7eb; position: sticky; top: 0; background: #fff; z-index: 1; }
.pv-sel { width: 260px; }
.d-block { display: block; }
.mono { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12.5px; }
.break-all { word-break: break-all; }
</style>