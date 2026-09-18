<script lang="ts" setup>
/**
 * MES 首页 - 右侧固定悬浮 AI 对话面板（白底黑字，清晰易读）
 *
 * 1. fixed 固定定位（右 16px / 下 20px）；面板固定白色背景 + 黑色文字，不随主题变化
 * 2. 宽 380px（最大化 560px），高 min(720px, 视口-32px)；消息区内部滚动
 * 3. 最小化收起为右下角悬浮球（悬停"MES 智能助手"）；支持最大化切换；默认展开
 * 4. 对接现有 AI 流式接口：权限拦截在后端，越权返回拒绝话术直接渲染
 * 5. Enter 发送 / Shift+Enter 换行；会话仅当前页面缓存，刷新清空
 */
import type { AiChatMessageApi } from '#/api/ai/chat/message';

import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Button, Input, Tooltip, message } from 'ant-design-vue';

import {
  createChatConversationMy,
  deleteChatConversationMy,
} from '#/api/ai/chat/conversation';
import { sendChatMessageStream } from '#/api/ai/chat/message';
import { getMyPage } from '#/api/ai/model/chatRole';
import { uploadFile } from '#/api/infra/file';
import { MarkdownView } from '#/components/markdown-view';

// 【新增模块 1、2、3】Word 单据链接 / Vben 预览弹窗 / 模板数据
import MesDocPreviewModal from './MesDocPreviewModal.vue';
import MesAiDocLink from './MesAiDocLink.vue';
import MesFeedbackPrefillModal from './MesFeedbackPrefillModal.vue';
import MesAndonDraftModal from './MesAndonDraftModal.vue';
import MesQcPrefillModal from './MesQcPrefillModal.vue';
import {
  getBlankSchema,
  matchDocIntent,
  type BlankDocFormState,
} from './blankDocSchema';
// 【新增模块：导入解析】Word/Excel/粘贴文本 → 识别单据 → 提取字段填充模板
// （注：附件文件改走后端统一抽取，前端 parseDocFromFile 退役，仅保留粘贴文本解析）
import {
  looksLikeDocText,
  parseDocFromText,
  type DocParseResult,
} from './mesDocImport';

// 关键：antd 的多行输入框是 Input.TextArea，必须显式取出（直接写 <Textarea> 会被当成未知元素）
const TextArea = Input.TextArea;

/** 组件 Props（带默认值：MES 首页无需传参即可工作） */
const props = withDefaults(
  defineProps<{
    /** AI 角色编号（不传则按 roleName 自动查找，仍找不到时使用该回退值） */
    fallbackRoleId?: number;
    /** 面板展开时是否推动页面主体避让（给 body 加 class） */
    pushBody?: boolean;
    /** AI 角色名称（用于自动查找角色编号） */
    roleName?: string;
  }>(),
  {
    fallbackRoleId: 20, // 兜底：MES 生产助手角色编号
    pushBody: true,
    roleName: 'MES 生产助手',
  },
);

/** 面板状态：展开（默认）/ 收起为悬浮球；最大化切换 */
const expanded = ref(true);
const maximized = ref(false);
/** 会话就绪标记（创建成功后才能发送） */
const conversationReady = ref(false);
/** 流式接收中 */
const streaming = ref(false);
/** 输入框内容 */
const inputText = ref('');
/** 聊天消息列表（页面会话内缓存，刷新清空）
 * 【新增】docKey：单据模板 key，有值时渲染预览入口链接
 * 【新增】docState：导入解析出的填充数据（确认后传入预览弹窗）
 * 【新增】docConfirm：true 时渲染「确认无误，打开预览」按钮 */
const messages = ref<
  {
    content: string;
    docConfirm?: boolean;
    docKey?: string;
    docState?: BlankDocFormState;
    /** 【新增】澄清选项：用户只说单据名词时提供快捷选择 */
    options?: string[];
    /** 【新增：任务包 B】报工预填：任务编号（确认后打开报工弹窗） */
    prefillTaskCode?: string;
    /** 【新增：任务包 D】安灯上报草稿：用户口述的异常描述（确认后打开上报弹窗） */
    andonDraftText?: string;
    /** 【新增：任务包 D】质检预填：任务编号（确认后打开检验单弹窗） */
    qcPrefillTaskCode?: string;
    role: 'assistant' | 'user';
    time: string;
  }[]
>([]);

/** 【新增】空白单据预览弹窗实例（Vben Modal，底部自带「关闭」「下载 Word 文件」） */
const docPreviewRef = ref<InstanceType<typeof MesDocPreviewModal>>();

/** 【新增】打开单据预览弹窗：按 key 取空白模板，所有字段留空由用户填写 */
function openDoc(key?: string) {
  if (!key) return;
  docPreviewRef.value?.open(getBlankSchema(key));
}

/**
 * 【新增：导入解析】打开单据预览弹窗（带解析出的填充数据）
 * 仅填充前端模板预览，不写任何后台数据库
 */
function openDocFilled(key: string | undefined, state?: BlankDocFormState) {
  if (!key) return;
  docPreviewRef.value?.open(getBlankSchema(key), state);
}

/** 【新增：导入解析】文件选择框 */
const fileInputRef = ref<HTMLInputElement>();

/** 【新增：任务包 B】报工预填弹窗实例 */
const feedbackModalRef = ref<InstanceType<typeof MesFeedbackPrefillModal>>();
/** 【新增：任务包 D】安灯上报弹窗 / 质检预填弹窗实例 */
const andonModalRef = ref<InstanceType<typeof MesAndonDraftModal>>();
const qcModalRef = ref<InstanceType<typeof MesQcPrefillModal>>();

/**
 * 【新增：任务包 D1】安灯上报意图：命中"上报异常/安灯呼叫/出了异常"等说法
 * @returns 用户口述的异常描述（作为草稿备注），未命中返回 null
 */
function matchAndonReport(text: string): string | null {
  return /(上报异常|异常上报|安灯呼叫|呼叫安灯|安灯上报|报个?异常|出了?(点|些)?异常|发生异常|设备异常|异常停机)/.test(
    text ?? '',
  )
    ? (text ?? '').trim()
    : null;
}

/**
 * 【新增：任务包 D2】质检预填意图：命中"质检/检验预填 + 任务编号"
 * @returns 任务编号；未命中返回 null
 */
function matchQcPrefill(text: string): string | null {
  const t = (text ?? '').trim();
  if (!/(质检预填|检验预填|质检填单|检验填单|报检|过程检验)/.test(t)) {
    return null;
  }
  const labeled = t.match(
    /(?:任务号|任务编号|任务|工单号|工单编号|编号)(?:是|为|叫|[:：])?\s*([A-Za-z0-9_-]{3,32})/,
  );
  return labeled?.[1] ?? t.match(/[A-Za-z0-9_-]{3,32}/)?.[0] ?? null;
}

/**
 * 【新增：任务包 D3】出入库操作引导：命中"入库/出库 + 流程/怎么/如何"等操作类问法
 * （纯数据查询如"库存多少"不受影响，仍走业务查询）
 */
function matchWmsGuide(text: string): boolean {
  const t = (text ?? '').trim();
  if (!t || t.length > 30) return false;
  return /((怎么|如何|什么流程|哪些步骤|什么步骤).{0,8}(入库|出库))|((入库|出库).{0,10}(流程|步骤|怎么操作|如何操作|怎么填|如何填|怎么办理|如何办理))/.test(
    t,
  );
}

/** 【新增：任务包 D3】出入库操作引导消息 */
function handleWmsGuide() {
  messages.value.push({
    role: 'assistant',
    content:
      '出入库单据的操作路径如下（AI 不代写单据，具体操作在「仓储管理」菜单完成）：\n' +
      '1. 入库：到达通知 → 收货 → 质检（如需）→ 上架；成品入库走「产品入库」。\n' +
      '2. 出库：领料出库（生产领料）/ 销售出库（发货）/ 其他出库，按对应单据创建并审核。\n' +
      '3. 我可以帮你先查数据：点击下方选项查库存或待检任务，确认后再去页面操作。',
    options: ['查询全部库存', '有哪些待检任务', '我要上报异常'],
    time: formatTime(),
  });
  scrollToBottom();
}

/** 【新增：任务包 D1】安灯上报成功回执 */
function handleAndonSubmitted(summary: string) {
  messages.value.push({
    role: 'assistant',
    content: `🔔 安灯呼叫成功：${summary}。已进入待处置列表，处置完成后可在安灯记录中查看。`,
    time: formatTime(),
  });
  scrollToBottom();
}

/** 【新增：任务包 D2】检验单创建成功回执 */
function handleQcSubmitted(summary: string) {
  messages.value.push({
    role: 'assistant',
    content: `✅ 过程检验单已创建：${summary}。可在 MES「过程检验」页面查看与补充检验项明细。`,
    time: formatTime(),
  });
  scrollToBottom();
}

/**
 * 【新增：任务包 B】报工意图识别：
 * 命中"报工/填单/预填" + 任务编号（字母数字-下划线，≥3 位）时进入报工预填流程
 * @returns 命中的任务编号；未命中返回 null
 */
function matchFeedbackPrefill(text: string): string | null {
  const t = (text ?? '').trim();
  // 【修复】工单添加/录入意图（创建类动词 + 工单）交给后端录入工具真实写库，不做报工预填
  if (/(添加|创建|新增|生成|录入|登记|补录).{0,10}(生产工单|工单)/.test(t)) {
    return null;
  }
  // 【修复】剥离括号内的说明文字（如"已确认（可直接用于报工/检验流程）"），
  // 避免"报工"仅出现在说明里时被误判为报工意图
  const core = t.replace(/[（(][^）)]*[）)]/g, ' ');
  if (!/(报工|填单|预填)/.test(core)) return null;
  // 优先取"任务号/编号(是|为|:)"后面的值，其次取任意编码形态的 token
  const labeled = core.match(
    /(?:任务号|任务编号|任务|编号)(?:是|为|叫|[:：])?\s*([A-Za-z0-9_-]{3,32})/,
  );
  const code = labeled?.[1] ?? core.match(/[A-Za-z0-9_-]{3,32}/)?.[0];
  return code ?? null;
}

