<script lang="ts" setup>
/**
 * MES 工业级 AI 助手（右下角悬浮入口 + 浮动对话面板，跟随主题深浅色）
 *
 * 视觉：核心视觉为 src/assets/imgs/ai-assistant.png（机器人图标，未重绘）；
 *      蓝色玻璃质感底盘 + 克制光晕，禁止霓虹/粒子/大幅旋转等游戏化效果。
 * 1. 悬浮球：固定 right 24 / bottom 24（≤1024 缩为 48px，≤768 缩为 46px 且靠边 16px），
 *    不套大面积纯色圆底，图标本身即视觉主体；hover 轻微放大 + 上浮；
 *    思考中仅外圈缓慢呼吸（不旋转图标）。
 * 2. 面板：右下角浮动卡片 380×520（bottom 88 紧贴悬浮球上方），不挤压页面、不做全屏。
 * 3. 顶栏：机器人头像 + 「AI 智能助手」/「MES 生产运营智能分析」 + 历史 / 重开 / 最小化 / 关闭。
 * 4. 首屏为欢迎区与 4 个 MES 快捷分析入口；输入区含常用指令快捷条、附件、语音。
 * 5. 对接现有 AI 流式接口：权限拦截在后端，越权返回拒绝话术直接渲染；Enter 发送 / Shift+Enter 换行。
 */
import type { AiChatMessageApi } from '#/api/ai/chat/message';

import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Button, Input, Tooltip, message } from 'ant-design-vue';

import {
  createChatConversationMy,
  deleteChatConversationMy,
  getChatConversationMyList,
} from '#/api/ai/chat/conversation';
import {
  getChatMessageListByConversationId,
  sendChatMessageStream,
} from '#/api/ai/chat/message';
import { getMyPage } from '#/api/ai/model/chatRole';
import { uploadFile } from '#/api/infra/file';
// AI 助手核心视觉资源（由参考图处理生成：透明底 + 玻璃蓝底 + 原始机器人造型，未重绘）
import assistantIcon from '#/assets/imgs/ai-assistant.png';
import { MarkdownView } from '#/components/markdown-view';
// 错误分类提示与思考标签剥离：与「AI首页/门户首页」助手共用（src/utils/ai-chat.ts）
import { describeRequestError, stripThink } from '#/utils/ai-chat';

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
    /** AI 角色名称（用于自动查找角色编号） */
    roleName?: string;
  }>(),
  {
    fallbackRoleId: 20, // 兜底：MES 生产助手角色编号
    roleName: 'MES 生产助手',
  },
);

/** 面板状态：默认收起为悬浮球（进入页面不自动弹出，点击悬浮球才展开） */
const expanded = ref(false);
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
    /** 模型的思考过程（气泡内折叠展示，正文仍只展示清洗后的回答） */
    think?: string;
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

/** 输入框实例（快捷意图填入后聚焦用） */
const intentInputRef = ref();

