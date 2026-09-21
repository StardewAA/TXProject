<script lang="ts" setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { useUserStore } from '@vben/stores';

import txgyLogo from '#/assets/imgs/txgy-logo.png';

defineOptions({ name: 'PortalHome' });

type DispatchTarget = {
  description: string;
  label: string;
  path: string;
};

type ChatAttachment = {
  name: string;
  size: string;
};

type ChatMessage = {
  action?: DispatchTarget;
  actions?: DispatchTarget[];
  at: number;
  attachments?: ChatAttachment[];
  id: string;
  role: 'assistant' | 'user';
  text: string;
};

type Conversation = {
  createdAt: number;
  id: string;
  messages: ChatMessage[];
  title: string;
  updatedAt: number;
};

type QuickChip = {
  label: string;
  prompt: string;
};

type StoredState = {
  conversations?: Conversation[];
};

const STORAGE_KEY = 'txgy-portal-home-chat-v1';
const MAX_ATTACHMENT_SIZE = 20 * 1024 * 1024;
const MAX_CONVERSATIONS = 30;
const TIME_DIVIDER_GAP = 5 * 60 * 1000;
const REPLY_DELAY = 420;

const router = useRouter();
const userStore = useUserStore();

const conversations = ref<Conversation[]>([]);
const activeId = ref<null | string>(null);
const entered = ref(false);
const input = ref('');
const attachments = ref<ChatAttachment[]>([]);
const fileError = ref('');
const headerHeight = ref(64);
const isNarrow = ref(false);
const sidebarOpen = ref(false);
const pendingId = ref<null | string>(null);
const textareaRef = ref<HTMLTextAreaElement>();
const fileInputRef = ref<HTMLInputElement>();
const threadRef = ref<HTMLElement>();

let mediaQuery: MediaQueryList | undefined;

/** 平台各业务入口，助手回答时作为去向卡片 */
const targetContent: DispatchTarget = {
  description: '按关键词检索政策法规、技术标准与行业资料。',
  label: 'CMS系统 · 政策法规智查',
  path: '/portal/content',
};

const targetProduct: DispatchTarget = {
  description: '在产品资料与标准库中按名称、规格或标准号检索技术参数。',
  label: '产品参数与标准检索',
  path: '/portal/content',
};

const targetTraining: DispatchTarget = {
  description: '进入课程中心、考试认证、直播大厅与学习任务。',
  label: '云培训学院',
  path: '/portal/training',
};

const targetBidding: DispatchTarget = {
  description: '查看外部公告、本平台发布的项目与投标流程。',
  label: '招标投标',
  path: '/portal/bidding',
};

const targetRecruit: DispatchTarget = {
  description: '浏览职位、查看企业信息并投递简历。',
  label: '我要找工作',
  path: '/portal/recruit',
};

const targetProfile: DispatchTarget = {
  description: '查看个人档案、成长记录与已获学时。',
  label: '个人档案与成长',
  path: '/portal/profile',
};

const dispatchRules: { pattern: RegExp; target: DispatchTarget }[] = [
  { pattern: /(产品|参数|规格|型号|材质|指标|配件|物资)/u, target: targetProduct },
  {
    pattern: /(政策|法规|标准|规范|条例|法律|合规|审查|资料|文件)/u,
    target: targetContent,
  },
  {
    pattern: /(培训|课程|学习|考试|证书|认证|学时|上岗|训练)/u,
    target: targetTraining,
  },
  { pattern: /(招标|投标|标书|公告|开标|中标|采购|项目)/u, target: targetBidding },
  {
    pattern: /(职位|招聘|求职|简历|应聘|找工作|岗位|企业)/u,
    target: targetRecruit,
  },
  { pattern: /(档案|成长|积分|记录|我的|个人)/u, target: targetProfile },
];

const fallbackTargets: DispatchTarget[] = [
  targetContent,
  targetTraining,
  targetBidding,
  targetRecruit,
];

const quickChips: QuickChip[] = [
  { label: '政策咨询', prompt: '我想咨询防腐保温相关的政策法规与执行标准。' },
  { label: '产品参数查询', prompt: '帮我查一下防腐保温产品的技术参数。' },
  { label: '考试认证查询', prompt: '我想查询培训考试与技能认证。' },
  { label: '招标投标查询', prompt: '我想查询最新的招标投标公告。' },
];
const isChatMode = computed(() => entered.value);

