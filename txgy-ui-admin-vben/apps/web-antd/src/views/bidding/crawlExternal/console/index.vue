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
  Radio,
  Select,
  Space,
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

import { fmtDt, jobSummaryText, tagOf, TASK_STATUS_MAP } from '../shared';
import ManualForm from './modules/manual-form.vue';
import SourceDrawer from './modules/source-drawer.vue';

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
const filterType = ref<'ALL' | 'TEST' | 'SCHEDULE'>('ALL');
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

<template>
  <div class="p-3">
    <!-- 顶部：动作 -->
    <div class="mb-3 flex items-center justify-between rounded-md border border-gray-200 bg-white px-3 py-2">
      <div class="flex items-center gap-4">
        <span class="text-sm font-semibold">爬取任务工作台</span>
        <span class="text-xs text-gray-400">
          每次“爬取 / 手动录入 / 定时轮询”生成一条任务条 · 源 · 提取内容 · 渲染内容 · 过程
        </span>
      </div>
      <Space>
        <Button type="primary" @click="manualModalApi.open()">
          手动录入（测试任务）
        </Button>
        <Dropdown>
          <Button :loading="!!runCodeBusy" :disabled="runBusy || sources.length === 0">
            立即爬取
          </Button>
          <template #overlay>
            <div class="rounded-md border border-gray-200 bg-white py-1 shadow">
              <div class="px-3 py-1 text-xs text-gray-400">选择采集源（受 120s 频控）</div>
              <div
                v-for="s in sources"
                :key="s.id"
                class="cursor-pointer px-3 py-1 text-sm hover:bg-blue-50"
                @click="onRunMenu(s)"
              >
                {{ s.sourceName || s.sourceCode }}
              </div>
            </div>
          </template>
        </Dropdown>
        <Button @click="sourceOpen = true">采集源配置</Button>
        <Button @click="refreshAll">刷新</Button>
      </Space>
    </div>

    <!-- 筛选 -->
    <div class="mb-3 flex items-center gap-4 rounded-md border border-gray-200 bg-white px-3 py-2">
      <Radio.Group v-model:value="filterType" option-type="button" :options="[{ label: '全部', value: 'ALL' }, { label: '定时任务', value: 'SCHEDULE' }, { label: '测试任务', value: 'TEST' }]" />
      <Select
        v-model:value="filterSource"
        placeholder="全部采集源"
        allow-clear
        style="width: 220px"
        :options="sourceOptions"
        @change="loadSched(true)"
      />
      <Select
        v-model:value="filterStatus"
        placeholder="全部状态"
        allow-clear
        style="width: 140px"
        :options="statusOptions"
        @change="loadSched(true)"
      />
      <span v-if="schedLoading" class="text-xs text-gray-400">加载中…</span>
    </div>

    <!-- 定时任务区 -->
    <section v-show="filterType !== 'TEST'" class="mb-4">
      <div class="mb-2 flex items-center gap-2">
        <span class="text-sm font-semibold text-gray-700">定时任务</span>
        <Tag color="blue">后端 crawl-task</Tag>
        <span class="text-xs text-gray-400">调度轮询/立即爬取产生的真实任务记录</span>
      </div>
      <div v-if="schedTasks.length === 0 && !schedLoading" class="rounded-md border border-dashed border-gray-300 bg-white p-6 text-center text-sm text-gray-400">
        暂无定时任务 —— 在“采集源配置”启用一个源，或点“立即爬取”生成第一条任务条
      </div>
      <div
        v-for="t in schedTasks"
        :key="String(t.id)"
        class="mb-2 rounded-md border border-gray-200 bg-white px-3 py-2"
      >
        <div class="flex items-center gap-3">
          <Tag v-if="filterType === 'ALL' || filterType === 'SCHEDULE'" color="geekblue">定时</Tag>
          <Tag :color="tagOf(TASK_STATUS_MAP, t.status)?.color || 'default'">
            {{ tagOf(TASK_STATUS_MAP, t.status)?.text || t.status || '—' }}
          </Tag>
          <span class="flex-1 truncate text-sm text-gray-700" :title="t.url || ''">{{ t.url || '（无 URL，见过程）' }}</span>
          <span class="text-xs text-gray-400">{{ fmtDt(t.finishedAt || t.createTime) }}</span>
          <Space>
            <a class="text-xs text-blue-600" @click="openTaskProcess(t)">过程</a>
            <a v-if="t.status === 'FAILED'" class="text-xs text-red-500" :class="{ 'opacity-50': retryBusy === t.id }" @click="retryTask(t)">
              {{ retryBusy === t.id ? '重试中…' : '重试' }}
            </a>
          </Space>
        </div>
        <div v-if="t.message" class="mt-1 truncate text-xs text-gray-400" :title="t.message">{{ t.message }}</div>
        <div class="mt-1 flex items-center gap-3 text-xs text-gray-400">
          <span v-if="t.sourceCode">源：{{ t.sourceCode }}</span>
          <span v-if="t.attempt != null">尝试：{{ t.attempt }}</span>
          <span v-if="t.channelCode">通道：{{ t.channelCode }}</span>
          <span v-if="t.parserCode">解析器：{{ t.parserCode }}</span>
        </div>
      </div>
      <div v-if="!schedEnd && schedTasks.length" class="mt-1 text-center">
        <a class="text-xs text-blue-600" @click="loadSched()">{{ schedLoading ? '加载中…' : '加载更多' }}</a>
      </div>
    </section>

    <!-- 后端 TEST 任务区 -->
    <section v-if="filterType !== 'SCHEDULE' && backendTests.length" class="mb-4">
      <div class="mb-2 flex items-center gap-2">
        <span class="text-sm font-semibold text-gray-700">后端测试任务（TEST）</span>
        <Tag color="purple">crawl-task · taskType=TEST</Tag>
      </div>
      <div v-for="t in backendTests" :key="String(t.id)" class="mb-2 rounded-md border border-gray-200 bg-white px-3 py-2">
        <div class="flex items-center gap-3">
          <Tag color="purple">测试</Tag>
          <Tag :color="tagOf(TASK_STATUS_MAP, t.status)?.color || 'default'">
            {{ tagOf(TASK_STATUS_MAP, t.status)?.text || t.status || '—' }}
          </Tag>
          <span class="flex-1 truncate text-sm text-gray-700" :title="t.url || ''">{{ t.url || '手动录入' }}</span>
          <span class="text-xs text-gray-400">{{ fmtDt(t.finishedAt || t.createTime) }}</span>
          <a class="text-xs text-blue-600" @click="openTaskProcess(t)">过程</a>
        </div>
      </div>
    </section>

    <!-- 本次会话手动/测试任务条 -->
    <section v-show="filterType !== 'SCHEDULE'">
      <div class="mb-2 flex items-center gap-2">
        <span class="text-sm font-semibold text-gray-700">测试任务 · 本次会话</span>
        <Tag color="cyan">手动录入即真实解析一次</Tag>
        <span v-if="manualRuns.length" class="text-xs text-gray-400">共 {{ manualRuns.length }} 条（会话内存，刷新后清空；后端 manual-run 契约落地后持久化）</span>
      </div>
      <Empty v-if="!manualRuns.length" description="暂无手动/测试任务条 — 点右上“手动录入”，粘贴 HTML/JSON 源代码或上传 PDF/HTML/JSON 文件，即刻生成一条含 源·提取·渲染·过程 的任务条">
        <template #image><div /></template>
      </Empty>
      <article
        v-for="r in manualRuns"
        :key="r.id"
        class="mb-3 overflow-hidden rounded-lg border border-gray-200 bg-white"
      >
        <!-- 卡头 -->
        <div class="flex items-center gap-3 border-b border-gray-100 bg-gray-50 px-3 py-2">
          <Tag color="purple">测试</Tag>
          <Tag :color="manualStatusTag(r).color">{{ manualStatusTag(r).text }}</Tag>
          <span class="text-sm font-medium text-gray-800">{{ (r.resp?.title || '').trim() || '（无标题产出）' }}</span>
          <span class="flex-1" />
          <span class="text-xs text-gray-400">{{ fmtDt(r.occurredAt) }}</span>
          <a class="text-xs text-blue-600" @click="openManualProcess(r)">过程</a>
        </div>
        <div class="grid grid-cols-3 divide-x divide-gray-100">
          <!-- ① 源：源代码 / PDF 文件 -->
          <div class="p-3">
            <div class="mb-1 text-xs font-semibold text-gray-500">① 源 · 源代码 / PDF</div>
            <div class="space-y-1 text-xs text-gray-600">
              <div>输入口径：{{ r.inputType || '—' }}{{ r.fileName ? ' · ' + r.fileName : '' }}</div>
              <div v-if="r.contentLength != null">内容长度：{{ r.contentLength }} 字符</div>
              <div v-if="r.url" class="truncate" :title="r.url">URL：{{ r.url }}</div>
              <div class="pt-1 text-[11px] text-gray-400">
                原文 HTML/PDF 完整内容由后端 FileApi 落库后经 fileUrl 提供（契约 TaskRunVO.raw），本会话仅保留解析回执。
              </div>
            </div>
          </div>
          <!-- ② 提取内容 -->
          <div class="p-3">
            <div class="mb-1 text-xs font-semibold text-gray-500">② 提取内容</div>
            <div v-if="kvOf(r).length" class="space-y-1 text-xs text-gray-700">
              <div v-for="[k, v] in kvOf(r)" :key="k" class="flex gap-2">
                <span class="w-16 shrink-0 text-gray-400">{{ k }}</span>
                <span class="truncate" :title="v">{{ v }}</span>
              </div>
            </div>
            <div v-if="extractedOf(r).length" class="mt-2 border-t border-gray-100 pt-1">
              <div class="mb-1 text-[11px] font-semibold text-gray-400">抽取字段</div>
              <div v-for="[k, v] in extractedOf(r)" :key="k" class="flex gap-2 text-xs text-gray-700">
                <span class="w-20 shrink-0 text-gray-400">{{ k }}</span>
                <span class="truncate" :title="v">{{ v }}</span>
              </div>
            </div>
            <div v-if="r.resp?.warnings?.length" class="mt-2">
              <Tag v-for="(w, i) in r.resp.warnings" :key="i" color="orange" class="mt-0.5 mr-1">{{ w }}</Tag>
            </div>
          </div>
          <!-- ③ 渲染后的内容 -->
          <div class="p-3">
            <div class="mb-1 text-xs font-semibold text-gray-500">③ 渲染后的内容</div>
            <div class="docx-bounded overflow-auto" v-html="renderHtmlOf(r)"></div>
          </div>
        </div>
      </article>
    </section>

    <!-- 批次（crawl-job）折叠区 -->
    <section class="mb-4 rounded-md border border-gray-200 bg-white px-3 py-2">
      <div class="mb-1 text-sm font-semibold text-gray-700">采集批次（后端 crawl-job，只读参照）</div>
      <div v-if="!jobs.length" class="text-xs text-gray-400">暂无批次记录（一次完整爬取=一个批次，详情见契约 crawl-job）</div>
      <div v-for="j in jobs" :key="String(j.id)" class="flex items-center gap-3 border-t border-gray-50 py-1 text-xs text-gray-600">
        <Tag :color="tagOf(TASK_STATUS_MAP, j.status)?.color || 'default'">{{ tagOf(TASK_STATUS_MAP, j.status)?.text || j.status }}</Tag>
        <span>源：{{ j.sourceCode || '—' }}</span>
        <span>触发：{{ j.triggerType || '—' }}</span>
        <span class="flex-1 truncate text-gray-400">{{ j.summaryJson ? jobSummaryText(j.summaryJson) : '' }}</span>
        <span class="text-gray-400">{{ fmtDt(j.finishedAt || j.createTime) }}</span>
      </div>
    </section>

    <!-- 过程抽屉 -->
    <Drawer
      v-model:open="processOpen"
      title="任务过程"
      :width="640"
    >
      <div v-if="processTask" class="space-y-3">
        <div class="text-xs text-gray-400">
          任务主键（雪花）：{{ String(processTask.id) }}<br />
          契约落地说明：下载器/解析器/中间文件与逐步时间线将在 TaskRunVO 落库后于此完整呈现（steps_json + logs + raw/extract/render）。
        </div>
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
      <div v-else-if="processManual" class="space-y-3">
        <Descriptions :column="1" size="small" bordered>
          <Descriptions.Item label="解析器">{{ processManual.parserName ? processManual.parserName + '（' + processManual.parserCode + '）' : processManual.parserCode }}</Descriptions.Item>
          <Descriptions.Item label="输入口径">{{ [processManual.inputType, processManual.fileName].filter(Boolean).join(' · ') }}</Descriptions.Item>
          <Descriptions.Item label="来源 URL">{{ processManual.url || '—' }}</Descriptions.Item>
          <Descriptions.Item label="耗时">{{ processManual.durationMs != null ? processManual.durationMs + ' ms' : '—' }}</Descriptions.Item>
          <Descriptions.Item label="解析状态">{{ manualStatusTag(processManual).text }}</Descriptions.Item>
          <Descriptions.Item v-if="processManual.resp?.error" label="失败原因">{{ processManual.resp.error }}</Descriptions.Item>
        </Descriptions>
        <div class="text-xs font-semibold text-gray-500">过程步骤</div>
        <div class="space-y-1 text-xs text-gray-600">
          <div>1. 注入原始输入（HTML/JSON/文件）→ 成功（手动录入无网络下载）</div>
          <div>2. 解析（parserCode={{ processManual.parserCode || '—' }}）→ {{ processManual.status === 'FAILED' ? '失败' : '成功' }}</div>
          <div>3. 渲染（契约 render_html 未落地 → 前端兜底公文化排版 builtBy=FRONTEND_FALLBACK）</div>
        </div>
        <div v-if="processManual.resp?.meta && Object.keys(processManual.resp.meta).length" class="space-y-1 text-xs text-gray-600">
          <div class="text-xs font-semibold text-gray-500">解析观测（meta）</div>
          <div v-for="(mv, mk) in processManual.resp.meta" :key="mk" class="flex gap-2">
            <span class="w-44 shrink-0 truncate text-gray-400" :title="mk">{{ mk }}</span>
            <span class="truncate" :title="mv">{{ mv }}</span>
          </div>
        </div>
        <div v-if="processManual.resp?.warnings?.length" class="space-y-1 text-xs text-amber-600">
          <div class="text-xs font-semibold text-gray-500">告警（warnings）</div>
          <div v-for="(w, i) in processManual.resp.warnings" :key="i">• {{ w }}</div>
        </div>
        <div v-if="processManual.resp?.entries?.length" class="space-y-1 text-xs text-gray-600">
          <div class="text-xs font-semibold text-gray-500">列表条目（列表页输入时）</div>
          <div v-for="(en, i) in processManual.resp.entries.slice(0, 10)" :key="i">• {{ en.title }}</div>
        </div>
      </div>
      <div v-else class="text-sm text-gray-400">加载中…</div>
    </Drawer>

    <ManualModal :source-options="sourceOptions" @parsed="onParsed" />
    <SourceDrawer v-model:open="sourceOpen" @changed="() => { void loadSources(); void loadSched(true); }" />
  </div>
</template>

<style scoped>
.docx-bounded {
  max-height: 220px;
  font-family: 'SimSun', 'FangSong', serif;
  font-size: 13px;
  line-height: 1.7;
  color: #333;
  white-space: normal;
}
.docx-bounded :deep(.doc-title) {
  text-align: center;
  font-weight: 700;
  font-size: 15px;
  font-family: 'SimHei', 'SimSun', sans-serif;
  margin: 0 0 8px;
}
.docx-bounded :deep(.doc-para) {
  margin: 0 0 4px;
  text-indent: 2em;
}
.docx-bounded :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 4px 0;
}
.docx-bounded :deep(td) {
  border: 1px solid #cbd5e1;
  padding: 2px 6px;
  vertical-align: top;
}
.docx-bounded :deep(.doc-kv-key) {
  width: 90px;
  color: #64748b;
  background: #f8fafc;
}
</style>
