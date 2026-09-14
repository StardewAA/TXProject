<script lang="ts" setup>
import type { CrawlExternalApi } from '#/api/bidding/crawlExternal';

import { computed, onMounted, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import {
  Button,
  Descriptions,
  Drawer,
  Dropdown,
  message,
  Modal,
  Pagination,
  RangePicker,
  Tag,
} from 'ant-design-vue';

import {
  fetchCrawlJobPage,
  fetchCrawlSourcePage,
  fetchCrawlTaskPage,
  getCrawlTask,
  retryCrawlTask,
  runCrawlSource,
} from '#/api/bidding/crawlExternal';
import { getRangePickerDefaultProps } from '#/utils/rangePickerProps';

import AttrFilter from '../../components/AttrFilter.vue';
import { CHANNEL_GENERIC_TEXT, contentFormText, FETCH_TYPE_HINT, fetchTypeText, INTERVAL_HINT, intervalText } from '../labels';
import { crawlErrMsg, fmtDt, JOB_STATUS_MAP, jobSummaryText, tagOf, TASK_STATUS_MAP } from '../shared';
import ManualForm from './modules/manual-form.vue';
import SourceDrawer from './modules/source-drawer.vue';
import TaskChainView from './modules/task-chain.vue';

import '../taskbar.css';

/* ---------- 本地模型：手动/测试任务条（真实解析回执；localStorage 留档刷新保留。后端 crawl-parser/test 只返回不回执不落库——测试记录仅前端留档） ---------- */
/** 本地回执留档键（最大保留 60 条；超出丢最旧） */
const MANUAL_STORE_KEY = 'bid-crawl-manual-runs-v1';
const MANUAL_STORE_MAX = 60;
interface ManualRun {
  id: string;
  /** 本地测试标记（非后端 crawl-task.taskType——后端测试不落库） */
  taskType: 'TEST';
  status: string;
  parserCode?: string;
  parserName?: string;
  inputType?: string;
  fileName?: string;
  contentLength?: number;
  durationMs?: number;
  url?: string;
  sourceCode?: string;
  resp?: CrawlExternalApi.ParseTestResp;
  occurredAt: number;
}
const MANUAL_STATUS = {
  SUCCESS: { text: '成功', color: 'green' },
  PARTIAL: { text: '部分产出', color: 'orange' },
  FAILED: { text: '失败', color: 'red' },
} as Record<string, { color: string; text: string; }>;
const manualRuns = ref<ManualRun[]>([]);
/* 测试任务条（与“任务”同款）：时间排序（desc=最新在前默认 / asc=最早在前）+ 本地分页 20/50/100 */
const manualSort = ref<'asc' | 'desc'>('desc');
const manualPage = ref(1);
const manualPageSize = ref(20);
const manualTotal = computed(() => manualRuns.value.length);
function toggleManualSort(): void {
  manualSort.value = manualSort.value === 'desc' ? 'asc' : 'desc';
  manualPage.value = 1;
}
/** 展示行：时间排序 + 分页切片（desc=留档原序最新在前；asc=按 occurredAt 升序） */
const manualShownRows = computed(() => {
  const sorted =
    manualSort.value === 'desc'
      ? manualRuns.value.slice()
      : [...manualRuns.value].sort((a, b) => (a.occurredAt ?? 0) - (b.occurredAt ?? 0));
  const start = (manualPage.value - 1) * manualPageSize.value;
  return sorted.slice(start, start + manualPageSize.value);
});
function manualPageChange(page: number, size: number): void {
  manualPage.value = page;
  manualPageSize.value = size;
}
/** 行留档序号（倒序：最新=#1） */
const manualSeqOf = (pageIdx: number): number => {
  const seq = (manualPage.value - 1) * manualPageSize.value + pageIdx + 1;
  return manualSort.value === 'desc' ? manualTotal.value - seq + 1 : seq;
};
function loadManualStore(): ManualRun[] {
  try {
    const raw = localStorage.getItem(MANUAL_STORE_KEY);
    if (!raw) return [];
    const arr = JSON.parse(raw) as ManualRun[];
    return Array.isArray(arr) ? arr : [];
  } catch {
    return [];
  }
}
function saveManualStore(): void {
  try {
    localStorage.setItem(MANUAL_STORE_KEY, JSON.stringify(manualRuns.value.slice(0, MANUAL_STORE_MAX)));
  } catch {
    /* 容量/隐私模式等写失败不影响本会话内存 */
  }
}
function dropManualRun(id: string): void {
  manualRuns.value = manualRuns.value.filter((r) => r.id !== id);
  saveManualStore();
}
/** 清空本页本地留档（测试仅本地留存，清空即无痕） */
function clearManualRuns(): void {
  manualRuns.value = [];
  manualPage.value = 1;
  saveManualStore();
}

/* ---------- 真实接口状态 ---------- */
const sources = ref<CrawlExternalApi.SourceItem[]>([]);
const sourceOptions = computed(() =>
  sources.value
    .filter((s) => s.sourceCode)
    .map((s) => ({ label: (s.sourceName || s.sourceCode) + '（' + s.sourceCode + '）', value: s.sourceCode! })),
);
/** 顶部选项卡：tasks=任务 / tests=后端测试 / manual=会话测试 / jobs=采集批次 */
const activeTab = ref<'jobs' | 'manual' | 'tasks'>('tasks');
/** 全部源任务总数（后端 total；KPI 与 tab 徽标显示真实总数，不再“首 20 条误导”） */
const taskTotal = ref(0);
const kpis = computed(() => ({
  sched: taskTotal.value,
  manual: manualRuns.value.length,
  jobs: jobs.value.length,
}));
const statusOptions = Object.entries(TASK_STATUS_MAP).map(([value, m]) => ({ label: m.text, value }));


/* ========== 整页三级导航状态（2026-09-10：任务器=信息源；信息源卡墙→该源任务条(筛选/分页/总数)→任务详情整页 上一条/下一条；任务详情不再用右侧抽屉） ========== */
/** home=工作台 / taskDetail=任务详情整页 */
const pageView = ref<'home' | 'taskDetail'>('home');
/** 当前选中信息源（任务器）；空=任务 tab 显示信息源卡墙 */
const curSource = ref<CrawlExternalApi.SourceItem | null>(null);
/** 任务条筛选（后端支持 sourceCode/status/channelCode/url/beginTime/endTime，无 task_type 勿传）
 *  2026-09-14：`range` 接线后端已支持的 beginTime/endTime —— 此前后端能按创建时间筛、界面却没入口，
 *  出问题时无法把死信缩到某个时间窗（"接口有能力、界面不可达"）。格式必须 'YYYY-MM-DD HH:mm:ss'（后端 @DateTimeFormat）。 */
const tFilter = ref<{ channelCode?: string; range?: [string, string]; status?: string; url?: string }>({});
/** 快捷时间段复用全站共享预设（src/utils/rangePickerProps.ts），避免本页自造"最近 7 天"口径 */
const RANGE_PRESETS = getRangePickerDefaultProps().presets;
const tPage = ref(1);
const tPageSize = ref(20);
const tTotal = ref(0);
/** 分页 total 文案（antd show-total 需函数） */
const pagiShowTotal = (total: number, _range: [number, number]) => '共 ' + total + ' 条';
const tRows = ref<CrawlExternalApi.TaskItem[]>([]);
const tRowsLoading = ref(false);
/** 详情当前任务在 tRows 中的游标 */
const detailCursorIdx = ref(-1);
/** 源卡墙任务数角标（sourceCode → 条数） */
const srcCountMap = ref<Record<string, number>>({});
/** 任务条时间排序：desc=最新在前(后端 id 倒序默认) / asc=最早在前(前端对当页升序) */
const tSort = ref<'asc' | 'desc'>('desc');
const jobs = ref<CrawlExternalApi.JobItem[]>([]);

const runBusy = ref(false);
const runCodeBusy = ref<string | undefined>();
const retryBusy = ref<number | string | undefined>();
/** 源 → 上次触发时间戳（前端 120s 节流） */
const lastTriggerAt = ref<Record<string, number>>({});
const sourceOpen = ref(false);

const processOpen = ref(false);
const processManual = ref<ManualRun>();

/* ---------- modal 挂载 ---------- */
const [ManualModal, manualModalApi] = useVbenModal({
  connectedComponent: ManualForm,
  destroyOnClose: true,
});

/* ---------- 数据加载 ---------- */
async function loadSources() {
  try {
    const res = (await fetchCrawlSourcePage({ pageNo: 1, pageSize: 100 })) as unknown as {
      list?: CrawlExternalApi.SourceItem[];
    };
    sources.value = res.list || [];
    void fillSourceCounts(sources.value);
  } catch {
    sources.value = [];
  }
}

/** 全源任务总数（pageSize=1 只读 total） */
async function loadTaskTotalGlobal(): Promise<void> {
  try {
    const res = (await fetchCrawlTaskPage({ pageNo: 1, pageSize: 1 })) as unknown as { total?: number };
    taskTotal.value = typeof res.total === 'number' ? res.total : 0;
  } catch {
    taskTotal.value = 0;
  }
}

async function loadJobs() {
  try {
    const res = (await fetchCrawlJobPage({ pageNo: 1, pageSize: 10 })) as unknown as {
      list?: CrawlExternalApi.JobItem[];
    };
    jobs.value = res.list || [];
  } catch {
    jobs.value = [];
  }
}

function refreshAll() {
  void loadTaskTotalGlobal();
  void loadJobs();
}

onMounted(() => {
  manualRuns.value = loadManualStore();
  void loadSources();
  void loadTaskTotalGlobal();
  void loadJobs();
});

/* ---------- 手动录入 → 生成测试任务条 ---------- */
function onParsed(
  resp: CrawlExternalApi.ParseTestResp,
  input: { fileName?: string; inputType?: string; },
) {
  const ok = resp && (resp.status === 'SUCCESS' || resp.status === 'PARTIAL');
  manualRuns.value.unshift({
    id: 'MT' + String(Date.now()),
    taskType: 'TEST',
    status: !resp ? 'FAILED' : (resp.status || 'FAILED'),
    parserCode: resp?.parserCode,
    parserName: resp?.parserName,
    inputType: input.inputType || resp?.payloadType,
    fileName: input.fileName || resp?.fileName,
    contentLength: resp?.contentLength,
    durationMs: resp?.durationMs,
    url: resp?.url,
    sourceCode: resp?.parserCode === 'MANUAL' ? undefined : 'MANUAL',
    resp,
    occurredAt: Date.now(),
  });
  saveManualStore();
  if (!ok) message.error(resp?.error || '解析未成功');
}

/* ---------- 立即获取（源下拉触发；同步接口 + 120s 节流）---------- */
function onRunMenu(source: CrawlExternalApi.SourceItem) {
  if (!source.sourceCode) return;
  const last = lastTriggerAt.value[source.sourceCode];
  if (last) {
    const wait = Math.ceil(120 - (Date.now() - last) / 1000);
    if (wait > 0) {
      message.warning(
        (source.sourceName || source.sourceCode) + ' 距上次获取不足 120 秒，还需 ' + wait + ' 秒（防封频控）',
      );
      return;
    }
  }
  Modal.confirm({
    title: '立即获取「' + (source.sourceName || source.sourceCode) + '」',
    content:
      '将对该信息源各栏目执行一轮获取（受 120s 频控与页上限约束）。接口同步执行，可能耗时数十秒，完成后任务条进入「任务」列表。',
    okText: '开始获取',
    cancelText: '取消',
    onOk: async () => {
      runCodeBusy.value = source.sourceCode;
      try {
        const r = await runCrawlSource(source.sourceCode!);
        lastTriggerAt.value[source.sourceCode!] = Date.now();
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
            (c.error ? '（' + c.error + '）' : ''),
        );
        Modal.info({
          title: '本轮获取完成',
          content: lines.length ? lines.join('<br/>') : '无栏目产出（检查源 enabled/栏目 URL）',
        });
        void loadTaskTotalGlobal();
        void loadJobs();
        void fillSourceCounts(sources.value);
      } catch (e) {
        message.error(crawlErrMsg(e));
      } finally {
        runCodeBusy.value = undefined;
      }
    },
  });
}