/** 【新增：任务包 B】报工意图分支：回复确认消息，用户点击后打开预填弹窗 */
function handleFeedbackPrefillIntent(taskCode: string) {
  messages.value.push({
    role: 'assistant',
    content: `✅ 已定位任务「${taskCode}」，即将为您预填报工信息（本次报工数量默认为剩余可报数量，可修改）。确认无误后点击下方按钮：`,
    docConfirm: true,
    prefillTaskCode: taskCode,
    time: formatTime(),
  });
  scrollToBottom();
}

/** 【新增：任务包 B】报工提交成功回执 */
function handleFeedbackSubmitted(feedbackCode: string) {
  messages.value.push({
    role: 'assistant',
    content: `🎉 报工单 ${feedbackCode} 已提交成功，当前进入审批流。可在 MES「生产报工」页面查看进度；如需继续报工其他任务，直接告诉我任务编号即可。`,
    time: formatTime(),
  });
  scrollToBottom();
}

/**
 * 【新增】单据名词 + 意图动词表：用于识别"只说了单据名词"的模糊输入
 */
const DOC_NOUNS = ['生产工单', '工单', '领料单', '入库单', '采购单', '检验单', '质检单'];
const ACTION_INTENT_RE =
  /(添加|新增|新建|创建|生成|录入|登记|出具|打印|制作|发起|申请|做|开|填|写|导入|解析|模板)/;
const QUERY_INTENT_RE =
  /(查|搜|看看|看一下|多少|几个|几条|列表|统计|明细|状态|进度|哪些|详情|情况|库存|在库)/;

/**
 * 【新增】判断是否为"只说单据名词"的模糊输入（如：生产工单 / 领料单）
 * 命中时先反问澄清（查询还是生成、什么状态），不擅自猜测意图
 * @returns 命中的单据名词，未命中返回 null
 */
function matchAmbiguousDoc(text: string): string | null {
  const t = (text ?? '').trim();
  if (!t || t.length > 12) return null;
  // 去掉语气后缀与客套词后，必须包含完整单据名词
  const stripped = t.replace(/[呢啊呀吧的吗]|帮我|请/g, '');
  if (ACTION_INTENT_RE.test(stripped) || QUERY_INTENT_RE.test(stripped)) {
    return null;
  }
  return DOC_NOUNS.find((n) => stripped.includes(n)) ?? null;
}

/**
 * 【新增：模糊工单查询澄清】"我想查看工单"这类无具体条件的查询，
 * 先反问澄清（状态/编号/产品），不直接倾倒全表——用户点选项或补充条件后再查。
 * @returns 命中返回 true（需要澄清）；带具体条件的查询返回 false（正常走后端）
 */
function matchVagueWorkOrderQuery(text: string): boolean {
  const t = (text ?? '').trim();
  if (!t || t.length > 24) return false;
  // 必须是查询类说法 + 提到工单
  if (!/(查看|查询|看看|找一下?|搜一下?|调取)/.test(t)) return false;
  if (!/工单/.test(t)) return false;
  // 已带具体条件则放行：工单编号 token、状态词、"全部"、产品名、客户名、数量词
  if (
    /(全部|已确认|已确认状态|草稿|已完成|已取消|[A-Za-z]{2,}[0-9_-]{2,}|\d{3,}|螺丝|PVC|钢筋|色粉|包装盒|客户|按产品|按编号)/.test(
      t,
    )
  ) {
    return false;
  }
  return true;
}

/** 【新增】按单据名词生成澄清选项（工单额外提供状态筛选） */
function buildDocOptions(noun: string): string[] {
  if (noun.includes('工单')) {
    return [
      '查询全部生产工单',
      '查询已确认的生产工单',
      '查询草稿状态的生产工单',
      '查询已完成的生产工单',
      '查询已取消的生产工单',
      '生成生产工单模板',
    ];
  }
  return [`查询全部${noun}`, `生成${noun}模板`];
}

/** 【新增】点击澄清选项：作为用户消息走正常处理链路 */
async function sendOption(opt: string) {
  if (streaming.value || !conversationReady.value) return;
  inputText.value = opt;
  await handleSend();
}

/** 【新增：导入解析】统一处理解析结果 → 助手确认/失败消息 */
function pushParseResult(result: DocParseResult) {
  if (result.ok && result.schema && result.state) {
    const missingHint =
      result.missing && result.missing.length > 0
        ? `\n（未提取到、将留空待填：${result.missing.join('、')}）`
        : '';
    messages.value.push({
      role: 'assistant',
      content: `✅ 已识别【${result.schema.title}】，提取到以下信息：\n${result.summary}${missingHint}\n\n确认无误后，点击下方按钮打开单据预览：`,
      docConfirm: true,
      docKey: result.schema.key,
      docState: result.state,
      time: formatTime(),
    });
  } else {
    messages.value.push({
      role: 'assistant',
      content: `❌ 无法识别单据：${result.reason ?? '内容格式无法解析'}。\n请检查文件/文本格式后重试；字段名与模板略有差异可自动匹配，完全缺失的字段将留空。（仅用于前端模板预览，不会写入后台数据库）`,
      time: formatTime(),
    });
  }
  scrollToBottom();
}

/**
 * 【交互改造】待发送附件：📎 选择后先挂在输入框上方（不立即处理），
 * 由用户输入意图（或点快捷意图按钮）后，发送时才统一上传并交给后端处理
 */
const pendingFiles = ref<
  Array<{ file: File; kind: 'image' | 'doc'; preview?: string }>
>([]);

/** 附件按钮点击：选择文件 → 挂到待发送区 */
function handleImportFile(e: Event) {
  const input = e.target as HTMLInputElement;
  const files = Array.from(input.files ?? []);
  input.value = ''; // 允许重复选择同一个文件
  for (const file of files) {
    const kind: 'image' | 'doc' = file.type.startsWith('image/')
      ? 'image'
      : 'doc';
    pendingFiles.value.push({
      file,
      kind,
      preview: kind === 'image' ? URL.createObjectURL(file) : undefined,
    });
  }
}

/** 移除待发送附件（同时释放预览 URL） */
function removePendingFile(index: number) {
  const item = pendingFiles.value[index];
  if (item?.preview) URL.revokeObjectURL(item.preview);
  pendingFiles.value.splice(index, 1);
}

/** 快捷意图：点击后填入输入框，用户可修改后发送 */
function applyQuickIntent(text: string) {
  inputText.value = text;
}

/** 【新增：导入解析】处理粘贴的单据文本（在 handleSend 中调用） */
function handlePastedDocText(content: string): boolean {
  // 像"工单编号：xxx / 物料清单：…"这类内容才尝试解析；解析成功或明确失败都由助手回复
  if (!looksLikeDocText(content)) return false;
  const result = parseDocFromText(content);
  if (result.ok) {
    pushParseResult(result);
    return true;
  }
  // 疑问句（工单编号：MO123 是什么状态？）不劫持，走正常问答
  const isQuestion = /[?？]|什么|怎么|多少|是否|如何|吗|哪/.test(content);
  if (isQuestion) return false;
  pushParseResult(result);
  return true;
}
/** 消息滚动容器 */
const messageListRef = ref<HTMLDivElement>();
/** 流式中断控制器 */
const abortController = ref<AbortController>();

/** ========== 悬浮球 / 面板拖拽定位 ==========
 * 初始固定在视口右下角；用户可拖拽移动（悬浮球整体拖拽、面板按住标题栏拖拽），
 * 拖拽后的位置存入 localStorage，刷新后恢复上次位置 */
const FAB_SIZE = 52;
const PANEL_WIDTH = 380;
const PANEL_HEIGHT_FALLBACK = 720;

function clampPos(x: number, y: number, w: number, h: number) {
  return {
    x: Math.min(Math.max(0, x), Math.max(0, window.innerWidth - w)),
    y: Math.min(Math.max(0, y), Math.max(0, window.innerHeight - h)),
  };
}

function loadPos(key: string, defX: number, defY: number) {
  try {
    const raw = localStorage.getItem(key);
    if (raw) {
      const p = JSON.parse(raw);
      if (Number.isFinite(p?.x) && Number.isFinite(p?.y)) {
        return { x: p.x, y: p.y };
      }
    }
  } catch {
    /* 忽略反序列化失败，使用默认位置 */
  }
  return { x: defX, y: defY };
}

function savePos(key: string, pos: { x: number; y: number }) {
  try {
    localStorage.setItem(key, JSON.stringify(pos));
  } catch {
    /* 存储不可用时忽略 */
  }
}

/** 悬浮球初始位置：右下角（right 20 / bottom 24） */
const fabPos = ref(
  loadPos(
    'mes-ai-fab-pos',
    window.innerWidth - FAB_SIZE - 20,
    window.innerHeight - FAB_SIZE - 24,
  ),
);
/** 面板初始位置：右下角（right 16 / top 20） */
const panelPos = ref(
  loadPos('mes-ai-panel-pos', window.innerWidth - PANEL_WIDTH - 16, 20),
);
/** 最大化停靠位置（默认右侧贴边、top 88，可拖拽后记忆） */
const maxPos = ref(loadPos('mes-ai-max-pos', window.innerWidth - 488, 88));

