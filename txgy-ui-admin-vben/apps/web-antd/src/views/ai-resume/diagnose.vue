<script lang="ts" setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

import { message } from 'ant-design-vue';

import {
  type HrmPortalRecruitAgentApi,
  applyResumeOptimize,
  buildAnalyzeIdempotencyKey,
  buildOptimizeApplyKey,
  chatWithRecruitAgent,
  getRecruitAgentTask,
  submitResumeAnalyze,
  submitResumeOptimize,
} from '#/api/hrm/portal/recruit-agent';
import {
  type HrmPortalResumeApi,
  getPortalResumeList,
  isOnlineResume,
} from '#/api/hrm/portal/resume';
import {
  buildResumeVersionIdempotencyKey,
  createPortalResumeVersion,
} from '#/api/hrm/portal/resume-version';

import DiagnosisReport, {
  type ReportDetailItem,
  type ReportDimension,
} from './components/diagnosis-report.vue';
import ResumeDocument from './components/resume-document.vue';
import {
  type ChatAttachment,
  composeOutgoingMessage,
  documentToText,
  extensionOf,
  imageToDataUrl,
  isDocumentFile,
  isImageFile,
  MAX_ATTACH_SIZE,
} from './components/chat-attach';
import ResumeEditorPanel from './components/resume-editor-panel.vue';
import ResumeLayoutPanel from './components/resume-layout-panel.vue';
import ResumePickerDialog from './components/resume-picker-dialog.vue';
import ResumeTemplatePanel from './components/resume-template-panel.vue';
import { parseResumeContent } from './components/resume-content';
import {
  applySuggestion,
  locateSuggestions,
  suggestionKey,
  type FieldSuggestion,
  type SuggestionMap,
} from './components/resume-suggestions';
import {
  clearSession,
  loadSession,
  saveSession,
  sessionKey,
} from './components/resume-session';
import { DEFAULT_TEMPLATE_KEY } from './components/resume-templates';

/**
 * 简历工作区：左编辑器 / 中预览 / 右求职助手，诊断完成后右栏换成 AI 诊断报告。
 *
 * 版式对齐参考站的 /jobseeker/resume-builder：进去先弹「请选择需要诊断的简历」，
 * 选定后自动在右侧发出「AI诊断简历全文」，诊断跑完切成两栏并把报告推到右栏。
 *
 * 关于「诊断不需要职位」：HRM 的 analyze / optimize 原先强制要 postId，
 * 后端已放开（不传即按整份简历诊断，见 HrmRecruitAgentAnalyzeServiceImpl），
 * 这里两个任务都不传 postId。
 */

const route = useRoute();
const router = useRouter();

type Mode = 'analyze' | 'generate' | 'optimize';

const mode = computed<Mode>(() => {
  const raw = String(route.query.mode || '');
  return raw === 'generate' || raw === 'optimize' ? raw : 'analyze';
});

/**
 * 从请求异常里取出可读的提示。
 *
 * 不能直接用 `error.message`：vben 的 defaultResponseInterceptor 在业务码非 0 时
 * 抛的是 `Object.assign({}, response, { response })` —— 一个**普通对象**，
 * 既没有 message，String() 出来也只有 "[object Object]"。
 * 真实文案在后端 CommonResult 的 msg 里；不透出来的话，界面只显示
 * "[object Object]"，用户既不知道出了什么事，也报不出有用的信息。
 */
function errorText(error: unknown, fallback: string): string {
  if (typeof error === 'string' && error) return error;
  const anyError = error as any;
  const message =
    anyError?.response?.data?.msg ||
    anyError?.response?.data?.message ||
    anyError?.data?.msg ||
    anyError?.message ||
    anyError?.msg;
  return typeof message === 'string' && message ? message : fallback;
}

// ── 简历选择 ──
const resumes = ref<HrmPortalResumeApi.Resume[]>([]);
const listLoading = ref(false);
const pickerOpen = ref(false);
const confirming = ref(false);
const current = ref<HrmPortalResumeApi.Resume>();

/** 编辑与预览用的结构化草稿；非结构化附件只把原文塞进 summary */
const draft = reactive<Record<string, any>>({});
/** 当前简历的内容是不是结构化 JSON。决定保存版本时写草稿还是写原文 */
const structured = ref(false);
/** 原始内容，非结构化简历保存版本时原样带上 */
const rawContent = ref('');

/*
 * 工作区是否已进入。
 * 不能只看有没有 current：生成入口不挑简历也能进，那时没有 current 但有草稿。
 */
const workspaceReady = ref(false);

/*
 * ── 左栏三个页签 ──
 *
 * 模板 / 编辑 / 布局，版式对齐参考站（Image #26 那一排）。
 * 生成入口默认停在模板页：从「附件简历制作」进来的人第一件事就是挑版式；
 * 诊断与优化入口直接进编辑页，他们关心的是内容。
 */
type LeftTab = 'edit' | 'layout' | 'template';

const LEFT_TABS: Array<{ icon: string; key: LeftTab; label: string }> = [
  { icon: 'lucide:layout-grid', key: 'template', label: '简历模板' },
  { icon: 'lucide:file-pen-line', key: 'edit', label: '简历编辑' },
  { icon: 'lucide:layout-panel-top', key: 'layout', label: '页面布局' },
];

const leftTab = ref<LeftTab>('edit');

/** 当前选中的模板；模板页里点一下即生效，同时写进草稿以便保存 */
const templateKey = ref(DEFAULT_TEMPLATE_KEY);

function pickTemplate(key: string) {
  templateKey.value = key;
  draft.template = key;
}

/** 顶栏标题。生成入口没有来源简历，显示成新建 */
const workspaceTitle = computed(() =>
  current.value ? resumeLabel(current.value) : '新建简历',
);

/** 有正文才诊断得了 —— 后端在内容为空时直接拒绝，不要让他点了才报错 */
function hasContent(resume: HrmPortalResumeApi.Resume) {
  return Boolean(resume.content && resume.content.trim());
}

function resumeLabel(resume: HrmPortalResumeApi.Resume) {
  return resume.fileName?.trim() || resume.title;
}

// ── 求职助手 ──
interface Turn {
  /** 这一轮带的附件。气泡里只留名字标签，正文/图片本身不进会话——见 chat-attach 的说明 */
  attachment?: { kind: 'image' | 'resume'; name: string };
  /** 图片附件的缩略图（就是发出去的那个 data URL），只用于显示 */
  previewUrl?: string;
  /**
   * 在这一轮下面挂一个「简历诊断报告」入口。
   * 报告是右栏的一个面板，关掉以后如果没有这个入口就只能重新跑一次诊断才回得去。
   */
  reportEntry?: boolean;
  role: 'assistant' | 'user';
  text: string;
}

const turns = ref<Turn[]>([]);
const sending = ref(false);
const chatError = ref('');
const thread = ref<HTMLElement>();

// ── 对话输入与附件 ──
const chatDraft = ref('');
const chatAttachment = ref<ChatAttachment>();
const attachLoading = ref(false);
const attachStatus = ref('');
const chatFileInput = ref<HTMLInputElement>();

function chooseChatFile(kind: 'image' | 'resume') {
  const input = chatFileInput.value;
  if (!input) return;
  input.accept = kind === 'image' ? 'image/*' : '.pdf,.doc,.docx';
  input.click();
}

