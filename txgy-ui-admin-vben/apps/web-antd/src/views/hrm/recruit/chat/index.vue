<script lang="ts" setup>
import type { HrmPortalRecruitChatApi } from '#/api/hrm/portal/recruit/chat';

import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue';

import { confirm, Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import { message } from 'ant-design-vue';

import {
  deleteAdminRecruitChatConversation,
  deleteAdminRecruitChatConversationList,
  getAdminRecruitChatConversationList,
  getAdminRecruitChatMessageList,
  markAdminRecruitChatRead,
  sendAdminRecruitChatMessage,
} from '#/api/hrm/portal/recruit/chat';

type Conversation = HrmPortalRecruitChatApi.Conversation;
type ChatMessage = HrmPortalRecruitChatApi.ChatMessage;
type FilterKey = 'all' | 'replied' | 'submitted' | 'waiting';
type ProfileFilterState = {
  education: string;
  graduateSchool: string;
  postName: string;
  sex: string;
  workTime: string;
};

const conversations = ref<Conversation[]>([]);
const selected = ref<Conversation>();
const chatMessages = ref<ChatMessage[]>([]);
const draft = ref('');
const searchText = ref('');
const activeFilter = ref<FilterKey>('all');
const loading = ref(false);
const messagesLoading = ref(false);
const sending = ref(false);
const chatOpen = ref(false);
const selectedIds = ref<number[]>([]);
const deletingId = ref<number>();
const batchDeleting = ref(false);
const currentPage = ref(1);
const pageSize = ref(10);
const conversationViewport = ref<HTMLElement>();
const horizontalScrollbar = ref<HTMLElement>();
const tableScrollWidth = ref(1360);
const profileFilters = reactive<ProfileFilterState>({
  education: '',
  graduateSchool: '',
  postName: '',
  sex: '',
  workTime: '',
});
let pollTimer: ReturnType<typeof setInterval> | undefined;
let polling = false;
let conversationListRequestId = 0;
let messageRequestId = 0;

const UNKNOWN_FILTER_VALUE = '__unknown__';

const educationMap: Record<number, string> = {
  1: '小学',
  2: '初中',
  3: '高中',
  4: '大专',
  5: '本科',
  6: '硕士',
  7: '博士',
};

const filterOptions = computed(() => [
  { key: 'all' as const, label: '全部会话', count: conversations.value.length },
  {
    key: 'waiting' as const,
    label: '待回复',
    count: conversations.value.filter((item) => item.status === 1).length,
  },
  {
    key: 'replied' as const,
    label: '已回复',
    count: conversations.value.filter((item) => item.status === 2).length,
  },
  {
    key: 'submitted' as const,
    label: '已投递',
    count: conversations.value.filter((item) => item.applicationStatus === 'SUBMITTED').length,
  },
]);

const postFilterOptions = computed(() =>
  [...new Set(conversations.value.map((item) => item.postName).filter(Boolean) as string[])].sort(
    (left, right) => left.localeCompare(right, 'zh-CN'),
  ),
);

const educationFilterOptions = computed(() => {
  const values = [...new Set(conversations.value.map((item) => educationText(item)))];
  const options = values
    .filter((value) => value !== '—')
    .sort((left, right) => left.localeCompare(right, 'zh-CN'))
    .map((value) => ({ value, label: value }));
  if (values.includes('—')) {
    options.push({ value: UNKNOWN_FILTER_VALUE, label: '未填写' });
  }
  return options;
});

const workTimeFilterOptions = computed(() => {
  const values = [
    ...new Set(
      conversations.value
        .map((item) => item.candidateWorkTime)
        .filter((value): value is number => value !== undefined && value !== null),
    ),
  ].sort((left, right) => left - right);
  const options = values.map((value) => ({
    value: String(value),
    label: workTimeText(value),
  }));
  if (conversations.value.some((item) => item.candidateWorkTime === undefined || item.candidateWorkTime === null)) {
    options.push({ value: UNKNOWN_FILTER_VALUE, label: '未填写' });
  }
  return options;
});

const profileFilterCount = computed(
  () =>
    [
      profileFilters.postName,
      profileFilters.sex,
      profileFilters.education,
      profileFilters.graduateSchool,
      profileFilters.workTime,
    ].filter(Boolean).length,
);

const filteredConversations = computed(() => {
  const keyword = searchText.value.trim().toLowerCase();
  return conversations.value.filter((item) => {
    const matchesFilter =
      activeFilter.value === 'all' ||
      (activeFilter.value === 'waiting' && item.status === 1) ||
      (activeFilter.value === 'replied' && item.status === 2) ||
      (activeFilter.value === 'submitted' && item.applicationStatus === 'SUBMITTED');
    if (!matchesFilter) return false;
    if (!matchesProfileFilters(item)) return false;
    if (!keyword) return true;
    return `${candidateName(item)} ${item.postName || ''} ${item.candidateGraduateSchool || ''} ${workTimeText(item.candidateWorkTime)} ${item.lastContent || ''}`
      .toLowerCase()
      .includes(keyword);
  });
});

const pageCount = computed(() =>
  Math.max(1, Math.ceil(filteredConversations.value.length / pageSize.value)),
);

const pageNumbers = computed(() => {
  const count = Math.min(5, pageCount.value);
  const start = Math.max(
    1,
    Math.min(currentPage.value - 2, pageCount.value - count + 1),
  );
  return Array.from({ length: count }, (_, index) => start + index);
});

const pagedConversations = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  return filteredConversations.value.slice(start, start + pageSize.value);
});

const visibleIds = computed(() =>
  pagedConversations.value.map((item) => item.id).filter(Boolean),
);

const allVisibleSelected = computed(
  () =>
    visibleIds.value.length > 0 &&
    visibleIds.value.every((id) => selectedIds.value.includes(id)),
);

const someVisibleSelected = computed(
  () =>
    visibleIds.value.some((id) => selectedIds.value.includes(id)) &&
    !allVisibleSelected.value,
);

const canReply = computed(
  () => selected.value?.status !== 3,
);

function candidateName(item: Conversation) {
  return item.candidateName || item.peerUserName || '候选人';
}

function candidateInitial(item: Conversation) {
  return candidateName(item).slice(0, 1);
}

function sexText(item: Conversation) {
  if (item.candidateSex === 1) return '男';
  if (item.candidateSex === 2) return '女';
  return '—';
}

function educationText(item: Conversation) {
  return item.candidateEducationName || educationMap[item.candidateEducation || 0] || '—';
}