const activeConversation = computed(
  () => conversations.value.find((item) => item.id === activeId.value) ?? null,
);

const activeMessages = computed(() => activeConversation.value?.messages ?? []);

const isReplying = computed(
  () => Boolean(pendingId.value) && pendingId.value === activeId.value,
);

const canSend = computed(
  () => input.value.trim().length > 0 || attachments.value.length > 0,
);

const displayName = computed(
  () => userStore.userInfo?.nickname || userStore.userInfo?.username || '用户',
);

const myInitial = computed(() => displayName.value.slice(0, 1));

const headerStyle = computed(() => `${headerHeight.value}px`);

const placeholder = computed(() =>
  isChatMode.value
    ? '继续描述你的问题，或从下方入口开始'
    : '有什么可以帮你的？描述你的问题或需求…',
);

const recentConversations = computed(() =>
  [...conversations.value]
    .sort((left, right) => right.updatedAt - left.updatedAt)
    .slice(0, 3),
);

const groupedConversations = computed(() => {
  const now = new Date();
  const startOfToday = new Date(
    now.getFullYear(),
    now.getMonth(),
    now.getDate(),
  ).getTime();
  const startOfYesterday = startOfToday - 24 * 60 * 60 * 1000;
  const weekAgo = startOfToday - 6 * 24 * 60 * 60 * 1000;
  const buckets: { items: Conversation[]; label: string }[] = [
    { items: [], label: '今天' },
    { items: [], label: '昨天' },
    { items: [], label: '7 天内' },
    { items: [], label: '更早' },
  ];
  const sorted = [...conversations.value].sort(
    (left, right) => right.updatedAt - left.updatedAt,
  );
  for (const item of sorted) {
    if (item.updatedAt >= startOfToday) {
      buckets[0]!.items.push(item);
    } else if (item.updatedAt >= startOfYesterday) {
      buckets[1]!.items.push(item);
    } else if (item.updatedAt >= weekAgo) {
      buckets[2]!.items.push(item);
    } else {
      buckets[3]!.items.push(item);
    }
  }
  return buckets.filter((bucket) => bucket.items.length > 0);
});

function createId() {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`;
}

function pad(value: number) {
  return String(value).padStart(2, '0');
}

function makeTitle(text: string) {
  const normalized = text.replace(/\s+/gu, ' ').trim();
  if (!normalized) {
    return '新的对话';
  }
  return normalized.length > 18 ? `${normalized.slice(0, 18)}…` : normalized;
}

function formatSize(bytes: number) {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function formatDivider(timestamp: number) {
  const date = new Date(timestamp);
  const now = new Date();
  const startOfToday = new Date(
    now.getFullYear(),
    now.getMonth(),
    now.getDate(),
  ).getTime();
  const clock = `${pad(date.getHours())}:${pad(date.getMinutes())}`;
  if (timestamp >= startOfToday) {
    return clock;
  }
  if (timestamp >= startOfToday - 24 * 60 * 60 * 1000) {
    return `昨天 ${clock}`;
  }
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${clock}`;
}

function needsDivider(index: number) {
  const list = activeMessages.value;
  const current = list[index];
  if (!current) {
    return false;
  }
  if (index === 0) {
    return true;
  }
  return current.at - (list[index - 1]?.at ?? 0) > TIME_DIVIDER_GAP;
}
function buildReply(text: string, files: ChatAttachment[]): ChatMessage {
  const matched = dispatchRules.find((rule) => rule.pattern.test(text));
  const message: ChatMessage = {
    at: Date.now(),
    id: createId(),
    role: 'assistant',
    text: '',
  };

  if (matched) {
    message.action = matched.target;
    message.text =
      matched.target === targetProduct
        ? '产品参数（规格、材质、执行标准等）现收录在「CMS系统」的资料与标准库中，可按产品名称或标准号检索；完整产品库仍在建设中，我先带你去检索已收录的标准与资料。'
        : `我帮你找到「${matched.target.label}」，${matched.target.description}点击下方卡片即可进入。`;
  } else {
    message.actions = fallbackTargets;
    message.text =
      '我还没听明白你的目标。你可以直接点下面的入口，或者换一种说法告诉我你想解决的问题。';
  }

  if (files.length > 0) {
    const names = files.map((file) => `《${file.name}》`).join('、');
    message.text = `已收到 ${names}，先记在当前会话里。助手暂时还不能解析文件正文，你可以直接描述文件中的问题。\n\n${message.text}`;
  }

  return message;
}