async function retryTask(row: CrawlExternalApi.TaskItem) {
  if (row.id == null) return;
  retryBusy.value = row.id;
  try {
    await retryCrawlTask(row.id);
    message.success('已加入重试队列');
    void loadTaskTotalGlobal();
  } catch (e) {
    message.error(crawlErrMsg(e));
  } finally {
    retryBusy.value = undefined;
  }
}

/* ---------- 过程抽屉 ---------- */
async function openManualProcess(run: ManualRun) {
  processManual.value = run;
  processOpen.value = true;
}
/** 抽屉内删除当前留档（数据 + 本地存储；删除后关闭抽屉） */
function deleteCurrentManual(): void {
  if (!processManual.value) return;
  dropManualRun(processManual.value.id);
  processOpen.value = false;
}
/* ---------- 详情档（任务/批次“两档视图”之舒张层，Drawer 900 就地分层展开） ----------
 * 业务：概览卡/行克制收敛后，点整卡或「详情 ▸」进入详情档铺开分层——
 *       任务详情 = 概要 → 同批任务(该行有 jobId 时) → 过程；批次详情 = 批次概要 → 该批采集任务 N 条(KPI+克制行) → 选中任务过程。
 * 理由：查看过程/重试/批内任务下钻等操作收进详情档，列表不再跳 tab（旧 drillJob 已被批次抽屉取代），
 *       避免跨 tab 上下文丢失；与 source-detail.vue（两档视图样板）口径一致。
 * 契约：fetchCrawlTaskPage({ jobId, pageNo, pageSize }) 见 crawl-batch §三.1 / 04-全链路 §5.7；
 *       getCrawlTask(id) 见 §5.8（失败原因/尝试/worker 回读）。 */
const detailOpen = ref(false);
/** 详情抽屉当前档位：task=任务详情 / job=批次详情（标题随档位切换） */
const detailKind = ref<'job' | 'task'>('task');
const detailTitle = computed(() =>
  detailJob.value ? '批次 #' + String(detailJob.value.id) + ' · ' + (detailJob.value.sourceCode || '未知源') : '批次详情',
);

/* ---------- 任务详情（tasks 卡进入） ---------- */
const detailTask = ref<CrawlExternalApi.TaskItem>();
const detailTaskFull = ref<CrawlExternalApi.TaskItem>();
const detailTaskLoading = ref(false);

/* ---------- 批次详情（jobs 行进入） ---------- */
const detailJob = ref<CrawlExternalApi.JobItem>();
const jobTasks = ref<CrawlExternalApi.TaskItem[]>([]);
const jobTasksLoading = ref(false);
const selJobTask = ref<CrawlExternalApi.TaskItem>();
const selJobTaskFull = ref<CrawlExternalApi.TaskItem>();
const selJobTaskLoading = ref(false);

/* ========== 整页三级导航数据函数（任务器=信息源） ========== */
/** 加载“当前信息源”任务条：筛选(状态/通道/URL) + 分页 + 总数 */
async function loadTaskRows(page = tPage.value, size = tPageSize.value): Promise<void> {
  if (!curSource.value?.sourceCode) {
    tRows.value = [];
    tTotal.value = 0;
    return;
  }
  tRowsLoading.value = true;
  try {
    const params: CrawlExternalApi.TaskPageParams = {
      pageNo: page,
      pageSize: size,
      sourceCode: curSource.value.sourceCode,
      status: tFilter.value.status,
      channelCode: tFilter.value.channelCode,
      url: tFilter.value.url,
      beginTime: tFilter.value.range?.[0],
      endTime: tFilter.value.range?.[1],
    };
    const res = (await fetchCrawlTaskPage(params)) as unknown as { list?: CrawlExternalApi.TaskItem[]; total?: number };
    tRows.value = res.list || [];
    tTotal.value = typeof res.total === 'number' ? res.total : tRows.value.length;
    tPage.value = page;
  } catch (e) {
    message.error(crawlErrMsg(e));
  } finally {
    tRowsLoading.value = false;
  }
}