/** 快捷意图：点击后填入输入框（自动聚焦、光标到末尾），用户可修改后发送 */
function applyQuickIntent(text: string) {
  inputText.value = text;
  nextTick(() => {
    // antd TextArea 组件实例 → 取内部原生 textarea 聚焦并置光标于文末
    const inst = intentInputRef.value;
    const el =
      inst?.resizableTextArea?.textArea ??
      inst?.$el?.querySelector?.('textarea');
    if (el) {
      el.focus();
      const len = inputText.value.length;
      el.setSelectionRange(len, len);
    }
  });
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

/** ========== 悬浮球 ==========
 * 固定在右下角（right 24 / bottom 24）：位置完全交给 CSS，因此天然避开浏览器滚动条；
 * 不再支持拖拽 —— 工业后台要求入口位置稳定，避免被拖到遮挡内容的位置。
 * 尺寸变化由 CSS 媒体查询处理（≤1024px 缩到 48px，≤768px 缩到 46px 并靠近边缘）。 */
/** 尺寸/位置均由 CSS 控制：52px（right 24 / bottom 24），≤1024 为 48px，≤768 为 46px */

/** 时间戳轻量展示（HH:mm） */
function formatTime(): string {
  const d = new Date();
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

/** 滚动消息区到底部 */
async function scrollToBottom() {
  await nextTick();
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight;
  }
}

/** 展开 / 收起（收起为右下角悬浮球）：面板是浮动卡片，完全不影响页面布局 */
function setExpanded(v: boolean) {
  expanded.value = v;
  if (v) scrollToBottom();
}

/**
 * 关闭面板：中断正在进行的流式输出后收起为悬浮球。
 * 与「最小化」的区别：最小化只收起、保留当前回答继续输出；关闭会停止本次生成。
 */
function closePanel() {
  abortController.value?.abort();
  abortController.value = undefined;
  streaming.value = false;
  setExpanded(false);
}

/** 初始化进行中标记：防止并发初始化导致出现两段欢迎语 */
const initLoading = ref(false);

/**
 * 解析当前租户下的 AI 角色编号。
 * 角色 id 是按租户分配的（例：「MES 生产助手」在租户 2010 为 20、租户 1 为 1000020），
 * 硬编码的 fallbackRoleId 只在个别租户成立，故改为按名称动态查找：
 *   1) 先查公开角色（publicStatus: true）——「MES 生产助手」是全租户共用的公开角色；
 *   2) 再查当前用户的私有角色（publicStatus: false）；
 *   3) 都未命中时退回 props.fallbackRoleId，兼容历史数据。
 */
async function resolveChatRoleId(): Promise<number> {
  for (const publicStatus of [true, false]) {
    try {
      const page = await getMyPage({
        name: props.roleName,
        pageNo: 1,
        pageSize: 10,
        publicStatus,
      });
      const list = page?.list || [];
      const hit =
        list.find((item: any) => item.name === props.roleName) || list[0];
      if (hit?.id) return hit.id;
    } catch {
      // 单次查询失败不阻断：继续尝试下一种查询范围
    }
  }
  if (props.fallbackRoleId) return props.fallbackRoleId;
  throw new Error('未找到 AI 角色配置');
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
      // 旧会话 id 同样是 19 位雪花号：原样透传，避免 Number() 丢精度导致删不掉
      await deleteChatConversationMy(oldId);
    } catch {
      // 旧会话可能已不存在或已被删除，忽略
    }
  }
  try {
    const roleId = await resolveChatRoleId();
    const conv = await createChatConversationMy({ roleId } as any);
    conversationId.value = String(conv ?? '');
    conversationReady.value = true;
    // 会话初始化成功：不再推文字欢迎语，改由消息区的「空态能力卡」承载引导
    // （能力卡含角色介绍 + 可点击的示例问题，比一句纯文本更好用；有消息后自动消失）
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

// ==================== 【历史会话记录】 ====================
const historyOpen = ref(false); // 历史列表展开状态（覆盖消息区显示）
const historyLoading = ref(false);
const historyList = ref<any[]>([]);

/** 时间值 → 毫秒：兼容「毫秒时间戳（数字/数字字符串）」与「已格式化时间字符串」两种返回 */
function toMillis(v: any): number {
  if (v === null || v === undefined || v === '') return 0;
  const n =
    typeof v === 'number'
      ? v
      : /^\d{10,13}$/.test(String(v))
        ? Number(v)
        : Number.NaN;
  if (Number.isFinite(n)) return n < 1e12 ? n * 1000 : n;
  // 兼容 iOS Safari：需把 "2026-09-22 10:32:14" 的 - 换成 /
  const t = new Date(String(v).replace(/-/g, '/')).getTime();
  return Number.isNaN(t) ? 0 : t;
}

/** 历史会话时间展示：今天只显示 HH:mm，今年显示 MM-DD HH:mm，跨年带年份 */
function formatHistoryTime(v: any): string {
  const t = toMillis(v);
  if (!t) return '';
  const d = new Date(t);
  const pad = (n: number) => String(n).padStart(2, '0');
  const now = new Date();
  const hm = `${pad(d.getHours())}:${pad(d.getMinutes())}`;
  if (
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate()
  ) {
    return hm;
  }
  const md = `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${hm}`;
  return d.getFullYear() === now.getFullYear() ? md : `${d.getFullYear()}-${md}`;
}

/** 拉取本助手角色的历史会话（仅本角色，按时间倒序） */
async function loadHistoryList() {
  historyLoading.value = true;
  try {
    const list = await getChatConversationMyList();
    historyList.value = (list || [])
      .filter((c: any) => c.roleId && c.id)
      .sort(
        (a: any, b: any) =>
          toMillis(b.updateTime || b.createTime) -
          toMillis(a.updateTime || a.createTime),
      );
  } catch {
    message.error('历史会话加载失败，请稍后重试');
  } finally {
    historyLoading.value = false;
  }
}

/** 打开/关闭历史会话列表（打开时拉取本角色全部历史会话） */
async function toggleHistory() {
  historyOpen.value = !historyOpen.value;
  if (historyOpen.value) await loadHistoryList();
}

/** 切换到历史会话：中断旧流 → 加载该会话全部消息 → 回显 */
async function switchConversation(item: any) {
  if (streaming.value) {
    abortController.value?.abort();
    abortController.value = undefined;
    streaming.value = false;
  }
  historyOpen.value = false;
  historyLoading.value = true;
  try {
    const list = await getChatMessageListByConversationId(item.id);
    const mapped = (list || []).map((m: any) => ({
      role: m.type === 'user' ? 'user' : 'assistant',
      content: m.content || '',
      time: m.createTime ? String(m.createTime).slice(0, 16) : formatTime(),
    }));
    messages.value = mapped;
    conversationId.value = String(item.id);
    conversationReady.value = true;
    scrollToBottom();
  } catch {
    message.error('历史消息加载失败，请稍后重试');
  } finally {
    historyLoading.value = false;
  }
}

/** 删除历史会话；若删除的是当前会话则重新初始化 */
async function removeHistory(item: any) {
  try {
    // 注意：对话 id 是 19 位雪花号，必须原样透传；Number() 会丢末位精度，
    // 后端按错误 id 查不到记录，会返回「对话不存在!」（这正是之前删除失败的根因）
    await deleteChatConversationMy(item.id);
    historyList.value = historyList.value.filter((c) => c.id !== item.id);
    if (String(item.id) === conversationId.value) {
      await initConversation();
      historyOpen.value = false;
    }
    message.success('会话已删除');
  } catch (e: any) {
    // 该会话可能已被删除（例如另开页面删过）：提示具体原因并刷新列表，避免残留脏项
    const hint = describeRequestError(e, '删除失败，请稍后重试');
    if (hint.includes('对话不存在')) {
      message.warning('该对话已不存在，已刷新列表');
      await loadHistoryList();
    } else {
      message.error(hint);
    }
  }
}

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
  // 【引导问法】用户在问功能入口/位置（在哪里/怎么找/菜单）时，
  // 不走前端本地意图分支（澄清反问/预填/模板等），直接交后端引导模式给菜单路径
  const guideQuestion =
    /(在哪里|在哪|哪儿|什么位置|怎么找|如何找|功能入口|菜单)/.test(content);

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
  if (!hasAttachments && !guideQuestion && matchVagueWorkOrderQuery(content)) {
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
  if (!hasAttachments && !guideQuestion && andonDraft) {
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
  if (!hasAttachments && !guideQuestion && qcCode) {
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

  // 【新增：任务包 B】报工智能填单：命中"报工/填单/预填 + 任务编号" → 预填确认流程
  const prefillCode = matchFeedbackPrefill(content);
  if (!hasAttachments && !guideQuestion && prefillCode) {
    handleFeedbackPrefillIntent(prefillCode);
    return;
  }

  // 【新增】只说了单据名词（如"生产工单"）：意图不明确，先反问澄清，
  // 不擅自猜测是要查询数据还是生成模板，避免答非所问
  const ambiguousNoun = matchAmbiguousDoc(content);
  if (!hasAttachments && !guideQuestion && ambiguousNoun) {
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
  if (!hasAttachments && !guideQuestion && handlePastedDocText(content)) {
    scrollToBottom();
    return;
  }

  const aiMsg = {
    role: 'assistant' as const,
    content: '',
    time: formatTime(),
    // 【识别场景标记】带附件且意图是识别/提取时，回答按纯文本展示，
    // 不渲染成业务查询卡片（识别不是查询，要求是识别不是添加）
    isRecognize: hasAttachments && /(识别|提取)/.test(content),
    /** 思考过程（折叠展示用，可能为空） */
    think: undefined as string | undefined,
  };
  messages.value.push(aiMsg); // AI 占位气泡（显示"正在思考"动画）
  streaming.value = true;
  abortController.value = new AbortController();
  scrollToBottom();

  let full = '';
  /** 模型单独返回的思考内容（reasoningContent） */
  let reasoning = '';
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
      conversationId.value,
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
        // 【思考折叠】思考过程优先取正文里的 <think> 段，其次取模型单独的 reasoningContent；
        // 只用于气泡内折叠展示，正式回答仍走 stripThink 清洗后的正文
        reasoning += data.receive.reasoningContent || '';
        aiMsg.think = (extractThink(full) || reasoning).trim() || undefined;
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
          // 【思考过程不展示】即使模型只输出了思考内容（<think> 无正文），
          // 也不展示思考过程本身，只提示重新提问
          stripped || '（模型本次未生成正式回答，请重新提问）',
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

/** ==================== 常用指令快捷条 ====================
 * 只收录「后端已授权的 MES 业务域」（生产工单 / 任务 / 报工 / 待检 / 质检 / 工艺 / 物料 / 车间 / 设备 / 安灯 / 客户供应商），
 * 未授权的 WMS 域（库存余额、仓库、出入库单等）不收录，避免点击后被权限守门员拒绝。 */
interface QuickCommand {
  icon: string;
  label: string;
  prompt: string;
}

const QUICK_COMMANDS: QuickCommand[] = [
  {
    icon: 'lucide:clipboard-list',
    label: '查生产工单',
    prompt: '查询最近的生产工单',
  },
  { icon: 'lucide:list-checks', label: '今日任务', prompt: '查询我今天的生产任务' },
  { icon: 'lucide:hammer', label: '去报工', prompt: '我要报工' },
  { icon: 'lucide:chart-column', label: '本周报工', prompt: '查询本周的报工记录' },
  { icon: 'lucide:search-check', label: '待检任务', prompt: '查询待检任务' },
  {
    icon: 'lucide:flask-conical',
    label: '过程检验',
    prompt: '查询最近的过程检验单',
  },
  { icon: 'lucide:box', label: '物料信息', prompt: '查询物料信息' },
  { icon: 'lucide:workflow', label: '工艺工序', prompt: '查询工艺工序' },
  { icon: 'lucide:factory', label: '车间产线', prompt: '查询车间产线信息' },
  { icon: 'lucide:cog', label: '设备台账', prompt: '查询设备台账' },
  {
    icon: 'lucide:alert-triangle',
    label: '上报安灯',
    prompt: '我要上报安灯异常',
  },
  { icon: 'lucide:users', label: '客户供应商', prompt: '查询客户信息' },
];

/** 「最近使用」记忆：最多 3 条，用过的会被置顶 */
const RECENT_COMMAND_KEY = 'mes-ai-quick-recent';

function loadRecentCommands(): string[] {
  try {
    const raw = localStorage.getItem(RECENT_COMMAND_KEY);
    const arr = raw ? JSON.parse(raw) : [];
    return Array.isArray(arr)
      ? arr.filter((x): x is string => typeof x === 'string').slice(0, 3)
      : [];
  } catch {
    return [];
  }
}

const recentCommandLabels = ref<string[]>(loadRecentCommands());

function saveRecentCommands(labels: string[]) {
  try {
    localStorage.setItem(RECENT_COMMAND_KEY, JSON.stringify(labels));
  } catch {
    /* 存储不可用时忽略 */
  }
}

/** 快捷指令列表：最近用过的排前面（recent 标记用于 UI 上区分） */
const quickCommands = computed(() => {
  const recentLabels = recentCommandLabels.value;
  const recent = recentLabels
    .map((label) => QUICK_COMMANDS.find((c) => c.label === label))
    .filter((c): c is QuickCommand => !!c)
    .map((c) => ({ ...c, recent: true }));
  const rest = QUICK_COMMANDS.filter(
    (c) => !recentLabels.includes(c.label),
  ).map((c) => ({ ...c, recent: false }));
  return [...recent, ...rest];
});

/** 点击快捷指令：填入输入框（可修改后发送）并记录「最近使用」 */
function applyQuickCommand(cmd: QuickCommand) {
  applyQuickIntent(cmd.prompt);
  recentCommandLabels.value = [
    cmd.label,
    ...recentCommandLabels.value.filter((l) => l !== cmd.label),
  ].slice(0, 3);
  saveRecentCommands(recentCommandLabels.value);
}

/** ==================== 首屏快捷分析入口（MES 场景，均在已授权业务域内） ==================== */
const WELCOME_ACTIONS = [
  {
    icon: 'lucide:trending-up',
    label: '生产数据分析',
    prompt: '查询今天生产情况',
  },
  {
    icon: 'lucide:cpu',
    label: '设备状态分析',
    prompt: '分析当前设备异常',
  },
  {
    icon: 'lucide:shield-check',
    label: '质量异常分析',
    prompt: '查看今日质量数据',
  },
  {
    icon: 'lucide:file-text',
    label: '生产报表生成',
    prompt: '生成今日生产报表',
  },
];

/** ==================== 语音输入（浏览器原生 Web Speech） ==================== */
const voiceSupported =
  typeof window !== 'undefined' &&
  !!(
    (window as any).SpeechRecognition ||
    (window as any).webkitSpeechRecognition
  );
const voiceListening = ref(false);
let recognition: any = null;
/** 浏览器麦克风 / 语音识别仅在安全上下文（https 或 localhost）下可用 */
const voiceSecureContext =
  typeof window !== 'undefined' && window.isSecureContext;
/** 本次识别是否已拿到过文字、是否已报错（用于结束时的兜底提示，避免重复打扰） */
let voiceGotResult = false;
let voiceErrored = false;

/** 开始 / 结束语音输入：识别结果实时写入输入框，可再手动修改后发送 */
function toggleVoice() {
  if (!voiceSupported) {
    message.warning('当前浏览器不支持语音输入，请使用 Chrome 或 Edge');
    return;
  }
  if (!voiceSecureContext) {
    message.warning('语音输入需要 https 或 localhost 环境，请换用 https 地址访问后重试');
    return;
  }
  if (voiceListening.value) {
    recognition?.stop();
    return;
  }
  const Ctor =
    (window as any).SpeechRecognition ||
    (window as any).webkitSpeechRecognition;
  recognition = new Ctor();
  recognition.lang = 'zh-CN';
  recognition.continuous = false;
  recognition.interimResults = true;
  recognition.maxAlternatives = 1;
  voiceGotResult = false;
  voiceErrored = false;
  recognition.onstart = () => {
    voiceListening.value = true;
  };
  recognition.onresult = (e: any) => {
    let text = '';
    let finalText = '';
    for (let i = 0; i < e.results.length; i += 1) {
      text += e.results[i][0].transcript;
      if (e.results[i].isFinal) finalText += e.results[i][0].transcript;
    }
    if (text) {
      voiceGotResult = true;
      inputText.value = finalText || text;
    }
  };
  recognition.onerror = (e: any) => {
    voiceListening.value = false;
    const err = e?.error;
    // aborted：用户主动停止 / 面板收起，属正常中断，不提示
    if (!err || err === 'aborted') {
      voiceErrored = true;
      return;
    }
    voiceErrored = true;
    // 按浏览器返回的错误码给出可操作的提示（而不是笼统的"识别失败"）
    const tips: Record<string, string> = {
      'audio-capture': '未检测到麦克风设备，请检查设备已连接并被系统识别',
      'bad-grammar': '语音识别失败，请重试或直接输入文字',
      language: '当前浏览器不支持中文识别，请改用 Chrome / Edge',
      network:
        '浏览器语音识别服务不可达（Chrome 的语音识别依赖境外服务，国内网络常被拦截），建议改用 Edge，或直接输入文字',
      'no-speech': '没有听到声音，请靠近麦克风再说一次',
      'not-allowed': '麦克风权限被拒绝：请点地址栏的锁图标允许麦克风后重试',
      'service-not-allowed': '浏览器禁用了语音识别服务，请检查浏览器设置',
    };
    message.warning(
      tips[err] || `语音识别失败（${err}），请重试或直接输入文字`,
    );
  };
  recognition.onend = () => {
    voiceListening.value = false;
    // 既没报错、也没识别到任何文字：多为没说话或麦克风没拾到声音
    if (!voiceErrored && !voiceGotResult) {
      message.warning('未识别到语音内容，请确认麦克风可用、音量正常后重试');
    }
  };
  voiceListening.value = true;
  try {
    recognition.start();
  } catch {
    voiceListening.value = false;
    message.warning('语音识别启动失败，请重试或直接输入文字');
  }
}

/** 释放语音识别（组件卸载时调用） */
function stopVoice() {
  try {
    recognition?.abort?.();
  } catch {
    /* 忽略 */
  }
  recognition = null;
  voiceListening.value = false;
}

/** ==================== 思考过程提取 & 复制 ==================== */
/** 提取原始文本中的 <think> 段（可能多段），供气泡内折叠展示 */
function extractThink(raw: string): string {
  if (!raw) return '';
  const re = /<think>([\s\S]*?)<\/think>/gi;
  const parts: string[] = [];
  let m = re.exec(raw);
  while (m) {
    const seg = (m[1] || '').trim();
    if (seg) parts.push(seg);
    m = re.exec(raw);
  }
  return parts.join('\n\n');
}

/** 已复制过的消息下标（用于按钮态反馈） */
const copiedIndex = ref(-1);
let copiedTimer: ReturnType<typeof setTimeout> | undefined;

/** 复制 AI 回答正文 */
async function copyMessage(msg: { content: string }, index: number) {
  const text = msg.content;
  if (!text) return;
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
    } else {
      // 兜底：非安全上下文（http）下 clipboard API 不可用
      const el = document.createElement('textarea');
      el.value = text;
      el.style.position = 'fixed';
      el.style.opacity = '0';
      document.body.append(el);
      el.select();
      document.execCommand('copy');
      el.remove();
    }
    copiedIndex.value = index;
    message.success('已复制回答');
    clearTimeout(copiedTimer);
    copiedTimer = setTimeout(() => {
      copiedIndex.value = -1;
    }, 1500);
  } catch {
    message.error('复制失败，请手动选中文本复制');
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
  [/^(检验单号|检验单编号|质检单号)$/, '检验单号'],
  [/^(检验类型|质检类型)$/, '检验类型'],
  [/^(所属工单|关联工单)$/, '所属工单'],
  [/^(任务编号|生产任务编号|生产任务)$/, '任务编号'],
  [/^(报工数量|汇报数量)$/, '报工数量'],
  [/^(报工人|报工人员|汇报人)$/, '报工人'],
  [/^(产品名称|产品|物料名称)$/, '产品名称'],
  [/^(物料)$/, '物料'],
  [/^(物料编码|编码)$/, '物料编码'],
  [/^(规格|规格型号)$/, '规格型号'],
  [/^(检验数量|质检数量)$/, '检验数量'],
  [/^(合格数量)$/, '合格数量'],
  [/^(不合格数量|不良数量)$/, '不合格数量'],
  [/^(检验结论|结论|判定结果|检验结果|检测结果)$/, '检验结论'],
  [/^(报工进度|进度)$/, '报工进度'],
  [/^(工单状态|状态)$/, '工单状态'],
  [/^(开工时间|开始时间)$/, '开工时间'],
  [/^(完工时间|结束时间)$/, '完工时间'],
  [/^(工艺|工艺信息|工序)$/, '工序'],
  [/^(客户|客户名称)$/, '客户'],
  [/^(供应商|供应商名称)$/, '供应商'],
  [/^(车间|车间名称)$/, '车间'],
  [/^(工位|工位名称)$/, '工位'],
  [/^(班组|班组名称)$/, '班组'],
  [/^(仓库|仓位)$/, '仓库'],
  [/^(库位)$/, '库位'],
  [/^(库区)$/, '库区'],
  [/^(设备|设备名称)$/, '设备'],
  [/^(设备编码|设备编号)$/, '设备编码'],
  [/^(安灯|安灯类型)$/, '安灯类型'],
  [/^(库存数量|库存数|现存量|在库数量)$/, '库存数量'],
  [/^(面积)$/, '面积'],
  [/^(要求交期|交期)$/, '要求交期'],
  [/^(备注)$/, '备注'],
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
  // 【兜底】白名单外的业务字段（报工单号/工作站/报工时间等）原样保留，
  // 保证任意业务类型的查询结果都完整展示，不再依赖白名单逐个枚举
  if (
    /^[\u4e00-\u9fa5A-Za-z0-9]{2,10}$/.test(rawLabel) &&
    !/^(提示|注意|说明|警告|示例|例如|参考|第一步|第二步|第三步|第四步|第五步|第六步|第[一二三四五六七八九十\d]+步|操作路径|操作步骤|概念|快捷方式|代录|原因|回答|别名)$/.test(
      rawLabel,
    )
  ) {
    return { label: rawLabel, value };
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
  // 【修复】业务类型按解析出的字段集合判断（不再在原文里搜关键词——
  // 任务名"待检验"/工位"质检工位"等词会误触发检验/工单标题）
  const labelSet = new Set<string>();
  validItems.forEach((item) => item.forEach((f) => labelSet.add(f.label)));
  let title = `📋 查询结果（共 ${validItems.length} 条）`;
  if (labelSet.has('检验单号') || labelSet.has('检验类型') || labelSet.has('检验结论')) {
    title = `🔍 过程检验单（IPQC）（共 ${validItems.length} 条）`;
  } else if (labelSet.has('报工单号') || labelSet.has('报工数量')) {
    title = `📝 生产报工记录（共 ${validItems.length} 条）`;
  } else if (labelSet.has('库存数量')) {
    title = `📦 库存查询结果（共 ${validItems.length} 条）`;
  } else if (labelSet.has('任务编号') || labelSet.has('已生产数量')) {
    title = `📑 生产任务（共 ${validItems.length} 条）`;
  } else if (labelSet.has('工单编号')) {
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
  } else if (labelSet.has('物料编码') || labelSet.has('产品名称')) {
    title = `📦 物料信息（共 ${validItems.length} 条）`;
  } else if (labelSet.has('设备编码') || labelSet.has('设备')) {
    title = `🔧 设备信息（共 ${validItems.length} 条）`;
  }
  // 【展示过滤】生产任务与 IPQC 检验单卡片不展示合格/不合格数量：
  // 质量数字以报工/检验页面为准，任务与检验列表只看进度与结论
  if (title.includes('生产任务') || title.includes('过程检验单')) {
    const filtered = validItems
      .map((item) =>
        item.filter((f) => f.label !== '合格数量' && f.label !== '不合格数量'),
      )
      .filter((item) => item.length >= 2);
    if (filtered.length > 0) validItems = filtered;
  }
  return { title, items: validItems };
}

/** 窗口尺寸变化时保持滚动在底部 */
function handleWindowResize() {
  scrollToBottom();
}

/** Esc 收起面板（与顶栏关闭按钮一致，仅收起、不中断生成本身） */
function handleGlobalKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && expanded.value) setExpanded(false);
}

onMounted(() => {
  initConversation();
  // 窗口尺寸变化时保持消息区滚动在底部
  window.addEventListener('resize', handleWindowResize);
  window.addEventListener('keydown', handleGlobalKeydown);
});

onBeforeUnmount(() => {
  abortController.value?.abort();
  stopVoice();
  clearTimeout(copiedTimer);
  window.removeEventListener('resize', handleWindowResize);
  window.removeEventListener('keydown', handleGlobalKeydown);
});
</script>

<template>
  <!-- 收起状态：右下角 AI 助手悬浮球（固定 right24/bottom24，图标本身即视觉主体） -->
  <Tooltip v-if="!expanded" title="AI 智能助手" placement="left">
    <div
      class="mes-ai-fab"
      :class="{ 'is-thinking': streaming }"
      @click="setExpanded(true)"
    >
      <img :src="assistantIcon" alt="AI 智能助手" class="mes-ai-fab__img" />
    </div>
  </Tooltip>

  <!-- 展开状态：右下角浮动对话面板（380×520，紧贴悬浮球上方，不挤压页面） -->
  <transition name="mes-ai-slide">
    <div
      v-show="expanded"
      class="mes-ai-panel"
      role="dialog"
      aria-label="AI 智能助手"
    >
      <!-- 顶栏：机器人头像 + 标题/副标题 + 历史 / 重开 / 最小化 / 关闭 -->
      <div class="mes-ai-header">
        <img :src="assistantIcon" alt="" class="mes-ai-header__avatar" />
        <div class="mes-ai-header__title">
          <span class="mes-ai-header__name">AI 智能助手</span>
          <span class="mes-ai-header__sub">MES 生产运营智能分析</span>
        </div>
        <div class="mes-ai-header__actions">
          <Tooltip title="历史会话" placement="bottom">
            <span
              class="mes-ai-icon-btn"
              :class="{ 'mes-ai-icon-btn--active': historyOpen }"
              @click="toggleHistory"
            >
              <IconifyIcon icon="lucide:history" :size="14" />
            </span>
          </Tooltip>
          <Tooltip title="重新开始会话" placement="bottom">
            <span class="mes-ai-icon-btn" @click="initConversation">
              <IconifyIcon icon="lucide:rotate-ccw" :size="14" />
            </span>
          </Tooltip>
          <Tooltip title="最小化" placement="bottom">
            <span class="mes-ai-icon-btn" @click="setExpanded(false)">
              <IconifyIcon icon="lucide:minus" :size="14" />
            </span>
          </Tooltip>
          <Tooltip title="关闭（停止本次生成）" placement="bottom">
            <span class="mes-ai-icon-btn" @click="closePanel">
              <IconifyIcon icon="lucide:x" :size="14" />
            </span>
          </Tooltip>
        </div>
      </div>

      <!-- 【历史会话列表】覆盖消息区显示，选择/删除历史对话 -->
      <div v-if="historyOpen" class="mes-ai-history">
        <div v-if="historyLoading" class="mes-ai-history__loading">
          加载中...
        </div>
        <div v-else-if="historyList.length === 0" class="mes-ai-history__empty">
          暂无历史会话
        </div>
        <div
          v-for="item in historyList"
          v-else
          :key="item.id"
          class="mes-ai-history__item"
          :class="{ 'is-current': String(item.id) === conversationId }"
          @click="switchConversation(item)"
        >
          <div class="mes-ai-history__main">
            <div class="mes-ai-history__title">
              {{ item.title || '未命名会话' }}
            </div>
            <div class="mes-ai-history__time">
              {{ formatHistoryTime(item.updateTime || item.createTime) }}
            </div>
          </div>
          <span
            class="mes-ai-history__del"
            title="删除会话"
            @click.stop="removeHistory(item)"
          >
            <IconifyIcon icon="lucide:trash-2" :size="13" />
          </span>
        </div>
      </div>

      <!-- 消息区：占面板剩余高度，内部滚动，不带动整页滚动 -->
      <div v-show="!historyOpen" ref="messageListRef" class="mes-ai-messages">
        <!-- 首次打开：欢迎内容 + 4 个 MES 快捷分析入口（点击即填入输入框） -->
        <div v-if="messages.length === 0" class="mes-ai-welcome">
          <img :src="assistantIcon" alt="" class="mes-ai-welcome__logo" />
          <div class="mes-ai-welcome__title">您好，我是 MES 智能助手</div>
          <div class="mes-ai-welcome__desc">
            我可以帮助您快速查询和分析生产运营数据。
          </div>
          <div class="mes-ai-welcome__actions">
            <button
              v-for="action in WELCOME_ACTIONS"
              :key="action.label"
              type="button"
              class="mes-ai-action"
              @click="applyQuickIntent(action.prompt)"
            >
              <IconifyIcon :icon="action.icon" :size="14" />
              <span>{{ action.label }}</span>
            </button>
          </div>
          <div class="mes-ai-welcome__tip">
            支持生产工单、任务、报工、质检、设备等业务数据的查询与分析
          </div>
        </div>

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
            <div
              class="mes-ai-bubble"
              :class="{
                'is-streaming':
                  streaming &&
                  index === messages.length - 1 &&
                  msg.role === 'assistant' &&
                  !!msg.content,
              }"
            >
              <!-- 【思考折叠】模型思考过程默认收起，点开可查看（正文已剔除思考内容） -->
              <details v-if="msg.think" class="mes-ai-think">
                <summary>
                  <IconifyIcon icon="lucide:brain-circuit" :size="12" />
                  <span>思考过程</span>
                </summary>
                <div class="mes-ai-think__body">{{ msg.think }}</div>
              </details>
              <!-- AI 回复（关键格式化处理位置 3）：业务数据渲染成卡片，普通问答走清洗后的文本 -->
              <template v-if="msg.role === 'assistant' && msg.content">
                <div
                  v-if="!msg.isRecognize && parseBusinessData(msg.content)"
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
            <!-- 消息时间戳 + 复制（复制仅对 AI 回答提供） -->
            <div class="mes-ai-meta">
              <span class="mes-ai-time">{{ msg.time }}</span>
              <Tooltip
                v-if="msg.role === 'assistant' && msg.content"
                title="复制回答"
                placement="top"
              >
                <span class="mes-ai-copy" @click="copyMessage(msg, index)">
                  <IconifyIcon
                    :icon="copiedIndex === index ? 'lucide:check' : 'lucide:copy'"
                    :size="12"
                  />
                  <em>{{ copiedIndex === index ? '已复制' : '复制' }}</em>
                </span>
              </Tooltip>
            </div>
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
        <!-- 常用指令快捷条：输入框为空时展示，点击即填（最近用过的排前面） -->
        <div v-if="!inputText?.trim()" class="mes-ai-quick">
          <span
            v-for="cmd in quickCommands"
            :key="cmd.label"
            class="mes-ai-quick__chip"
            :class="{ 'is-recent': cmd.recent }"
            @click="applyQuickCommand(cmd)"
          >
            <IconifyIcon
              :icon="cmd.recent ? 'lucide:history' : cmd.icon"
              :size="12"
            />
            {{ cmd.label }}
          </span>
        </div>
        <TextArea
          ref="intentInputRef"
          v-model:value="inputText"
          :auto-size="{ minRows: 2, maxRows: 4 }"
          class="mes-ai-textarea"
          placeholder="输入您想查询的问题..."
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
            <Tooltip
              :title="
                voiceSupported
                  ? voiceListening
                    ? '停止语音输入'
                    : '语音输入（普通话）'
                  : '当前浏览器不支持语音输入，请用 Chrome / Edge'
              "
              placement="top"
            >
              <span
                class="mes-ai-icon-btn mes-ai-mic"
                :class="{ 'is-listening': voiceListening }"
                @click="toggleVoice"
              >
                <IconifyIcon icon="lucide:mic" :size="14" />
              </span>
            </Tooltip>
            <span class="mes-ai-input__hint">Enter 发送 · Shift+Enter 换行</span>
          </div>
          <!-- 发送：圆形图标按钮（克制、与工业后台控件尺寸一致） -->
          <Button
            type="primary"
            shape="circle"
            class="mes-ai-send"
            :loading="streaming"
            :disabled="!inputText?.trim() || !conversationReady"
            title="发送（Enter）"
            @click="handleSend()"
          >
            <template #icon>
              <IconifyIcon icon="lucide:send" :size="15" />
            </template>
          </Button>
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
/* ==========================================================================
 * 主题变量层：颜色全部走变量，自动跟随 vben 深浅色主题
 * （主题变量是 HSL 三元组，如 `--primary: 212 100% 45%`，因此必须用 hsl() 包裹）
 * ========================================================================== */
.mes-ai-panel,
.mes-ai-fab {
  --mes-primary-hsl: var(--primary, 212 100% 45%);
  --mes-primary: hsl(var(--mes-primary-hsl));
  --mes-primary-soft: hsl(var(--mes-primary-hsl) / 8%);
  --mes-primary-border: hsl(var(--mes-primary-hsl) / 35%);
  --mes-surface: hsl(var(--card, 0 0% 100%));
  --mes-surface-2: hsl(var(--muted, 240 4.8% 95.9%));
  --mes-fill: hsl(var(--accent, 240 5% 96%));
  --mes-fg: hsl(var(--foreground, 210 6% 21%));
  --mes-fg-strong: hsl(var(--card-foreground, 222.2 84% 4.9%));
  --mes-fg-muted: hsl(var(--muted-foreground, 240 3.8% 46.1%));
  --mes-fg-subtle: hsl(var(--muted-foreground, 240 3.8% 46.1%) / 70%);
  --mes-border: hsl(var(--border, 240 5.9% 90%));
  --mes-border-strong: hsl(var(--input, 240 5.9% 90%));
  --mes-danger: hsl(var(--destructive, 359 100% 65%));
  --mes-on-primary: hsl(var(--primary-foreground, 0 0% 98%));
}

/* ========== 面板：右下角浮动卡片（380×520，贴悬浮球上方，不挤压页面） ========== */
.mes-ai-panel {
  position: fixed;
  right: 24px;
  bottom: 88px;
  z-index: 1200;
  display: flex;
  flex-direction: column;
  width: 380px;
  height: min(520px, calc(100vh - 112px));
  overflow: hidden;
  color: var(--mes-fg);
  background: var(--mes-surface);
  border: 1px solid var(--mes-border);
  border-radius: 14px;
  /* 很轻的主阴影 + 一抹极淡蓝色边缘光：有层次但不喧宾夺主 */
  box-shadow:
    0 10px 30px rgb(15 23 42 / 12%),
    0 0 0 1px hsl(var(--mes-primary-hsl) / 6%);
}

/* ========== 顶栏：机器人头像 + 标题/副标题 + 操作 ========== */
.mes-ai-header {
  display: flex;
  flex-shrink: 0;
  gap: 10px;
  align-items: center;
  padding: 12px 14px;
  background: var(--mes-surface);
  border-bottom: 1px solid var(--mes-border);
}

/* 顶部很小的机器人头像（与悬浮球同一视觉资源，不重复绘制） */
.mes-ai-header__avatar {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  object-fit: contain;
}

.mes-ai-header__title {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-width: 0;
  line-height: 1.3;
  text-align: left;
}

.mes-ai-header__name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--mes-fg-strong);
}

