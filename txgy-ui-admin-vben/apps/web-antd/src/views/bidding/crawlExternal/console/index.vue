<script lang="ts" setup>
import type { CrawlExternalApi } from '#/api/bidding/crawlExternal';

import { computed, onMounted, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import {
  Button,
  Descriptions,
  Drawer,
  Dropdown,
  Empty,
  message,
  Modal,
  Select,
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

import { fmtDt, JOB_STATUS_MAP, jobSummaryText, tagOf, TASK_STATUS_MAP } from '../shared';
import ManualForm from './modules/manual-form.vue';
import SourceDrawer from './modules/source-drawer.vue';
import TaskChainView from './modules/task-chain.vue';

/* ---------- 本地模型：手动/测试任务条（本次会话，真实解析回执） ---------- */
interface ManualRun {
  id: string;
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
} as Record<string, { text: string; color: string }>;
const manualRuns = ref<ManualRun[]>([]);

/* ---------- 真实接口状态 ---------- */
const sources = ref<CrawlExternalApi.SourceItem[]>([]);
const sourceOptions = computed(() =>
  sources.value
    .filter((s) => s.sourceCode)
    .map((s) => ({ label: (s.sourceName || s.sourceCode) + '（' + s.sourceCode + '）', value: s.sourceCode! })),
);
/** 顶部选项卡：tasks=任务 / tests=后端测试 / manual=会话测试 / jobs=采集批次 */
const activeTab = ref<'tasks' | 'tests' | 'manual' | 'jobs'>('tasks');
/** 全部源任务总数（后端 total；KPI 与 tab 徽标显示真实总数，不再“首 20 条误导”） */
const taskTotal = ref(0);
const kpis = computed(() => ({
  sched: taskTotal.value,
  tests: backendTests.value.length,
  manual: manualRuns.value.length,
  jobs: jobs.value.length,
}));
const filterSource = ref<string | undefined>(undefined);
const filterStatus = ref<string | undefined>(undefined);
const statusOptions = Object.entries(TASK_STATUS_MAP).map(([value, m]) => ({ label: m.text, value }));

const schedTasks = ref<CrawlExternalApi.TaskItem[]>([]);
const schedPage = ref(1);
const schedEnd = ref(false);
const schedLoading = ref(false);

/* ========== 整页三级导航状态（2026-09-10：任务器=信息源；信息源卡墙→该源任务条(筛选/分页/总数)→任务详情整页 上一条/下一条；任务详情不再用右侧抽屉） ========== */
/** home=工作台 / taskDetail=任务详情整页 */
const pageView = ref<'home' | 'taskDetail'>('home');
/** 当前选中信息源（任务器）；空=任务 tab 显示信息源卡墙 */
const curSource = ref<CrawlExternalApi.SourceItem | null>(null);
/** 任务条筛选（后端支持 sourceCode/status/channelCode/url/beginTime/endTime，无 task_type 勿传） */
const tFilter = ref<{ status?: string; channelCode?: string; url?: string }>({});
const tPage = ref(1);
const tPageSize = ref(20);
const tTotal = ref(0);
const tRows = ref<CrawlExternalApi.TaskItem[]>([]);
const tRowsLoading = ref(false);
/** 详情当前任务在 tRows 中的游标 */
const detailCursorIdx = ref(-1);
/** 源卡墙任务数角标（sourceCode → 条数） */
const srcCountMap = ref<Record<string, number>>({});
const backendTests = ref<CrawlExternalApi.TaskItem[]>([]);
const jobs = ref<CrawlExternalApi.JobItem[]>([]);

const runBusy = ref(false);
const runCodeBusy = ref<string | undefined>();
const retryBusy = ref<number | string | undefined>();
/** 源 → 上次触发时间戳（前端 120s 节流） */
const lastTriggerAt = ref<Record<string, number>>({});
const sourceOpen = ref(false);

const processOpen = ref(false);
const processManual = ref<ManualRun>();
const processTask = ref<CrawlExternalApi.TaskItem>();
const processDetail = ref<CrawlExternalApi.TaskItem>();

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

async function loadSched(reset = false) {
  if (reset) {
    schedPage.value = 1;
    schedTasks.value = [];
    schedEnd.value = false;
  }
  if (schedEnd.value || schedLoading.value) return;
  schedLoading.value = true;
  try {
    const params: CrawlExternalApi.TaskPageParams = {
      pageNo: schedPage.value,
      pageSize: 20,
      sourceCode: filterSource.value,
      status: filterStatus.value,
      taskType: 'SCHEDULE',
    };
    const res = (await fetchCrawlTaskPage(params)) as unknown as { list?: CrawlExternalApi.TaskItem[] };
    const list = res.list || [];
    schedTasks.value.push(...list);
    if (list.length < 20) schedEnd.value = true;
    else schedPage.value += 1;
  } catch (e) {
    message.error(e instanceof Error ? e.message : String(e));
  } finally {
    schedLoading.value = false;
  }
}

async function loadBackendTests() {
  try {
    const res = (await fetchCrawlTaskPage({
      pageNo: 1,
      pageSize: 100,
      taskType: 'TEST',
    })) as unknown as { list?: CrawlExternalApi.TaskItem[] };
    backendTests.value = res.list || [];
  } catch {
    backendTests.value = [];
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
  void loadSched(true);
  void loadBackendTests();
  void loadJobs();
}

onMounted(() => {
  void loadSources();
  void loadTaskTotalGlobal();
  void loadSched(true);
  void loadBackendTests();
  void loadJobs();
});

/* ---------- 手动录入 → 生成测试任务条 ---------- */
function onParsed(
  resp: CrawlExternalApi.ParseTestResp,
  input: { inputType?: string; fileName?: string },
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
  if (!ok) message.error(resp?.error || '解析未成功');
  void loadBackendTests();
}

/* ---------- 立即爬取（源下拉触发；同步接口 + 120s 节流） ---------- */
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
          ((r as { categories?: Array<{ name?: string; fetched?: number; ingested?: number; failed?: number; error?: string }> })
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
        void loadSched(true);
      } catch (e) {
        message.error(e instanceof Error ? e.message : String(e));
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
    void loadSched(true);
  } catch (e) {
    message.error(e instanceof Error ? e.message : String(e));
  } finally {
    retryBusy.value = undefined;
  }
}

/* ---------- 过程抽屉 ---------- */
async function openManualProcess(run: ManualRun) {
  processManual.value = run;
  processTask.value = undefined;
  processOpen.value = true;
}
async function openTaskProcess(row: CrawlExternalApi.TaskItem) {
  processManual.value = undefined;
  processTask.value = row;
  processDetail.value = undefined;
  processOpen.value = true;
  if (row.id != null) {
    try {
      processDetail.value = await getCrawlTask(row.id);
    } catch {
      processDetail.value = undefined;
    }
  }
}

/* ---------- 详情档（任务/批次“两档视图”之舒张层，Drawer 900 就地分层，不嵌套 Drawer） ----------
 * 业务：概览卡/行克制收敛后，点整卡或「详情 ▸」进入详情档铺开分层——
 *       任务详情 = 概要 → 同批任务(该行有 jobId 时) → 过程；批次详情 = 批次概要 → 该批爬虫任务 N 条(KPI+克制行) → 选中任务过程。
 * 理由：查看过程/重试/批内任务下钻等操作收进详情档，列表不再跳 tab（旧 drillJob 已被批次抽屉取代），
 *       避免跨 tab 上下文丢失；与 source-detail.vue（两档视图样板）口径一致。
 * 契约：fetchCrawlTaskPage({ jobId, pageNo, pageSize }) 见 crawl-batch §三.1 / 04-全链路 §5.7；
 *       getCrawlTask(id) 见 §5.8（失败原因/尝试/worker 回读）。 */
const detailOpen = ref(false);
/** 详情抽屉当前档位：task=任务详情 / job=批次详情（标题随档位切换） */
const detailKind = ref<'task' | 'job'>('task');
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
    };
    const res = (await fetchCrawlTaskPage(params)) as unknown as { list?: CrawlExternalApi.TaskItem[]; total?: number };
    tRows.value = res.list || [];
    tTotal.value = typeof res.total === 'number' ? res.total : tRows.value.length;
    tPage.value = page;
  } catch (e) {
    message.error(e instanceof Error ? e.message : String(e));
  } finally {
    tRowsLoading.value = false;
  }
}

