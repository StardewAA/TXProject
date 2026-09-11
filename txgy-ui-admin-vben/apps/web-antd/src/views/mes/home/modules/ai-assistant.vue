<script setup lang="ts">
import type { AiChatConversationApi } from '#/api/ai/chat/conversation';
import type { AiChatMessageApi } from '#/api/ai/chat/message';

import type { CSSProperties } from 'vue';

import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { alert } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import { Button, message } from 'ant-design-vue';

import {
  createChatConversationMy,
  getChatConversationMy,
  getChatConversationMyList,
} from '#/api/ai/chat/conversation';
import {
  getChatMessageListByConversationId,
  sendChatMessageStream,
} from '#/api/ai/chat/message';

import MessageList from '../../../ai/chat/index/modules/message/list.vue';
import MessageLoading from '../../../ai/chat/index/modules/message/loading.vue';

defineOptions({ name: 'MesHomeAiAssistant' });

/** MES 首页固定使用「MES 生产助手」角色（租户内配置） */
const MES_ROLE_ID = 20;

// =========== 悬浮窗状态 ===========
const PANEL_STORAGE_KEY = 'mes-ai-assistant-panel-pos'; // 面板位置持久化 key
const panelOpen = ref(false); // 面板是否展开（false 时显示悬浮球）
const isDragging = ref(false); // 是否正在拖拽
const panelRef = ref<HTMLElement>(); // 面板 DOM（用于读取实际宽高做边界检测）
const panelPos = ref<{ x: number; y: number }>({ x: 0, y: 0 }); // 面板左上角坐标
// 拖拽起始快照（鼠标位置 + 面板位置），mousemove 时用差值计算新坐标
let dragStart = { mouseX: 0, mouseY: 0, panelX: 0, panelY: 0 };

/** 面板样式：left/top 由拖拽位置决定 */
const panelStyle = computed<CSSProperties>(() => ({
  height: 'min(600px, calc(100vh - 24px))',
  left: `${panelPos.value.x}px`,
  top: `${panelPos.value.y}px`,
  width: 'min(380px, calc(100vw - 24px))',
}));

/** 默认停靠位置：视口右下角（留 16px 边距） */
function defaultPanelPos() {
  const rect = panelRef.value?.getBoundingClientRect();
  const width = rect?.width ?? 380;
  const height = rect?.height ?? 600;
  return {
    x: Math.max(8, window.innerWidth - width - 16),
    y: Math.max(8, window.innerHeight - height - 16),
  };
}

/** 边界检测：把坐标限制在可视区域内，避免面板被拖出屏幕 */
function clampPanelPos(x: number, y: number) {
  const rect = panelRef.value?.getBoundingClientRect();
  const width = rect?.width ?? 380;
  const height = rect?.height ?? 600;
  const maxX = Math.max(8, window.innerWidth - width - 8);
  const maxY = Math.max(8, window.innerHeight - height - 8);
  return {
    x: Math.min(Math.max(8, x), maxX),
    y: Math.min(Math.max(8, y), maxY),
  };
}

/** 从 localStorage 恢复上次拖拽的位置 */
function restorePanelPos() {
  try {
    const raw = localStorage.getItem(PANEL_STORAGE_KEY);
    if (raw) {
      const saved = JSON.parse(raw) as { x: number; y: number };
      if (Number.isFinite(saved?.x) && Number.isFinite(saved?.y)) {
        panelPos.value = clampPanelPos(saved.x, saved.y);
        return;
      }
    }
  } catch {
    // 数据损坏时忽略，走默认位置
  }
  panelPos.value = defaultPanelPos();
}

/** 标题栏按下：开始拖拽（鼠标 + 触屏统一入口） */
function onHeaderDown(event: MouseEvent | TouchEvent) {
  // 点在按钮/链接/输入框上时不触发拖拽，避免影响内部交互
  if (
    event.target instanceof Element &&
    event.target.closest('button, a, textarea, input, [role="button"]')
  ) {
    return;
  }
  const point = 'touches' in event ? event.touches[0] : event;
  if (!point) return;
  isDragging.value = true;
  dragStart = {
    mouseX: point.clientX,
    mouseY: point.clientY,
    panelX: panelPos.value.x,
    panelY: panelPos.value.y,
  };
  document.addEventListener('mousemove', onDragMove);
  document.addEventListener('mouseup', onDragEnd);
  document.addEventListener('touchmove', onDragMove, { passive: false });
  document.addEventListener('touchend', onDragEnd);
  // 防止拖拽时选中标题文字
  if ('touches' in event) event.preventDefault();
  else event.preventDefault();
}