function workTimeText(value?: number) {
  if (value === undefined || value === null) return '—';
  return value <= 0 ? '无经验' : `${value} 年`;
}

function imageMeta(content: string) {
  const [prefix, encodedName, fileUrl] = content.split('|');
  if (prefix !== '__HRM_IMAGE__' || !fileUrl) return undefined;
  let fileName = '聊天图片';
  if (encodedName) {
    try {
      fileName = decodeURIComponent(encodedName);
    } catch {
      fileName = encodedName;
    }
  }
  return { fileName, fileUrl };
}

function previewContent(content?: string) {
  if (!content) return '暂无消息';
  return imageMeta(content) ? '[图片]' : content;
}

function matchesProfileFilters(item: Conversation) {
  const school = item.candidateGraduateSchool || '';
  const education = educationText(item);
  const matchesEducation =
    !profileFilters.education ||
    (profileFilters.education === UNKNOWN_FILTER_VALUE
      ? education === '—'
      : education === profileFilters.education);
  const matchesWorkTime =
    !profileFilters.workTime ||
    (profileFilters.workTime === UNKNOWN_FILTER_VALUE
      ? item.candidateWorkTime === undefined || item.candidateWorkTime === null
      : String(item.candidateWorkTime) === profileFilters.workTime);
  return (
    (!profileFilters.postName || item.postName === profileFilters.postName) &&
    (!profileFilters.sex || String(item.candidateSex ?? '') === profileFilters.sex) &&
    matchesEducation &&
    (!profileFilters.graduateSchool || school.toLowerCase().includes(profileFilters.graduateSchool.trim().toLowerCase())) &&
    matchesWorkTime
  );
}

function resetProfileFilters() {
  profileFilters.postName = '';
  profileFilters.sex = '';
  profileFilters.education = '';
  profileFilters.graduateSchool = '';
  profileFilters.workTime = '';
}

function goToPage(page: number) {
  currentPage.value = Math.min(Math.max(page, 1), pageCount.value);
}

function syncHorizontalFromViewport() {
  if (horizontalScrollbar.value && conversationViewport.value) {
    horizontalScrollbar.value.scrollLeft = conversationViewport.value.scrollLeft;
  }
}

function syncViewportFromHorizontal() {
  if (horizontalScrollbar.value && conversationViewport.value) {
    conversationViewport.value.scrollLeft = horizontalScrollbar.value.scrollLeft;
  }
}

function refreshHorizontalScrollbar() {
  void nextTick(() => {
    if (!conversationViewport.value) return;
    tableScrollWidth.value = Math.max(
      1360,
      conversationViewport.value.scrollWidth,
      conversationViewport.value.clientWidth,
    );
    syncHorizontalFromViewport();
  });
}

function ageText(item: Conversation) {
  return item.candidateAge ? `${item.candidateAge} 岁` : '—';
}

function statusText(status?: number) {
  if (status === 1) return '等待回复';
  if (status === 2) return '招聘方已回复';
  if (status === 3) return '沟通失败';
  return '等待回复';
}

function statusClass(status?: number) {
  if (status === 2) return 'status-replied';
  if (status === 3) return 'status-failed';
  return 'status-waiting';
}

function messageContent(item: ChatMessage) {
  if (item.contentType === 2) {
    return `简历：${item.content.split('|')[0] || '附件简历'}`;
  }
  return item.content;
}

function formatTime(value?: string) {
  if (!value) return '暂无时间';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const pad = (number: number) => String(number).padStart(2, '0');
  const now = new Date();
  const time = `${pad(date.getHours())}:${pad(date.getMinutes())}`;
  if (date.toDateString() === now.toDateString()) return `今天 ${time}`;
  return `${date.getMonth() + 1}月${date.getDate()}日 ${time}`;
}

async function loadConversations(silent = false) {
  const requestId = ++conversationListRequestId;
  loading.value = true;
  try {
    const result = await getAdminRecruitChatConversationList();
    if (requestId !== conversationListRequestId) return;
    conversations.value = (result || []).filter((item) => item.lastTime);
    const availableIds = new Set(conversations.value.map((item) => item.id));
    selectedIds.value = selectedIds.value.filter((id) => availableIds.has(id));
    if (selected.value) {
      const current = conversations.value.find((item) => item.id === selected.value?.id);
      selected.value = current;
      if (!current && chatOpen.value) closeChat();
    }
  } catch (error: any) {
    if (!silent) message.error(error?.msg || error?.message || '招聘沟通列表加载失败');
  } finally {
    if (requestId === conversationListRequestId) loading.value = false;
    refreshHorizontalScrollbar();
  }
}

function updateSelection(id: number, event: Event) {
  const checked = (event.target as HTMLInputElement).checked;
  selectedIds.value = checked
    ? [...new Set([...selectedIds.value, id])]
    : selectedIds.value.filter((selectedId) => selectedId !== id);
}

function updateAllSelection(event: Event) {
  const checked = (event.target as HTMLInputElement).checked;
  if (checked) {
    selectedIds.value = [...new Set([...selectedIds.value, ...visibleIds.value])];
    return;
  }
  const visibleIdSet = new Set(visibleIds.value);
  selectedIds.value = selectedIds.value.filter((id) => !visibleIdSet.has(id));
}

async function loadMessages(conversationId: number, append = false) {
  const requestId = ++messageRequestId;
  if (!append) messagesLoading.value = true;
  try {
    const result =
      (await getAdminRecruitChatMessageList({
        conversationId,
        minId: append ? chatMessages.value.at(-1)?.id : undefined,
        limit: 200,
      })) || [];
    if (requestId !== messageRequestId || selected.value?.id !== conversationId) return;
    if (append) {
      const knownIds = new Set(chatMessages.value.map((item) => item.id));
      chatMessages.value.push(...result.filter((item) => !knownIds.has(item.id)));
    } else {
      chatMessages.value = result;
    }
  } catch (error: any) {
    if (!append) message.error(error?.msg || error?.message || '消息加载失败');
  } finally {
    if (requestId === messageRequestId) messagesLoading.value = false;
  }
}

async function openChat(item: Conversation) {
  selected.value = item;
  chatMessages.value = [];
  draft.value = '';
  chatOpen.value = true;
  await loadMessages(item.id);
  if (selected.value?.id !== item.id) return;
  await markAdminRecruitChatRead(item.id);
  if (selected.value?.id !== item.id) return;
  item.unreadCount = 0;
}

function closeChat() {
  chatOpen.value = false;
}