async function handleChatFile(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  // 先清空 value，同一个文件连选两次也要能触发 change
  input.value = '';
  if (!file) return;
  if (file.size > MAX_ATTACH_SIZE) {
    message.warning('附件不能超过 10 MB');
    return;
  }

  attachLoading.value = true;
  attachStatus.value = '正在读取文件…';
  try {
    // 按文件本身的类型分派，不看用户点的是哪个菜单项：
    // 从「图片」里挑到 PDF、从「文档」里挑到截图，都是常事
    if (isImageFile(file)) {
      attachStatus.value = '正在压缩图片…';
      chatAttachment.value = {
        dataUrl: await imageToDataUrl(file),
        kind: 'image',
        name: file.name,
      };
      return;
    }
    if (isDocumentFile(file)) {
      attachStatus.value = '正在提取正文…';
      const text = await documentToText(file);
      if (!text) {
        message.warning(
          extensionOf(file) === 'doc'
            ? '旧版 .doc 读不了，请另存为 .docx 或 PDF 再传'
            : '没能从这份文件里提取出文字。扫描件请改从「图片」发',
        );
        return;
      }
      chatAttachment.value = { file, kind: 'resume', name: file.name, text };
      return;
    }
    message.warning('只支持图片和 PDF / DOC / DOCX');
  } catch (error: any) {
    message.error(errorText(error, '附件解析失败，请稍后重试'));
  } finally {
    attachLoading.value = false;
    attachStatus.value = '';
  }
}

function clearChatAttachment() {
  chatAttachment.value = undefined;
}

/**
 * 手动发一条对话（输入框回车或点发送）。
 *
 * 与自动发出的那几条不同：这里带附件。只带附件不写字也要能发——
 * 传张简历照片一个字不说，是最常见的用法。
 */
async function submitChat() {
  const prompt = chatDraft.value.trim();
  const attachment = chatAttachment.value;
  if ((!prompt && !attachment) || sending.value || attachLoading.value) return;

  chatDraft.value = '';
  chatError.value = '';

  turns.value.push({
    role: 'user',
    text: prompt,
    // 气泡里只留名字标签；正文进消息正文、图片走 imageDataUrl，都不留在会话里
    attachment: attachment
      ? { kind: attachment.kind, name: attachment.name }
      : undefined,
    previewUrl: attachment?.kind === 'image' ? attachment.dataUrl : undefined,
  });
  // 附件在这一轮就消费掉了，先清空再发，免得失败重发时又被带上
  chatAttachment.value = undefined;

  sending.value = true;
  await scrollToBottom();
  try {
    const reply = await chatWithRecruitAgent(
      composeOutgoingMessage(prompt, attachment),
      attachment?.kind === 'image' ? attachment.dataUrl : undefined,
    );
    turns.value.push({
      role: 'assistant',
      text: reply?.reply?.trim() || '（这次没有返回内容）',
    });
  } catch (error: any) {
    chatError.value = errorText(error, '求职助手暂时联系不上，请稍后重试');
  } finally {
    sending.value = false;
    await scrollToBottom();
  }
}

async function scrollToBottom() {
  await nextTick();
  if (thread.value) thread.value.scrollTop = thread.value.scrollHeight;
}

// ── AI 任务 ──
const analyzing = ref(false);
const optimizing = ref(false);
const diagnosis = ref<HrmPortalRecruitAgentApi.AnalyzeResult>();
const optimization = ref<HrmPortalRecruitAgentApi.OptimizeResult>();
const reportOpen = ref(false);
const taskError = ref('');

/*
 * ── 字段级 AI 建议 ──
 *
 * 一次 optimize 会返回至多 8 条按字段定位的改写建议。这里按 originalValue
 * 把它们挂到各自的字段上，由编辑器逐条渲染成「忽略 / 使用」。
 *
 * 采纳结果最终走 applyDecisions 固化。那个接口**从跑优化时固化的那个版本**
 * 重新计算，所以必须把当时的 versionId / taskId 留着，否则没法提交。
 */
interface OptimizeSource {
  /** 产出这些建议的优化任务 */
  taskId: number;
  /** 跑优化前固化的那个版本，applyDecisions 以它为源 */
  versionId: number;
}

const optimizeSource = ref<OptimizeSource>();

/**
 * 已处理过的建议（点过使用或忽略），处理完就不再显示。
 *
 * 存的是 {@link suggestionKey} 拼出来的字符串而不是建议对象本身：
 * 会话要落 localStorage 以便刷新后接着看，对象过一遍 JSON 就换了引用，
 * 按引用记的话刷新之后用户点过的建议会全部复活。
 */
const handledSuggestions = ref(new Set<string>());

/**
 * 待提交的确认项。
 *
 * 比服务端的 Decision 多带一个 path：本地要用它把「使用」写回草稿，
 * 提交时只取 payload 那部分发出去。
 */
interface PendingDecision {
  path: string;
  payload: HrmPortalRecruitAgentApi.OptimizeDecisionItem;
}

const pendingDecisions = ref<PendingDecision[]>([]);
const committing = ref(false);

/** 建议 → 所在字段路径。草稿一变就重算，用户改了原文的建议会自然失配 */
const location = computed(() =>
  locateSuggestions(draft, optimization.value?.items),
);

const suggestions = computed<SuggestionMap>(() => {
  const out: SuggestionMap = {};
  for (const [path, items] of Object.entries(location.value.byPath)) {
    const left = items.filter(
      (item) => !handledSuggestions.value.has(suggestionKey(item)),
    );
    if (left.length) out[path] = left;
  }
  return out;
});

/** 还没处理的建议条数 */
const openSuggestionCount = computed(() =>
  Object.values(suggestions.value).reduce((sum, list) => sum + list.length, 0),
);

/**
 * 失效的建议条数：用户改动了原文，originalValue 对不上，建议已经不适用。
 *
 * 要排掉**已处理**的那些：点「使用」本身就会把原文替换掉，紧接着这一条也会
 * 定位失败。不排掉的话，每采纳一条，操作条上就凭空多一条「已失效」，看着像出错。
 * 只统计用户还没处理过、却已经对不上原文的。
 *
 * 这类建议不能悄悄消失 —— 用户会以为 AI 什么都没给，所以在操作条里报出来。
 */
const staleSuggestionCount = computed(() =>
  location.value.unlocated.filter(
    (item) => !handledSuggestions.value.has(suggestionKey(item)),
  ).length,
);

/**
 * 建议卡上的「使用 / 忽略」。
 *
 * 「使用」就地把建议写进草稿（中栏预览立刻变），「忽略」只记一笔不改内容 ——
 * 与后端 applyDecisions 对 REJECT 的处理一致：拒绝项不产生改动，但采纳状态照常留痕。
 */
function onSuggestionDecide(
  item: FieldSuggestion,
  decision: 'ACCEPT' | 'REJECT',
) {
  const path = location.value.pathByItem.get(item);
  if (!path) return;

  if (decision === 'ACCEPT') {
    if (!applySuggestion(draft, path, item, item.suggestedValue)) {
      message.warning('这段原文已经变了，这条建议用不上了');
      handledSuggestions.value.add(suggestionKey(item));
      return;
    }
  }

  pendingDecisions.value.push({
    path,
    payload: {
      module: item.module,
      fieldPath: item.fieldPath,
      originalValue: item.originalValue,
      suggestedValue: item.suggestedValue,
      // 拒绝 = 保持原样。服务端要求 REJECT 的 finalValue 必须等于 originalValue，
      // 填成建议值会被判为状态失真直接报错。
      finalValue:
        decision === 'ACCEPT' ? item.suggestedValue : item.originalValue,
      decision,
    },
  });
  handledSuggestions.value.add(suggestionKey(item));
}

/** 清空建议相关的全部状态；换简历、重跑优化时都要清，避免旧建议挂在新内容上 */
function resetSuggestions() {
  handledSuggestions.value = new Set();
  pendingDecisions.value = [];
  optimizeSource.value = undefined;
}