/** 拖拽中：实时更新面板位置（边界检测在 clampPanelPos 内完成） */
function onDragMove(event: MouseEvent | TouchEvent) {
  if (!isDragging.value) return;
  const point = 'touches' in event ? event.touches[0] : event;
  if (!point) return;
  const nextX = dragStart.panelX + (point.clientX - dragStart.mouseX);
  const nextY = dragStart.panelY + (point.clientY - dragStart.mouseY);
  panelPos.value = clampPanelPos(nextX, nextY);
  if ('touches' in event) event.preventDefault();
}

/** 拖拽结束：解除监听 + 位置写入 localStorage */
function onDragEnd() {
  if (!isDragging.value) return;
  isDragging.value = false;
  document.removeEventListener('mousemove', onDragMove);
  document.removeEventListener('mouseup', onDragEnd);
  document.removeEventListener('touchmove', onDragMove);
  document.removeEventListener('touchend', onDragEnd);
  try {
    localStorage.setItem(PANEL_STORAGE_KEY, JSON.stringify(panelPos.value));
  } catch {
    // localStorage 不可用（隐私模式等）时静默失败，不影响功能
  }
}

/** 浏览器窗口变化时把面板拉回可视区域 */
function onWindowResize() {
  panelPos.value = clampPanelPos(panelPos.value.x, panelPos.value.y);
}

// =========== 会话状态 ===========
const activeConversationId = ref<null | number>(null); // 选中的对话编号
const activeConversation = ref<AiChatConversationApi.ChatConversation | null>(
  null,
); // 选中的 Conversation
const activeMessageList = ref<AiChatMessageApi.ChatMessage[]>([]); // 选中对话的消息列表
const activeMessageListLoading = ref(false); // 是否加载中
const loadingTimer = ref<any>(); // 加载定时器
const initError = ref<string>(''); // 初始化失败原因

// =========== 发送状态 ===========
const conversationInProgress = ref(false); // 是否正在回复中
const conversationInAbortController = ref<any>(); // 流式对话中止控制器
const prompt = ref<string>(); // 输入内容
const isComposing = ref(false); // 输入法组合中
const inputTimeout = ref<any>(); // 回车输入定时器
const enableContext = ref(true); // 上下文开关
const enableWebSearch = ref(false); // 联网搜索开关

// =========== 快捷提问（空会话引导） ===========
const quickPrompts = [
  { label: '今日工单', text: '帮我查一下今天的生产工单情况' },
  { label: '待办任务', text: '有哪些待完成的生产任务？' },
  { label: '报工记录', text: '查询最近的生产报工记录' },
  { label: '质量检验', text: '查询最近的过程检验单' },
];

const router = useRouter();

/** 打开完整 AI 对话页 */
function handleOpenFullChat() {
  router.push({
    path: '/ai/chat',
    query: activeConversationId.value
      ? { conversationId: activeConversationId.value }
      : {},
  });
}

/** 新建一个「MES 生产助手」对话并切换过去 */
async function handleNewConversation() {
  if (conversationInProgress.value) {
    await alert('回复中，不能新建对话!');
    return;
  }
  const id = await createChatConversationMy({
    roleId: MES_ROLE_ID,
  } as AiChatConversationApi.ChatConversation);
  activeConversationId.value = id;
  activeConversation.value = await getChatConversationMy(id);
  activeMessageList.value = [];
  prompt.value = '';
  await scrollToBottom();
}

