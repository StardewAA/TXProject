<script lang="ts" setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';
import { useUserStore } from '@vben/stores';

import { message, Modal } from 'ant-design-vue';

import {
  getPortalRecruitApplicationPage,
  getPortalRecruitApplication,
  getPortalRecruitPost,
  getPortalRecruitPostPage,
  getPortalRecruitPostTypeList,
  withdrawPortalRecruitApplication,
  type HrmPortalRecruitApi,
} from '#/api/hrm/portal/recruit';
import { uploadHrmRecruitFile } from '#/api/hrm/portal/recruit/file';
import { getHrmRecruitFilePreviewUrl } from '#/api/hrm/portal/recruit/file-url';
import {
  createPortalResume,
  deletePortalResume,
  getPortalResumeList,
  getRecruitProfileResume,
  isOnlineResume,
  type HrmPortalResumeApi,
  RECRUIT_PROFILE_TITLE,
  uploadPortalResume,
} from '#/api/hrm/portal/resume';
import {
  buildResumeVersionIdempotencyKey,
  createPortalResumeVersion,
  getPortalResumeVersion,
  getPortalResumeVersionList,
  type HrmPortalResumeVersionApi,
} from '#/api/hrm/portal/resume-version';
import {
  applyResumeOptimize,
  buildAnalyzeIdempotencyKey,
  buildOptimizeApplyKey,
  buildGenerateIdempotencyKey,
  chatWithRecruitAgent,
  getRecruitAgentTask,
  submitResumeAnalyze,
  submitResumeOptimize,
  submitResumeGenerate,
  type HrmPortalRecruitAgentApi,
} from '#/api/hrm/portal/recruit-agent';
import { getAreaTree } from '#/api/system/area';
import {
  HrmRecruitPostEducation,
  HrmRecruitPostEducationOptions,
  HrmRecruitWorkTime,
} from '#/views/hrm/utils/constants';
import CityPicker from '#/views/portal/recruit/components/city-picker.vue';
import { intakeResumeFile } from '#/views/portal/recruit/components/resume-intake';

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
import ChatHistory from './components/chat-history.vue';
import ResumeCard from './components/resume-card.vue';
import ResumePreviewDialog from './components/resume-preview-dialog.vue';

type WorkspaceKey =
  | 'applications'
  | 'home'
  | 'job'
  | 'profile'
  | 'recommend'
  | 'resume'
  | 'settings';

type ProfileData = {
  city?: string;
  education?: string;
  email?: string;
  expectedCities?: string[];
  expectedPostName?: string;
  experiences?: Array<Record<string, unknown>>;
  gender?: number;
  identity?: string;
  major?: string;
  mobile?: string;
  name?: string;
  projects?: Array<Record<string, unknown>>;
  school?: string;
  skills?: string;
  strengths?: string;
};

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();

const navItems: Array<{
  key: WorkspaceKey;
  label: string;
  icon: string;
  routeName: string;
}> = [
  { key: 'home', label: '求职助理', icon: 'lucide:message-square-more', routeName: 'AiResumeHome' },
  { key: 'recommend', label: '职位推荐', icon: 'lucide:briefcase-business', routeName: 'AiResumeRecommend' },
  { key: 'profile', label: '个人档案', icon: 'lucide:id-card', routeName: 'AiResumeProfile' },
  { key: 'resume', label: 'AI 简历', icon: 'lucide:sparkles', routeName: 'AiResumeEditor' },
  { key: 'settings', label: 'Agent 设置', icon: 'lucide:settings-2', routeName: 'AiResumeSettings' },
  { key: 'applications', label: '投递记录', icon: 'lucide:file-check-2', routeName: 'AiResumeApplications' },
];

const routeToKey: Record<string, WorkspaceKey> = {
  AiResumeApplications: 'applications',
  AiResumeEditor: 'resume',
  AiResumeHome: 'home',
  AiResumeJob: 'job',
  AiResumeProfile: 'profile',
  AiResumeRecommend: 'recommend',
  AiResumeSettings: 'settings',
};

const currentKey = computed<WorkspaceKey>(() => {
  const key = routeToKey[String(route.name)];
  return key || 'resume';
});
const visibleNavItems = computed(() => navItems);
const sidebarCollapsed = ref(false);

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value;
}

/*
 * 侧栏那个称呼。
 *
 * 优先取求职资料里填的姓名：账号昵称多数就是登录名（ackdisk 这种），
 * 在简历站里顶着个登录名显示对人没有意义。资料没填才退回昵称/用户名。
 *
 * profile 在下面才声明，这里靠 computed 的惰性求值避开暂时性死区——
 * 取值发生在首次渲染时，那时 profile 早已初始化。
 */
const displayName = computed(
  () =>
    String(profile.name || '').trim() ||
    userStore.userInfo?.nickname ||
    userStore.userInfo?.username ||
    '求职者',
);
const initials = computed(() => displayName.value.slice(0, 1).toUpperCase());

const resumes = ref<HrmPortalResumeApi.Resume[]>([]);
const jobs = ref<HrmPortalRecruitApi.Post[]>([]);
const applications = ref<HrmPortalRecruitApi.Application[]>([]);
const applicationDetail = ref<HrmPortalRecruitApi.Application>();
const applicationDetailLoading = ref(false);
const selectedPost = ref<HrmPortalRecruitApi.Post>();
const selectedResumeId = ref<number>();
const onlineResume = ref<HrmPortalResumeApi.Resume>();
const profile = reactive<ProfileData>({});
const pageLoading = ref(false);
const jobsLoading = ref(false);
const applicationsLoading = ref(false);
const uploadLoading = ref(false);
const uploadInput = ref<HTMLInputElement>();
/*
 * ── 对话状态 ──
 *
 * 两个页面各存一份。求职助理问的是「我该找什么工作」，职位推荐右侧问的是
 * 「这几个岗位怎么样」，早先共用一条线程，两边的问答混在一起谁都读不顺。
 *
 * 按页写进 localStorage（键里带页面名），刷新不丢；每页保留最近若干条，
 * 右上角的历史记录就是这份列表。存本地而不是后端：这个端点本来就是无状态的一问一答，
 * 没有会话表；要跨设备同步才需要落库，那是另一件事。
 */
type ChatRole = 'assistant' | 'user';

interface ChatTurn {
  role: ChatRole;
  text: string;
  /** 该轮附带的岗位卡片；只有助手在列岗位时才有，用户那侧永远没有 */
  jobs?: HrmPortalRecruitAgentApi.ChatJob[];
  /**
   * 这一轮带的附件。
   * 只存文件名和类型用来画标签 —— 解析出来的正文不落本地：
   * 一段简历正文好几千字，一个对话存二十轮会把 localStorage 撑爆，
   * 而它本来也只在发送那一次有用（这个端点是一问一答，不带上下文）。
   */
  attachment?: { kind: 'image' | 'resume'; name: string };
  /**
   * 图片附件的缩略图（就是发出去的那个 data URL）。
   * **只在内存里**：一张图二十几万字符，二十轮攒下来能把 localStorage 撑爆，
   * 所以保存本地记录时会剥掉。代价是刷新后历史里只剩文件名。
   */
  previewUrl?: string;
  /**
   * 简历附件提取出的正文，给「在线查看」用。
   * 和 previewUrl 一样只在内存里，保存本地记录时剥掉。
   */
  attachmentText?: string;
  /**
   * 原文件的 object URL，用于「打开原文件」—— PDF 直接走浏览器自带的阅读器。
   * 和另外两个一样只在内存里：blob URL 一刷新就失效，存进本地记录没有意义。
   */
  fileUrl?: string;
}

interface ChatConversation {
  id: string;
  /** 标题取第一句用户的话，列表里好认 */
  title: string;
  turns: ChatTurn[];
  updatedAt: number;
}

interface ChatState {
  /** 当前对话编号；为空表示「下一条消息开一段新的」 */
  activeId: null | string;
  /** 最近的排在最前 */
  conversations: ChatConversation[];
  draft: string;
  error: string;
  sending: boolean;
}

/** 只有这两页有对话 */
type ChatPage = 'home' | 'recommend';

const CHAT_STORAGE_PREFIX = 'hrm-ai-resume-chat:';
const MAX_CONVERSATIONS = 20;
/** 标题截断长度：列表里一行放得下，又不至于看不出问的是什么 */
const CHAT_TITLE_LIMIT = 18;

function createChatState(): ChatState {
  return { activeId: null, conversations: [], draft: '', error: '', sending: false };
}

const chatStates = reactive<Record<ChatPage, ChatState>>({
  home: createChatState(),
  recommend: createChatState(),
});

/** 其余页面（简历、档案…）不渲染对话，统一落到 home 那份，不会有人看见 */
const chatPage = computed<ChatPage>(() =>
  currentKey.value === 'recommend' ? 'recommend' : 'home',
);
const chat = computed(() => chatStates[chatPage.value]);
const activeConversation = computed(() =>
  chat.value.conversations.find((item) => item.id === chat.value.activeId),
);
/**
 * 当前对话的消息轮次。
 * 注意轮次挂在 conversation 上，不在 ChatState 上 —— 喊「开始新对话」之后
 * activeId 会被置空，这时没有当前对话，要退回空数组而不是 undefined。
 */
const chatTurns = computed(() => activeConversation.value?.turns ?? []);
/** 有对话内容（或正在等回复）时，中间区域显示对话流，否则显示三张状态卡 */
const chatActive = computed(() => Boolean(activeConversation.value) || chat.value.sending);

/*
 * 本地记录的形状校验。
 * localStorage 是用户能随手改的地方，一条结构不对的记录不该让整块对话打不开。
 */
function isChatTurn(value: unknown): value is ChatTurn {
  if (!value || typeof value !== 'object') return false;
  const turn = value as ChatTurn;
  return (
    (turn.role === 'assistant' || turn.role === 'user') &&
    typeof turn.text === 'string' &&
    // jobs 可以没有；有就必须是数组，否则模板里的 v-for 会拿字符串去遍历
    (turn.jobs === undefined || Array.isArray(turn.jobs))
  );
}

function isChatConversation(value: unknown): value is ChatConversation {
  if (!value || typeof value !== 'object') return false;
  const item = value as ChatConversation;
  return (
    typeof item.id === 'string' &&
    typeof item.title === 'string' &&
    typeof item.updatedAt === 'number' &&
    Array.isArray(item.turns) &&
    item.turns.every(isChatTurn)
  );
}

function loadChatState(page: ChatPage) {
  try {
    const raw = localStorage.getItem(CHAT_STORAGE_PREFIX + page);
    if (!raw) return;
    const parsed = JSON.parse(raw) as Partial<ChatState>;
    if (!Array.isArray(parsed.conversations)) return;
    const state = chatStates[page];
    state.conversations = parsed.conversations
      .filter(isChatConversation)
      .slice(0, MAX_CONVERSATIONS);
    state.activeId =
      typeof parsed.activeId === 'string' &&
      state.conversations.some((item) => item.id === parsed.activeId)
        ? parsed.activeId
        : null;
  } catch {
    // 忽略损坏或不兼容的本地记录，当作空白开始
  }
}

function saveChatState(page: ChatPage) {
  try {
    const state = chatStates[page];
    localStorage.setItem(
      CHAT_STORAGE_PREFIX + page,
      JSON.stringify({
        activeId: state.activeId,
        // previewUrl 是 data URL，不进本地记录（见 ChatTurn 上的注释）
        conversations: state.conversations.map((item) => ({
          ...item,
          turns: item.turns.map((turn) => ({
            ...turn,
            attachmentText: undefined,
            fileUrl: undefined,
            previewUrl: undefined,
          })),
        })),
      }),
    );
  } catch {
    // 存不下（隐私模式 / 配额满）就算了，不影响当前这轮对话
  }
}