/**
 * 固化采纳结果。
 *
 * 走 applyDecisions 而不是普通保存，因为它会：把采纳情况留痕
 * （hrm_recruit_agent_optimize_item）、对接受项再过一次事实护栏、生成一个新版本
 * 而不是覆盖源版本。
 *
 * 它从**跑优化时的源版本**重算，所以带不上那之后的手工编辑。这里刻意不重新
 * 加载服务端返回的新版本：草稿保持原样，用户看到的东西一点不少，之后点「保存」
 * 照样把自己改的全部存进去。两边各管一段，谁都不会把对方悄悄抹掉。
 */
async function commitOptimize() {
  const source = optimizeSource.value;
  if (!source || !pendingDecisions.value.length || committing.value) return;

  committing.value = true;
  try {
    const versionId = await applyResumeOptimize({
      sourceVersionId: source.versionId,
      taskId: source.taskId,
      changeSummary: `确认 ${pendingDecisions.value.length} 条优化建议`,
      idempotencyKey: buildOptimizeApplyKey(),
      decisions: pendingDecisions.value.map((item) => item.payload),
    });
    const accepted = pendingDecisions.value.filter(
      (item) => item.payload.decision === 'ACCEPT',
    ).length;
    message.success(
      `已固化 ${accepted} 条采纳建议，生成新版本 #${versionId}`,
    );
    // 采纳记录已经落到服务端，本地不必再留；草稿保持不动
    pendingDecisions.value = [];
  } catch (error: any) {
    message.error(errorText(error, '固化优化结果失败，请稍后重试'));
  } finally {
    committing.value = false;
  }
}

/** 任务进行中：编辑器顶部的提示文案 */
const busyLabel = computed(() => {
  if (analyzing.value && optimizing.value) return 'AI 正在诊断并优化你的简历';
  if (analyzing.value) return 'AI 正在诊断你的简历';
  if (optimizing.value) return 'AI 正在优化你的简历';
  return '';
});

/*
 * ── 会话留存 ──
 *
 * 刷新页面不该把整轮 AI 任务重跑一遍、把提示重发一遍：用户只是刷新了一下，
 * 代价却是等 20-40 秒、再烧一次模型调用，对话还会被冲掉重来。
 *
 * 留存范围是「用户此刻看到的东西」：对话、诊断与优化结果、待确认的建议、
 * 以及草稿本身。草稿也要留 —— 只恢复对话的话，用户在上面点过的「使用」
 * 会在刷新后集体回滚，那比不恢复更让人困惑。
 */
interface WorkspaceSnapshot {
  decisions: PendingDecision[];
  diagnosis?: HrmPortalRecruitAgentApi.AnalyzeResult;
  draft: Record<string, any>;
  /** 已处理的建议键（suggestionKey 拼出来的字符串） */
  handled: string[];
  optimization?: HrmPortalRecruitAgentApi.OptimizeResult;
  optimizeSource?: OptimizeSource;
  rawContent: string;
  structured: boolean;
  templateKey: string;
  turns: Turn[];
}

/** 生成入口没有简历，用固定键；其余按简历分开，换一份简历就是另一条对话 */
const sessionId = computed(() =>
  current.value?.id === undefined ? 'blank' : `resume-${current.value.id}`,
);
const storeKey = computed(() => sessionKey(mode.value, sessionId.value));

/** 初次恢复完成前一律不写盘，见 persistSession 的说明 */
const sessionReady = ref(false);

let saveTimer: number | undefined;

/** 合并写入：草稿每敲一个字都会触发变更，不合并的话一次输入就是几十次序列化 */
function scheduleSessionSave() {
  if (saveTimer !== undefined) window.clearTimeout(saveTimer);
  saveTimer = window.setTimeout(persistSession, 400);
}

function persistSession() {
  saveTimer = undefined;
  /*
   * 恢复完成前不写。
   * applyResume 会先把草稿置成简历原样、并把 workspaceReady 置真，那一瞬间的
   * 状态和上次会话完全不同；如果此时落盘，就会把刚读到、还没接回来的会话覆盖掉。
   */
  if (!sessionReady.value || !workspaceReady.value) return;

  saveSession(storeKey.value, {
    decisions: pendingDecisions.value,
    diagnosis: diagnosis.value,
    draft: { ...draft },
    handled: [...handledSuggestions.value],
    optimization: optimization.value,
    optimizeSource: optimizeSource.value,
    rawContent: rawContent.value,
    structured: structured.value,
    templateKey: templateKey.value,
    turns: turns.value,
  } satisfies WorkspaceSnapshot);
}

watch(
  [
    turns,
    diagnosis,
    optimization,
    optimizeSource,
    handledSuggestions,
    pendingDecisions,
    templateKey,
    structured,
    rawContent,
    () => draft,
  ],
  scheduleSessionSave,
  { deep: true },
);

/** 把上次的会话接回来；返回是否接上了。接上就不再自动发提示、也不再跑任务。 */
function restoreSession(): boolean {
  const saved = loadSession<WorkspaceSnapshot>(storeKey.value);
  if (!saved?.turns?.length) return false;

  turns.value = saved.turns;
  diagnosis.value = saved.diagnosis;
  optimization.value = saved.optimization;
  optimizeSource.value = saved.optimizeSource;
  handledSuggestions.value = new Set(saved.handled || []);
  pendingDecisions.value = saved.decisions || [];
  rawContent.value = saved.rawContent || '';
  structured.value = saved.structured ?? true;
  templateKey.value = saved.templateKey || DEFAULT_TEMPLATE_KEY;
  if (saved.draft) replaceDraft(saved.draft);
  return true;
}

// ── 报告数据：把两个任务的结果拼成一张报告 ──
const reportDimensions = computed<ReportDimension[]>(
  () => diagnosis.value?.dimensions || [],
);

const reportAchievements = computed(() => {
  const parts: string[] = [];
  if (diagnosis.value?.strengths?.length) parts.push(...diagnosis.value.strengths);
  return parts;
});

const reportFurther = computed(() => {
  const parts: string[] = [];
  if (diagnosis.value?.risks?.length) parts.push(...diagnosis.value.risks);
  if (diagnosis.value?.missingInfo?.length) parts.push(...diagnosis.value.missingInfo);
  return parts;
});

/**
 * 优化详情：按模块把建议项聚起来。
 *
 * 「专家洞察」取该模块第一条建议的 reason —— 诊断结果里的 reason 就是「为什么这么改」，
 * 与逐条的改写建议天然同源；服务端没有单独的洞察字段，硬造一个会与建议脱节。
 */
const reportDetails = computed<ReportDetailItem[]>(() => {
  const items = optimization.value?.items || [];
  if (!items.length) return [];

  const byModule = new Map<string, typeof items>();
  for (const item of items) {
    const key = item.module || '其他';
    const bucket = byModule.get(key) || [];
    bucket.push(item);
    byModule.set(key, bucket);
  }

  return [...byModule.entries()].map(([module, bucket]) => ({
    module,
    insight: bucket.map((item) => item.reason).filter(Boolean),
    before: bucket.map((item) => item.originalValue).join('\n'),
    after: bucket.map((item) => item.suggestedValue).join('\n'),
  }));
});

const reportHeading = computed(() =>
  current.value ? `${resumeLabel(current.value)} · 完整简历诊断与深度优化` : '',
);

const reportSummary = computed(() => {
  if (!diagnosis.value) return '';
  return (
    diagnosis.value.strengths?.[0] ||
    diagnosis.value.suggestions?.[0]?.advice ||
    '诊断已完成，详情见下方维度与优化详情。'
  );
});

// ── 数据加载 ──
async function loadResumes() {
  listLoading.value = true;
  try {
    resumes.value = (await getPortalResumeList()) || [];
  } catch (error: any) {
    message.error(errorText(error, '简历加载失败'));
  } finally {
    listLoading.value = false;
  }
}