/** 初始化：复用最近的 MES 生产助手对话，没有则自动创建 */
async function ensureConversation() {
  try {
    const list = await getChatConversationMyList();
    const sorted = (list || []).toSorted(
      (a, b) => Number(b.createTime) - Number(a.createTime),
    );
    const target =
      sorted.find((item) => item.roleId === MES_ROLE_ID) || sorted[0] || null;
    if (target) {
      activeConversationId.value = target.id;
      activeConversation.value = target;
    } else {
      const id = await createChatConversationMy({
        roleId: MES_ROLE_ID,
      } as AiChatConversationApi.ChatConversation);
      activeConversationId.value = id;
      activeConversation.value = await getChatConversationMy(id);
    }
  } catch (error: any) {
    initError.value = error?.message || 'AI 助手初始化失败';
    return;
  }
  await getMessageList();
}

// =========== 消息列表 ===========
async function getMessageList() {
  if (activeConversationId.value === null) {
    return;
  }
  try {
    loadingTimer.value = setTimeout(() => {
      activeMessageListLoading.value = true;
    }, 60);
    activeMessageList.value = await getChatMessageListByConversationId(
      activeConversationId.value,
    );
    await nextTick();
    await scrollToBottom();
  } finally {
    if (loadingTimer.value) {
      clearTimeout(loadingTimer.value);
    }
    activeMessageListLoading.value = false;
  }
}

/** 待展示消息（不注入 systemMessage，避免把角色设定当欢迎语） */
const messageList = computed(() => activeMessageList.value);
const messageRef = ref(); // MessageList 组件实例（提供 scrollToBottom）

// =========== 发送消息 ===========
async function handleSendByKeydown(event: any) {
  if (isComposing.value || conversationInProgress.value) {
    return;
  }
  if (event.key === 'Enter') {
    if (event.shiftKey) {
      prompt.value += '\r\n';
      event.preventDefault();
    } else {
      await doSendMessage(prompt.value?.trim() as string);
      event.preventDefault();
    }
  }
}

function handleSendByButton() {
  doSendMessage(prompt.value?.trim() as string);
}

function handlePromptInput(event: any) {
  if (!isComposing.value) {
    if (event.data === null || event.data === 'null') {
      return;
    }
    isComposing.value = true;
  }
  if (inputTimeout.value) {
    clearTimeout(inputTimeout.value);
  }
  inputTimeout.value = setTimeout(() => {
    isComposing.value = false;
  }, 400);
}

function onCompositionstart() {
  isComposing.value = true;
}

function onCompositionend() {
  setTimeout(() => {
    isComposing.value = false;
  }, 200);
}

async function doSendMessage(content: string) {
  if (!content) {
    return;
  }
  // 尚未初始化会话时，尝试先创建
  if (activeConversationId.value === null) {
    await ensureConversation();
    if (activeConversationId.value === null) {
      message.error('AI 助手尚未就绪，请刷新页面重试！');
      return;
    }
  }
  prompt.value = '';
  await doSendMessageStream({
    conversationId: activeConversationId.value as number,
    content,
  } as AiChatMessageApi.ChatMessage);
}

/** 发送消息：SSE 流式接收并逐字追加到气泡 */
async function doSendMessageStream(userMessage: AiChatMessageApi.ChatMessage) {
  // 1. 准备状态
  conversationInAbortController.value = new AbortController();
  conversationInProgress.value = true;

  // 2. 先放两个占位消息（用户 + 思考中），等首个 chunk 到达后替换
  activeMessageList.value.push(
    {
      id: -1,
      conversationId: activeConversationId.value,
      type: 'user',
      content: userMessage.content,
      createTime: new Date(),
    } as AiChatMessageApi.ChatMessage,
    {
      id: -2,
      conversationId: activeConversationId.value,
      type: 'assistant',
      content: '思考中...',
      reasoningContent: '',
      createTime: new Date(),
    } as AiChatMessageApi.ChatMessage,
  );
  await nextTick();
  await scrollToBottom();

  // 3. 发送 SSE
  let isFirstChunk = true;
  try {
    await sendChatMessageStream(
      userMessage.conversationId,
      userMessage.content,
      conversationInAbortController.value,
      enableContext.value,
      enableWebSearch.value,
      async (res: any) => {
        const { code, data, msg } = JSON.parse(res.data);
        if (code !== 0) {
          await alert(`对话异常! ${msg}`);
          if (activeMessageList.value.length >= 2) {
            activeMessageList.value.pop();
          }
          return;
        }
        // 内容和推理都为空，忽略
        if (data.receive.content === '' && !data.receive.reasoningContent) {
          return;
        }
        // 首个有效 chunk：移除占位，插入真实消息
        if (isFirstChunk) {
          isFirstChunk = false;
          activeMessageList.value.pop();
          activeMessageList.value.pop();
          activeMessageList.value.push(data.send, data.receive);
        }
        const lastMessage =
          activeMessageList.value[activeMessageList.value.length - 1];
        if (!lastMessage) {
          return;
        }
        // 推理内容累加
        if (data.receive.reasoningContent) {
          lastMessage.reasoningContent =
            (lastMessage.reasoningContent || '') +
            data.receive.reasoningContent;
        }
        // 正文内容累加
        if (data.receive.content !== '') {
          lastMessage.content = (lastMessage.content || '') +
            data.receive.content;
        }
        await scrollToBottom();
      },
      (error: any) => {
        alert('对话异常!');
        stopStream();
        throw error;
      },
      () => {
        stopStream();
      },
    );
  } catch {}
}