function persist() {
  try {
    const state: StoredState = {
      conversations: conversations.value.slice(0, MAX_CONVERSATIONS),
    };
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
    // 隐私模式等场景下 localStorage 不可用，忽略即可
  }
}

/** 只恢复历史会话；首屏固定回到内容居中的欢迎态，由用户发起新的对话 */
function restore() {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return;
    }
    const saved = JSON.parse(raw) as StoredState;
    if (Array.isArray(saved.conversations)) {
      conversations.value = saved.conversations;
    }
  } catch {
    conversations.value = [];
  }
}

async function scrollThreadToBottom() {
  await nextTick();
  const element = threadRef.value;
  element?.scrollTo({ behavior: 'smooth', top: element.scrollHeight });
}

function ensureConversation(seed: string) {
  const current = conversations.value.find((item) => item.id === activeId.value);
  if (current) {
    return current;
  }
  const conversation: Conversation = {
    createdAt: Date.now(),
    id: createId(),
    messages: [],
    title: makeTitle(seed),
    updatedAt: Date.now(),
  };
  conversations.value = [conversation, ...conversations.value].slice(
    0,
    MAX_CONVERSATIONS,
  );
  activeId.value = conversation.id;
  return conversation;
}
function submit(preset?: string) {
  const text = (preset ?? input.value).trim();
  if (!text && attachments.value.length === 0) {
    return;
  }

  const conversation = ensureConversation(text);
  const files = attachments.value.slice();

  conversation.messages.push({
    at: Date.now(),
    attachments: files.length > 0 ? files : undefined,
    id: createId(),
    role: 'user',
    text,
  });
  conversation.updatedAt = Date.now();
  if (conversation.title === '新的对话') {
    conversation.title = makeTitle(text || files[0]?.name || '新的对话');
  }

  input.value = '';
  attachments.value = [];
  fileError.value = '';
  entered.value = true;
  pendingId.value = conversation.id;
  persist();
  void scrollThreadToBottom();

  window.setTimeout(() => {
    conversation.messages.push(buildReply(text, files));
    conversation.updatedAt = Date.now();
    if (pendingId.value === conversation.id) {
      pendingId.value = null;
    }
    persist();
    void scrollThreadToBottom();
  }, REPLY_DELAY);
}

function openConversation(id: string) {
  activeId.value = id;
  entered.value = true;
  sidebarOpen.value = false;
  persist();
  void scrollThreadToBottom();
}

function startNewChat() {
  activeId.value = null;
  entered.value = conversations.value.length > 0;
  sidebarOpen.value = false;
  input.value = '';
  attachments.value = [];
  fileError.value = '';
  pendingId.value = null;
  persist();
  void nextTick(() => textareaRef.value?.focus());
}

function removeConversation(id: string) {
  conversations.value = conversations.value.filter((item) => item.id !== id);
  if (activeId.value === id) {
    activeId.value = conversations.value[0]?.id ?? null;
  }
  if (conversations.value.length === 0) {
    entered.value = false;
  }
  persist();
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing) {
    return;
  }
  event.preventDefault();
  submit();
}

function pickFile() {
  fileInputRef.value?.click();
}

function removeAttachment(index: number) {
  attachments.value.splice(index, 1);
}

function onFileChange(event: Event) {
  const target = event.target as HTMLInputElement;
  const file = target.files?.[0];
  fileError.value = '';
  if (file) {
    if (file.size > MAX_ATTACHMENT_SIZE) {
      fileError.value = '单个文件请控制在 20MB 以内';
    } else {
      attachments.value.push({ name: file.name, size: formatSize(file.size) });
    }
  }
  target.value = '';
}

function openTarget(path: string) {
  void router.push(path);
}

function syncNarrow(event: MediaQueryList | MediaQueryListEvent) {
  isNarrow.value = event.matches;
}

function syncHeaderHeight() {
  const header = document.querySelector('.portal-header');
  if (header instanceof HTMLElement && header.offsetHeight > 0) {
    headerHeight.value = header.offsetHeight;
  }
}