/** 信息源卡（任务器）→ 进入该源任务列表 */
function enterSourceTasks(src: CrawlExternalApi.SourceItem): void {
  if (!src?.sourceCode) return;
  curSource.value = src;
  tFilter.value = { status: undefined, channelCode: undefined, url: undefined };
  tPage.value = 1;
  void loadTaskRows(1);
}
/** 返回信息源卡墙 */
function backToSourceWall(): void {
  curSource.value = null;
  tRows.value = [];
  tTotal.value = 0;
}
/** 筛选变化 → 回第 1 页重查 */
function applyTaskFilter(): void {
  tPage.value = 1;
  void loadTaskRows(1);
}

/** 整页详情：上一条 / 下一条（按当前 tRows 游标） */
async function stepTask(dir: 1 | -1): Promise<void> {
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

/** 批次详情层2：该批爬虫任务（pageSize 200；契约见 crawl-batch §三.1） */
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

/** 批次详情层3：选中任务 → 就地回读该任务过程明细（同抽屉第二区域展开，不嵌套 Drawer） */
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
    ['原网址', (x?.origUrl || '').trim()],
  ];
  return list.filter(([, v]) => v);
};
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
          <span v-if="detailCursorIdx >= 0" class="ml-3 text-xs font-semibold text-gray-500">本列表 {{ detailCursorIdx + 1 }} / {{ tTotal }}</span>
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
          平台级采集（G 轨，外部公告域）。每次「立即爬取 / 手动录入 / 定时轮询」生成一条任务条：
          信息源 → 任务（通道/解析器/尝试） → 提取内容 → 渲染结果 → 过程明细；批次为一次完整爬取的行迹。
        </div>
      </div>
      <div class="ind-actions">
        <Button type="primary" size="large" @click="manualModalApi.open()">＋ 手动录入（测试任务）</Button>
        <Dropdown>
          <Button size="large" :loading="!!runCodeBusy" :disabled="runBusy || sources.length === 0">立即获取 ▾</Button>
          <template #overlay>
            <div class="rounded-md border border-gray-300 bg-white py-1 shadow-lg">
              <div class="px-4 py-2 text-xs font-semibold text-gray-500">选择信息源（受 120s 频控）</div>
              <div v-for="src in sources" :key="src.id" class="cursor-pointer px-4 py-2 text-sm font-medium text-gray-800 hover:bg-orange-50" @click="onRunMenu(src)">
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
      <div class="ind-kpi k-blue"><div class="k-label">任务</div><div class="k-num">{{ kpis.sched }}</div><div class="k-note">调度轮询 / 立即爬取（crawl-task）</div></div>
      <div class="ind-kpi k-green"><div class="k-label">后端测试</div><div class="k-num">{{ kpis.tests }}</div><div class="k-note">taskType=TEST 服务端留痕</div></div>
      <div class="ind-kpi k-orange"><div class="k-label">会话测试</div><div class="k-num">{{ kpis.manual }}</div><div class="k-note">本次会话手动录入（刷新清空）</div></div>
      <div class="ind-kpi k-gray"><div class="k-label">采集批次</div><div class="k-num">{{ kpis.jobs }}</div><div class="k-note">crawl-job 只读参照</div></div>
    </div>

    <!-- 选项卡 + 数据面板 -->
    <div class="ind-tabs">
      <button type="button" class="ind-tab" :class="{ on: activeTab === 'tasks' }" @click="activeTab = 'tasks'">任务<span v-if="kpis.sched" class="cnt">{{ kpis.sched }}</span></button>
      <button type="button" class="ind-tab" :class="{ on: activeTab === 'tests' }" @click="activeTab = 'tests'">后端测试任务<span v-if="kpis.tests" class="cnt">{{ kpis.tests }}</span></button>
      <button type="button" class="ind-tab" :class="{ on: activeTab === 'manual' }" @click="activeTab = 'manual'">会话测试 · 解析留档<span v-if="kpis.manual" class="cnt">{{ kpis.manual }}</span></button>
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
                <Tag :color="src.fetchType === 'API' ? 'purple' : 'blue'">{{ src.fetchType === 'API' ? 'API · 直取' : 'CRAWLER · 爬虫' }}</Tag>
                <Tag :color="src.enabled ? 'green' : 'default'">{{ src.enabled ? '启用' : '停用' }}</Tag>
                <span class="fp-src-code">{{ src.sourceCode || '—' }}</span>
              </div>
              <div class="fp-src-name" :title="src.sourceName || ''">{{ src.sourceName || '（未命名）' }}</div>
              <div class="fp-src-meta">任务 {{ srcCountMap[src.sourceCode || ''] ?? '…' }} 条 · {{ src.fetchIntervalSeconds != null ? '间隔 ' + src.fetchIntervalSeconds + 's' : '—' }}</div>
              <div class="fp-src-go">查看任务条 ▸</div>
            </div>
          </div>
        </template>

        <!-- B. 已选源：该源任务条（筛选 + 共 N 条 + 分页 + 行列表） -->
        <template v-else>
          <div class="mb-3 flex flex-wrap items-center gap-2">
            <a class="text-sm font-bold text-blue-700" href="#" @click.prevent="backToSourceWall()">‹ 返回信息源</a>
            <span class="text-sm font-extrabold text-gray-700">{{ curSource.sourceName || curSource.sourceCode }}</span>
            <Tag :color="curSource.fetchType === 'API' ? 'purple' : 'blue'">{{ curSource.fetchType === 'API' ? 'API · 直取' : 'CRAWLER · 爬虫' }}</Tag>
            <span class="ml-2 text-xs font-semibold text-gray-500">共 {{ tTotal }} 条任务</span>
          </div>
          <div class="mb-3 flex flex-wrap items-center gap-2">
            <Select v-model:value="tFilter.status" placeholder="全部状态" allow-clear style="width: 140px" :options="statusOptions" @change="applyTaskFilter" />
            <Select v-model:value="tFilter.channelCode" placeholder="全部通道" allow-clear style="width: 150px" :options="[{ label: 'GENERIC · 通用下载', value: 'GENERIC' }]" @change="applyTaskFilter" />
            <input v-model="tFilter.url" placeholder="URL 关键字" class="fp-url-input" @keyup.enter="applyTaskFilter" />
            <Button size="small" @click="applyTaskFilter">查询</Button>
            <Button size="small" @click="tFilter = { status: undefined, channelCode: undefined, url: undefined }; applyTaskFilter()">重置</Button>
          </div>
          <div class="mb-2 text-right">
            <a-pagination v-model:current="tPage" :total="tTotal" :page-size="tPageSize" show-total :show-size-changer="false" @change="loadTaskRows" />
          </div>
          <div v-if="tRowsLoading" class="ind-empty">加载中…</div>
          <div v-else-if="tRows.length === 0" class="ind-empty">该信息源暂无任务条 —— 在该源「立即获取」或启用后按间隔自动轮询产生。</div>
          <div v-else class="space-y-2">
            <article v-for="t in tRows" :key="String(t.id)" class="ind-card ind-card-click fp-row" @click="openTaskDetail(t)">
              <div class="ind-card-h">
                <Tag :color="tagOf(TASK_STATUS_MAP, t.status)?.color || 'default'">{{ tagOf(TASK_STATUS_MAP, t.status)?.text || t.status || '—' }}</Tag>
                <Tag v-if="t.channelCode" color="geekblue">{{ t.channelCode }}</Tag>
                <span class="fp-row-id">#{{ String(t.id) }}</span>
                <span class="cd-title truncate" :title="t.url || ''">{{ t.url || '（无 URL）' }}</span>
                <span class="cd-meta">{{ fmtDt(t.finishedAt || t.createTime) }}</span>
                <span class="cd-link">详情 ▸</span>
              </div>
              <div class="ind-card-b">
                <div class="fp-row-sub truncate" :title="t.message || ''">
                  <template v-if="t.message">{{ t.message }}</template>
                  <template v-else>解析器：{{ t.parserCode || '—' }} · 尝试 {{ t.attempt ?? '—' }}<template v-if="t.workerId"> · worker {{ t.workerId }}</template></template>
                </div>
              </div>
            </article>
          </div>
        </template>
      </section>

      <!-- ============ ② 后端测试任务 ============ -->
      <section v-show="activeTab === 'tests'">
        <div v-if="backendTests.length === 0" class="ind-empty">暂无后端测试任务（taskType=TEST）。手动录入后服务端留痕即出现于此。</div>
        <article v-for="t in backendTests" :key="String(t.id)" class="ind-card">
          <div class="ind-card-h">
            <Tag color="purple">测试</Tag>
            <Tag :color="tagOf(TASK_STATUS_MAP, t.status)?.color || 'default'">{{ tagOf(TASK_STATUS_MAP, t.status)?.text || t.status || '—' }}</Tag>
            <span class="cd-title truncate" :title="t.url || ''">{{ t.url || '手动录入' }}</span>
            <span class="cd-meta">{{ fmtDt(t.finishedAt || t.createTime) }}</span>
          </div>
          <div class="ind-card-b">
            <div class="ind-kv">
              <div class="kv"><span class="k">源</span><span class="v">{{ t.sourceCode || '—' }}</span></div>
              <div class="kv"><span class="k">尝试</span><span class="v">{{ t.attempt ?? '—' }}</span></div>
              <div class="kv"><span class="k">动作</span><span class="v weak"><a class="text-blue-700 font-bold" @click="openTaskProcess(t)">查看过程</a></span></div>
            </div>
          </div>
        </article>
      </section>

      <!-- ============ ③ 会话测试 · 手动录入 ============ -->
      <section v-show="activeTab === 'manual'">
        <Empty v-if="!manualRuns.length" description="暂无手动/测试任务条 —— 点右上「手动录入」，粘贴 HTML/JSON 或上传 PDF/HTML/JSON 文件，即刻生成一条含 源·提取·渲染·过程 的任务条" />
        <article v-for="r in manualRuns" :key="r.id" class="ind-card">
          <div class="ind-card-h">
            <Tag color="purple">测试</Tag>
            <Tag :color="manualStatusTag(r).color">{{ manualStatusTag(r).text }}</Tag>
            <span class="cd-title truncate">{{ (r.resp?.title || '').trim() || '（无标题产出）' }}</span>
            <span class="cd-meta">{{ fmtDt(r.occurredAt) }}</span>
            <a class="text-sm font-bold text-blue-700" @click="openManualProcess(r)">过程</a>
          </div>
          <div class="grid grid-cols-3 max-lg:grid-cols-1">
            <!-- ① 源 -->
            <div class="p-4 border-r border-dashed border-gray-300">
              <div class="ind-sec-t"><span>①</span> 源 · 源代码 / PDF</div>
              <div class="ind-kv" style="grid-template-columns: 1fr;">
                <div class="kv"><span class="k">输入口径</span><span class="v">{{ r.inputType || '—' }}{{ r.fileName ? ' · ' + r.fileName : '' }}</span></div>
                <div v-if="r.contentLength != null" class="kv"><span class="k">内容长度</span><span class="v">{{ r.contentLength }} 字符</span></div>
                <div v-if="r.url" class="kv"><span class="k">URL</span><span class="v truncate" :title="r.url">{{ r.url }}</span></div>
              </div>
              <div class="mt-2 text-xs font-medium text-gray-500">原文 HTML/PDF 完整内容由后端 FileApi 落库后经 fileUrl 提供（契约 TaskRunVO.raw），本会话仅保留解析回执。</div>
            </div>
            <!-- ② 提取内容 -->
            <div class="p-4 border-r border-dashed border-gray-300">
              <div class="ind-sec-t"><span>②</span> 提取内容</div>
              <div class="ind-kv" style="grid-template-columns: 1fr;">
                <div v-for="[k, v] in kvOf(r)" :key="'k' + k" class="kv"><span class="k">{{ k }}</span><span class="v truncate" :title="v">{{ v }}</span></div>
              </div>
              <div v-if="extractedOf(r).length" class="mt-3 border-t border-dashed border-gray-300 pt-2">
                <div class="ind-sec-t"><span>字段</span> 抽取字段</div>
                <div class="ind-kv" style="grid-template-columns: 1fr;">
                  <div v-for="[k, v] in extractedOf(r)" :key="'e' + k" class="kv"><span class="k">{{ k }}</span><span class="v truncate" :title="v">{{ v }}</span></div>
                </div>
              </div>
              <div v-if="r.resp?.warnings?.length" class="mt-2">
                <Tag v-for="(w, i) in r.resp.warnings" :key="i" color="orange" class="mr-1 mt-0.5">{{ w }}</Tag>
              </div>
            </div>
            <!-- ③ 渲染内容 -->
            <div class="p-4">
              <div class="ind-sec-t"><span>③</span> 渲染后的内容</div>
              <div class="ind-doc" v-html="renderHtmlOf(r)"></div>
            </div>
          </div>
        </article>
      </section>

      <!-- ============ ④ 采集批次（克制档行：状态Tag/源/触发/摘要(单行截断, title 全量)/时间；整行或「详情 ▸」进批次详情层） ============ -->
      <section v-show="activeTab === 'jobs'">
        <div class="ind-panel">
          <div class="ind-panel-h"><span class="ind-panel-t">采集批次</span><span class="ind-panel-meta">一次完整爬取 = 一个批次（crawl-job，只读参照；批内任务在详情层内按 jobId 拉取）</span></div>
          <div class="ind-panel-b">
            <div v-if="!jobs.length" class="ind-empty">暂无批次记录</div>
            <div v-for="j in jobs" :key="String(j.id)" class="ind-row ind-dl-row" @click="openJobDetail(j)">
              <Tag :color="tagOf(JOB_STATUS_MAP, j.status)?.color || 'default'">{{ tagOf(JOB_STATUS_MAP, j.status)?.text || j.status || '—' }}</Tag>
              <span class="rw">源：{{ j.sourceCode || '—' }}</span>
              <span class="rw">触发：{{ j.triggerType || '—' }}</span>
              <span class="rw main" :title="j.summaryJson ? jobSummaryText(j.summaryJson) : ''">{{ j.summaryJson ? jobSummaryText(j.summaryJson) : '—' }}</span>
              <span class="rw time">{{ fmtDt(j.finishedAt || j.createTime) }}</span>
              <a class="rw cd-link shrink-0" href="#" @click.prevent.stop="openJobDetail(j)">详情 ▸</a>
            </div>
          </div>
        </div>
      </section>
    </div>

    <!-- 过程抽屉 -->
    <Drawer v-model:open="processOpen" title="任务过程" :width="720">
      <div v-if="processTask" class="space-y-3">
        <div class="ind-panel">
          <div class="ind-panel-h"><span class="ind-panel-t">任务过程</span><span class="ind-panel-meta">主键：{{ String(processTask.id) }}</span></div>
          <div class="ind-panel-b">
            <div class="mb-2 text-xs font-medium text-amber-700">下载器/解析器/中间文件与逐步时间线将在 TaskRunVO 落库后完整呈现（steps_json + logs + raw/extract/render）。</div>
            <Descriptions :column="1" size="small" bordered>
              <Descriptions.Item label="状态">{{ tagOf(TASK_STATUS_MAP, processTask.status)?.text || processTask.status }}</Descriptions.Item>
              <Descriptions.Item label="源 / URL">{{ processTask.sourceCode || '—' }}<br />{{ processTask.url || '—' }}</Descriptions.Item>
              <Descriptions.Item label="通道 / 解析器">{{ processTask.channelCode || '—' }} / {{ processTask.parserCode || '—' }}</Descriptions.Item>
              <Descriptions.Item label="尝试次数">{{ processTask.attempt ?? '—' }}</Descriptions.Item>
              <Descriptions.Item label="worker">{{ processTask.workerId || '—' }}</Descriptions.Item>
              <Descriptions.Item label="开始 / 完成">{{ fmtDt(processTask.startedAt) || '—' }} / {{ fmtDt(processTask.finishedAt) || '—' }}</Descriptions.Item>
              <Descriptions.Item label="消息 / 错误">{{ processDetail?.message || processTask.message || '—' }}</Descriptions.Item>
            </Descriptions>
          </div>
        </div>
      </div>
      <div v-else-if="processManual" class="space-y-3">
        <div class="ind-panel">
          <div class="ind-panel-h"><span class="ind-panel-t">会话测试过程</span></div>
          <div class="ind-panel-b">
            <Descriptions :column="1" size="small" bordered>
              <Descriptions.Item label="解析器">{{ processManual.parserName ? processManual.parserName + '（' + processManual.parserCode + '）' : processManual.parserCode }}</Descriptions.Item>
              <Descriptions.Item label="输入口径">{{ [processManual.inputType, processManual.fileName].filter(Boolean).join(' · ') }}</Descriptions.Item>
              <Descriptions.Item label="来源 URL">{{ processManual.url || '—' }}</Descriptions.Item>
              <Descriptions.Item label="耗时">{{ processManual.durationMs != null ? processManual.durationMs + ' ms' : '—' }}</Descriptions.Item>
              <Descriptions.Item label="解析状态">{{ manualStatusTag(processManual).text }}</Descriptions.Item>
              <Descriptions.Item v-if="processManual.resp?.error" label="失败原因">{{ processManual.resp.error }}</Descriptions.Item>
            </Descriptions>
            <div class="mt-3">
              <div class="ind-sec-t"><span>STEP</span> 过程步骤</div>
              <div class="mt-1 space-y-1 text-sm font-medium text-gray-700">
                <div>1. 注入原始输入（HTML/JSON/文件）→ 成功（手动录入无网络下载）</div>
                <div>2. 解析（parserCode={{ processManual.parserCode || '—' }}）→ {{ processManual.status === 'FAILED' ? '失败' : '成功' }}</div>
                <div>3. 渲染（契约 render_html 未落地 → 前端兜底公文化排版 builtBy=FRONTEND_FALLBACK）</div>
              </div>
            </div>
            <div v-if="processManual.resp?.meta && Object.keys(processManual.resp.meta).length" class="mt-3">
              <div class="ind-sec-t"><span>OBS</span> 解析观测（meta）</div>
              <div class="mt-1 space-y-1 text-sm font-medium text-gray-700">
                <div v-for="(mv, mk) in processManual.resp.meta" :key="String(mk)" class="flex gap-2">
                  <span class="w-48 shrink-0 truncate text-gray-500" :title="String(mk)">{{ mk }}</span>
                  <span class="truncate">{{ mv }}</span>
                </div>
              </div>
            </div>
            <div v-if="processManual.resp?.warnings?.length" class="mt-3">
              <div class="ind-sec-t"><span>WARN</span> 告警（warnings）</div>
              <div class="mt-1 space-y-1 text-sm font-medium text-amber-700">
                <div v-for="(w, i) in processManual.resp.warnings" :key="i">• {{ w }}</div>
              </div>
            </div>
            <div v-if="processManual.resp?.entries?.length" class="mt-3">
              <div class="ind-sec-t"><span>LIST</span> 列表条目（列表页输入时）</div>
              <div class="mt-1 space-y-1 text-sm font-medium text-gray-700">
                <div v-for="(en, i) in processManual.resp.entries.slice(0, 10)" :key="i">• {{ en.title }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="text-sm font-semibold text-gray-500">加载中…</div>
    </Drawer>

    <!-- 详情档抽屉（任务/批次两档视图之“舒张”层：宽 900 就地渲染，不嵌套 Drawer；打开方=①任务卡详情 / ④批次行详情） -->
    <Drawer v-model:open="detailOpen" :width="900" :title="detailTitle">
      <!-- ===== 批次详情：层1 批次概要 → 层2 该批爬虫任务 N 条（KPI 卡+克制行） → 层3 选中任务过程（同抽屉第二区域） ===== -->
      <div v-if="detailKind === 'job' && detailJob" class="space-y-3">
        <div class="ind-panel">
          <div class="ind-panel-h"><span class="ind-panel-t">批次概要</span><span class="ind-panel-meta">crawl-job 只读参照</span></div>
          <div class="ind-panel-b">
            <Descriptions :column="2" size="small" bordered>
              <Descriptions.Item label="状态"><Tag :color="tagOf(JOB_STATUS_MAP, detailJob.status)?.color || 'default'">{{ tagOf(JOB_STATUS_MAP, detailJob.status)?.text || detailJob.status || '—' }}</Tag></Descriptions.Item>
              <Descriptions.Item label="源">{{ detailJob.sourceCode || '—' }}</Descriptions.Item>
              <Descriptions.Item label="触发">{{ detailJob.triggerType || '—' }}</Descriptions.Item>
              <Descriptions.Item label="scope">{{ detailJob.scope || '—' }}</Descriptions.Item>
              <Descriptions.Item label="创建">{{ fmtDt(detailJob.createTime) }}</Descriptions.Item>
              <Descriptions.Item label="完成">{{ fmtDt(detailJob.finishedAt) }}</Descriptions.Item>
              <Descriptions.Item label="汇总摘要" :span="2"><span class="break-all" :title="detailJob.summaryJson || ''">{{ jobSummaryText(detailJob.summaryJson) || '—' }}</span></Descriptions.Item>
            </Descriptions>
          </div>
        </div>

        <div class="ind-panel">
          <div class="ind-panel-h">
            <span class="ind-panel-t">该批爬虫任务</span>
            <span class="ind-panel-meta">jobId #{{ String(detailJob.id) }} · 点击行就地展开该任务过程</span>
          </div>
          <div class="ind-panel-b">
            <div class="ind-kpis" style="margin: 0 0 6px; max-width: 340px;">
              <div class="ind-kpi k-blue">
                <div class="k-label">该批爬虫任务</div>
                <div class="k-num">{{ jobTasks.length }}<span class="k-unit"> 条</span></div>
                <div class="k-note">fetchCrawlTaskPage({ jobId, pageNo: 1, pageSize: 200 })</div>
              </div>
            </div>
            <div v-if="jobTasksLoading" class="text-xs font-semibold text-orange-600">任务加载中…</div>
            <div v-else-if="!jobTasks.length" class="text-sm font-medium text-gray-500">该批暂无任务行（批次落库见 crawl-batch 契约）</div>
            <div v-else class="-my-1">
              <div v-for="t in jobTasks" :key="String(t.id)" class="ind-row ind-dl-row" :class="{ on: selJobTask && selJobTask.id === t.id }" @click="pickJobTask(t)">
                <Tag :color="tagOf(TASK_STATUS_MAP, t.status)?.color || 'default'">{{ tagOf(TASK_STATUS_MAP, t.status)?.text || t.status || '—' }}</Tag>
                <span class="rw main truncate" :title="t.url || ''">{{ t.url || '（无 URL）' }}</span>
                <span class="rw">解析器：{{ t.parserCode || '—' }}</span>
                <span class="rw">attempt {{ t.attempt ?? '—' }}</span>
                <span class="rw time">{{ fmtDt(t.finishedAt || t.createTime) }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 层3：选中任务 → 该任务过程（getCrawlTask 详情 Descriptions，放同抽屉内第二区域，不嵌套 Drawer） -->
        <div v-if="selJobTask" class="ind-panel">
          <div class="ind-panel-h">
            <span class="ind-panel-t">任务过程</span>
            <span class="ind-panel-meta">任务 #{{ String(selJobTask.id) }} · 回读 getCrawlTask 明细</span>
            <span class="ml-auto">
              <Button v-if="(selJobTaskFull?.status || selJobTask.status) === 'FAILED'" danger size="small" :loading="retryBusy === selJobTask.id" @click="retryJobTask">重试（归零 attempt）</Button>
            </span>
          </div>
          <div class="ind-panel-b">
            <div v-if="selJobTaskLoading" class="mb-2 text-xs font-semibold text-orange-600">详情回读中…</div>
            <Descriptions :column="2" size="small" bordered>
              <Descriptions.Item label="状态">{{ tagOf(TASK_STATUS_MAP, selJobTaskFull?.status || selJobTask.status)?.text || selJobTask.status || '—' }}</Descriptions.Item>
              <Descriptions.Item label="源">{{ selJobTaskFull?.sourceCode || selJobTask.sourceCode || '—' }}</Descriptions.Item>
              <Descriptions.Item label="URL" :span="2"><span class="break-all">{{ selJobTaskFull?.url || selJobTask.url || '—' }}</span></Descriptions.Item>
              <Descriptions.Item label="通道 / 解析器">{{ selJobTaskFull?.channelCode || selJobTask.channelCode || '—' }} / {{ selJobTaskFull?.parserCode || selJobTask.parserCode || '—' }}</Descriptions.Item>
              <Descriptions.Item label="尝试">{{ selJobTaskFull?.attempt ?? selJobTask.attempt ?? '—' }}</Descriptions.Item>
              <Descriptions.Item label="worker">{{ selJobTaskFull?.workerId || selJobTask.workerId || '—' }}</Descriptions.Item>
              <Descriptions.Item label="开始 / 完成">{{ fmtDt(selJobTaskFull?.startedAt || selJobTask.startedAt) }} / {{ fmtDt(selJobTaskFull?.finishedAt || selJobTask.finishedAt) }}</Descriptions.Item>
              <Descriptions.Item label="消息 / 错误" :span="2">{{ selJobTaskFull?.message || selJobTask.message || '—' }}</Descriptions.Item>
            </Descriptions>
            <div class="mt-3 text-xs font-medium text-amber-700">下载器/解析器/中间文件与逐步时间线将在 TaskRunVO 落库后完整呈现（steps_json + logs + raw/extract/render）。</div>
          </div>
        </div>
      </div>
    </Drawer>

    <ManualModal :source-options="sourceOptions" @parsed="onParsed" />
    <SourceDrawer v-model:open="sourceOpen" @changed="() => { void loadSources(); void loadSched(true); }" />
    </template>
  </div>
</template>

<style scoped>
/* ========== 整页三级导航（信息源→任务条→整页详情） 2026-09-10 ========== */
.fp-page { min-height: calc(100vh - 140px); background: var(--ind-paper); border: 2px solid var(--ind-line); border-top: 3px solid var(--ind-ink); padding: 14px; }
.fp-bar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 14px; padding: 10px 12px; background: #fff; border: 1px solid var(--ind-line); border-radius: 6px; }
.fp-title { font-size: 16px; font-weight: 800; color: var(--ind-ink-text); }
.fp-nav { display: flex; align-items: center; }
.fp-loading { margin-bottom: 10px; font-size: 13px; font-weight: 700; color: #c2571a; }
.fp-guide { margin-bottom: 12px; padding: 8px 12px; font-size: 12.5px; line-height: 1.7; color: #5b4a1f; background: #fdf6e3; border: 1px solid #e8d9a8; border-left: 4px solid #c9a227; border-radius: 4px; }
.fp-wall { display: grid; grid-template-columns: repeat(auto-fill, minmax(252px, 1fr)); gap: 12px; }
.fp-src { display: flex; flex-direction: column; gap: 8px; padding: 12px 14px; background: #fff; border: 1.5px solid var(--ind-line); border-radius: 8px; cursor: pointer; transition: border-color .15s, box-shadow .15s, transform .12s; }
.fp-src:hover { border-color: #1d4ed8; box-shadow: 0 4px 12px rgba(29, 78, 216, .12); transform: translateY(-1px); }
.fp-src-h { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.fp-src-code { margin-left: auto; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; font-weight: 700; color: var(--ind-weak-text); }
.fp-src-name { font-size: 15px; font-weight: 800; color: var(--ind-ink-text); line-height: 1.4; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fp-src-meta { font-size: 12.5px; font-weight: 600; color: var(--ind-sub-text); }
.fp-src-go { margin-top: auto; align-self: flex-end; font-size: 13px; font-weight: 800; color: #1d4ed8; }
.fp-row { display: block; }
.fp-row-id { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; font-weight: 700; color: #9aa3ad; margin-right: 2px; }
.fp-row-sub { font-size: 13px; font-weight: 600; color: var(--ind-sub-text); }
.fp-url-input { height: 30px; padding: 0 10px; font-size: 13px; color: #1f2937; background: #fff; border: 1px solid #d0d5dd; border-radius: 6px; outline: none; transition: border-color .15s; }
.fp-url-input:focus { border-color: #1d4ed8; }
.fp-url-input::placeholder { color: #9aa3ad; }

/* 工业选项卡（自绘，无 antd Tabs 依赖，更贴合线稿/工业语言） */
.ind-tabs { display: flex; align-items: flex-end; gap: 2px; border-bottom: 3px solid var(--ind-line); margin-top: 4px; }
.ind-tab { appearance: none; background: #eceff2; border: 2px solid var(--ind-line); border-bottom: none; border-radius: 4px 4px 0 0;
  font-size: 15px; font-weight: 700; color: #4b5563; padding: 10px 20px 9px; cursor: pointer; display: inline-flex; align-items: center; gap: 8px; }
.ind-tab:hover { background: #f6f1e6; }
.ind-tab.on { background: var(--ind-paper); color: var(--ind-ink-text); padding-bottom: 11px; margin-bottom: -2px; position: relative; }
.ind-tab.on::after { content: ''; position: absolute; left: 0; right: 0; bottom: -3px; height: 4px; background: var(--ind-orange); }
.ind-tab .cnt { font-size: 12px; font-weight: 800; background: var(--ind-ink); color: #fff; border-radius: 10px; padding: 0 8px; line-height: 18px; }
.ind-tab.on .cnt { background: var(--ind-orange); }
.ind-tab-body { background: var(--ind-paper); border: 2px solid var(--ind-line); border-top: none; padding: 16px; }
.ind-empty { border: 2px dashed #c7ccd3; background: #fbfbfc; color: var(--ind-weak-text); text-align: center;
  font-size: 14.5px; font-weight: 500; padding: 36px 20px; }
.ind-sec-t { display: flex; align-items: center; gap: 7px; font-size: 13.5px; font-weight: 800; color: var(--ind-ink-text); margin-bottom: 9px; }
.ind-sec-t span { display: inline-flex; align-items: center; justify-content: center; min-width: 20px; height: 20px; padding: 0 6px;
  background: var(--ind-ink); color: var(--ind-orange); border-radius: 2px; font-size: 11.5px; font-weight: 800; letter-spacing: .5px; }
.ind-doc-extra { max-height: 300px; overflow: auto; }

/* ---------- 两档视图（克制概览 → 详情档）交互反馈：详情入口链接 / 卡片·行 hover 与选中态 ---------- */
.ind-card-click { cursor: pointer; transition: border-color .15s, box-shadow .15s; }
.ind-card-click:hover { border-color: var(--ind-line); box-shadow: 0 3px 10px rgba(20, 28, 38, .09); }
.cd-link { font-size: 13.5px; font-weight: 800; color: #1d4ed8; white-space: nowrap; }
.cd-link:hover { text-decoration: underline; }
.ind-card-b .cd-abs { font-size: 13px; font-weight: 600; color: var(--ind-sub-text); }
.ind-dl-row { cursor: pointer; transition: background .12s; border-radius: 3px; }
.ind-dl-row:hover { background: #f6f1e6; }
.ind-dl-row.on { background: #fdf3dc; }
.ind-kpi .k-unit { font-size: 15px; font-weight: 800; color: var(--ind-weak-text); }
</style>