function applyResume(resume: HrmPortalResumeApi.Resume) {
  current.value = resume;

  // 解析规则与列表页的预览弹窗共用一份，两边对「非结构化怎么显示」不会各说各话
  const parsed = parseResumeContent(resume.content, resumeLabel(resume));
  rawContent.value = parsed.raw;
  structured.value = parsed.structured;

  replaceDraft(parsed.draft);
  // 简历里存过模板就回显，没有就用默认的
  templateKey.value = String(parsed.draft.template || DEFAULT_TEMPLATE_KEY);
  workspaceReady.value = true;
}

function replaceDraft(value: Record<string, any>) {
  for (const key of Object.keys(draft)) delete draft[key];
  Object.assign(draft, value);
}

/**
 * 生成入口：不挑来源简历，直接开一份空白草稿。
 *
 * 这个入口做的是「从零起草」，本来就没有哪份现成简历是「对的」，
 * 弹窗拦一道只会平白多一步。姓名从求职资料里带上，其余留空由用户填。
 */
function startBlankWorkspace() {
  current.value = undefined;
  rawContent.value = '';
  structured.value = true;

  turns.value = [];
  diagnosis.value = undefined;
  optimization.value = undefined;
  reportOpen.value = false;
  taskError.value = '';
  resetSuggestions();

  // 姓名能从求职资料里带出来就先带上，省得用户再打一遍；其余留空
  const online = resumes.value.find(isOnlineResume);
  const seedName = online
    ? String(parseResumeContent(online.content).draft.name || '').trim()
    : '';

  templateKey.value = DEFAULT_TEMPLATE_KEY;
  replaceDraft({ ...(seedName ? { name: seedName } : {}), template: DEFAULT_TEMPLATE_KEY });

  workspaceReady.value = true;

  // 生成页同样保留会话：刷新回来该看到刚才的对话与草稿，而不是一片空白
  if (restoreSession()) {
    leftTab.value = 'edit';
    return;
  }

  // 从「附件简历制作」进来的人第一件事是挑版式
  leftTab.value = 'template';
}

// ── 保存版本：AI 任务以固定版本为输入，先固化再送模型 ──
async function saveVersion(summary: string) {
  const content = structured.value ? JSON.stringify(draft) : rawContent.value;
  return createPortalResumeVersion({
    sourceResumeId: current.value?.id,
    createType: 1,
    changeSummary: summary,
    content,
    idempotencyKey: buildResumeVersionIdempotencyKey(`ai-ws-${Date.now()}`),
  });
}

async function pollTask(taskId: number) {
  for (let attempt = 0; attempt < 60; attempt += 1) {
    const detail = await getRecruitAgentTask(taskId);
    if (detail.status === 'SUCCEEDED' || detail.status === 'FAILED') return detail;
    await new Promise((resolve) => window.setTimeout(resolve, 1000));
  }
  throw new Error('AI 任务响应超时，请稍后重试');
}

/**
 * 跑诊断与优化。
 *
 * 两个任务并行提交：它们互不依赖，串行只会让等待时间翻倍。
 * 任一失败不阻断另一个 —— 报告里缺哪块就少显示哪块。
 */
async function runDiagnosis(versionId: number) {
  taskError.value = '';
  analyzing.value = true;
  optimizing.value = true;
  // 重跑会产出新一批建议，上一批连同它的 taskId 一起作废
  resetSuggestions();

  const analyzeJob = submitResumeAnalyze({
    resumeVersionId: versionId,
    idempotencyKey: buildAnalyzeIdempotencyKey(),
  })
    .then(pollTask)
    .then((detail) => {
      if (detail.status !== 'SUCCEEDED' || !detail.result) {
        throw new Error(
          detail.errorMessage || `诊断未完成（状态 ${detail.status}）`,
        );
      }
      diagnosis.value = detail.result as HrmPortalRecruitAgentApi.AnalyzeResult;
    })
    .finally(() => {
      analyzing.value = false;
    });

  const optimizeJob = submitResumeOptimize({
    resumeVersionId: versionId,
    style: 'PROFESSIONAL',
    idempotencyKey: buildAnalyzeIdempotencyKey(),
  })
    .then(async (taskId) => {
      const detail = await pollTask(taskId);
      if (detail.status !== 'SUCCEEDED' || !detail.result) {
        throw new Error(detail.errorMessage || '优化没有返回结果');
      }
      optimization.value = detail.result as HrmPortalRecruitAgentApi.OptimizeResult;
      // 采纳后要凭这两个编号调 applyDecisions，这里不记下来就提交不了
      optimizeSource.value = { taskId, versionId };
    })
    .finally(() => {
      optimizing.value = false;
    });

  const [analyzeOutcome, optimizeOutcome] = await Promise.allSettled([
    analyzeJob,
    optimizeJob,
  ]);
  if (analyzeOutcome.status === 'rejected') {
    taskError.value = errorText(analyzeOutcome.reason, '诊断暂时不可用');
    throw new Error(taskError.value);
  }
  /*
   * 诊断成了但优化没成：报告里少「优化详情」，编辑器里也没有逐条建议。
   * 这一条必须说出来 —— 两个任务都失败时界面至少有红字，只失败优化时却一片安静，
   * 用户等完 20 秒看到什么都没变，只会以为 AI 觉得简历没问题。
   */
  if (optimizeOutcome.status === 'rejected') {
    message.warning(
      errorText(optimizeOutcome.reason, '本次没有拿到逐条优化建议'),
    );
  }
  reportOpen.value = true;
}

/**
 * 各入口进入工作区后自动发出的话术。
 *
 * 与参考站一致：诊断入口发「AI诊断简历全文」，优化入口发「请润色简历全文」。
 * 生成走的是「从零起草」的另一条链路，不在这条对话里，留空即不自动发。
 */
const MODE_PROMPTS: Record<Mode, string> = {
  analyze: 'AI诊断简历全文',
  generate: '',
  optimize: '请润色简历全文',
};

/**
 * 诊断的固定开场白。
 *
 * 措辞定死、不等模型返回：模型每次的说法都不一样（有时省掉等待提示，
 * 有时把「20-40s」写成别的数），而这句属于流程节奏的一部分，得稳定。
 * 真正的诊断结论在任务跑完后追加到这条消息后面。
 */
const DIAGNOSE_INTRO = [
  '好的，收到你的要求。你希望我对整篇简历做一次完整的诊断和深度优化评估，不涉及压缩到一页。',
  '我先加载行业知识，然后执行诊断流程。',
  '现在执行完整诊断流程。',
  '简历诊断通常需要20-40s，完成后会总结关键问题和优化方向',
].join('\n\n');

/**
 * 自动发出一条消息并把它和助手的回复放进对话。
 *
 * 走的是求职助手的对话接口（一问一答），把简历正文拼进消息里 ——
 * 这个接口不带会话上下文，正文必须跟着这一条一起发。
 */
async function sendAutoMessage(prompt: string, appendTo?: number) {
  sending.value = true;
  chatError.value = '';
  await scrollToBottom();

  try {
    const body = rawContent.value || JSON.stringify(draft);
    const reply = await chatWithRecruitAgent(
      `${prompt}\n\n以下是我的简历全文：\n${body}`,
    );
    const text = reply?.reply || '已完成。';

    const target = appendTo === undefined ? undefined : turns.value[appendTo];
    if (target) {
      // 改数组里的那个代理对象才会触发更新；外面另存的原始对象改了界面不动
      target.text = `${target.text}\n\n以下是诊断结果：\n\n${text}`;
    } else {
      turns.value.push({ role: 'assistant', text });
    }
  } catch (error: any) {
    chatError.value = errorText(error, '求职助手暂时联系不上，请稍后重试');
  } finally {
    sending.value = false;
    await scrollToBottom();
  }
}

