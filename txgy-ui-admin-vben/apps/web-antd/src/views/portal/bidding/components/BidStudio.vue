<!--
  标书工坊（Bid Studio）· 标书制作工作台（前台用户端）
  表单区：标书类型（技术/商务）+ 标书名称 + 招标/补充文件上传 + 一键生成 + 标书预览（占位）。
  一键生成：真实链路——上传招标文件登记为 TENDER 材料 → 调 task/generate 发起生成（无需选项目/标段，后端默认兜底）。
  我的标书：真实生成任务列表，查看/下载走产物地址，删除调后端 delete。
-->
<script lang="ts" setup>
import { onMounted, onUnmounted, ref, watch } from 'vue';

import { message, Modal } from 'ant-design-vue';

import { useUserStore } from '@vben/stores';

import type { PageResult } from '@vben/request';

import { useAuthStore } from '#/store';

import type { BidAgentPlatformApi } from '#/api/bidding/bidAgent/platform';
import { cancelBidTask, retryBidTask } from '#/api/bidding/bidAgent/platform';
import { uploadBiddingFile } from '#/api/bidding/file';
import { requestClient } from '#/api/request';

interface PickedFile {
  file: File;
  name: string;
  size: number;
}
type Category = 'technical' | 'business';

const CATEGORY_TEXT: Record<Category, string> = {
  technical: '技术标书',
  business: '商务标书',
};

const STATUS_TEXT: Record<string, string> = {
  PENDING: '待执行',
  RUNNING: '生成中',
  DONE: '已完成',
  FAILED: '失败',
  CANCELED: '已取消',
};

const userStore = useUserStore();
const authStore = useAuthStore();

const docName = ref('');
const docCategory = ref<Category>('technical');
const mainFiles = ref<PickedFile[]>([]);
const supFiles = ref<PickedFile[]>([]);
const generating = ref(false);

/* ---------- 上传区（拖拽 + 点击） ---------- */
/** 单文件大小上限（与后端/底座侧 60MB 上限之间留出余量） */
const MAX_FILE_MB = 50;
const ALLOWED_EXT = ['pdf', 'doc', 'docx', 'txt', 'md', 'xlsx', 'pptx'];
function fmtSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}
function extOf(name: string): string {
  const i = name.lastIndexOf('.');
  return i >= 0 ? name.slice(i + 1).toLowerCase() : '';
}
function addFiles(target: 'main' | 'sup', list: FileList | null): void {
  const arr = Array.from(list ?? []);
  if (arr.length === 0) return;
  const bucket = target === 'main' ? mainFiles : supFiles;
  arr.forEach((f) => {
    if (f.size > MAX_FILE_MB * 1024 * 1024) {
      message.warning(`「${f.name}」超过 ${MAX_FILE_MB}MB 上限，已跳过`);
      return;
    }
    const ext = extOf(f.name);
    if (!ext || !ALLOWED_EXT.includes(ext)) {
      message.warning(`「${f.name}」不是支持的文档类型（${ALLOWED_EXT.join('/')}），已跳过`);
      return;
    }
    bucket.value.push({ file: f, name: f.name, size: f.size });
  });
}
function removeFile(target: 'main' | 'sup', idx: number): void {
  const bucket = target === 'main' ? mainFiles : supFiles;
  bucket.value.splice(idx, 1);
}

/* ---------- 文件列表折叠：文件过多时只平铺前几项，其余收起 ---------- */
/** 列表默认最多平铺展示的文件数，超出部分折叠为「展开剩余 N 个文件」 */
const FILE_LIST_LIMIT = 3;
const mainFilesExpanded = ref(false);
const supFilesExpanded = ref(false);

function visibleFiles(list: PickedFile[], expanded: boolean): PickedFile[] {
  return expanded || list.length <= FILE_LIST_LIMIT ? list : list.slice(0, FILE_LIST_LIMIT);
}
function hiddenCount(list: PickedFile[], expanded: boolean): number {
  return expanded ? 0 : Math.max(0, list.length - FILE_LIST_LIMIT);
}
function onDrop(target: 'main' | 'sup', e: DragEvent): void {
  addFiles(target, e.dataTransfer?.files ?? null);
}

/* ---------- 一键生成（真实链路，无需选项目/标段） ---------- */
async function registerMaterial(file: PickedFile, docType: 'TENDER' | 'FACT'): Promise<number> {
  const uploaded = await uploadBiddingFile(file.file, 'bidding/bid-agent');
  const materialId = await requestClient.post<number>('/bidding/bid-agent/material/save', {
    docType,
    name: file.name,
    fileId: uploaded.fileId,
    fileUrl: uploaded.url,
    fileName: uploaded.name ?? file.name,
    fileSize: uploaded.size ?? file.size,
  });
  return materialId;
}