/** 当前对话；还没有就现开一条 —— 只有真要写入时才开，空对话不占历史列表 */
function ensureConversation(): ChatConversation {
  const state = chat.value;
  const existing = state.conversations.find((item) => item.id === state.activeId);
  if (existing) return existing;
  const created: ChatConversation = {
    id: `chat-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    title: '',
    turns: [],
    updatedAt: Date.now(),
  };
  state.conversations.unshift(created);
  state.activeId = created.id;
  return created;
}

/** 把某条挪到最前（最近用过的排最上），并按上限截断 */
function touchConversation(id: string) {
  const state = chat.value;
  const index = state.conversations.findIndex((item) => item.id === id);
  if (index > 0) {
    const [item] = state.conversations.splice(index, 1);
    if (item) state.conversations.unshift(item);
  }
  if (state.conversations.length > MAX_CONVERSATIONS) {
    state.conversations.length = MAX_CONVERSATIONS;
    if (!state.conversations.some((item) => item.id === state.activeId)) {
      state.activeId = state.conversations[0]?.id ?? null;
    }
  }
}

function summarizeChatTitle(text: string) {
  const flat = text.replace(/\s+/g, ' ').trim();
  if (!flat) return '新对话';
  return flat.length > CHAT_TITLE_LIMIT ? `${flat.slice(0, CHAT_TITLE_LIMIT)}…` : flat;
}

/** 两个对话区的滚动容器：求职助理那页是 .home-stage，职位推荐右侧是 .rail-thread */
const homeStage = ref<HTMLElement>();
const railThread = ref<HTMLElement>();

/**
 * 把对话滚到最底部。
 *
 * <p>对话是从上往下读的，停在一堆旧消息的顶上等于要用户自己往下拉。
 * 进页面、切换历史对话、来了新消息，三处都要做。</p>
 *
 * <p>必须等 nextTick：刷新后本地记录刚读回来，DOM 还没按新的条数渲染完，
 * 这时候取 scrollHeight 拿到的是旧值。</p>
 */
function scrollChatToBottom() {
  void nextTick(() => {
    for (const el of [homeStage.value, railThread.value]) {
      if (el) el.scrollTop = el.scrollHeight;
    }
  });
}

const historyOpen = ref(false);

function toggleHistory() {
  historyOpen.value = !historyOpen.value;
}

/** 点浮层外面或按 Esc 收起（由 ChatHistory 子组件抛上来） */
function closeHistory() {
  historyOpen.value = false;
}

/*
 * ── 对话附件 ──
 *
 * 模型看不到图（当前配的是纯文本模型），所以图片必须在前端 OCR 成文字再发；
 * 简历同理，PDF/DOCX 先提取正文。两者最后都变成「随这条消息一起发出去的一段文本」。
 *
 * 只用于这一轮对话：不落库、不进「我的简历」。
 */
const chatAttachment = ref<ChatAttachment>();
const attachLoading = ref(false);
const attachStatus = ref('');
const attachMenuOpen = ref(false);
const chatFileInput = ref<HTMLInputElement>();
const attachMenuRoot = ref<HTMLElement>();

/** 点菜单外面关掉它。原来 closeAttachMenu 写了却没接任何事件，菜单只能靠再点一次「+」关 */
function onAttachMenuPointerDown(event: MouseEvent) {
  if (!attachMenuOpen.value) return;
  if (attachMenuRoot.value && !attachMenuRoot.value.contains(event.target as Node)) {
    closeAttachMenu();
  }
}
/** 同一个 input 要接两种 accept，所以「这次点的是哪种」得单独记一下 */
const attachIntent = ref<'image' | 'resume'>('resume');

function toggleAttachMenu() {
  attachMenuOpen.value = !attachMenuOpen.value;
}

function closeAttachMenu() {
  attachMenuOpen.value = false;
}

function chooseChatFile(kind: 'image' | 'resume') {
  attachIntent.value = kind;
  attachMenuOpen.value = false;
  const input = chatFileInput.value;
  if (!input) return;
  input.accept = kind === 'image' ? 'image/*' : '.pdf,.doc,.docx';
  input.click();
}

async function handleChatFile(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  // 先清空 value：同一个文件连选两次也得能触发 change
  input.value = '';
  if (!file) return;
  if (file.size > MAX_ATTACH_SIZE) {
    message.warning('附件不能超过 10 MB');
    return;
  }
  attachLoading.value = true;
  attachStatus.value = '正在读取文件…';
  try {
    // 按文件本身的类型分派，不看用户点的是哪个菜单项 ——
    // 从「上传简历」里挑到一张图、或从「上传图片」里挑到 PDF，都是常事，
    // 按菜单项分派的话这两种都会被一句「请按类型选择」挡回去。
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
      attachStatus.value = '正在提取简历正文…';
      const text = await documentToText(file);
      if (!text) {
        // 提取器内部把异常吞掉了（它只 console 出来），这里给出可操作的说法。
        // 旧版 .doc 不在它的处理范围内（只认 pdf/docx），扫描件 PDF 没有文字层。
        message.warning(
          extensionOf(file) === 'doc'
            ? '旧版 .doc 读不了，请另存为 .docx 或 PDF 再传'
            : '没能从这份文件里提取出文字。扫描件（图片型 PDF）请从「上传图片」发',
        );
        return;
      }
      chatAttachment.value = { file, kind: 'resume', name: file.name, text };
      return;
    }
    message.warning('只支持图片和 PDF / DOC / DOCX');
  } catch (error: any) {
    message.error(error?.message || '附件解析失败，请稍后重试');
  } finally {
    attachLoading.value = false;
    attachStatus.value = '';
  }
}

/** 打开附件原文件。PDF 会走浏览器自带的阅读器，其他格式交给浏览器自己决定 */
function openAttachment(fileUrl?: string) {
  if (!fileUrl) return;
  window.open(fileUrl, '_blank', 'noopener,noreferrer');
}

/** 图片放大预览：存 data URL，空表示没开 */
const previewImage = ref('');
function clearAttachment() {
  chatAttachment.value = undefined;
}

/** 菜单第三项：走原来的附件简历上传（存进简历列表）。和对话附件是两回事，别混 */
function chooseProfileUpload() {
  attachMenuOpen.value = false;
  chooseUpload();
}

/**
 * 开一段新对话。
 * 旧的不删 —— 它已经在列表里，随时能点回来；这里只是把「当前」让开，
 * 下一条消息会新建一条。所以空对话不会进列表（惰性创建）。
 */
function startNewConversation() {
  chat.value.activeId = null;
  chat.value.error = '';
  historyOpen.value = false;
  saveChatState(chatPage.value);
}

function openConversation(id: string) {
  chat.value.activeId = id;
  chat.value.error = '';
  historyOpen.value = false;
  touchConversation(id);
  saveChatState(chatPage.value);
}

/*
 * 薪资档位。
 *
 * 面板上是 chip，后端要的是 salaryMin / salaryMax / salaryNegotiable 三个参数，
 * 换算只发生在 SALARY_BAND_PARAMS 这一处。
 * 「面议」不等于「不限」：不限是不加条件，面议是明确只要没标薪资的那些。
 */
const SALARY_BANDS = [
  { key: 'any', label: '不限' },
  { key: 'lt3', label: '3K以下' },
  { key: '3-5', label: '3-5K' },
  { key: '5-10', label: '5-10K' },
  { key: '10-20', label: '10-20K' },
  { key: '20-50', label: '20-50K' },
  { key: 'gt50', label: '50K以上' },
  { key: 'negotiable', label: '面议' },
] as const;
type SalaryBandKey = (typeof SALARY_BANDS)[number]['key'];

const SALARY_BAND_PARAMS: Record<
  SalaryBandKey,
  { max?: number; min?: number; negotiable?: boolean }
> = {
  '10-20': { max: 20, min: 10 },
  '20-50': { max: 50, min: 20 },
  '3-5': { max: 5, min: 3 },
  '5-10': { max: 10, min: 5 },
  any: {},
  gt50: { min: 50 },
  lt3: { max: 3 },
  negotiable: { negotiable: true },
};

const jobKeyword = ref('');
const jobFilter = ref<'latest' | 'salary'>('latest');
const jobCity = ref('');
const jobSalaryBand = ref<SalaryBandKey>('any');
const jobPostTypeId = ref<number>();
const jobWorkTime = ref<number>();
const jobEducation = ref<number>();
const postTypes = ref<HrmPortalRecruitApi.PostType[]>([]);
/*
 * 筛选面板里的草稿。
 * 只有点「确定」才写回上面那组生效值；「清除筛选」、取消、直接关闭都只动草稿，列表不受影响。
 */
const filterDraft = reactive({
  city: '',
  // 学历和经验用字典里的 UNLIMITED（1）表示「不限」，面板上的 chip 才能和字典共用一份选项
  education: HrmRecruitPostEducation.UNLIMITED as number,
  postName: '',
  postTypeId: undefined as number | undefined,
  salaryBand: 'any' as SalaryBandKey,
  sort: 'latest' as 'latest' | 'salary',
  workTime: HrmRecruitWorkTime.UNLIMITED as number,
});
const filterPanelOpen = ref(false);
/*
 * 右侧助手的三条示例问题。
 * 只写这个助手真能接住的问法：它手上只有你的资料和当前职位列表，
 * 让它「帮我投递」「帮我筛掉某家公司」会得到一个编出来的答复。
 */
const ASSISTANT_TIPS = [
  '帮我看看哪些职位更适合我',
  '怎么写工作经历更容易通过筛选',
  '投递之后大概多久会有回复',
];
const applicationStatusFilter = ref('');
const applicationStatusParam: Record<string, string> = {
  INTERVIEW: 'INTERVIEWING',
  OFFER: 'OFFER_SENT',
  REJECTED: 'RESUME_REJECTED',
};
const editorAction = ref<'diagnose' | 'generate' | 'optimize'>('generate');
const editorStatus = ref<'idle' | 'running' | 'success' | 'failed'>('idle');
const editorError = ref('');
const generatedDraft = ref<HrmPortalRecruitAgentApi.GenerateResult>();
const generatedTask = ref<HrmPortalRecruitAgentApi.TaskResp>();
const versions = ref<HrmPortalResumeVersionApi.Version[]>([]);
const selectedVersionId = ref<number>();
const activeVersion = ref<HrmPortalResumeVersionApi.Version>();
const diagnosis = ref<HrmPortalRecruitAgentApi.AnalyzeResult>();
const optimization = ref<HrmPortalRecruitAgentApi.OptimizeResult>();
const optimizeDecisions = ref<Record<string, HrmPortalRecruitAgentApi.OptimizeDecision>>({});
const draftContent = reactive<Record<string, any>>({
  name: '',
  expectedPostName: '',
  city: '',
  email: '',
  mobile: '',
  strengths: '',
  summary: '',
  skills: '',
  experiences: [],
  projects: [],
  educationExperiences: [],
});
const versionSaving = ref(false);

const settings = reactive({
  communication: '您好，希望可以进一步了解贵公司的岗位，也很期待有机会参加面试。',
  browserNotice: true,
  memory: true,
  workMode: 'confirm',
});

const attachmentResumes = computed(() =>
  resumes.value.filter((resume) => resume.resumeType === 1),
);
const selectedResume = computed(() =>
  resumes.value.find((resume) => resume.id === selectedResumeId.value),
);

/**
 * 「我的简历」网格要展示的简历。
 *
 * 附件简历全部展示；在线求职资料只留一份 —— 库里同一个用户会有好几条
 * title 都是「求职资料（系统）」的记录（每保存一次个人资料就新插一条），
 * 全铺出来就是一排一模一样的卡片，只有最新那条有意义。
 * findOnlineResume 取的是列表里的第一份，也就是最新的那份。
 */
const visibleResumes = computed(() => {
  const list = [...attachmentResumes.value];
  if (onlineResume.value) list.push(onlineResume.value);
  return list;
});

/**
 * 卡片缩略图上要显示的姓名。
 *
 * 库里没有专门的姓名列，只能从 content 这份 JSON 里读；读不到就留空，
 * 让缩略图退化成占位标题，而不是硬凑一个看起来像真名的字符串。
 */
function resumeCardName(resume: HrmPortalResumeApi.Resume) {
  return String(parseJson(resume.content).name || '').trim();
}

/*
 * 卡面上的「简历诊断 / 已优化」角标。
 *
 * 后端目前不返回「这份简历诊断过没有」，列表接口里没有这个字段，
 * 一条条去查版本又太贵，所以按本机记：诊断/优化跑完时把 id 记进 localStorage。
 * 换设备看不到角标 —— 这是已知取舍，等后端补了状态字段再换掉。
 */
const DIAGNOSED_KEY = 'ai-resume-diagnosed-ids';
const OPTIMIZED_KEY = 'ai-resume-optimized-ids';

function readIdList(key: string): number[] {
  try {
    const raw = localStorage.getItem(key);
    const parsed = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed.filter((id) => typeof id === 'number') : [];
  } catch {
    return [];
  }
}

const diagnosedResumeIds = ref<number[]>(readIdList(DIAGNOSED_KEY));
const optimizedResumeIds = ref<number[]>(readIdList(OPTIMIZED_KEY));

function rememberResumeFlag(key: string, target: typeof diagnosedResumeIds, id?: number) {
  if (!id || target.value.includes(id)) return;
  target.value = [...target.value, id];
  try {
    localStorage.setItem(key, JSON.stringify(target.value));
  } catch {
    // 隐私模式下写不进去，角标当次有效即可，不影响主流程
  }
}
const profileCompletion = computed(() => {
  const fields = [
    profile.name,
    profile.expectedPostName,
    profile.city,
    profile.education,
    profile.school,
    profile.skills,
    profile.strengths,
  ];
  return Math.round((fields.filter(Boolean).length / fields.length) * 100);
});
const parsedResumeContent = computed(() => parseJson(selectedResume.value?.content));
const draftSkills = computed(() => String(draftContent.skills || '').split(/[、,，\n]/).map((item) => item.trim()).filter(Boolean));

function parseJson(value?: string): ProfileData {
  if (!value) return {};
  try {
    const result = JSON.parse(value) as ProfileData;
    return result && typeof result === 'object' ? result : {};
  } catch {
    return {};
  }
}

function setProfileFromResume(resume?: HrmPortalResumeApi.Resume) {
  Object.assign(profile, parseJson(resume?.content));
  setDraftContent(parseJson(resume?.content));
}

function setDraftContent(value?: Record<string, any>) {
  const source = value || {};
  Object.assign(draftContent, {
    ...source,
    name: source.name || profile.name || displayName.value,
    expectedPostName: source.expectedPostName || profile.expectedPostName || '',
    city: source.city || profile.city || '',
    email: source.email || profile.email || '',
    mobile: source.mobile || profile.mobile || '',
    strengths: source.strengths || profile.strengths || '',
    summary: source.summary || source.strengths || profile.strengths || '',
    skills: source.skills || profile.skills || '',
    experiences: Array.isArray(source.experiences) ? source.experiences : [],
    projects: Array.isArray(source.projects) ? source.projects : [],
    educationExperiences: Array.isArray(source.educationExperiences) ? source.educationExperiences : [],
  });
}

function draftJson() {
  return JSON.stringify({ ...draftContent, skills: String(draftContent.skills || '') });
}

function mergeGeneratedDraft(result: HrmPortalRecruitAgentApi.GenerateResult) {
  Object.assign(draftContent, {
    headline: result.headline,
    summary: result.summary,
    skills: result.skills?.join('、') || draftContent.skills,
    experiences: result.experiences || [],
    projects: result.projects || [],
    educationExperiences: result.educationExperiences || [],
  });
}

function formatSalary(post?: HrmPortalRecruitApi.Post) {
  if (!post) return '薪资面议';
  if (post.minSalary == null || post.minSalary < 0) return '薪资面议';
  if (post.maxSalary != null && post.maxSalary >= 0 && post.maxSalary !== post.minSalary) {
    return `${post.minSalary}-${post.maxSalary}K`;
  }
  return `${post.minSalary}K 起`;
}

/*
 * 学历与经验文案。
 *
 * 两本字典取自 hrm_recruit_post_education / hrm_recruit_work_time。
 * 上一版这里自己编了一套（学历 4 = 大专、经验 1/3/5/10），和后端对不上：
 * 4 其实是「本科及以上」，而工作经验根本没有 10 这个值，筛选一直是错的。
 */
const WORK_TIME_OPTIONS = [
  { label: '不限', value: HrmRecruitWorkTime.UNLIMITED },
  { label: '1 年以下', value: HrmRecruitWorkTime.LESS_THAN_ONE_YEAR },
  { label: '1-3 年', value: HrmRecruitWorkTime.ONE_TO_THREE_YEARS },
  { label: '3-5 年', value: HrmRecruitWorkTime.THREE_TO_FIVE_YEARS },
  { label: '5-10 年', value: HrmRecruitWorkTime.FIVE_TO_TEN_YEARS },
  { label: '10 年以上', value: HrmRecruitWorkTime.MORE_THAN_TEN_YEARS },
];

/*
 * 经验与学历都从选项表反查文案，不再各写一份 ——
 * 筛选面板的 chip 和职位卡的事实行必须永远是同一串字。
 */
function educationLabel(value?: number) {
  if (!value || value === HrmRecruitPostEducation.UNLIMITED) return '学历不限';
  return (
    HrmRecruitPostEducationOptions.find((option) => option.value === value)?.label || '学历不限'
  );
}

function workTimeLabel(value?: number) {
  if (!value || value === HrmRecruitWorkTime.UNLIMITED) return '经验不限';
  return WORK_TIME_OPTIONS.find((option) => option.value === value)?.label || '经验不限';
}

/** 公司行：短名 · 融资阶段 · 规模。缺哪一段就少哪一段，不留空分隔符 */
function companyLine(post: HrmPortalRecruitApi.Post) {
  return [
    post.companyShortName || post.companyName || '天信合作企业',
    post.companyFinancingStage,
    post.companyScale,
  ]
    .filter(Boolean)
    .join(' · ');
}

/** 生效中的筛选条件数量，非零时在「筛选职位」上落一个角标 */
const activeFilterCount = computed(
  () =>
    [
      jobKeyword.value.trim(),
      jobCity.value.trim(),
      jobPostTypeId.value,
      jobWorkTime.value,
      jobEducation.value,
      jobSalaryBand.value === 'any' ? '' : jobSalaryBand.value,
    ].filter((value) => value !== undefined && value !== '' && value !== null).length,
);

/**
 * 偏好横幅第二行：期望职位 | 意向城市 | 薪资要求。
 *
 * 读的是**已保存的求职偏好**，不是上面那组列表筛选条件 ——
 * 横幅说的是「你是谁」，筛选面板说的是「这一次想看哪些职位」。
 * 早先这里读的是筛选条件，于是用户筛一次列表横幅就跟着变了，
 * 而他并没有改过自己的求职意向。
 */
const preferenceSummary = computed(() => {
  const salary =
    SALARY_OPTIONS.find((item) => item.value === savedPreference.salary)?.label ||
    '薪资不限';
  return [
    savedPreference.expectedPostName || '职位不限',
    savedPreference.expectedCities.join('、') || '城市不限',
    savedPreference.salary === 'all' ? '薪资不限' : salary,
  ].join(' | ');
});

/*
 * 求职偏好抽屉。
 *
 * 和筛选面板是两回事：这个说的是「你是谁」，会落到个人信息里；
 * 筛选面板说的是「这一次想看哪些职位」，离开就不作数。
 *
 * 参考站那张抽屉里还有拒绝职位 / 公司类型 / 公司规模 / 优先偏好 / 拒绝偏好，
 * 我的库里没有这些字段，就没有摆上去。留下的是我确实有的求职意向，
 * 存在「求职资料（系统）」这条简历的 content JSON 里。
 */
const preferenceDrawerOpen = ref(false);

/*
 * 岗位类型 / 薪资要求 / 期望行业三组选项。
 *
 * 值和在线简历页（portal/recruit/online-resume.vue）里的 jobTypeOptions /
 * salaryOptions / industryOptions 必须一致 —— 它们直接落进求职资料的 content JSON，
 * 两边一旦对不上，存进去的就是读不出来的无效值。
 */
const JOB_TYPE_OPTIONS = [
  { label: '不限', value: 'all' },
  { label: '全职', value: 'fulltime' },
  { label: '实习', value: 'internship' },
  { label: '兼职', value: 'parttime' },
];

const SALARY_OPTIONS = [
  { label: '不限', value: 'all' },
  { label: '3000 元以下', value: 'below-3k' },
  { label: '3000-5000 元', value: '3k-5k' },
  { label: '5000-10000 元', value: '5k-10k' },
  { label: '10000-20000 元', value: '10k-20k' },
  { label: '20000-50000 元', value: '20k-50k' },
  { label: '50000 元以上', value: 'above-50k' },
];

const INDUSTRY_OPTIONS = [
  '防腐保温',
  '建筑工程',
  '能源 / 化工',
  '装备制造',
  '新材料',
  '房地产 / 物业',
  '互联网 / IT',
  '物流运输',
  '教育培训',
  '金融服务',
  '医疗健康',
  '其它行业',
];

type AreaTreeNodeLite = { children?: AreaTreeNodeLite[]; id?: number; name: string };

const preferenceLoading = ref(false);
const preferenceSaving = ref(false);
const preferenceForm = reactive({
  expectedCities: [] as string[],
  expectedCityAreaIds: [] as number[],
  expectedPostId: undefined as number | undefined,
  /** 职位名。正常从 postTypes 反查，查不到时留着原值，避免选过的职位被改名后名字丢失 */
  expectedPostName: '',
  industry: '',
  jobType: 'fulltime',
  salary: 'all',
});
/**
 * 已保存的偏好，横幅那行读的就是它。
 * 抽屉里改的是草稿，点「取消」不该动到横幅，所以两者分开存，保存成功才同步过来。
 */
const savedPreference = reactive({
  expectedCities: [] as string[],
  expectedPostName: '',
  salary: 'all',
});
/** 意向城市候选：地区树的第二级（省 / 市 / 区里的市） */
const cityOptions = ref<Array<{ id: number; name: string }>>([]);

/** 把简历 content 里的偏好字段读成表单结构，顺带兜掉几个老字段名 */
function parsePreference(raw: Record<string, any>) {
  return {
    expectedCities: Array.isArray(raw.expectedCities)
      ? raw.expectedCities.map(String)
      : [],
    expectedCityAreaIds: Array.isArray(raw.expectedCityAreaIds)
      ? raw.expectedCityAreaIds.map(Number)
      : [],
    expectedPostId:
      typeof raw.expectedPostId === 'number' ? raw.expectedPostId : undefined,
    expectedPostName: String(raw.expectedPostName || raw.postTypeName || ''),
    industry: String(raw.industry || raw.expectedIndustry || ''),
    jobType: String(raw.jobType || 'fulltime'),
    salary: String(raw.salary || 'all'),
  };
}

/** 表单里的数组是可变对象，必须拷一份，否则编辑草稿会连带改掉横幅 */
function syncSavedPreference(value: {
  expectedCities: string[];
  expectedPostName: string;
  salary: string;
}) {
  savedPreference.expectedCities = [...value.expectedCities];
  savedPreference.expectedPostName = value.expectedPostName;
  savedPreference.salary = value.salary;
}

/**
 * 读求职资料里存的那份偏好。
 *
 * 存储是整体覆盖的（createPortalResume 每次整条 content 写回），
 * 所以读的时候要拿全量 JSON，保存时也要先摊开原值再盖 —— 见 savePreferences。
 *
 * 进页面时也要跑一次：横幅那行显示的就是它。
 */
async function loadPreferences() {
  try {
    const resume = await getRecruitProfileResume();
    const raw = (
      resume?.content ? JSON.parse(resume.content) : {}
    ) as Record<string, any>;
    const parsed = parsePreference(raw);
    Object.assign(preferenceForm, parsed);
    syncSavedPreference(parsed);
  } catch {
    // 读不出来就当没填过，横幅显示「不限」
  }
}

async function openPreferenceDrawer() {
  preferenceDrawerOpen.value = true;
  void loadCityOptions();
  void loadCityCounts();
  preferenceLoading.value = true;
  await loadPreferences();
  preferenceLoading.value = false;
}

async function loadCityOptions() {
  if (cityOptions.value.length) return;
  try {
    const tree = (await getAreaTree()) as AreaTreeNodeLite[];
    cityOptions.value = (tree || []).flatMap((province) =>
      (province.children || []).map((city) => ({
        id: Number(city.id),
        name: String(city.name),
      })),
    );
  } catch {
    // 地区树拉不到就不给加城市，其余字段照常能改
  }
}

/**
 * 城市面板给的是名字，而落库要的是地区编号（后端预筛按编号走）。
 * 拿 cityOptions 反查一次；查不到就不放行，免得存进一个只有名字、筛不动的城市。
 */
function addExpectedCityByName(name: string) {
  const matched = cityOptions.value.find((item) => item.name === name);
  if (!matched) {
    message.warning('这个城市暂时添加不了，请换个相近的市');
    return;
  }
  addExpectedCity(matched.id);
}

/** 各省 / 市的在招岗位数，给城市面板显示「哪儿活儿多」用 */
const cityPostCounts = ref(new Map<string, number>());

async function loadCityCounts() {
  if (cityPostCounts.value.size) return;
  try {
    // 岗位要从职位分页另查 —— 公司列表不带岗位（企业目录页也是这么拼的）
    const first = await getPortalRecruitPostPage({ pageNo: 1, pageSize: 100 });
    const rest = first?.total > 100
      ? await Promise.all(
          Array.from({ length: Math.ceil(first.total / 100) - 1 }, (_, index) =>
            getPortalRecruitPostPage({ pageNo: index + 2, pageSize: 100 }),
          ),
        )
      : [];
    const counts = new Map<string, number>();
    for (const page of [first, ...rest]) {
      for (const post of page?.list || []) {
        const parts = post.areaName?.trim().split(/\s+/) || [];
        const province = parts[0];
        const city = parts[1] || parts[0];
        if (province) counts.set(province, (counts.get(province) ?? 0) + 1);
        if (city) counts.set(city, (counts.get(city) ?? 0) + 1);
      }
    }
    cityPostCounts.value = counts;
  } catch {
    // 拿不到就不显示数字，面板本身仍可用
  }
}

function addExpectedCity(value: unknown) {
  const areaId = Number(value);
  const city = cityOptions.value.find((item) => item.id === areaId);
  if (!city) return;
  if (preferenceForm.expectedCityAreaIds.includes(areaId)) {
    message.info('这个城市已经选过了');
    return;
  }
  if (preferenceForm.expectedCities.length >= 3) {
    message.info('意向城市最多选 3 个');
    return;
  }
  preferenceForm.expectedCityAreaIds.push(areaId);
  preferenceForm.expectedCities.push(city.name);
}

function removeExpectedCity(index: number) {
  preferenceForm.expectedCityAreaIds.splice(index, 1);
  preferenceForm.expectedCities.splice(index, 1);
}

async function savePreferences() {
  preferenceSaving.value = true;
  try {
    const resume = await getRecruitProfileResume();
    const raw = (
      resume?.content ? JSON.parse(resume.content) : {}
    ) as Record<string, any>;
    const postName =
      postTypes.value.find((type) => type.id === preferenceForm.expectedPostId)
        ?.name || preferenceForm.expectedPostName;
    const cities = preferenceForm.expectedCities;
    /*
     * 先摊开原来的 content，只盖掉偏好这几个键。
     * 少了这一步，简历里的头像、工作经历、教育经历会被整条覆盖掉 ——
     * 后端这个接口是整体覆盖写入，不是按字段更新。
     */
    const next = {
      ...raw,
      city: cities.join('、'),
      expectedCities: cities,
      expectedCityAreaIds: preferenceForm.expectedCityAreaIds,
      expectedIndustry: preferenceForm.industry,
      expectedPostId: preferenceForm.expectedPostId,
      expectedPostName: postName,
      industry: preferenceForm.industry,
      jobType: preferenceForm.jobType,
      postTypeId: preferenceForm.expectedPostId,
      postTypeName: postName,
      salary: preferenceForm.salary,
    };
    await createPortalResume({
      content: JSON.stringify(next),
      remark: '在线简历',
      title: RECRUIT_PROFILE_TITLE,
    });
    message.success('求职偏好已保存');
    // 保存成功才让横幅跟着变；点取消或直接关掉都走不到这里
    syncSavedPreference({
      expectedCities: cities,
      expectedPostName: postName,
      salary: preferenceForm.salary,
    });
    preferenceDrawerOpen.value = false;
  } catch (error: any) {
    message.error(error?.message || '保存失败，请稍后重试');
  } finally {
    preferenceSaving.value = false;
  }
}

function openFilterPanel() {
  // 每次打开都从当前生效值重新起草，免得上一次没提交的草稿阴魂不散
  Object.assign(filterDraft, {
    city: jobCity.value,
    education: jobEducation.value ?? HrmRecruitPostEducation.UNLIMITED,
    postName: jobKeyword.value,
    postTypeId: jobPostTypeId.value,
    salaryBand: jobSalaryBand.value,
    sort: jobFilter.value,
    workTime: jobWorkTime.value ?? HrmRecruitWorkTime.UNLIMITED,
  });
  filterPanelOpen.value = true;
}

function applyFilters() {
  jobKeyword.value = filterDraft.postName.trim();
  jobCity.value = filterDraft.city.trim();
  jobPostTypeId.value = filterDraft.postTypeId;
  jobSalaryBand.value = filterDraft.salaryBand;
  jobFilter.value = filterDraft.sort;
  // 字典里的「不限」就是不带这个参数，别把 1 当成一个真实条件发给后端
  jobWorkTime.value =
    filterDraft.workTime === HrmRecruitWorkTime.UNLIMITED ? undefined : filterDraft.workTime;
  jobEducation.value =
    filterDraft.education === HrmRecruitPostEducation.UNLIMITED ? undefined : filterDraft.education;
  filterPanelOpen.value = false;
  void loadJobs();
}

/** 清除筛选只清草稿，用户还得点「确定」才落到列表上 */
function resetFilters() {
  Object.assign(filterDraft, {
    city: '',
    education: HrmRecruitPostEducation.UNLIMITED,
    postName: '',
    postTypeId: undefined,
    salaryBand: 'any',
    sort: 'latest',
    workTime: HrmRecruitWorkTime.UNLIMITED,
  });
}

/** 点建议即发送，省掉一次「复制 → 粘贴」 */
function sendSuggestion(text: string) {
  if (chat.value.sending) return;
  chat.value.draft = text;
  void submitAssistant();
}

function formatApplicationStatus(application?: HrmPortalRecruitApi.Application) {
  if (!application) return '处理中';
  return application.statusName || application.status || '处理中';
}

function go(key: WorkspaceKey) {
  const target = navItems.find((item) => item.key === key);
  if (!target) return;
  void router.push({ name: target.routeName });
}

function goRecruitment() {
  void router.push({ name: 'PortalRecruit' });
}

function openProfileEditor() {
  void router.push({ name: 'PortalProfileRecruit', query: { section: 'resume' } });
}

function openJob(post: HrmPortalRecruitApi.Post) {
  void router.push({ name: 'AiResumeJob', params: { id: post.id } });
}

/** 点对话里的岗位卡片，进职位详情 */
function openChatJob(job: HrmPortalRecruitAgentApi.ChatJob) {
  void router.push({ name: 'AiResumeJob', params: { id: job.id } });
}

function openChat(post: HrmPortalRecruitApi.Post) {
  selectedPost.value = post;
  void router.push({
    name: 'PortalRecruit',
    query: { section: 'messages', postId: String(post.id) },
  });
}

function openResumePreview(resume: HrmPortalResumeApi.Resume) {
  const previewUrl = getHrmRecruitFilePreviewUrl(resume.fileUrl);
  if (!previewUrl) {
    message.info('这份简历还没有可预览的文件');
    return;
  }
  window.open(previewUrl, '_blank', 'noopener,noreferrer');
}

function chooseUpload() {
  const input = uploadInput.value;
  // 拿不到控件时明确说出来。原先用 `?.` 静默跳过，用户那边看到的就是「点了没反应」，
  // 既不弹框也不报错，连排查方向都没有。
  if (!input) {
    message.warning('上传控件未就绪，请刷新页面后重试');
    return;
  }
  input.click();
}

/**
 * 入库正文的长度上限。
 *
 * <p>比对话附件的 6000 宽松得多：那个数是按「省 token」定的，只管当下这一问；
 * 而这份正文要长期存着，预览靠它渲染、AI 诊断/优化把它当输入，截断了就是真丢内容。
 * 五万字足够覆盖任何简历，留着只是为了挡住「把一整本 PDF 传上来」这种极端情况。</p>
 */
const MAX_RESUME_CONTENT = 50_000;

/**
 * 附件 → 入库内容。失败一律降级，不往上抛。
 *
 * <p>调用方是上传流程：文件这时候已经存进存储了，不该因为解析不出去把整次上传判死 ——
 * 那份记录仍然有用（预览可以退回直接渲染原件）。失败原因交给调用方决定怎么说。</p>
 *
 * <p>两步：先提取正文（文字层读不出来就走多模态转写，覆盖扫描件），
 * 再让模型把它**整理成结构化字段** —— 纯文本入库的话，预览只能把全文挂在
 * 「个人优势」下，AI 的逐字段建议也定位不到。</p>
 */
async function extractResumeText(file: File) {
  try {
    const intake = await intakeResumeFile(file, (error) => {
      console.warn('[AiResume] 附件正文提取失败', error);
    });
    return {
      content: intake.content.slice(0, MAX_RESUME_CONTENT),
      structured: intake.structured,
    };
  } catch (error) {
    console.warn('[AiResume] 附件正文提取失败', error);
    return { content: '', structured: false };
  }
}

async function handleUpload(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) return;
  const extension = file.name.split('.').pop()?.toLowerCase();
  if (!extension || !['pdf', 'doc', 'docx'].includes(extension)) {
    message.warning('仅支持 PDF、DOC、DOCX 简历');
    return;
  }
  if (file.size > 10 * 1024 * 1024) {
    message.warning('简历文件不能超过 10 MB');
    return;
  }
  uploadLoading.value = true;
  try {
    const fileUrl = await uploadHrmRecruitFile(file, 'resume');
    // 正文必须跟着一起入库，它不是预览专用的：后端 resolveResume() 在 content 为空时
    // 直接抛「简历内容为空或不存在」。原先这里只传文件不传正文，于是工作台上传的简历
    // 既预览不了、也跑不了 AI 诊断 —— 两处报错都指不到真正的原因。
    const { content, structured } = await extractResumeText(file);
    await uploadPortalResume({
      title: file.name.replace(/\.[^.]+$/, '') || '我的附件简历',
      fileName: file.name,
      fileUrl,
      remark: 'AI 简历工作台上传',
      content: content || undefined,
    });
    await loadResumes();
    // 提不出文字要当面说清楚，不能默默存一份空简历 ——
    // 否则用户点开预览看到的是「还没有可预览的内容」，跑 AI 又报「简历内容为空」。
    if (!content) {
      const reason =
        extension === 'doc'
          ? '.doc 格式不支持解析'
          : '可能是扫描件，没有文字层';
      message.warning(
        `已加入工作台，但没能提取出正文（${reason}）。这份简历暂时跑不了 AI 诊断，原件仍可以打开；建议另存为 PDF 或 DOCX 后重新上传。`,
        8,
      );
    } else if (structured) {
      message.success('附件简历已加入工作台，正文已整理成可编辑字段');
    } else {
      // 整理了但没成功：正文在，能预览也能诊断，只是不逐字段
      message.success('附件简历已加入工作台');
      message.warning('这份的正文没能整理成可分节的字段，AI 建议只能给整段意见', 6);
    }
  } catch (error: any) {
    message.error(error?.message || '附件上传失败，请稍后重试');
  } finally {
    uploadLoading.value = false;
  }
}

/**
 * 删除一份简历。
 *
 * <p>「求职资料（系统）」不给删 —— 求职偏好、职位预筛、投递时的简历选择都在读它，
 * 删掉不会报错，只会让那几处悄悄退化成空。</p>
 */
function removeResume(resume: HrmPortalResumeApi.Resume) {
  if (isOnlineResume(resume)) {
    message.warning('求职资料由系统维护，不能删除');
    return;
  }
  Modal.confirm({
    cancelText: '取消',
    content: '删除后无法恢复；如果只是想换一份用，可以直接选中另一份。',
    okText: '删除',
    okType: 'danger',
    title: `删除简历「${resume.title}」？`,
    async onOk() {
      try {
        await deletePortalResume(resume.id);
        // 删掉的正好是当前选中项时先把选中让开，否则右侧画布会停在一条不存在的记录上
        if (selectedResumeId.value === resume.id) selectedResumeId.value = undefined;
        await loadResumes();
        message.success('简历已删除');
      } catch (error: any) {
        message.error(error?.message || '删除失败，请稍后重试');
      }
    },
  });
}

/** 卡片「⋯」菜单发出的删除：菜单只带 id，这里换回对象交给 removeResume 的确认流程 */
function removeResumeById(id: number) {
  const resume = resumes.value.find((item) => item.id === id);
  if (resume) removeResume(resume);
}

/*
 * 卡片「⋯」菜单里原本还想要「重命名」，撤了：
 * HRM 个人简历只有 /upload 与 /create 两个写接口，两个都是**新建**一条记录，
 * 没有按 id 改 title 的接口。照着重命名会复制出一份简历，比不能改名更糟。
 * 要支持改名得先在后端补一个 update 接口，那是另一件事。
 */

/** 进简历工作区（编辑器 + 预览 + 求职助手），默认不自动开始诊断 */
function openResumeWorkspace(id: number) {
  void router.push({ name: 'AiResumeDiagnose', query: { resumeId: String(id) } });
}

/*
 * 卡片点击原本直接跳工作区，改成先出预览。
 * 从列表点一下就把人丢进编辑器太容易误触，而预览是无副作用的；
 * 真要改的人，预览弹窗右下角有「进入编辑」。
 */
const previewOpen = ref(false);
const previewResume = ref<HrmPortalResumeApi.Resume>();

function openResumePreviewDialog(id: number) {
  const resume = resumes.value.find((item) => item.id === id);
  if (!resume) return;
  previewResume.value = resume;
  previewOpen.value = true;
}

/** 预览弹窗里点「进入编辑」：先关弹窗再跳，回来时不会还压着一层 */
function editResumeFromPreview(id: number) {
  previewOpen.value = false;
  openResumeWorkspace(id);
}

/** 「⋯」菜单里的「预览原文件」：PDF 交给浏览器自带的阅读器 */
function openResumeFile(id: number) {
  const resume = resumes.value.find((item) => item.id === id);
  if (resume) openResumePreview(resume);
}

/**
 * 服务卡进入工作区：先在工作区里选简历，再自动跑对应的 AI 任务。
 * 三张卡（诊断/优化/生成）走的是同一个页面，只是进去以后自动触发的动作不同。
 */
function startAgentFlow(mode: 'analyze' | 'generate' | 'optimize') {
  void router.push({ name: 'AiResumeDiagnose', query: { mode } });
}

async function loadResumes() {
  try {
    resumes.value = (await getPortalResumeList()) || [];
    onlineResume.value = resumes.value.find(isOnlineResume);
    if (onlineResume.value) setProfileFromResume(onlineResume.value);
    if (!selectedResumeId.value) {
      selectedResumeId.value = attachmentResumes.value[0]?.id || onlineResume.value?.id;
    }
  } catch (error: any) {
    message.error(error?.message || '简历数据加载失败');
  }
}

async function loadVersions() {
  if (!selectedResumeId.value) {
    versions.value = [];
    selectedVersionId.value = undefined;
    activeVersion.value = undefined;
    return;
  }
  try {
    versions.value = (await getPortalResumeVersionList(selectedResumeId.value)) || [];
    if (selectedVersionId.value && versions.value.some((item) => item.id === selectedVersionId.value)) return;
    selectedVersionId.value = versions.value[0]?.id;
  } catch (error: any) {
    message.error(error?.message || '简历版本加载失败');
  }
}

async function loadVersion(id: number) {
  try {
    activeVersion.value = await getPortalResumeVersion(id);
    setDraftContent(parseJson(activeVersion.value?.content));
  } catch (error: any) {
    message.error(error?.message || '简历版本读取失败');
  }
}

async function saveResumeVersion(options: { silent?: boolean; createType?: number } = {}) {
  if (versionSaving.value) return selectedVersionId.value;
  if (!draftContent.name && !draftContent.expectedPostName) {
    message.warning('请先填写简历基本信息');
    return;
  }
  versionSaving.value = true;
  try {
    const versionId = await createPortalResumeVersion({
      sourceResumeId: selectedResumeId.value,
      parentVersionId: selectedVersionId.value,
      sourcePostId: selectedPost.value?.id,
      createType: options.createType || 1,
      changeSummary: options.createType === 2 ? '确认 AI 优化建议' : '保存结构化简历草稿',
      content: draftJson(),
      idempotencyKey: buildResumeVersionIdempotencyKey(`ai-resume-${Date.now()}`),
    });
    selectedVersionId.value = versionId;
    await loadVersions();
    if (!options.silent) message.success('简历版本已保存');
    return versionId;
  } catch (error: any) {
    message.error(error?.message || '简历版本保存失败');
  } finally {
    versionSaving.value = false;
  }
}

async function pollAgentTask(taskId: number) {
  for (let attempt = 0; attempt < 30; attempt += 1) {
    const detail = await getRecruitAgentTask(taskId);
    if (detail.status === 'SUCCEEDED' || detail.status === 'FAILED') return detail;
    await new Promise((resolve) => window.setTimeout(resolve, 800));
  }
  throw new Error('AI 任务响应超时，请稍后重试');
}

async function loadJobs() {
  jobsLoading.value = true;
  try {
    const result = await getPortalRecruitPostPage({
      pageNo: 1,
      pageSize: 12,
      postName: jobKeyword.value.trim() || undefined,
      areaId: undefined,
      postTypeId: jobPostTypeId.value,
      workTime: jobWorkTime.value,
      educationRequire: jobEducation.value,
      // 档位到参数的换算只走这一处，「面议」也在这里落到 salaryNegotiable 上
      salaryMin: SALARY_BAND_PARAMS[jobSalaryBand.value].min,
      salaryMax: SALARY_BAND_PARAMS[jobSalaryBand.value].max,
      salaryNegotiable: SALARY_BAND_PARAMS[jobSalaryBand.value].negotiable,
    });
    jobs.value = result?.list || [];
    if (jobCity.value.trim()) {
      const city = jobCity.value.trim().toLowerCase();
      jobs.value = jobs.value.filter((post) =>
        `${post.areaName || ''}${post.workAddress || ''}`.toLowerCase().includes(city),
      );
    }
    if (jobFilter.value === 'salary') {
      jobs.value = [...jobs.value].sort((left, right) => (right.maxSalary || 0) - (left.maxSalary || 0));
    }
  } catch (error: any) {
    message.error(error?.message || '职位加载失败');
  } finally {
    jobsLoading.value = false;
  }
}

async function loadApplications() {
  applicationsLoading.value = true;
  try {
    const result = await getPortalRecruitApplicationPage({
      pageNo: 1,
      pageSize: 20,
      status: applicationStatusParam[applicationStatusFilter.value] || applicationStatusFilter.value || undefined,
    });
    applications.value = result?.list || [];
  } catch (error: any) {
    message.error(error?.message || '投递记录加载失败');
  } finally {
    applicationsLoading.value = false;
  }
}

async function loadApplicationDetail(id: number) {
  applicationDetailLoading.value = true;
  try {
    applicationDetail.value = await getPortalRecruitApplication(id);
  } catch (error: any) {
    message.error(error?.message || '投递详情加载失败');
  } finally {
    applicationDetailLoading.value = false;
  }
}

async function withdrawApplication(application: HrmPortalRecruitApi.Application) {
  if (!application.id || ['WITHDRAWN', 'REJECTED', 'HIRED'].includes(application.status)) return;
  try {
    await withdrawPortalRecruitApplication(application.id, '求职者主动撤回');
    message.success('投递已撤回');
    applicationDetail.value = undefined;
    await loadApplications();
  } catch (error: any) {
    message.error(error?.message || '撤回投递失败');
  }
}

async function loadPostTypes() {
  try {
    postTypes.value = (await getPortalRecruitPostTypeList()) || [];
  } catch {
    postTypes.value = [];
  }
}

async function loadJobDetail() {
  const id = Number(route.params.id);
  if (!Number.isFinite(id) || id <= 0) return;
  try {
    selectedPost.value = await getPortalRecruitPost(id);
  } catch (error: any) {
    message.error(error?.message || '职位详情加载失败');
  }
}

async function generateResume() {
  if (editorStatus.value === 'running') return;
  const source = parsedResumeContent.value;
  const targetRole = profile.expectedPostName || source.expectedPostName || '';
  if (!targetRole) {
    message.warning('请先在个人档案中填写期望职位');
    return;
  }
  editorStatus.value = 'running';
  editorError.value = '';
  generatedDraft.value = undefined;
  try {
    const taskId = await submitResumeGenerate({
      targetRole,
      targetCity: profile.city || source.city,
      education: profile.education || source.education,
      experience: JSON.stringify(profile.experiences || source.experiences || []),
      projects: JSON.stringify(profile.projects || source.projects || []),
      skills: profile.skills || source.skills,
      strengths: profile.strengths || source.strengths,
      idempotencyKey: buildGenerateIdempotencyKey(),
    });
    const detail = await pollAgentTask(taskId);
    if (detail?.status !== 'SUCCEEDED' || !detail.result) {
      throw new Error(detail?.errorMessage || 'AI 没有返回可编辑的简历草稿');
    }
    generatedTask.value = detail;
    generatedDraft.value = detail.result as HrmPortalRecruitAgentApi.GenerateResult;
    mergeGeneratedDraft(generatedDraft.value);
    editorStatus.value = 'success';
  } catch (error: any) {
    editorError.value = error?.message || 'AI 服务暂时不可用';
    editorStatus.value = 'failed';
  }
}

async function diagnoseResume() {
  if (editorStatus.value === 'running') return;
  if (!selectedPost.value) {
    message.info('请先选择目标职位，再开始诊断');
    go('recommend');
    return;
  }
  editorStatus.value = 'running';
  editorError.value = '';
  diagnosis.value = undefined;
  try {
    const versionId = selectedVersionId.value || await saveResumeVersion({ silent: true });
    if (!versionId) throw new Error('请先保存一份可诊断的简历版本');
    const taskId = await submitResumeAnalyze({ resumeVersionId: versionId, postId: selectedPost.value.id, idempotencyKey: buildAnalyzeIdempotencyKey() });
    const detail = await pollAgentTask(taskId);
    if (detail.status !== 'SUCCEEDED' || !detail.result) throw new Error(detail.errorMessage || '诊断没有返回结果');
    diagnosis.value = detail.result as HrmPortalRecruitAgentApi.AnalyzeResult;
    generatedTask.value = detail;
    editorStatus.value = 'success';
  } catch (error: any) {
    editorError.value = error?.message || '诊断暂时不可用';
    editorStatus.value = 'failed';
  }
}

async function optimizeResume() {
  if (editorStatus.value === 'running') return;
  if (!selectedPost.value) {
    message.info('请先选择目标职位，再开始优化');
    go('recommend');
    return;
  }
  editorStatus.value = 'running';
  editorError.value = '';
  optimization.value = undefined;
  try {
    const versionId = selectedVersionId.value || await saveResumeVersion({ silent: true });
    if (!versionId) throw new Error('请先保存一份可优化的简历版本');
    const taskId = await submitResumeOptimize({ resumeVersionId: versionId, postId: selectedPost.value.id, style: 'PROFESSIONAL', idempotencyKey: buildAnalyzeIdempotencyKey() });
    const detail = await pollAgentTask(taskId);
    if (detail.status !== 'SUCCEEDED' || !detail.result) throw new Error(detail.errorMessage || '优化没有返回结果');
    optimization.value = detail.result as HrmPortalRecruitAgentApi.OptimizeResult;
    optimizeDecisions.value = {};
    generatedTask.value = detail;
    editorStatus.value = 'success';
  } catch (error: any) {
    editorError.value = error?.message || '优化暂时不可用';
    editorStatus.value = 'failed';
  }
}

async function applyOptimization() {
  if (!optimization.value?.items?.length || !selectedVersionId.value || !generatedTask.value?.taskId) return;
  try {
    const decisions = optimization.value.items.map((item) => ({
      module: item.module,
      fieldPath: item.fieldPath,
      originalValue: item.originalValue,
      suggestedValue: item.suggestedValue,
      finalValue: optimizeDecisions.value[item.fieldPath] === 'ACCEPT' ? item.suggestedValue : item.originalValue,
      decision: optimizeDecisions.value[item.fieldPath] || 'REJECT',
    }));
    await applyResumeOptimize({ sourceVersionId: selectedVersionId.value, taskId: generatedTask.value.taskId, changeSummary: '确认 AI 优化建议', idempotencyKey: buildOptimizeApplyKey(), decisions });
    message.success('优化建议已确认并生成新版本');
    await loadVersions();
  } catch (error: any) {
    message.error(error?.message || '优化建议保存失败');
  }
}

function runEditorAction(action: 'diagnose' | 'generate' | 'optimize') {
  editorAction.value = action;
  if (action === 'generate') {
    void generateResume();
    return;
  }
  if (action === 'diagnose' && !selectedResume.value) {
    message.warning('请先选择一份简历');
    return;
  }
  if (action === 'diagnose') {
    void diagnoseResume();
    return;
  }
  void optimizeResume();
}

/**
 * 首页提问：真的发给模型，拿回一段话显示在页面上。
 *
 * 这里原本是正则匹配后跳转（命中「职位」跳职位推荐，命中「简历」跳 AI 简历），
 * 页面上那句「AI 生成内容仅供参考」其实是空头支票 —— 一次模型都没调过。
 * 现在改成单轮真对话：一问一答，不做多轮上下文。
 */
/**
 * 组装真正发给模型的那条消息。
 *
 * 简历正文动辄几千字，不塞进气泡里显示（气泡只留一个文件名标签），
 * 但必须跟着这一问发过去 —— 用户传简历就是想让它读。
 * 一句话都没写时给个默认问法：服务端 message 是 @NotBlank，空着直接被 400 挡回来。
 */
async function submitAssistant() {
  const state = chat.value;
  const prompt = state.draft.trim();
  const attachment = chatAttachment.value;
  // 只带附件不写字也要能发 —— 传张简历照片、一个字不说，是最常见的用法
  if ((!prompt && !attachment) || state.sending) return;
  state.draft = '';
  state.error = '';
  const conversation = ensureConversation();
  conversation.turns.push({
    role: 'user',
    text: prompt,
    // 气泡里只留文件名标签；正文/图片本身不进本地记录
    attachment: attachment ? { kind: attachment.kind, name: attachment.name } : undefined,
    previewUrl: attachment?.kind === 'image' ? attachment.dataUrl : undefined,
    attachmentText: attachment?.kind === 'resume' ? attachment.text : undefined,
    // 原文件留给「打开原文件」。blob URL 只在当前页面生命周期有效，刷新即失效
    fileUrl:
      attachment?.kind === 'resume' && attachment.file
        ? URL.createObjectURL(attachment.file)
        : undefined,
  });
  if (!conversation.title) {
    conversation.title = summarizeChatTitle(prompt || attachment?.name || '');
  }
  conversation.updatedAt = Date.now();
  state.sending = true;
  // 附件在这一轮就消费掉了，先清空再发，免得失败重发时又被带上
  chatAttachment.value = undefined;
  try {
    const resp = await chatWithRecruitAgent(
      composeOutgoingMessage(prompt, attachment),
      attachment?.kind === 'image' ? attachment.dataUrl : undefined,
    );
    const reply = resp?.reply?.trim();
    if (!reply) {
      throw new Error('模型没有返回内容');
    }
    conversation.turns.push({
      role: 'assistant',
      text: reply,
      // 只有服务端判定「这条在列岗位」时才挂卡片。
      // 无条件挂的话，用户问「简历怎么写」底下也会弹十张岗位卡。
      jobs: resp?.listing && resp.jobs?.length ? resp.jobs : undefined,
    });
    conversation.updatedAt = Date.now();
  } catch (error: any) {
    // 用户那句话留着不撤：他得看见自己问了什么，才知道该重发什么
    state.error = error?.message || 'AI 暂时不可用，请稍后重试';
  } finally {
    state.sending = false;
    touchConversation(conversation.id);
    saveChatState(chatPage.value);
  }
}

function saveSettings() {
  localStorage.setItem('hrm-ai-resume-settings', JSON.stringify(settings));
  message.success('Agent 设置已保存');
}

function loadSettings() {
  try {
    const saved = JSON.parse(localStorage.getItem('hrm-ai-resume-settings') || '{}') as Partial<typeof settings>;
    Object.assign(settings, saved);
  } catch {
    // 忽略损坏的本地设置，使用默认值。
  }
}

function loadCurrentData() {
  const key = currentKey.value;
  if (key === 'recommend') void loadJobs();
  // 首页那张「最近投递」卡用的也是 applications，之前只在投递记录页拉过，
  // 导致首页永远显示 0 条 / 还没有投递记录，和投递记录页对不上。
  if (key === 'applications' || key === 'home') void loadApplications();
  if (key === 'job') void loadJobDetail();
}

watch(currentKey, () => {
  // 换页时收起历史浮层：它列的是上一页的对话，留着会让人以为是当前页的
  historyOpen.value = false;
  loadCurrentData();
  // 换到有对话的那一页，同样要停在最新一条
  scrollChatToBottom();
});
// 切换历史对话或来了新消息，都滚到底。盯长度而不是整个数组，避免流式更新时反复触发
watch([() => chat.value.activeId, () => chatTurns.value.length], scrollChatToBottom);
watch(selectedResumeId, () => {
  if (selectedResumeId.value) void loadVersions();
});
watch(selectedVersionId, (id) => {
  if (id) void loadVersion(id);
});
/*
 * 这里原本挂了一个监听筛选条件的 watch，用来在条件变化时重新拉列表。
 * 现在条件只在筛选面板点「确定」时变，applyFilters() 自己就把列表拉了，
 * 留着这个 watch 只会每次多打一次接口。
 */
watch(applicationStatusFilter, () => {
  if (currentKey.value === 'applications') void loadApplications();
});

onMounted(async () => {
  document.addEventListener('mousedown', onAttachMenuPointerDown);
  pageLoading.value = true;
  loadSettings();
  // 两个页面的对话各存各的，进页面先把本地记录读回来
  loadChatState('home');
  loadChatState('recommend');
  await Promise.allSettled([
    loadResumes(),
    loadJobs(),
    loadPostTypes(),
    // 横幅那行显示的是已保存的求职偏好，进页面就得先拿到
    loadPreferences(),
  ]);
  await loadVersions();
  loadCurrentData();
  pageLoading.value = false;
  /*
   * 必须放在关掉 pageLoading **之后**。
   * 对话区在 v-if="pageLoading" 的分支里，前面调用时那个容器还不存在，
   * homeStage.value 是 undefined，等于什么都没做 —— 而且不报错，
   * lint 和编译都查不出「调用时机不对」这类问题。
   */
  scrollChatToBottom();
});

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onAttachMenuPointerDown);
});
</script>

<template>
  <div class="ai-resume-app" :class="{ 'sidebar-collapsed': sidebarCollapsed }">
    <!--
      「上传附件简历」用的隐藏文件框，只此一个，挂在应用根上。
      原先在求职助理和 AI 简历两个互斥分支里各放了一个、共用同一个 ref ——
      切页时 ref 指向哪一个、什么时候被卸载，依赖 Vue 内部的挂载顺序，
      是个不该留的隐患。放在根节点上，任何页面点上传都能拿到它。
    -->
    <input
      ref="uploadInput"
      class="visually-hidden"
      type="file"
      accept=".pdf,.doc,.docx"
      @change="handleUpload"
    />
    <aside class="ai-resume-sidebar">
      <div class="ai-brand">
        <button class="ai-brand-mark" type="button" aria-label="返回招聘门户" @click="goRecruitment">
          <img src="/hrm/brand-logo.png" alt="天信管业" class="ai-brand-logo" />
          <span class="brand-copy"><strong>天信招聘</strong><small>TIANXIN CAREERS</small></span>
        </button>
        <button
          class="ai-sidebar-collapse"
          type="button"
          :aria-label="sidebarCollapsed ? '展开导航' : '收起导航'"
          :title="sidebarCollapsed ? '展开导航' : '收起导航'"
          :aria-expanded="!sidebarCollapsed"
          @click="toggleSidebar"
        >
          <IconifyIcon :icon="sidebarCollapsed ? 'lucide:panel-left-open' : 'lucide:panel-left-close'" />
        </button>
      </div>

      <nav class="ai-sidebar-nav" aria-label="AI 简历导航">
        <button
          v-for="item in visibleNavItems"
          :key="item.key"
          type="button"
          class="ai-nav-item"
          :class="{ active: currentKey === item.key }"
          @click="go(item.key)"
        >
          <IconifyIcon :icon="item.icon" />
          <span>{{ item.label }}</span>
        </button>
      </nav>

      <div class="ai-sidebar-footer">
        <button class="ai-user-card" type="button" @click="go('profile')">
          <span class="ai-avatar">{{ initials }}</span>
          <span class="ai-user-copy"><strong>{{ displayName }}</strong><small>求职者</small></span>
          <IconifyIcon icon="lucide:chevron-right" />
        </button>
        <button class="ai-back-link" type="button" @click="goRecruitment">
          <IconifyIcon icon="lucide:arrow-left" /> 返回招聘门户
        </button>
      </div>
    </aside>

    <main class="ai-resume-main">
      <!--
        这里原本有一条顶栏（面包屑 + h1 + 新手引导按钮），现在整条去掉：
        面包屑和 h1 跟各页自己的 h2 重复；新手引导按钮按需求也删了。
        参考站那一页同样没有标题栏。
      -->

      <div v-if="pageLoading" class="ai-page-loading" role="status">
        <IconifyIcon icon="lucide:loader-circle" class="spin" /> 正在加载你的求职工作台
      </div>

      <section v-else-if="currentKey === 'home'" class="workspace home-workspace">
        <!-- 这一页没有顶栏，历史记录就落在内容区右上角（.is-corner 给定位） -->
        <ChatHistory
          class="is-corner"
          :active-id="chat.activeId"
          :conversations="chat.conversations"
          :open="historyOpen"
          @close="closeHistory"
          @create="startNewConversation"
          @select="openConversation"
          @toggle="toggleHistory"
        />
        <!--
          版式对齐参考站的求职助理首页：中间留白、输入框贴底居中。
          参考站中间是空的（空对话态），这里把三张状态卡放进那片留白居中排布，
          信息不丢，但仍是一块「安静」的区域，不和底部的输入框抢注意力。
        -->
        <div ref="homeStage" class="home-stage" :class="{ 'is-chatting': chatActive }">
          <div v-if="chatActive" class="chat-panel">
            <div class="chat-thread">
              <!--
                一轮 = 气泡 +（可选）岗位卡片。
                卡片挂在气泡外面而不是里面：气泡是 max-width 88% 的窄条，
                塞进去每张卡都会被压成两行；包一层才能让卡片占满整栏。
              -->
              <div
                v-for="(item, index) in chatTurns"
                :key="`${index}-${item.role}`"
                class="chat-turn"
                :class="`is-${item.role}`"
              >
                <div class="chat-bubble" :class="`is-${item.role}`">
                  <span class="chat-mark">
                    <IconifyIcon :icon="item.role === 'user' ? 'lucide:user-round' : 'lucide:sparkles'" />
                  </span>
                  <p v-if="item.text">{{ item.text }}</p>
                  <!--
                    只带附件没打字时气泡里是空的，就只剩一个黑头像圈 ——
                    附件必须在这里露出来，否则用户看到的只是个点，以为坏了。
                  -->
                  <button
                    v-if="item.previewUrl"
                    class="bubble-attach is-image"
                    type="button"
                    :aria-label="`放大查看 ${item.attachment?.name || '附件图片'}`"
                    @click="previewImage = item.previewUrl"
                  >
                    <img :src="item.previewUrl" :alt="item.attachment?.name || '附件图片'" />
                  </button>
                  <button
                    v-else-if="item.attachment"
                    class="bubble-file"
                    type="button"
                    :title="item.fileUrl ? '在新标签打开原文件' : '刷新后原文件不再保留'"
                    @click="openAttachment(item.fileUrl)"
                  >
                    <span class="bubble-file-icon"><IconifyIcon icon="lucide:file-text" /></span>
                    <span class="bubble-file-copy">
                      <strong>{{ item.attachment.name }}</strong>
                      <small>简历文件 · 已提取正文</small>
                    </span>
                  </button>
                </div>
                <div v-if="item.jobs?.length" class="chat-jobs">
                  <button
                    v-for="job in item.jobs"
                    :key="job.id"
                    class="chat-job"
                    type="button"
                    @click="openChatJob(job)"
                  >
                    <span class="chat-job-head">
                      <strong>{{ job.postName }}</strong>
                      <b>{{ job.salary || '面议' }}</b>
                    </span>
                    <span class="chat-job-facts">
                      <span>{{ job.areaName || '地点不限' }}</span>
                      <span>{{ job.education || '学历不限' }}</span>
                      <span>{{ job.experience || '经验不限' }}</span>
                    </span>
                    <small>{{ job.companyName || '天信合作企业' }}</small>
                  </button>
                </div>
              </div>
              <div v-if="chat.sending" class="chat-turn is-assistant">
                <div class="chat-bubble is-assistant">
                  <span class="chat-mark"><IconifyIcon icon="lucide:sparkles" /></span>
                  <p class="chat-typing">正在思考…</p>
                </div>
              </div>
            </div>
            <p v-if="chat.error" class="chat-error" role="alert">{{ chat.error }}</p>
            <button class="chat-reset" type="button" @click="startNewConversation">开始新对话</button>
          </div>

          <div v-else class="home-overview-grid">
            <button class="overview-card" type="button" @click="go('profile')">
              <span class="overview-head">
                <span class="overview-icon"><IconifyIcon icon="lucide:user-round-check" /></span>
                <span class="overview-value">{{ profileCompletion }}<small>%</small></span>
              </span>
              <span class="overview-label">个人档案完成度</span>
              <span class="overview-note">{{ profileCompletion >= 80 ? '资料完整，可以开始投递' : '补充资料，获得更准确的推荐' }}</span>
              <span class="progress-track"><i :style="{ width: `${profileCompletion}%` }"></i></span>
            </button>

            <button class="overview-card" type="button" @click="go('resume')">
              <span class="overview-head">
                <span class="overview-icon warm"><IconifyIcon icon="lucide:file-sparkles" /></span>
                <span class="overview-value">{{ resumes.length }}</span>
              </span>
              <span class="overview-label">我的简历</span>
              <span class="overview-note">{{ attachmentResumes.length ? `${attachmentResumes.length} 份附件简历可用` : '还没有上传附件简历' }}</span>
            </button>

            <button class="overview-card" type="button" @click="go('applications')">
              <span class="overview-head">
                <span class="overview-icon blue"><IconifyIcon icon="lucide:send" /></span>
                <span class="overview-value">{{ applications.length }}</span>
              </span>
              <span class="overview-label">最近投递</span>
              <span class="overview-note">{{ applications.length ? `最近一次：${formatApplicationStatus(applications[0])}` : '还没有投递记录' }}</span>
            </button>
          </div>
        </div>

        <div class="home-composer">
          <form class="composer-card" @submit.prevent="submitAssistant">
            <div class="composer-editor">
              <!--
                附件标签。解析出来的正文不显示（几千字），只留文件名让他知道带了什么；
                正文本身会跟着这一问发给模型。
              -->
              <div v-if="chatAttachment || attachLoading" class="composer-attach">
                <span v-if="attachLoading" class="attach-chip is-loading">
                  <IconifyIcon icon="lucide:loader-circle" class="spin" />
                  {{ attachStatus || '正在读取…' }}
                </span>
                <span v-else-if="chatAttachment" class="attach-chip">
                  <IconifyIcon :icon="chatAttachment.kind === 'image' ? 'lucide:image' : 'lucide:file-text'" />
                  {{ chatAttachment.name }}
                  <button type="button" aria-label="移除附件" @click="clearAttachment">
                    <IconifyIcon icon="lucide:x" />
                  </button>
                </span>
              </div>
              <textarea
                v-model="chat.draft"
                aria-label="描述你的求职目标"
                placeholder="说说你想找什么样的工作，比如某个岗位的关键词"
                rows="2"
                @keydown.enter.exact.prevent="submitAssistant"
              ></textarea>
            </div>
            <div class="composer-bar">
              <!--
                「+」改成了附件菜单，而不是直接触发上传。
                原来那个「+」是「上传附件简历并存进简历列表」，和现在要的
                「随手把简历/图片发给助手」是两件事 —— 所以菜单里两项都留着，谁也别顶掉谁。
              -->
              <div ref="attachMenuRoot" class="composer-attach-menu">
                <button
                  class="composer-icon"
                  type="button"
                  aria-label="添加附件"
                  :aria-expanded="attachMenuOpen"
                  @click="toggleAttachMenu"
                >
                  <IconifyIcon icon="lucide:plus" />
                </button>
                <div v-if="attachMenuOpen" class="attach-menu" role="menu">
                  <button type="button" role="menuitem" @click="chooseChatFile('resume')">
                    <IconifyIcon icon="lucide:file-text" />
                    <span><strong>上传简历</strong><small>提取正文，随这条消息发给助手</small></span>
                  </button>
                  <button type="button" role="menuitem" @click="chooseChatFile('image')">
                    <IconifyIcon icon="lucide:image" />
                    <span><strong>上传图片</strong><small>直接给助手看，它能读图</small></span>
                  </button>
                  <button type="button" role="menuitem" @click="chooseProfileUpload">
                    <IconifyIcon icon="lucide:folder-plus" />
                    <span><strong>存为我的简历</strong><small>上传到简历列表，供投递时选用</small></span>
                  </button>
                </div>
              </div>
              <input ref="chatFileInput" class="visually-hidden" type="file" @change="handleChatFile" />
              <button class="composer-chip" type="button" @click="go('recommend')">
                <IconifyIcon icon="lucide:search" /> 职位搜索
              </button>
              <button class="composer-chip" type="button" @click="go('resume')">
                <IconifyIcon icon="lucide:file-pen-line" /> 简历优化
              </button>
              <button class="composer-chip" type="button" @click="go('applications')">
                <IconifyIcon icon="lucide:send" /> 投递记录
              </button>
              <button class="composer-send" type="submit" aria-label="发送" :disabled="!chat.draft.trim() && !chatAttachment">
                <IconifyIcon icon="lucide:arrow-up" />
              </button>
            </div>
          </form>
          <p class="composer-note">AI 生成内容仅供参考，请核实后使用</p>
        </div>
      </section>

      <section v-else-if="currentKey === 'resume'" class="workspace resume-workspace">
        <div class="resume-hero"><div><p class="eyebrow">SMART RESUME STUDIO</p><h2>让每一份经历，更接近下一份工作。</h2><p>导入附件或使用求职资料，AI 会整理出一份可编辑、可追溯的简历草稿。</p></div></div>
        <div class="service-grid"><button type="button" class="service-card service-diagnose" @click="startAgentFlow('analyze')"><span><IconifyIcon icon="lucide:scan-search" /></span><strong>AI 简历诊断</strong><small>快速分析简历问题，给出可执行建议</small><IconifyIcon class="service-arrow" icon="lucide:arrow-up-right" /></button><button type="button" class="service-card service-optimize" @click="startAgentFlow('optimize')"><span><IconifyIcon icon="lucide:file-pen-line" /></span><strong>AI 简历优化</strong><small>结合目标职位，优化表达与匹配度</small><IconifyIcon class="service-arrow" icon="lucide:arrow-up-right" /></button><button type="button" class="service-card service-generate" @click="startAgentFlow('generate')"><span><IconifyIcon icon="lucide:sparkles" /></span><strong>AI 简历生成</strong><small>从你的真实经历生成可编辑简历草稿</small><IconifyIcon class="service-arrow" icon="lucide:arrow-up-right" /></button></div>
        <div class="resume-section-head">
          <h3>我的简历</h3>
          <button type="button" class="upload-action" @click="chooseUpload">
            {{ uploadLoading ? '上传中…' : '上传附件简历' }}
          </button>
        </div>
        <div v-if="visibleResumes.length" class="resume-card-grid">
          <ResumeCard
            v-for="resume in visibleResumes"
            :key="resume.id"
            :diagnosed="diagnosedResumeIds.includes(resume.id)"
            :name="resumeCardName(resume)"
            :optimized="optimizedResumeIds.includes(resume.id)"
            :resume="resume"
            @edit="openResumeWorkspace"
            @file="openResumeFile"
            @preview="openResumePreviewDialog"
            @remove="removeResumeById"
          />
        </div>
        <div v-else class="resume-empty">
          <IconifyIcon icon="lucide:file-plus-2" />
          <strong>还没有简历</strong>
          <p>上传一份附件简历，或先用求职资料生成一份。</p>
        </div>

        <ResumePreviewDialog
          :open="previewOpen"
          :resume="previewResume"
          @close="previewOpen = false"
          @edit="editResumeFromPreview"
          @file="openResumeFile"
        />
      </section>

      <section v-else-if="currentKey === 'recommend'" class="workspace recommend-workspace">
        <div class="recommend-column">
          <div class="recommend-title-row">
            <h2>职位推荐</h2>
            <button class="title-info" type="button" title="推荐依据" aria-label="推荐依据" @click="openPreferenceDrawer">
              <IconifyIcon icon="lucide:info" />
            </button>
          </div>

          <button class="preference-banner" type="button" @click="openPreferenceDrawer">
            <span class="preference-icon"><IconifyIcon icon="lucide:clipboard-list" /></span>
            <span class="preference-copy">
              <strong>求职偏好信息</strong>
              <small>{{ preferenceSummary }}</small>
            </span>
            <span class="preference-hint"><IconifyIcon icon="lucide:info" /> 职位推荐基于求职偏好信息</span>
            <IconifyIcon class="preference-arrow" icon="lucide:arrow-right" />
          </button>

          <div class="list-head">
            <h3>{{ jobKeyword.trim() || profile.expectedPostName || '全部职位' }}</h3>
            <button class="filter-trigger" type="button" @click="openFilterPanel">
              <IconifyIcon icon="lucide:sliders-horizontal" />
              筛选职位
              <span v-if="activeFilterCount" class="filter-count">{{ activeFilterCount }}</span>
            </button>
          </div>

          <div v-if="jobsLoading" class="list-state">
            <IconifyIcon icon="lucide:loader-circle" class="spin" /> 加载职位中
          </div>
          <div v-else-if="!jobs.length" class="list-state">
            <IconifyIcon icon="lucide:search-x" />
            <strong>暂时没有匹配职位</strong>
            <p>放宽筛选条件，或完善求职意向后再试。</p>
          </div>
          <div v-else class="job-list">
            <article
              v-for="post in jobs"
              :key="post.id"
              class="job-card"
              tabindex="0"
              @click="openJob(post)"
              @keydown.enter="openJob(post)"
            >
              <div class="job-card-head">
                <h3>{{ post.postName }}</h3>
                <strong>{{ formatSalary(post) }}</strong>
              </div>
              <ul class="job-facts">
                <li><IconifyIcon icon="lucide:map-pin" />{{ post.areaName || '地点不限' }}</li>
                <li><IconifyIcon icon="lucide:graduation-cap" />{{ educationLabel(post.educationRequire) }}</li>
                <li><IconifyIcon icon="lucide:wallet" />{{ formatSalary(post) }}</li>
                <li><IconifyIcon icon="lucide:clock-3" />{{ workTimeLabel(post.workTime) }}</li>
              </ul>
              <div class="job-card-foot">
                <small>{{ companyLine(post) }}</small>
                <div class="job-card-actions">
                  <button class="solid-action" type="button" @click.stop="openChat(post)">立即沟通</button>
                </div>
              </div>
            </article>
          </div>
        </div>

        <aside class="assistant-rail">
          <header class="rail-head">
            <span class="rail-avatar"><IconifyIcon icon="lucide:bot" /></span>
            <span class="rail-identity">
              <strong>求职助手</strong>
            </span>
            <ChatHistory
              class="is-inline"
              :active-id="chat.activeId"
              :conversations="chat.conversations"
              :open="historyOpen"
              @close="closeHistory"
              @create="startNewConversation"
              @select="openConversation"
              @toggle="toggleHistory"
            />
          </header>

          <div ref="railThread" class="rail-thread">
            <template v-if="chatTurns.length">
              <div v-for="(item, index) in chatTurns" :key="`${index}-${item.role}`" class="rail-turn" :class="`is-${item.role}`">
                <div class="rail-bubble" :class="`is-${item.role}`">
                  <span class="rail-mark"><IconifyIcon :icon="item.role === 'user' ? 'lucide:user-round' : 'lucide:sparkles'" /></span>
                  <p v-if="item.text">{{ item.text }}</p>
                  <button
                    v-if="item.previewUrl"
                    class="bubble-attach is-image"
                    type="button"
                    :aria-label="`放大查看 ${item.attachment?.name || '附件图片'}`"
                    @click="previewImage = item.previewUrl"
                  >
                    <img :src="item.previewUrl" :alt="item.attachment?.name || '附件图片'" />
                  </button>
                  <button
                    v-else-if="item.attachment"
                    class="bubble-file"
                    type="button"
                    :title="item.fileUrl ? '在新标签打开原文件' : '刷新后原文件不再保留'"
                    @click="openAttachment(item.fileUrl)"
                  >
                    <span class="bubble-file-icon"><IconifyIcon icon="lucide:file-text" /></span>
                    <span class="bubble-file-copy">
                      <strong>{{ item.attachment.name }}</strong>
                      <small>简历文件 · 已提取正文</small>
                    </span>
                  </button>
                </div>
                <!-- 岗位卡片同理挂在气泡外：气泡只有 92% 宽，塞进去会被压成两行 -->
                <div v-if="item.jobs?.length" class="chat-jobs">
                  <button
                    v-for="job in item.jobs"
                    :key="job.id"
                    class="chat-job"
                    type="button"
                    @click="openChatJob(job)"
                  >
                    <span class="chat-job-head">
                      <strong>{{ job.postName }}</strong>
                      <b>{{ job.salary || '面议' }}</b>
                    </span>
                    <span class="chat-job-facts">
                      <span>{{ job.areaName || '地点不限' }}</span>
                      <span>{{ job.education || '学历不限' }}</span>
                      <span>{{ job.experience || '经验不限' }}</span>
                    </span>
                    <small>{{ job.companyName || '天信合作企业' }}</small>
                  </button>
                </div>
              </div>
              <div v-if="chat.sending" class="rail-turn is-assistant">
                <div class="rail-bubble is-assistant">
                  <span class="rail-mark"><IconifyIcon icon="lucide:sparkles" /></span>
                  <p class="chat-typing">正在思考…</p>
                </div>
              </div>
            </template>
            <template v-else>
              <h3 class="rail-greeting">Hi~你好！</h3>
              <p class="rail-lede">我是你的求职助手，很高兴为你服务。</p>
              <div class="rail-suggest">
                <strong>你可以试试问我这些</strong>
                <ul>
                  <li v-for="tip in ASSISTANT_TIPS" :key="tip">
                    <button type="button" @click="sendSuggestion(tip)">{{ tip }}</button>
                  </li>
                </ul>
              </div>
            </template>
            <p v-if="chat.error" class="rail-error" role="alert">{{ chat.error }}</p>
          </div>

          <form class="rail-composer" @submit.prevent="submitAssistant">
            <textarea
              v-model="chat.draft"
              aria-label="向 AI 求职助手提问"
              placeholder="可以继续问我问题哦"
              rows="2"
              @keydown.enter.exact.prevent="submitAssistant"
            ></textarea>
            <button class="rail-send" type="submit" aria-label="发送" :disabled="!chat.draft.trim()">
              <IconifyIcon icon="lucide:arrow-up" />
            </button>
          </form>
          <p class="rail-note">内容由 AI 生成，请仔细甄别</p>
        </aside>

        <div v-if="filterPanelOpen" class="filter-scrim" @click.self="filterPanelOpen = false"></div>
        <section v-if="filterPanelOpen" class="filter-panel" role="dialog" aria-label="筛选条件" aria-modal="true">
          <header class="panel-head">
            <h3>筛选条件</h3>
            <button class="panel-close" type="button" aria-label="关闭筛选" @click="filterPanelOpen = false">
              <IconifyIcon icon="lucide:x" />
            </button>
          </header>

          <div class="panel-body">
            <div class="filter-row">
              <span class="filter-row-label">关键词</span>
              <div class="filter-row-body">
                <input v-model="filterDraft.postName" class="filter-input" placeholder="职位名称，如：防腐工" />
                <input v-model="filterDraft.city" class="filter-input" placeholder="工作城市，如：西安" />
              </div>
            </div>

            <div class="filter-row">
              <span class="filter-row-label">职位类型</span>
              <div class="filter-row-body">
                <button :class="{ active: filterDraft.postTypeId == null }" class="filter-chip" type="button" @click="filterDraft.postTypeId = undefined">不限</button>
                <button v-for="type in postTypes" :key="type.id" :class="{ active: filterDraft.postTypeId === type.id }" class="filter-chip" type="button" @click="filterDraft.postTypeId = type.id">{{ type.name }}</button>
              </div>
            </div>

            <div class="filter-row">
              <span class="filter-row-label">薪资待遇</span>
              <div class="filter-row-body">
                <button v-for="band in SALARY_BANDS" :key="band.key" :class="{ active: filterDraft.salaryBand === band.key }" class="filter-chip" type="button" @click="filterDraft.salaryBand = band.key">{{ band.label }}</button>
              </div>
            </div>

            <div class="filter-row">
              <span class="filter-row-label">经验要求</span>
              <div class="filter-row-body">
                <button v-for="option in WORK_TIME_OPTIONS" :key="option.value" :class="{ active: filterDraft.workTime === option.value }" class="filter-chip" type="button" @click="filterDraft.workTime = option.value">{{ option.label }}</button>
              </div>
            </div>

            <div class="filter-row">
              <span class="filter-row-label">学历要求</span>
              <div class="filter-row-body">
                <button v-for="option in HrmRecruitPostEducationOptions" :key="option.value" :class="{ active: filterDraft.education === option.value }" class="filter-chip" type="button" @click="filterDraft.education = option.value">{{ option.label }}</button>
              </div>
            </div>

            <div class="filter-row">
              <span class="filter-row-label">排序方式</span>
              <div class="filter-row-body">
                <button :class="{ active: filterDraft.sort === 'latest' }" class="filter-chip" type="button" @click="filterDraft.sort = 'latest'">最新发布</button>
                <button :class="{ active: filterDraft.sort === 'salary' }" class="filter-chip" type="button" @click="filterDraft.sort = 'salary'">薪资优先</button>
              </div>
            </div>
          </div>

          <footer class="panel-foot">
            <button class="ghost-button" type="button" @click="resetFilters">清除筛选</button>
            <div class="panel-foot-right">
              <button class="ghost-button" type="button" @click="filterPanelOpen = false">取消</button>
              <button class="solid-button" type="button" @click="applyFilters">确定</button>
            </div>
          </footer>
        </section>

        <div v-if="preferenceDrawerOpen" class="preference-scrim" @click.self="preferenceDrawerOpen = false"></div>
        <aside v-if="preferenceDrawerOpen" class="preference-drawer" role="dialog" aria-label="求职偏好信息" aria-modal="true">
          <header class="drawer-head">
            <div>
              <h3>求职偏好信息</h3>
              <p>信息完善后，将保存在个人信息内。</p>
            </div>
            <button class="panel-close" type="button" aria-label="关闭求职偏好" @click="preferenceDrawerOpen = false">
              <IconifyIcon icon="lucide:x" />
            </button>
          </header>

          <div class="drawer-body">
            <div v-if="preferenceLoading" class="drawer-loading">
              <IconifyIcon icon="lucide:loader-circle" class="spin" /> 正在读取求职资料
            </div>
            <template v-else>
              <section class="drawer-section">
                <h4>求职期望</h4>

                <label class="pref-field">
                  <span>期望职位</span>
                  <select v-model="preferenceForm.expectedPostId">
                    <option :value="undefined">请选择职位</option>
                    <option v-for="type in postTypes" :key="type.id" :value="type.id">{{ type.name }}</option>
                  </select>
                </label>

                <div class="pref-field">
                  <span>意向城市 <small>最多 3 个</small></span>
                  <div class="pref-cities">
                    <span v-for="(city, index) in preferenceForm.expectedCities" :key="city" class="pref-city">
                      {{ city }}
                      <button type="button" :aria-label="`移除 ${city}`" @click="removeExpectedCity(index)">
                        <IconifyIcon icon="lucide:x" />
                      </button>
                    </span>
                    <CityPicker
                      v-if="preferenceForm.expectedCities.length < 3"
                      align="left"
                      :counts="cityPostCounts"
                      :label="preferenceForm.expectedCities.length ? '继续添加…' : '添加城市…'"
                      panel-width="calc(min(440px, 100vw) - 44px)"
                      :selected="preferenceForm.expectedCities"
                      @pick="addExpectedCityByName"
                    />
                    <span v-else class="pref-city-full">已选满 3 个</span>
                  </div>
                </div>

                <label class="pref-field">
                  <span>薪资要求 <small>月薪</small></span>
                  <select v-model="preferenceForm.salary">
                    <option v-for="item in SALARY_OPTIONS" :key="item.value" :value="item.value">{{ item.label }}</option>
                  </select>
                </label>

                <div class="pref-field">
                  <span>岗位类型</span>
                  <div class="pref-chips">
                    <button v-for="item in JOB_TYPE_OPTIONS" :key="item.value" :class="{ active: preferenceForm.jobType === item.value }" class="filter-chip" type="button" @click="preferenceForm.jobType = item.value">{{ item.label }}</button>
                  </div>
                </div>

                <label class="pref-field">
                  <span>期望行业</span>
                  <select v-model="preferenceForm.industry">
                    <option value="">请选择行业</option>
                    <option v-for="item in INDUSTRY_OPTIONS" :key="item" :value="item">{{ item }}</option>
                  </select>
                </label>
              </section>

              <p class="drawer-note">
                <IconifyIcon icon="lucide:info" />
                这里改的是往后的职位推荐依据，已经投出去的记录不受影响。
              </p>
            </template>
          </div>

          <footer class="drawer-foot">
            <button class="ghost-button" type="button" @click="preferenceDrawerOpen = false">取消</button>
            <button class="solid-button" type="button" :disabled="preferenceSaving" @click="savePreferences">
              {{ preferenceSaving ? '保存中' : '保存' }}
            </button>
          </footer>
        </aside>
      </section>

      <section v-else-if="currentKey === 'job'" class="workspace job-detail-workspace"><button type="button" class="back-inline" @click="go('recommend')"><IconifyIcon icon="lucide:arrow-left" /> 返回职位推荐</button><div v-if="selectedPost" class="job-detail-grid"><article class="job-detail-card"><div class="job-detail-top"><div><p class="eyebrow">OPEN POSITION</p><h2>{{ selectedPost.postName }}</h2><p>{{ selectedPost.companyShortName || selectedPost.companyName || '天信合作企业' }}</p></div><strong class="job-detail-salary">{{ formatSalary(selectedPost) }}</strong></div><div class="job-facts"><span><IconifyIcon icon="lucide:map-pin" /> {{ selectedPost.areaName || '地点不限' }}</span><span><IconifyIcon icon="lucide:graduation-cap" /> 学历要求</span><span><IconifyIcon icon="lucide:clock-3" /> 经验要求</span><span><IconifyIcon icon="lucide:users-round" /> 招聘 {{ selectedPost.recruitNum || '若干' }} 人</span></div><div class="job-detail-copy"><h3>职位描述</h3><p>{{ selectedPost.description || '职位描述将在招聘方发布后展示。' }}</p></div><div class="job-detail-actions"><button type="button" class="primary-action" @click="openChat(selectedPost)"><IconifyIcon icon="lucide:messages-square" /> 立即沟通</button></div></article><aside class="job-detail-side"><div class="aside-card"><span class="assistant-orb small"><IconifyIcon icon="lucide:sparkles" /></span><h3>简历匹配提示</h3><p>准备一份针对这个职位的简历版本，可以让招聘方更快看到你的相关经历。</p><button type="button" class="aside-cta" @click="go('resume')">打开简历 AI <IconifyIcon icon="lucide:arrow-up-right" /></button></div></aside></div><div v-else class="list-state"><IconifyIcon icon="lucide:file-question" /><strong>职位不存在或已下线</strong><button type="button" class="secondary-action" @click="go('recommend')">返回职位推荐</button></div></section>

      <section v-else-if="currentKey === 'profile'" class="workspace profile-workspace"><div class="profile-header-card"><div class="profile-avatar-large">{{ initials }}</div><div><p class="eyebrow">YOUR PROFILE</p><h2>{{ profile.name || displayName }}</h2><p>{{ profile.expectedPostName || '还没有填写期望职位' }} · {{ profile.city || '城市不限' }}</p></div><div class="profile-progress"><strong>{{ profileCompletion }}%</strong><small>资料完成度</small><div class="progress-track"><i :style="{ width: `${profileCompletion}%` }"></i></div></div><button type="button" class="secondary-action" @click="openProfileEditor"><IconifyIcon icon="lucide:pen-line" /> 编辑档案</button></div><div class="profile-content-grid"><div class="profile-main-card"><div class="profile-card-title"><h3>档案详情</h3><span>来自 HRM 求职资料</span></div><div class="profile-fields"><div><small>期望职位</small><strong>{{ profile.expectedPostName || '未填写' }}</strong></div><div><small>期望城市</small><strong>{{ profile.expectedCities?.join('、') || profile.city || '未填写' }}</strong></div><div><small>学历</small><strong>{{ profile.education || '未填写' }}</strong></div><div><small>学校 / 专业</small><strong>{{ [profile.school, profile.major].filter(Boolean).join(' · ') || '未填写' }}</strong></div><div><small>联系方式</small><strong>{{ profile.mobile || '未填写' }} {{ profile.email ? `· ${profile.email}` : '' }}</strong></div></div><div class="profile-section"><h4>个人优势</h4><p>{{ profile.strengths || '还没有填写个人优势。' }}</p></div><div class="profile-section"><h4>专业技能</h4><div v-if="profile.skills" class="draft-tags"><span v-for="skill in profile.skills.split(/[、,，\n]/).filter(Boolean)" :key="skill">{{ skill }}</span></div><p v-else>还没有填写专业技能。</p></div></div><aside class="profile-side-card"><h3>资料清单</h3><div class="check-list"><span :class="{ done: Boolean(profile.name) }"><IconifyIcon :icon="profile.name ? 'lucide:circle-check' : 'lucide:circle'" /> 基本信息</span><span :class="{ done: Boolean(profile.expectedPostName) }"><IconifyIcon :icon="profile.expectedPostName ? 'lucide:circle-check' : 'lucide:circle'" /> 求职意向</span><span :class="{ done: Boolean(profile.school) }"><IconifyIcon :icon="profile.school ? 'lucide:circle-check' : 'lucide:circle'" /> 教育经历</span><span :class="{ done: Boolean(profile.skills) }"><IconifyIcon :icon="profile.skills ? 'lucide:circle-check' : 'lucide:circle'" /> 专业技能</span></div><button type="button" class="aside-cta" @click="openProfileEditor">完善求职资料 <IconifyIcon icon="lucide:arrow-up-right" /></button></aside></div></section>

      <section v-else-if="currentKey === 'applications'" class="workspace applications-workspace"><div class="section-intro-row"><div><p class="eyebrow">YOUR APPLICATIONS</p><h2>投递记录</h2><p>每一次投递都保留它当时使用的简历版本和职位上下文。</p></div><button type="button" class="secondary-action" @click="go('recommend')"><IconifyIcon icon="lucide:search" /> 浏览职位</button></div><div class="application-toolbar"><label><span>状态筛选</span><select v-model="applicationStatusFilter"><option value="">全部状态</option><option value="SUBMITTED">已投递</option><option value="SCREENING">筛选中</option><option value="INTERVIEW">面试中</option><option value="OFFER">已发 Offer</option><option value="REJECTED">未通过</option><option value="WITHDRAWN">已撤回</option></select></label><span>{{ applications.length }} 条记录</span></div><div v-if="applicationsLoading" class="list-state"><IconifyIcon icon="lucide:loader-circle" class="spin" /> 加载投递记录中</div><div v-else-if="!applications.length" class="empty-record"><span><IconifyIcon icon="lucide:clipboard-list" /></span><h3>还没有投递记录</h3><p>从职位推荐中选择一个适合你的机会，开始第一次投递。</p><button type="button" class="primary-action" @click="go('recommend')">去浏览职位</button></div><div v-else class="record-list"><article v-for="application in applications" :key="application.id" class="record-card"><div class="record-main"><span class="record-icon"><IconifyIcon icon="lucide:send" /></span><div><h3>{{ application.postName || `职位 #${application.postId}` }}</h3><p>{{ application.ownerUserName || '招聘团队' }} · {{ application.createTime || '最近投递' }}</p></div></div><span class="record-status">{{ formatApplicationStatus(application) }}</span><button type="button" class="icon-action" title="查看投递详情" aria-label="查看投递详情" @click="loadApplicationDetail(application.id)"><IconifyIcon icon="lucide:receipt-text" /></button><button type="button" class="icon-action" title="查看职位" aria-label="查看职位" @click="router.push({ name: 'AiResumeJob', params: { id: application.postId } })"><IconifyIcon icon="lucide:arrow-up-right" /></button></article></div><aside v-if="applicationDetail" class="application-detail-panel"><div class="detail-panel-heading"><div><p class="eyebrow">APPLICATION DETAIL</p><h3>{{ applicationDetail.postName || `职位 #${applicationDetail.postId}` }}</h3></div><button type="button" class="icon-action" title="关闭详情" aria-label="关闭详情" @click="applicationDetail = undefined"><IconifyIcon icon="lucide:x" /></button></div><dl><div><dt>当前状态</dt><dd>{{ formatApplicationStatus(applicationDetail) }}</dd></div><div><dt>投递时间</dt><dd>{{ applicationDetail.createTime || '—' }}</dd></div><div><dt>简历附件</dt><dd>{{ applicationDetail.resumeFileName || '在线简历版本' }}</dd></div><div><dt>招聘负责人</dt><dd>{{ applicationDetail.ownerUserName || '招聘团队' }}</dd></div></dl><section v-if="applicationDetail.interviews?.length" class="timeline"><h4>面试安排</h4><div v-for="interview in applicationDetail.interviews" :key="interview.id" class="timeline-item"><strong>{{ interview.interviewTime || '待定' }}</strong><span>{{ interview.address || '面试地点待确认' }}</span><small>{{ interview.remark || '等待招聘方更新结果' }}</small></div></section><button v-if="!['WITHDRAWN', 'REJECTED', 'HIRED'].includes(applicationDetail.status)" type="button" class="secondary-action" :disabled="applicationDetailLoading" @click="withdrawApplication(applicationDetail)"><IconifyIcon icon="lucide:undo-2" /> 撤回投递</button></aside></section>

      <section v-else-if="currentKey === 'settings'" class="workspace settings-workspace"><div class="section-intro-row"><div><p class="eyebrow">YOUR AGENT</p><h2>Agent 设置</h2><p>调整 AI 助手如何理解你的偏好和沟通方式。</p></div><button type="button" class="primary-action" @click="saveSettings"><IconifyIcon icon="lucide:save" /> 保存设置</button></div><div class="settings-layout"><aside class="settings-nav"><button class="active" type="button">沟通设置</button><button type="button">浏览器通知</button><button type="button">AI 长期记忆</button><button type="button">工作模式</button></aside><div class="settings-content"><section class="settings-section"><h3>沟通设置</h3><p>这段话会作为你与招聘方第一次沟通时的建议内容。</p><textarea v-model="settings.communication" rows="4"></textarea></section><section class="settings-section settings-row"><div><h3>浏览器通知</h3><p>任务完成、职位推荐和招聘消息到达时提醒我。</p></div><button class="switch" :class="{ on: settings.browserNotice }" type="button" :aria-pressed="settings.browserNotice" @click="settings.browserNotice = !settings.browserNotice"><i></i></button></section><section class="settings-section settings-row"><div><h3>AI 长期记忆</h3><p>记住我主动提供的求职偏好，不记录敏感身份信息。</p></div><button class="switch" :class="{ on: settings.memory }" type="button" :aria-pressed="settings.memory" @click="settings.memory = !settings.memory"><i></i></button></section><section class="settings-section"><h3>工作模式</h3><p>所有会影响简历、投递或消息的操作都需要你的确认。</p><div class="mode-options"><button type="button" :class="{ active: settings.workMode === 'confirm' }" @click="settings.workMode = 'confirm'"><IconifyIcon icon="lucide:shield-check" /><strong>确认后执行</strong><small>推荐，所有外部动作由你确认</small></button><button type="button" :class="{ active: settings.workMode === 'suggest' }" @click="settings.workMode = 'suggest'"><IconifyIcon icon="lucide:lightbulb" /><strong>只给建议</strong><small>AI 只提供分析和内容建议</small></button></div></section></div></div></section>
    </main>

    <!--
      附件预览。放在根容器末尾而不是各自页面里 —— 两个页面（求职助理 / 职位推荐右侧）
      都可能出现附件，放一份省得维护两处。
    -->
    <div v-if="previewImage" class="preview-scrim" @click.self="previewImage = ''">
      <img :src="previewImage" alt="附件大图" />
      <button class="preview-close" type="button" aria-label="关闭预览" @click="previewImage = ''">
        <IconifyIcon icon="lucide:x" />
      </button>
    </div>

  </div>
</template>

<style scoped>
:global(html.ai-resume-document) { color-scheme: light; }
.ai-resume-app { --ink: #1f2b2b; --muted: #7d8984; --line: #e2e7df; --paper: #fff; --canvas: #f8f9f5; --soft: #eef2ea; --olive: #6e8d57; --olive-deep: #4e6840; --coral: #d87850; --blue: #4f7c95; display: flex; min-height: 100vh; background: var(--canvas); color: var(--ink); font-family: 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif; }
.ai-resume-sidebar { position: fixed; z-index: 3; display: flex; width: 246px; min-height: 100vh; flex-direction: column; border-right: 1px solid #e8ebe4; background: #f1f3ed; transition: width .2s ease; }
.ai-brand { display: flex; align-items: center; justify-content: space-between; height: 84px; padding: 0 21px 0 25px; border-bottom: 1px solid #e8ebe4; }
.ai-brand-mark, .ai-sidebar-collapse, .ai-nav-item, .ai-user-card, .ai-back-link, .guide-button, .text-action, .icon-action, .back-inline { border: 0; background: transparent; cursor: pointer; font: inherit; }
.ai-brand-mark { display: inline-flex; align-items: center; gap: 10px; color: #123f49; text-align: left; }
.ai-brand-logo { width: 39px; height: 39px; object-fit: contain; }
.brand-copy { display: grid; gap: 2px; line-height: 1; }
.brand-copy strong { font-size: 18px; font-weight: 800; letter-spacing: .02em; }
.brand-copy small { color: #78929b; font-size: 9px; letter-spacing: .14em; }
.ai-sidebar-collapse { color: #9aa49c; font-size: 19px; }
.ai-sidebar-nav { display: grid; gap: 5px; padding: 46px 9px 18px; }
.ai-nav-item { position: relative; display: flex; align-items: center; gap: 15px; min-height: 48px; padding: 0 14px; border-radius: 9px; color: #4e5969; font-size: 16px; text-align: left; transition: background 160ms ease, color 160ms ease; }
.ai-nav-item:hover { background: #e8eee3; color: var(--olive-deep); }
.ai-nav-item.active { background: #fff; color: #000; box-shadow: 0 2px 8px rgb(10 27 51 / 5%); }
.ai-nav-item.active::before { position: absolute; top: 10px; bottom: 10px; left: 0; width: 3px; border-radius: 0 3px 3px 0; background: var(--olive); content: ''; }
.ai-nav-item > svg { width: 19px; color: #6e7973; }
.ai-nav-item.active > svg { color: var(--olive-deep); }
.nav-unread { width: 7px; height: 7px; margin-left: auto; border-radius: 50%; background: var(--coral); }
.ai-sidebar-footer { display: grid; gap: 17px; margin-top: auto; padding: 18px 17px 23px; border-top: 1px solid #e1e6de; }
.ai-user-card { display: flex; align-items: center; gap: 10px; padding: 3px 0; color: var(--ink); text-align: left; }
.ai-avatar, .profile-avatar-large, .conversation-avatar { display: grid; place-items: center; flex: 0 0 auto; border-radius: 50%; background: #d8e5ce; color: var(--olive-deep); font-weight: 700; }
.ai-avatar { width: 36px; height: 36px; font-size: 14px; }
.ai-user-copy { display: grid; min-width: 0; gap: 2px; flex: 1; }
.ai-user-copy strong { overflow: hidden; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.ai-user-copy small { color: var(--muted); font-size: 11px; }
.ai-user-card > svg { color: #9aa49c; font-size: 16px; }
.ai-back-link { display: inline-flex; align-items: center; gap: 8px; padding: 0; color: #78847d; font-size: 12px; text-align: left; }
.ai-back-link:hover, .text-action:hover, .back-inline:hover { color: var(--olive-deep); }
.ai-resume-main { width: calc(100% - 246px); min-height: 100vh; margin-left: 246px; transition: width .2s ease, margin-left .2s ease; }
.sidebar-collapsed .ai-resume-sidebar { width: 70px; }
.sidebar-collapsed .ai-resume-main { width: calc(100% - 70px); margin-left: 70px; }
.sidebar-collapsed .ai-brand { position: relative; justify-content: center; padding: 0; }
.sidebar-collapsed .brand-copy,
.sidebar-collapsed .ai-nav-item span:not(.nav-unread),
.sidebar-collapsed .ai-user-copy,
.sidebar-collapsed .ai-user-card > svg,
.sidebar-collapsed .ai-back-link { display: none; }
.sidebar-collapsed .ai-brand-mark { gap: 0; }
.sidebar-collapsed .ai-sidebar-collapse { position: absolute; top: 7px; right: 4px; display: inline-flex; padding: 2px; font-size: 15px; }
.workspace { width: min(1260px, calc(100% - 80px)); margin: 0 auto; padding: 45px 0 68px; }
.eyebrow { margin: 0 0 9px; color: #97a499; font-size: 10px; font-weight: 700; letter-spacing: .19em; }
.workspace h2 { margin: 0; font-family: Georgia, 'Noto Serif SC', serif; font-size: clamp(26px, 3vw, 40px); font-weight: 600; line-height: 1.2; }
.workspace h3, .workspace h4, .workspace p { margin-top: 0; }
.section-intro-row > div > p:last-child { margin: 14px 0 0; color: #7b8781; font-size: 14px; line-height: 1.75; }
.aside-card-heading { display: flex; align-items: center; gap: 11px; }
.aside-card-heading > div { display: grid; gap: 2px; }
.aside-card-heading strong { font-size: 14px; }
.aside-card-heading small { color: #8b958e; font-size: 11px; }
.assistant-orb { display: grid; width: 36px; height: 36px; place-items: center; border-radius: 50%; background: #dce9c3; color: var(--olive-deep); font-size: 18px; }
.assistant-orb.small { width: 31px; height: 31px; font-size: 15px; }
.progress-track { height: 4px; overflow: hidden; border-radius: 4px; background: #e8ece5; }
.progress-track i { display: block; height: 100%; border-radius: inherit; background: var(--olive); }
.resume-hero { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; padding-bottom: 27px; border-bottom: 1px solid var(--line); }
.resume-hero > div { max-width: 650px; }
.resume-hero p:last-child { margin: 14px 0 0; color: #7b8781; font-size: 14px; line-height: 1.7; }
.primary-action, .secondary-action, .dark-action { display: inline-flex; align-items: center; justify-content: center; gap: 7px; min-height: 39px; padding: 0 15px; border-radius: 7px; cursor: pointer; font: inherit; font-size: 12px; }
.primary-action { border: 1px solid #5f8350; background: #668a57; color: #fff; }
.primary-action:hover { background: #4f703f; }
.primary-action.small { min-height: 33px; padding: 0 12px; }
.secondary-action { border: 1px solid #d7e0d7; background: #fff; color: #55645b; }
.secondary-action:hover { border-color: #a9c19e; color: var(--olive-deep); }
.dark-action { min-height: 34px; border: 1px solid #27302d; background: #27302d; color: #fff; }
.dark-action:hover { background: #111916; }
.service-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 15px; margin-top: 22px; }
.service-card { position: relative; display: grid; gap: 8px; min-height: 126px; padding: 18px 43px 16px 20px; border: 1px solid var(--line); border-radius: 13px; background: #fff; color: var(--ink); cursor: pointer; text-align: left; }
.service-card:hover { transform: translateY(-2px); box-shadow: 0 10px 26px rgb(61 78 57 / 8%); }
.service-card > span { display: grid; width: 33px; height: 33px; place-items: center; border-radius: 9px; background: #edf4e6; color: var(--olive); font-size: 18px; }
.service-card strong { font-size: 14px; }
.service-card small { color: #8a958e; font-size: 12px; line-height: 1.5; }
.service-card.service-optimize > span { background: #e9f1f5; color: var(--blue); }
.service-card.service-generate > span { background: #fbede6; color: var(--coral); }
.service-arrow { position: absolute; top: 20px; right: 18px; color: #a2ada4; font-size: 17px; }
.resume-section-head { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-top: 39px; }
.resume-section-head h3 { margin: 0; color: #141414; font-size: 20px; font-weight: 500; line-height: 28px; }
.text-action { display: inline-flex; align-items: center; gap: 5px; padding: 0; color: var(--olive-deep); font-size: 12px; }
/* 参考站：标题行右侧是一枚黑色胶囊按钮，不是文字链接 */
.upload-action { flex-shrink: 0; padding: 7px 16px; border: 0; border-radius: 999px; background: #141414; color: #fff; font-size: 13px; font-weight: 500; line-height: 20px; cursor: pointer; transition: background .2s ease-in-out; }
.upload-action:hover { background: #2e2e2e; }
/*
 * 卡片网格：参考站是固定 5 列、每格内部居中的 205px 封面。
 * 这里改成 auto-fill + minmax：窗口窄下去自动减列，不用再写一串断点；
 * 205px 是封面的实际宽度，留 16px 的缝和参考站的 gap-4 一致。
 */
.resume-card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(205px, 1fr)); gap: 16px; margin-top: 18px; }
.resume-empty { display: grid; justify-items: center; gap: 8px; margin-top: 18px; padding: 54px 20px; border: 1px dashed var(--line); border-radius: 12px; color: var(--muted); text-align: center; }
.resume-empty svg { font-size: 30px; color: #b6c0b3; }
.resume-empty strong { color: var(--ink); font-size: 15px; font-weight: 600; }
.resume-empty p { margin: 0; font-size: 13px; }
.resume-source-list, .resume-canvas, .profile-main-card, .profile-side-card { border: 1px solid var(--line); border-radius: 12px; background: #fff; }
.resume-source-list { align-self: start; overflow: hidden; }
.resume-source-item { display: grid; grid-template-columns: 33px minmax(0, 1fr) auto; align-items: center; gap: 9px; width: 100%; padding: 13px 13px; border: 0; border-bottom: 1px solid #edf0eb; background: transparent; color: var(--ink); cursor: pointer; text-align: left; }
.resume-source-item:last-child { border-bottom: 0; }
.resume-source-item:hover, .resume-source-item.active { background: #f2f6ee; }
.resume-source-item.active { box-shadow: inset 3px 0 0 var(--olive); }
.resume-source-item > span:nth-child(2) { display: grid; min-width: 0; gap: 4px; }
.resume-source-item strong, .resume-source-item small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.resume-source-item strong { font-size: 12px; }
.resume-source-item small { color: #8a958e; font-size: 11px; }
/* 删除键平时压暗，悬停才变红 —— 列表里常年挂一排红叉太吵 */
.resume-remove {
  display: inline-flex;
  width: 24px;
  height: 24px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #b6c0b9;
  cursor: pointer;
  font-size: 14px;
}
.resume-remove:hover {
  background: #fdeaea;
  color: #d4380d;
}
.file-icon { display: grid; width: 31px; height: 31px; place-items: center; border-radius: 7px; background: #edf4e7; color: var(--olive-deep); font-size: 15px; }
.resume-source-item > svg { color: #adb6ae; font-size: 14px; }
.resume-canvas { min-height: 394px; padding: 18px; }
.canvas-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 18px; padding-bottom: 15px; border-bottom: 1px solid #edf0eb; }
.canvas-toolbar > div:first-child { display: grid; min-width: 0; gap: 4px; }
.canvas-toolbar strong { overflow: hidden; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.canvas-toolbar small { color: #8a958e; font-size: 11px; }
.canvas-toolbar-actions { display: flex; align-items: center; gap: 8px; }
.icon-action { display: inline-grid; width: 32px; height: 32px; place-items: center; border: 1px solid #dce4dc; border-radius: 7px; color: #69766e; font-size: 15px; }
.icon-action:hover { border-color: #abc29f; color: var(--olive-deep); }
.editor-empty, .editor-state { display: grid; place-items: center; padding: 58px 16px 30px; text-align: center; }
.editor-empty h3, .editor-state strong { margin: 14px 0 6px; font-family: Georgia, 'Noto Serif SC', serif; font-size: 21px; font-weight: 600; }
.editor-empty p, .editor-state p { margin: 0; color: #8a958e; font-size: 13px; }
.empty-orbit { display: grid; width: 52px; height: 52px; place-items: center; border-radius: 50%; background: #edf4e7; color: var(--olive); font-size: 23px; }
.editor-hints { display: flex; gap: 15px; margin-top: 20px; color: #8b968e; font-size: 11px; }
.editor-hints span { display: inline-flex; align-items: center; gap: 5px; }
.editor-state > svg { color: var(--olive); font-size: 26px; }
.editor-state.error > svg { color: var(--coral); }
.editor-state.error .secondary-action { margin-top: 18px; }
.draft-preview { display: grid; gap: 14px; padding: 22px 5px 5px; }
.draft-label { display: flex; align-items: center; gap: 8px; color: var(--olive-deep); font-size: 11px; }
.draft-label span { padding: 4px 7px; border-radius: 4px; background: #ecf4e7; }
.draft-label small { color: #9aa59c; }
.draft-preview h3 { margin: 0; font-family: Georgia, 'Noto Serif SC', serif; font-size: 25px; }
.draft-preview > p { margin: 0; color: #6e7c73; font-size: 13px; line-height: 1.7; }
.version-list { display: grid; gap: 6px; padding: 14px 12px; border-top: 1px solid #edf0eb; }
.version-list-title { color: #86948a; font-size: 11px; font-weight: 700; }
.version-list button { display: grid; gap: 3px; padding: 8px; border: 1px solid transparent; border-radius: 6px; background: transparent; color: #52645a; cursor: pointer; font: inherit; font-size: 11px; text-align: left; }
.version-list button.active, .version-list button:hover { border-color: #d6e4cf; background: #f3f8ef; color: var(--olive-deep); }
.version-list button small { color: #9aa59d; font-size: 10px; }
.structured-editor { display: grid; gap: 16px; padding: 20px 5px 5px; }
.editor-badge { display: inline-flex; align-items: center; gap: 7px; width: max-content; padding: 6px 9px; border-radius: 5px; background: #f0f6e9; color: var(--olive-deep); font-size: 11px; }
.structured-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.structured-grid label { display: grid; gap: 6px; color: #66766d; font-size: 12px; }
.structured-grid label.wide { grid-column: 1 / -1; }
.structured-grid input, .structured-grid textarea { width: 100%; box-sizing: border-box; padding: 9px 10px; border: 1px solid #dfe7dd; border-radius: 6px; outline: 0; background: #fff; color: var(--ink); font: inherit; font-size: 13px; resize: vertical; }
.structured-grid input:focus, .structured-grid textarea:focus { border-color: #9fbd8c; box-shadow: 0 0 0 2px #edf5e8; }
.job-filter-bar { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 10px; margin: 14px 0; padding: 12px; border: 1px solid #e2e9df; border-radius: 8px; background: #fff; }
.job-filter-bar label, .application-toolbar label { display: grid; gap: 5px; color: #718077; font-size: 11px; }
.job-filter-bar input, .job-filter-bar select, .application-toolbar select { width: 100%; box-sizing: border-box; min-height: 32px; padding: 6px 8px; border: 1px solid #dfe7dd; border-radius: 5px; background: #fff; color: var(--ink); font: inherit; font-size: 12px; }
.job-filter-bar input:focus, .job-filter-bar select:focus, .application-toolbar select:focus { border-color: #9fbd8c; outline: 0; }
.application-toolbar { display: flex; align-items: end; justify-content: space-between; gap: 12px; margin: 12px 0 16px; color: #8a968e; font-size: 12px; }
.application-toolbar label { width: 180px; }
.application-detail-panel { display: grid; gap: 14px; margin-top: 16px; padding: 18px; border: 1px solid #dfe8da; border-radius: 8px; background: #fbfdf9; }
.detail-panel-heading { display: flex; align-items: start; justify-content: space-between; gap: 12px; }
.detail-panel-heading h3 { margin: 0; color: #26372d; font-size: 17px; }
.application-detail-panel dl { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px 20px; margin: 0; }
.application-detail-panel dl div { display: grid; gap: 3px; }
.application-detail-panel dt { color: #8b978f; font-size: 11px; }
.application-detail-panel dd { margin: 0; color: #52635a; font-size: 12px; }
.timeline { display: grid; gap: 8px; padding-top: 12px; border-top: 1px solid #e8eee5; }
.timeline h4 { margin: 0; color: var(--olive-deep); font-size: 12px; }
.timeline-item { display: grid; gap: 3px; padding-left: 11px; border-left: 2px solid #c9ddbc; }
.timeline-item strong { color: #52635a; font-size: 12px; }
.timeline-item span, .timeline-item small { color: #8b978f; font-size: 11px; }
.structured-preview { display: grid; gap: 10px; padding: 20px; border: 1px solid #e2e9df; border-radius: 8px; background: #fff; }
.structured-preview .preview-head { display: flex; align-items: baseline; justify-content: space-between; color: #829087; font-size: 11px; }
.structured-preview h3 { margin: 0; color: #25352d; font-family: Georgia, 'Noto Serif SC', serif; font-size: 25px; }
.structured-preview > p { margin: 0; color: #7e8c84; font-size: 12px; }
.structured-preview section { display: grid; gap: 5px; padding-top: 9px; border-top: 1px solid #edf0eb; }
.structured-preview section h4 { margin: 0; color: var(--olive-deep); font-size: 12px; }
.structured-preview section p { margin: 0; color: #596b61; font-size: 12px; line-height: 1.7; white-space: pre-wrap; }
.ai-result-card { display: grid; gap: 12px; margin-top: 16px; padding: 18px; border: 1px solid #dfe8da; border-radius: 9px; background: #fbfdf9; }
.result-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.result-heading strong { font-size: 15px; }
.result-heading b { color: var(--olive-deep); font-size: 24px; }
.result-heading b small { color: #8d9a90; font-size: 11px; font-weight: 400; }
.ai-result-card p { margin: 0; color: #66756b; font-size: 12px; line-height: 1.65; }
.ai-result-card ul { display: grid; gap: 8px; margin: 0; padding-left: 17px; color: #53645a; font-size: 12px; }
.ai-result-card li small { display: block; margin-top: 2px; color: #93a097; font-size: 11px; }
.optimize-item { display: grid; gap: 6px; padding: 10px; border: 1px solid #e2e9df; border-radius: 6px; background: #fff; }
.optimize-item label { color: #54655b; font-size: 12px; }
.optimize-item p { display: grid; gap: 4px; }
.optimize-item del { color: #a2ada5; }
.optimize-item p span { color: var(--olive-deep); }
.optimize-item > small { color: #8d9a90; font-size: 11px; }
@media (max-width: 560px) { .structured-grid { grid-template-columns: 1fr; } .structured-grid label.wide { grid-column: auto; } }
.draft-tags { display: flex; flex-wrap: wrap; gap: 7px; }
.draft-tags span { padding: 5px 8px; border-radius: 4px; background: #f0f3ee; color: #68766c; font-size: 11px; }
.draft-columns { display: grid; grid-template-columns: 1fr 1fr; gap: 22px; padding: 15px 0; border-top: 1px solid #edf0eb; border-bottom: 1px solid #edf0eb; }
.draft-columns h4 { margin-bottom: 8px; color: #66746b; font-size: 12px; }
.draft-columns p { margin: 0 0 10px; color: #445148; font-size: 12px; line-height: 1.6; }
.draft-columns small { color: #88958c; }
.section-intro-row { display: flex; align-items: flex-end; justify-content: space-between; gap: 25px; }
.job-search { display: flex; align-items: center; gap: 8px; width: min(340px, 100%); min-height: 39px; padding: 0 7px 0 13px; border: 1px solid #dbe4da; border-radius: 7px; background: #fff; }
.job-search > svg { color: #a0aaa2; }
.job-search input { min-width: 0; flex: 1; border: 0; outline: 0; color: var(--ink); font: inherit; font-size: 12px; }
.job-search button { min-height: 29px; padding: 0 9px; border: 0; border-radius: 5px; background: #eef3eb; color: var(--olive-deep); cursor: pointer; font: inherit; font-size: 11px; }
.preference-banner { display: flex; align-items: center; gap: 12px; margin-top: 27px; padding: 16px 18px; border: 1px solid #d8e6c5; border-radius: 12px; background: #eef5d3; }
.preference-icon { display: grid; width: 31px; height: 31px; place-items: center; border-radius: 50%; background: rgb(255 255 255 / 70%); color: var(--olive-deep); }
.preference-banner > div { display: grid; gap: 4px; flex: 1; }
.preference-banner strong { font-size: 14px; }
.preference-banner small { color: #708452; font-size: 11px; }
.preference-banner button { display: inline-flex; align-items: center; gap: 5px; border: 0; background: transparent; color: #647c42; cursor: pointer; font: inherit; font-size: 12px; }
.section-tabs, .record-tabs { display: flex; align-items: center; gap: 19px; margin-top: 22px; border-bottom: 1px solid var(--line); }
.section-tabs button, .record-tabs button { position: relative; padding: 0 1px 13px; border: 0; background: transparent; color: #8b958e; cursor: pointer; font: inherit; font-size: 12px; }
.section-tabs button.active, .record-tabs button.active { color: var(--olive-deep); font-weight: 700; }
.section-tabs button.active::after, .record-tabs button.active::after { position: absolute; right: 0; bottom: -1px; left: 0; height: 2px; background: var(--olive); content: ''; }
.section-tabs > span { margin-left: auto; padding-bottom: 13px; color: #9ca69e; font-size: 11px; }
.jobs-layout { display: grid; grid-template-columns: minmax(0, 1fr) 290px; gap: 19px; margin-top: 17px; }
.job-list { display: grid; gap: 11px; }
.job-card { display: grid; gap: 15px; padding: 18px; border: 1px solid #dfe7dc; border-radius: 12px; background: #fff; cursor: pointer; transition: border-color 160ms ease, transform 160ms ease, box-shadow 160ms ease; }
.job-card:hover { border-color: #abc49d; box-shadow: 0 9px 22px rgb(61 78 57 / 7%); transform: translateY(-1px); }
.job-card-main { display: flex; gap: 13px; }
.company-mark { display: grid; width: 39px; height: 39px; place-items: center; flex: 0 0 auto; border-radius: 9px; background: #edf4e7; color: var(--olive-deep); font-size: 17px; font-weight: 700; }
.job-card-copy { min-width: 0; flex: 1; }
.job-card-title { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.job-card-title h3 { margin: 0; font-size: 15px; }
.job-card-title strong { color: #27342d; font-size: 15px; white-space: nowrap; }
.job-card-copy p { margin: 8px 0 7px; color: #6f7d73; font-size: 12px; }
.job-card-copy small { color: #9aa59c; font-size: 11px; }
.job-card-actions { display: flex; align-items: center; justify-content: space-between; padding-top: 12px; border-top: 1px solid #edf0eb; }
.match-label { display: inline-flex; align-items: center; gap: 5px; color: #83a36b; font-size: 11px; }
.match-label svg { font-size: 13px; }
.recommend-aside, .job-detail-side { align-self: start; }
.aside-card { display: grid; gap: 14px; padding: 19px; border: 1px solid #e1e7dd; border-radius: 12px; background: #fff; }
.aside-card h3 { margin: 9px 0 0; font-family: Georgia, 'Noto Serif SC', serif; font-size: 21px; line-height: 1.35; }
.aside-card > p { margin: 0; color: #7d8981; font-size: 12px; line-height: 1.7; }
.aside-cta { display: inline-flex; align-items: center; justify-content: space-between; min-height: 36px; padding: 0 11px; border: 1px solid #dbe6d7; border-radius: 7px; background: #f1f6ee; color: var(--olive-deep); cursor: pointer; font: inherit; font-size: 12px; }
.aside-cta:hover { background: #e7f0e2; }
.list-state, .empty-record, .empty-block { display: grid; place-items: center; gap: 8px; padding: 62px 20px; border: 1px dashed #d8e1d6; border-radius: 12px; background: rgb(255 255 255 / 65%); color: #89958d; text-align: center; }
.list-state > svg, .empty-record > span > svg, .empty-block > svg { color: #a2b399; font-size: 30px; }
.list-state strong, .empty-record h3, .empty-block strong { margin: 0; color: #5d6c62; font-size: 14px; }
.list-state p, .empty-record p, .empty-block p { margin: 0; font-size: 12px; }
.empty-block { min-height: 160px; border: 0; border-radius: 0; background: transparent; }
.empty-block.compact { padding: 25px 12px; }
.list-state.compact { min-height: 70px; padding: 12px; border: 0; background: transparent; }
.job-detail-workspace { padding-top: 27px; }
.back-inline { display: inline-flex; align-items: center; gap: 6px; padding: 0; color: #7d8981; font-size: 12px; }
.job-detail-grid { display: grid; grid-template-columns: minmax(0, 1fr) 290px; gap: 20px; margin-top: 23px; }
.job-detail-card { padding: 27px; border: 1px solid var(--line); border-radius: 13px; background: #fff; }
.job-detail-top { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; padding-bottom: 22px; border-bottom: 1px solid #edf0eb; }
.job-detail-top h2 { font-size: 30px; }
.job-detail-top p:last-child { margin: 9px 0 0; color: #758078; font-size: 13px; }
.job-detail-salary { color: var(--olive-deep); font-size: 20px; }
.job-facts { display: flex; flex-wrap: wrap; gap: 11px 25px; padding: 17px 0 5px; color: #6f7d73; font-size: 12px; }
.job-facts span { display: inline-flex; align-items: center; gap: 5px; }
.job-facts svg { color: #9aab92; }
.job-detail-copy { min-height: 220px; padding-top: 22px; }
.job-detail-copy h3 { margin-bottom: 12px; font-size: 15px; }
.job-detail-copy p { color: #5f6d64; font-size: 13px; line-height: 1.9; white-space: pre-line; }
.job-detail-actions { display: flex; gap: 9px; padding-top: 17px; border-top: 1px solid #edf0eb; }
.profile-header-card { display: flex; align-items: center; gap: 17px; padding: 23px; border: 1px solid var(--line); border-radius: 13px; background: #fff; }
.profile-avatar-large { width: 62px; height: 62px; font-size: 23px; }
.profile-header-card > div:nth-child(2) { flex: 1; }
.profile-header-card h2 { font-size: 26px; }
.profile-header-card p:last-child { margin: 7px 0 0; color: #7d8981; font-size: 12px; }
.profile-progress { display: grid; gap: 6px; width: 180px; }
.profile-progress strong { color: var(--olive-deep); font-size: 20px; }
.profile-progress small { color: #8c988f; font-size: 11px; }
.profile-content-grid { display: grid; grid-template-columns: minmax(0, 1fr) 290px; gap: 18px; margin-top: 18px; }
.profile-main-card, .profile-side-card { padding: 22px; }
.profile-card-title { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding-bottom: 17px; border-bottom: 1px solid #edf0eb; }
.profile-card-title h3 { margin: 0; font-size: 16px; }
.profile-card-title span { color: #99a49b; font-size: 11px; }
.profile-fields { display: grid; grid-template-columns: 1fr 1fr; gap: 20px 30px; padding: 20px 0; border-bottom: 1px solid #edf0eb; }
.profile-fields > div { display: grid; gap: 5px; }
.profile-fields small { color: #99a49b; font-size: 11px; }
.profile-fields strong { color: #45534a; font-size: 13px; font-weight: 500; }
.profile-section { padding-top: 19px; }
.profile-section h4 { margin-bottom: 10px; font-size: 13px; }
.profile-section p { margin: 0; color: #748078; font-size: 12px; line-height: 1.8; }
.profile-side-card { align-self: start; }
.profile-side-card h3 { margin: 0 0 18px; font-size: 15px; }
.check-list { display: grid; gap: 14px; }
.check-list span { display: inline-flex; align-items: center; gap: 8px; color: #9aa49d; font-size: 12px; }
.check-list span.done { color: var(--olive-deep); }
.check-list svg { font-size: 15px; }
.profile-side-card .aside-cta { width: 100%; margin-top: 22px; }
.record-tabs { gap: 22px; margin-top: 30px; }
.record-list { display: grid; gap: 10px; margin-top: 17px; }
.record-card { display: grid; grid-template-columns: minmax(0, 1fr) auto 32px; align-items: center; gap: 15px; padding: 16px 17px; border: 1px solid var(--line); border-radius: 11px; background: #fff; }
.record-main { display: flex; align-items: center; gap: 12px; min-width: 0; }
.record-icon { display: grid; width: 34px; height: 34px; place-items: center; border-radius: 8px; background: #edf4e7; color: var(--olive-deep); }
.record-main > div { min-width: 0; }
.record-main h3 { overflow: hidden; margin: 0 0 5px; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.record-main p { margin: 0; color: #8b958e; font-size: 11px; }
.record-status { color: var(--olive-deep); font-size: 12px; }
.empty-record { min-height: 280px; margin-top: 17px; }
.empty-record h3 { font-family: Georgia, 'Noto Serif SC', serif; font-size: 20px; }
.empty-record.compact { min-height: 250px; margin: 0; border: 0; background: transparent; }
.settings-layout { display: grid; grid-template-columns: 190px minmax(0, 1fr); gap: 27px; margin-top: 30px; }
.settings-nav { display: grid; align-content: start; gap: 4px; }
.settings-nav button { min-height: 38px; padding: 0 14px; border: 0; border-radius: 6px; background: transparent; color: #77847b; cursor: pointer; font: inherit; font-size: 13px; text-align: left; }
.settings-nav button.active, .settings-nav button:hover { background: #eef2eb; color: #465943; }
.settings-content { display: grid; gap: 17px; }
.settings-section { display: grid; gap: 10px; padding: 19px; border-top: 1px solid #e4e9e2; }
.settings-section:first-child { padding-top: 0; border-top: 0; }
.settings-section h3 { margin: 0; font-size: 15px; }
.settings-section p { margin: 0; color: #8a958e; font-size: 12px; line-height: 1.6; }
.settings-section textarea { min-height: 86px; resize: vertical; border: 1px solid #dce5dc; border-radius: 7px; padding: 10px; outline: 0; color: var(--ink); font: inherit; font-size: 12px; line-height: 1.7; }
.settings-row { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.switch { position: relative; width: 42px; height: 24px; flex: 0 0 auto; padding: 0; border: 0; border-radius: 13px; background: #cbd2cb; cursor: pointer; transition: background 160ms ease; }
.switch i { position: absolute; top: 3px; left: 3px; width: 18px; height: 18px; border-radius: 50%; background: #fff; box-shadow: 0 1px 3px rgb(0 0 0 / 14%); transition: transform 160ms ease; }
.switch.on { background: #7aa56c; }
.switch.on i { transform: translateX(18px); }
.mode-options { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.mode-options button { display: grid; gap: 6px; padding: 13px; border: 1px solid #dce5dc; border-radius: 8px; background: #fff; color: #536158; cursor: pointer; font: inherit; text-align: left; }
.mode-options button.active { border-color: #a8c39d; background: #f0f6ed; box-shadow: inset 0 0 0 1px #a8c39d; }
.mode-options svg { color: var(--olive-deep); font-size: 17px; }
.mode-options strong { font-size: 12px; }
.mode-options small { color: #8c978f; font-size: 10px; line-height: 1.5; }
.ai-page-loading { display: flex; min-height: 60vh; align-items: center; justify-content: center; gap: 10px; color: #87958b; font-size: 13px; }
.visually-hidden { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
.spin { animation: ai-resume-spin 900ms linear infinite; }
@keyframes ai-resume-spin { to { transform: rotate(360deg); } }
@media (max-width: 1080px) {
  .ai-resume-sidebar { width: 210px; }
  .ai-resume-main { width: calc(100% - 210px); margin-left: 210px; }
  .workspace { width: min(100% - 48px, 900px); }
  .jobs-layout, .job-detail-grid, .profile-content-grid { grid-template-columns: minmax(0, 1fr); }
  .recommend-aside, .job-detail-side { display: none; }
}
@media (max-width: 760px) {
  .ai-resume-sidebar { position: static; width: 70px; min-height: 100vh; }
  .ai-brand { height: 72px; justify-content: center; padding: 0; }
  .brand-copy, .ai-sidebar-collapse, .ai-nav-item span:not(.nav-unread), .ai-user-copy, .ai-user-card > svg, .ai-back-link { display: none; }
  .ai-brand-mark { gap: 0; }
  .ai-sidebar-nav { padding: 28px 9px; }
  .ai-nav-item { justify-content: center; padding: 0; }
  .ai-nav-item.active::before { top: 12px; bottom: 12px; }
  .ai-sidebar-footer { padding: 14px 9px; }
  .ai-user-card { justify-content: center; }
  .ai-resume-main { width: calc(100% - 70px); margin-left: 0; }
  .workspace { width: calc(100% - 32px); padding: 29px 0 50px; }
  .resume-hero, .section-intro-row, .profile-header-card { align-items: flex-start; flex-direction: column; }
  .home-overview-grid, .service-grid, .mode-options { grid-template-columns: 1fr; }
  .settings-layout { grid-template-columns: 1fr; }
  .resume-card-grid { grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); }
  .resume-source-item { min-width: 210px; border-right: 1px solid #edf0eb; border-bottom: 0; }
  .canvas-toolbar { align-items: flex-start; flex-direction: column; }
  .profile-progress { width: 100%; }
  .profile-fields, .draft-columns { grid-template-columns: 1fr; }
  .conversation-list { max-height: 260px; overflow: auto; border-right: 0; border-bottom: 1px solid #edf0eb; }
  .job-card-title { align-items: flex-start; flex-direction: column; gap: 5px; }
}
@media (prefers-reduced-motion: reduce) {
  .spin, .job-card, .service-card { animation: none; transition: none; }
}

/*
 * 参考站视觉层。
 *
 * 下面的值不是看截图估出来的，是从 https://www.deephire.cn（职决）生产站点里读到的
 * CSS 自定义属性：--base-font-family / --text-base / --color-text-primary /
 * --color-icon-primary / --color-border-primary / --color-bg-selected /
 * --color-bg-primary / --color-border-base / --input-border-radius / --popup-radius。
 *
 * 上一版这一层有两处取法不对，一并纠正：
 *   1) 标题用了藏青 —— 参考站的正文和标题是同一个深色，没有第二套深色系；
 *   2) 主按钮用绿色 —— 参考站的主操作是纯黑（提交按钮 #000、首页大 CTA #000），
 *      绿色 #78b33e 只作品牌点缀：图标、选中态、焦点环。
 */
.ai-resume-app {
  --ink: #292929;
  --ink-strong: #141414;
  --muted: #6b6b6b;
  --muted-soft: #949494;

  /* 参考站 --color-border-base 只有 5% 黑；细线用 5%，需要看得见的边界用 8% */
  --line: rgb(0 0 0 / 8%);
  --line-soft: rgb(0 0 0 / 5%);

  /*
   * 描边跟着侧栏色走。原来是参考站的灰绿 #E8ECE5 —— 那是配它暖白底的，
   * 压在这片蓝上会发脏，所以按侧栏色重新调了三档：
   * 常规描边 / 悬停描边 / 悬停底色。
   */
  --line-blue: color-mix(in srgb, #d0e4f2 58%, #fff);
  --line-blue-strong: color-mix(in srgb, #d0e4f2 88%, #fff);
  --hover-blue: color-mix(in srgb, #d0e4f2 32%, #fff);

  /*
   * 底色按用户最新指定：左侧导航栏用整色，右侧内容区用它的 20% 淡化。
   * 这里写成 color-mix 而不是写死 #F6FAFC，是为了把「20%」这层关系留在代码里，
   * 以后换色只要改 #d0e4f2 一处。
   */
  --sidebar-bg: #d0e4f2;
  --canvas: color-mix(in srgb, #d0e4f2 20%, #fff);
  --paper: #fff;

  --olive: #78b33e;
  --olive-deep: #59822f;
  --olive-wash: #dbe6ce;
  --sage-1: #f5f5f4;
  --sage-2: #e8ece5;
  --sage-3: #e1eadb;

  /*
   * 推荐页的柠檬绿。取自参考站截图的像素众数，不是估色：
   * 横幅底色 #DFEC8C、压在上面的字 #2F3A12、右上角提示 #55602C。
   * --olive-heading 是那一栏小标题的绿，也来自同一张图。
   */
  --ink-soft: #5e5e5e;
  /*
   * 薪资用红：沿用门户里那个例外色，变量名和取值都跟职位页 / 公司页一致
   * （portal/recruit/index.vue、detail.vue、company.vue 都是 #ff0000），
   * 不在这里另造一个红。
   */
  --job-salary: #ff0000;
  --lime: #dfec8c;
  --lime-ink: #2f3a12;
  --lime-muted: #55602c;
  --olive-heading: #263312;

  --action: #000;
  --action-hover: #262626;

  --coral: #d86219;
  --blue: #1c64f2;

  --radius-input: 8px;
  --radius-card: 12px;
  --radius-panel: 16px;
  --radius-pill: 69px;

  font-family: 'PingFang SC', 'Microsoft YaHei', 'Heiti SC', SimSun, sans-serif;
  font-size: 15px;
  color: var(--ink);
  letter-spacing: 0;
}
.ai-resume-app button,
.ai-resume-app input,
.ai-resume-app textarea {
  font-family: inherit;
  letter-spacing: 0;
}
/*
 * ── 外壳：侧栏 ──
 * 左侧导航栏用整色 #D0E4F2，右侧内容区用它的 20% 淡化，两者分工是整个界面的底色骨架。
 * 参考站原本是「暖白画布 + 暖灰侧栏」。选中态沿用参考站的白色半透明，
 * 但他们那条左侧绿竖条没有保留 —— 参考站自身并没有。
 */
.ai-resume-sidebar {
  width: 260px;
  border-right: 0;
  background: var(--sidebar-bg);
}
.ai-brand {
  height: 72px;
  border-bottom: 0;
}
/* 参考站品牌行 72px 之下紧接第一项（首项 y=72），左右留白 7px */
.ai-sidebar-nav {
  gap: 2px;
  padding: 0 7px 18px;
}
.ai-nav-item {
  min-height: 52px;
  padding: 0 11px;
  border-radius: 8px;
  color: var(--ink);
  font-size: 16px;
  line-height: 24px;
}
.ai-nav-item:hover {
  background: rgb(255 255 255 / 45%);
  color: var(--ink);
}
.ai-nav-item.active {
  background: rgb(255 255 255 / 60%);
  color: var(--ink);
  box-shadow: none;
}
.ai-nav-item.active::before {
  display: none;
}
.ai-nav-item > svg {
  color: var(--muted);
}
.ai-nav-item:hover > svg,
.ai-nav-item.active > svg {
  color: var(--ink);
}
/*
 * 参考站侧栏底部的用户头像是 40px 实心黑圆（他们用 !bg-primary-6）。
 * 这里带 .ai-resume-app 提高一级权重：文件更靠后还有一条
 * `.profile-avatar-large, .ai-avatar, .conversation-avatar { background: #e4f1e4 }`
 * 是同权重的，不带这一级会被它盖回去。
 */
.ai-resume-app .ai-avatar {
  width: 40px;
  height: 40px;
  background: #141414;
  color: #fff;
  font-size: 15px;
}
.ai-resume-main {
  width: calc(100% - 260px);
  margin-left: 260px;
  background: transparent;
}
.workspace {
  padding-top: 42px;
}
.eyebrow {
  color: var(--muted-soft);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: .14em;
}
.workspace h2 {
  font-family: inherit;
  color: #0a1b33;
  font-size: 32px;
  font-weight: 650;
  letter-spacing: 0;
}
.workspace h3,
.workspace h4 {
  color: #1a1a1a;
}
.section-intro-row > div > p:last-child,
.resume-hero p:last-child {
  color: var(--muted);
  font-size: 13px;
  line-height: 1.7;
}
.overview-card,
.service-card,
.resume-source-list,
.resume-canvas,
.job-card,
.aside-card,
.job-detail-card,
.profile-header-card,
.profile-main-card,
.profile-side-card,
.record-card {
  border-color: var(--line-blue);
  border-radius: 16px;
  box-shadow: none;
}
.aside-card h3,
.editor-empty h3,
.editor-state strong,
.empty-record h3,
.draft-preview h3 {
  font-family: inherit;
  color: var(--ink);
  font-weight: 650;
}
.aside-card > p,
.editor-empty p,
.editor-state p {
  color: var(--muted);
  font-size: 13px;
}

/*
 * ── 求职助理首页 ──
 * 参考站这一页的结构是：整片留白 + 输入框贴底居中（740px 宽、白底、圆角 20px、
 * 无投影）。三张状态卡放进那片留白里居中，用同一条 740px 栏宽与输入框对齐。
 */
.home-workspace {
  display: flex;
  width: 100%;
  /*
   * 用 height 而不是 min-height：底部输入框要钉在视口里，
   * 对话变长时让中间的 .home-stage 自己滚动，而不是把输入框顶到屏幕外。
   */
  height: 100vh;
  box-sizing: border-box;
  flex-direction: column;
  align-items: center;
  overflow: hidden;
  padding: 0 24px;
  /* 历史记录按钮绝对定位落在右上角，锚点在这里 */
  position: relative;
}
.home-stage {
  display: flex;
  width: 100%;
  min-height: 0;
  flex: 1;
  align-items: center;
  justify-content: center;
  overflow-y: auto;
  padding: 32px 0;
}
/* 有对话时从顶部排，否则一边聊一边往上跳 */
.home-stage.is-chatting {
  align-items: flex-start;
}
.chat-panel {
  display: grid;
  width: min(740px, 100%);
  gap: 10px;
}
.chat-thread {
  display: grid;
  gap: 14px;
}
.chat-bubble {
  display: flex;
  max-width: 88%;
  gap: 10px;
}
.chat-bubble.is-user {
  margin-left: auto;
  flex-direction: row-reverse;
}
.chat-bubble p {
  margin: 0;
  padding: 12px 15px;
  border-radius: 14px;
  background: var(--paper);
  color: var(--ink);
  font-size: 15px;
  line-height: 1.75;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
/* 用户气泡沿用页面上那唯一一处纯黑，和发送键同一个颜色 */
.chat-bubble.is-user p {
  background: #141414;
  color: #fff;
}
.chat-mark {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 50%;
  background: #e8ece5;
  color: #426122;
  font-size: 15px;
}
.chat-bubble.is-user .chat-mark {
  background: var(--line-blue-strong);
  color: var(--ink);
}
.chat-typing {
  color: var(--muted-soft);
}
.chat-error {
  margin: 0;
  color: #b42318;
  font-size: 13px;
  text-align: center;
}
.chat-reset {
  width: max-content;
  margin: 0 auto;
  padding: 6px 14px;
  border: 1px solid var(--line-blue);
  border-radius: 9999px;
  background: var(--paper);
  color: var(--muted);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  transition: border-color 160ms ease, color 160ms ease;
}
.chat-reset:hover {
  border-color: var(--line-blue-strong);
  color: var(--ink);
}
.home-overview-grid {
  display: grid;
  width: min(740px, 100%);
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin: 0;
}
.overview-card {
  display: grid;
  min-height: 156px;
  align-content: start;
  gap: 10px;
  padding: 18px;
  border: 1px solid var(--line-blue);
  background: var(--paper);
  color: var(--ink);
  cursor: pointer;
  font: inherit;
  text-align: left;
  transition: border-color 160ms ease, transform 160ms ease;
}
.overview-card:hover {
  border-color: var(--line-blue-strong);
  transform: translateY(-1px);
}
.overview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.overview-icon {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 9px;
  background: #e8ece5;
  color: #426122;
  font-size: 17px;
}
.overview-icon.warm {
  background: #fdeee4;
  color: var(--coral);
}
.overview-icon.blue {
  background: #e9f0fe;
  color: var(--blue);
}
/* 数字用墨色不用绿：参考站里绿色是点缀，承担信息的是黑白灰 */
.overview-value {
  color: var(--ink);
  font-size: 26px;
  font-weight: 600;
  line-height: 1;
  letter-spacing: -0.02em;
}
.overview-value small {
  margin-left: 1px;
  color: var(--muted-soft);
  font-size: 14px;
  font-weight: 500;
}
.overview-label {
  color: var(--ink);
  font-size: 15px;
  font-weight: 600;
}
.overview-note {
  color: var(--muted);
  font-size: 13px;
  line-height: 1.6;
}
.progress-track {
  background: #e8ece5;
}
.progress-track i {
  background: var(--olive);
}
.home-composer {
  width: min(740px, 100%);
  padding-bottom: 14px;
}
/* 圆角 20px 与参考站 --sender-radius 一致 */
.composer-card {
  margin: 0;
  padding: 10px 0 0;
  border-radius: 20px;
  background: var(--paper);
}
.composer-editor {
  padding: 6px 20px 12px;
}
/* 70 + 上6 + 下12 = 88，与参考站编辑区等高，整张卡片因此也是 158px */
.composer-editor textarea {
  display: block;
  width: 100%;
  min-height: 70px;
  box-sizing: border-box;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--ink);
  font: inherit;
  font-size: 16px;
  line-height: 26px;
  resize: none;
}
/* 参考站的占位文字是 20% 黑 */
.composer-editor textarea::placeholder {
  color: rgb(0 0 0 / 20%);
}
.composer-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 20px 20px;
}
.composer-icon,
.composer-send {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  place-items: center;
  border: 0;
  cursor: pointer;
  font-size: 18px;
}
.composer-icon {
  border: 1px solid var(--line-blue);
  border-radius: 8px;
  background: var(--paper);
  color: #4c596a;
  transition: background 160ms ease;
}
.composer-icon:hover {
  background: var(--hover-blue);
}
.composer-chip {
  display: inline-flex;
  height: 36px;
  align-items: center;
  gap: 6px;
  padding: 0 12px;
  border: 1px solid var(--line-blue);
  border-radius: 8px;
  background: var(--paper);
  color: var(--ink);
  cursor: pointer;
  font: inherit;
  font-size: 14px;
  line-height: 20px;
  white-space: nowrap;
  transition: border-color 160ms ease, background 160ms ease, color 160ms ease;
}
.composer-chip > svg {
  color: var(--olive);
  font-size: 15px;
}
.composer-chip:hover {
  border-color: var(--line-blue-strong);
  background: var(--hover-blue);
  color: var(--olive-deep);
}
/* 页面上唯一一处纯黑：参考站的发送键就是 #141414 的实心圆 */
.composer-send {
  margin-left: auto;
  border-radius: 9999px;
  background: #141414;
  color: #fff;
  transition: background 160ms ease, transform 160ms ease;
}
.composer-send:hover:not(:disabled) {
  background: #000;
  transform: scale(1.05);
}
/* 禁用态：淡蓝底上不能再用白色箭头，那样等于看不见（约 1.1:1）；换成蓝灰，约 3.4:1 */
.composer-send:disabled {
  background: var(--line-blue);
  color: #6b8299;
  cursor: not-allowed;
}
/*
 * 参考站这里是 #B8B8B8 —— 在它的暖白底上只有 1.78:1，本来就偏低；
 * 换到这片浅蓝内容区上更只剩约 1.5:1，几乎看不见。取 #5C6370 补到 5.7:1。
 */
/* ── 对话附件 ── */
.composer-attach {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 2px 0 8px;
}
.attach-chip {
  display: inline-flex;
  max-width: 100%;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border-radius: 6px;
  background: var(--hover-blue);
  color: var(--ink);
  font-size: 12px;
}
.attach-chip.is-loading {
  color: var(--muted);
}
.attach-chip > button {
  display: inline-flex;
  width: 15px;
  height: 15px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
  font-size: 11px;
}
.attach-chip > button:hover {
  background: rgb(255 255 255 / 75%);
  color: var(--ink);
}

/* 「+」的附件菜单。输入框贴在底部，所以菜单往**上**弹 */
.composer-attach-menu {
  position: relative;
}
.attach-menu {
  position: absolute;
  z-index: 20;
  bottom: calc(100% + 8px);
  left: 0;
  display: grid;
  width: 248px;
  padding: 6px;
  border: 1px solid var(--line-blue);
  border-radius: var(--radius-card);
  background: var(--paper);
  box-shadow: 0 -10px 28px rgb(10 27 51 / 12%);
}
.attach-menu button {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--ink);
  cursor: pointer;
  font: inherit;
  text-align: left;
}
.attach-menu button:hover {
  background: var(--hover-blue);
}
.attach-menu button > svg {
  flex: none;
  color: var(--muted);
  font-size: 16px;
}
.attach-menu button > span {
  display: grid;
  gap: 1px;
  min-width: 0;
}
.attach-menu strong {
  font-size: 13px;
  font-weight: 600;
}
.attach-menu small {
  color: var(--muted);
  font-size: 11px;
}

/* 气泡里的附件标签。只出现在用户那侧（黑底），所以用半透明白 */
.bubble-attach {
  display: inline-flex;
  max-width: 100%;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  border: 1px solid var(--line-blue);
  border-radius: 8px;
  background: var(--paper);
  color: var(--ink);
  font-size: 12px;
  overflow-wrap: anywhere;
}

.bubble-attach.is-image {
  padding: 0;
  border-radius: 10px;
  background: transparent;
}
.bubble-attach.is-image img {
  display: block;
  /* 气泡本身是窄条，缩略图给个上限，别把气泡撑成整张图 */
  max-width: 220px;
  max-height: 220px;
  border-radius: 10px;
  object-fit: cover;
}

/* 附件标签现在是按钮（图片可放大、文档可看正文），要把浏览器默认样式压掉 */
button.bubble-attach {
  border: 0;
  cursor: pointer;
  font: inherit;
  font-size: 12px;
}
button.bubble-attach:disabled {
  cursor: default;
}

/* 图片放大：铺满视口，点背景关 */
.preview-scrim {
  position: fixed;
  z-index: 80;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  padding: 48px;
  background: rgb(8 18 32 / 82%);
}
.preview-scrim img {
  max-width: 100%;
  max-height: 100%;
  border-radius: 10px;
  box-shadow: 0 24px 60px rgb(0 0 0 / 45%);
}
.preview-close {
  position: absolute;
  top: 20px;
  right: 24px;
  display: inline-flex;
  width: 36px;
  height: 36px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 50%;
  background: rgb(255 255 255 / 16%);
  color: #fff;
  cursor: pointer;
  font-size: 18px;
}
.preview-close:hover {
  background: rgb(255 255 255 / 28%);
}

/* 文档附件卡片。样式对齐沟通页面的文件卡：图标块 + 文件名 + 副标题 */
.bubble-file {
  display: flex;
  max-width: 100%;
  align-items: center;
  gap: 10px;
  padding: 8px 12px 8px 10px;
  /*
   * 白底深字。这张卡是气泡的**兄弟节点**，不在那个深色 <p> 里 ——
   * 它实际落在浅色画布上，按深色气泡去配（白字 + 半透明白底）会白得看不见。
   */
  border: 1px solid var(--line-blue);
  border-radius: 10px;
  background: var(--paper);
  cursor: pointer;
  font: inherit;
  text-align: left;
}
.bubble-file:hover {
  border-color: var(--line-blue-strong);
  background: var(--hover-blue);
}
.bubble-file-icon {
  display: inline-flex;
  width: 32px;
  height: 32px;
  align-items: center;
  justify-content: center;
  flex: none;
  border-radius: 8px;
  background: var(--hover-blue);
  color: #2f6fb3;
  font-size: 16px;
}
.bubble-file-copy {
  display: grid;
  gap: 1px;
  min-width: 0;
}
.bubble-file-copy strong {
  overflow: hidden;
  color: var(--ink-strong);
  font-size: 13px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bubble-file-copy small {
  color: var(--muted);
  font-size: 11px;
}

.composer-note {
  margin: 12px 0 0;
  color: #5c6370;
  font-size: 13px;
  line-height: 1.5;
  text-align: center;
}
@media (max-width: 760px) {
  /* 这一层的 260px 写在基础层之后，会盖掉基础层 <=760px 的图标栏折叠，得补回来 */
  .ai-resume-sidebar {
    width: 70px;
  }
  .ai-resume-main {
    width: calc(100% - 70px);
    margin-left: 0;
  }
  .home-workspace {
    min-height: 100vh;
    padding: 0 16px;
  }
  .home-stage {
    padding: 20px 0;
  }
  .home-overview-grid {
    grid-template-columns: 1fr;
  }
  .composer-bar {
    flex-wrap: wrap;
  }
}
@media (prefers-reduced-motion: reduce) {
  .overview-card,
  .composer-icon,
  .composer-chip,
  .composer-send {
    transition: none;
  }
}
.service-card strong,
.job-card-title h3,
.profile-card-title h3,
.settings-section h3 {
  color: var(--ink);
  font-size: 14px;
  font-weight: 650;
}
.service-card small,
.job-card-copy p,
.job-card-copy small,
.profile-fields small,
.profile-section p,
.settings-section p {
  color: var(--muted);
}
.file-icon,
.record-icon {
  background: #e8ece5;
  color: #426122;
}
/* 参考站的主操作是纯黑，不是绿色 —— 这里跟 token 注释里说的一致 */
.primary-action {
  border-color: var(--action);
  background: var(--action);
  font-size: 13px;
}
.primary-action:hover {
  background: var(--action-hover);
}
.secondary-action {
  border-color: #d9d9d9;
  color: #4c596a;
  font-size: 13px;
}
.secondary-action:hover {
  border-color: #78b33e;
  color: #59822f;
}
.dark-action {
  border-color: #1a1a1a;
  background: #1a1a1a;
  font-size: 13px;
}
.service-card > span {
  background: #e4f1e4;
  color: #59822f;
}
.service-card.service-optimize > span {
  background: #eff4fe;
  color: #1c64f2;
}
.service-card.service-generate > span {
  background: #fef4f2;
  color: #d86219;
}
.service-card small {
  font-size: 12px;
}
.resume-section-head h3 {
  font-family: inherit;
  color: #141414;
  font-size: 20px;
  font-weight: 500;
}
.text-action,
.back-inline {
  color: #59822f;
  font-size: 13px;
}
.resume-source-item strong,
.canvas-toolbar strong {
  color: #1a1a1a;
  font-size: 13px;
}
.resume-source-item small,
.canvas-toolbar small {
  color: #949494;
}
.resume-source-item:hover,
.resume-source-item.active {
  background: #f2f5ee;
}
.resume-source-item.active {
  box-shadow: inset 3px 0 0 #78b33e;
}
.editor-empty h3,
.editor-state strong {
  font-size: 20px;
}
.section-tabs button,
.record-tabs button,
.settings-nav button {
  color: #7b8088;
  font-size: 13px;
}
.section-tabs button.active,
.record-tabs button.active {
  color: #59822f;
}
.section-tabs button.active::after,
.record-tabs button.active::after {
  background: #78b33e;
}
.job-search {
  border-color: #d9d9d9;
  border-radius: 7px;
}
.job-search input {
  font-size: 13px;
}
.job-search button {
  background: #f2f5ee;
  color: #59822f;
  font-size: 12px;
}
.preference-banner {
  border-color: #c6e2c6;
  background: #f2f5ee;
}
.preference-banner strong {
  color: #1a1a1a;
  font-size: 14px;
}
.preference-banner small {
  color: #59822f;
  font-size: 12px;
}
.job-card:hover {
  border-color: #a3d2a1;
  box-shadow: 0 8px 22px rgb(10 27 51 / 7%);
}
.job-card-title strong,
.job-detail-salary {
  color: #0a1b33;
  font-size: 16px;
  font-weight: 650;
}
.job-card-copy p,
.job-facts,
.job-detail-copy p {
  color: #677281;
  font-size: 13px;
}
.job-card-copy small {
  color: #949494;
  font-size: 12px;
}
.match-label {
  color: #59822f;
  font-size: 12px;
}
.job-detail-top h2,
.profile-header-card h2 {
  font-family: inherit;
  color: #0a1b33;
  font-weight: 650;
}
.job-detail-top h2 {
  font-size: 28px;
}
.job-detail-copy h3,
.profile-section h4 {
  color: #1a1a1a;
  font-size: 15px;
  font-weight: 650;
}
.profile-avatar-large,
.ai-avatar,
.conversation-avatar {
  background: #e4f1e4;
  color: #59822f;
}
.profile-header-card p:last-child,
.profile-card-title span,
.profile-progress small {
  color: #7b8088;
  font-size: 12px;
}
.profile-progress strong {
  color: #59822f;
  font-size: 20px;
}
.profile-fields strong {
  color: #1a1a1a;
  font-size: 13px;
}
.record-main h3 {
  color: #1a1a1a;
  font-size: 14px;
}
.record-main p,
.record-status {
  color: #7b8088;
  font-size: 12px;
}
.record-status {
  color: #59822f;
}
.empty-record h3 {
  font-family: inherit;
  color: #0a1b33;
  font-size: 20px;
}
.settings-nav button.active,
.settings-nav button:hover {
  background: #f2f5ee;
  color: #59822f;
}
.settings-section textarea {
  border-color: #d9d9d9;
  font-size: 13px;
}
.settings-section textarea:focus {
  border-color: #78b33e;
  box-shadow: 0 0 0 2px #e4f1e4;
}
.mode-options button {
  border-color: #d9d9d9;
}
.mode-options button.active {
  border-color: #a3d2a1;
  background: #f2f5ee;
  box-shadow: inset 0 0 0 1px #a3d2a1;
}
.switch.on {
  background: #78b33e;
}
/*
 * ── 职位推荐 ──
 *
 * 版式照参考站：左边一栏职位列表（920），右边一栏常驻 AI 助手（488），
 * 中间留 36 的空隙，整块居中。下面的数值不是目测出来的，是拿参考站截图逐像素量的：
 * 卡片高 130 / 间距 16 / 圆角 12 / 描边 1px、投递按钮 92×34 填充 #292929、
 * 偏好横幅高 105 底色 #DFEC8C、卡内 meta 行是 #292929（不是灰，只有图标是灰）、
 * 公司行 #5E5E5E。
 *
 * 两处刻意偏离参考站，都写在这里免得以后被当成写错：
 *
 *   1) 画布和描边仍是蓝的。参考站是暖白底 + 暖灰侧栏 + 灰绿描边，
 *      而用户后一轮明确指定了「侧栏 #D0E4F2、右侧用它的 20% 淡化」，
 *      那是更晚也更具体的指令。描边同理：参考站的 #E1EADB 压在这片蓝上会发脏，
 *      所以沿用 --line-blue 那三档。
 *   2) 横幅的柠檬绿 #DFEC8C 照搬，没有跟着换成蓝色系。
 *      它是这一页最认得出的东西（参考站整页只有这一块彩色），
 *      而且是参考站的真实取值，不是配色方案。放在冷色画布上对比度反而更高。
 */
.recommend-workspace {
  /*
   * 侧栏宽度。筛选面板要以「侧栏右侧那块内容区」为基准居中，
   * 所以这个值必须和 .ai-resume-sidebar 的实际宽度一致 —— 它有四种取值：
   * 默认 260、折叠时 70、≤1080px 时 210、≤760px 时 70。
   * 下面三条覆盖分别对应后三种，改侧栏宽度时记得一起改。
   */
  --sidebar-width: 260px;
  display: flex;
  height: 100vh;
  box-sizing: border-box;
  justify-content: center;
  gap: 36px;
  padding: 0;
  overflow: hidden;
}
/* 权重 (0,2,0) 高于下面两条媒体查询，折叠时无论多窄都以 70 为准 */
.sidebar-collapsed .recommend-workspace {
  --sidebar-width: 70px;
}
@media (max-width: 1080px) {
  .recommend-workspace {
    --sidebar-width: 210px;
  }
}

/* ── 左栏：列表 ── */
.recommend-column {
  display: flex;
  width: 100%;
  max-width: 920px;
  min-width: 0;
  flex: 1 1 0;
  flex-direction: column;
  overflow-y: auto;
  padding: 24px 0 0;
  scrollbar-gutter: stable;
}
.recommend-title-row {
  display: flex;
  min-height: 32px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.recommend-title-row h2 {
  margin: 0;
  color: var(--ink-strong);
  font-size: 20px;
  font-weight: 650;
}
.title-info {
  display: inline-flex;
  width: 24px;
  height: 24px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--muted-soft);
  cursor: pointer;
  font-size: 15px;
}
.title-info:hover {
  background: var(--hover-blue);
  color: var(--ink);
}

/* 偏好横幅：整块可点，点开就是筛选抽屉 */
.preference-banner {
  display: grid;
  min-height: 104px;
  box-sizing: border-box;
  grid-template-areas:
    'icon copy hint'
    'icon copy arrow';
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  column-gap: 12px;
  row-gap: 2px;
  margin-top: 22px;
  padding: 18px 22px;
  border: 0;
  border-radius: var(--radius-card);
  background: var(--lime);
  color: var(--lime-ink);
  cursor: pointer;
  font: inherit;
  text-align: left;
  transition: filter 160ms ease;
}
.preference-banner:hover {
  filter: brightness(1.02);
}
.preference-icon {
  display: inline-flex;
  width: 30px;
  height: 30px;
  align-items: center;
  justify-content: center;
  grid-area: icon;
  border-radius: 8px;
  background: var(--lime-ink);
  color: var(--lime);
  font-size: 16px;
}
.preference-copy {
  display: grid;
  align-self: center;
  grid-area: copy;
  gap: 4px;
  min-width: 0;
}
.preference-copy strong {
  font-size: 13px;
  font-weight: 600;
}
.preference-copy small {
  overflow: hidden;
  font-size: 16px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.preference-hint {
  display: inline-flex;
  align-items: center;
  align-self: end;
  grid-area: hint;
  justify-self: end;
  gap: 4px;
  color: var(--lime-muted);
  font-size: 12px;
}
.preference-arrow {
  align-self: start;
  grid-area: arrow;
  justify-self: end;
  color: var(--lime-ink);
  font-size: 18px;
}

.list-head {
  display: flex;
  min-height: 32px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 26px;
}
.list-head h3 {
  margin: 0;
  overflow: hidden;
  color: var(--olive-heading);
  font-size: 18px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.filter-trigger {
  display: inline-flex;
  min-height: 32px;
  align-items: center;
  flex: none;
  gap: 6px;
  padding: 0 14px;
  border: 1px solid var(--line-blue);
  border-radius: 8px;
  background: var(--paper);
  color: var(--ink);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
}
.filter-trigger:hover {
  border-color: var(--line-blue-strong);
  background: var(--hover-blue);
}
.filter-count {
  min-width: 18px;
  padding: 0 5px;
  border-radius: 9999px;
  background: var(--action);
  color: #fff;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
}

.job-list {
  display: grid;
  gap: 16px;
  margin-top: 16px;
  padding-bottom: 32px;
}
.job-card {
  display: grid;
  gap: 14px;
  padding: 17px 22px;
  border: 1px solid var(--line-blue);
  border-radius: 12px;
  background: var(--paper);
  cursor: pointer;
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease;
}
.job-card:hover,
.job-card:focus-visible {
  border-color: var(--line-blue-strong);
  box-shadow: 0 8px 20px rgb(10 27 51 / 6%);
  outline: 0;
}
.job-card-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
}
.job-card-head h3 {
  margin: 0;
  overflow: hidden;
  min-width: 0;
  color: var(--ink-strong);
  font-size: 17px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.job-card-head strong {
  color: var(--job-salary);
  font-size: 17px;
  font-weight: 650;
  white-space: nowrap;
}
/* 职位详情的薪资用同一个 token：卡片和详情说的是同一件事，不该是两个颜色 */
.job-detail-salary {
  color: var(--job-salary);
}

/*
 * 事实行：条目之间是 1px 竖线，不是「·」。
 * 线由 li + li::before 画，左边距 10 右边距 5 —— 再叠上 li 自身的 5px gap，
 * 两侧视觉间距都是 10。
 */
.job-facts {
  display: flex;
  align-items: center;
  margin: 0;
  padding: 0;
  color: var(--ink);
  font-size: 13px;
  list-style: none;
}
.job-facts li {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  gap: 5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.job-facts li + li::before {
  width: 1px;
  height: 12px;
  flex: none;
  margin: 0 5px 0 10px;
  background: rgb(0 0 0 / 12%);
  content: '';
}
.job-facts svg {
  flex: none;
  color: var(--muted-soft);
  font-size: 14px;
}

.job-card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.job-card-foot small {
  overflow: hidden;
  min-width: 0;
  color: var(--ink-soft);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.job-card-actions {
  display: flex;
  align-items: center;
  flex: none;
  gap: 10px;
}
.solid-action {
  min-height: 34px;
  padding: 0 18px;
  border: 1px solid #292929;
  border-radius: 8px;
  background: #292929;
  color: #fff;
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  font-weight: 500;
}
.solid-action:hover {
  border-color: #3d3d3d;
  background: #3d3d3d;
}

/* ── 右栏：常驻 AI 助手 ── */
.assistant-rail {
  display: flex;
  width: 400px;
  flex: 0 0 400px;
  flex-direction: column;
  min-height: 0;
  border-left: 1px solid var(--line-soft);
  background: var(--paper);
  overflow: hidden;
}
.rail-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 20px 16px;
  border-bottom: 1px solid var(--line-soft);
}

/*
 * 历史记录浮层的两种落位。
 * ChatHistory 组件本身只管「按钮 + 面板」，摆哪儿由调用方决定：
 * 求职助理那页没有顶栏，落在内容区右上角；职位推荐那页落在右侧助手栏的表头右端。
 */
.chat-history.is-corner {
  position: absolute;
  z-index: 5;
  top: 24px;
  right: 28px;
}
.chat-history.is-inline {
  margin-left: auto;
}
.rail-avatar {
  display: inline-flex;
  width: 40px;
  height: 40px;
  align-items: center;
  justify-content: center;
  flex: none;
  border-radius: 50%;
  background: #e8ece5;
  color: #426122;
  font-size: 20px;
}
.rail-identity {
  display: grid;
  min-width: 0;
}
.rail-identity strong {
  color: var(--ink-strong);
  font-size: 14px;
  font-weight: 600;
}
.rail-thread {
  display: grid;
  align-content: start;
  flex: 1;
  gap: 14px;
  min-height: 0;
  overflow-y: auto;
  padding: 20px;
}
.rail-greeting {
  margin: 0;
  color: var(--ink-strong);
  font-size: 16px;
  font-weight: 650;
}
.rail-lede {
  margin: 0;
  color: var(--ink);
  font-size: 14px;
  line-height: 1.7;
}
.rail-suggest {
  display: grid;
  gap: 10px;
  margin-top: 4px;
  padding: 16px;
  border-radius: var(--radius-card);
  background: var(--sage-1);
}
.rail-suggest strong {
  color: var(--ink);
  font-size: 13px;
  font-weight: 600;
}
.rail-suggest ul {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.rail-suggest button {
  display: block;
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  line-height: 1.6;
  text-align: left;
}
.rail-suggest button:hover {
  color: var(--ink);
}
.rail-bubble {
  display: flex;
  max-width: 92%;
  gap: 8px;
}
.rail-bubble.is-user {
  margin-left: auto;
  flex-direction: row-reverse;
}

/*
 * 一轮 = 气泡 +（可选）岗位卡片，所以包了一层。
 * 对齐交给这一层：气泡自己是 max-width 88%/92% 的窄条，卡片要占满整栏。
 */
.chat-turn,
.rail-turn {
  display: grid;
  gap: 8px;
}
.chat-turn.is-user,
.rail-turn.is-user {
  justify-items: end;
}

/*
 * 对话里的岗位卡片。
 * 信息和职位推荐页的 .job-card 是同一套（职位名 + 薪资 / 城市·学历·经验 / 公司），
 * 但窄得多 —— 助手栏只有 400px 宽，照搬那套会被压散。
 * 薪资同样用 --job-salary（红），和列表页保持同一个语义。
 */
.chat-jobs {
  display: grid;
  width: 100%;
  gap: 8px;
}

/*
 * 求职助理页：卡片和气泡里那段文字同宽并居中。
 *
 * 611.19 不是一个独立的设计数值，它等于 740（面板）× 88%（气泡 max-width）
 * 再减掉气泡左侧的 30px 图标 + 10px 间距。所以这里写成关系式而不是写死数字 ——
 * 面板宽度一变（窄屏时是 100%），卡片会跟着气泡一起变，不会错位。
 * 助手栏那侧太窄，卡片保持整栏宽，不套这条。
 */
.chat-turn .chat-jobs {
  width: calc(100% * 0.88 - 40px);
  margin: 0 auto;
}
.chat-job {
  display: grid;
  width: 100%;
  box-sizing: border-box;
  gap: 6px;
  padding: 10px 12px;
  border: 1px solid var(--line-blue);
  border-radius: 10px;
  background: var(--paper);
  cursor: pointer;
  font: inherit;
  text-align: left;
  transition: border-color 160ms ease, box-shadow 160ms ease;
}
.chat-job:hover {
  border-color: var(--line-blue-strong);
  box-shadow: 0 6px 16px rgb(10 27 51 / 8%);
}
.chat-job-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}
.chat-job-head strong {
  overflow: hidden;
  color: var(--ink-strong);
  font-size: 14px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chat-job-head b {
  color: var(--job-salary);
  font-size: 14px;
  font-weight: 650;
  white-space: nowrap;
}
.chat-job-facts {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
  color: var(--ink);
  font-size: 12px;
}
.chat-job small {
  overflow: hidden;
  color: var(--ink-soft);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rail-mark {
  display: inline-flex;
  width: 26px;
  height: 26px;
  align-items: center;
  justify-content: center;
  flex: none;
  border-radius: 50%;
  background: #e8ece5;
  color: #426122;
  font-size: 13px;
}
.rail-bubble.is-user .rail-mark {
  background: var(--action);
  color: #fff;
}
.rail-bubble p {
  margin: 0;
  padding: 9px 12px;
  border-radius: 10px;
  background: var(--sage-1);
  color: var(--ink);
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
}
.rail-bubble.is-user p {
  background: var(--action);
  color: #fff;
}
.rail-error {
  margin: 0;
  color: #b42318;
  font-size: 12px;
}
.rail-composer {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  margin: 0 20px 16px;
  padding: 10px 10px 10px 14px;
  border: 1px solid var(--line-blue);
  border-radius: var(--radius-card);
  background: var(--paper);
}
.rail-composer:focus-within {
  border-color: var(--line-blue-strong);
}
.rail-composer textarea {
  width: 100%;
  min-height: 44px;
  flex: 1;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--ink);
  font: inherit;
  font-size: 14px;
  line-height: 22px;
  resize: none;
}
.rail-composer textarea::placeholder {
  color: rgb(0 0 0 / 20%);
}
.rail-send {
  display: inline-flex;
  width: 32px;
  height: 32px;
  align-items: center;
  justify-content: center;
  flex: none;
  border: 0;
  border-radius: 9999px;
  background: var(--action);
  color: #fff;
  cursor: pointer;
  font-size: 16px;
}
.rail-send:disabled {
  background: var(--line-blue);
  color: #6b8299;
  cursor: not-allowed;
}
.rail-note {
  margin: 0;
  padding: 0 20px 18px;
  color: #5c6370;
  font-size: 11px;
  text-align: center;
}

/* ── 求职偏好信息抽屉 ──
 *
 * 和下面的筛选面板是两码事，所以长得也不一样：
 * 这个改的是「你是谁」，从右侧滑入；筛选面板改的是「这一次看哪些职位」，中间弹出。
 *
 * 参考站那张抽屉里填的是它自己的偏好模型（拒绝职位 / 公司类型 / 公司规模 /
 * 优先偏好 / 拒绝偏好），我的库里没有这些字段 —— 摆上去就是几个存不下去的输入框。
 * 所以这里只展示我实际有的求职资料，要改就回门户的资料页改。
 */
.preference-scrim {
  position: fixed;
  z-index: 60;
  inset: 0;
  background: rgb(0 0 0 / 28%);
}
.preference-drawer {
  position: fixed;
  z-index: 61;
  top: 0;
  right: 0;
  bottom: 0;
  display: flex;
  width: min(440px, 100%);
  flex-direction: column;
  background: var(--paper);
  box-shadow: -12px 0 34px rgb(10 27 51 / 14%);
}
.drawer-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 20px 22px 16px;
  border-bottom: 1px solid var(--line-soft);
}
.drawer-head h3 {
  margin: 0 0 4px;
  color: var(--ink-strong);
  font-size: 17px;
  font-weight: 650;
}
.drawer-head p {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}
.drawer-body {
  display: grid;
  align-content: start;
  flex: 1;
  gap: 22px;
  min-height: 0;
  overflow-y: auto;
  padding: 20px 22px;
}
.drawer-section h4 {
  margin: 0 0 10px;
  color: var(--ink);
  font-size: 13px;
  font-weight: 650;
}
.drawer-section > p {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.8;
}
.drawer-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px 0;
  color: var(--muted);
  font-size: 13px;
}
/* 一行一个字段：标签在上、控件在下，和参考站那张抽屉一致 */
.pref-field {
  display: grid;
  gap: 7px;
  margin-top: 14px;
}
.pref-field > span {
  color: var(--ink);
  font-size: 13px;
}
.pref-field > span small {
  color: var(--muted-soft);
  font-size: 12px;
}
.pref-field select {
  width: 100%;
  min-height: 38px;
  box-sizing: border-box;
  padding: 0 12px;
  border: 1px solid var(--line-blue);
  border-radius: var(--radius-input);
  background: var(--paper);
  color: var(--ink);
  font: inherit;
  font-size: 13px;
}
.pref-field select:focus {
  border-color: var(--line-blue-strong);
  outline: 0;
}
.pref-field select:disabled {
  background: var(--sage-1);
  color: var(--muted-soft);
}
/*
 * 意向城市是一个整体控件：已选的城市标签和「添加城市」下拉装在同一个描边框里，
 * 和参考站那种「框里有标签 + 右侧箭头」一致。
 * 早先标签和下拉是上下两块，看着像两个互不相干的字段。
 */
.pref-cities {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 38px;
  box-sizing: border-box;
  padding: 5px 10px;
  border: 1px solid var(--line-blue);
  border-radius: var(--radius-input);
  background: var(--paper);
}
.pref-cities:focus-within {
  border-color: var(--line-blue-strong);
}
.pref-city {
  display: inline-flex;
  height: 24px;
  align-items: center;
  gap: 3px;
  padding: 0 4px 0 9px;
  border-radius: 6px;
  background: var(--line-blue-strong);
  color: var(--ink);
  font-size: 12px;
}
.pref-city button {
  display: inline-flex;
  width: 15px;
  height: 15px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
  font-size: 11px;
}
.pref-city button:hover {
  background: rgb(255 255 255 / 75%);
  color: var(--ink);
}
.pref-city-full {
  color: var(--muted-soft);
  font-size: 12px;
}
.pref-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
}
.drawer-note {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin: 18px 0 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}
.drawer-note > svg {
  flex: none;
  margin-top: 2px;
}
.drawer-foot {
  display: flex;
  justify-content: flex-end;
  padding: 16px 22px;
  border-top: 1px solid var(--line-soft);
}
/* ── 筛选面板 ──
 *
 * 版式借自参考站那张「筛选条件」：一行 = 左侧标签列 + 一撮 chip，
 * chip 高 26、行距 42（行间 16）、标签列宽 70、底部左「清除筛选」右「取消 / 确定」。
 * 桌面端 chip 抬到 28，其余照量到的值。
 *
 * 换成了我这套主题的两处：
 *   1) 选中态。参考站是块绿调灰 #EAF0E6，压在蓝画布上会发脏，
 *      改成 --line-blue-strong 的淡蓝；悬停再浅一档。
 *   2) 位置与宽度。参考站是窄视口下贴底的通铺；桌面端通铺到 1600px 太怪，
 *      改成 760 宽、屏幕正中弹出。注意居中的基准要扣掉左侧导航：
 *      直接 left:50% 是按整个视口算的，会让面板比内容区正中偏左半个侧栏宽。
 */
.filter-scrim {
  position: fixed;
  z-index: 60;
  inset: 0;
  background: rgb(0 0 0 / 28%);
}
.filter-panel {
  position: fixed;
  z-index: 61;
  top: 50%;
  /* 居中的基准是「侧栏右侧那块内容区」，不是整个视口 */
  left: calc(var(--sidebar-width) + (100% - var(--sidebar-width)) / 2);
  display: flex;
  width: min(760px, calc(100% - var(--sidebar-width) - 48px));
  max-height: 82vh;
  box-sizing: border-box;
  flex-direction: column;
  border-radius: 16px;
  background: var(--paper);
  box-shadow: 0 18px 48px rgb(10 27 51 / 18%);
  transform: translate(-50%, -50%);
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 22px 10px;
}
.panel-head h3 {
  margin: 0;
  color: var(--ink-strong);
  font-size: 17px;
  font-weight: 650;
}
.panel-close {
  display: inline-flex;
  width: 28px;
  height: 28px;
  align-items: center;
  justify-content: center;
  flex: none;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
  font-size: 16px;
}
.panel-close:hover {
  background: var(--hover-blue);
  color: var(--ink);
}
.panel-body {
  display: grid;
  align-content: start;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 4px 22px 8px;
}
.filter-row {
  display: grid;
  grid-template-columns: 70px minmax(0, 1fr);
  align-items: start;
  gap: 16px;
  padding: 8px 0;
}
.filter-row-label {
  padding-top: 6px;
  color: var(--ink);
  font-size: 13px;
}
.filter-row-body {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 8px;
  min-width: 0;
}
.filter-chip {
  min-height: 28px;
  padding: 0 11px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  line-height: 28px;
  white-space: nowrap;
}
.filter-chip:hover {
  background: var(--hover-blue);
  color: var(--ink);
}
.filter-chip.active {
  background: var(--line-blue-strong);
  color: var(--ink);
  font-weight: 600;
}
.filter-input {
  min-height: 32px;
  flex: 1 1 220px;
  box-sizing: border-box;
  padding: 0 12px;
  border: 1px solid var(--line-blue);
  border-radius: var(--radius-input);
  background: var(--paper);
  color: var(--ink);
  font: inherit;
  font-size: 13px;
}
.filter-input:focus {
  border-color: var(--line-blue-strong);
  outline: 0;
}
.panel-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 22px 18px;
  border-top: 1px solid var(--line-soft);
}
.panel-foot-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.ghost-button {
  min-height: 34px;
  padding: 0 18px;
  border: 1px solid var(--line-blue);
  border-radius: var(--radius-input);
  background: var(--paper);
  color: var(--ink);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
}
.ghost-button:hover {
  background: var(--hover-blue);
}
.solid-button {
  min-height: 34px;
  padding: 0 22px;
  border: 1px solid var(--action);
  border-radius: var(--radius-input);
  background: var(--action);
  color: #fff;
  cursor: pointer;
  font: inherit;
  font-size: 13px;
}
.solid-button:hover {
  background: var(--action-hover);
}
/* 920 + 36 + 400 = 1356，放不下就先撤右栏，列表自己撑满 */
@media (max-width: 1180px) {
  .recommend-workspace {
    gap: 20px;
  }
  .assistant-rail {
    display: none;
  }
}
@media (max-width: 760px) {
  .recommend-workspace {
    --sidebar-width: 70px;
  }
  .recommend-column {
    padding: 16px 16px 0;
  }
  .job-card {
    padding: 14px 16px;
  }
  .job-facts {
    flex-wrap: wrap;
    row-gap: 6px;
  }
  .job-card-foot {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