/** 信息源卡（任务器）→ 进入该源任务列表 */
function enterSourceTasks(src: CrawlExternalApi.SourceItem): void {
  if (!src?.sourceCode) return;
  curSource.value = src;
  tFilter.value = { status: undefined, channelCode: undefined, url: undefined, range: undefined };
  tPage.value = 1;
  void loadTaskRows(1);
}
/** 返回信息源卡墙 */
function backToSourceWall(): void {
  curSource.value = null;
  tRows.value = [];
  tTotal.value = 0;
}
/** 重置全部筛选条件（行式筛选区与时间范围一起清） */
function resetTaskFilter(): void {
  tFilter.value = { status: undefined, channelCode: undefined, url: undefined, range: undefined };
  applyTaskFilter();
}
/** 筛选变化 → 回第 1 页重查 */
function applyTaskFilter(): void {
  tPage.value = 1;
  void loadTaskRows(1);
}

/** 整页详情：上一条 / 下一条（按当前 tRows 游标） */
async function stepTask(dir: -1 | 1): Promise<void> {
  if (!tRows.value.length) return;
  const next = detailCursorIdx.value + dir;
  if (next < 0 || next >= tRows.value.length) {
    message.info(dir === 1 ? '已是本列表最后一条' : '已是本列表第一条');
    return;
  }
  detailCursorIdx.value = next;
  const row = tRows.value[next];
  if (!row || row.id == null) return;
  detailTask.value = row;
  detailTaskFull.value = undefined;
  detailTaskLoading.value = true;
  try {
    detailTaskFull.value = (await getCrawlTask(row.id)) as unknown as CrawlExternalApi.TaskItem;
  } catch {
    detailTaskFull.value = undefined;
  }
  detailTaskLoading.value = false;
}
/** 从整页详情返回任务条列表（保持筛选/分页上下文） */
function backFromDetail(): void {
  pageView.value = 'home';
  activeTab.value = 'tasks';
  void loadTaskRows(tPage.value);
}

/** 单个信息源任务总数（卡片墙角标） */
async function sourceTaskCount(src: CrawlExternalApi.SourceItem): Promise<number> {
  if (!src.sourceCode) return 0;
  try {
    const res = (await fetchCrawlTaskPage({ pageNo: 1, pageSize: 1, sourceCode: src.sourceCode })) as unknown as {
      total?: number;
    };
    return typeof res.total === 'number' ? res.total : 0;
  } catch {
    return 0;
  }
}
/** 时间排序列键：finishedAt || createTime（无则 0） */
function timeKeyOf(t: CrawlExternalApi.TaskItem): number {
  const v = t.finishedAt || t.createTime || 0;
  return typeof v === 'number' ? v : new Date(String(v)).getTime() || 0;
}
/** 展示行：按 tSort 排序（desc=后端倒序原样；asc=本页最早在前——后端暂无全量排序参数，仅当页生效） */
const shownRows = computed(() => {
  if (tSort.value === 'desc') return tRows.value;
  return [...tRows.value].sort((a, b) => timeKeyOf(a) - timeKeyOf(b));
});
/** 切换时间排序 */
function toggleSort(): void {
  tSort.value = tSort.value === 'desc' ? 'asc' : 'desc';
}

/** 源卡墙并发补任务数角标（限前 12 源） */
async function fillSourceCounts(list: CrawlExternalApi.SourceItem[]): Promise<void> {
  const m: Record<string, number> = {};
  await Promise.all(
    list.slice(0, 12).map(async (s) => {
      if (s.sourceCode) m[s.sourceCode] = await sourceTaskCount(s);
    }),
  );
  srcCountMap.value = m;
}

/** 点任务条 → 整页详情（不再开右侧抽屉；记录在 tRows 游标供上一条/下一条） */
async function openTaskDetail(t: CrawlExternalApi.TaskItem): Promise<void> {
  if (!t || t.id == null) return;
  pageView.value = 'taskDetail';
  detailTask.value = t;
  detailTaskFull.value = undefined;
  detailCursorIdx.value = tRows.value.findIndex((r) => r.id != null && String(r.id) === String(t.id));
  detailTaskLoading.value = true;
  try {
    detailTaskFull.value = (await getCrawlTask(t.id)) as unknown as CrawlExternalApi.TaskItem;
  } catch {
    detailTaskFull.value = undefined; // 回读失败不阻断：先以列表行字段呈现
  }
  detailTaskLoading.value = false;
}





async function openJobDetail(j: CrawlExternalApi.JobItem): Promise<void> {
  detailKind.value = 'job';
  detailJob.value = j;
  detailTask.value = undefined;
  detailTaskFull.value = undefined;
  selJobTask.value = undefined;
  selJobTaskFull.value = undefined;
  detailOpen.value = true;
  void loadJobTasks(j);
}

/** 批次详情层2：该批采集任务（pageSize 200；契约见 crawl-batch §三.1） */
async function loadJobTasks(j: CrawlExternalApi.JobItem): Promise<void> {
  if (j.id == null) return;
  jobTasksLoading.value = true;
  try {
    const res = (await fetchCrawlTaskPage({
      jobId: j.id,
      pageNo: 1,
      pageSize: 200,
    })) as unknown as { list?: CrawlExternalApi.TaskItem[] };
    jobTasks.value = res.list || [];
  } catch {
    jobTasks.value = [];
  } finally {
    jobTasksLoading.value = false;
  }
}

/** 批次详情层3：选中任务 → 就地回读该任务过程明细（同抽屉第二区域展开） */
async function pickJobTask(t: CrawlExternalApi.TaskItem): Promise<void> {
  if (selJobTask.value && selJobTask.value.id === t.id) {
    selJobTask.value = undefined; // 再次点击同一条 → 收起
    selJobTaskFull.value = undefined;
    return;
  }
  selJobTask.value = t;
  selJobTaskFull.value = undefined;
  selJobTaskLoading.value = true;
  if (t.id != null) {
    try {
      selJobTaskFull.value = (await getCrawlTask(t.id)) as unknown as CrawlExternalApi.TaskItem;
    } catch {
      selJobTaskFull.value = undefined;
    }
  }
  selJobTaskLoading.value = false;
}

/** 批次内选中任务重试（操作同样收在详情档内） */
async function retryJobTask(): Promise<void> {
  const t = selJobTask.value;
  if (!t || t.id == null) return;
  await retryTask(t);
  if (detailJob.value) void loadJobTasks(detailJob.value);
  try {
    selJobTaskFull.value = (await getCrawlTask(t.id)) as unknown as CrawlExternalApi.TaskItem;
  } catch {
    selJobTaskFull.value = undefined;
  }
}

/* ---------- 展示工具 ---------- */
const esc = (s: unknown): string =>
  String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');

/** 渲染后的内容：优先真实抽取内容兜底排版（契约 run-detail render_html 落地后由后端下发） */
function renderHtmlOf(r: ManualRun): string {
  const x = r.resp;
  const parts: string[] = [];
  const title = (x?.title || '').trim();
  if (title) parts.push('<h1 class="doc-title">' + esc(title) + '</h1>');
  const kv: Array<[string, string]> = [
    ['发布单位', (x?.issuerName || '').trim()],
    ['公告类型', (x?.noticeTypeRaw || '').trim()],
    ['项目编号', (x?.projectKey || '').trim()],
    ['招标项目编号', (x?.tenderProjectKey || '').trim()],
    ['标段编号', (x?.bidSectionCodes || '').trim()],
    ['内容形态', (x?.contentType || '').trim()],
    ['依法必招', x?.legalFlag === 'Y' ? '是' : ''],
    ['原网址', (x?.origUrl || '').trim()],
  ];
  const kvs = kv.filter(([, v]) => v);
  if (kvs.length) {
    parts.push(
      '<table><tbody>' +
        kvs
          .map(
            ([k, v]) =>
              '<tr><td class="doc-kv-key">' +
              esc(k) +
              '</td><td>' +
              esc(v) +
              '</td></tr>',
          )
          .join('') +
        '</tbody></table>',
    );
  }
  const text = (x?.contentTextPreview || '').trim();
  if (text) {
    // 每行 trim 后输出 → 开头空格段落不再造成空白占位/伪渲染
    text
      .split(/\n+/)
      .map((t) => t.trim())
      .filter(Boolean)
      .forEach((t) => parts.push('<p class="doc-para">' + esc(t) + '</p>'));
  }
  if (!parts.length) {
    parts.push('<p class="doc-para">（本次解析无正文产出）</p>');
  }
  return parts.join('');
}