const fabStyle = computed(() => ({
  left: `${fabPos.value.x}px`,
  top: `${fabPos.value.y}px`,
}));

/** 面板尺寸：最大化默认停靠屏幕右侧（全高抽屉式，可拖拽后记忆），普通尺寸按拖拽位置定位 */
const panelStyle = computed(() => {
  if (maximized.value) {
    const w = Math.min(480, window.innerWidth - 64);
    const p = clampPos(maxPos.value.x, maxPos.value.y, w, window.innerHeight - 108);
    return {
      width: `${w}px`,
      height: `${Math.round(window.innerHeight - p.y - 20)}px`,
      left: `${p.x}px`,
      top: `${p.y}px`,
    };
  }
  return {
    height: 'min(720px, calc(100vh - 32px))',
    width: '380px',
    left: `${panelPos.value.x}px`,
    top: `${panelPos.value.y}px`,
  };
});

/** 拖拽状态（模块级即可，同一时刻只拖一个元素） */
interface DragState {
  target: 'fab' | 'panel';
  startX: number;
  startY: number;
  origX: number;
  origY: number;
  moved: boolean;
}
let dragState: null | DragState = null;
/** 上一次拖拽是否发生了移动（用于区分「拖拽」与「点击」） */
let lastDragMoved = false;

function onDragMove(e: MouseEvent) {
  if (!dragState) return;
  const dx = e.clientX - dragState.startX;
  const dy = e.clientY - dragState.startY;
  if (Math.abs(dx) > 3 || Math.abs(dy) > 3) {
    dragState.moved = true;
  }
  const isFab = dragState.target === 'fab';
  const isPanelMax = dragState.target === 'panel' && maximized.value;
  const w = isFab ? FAB_SIZE : isPanelMax ? Math.min(480, window.innerWidth - 64) : PANEL_WIDTH;
  const h = isFab
    ? FAB_SIZE
    : isPanelMax
      ? window.innerHeight - 88
      : Math.min(PANEL_HEIGHT_FALLBACK, window.innerHeight - 32);
  const p = clampPos(dragState.origX + dx, dragState.origY + dy, w, h);
  if (isFab) {
    fabPos.value = p;
    savePos('mes-ai-fab-pos', p);
  } else if (isPanelMax) {
    maxPos.value = p;
    savePos('mes-ai-max-pos', p);
  } else {
    panelPos.value = p;
    savePos('mes-ai-panel-pos', p);
  }
}

function onDragEnd() {
  window.removeEventListener('mousemove', onDragMove);
  window.removeEventListener('mouseup', onDragEnd);
  document.body.style.userSelect = '';
  lastDragMoved = dragState?.moved ?? false;
  dragState = null;
}

/** 拖拽入口：悬浮球整体 / 面板标题栏（最大化时同样可拖拽，位置独立记忆） */
function onDragStart(e: MouseEvent, target: 'fab' | 'panel') {
  const pos =
    target === 'fab'
      ? fabPos.value
      : maximized.value
        ? maxPos.value
        : panelPos.value;
  dragState = {
    target,
    startX: e.clientX,
    startY: e.clientY,
    origX: pos.x,
    origY: pos.y,
    moved: false,
  };
  lastDragMoved = false;
  document.body.style.userSelect = 'none';
  window.addEventListener('mousemove', onDragMove);
  window.addEventListener('mouseup', onDragEnd);
}

/** 悬浮球点击：拖拽结束后未移动才视为点击展开 */
function onFabClick() {
  if (lastDragMoved) {
    lastDragMoved = false;
    return;
  }
  setExpanded(true);
}

// 视口尺寸变化时把两个元素拉回可视范围
function clampAllToViewport() {
  fabPos.value = clampPos(fabPos.value.x, fabPos.value.y, FAB_SIZE, FAB_SIZE);
  if (maximized.value) {
    const w = Math.min(480, window.innerWidth - 64);
    maxPos.value = clampPos(
      maxPos.value.x,
      maxPos.value.y,
      w,
      window.innerHeight - 108,
    );
  } else {
    panelPos.value = clampPos(
      panelPos.value.x,
      panelPos.value.y,
      PANEL_WIDTH,
      Math.min(PANEL_HEIGHT_FALLBACK, window.innerHeight - 32),
    );
  }
}

/** 时间戳轻量展示（HH:mm） */
function formatTime(): string {
  const d = new Date();
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

/** 剥离回复文本中的 <think> 思考过程标签（面板只展示最终回答） */
function stripThink(content: string): string {
  if (!content) return content;
  // 含思考开始标签才进入思考处理；未闭合时返回空（流式中先显示"正在思考"）
  if (content.includes('<think>')) {
    if (content.includes('</think>')) {
      return content.slice(content.indexOf('</think>') + 8).replace(/^\s+/, '');
    }
    return '';
  }
  // 无思考标签：直接展示正文（避免正常回答被误判为思考内容）
  return content;
}

/** 滚动消息区到底部 */
async function scrollToBottom() {
  await nextTick();
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight;
  }
}

/** 展开/收起切换：同步管理 body 避让 class */
function setExpanded(v: boolean) {
  expanded.value = v;
  if (props.pushBody !== false) {
    document.body.classList.toggle('mes-ai-panel-open', v);
  }
  if (v) scrollToBottom();
}

/** 最大化/还原切换 */
function toggleMaximized() {
  maximized.value = !maximized.value;
  scrollToBottom();
}

/** 初始化进行中标记：防止并发初始化导致出现两段欢迎语 */
const initLoading = ref(false);

/**
 * 请求错误分类提示：把底层异常翻译成面向用户的友好提示。
 *
 * 判断顺序：鉴权失效（401/403）> 网络层异常（断网/超时/CORS）> 后端业务异常（带 msg）> 服务不可用（5xx/无响应体）。
 * @param e        抛出的错误对象（axios 错误 / fetch 错误 / 任意异常）
 * @param fallback 无法识别类型时的兜底文案
 */
function describeRequestError(e: any, fallback: string): string {
  const respData = e?.response?.data ?? e?.data ?? {};
  const httpStatus = e?.response?.status;
  const bizCode = respData?.code;
  // 1. 鉴权失效：HTTP 401/403 或业务 code 401/403（后端安全过滤器直接返回 JSON 时 HTTP 可能为 200）
  if (bizCode === 401 || httpStatus === 401 || bizCode === 403 || httpStatus === 403) {
    return '登录状态已失效，请重新登录后再试。';
  }
  const rawMsg = String(respData?.msg ?? e?.message ?? e?.msg ?? '').trim();
  // 2. 网络层错误特征：axios/fetch 在无业务响应体时抛出的报错信息
  if (
    !rawMsg ||
    /network\s*error|failed\s+to\s+fetch|load\s+failed|timeout|timed\s*out|econnaborted|econnrefused|econnreset|网络错误|网络异常|请求超时/i.test(
      rawMsg,
    )
  ) {
    return '网络异常，请检查网络连接后重试。';
  }
  // 3. 服务不可用：HTTP 5xx 或通用内部错误信息
  //    含网关服务发现失败（如 "Unable to find instance for ai-server"）——AI 服务重启/维护窗口期出现
  if (
    (httpStatus && httpStatus >= 500) ||
    /系统异常|服务不可用|internal server error|暂时无法服务|unable\s+to\s+find\s+instance|no\s+instances\s+available|loadbalancer/i.test(
      rawMsg,
    )
  ) {
    return 'AI 服务暂时不可用（可能正在维护或重启），请稍后重试。';
  }
  // 4. [SSE 修复] 流式接口的 Content-Type 校验失败：
  //    fetch-event-source 在收到 application/json 时会抛英文
  //    "Expected content-type to be text/event-stream, Actual: ..."，
  //    本质是后端流链路异常被 JSON 化，按约定话术转中文，不展示原始英文。
  if (/expected\s+content-type|text\/event-stream|event-stream/i.test(rawMsg)) {
    return '数据查询接口异常，请稍后重试。';
  }
  // 5. 后端业务错误（如“聊天角色不存在”“找不到默认模型”）：透出后端真实原因
  return rawMsg || fallback;
}

/** 创建会话：查找「MES 生产助手」角色（带回退逻辑），失败给出分类提示可重试 */
async function initConversation() {
  // 防重入：上一次初始化未完成时跳过。
  // 流式接收中允许重开：下方会先中断旧流，再销毁旧会话重建，保证「重新开始会话」始终可用
  if (initLoading.value) return;
  initLoading.value = true;
  // 1. 中断可能还在进行中的流式请求，释放旧会话上下文
  abortController.value?.abort();
  abortController.value = undefined;
  streaming.value = false;
  messages.value = []; // 重置会话
  // 2. 销毁旧会话：后端对话及其消息记录一并删除（删除失败不阻断重建，忽略即可）
  const oldId = conversationId.value;
  conversationId.value = '';
  conversationReady.value = false;
  if (oldId) {
    try {
      await deleteChatConversationMy(Number(oldId));
    } catch {
      // 旧会话可能已不存在或已被删除，忽略
    }
  }
  try {
    let roleId = props.fallbackRoleId;
    const page = await getMyPage({
      name: props.roleName,
      pageNo: 1,
      pageSize: 10,
      publicStatus: false,
    });
    const list = page?.list || [];
    const hit = list.find((item: any) => item.name === props.roleName) || list[0];
    if (hit) roleId = hit.id;
    if (!roleId) throw new Error('未找到 AI 角色配置');
    const conv = await createChatConversationMy({ roleId } as any);
    conversationId.value = String(conv ?? '');
    conversationReady.value = true;
    // 会话初始化成功：仅输出一次欢迎引导语（后续对话不会再自我重复介绍）
    messages.value.push({
      role: 'assistant',
      content: `您好，我是${props.roleName}，可为您解答生产工单、任务、报工、质检、设备、库存等业务问题（权限不足的问题会被拒绝回答）。`,
      time: formatTime(),
    });
  } catch (e: any) {
    // 初始化失败：按错误类型分类提示，避免笼统的“未知错误”
    conversationReady.value = false;
    const hint = describeRequestError(e, '后端服务暂时不可用，请稍后重试。');
    messages.value.push({
      role: 'assistant',
      content: `会话初始化失败：${hint}。请点击右上角「重新开始会话」重试。`,
      time: formatTime(),
    });
  } finally {
    initLoading.value = false; // 释放初始化锁
  }
  scrollToBottom();
}