.mes-ai-header__sub {
  margin-top: 2px;
  overflow: hidden;
  font-size: 11.5px;
  font-weight: 400;
  color: var(--mes-fg-muted);
  text-overflow: ellipsis;
  white-space: nowrap;
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
  color: var(--mes-fg-muted);
  cursor: pointer;
  background: var(--mes-fill);
  border-radius: 6px;
  transition: all 0.2s;
}

.mes-ai-icon-btn:hover {
  color: var(--mes-primary);
  background: var(--mes-primary-soft);
}

.mes-ai-primary {
  color: var(--mes-primary);
}

/* ========== 消息区：占面板剩余高度，浅灰底衬托白色气泡，内部滚动 ========== */
.mes-ai-messages {
  flex: 1 1 auto;
  min-height: 0;
  padding: 12px;
  overflow-y: auto; /* 溢出只做内部滚动，不带动整页 */
  background: var(--mes-surface-2);
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
  color: var(--mes-on-primary);
  background: var(--mes-primary);
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
  color: var(--mes-fg);
  background: var(--mes-surface);
  border: 1px solid var(--mes-border);
  border-top-left-radius: 2px;
}

.is-user .mes-ai-bubble {
  color: var(--mes-on-primary);
  background: var(--mes-primary);
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
  border-top: 1px dashed var(--mes-border);
}

