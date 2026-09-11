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
import type { NoticeExternalApi } from '#/api/bidding/noticeExternal';

import { computed, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Button, message, Modal, Select, Tag } from 'ant-design-vue';

import { fetchCrawlSourcePage, fetchCrawlTaskPage, getCrawlTask, retryCrawlTask } from '#/api/bidding/crawlExternal';
import { getManagePage } from '#/api/bidding/noticeExternal';

import { AUDIT_MAP, STATUS_MAP as EXT_STATUS_MAP } from '../../../noticeExternal/data';
import NoticeDetailModal from '../../../noticeExternal/modules/detail.vue';
import { fmtDt, tagOf, TASK_STATUS_MAP } from '../../shared';

import '../../taskbar.css';

const props = withDefaults(defineProps<{
  /** 是否允许 FAILED 死信重试按钮，默认 true */
  allowRetry?: boolean;
  /** 是否内嵌展示同批任务（有 jobId 时），默认 true */
  showSameBatch?: boolean;
  /** 任务行对象（列表行即可，组件按 id 回读明细；为 null 显示空态） */
  task?: CrawlExternalApi.TaskItem | null;
}>(), { task: null, showSameBatch: true, allowRetry: true });

const emit = defineEmits<{ (e: 'pick', t: CrawlExternalApi.TaskItem): void }>();

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
    // SUCCESS 任务：按 sourceCode+origUrl 定位产出入库的外部公告（段6 成品预览）
    if (got?.status === 'SUCCESS') void findProduct();
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