onMounted(() => {
  restore();
  syncHeaderHeight();
  mediaQuery = window.matchMedia('(max-width: 720px)');
  syncNarrow(mediaQuery);
  mediaQuery.addEventListener('change', syncNarrow);
  window.addEventListener('resize', syncHeaderHeight);
});

onBeforeUnmount(() => {
  mediaQuery?.removeEventListener('change', syncNarrow);
  window.removeEventListener('resize', syncHeaderHeight);
});
</script>
<template>
  <div
    class="portal-chat"
    :class="{
      'portal-chat--chat': isChatMode,
      'portal-chat--sidebar': isNarrow && sidebarOpen,
      'portal-chat--welcome': !isChatMode,
    }"
    :style="{ '--portal-header-height': headerStyle }"
  >
    <div
      v-if="isNarrow && sidebarOpen"
      class="chat-scrim"
      @click="sidebarOpen = false"
    ></div>

    <aside v-if="isChatMode" class="chat-aside">
      <div class="aside-head">
        <span class="aside-brand">
          <img alt="天信管业" class="aside-brand-logo" :src="txgyLogo" />
          <span>智慧平台助手</span>
        </span>
      </div>

      <button class="aside-new" type="button" @click="startNewChat">
        <svg aria-hidden="true" viewBox="0 0 24 24">
          <path d="M12 5v14M5 12h14" />
        </svg>
        新对话
      </button>

      <div class="aside-list">
        <template v-for="group in groupedConversations" :key="group.label">
          <p class="aside-group">{{ group.label }}</p>
          <div
            v-for="item in group.items"
            :key="item.id"
            class="aside-item"
            :class="{ 'aside-item--active': item.id === activeId }"
          >
            <button
              class="aside-item-main"
              type="button"
              @click="openConversation(item.id)"
            >
              {{ item.title }}
            </button>
            <button
              aria-label="删除对话"
              class="aside-item-remove"
              title="删除对话"
              type="button"
              @click="removeConversation(item.id)"
            >
              ×
            </button>
          </div>
        </template>
        <p v-if="groupedConversations.length === 0" class="aside-empty">
          还没有对话记录
        </p>
      </div>

      <div class="aside-foot">
        <span class="aside-user">{{ displayName }}</span>
      </div>
    </aside>

    <div class="chat-main">
      <div v-if="!isChatMode" class="welcome">
        <div class="welcome-brand">
          <img alt="天信管业" class="welcome-logo" :src="txgyLogo" />
          <h1 class="welcome-title">天信管业集团防腐保温智慧平台</h1>
        </div>
        <p class="welcome-slogan">让天下没有难做的防腐保温工程</p>
        <p class="welcome-desc">
          打造标准规范化、全链数字化、管控智能化、服务一体化、合规常态化的防腐保温全产业链服务平台——准备好，我们就从这里开始。
        </p>

        <div v-if="recentConversations.length > 0" class="welcome-recent">
          <span class="welcome-recent-label">最近对话</span>
          <button
            v-for="item in recentConversations"
            :key="item.id"
            class="welcome-recent-item"
            type="button"
            @click="openConversation(item.id)"
          >
            {{ item.title }}
          </button>
        </div>
      </div>

      <header v-else class="chat-head">
        <button
          v-if="isNarrow"
          aria-label="对话列表"
          class="chat-head-toggle"
          title="对话列表"
          type="button"
          @click="sidebarOpen = !sidebarOpen"
        >
          <svg aria-hidden="true" viewBox="0 0 24 24">
            <path d="M4 7h16M4 12h16M4 17h16" />
          </svg>
        </button>
        <span class="chat-head-title">
          {{ activeConversation?.title || '新的对话' }}
        </span>
        <span class="chat-head-sub">天信管业集团 · 智能助手</span>
      </header>
      <div v-if="isChatMode" ref="threadRef" class="chat-thread">
        <div
          v-if="activeMessages.length === 0 && !isReplying"
          class="thread-hint"
        >
          <p>开始新的对话</p>
          <span>选择下方入口，或直接描述你想解决的问题。</span>
        </div>

        <template v-for="(message, index) in activeMessages" :key="message.id">
          <div v-if="needsDivider(index)" class="thread-divider">
            <span>{{ formatDivider(message.at) }}</span>
          </div>

          <div class="thread-row" :class="`thread-row--${message.role}`">
            <img
              v-if="message.role === 'assistant'"
              alt=""
              class="thread-avatar"
              :src="txgyLogo"
            />
            <span v-else class="thread-avatar thread-avatar--me">
              {{ myInitial }}
            </span>

            <div class="bubble" :class="`bubble--${message.role}`">
              <div v-if="message.text" class="bubble-text">
                {{ message.text }}
              </div>

              <ul v-if="message.attachments?.length" class="bubble-files">
                <li
                  v-for="(file, fileIndex) in message.attachments"
                  :key="`${file.name}-${fileIndex}`"
                >
                  <svg aria-hidden="true" viewBox="0 0 24 24">
                    <path
                      d="m8.5 12.5 5.8-5.8a3 3 0 0 1 4.2 4.2l-7.7 7.7a5 5 0 0 1-7.1-7.1l8-8a3.5 3.5 0 0 1 5 5l-7.4 7.4a2 2 0 0 1-2.8-2.8l6.4-6.4"
                    />
                  </svg>
                  <span>{{ file.name }}</span>
                  <em>{{ file.size }}</em>
                </li>
              </ul>

              <button
                v-if="message.action"
                class="bubble-action"
                type="button"
                @click="openTarget(message.action.path)"
              >
                <span class="bubble-action-label">
                  {{ message.action.label }}
                </span>
                <span class="bubble-action-desc">
                  {{ message.action.description }}
                </span>
                <span class="bubble-action-go">进入 →</span>
              </button>

              <div v-if="message.actions?.length" class="bubble-links">
                <button
                  v-for="action in message.actions"
                  :key="action.path"
                  class="bubble-link"
                  type="button"
                  @click="openTarget(action.path)"
                >
                  {{ action.label }}
                </button>
              </div>
            </div>
          </div>
        </template>

        <div v-if="isReplying" class="thread-row thread-row--assistant">
          <img alt="" class="thread-avatar" :src="txgyLogo" />
          <div class="bubble bubble--assistant bubble--typing">
            <i></i><i></i><i></i>
          </div>
        </div>
      </div>
      <div class="chat-composer">
        <form
          class="composer"
          :class="{ 'composer--chat': isChatMode }"
          @submit.prevent="submit()"
        >
          <div v-if="attachments.length > 0" class="composer-files">
            <span
              v-for="(file, index) in attachments"
              :key="`${file.name}-${index}`"
              class="composer-file"
            >
              {{ file.name }}
              <button
                aria-label="移除文件"
                class="composer-file-remove"
                title="移除"
                type="button"
                @click="removeAttachment(index)"
              >
                ×
              </button>
            </span>
          </div>

          <textarea
            ref="textareaRef"
            v-model="input"
            class="composer-input"
            :placeholder="placeholder"
            rows="3"
            @keydown="handleKeydown"
          ></textarea>

          <p v-if="fileError" class="composer-error">{{ fileError }}</p>

          <div class="composer-bar">
            <button
              aria-label="上传文件"
              class="composer-tool"
              title="上传文件"
              type="button"
              @click="pickFile"
            >
              <svg aria-hidden="true" viewBox="0 0 24 24">
                <path
                  d="m8.5 12.5 5.8-5.8a3 3 0 0 1 4.2 4.2l-7.7 7.7a5 5 0 0 1-7.1-7.1l8-8a3.5 3.5 0 0 1 5 5l-7.4 7.4a2 2 0 0 1-2.8-2.8l6.4-6.4"
                />
              </svg>
            </button>

            <div class="composer-chips">
              <button
                v-for="chip in quickChips"
                :key="chip.label"
                class="composer-chip"
                type="button"
                @click="submit(chip.prompt)"
              >
                {{ chip.label }}
              </button>
            </div>

            <button
              aria-label="发送"
              class="composer-send"
              :disabled="!canSend"
              title="发送"
              type="submit"
            >
              <svg aria-hidden="true" viewBox="0 0 24 24">
                <path d="m22 2-7 20-4-9-9-4Z" />
                <path d="M22 2 11 13" />
              </svg>
            </button>
          </div>

          <input
            ref="fileInputRef"
            class="composer-file-input"
            type="file"
            @change="onFileChange"
          />
        </form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.portal-chat {
  position: relative;
  display: flex;
  width: 100%;
  height: calc(100vh - var(--portal-header-height, 64px));
  min-height: calc(100vh - var(--portal-header-height, 64px));
  overflow: hidden;
  background: #fff;
}