async function generate(): Promise<void> {
  if (mainFiles.value.length === 0) {
    message.warning('请先上传至少一个招标文件');
    return;
  }

  generating.value = true;
  try {
    // 1. 主文件逐一登记为 TENDER 材料（多份招标文件内容都进入知识库参与检索）
    const materialIds: number[] = [];
    for (const file of mainFiles.value) {
      materialIds.push(await registerMaterial(file, 'TENDER'));
    }
    // 2. 补充文件登记为 FACT（企业资料）材料，参与检索与写作取证
    for (const file of supFiles.value) {
      await registerMaterial(file, 'FACT');
    }
    // 3. 发起一键生成（首份主文件为解析主体；projectId/bidSectionCode 由后端默认兜底）
    const taskId = await requestClient.post<number>('/bidding/bid-agent/task/generate', {
      tenderMaterialId: materialIds[0],
      docCategory: docCategory.value,
      remark: docName.value.trim() || undefined,
    });
    message.success(`已发起生成任务 #${taskId}，可在「我的标书」查看进度`);
    await loadTasks();
  } catch (error) {
    console.error('[BidStudio][generate] 发起生成失败', error);
  } finally {
    generating.value = false;
  }
}

/* ---------- 我的标书（真实生成任务） ---------- */
const tasks = ref<BidAgentPlatformApi.GenerateTask[]>([]);
const tasksTotal = ref(0);
const loadingTasks = ref(false);
let loadTasksInFlight = false;
let lastPollErrorToastAt = 0;

async function loadTasks(options?: { poll?: boolean }): Promise<void> {
  if (loadTasksInFlight) return;
  loadTasksInFlight = true;
  if (!options?.poll) loadingTasks.value = true;
  try {
    const res = await requestClient.get<PageResult<BidAgentPlatformApi.GenerateTask>>(
      '/bidding/bid-agent/task/page',
      options?.poll
        ? { params: { pageNo: 1, pageSize: 20 }, headers: { 'X-Silent-Error': 'true' } }
        : { params: { pageNo: 1, pageSize: 20 } },
    );
    tasks.value = res.list ?? [];
    tasksTotal.value = res.total ?? 0;
    pollDelay = 3000;
  } catch (error) {
    pollDelay = Math.min(pollDelay * 2, POLL_MAX_DELAY);
    if (options?.poll) {
      // 轮询失败：全局拦截器已静默（X-Silent-Error），这里 30 秒节流提示一次，避免刷屏
      const now = Date.now();
      if (now - lastPollErrorToastAt > 30_000) {
        lastPollErrorToastAt = now;
        message.warning('任务列表刷新失败，将自动重试');
      }
    } else {
      console.error('[BidStudio][loadTasks] 任务列表加载失败', error);
    }
  } finally {
    loadingTasks.value = false;
    loadTasksInFlight = false;
  }
}