/** 停止流式回复 */
async function stopStream() {
  if (conversationInAbortController.value) {
    conversationInAbortController.value.abort();
  }
  conversationInProgress.value = false;
}

/** 滚动到底部 */
async function scrollToBottom() {
  await nextTick();
  if (messageRef.value) {
    messageRef.value.scrollToBottom();
  }
}

onMounted(() => {
  restorePanelPos();
  window.addEventListener('resize', onWindowResize);
  ensureConversation();
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', onWindowResize);
  // 组件卸载时兜底清理拖拽监听，避免泄漏
  document.removeEventListener('mousemove', onDragMove);
  document.removeEventListener('mouseup', onDragEnd);
  document.removeEventListener('touchmove', onDragMove);
  document.removeEventListener('touchend', onDragEnd);
  if (conversationInAbortController.value) {
    conversationInAbortController.value.abort();
  }
});
</script>

<template>
  <!-- 收起态：右下角悬浮球 -->
  <button
    v-if="!panelOpen"
    class="fixed bottom-6 right-6 z-[1200] flex size-14 items-center justify-center rounded-full bg-blue-600 text-white shadow-lg transition-all hover:scale-105 hover:bg-blue-500 hover:shadow-xl"
    title="AI 智能助手"
    @click="panelOpen = true"
  >
    <IconifyIcon icon="lucide:sparkles" class="size-6" />
    <span
      class="absolute right-0 top-0 size-3 animate-pulse rounded-full bg-emerald-400 ring-2 ring-white"
    ></span>
  </button>

  <!-- 展开态：可拖拽面板 -->
  <div
    v-else
    ref="panelRef"
    class="mes-ai-panel fixed z-[1200] flex flex-col overflow-hidden rounded-xl border border-border bg-background shadow-xl"
    :class="isDragging ? 'select-none opacity-95 shadow-2xl' : ''"
    :style="panelStyle"
  >
    <!-- 标题栏：拖拽把手（cursor-move），按住空白处即可拖动 -->
    <div
      class="flex flex-shrink-0 items-center justify-between border-b border-border bg-blue-600 px-3 py-2 text-white"
      :class="isDragging ? 'cursor-move' : 'cursor-move'"
      @mousedown="onHeaderDown"
      @touchstart="onHeaderDown"
    >
      <div class="flex items-center gap-2 overflow-hidden">
        <IconifyIcon icon="lucide:sparkles" class="size-4 flex-shrink-0" />
        <span class="text-sm font-medium">AI 智能助手</span>
        <span class="text-xs font-normal opacity-80">MES 生产助手</span>
      </div>
      <div class="flex flex-shrink-0 items-center gap-1">
        <Button
          type="text"
          size="small"
          class="!text-white hover:!bg-white/20"
          :disabled="conversationInProgress"
          @click="handleNewConversation"
        >
          <IconifyIcon icon="lucide:plus" class="mr-1" />
          新对话
        </Button>
        <button
          class="flex size-6 items-center justify-center rounded transition-colors hover:bg-white/20"
          title="收起面板"
          @click="panelOpen = false"
        >
          <IconifyIcon icon="lucide:x" class="size-4" />
        </button>
      </div>
    </div>

    <!-- 内容区 -->
    <div class="flex min-h-0 flex-1 flex-col p-3">
      <!-- 初始化失败提示 -->
      <div v-if="initError" class="flex flex-1 items-center justify-center">
        <div class="text-center">
          <div class="text-lg font-bold text-red-500">AI 助手不可用</div>
          <div class="mt-2 text-sm opacity-70">{{ initError }}</div>
        </div>
      </div>

      <!-- 加载中 -->
      <MessageLoading v-else-if="activeMessageListLoading" />

      <template v-else>
        <!-- 空会话：欢迎 + 快捷提问 -->
        <div
          v-if="activeConversation && messageList.length === 0"
          class="flex min-h-0 flex-1 flex-col items-center justify-center px-4"
        >
          <IconifyIcon icon="lucide:bot" class="size-12 text-blue-400" />
          <div class="mt-3 text-base font-medium">
            你好，我是「MES 生产助手」
          </div>
          <div class="mt-1 text-center text-sm opacity-60">
            可以问我生产工单、任务、报工、质检等情况
          </div>
          <div class="mt-4 flex flex-wrap justify-center gap-2">
            <div
              v-for="item in quickPrompts"
              :key="item.label"
              class="cursor-pointer rounded-full border border-blue-200 px-3 py-1.5 text-xs text-blue-600 transition-all hover:bg-blue-50 hover:shadow"
              @click="doSendMessage(item.text)"
            >
              {{ item.label }}
            </div>
          </div>
        </div>

        <!-- 消息列表 -->
        <div v-else class="min-h-0 flex-1 overflow-hidden">
          <MessageList
            v-if="activeConversation && messageList.length > 0"
            ref="messageRef"
            :conversation="activeConversation as any"
            :list="messageList as any"
          />
        </div>
      </template>

      <!-- 底部输入区：与聊天区之间保留 16px 间距（内容区 p-3 底部 12px + mt-1 4px） -->
      <div class="mt-1 flex-shrink-0 border-t border-border pt-3">
        <form class="flex flex-col gap-2">
          <textarea
            id="mes-ai-assistant-input"
            v-model="prompt"
            class="box-border h-14 w-full resize-none rounded-md border border-border p-2 text-sm focus:border-blue-400 focus:outline-none"
            placeholder="问我任何问题...（Shift+Enter 换行，Enter 发送）"
            @keydown="handleSendByKeydown"
            @input="handlePromptInput"
            @compositionstart="onCompositionstart"
            @compositionend="onCompositionend"
          ></textarea>
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2 text-xs text-gray-400">
              <span>上下文</span>
              <button
                type="button"
                class="text-blue-500 underline decoration-dotted"
                title="打开完整 AI 对话页"
                @click="handleOpenFullChat"
              >
                完整对话
              </button>
            </div>
            <div class="flex items-center gap-2">
              <Button
                type="primary"
                size="small"
                :loading="conversationInProgress"
                :disabled="conversationInProgress"
                @click="handleSendByButton"
              >
                <IconifyIcon icon="lucide:send-horizontal" class="mr-1" />
                发送
              </Button>
              <Button
                v-if="conversationInProgress"
                type="primary"
                size="small"
                danger
                @click="stopStream()"
              >
                <IconifyIcon icon="lucide:circle-stop" class="mr-1" />
                停止
              </Button>
            </div>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 拖拽把手（标题栏）外观 */