.portal-chat--chat {
  height: calc(100dvh - var(--portal-header-height, 64px));
  min-height: calc(100dvh - var(--portal-header-height, 64px));
}
/* ========== 侧边栏 ========== */
.chat-aside {
  display: flex;
  width: 264px;
  flex: 0 0 264px;
  flex-direction: column;
  border-right: 1px solid #e8eceb;
  background: #f7f9f9;
}

.aside-head {
  display: flex;
  align-items: center;
  min-height: 56px;
  padding: 0 16px;
}

.aside-brand {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #17343d;
  font-size: 14px;
  font-weight: 650;
}

.aside-brand-logo {
  width: 26px;
  height: 26px;
  object-fit: contain;
}

.aside-new {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  margin: 0 12px 12px;
  padding: 10px 12px;
  border: 1px solid #0f8f8c;
  border-radius: 10px;
  background: #fff;
  color: #0f8f8c;
  cursor: pointer;
  font-size: 13px;
  font-weight: 650;
}

.aside-new:hover {
  background: #eef8f7;
}

.aside-new svg {
  width: 15px;
  height: 15px;
  fill: none;
  stroke: currentcolor;
  stroke-linecap: round;
  stroke-width: 2;
}

.aside-list {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
  padding: 0 8px 12px;
}

.aside-group {
  margin: 10px 8px 6px;
  color: #9aa8a6;
  font-size: 11px;
  letter-spacing: 0.04em;
}