/**
 * 单独跑一次优化。
 *
 * 与诊断分开是因为两者的产出不同：诊断给维度评分，优化给逐条的改写建议，
 * 而报告里的「优化详情」只有后者填得上。用户在编辑器里补完内容后可以只重跑优化。
 *
 * @param options.openReport 跑完是否打开 AI 诊断报告。手动点「AI润色」时开
 *        （用户明确要结果）；从优化入口自动跑时不开，那个面板属于诊断流程。
 */
async function runOptimizeOnly(options: { openReport?: boolean } = {}) {
  if (!current.value || optimizing.value) return;
  taskError.value = '';
  optimizing.value = true;
  resetSuggestions();
  try {
    const versionId = await saveVersion('AI 工作台 · 优化前固化');
    const taskId = await submitResumeOptimize({
      resumeVersionId: versionId,
      style: 'PROFESSIONAL',
      idempotencyKey: buildAnalyzeIdempotencyKey(),
    });
    const detail = await pollTask(taskId);
    if (detail.status !== 'SUCCEEDED' || !detail.result) {
      throw new Error(detail.errorMessage || `优化未完成（状态 ${detail.status}）`);
    }
    optimization.value = detail.result as HrmPortalRecruitAgentApi.OptimizeResult;
    optimizeSource.value = { taskId, versionId };
    if (options.openReport !== false) reportOpen.value = true;
  } catch (error: any) {
    taskError.value = errorText(error, '优化暂时不可用');
    message.error(taskError.value);
  } finally {
    optimizing.value = false;
  }
}

/**
 * 起对话并跑任务。
 *
 * startWorkspace 与「重新开始」共用：两者差别只在前面怎么准备草稿，
 * 从「发提示 + 跑任务 + 挂报告入口」往后完全一样，分两份迟早会走偏。
 */
async function launchSession() {
  // 生成入口不自动跑：那是「从零起草」的另一条链路，得先让用户看清草稿
  const prompt = MODE_PROMPTS[mode.value];
  if (!prompt) return;

  const diagnosing = mode.value === 'analyze';
  turns.value.push({ role: 'user', text: prompt });

  /*
   * 诊断的固定开场白先落进对话，模型的结论稍后追加到同一条消息上。
   * 分两条气泡会让「20-40s」那句等待提示和目标结果看起来像两轮对话，
   * 实际是同一轮的「先说在做什么、再说做出来什么」。
   */
  let introIndex: number | undefined;
  if (diagnosing) {
    introIndex = turns.value.length;
    turns.value.push({ role: 'assistant', text: DIAGNOSE_INTRO });
  }

  const chatJob = sendAutoMessage(prompt, introIndex);

  try {
    const versionId = await saveVersion('AI 工作台 · 提交前固化');
    if (diagnosing) {
      // 诊断与优化一起跑：报告的维度与「优化详情」分别来自这两个任务
      await runDiagnosis(versionId);
    } else {
      // 优化入口只跑优化，并且**不弹** AI 诊断报告——
      // 那个面板是诊断流程的产物（分数、雷达都来自诊断），
      // 从优化进来硬弹一份半截报告，用户看到的是没有分数的空壳。
      await runOptimizeOnly({ openReport: false });
    }
  } catch (error: any) {
    message.error(errorText(error, 'AI 任务失败，请稍后重试'));
  }
  await chatJob;

  // 诊断成了就在对话末尾挂一个报告入口：报告关掉以后还能再打开
  if (diagnosing && diagnosis.value) {
    turns.value.push({ role: 'assistant', text: '', reportEntry: true });
  }
}

/** 选定简历后的统一入口：进工作区 → 接回上次会话或起新对话 */
async function startWorkspace(resume: HrmPortalResumeApi.Resume) {
  applyResume(resume);
  turns.value = [];
  diagnosis.value = undefined;
  optimization.value = undefined;
  reportOpen.value = false;
  taskError.value = '';
  resetSuggestions();
  // 诊断与优化进来的人关心的是内容，直接停在编辑页
  leftTab.value = 'edit';

  // 刷新回到同一份简历：接回上次的会话，不重跑任务、也不重发提示
  if (restoreSession()) return;

  await launchSession();
}

/**
 * 清掉本地留存，重新跑一遍。
 *
 * 有了「刷新即恢复」就必须有这条路：否则用户想重跑一次诊断，
 * 刷新会被恢复拦住，只能换份简历再换回来。
 *
 * 重跑用的是**当前草稿**（而不是重新从简历内容拉一份）：用户可能刚补完内容，
 * 要诊断的正是补完后的这一版。
 */
const restarting = ref(false);

const restartLabel = computed(() => {
  if (mode.value === 'generate') return '清空对话';
  return mode.value === 'optimize' ? '重新优化' : '重新诊断';
});

async function restartSession() {
  if (!workspaceReady.value || restarting.value) return;
  restarting.value = true;
  try {
    clearSession(storeKey.value);
    turns.value = [];
    diagnosis.value = undefined;
    optimization.value = undefined;
    reportOpen.value = false;
    taskError.value = '';
    resetSuggestions();
    await launchSession();
  } finally {
    restarting.value = false;
  }
}

async function onPick(resumeId: number) {
  const resume = resumes.value.find((item) => item.id === resumeId);
  if (!resume) return;
  if (!hasContent(resume)) {
    message.warning('这份简历还没有解析出正文，暂时不能诊断');
    return;
  }
  confirming.value = true;
  pickerOpen.value = false;
  try {
    await startWorkspace(resume);
  } finally {
    confirming.value = false;
    // 选完把 query 里的 resumeId 记上，刷新不丢上下文
    void router.replace({ query: { ...route.query, resumeId: String(resumeId) } });
  }
}

function openPicker() {
  pickerOpen.value = true;
}

function goBack() {
  void router.push({ name: 'AiResumeEditor' });
}

/** 保存当前草稿为新版本，供人工确认后固化 */
const saving = ref(false);
async function manualSave() {
  if (!current.value || saving.value) return;
  saving.value = true;
  try {
    await saveVersion('工作台手动保存');
    message.success('已保存为新版本');
  } catch (error: any) {
    message.error(errorText(error, '保存失败，请稍后重试'));
  } finally {
    saving.value = false;
  }
}

/**
 * 下载简历：交给浏览器打印，用户在对话框里选「另存为 PDF」。
 *
 * 不自己生成 PDF。中栏预览就是这份简历的最终版式（同一个组件、同一份草稿），
 * 交给浏览器出，导出的和用户看到的不可能出现偏差；另起一套服务端排版，
 * 迟早会演变成「下载的和看到的不一样」——那类问题最难查，因为两边各自都「对」。
 *
 * 打印范围靠 diagnose.vue 与 layouts/ai-resume.vue 里的 @media print 收窄到
 * 只剩中栏，所以这里只负责把滚动位置归零，别让打印停在半截。
 */
function downloadResume() {
  if (!workspaceReady.value) return;
  window.scrollTo(0, 0);
  window.print();
}

onMounted(async () => {
  try {
    await loadResumes();

    // 生成入口不拦截：直接从空白草稿进工作区
    if (mode.value === 'generate') {
      startBlankWorkspace();
      return;
    }

    const resumeId = Number(route.query.resumeId);
    const target = resumeId
      ? resumes.value.find((item) => item.id === resumeId)
      : undefined;

    if (target && hasContent(target)) {
      await startWorkspace(target);
      return;
    }
    // 没有指定或指定的那份没有正文 —— 按参考站先让用户挑
    pickerOpen.value = true;
  } finally {
    /*
     * 初次恢复做完才放开写盘。
     * 必须是这里而不是 nextTick：上面几个分支里有 await，nextTick 会在网络回来
     * 之前就触发，那时 applyResume 刚把草稿置成简历原样、会话还没接回来，
     * 一落盘就把上次的会话覆盖掉了。
     */
    sessionReady.value = true;
    /*
     * 放开之后立刻补存一次。
     * 前面那次防抖十有八九是白跑的：startWorkspace 要跑完二十多秒的任务，
     * 期间最后一批状态变更加上 400ms 防抖，往往先于这个 finally 到期，
     * 于是被「还没恢复完」挡掉；而之后如果不再有变更，就永远补不上了 ——
     * 表现就是跑完一轮什么都没有存下来。
     */
    scheduleSessionSave();
  }
});
</script>