function statusText(s: string): string {
  return STATUS_TEXT[s] ?? s;
}
function fmtTime(v?: number): string {
  if (!v) return '—';
  const d = new Date(v);
  if (Number.isNaN(+d)) return '—';
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

function artifactOf(task: BidAgentPlatformApi.GenerateTask): string {
  return task.artifactUrl ?? '';
}

/** 标书显示名：优先「标书名称」（生成时填写，落在 remark），其次招标物料名，最后任务号 */
function displayName(task: BidAgentPlatformApi.GenerateTask): string {
  const remark = task.remark?.trim();
  if (remark) return remark;
  return task.tenderMaterialName || task.taskNo || '标书';
}

function downloadTask(task: BidAgentPlatformApi.GenerateTask): void {
  const url = artifactOf(task);
  if (!url) {
    message.warning('该任务尚未生成产物');
    return;
  }
  const a = document.createElement('a');
  a.href = url;
  a.download = `${displayName(task)}.docx`;
  a.rel = 'noopener';
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
}

async function deleteTask(task: BidAgentPlatformApi.GenerateTask): Promise<void> {
  const running = task.status === 'PENDING' || task.status === 'RUNNING';
  Modal.confirm({
    title: '删除标书任务',
    content: running
      ? `「${displayName(task)}」正在生成中，删除会先取消该任务，确认删除？`
      : `确认删除「${displayName(task)}」？删除后不可恢复。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await requestClient.post<boolean>('/bidding/bid-agent/task/delete', null, {
          params: { id: task.id },
        });
        message.success('已删除');
        await loadTasks();
      } catch (error) {
        console.error('[BidStudio][deleteTask] 删除失败', error);
      }
    },
  });
}

/** 取消进行中的生成任务（释放同标段槽位，之后可重新发起一键生成） */
async function cancelTask(task: BidAgentPlatformApi.GenerateTask): Promise<void> {
  try {
    await cancelBidTask(task.id);
    message.success('已取消生成任务');
    await loadTasks();
  } catch (error) {
    console.error('[BidStudio][cancelTask] 取消失败', error);
  }
}

/** 失败任务重试 */
async function retryTask(task: BidAgentPlatformApi.GenerateTask): Promise<void> {
  try {
    await retryBidTask(task.id);
    message.success('已重新发起生成');
    await loadTasks();
  } catch (error) {
    console.error('[BidStudio][retryTask] 重试失败', error);
  }
}

/* ---------- 标书预览：页内渲染章节树与正文（方案一） ---------- */
const previewTask = ref<BidAgentPlatformApi.GenerateTask | null>(null);
const previewChapters = ref<BidAgentPlatformApi.Chapter[]>([]);
const previewLoading = ref(false);
const previewError = ref('');

function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

/** 轻量 markdown → HTML：正文里的 ##/### 标题、有序/无序列表、段落。 */
function mdToHtml(md: string | null | undefined): string {
  if (!md) return '';
  const out: string[] = [];
  let list: 'ul' | 'ol' | null = null;
  const closeList = () => {
    if (list) {
      out.push(`</${list}>`);
      list = null;
    }
  };
  for (const raw of md.split('\n')) {
    const line = raw.replace(/\s+$/, '');
    if (!line) {
      closeList();
      continue;
    }
    if (line.startsWith('### ')) {
      closeList();
      out.push(`<h4>${escapeHtml(line.slice(4))}</h4>`);
      continue;
    }
    if (line.startsWith('## ')) {
      closeList();
      out.push(`<h3>${escapeHtml(line.slice(3))}</h3>`);
      continue;
    }
    if (line.startsWith('# ')) {
      closeList();
      out.push(`<h3>${escapeHtml(line.slice(2))}</h3>`);
      continue;
    }
    const ol = line.match(/^(\d+)[.、]\s*(.*)$/);
    if (ol) {
      if (list !== 'ol') {
        closeList();
        out.push('<ol>');
        list = 'ol';
      }
      out.push(`<li>${escapeHtml(ol[2])}</li>`);
      continue;
    }
    const ul = line.match(/^[-*•]\s+(.*)$/);
    if (ul) {
      if (list !== 'ul') {
        closeList();
        out.push('<ul>');
        list = 'ul';
      }
      out.push(`<li>${escapeHtml(ul[1])}</li>`);
      continue;
    }
    closeList();
    out.push(`<p>${escapeHtml(line)}</p>`);
  }
  closeList();
  return out.join('');
}

/** 章节标题：取正文首个 ## 标题，并去掉重复的 nodeId 前缀。 */
function chapterTitle(ch: BidAgentPlatformApi.Chapter): string {
  const first = (ch.content ?? '').split('\n').find((l) => l.trim().startsWith('## '));
  let title = first ? first.trim().replace(/^##\s+/, '') : ch.title || ch.nodeId;
  if (ch.nodeId) {
    title = title.replace(new RegExp(`^${ch.nodeId.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*`), '');
  }
  return title || ch.nodeId;
}

async function openPreview(task: BidAgentPlatformApi.GenerateTask): Promise<void> {
  previewTask.value = task;
  previewLoading.value = true;
  previewError.value = '';
  previewChapters.value = [];
  try {
    const resp = await requestClient.get<BidAgentPlatformApi.OutlineResp>(
      '/bidding/bid-agent/task/outline',
      { params: { id: task.id } },
    );
    previewChapters.value = (resp.chapters ?? [])
      .filter((c) => (c.content ?? '').trim().length > 0)
      .map((c) => {
        const title = chapterTitle(c);
        // 去掉正文首行的 ## 标题（已在章节头展示，避免重复）
        const content = (c.content ?? '').replace(/^##\s+[^\n]*\n?/, '').trim();
        return { ...c, title, content };
      });
    if (previewChapters.value.length === 0) {
      previewError.value = '该任务还没有章节正文（生成未完成，或产物尚未回写）。';
    }
  } catch (error) {
    previewError.value = (error as Error)?.message ?? '预览加载失败';
  } finally {
    previewLoading.value = false;
  }
}

/* ---------- 人工确认（HIL）：底座在关键阶段挂起，等待用户回填答复 ---------- */
interface PendingQuestion {
  questionId: string;
  question: string;
  options?: string[];
  required?: boolean;
  context?: string;
}

const confirmVisible = ref(false);
const confirming = ref(false);
const confirmTarget = ref<BidAgentPlatformApi.GenerateTask | null>(null);
const confirmQuestions = ref<Array<PendingQuestion & { answer: string }>>([]);

function parsePendingQuestions(task: BidAgentPlatformApi.GenerateTask): PendingQuestion[] {
  if (!task.openQuestionsJson) return [];
  try {
    const parsed = JSON.parse(task.openQuestionsJson);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function hasPending(task: BidAgentPlatformApi.GenerateTask): boolean {
  return task.status === 'RUNNING' && parsePendingQuestions(task).length > 0;
}

function openConfirm(task: BidAgentPlatformApi.GenerateTask): void {
  const qs = parsePendingQuestions(task);
  if (qs.length === 0) {
    message.warning('该任务当前没有待确认问题');
    return;
  }
  confirmTarget.value = task;
  confirmQuestions.value = qs.map((q) => ({ ...q, answer: q.options?.[0] ?? '' }));
  confirmVisible.value = true;
}

async function submitConfirm(): Promise<void> {
  const task = confirmTarget.value;
  if (!task || confirming.value) return;
  confirming.value = true;
  try {
    await requestClient.post<boolean>(
      '/bidding/bid-agent/task/confirm',
      { answers: confirmQuestions.value.map((q) => ({ questionId: q.questionId, value: q.answer })) },
      { params: { id: task.id } },
    );
    message.success('已确认，继续生成');
    confirmVisible.value = false;
    await loadTasks();
  } catch (error) {
    console.error('[BidStudio][submitConfirm] 确认失败', error);
  } finally {
    confirming.value = false;
  }
}

onMounted(() => {
  void loadTasks();
  if (!userStore.userInfo) {
    authStore.fetchUserInfo().catch(() => {
      /* 忽略 */
    });
  }
});

/* ---------- 进度实时轮询：有进行中任务时刷新；3s 起、失败翻倍退避（上限 30s） ----------
   只要还有进行中任务就持续轮询（生成时长可能超过 30 分钟，停表会把界面冻结在旧状态）；
   任务全部结束后由 watch 停止。有「待确认」问题（HIL）时放慢到 15s，避免长时间空转。 */
let pollTimer: ReturnType<typeof setTimeout> | undefined;
let pollDelay = 3000;
const POLL_MAX_DELAY = 30_000;
const POLL_DELAY_HIL = 15_000;

function hasRunning(): boolean {
  return tasks.value.some((t) => t.status === 'RUNNING' || t.status === 'PENDING');
}
function hasPendingConfirm(): boolean {
  return tasks.value.some((t) => t.status === 'RUNNING' && parsePendingQuestions(t).length > 0);
}
function schedulePoll(): void {
  if (pollTimer) return;
  pollTimer = setTimeout(() => {
    pollTimer = undefined;
    if (!hasRunning()) {
      stopPolling();
      return;
    }
    void loadTasks({ poll: true }).finally(() => schedulePoll());
  }, hasPendingConfirm() ? Math.max(pollDelay, POLL_DELAY_HIL) : pollDelay);
}
function startPolling(): void {
  schedulePoll();
}
function stopPolling(): void {
  if (pollTimer) {
    clearTimeout(pollTimer);
    pollTimer = undefined;
  }
  pollDelay = 3000;
}
watch(
  tasks,
  (v) => {
    if (v.some((t) => t.status === 'RUNNING' || t.status === 'PENDING')) {
      startPolling();
    } else {
      stopPolling();
    }
  },
  { deep: true },
);
onUnmounted(() => {
  stopPolling();
});
</script>

<template>
  <div class="studio">
    <p class="eyebrow">Tender Document</p>
    <h1 class="section-title">标书工坊</h1>
    <p class="section-sub">上传招标文件与补充文件，填入标书名称，一键生成完整标书。</p>

    <div class="desk">
      <!-- 左：工作区 -->
      <div class="card card-pad">
        <label class="field-label">标书类型</label>
        <div class="seg">
          <button type="button" :class="{ on: docCategory === 'technical' }" @click="docCategory = 'technical'">
            {{ CATEGORY_TEXT.technical }}
          </button>
          <button type="button" :class="{ on: docCategory === 'business' }" @click="docCategory = 'business'">
            {{ CATEGORY_TEXT.business }}
          </button>
        </div>

        <label class="field-label" for="docName">标书名称</label>
        <input id="docName" v-model="docName" class="text-input" type="text" placeholder="例如：南山智造产业园招标文件" />

        <div class="file-head">
          <span class="t">招标文件<span class="req">*</span></span>
          <span class="badge">{{ mainFiles.length }} 个文件</span>
        </div>
        <div
          class="dropzone"
          role="button"
          tabindex="0"
          @click="(e) => (e.currentTarget as HTMLElement).querySelector('input')?.click()"
          @keydown.enter.prevent="(e) => (e.currentTarget as HTMLElement).querySelector('input')?.click()"
          @dragover.prevent
          @drop.prevent="onDrop('main', $event)"
        >
          <div class="dz-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 16V4M8 8l4-4 4 4" />
              <path d="M4 15v3a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-3" />
            </svg>
          </div>
          <div class="dz-main">拖拽文件到此处</div>
          <div class="dz-sub">或 <b>点击选择文件</b>（pdf/doc/docx/txt/md/xlsx/pptx，≤{{ MAX_FILE_MB }}MB）</div>
          <input
            type="file"
            multiple
            hidden
            accept=".pdf,.doc,.docx,.txt,.md,.xlsx,.pptx"
            @change="addFiles('main', ($event.target as HTMLInputElement).files)"
          />
        </div>
        <div class="files">
          <div v-if="mainFiles.length === 0" class="empty-tip">还没有文件，拖拽或点击上方区域上传。</div>
          <div v-for="(f, idx) in visibleFiles(mainFiles, mainFilesExpanded)" :key="`m-${idx}-${f.name}`" class="file">
            <div class="file-ico" :class="{ pdf: extOf(f.name) === 'pdf' }" aria-hidden="true">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                <path d="M14 2v6h6" />
              </svg>
            </div>
            <div class="file-meta">
              <div class="file-name" :title="f.name">{{ f.name }}</div>
              <div class="file-size">{{ fmtSize(f.size) }}</div>
            </div>
            <button class="file-x" type="button" aria-label="移除文件" @click="removeFile('main', idx)">×</button>
          </div>
        </div>
        <div v-if="mainFiles.length > FILE_LIST_LIMIT" class="file-fold">
          <button type="button" class="file-fold-btn" @click="mainFilesExpanded = !mainFilesExpanded">
            {{ mainFilesExpanded ? '收起文件列表' : `展开剩余 ${hiddenCount(mainFiles, mainFilesExpanded)} 个文件` }}
          </button>
        </div>

        <div class="file-head">
          <span class="t">补充的文件</span>
          <span class="badge">{{ supFiles.length }} 个文件</span>
        </div>
        <div
          class="dropzone"
          role="button"
          tabindex="0"
          @click="(e) => (e.currentTarget as HTMLElement).querySelector('input')?.click()"
          @keydown.enter.prevent="(e) => (e.currentTarget as HTMLElement).querySelector('input')?.click()"
          @dragover.prevent
          @drop.prevent="onDrop('sup', $event)"
        >
          <div class="dz-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 16V4M8 8l4-4 4 4" />
              <path d="M4 15v3a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-3" />
            </svg>
          </div>
          <div class="dz-main">拖拽补充文件到此处</div>
          <div class="dz-sub">或 <b>点击选择文件</b>（选填，登记为企业资料参与检索）</div>
          <input
            type="file"
            multiple
            hidden
            accept=".pdf,.doc,.docx,.txt,.md,.xlsx,.pptx"
            @change="addFiles('sup', ($event.target as HTMLInputElement).files)"
          />
        </div>
        <div class="files">
          <div v-if="supFiles.length === 0" class="empty-tip">还没有文件，拖拽或点击上方区域上传。</div>
          <div v-for="(f, idx) in visibleFiles(supFiles, supFilesExpanded)" :key="`s-${idx}-${f.name}`" class="file">
            <div class="file-ico" :class="{ pdf: extOf(f.name) === 'pdf' }" aria-hidden="true">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                <path d="M14 2v6h6" />
              </svg>
            </div>
            <div class="file-meta">
              <div class="file-name" :title="f.name">{{ f.name }}</div>
              <div class="file-size">{{ fmtSize(f.size) }}</div>
            </div>
            <button class="file-x" type="button" aria-label="移除文件" @click="removeFile('sup', idx)">×</button>
          </div>
        </div>
        <div v-if="supFiles.length > FILE_LIST_LIMIT" class="file-fold">
          <button type="button" class="file-fold-btn" @click="supFilesExpanded = !supFilesExpanded">
            {{ supFilesExpanded ? '收起文件列表' : `展开剩余 ${hiddenCount(supFiles, supFilesExpanded)} 个文件` }}
          </button>
        </div>

        <button class="gen-btn" type="button" :disabled="generating" @click="generate">
          <span v-if="generating" class="spinner" aria-hidden="true"></span>
          <span>{{ generating ? '正在生成…' : '一键生成标书' }}</span>
        </button>
      </div>

      <!-- 右：标书预览（页内渲染章节树与正文） -->
      <div class="preview-col">
        <div class="card card-pad">
          <div class="box-head">
            <span class="t">标书预览</span>
            <span v-if="previewTask && !previewLoading && !previewError" class="pv-meta">
              {{ previewChapters.length }} 章
            </span>
          </div>

          <div v-if="previewLoading" class="preview-box">
            <p class="preview-box-t">正在加载章节…</p>
          </div>

          <div v-else-if="previewError" class="preview-box">
            <p class="preview-box-t">{{ previewError }}</p>
          </div>

          <div v-else-if="previewChapters.length === 0" class="preview-box">
            <div class="preview-box-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                <path d="M14 2v6h6" />
              </svg>
            </div>
            <p class="preview-box-t">生成完成后，在「我的标书」点「预览」查看章节内容</p>
          </div>

          <div v-else class="pv-body">
            <div v-for="ch in previewChapters" :key="ch.nodeId" class="pv-chapter">
              <div class="pv-chapter-h">
                <span class="pv-node">{{ ch.nodeId }}</span>
                <span class="pv-title">{{ ch.title }}</span>
                <span v-if="ch.status === 'review'" class="tag pending-wait">待复核</span>
              </div>
              <div class="pv-content" v-html="mdToHtml(ch.content)"></div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 我的标书列表（真实生成任务） -->
    <div class="card card-pad records-card">
      <div class="records-head">
        <span class="t">我的标书</span>
        <span class="n">{{ tasksTotal }} 份 · <a class="refresh" @click="loadTasks">刷新</a></span>
      </div>

      <div v-if="!loadingTasks && tasks.length === 0" class="records-empty">
        还没有生成任务。上传招标文件后点击「一键生成标书」。
      </div>
      <table v-else class="rec-table">
        <thead>
          <tr>
            <th>序号</th>
            <th>标书文件</th>
            <th>标段</th>
            <th>状态</th>
            <th>进度</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in tasks" :key="t.id">
            <td class="idx">{{ t.id }}</td>
            <td class="name" :title="displayName(t)">{{ displayName(t) }}</td>
            <td>{{ t.bidSectionCode || '—' }}</td>
            <td>
              <span
                class="tag"
                :class="t.status?.toLowerCase()"
                :title="t.status === 'FAILED' ? (t.errorMessage ?? undefined) : undefined"
              >
                {{ statusText(t.status) }}
              </span>
              <span v-if="hasPending(t)" class="tag pending-wait">待确认</span>
            </td>
            <td>{{ t.progress != null ? `${t.progress}%` : '—' }}</td>
            <td>{{ fmtTime(t.createTime) }}</td>
            <td class="ops">
              <a v-if="hasPending(t)" @click="openConfirm(t)">确认</a>
              <a @click="openPreview(t)">预览</a>
              <a class="ghost" @click="downloadTask(t)">下载</a>
              <a
                v-if="t.status === 'PENDING' || t.status === 'RUNNING'"
                class="ghost"
                @click="cancelTask(t)"
              >
                取消
              </a>
              <a v-if="t.status === 'FAILED' || t.status === 'CANCELED'" @click="retryTask(t)">重试</a>
              <a class="danger" @click="deleteTask(t)">删除</a>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 人工确认（HIL）弹窗 -->
    <Modal
      v-model:open="confirmVisible"
      :title="`人工确认 · 任务 #${confirmTarget?.id ?? ''}`"
      :confirm-loading="confirming"
      ok-text="确认提交"
      cancel-text="取消"
      @ok="submitConfirm"
    >
      <div v-for="(q, i) in confirmQuestions" :key="q.questionId" class="hq">
        <div class="hq-q">{{ i + 1 }}. {{ q.question }}</div>
        <div v-if="q.context" class="hq-ctx">{{ q.context }}</div>
        <div v-if="q.options?.length" class="hq-opts">
          <button
            v-for="opt in q.options"
            :key="opt"
            type="button"
            class="hq-chip"
            :class="{ on: q.answer === opt }"
            @click="q.answer = opt"
          >
            {{ opt }}
          </button>
        </div>
        <textarea
          v-model="q.answer"
          class="hq-input"
          rows="2"
          :placeholder="q.options?.length ? '或输入自定义答复' : '输入答复（如：确认 / 修改意见）'"
        ></textarea>
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.studio {
  --ink: #1f2937;
  --ink-soft: #334155;
  --muted: #64748b;
  --paper: #f8fafc;
  --card: #ffffff;
  --line: #e2e8f0;
  --primary: #2563eb;
  --primary-deep: #1d4ed8;
  --blue-soft: #60a5fa;
  --green: #16a34a;
  --red: #dc2626;
  --shadow: 0 1px 2px rgba(31, 42, 51, 0.05), 0 8px 24px rgba(31, 42, 51, 0.05);
  --serif: 'Noto Serif SC', 'Source Han Serif SC', 'Songti SC', 'STSong', SimSun, serif;
  --sans: 'Noto Sans SC', 'Source Han Sans SC', 'Microsoft YaHei', 'PingFang SC', sans-serif;
  --mono: 'SF Mono', 'JetBrains Mono', Consolas, 'Courier New', monospace;

  color: var(--ink);
  font-family: var(--sans);
  font-size: 15px;
  line-height: 1.6;
}
.studio * {
  box-sizing: border-box;
}

.eyebrow {
  font-family: var(--mono);
  font-size: 11px;
  letter-spacing: 2.5px;
  text-transform: uppercase;
  color: var(--primary);
  margin: 0 0 6px;
}
.section-title {
  font-family: var(--serif);
  font-size: 26px;
  font-weight: 700;
  margin: 0 0 6px;
  letter-spacing: 0.5px;
}
.section-sub {
  color: var(--muted);
  margin: 0 0 22px;
  max-width: 640px;
}

.desk {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr);
  gap: 20px;
  align-items: stretch;
}
@media (max-width: 960px) {
  .desk {
    grid-template-columns: 1fr;
  }
}

.card {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 14px;
  box-shadow: var(--shadow);
}
.card-pad {
  padding: 22px;
}

.field-label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-soft);
  margin: 0 0 8px;
}
.field-label .req {
  color: var(--primary);
  margin-left: 4px;
}
.text-input {
  width: 100%;
  border: 1px solid var(--line);
  border-radius: 9px;
  padding: 10px 12px;
  font-family: var(--sans);
  font-size: 14px;
  color: var(--ink);
  background: #fff;
  transition: border-color 0.18s, box-shadow 0.18s;
}
.text-input:focus {
  outline: none;
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

.seg {
  display: flex;
  gap: 8px;
  margin-bottom: 18px;
}
.seg button {
  flex: 1;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: #fff;
  color: var(--ink-soft);
  padding: 9px 0;
  font-family: var(--sans);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s, background 0.15s;
}
.seg button.on {
  border-color: var(--primary);
  color: var(--primary);
  background: #eff6ff;
}

.file-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 20px 0 10px;
}
.file-head .t {
  font-size: 14px;
  font-weight: 700;
  color: var(--ink);
}
.file-head .t .req {
  color: var(--primary);
  margin-left: 4px;
}
.badge {
  font-family: var(--mono);
  font-size: 11px;
  color: var(--muted);
  background: rgba(31, 42, 51, 0.06);
  border-radius: 999px;
  padding: 2px 9px;
}

.dropzone {
  border: 1.5px dashed var(--line);
  border-radius: 12px;
  background: #f8fafc;
  padding: 24px 18px;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.18s, background 0.18s, transform 0.12s;
  position: relative;
}
.dropzone:hover {
  border-color: var(--blue-soft);
  background: #f0f6ff;
}
.dropzone .dz-icon {
  width: 38px;
  height: 38px;
  margin: 0 auto 8px;
  color: var(--muted);
}
.dz-main {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink-soft);
}
.dz-sub {
  font-size: 13px;
  color: var(--muted);
  margin-top: 2px;
}
.dz-sub b {
  color: var(--primary);
  font-weight: 600;
}

.files {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  /* 展开大量文件时列表内部滚动，避免左卡片被撑到超高、把预览区拉出大片空白 */
  max-height: 320px;
  overflow-y: auto;
  scrollbar-width: thin;
}
.file {
  display: flex;
  align-items: center;
  gap: 12px;
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 10px 12px;
  background: #fff;
}
.file-ico {
  width: 34px;
  height: 34px;
  flex: none;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: rgba(31, 42, 51, 0.06);
  color: var(--ink-soft);
}
.file-ico.pdf {
  background: rgba(37, 99, 235, 0.1);
  color: var(--primary);
}
.file-meta {
  flex: 1;
  min-width: 0;
}
.file-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.file-size {
  font-family: var(--mono);
  font-size: 11px;
  color: var(--muted);
}
.file-x {
  border: 0;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
  width: 28px;
  height: 28px;
  border-radius: 7px;
  font-size: 16px;
  line-height: 1;
  flex: none;
}
.file-x:hover {
  background: rgba(220, 38, 38, 0.1);
  color: var(--red);
}
.empty-tip {
  font-size: 13px;
  color: var(--muted);
  padding: 6px 2px;
}
.file-fold {
  text-align: center;
  margin-top: 8px;
}
.file-fold-btn {
  border: 0;
  background: transparent;
  color: var(--primary);
  font-family: var(--sans);
  font-size: 13px;
  cursor: pointer;
  padding: 4px 10px;
  border-radius: 7px;
  transition: background 0.15s;
}
.file-fold-btn:hover {
  background: rgba(37, 99, 235, 0.08);
}

.gen-btn {
  margin-top: 24px;
  width: 100%;
  border: 0;
  border-radius: 12px;
  padding: 14px 20px;
  background: var(--primary);
  color: #fff;
  font-family: var(--serif);
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 2px;
  cursor: pointer;
  box-shadow: 0 6px 18px rgba(37, 99, 235, 0.25);
  transition: background 0.18s, transform 0.1s, box-shadow 0.18s;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}
.gen-btn:hover {
  background: var(--primary-deep);
}
.gen-btn:active {
  transform: translateY(1px);
  box-shadow: 0 3px 10px rgba(37, 99, 235, 0.2);
}
.gen-btn:disabled {
  background: #cbd5e1;
  box-shadow: none;
  cursor: default;
}
.gen-btn .spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.4);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.preview-col {
  display: flex;
  flex-direction: column;
}
.preview-col .card {
  flex: 1;
  display: flex;
  flex-direction: column;
  /* 文字区底部与预览框底部留 15px（设计要求：文字区撑满整个预览模块，不留死空白） */
  padding-bottom: 15px;
  /* 预览模块整体限高（约 90vh + 标题行与上下 padding）：与左侧表单大体等高、右侧不残留空白；
     内容再多也只在文字区内滚动，不会把卡片或页面撑高，保证文字区始终填满卡片到 15px 处 */
  max-height: calc(90vh + 80px);
}
.box-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 14px;
}
.box-head .t {
  font-family: var(--serif);
  font-size: 18px;
  font-weight: 700;
}
.preview-box {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  gap: 14px;
  min-height: 300px;
}
.preview-box-icon {
  width: 44px;
  height: 44px;
  color: var(--line);
}
.preview-box-t {
  font-size: 15px;
  color: var(--muted);
  margin: 0;
}
.pv-meta {
  font-family: var(--mono);
  font-size: 12px;
  color: var(--muted);
}
.pv-body {
  flex: 1;
  /* 滚动容器自动最小高度为 0：文字区随卡片伸缩并内部滚动，而不是把卡片/页面撑高 */
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
}
.pv-chapter {
  border-bottom: 1px solid var(--line);
  padding: 12px 0;
}
.pv-chapter:first-child {
  padding-top: 0;
}
.pv-chapter:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}
.pv-chapter-h {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.pv-node {
  font-family: var(--mono);
  font-size: 12px;
  color: var(--primary);
  background: rgba(37, 99, 235, 0.08);
  border-radius: 6px;
  padding: 2px 8px;
  flex: none;
}
.pv-title {
  font-weight: 700;
  font-size: 15px;
  color: var(--ink);
}
.pv-content {
  font-size: 13.5px;
  line-height: 1.75;
  color: var(--ink-soft);
  word-break: break-word;
}
.pv-content h3,
.pv-content h4 {
  margin: 12px 0 6px;
  color: var(--ink);
  font-weight: 700;
}
.pv-content h3 {
  font-size: 14px;
}
.pv-content h4 {
  font-size: 13.5px;
}
.pv-content p {
  margin: 6px 0;
}
.pv-content ul,
.pv-content ol {
  margin: 6px 0;
  padding-left: 20px;
}
.pv-content li {
  margin: 3px 0;
}

.records-card {
  margin-top: 20px;
}
.records-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 14px;
}
.records-head .t {
  font-family: var(--serif);
  font-size: 18px;
  font-weight: 700;
}
.records-head .n {
  font-family: var(--mono);
  font-size: 12px;
  color: var(--muted);
}
.refresh {
  color: var(--primary);
  cursor: pointer;
  text-decoration: none;
  margin-left: 8px;
}
.refresh:hover {
  text-decoration: underline;
}