/** 会话编号（后端返回字符串形式的 Long，保持字符串避免精度丢失） */
const conversationId = ref<string>('');

/** 发送消息：流式接收；权限拦截在后端，越权返回拒绝话术直接渲染 */
async function handleSend(attachmentUrls?: string[]) {
  const content = inputText.value?.trim();
  // 输入为空、未就绪、正在接收时禁止发送（有挂起附件时也要求写明意图）
  if (!content || streaming.value || !conversationReady.value) {
    if (!content && pendingFiles.value.length > 0 && !streaming.value) {
      message.warning('请先输入你想对附件做什么，例如：根据图片录入工单');
    }
    return;
  }
  // 图片录入流程传入的附件（@click 直接调用时首参是事件对象，需过滤）
  const urls = Array.isArray(attachmentUrls) ? attachmentUrls : undefined;

  inputText.value = ''; // 发送后清空输入框
  // 【附件】待发送附件统一在此上传：图片 → 后端视觉识别；文档 → 后端抽取文本
  let pendingUrls: string[] | undefined = urls;
  const pendingNames: string[] = [];
  if (pendingFiles.value.length > 0) {
    streaming.value = true; // 上传期间锁定发送按钮
    try {
      for (const item of pendingFiles.value) {
        const up = (await uploadFile({ file: item.file })) as unknown as string;
        if (!up) throw new Error('上传返回为空');
        pendingUrls = pendingUrls ?? [];
        pendingUrls.push(up);
        pendingNames.push(item.file.name);
      }
      pendingFiles.value.forEach((item) => {
        if (item.preview) URL.revokeObjectURL(item.preview);
      });
      pendingFiles.value = [];
    } catch {
      message.error('附件上传失败，请检查网络后重试');
      streaming.value = false;
      return;
    }
  }
  messages.value.push({
    role: 'user',
    content: pendingNames.length > 0 ? `${content}\n📎 附件：${pendingNames.join('、')}` : content,
    time: formatTime(),
  });
  // 带附件的请求统一交给后端处理（视觉识别/文本抽取 + 意图理解），
  // 不走前端的查询澄清/预填/引导等本地意图分支，避免附件请求被劫持
  const hasAttachments = (pendingUrls?.length ?? 0) > 0;

  // 【单据模板分支（已收窄）】仅当用户明确索要"模板/空白单"时才本地生成模板链接；
  // "添加/创建/录入 + 工单" 等带真实字段的请求一律放行到后端——
  // 由 AI 调用录入 Function 工具直接写入 MES 真实数据库（用户要求：直接入库，不要 Word 模板）
  const docSchema = matchDocIntent(content);
  if (!hasAttachments && docSchema && /(模板|空白|样例|示例单)/.test(content)) {
    messages.value.push({
      role: 'assistant',
      content: `已为您生成${docSchema.title}模板，点击下方链接查看：`,
      docKey: docSchema.key,
      time: formatTime(),
    });
    scrollToBottom();
    return;
  }

  // 【新增：模糊工单查询澄清】"我想查看工单"等无具体条件的查询 → 先反问，不直接倾倒全表
  if (!hasAttachments && matchVagueWorkOrderQuery(content)) {
    messages.value.push({
      role: 'assistant',
      content:
        '您想查看什么样的工单呢？可点击下方选项快速筛选，也可以直接补充条件，例如：\n- 查询工单编号 MO_TEST_20260914_01\n- 查询已确认的生产工单\n- 查询产品 PVC 颗粒的工单',
      options: buildDocOptions('生产工单'),
      time: formatTime(),
    });
    scrollToBottom();
    return;
  }

  // 【新增：任务包 D1】安灯异常上报：口述异常 → 草稿确认弹窗（提交走既有接口）
  const andonDraft = matchAndonReport(content);
  if (!hasAttachments && andonDraft) {
    messages.value.push({
      role: 'assistant',
      content: '收到，我帮你起草了安灯呼叫单。请确认异常原因、等级与工位后提交：',
      docConfirm: true,
      andonDraftText: andonDraft,
      time: formatTime(),
    });
    scrollToBottom();
    return;
  }

  // 【新增：任务包 D2】质检预填：命中"质检/检验预填 + 任务编号" → 过程检验单确认流程
  const qcCode = matchQcPrefill(content);
  if (!hasAttachments && qcCode) {
    messages.value.push({
      role: 'assistant',
      content: `✅ 已定位任务「${qcCode}」，即将为您预填过程检验单（检验数量默认为已生产数量，可修改）。确认无误后点击下方按钮：`,
      docConfirm: true,
      qcPrefillTaskCode: qcCode,
      time: formatTime(),
    });
    scrollToBottom();
    return;
  }

  // 【新增：任务包 D3】出入库操作引导（不影响纯数据查询）
  if (!hasAttachments && matchWmsGuide(content)) {
    handleWmsGuide();
    return;
  }

  // 【新增：任务包 B】报工智能填单：命中"报工/填单/预填 + 任务编号" → 预填确认流程
  const prefillCode = matchFeedbackPrefill(content);
  if (!hasAttachments && prefillCode) {
    handleFeedbackPrefillIntent(prefillCode);
    return;
  }

  // 【新增】只说了单据名词（如"生产工单"）：意图不明确，先反问澄清，
  // 不擅自猜测是要查询数据还是生成模板，避免答非所问
  const ambiguousNoun = matchAmbiguousDoc(content);
  if (!hasAttachments && ambiguousNoun) {
    messages.value.push({
      role: 'assistant',
      content: `您提到「${ambiguousNoun}」，想做什么呢？可点击下方选项快速操作，也可以直接补充说明，例如"查询已完成的生产工单"。`,
      options: buildDocOptions(ambiguousNoun),
      time: formatTime(),
    });
    scrollToBottom();
    return;
  }

  // 【新增：导入解析】粘贴的单据文本（含"字段：值"多行/表格标题）→ 解析填充；
  // 疑问句不劫持，继续走后端问答
  if (!hasAttachments && handlePastedDocText(content)) {
    scrollToBottom();
    return;
  }

  const aiMsg = { role: 'assistant' as const, content: '', time: formatTime() };
  messages.value.push(aiMsg); // AI 占位气泡（显示"正在思考"动画）
  streaming.value = true;
  abortController.value = new AbortController();
  scrollToBottom();

  let full = '';
  // 流式结束互斥标记：onmessage 业务错误 / onerror / onclose / catch 可能先后触发，
  // 保证最终文案只被写入一次，避免后续回调把前面的提示覆盖掉
  let ended = false;
  const endStream = (finalContent?: string) => {
    if (ended) return;
    ended = true;
    streaming.value = false;
    abortController.value = undefined;
    if (finalContent) aiMsg.content = finalContent;
    scrollToBottom();
  };
  try {
    await sendChatMessageStream(
      conversationId.value as unknown as number,
      content,
      abortController.value,
      true, // 携带上下文
      false, // 不启用联网搜索
      (res: any) => {
        let payload: any;
        try {
          payload = JSON.parse(res.data);
        } catch {
          return; // 忽略非 JSON 帧
        }
        const { code, data, msg } = payload;
        if (code !== 0) {
          // 后端业务异常（权限不足/对话生成异常/服务发现失败等）：
          // 统一走中文分类翻译，避免网关英文错误（如 "Unable to find instance for ai-server"）直接裸露
          endStream(describeRequestError({ msg }, '请求失败，请稍后再试。'));
          return;
        }
        if (!data.receive?.content && !data.receive?.reasoningContent) return;
        full += data.receive.content || '';
        aiMsg.content = stripThink(full);
      },
      (err: any) => {
        // SSE 连接中断/接口异常：
        // - 已流式输出部分内容：保留内容并追加中断提示，避免用户误以为回答完毕
        // - 尚未收到内容：按错误类型给出友好提示
        if (full) {
          aiMsg.content = `${stripThink(full)}\n\n（回答中断：${describeRequestError(err, '网络连接异常')}，请重新发送问题）`;
        } else {
          aiMsg.content = describeRequestError(err, '网络异常，请检查网络连接后重试。');
        }
        endStream();
      },
      () => {
        // 正常关闭兜底：仅当内容仍为空时补写提示；已有内容（含上面的中断提示）一律保留
        if (aiMsg.content) {
          endStream();
          return;
        }
        const stripped = stripThink(full);
        endStream(
          stripped ||
            (full
              ? `${full.replace(/<\/?think>/g, '').trim()}\n\n（注：以上为模型思考内容，未生成正式回答，请重新提问）`
              : '（无返回内容，请重新提问）'),
        );
      },
      pendingUrls, // 【附件】挂起附件上传后的 URL：图片走视觉识别、文档走文本抽取
    );
  } catch (e: any) {
    // 连接建立阶段即抛错（鉴权失败/网络异常/服务不可用等）
    aiMsg.content =
      aiMsg.content ||
      (full ? stripThink(full) : describeRequestError(e, '发送失败，请稍后重试。'));
    endStream();
  }
  scrollToBottom();
}