<template>
  <div class="ws">
    <header class="ws-top">
      <button class="top-back" type="button" aria-label="返回我的简历" @click="goBack">
        <IconifyIcon icon="lucide:chevron-left" />
      </button>
      <img class="top-logo" src="/hrm/brand-logo.png" alt="天信招聘" />
      <div class="top-title">
        <span>{{ workspaceTitle }}</span>
        <IconifyIcon icon="lucide:square-pen" />
      </div>
      <span class="top-pages">共 {{ structured ? 1 : 1 }} 页</span>

      <div class="top-actions">
        <!--
          原来这里还有一个「同步」，没有任何点击处理，纯摆设，已删除。
          工作区现在靠会话留存自动接着上次，不需要「同步」这个动作。
        -->
        <button
          type="button"
          :disabled="!workspaceReady"
          title="导出 PDF：在打印对话框里把目标改成「另存为 PDF」，并取消勾选「页眉和页脚」"
          @click="downloadResume"
        >
          <IconifyIcon icon="lucide:download" /> 下载
        </button>
        <button
          class="is-primary"
          type="button"
          :disabled="!workspaceReady || saving"
          @click="manualSave"
        >
          <IconifyIcon icon="lucide:save" /> {{ saving ? '保存中' : '保存' }}
        </button>
      </div>
    </header>

    <div v-if="!workspaceReady" class="ws-blank">
      <IconifyIcon icon="lucide:file-search" />
      <strong>先选择一份简历</strong>
      <p>选定后会自动开始诊断。</p>
      <button type="button" @click="openPicker">选择简历</button>
    </div>

    <div v-else class="ws-body" :class="{ 'is-report': reportOpen }">
      <div v-if="!reportOpen" class="col col-editor">
        <nav class="left-tabs" role="tablist" aria-label="简历工作区">
          <button
            v-for="tab in LEFT_TABS"
            :key="tab.key"
            type="button"
            role="tab"
            :aria-selected="leftTab === tab.key"
            class="left-tab"
            :class="{ 'is-on': leftTab === tab.key }"
            @click="leftTab = tab.key"
          >
            <IconifyIcon :icon="tab.icon" />
            <span>{{ tab.label }}</span>
          </button>
        </nav>

        <div class="left-body">
          <!--
            操作条固定在面板上方不跟着滚：建议可能挂在很靠下的分区，
            翻下去处理几条之后还得能一眼看到还剩多少、以及在哪提交。
            它必须排在下面那条 v-if / v-else-if 链**之前**，插在中间会把链打断。
          -->
          <div
            v-if="
              leftTab === 'edit' &&
              (openSuggestionCount || pendingDecisions.length)
            "
            class="sug-bar"
          >
            <span class="sug-bar-text">
              <IconifyIcon icon="lucide:sparkles" />
              <template v-if="openSuggestionCount">
                还有 {{ openSuggestionCount }} 条建议待处理
              </template>
              <template v-else>
                已处理 {{ pendingDecisions.length }} 条，待固化
              </template>
            </span>
            <span v-if="staleSuggestionCount" class="sug-bar-stale">
              {{ staleSuggestionCount }} 条已失效
            </span>
            <button
              type="button"
              class="sug-bar-btn"
              :disabled="!pendingDecisions.length || committing"
              @click="commitOptimize"
            >
              {{ committing ? '固化中…' : '固化建议' }}
            </button>
          </div>

          <ResumeTemplatePanel
            v-if="leftTab === 'template'"
            :selected="templateKey"
            @confirm="leftTab = 'edit'"
            @select="pickTemplate"
            @skip="leftTab = 'edit'"
          />

          <ResumeEditorPanel
            v-else-if="leftTab === 'edit'"
            :busy-label="busyLabel"
            :content="draft"
            :suggestions="suggestions"
            @decide="onSuggestionDecide"
          />

          <ResumeLayoutPanel v-else :content="draft" />
        </div>
      </div>

      <div class="col col-doc">
        <p v-if="taskError" class="doc-error" role="alert">
          <IconifyIcon icon="lucide:triangle-alert" /> {{ taskError }}
        </p>
        <ResumeDocument :content="draft" />
      </div>

      <aside class="col col-side">
        <DiagnosisReport
          v-if="reportOpen"
          :achievements="reportAchievements"
          :details="reportDetails"
          :dimensions="reportDimensions"
          :further="reportFurther"
          :heading="reportHeading"
          :loading="analyzing || optimizing"
          :score="diagnosis?.overallScore || 0"
          :summary="reportSummary"
          @close="reportOpen = false"
        />

        <div v-else class="assistant">
          <header class="as-head">
            <span class="as-avatar">
              <IconifyIcon icon="lucide:sparkles" />
            </span>
            <span class="as-copy">
              <strong>简历助手</strong>
              <small>你的 AI 求职助手</small>
            </span>
            <!--
              对话会随刷新恢复，所以必须给一条「重来」的路：
              否则想重跑一次诊断，除了换份简历再换回来没有别的办法。
            -->
            <button
              v-if="workspaceReady"
              type="button"
              class="as-restart"
              :disabled="restarting || sending"
              :title="restartLabel"
              :aria-label="restartLabel"
              @click="restartSession"
            >
              <IconifyIcon :icon="restarting ? 'lucide:loader-circle' : 'lucide:rotate-ccw'" :class="{ spin: restarting }" />
              <span>{{ restarting ? '进行中' : restartLabel }}</span>
            </button>
          </header>

          <div ref="thread" class="as-thread">
            <div v-for="(turn, index) in turns" :key="index" class="turn" :class="`is-${turn.role}`">
              <div
                v-if="turn.text || turn.attachment"
                class="bubble"
                :class="`is-${turn.role}`"
              >
                <p v-if="turn.text">{{ turn.text }}</p>
                <!-- 图片直接把发出去的那张缩略图显示出来；文档只留文件名 -->
                <img
                  v-if="turn.previewUrl"
                  class="bubble-img"
                  :src="turn.previewUrl"
                  :alt="turn.attachment?.name || '附件图片'"
                />
                <span v-else-if="turn.attachment" class="bubble-file">
                  <IconifyIcon icon="lucide:file-text" />
                  <span>{{ turn.attachment.name }}</span>
                </span>
              </div>
              <button
                v-if="turn.reportEntry"
                class="report-entry"
                type="button"
                @click="reportOpen = true"
              >
                <IconifyIcon icon="lucide:file-text" />
                <span>简历诊断报告</span>
              </button>
            </div>
            <div v-if="sending" class="turn is-assistant">
              <p class="typing">正在思考…</p>
            </div>
            <p v-if="chatError" class="as-error" role="alert">{{ chatError }}</p>
          </div>

          <footer class="as-foot">
            <!-- 待发送的附件：只显示名字/缩略图，正文不在这儿展开 -->
            <div v-if="chatAttachment || attachLoading" class="composer-attach">
              <span v-if="attachLoading" class="attach-chip is-loading">
                <IconifyIcon icon="lucide:loader-circle" class="spin" />
                {{ attachStatus || '正在读取…' }}
              </span>
              <span v-else-if="chatAttachment" class="attach-chip">
                <IconifyIcon
                  :icon="chatAttachment.kind === 'image' ? 'lucide:image' : 'lucide:file-text'"
                />
                {{ chatAttachment.name }}
                <button type="button" aria-label="移除附件" @click="clearChatAttachment">
                  <IconifyIcon icon="lucide:x" />
                </button>
              </span>
            </div>
            <form class="composer" @submit.prevent="submitChat">
              <button
                class="icon-btn"
                type="button"
                title="上传图片"
                aria-label="上传图片"
                :disabled="sending"
                @click="chooseChatFile('image')"
              >
                <IconifyIcon icon="lucide:image-plus" />
              </button>
              <button
                class="icon-btn"
                type="button"
                title="上传文档（PDF / DOC / DOCX）"
                aria-label="上传文档"
                :disabled="sending"
                @click="chooseChatFile('resume')"
              >
                <IconifyIcon icon="lucide:paperclip" />
              </button>
              <input
                v-model="chatDraft"
                type="text"
                placeholder="可以继续问我问题哦"
                :disabled="sending"
              />
              <button
                class="send-btn"
                type="submit"
                title="发送"
                aria-label="发送"
                :disabled="sending || (!chatDraft.trim() && !chatAttachment)"
              >
                <IconifyIcon icon="lucide:arrow-up" />
              </button>
            </form>
            <input
              ref="chatFileInput"
              type="file"
              style="display: none"
              @change="handleChatFile"
            />
            <p class="as-note">内容由AI生成，请仔细甄别</p>
          </footer>
        </div>
      </aside>
    </div>

    <ResumePickerDialog
      :action="mode === 'optimize' ? '优化' : '诊断'"
      :confirming="confirming"
      :loading="listLoading"
      :open="pickerOpen"
      :resumes="resumes"
      @cancel="pickerOpen = false"
      @confirm="onPick"
      @replace="openPicker"
      @upload="openPicker"
    />
  </div>