.rec-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.rec-table th,
.rec-table td {
  text-align: left;
  padding: 11px 12px;
  border-bottom: 1px solid var(--line);
  white-space: nowrap;
}
.rec-table thead th {
  background: #f8fafc;
  color: var(--ink-soft);
  font-weight: 700;
  font-size: 12.5px;
}
.rec-table tbody tr:last-child td {
  border-bottom: 0;
}
.rec-table td.idx {
  font-family: var(--mono);
  color: var(--muted);
}
.rec-table td.name {
  font-weight: 600;
  color: var(--ink);
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
}
.tag {
  display: inline-block;
  font-size: 11.5px;
  font-weight: 600;
  border-radius: 999px;
  padding: 2px 10px;
  white-space: nowrap;
}
.tag.running,
.tag.pending {
  background: rgba(37, 99, 235, 0.1);
  color: var(--primary);
}
.tag.done {
  background: rgba(22, 163, 74, 0.12);
  color: var(--green);
}
.tag.failed,
.tag.canceled {
  background: rgba(220, 38, 38, 0.1);
  color: var(--red);
}
.tag.pending-wait {
  background: rgba(245, 158, 11, 0.14);
  color: #b45309;
  margin-left: 4px;
}
.hq {
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 12px;
  background: #fbfcfd;
}
.hq-q {
  font-weight: 600;
  color: var(--ink);
  white-space: pre-wrap;
  margin-bottom: 6px;
}
.hq-ctx {
  font-size: 12.5px;
  color: var(--muted);
  background: rgba(31, 42, 51, 0.04);
  border-radius: 7px;
  padding: 6px 9px;
  margin-bottom: 8px;
  white-space: pre-wrap;
}
.hq-opts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}
.hq-chip {
  border: 1px solid var(--line);
  border-radius: 999px;
  background: #fff;
  color: var(--ink-soft);
  padding: 4px 12px;
  font-size: 13px;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s, background 0.15s;
}
.hq-chip.on {
  border-color: var(--primary);
  color: var(--primary);
  background: #eff6ff;
}
.hq-input {
  width: 100%;
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 8px 10px;
  font-family: var(--sans);
  font-size: 13.5px;
  color: var(--ink);
  resize: vertical;
}
.hq-input:focus {
  outline: none;
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}
.rec-table td.ops a {
  font-size: 13px;
  color: var(--primary);
  cursor: pointer;
  text-decoration: none;
  margin-right: 12px;
}
.rec-table td.ops a:hover {
  text-decoration: underline;
}
.rec-table td.ops a.ghost {
  color: var(--muted);
}
.rec-table td.ops a.danger {
  color: var(--red);
}
.records-empty {
  color: var(--muted);
  font-size: 14px;
  padding: 10px 2px;
}

@media (prefers-reduced-motion: reduce) {
  * {
    animation-duration: 0.001s !important;
    transition-duration: 0.001s !important;
  }
}
</style>