async function send() {
  const conversation = selected.value;
  const content = draft.value.trim();
  if (!conversation || !content || sending.value || !canReply.value) return;
  const conversationId = conversation.id;
  sending.value = true;
  try {
    const sent = await sendAdminRecruitChatMessage({
      conversationId,
      contentType: 1,
      content,
    });
    if (selected.value?.id === conversationId) {
      if (sent) chatMessages.value.push(sent);
      draft.value = '';
    }
    await loadConversations(true);
    message.success('回复已发送');
  } catch (error: any) {
    message.error(error?.msg || error?.message || '发送失败');
  } finally {
    sending.value = false;
  }
}

async function handleDelete(item: Conversation) {
  if (deletingId.value || batchDeleting.value) return;
  try {
    await confirm(`确定删除与“${candidateName(item)}”的沟通会话吗？`);
    deletingId.value = item.id;
    await deleteAdminRecruitChatConversation(item.id);
    selectedIds.value = selectedIds.value.filter((id) => id !== item.id);
    if (selected.value?.id === item.id) closeChat();
    message.success('沟通会话已删除');
    await loadConversations(true);
  } catch {
  } finally {
    deletingId.value = undefined;
  }
}

async function handleBatchDelete() {
  if (!selectedIds.value.length || deletingId.value || batchDeleting.value) return;
  const ids = [...selectedIds.value];
  try {
    await confirm(`确定删除选中的 ${ids.length} 个沟通会话吗？`);
    batchDeleting.value = true;
    await deleteAdminRecruitChatConversationList(ids);
    if (selected.value && ids.includes(selected.value.id)) closeChat();
    selectedIds.value = [];
    message.success(`已删除 ${ids.length} 个沟通会话`);
    await loadConversations(true);
  } catch {
  } finally {
    batchDeleting.value = false;
  }
}

async function poll() {
  if (polling || deletingId.value || batchDeleting.value) return;
  polling = true;
  try {
    await loadConversations(true);
    const conversationId = selected.value?.id;
    if (chatOpen.value && conversationId) {
      await loadMessages(conversationId, true);
    }
  } finally {
    polling = false;
  }
}

onMounted(() => {
  document.documentElement.classList.add('hrm-recruit-chat-html');
  document.body.classList.add('hrm-recruit-chat-body');
  void loadConversations();
  refreshHorizontalScrollbar();
  pollTimer = setInterval(() => void poll(), 4000);
});

watch(
  [
    activeFilter,
    searchText,
    () => profileFilters.postName,
    () => profileFilters.sex,
    () => profileFilters.education,
    () => profileFilters.graduateSchool,
    () => profileFilters.workTime,
  ],
  () => {
    currentPage.value = 1;
    refreshHorizontalScrollbar();
  },
);

watch([pageCount, pageSize], () => {
  if (currentPage.value > pageCount.value) currentPage.value = pageCount.value;
  refreshHorizontalScrollbar();
});

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer);
  document.documentElement.classList.remove('hrm-recruit-chat-html');
  document.body.classList.remove('hrm-recruit-chat-body');
});
</script>