/** 测试任务条“消息/明细”列文本（与任务一致的单行可读格式：出错给出错，否则 解析器 · 输入口径 · 统计） */
function manualMsgText(r: ManualRun): string {
  if (r.resp?.error) return r.resp.error;
  const parser = r.parserName || r.parserCode || '—';
  const input = [r.inputType, r.fileName].filter(Boolean).join(' · ') || '—';
  const len = r.contentLength != null ? r.contentLength + ' 字符' : '—';
  const dur = r.durationMs != null ? r.durationMs + ' ms' : '—';
  return '解析器 ' + parser + ' · ' + input + ' · ' + len + ' · ' + dur;
}
function manualStatusTag(r: ManualRun) {
  const m = MANUAL_STATUS[r.status] || { text: r.status, color: 'default' };
  return m;
}

/** 手动任务条：三段（源 / 提取 / 渲染） */
const kvOf = (r: ManualRun): Array<[string, string]> => {
  const list: Array<[string, string]> = [
    ['解析器', r.parserCode ? (r.parserName ? r.parserName + '（' + r.parserCode + '）' : r.parserCode) : '—'],
    ['输入口径', [r.inputType, r.fileName].filter(Boolean).join(' · ') || '—'],
    ['内容长度', r.contentLength != null ? String(r.contentLength) + ' 字符' : '—'],
    ['解析耗时', r.durationMs != null ? r.durationMs + ' ms' : '—'],
  ];
  return list;
};
const extractedOf = (r: ManualRun): Array<[string, string]> => {
  const x = r.resp;
  const list: Array<[string, string]> = [
    ['标题', (x?.title || '').trim()],
    ['发布单位', (x?.issuerName || '').trim()],
    ['项目编号', (x?.projectKey || '').trim()],
    ['招标项目编号', (x?.tenderProjectKey || '').trim()],
    ['标段编号', (x?.bidSectionCodes || '').trim()],
    ['公告类型', (x?.noticeTypeRaw || '').trim()],
    ['内容形态建议', (x?.contentType || '').trim()],
    ['依法必招', x?.legalFlag === 'Y' ? '是' : x?.legalFlag === 'N' ? '否' : ''],
    ['生效开始', (x?.effectiveFrom || '').trim()],
    ['生效截止', (x?.effectiveTo || '').trim()],
    ['原网址', (x?.origUrl || '').trim()],
  ];
  return list.filter(([, v]) => v);
};

/** 页面形态中文名（后端 pageType：DETAIL/LIST/UNKNOWN）——列表页输入时解析器只出条目、不出公告字段 */
const pageTypeText = (t?: string) =>
  t === 'DETAIL' ? '公告详情' : t === 'LIST' ? '公告列表（出条目）' : t === 'UNKNOWN' ? '未识别' : (t || '—');

/** 附件候选里"链接未绝对化"的条数——>0 表示下载器阶段拿不到绝对地址，属解析规则/信息源配置需校准 */
const incompleteOf = (r: ManualRun) =>
  (r.resp?.attachments || []).filter((att) => att.urlIncomplete).length;
</script>

<!-- ============ 外部信息获取工作台 · 工业风：选项卡 + 数据面板（2026-09-10） ============ -->
<template>
<!--
  外部信息获取工作台 · 平台职能（信息源/任务/测试/批次 总览与执行）
  业务：平台运营配置信息源、立即获取/定时轮询、解析器测试、任务死信重试，并查看信息获取任务与批次行迹；
        目标=把依法公开的招标公告及时、完整采回入库，支撑大厅免费查阅。
  理由：采集是平台级聚合（G 轨）能力，仅平台管理职能可用；间隔≥120s 限频防对源站构成压力；采集对象=依法公开信息。
    业务边界：本页属平台职能（G 轨，无租户语义）——职能角色矩阵（后端执行清单 §7）：爬虫管理/外部公告审核=平台职能，
        勿授企业租户；企业租户只使用发布/档案/跟踪等 T 轨功能。
    依据（法规/政府文件）：10号令 第12/13条（部门规章）——公开公告免费查阅/检索是采集目的；电子办法 第9条（部门规章）公开获取。
    后端契约：爬虫任务工作台-前后端契约-2026-09-07.md；crawl-source/crawl-task/crawl-job/crawl-parser 域（01 接口文档 §2）；
      fetchIntervalSeconds≥120（<120 返 106_046）、run 同步执行返回汇总、retry 仅 FAILED 死信归零 attempt。
