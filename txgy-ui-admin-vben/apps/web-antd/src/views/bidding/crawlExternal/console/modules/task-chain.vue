<!--
  任务“纵向数据链”调试视图（TaskChainView，无外壳纯内容，供 console 任务详情 / task 独立页 / job 页点任务复用）：
  业务：按任务 ID 展示一条采集/接入链路从“请求来源”到“落库结果”的全过程原始数据（调试用途）：
        请求 URL → 列表驱动方式(CRAWLER/API)+正文形态(HTML/PDF)+下载器/接口配置 → 原始载荷黑块(payload_json，含列表原文/全文) →
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
import { fmtBytes } from '#/utils/format';

import { AUDIT_MAP, STATUS_MAP as EXT_STATUS_MAP } from '../../../noticeExternal/data';
import NoticeDetailModal from '../../../noticeExternal/modules/detail.vue';
import { contentFormText, FETCH_TYPE_LABEL, fetchTypeText } from '../../labels';
import { crawlErrMsg, fmtDt, tagOf, TASK_STATUS_MAP } from '../../shared';

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
/** 列表驱动方式：源.fetchType（**只表示列表怎么拿**，正文形态见 contentForm）；API 源才展示 api_config 段 */
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
    message.error(crawlErrMsg(e));
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
    message.error(crawlErrMsg(e));
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
  if (t.value?.payloadJson) opts.push({ value: 'payload', label: '原始载荷（payload_json）— 技术视图' });
  if (isApi.value && src.value?.apiConfigJson) opts.push({ value: 'apiConfig', label: 'API 配置（api_config_json）— 技术视图' });
  opts.push({ value: 'taskFields', label: '任务全字段（JSON）— 技术视图' });
  if (src.value) opts.push({ value: 'sourceFields', label: '信息源配置全字段（JSON）— 技术视图' });
  return opts;
});
const previewText = computed(() => {
  const k = previewKey.value;
  if (k === 'payload') return prettyJson(t.value?.payloadJson);
  if (k === 'apiConfig') return prettyJson(src.value?.apiConfigJson);
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
    // 同源未按 URL 精确命中时：最近 20 条内标题兜底（采集端与入库标题可能略有差异，仅展示候选供人工比对）
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
    productErr.value = crawlErrMsg(e);
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

/** JSON 字符串转为立体格式；解析失败时保留原文，避免隐藏异常载荷。 */
function prettyJson(raw: string | undefined): string {
  if (!raw) return '';
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
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
/** 业务链接：新窗口打开原文网址 */
function openOrigin() {
  const u = originUrl.value;
  if (u) window.open(u, '_blank');
}
/** 字节数可读化（空值留白：原始文件大小未知时不显示占位）——委托 #/utils/format 的统一口径 */
function sizeText(n: number | undefined): string {
  return fmtBytes(n, '');
}

/* ===== 业务可读口径：把技术字段翻译成业务人员看得懂的话（纯展示，不改后端/VO/存储） ===== */
/** 友好状态标签（替代 CRAWLER / attempt 等技术词） */
const FRIENDLY_STATUS: Record<string, { color: string; text: string; }> = {
  PENDING: { text: '🟡 排队中（预计 1 分钟）', color: 'gold' },
  RUNNING: { text: '🔵 正在处理', color: 'blue' },
  SUCCESS: { text: '✅ 已完成', color: 'green' },
  FAILED: { text: '🔴 处理失败', color: 'red' },
  SKIPPED: { text: '⚪ 已跳过', color: 'default' },
};
const friendlyStatus = computed(() => FRIENDLY_STATUS[t.value?.status || ''] || { text: t.value?.status || '—', color: 'default' });

/** 原文链接（载荷快照优先，兜底任务 url） */
const originUrl = computed(() => (payloadObj.value.url || t.value?.url || '').trim());
/** 来源名称（业务可读） */
const sourceNameText = computed(() => src.value?.sourceName || src.value?.sourceCode || '未知信息源');
/** sourceCode → 中文名兜底映射（源配置未填 sourceName 时，业务可读替代技术码；新增技术码在此补一条即可） */
const SOURCE_NAME_FALLBACK: Record<string, string> = {
  GX_E_BIDDING: '山西国信 e 采',
};
/** 匹配规则可读文案：源名优先 → 中文名映射兜底 → 原始 code → 自动匹配 */
const matchText = computed(() => {
  const name = (src.value?.sourceName || '').trim();
  const code = src.value?.sourceCode;
  const readable = name || (code ? SOURCE_NAME_FALLBACK[code] : '') || code || '自动匹配';
  return `匹配规则：${readable}（自动识别）`;
});
const matchOk = computed(() => t.value?.status === 'SUCCESS' || t.value?.status === 'RUNNING');
/** 技术详情折叠开关（默认隐藏） */
const showTech = ref(false);

/** 处理进度时间线（快递轨迹式；诚实映射可用生命周期字段，不虚构逐步时间） */
const timeline = computed<Array<{ current: boolean; icon: string; label: string; time?: string; }>>(() => {
  const st = t.value?.status;
  const started = fmtDt(t.value?.startedAt);
  const finished = fmtDt(t.value?.finishedAt);
  const out: Array<{ current: boolean; icon: string; label: string; time?: string; }> = [];
  if (!st || st === 'PENDING') {
    out.push({ icon: '⏳', label: '排队中，等待执行', time: started, current: true });
    out.push({ icon: '○', label: '抓取网页', current: false });
    out.push({ icon: '○', label: '提取标题、日期并归档', current: false });
  } else if (st === 'SKIPPED') {
    out.push({ icon: '⚪', label: '已跳过', time: finished, current: true });
  } else if (st === 'RUNNING') {
    out.push({ icon: '✅', label: '已抓取网页', time: started, current: false });
    out.push({ icon: '⏳', label: '正在提取标题、日期…', time: started, current: true });
    out.push({ icon: '○', label: '入库归档', current: false });
  } else if (st === 'SUCCESS') {
    out.push({ icon: '✅', label: '已抓取网页', time: started, current: false });
    out.push({ icon: '✅', label: '已提取标题、日期', current: false });
    out.push({ icon: '✅', label: '已入库归档', time: finished, current: true });
  } else if (st === 'FAILED') {
    out.push({ icon: '✅', label: '已抓取网页', time: started, current: false });
    out.push({ icon: '🔴', label: '处理失败', time: finished, current: true });
  } else {
    out.push({ icon: '—', label: st || '未知状态', time: finished, current: true });
  }
  return out;
});
</script>

<template>
  <div v-if="loading && !full" class="tc-hint">任务明细回读中…</div>
  <div v-else-if="!t.id" class="tc-hint">（未选择任务）</div>
  <div v-else class="tc-chain">
    <!-- 链头：友好进度状态（公告名称由外层整页详情 fp-bar 展示，本组件不再重复） -->
    <div class="tc-head">
      <Tag :color="friendlyStatus.color">{{ friendlyStatus.text }}</Tag>
      <span class="ml-auto tc-ops">
        <Button v-if="allowRetry && t.status === 'FAILED'" danger size="small" :loading="retryBusy" @click="handleRetry">重试</Button>
      </span>
    </div>

    <!-- 段0+1：来源信息（业务可读：来源 + 原文链接；技术细节折叠进“查看技术详情”） -->
    <div class="ind-panel tc-step">
      <div class="ind-panel-h tc-step-t">来源信息</div>
      <div class="ind-panel-b">
        <div v-if="srcLoading" class="tc-hint">源信息回读中…</div>
        <template v-else>
          <div class="tc-kv"><span class="tc-k">来源</span><span class="tc-v">{{ sourceNameText }}</span></div>
          <div class="tc-kv"><span class="tc-k">原文网址</span><span class="tc-v"><a v-if="originUrl" href="#" @click.prevent="openOrigin()">点击跳转网页</a><span v-else>—</span></span></div>
          <div v-if="showTech" class="tc-tech">
            <div class="tc-kv"><span class="tc-k">请求 URL</span><span class="tc-v mono break-all">{{ t.url || '—' }}</span></div>
            <div class="tc-kv"><span class="tc-k">URL hash</span><span class="tc-v mono break-all">{{ t.urlHash || '—' }}</span></div>
            <div v-if="t.jobId != null" class="tc-kv"><span class="tc-k">所属批次</span><span class="tc-v">jobId #{{ String(t.jobId) }}</span></div>
            <div class="tc-kv"><span class="tc-k">{{ FETCH_TYPE_LABEL }}</span><span class="tc-v">{{ fetchTypeText(isApi ? 'API' : 'CRAWLER') }}</span></div>
            <div v-if="src?.contentForm" class="tc-kv"><span class="tc-k">正文形态</span><span class="tc-v">{{ contentFormText(src.contentForm) }}</span></div>
            <div v-if="!isApi" class="tc-kv"><span class="tc-k">下载器/通道</span><span class="tc-v mono">{{ t.channelCode || src?.channelCode || '—' }}</span></div>
            <div v-else class="tc-kv">
              <span class="tc-k">接口配置</span>
              <span class="tc-v mono">
                <span v-if="apiCfgLines().length" class="tc-apitxt"><span v-for="(ln, i) in apiCfgLines()" :key="i" class="d-block">{{ ln }}</span></span>
                <span v-else>{{ src?.apiConfigJson || '（API 源未配置 api_config_json）' }}</span>
                <a v-if="src?.apiConfigJson" class="tc-act" href="#" @click.prevent="openPreview('apiConfig')">查看全量 JSON</a>
              </span>
            </div>
          </div>
          <a class="tc-act" href="#" @click.prevent="showTech = !showTech">{{ showTech ? '收起技术详情 ▴' : '查看技术详情 ▾' }}</a>
        </template>
      </div>
    </div>

    <!-- 段2：抓取到的核心内容（载荷快照解析成业务表格；原始 JSON 走全屏预览，技术排查用） -->
    <div class="ind-panel tc-step">
      <div class="ind-panel-h tc-step-t">
        抓取到的核心内容
        <a class="tc-act" href="#" @click.prevent="openPreview('payload')">查看原始 JSON</a>
        <a class="tc-act" href="#" @click.prevent="copyText(prettyJson(t.payloadJson))">复制</a>
      </div>
      <div class="ind-panel-b">
        <template v-if="t.payloadJson">
          <div class="tc-kv"><span class="tc-k">标题</span><span class="tc-v">{{ payloadObj.title || '—' }}</span></div>
          <div class="tc-kv"><span class="tc-k">发布日期</span><span class="tc-v">{{ payloadObj.date || '—' }}</span></div>
          <div class="tc-kv"><span class="tc-k">原文链接</span><span class="tc-v mono break-all">{{ payloadObj.url || '—' }}</span></div>
          <div v-if="payloadObj.category" class="tc-kv"><span class="tc-k">栏目</span><span class="tc-v">{{ payloadObj.category }}</span></div>
          <div v-if="payloadObj.rawFileUrl" class="tc-kv">
            <span class="tc-k">原文文件</span>
            <span class="tc-v">
              <a href="#" @click.prevent="openRawFile()">查看原文文件</a>
              <span class="tc-sub">{{ sizeText(payloadObj.rawSize) }}<template v-if="payloadObj.rawSha256"> · sha256 {{ String(payloadObj.rawSha256).slice(0, 16) }}…</template></span>
            </span>
          </div>
        </template>
        <div v-else class="tc-blob-empty">（本任务暂无抓取内容快照——由后台在抓取阶段写入）</div>
      </div>
    </div>

    <!-- 段3：匹配规则（大白话，替代解析器码） -->
    <div class="ind-panel tc-step">
      <div class="ind-panel-h tc-step-t">匹配规则</div>
      <div class="ind-panel-b">
        <div class="tc-kv"><span class="tc-k">规则</span><span class="tc-v">{{ matchText }} <span v-if="matchOk">✅</span></span></div>
      </div>
    </div>

    <!-- 段5：处理进度时间线（快递轨迹式；替代 attempt/worker 等技术字段） -->
    <div class="ind-panel tc-step">
      <div class="ind-panel-h tc-step-t">处理进度时间线</div>
      <div class="ind-panel-b">
        <div v-for="(s, i) in timeline" :key="i" class="tc-step-line">
          <span class="tc-step-icon">{{ s.icon }}</span>
          <span class="tc-step-txt">{{ s.label }}</span>
          <span v-if="s.time" class="tc-sub">{{ s.time }}</span>
        </div>
        <div v-if="t.message" class="tc-hint">{{ t.message }}</div>
      </div>
    </div>

    <!-- 段6：后续操作（落库/产出，业务化文案；保留成品直达） -->
    <div class="ind-panel tc-step">
      <div class="ind-panel-h tc-step-t">
        后续操作
        <template v-if="productState === 'found'">
          <a class="tc-act" href="#" @click.prevent="findProduct">刷新</a>
        </template>
      </div>
      <div class="ind-panel-b">
        <div class="tc-hint">抓取成功后，将自动归档到外部公告库，等待审核后发布。</div>
        <template v-if="productLoading">
          <div class="tc-hint">正在定位已归档的公告…</div>
        </template>
        <template v-else-if="productState === 'found'">
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
          <div class="tc-hint">{{ t.status === 'SUCCESS' ? '已成功抓取，但尚未在公告库中找到对应记录。' : '尚未完成抓取，暂无可归档内容。' }}</div>
        </template>
        <template v-else-if="productState === 'error'">
          <div class="tc-hint">查询公告库失败：{{ productErr || '接口异常' }}（需 bidding:notice-external:update 权限）</div>
        </template>
        <template v-else>
          <div class="tc-hint">本条抓取成功后，将自动归档到外部公告库，等待审核发布。</div>
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
.tc-hint { color: hsl(var(--muted-foreground)); font-size: 13px; padding: 10px 2px; }
.tc-prod { display: flex; align-items: center; gap: 10px; padding: 7px 10px; border: 1px solid hsl(var(--border)); border-radius: var(--radius); margin-bottom: 6px; background: hsl(var(--card)); font-size: 13px; }
.tc-prod-title { flex: 1; min-width: 0; font-weight: 600; color: hsl(var(--foreground)); }
.tc-prod-tags { display: flex; gap: 4px; flex: none; }
.tc-prod-go { flex: none; font-weight: 600; }
.tc-head { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.tc-ops { margin-left: auto; }
.tc-tech { margin: 4px 0 6px; padding: 8px 10px; background: hsl(var(--muted) / 40%); border: 1px dashed hsl(var(--border)); border-radius: var(--radius); }
.tc-step-line { display: flex; align-items: center; gap: 8px; padding: 3px 0; font-size: 13px; }
.tc-step-icon { flex: none; width: 20px; text-align: center; }
.tc-step-txt { color: hsl(var(--foreground)); }
/* 段容器复用工作台线稿面板（bid-indust.css 全局 .bid-ind .ind-panel 家族）：
 *   边框/纸面/圆角由 .ind-panel 提供，段头底纹与内边距由 .ind-panel-h / .ind-panel-b 提供，
 *   这里只保留段间距与段头排版，避免自有样式与全局面板样式分叉（任务详情与批次详情/抽屉面板同观感）。 */
.tc-step { margin-bottom: 10px; }
.tc-step-t { display: flex; align-items: center; gap: 10px; font-size: 15px; font-weight: 600; color: hsl(var(--foreground)); }
.tc-kv { display: flex; gap: 10px; padding: 3px 0; font-size: 13px; }
.tc-k { flex-shrink: 0; width: 108px; color: hsl(var(--muted-foreground)); }
.tc-v { min-width: 0; }
.tc-sub { color: hsl(var(--muted-foreground)); font-size: 12px; margin-left: 8px; }
.tc-apitxt { display: block; white-space: pre-wrap; word-break: break-all; }
.tc-blob { background: hsl(var(--muted)); color: hsl(var(--foreground)); font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12px; line-height: 1.6; padding: 12px; border-radius: var(--radius); max-height: 300px; overflow: auto; white-space: pre-wrap; word-break: break-all; margin: 0; }
.pv-blob { max-height: none; border-radius: 0; }
.tc-blob-empty { border: 1px dashed hsl(var(--border)); border-radius: var(--radius); padding: 16px; color: hsl(var(--muted-foreground)); font-size: 12.5px; background: hsl(var(--muted) / 40%); }
.tc-act { color: hsl(var(--primary)); font-size: 12px; margin-left: auto; text-decoration: none; }
.tc-act + .tc-act { margin-left: 6px; }
.tc-batch { display: flex; flex-direction: column; gap: 2px; }
/* 同批任务行改用统一任务条样式（../taskbar.css 的 .fp-tbl） */
.pv-head { display: flex; align-items: center; gap: 8px; padding: 10px 12px; border-bottom: 1px solid hsl(var(--border)); position: sticky; top: 0; background: hsl(var(--card)); z-index: 1; }
.pv-sel { width: 260px; }
.d-block { display: block; }
.mono { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12.5px; }
.break-all { word-break: break-all; }
</style>