.mes-ai-panel > div:first-child {
  touch-action: none; /* 移动端拖拽时阻止页面滚动 */
  user-select: none;
}

/* 拖拽中：面板本体加更深的阴影提示“被抓起” */
.mes-ai-panel {
  will-change: left, top;
}

/* ============================================================
   消息气泡样式优化（仅作用于 MES 悬浮窗，通过 :deep 覆盖子组件
   MessageList 的默认样式，不影响 /ai/chat 完整对话页）
   ============================================================ */

/* 6. 消息列表：两侧留白 16px，条目间距 16px（覆盖原 px-5 / mt-12） */
.mes-ai-panel :deep(.mt-12) {
  margin-top: 0;
  margin-bottom: 16px;
  padding-left: 16px;
  padding-right: 16px;
}

/* 时间戳行统一尺寸，消除 AI(leading-10)/用户(leading-8) 的不对称 */
.mes-ai-panel :deep(.mx-4 > div:first-child) {
  font-size: 12px;
  line-height: 1.4;
  margin-bottom: 4px;
  opacity: 0.6;
}

/* ---------- AI 消息气泡（靠左） ----------
   width: fit-content 消除 flex-col 拉伸导致的“气泡过宽”，
   按内容自适应，最长不超过聊天区 72% */
.mes-ai-panel :deep(.mx-4 > .relative.rounded-lg) {
  width: fit-content;
  max-width: 72%;
  min-height: 42px; /* 短消息也保有协调的宽高比，不变成细长条 */
  padding: 14px 18px;
  border-radius: 16px;
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
    'Microsoft YaHei', sans-serif;
  font-size: 14px;
  line-height: 1.65;
}