/* 【新增】澄清选项：可点击的快捷选项 chips */
.mes-ai-options {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding-top: 6px;
  margin-top: 6px;
  border-top: 1px dashed var(--mes-border);
}

.mes-ai-option {
  padding: 3px 10px;
  font-size: 12.5px;
  color: var(--mes-primary);
  cursor: pointer;
  background: var(--mes-primary-soft);
  border: 1px solid var(--mes-primary-border);
  border-radius: 12px;
  transition: all 0.15s;
}

.mes-ai-option:hover {
  color: var(--mes-on-primary);
  background: var(--mes-primary);
  border-color: var(--mes-primary);
}

/* 【新增：导入解析】输入区左侧（附件按钮 + 快捷键提示） */
.mes-ai-input__left {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.mes-ai-attach {
  color: var(--mes-fg-muted);
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
  color: var(--mes-fg);
}

.mes-ai-md :deep(p:last-child) {
  margin-bottom: 0;
}

/* 列表：序号与内容紧凑同行，条目间距小 */
.mes-ai-md :deep(ul),
.mes-ai-md :deep(ol) {
  padding-left: 20px;
  margin: 2px 0 8px;
  color: var(--mes-fg);
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
  color: var(--mes-fg-strong);
}

/* 标题：小尺寸加粗 + 上下小间距（气泡内不需要大标题） */
.mes-ai-md :deep(h1),
.mes-ai-md :deep(h2),
.mes-ai-md :deep(h3),
.mes-ai-md :deep(h4) {
  font-weight: 600;
  color: var(--mes-fg-strong);
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
  color: var(--mes-fg);
  background: var(--mes-fill);
  border-radius: 4px;
}

/* 代码块 */
.mes-ai-md :deep(pre) {
  padding: 8px 10px;
  margin: 4px 0 8px;
  overflow-x: auto;
  font-size: 12px;
  line-height: 1.5;
  background: var(--mes-surface-2);
  border: 1px solid var(--mes-border);
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
  color: var(--mes-fg-muted);
  background: var(--mes-surface-2);
  border-left: 3px solid var(--mes-primary);
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
  border: 1px solid var(--mes-border);
}

.mes-ai-md :deep(th) {
  font-weight: 600;
  color: var(--mes-fg-strong);
  background: var(--mes-fill);
}

.mes-ai-md :deep(tr:nth-child(2n) td) {
  background: var(--mes-surface-2);
}

/* 分隔线 */
.mes-ai-md :deep(hr) {
  margin: 8px 0;
  border: none;
  border-top: 1px dashed var(--mes-border);
}

/* 链接 */
.mes-ai-md :deep(a) {
  color: var(--mes-primary);
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
  background: var(--mes-fg-subtle);
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
  color: var(--mes-fg-muted);
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
  color: var(--mes-fg-subtle);
}

/* ========== 输入区：白色圆角输入卡片 + 底部操作行（固定高度不挤压消息区） ========== */
.mes-ai-input {
  flex-shrink: 0;
  padding: 10px 12px 8px;
  background: var(--mes-surface);
  border-top: 1px solid var(--mes-border);
}

/* ========== 待发送附件区：chip + 快捷意图（挂在输入框上方） ========== */
.mes-ai-pending {
  margin-bottom: 8px;
  padding: 8px;
  background: var(--mes-surface-2);
  border: 1px dashed var(--mes-border-strong);
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
  color: var(--mes-fg);
  background: var(--mes-surface);
  border: 1px solid var(--mes-border);
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
  color: var(--mes-fg-muted);
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
  color: var(--mes-fg-subtle);
  cursor: pointer;
}

.mes-ai-pending__remove:hover {
  color: var(--mes-danger);
}

.mes-ai-pending__intents {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}

.mes-ai-pending__intent {
  font-size: 12px;
  color: var(--mes-primary);
  background: var(--mes-surface);
  border-color: var(--mes-primary-border);
  border-radius: 8px;
}

/* 输入卡片：白底圆角，文字黑色，聚焦高亮蓝边 */
.mes-ai-textarea {
  font-size: 13px;
  color: var(--mes-fg);
  background: var(--mes-surface);
  border: 1px solid var(--mes-border-strong);
  border-radius: 10px;
  transition: border-color 0.2s;
}

.mes-ai-textarea:focus,
.mes-ai-textarea:focus-within {
  border-color: var(--mes-primary);
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
  color: var(--mes-fg-subtle);
}

/* 发送：圆形图标按钮（30px，与顶栏图标按钮同一尺寸体系，克制不抢眼） */
.mes-ai-send {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 30px;
  min-width: 30px;
  height: 30px;
  padding: 0;
  border-radius: 50%;
}

/* 图标严格居中（antd 圆形按钮默认行高会带来约 1px 偏移） */
.mes-ai-send :deep(.anticon),
.mes-ai-send :deep(span) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin: 0;
  line-height: 1;
  letter-spacing: normal;
}