-->

  <div class="p-3 bid-ind">
    <!-- ===== 整页详情视图（pageView=taskDetail：点任务条整页打开，可上一条/下一条 + 返回；不再用右侧抽屉） ===== -->
    <div v-if="pageView === 'taskDetail'" class="fp-page">
      <div class="fp-bar">
        <Button size="small" @click="backFromDetail">‹ 返回任务列表</Button>
        <span class="fp-title">任务 #{{ detailTask ? String(detailTask.id) : '—' }}</span>
        <span class="ml-auto fp-nav">
          <Button size="small" :disabled="detailCursorIdx <= 0" @click="stepTask(-1)">▲ 上一条</Button>
          <Button size="small" class="ml-2" :disabled="detailCursorIdx < 0 || detailCursorIdx >= tRows.length - 1" @click="stepTask(1)">下一条 ▼</Button>
          <span v-if="detailCursorIdx >= 0" class="ml-3 text-xs font-semibold text-muted-foreground">本列表 {{ detailCursorIdx + 1 }} / {{ tTotal }}</span>
        </span>
      </div>
      <div v-if="detailTaskLoading" class="fp-loading">详情回读中…</div>
      <TaskChainView v-if="detailTask" :task="detailTask" :show-same-batch="false" :allow-retry="true" />
    </div>

    <!-- ===== home 视图（信息源卡墙 / 任务条 / 测试 / 批次） ===== -->
    <template v-else>
    <!-- 页头 -->
    <div class="ind-stripes"></div>
    <div class="ind-head">
      <div>
        <div class="ind-title">
          外部信息获取工作台
          <span class="ind-mark">PLATFORM</span>
        </div>
        <div class="ind-sub">
          平台级采集（G 轨，外部公告域）。每次「立即获取 / 手动录入 / 定时轮询」生成一条任务条：
          信息源 → 任务（通道/解析器/尝试） → 提取内容 → 渲染结果 → 过程明细；批次为一次完整获取的行迹。
        </div>
      </div>
      <div class="ind-actions">
        <Button type="primary" size="large" @click="manualModalApi.open()">＋ 手动录入（测试任务）</Button>
        <Dropdown>
          <Button size="large" :loading="!!runCodeBusy" :disabled="runBusy || sources.length === 0">立即获取 ▾</Button>
          <template #overlay>
            <div class="rounded-md border border-border bg-card py-1 shadow-lg">
              <div class="px-4 py-2 text-xs font-semibold text-muted-foreground">选择信息源（受 120s 频控）</div>
              <div v-for="src in sources" :key="src.id" class="cursor-pointer px-4 py-2 text-sm font-medium text-foreground hover:bg-accent" @click="onRunMenu(src)">
                {{ src.sourceName || src.sourceCode }}
              </div>
            </div>
          </template>
        </Dropdown>
        <Button size="large" @click="sourceOpen = true">信息源配置</Button>
        <Button size="large" @click="refreshAll">刷新</Button>
      </div>
    </div>

    <!-- KPI -->
    <div class="ind-kpis">
      <div class="ind-kpi k-blue"><div class="k-label">任务</div><div class="k-num">{{ kpis.sched }}</div><div class="k-note">调度轮询 / 立即获取（crawl-task）</div></div>
      <div class="ind-kpi k-green"><div class="k-label">测试</div><div class="k-num">{{ kpis.manual }}</div><div class="k-note">手动录入 / 解析器测试回执（本地留档）</div></div>
      <div class="ind-kpi k-gray"><div class="k-label">采集批次</div><div class="k-num">{{ kpis.jobs }}</div><div class="k-note">crawl-job 只读参照</div></div>
    </div>

    <!-- 选项卡 + 数据面板 -->
    <div class="ind-tabs">
      <button type="button" class="ind-tab" :class="{ on: activeTab === 'tasks' }" @click="activeTab = 'tasks'">任务<span v-if="kpis.sched" class="cnt">{{ kpis.sched }}</span></button>
      <button type="button" class="ind-tab" :class="{ on: activeTab === 'manual' }" @click="activeTab = 'manual'">测试<span v-if="kpis.manual" class="cnt">{{ kpis.manual }}</span></button>
      <button type="button" class="ind-tab" :class="{ on: activeTab === 'jobs' }" @click="activeTab = 'jobs'">采集批次<span v-if="kpis.jobs" class="cnt">{{ kpis.jobs }}</span></button>
    </div>

    <div class="ind-tab-body">
      <!-- ============ ① 任务（任务器=信息源：卡墙→该源任务条列表(筛选/分页/总数)→点任务条整页详情；2026-09-10 重构去右侧抽屉） ============ -->
      <section v-show="activeTab === 'tasks'">
        <div class="fp-guide">操作路径：① 选信息源（任务器）→ ② 看它的任务条（可筛选/分页）→ ③ 点任务条进整页详情（上一条/下一条）。信息源在「信息源配置」维护。</div>

        <!-- A. 未选源：信息源卡墙（任务器列表） -->
        <template v-if="!curSource">
          <div v-if="sources.length === 0" class="ind-empty">暂无信息源 —— 点右上「信息源配置」新增（CRAWLER/API），启用后即可产生任务。</div>
          <div v-else class="fp-wall">
            <div v-for="src in sources" :key="String(src.id)" class="fp-src" @click="enterSourceTasks(src)">
              <div class="fp-src-h">
                <Tag :color="src.fetchType === 'API' ? 'purple' : 'blue'" :title="FETCH_TYPE_HINT">{{ fetchTypeText(src.fetchType) }}</Tag>
                <Tag v-if="src.contentForm" color="cyan" :title="FETCH_TYPE_HINT">{{ contentFormText(src.contentForm) }}</Tag>
                <Tag :color="src.enabled ? 'green' : 'default'">{{ src.enabled ? '启用' : '停用' }}</Tag>
                <span class="fp-src-code">{{ src.sourceCode || '—' }}</span>
              </div>
              <div class="fp-src-name" :title="src.sourceName || ''">{{ src.sourceName || '（未命名）' }}</div>
              <div class="fp-src-meta" :title="INTERVAL_HINT">
                任务 {{ srcCountMap[src.sourceCode || ''] ?? '…' }} 条 · 抓取间隔 {{ intervalText(src.fetchIntervalSeconds) }}
              </div>
              <div class="fp-src-go">查看任务条 ▸</div>
            </div>
          </div>
        </template>

        <!-- B. 已选源：该源任务条（筛选 + 共 N 条 + 分页 + 行列表） -->
        <template v-else>
          <div class="mb-3 flex flex-wrap items-center gap-2">
            <a class="text-sm font-bold text-primary" href="#" @click.prevent="backToSourceWall()">‹ 返回信息源</a>
            <span class="text-sm font-extrabold text-foreground">{{ curSource.sourceName || curSource.sourceCode }}</span>
            <Tag :color="curSource.fetchType === 'API' ? 'purple' : 'blue'" :title="FETCH_TYPE_HINT">{{ fetchTypeText(curSource.fetchType) }}</Tag>
            <Tag v-if="curSource.contentForm" color="cyan" :title="FETCH_TYPE_HINT">{{ contentFormText(curSource.contentForm) }}</Tag>
            <span class="text-xs text-muted-foreground">
              列表解析器 {{ curSource.parserCodeDefault || '—' }} · 正文解析器 {{ curSource.detailParserCode || '—' }}
            </span>
          </div>

          <!-- 筛选行 -->

          <!-- 筛选：前台样式（左侧属性名 + 右侧文字选项），属性名写清在筛什么；此前只有 placeholder 暗示 -->
          <div class="mb-3 rounded-md border border-border bg-card px-3.5 pt-1.5 pb-2.5">
            <AttrFilter v-model="tFilter.status" label="任务状态" :options="statusOptions" @change="applyTaskFilter" />
            <AttrFilter v-model="tFilter.channelCode" label="下载通道" :options="[{ label: CHANNEL_GENERIC_TEXT, value: 'GENERIC' }]" @change="applyTaskFilter" />
            <AttrFilter label="URL 关键字">
              <input v-model="tFilter.url" placeholder="模糊匹配采集地址，回车查询" class="fp-url-input" @keyup.enter="applyTaskFilter" />
            </AttrFilter>
            <AttrFilter label="创建时间">
              <RangePicker
                v-model:value="tFilter.range"
                show-time
                value-format="YYYY-MM-DD HH:mm:ss"
                :presets="RANGE_PRESETS"
                :placeholder="['起（可选）', '止（可选）']"
                style="width: 330px"
                @change="applyTaskFilter"
              />
            </AttrFilter>
            <div class="mt-2 flex items-center gap-2">
              <Button size="small" type="primary" @click="applyTaskFilter">查询</Button>
              <Button size="small" @click="resetTaskFilter">重置</Button>
              <span class="ml-auto text-xs font-semibold text-muted-foreground">共 {{ tTotal }} 条任务 · 每页 {{ tPageSize }}</span>
            </div>
          </div>

          <div v-if="tRowsLoading" class="ind-empty">加载中…</div>
          <div v-else-if="tRows.length === 0" class="ind-empty">该信息源暂无任务条 —— 在该源「立即获取」或启用后按间隔自动轮询产生。</div>
          <template v-else>
            <!-- 表格化查询列表（状态 / 时间(可排序) / 任务# / URL / 消息；行点击 → 整页详情） -->
            <div class="fp-tbl">
              <div class="fp-tr fp-th">
                <span class="fp-td w-status">状态</span>
                <span class="fp-td w-time fp-sortable" @click="toggleSort" :title="tSort === 'desc' ? '当前：最新在前（点此改最早在前）' : '当前：最早在前（点此改最新在前）'">时间 {{ tSort === 'desc' ? '↓' : '↑' }}</span>
                <span class="fp-td w-id">任务 #</span>
                <span class="fp-td flex-1">URL / 来源</span>
                <span class="fp-td w-msg">消息 / 明细</span>
              </div>
              <div v-for="t in shownRows" :key="String(t.id)" class="fp-tr fp-row-click" @click="openTaskDetail(t)">
                <span class="fp-td w-status">
                  <Tag :color="tagOf(TASK_STATUS_MAP, t.status)?.color || 'default'">{{ tagOf(TASK_STATUS_MAP, t.status)?.text || t.status || '—' }}</Tag>
                  <Tag v-if="t.channelCode" color="geekblue">{{ t.channelCode }}</Tag>
                </span>
                <span class="fp-td w-time">{{ fmtDt(t.finishedAt || t.createTime) || '—' }}</span>
                <span class="fp-td w-id">#{{ String(t.id) }}</span>
                <span class="fp-td flex-1 fp-url" :title="t.url || ''">{{ t.url || '（无 URL，见详情）' }}</span>
                <span class="fp-td w-msg fp-msg" :title="t.message || ''">
                  <template v-if="t.message">{{ t.message }}</template>
                  <template v-else>解析器 {{ t.parserCode || '—' }} · 尝试 {{ t.attempt ?? '—' }}<template v-if="t.workerId"> · 执行节点 {{ t.workerId }}</template></template>
                </span>
              </div>
            </div>
            <div class="mt-3 flex justify-end">
              <Pagination v-model:current="tPage" :total="tTotal" :page-size="tPageSize" :show-total="pagiShowTotal" show-size-changer :page-size-options="['20', '50', '100']" @change="loadTaskRows" />
            </div>
          </template>
        </template>
      </section>



      <!-- ============ ② 测试（手动录入 / 解析器测试回执：任务条形式，点击行 → 抽屉看 源/提取/渲染 三段详情；localStorage 留档刷新保留，后端不落库） ============ -->
      <section v-show="activeTab === 'manual'">
        <div class="fp-guide">手动录入（右上按钮）或会话解析回执：任务条与「任务」页签同构 —— 状态 / 时间（可排序）/ # / 标题·输入 / 消息·明细；点行进详情看 源·提取·渲染。留档存于本地（最多 {{ MANUAL_STORE_MAX }} 条，刷新保留；后端 crawl-parser/test 不落库，仅本机可见）。</div>
        <div v-if="!manualRuns.length" class="ind-empty">暂无测试任务条 —— 点右上「手动录入」，粘贴 HTML/JSON 或上传 PDF/HTML/JSON 文件，即刻生成一条含 源·提取·渲染 的任务条</div>
        <template v-else>
          <div class="fp-tbl">
            <div class="fp-tr fp-th">
              <span class="fp-td w-status">状态</span>
              <span class="fp-td w-time fp-sortable" @click="toggleManualSort" :title="manualSort === 'desc' ? '当前：最新在前（点此改最早在前）' : '当前：最早在前（点此改最新在前）'">时间 {{ manualSort === 'desc' ? '↓' : '↑' }}</span>
              <span class="fp-td w-id">留档 #</span>
              <span class="fp-td flex-1">标题 / 输入</span>
              <span class="fp-td w-msg">消息 / 明细</span>
            </div>
            <div v-for="(r, i) in manualShownRows" :key="r.id" class="fp-tr fp-row-click" @click="openManualProcess(r)">
              <span class="fp-td w-status">
                <Tag color="purple">测试</Tag>
                <Tag :color="manualStatusTag(r).color">{{ manualStatusTag(r).text }}</Tag>
              </span>
              <span class="fp-td w-time">{{ fmtDt(r.occurredAt) || '—' }}</span>
              <span class="fp-td w-id" title="本地留档序号（测试不落库）">#{{ manualSeqOf(i) }}</span>
              <span class="fp-td flex-1 fp-url" :title="r.url || ''">{{ (r.resp?.title || '').trim() || r.url || '（无标题产出，见详情）' }}</span>
              <span class="fp-td w-msg fp-msg" :title="manualMsgText(r)">
                {{ manualMsgText(r) }}
              </span>
            </div>
          </div>
          <div class="mt-3 flex items-center justify-between gap-3 flex-wrap">
            <span class="text-xs font-medium text-muted-foreground">本地留档最多 {{ MANUAL_STORE_MAX }} 条（超出丢最旧）<a class="ml-3 text-primary font-bold" href="#" @click.prevent="clearManualRuns()">清空留档</a></span>
            <Pagination v-model:current="manualPage" :total="manualTotal" :page-size="manualPageSize" :show-total="pagiShowTotal" show-size-changer :page-size-options="['20', '50', '100']" @change="manualPageChange" />
          </div>
        </template>
      </section>

      <!-- ============ ③ 采集批次（统一任务条 fp-tbl：状态/时间/批次#/源·触发/摘要；行点击进批次详情层） ============ -->
      <section v-show="activeTab === 'jobs'">
        <div class="ind-panel">
          <div class="ind-panel-h"><span class="ind-panel-t">采集批次</span><span class="ind-panel-meta">一次完整获取 = 一个批次（crawl-job，只读参照；批内任务在详情层内按 jobId 拉取）</span></div>
          <div class="ind-panel-b">
            <div v-if="!jobs.length" class="ind-empty">暂无批次记录</div>
            <div v-else class="fp-tbl">
              <div class="fp-tr fp-th">
                <span class="fp-td w-status">状态</span>
                <span class="fp-td w-time">时间</span>
                <span class="fp-td w-id">批次 #</span>
                <span class="fp-td flex-1">源 / 触发</span>
                <span class="fp-td w-msg">摘要</span>
              </div>
              <div v-for="j in jobs" :key="String(j.id)" class="fp-tr fp-row-click" @click="openJobDetail(j)">
                <span class="fp-td w-status">
                  <Tag :color="tagOf(JOB_STATUS_MAP, j.status)?.color || 'default'">{{ tagOf(JOB_STATUS_MAP, j.status)?.text || j.status || '—' }}</Tag>
                </span>
                <span class="fp-td w-time">{{ fmtDt(j.finishedAt || j.createTime) || '—' }}</span>
                <span class="fp-td w-id">#{{ String(j.id) }}</span>
                <span class="fp-td flex-1 fp-url">{{ j.sourceCode || '—' }}<template v-if="j.triggerType"> · {{ j.triggerType }}</template></span>
                <span class="fp-td w-msg fp-msg" :title="j.summaryJson ? jobSummaryText(j.summaryJson) : ''">{{ j.summaryJson ? jobSummaryText(j.summaryJson) : '—' }}</span>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>

    <!-- 会话测试解析详情抽屉（测试任务条行点击 → 三段：源 · 提取 · 渲染）。
         任务详情已统一为整页视图（pageView=taskDetail，见上方 fp-page），此处不再保留任务过程分支。 -->
    <Drawer v-model:open="processOpen" class="bid-ind" :width="980" title="会话测试 · 解析详情">
      <!-- 会话测试解析详情（manual 任务条行点击 → 三段：源 · 提取 · 渲染 + 观测；含本会话本地留档 + 后端 TEST 留痕） -->
      <div v-if="processManual" class="space-y-3">
        <div class="ind-panel">
          <div class="ind-panel-h">
            <span class="ind-panel-t">解析回执</span>
            <span class="ind-panel-meta">parserCode={{ processManual.parserCode || '—' }} · {{ fmtDt(processManual.occurredAt) }}</span>
          </div>
          <div class="ind-panel-b">
            <Descriptions :column="1" size="small" bordered>
              <Descriptions.Item label="解析器">{{ processManual.parserName ? `${processManual.parserName }（${ processManual.parserCode }）` : processManual.parserCode || '—' }}</Descriptions.Item>
              <Descriptions.Item label="输入口径">{{ [processManual.inputType, processManual.fileName].filter(Boolean).join(' · ') || '—' }}</Descriptions.Item>
              <Descriptions.Item label="来源 URL">{{ processManual.url || '—' }}</Descriptions.Item>
              <Descriptions.Item label="耗时">{{ processManual.durationMs != null ? `${processManual.durationMs } ms` : '—' }}</Descriptions.Item>
              <Descriptions.Item label="解析状态"><Tag :color="manualStatusTag(processManual).color">{{ manualStatusTag(processManual).text }}</Tag></Descriptions.Item>
              <Descriptions.Item v-if="processManual.resp?.error" label="失败原因">{{ processManual.resp.error }}</Descriptions.Item>
            </Descriptions>
            <div class="mt-2 flex justify-end">
              <Button size="small" type="primary" ghost @click="deleteCurrentManual()">删除本条留档</Button>
            </div>
          </div>
        </div>

        <div class="grid grid-cols-3 max-lg:grid-cols-1 gap-3">
          <!-- ① 源 -->
          <div class="p-4 border border-dashed border-border rounded">
            <div class="ind-sec-t"><span>①</span> 源 · 输入</div>
            <div class="ind-kv" style="grid-template-columns: 1fr;">
              <div class="kv"><span class="k">输入口径</span><span class="v">{{ processManual.inputType || '—' }}{{ processManual.fileName ? ` · ${ processManual.fileName}` : '' }}</span></div>
              <div v-if="processManual.contentLength != null" class="kv"><span class="k">内容长度</span><span class="v">{{ processManual.contentLength }} 字符</span></div>
              <div v-if="processManual.resp?.pageType" class="kv">
                <span class="k">页面形态</span><span class="v">{{ pageTypeText(processManual.resp.pageType) }}</span>
              </div>
              <div v-if="processManual.resp?.contentHtmlLength != null" class="kv">
                <span class="k">HTML 原文长度</span>
                <span class="v">{{ processManual.resp.contentHtmlLength }} 字符（仅下发长度，正文按预览核对）</span>
              </div>
              <div v-if="processManual.url" class="kv"><span class="k">URL</span><span class="v truncate" :title="processManual.url">{{ processManual.url }}</span></div>
            </div>
          </div>
          <!-- ② 提取内容 -->
          <div class="p-4 border border-dashed border-border rounded">
            <div class="ind-sec-t"><span>②</span> 提取内容</div>
            <div class="ind-kv" style="grid-template-columns: 1fr;">
              <div v-for="[k, v] in kvOf(processManual)" :key="`k${ k}`" class="kv"><span class="k">{{ k }}</span><span class="v truncate" :title="v">{{ v }}</span></div>
            </div>
            <div v-if="extractedOf(processManual).length" class="mt-3 border-t border-dashed border-border pt-2">
              <div class="ind-sec-t"><span>字段</span> 抽取字段</div>
              <div class="ind-kv" style="grid-template-columns: 1fr;">
                <div v-for="[k, v] in extractedOf(processManual)" :key="`e${ k}`" class="kv"><span class="k">{{ k }}</span><span class="v truncate" :title="v">{{ v }}</span></div>
              </div>
            </div>
            <div v-if="processManual.resp?.warnings?.length" class="mt-2">
              <Tag v-for="(w, i) in processManual.resp.warnings" :key="i" color="orange" class="mr-1 mt-0.5">{{ w }}</Tag>
            </div>
          </div>
          <!-- ③ 渲染内容 -->
          <div class="p-4 border border-dashed border-border rounded">
            <div class="ind-sec-t"><span>③</span> 渲染后的内容</div>
            <div class="ind-doc" v-html="renderHtmlOf(processManual)"></div>
          </div>
        </div>

        <div class="ind-panel">
          <div class="ind-panel-h"><span class="ind-panel-t">过程步骤</span></div>
          <div class="ind-panel-b">
            <div class="mt-1 space-y-1 text-sm font-medium text-foreground">
              <div>1. 注入原始输入（HTML/JSON/文件）→ 成功（手动录入无网络下载）</div>
              <div>2. 解析（parserCode={{ processManual.parserCode || '—' }}）→ {{ processManual.status === 'FAILED' ? '失败' : '成功' }}</div>
              <div>3. 渲染（契约 render_html 未落地 → 前端兜底公文化排版 builtBy=FRONTEND_FALLBACK）</div>
            </div>
            <div v-if="processManual.resp?.meta && Object.keys(processManual.resp.meta).length" class="mt-3">
              <div class="ind-sec-t"><span>OBS</span> 解析观测（meta）</div>
              <div class="mt-1 space-y-1 text-sm font-medium text-foreground">
                <div v-for="(mv, mk) in processManual.resp.meta" :key="String(mk)" class="flex gap-2">
                  <span class="w-48 shrink-0 truncate text-muted-foreground" :title="String(mk)">{{ mk }}</span>
                  <span class="truncate">{{ mv }}</span>
                </div>
              </div>
            </div>
            <div v-if="processManual.resp?.warnings?.length" class="mt-3">
              <div class="ind-sec-t"><span>WARN</span> 告警（warnings）</div>
              <div class="mt-1 space-y-1 text-sm font-medium text-amber-600 dark:text-amber-400">
                <div v-for="(w, i) in processManual.resp.warnings" :key="i">• {{ w }}</div>
              </div>
            </div>
            <!-- 附件候选：解析出的附件清单（下载器阶段会下载后回传 ingest）。
                 urlIncomplete=true 表示相对链接未绝对化——下载会失败，属"该补源 baseUrl / 调解析规则"的直接信号，
                 故单列标记而不是只列文件名。 -->
            <div v-if="processManual.resp?.attachments?.length" class="mt-3">
              <div class="ind-sec-t">
                <span>ATTACH</span> 附件候选（{{ processManual.resp.attachments.length }} 个<span
                  v-if="incompleteOf(processManual) > 0"
                  class="ml-1 text-amber-600 dark:text-amber-400"
                  >· {{ incompleteOf(processManual) }} 个链接未绝对化</span>）
              </div>
              <div class="mt-1 space-y-1 text-sm font-medium text-foreground">
                <div
                  v-for="(att, i) in processManual.resp.attachments"
                  :key="i"
                  class="flex items-start gap-2"
                >
                  <span class="shrink-0" :class="att.urlIncomplete ? 'text-amber-600 dark:text-amber-400' : 'text-muted-foreground'">
                    {{ att.urlIncomplete ? '⚠' : '•' }}
                  </span>
                  <span class="min-w-0">
                    <span>{{ att.name || '（未命名附件）' }}</span>
                    <span v-if="att.url" class="ml-2 break-all text-xs text-muted-foreground" :title="att.url">{{ att.url }}</span>
                    <span v-if="att.urlIncomplete" class="ml-2 text-xs text-amber-600 dark:text-amber-400">
                      （相对链接，缺来源 URL → 下载阶段无法定位；请在信息源配置里补 baseUrl 或改用带 url 的录入）
                    </span>
                  </span>
                </div>
              </div>
            </div>
            <div v-if="processManual.resp?.entries?.length" class="mt-3">
              <div class="ind-sec-t">
                <span>LIST</span> 列表条目（{{ processManual.resp.entries.length }} 条<span
                  v-if="processManual.resp.entries.length > 10"
                  >，展示前 10</span>）
              </div>
              <div class="mt-1 space-y-1 text-sm font-medium text-foreground">
                <div v-for="(en, i) in processManual.resp.entries.slice(0, 10)" :key="i" class="flex items-start gap-2">
                  <span class="shrink-0 text-muted-foreground">•</span>
                  <span class="min-w-0">
                    <span>{{ en.title || '（无标题）' }}</span>
                    <span v-if="en.date" class="ml-2 text-xs text-muted-foreground">{{ en.date }}</span>
                    <span v-if="en.category" class="ml-2 text-xs text-muted-foreground">[{{ en.category }}]</span>
                    <span v-if="en.url" class="ml-2 break-all text-xs text-muted-foreground" :title="en.url">{{ en.url }}</span>
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="text-sm font-semibold text-muted-foreground">加载中…</div>
    </Drawer>

    <!-- 详情档抽屉（任务/批次两档视图之“舒张”层：宽 900 就地渲染展开；打开方=①任务卡详情 / ④批次行详情） -->
    <Drawer v-model:open="detailOpen" class="bid-ind" :width="900" :title="detailTitle">
      <!-- ===== 批次详情：层1 批次概要 → 层2 该批爬虫任务 N 条（KPI 卡+克制行） → 层3 选中任务过程（同抽屉第二区域） ===== -->
      <div v-if="detailKind === 'job' && detailJob" class="space-y-3">
        <div class="ind-panel">
          <div class="ind-panel-h"><span class="ind-panel-t">批次概要</span><span class="ind-panel-meta">crawl-job 只读参照</span></div>
          <div class="ind-panel-b">
            <Descriptions :column="2" size="small" bordered>
              <Descriptions.Item label="状态"><Tag :color="tagOf(JOB_STATUS_MAP, detailJob.status)?.color || 'default'">{{ tagOf(JOB_STATUS_MAP, detailJob.status)?.text || detailJob.status || '—' }}</Tag></Descriptions.Item>
              <Descriptions.Item label="源">{{ detailJob.sourceCode || '—' }}</Descriptions.Item>
              <Descriptions.Item label="触发">{{ detailJob.triggerType || '—' }}</Descriptions.Item>
              <Descriptions.Item label="采集范围">{{ detailJob.scope || '—' }}</Descriptions.Item>
              <Descriptions.Item label="创建">{{ fmtDt(detailJob.createTime) }}</Descriptions.Item>
              <Descriptions.Item label="完成">{{ fmtDt(detailJob.finishedAt) }}</Descriptions.Item>
              <Descriptions.Item label="汇总摘要" :span="2"><span class="break-all" :title="detailJob.summaryJson || ''">{{ jobSummaryText(detailJob.summaryJson) || '—' }}</span></Descriptions.Item>
            </Descriptions>
          </div>
        </div>

        <div class="ind-panel">
          <div class="ind-panel-h">
            <span class="ind-panel-t">该批爬虫任务</span>
            <span class="ind-panel-meta">jobId #{{ String(detailJob.id) }} · 任务条同工作台口径（状态/时间/任务#/URL/消息），点击行就地展开过程</span>
          </div>
          <div class="ind-panel-b">
            <div class="ind-kpis" style="margin: 0 0 6px; max-width: 340px;">
              <div class="ind-kpi k-blue">
                <div class="k-label">该批爬虫任务</div>
                <div class="k-num">{{ jobTasks.length }}<span class="k-unit"> 条</span></div>
                <div class="k-note">fetchCrawlTaskPage({ jobId, pageNo: 1, pageSize: 200 })</div>
              </div>
            </div>
            <div v-if="jobTasksLoading" class="text-xs font-semibold text-amber-600 dark:text-amber-400">任务加载中…</div>
            <div v-else-if="!jobTasks.length" class="text-sm font-medium text-muted-foreground">该批暂无任务行（批次落库见 crawl-batch 契约）</div>
            <div v-else class="fp-tbl">
              <div class="fp-tr fp-th">
                <span class="fp-td w-status">状态</span>
                <span class="fp-td w-time">时间</span>
                <span class="fp-td w-id">任务 #</span>
                <span class="fp-td flex-1">URL / 来源</span>
                <span class="fp-td w-msg">消息 / 明细</span>
              </div>
              <div v-for="t in jobTasks" :key="String(t.id)" class="fp-tr fp-row-click" :class="{ 'fp-row-on': selJobTask && selJobTask.id === t.id }" @click="pickJobTask(t)">
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
        </div>

        <!-- 层3：选中任务 → 该任务过程（getCrawlTask 详情 Descriptions，放同抽屉内第二区域展开） -->
        <div v-if="selJobTask" class="ind-panel">
          <div class="ind-panel-h">
            <span class="ind-panel-t">任务过程</span>
            <span class="ind-panel-meta">任务 #{{ String(selJobTask.id) }} · 回读 getCrawlTask 明细</span>
            <span class="ml-auto">
              <Button v-if="(selJobTaskFull?.status || selJobTask.status) === 'FAILED'" danger size="small" :loading="retryBusy === selJobTask.id" @click="retryJobTask">重试（归零 attempt）</Button>
            </span>
          </div>
          <div class="ind-panel-b">
            <div v-if="selJobTaskLoading" class="mb-2 text-xs font-semibold text-amber-600 dark:text-amber-400">详情回读中…</div>
            <Descriptions :column="2" size="small" bordered>
              <Descriptions.Item label="状态">{{ tagOf(TASK_STATUS_MAP, selJobTaskFull?.status || selJobTask.status)?.text || selJobTask.status || '—' }}</Descriptions.Item>
              <Descriptions.Item label="源">{{ selJobTaskFull?.sourceCode || selJobTask.sourceCode || '—' }}</Descriptions.Item>
              <Descriptions.Item label="URL" :span="2"><span class="break-all">{{ selJobTaskFull?.url || selJobTask.url || '—' }}</span></Descriptions.Item>
              <Descriptions.Item label="通道 / 解析器">{{ selJobTaskFull?.channelCode || selJobTask.channelCode || '—' }} / {{ selJobTaskFull?.parserCode || selJobTask.parserCode || '—' }}</Descriptions.Item>
              <Descriptions.Item label="尝试">{{ selJobTaskFull?.attempt ?? selJobTask.attempt ?? '—' }}</Descriptions.Item>
              <Descriptions.Item label="执行节点">{{ selJobTaskFull?.workerId || selJobTask.workerId || '—' }}</Descriptions.Item>
              <Descriptions.Item label="开始 / 完成">{{ fmtDt(selJobTaskFull?.startedAt || selJobTask.startedAt) }} / {{ fmtDt(selJobTaskFull?.finishedAt || selJobTask.finishedAt) }}</Descriptions.Item>
              <Descriptions.Item label="消息 / 错误" :span="2">{{ selJobTaskFull?.message || selJobTask.message || '—' }}</Descriptions.Item>
            </Descriptions>
            <div class="mt-3 text-xs font-medium text-amber-600 dark:text-amber-400">下载器/解析器/中间文件与逐步时间线将在 TaskRunVO 落库后完整呈现（steps_json + logs + raw/extract/render）。</div>
          </div>
        </div>
      </div>
    </Drawer>

    <ManualModal :source-options="sourceOptions" @parsed="onParsed" />
    <SourceDrawer v-model:open="sourceOpen" @changed="() => { void loadSources(); void loadTaskTotalGlobal(); void loadJobs(); }" />
    </template>
  </div>