.aside-item {
  display: flex;
  align-items: center;
  margin-bottom: 2px;
  border-radius: 9px;
}

.aside-item:hover {
  background: #eceff0;
}

.aside-item--active {
  background: #e2f1ef;
}

.aside-item-main {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  padding: 9px 6px 9px 12px;
  border: 0;
  background: transparent;
  color: #33484d;
  cursor: pointer;
  font-size: 13px;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.aside-item--active .aside-item-main {
  color: #0f8f8c;
  font-weight: 650;
}

.aside-item-remove {
  width: 26px;
  height: 26px;
  flex: 0 0 26px;
  margin-right: 4px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #9aa8a6;
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  opacity: 0;
}

.aside-item:hover .aside-item-remove,
.aside-item-remove:focus-visible {
  opacity: 1;
}

.aside-item-remove:hover {
  background: #fdeceb;
  color: #d9483f;
}

.aside-empty {
  margin: 18px 12px;
  color: #9aa8a6;
  font-size: 12px;
}

.aside-foot {
  padding: 12px 16px;
  border-top: 1px solid #e8eceb;
}

.aside-user {
  color: #5c7075;
  font-size: 12px;
}
/* ========== 主区域 ========== */
.chat-main {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

.portal-chat--welcome .chat-main {
  align-items: center;
  justify-content: center;
  padding: 0 24px;
}

.welcome {
  display: flex;
  width: min(720px, 100%);
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.welcome-brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
}

.welcome-logo {
  width: 44px;
  height: 44px;
  flex: 0 0 44px;
  object-fit: contain;
}

.welcome-title {
  margin: 0;
  color: #17343d;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.01em;
  line-height: 1.4;
}

.welcome-slogan {
  margin: 12px 0 0;
  color: #3d585d;
  font-size: 18px;
  font-weight: 500;
  line-height: 1.5;
}

.welcome-desc {
  max-width: 640px;
  margin: 16px 0 0;
  color: #8697a1;
  font-size: 13px;
  line-height: 1.9;
}

.welcome-recent {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 20px;
}

.welcome-recent-label {
  color: #9aa8a6;
  font-size: 12px;
}

.welcome-recent-item {
  max-width: 220px;
  overflow: hidden;
  padding: 5px 12px;
  border: 1px solid #e6ecec;
  border-radius: 15px;
  background: #fff;
  color: #5c7075;
  cursor: pointer;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.welcome-recent-item:hover {
  border-color: #0f8f8c;
  color: #0f8f8c;
}

.chat-head {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 56px;
  padding: 0 22px;
  border-bottom: 1px solid #e8eceb;
  background: #fff;
}

.chat-head-toggle {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  border: 1px solid #e2e8e8;
  border-radius: 8px;
  background: #fff;
  color: #5c7075;
  cursor: pointer;
}

.chat-head-toggle svg {
  width: 16px;
  height: 16px;
  fill: none;
  stroke: currentcolor;
  stroke-linecap: round;
  stroke-width: 2;
}

.chat-head-title {
  overflow: hidden;
  color: #17343d;
  font-size: 15px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-head-sub {
  margin-left: auto;
  color: #9aa8a6;
  font-size: 12px;
}

.chat-thread {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
  padding: 18px 22px 6px;
  background: #f2f4f5;
}

.thread-hint {
  padding: 64px 0;
  color: #9aa8a6;
  text-align: center;
}

.thread-hint p {
  margin: 0 0 8px;
  color: #5c7075;
  font-size: 15px;
  font-weight: 650;
}

.thread-hint span {
  font-size: 12px;
}

.thread-divider {
  margin: 14px 0;
  text-align: center;
}

.thread-divider span {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 8px;
  background: rgb(0 0 0 / 15%);
  color: #fff;
  font-size: 11px;
}

.thread-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 18px;
}

.thread-row--user {
  flex-direction: row-reverse;
}

.thread-avatar {
  width: 38px;
  height: 38px;
  flex: 0 0 38px;
  border-radius: 6px;
  background: #fff;
  box-shadow: 0 1px 3px rgb(23 52 61 / 12%);
  object-fit: contain;
}

.thread-avatar--me {
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #0f9f9b, #2f80ed);
  box-shadow: none;
  color: #fff;
  font-size: 15px;
  font-weight: 700;
}
.bubble {
  position: relative;
  max-width: min(680px, 68%);
  padding: 10px 14px;
  border-radius: 6px;
  font-size: 14px;
  line-height: 1.75;
  word-break: break-word;
}

.bubble--assistant {
  border: 1px solid #e4e9e9;
  background: #fff;
  color: #22343a;
}

.bubble--user {
  background: #95ec69;
  color: #17343d;
}

.bubble--assistant::before,
.bubble--user::before {
  position: absolute;
  top: 12px;
  width: 0;
  height: 0;
  border: 6px solid transparent;
  content: '';
}

.bubble--assistant::before {
  left: -13px;
  border-right-color: #e4e9e9;
}

.bubble--assistant::after {
  position: absolute;
  top: 13px;
  left: -11px;
  width: 0;
  height: 0;
  border: 5px solid transparent;
  border-right-color: #fff;
  content: '';
}

.bubble--user::before {
  right: -13px;
  border-left-color: #95ec69;
}

.bubble-text {
  white-space: pre-wrap;
}

.bubble-files {
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
}

.bubble-files li {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 9px;
  border-radius: 6px;
  background: rgb(0 0 0 / 5%);
  font-size: 12px;
}

.bubble-files li + li {
  margin-top: 5px;
}

.bubble-files li span {
  overflow: hidden;
  flex: 1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bubble-files li em {
  color: #7b8b8f;
  font-size: 11px;
  font-style: normal;
}

.bubble-files svg {
  width: 13px;
  height: 13px;
  flex: 0 0 13px;
  fill: none;
  stroke: currentcolor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
}

.bubble-action {
  display: grid;
  width: 100%;
  gap: 3px;
  margin-top: 10px;
  padding: 10px 12px;
  border: 1px solid #cfe6e2;
  border-radius: 8px;
  background: #f6fbfa;
  cursor: pointer;
  text-align: left;
}

.bubble-action:hover {
  border-color: #0f8f8c;
  background: #eef8f7;
}

.bubble-action-label {
  color: #0f8f8c;
  font-size: 13px;
  font-weight: 700;
}

.bubble-action-desc {
  color: #6b7d81;
  font-size: 12px;
  line-height: 1.6;
}

.bubble-action-go {
  color: #0f8f8c;
  font-size: 12px;
  font-weight: 650;
}

.bubble-links {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.bubble-link {
  padding: 5px 10px;
  border: 1px solid #cfe6e2;
  border-radius: 14px;
  background: #f6fbfa;
  color: #0f8f8c;
  cursor: pointer;
  font-size: 12px;
}

.bubble-link:hover {
  border-color: #0f8f8c;
  background: #eef8f7;
}

.bubble--typing {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 15px 16px;
}

.bubble--typing i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  animation: typing 1.2s infinite ease-in-out;
  background: #b6c4c2;
}

.bubble--typing i:nth-child(2) {
  animation-delay: 0.18s;
}

.bubble--typing i:nth-child(3) {
  animation-delay: 0.36s;
}

@keyframes typing {
  0%,
  60%,
  100% {
    opacity: 0.35;
    transform: translateY(0);
  }

  30% {
    opacity: 1;
    transform: translateY(-3px);
  }
}
/* ========== 输入区 ========== */
.chat-composer {
  display: flex;
  flex: 0 0 auto;
  justify-content: center;
  padding: 14px 22px 18px;
  background: #fff;
}

.portal-chat--welcome .chat-composer {
  width: min(720px, 100%);
  margin-top: 30px;
  padding: 0;
}

.composer {
  width: min(880px, 100%);
  padding: 12px 14px 10px;
  border: 1px solid #e2e8e8;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 6px 22px rgb(25 45 55 / 8%);
}

.composer--chat {
  border-radius: 12px;
}

/* 固定 3 行高（3 × 1.6em 行高 + 上下内边距），超出后由 textarea 自身滚动 */
.composer-input {
  width: 100%;
  height: calc(1.6em * 3 + 12px);
  overflow-x: hidden;
  overflow-y: auto;
  padding: 4px 4px 8px;
  border: 0;
  outline: 0;
  background: transparent;
  color: #17343d;
  font: inherit;
  font-size: 14px;
  line-height: 1.6;
  resize: none;
}

.composer-input::placeholder {
  color: #a8b6b6;
}

.composer-error {
  margin: 0 4px 6px;
  color: #d9483f;
  font-size: 12px;
}

.composer-files {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.composer-file {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 6px 4px 10px;
  border-radius: 14px;
  background: #eef4f4;
  color: #48605f;
  font-size: 12px;
}

.composer-file-remove {
  width: 18px;
  height: 18px;
  border: 0;
  border-radius: 50%;
  background: rgb(0 0 0 / 6%);
  color: #5c7075;
  cursor: pointer;
  font-size: 13px;
  line-height: 1;
}

.composer-file-remove:hover {
  background: #fdeceb;
  color: #d9483f;
}

.composer-bar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.composer-tool {
  display: grid;
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  place-items: center;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: #71817f;
  cursor: pointer;
}

.composer-tool:hover {
  background: #f1f5f4;
  color: #0f8f8c;
}

.composer-tool svg,
.composer-send svg {
  fill: none;
  stroke: currentcolor;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.composer-tool svg {
  width: 17px;
  height: 17px;
  stroke-width: 1.9;
}

.composer-chips {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-wrap: wrap;
  gap: 6px;
}

.composer-chip {
  padding: 5px 12px;
  border: 1px solid #e2e8e8;
  border-radius: 15px;
  background: #f8fafa;
  color: #48605f;
  cursor: pointer;
  font-size: 12px;
  white-space: nowrap;
}

.composer-chip:hover {
  border-color: #0f8f8c;
  background: #eef8f7;
  color: #0f8f8c;
}

.composer-send {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  place-items: center;
  border: 0;
  border-radius: 50%;
  background: #0f8f8c;
  color: #fff;
  cursor: pointer;
}

.composer-send svg {
  width: 17px;
  height: 17px;
  stroke-width: 2;
}

.composer-send:hover:not(:disabled) {
  background: #0b777c;
}

.composer-send:disabled {
  background: #e3e9e9;
  color: #a5b2b4;
  cursor: not-allowed;
}

.composer-file-input {
  display: none;
}

.chat-scrim {
  position: absolute;
  z-index: 25;
  background: rgb(20 40 45 / 32%);
  inset: 0;
}

/* ========== 窄屏 ========== */
@media (max-width: 720px) {
  .chat-aside {
    position: absolute;
    z-index: 30;
    height: 100%;
    transform: translateX(-100%);
    transition: transform 200ms ease;
  }

  .portal-chat--sidebar .chat-aside {
    box-shadow: 0 12px 40px rgb(23 52 61 / 18%);
    transform: none;
  }

  .chat-thread {
    padding: 14px 12px 6px;
  }

  .bubble {
    max-width: 82%;
  }

  .chat-composer {
    padding: 10px 12px 14px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .chat-aside,
  .bubble--typing i {
    animation: none;
    transition: none;
  }
}
</style>