.mes-ai-send.ant-btn-loading {
  padding-inline: 0;
}

/* ========== 收起状态：右下角 AI 助手悬浮球 ==========
 * 图标本身即视觉主体（图片自带蓝色玻璃底盘），容器透明，不额外套大面积纯色圆底；
 * 阴影与光晕来自容器圆形 border-box —— hover 仅轻微放大 + 上浮，不加旋转/弹跳等花哨动画。 */
.mes-ai-fab {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 1200;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  cursor: pointer;
  user-select: none;
  background: transparent;
  border-radius: 50%;
  /* 光晕取青蓝调，与图标自带的青色边缘光衔接（克制的轻微发光，非霓虹） */
  box-shadow: 0 6px 20px rgb(37 99 235 / 22%);
  transition:
    transform 0.22s ease,
    box-shadow 0.22s ease;
}

.mes-ai-fab__img {
  display: block;
  width: 52px;
  height: 52px;
  pointer-events: none;
  user-select: none;
  object-fit: contain;
}

.mes-ai-fab:hover {
  transform: scale(1.06) translateY(-2px);
  box-shadow:
    0 10px 26px rgb(37 99 235 / 30%),
    0 0 0 4px rgb(56 189 248 / 12%);
}

.mes-ai-fab:active {
  transform: scale(1.02) translateY(-1px);
}