</template>

<style scoped>
/* 说明：.ind-*（面板/选项卡/空态等）已收敛到全局皮肤 bid-indust.css，本页只保留 fp-* 局部样式 */
.fp-page { min-height: calc(100vh - 140px); padding: 14px; background: hsl(var(--card)); border: 1px solid hsl(var(--border)); border-radius: var(--radius); }
.fp-bar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 14px; padding: 10px 12px; background: hsl(var(--card)); border: 1px solid hsl(var(--border)); border-radius: var(--radius); }
.fp-title { font-size: 16px; font-weight: 600; color: hsl(var(--foreground)); }
.fp-nav { display: flex; align-items: center; }
.fp-loading { margin-bottom: 10px; font-size: 13px; font-weight: 500; color: hsl(var(--primary)); }
.fp-guide { margin-bottom: 12px; padding: 8px 12px; font-size: 12.5px; line-height: 1.7; color: hsl(var(--muted-foreground)); background: hsl(var(--muted) / 40%); border: 1px solid hsl(var(--border)); border-left: 3px solid hsl(var(--primary)); border-radius: var(--radius); }
.fp-wall { display: grid; grid-template-columns: repeat(auto-fill, minmax(252px, 1fr)); gap: 12px; }
.fp-src { display: flex; flex-direction: column; gap: 8px; padding: 12px 14px; background: hsl(var(--card)); border: 1px solid hsl(var(--border)); border-radius: var(--radius); cursor: pointer; transition: border-color .15s, box-shadow .15s, transform .12s; }
.fp-src:hover { border-color: hsl(var(--primary) / 50%); box-shadow: 0 4px 12px rgb(0 0 0 / 8%); transform: translateY(-1px); }
.fp-src-h { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.fp-src-code { margin-left: auto; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; font-weight: 600; color: hsl(var(--muted-foreground)); }
.fp-src-name { font-size: 15px; font-weight: 600; color: hsl(var(--foreground)); line-height: 1.4; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fp-src-meta { font-size: 12.5px; font-weight: 400; color: hsl(var(--muted-foreground)); }
.fp-src-go { margin-top: auto; align-self: flex-end; font-size: 13px; font-weight: 500; color: hsl(var(--primary)); }
.fp-row { display: block; }
.fp-row-id { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; font-weight: 600; color: hsl(var(--muted-foreground)); margin-right: 2px; }
.fp-row-sub { font-size: 13px; font-weight: 400; color: hsl(var(--muted-foreground)); }
.fp-url-input { height: 30px; padding: 0 10px; font-size: 13px; color: hsl(var(--foreground)); background: hsl(var(--card)); border: 1px solid hsl(var(--border)); border-radius: 6px; outline: none; transition: border-color .15s; }
.fp-url-input:focus { border-color: hsl(var(--primary)); }
.fp-url-input::placeholder { color: hsl(var(--muted-foreground)); }
/* 表格化任务条 .fp-tbl / .fp-tr / .fp-td 等样式已抽到 ../taskbar.css，多组件共用（任务页签/测试页签/批次抽屉/数据链同批/批次详情/信息源详情），勿在本文件重复定义。 */
</style>