/* ---------- 用户消息气泡（靠右） ----------
   与 AI 气泡完全对称：同限宽/内边距/圆角/字号/行高/min-height */
.mes-ai-panel :deep(.bg-blue-500.rounded-lg) {
  min-height: 42px;
  max-width: 72%;
  padding: 14px 18px;
  border-radius: 16px;
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
    'Microsoft YaHei', sans-serif;
  font-size: 14px;
  line-height: 1.65;
  margin-left: auto;
  display: flex;
  align-items: center; /* 单行短消息垂直居中，视觉更协调 */
}

/* 普通文本防止长 token（URL、函数调用标记）撑破气泡 */
.mes-ai-panel :deep(.mx-4 > .relative.rounded-lg *) {
  overflow-wrap: break-word;
}

/* 3. 代码块：与气泡背景色区分（深色块），不换行 + 横向滚动，完整显示
      函数调用标记（<|FunctionCallBegin|> 等长 token 不会被截断） */
.mes-ai-panel :deep(pre) {
  margin: 8px 0;
  overflow-x: auto;
  padding: 12px;
  background: #1f2937; /* gray-800，与气泡浅灰底明显区分 */
  border-radius: 10px;
  color: #e5e7eb;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre; /* 保持原始格式，不自动换行 */
  tab-size: 4;
}

.mes-ai-panel :deep(pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
  font-size: 13px;
  white-space: pre; /* 代码内禁止异常换行 */
}

/* 行内代码：浅蓝底与正文区分 */
.mes-ai-panel :deep(:not(pre) > code) {
  padding: 1px 5px;
  background: rgb(96 165 250 / 15%);
  border-radius: 4px;
  color: #2563eb;
  font-size: 13px;
}

/* 7. 滚动条优化：消息列表纵向滚动条纤细化（Webkit + Firefox） */
.mes-ai-panel :deep(.overflow-y-auto) {
  scrollbar-width: thin; /* Firefox */
  scrollbar-color: rgb(148 163 184 / 45%) transparent;
}

.mes-ai-panel :deep(.overflow-y-auto::-webkit-scrollbar) {
  width: 6px;
  height: 6px;
}

.mes-ai-panel :deep(.overflow-y-auto::-webkit-scrollbar-thumb) {
  border-radius: 3px;
  background: rgb(148 163 184 / 45%);
}

.mes-ai-panel :deep(.overflow-y-auto::-webkit-scrollbar-thumb:hover) {
  background: rgb(100 116 139 / 65%);
}

.mes-ai-panel :deep(.overflow-y-auto::-webkit-scrollbar-track) {
  background: transparent;
}

/* 响应式：小屏下气泡限宽放大到 90%，仍保留两侧边距 */
@media (max-width: 640px) {
  .mes-ai-panel :deep(.mx-4 > .relative.rounded-lg),
  .mes-ai-panel :deep(.bg-blue-500.rounded-lg) {
    max-width: 90%;
  }
}
</style>