<template>
  <Page content-class="hrm-recruit-chat-page">
    <div class="chat-workspace">
      <section class="chat-header">
        <div class="chat-heading">
          <div class="chat-eyebrow"><span class="live-dot"></span>招聘沟通 · 实时收件箱</div>
          <h1>和合适的人选，聊得更深入</h1>
        </div>
        <div class="chat-metrics" aria-label="沟通概览">
          <div><strong>{{ conversations.length }}</strong><span>全部求职者</span></div>
          <div><strong>{{ filterOptions[1]?.count || 0 }}</strong><span>待我回复</span></div>
          <div><strong>{{ filterOptions[3]?.count || 0 }}</strong><span>已投递</span></div>
        </div>
      </section>

      <section class="chat-panel">
          <div class="chat-panel-toolbar">
          <div class="filter-tabs" role="tablist" aria-label="沟通状态筛选">
            <button
              v-for="filter in filterOptions"
              :key="filter.key"
              :class="{ active: activeFilter === filter.key }"
              type="button"
              role="tab"
              :aria-selected="activeFilter === filter.key"
              @click="activeFilter = filter.key"
            >
              {{ filter.label }}<b>{{ filter.count }}</b>
            </button>
          </div>
          <div class="toolbar-right">
            <div class="selection-tools">
              <span v-if="selectedIds.length" class="selection-count">已选择 {{ selectedIds.length }} 个</span>
              <button class="bulk-delete-action" type="button" :disabled="!selectedIds.length || batchDeleting" @click="handleBatchDelete">
                <IconifyIcon icon="lucide:trash-2" />批量删除
              </button>
            </div>
            <div class="chat-search">
              <IconifyIcon icon="lucide:search" />
              <input v-model="searchText" aria-label="搜索候选人或职位" placeholder="搜索候选人、职位或消息" />
              <button v-if="searchText" type="button" aria-label="清空搜索" @click="searchText = ''"><IconifyIcon icon="lucide:x" /></button>
            </div>
          </div>
        </div>

        <div class="profile-filter-bar" aria-label="求职者资料筛选">
          <div class="profile-filter-heading">
            <span class="profile-filter-icon"><IconifyIcon icon="lucide:sliders-horizontal" /></span>
            <div><strong>求职者筛选</strong></div>
          </div>
          <div class="profile-filter-fields">
            <label class="profile-filter-field">
              <span>应聘职位</span>
              <select v-model="profileFilters.postName" aria-label="按应聘职位筛选">
                <option value="">全部职位</option>
                <option v-for="postName in postFilterOptions" :key="postName" :value="postName">{{ postName }}</option>
              </select>
            </label>
            <label class="profile-filter-field">
              <span>性别</span>
              <select v-model="profileFilters.sex" aria-label="按性别筛选">
                <option value="">全部性别</option>
                <option value="1">男</option>
                <option value="2">女</option>
              </select>
            </label>
            <label class="profile-filter-field">
              <span>学历</span>
              <select v-model="profileFilters.education" aria-label="按学历筛选">
                <option value="">全部学历</option>
                <option v-for="option in educationFilterOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
            </label>
            <label class="profile-filter-field school-filter-field">
              <span>毕业院校</span>
              <input v-model="profileFilters.graduateSchool" aria-label="按毕业院校筛选" placeholder="输入院校名称" />
            </label>
            <label class="profile-filter-field">
              <span>工作经验</span>
              <select v-model="profileFilters.workTime" aria-label="按工作经验筛选">
                <option value="">全部经验</option>
                <option v-for="option in workTimeFilterOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
            </label>
            <button class="reset-filter-action" type="button" :disabled="!profileFilterCount" @click="resetProfileFilters">
              <IconifyIcon icon="lucide:rotate-ccw" />重置{{ profileFilterCount ? `（${profileFilterCount}）` : '' }}
            </button>
          </div>
        </div>

        <div ref="conversationViewport" class="conversation-viewport" @scroll="syncHorizontalFromViewport">
          <div class="conversation-table" role="table" aria-label="招聘沟通列表">
            <div class="conversation-row conversation-head" role="row">
            <div role="columnheader" class="name-heading">
              <input
                class="selection-checkbox"
                type="checkbox"
                aria-label="全选当前会话"
                :checked="allVisibleSelected"
                :indeterminate="someVisibleSelected"
                @change="updateAllSelection"
              />
              <span>名字</span>
            </div>
            <div role="columnheader">应聘职位</div>
            <div role="columnheader">性别</div>
            <div role="columnheader">年龄</div>
            <div role="columnheader">学历</div>
            <div role="columnheader">毕业院校</div>
            <div role="columnheader">工作经验</div>
            <div role="columnheader">发来的消息</div>
            <div role="columnheader" class="action-heading">操作</div>
            </div>

            <div v-if="loading && !conversations.length" class="table-state">正在加载沟通会话…</div>
            <div v-else-if="!filteredConversations.length" class="table-state table-empty">
              <span class="empty-icon"><IconifyIcon icon="lucide:messages-square" /></span>
              <strong>{{ searchText || profileFilterCount ? '没有找到匹配的沟通' : '还没有招聘沟通' }}</strong>
              <p>{{ searchText || profileFilterCount ? '调整筛选条件或搜索关键词后重试' : '求职者发送消息后，会话会显示在这里' }}</p>
            </div>

            <div
              v-for="item in pagedConversations"
              :key="item.id"
              class="conversation-row conversation-data-row"
              :class="{ selected: selected?.id === item.id }"
              role="row"
            >
              <div class="candidate-cell" role="cell">
                <input
                  class="selection-checkbox"
                  type="checkbox"
                  :aria-label="`选择${candidateName(item)}`"
                  :checked="selectedIds.includes(item.id)"
                  @click.stop
                  @change="updateSelection(item.id, $event)"
                />
                <span class="candidate-avatar">
                  <img v-if="item.peerUserAvatar" :src="item.peerUserAvatar" alt="" />
                  <span v-else>{{ candidateInitial(item) }}</span>
                </span>
                <span class="candidate-copy">
                  <strong>{{ candidateName(item) }}</strong>
                  <small class="status-label" :class="[statusClass(item.status)]"><i></i>{{ statusText(item.status) }}</small>
                </span>
                <em v-if="item.unreadCount" class="unread-count">{{ item.unreadCount > 99 ? '99+' : item.unreadCount }}</em>
              </div>
              <div class="post-cell" role="cell"><strong>{{ item.postName || '招聘职位' }}</strong><small v-if="item.areaName">{{ item.areaName }}</small></div>
              <div class="muted-cell" role="cell">{{ sexText(item) }}</div>
              <div class="muted-cell" role="cell">{{ ageText(item) }}</div>
              <div class="muted-cell" role="cell">{{ educationText(item) }}</div>
              <div class="school-cell" role="cell" :title="item.candidateGraduateSchool || ''">{{ item.candidateGraduateSchool || '—' }}</div>
              <div class="muted-cell experience-cell" role="cell">{{ workTimeText(item.candidateWorkTime) }}</div>
              <div class="last-message-cell" role="cell">
                <span v-if="item.lastContent" class="message-sender">{{ item.lastSenderUserId === item.candidateUserId ? '候选人' : '我' }}</span>
                <p>{{ previewContent(item.lastContent) }}</p>
                <time>{{ formatTime(item.lastTime) }}</time>
              </div>
              <div class="row-action" role="cell">
                <button class="chat-action" type="button" @click="openChat(item)">
                  <IconifyIcon icon="lucide:message-circle-more" />立即沟通
                </button>
                <button
                  class="delete-action"
                  type="button"
                  :disabled="deletingId === item.id || batchDeleting"
                  @click="handleDelete(item)"
                >
                  <IconifyIcon icon="lucide:trash-2" />删除
                </button>
              </div>
            </div>
          </div>
        </div>

        <div
          ref="horizontalScrollbar"
          class="conversation-horizontal-scroll"
          aria-label="招聘沟通列表横向滚动条"
          @scroll="syncViewportFromHorizontal"
        >
          <div class="conversation-horizontal-track" :style="{ width: `${tableScrollWidth}px` }"></div>
        </div>

        <footer class="chat-panel-footer">
          <span>共 {{ filteredConversations.length }} 位求职者</span>
          <div class="table-pagination" aria-label="求职者分页">
            <label class="page-size-picker">
              <span>每页</span>
              <select v-model.number="pageSize" aria-label="每页显示数量">
                <option :value="10">10条/页</option>
                <option :value="20">20条/页</option>
                <option :value="50">50条/页</option>
              </select>
            </label>
            <span class="page-indicator">第 {{ currentPage }} / {{ pageCount }} 页</span>
            <button type="button" :disabled="currentPage <= 1" @click="goToPage(1)">首页</button>
            <button type="button" :disabled="currentPage <= 1" @click="goToPage(currentPage - 1)">上一页</button>
            <button
              v-for="page in pageNumbers"
              :key="page"
              type="button"
              :class="{ active: currentPage === page }"
              @click="goToPage(page)"
            >
{{ page }}
</button>
            <button type="button" :disabled="currentPage >= pageCount" @click="goToPage(currentPage + 1)">下一页</button>
            <button type="button" :disabled="currentPage >= pageCount" @click="goToPage(pageCount)">末页</button>
          </div>
        </footer>
      </section>
    </div>

    <a-modal
      v-model:open="chatOpen"
      :footer="null"
      :title="null"
      :width="920"
      :mask-closable="false"
      wrap-class-name="hrm-recruit-chat-modal"
      @cancel="closeChat"
    >
      <div v-if="selected" class="dialog-card">
        <header class="dialog-header">
          <div class="dialog-person">
            <span class="dialog-avatar">
              <img v-if="selected.peerUserAvatar" :src="selected.peerUserAvatar" alt="" />
              <span v-else>{{ candidateInitial(selected) }}</span>
            </span>
            <div>
              <div class="dialog-name-line"><h2>{{ candidateName(selected) }}</h2><span class="dialog-status" :class="[statusClass(selected.status)]"><i></i>{{ statusText(selected.status) }}</span></div>
              <p>应聘 <b>{{ selected.postName || '招聘职位' }}</b><span v-if="selected.areaName"> · {{ selected.areaName }}</span></p>
            </div>
          </div>
          <div class="dialog-top-actions">
            <span v-if="selected.applicationStatus === 'SUBMITTED'" class="application-pill"><IconifyIcon icon="lucide:send" />已投递</span>
            <span v-else-if="selected.applicationStatus === 'READY_TO_SUBMIT'" class="application-pill ready"><IconifyIcon icon="lucide:file-check-2" />待投递</span>
          </div>
        </header>

        <div class="dialog-profile-strip">
          <div><span>性别</span><strong>{{ sexText(selected) }}</strong></div>
          <div><span>年龄</span><strong>{{ ageText(selected) }}</strong></div>
          <div><span>学历</span><strong>{{ educationText(selected) }}</strong></div>
          <div class="profile-school"><span>毕业院校</span><strong>{{ selected.candidateGraduateSchool || '—' }}</strong></div>
          <div><span>工作经验</span><strong>{{ workTimeText(selected.candidateWorkTime) }}</strong></div>
        </div>

        <main class="dialog-main">
          <div class="dialog-section-title"><span>沟通记录</span><small>与候选人直接交流</small></div>
          <div class="dialog-messages">
            <div v-if="messagesLoading" class="dialog-state">正在加载消息…</div>
            <div v-else-if="!chatMessages.length" class="dialog-state dialog-no-message"><span><IconifyIcon icon="lucide:message-square-dashed" /></span><strong>暂无沟通消息</strong><p>求职者发送消息后，招聘方可在这里回复。</p></div>
            <div v-for="item in chatMessages" :key="item.id" class="message-line" :class="{ mine: item.mine }">
              <span class="message-avatar">{{ item.mine ? '我' : candidateInitial(selected) }}</span>
              <div class="message-body">
                <div class="message-meta"><b>{{ item.mine ? '我' : candidateName(selected) }}</b><time>{{ formatTime(item.sendTime) }}</time></div>
                <div class="message-bubble">
                  <template v-if="item.contentType === 2">{{ messageContent(item) }}</template>
                  <a
                    v-else-if="imageMeta(item.content)"
                    class="message-image"
                    :href="imageMeta(item.content)?.fileUrl"
                    target="_blank"
                    rel="noreferrer"
                  >
                    <img :src="imageMeta(item.content)?.fileUrl" :alt="imageMeta(item.content)?.fileName" />
                    <span>{{ imageMeta(item.content)?.fileName }}</span>
                  </a>
                  <template v-else>{{ item.content }}</template>
                </div>
              </div>
            </div>
          </div>

          <div v-if="canReply" class="reply-composer">
            <div class="composer-head"><span><IconifyIcon icon="lucide:pen-line" />回复候选人</span><small>{{ draft.length }}/500</small></div>
            <a-textarea v-model:value="draft" :maxlength="500" :rows="4" placeholder="请输入想对候选人说的话…" />
            <div class="composer-foot"><span>回复会实时同步到候选人的消息中心</span><a-button type="primary" :loading="sending" :disabled="!draft.trim()" @click="send"><IconifyIcon icon="lucide:send" />发送回复</a-button></div>
          </div>
          <div v-else class="reply-locked"><IconifyIcon icon="lucide:circle-off" /><div><strong>沟通失败</strong><span>当前会话无法继续发送消息。</span></div></div>
        </main>
      </div>
    </a-modal>
  </Page>