/** 成品预览弹窗（外部公告管理预览：默认落在“前台观感”页签） */
const [NoticeDetail, noticeDetailApi] = useVbenModal({
  connectedComponent: NoticeDetailModal,
  destroyOnClose: true,
});

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
type BlobKey = 'apiConfig' | 'payload' | 'sourceFields' | 'taskFields';
const previewKey = ref<BlobKey>('payload');
/** 可选数据源（只列有内容的） */
const previewOptions = computed(() => {
  const opts: Array<{ label: string; value: BlobKey; }> = [];
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

/* ===== 段6 产出自查：任务 SUCCESS → 按 sourceCode+origUrl 在 notice-external 管理列表定位本条公告 ===== */
const productState = ref<'error' | 'found' | 'idle' | 'loading' | 'none'>('idle');
const productErr = ref('');
const productRows = ref<NoticeExternalApi.ManageItem[]>([]);
const productLoading = ref(false);

/** URL 归一：去首尾空白（站点外壳页 hash/query 差异由后端入库时已规整，前端只做基本 trim 兜底） */
const normUrl = (u?: null | string): string => (u || '').trim();

/** 本条任务对应的外部公告候选：payload 快照 URL / 任务行 url 任一同源即认（origUrl 即采集详情 URL，后端 ingest 同值写入） */
async function findProduct() {
  productState.value = 'idle';
  productRows.value = [];
  productErr.value = '';
  const code = src.value?.sourceCode || props.task?.sourceCode;
  if (!code) return;
  const tUrl = normUrl(t.value?.url);
  const pUrl = normUrl(payloadObj.value?.url);
  if (!tUrl && !pUrl) {
    productState.value = 'none';
    return;
  }
  productLoading.value = true;
  productState.value = 'loading';
  try {
    const res = (await getManagePage({ sourceCode: code, pageNo: 1, pageSize: 100 })) as unknown as {
      list?: NoticeExternalApi.ManageItem[];
      total?: number;
    };
    const list = res.list || [];
    const hit = list.filter((row) => {
      const o = normUrl(row.origUrl);
      return o && (o === tUrl || o === pUrl);
    });
    // 同源未按 URL 精确命中时：最近 20 条内标题兜底（爬取端与入库标题可能略有差异，仅展示候选供人工比对）
    const titleHit = hit.length
      ? []
      : list.slice(0, 20).filter((row) => {
          const a = (row.title || '').replace(/\s+/g, '').replace(/[/／]/g, '');
          const b = (payloadObj.value?.title || '').replace(/\s+/g, '').replace(/[/／]/g, '');
          return b && a === b;
        });
    productRows.value = hit.length ? hit : titleHit;
    productState.value = productRows.value.length ? 'found' : 'none';
  } catch (e) {
    productErr.value = e instanceof Error ? e.message : String(e);
    productState.value = 'error';
  } finally {
    productLoading.value = false;
  }
}

/** 打开成品预览（外部公告管理详情，默认页签=前台观感：审核发布后门户展示形态） */
function openProduct(row: NoticeExternalApi.ManageItem) {
  noticeDetailApi.setData({ id: row.id, title: row.title }).open();
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
const payloadObj = computed<{ category?: string; date?: string; rawFileUrl?: string; rawSha256?: string; rawSize?: number; title?: string; url?: string; }>(() => {
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
    <div class="ind-panel tc-step">
      <div class="ind-panel-h tc-step-t">① 请求来源 URL</div>
      <div class="ind-panel-b">
        <div class="tc-kv"><span class="tc-k">URL</span><span class="tc-v mono break-all">{{ t.url || '—' }}</span></div>
        <div class="tc-kv"><span class="tc-k">URL hash</span><span class="tc-v mono break-all">{{ t.urlHash || '—' }}</span></div>
        <div v-if="t.jobId != null" class="tc-kv"><span class="tc-k">所属批次</span><span class="tc-v">jobId #{{ String(t.jobId) }}</span></div>
      </div>
    </div>

    <!-- 段1：获取方式（CRAWLER=下载器 / API=接口配置） -->
    <div class="ind-panel tc-step">
      <div class="ind-panel-h tc-step-t">② 获取方式</div>
      <div class="ind-panel-b">
        <div v-if="srcLoading" class="tc-hint">源信息回读中…</div>
        <template v-else>
          <div class="tc-kv"><span class="tc-k">获取方式</span><span class="tc-v">{{ isApi ? 'API · 接口直取(JSON)' : 'CRAWLER · 爬虫(列表HTML)' }}<span v-if="src?.sourceCode" class="tc-sub">（源 {{ src.sourceCode }}{{ src.sourceName ? ` · ${ src.sourceName}` : '' }}）</span></span></div>
          <div v-if="!isApi" class="tc-kv"><span class="tc-k">下载器/通道</span><span class="tc-v mono">{{ t.channelCode || src?.channelCode || '—' }}</span></div>
          <div v-else class="tc-kv">
<span class="tc-k">接口配置</span>
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
    <div class="ind-panel tc-step">
      <div class="ind-panel-h tc-step-t">
③ 原始载荷（payload_json）
        <a class="tc-act" href="#" @click.prevent="openPreview('payload')">全屏 ▾</a>
        <a class="tc-act" href="#" @click.prevent="copyText(t.payloadJson)">复制</a>
      </div>
      <div class="ind-panel-b">
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
    <div class="ind-panel tc-step">
      <div class="ind-panel-h tc-step-t">④ 解析 / 映射</div>
      <div class="ind-panel-b">
        <div class="tc-kv"><span class="tc-k">解析/策略码</span><span class="tc-v mono">{{ t.parserCode || src?.parserCodeDefault || '（按 URL 规则自动匹配）' }}</span></div>
        <div v-if="isApi" class="tc-hint">API 源字段映射/抽取策略见 ② 接口配置 fieldMapping（全量 JSON 可全屏查看）</div>
      </div>
    </div>

    <!-- 段4：执行记录（尝试/worker/时间/错误） -->
    <div class="ind-panel tc-step">
      <div class="ind-panel-h tc-step-t">⑤ 执行记录 / 结果</div>
      <div class="ind-panel-b">
        <div class="tc-kv"><span class="tc-k">状态 / 尝试</span><span class="tc-v">{{ tagOf(TASK_STATUS_MAP, t.status)?.text || t.status || '—' }} · attempt {{ t.attempt ?? '—' }}<template v-if="t.nextRetryTime"> · 下次重试 {{ fmtDt(t.nextRetryTime) }}</template></span></div>
        <div class="tc-kv"><span class="tc-k">worker</span><span class="tc-v mono">{{ t.workerId || '—' }}</span></div>
        <div class="tc-kv"><span class="tc-k">开始 / 完成</span><span class="tc-v">{{ fmtDt(t.startedAt) }} / {{ fmtDt(t.finishedAt) }}</span></div>
        <div class="tc-kv"><span class="tc-k">消息 / 错误</span><span class="tc-v break-all">{{ t.message || '—' }}</span></div>
      </div>
    </div>

    <!-- 段6：落库 / 产出（notice-external 成品定位：SUCCESS 时按 sourceCode + origUrl 匹配入库公告，可看“前台观感”） -->
    <div class="ind-panel tc-step">
      <div class="ind-panel-h tc-step-t">
⑥ 落库 / 产出（外部公告 · 成品）
        <template v-if="productState === 'found'">
          <a class="tc-act" href="#" @click.prevent="findProduct">刷新</a>
        </template>
      </div>
      <div class="ind-panel-b">
        <template v-if="productLoading">
          <div class="tc-hint">在外部公告库中定位本条任务产出…</div>
        </template>
        <template v-else-if="productState === 'found'">
          <div class="tc-hint">任务抓取内容已入库（notice-external 域），点击「查看成品」看它在门户大厅发布后的样子：</div>
          <div v-for="r in productRows.slice(0, 5)" :key="String(r.id)" class="tc-prod">
            <span class="tc-prod-title truncate" :title="r.title || ''">{{ r.title || '（无标题）' }}</span>
            <span class="tc-prod-tags">
              <Tag v-if="r.status" :color="tagOf(EXT_STATUS_MAP, r.status)?.color || 'default'">{{ tagOf(EXT_STATUS_MAP, r.status)?.text || r.status }}</Tag>
              <Tag v-if="r.auditStatus" :color="tagOf(AUDIT_MAP, r.auditStatus)?.color || 'default'">{{ tagOf(AUDIT_MAP, r.auditStatus)?.text || r.auditStatus }}</Tag>
            </span>
            <a class="tc-act tc-prod-go" href="#" @click.prevent="openProduct(r)">查看成品 ▸</a>
          </div>
          <div class="tc-hint" v-if="productRows.length > 5">（仅列前 5 条；同源同日多条同名公告属正常，可逐条比对）</div>
        </template>
        <template v-else-if="productState === 'none'">
          <div class="tc-hint">
            {{ t.status === 'SUCCESS' ? '任务执行成功，但在外部公告库未按 URL 匹配到对应产出。' : '任务未成功执行，暂无落库产出。' }}
            落库仅在 SUCCESS 且后端投递链路完成时发生；若已成功仍未见，可到「外部公告审核管理」按来源站人工核查，或等后端 task→notice 回指补齐后此处自动直达。
          </div>
        </template>
        <template v-else-if="productState === 'error'">
          <div class="tc-hint">产出定位失败：{{ productErr || '接口异常' }}（管理列表需 bidding:notice-external:update 权限）</div>
        </template>
        <template v-else>
          <div class="tc-hint">本条任务执行成功后，自动在外部公告库中定位产出入库的成品（notice-external 域 → 审核 → 大厅）。</div>
        </template>
      </div>
    </div>

    <!-- 同批任务（内嵌切换，父容器是否展示由 showSameBatch 控制） -->
    <div v-if="showSameBatch && t.jobId != null" class="ind-panel tc-step">
      <div class="ind-panel-h tc-step-t">同批任务（jobId #{{ String(t.jobId) }} · {{ sameBatch.length }} 条）</div>
      <div class="ind-panel-b">
        <div v-if="sameBatchLoading" class="tc-hint">加载中…</div>
        <div v-else-if="!sameBatch.length" class="tc-hint">该批暂无其它任务行</div>
        <div v-else class="fp-tbl">
          <div class="fp-tr fp-th">
            <span class="fp-td w-status">状态</span>
            <span class="fp-td w-time">时间</span>
            <span class="fp-td w-id">任务 #</span>
            <span class="fp-td flex-1">URL / 来源</span>
            <span class="fp-td w-msg">消息 / 明细</span>
          </div>
          <div v-for="s in sameBatch" :key="String(s.id)" class="fp-tr fp-row-click" @click="$emit('pick', s)">
            <span class="fp-td w-status">
              <Tag v-if="tagOf(TASK_STATUS_MAP, s.status)" :color="tagOf(TASK_STATUS_MAP, s.status)!.color">{{ tagOf(TASK_STATUS_MAP, s.status)!.text }}</Tag>
              <span v-else>—</span>
            </span>
            <span class="fp-td w-time">{{ fmtDt(s.finishedAt || s.createTime) || '—' }}</span>
            <span class="fp-td w-id">#{{ String(s.id) }}</span>
            <span class="fp-td flex-1 fp-url" :title="s.url || ''">{{ s.url || '（无 URL）' }}</span>
            <span class="fp-td w-msg fp-msg" :title="s.message || ''">
              <template v-if="s.message">{{ s.message }}</template>
              <template v-else>解析器 {{ s.parserCode || '—' }} · 尝试 {{ s.attempt ?? '—' }}</template>
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- 成品预览（外部公告管理详情，前台观感页签） -->
  <NoticeDetail />

  <!-- 全屏完整预览 Modal（下拉切换数据源） -->
  <Modal v-model:open="previewOpen" width="92vw" :body-style="{ maxHeight: '78vh', overflow: 'auto', padding: 0 }" :footer="null">
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
.tc-prod { display: flex; align-items: center; gap: 10px; padding: 7px 10px; border: 1.5px solid var(--ind-line, #d8dde3); border-radius: 3px; margin-bottom: 6px; background: #fff; font-size: 13px; }
.tc-prod-title { flex: 1; min-width: 0; font-weight: 600; color: var(--ind-ink-text, #1f2937); }
.tc-prod-tags { display: flex; gap: 4px; flex: none; }
.tc-prod-go { flex: none; font-weight: 700; }
.tc-head { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.tc-head-id { font-size: 16px; font-weight: 800; color: var(--ind-ink-text, #1f2937); }
.tc-ops { margin-left: auto; }
/* 段容器复用工作台线稿面板（bid-indust.css 全局 .bid-ind .ind-panel 家族）：
 *   边框/纸面/圆角由 .ind-panel 提供，段头底纹与内边距由 .ind-panel-h / .ind-panel-b 提供，
 *   这里只保留段间距与段头排版，避免自有样式与全局面板样式分叉（任务详情与批次详情/抽屉面板同观感）。 */
.tc-step { margin-bottom: 10px; }
.tc-step-t { display: flex; align-items: center; gap: 10px; font-size: 15px; font-weight: 800; color: var(--ind-ink-text, #1f2937); }
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
/* 同批任务行改用统一任务条样式（../taskbar.css 的 .fp-tbl） */
.pv-head { display: flex; align-items: center; gap: 8px; padding: 10px 12px; border-bottom: 1px solid #e5e7eb; position: sticky; top: 0; background: #fff; z-index: 1; }
.pv-sel { width: 260px; }
.d-block { display: block; }
.mono { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12.5px; }
.break-all { word-break: break-all; }
</style>