<script setup lang="ts">
import type { AiChatConversationApi } from '#/api/ai/chat/conversation';
import type { AiChatMessageApi } from '#/api/ai/chat/message';

import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { alert } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import { Button, Card, message } from 'ant-design-vue';

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

const props = withDefaults(
  defineProps<{
    /** 面板高度（px） */
    height?: number;
  }>(),
  { height: 500 },
);

const router = useRouter();

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
  ensureConversation();
});

onBeforeUnmount(() => {
  if (conversationInAbortController.value) {
    conversationInAbortController.value.abort();
  }
});
</script>

<template>
  <Card
    class="h-full flex flex-col overflow-hidden"
    :body-style="{ flex: '1', minHeight: 0, overflow: 'hidden' }"
  >
    <template #title>
      <div class="flex items-center gap-2">
        <IconifyIcon icon="lucide:sparkles" class="size-4 text-blue-500" />
        <span>AI 智能助手</span>
        <span class="text-xs font-normal opacity-60">MES 生产助手</span>
      </div>
    </template>
    <template #extra>
      <div class="flex items-center gap-1">
        <Button
          type="text"
          size="small"
          :disabled="conversationInProgress"
          @click="handleNewConversation"
        >
          <IconifyIcon icon="lucide:plus" class="mr-1" />
          新对话
        </Button>
        <Button type="link" size="small" @click="handleOpenFullChat">
          完整对话
          <IconifyIcon icon="lucide:external-link" class="ml-1 size-3.5" />
        </Button>
      </div>
    </template>

    <div class="flex h-full flex-col" :style="{ height: `${props.height}px` }">
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
          class="flex min-h-0 flex-1 flex-col items-center justify-center px-6"
        >
          <IconifyIcon
            icon="lucide:bot"
            class="size-12 text-blue-400"
          />
          <div class="mt-3 text-base font-medium">
            你好，我是「MES 生产助手」
          </div>
          <div class="mt-1 text-sm opacity-60">
            可以问我生产工单、任务、报工、质检等情况，也可以让我帮你分析
          </div>
          <div class="mt-5 flex flex-wrap justify-center gap-3">
            <div
              v-for="item in quickPrompts"
              :key="item.label"
              class="cursor-pointer rounded-full border border-blue-200 px-4 py-1.5 text-sm text-blue-600 transition-all hover:bg-blue-50 hover:shadow"
              @click="doSendMessage(item.text)"
            >
              {{ item.label }}
            </div>
          </div>
        </div>

        <!-- 消息列表 -->
        <div v-else class="min-h-0 flex-1 overflow-hidden">
          <div class="mx-auto h-full max-w-[1000px]">
            <MessageList
              v-if="activeConversation && messageList.length > 0"
              ref="messageRef"
              :conversation="activeConversation as any"
              :list="messageList as any"
            />
          </div>
        </div>
      </template>

      <!-- 底部输入区 -->
      <div class="mt-3 flex-shrink-0 border-t border-border pt-3">
        <form class="flex flex-col gap-2">
          <textarea
            id="mes-ai-assistant-input"
            v-model="prompt"
            class="box-border h-16 w-full resize-none rounded-md border border-border p-2 text-sm focus:border-blue-400 focus:outline-none"
            placeholder="问我任何问题...（Shift+Enter 换行，Enter 发送）"
            @keydown="handleSendByKeydown"
            @input="handlePromptInput"
            @compositionstart="onCompositionstart"
            @compositionend="onCompositionend"
          ></textarea>
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-4 text-sm text-gray-400">
              <span>上下文</span>
            </div>
            <div class="flex items-center gap-2">
              <Button
                type="primary"
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
  </Card>
</template>

<style scoped>
:deep(.ant-card-body) {
  display: flex;
  flex-direction: column;
  padding: 12px 16px 16px;
}
</style>