</template>

<style scoped>
/*
 * 固定一屏高，而不是 min-height。
 *
 * 用 min-height 时容器会长过视口、整页出现滚动条，左中右三栏就成了一条长内容的
 * 三列——想看右侧对话得先把中间那页简历滚到底。钉死高度后，滚动分别落在每一栏里。
 */
.ws {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
  background: #f6f7f5;
}

/* ── 顶栏 ── */
.ws-top {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 12px;
  height: 52px;
  padding: 0 16px;
  border-bottom: 1px solid #ececec;
  background: #fff;
}

.top-back {
  display: flex;
  padding: 4px;
  border: 0;
  background: transparent;
  color: #5e5e5e;
  font-size: 18px;
  cursor: pointer;
}

.top-logo {
  width: 26px;
  height: 26px;
  object-fit: contain;
}

.top-title {
  display: flex;
  align-items: center;
  gap: 5px;
  overflow: hidden;
  color: #292929;
  font-size: 15px;
  font-weight: 600;
}

.top-title span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.top-pages {
  flex-shrink: 0;
  color: #949494;
  font-size: 13px;
}

.top-actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

.top-actions button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 32px;
  padding: 0 14px;
  border: 1px solid #ececec;
  border-radius: 999px;
  background: #fff;
  color: #5e5e5e;
  font-size: 13px;
  cursor: pointer;
}

.top-actions button:disabled {
  color: #c4c4c4;
  cursor: not-allowed;
}

.top-actions .is-primary {
  border-color: #141414;
  background: #141414;
  color: #fff;
}

.top-actions .is-primary:disabled {
  border-color: rgb(0 0 0 / 10%);
  background: rgb(0 0 0 / 10%);
  color: rgb(0 0 0 / 30%);
}

/* ── 主体三栏 / 两栏 ── */
/*
 * 三栏定宽：440 / 800 / 430。
 *
 * 中间用 minmax(800px, 1fr) 而不是死值 800px —— 屏幕更宽时让简历页多占一点，
 * 不会在右侧留一片空白；屏幕不够宽时这三栏（至少 1670px）反过来要求横向滚动，
 * 所以下面开了 overflow-x。
 */
.ws-body {
  display: grid;
  flex: 1;
  grid-template-columns: 440px minmax(800px, 1fr) 430px;
  /* 三栏各自的滚动条要工作，这一层必须先被压住高度：
     grid 项默认 min-height:auto，不写 0 的话内容会把行顶高，滚动又跑回整页 */
  min-height: 0;
  overflow-x: auto;
  overflow-y: hidden;
}

/* 诊断完成：编辑器让位，预览与报告两栏平分。右侧保持与助手栏同宽，切换时不跳动 */
.ws-body.is-report {
  grid-template-columns: minmax(800px, 1fr) 430px;
}

.col {
  min-height: 0;
  height: 100%;
  overflow-y: auto;
  /* 三栏并排时各自留出滚动条宽度，避免内容被压到滚动条下面 */
  scrollbar-gutter: stable;
}

/*
 * 左栏改成「页签条 + 可滚动内容」两段。
 * 滚动交给里层的面板（模板页自己带固定底栏，编辑页整页滚），
 * 所以这一层设 overflow: hidden，否则会出现两条滚动条。
 */
.col-editor {
  display: flex;
  flex-direction: column;
  border-right: 1px solid #ececec;
  background: #fbfbfa;
  overflow: hidden;
}

.left-tabs {
  display: flex;
  flex-shrink: 0;
  gap: 4px;
  padding: 8px 10px;
  border-bottom: 1px solid #ececec;
  background: #fff;
}

.left-tab {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 12px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: #8a8a8a;
  font-size: 13px;
  cursor: pointer;
  transition: 0.2s ease-in-out;
}

.left-tab:hover {
  color: #4e6840;
}

.left-tab.is-on {
  background: #f2f5ee;
  color: #1c2c1b;
  font-weight: 500;
}

.left-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

/* ── 建议操作条 ── */
.sug-bar {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid #dde8d2;
  background: #f2f6ee;
}

.sug-bar-text {
  display: inline-flex;
  flex: 1;
  align-items: center;
  gap: 6px;
  min-width: 0;
  color: #4e6840;
  font-size: 12px;
}

.sug-bar-stale {
  flex-shrink: 0;
  color: #a8a8a8;
  font-size: 12px;
}

.sug-bar-btn {
  flex-shrink: 0;
  padding: 4px 12px;
  border: 1px solid #6e8d57;
  border-radius: 6px;
  background: #6e8d57;
  color: #fff;
  font-size: 12px;
  cursor: pointer;
  transition: 0.2s ease-in-out;
}

.sug-bar-btn:hover:not(:disabled) {
  border-color: #5e7a49;
  background: #5e7a49;
}

.sug-bar-btn:disabled {
  border-color: #d5d5d5;
  background: #d5d5d5;
  cursor: not-allowed;
}

/* ── 页面布局页 ── */
.layout-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px 16px;
}

.layout-field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.layout-field > span {
  color: #5e5e5e;
  font-size: 13px;
}

.layout-field select {
  min-width: 120px;
  padding: 6px 10px;
  border: 1px solid #e6e6e6;
  border-radius: 6px;
  background: #fff;
  color: #292929;
  font-family: inherit;
  font-size: 13px;
}

.layout-switch {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #5e5e5e;
  font-size: 13px;
  cursor: pointer;
}

.layout-note {
  margin: 2px 0 0;
  color: #a8a8a8;
  font-size: 12px;
  line-height: 1.7;
}

/* 左右留白交给文档本身的居中与内边距，这里不再加一层，
   否则 800px 的栏只剩 760px 给简历页 */
.col-doc {
  padding: 0;
}

/*
 * 右栏自己不滚：里面的对话区和报告正文各自带滚动条，
 * 外层再滚一次会出现两条并排的滚动条，滚哪个都别扭。
 */