/* 思考中：外圈缓慢呼吸光晕（不旋转图标的「AI 正在工作」提示，极其克制） */
.mes-ai-fab.is-thinking::after {
  position: absolute;
  inset: -5px;
  content: '';
  border: 2px solid rgb(56 189 248 / 38%);
  border-radius: 50%;
  animation: mes-ai-breath 2.4s ease-in-out infinite;
}

@keyframes mes-ai-breath {
  0%,
  100% {
    opacity: 0.35;
    transform: scale(0.96);
  }

  50% {
    opacity: 0.9;
    transform: scale(1.06);
  }
}

/* ========== 展开/收起过渡：右下角向上滑入 + 淡入 ========== */
.mes-ai-slide-enter-active,
.mes-ai-slide-leave-active {
  transition:
    opacity 0.24s ease,
    transform 0.24s ease;
}

.mes-ai-slide-enter-from,
.mes-ai-slide-leave-to {
  opacity: 0;
  transform: translateY(10px);
}

/* ========== 业务数据卡片（气泡内轻量卡片，白底面板下浅灰蓝底，深浅主题均清晰） ========== */
.mes-ai-card {
  width: 100%; /* 完全包裹在气泡内部，不溢出聊天框 */
  max-height: 40vh; /* 长内容卡片内部滚动 */
  padding: 10px 12px;
  overflow-y: auto;
  background: var(--mes-surface-2);
  border: 1px solid var(--mes-border);
  border-radius: 8px; /* 细微圆角 */
}

