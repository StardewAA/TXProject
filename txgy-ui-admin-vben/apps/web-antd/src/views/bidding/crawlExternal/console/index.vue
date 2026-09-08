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
/** 顶部选项卡：tasks=定时任务 / tests=后端测试 / manual=会话测试 / jobs=采集批次 */
const activeTab = ref<'tasks' | 'tests' | 'manual' | 'jobs'>('tasks');
const kpis = computed(() => ({
  sched: schedTasks.value.length,
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
  void loadSched(true);
  void loadBackendTests();
  void loadJobs();
}

onMounted(() => {
  void loadSources();
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
        (source.sourceName || source.sourceCode) + ' 距上次采集不足 120 秒，还需 ' + wait + ' 秒（防封频控）',
      );
      return;
    }
  }
  Modal.confirm({
    title: '立即爬取「' + (source.sourceName || source.sourceCode) + '」',
    content:
      '将对该源各栏目执行一轮采集（受 120s 频控与页上限约束）。接口同步执行，可能耗时数十秒，完成后任务条进入“定时任务”区。',
    okText: '开始爬取',
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
          title: '本轮爬取完成',
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
    await retryCrawlTask(Number(row.id));
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
      processDetail.value = await getCrawlTask(Number(row.id));
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
const detailTitle = computed(() => {
  if (detailKind.value === 'job')
    return detailJob.value ? '批次 #' + String(detailJob.value.id) + ' · ' + (detailJob.value.sourceCode || '未知源') : '批次详情';
  return detailTask.value ? '任务 #' + String(detailTask.value.id) + ' 详情' : '任务详情';
});

/* ---------- 任务详情（tasks 卡进入） ---------- */
const detailTask = ref<CrawlExternalApi.TaskItem>();
const detailTaskFull = ref<CrawlExternalApi.TaskItem>();
const detailTaskLoading = ref(false);
/** 跳过同批任务层：从同批任务行继续下钻时批次上下文已具备，免重复加载同批列表 */
const taskSkipSameBatch = ref(false);
/** 展示用任务对象：优先 getCrawlTask 回读详情，回读前以列表行字段兜底 */
const dt = computed(() => detailTaskFull.value || detailTask.value || ({} as CrawlExternalApi.TaskItem));
/* 层2：同批任务（task.jobId 非空时展示，参照 crawl-batch §三.1 携 jobId 拉取） */
const sameBatchTasks = ref<CrawlExternalApi.TaskItem[]>([]);
const sameBatchLoading = ref(false);
const showSameBatch = computed(
  () => detailKind.value === 'task' && dt.value?.jobId != null && !taskSkipSameBatch.value,
);

/* ---------- 批次详情（jobs 行进入） ---------- */
const detailJob = ref<CrawlExternalApi.JobItem>();
const jobTasks = ref<CrawlExternalApi.TaskItem[]>([]);
const jobTasksLoading = ref(false);
const selJobTask = ref<CrawlExternalApi.TaskItem>();
const selJobTaskFull = ref<CrawlExternalApi.TaskItem>();
const selJobTaskLoading = ref(false);

async function openTaskDetail(t: CrawlExternalApi.TaskItem, skipSameBatch = false): Promise<void> {
  detailKind.value = 'task';
  detailJob.value = undefined;
  selJobTask.value = undefined;
  selJobTaskFull.value = undefined;
  detailTask.value = t;
  detailTaskFull.value = undefined;
  taskSkipSameBatch.value = skipSameBatch;
  sameBatchTasks.value = [];
  detailOpen.value = true;
  detailTaskLoading.value = true;
  if (t.id != null) {
    try {
      detailTaskFull.value = (await getCrawlTask(Number(t.id))) as unknown as CrawlExternalApi.TaskItem;
    } catch {
      detailTaskFull.value = undefined; // 回读失败不阻断抽屉：先以列表行字段呈现概要
    }
  }
  detailTaskLoading.value = false;
  if (t.jobId != null && !skipSameBatch) void loadSameBatch(t.jobId);
}

/** 同批任务：同一批次下的爬虫任务（克制行，点击可继续下钻另一条任务） */
async function loadSameBatch(jobId: number | string): Promise<void> {
  sameBatchLoading.value = true;
  try {
    const res = (await fetchCrawlTaskPage({
      jobId: Number(jobId),
      pageNo: 1,
      pageSize: 200,
    })) as unknown as { list?: CrawlExternalApi.TaskItem[] };
    sameBatchTasks.value = res.list || [];
  } catch {
    sameBatchTasks.value = [];
  } finally {
    sameBatchLoading.value = false;
  }
}

/** 详情档内重试：复用列表重试（仅 FAILED 死信归零 attempt 转 PENDING），成功后回读刷新概要 */
async function retryFromDetail(): Promise<void> {
  const t = detailTask.value;
  if (!t || t.id == null) return;
  await retryTask(t);
  try {
    detailTaskFull.value = (await getCrawlTask(Number(t.id))) as unknown as CrawlExternalApi.TaskItem;
  } catch {
    detailTaskFull.value = undefined;
  }
}

async function openJobDetail(j: CrawlExternalApi.JobItem): Promise<void> {
  detailKind.value = 'job';
  detailJob.value = j;
  detailTask.value = undefined;
  detailTaskFull.value = undefined;
  sameBatchTasks.value = [];
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
      jobId: Number(j.id),
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
      selJobTaskFull.value = (await getCrawlTask(Number(t.id))) as unknown as CrawlExternalApi.TaskItem;
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
    selJobTaskFull.value = (await getCrawlTask(Number(t.id))) as unknown as CrawlExternalApi.TaskItem;
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

<!-- ============ 爬取任务工作台 · 工业风：选项卡 + 数据面板（2026-09-10） ============ -->
<template>
<!--
  爬虫工作台 · 平台职能（采集源/任务/测试/批次 总览与执行）
  业务：平台运营配置采集源、立即爬取/定时轮询、解析器测试、任务死信重试，并查看采集任务与批次行迹；
        目标=把依法公开的招标公告及时、完整采回入库，支撑大厅免费查阅。
  理由：采集是平台级聚合（G 轨）能力，仅平台管理职能可用；间隔≥120s 限频防对源站构成压力；采集对象=依法公开信息。
    业务边界：本页属平台职能（G 轨，无租户语义）——职能角色矩阵（后端执行清单 §7）：爬虫管理/外部公告审核=平台职能，
        勿授企业租户；企业租户只使用发布/档案/跟踪等 T 轨功能。
    依据（法规/政府文件）：10号令 第12/13条（部门规章）——公开公告免费查阅/检索是采集目的；电子办法 第9条（部门规章）公开获取。
    后端契约：爬虫任务工作台-前后端契约-2026-09-07.md；crawl-source/crawl-task/crawl-job/crawl-parser 域（01 接口文档 §2）；
      fetchIntervalSeconds≥120（<120 返 106_046）、run 同步执行返回汇总、retry 仅 FAILED 死信归零 attempt。
-->

  <div class="p-3 bid-ind">
    <!-- 页头 -->
    <div class="ind-stripes"></div>
    <div class="ind-head">
      <div>
        <div class="ind-title">
          爬取任务工作台
          <span class="ind-mark">PLATFORM</span>
        </div>
        <div class="ind-sub">
          平台级采集（G 轨，外部公告域）。每次「立即爬取 / 手动录入 / 定时轮询」生成一条任务条：
          采集源 → 任务（通道/解析器/尝试） → 提取内容 → 渲染结果 → 过程明细；批次为一次完整爬取的行迹。
        </div>
      </div>
      <div class="ind-actions">
        <Button type="primary" size="large" @click="manualModalApi.open()">＋ 手动录入（测试任务）</Button>
        <Dropdown>
          <Button size="large" :loading="!!runCodeBusy" :disabled="runBusy || sources.length === 0">立即爬取 ▾</Button>
          <template #overlay>
            <div class="rounded-md border border-gray-300 bg-white py-1 shadow-lg">
              <div class="px-4 py-2 text-xs font-semibold text-gray-500">选择采集源（受 120s 频控）</div>
              <div v-for="src in sources" :key="src.id" class="cursor-pointer px-4 py-2 text-sm font-medium text-gray-800 hover:bg-orange-50" @click="onRunMenu(src)">
                {{ src.sourceName || src.sourceCode }}
              </div>
            </div>
          </template>
        </Dropdown>
        <Button size="large" @click="sourceOpen = true">采集源配置</Button>
        <Button size="large" @click="refreshAll">刷新</Button>
      </div>
    </div>

    <!-- KPI -->
    <div class="ind-kpis">
      <div class="ind-kpi k-blue"><div class="k-label">定时任务</div><div class="k-num">{{ kpis.sched }}</div><div class="k-note">调度轮询 / 立即爬取（crawl-task）</div></div>
      <div class="ind-kpi k-green"><div class="k-label">后端测试</div><div class="k-num">{{ kpis.tests }}</div><div class="k-note">taskType=TEST 服务端留痕</div></div>
      <div class="ind-kpi k-orange"><div class="k-label">会话测试</div><div class="k-num">{{ kpis.manual }}</div><div class="k-note">本次会话手动录入（刷新清空）</div></div>
      <div class="ind-kpi k-gray"><div class="k-label">采集批次</div><div class="k-num">{{ kpis.jobs }}</div><div class="k-note">crawl-job 只读参照</div></div>
    </div>

    <!-- 选项卡 + 数据面板 -->
    <div class="ind-tabs">
      <button type="button" class="ind-tab" :class="{ on: activeTab === 'tasks' }" @click="activeTab = 'tasks'">定时任务<span v-if="kpis.sched" class="cnt">{{ kpis.sched }}</span></button>
      <button type="button" class="ind-tab" :class="{ on: activeTab === 'tests' }" @click="activeTab = 'tests'">后端测试任务<span v-if="kpis.tests" class="cnt">{{ kpis.tests }}</span></button>
      <button type="button" class="ind-tab" :class="{ on: activeTab === 'manual' }" @click="activeTab = 'manual'">会话测试 · 解析留档<span v-if="kpis.manual" class="cnt">{{ kpis.manual }}</span></button>
      <button type="button" class="ind-tab" :class="{ on: activeTab === 'jobs' }" @click="activeTab = 'jobs'">采集批次<span v-if="kpis.jobs" class="cnt">{{ kpis.jobs }}</span></button>
    </div>

    <div class="ind-tab-body">
      <!-- ============ ① 定时任务（克制档：状态Tag/标题(URL 截断)/时间 + 一行摘要；整卡或「详情 ▸」进详情档） ============ -->
      <section v-show="activeTab === 'tasks'">
        <div class="mb-3 flex flex-wrap items-center gap-3">
          <Select v-model:value="filterSource" placeholder="全部采集源" allow-clear style="width: 230px" :options="sourceOptions" @change="loadSched(true)" />
          <Select v-model:value="filterStatus" placeholder="全部状态" allow-clear style="width: 150px" :options="statusOptions" @change="loadSched(true)" />
          <span v-if="schedLoading" class="text-xs font-semibold text-orange-600">加载中…</span>
          <a v-if="!schedEnd && schedTasks.length" class="ml-auto text-sm font-semibold text-blue-600" @click="loadSched()">{{ schedLoading ? '加载中…' : '加载更多 ▾' }}</a>
        </div>
        <div v-if="schedTasks.length === 0 && !schedLoading" class="ind-empty">暂无定时任务 —— 在「采集源配置」启用一个源，或点「立即爬取」生成第一条任务条</div>
        <article v-for="t in schedTasks" :key="String(t.id)" class="ind-card ind-card-click" @click="openTaskDetail(t)">
          <div class="ind-card-h">
            <Tag :color="tagOf(TASK_STATUS_MAP, t.status)?.color || 'default'">{{ tagOf(TASK_STATUS_MAP, t.status)?.text || t.status || '—' }}</Tag>
            <span class="cd-title truncate" :title="t.url || ''">{{ t.url || '（无 URL，见详情）' }}</span>
            <span class="cd-meta">{{ fmtDt(t.finishedAt || t.createTime) }}</span>
            <a class="cd-link" href="#" @click.prevent.stop="openTaskDetail(t)">详情 ▸</a>
          </div>
          <div class="ind-card-b">
            <!-- 克制摘要：仅一行 message（无则“无消息”），截断由 title 提示全量 -->
            <div class="cd-abs truncate" :title="t.message || t.url || ''">{{ t.message || '无消息' }}</div>
          </div>
        </article>
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
      <!-- ===== 任务详情：层1 概要 → 层2 同批任务(该行有 jobId 时) → 层3 过程（原 processOpen 占位文案保留） ===== -->
      <div v-if="detailKind === 'task' && detailTask" class="space-y-3">
        <div class="ind-panel">
          <div class="ind-panel-h">
            <span class="ind-panel-t">任务概要</span>
            <span class="ind-panel-meta">主键 #{{ String(detailTask.id) }}<template v-if="dt.jobId != null"> · 批次 jobId #{{ String(dt.jobId) }}</template></span>
            <span class="ml-auto">
              <Button v-if="(detailTaskFull?.status || detailTask.status) === 'FAILED'" danger size="small" :loading="retryBusy === detailTask.id" @click="retryFromDetail">重试（归零 attempt）</Button>
            </span>
          </div>
          <div class="ind-panel-b">
            <div v-if="detailTaskLoading" class="mb-2 text-xs font-semibold text-orange-600">详情回读中…</div>
            <Descriptions :column="2" size="small" bordered>
              <Descriptions.Item label="状态">{{ tagOf(TASK_STATUS_MAP, dt.status)?.text || dt.status || '—' }}</Descriptions.Item>
              <Descriptions.Item label="源">{{ dt.sourceCode || '—' }}</Descriptions.Item>
              <Descriptions.Item label="URL" :span="2"><span class="break-all">{{ dt.url || '—' }}</span></Descriptions.Item>
              <Descriptions.Item label="通道 / 解析器">{{ dt.channelCode || '—' }} / {{ dt.parserCode || '—' }}</Descriptions.Item>
              <Descriptions.Item label="尝试">{{ dt.attempt ?? '—' }}</Descriptions.Item>
              <Descriptions.Item label="worker">{{ dt.workerId || '—' }}</Descriptions.Item>
              <Descriptions.Item label="开始 / 完成">{{ fmtDt(dt.startedAt) }} / {{ fmtDt(dt.finishedAt) }}</Descriptions.Item>
              <Descriptions.Item label="消息 / 错误" :span="2">{{ dt.message || '—' }}</Descriptions.Item>
            </Descriptions>
          </div>
        </div>

        <!-- 层2：同批任务（该行有 jobId 且非“批内继续下钻”上下文时展示；crawl-batch §三.1 携 jobId 拉取） -->
        <div v-if="showSameBatch" class="ind-panel">
          <div class="ind-panel-h">
            <span class="ind-panel-t">同批任务</span>
            <span class="ind-panel-meta">jobId #{{ String(dt.jobId) }} · {{ sameBatchTasks.length }} 条（点击任一行继续下钻该任务）</span>
          </div>
          <div class="ind-panel-b">
            <div v-if="sameBatchLoading" class="text-xs font-semibold text-orange-600">加载中…</div>
            <div v-else-if="!sameBatchTasks.length" class="text-sm font-medium text-gray-500">该批暂无其它任务行</div>
            <div v-else class="-my-1">
              <div v-for="s in sameBatchTasks" :key="String(s.id)" class="ind-row ind-dl-row" :class="{ on: dt.id != null && s.id === dt.id }" @click="openTaskDetail(s, true)">
                <Tag :color="tagOf(TASK_STATUS_MAP, s.status)?.color || 'default'">{{ tagOf(TASK_STATUS_MAP, s.status)?.text || s.status || '—' }}</Tag>
                <span class="rw main truncate" :title="s.url || ''">{{ s.url || '（无 URL）' }}</span>
                <span class="rw">解析器：{{ s.parserCode || '—' }}</span>
                <span class="rw">attempt {{ s.attempt ?? '—' }}</span>
                <span class="rw time">{{ fmtDt(s.finishedAt || s.createTime) }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 层3：纵向数据链（调试视图：①请求 URL → ②获取方式 CRAWLER/API → ③原始载荷 payload_json 黑块 → ④解析 → ⑤执行/结果 → ⑥产出；每段可复制/全屏） -->
        <div class="ind-panel">
          <div class="ind-panel-h">
            <span class="ind-panel-t">纵向数据链</span>
            <span class="ind-panel-meta">原始载荷含列表原文/接口 JSON；「全屏 ▾」看完整数据</span>
          </div>
          <div class="ind-panel-b">
            <TaskChainView :task="detailTask" :show-same-batch="false" />
          </div>
        </div>
      </div>

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
  </div>
</template>

<style scoped>
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