.col-side {
  border-left: 1px solid #ececec;
  background: #fff;
  overflow: hidden;
}

.doc-error {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 16px 0 0;
  padding: 8px 12px;
  border-radius: 8px;
  background: #fdf3f0;
  color: #c2703f;
  font-size: 13px;
}

/* ── 空态 ── */
.ws-blank {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #9a9a9a;
}

.ws-blank svg {
  font-size: 34px;
  color: #c3ccc0;
}

.ws-blank strong {
  color: #292929;
  font-size: 16px;
}

.ws-blank p {
  margin: 0;
  font-size: 13px;
}

.ws-blank button {
  margin-top: 6px;
  padding: 8px 20px;
  border: 0;
  border-radius: 999px;
  background: #141414;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}

/* ── 求职助手 ── */
.assistant {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.as-head {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 9px;
  padding: 16px 18px 12px;
  border-bottom: 1px solid #f0f0f0;
}

/* 「重新诊断 / 重新优化」：靠右，平时不抢眼，需要重来时找得到 */
.as-restart {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  gap: 4px;
  margin-left: auto;
  padding: 3px 10px;
  border: 1px solid #e2e2e2;
  border-radius: 999px;
  background: #fff;
  color: #6f6f6f;
  font-size: 12px;
  cursor: pointer;
  transition: 0.2s ease-in-out;
}

.as-restart:hover:not(:disabled) {
  border-color: #9ab583;
  background: #f7faf4;
  color: #4e6840;
}

.as-restart:disabled {
  color: #bdbdbd;
  cursor: not-allowed;
}

.as-restart .spin {
  animation: as-spin 1s linear infinite;
}

@keyframes as-spin {
  to {
    transform: rotate(360deg);
  }
}

.as-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #f2f6ee;
  color: #4e6840;
}

.as-copy {
  display: flex;
  flex-direction: column;
}

.as-copy strong {
  color: #292929;
  font-size: 16px;
  font-weight: 600;
  line-height: 22px;
}

.as-copy small {
  color: #5e5e5e;
  font-size: 13px;
  line-height: 22px;
}

.as-thread {
  flex: 1;
  min-height: 0;
  padding: 14px 18px;
  overflow-y: auto;
}

/* 一轮里可能同时有气泡和报告入口卡片，所以是纵向排列而不是单行 */
.turn {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}

.turn.is-user {
  align-items: flex-end;
}

.turn.is-assistant {
  align-items: flex-start;
}

/* 气泡包住正文与附件，两者要一起贴着同一块底色 */
.bubble {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 92%;
  padding: 9px 13px;
  border-radius: 12px;
}

.turn.is-user .bubble {
  max-width: 88%;
  background: #141414;
  color: #fff;
}

.turn.is-assistant .bubble {
  background: #f5f6f4;
  color: #292929;
}

/* 只带附件不带文字时，气泡里没有 p，靠这段撑住内边距 */
.bubble p {
  margin: 0;
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
}

.bubble-img {
  max-width: 100%;
  max-height: 180px;
  border-radius: 8px;
  object-fit: cover;
}

.bubble-file {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.turn.is-user .bubble-file {
  color: rgb(255 255 255 / 90%);
}

/* 对话里的「简历诊断报告」入口：点开右栏那份报告 */
.report-entry {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 9px 14px;
  border: 1px solid #ececec;
  border-radius: 10px;
  background: #fff;
  color: #292929;
  font-size: 13px;
  cursor: pointer;
  transition: 0.2s ease-in-out;
}

.report-entry:hover {
  border-color: #cbd7bd;
  background: #f7faf4;
  color: #4e6840;
}

.typing {
  color: #9a9a9a !important;
}

.as-error {
  margin: 0;
  color: #c2703f;
  font-size: 13px;
}

.as-foot {
  flex-shrink: 0;
  padding: 0 18px 14px;
}

/* ── 待发送的附件 ── */
.composer-attach {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.attach-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  max-width: 100%;
  padding: 4px 8px;
  border-radius: 8px;
  background: #f2f6ee;
  color: #4e6840;
  font-size: 12px;
}

.attach-chip.is-loading {
  color: #8a8a8a;
}

.attach-chip button {
  display: flex;
  padding: 0;
  border: 0;
  background: transparent;
  color: #8aa377;
  cursor: pointer;
}

.attach-chip button:hover {
  color: #d8504e;
}

/* ── 输入区 ── */
.composer {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 8px;
  border: 1px solid #ececec;
  border-radius: 12px;
  transition: border-color 0.2s ease-in-out;
}

.composer:focus-within {
  border-color: #cbd7bd;
}

.composer input {
  flex: 1;
  min-width: 0;
  padding: 4px 2px;
  border: 0;
  background: transparent;
  color: #292929;
  font-family: inherit;
  font-size: 13px;
  outline: none;
}

.composer .icon-btn {
  display: flex;
  flex-shrink: 0;
  padding: 5px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #9a9a9a;
  font-size: 16px;
  cursor: pointer;
  transition: 0.2s ease-in-out;
}

.composer .icon-btn:hover:not(:disabled) {
  background: #f2f6ee;
  color: #4e6840;
}

.composer .icon-btn:disabled {
  color: #d4d4d4;
  cursor: not-allowed;
}

.send-btn {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: 50%;
  background: #141414;
  color: #fff;
  font-size: 15px;
  cursor: pointer;
  transition: 0.2s ease-in-out;
}

.send-btn:hover:not(:disabled) {
  background: #2e2e2e;
}

.send-btn:disabled {
  background: #e6e6e6;
  color: #b4b4b4;
  cursor: not-allowed;
}

.as-note {
  margin: 8px 0 0;
  color: #c4c4c4;
  font-size: 12px;
  text-align: center;
}

/*
 * 响应式：窄屏把三栏叠成一列。
 * 断点跟着左右两栏的定宽走：440 + 430 = 870，再留 400 给中间的简历页，
 * 所以低于 1270 就该摞起来，否则中间那页会被压成一条窄缝。
 */
@media (max-width: 1270px) {
  .ws-body,
  .ws-body.is-report {
    grid-template-columns: minmax(0, 1fr);
  }

  .col {
    overflow-y: visible;
  }

  .col-editor,
  .col-side {
    border: 0;
    border-top: 1px solid #ececec;
  }

  .assistant {
    height: auto;
    min-height: 420px;
  }
}

/*
 * ── 打印：只出简历 ──
 *
 * 「下载」走浏览器打印（window.print），所以打印出来什么，完全由这里决定。
 * 工作区的顶栏和左右两栏在屏幕上各有作用，打出来只是噪音；中栏的 .doc-page
 * 本身就是一份 800px 宽的 A4 版式，清掉屏幕专属的装饰后正好一页。
 */
@media print {
  .ws-top,
  .col-editor,
  .col-side,
  .ws-blank,
  .sug-bar {
    display: none !important;
  }

  /* 屏幕上「撑满视口 + 各栏各自滚动」的布局到打印时全是障碍，恢复成普通文档流 */
  .ws,
  .ws-body,
  .col-doc {
    display: block !important;
    height: auto !important;
    max-height: none !important;
    overflow: visible !important;
    background: #fff !important;
  }

  .resume-doc {
    padding: 0 !important;
  }

  /*
   * zoom 是屏幕上的预览缩放（窄栏里也能看全整页），打印按 1:1 出。
   * 页面留白交给下面的 @page，这里的内边距要清掉，否则会叠加成双重边距。
   */
  .resume-doc :deep(.doc-page) {
    zoom: 1 !important;
    width: 100% !important;
    padding: 0 !important;
    border-radius: 0 !important;
    box-shadow: none !important;
  }
}

@page {
  margin: 14mm 12mm;
}
</style>