.mes-ai-card__title {
  padding-bottom: 6px;
  margin-bottom: 2px;
  font-size: 13px;
  font-weight: 600;
  color: var(--mes-fg-strong);
  border-bottom: 1px solid var(--mes-border);
}

.mes-ai-card__item {
  padding: 7px 0;
  border-bottom: 1px dashed var(--mes-border); /* 柔和分隔线 */
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
  color: var(--mes-fg-strong);
}

.mes-ai-card__value {
  color: var(--mes-fg);
  word-break: break-all; /* 长编号/名称自动换行不溢出 */
}

/* 卡片内部滚动条美化 */
.mes-ai-card::-webkit-scrollbar {
  width: 6px;
}

.mes-ai-card::-webkit-scrollbar-thumb {
  background: var(--mes-border-strong);
  border-radius: 3px;
}

.mes-ai-card::-webkit-scrollbar-track {
  background: transparent;
}

/* ========== 历史会话列表（占满头部以下的剩余空间，自动适应头部高度） ========== */
.mes-ai-history {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
  padding: 10px;
  overflow-y: auto;
  background: var(--mes-surface-2);
}

.mes-ai-history__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 11px;
  margin-bottom: 6px;
  cursor: pointer;
  background: var(--mes-surface);
  border: 1px solid var(--mes-border);
  border-radius: 8px;
  transition: border-color 0.2s;
}

.mes-ai-history__item:hover {
  border-color: var(--mes-primary);
}

.mes-ai-history__item.is-current {
  background: var(--mes-primary-soft);
  border-color: var(--mes-primary-border);
}