</template>

<style scoped>
:global(.hrm-recruit-chat-html), :global(.hrm-recruit-chat-body) { height: 100vh; overflow: hidden; }
:global(.hrm-recruit-chat-body .relative.flex.min-h-full.w-full) { height: 100%; min-height: 0; overflow: hidden; }
:global(.hrm-recruit-chat-body #__vben_main_content) { min-height: 0; overflow: hidden; }
:global(.hrm-recruit-chat-modal .ant-modal) { transform: none !important; }
:global(.hrm-recruit-chat-page) { display: flex; min-height: 0; flex: 1; flex-direction: column; overflow: hidden; }
.chat-workspace { display: flex; height: 100%; min-height: 0; flex-direction: column; gap: 14px; overflow: hidden; padding: 2px; color: #e7ebf2; }
.chat-header { position: relative; display: flex; min-height: 150px; align-items: center; justify-content: space-between; gap: 32px; overflow: hidden; padding: 28px 34px; border: 1px solid #292e37; border-radius: 6px; background: linear-gradient(110deg, #191c21 0%, #1d222a 58%, #182c45 100%); box-shadow: 0 12px 34px rgb(0 0 0 / 16%); }
.chat-header::after { position: absolute; top: -100px; right: 8%; width: 300px; height: 300px; border: 1px solid rgb(50 143 255 / 16%); border-radius: 50%; box-shadow: 0 0 0 26px rgb(50 143 255 / 4%), 0 0 0 54px rgb(50 143 255 / 3%); content: ''; pointer-events: none; }
.chat-heading { position: relative; z-index: 1; }
.chat-eyebrow { display: flex; align-items: center; gap: 8px; color: #6aaeff; font-size: 20px; font-weight: 700; letter-spacing: .12em; }
.live-dot { width: 7px; height: 7px; border-radius: 50%; background: #36d399; box-shadow: 0 0 0 4px rgb(54 211 153 / 12%); }
.chat-heading h1 { margin: 10px 0 6px; color: #f7f9fc; font-size: 26px; font-weight: 650; letter-spacing: -.02em; }
.chat-heading p { margin: 0; color: #919baa; font-size: 15px; }
.chat-metrics { position: relative; z-index: 1; display: flex; min-width: 300px; align-items: stretch; border: 1px solid #303743; border-radius: 5px; background: rgb(13 16 21 / 42%); }
.chat-metrics div { display: grid; min-width: 98px; padding: 16px 18px; gap: 4px; border-right: 1px solid #303743; }
.chat-metrics div:last-child { border-right: 0; }
.chat-metrics strong { color: #f5f8ff; font-size: 25px; font-weight: 650; line-height: 1; }
.chat-metrics span { color: #7f8a9a; font-size: 13px; white-space: nowrap; }
.chat-panel { display: flex; min-height: 0; flex: 1; flex-direction: column; overflow: hidden; border: 1px solid #292e37; border-radius: 6px; background: #191c21; }
.chat-panel-toolbar { display: flex; min-height: 72px; align-items: center; justify-content: space-between; gap: 20px; overflow: hidden; padding: 14px 18px 0; border-bottom: 1px solid #292e37; }
.toolbar-right { display: flex; align-items: center; gap: 14px; }
.profile-filter-bar { display: flex; align-items: center; gap: 22px; padding: 13px 18px; border-bottom: 1px solid #292e37; background: #1d2127; }
.profile-filter-heading { display: flex; min-width: 168px; align-items: center; gap: 9px; }
.profile-filter-heading > div { display: grid; gap: 3px; }
.profile-filter-heading strong { color: #dce5f0; font-size: 14px; font-weight: 600; }
.profile-filter-heading small { color: #737f8f; font-size: 12px; white-space: nowrap; }
.profile-filter-icon { display: grid; width: 28px; height: 28px; place-items: center; border: 1px solid #2e5682; border-radius: 5px; background: rgb(22 119 255 / 10%); color: #6eb2ff; font-size: 17px; }
.profile-filter-fields { display: grid; min-width: 0; flex: 1; grid-template-columns: repeat(5, minmax(108px, 1fr)) auto; align-items: end; gap: 10px; }
.profile-filter-field { display: grid; min-width: 0; gap: 5px; }
.profile-filter-field > span { color: #7e8998; font-size: 12px; }
.profile-filter-field select, .profile-filter-field input { width: 100%; height: 32px; min-width: 0; padding: 0 9px; border: 1px solid #343c48; border-radius: 4px; outline: 0; background: #15181d; color: #dce3ec; font: inherit; font-size: 13px; }
.profile-filter-field select { cursor: pointer; }
.profile-filter-field input::placeholder { color: #687382; }
.profile-filter-field select:focus, .profile-filter-field input:focus { border-color: #397fbd; box-shadow: 0 0 0 2px rgb(22 119 255 / 10%); }
.reset-filter-action { display: inline-flex; height: 32px; align-items: center; gap: 5px; padding: 0 10px; border: 1px solid #3a4654; border-radius: 4px; background: transparent; color: #8d99a8; cursor: pointer; font-size: 13px; white-space: nowrap; }
.reset-filter-action:hover:not(:disabled) { border-color: #4c8cc6; background: rgb(22 119 255 / 9%); color: #79b8f3; }
.reset-filter-action:disabled { cursor: not-allowed; opacity: .38; }
.selection-tools { display: flex; align-items: center; gap: 9px; white-space: nowrap; }
.selection-count { color: #8c98a8; font-size: 13px; }
.bulk-delete-action, .delete-action { display: inline-flex; align-items: center; gap: 6px; border: 1px solid #5b3540; border-radius: 4px; background: rgb(194 74 99 / 9%); color: #dc8095; cursor: pointer; font-size: 14px; transition: border-color .18s ease, background .18s ease, color .18s ease; }
.bulk-delete-action { height: 34px; padding: 0 11px; }
.bulk-delete-action:hover:not(:disabled), .delete-action:hover:not(:disabled) { border-color: #c24a63; background: #c24a63; color: #fff; }
.bulk-delete-action:disabled, .delete-action:disabled { cursor: not-allowed; opacity: .42; }
.filter-tabs { display: flex; min-width: 0; flex: 1 1 auto; align-self: stretch; align-items: flex-end; gap: 4px; overflow: hidden; }
.filter-tabs button { position: relative; display: flex; align-items: center; gap: 7px; height: 48px; padding: 0 10px; border: 0; background: transparent; color: #7e8998; cursor: pointer; font-size: 15px; white-space: nowrap; }
.filter-tabs button::after { position: absolute; right: 14px; bottom: -1px; left: 14px; height: 2px; background: transparent; content: ''; }
.filter-tabs button:hover, .filter-tabs button.active { color: #56a5ff; }
.filter-tabs button.active::after { background: #1677ff; }
.filter-tabs b { min-width: 20px; padding: 2px 6px; border: 1px solid #363d49; border-radius: 10px; color: #8d98a8; font-size: 13px; font-weight: 500; }
.filter-tabs button.active b { border-color: rgb(22 119 255 / 40%); background: rgb(22 119 255 / 12%); color: #72b5ff; }
.chat-search { display: flex; width: 226px; height: 34px; align-items: center; gap: 8px; padding: 0 10px; border: 1px solid #343a45; border-radius: 4px; background: #15171b; color: #697586; }
.chat-search input { width: 100%; border: 0; outline: 0; background: transparent; color: #e7ebf2; font-size: 14px; }
.chat-search input::placeholder { color: #687281; }
.chat-search button { display: grid; padding: 0; border: 0; background: transparent; color: #727e8d; cursor: pointer; }
.conversation-viewport { min-height: 0; flex: 1; overflow: auto; }
.conversation-viewport::-webkit-scrollbar:horizontal { height: 0; }
.conversation-table { min-height: 100%; min-width: 1360px; }
.conversation-horizontal-scroll { flex: 0 0 14px; overflow-x: auto; overflow-y: hidden; border-top: 1px solid #292e37; background: #15181d; }
.conversation-horizontal-track { height: 1px; min-width: 1360px; }
.conversation-row { display: grid; min-width: 1360px; grid-template-columns: 1.35fr 1.05fr .55fr .62fr .7fr 1fr .75fr 2.05fr 1.25fr; }
.conversation-head { position: sticky; z-index: 2; top: 0; border-bottom: 1px solid #343a44; background: #20242b; color: #919baa; font-size: 14px; }
.conversation-head > div { padding: 15px 14px; font-weight: 600; }
.conversation-head > div:first-child, .conversation-data-row > div:first-child { padding-left: 22px; }
.name-heading { display: flex; align-items: center; gap: 9px; }
.action-heading { position: sticky; z-index: 4; right: 0; background: #20242b; box-shadow: -12px 0 18px -18px rgb(0 0 0 / 90%); text-align: center; }
.conversation-data-row { min-height: 78px; align-items: center; border-bottom: 1px solid #292e37; color: #dce1e9; transition: background .18s ease; }
.conversation-data-row:hover, .conversation-data-row.selected { background: #202731; }
.conversation-data-row.selected { box-shadow: inset 2px 0 #1677ff; }
.conversation-data-row > div { min-width: 0; padding: 12px 14px; }
.conversation-data-row > .row-action { position: sticky; z-index: 3; right: 0; background: #191c21; box-shadow: -12px 0 18px -18px rgb(0 0 0 / 90%); }
.conversation-data-row:hover > .row-action, .conversation-data-row.selected > .row-action { background: #202731; }
.candidate-cell { position: relative; display: flex; align-items: center; gap: 10px; }
.selection-checkbox { width: 14px; height: 14px; flex: 0 0 auto; margin: 0; border: 1px solid #536071; border-radius: 3px; accent-color: #1677ff; cursor: pointer; }
.selection-checkbox:focus-visible { outline: 2px solid rgb(86 165 255 / 65%); outline-offset: 2px; }
.candidate-avatar, .dialog-avatar { display: grid; width: 36px; height: 36px; flex: 0 0 auto; place-items: center; overflow: hidden; border: 1px solid #385271; border-radius: 50%; background: linear-gradient(145deg, #234e7f, #1b2c46); color: #8dc5ff; font-size: 16px; font-weight: 700; }
.candidate-avatar img, .dialog-avatar img { width: 100%; height: 100%; object-fit: cover; }
.candidate-copy { display: grid; min-width: 0; gap: 5px; }
.candidate-copy strong, .post-cell strong { overflow: hidden; color: #f1f4f8; font-size: 15px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.candidate-copy small { display: flex; align-items: center; gap: 5px; overflow: hidden; color: #7d8999; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.status-label i, .dialog-status i { width: 5px; height: 5px; border-radius: 50%; background: currentColor; }
.status-waiting { color: #5ca9ff !important; }
.status-replied { color: #35c98b !important; }
.status-failed { color: #77808d !important; }
.unread-count { position: absolute; top: 8px; left: 47px; min-width: 16px; padding: 1px 4px; border: 2px solid #191c21; border-radius: 10px; background: #1677ff; color: #fff; font-size: 11px; font-style: normal; line-height: 12px; text-align: center; }
.post-cell { display: grid; gap: 5px; }
.post-cell small { overflow: hidden; color: #737e8e; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.muted-cell, .school-cell { overflow: hidden; color: #aeb7c4; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.school-cell { color: #d0d6df; }
.last-message-cell { display: grid; min-width: 0; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 7px; }
.last-message-cell p { overflow: hidden; margin: 0; color: #c0c8d3; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.last-message-cell time { color: #697586; font-size: 12px; white-space: nowrap; }
.message-sender { color: #659ed9; font-size: 12px; white-space: nowrap; }
.row-action { display: flex; align-items: center; justify-content: center; gap: 7px; }
.chat-action { display: inline-flex; align-items: center; gap: 6px; padding: 8px 12px; border: 1px solid #235d9c; border-radius: 4px; background: rgb(22 119 255 / 12%); color: #61adff; cursor: pointer; font-size: 14px; transition: border-color .18s ease, background .18s ease, color .18s ease; }
.chat-action:hover { border-color: #1677ff; background: #1677ff; color: #fff; }
.delete-action { padding: 8px 11px; }
.table-state { display: grid; min-height: 260px; place-items: center; align-content: center; color: #7d8998; font-size: 15px; }
.table-empty { gap: 8px; }
.table-empty strong { color: #c9d0db; font-size: 16px; }
.table-empty p { margin: 0; color: #737e8e; font-size: 14px; }
.empty-icon { display: grid; width: 48px; height: 48px; margin-bottom: 4px; place-items: center; border: 1px solid #303946; border-radius: 12px; background: #20252d; color: #5a7696; font-size: 22px; }
.chat-panel-footer { display: flex; min-height: 58px; align-items: center; justify-content: space-between; gap: 16px; padding: 8px 18px; border-top: 1px solid #292e37; color: #737e8e; font-size: 13px; }
.table-pagination { display: flex; align-items: center; justify-content: flex-end; gap: 5px; }
.page-size-picker { display: inline-flex; align-items: center; gap: 6px; margin-right: 8px; white-space: nowrap; }
.page-size-picker select { height: 29px; padding: 0 7px; border: 1px solid #343c48; border-radius: 4px; outline: 0; background: #15181d; color: #c7d0dc; cursor: pointer; font: inherit; }
.page-indicator { margin-right: 5px; color: #8995a4; white-space: nowrap; }
.table-pagination button { min-width: 29px; height: 29px; padding: 0 7px; border: 1px solid #343c48; border-radius: 4px; background: #1d2229; color: #aab5c2; cursor: pointer; font-size: 12px; }
.table-pagination button:hover:not(:disabled), .table-pagination button.active { border-color: #1677ff; background: rgb(22 119 255 / 16%); color: #6eb2ff; }
.table-pagination button:disabled { cursor: not-allowed; opacity: .38; }
.dialog-card { display: flex; height: min(86vh, 860px); max-height: calc(100vh - 32px); margin: -24px; flex-direction: column; overflow: hidden; border-radius: 7px; background: #1a1d22; color: #e7ebf2; }
:global(.hrm-recruit-chat-modal .ant-modal) { top: 16px; padding-bottom: 16px; }
.dialog-header { display: flex; min-height: 86px; flex: 0 0 auto; align-items: center; justify-content: space-between; gap: 20px; padding: 18px 76px 18px 24px; border-bottom: 1px solid #303640; background: linear-gradient(105deg, #1b1e24, #202630); }
.dialog-person { display: flex; align-items: center; gap: 13px; min-width: 0; }
.dialog-avatar { width: 44px; height: 44px; font-size: 19px; }
.dialog-name-line { display: flex; align-items: center; gap: 10px; }
.dialog-name-line h2 { margin: 0; color: #f4f7fb; font-size: 19px; font-weight: 650; }
.dialog-person p { margin: 5px 0 0; color: #8994a3; font-size: 14px; }
.dialog-person p b { color: #bed8f7; font-weight: 500; }
.dialog-status { display: inline-flex; align-items: center; gap: 5px; font-size: 13px; }
.dialog-top-actions { display: flex; align-items: center; gap: 16px; }
.application-pill { display: inline-flex; align-items: center; gap: 5px; padding: 5px 9px; border: 1px solid rgb(54 211 153 / 35%); border-radius: 4px; background: rgb(54 211 153 / 9%); color: #52d99e; font-size: 13px; }
.application-pill.ready { border-color: rgb(22 119 255 / 40%); background: rgb(22 119 255 / 10%); color: #72b5ff; }
.dialog-profile-strip { display: flex; min-height: 64px; align-items: center; gap: 0; padding: 0 24px; border-bottom: 1px solid #2c323b; background: #171a1f; }
.dialog-profile-strip > div { display: grid; min-width: 120px; padding-right: 24px; gap: 5px; border-right: 1px solid #2a3039; }
.dialog-profile-strip > div + div { padding-left: 24px; }
.dialog-profile-strip > div:last-child { flex: 1; border-right: 0; }
.dialog-profile-strip span { color: #727e8d; font-size: 13px; }
.dialog-profile-strip strong { overflow: hidden; color: #d9e0e9; font-size: 14px; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.dialog-main { display: flex; min-height: 0; flex: 1; flex-direction: column; overflow: hidden; padding: 20px 24px 22px; }
.dialog-section-title { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.dialog-section-title span { color: #e4eaf2; font-size: 15px; font-weight: 600; }
.dialog-section-title small { color: #687586; font-size: 13px; }
.dialog-messages { display: flex; min-height: 0; flex: 1; flex-direction: column; gap: 16px; overflow-y: auto; padding: 5px 4px 14px 2px; }
.message-line { display: flex; align-items: flex-start; gap: 9px; max-width: 76%; }
.message-line.mine { align-self: flex-end; flex-direction: row-reverse; }
.message-avatar { display: grid; width: 28px; height: 28px; flex: 0 0 auto; place-items: center; border: 1px solid #33455d; border-radius: 50%; background: #24364e; color: #9acbff; font-size: 12px; font-weight: 600; }
.message-line.mine .message-avatar { border-color: #1d65aa; background: #1260aa; color: #fff; }
.message-body { min-width: 0; }
.message-line.mine .message-body { text-align: right; }
.message-meta { display: flex; align-items: center; gap: 8px; margin-bottom: 5px; color: #8c98a7; font-size: 12px; }
.message-line.mine .message-meta { justify-content: flex-end; }
.message-meta b { color: #b8c3d1; font-weight: 500; }
.message-meta time { color: #687586; }
.message-bubble { padding: 9px 12px; border: 1px solid #303843; border-radius: 4px 10px 10px 10px; background: #242a32; color: #d8dfe8; font-size: 14px; line-height: 1.6; text-align: left; white-space: pre-wrap; word-break: break-word; }
.message-line.mine .message-bubble { border-color: #1c64a5; border-radius: 10px 4px 10px 10px; background: #125a9c; color: #f6fbff; }
.message-image { display: inline-flex; max-width: 250px; flex-direction: column; gap: 6px; color: inherit; text-decoration: none; }
.message-image img { display: block; max-width: 250px; max-height: 190px; border-radius: 4px; object-fit: contain; }
.message-image span { overflow: hidden; color: #9caabd; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.dialog-state { display: grid; min-height: 190px; place-items: center; align-content: center; color: #748091; font-size: 14px; }
.dialog-no-message { gap: 5px; }
.dialog-no-message > span { display: grid; width: 40px; height: 40px; margin-bottom: 3px; place-items: center; border: 1px dashed #394654; border-radius: 10px; color: #6380a1; font-size: 20px; }
.dialog-no-message strong { color: #a9b4c2; font-size: 14px; font-weight: 500; }
.dialog-no-message p { margin: 0; color: #697586; font-size: 13px; }
.reply-composer { margin-top: 4px; padding: 13px 14px 11px; border: 1px solid #303843; border-radius: 5px; background: #20252c; }
.composer-head, .composer-foot { display: flex; align-items: center; justify-content: space-between; }
.composer-head { margin-bottom: 8px; color: #d7e0eb; font-size: 14px; }
.composer-head span { display: inline-flex; align-items: center; gap: 6px; }
.composer-head small { color: #6f7b8b; font-size: 12px; }
.reply-composer :deep(.ant-input) { min-height: 74px !important; padding: 9px 10px; border-color: #39414d !important; border-radius: 4px; background: #171a1f !important; color: #e8edf5 !important; font-size: 16px; resize: none; }
.reply-composer :deep(.ant-input::placeholder) { color: #657181; }
.composer-foot { margin-top: 10px; color: #6d7989; font-size: 12px; }
.composer-foot :deep(.ant-btn) { display: inline-flex; height: 31px; align-items: center; gap: 6px; border-radius: 4px; font-size: 14px; }
.reply-locked { display: flex; align-items: center; gap: 11px; margin-top: 4px; padding: 15px 16px; border: 1px solid #303843; border-radius: 5px; background: #20252c; color: #7a8797; }
.reply-locked > :deep(.iconify) { color: #71839a; font-size: 20px; }
.reply-locked div { display: grid; gap: 4px; }
.reply-locked strong { color: #b1bbc8; font-size: 14px; font-weight: 500; }
.reply-locked span { color: #6f7b8b; font-size: 13px; }
@media (max-width: 900px) {
  .chat-header { align-items: flex-start; flex-direction: column; }
  .chat-metrics { width: 100%; }
  .chat-metrics div { flex: 1; }
  .chat-panel-toolbar { align-items: flex-start; flex-direction: column; gap: 0; padding-top: 8px; }
  .filter-tabs { width: 100%; flex: 0 0 auto; }
  .toolbar-right { width: 100%; justify-content: space-between; gap: 10px; margin: 8px 0 12px; }
  .chat-search { width: 100%; margin: 8px 0 12px; }
  .profile-filter-bar { align-items: stretch; flex-direction: column; gap: 12px; }
  .profile-filter-heading { min-width: 0; }
  .profile-filter-fields { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .reset-filter-action { justify-content: center; }
  .chat-panel-footer { align-items: flex-start; flex-direction: column; }
  .table-pagination { width: 100%; justify-content: flex-start; overflow-x: auto; padding-bottom: 2px; }
}
@media (max-width: 640px) {
  .chat-workspace { gap: 10px; }
  .chat-header { padding: 22px 18px; }
  .chat-heading h1 { font-size: 22px; }
  .chat-heading p { line-height: 1.6; }
  .chat-metrics { min-width: 0; }
  .chat-metrics div { min-width: 0; padding: 13px 10px; }
  .chat-metrics strong { font-size: 21px; }
  .toolbar-right { align-items: stretch; flex-direction: column-reverse; }
  .selection-tools { justify-content: space-between; }
  .chat-search { margin: 0; }
  .profile-filter-fields { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .school-filter-field { grid-column: span 2; }
  .dialog-card { height: calc(100vh - 32px); margin: -24px; }
  .dialog-header { padding: 16px 60px 16px 16px; }
  .dialog-profile-strip { padding: 0 16px; overflow-x: auto; }
  .dialog-profile-strip > div { min-width: 82px; padding-right: 12px; }
  .dialog-profile-strip > div + div { padding-left: 12px; }
  .dialog-main { padding: 16px; }
  .dialog-top-actions .application-pill { display: none; }
}
@media (prefers-reduced-motion: reduce) {
  .conversation-data-row, .chat-action, .delete-action, .bulk-delete-action { transition: none; }
}
</style>