/** Enter 发送、Shift+Enter 换行 */
function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    handleSend();
  }
}

/**
 * ==================== 关键格式化处理位置 1：AI 原始文本清洗 ====================
 * 后端大模型依旧输出原始文本，由前端统一清洗后再展示：
 * 去 <think> 思考段、去 markdown ** 星号、剔除内部调试语句行、修复常见语病、压缩换行
 */

/** 内部调试/知识库来源信息行关键词：命中即整行剔除，不展示给用户 */
const INTERNAL_LINE_PATTERNS = [
  '数据来源',
  '知识来源',
  '知识库来源',
  '来源：平台',
  '来源:平台',
  '平台共有知识库',
  '根据检索结果',
  '知识库根据检索',
  '参考内容来自',
  '知识库检索',
  '检索结果来自',
];

/** 清洗 AI 原始文本 */
function cleanAiText(raw: string): string {
  let text = raw || '';
  // 去掉思考过程段与残留 think 标签
  text = text.replace(/<think>[\s\S]*?<\/think>/gi, '');
  text = text.replace(/<\/?think>/gi, '');
  // 去掉 markdown 加粗/代码符号（**、__、`），避免星号直接露出
  text = text.replace(/\*\*|__|`/g, '');
  // 按行剔除内部调试/来源信息行
  text = text
    .split('\n')
    .filter((line) => {
      const t = line.trim();
      if (!t) return true;
      // 过滤"说明："开头的模型注释文本，不展示给用户
      if (t.startsWith('说明：') || t.startsWith('说明:')) return false;
      return !INTERNAL_LINE_PATTERNS.some((p) => t.includes(p));
    })
    .join('\n');
  // 修复模型常见的语病/残缺句式
  text = text.replace(/当前张状态/g, '工单状态');
  text = text.replace(/明细如下[.。：:]*\s*工?[:：]?/g, '明细如下：');
  // 压缩 3 行以上连续换行为 1 个空行
  text = text.replace(/\n{3,}/g, '\n\n');
  return text.trim();
}

/**
 * ==================== 关键格式化处理位置 2：业务数据结构化解析 ====================
 * 识别工单、库存这类结构化业务结果，解析为卡片条目；普通自然问答返回 null 走文本渲染
 */

/** 业务字段别名表：把模型输出的各种叫法归一成标准标签（加粗展示） */
const FIELD_ALIASES: Array<[RegExp, string]> = [
  [/^(工单编号|单号|工单号|编号)$/, '工单编号'],
  [/^(产品名称|产品|物料名称)$/, '产品名称'],
  [/^(物料编码|编码)$/, '物料编码'],
  [/^(规格|规格型号)$/, '规格型号'],
  [/^(计划数量|计划数)$/, '计划数量'],
  [/^(已生产数量|已生产|完工数量)$/, '已生产数量'],
  [/^(合格数量)$/, '合格数量'],
  [/^(不合格数量)$/, '不合格数量'],
  [/^(报工进度|进度)$/, '报工进度'],
  [/^(工单状态|状态)$/, '工单状态'],
  [/^(开工时间|开始时间)$/, '开工时间'],
  [/^(完工时间|结束时间)$/, '完工时间'],
  [/^(工艺|工艺信息|工序)$/, '工序'],
  [/^(客户|客户名称)$/, '客户'],
  [/^(车间|车间名称)$/, '车间'],
  [/^(工位|工位名称)$/, '工位'],
  [/^(库存数量|库存数|现存量|在库数量)$/, '库存数量'],
  [/^(单位)$/, '单位'],
];

/** 解析单行"标签：值"格式的业务字段（容忍序号前缀 1. / 1、/ - / 残留星号） */
function parseFieldLine(line: string): { label: string; value: string } | null {
  const m = line.match(/^\s*(?:[-*•·]|\d+[.、)])?\s*([^：:]{2,12})[:：]\s*(.+?)\s*$/);
  if (!m) return null;
  // 去掉标签中的数字序号前缀（如"1单号"→"单号"）与残留星号
  const rawLabel = m[1]!.replace(/^\d+/, '').replace(/\*/g, '').trim();
  const value = m[2]!.replace(/\*/g, '').replace(/^[-•·]\s*/, '').trim();
  if (!value) return null;
  for (const [re, std] of FIELD_ALIASES) {
    if (re.test(rawLabel)) return { label: std, value };
  }
  return null;
}

/** 内联字段提取正则表：用于"1. 工单号【xxx】：产品【yyy】，计划数量 1.00"这类无换行的内联记录 */
// 【】值支持一层嵌套（如 产品【色粉【黑色】】）
const BRACKET = '((?:[^【】]|【[^】]*】)+)';
const INLINE_FIELDS: Array<[RegExp, string]> = [
  [new RegExp(`工单号【${BRACKET}】`), '工单编号'],
  [new RegExp(`产品【${BRACKET}】`), '产品名称'],
  [new RegExp(`物料【${BRACKET}】`), '物料名称'],
  [/计划数量\s*([\d.]+)/, '计划数量'],
  [/已生产\s*([\d.]+)/, '已生产数量'],
  [/合格\s*([\d.]+)/, '合格数量'],
  [new RegExp(`客户【${BRACKET}】`), '客户'],
  [/要求交期\s*([\d-]+)/, '要求交期'],
  [/完成时间\s*([\d]+[\d: -]{6,})/, '完成时间'],
  [/状态[为【:：]\s*([^\s，。,】]+)/, '工单状态'],
];

/** 解析业务数据为卡片结构（标题 + 多条记录，每条为字段数组）；非业务数据返回 null */
function parseBusinessData(
  raw: string,
): { items: { label: string; value: string }[][]; title: string } | null {
  const text = cleanAiText(raw);
  if (!text) return null;
  const lines = text.split('\n');
  const items: { label: string; value: string }[][] = [];
  let cur: { label: string; value: string }[] = [];
  for (const line of lines) {
    const field = parseFieldLine(line);
    if (field) {
      cur.push(field);
    } else if (cur.length > 0 && (!line.trim() || /^[-─=—_]+$/.test(line.trim()))) {
      // 空行或分隔线：一条记录结束
      items.push(cur);
      cur = [];
    }
  }
  if (cur.length > 0) items.push(cur);
  // 过滤字段过少的碎片记录（至少 2 个字段才算一条有效业务记录）
  let validItems = items.filter((item) => item.length >= 2);
  // 兜底：行级解析失败时，尝试"序号 + 工单号【xxx】"内联多记录格式（模型有时不换行）
  if (validItems.length === 0 && /工单号【/.test(text)) {
    const segs = text.split(/(?=\d+[.、)]?\s*工单号)/);
    for (const seg of segs) {
      if (!/工单号【/.test(seg)) continue;
      const fields: { label: string; value: string }[] = [];
      for (const [re, label] of INLINE_FIELDS) {
        const m = seg.match(re);
        if (m && m[1] && m[1].trim() && m[1].trim() !== '无') {
          fields.push({ label, value: m[1].trim() });
        }
      }
      if (fields.length >= 3) validItems.push(fields);
    }
  }
  if (!validItems.length) return null;
  // 业务卡片触发条件：>= 2 条记录；或单条记录字段 >= 4（避免把普通聊天误判为卡片）
  const isBusiness = validItems.length >= 2 || validItems[0]!.length >= 4;
  if (!isBusiness) return null;

  // 【修复】标题与数据对齐：从解析出的记录里收集"工单状态"集合，
  // 只有全部记录为同一状态时，标题才标注该状态，
  // 避免"标题说已完成、数据里混着已确认/草稿"的错位
  const statusSet = new Set<string>();
  validItems.forEach((item) =>
    item.forEach((f) => {
      if (f.label === '工单状态' && f.value) statusSet.add(f.value.trim());
    }),
  );
  let title = `📋 查询结果（共 ${validItems.length} 条）`;
  if (/工单|单号|生产/.test(raw)) {
    if (statusSet.size === 1 && statusSet.has('已完成')) {
      title = `✅ 已完成生产工单（共 ${validItems.length} 条）`;
    } else if (statusSet.size === 1 && statusSet.has('已取消')) {
      title = `🚫 已取消生产工单（共 ${validItems.length} 条）`;
    } else if (statusSet.size === 1 && statusSet.has('已确认')) {
      title = `🏭 已确认生产工单（共 ${validItems.length} 条）`;
    } else if (statusSet.size === 1 && statusSet.has('草稿')) {
      title = `📝 草稿生产工单（共 ${validItems.length} 条）`;
    } else {
      title = `🏭 生产工单（共 ${validItems.length} 条）`;
    }
  } else if (/库存|仓位|仓库/.test(raw)) {
    title = `📦 库存查询结果（共 ${validItems.length} 条）`;
  } else if (/物料/.test(raw)) {
    title = `📦 物料信息（共 ${validItems.length} 条）`;
  } else if (/设备/.test(raw)) {
    title = `🔧 设备信息（共 ${validItems.length} 条）`;
  }
  return { title, items: validItems };
}

/** 窗口尺寸变化时保持滚动在底部 */
function handleWindowResize() {
  scrollToBottom();
}

watch(
  () => [expanded.value],
  () => {
    document.body.classList.toggle('mes-ai-panel-open', expanded.value);
    if (expanded.value) scrollToBottom();
  },
);

onMounted(() => {
  document.body.classList.toggle('mes-ai-panel-open', expanded.value);
  initConversation();
  window.addEventListener('resize', handleWindowResize);
  // 视口尺寸变化时把悬浮球/面板拉回可视范围
  window.addEventListener('resize', clampAllToViewport);
});

onBeforeUnmount(() => {
  abortController.value?.abort();
  document.body.classList.remove('mes-ai-panel-open');
  window.removeEventListener('resize', handleWindowResize);
  window.removeEventListener('resize', clampAllToViewport);
});
</script>

<template>
  <!-- 收起状态：圆形悬浮按钮（可拖拽移动位置，点击展开） -->
  <Tooltip v-if="!expanded" title="MES 智能助手" placement="left">
    <div
      class="mes-ai-fab"
      :style="fabStyle"
      title="按住拖拽移动位置"
      @mousedown="onDragStart($event, 'fab')"
      @click="onFabClick"
    >
      <IconifyIcon icon="lucide:bot" :size="26" />
    </div>
  </Tooltip>

  <!-- 展开状态：完整对话面板（白底黑字，按住标题栏可拖拽移动） -->
  <transition name="mes-ai-slide">
    <div
      v-show="expanded"
      class="mes-ai-panel"
      :class="{ 'mes-ai-panel--max': maximized }"
      :style="panelStyle"
    >
      <!-- 头部：标题（拖拽把手） + 最大化 / 新会话 / 最小化 -->
      <div
        class="mes-ai-header"
        :class="{ 'mes-ai-header--draggable': !maximized }"
        @mousedown="onDragStart($event, 'panel')"
      >
        <div class="mes-ai-header__title">
          <IconifyIcon icon="lucide:bot" :size="18" class="mes-ai-primary" />
          <span>MES 智能助手</span>
          <span class="mes-ai-header__sub">{{ roleName }}</span>
        </div>
        <div class="mes-ai-header__actions" @mousedown.stop>
          <Tooltip title="最大化/还原" placement="top">
            <span class="mes-ai-icon-btn" @click="toggleMaximized">
              <IconifyIcon
                :icon="maximized ? 'lucide:minimize' : 'lucide:maximize'"
                :size="14"
              />
            </span>
          </Tooltip>
          <Tooltip title="重新开始会话" placement="top">
            <span class="mes-ai-icon-btn" @click="initConversation">
              <IconifyIcon icon="lucide:rotate-ccw" :size="14" />
            </span>
          </Tooltip>
          <Tooltip title="最小化" placement="top">
            <span class="mes-ai-icon-btn" @click="setExpanded(false)">
              <IconifyIcon icon="lucide:minimize-2" :size="14" />
            </span>
          </Tooltip>
        </div>
      </div>

      <!-- 消息区：占面板剩余高度，内部滚动，不带动整页滚动 -->
      <div ref="messageListRef" class="mes-ai-messages">
        <div
          v-for="(msg, index) in messages"
          :key="index"
          class="mes-ai-row"
          :class="msg.role === 'user' ? 'is-user' : 'is-assistant'"
        >
          <!-- AI 头像 -->
          <div v-if="msg.role === 'assistant'" class="mes-ai-avatar">
            <IconifyIcon icon="lucide:bot" :size="16" />
          </div>
          <div class="mes-ai-bubble-wrap">
            <div class="mes-ai-bubble">
              <!-- AI 回复（关键格式化处理位置 3）：业务数据渲染成卡片，普通问答走清洗后的文本 -->
              <template v-if="msg.role === 'assistant' && msg.content">
                <div
                  v-if="parseBusinessData(msg.content)"
                  class="mes-ai-card"
                >
                  <!-- 卡片标题 -->
                  <div class="mes-ai-card__title">
                    {{ parseBusinessData(msg.content)?.title }}
                  </div>
                  <!-- 卡片条目列表：每条业务记录一组字段 -->
                  <div class="mes-ai-card__list">
                    <div
                      v-for="(item, ci) in parseBusinessData(msg.content)?.items"
                      :key="ci"
                      class="mes-ai-card__item"
                    >
                      <div
                        v-for="(fv, fi) in item"
                        :key="fi"
                        class="mes-ai-card__field"
                      >
                        <span class="mes-ai-card__label">{{ fv.label }}：</span>
                        <span class="mes-ai-card__value">{{ fv.value }}</span>
                      </div>
                    </div>
                  </div>
                </div>
                <!-- 普通自然问答：清洗后的 Markdown 文本渲染 -->
                <MarkdownView
                  v-else
                  class="mes-ai-md"
                  :content="cleanAiText(msg.content)"
                />
              </template>
              <span v-else-if="msg.content">{{ msg.content }}</span>
              <!-- 请求 AI 时的"正在思考"加载动画 -->
              <span v-else class="mes-ai-typing">
                <i></i><i></i><i></i>
                <em>正在思考</em>
              </span>

              <!-- 【新增模块 1】单据链接：命中单据生成指令的消息额外渲染可点击链接 -->
              <div v-if="msg.docKey" class="mes-ai-doc">
                <MesAiDocLink
                  :text="getBlankSchema(msg.docKey).linkText"
                  @open="openDoc(msg.docKey)"
                />
              </div>

              <!-- 【新增：导入解析】填充确认按钮：确认无误后打开带数据的预览弹窗 -->
              <div v-if="msg.docConfirm && msg.docKey" class="mes-ai-doc">
                <Button
                  size="small"
                  type="primary"
                  class="mes-ai-doc-confirm"
                  @click="openDocFilled(msg.docKey, msg.docState)"
                >
                  确认无误，打开预览
                </Button>
              </div>

              <!-- 【新增：任务包 B】报工预填确认按钮 -->
              <div v-if="msg.prefillTaskCode" class="mes-ai-doc">
                <Button
                  size="small"
                  type="primary"
                  class="mes-ai-doc-confirm"
                  @click="feedbackModalRef?.open(msg.prefillTaskCode)"
                >
                  确认无误，去报工
                </Button>
              </div>

              <!-- 【新增：任务包 D1】安灯上报确认按钮 -->
              <div v-if="msg.andonDraftText" class="mes-ai-doc">
                <Button
                  size="small"
                  type="primary"
                  class="mes-ai-doc-confirm"
                  @click="andonModalRef?.open(msg.andonDraftText)"
                >
                  填写安灯上报单
                </Button>
              </div>

              <!-- 【新增：任务包 D2】质检预填确认按钮 -->
              <div v-if="msg.qcPrefillTaskCode" class="mes-ai-doc">
                <Button
                  size="small"
                  type="primary"
                  class="mes-ai-doc-confirm"
                  @click="qcModalRef?.open(msg.qcPrefillTaskCode)"
                >
                  确认无误，去检验
                </Button>
              </div>

              <!-- 【新增】澄清选项：只说单据名词时提供快捷选择 -->
              <div v-if="msg.options?.length" class="mes-ai-options">
                <span
                  v-for="opt in msg.options"
                  :key="opt"
                  class="mes-ai-option"
                  @click="sendOption(opt)"
                >
                  {{ opt }}
                </span>
              </div>
            </div>
            <!-- 消息时间戳轻量展示 -->
            <div class="mes-ai-time">{{ msg.time }}</div>
          </div>
        </div>
      </div>

      <!-- 输入区：白色圆角输入卡片 + 底部操作行 -->
      <div class="mes-ai-input">
        <!-- 【交互改造】待发送附件区：📎 选择后先挂这里，用户输入意图后发送时才上传处理 -->
        <div v-if="pendingFiles.length > 0" class="mes-ai-pending">
          <div class="mes-ai-pending__files">
            <span
              v-for="(item, idx) in pendingFiles"
              :key="`${item.file.name}-${idx}`"
              class="mes-ai-pending__chip"
            >
              <img
                v-if="item.preview"
                :src="item.preview"
                alt=""
                class="mes-ai-pending__thumb"
              />
              <IconifyIcon
                v-else
                icon="lucide:file-text"
                :size="14"
                class="mes-ai-pending__icon"
              />
              <span class="mes-ai-pending__name" :title="item.file.name">
                {{ item.file.name }}
              </span>
              <span
                class="mes-ai-pending__remove"
                title="移除"
                @click="removePendingFile(idx)"
              >×</span>
            </span>
          </div>
          <!-- 快捷意图：点击填入输入框，用户可修改后发送 -->
          <div class="mes-ai-pending__intents">
            <template v-if="pendingFiles.some((f) => f.kind === 'image')">
              <Button
                size="small"
                class="mes-ai-pending__intent"
                @click="applyQuickIntent('根据图片中的单据信息，提取字段并录入对应的业务单据')"
              >根据图片录入单据 →</Button>
              <Button
                size="small"
                class="mes-ai-pending__intent"
                @click="applyQuickIntent('识别图片中的字段内容')"
              >识别图片字段 →</Button>
            </template>
            <template v-if="pendingFiles.some((f) => f.kind === 'doc')">
              <Button
                size="small"
                class="mes-ai-pending__intent"
                @click="applyQuickIntent('根据文档内容提取单据字段并录入 MES 系统')"
              >根据文档录入单据 →</Button>
              <Button
                size="small"
                class="mes-ai-pending__intent"
                @click="applyQuickIntent('详细总结文档内容')"
              >总结文档内容 →</Button>
            </template>
          </div>
        </div>
        <TextArea
          v-model:value="inputText"
          :auto-size="{ minRows: 2, maxRows: 6 }"
          class="mes-ai-textarea"
          placeholder="发消息，Enter 发送，Shift+Enter 换行"
          @keydown="handleKeydown"
        />
        <div class="mes-ai-input__bar">
          <div class="mes-ai-input__left">
            <!-- 【新增：导入解析】附件按钮：上传 Word/Excel，自动识别单据并填充 -->
            <span
              class="mes-ai-icon-btn mes-ai-attach"
              title="上传图片 / Word / Excel：先添加到输入框上方，输入你的意图后发送"
              @click="fileInputRef?.click()"
            >
              <IconifyIcon icon="lucide:paperclip" :size="14" />
            </span>
            <span class="mes-ai-input__hint">Enter 发送 · Shift+Enter 换行</span>
          </div>
          <!-- [修改点1] 按钮文本与标签同级书写，不再产生首尾空白字符 -->
          <Button
            type="primary"
            class="mes-ai-send"
            :loading="streaming"
            :disabled="!inputText?.trim() || !conversationReady"
            @click="handleSend()"
          >发送</Button>
        </div>
        <!-- 【新增：导入解析】隐藏的文件选择框（Word/Excel/Txt + 图片：图片走视觉识别录入） -->
        <input
          ref="fileInputRef"
          accept=".docx,.xlsx,.xls,.txt,.png,.jpg,.jpeg,.webp,.bmp"
          class="mes-ai-file-input"
          multiple
          type="file"
          @change="handleImportFile"
        />
      </div>

      <!-- 【新增模块 2】空白单据预览弹窗（Vben Modal：右上角关闭 ×，底部 关闭 / 下载 Word 文件） -->
      <MesDocPreviewModal ref="docPreviewRef" />

      <!-- 【新增：任务包 B】报工智能填单弹窗（预填 + 确认提交，走既有 MES 接口） -->
      <MesFeedbackPrefillModal
        ref="feedbackModalRef"
        @submitted="handleFeedbackSubmitted"
      />

      <!-- 【新增：任务包 D1】安灯异常上报弹窗（草稿确认 + 既有创建接口） -->
      <MesAndonDraftModal ref="andonModalRef" @submitted="handleAndonSubmitted" />

      <!-- 【新增：任务包 D2】过程检验预填弹窗（预填 + 既有创建接口） -->
      <MesQcPrefillModal ref="qcModalRef" @submitted="handleQcSubmitted" />
    </div>
  </transition>
</template>

<style scoped>
/* ========== 面板：固定白底黑字（不随主题变化，保证清晰可读） ========== */
.mes-ai-panel {
  position: fixed;
  z-index: 1200;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  color: #1f2937;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  box-shadow: 0 6px 24px rgb(0 0 0 / 22%);
}

/* ========== 最大化状态专属美化 ========== */
/* 更深的悬浮阴影 + 更大圆角，与页面内容形成清晰层次 */
.mes-ai-panel--max {
  border-radius: 14px;
  box-shadow:
    0 24px 64px rgb(15 23 42 / 28%),
    0 2px 8px rgb(15 23 42 / 10%);
}

/* 标题栏加高加重，拖拽把手更明显 */
.mes-ai-panel--max .mes-ai-header {
  padding: 14px 20px;
}

.mes-ai-panel--max .mes-ai-header__title {
  font-size: 15px;
  font-weight: 600;
}

/* 消息区：大空间下的呼吸感 */
.mes-ai-panel--max .mes-ai-messages {
  padding: 18px 22px;
}

/* 气泡放宽（大屏下长回答不挤） */
.mes-ai-panel--max .mes-ai-bubble {
  max-width: 86%;
  padding: 10px 14px;
}

/* 正文与回答字号微增 */
.mes-ai-panel--max .mes-ai-bubble,
.mes-ai-panel--max .mes-ai-md {
  font-size: 13.5px;
}

/* 输入区加宽内边距，与消息区对齐 */
.mes-ai-panel--max .mes-ai-input {
  padding: 14px 20px 10px;
}

.mes-ai-panel--max .mes-ai-textarea {
  min-height: 52px;
}

/* 快捷选项 chips 稍大 */
.mes-ai-panel--max .mes-ai-option {
  padding: 5px 14px;
  font-size: 13px;
  border-radius: 14px;
}

/* ========== 头部 ========== */
.mes-ai-header {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: #ffffff;
  border-bottom: 1px solid #e5e7eb;
}

/* 非最大化时标题栏作为拖拽把手 */
.mes-ai-header--draggable {
  cursor: move;
  user-select: none;
}

.mes-ai-header__title {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}

.mes-ai-header__sub {
  font-size: 12px;
  font-weight: 400;
  color: #6b7280;
}

.mes-ai-header__actions {
  display: flex;
  gap: 8px;
}

.mes-ai-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  color: #4b5563;
  cursor: pointer;
  background: #f3f4f6;
  border-radius: 6px;
  transition: all 0.2s;
}

.mes-ai-icon-btn:hover {
  color: #1677ff;
  background: #e6f0ff;
}

.mes-ai-primary {
  color: #1677ff;
}

/* ========== 消息区：占面板剩余高度，浅灰底衬托白色气泡，内部滚动 ========== */
.mes-ai-messages {
  flex: 1 1 auto;
  min-height: 0;
  padding: 12px;
  overflow-y: auto; /* 溢出只做内部滚动，不带动整页 */
  background: #f9fafb;
}

.mes-ai-row {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}

.mes-ai-row.is-user {
  flex-direction: row-reverse; /* 用户提问气泡居右 */
}

.mes-ai-avatar {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: #ffffff;
  background: #1677ff;
  border-radius: 50%;
}

.mes-ai-bubble-wrap {
  display: flex;
  flex-direction: column;
  max-width: calc(100% - 40px);
}

.mes-ai-row.is-user .mes-ai-bubble-wrap {
  align-items: flex-end;
}

.mes-ai-bubble {
  padding: 8px 12px;
  font-size: 13px;
  line-height: 1.7;
  word-break: break-all; /* 大段业务文本自动换行，防止溢出 */
  white-space: pre-wrap;
  border-radius: 10px;
}

.is-assistant .mes-ai-bubble {
  color: #1f2937;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-top-left-radius: 2px;
}

.is-user .mes-ai-bubble {
  color: #ffffff;
  background: #1677ff;
  border-top-right-radius: 2px;
}

/* ========== 字体统一（关键修改位置）==========
 * AI 气泡（MarkdownView 渲染）与用户气泡字号/行高完全一致：
 * 用户气泡字号在 .mes-ai-bubble 的 13px / 1.7，这里强制 AI 渲染树继承同样的值，
 * 不对 AI 气波单独放大/缩小；字号为固定 px，深浅两套主题下均保持一致 */
.mes-ai-md {
  font-size: 13px; /* 与 .mes-ai-bubble 的 font-size 完全一致 */
  line-height: 1.7; /* 与 .mes-ai-bubble 的 line-height 完全一致 */
}

/* 【新增模块 1 配套样式】单据链接容器：与上方正文用虚线分隔，不撑破气泡 */
.mes-ai-doc {
  padding-top: 6px;
  margin-top: 6px;
  border-top: 1px dashed #e5e7eb;
}

/* 【新增】澄清选项：可点击的快捷选项 chips */
.mes-ai-options {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding-top: 6px;
  margin-top: 6px;
  border-top: 1px dashed #e5e7eb;
}

.mes-ai-option {
  padding: 3px 10px;
  font-size: 12.5px;
  color: #1677ff;
  cursor: pointer;
  background: #f0f7ff;
  border: 1px solid #91caff;
  border-radius: 12px;
  transition: all 0.15s;
}

.mes-ai-option:hover {
  color: #fff;
  background: #1677ff;
  border-color: #1677ff;
}

/* 【新增：导入解析】输入区左侧（附件按钮 + 快捷键提示） */
.mes-ai-input__left {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.mes-ai-attach {
  color: #4b5563;
}

/* 隐藏原生文件选择框 */
.mes-ai-file-input {
  display: none;
}

/* Markdown 内部所有文本元素强制继承统一字号与行高，防止组件默认样式放大字体 */
.mes-ai-md :deep(p),
.mes-ai-md :deep(ul),
.mes-ai-md :deep(ol),
.mes-ai-md :deep(li),
.mes-ai-md :deep(span),
.mes-ai-md :deep(div),
.mes-ai-md :deep(h1),
.mes-ai-md :deep(h2),
.mes-ai-md :deep(h3),
.mes-ai-md :deep(h4),
.mes-ai-md :deep(strong) {
  font-size: inherit;
  line-height: inherit;
}

/* ========== AI 回复 Markdown 排版美化（紧凑、清晰） ========== */
/* 段落：小间距，避免大面积留白 */
.mes-ai-md :deep(p) {
  margin: 0 0 6px;
  color: #1f2937;
}

.mes-ai-md :deep(p:last-child) {
  margin-bottom: 0;
}

/* 列表：序号与内容紧凑同行，条目间距小 */
.mes-ai-md :deep(ul),
.mes-ai-md :deep(ol) {
  padding-left: 20px;
  margin: 2px 0 8px;
  color: #1f2937;
}

.mes-ai-md :deep(li) {
  padding: 1px 0;
  margin: 0;
}

.mes-ai-md :deep(li:not(:last-child)) {
  margin-bottom: 3px;
}

/* 关键修复：li 内嵌段落不再另起一行与大间距，与序号保持同行紧凑排版 */
.mes-ai-md :deep(li > p) {
  display: inline;
  margin: 0;
}

/* 加粗文字：深色加粗突出 */
.mes-ai-md :deep(strong) {
  font-weight: 600;
  color: #111827;
}

/* 标题：小尺寸加粗 + 上下小间距（气泡内不需要大标题） */
.mes-ai-md :deep(h1),
.mes-ai-md :deep(h2),
.mes-ai-md :deep(h3),
.mes-ai-md :deep(h4) {
  font-weight: 600;
  color: #111827;
  margin: 8px 0 4px;
}

.mes-ai-md :deep(h1:first-child),
.mes-ai-md :deep(h2:first-child),
.mes-ai-md :deep(h3:first-child),
.mes-ai-md :deep(h4:first-child) {
  margin-top: 0;
}

/* 行内代码 */
.mes-ai-md :deep(code) {
  padding: 1px 4px;
  font-size: 12px;
  color: #1f2937;
  background: #f3f4f6;
  border-radius: 4px;
}

/* 代码块 */
.mes-ai-md :deep(pre) {
  padding: 8px 10px;
  margin: 4px 0 8px;
  overflow-x: auto;
  font-size: 12px;
  line-height: 1.5;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.mes-ai-md :deep(pre code) {
  padding: 0;
  background: transparent;
}

/* 引用块：左侧蓝色竖线 */
.mes-ai-md :deep(blockquote) {
  padding: 4px 10px;
  margin: 4px 0 8px;
  color: #4b5563;
  background: #f8fafc;
  border-left: 3px solid #1677ff;
  border-radius: 0 6px 6px 0;
}

.mes-ai-md :deep(blockquote p) {
  margin: 0;
}

/* 表格：细边框 + 表头浅底 + 斑马纹（库存/工单等结构化数据更易读） */
.mes-ai-md :deep(table) {
  width: 100%;
  margin: 4px 0 8px;
  font-size: 12.5px;
  border-collapse: collapse;
}

.mes-ai-md :deep(th),
.mes-ai-md :deep(td) {
  padding: 4px 8px;
  text-align: left;
  border: 1px solid #e5e7eb;
}

.mes-ai-md :deep(th) {
  font-weight: 600;
  color: #374151;
  background: #f3f4f6;
}

.mes-ai-md :deep(tr:nth-child(2n) td) {
  background: #fafafa;
}

/* 分隔线 */
.mes-ai-md :deep(hr) {
  margin: 8px 0;
  border: none;
  border-top: 1px dashed #e5e7eb;
}

/* 链接 */
.mes-ai-md :deep(a) {
  color: #1677ff;
  text-decoration: none;
}

.mes-ai-md :deep(a:hover) {
  text-decoration: underline;
}

/* "正在思考"加载动画 */
.mes-ai-typing {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  height: 16px;
}

.mes-ai-typing i {
  width: 6px;
  height: 6px;
  background: #9ca3af;
  border-radius: 50%;
  animation: mes-ai-blink 1.2s infinite ease-in-out;
}

.mes-ai-typing i:nth-child(2) {
  animation-delay: 0.2s;
}

.mes-ai-typing i:nth-child(3) {
  animation-delay: 0.4s;
}

.mes-ai-typing em {
  margin-left: 4px;
  font-size: 12px;
  font-style: normal;
  color: #6b7280;
}

@keyframes mes-ai-blink {
  0%,
  80%,
  100% {
    opacity: 0.3;
    transform: scale(0.8);
  }

  40% {
    opacity: 1;
    transform: scale(1);
  }
}

.mes-ai-time {
  margin-top: 4px;
  font-size: 11px;
  color: #9ca3af;
}

/* ========== 输入区：白色圆角输入卡片 + 底部操作行（固定高度不挤压消息区） ========== */
.mes-ai-input {
  flex-shrink: 0;
  padding: 10px 12px 8px;
  background: #ffffff;
  border-top: 1px solid #e5e7eb;
}

/* ========== 待发送附件区：chip + 快捷意图（挂在输入框上方） ========== */
.mes-ai-pending {
  margin-bottom: 8px;
  padding: 8px;
  background: #f9fafb;
  border: 1px dashed #d1d5db;
  border-radius: 10px;
}

.mes-ai-pending__files {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.mes-ai-pending__chip {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  padding: 2px 6px;
  font-size: 12px;
  color: #1f2937;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.mes-ai-pending__thumb {
  width: 22px;
  height: 22px;
  margin-right: 4px;
  object-fit: cover;
  border-radius: 4px;
}

.mes-ai-pending__icon {
  flex-shrink: 0;
  margin-right: 4px;
  color: #6b7280;
}

.mes-ai-pending__name {
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mes-ai-pending__remove {
  padding: 0 4px;
  margin-left: 2px;
  font-size: 14px;
  line-height: 1;
  color: #9ca3af;
  cursor: pointer;
}

.mes-ai-pending__remove:hover {
  color: #ef4444;
}

.mes-ai-pending__intents {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}

.mes-ai-pending__intent {
  font-size: 12px;
  color: #1677ff;
  background: #ffffff;
  border-color: #bfdbfe;
  border-radius: 8px;
}

/* 输入卡片：白底圆角，文字黑色，聚焦高亮蓝边 */
.mes-ai-textarea {
  font-size: 13px;
  color: #1f2937;
  background: #ffffff;
  border: 1px solid #d1d5db;
  border-radius: 10px;
  transition: border-color 0.2s;
}

.mes-ai-textarea:focus,
.mes-ai-textarea:focus-within {
  border-color: #1677ff;
}

/* 底部操作行：左侧快捷键提示 + 右侧发送按钮 */
.mes-ai-input__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.mes-ai-input__hint {
  font-size: 11px;
  color: #9ca3af;
}

/* [修改点2] 发送按钮：去除写死宽度与多余留白，尺寸贴合文字、内容严格居中 */
.mes-ai-send {
  display: inline-flex;
  flex: none; /* 不被底部操作行拉伸 */
  align-items: center;
  justify-content: center;
  width: auto; /* 宽度由文字 + 内边距决定 */
  min-width: 56px; /* 仅保留最小可点击宽度，不再是固定的 84px */
  height: 28px;
  padding: 0 12px; /* 收紧左右内边距 */
  font-size: 13px;
  line-height: 1; /* 去掉行高造成的上下留白 */
  white-space: nowrap; /* 文字不换行 */
  border-radius: 6px;
}

/* [修改点3] 按钮内部文本节点：清掉首尾空白、字间距归零，保证视觉居中 */
.mes-ai-send :deep(span) {
  display: inline-flex;
  align-items: center;
  margin: 0;
  letter-spacing: normal;
  white-space: nowrap;
}

/* loading 态：图标与文字共用同一套内边距，不因空格产生偏移 */
.mes-ai-send.ant-btn-loading {
  padding-inline: 12px;
}

/* ========== 收起状态：右下角圆形悬浮按钮 ========== */
.mes-ai-fab {
  position: fixed;
  z-index: 1200;
  user-select: none;
  touch-action: none;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  color: #ffffff;
  cursor: pointer;
  background: #1677ff;
  border-radius: 50%;
  box-shadow: 0 4px 12px rgb(22 119 255 / 40%);
  transition: transform 0.2s;
}

.mes-ai-fab:hover {
  transform: scale(1.08);
}

/* ========== 展开/收起过渡 ========== */
.mes-ai-slide-enter-active,
.mes-ai-slide-leave-active {
  transition:
    opacity 0.25s ease,
    transform 0.25s ease;
}

.mes-ai-slide-enter-from,
.mes-ai-slide-leave-to {
  opacity: 0;
  transform: translateY(16px);
}

/* ========== 业务数据卡片（气泡内轻量卡片，白底面板下浅灰蓝底，深浅主题均清晰） ========== */
.mes-ai-card {
  width: 100%; /* 完全包裹在气泡内部，不溢出聊天框 */
  max-height: 40vh; /* 长内容卡片内部滚动 */
  padding: 10px 12px;
  overflow-y: auto;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 8px; /* 细微圆角 */
}

.mes-ai-card__title {
  padding-bottom: 6px;
  margin-bottom: 2px;
  font-size: 13px;
  font-weight: 600;
  color: #111827;
  border-bottom: 1px solid #e5e7eb;
}

.mes-ai-card__item {
  padding: 7px 0;
  border-bottom: 1px dashed #e5e7eb; /* 柔和分隔线 */
}

.mes-ai-card__item:last-child {
  padding-bottom: 0;
  border-bottom: none;
}

.mes-ai-card__field {
  display: flex;
  font-size: 13px;
  line-height: 1.9;
}

.mes-ai-card__label {
  flex-shrink: 0;
  font-weight: 600; /* 标签加粗区分 */
  color: #374151;
}

.mes-ai-card__value {
  color: #1f2937;
  word-break: break-all; /* 长编号/名称自动换行不溢出 */
}

/* 卡片内部滚动条美化 */
.mes-ai-card::-webkit-scrollbar {
  width: 6px;
}

.mes-ai-card::-webkit-scrollbar-thumb {
  background: #d1d5db;
  border-radius: 3px;
}

.mes-ai-card::-webkit-scrollbar-track {
  background: transparent;
}

/* ========== 小屏适配 ========== */
@media (max-width: 1100px) {
  .mes-ai-panel {
    width: min(380px, calc(100vw - 32px));
  }
}
</style>

<style>
/* ========== 非 scoped：页面主体避让样式（作用于 body 与 MES 首页 Page 容器） ========== */

/* 面板展开时，标记了 mes-ai-avoid 的页面主体容器向左避让，不被面板遮挡 */
body.mes-ai-panel-open .mes-ai-avoid {
  padding-right: 404px; /* 380 面板宽 + 16 右侧间距 + 8 缓冲 */
  transition: padding-right 0.3s ease;
}

/* 小屏不避让，面板悬浮覆盖 */
@media (max-width: 1100px) {
  body.mes-ai-panel-open .mes-ai-avoid {
    padding-right: 16px;
  }
}

/* antd 多行输入框内部样式（白底黑字、聚焦蓝边） */
.mes-ai-textarea.ant-input {
  color: #1f2937;
  background: #ffffff;
  border-color: #d1d5db;
}

.mes-ai-textarea.ant-input:focus,
.mes-ai-textarea.ant-input:hover {
  border-color: #1677ff;
}
</style>