.mes-ai-history__title {
  max-width: 240px;
  overflow: hidden;
  font-size: 13px;
  color: var(--mes-fg);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mes-ai-history__time {
  margin-top: 3px;
  font-size: 11px;
  color: var(--mes-fg-subtle);
}

.mes-ai-history__del {
  flex-shrink: 0;
  padding: 4px;
  color: var(--mes-fg-subtle);
  cursor: pointer;
}

.mes-ai-history__del:hover {
  color: var(--mes-danger);
}

.mes-ai-history__loading,
.mes-ai-history__empty {
  padding: 24px 0;
  font-size: 12px;
  color: var(--mes-fg-subtle);
  text-align: center;
}

.mes-ai-icon-btn--active {
  color: var(--mes-primary);
}

/* ========== 响应式 ==========
 * ≥1440：380×520 固定右下；≤1024：面板 360、悬浮球 48；≤768：贴近边缘、面板占满可用宽度 */
@media (max-width: 1024px) {
  .mes-ai-panel {
    width: 360px;
  }

  .mes-ai-fab,
  .mes-ai-fab__img {
    width: 48px;
    height: 48px;
  }
}

@media (max-width: 768px) {
  .mes-ai-panel {
    right: 12px;
    bottom: 74px;
    width: calc(100vw - 24px);
    height: min(520px, calc(100vh - 96px));
  }

  .mes-ai-fab {
    right: 16px;
    bottom: 16px;
  }

  .mes-ai-fab,
  .mes-ai-fab__img {
    width: 46px;
    height: 46px;
  }
}

/* ========== 首屏欢迎区 + 4 个 MES 快捷分析入口 ========== */
.mes-ai-welcome {
  padding: 10px 2px 4px;
  text-align: center;
}

.mes-ai-welcome__logo {
  display: block;
  width: 52px;
  height: 52px;
  margin: 4px auto 12px;
  object-fit: contain;
}

.mes-ai-welcome__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--mes-fg-strong);
}

.mes-ai-welcome__desc {
  margin-top: 6px;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--mes-fg-muted);
}

/* 快捷入口：浅蓝/白风格（描边 + 悬停淡蓝底），不使用大面积实心蓝 */
.mes-ai-welcome__actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 16px;
  text-align: left;
}

.mes-ai-action {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 9px 10px;
  font-family: inherit;
  font-size: 12.5px;
  color: var(--mes-fg);
  cursor: pointer;
  background: var(--mes-surface);
  border: 1px solid var(--mes-border);
  border-radius: 10px;
  transition: all 0.2s ease;
}

.mes-ai-action:hover {
  color: var(--mes-primary);
  background: var(--mes-primary-soft);
  border-color: var(--mes-primary-border);
}

.mes-ai-welcome__tip {
  padding-top: 12px;
  margin-top: 14px;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--mes-fg-subtle);
  border-top: 1px dashed var(--mes-border);
}

/* ========== 常用指令快捷条（输入框为空时展示，最近用过的排前面） ==========
 * 与欢迎区的快捷入口同风格：白底 + 细描边 + 悬停淡蓝，避免出现大量实心蓝块 */
.mes-ai-quick {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  max-height: 62px;
  margin-bottom: 8px;
  overflow-y: auto;
}

.mes-ai-quick__chip {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  padding: 3px 9px;
  font-size: 12px;
  color: var(--mes-fg-muted);
  cursor: pointer;
  background: var(--mes-surface);
  border: 1px solid var(--mes-border);
  border-radius: 12px;
  transition: all 0.2s ease;
}

.mes-ai-quick__chip:hover {
  color: var(--mes-primary);
  background: var(--mes-primary-soft);
  border-color: var(--mes-primary-border);
}

.mes-ai-quick__chip.is-recent {
  color: var(--mes-primary);
  background: var(--mes-primary-soft);
  border-color: var(--mes-primary-border);
}

/* ========== 语音输入：录音中红色脉冲提示 ========== */
.mes-ai-mic.is-listening {
  color: var(--mes-danger);
  background: hsl(var(--destructive, 359 100% 65%) / 12%);
  animation: mes-ai-pulse 1.4s infinite;
}

@keyframes mes-ai-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 hsl(var(--destructive, 359 100% 65%) / 35%);
  }

  50% {
    box-shadow: 0 0 0 6px hsl(var(--destructive, 359 100% 65%) / 0%);
  }
}

/* ========== 思考过程折叠 ========== */
.mes-ai-think {
  margin-bottom: 6px;
  font-size: 12px;
  color: var(--mes-fg-muted);
}

.mes-ai-think > summary {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  padding: 2px 8px;
  cursor: pointer;
  list-style: none;
  background: var(--mes-surface-2);
  border-radius: 10px;
}

.mes-ai-think > summary::-webkit-details-marker {
  display: none;
}

.mes-ai-think[open] > summary {
  color: var(--mes-primary);
}

.mes-ai-think__body {
  max-height: 220px;
  padding: 8px 10px;
  margin-top: 6px;
  overflow-y: auto;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
  background: var(--mes-surface-2);
  border-left: 2px solid var(--mes-border-strong);
  border-radius: 0 8px 8px 0;
}

/* ========== 消息底部：时间 + 复制（悬停显示） ========== */
.mes-ai-meta {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 4px;
}

.mes-ai-meta .mes-ai-time {
  margin-top: 0;
}

.mes-ai-copy {
  display: inline-flex;
  gap: 3px;
  align-items: center;
  font-size: 11px;
  color: var(--mes-fg-subtle);
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s;
}

.mes-ai-copy em {
  font-style: normal;
}

.mes-ai-row:hover .mes-ai-copy {
  opacity: 1;
}

/* ========== 流式输出光标（正在逐字输出时显示） ========== */
.mes-ai-bubble.is-streaming::after {
  display: inline-block;
  width: 6px;
  height: 14px;
  margin-left: 2px;
  vertical-align: -2px;
  content: '';
  background: var(--mes-primary);
  border-radius: 2px;
  animation: mes-ai-caret 1s steps(2, start) infinite;
}

@keyframes mes-ai-caret {
  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0;
  }
}

/* ========== 消息区滚动条：细、低干扰 ========== */
.mes-ai-messages::-webkit-scrollbar,
.mes-ai-quick::-webkit-scrollbar {
  width: 8px;
}

.mes-ai-messages::-webkit-scrollbar-thumb,
.mes-ai-quick::-webkit-scrollbar-thumb {
  background: hsl(var(--muted-foreground, 240 3.8% 46.1%) / 25%);
  border-radius: 4px;
}

.mes-ai-messages::-webkit-scrollbar-thumb:hover,
.mes-ai-quick::-webkit-scrollbar-thumb:hover {
  background: hsl(var(--muted-foreground, 240 3.8% 46.1%) / 40%);
}

.mes-ai-messages::-webkit-scrollbar-track,
.mes-ai-quick::-webkit-scrollbar-track {
  background: transparent;
}
</style>

<style>
/* ========== 非 scoped：面板是右下角浮动卡片，不改写任何页面布局（无 body 让位样式） ========== */

/* antd 多行输入框内部样式（跟随主题，聚焦高亮主色） */
.mes-ai-textarea.ant-input {
  color: var(--mes-fg);
  background: var(--mes-surface);
  border-color: var(--mes-border-strong);
}

.mes-ai-textarea.ant-input:focus,
.mes-ai-textarea.ant-input:hover {
  border-color: var(--mes-primary);
}
</style>
