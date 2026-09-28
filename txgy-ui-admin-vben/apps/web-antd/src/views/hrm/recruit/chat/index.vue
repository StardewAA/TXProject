<script lang="ts" setup>
import type { HrmPortalRecruitChatApi } from '#/api/hrm/portal/recruit/chat';

import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue';

import { useAccess } from '@vben/access';
import { confirm, Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import { message } from 'ant-design-vue';

import {
  aiFilterConversations,
  convertAdminRecruitChatConversationToCandidate,
  deleteAdminRecruitChatConversation,
  deleteAdminRecruitChatConversationList,
  getAdminRecruitChatConversationList,
  getAdminRecruitChatMessageList,
  markAdminRecruitChatRead,
  sendAdminRecruitChatMessage,
} from '#/api/hrm/portal/recruit/chat';
import { getHrmRecruitChatAttachment } from '#/api/hrm/portal/recruit/chat-attachment';
import { buildAgentIdempotencyKey } from '#/api/hrm/recruit/agent';

import AiMatchModal from './modules/ai-match-modal.vue';
import CandidateResumeModal from './modules/candidate-resume-modal.vue';

/**
 * 招聘沟通操作收敛（Q-020 / I-018）
 *
 * <p>删除 / 批量删除在后端要求 `hrm:recruit:chat:delete` 权限；前端此前无条件展示，
 * 没有该权限的 HR 点下去只会拿到 403。这里按权限显隐，保证"看得见就能点得通"。</p>
 */
const { hasAccessByCodes } = useAccess();
const canDeleteConversation = computed(() =>
  hasAccessByCodes(['hrm:recruit:chat:delete']),
);

type Conversation = HrmPortalRecruitChatApi.Conversation;
type ChatMessage = HrmPortalRecruitChatApi.ChatMessage;
type FilterKey = 'all' | 'replied' | 'submitted' | 'waiting';
/** AI 匹配分排序：none 保持默认（按最近沟通时间），其余按分排 */
type AiSortKey = 'asc' | 'desc' | 'none';
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
const selectedHasResume = computed(() =>
  chatMessages.value.some(
    (item) =>
      getHrmRecruitChatAttachment(item.content, item.contentType)?.type ===
      'resume',
  ),
);
const draft = ref('');
const searchText = ref('');
const activeFilter = ref<FilterKey>('all');
const aiSort = ref<AiSortKey>('none');
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
const tableScrollWidth = ref(1472);
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
  const matched = conversations.value.filter((item) => {
    // AI 筛选是入口级条件：没命中的直接排除，其余筛选照常叠加
    if (aiFilter.value && !aiMatchedIds.value.has(item.id)) return false;
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
  return sortByAiScore(matched);
});

/**
 * 按 AI 匹配分排序。
 *
 * <p>没分析过的人一律沉到末尾——否则一旦升序排，未分析会比低分还靠前，反而更难筛。</p>
 */
function sortByAiScore(list: Conversation[]) {
  if (aiSort.value === 'none') return list;
  const direction = aiSort.value === 'desc' ? -1 : 1;
  return [...list].sort((left, right) => {
    const leftScore = left.aiMatchScore;
    const rightScore = right.aiMatchScore;
    if (leftScore === undefined && rightScore === undefined) return 0;
    if (leftScore === undefined) return 1;
    if (rightScore === undefined) return -1;
    return (leftScore - rightScore) * direction;
  });
}

/** 与 AI 弹窗、候选人详情页保持同一套评分色调 */
function aiScoreTone(score?: number) {
  if (score === undefined || score === null) return 'mid';
  if (score >= 80) return 'good';
  if (score >= 60) return 'mid';
  return 'low';
}

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
  return item.candidateName || item.peerUserName || '求职者';
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

function previewContent(content?: string) {
  if (!content) return '暂无消息';
  return getHrmRecruitChatAttachment(content, 1) ? '[图片]' : content;
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
      1472,
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
  if (status === 2) return '求职方已回复';
  if (status === 3) return '沟通失败';
  return '等待回复';
}

function statusClass(status?: number) {
  if (status === 2) return 'status-replied';
  if (status === 3) return 'status-failed';
  return 'status-waiting';
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

const resumeModalOpen = ref(false);
const resumeConversation = ref<Conversation>();

/** 点头像或姓名查看求职者的在线简历，与「立即沟通」区分开，不打开对话 */
function openCandidateResume(item: Conversation) {
  resumeConversation.value = item;
  resumeModalOpen.value = true;
}

const aiModalOpen = ref(false);
const aiConversation = ref<Conversation>();

/** 打开 AI 匹配分析弹窗；该会话还没分析过时，由弹窗自动提交一次 */
function openAiMatch(item: Conversation) {
  aiConversation.value = item;
  aiModalOpen.value = true;
}

/** 弹窗产出新结果后刷新列表，让分数徽标与排序跟着更新 */
async function handleAiAnalyzed() {
  await loadConversations(true);
}

const aiQuery = ref('');
const aiFiltering = ref(false);
/** AI 筛选结果；有值时列表只显示命中的会话 */
const aiFilter = ref<HrmPortalRecruitChatApi.AiFilterResult>();
const aiMatchedIds = computed(
  () => new Set(aiFilter.value?.matchedConversationIds || []),
);

/**
 * 把招聘方这句话交给服务端：模型只做「自然语言 → 条件」的翻译，
 * 命中判断用的是服务端本地数据，前端这边只负责把结果套到列表上。
 */
async function runAiFilter() {
  const query = aiQuery.value.trim();
  if (!query || aiFiltering.value) return;
  aiFiltering.value = true;
  try {
    const result = await aiFilterConversations({
      query,
      idempotencyKey: buildAgentIdempotencyKey(),
    });
    aiFilter.value = result;
    currentPage.value = 1;
    if (!result?.matchedConversationIds?.length) {
      message.info('没有找到符合条件的求职者，换个说法再试试');
    }
  } catch (error: any) {
    message.error(error?.msg || error?.message || 'AI 筛选失败，请稍后重试');
  } finally {
    aiFiltering.value = false;
  }
}

function clearAiFilter() {
  aiFilter.value = undefined;
  aiQuery.value = '';
  currentPage.value = 1;
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

async function handleConvertToCandidate(item: Conversation) {
  if (item.candidateId && item.applicationId) return;
  const repairingApplication = Boolean(item.candidateId);
  try {
    await confirm(item.candidateId
      ? `确定为“${candidateName(item)}”补建应聘记录吗？`
      : `确定将“${candidateName(item)}”转为候选人吗？`);
    const candidateId = await convertAdminRecruitChatConversationToCandidate(item.id);
    item.candidateId = candidateId;
    if (selected.value?.id === item.id) {
      selected.value.candidateId = candidateId;
    }
    message.success(repairingApplication ? '应聘记录已补建' : '已转为候选人并创建应聘记录');
    await loadConversations(true);
  } catch (error: any) {
    if (error?.msg || error?.code || error?.response) {
      message.error(error?.msg || error?.message || '转为候选人失败');
    }
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
        <!--
          AI 筛选：模型只把这句话翻译成结构化条件，命中判断在服务端用本地数据完成，
          因此户籍地这类属性既能筛，又不会离开内网。
        -->
        <div class="ai-filter">
          <div class="ai-filter-box">
            <IconifyIcon icon="lucide:sparkles" />
            <input
              v-model="aiQuery"
              type="text"
              :disabled="aiFiltering"
              placeholder="用一句话描述要求"
              aria-label="用自然语言筛选求职者"
              @keydown.enter="runAiFilter"
            />
            <button
              class="ai-filter-run"
              type="button"
              :disabled="!aiQuery.trim() || aiFiltering"
              @click="runAiFilter"
            >
              {{ aiFiltering ? '筛选中…' : 'AI 筛选' }}
            </button>
          </div>
          <div v-if="aiFilter" class="ai-filter-summary">
            <span class="ai-filter-criteria">
              <IconifyIcon icon="lucide:sparkles" />{{ aiFilter.criteria }}
            </span>
            <b>从 {{ aiFilter.totalCandidates }} 人中筛出 {{ aiFilter.matchedConversationIds.length }} 人</b>
            <span v-if="aiFilter.unsupported?.length" class="ai-filter-unsupported">
              未处理：{{ aiFilter.unsupported.join('、') }}
            </span>
            <button class="ai-filter-clear" type="button" @click="clearAiFilter">清除筛选</button>
          </div>
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
              <button v-if="canDeleteConversation" class="bulk-delete-action" type="button" :disabled="!selectedIds.length || batchDeleting" @click="handleBatchDelete">
                <IconifyIcon icon="lucide:trash-2" />批量删除
              </button>
            </div>
            <div class="chat-search">
              <IconifyIcon icon="lucide:search" />
              <input v-model="searchText" aria-label="搜索求职者或职位" placeholder="搜索求职者、职位或消息" />
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
            <label class="profile-filter-field">
              <span>AI 匹配分</span>
              <select v-model="aiSort" aria-label="按 AI 匹配分排序">
                <option value="none">不排序</option>
                <option value="desc">由高到低</option>
                <option value="asc">由低到高</option>
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
            <div role="columnheader">发来的消息及时间</div>
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
                <span
                  class="candidate-avatar-wrap"
                  role="button"
                  tabindex="0"
                  :aria-label="`查看${candidateName(item)}的在线简历`"
                  @click="openCandidateResume(item)"
                  @keydown.enter.prevent="openCandidateResume(item)"
                  @keydown.space.prevent="openCandidateResume(item)"
                >
                  <span class="candidate-avatar">
                    <img v-if="item.peerUserAvatar" :src="item.peerUserAvatar" alt="" />
                    <span v-else>{{ candidateInitial(item) }}</span>
                  </span>
                  <em v-if="item.unreadCount" class="unread-count">{{ item.unreadCount > 99 ? '99+' : item.unreadCount }}</em>
                </span>
                <span class="candidate-copy">
                  <strong
                    role="button"
                    tabindex="0"
                    :title="`查看${candidateName(item)}的在线简历`"
                    @click="openCandidateResume(item)"
                    @keydown.enter.prevent="openCandidateResume(item)"
                    @keydown.space.prevent="openCandidateResume(item)"
                  >{{ candidateName(item) }}</strong>
                  <span class="candidate-meta">
                    <small class="status-label" :class="[statusClass(item.status)]"><i></i>{{ statusText(item.status) }}</small>
                    <button
                      v-if="item.aiMatchScore !== undefined"
                      class="ai-badge"
                      :class="aiScoreTone(item.aiMatchScore)"
                      type="button"
                      :title="`查看 ${candidateName(item)} 的 AI 匹配分析`"
                      @click.stop="openAiMatch(item)"
                    >
                      <IconifyIcon icon="lucide:sparkles" />{{ item.aiMatchScore }}
                    </button>
                    <button
                      v-else-if="item.applicationId"
                      class="ai-badge idle"
                      type="button"
                      :title="`用 AI 分析 ${candidateName(item)} 与岗位的匹配度`"
                      @click.stop="openAiMatch(item)"
                    >
                      <IconifyIcon icon="lucide:sparkles" />AI 分析
                    </button>
                  </span>
                </span>
              </div>
              <div class="post-cell" role="cell"><strong>{{ item.postName || '招聘职位' }}</strong></div>
              <div class="muted-cell" role="cell">{{ sexText(item) }}</div>
              <div class="muted-cell" role="cell">{{ ageText(item) }}</div>
              <div class="muted-cell" role="cell">{{ educationText(item) }}</div>
              <div class="school-cell" role="cell" :title="item.candidateGraduateSchool || ''">{{ item.candidateGraduateSchool || '—' }}</div>
              <div class="muted-cell experience-cell" role="cell">{{ workTimeText(item.candidateWorkTime) }}</div>
              <div class="last-message-cell" role="cell">
                <span v-if="item.lastContent" class="message-sender">{{ item.lastSenderUserId === item.candidateUserId ? '求职者' : '我' }}</span>
                <p>{{ previewContent(item.lastContent) }}</p>
                <time>{{ formatTime(item.lastTime) }}</time>
              </div>
              <div class="row-action" role="cell">
                <button class="chat-action" type="button" @click="openChat(item)">
                  <IconifyIcon icon="lucide:message-circle-more" />立即沟通
                </button>
                <button
                  v-if="!item.candidateId || !item.applicationId"
                  class="convert-action"
                  type="button"
                  @click="handleConvertToCandidate(item)"
                >
                  <IconifyIcon icon="lucide:user-round-plus" />{{ item.candidateId ? '补建应聘记录' : '转为候选人' }}
                </button>
                <span v-else class="converted-action"><IconifyIcon icon="lucide:badge-check" />已转候选人</span>
                <button
                  v-if="canDeleteConversation"
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
            <span
              class="dialog-avatar"
              role="button"
              tabindex="0"
              :aria-label="`查看${candidateName(selected)}的在线简历`"
              @click="openCandidateResume(selected)"
              @keydown.enter.prevent="openCandidateResume(selected)"
              @keydown.space.prevent="openCandidateResume(selected)"
            >
              <img v-if="selected.peerUserAvatar" :src="selected.peerUserAvatar" alt="" />
              <span v-else>{{ candidateInitial(selected) }}</span>
            </span>
            <div>
              <div class="dialog-name-line">
<h2
                role="button"
                tabindex="0"
                :title="`查看${candidateName(selected)}的在线简历`"
                @click="openCandidateResume(selected)"
                @keydown.enter.prevent="openCandidateResume(selected)"
                @keydown.space.prevent="openCandidateResume(selected)"
              >
{{ candidateName(selected) }}
</h2><span class="dialog-status" :class="[statusClass(selected.status)]"><i></i>{{ statusText(selected.status) }}</span>
</div>
              <p>应聘 <b>{{ selected.postName || '招聘职位' }}</b></p>
            </div>
          </div>
          <div class="dialog-top-actions">
            <span v-if="selected.applicationStatus === 'SUBMITTED'" class="application-pill"><IconifyIcon icon="lucide:send" />已投递</span>
            <span v-else-if="selected.applicationStatus === 'READY_TO_SUBMIT'" class="application-pill ready"><IconifyIcon icon="lucide:file-check-2" />待投递</span>
            <span v-if="!selected.candidateId && selectedHasResume" class="application-pill ready"><IconifyIcon icon="lucide:user-round-plus" />待 HR 手动转为候选人</span>
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
           <div class="dialog-messages">
            <div v-if="messagesLoading" class="dialog-state">正在加载消息…</div>
            <div v-else-if="!chatMessages.length" class="dialog-state dialog-no-message"><span><IconifyIcon icon="lucide:message-square-dashed" /></span><strong>暂无沟通消息</strong><p>求职者发送消息后，招聘方可在这里回复。</p></div>
            <div v-for="item in chatMessages" :key="item.id" class="message-line" :class="{ mine: item.mine }">
              <span class="message-avatar">{{ item.mine ? '我' : candidateInitial(selected) }}</span>
              <div class="message-body">
                <div class="message-meta"><b>{{ item.mine ? '我' : candidateName(selected) }}</b><time>{{ formatTime(item.sendTime) }}</time></div>
                <div class="message-bubble">
                  <a
                    v-if="getHrmRecruitChatAttachment(item.content, item.contentType)?.type === 'resume'"
                    class="message-file"
                    :href="getHrmRecruitChatAttachment(item.content, item.contentType)?.previewUrl"
                    target="_blank"
                    rel="noreferrer"
                  >
                    <span class="message-file-icon">📄</span>
                    <span class="message-file-copy">
                      <b>{{ getHrmRecruitChatAttachment(item.content, item.contentType)?.title }}</b>
                      <em>{{ getHrmRecruitChatAttachment(item.content, item.contentType)?.fileName }}</em>
                    </span>
                  </a>
                  <a
                    v-else-if="getHrmRecruitChatAttachment(item.content, item.contentType)?.type === 'image'"
                    class="message-image"
                    :href="getHrmRecruitChatAttachment(item.content, item.contentType)?.previewUrl"
                    target="_blank"
                    rel="noreferrer"
                  >
                    <img :src="getHrmRecruitChatAttachment(item.content, item.contentType)?.previewUrl" :alt="getHrmRecruitChatAttachment(item.content, item.contentType)?.fileName" />
                    <span>{{ getHrmRecruitChatAttachment(item.content, item.contentType)?.fileName }}</span>
                  </a>
                  <template v-else>{{ item.content }}</template>
                </div>
              </div>
            </div>
          </div>

          <div v-if="canReply" class="reply-composer">
            <div class="composer-head"><span><IconifyIcon icon="lucide:pen-line" />回复求职者</span><small>{{ draft.length }}/500</small></div>
            <a-textarea v-model:value="draft" :maxlength="500" :rows="4" placeholder="请输入想对求职者说的话…" />
            <div class="composer-foot"><a-button type="primary" :loading="sending" :disabled="!draft.trim()" @click="send"><IconifyIcon icon="lucide:send" />发送回复</a-button></div>
          </div>
          <div v-else class="reply-locked"><IconifyIcon icon="lucide:circle-off" /><div><strong>沟通失败</strong><span>当前会话无法继续发送消息。</span></div></div>
        </main>
      </div>
    </a-modal>

    <CandidateResumeModal
      v-model:open="resumeModalOpen"
      :conversation="resumeConversation"
    />

    <AiMatchModal
      v-model:open="aiModalOpen"
      :conversation="aiConversation"
      @analyzed="handleAiAnalyzed"
    />
  </Page>
</template>

<style scoped>
:global(.hrm-recruit-chat-html) {
  --hrm-chat-header-start: #fff;
  --hrm-chat-header-mid: hsl(var(--muted));
  --hrm-chat-header-end: #e9f3ff;
  --hrm-chat-header-border: #dbe4ef;
  --hrm-chat-header-ring: color-mix(in srgb, hsl(var(--primary)) 18%, transparent);
  --hrm-chat-header-ring-soft: color-mix(in srgb, hsl(var(--primary)) 7%, transparent);
  --hrm-chat-shadow: 0 12px 34px rgb(31 56 88 / 10%);
  --hrm-chat-panel-bg: #fff;
  --hrm-chat-subtle-bg: hsl(var(--muted));
  --hrm-chat-input-bg: #fff;
  --hrm-chat-head-bg: #f3f6fa;
  --hrm-chat-hover-bg: #f0f6ff;
  --hrm-chat-chip-bg: hsl(var(--muted));
  --hrm-chat-dialog-bg: #fff;
  --hrm-chat-dialog-header-start: #f8fbff;
  --hrm-chat-dialog-header-end: #edf4fb;
  --hrm-chat-dialog-subtle-bg: hsl(var(--muted));
  --hrm-chat-message-bg: #f4f6f8;
  --hrm-chat-composer-bg: hsl(var(--muted));
  --hrm-chat-border: #dfe6ee;
  --hrm-chat-border-strong: #d2dbe5;
  --hrm-chat-border-soft: #e7edf3;
  --hrm-chat-input-border: #cfd9e5;
  --hrm-chat-text: #344054;
  --hrm-chat-heading-text: #182338;
  --hrm-chat-strong-text: #26364a;
  --hrm-chat-soft-text: #526276;
  --hrm-chat-muted-text: #667085;
  --hrm-chat-subtle-text: #7b8798;
  --hrm-chat-faint-text: #98a2b3;
  --hrm-chat-accent: hsl(var(--primary));
  --hrm-chat-accent-text: #1d6dcc;
  --hrm-chat-accent-bright: #4b95e8;
  --hrm-chat-success: #0fa16a;
  --hrm-chat-success-bg: color-mix(in srgb, hsl(var(--success)) 10%, transparent);
  --hrm-chat-danger: hsl(var(--destructive));
  --hrm-chat-danger-text: #b2435d;
  --hrm-chat-danger-bg: color-mix(in srgb, hsl(var(--destructive)) 8%, transparent);
  --hrm-chat-metrics-bg: rgb(255 255 255 / 72%);
  --hrm-chat-avatar-border: hsl(var(--border));
  --hrm-chat-avatar-bg: linear-gradient(145deg, #eaf4ff, #dbeaff);
  --hrm-chat-avatar-text: #3370ad;
  --hrm-chat-mine-border: #0f66d7;
  --hrm-chat-mine-bg: hsl(var(--primary));
  --hrm-chat-mine-text: #fff;
  --hrm-chat-focus: hsl(var(--primary));
  --hrm-chat-search-icon: #7a8798;
  --hrm-chat-checkbox-border: #9aabbc;
  --hrm-chat-selection-shadow: rgb(31 56 88 / 22%);
}

:global(.hrm-recruit-chat-html.dark) {
  /* 暗色下 --foreground 是近白色（文本色），不能当背景/边框用，否则整页发白、看起来“没跟随主题” */
  --hrm-chat-header-start: hsl(var(--card));
  --hrm-chat-header-mid: #1d222a;
  --hrm-chat-header-end: #182c45;
  --hrm-chat-header-border: hsl(var(--border));
  --hrm-chat-header-ring: rgb(50 143 255 / 16%);
  --hrm-chat-header-ring-soft: rgb(50 143 255 / 4%);
  --hrm-chat-shadow: 0 12px 34px rgb(0 0 0 / 16%);
  --hrm-chat-panel-bg: hsl(var(--card));
  --hrm-chat-subtle-bg: #1d2127;
  --hrm-chat-input-bg: #15181d;
  --hrm-chat-head-bg: #20242b;
  --hrm-chat-hover-bg: #202731;
  --hrm-chat-chip-bg: #20252d;
  --hrm-chat-dialog-bg: #1a1d22;
  --hrm-chat-dialog-header-start: #1b1e24;
  --hrm-chat-dialog-header-end: #202630;
  --hrm-chat-dialog-subtle-bg: #171a1f;
  --hrm-chat-message-bg: #242a32;
  --hrm-chat-composer-bg: #20252c;
  --hrm-chat-border: hsl(var(--border));
  --hrm-chat-border-strong: #343a44;
  --hrm-chat-border-soft: #2c323b;
  --hrm-chat-input-border: #343c48;
  --hrm-chat-text: #dce1e9;
  --hrm-chat-heading-text: hsl(var(--foreground));
  --hrm-chat-strong-text: #f1f4f8;
  --hrm-chat-soft-text: hsl(var(--muted-foreground));
  --hrm-chat-muted-text: #919baa;
  --hrm-chat-subtle-text: #737e8e;
  --hrm-chat-faint-text: #687586;
  --hrm-chat-accent: hsl(var(--primary));
  --hrm-chat-accent-text: #72b5ff;
  --hrm-chat-accent-bright: #6eb2ff;
  --hrm-chat-success: #35c98b;
  --hrm-chat-success-bg: rgb(54 211 153 / 9%);
  --hrm-chat-danger: hsl(var(--destructive));
  --hrm-chat-danger-text: #dc8095;
  --hrm-chat-danger-bg: color-mix(in srgb, hsl(var(--destructive)) 9%, transparent);
  --hrm-chat-metrics-bg: rgb(13 16 21 / 42%);
  --hrm-chat-avatar-border: #385271;
  --hrm-chat-avatar-bg: linear-gradient(145deg, #234e7f, #1b2c46);
  --hrm-chat-avatar-text: #8dc5ff;
  --hrm-chat-mine-border: #1c64a5;
  --hrm-chat-mine-bg: #125a9c;
  --hrm-chat-mine-text: #f6fbff;
  --hrm-chat-focus: hsl(var(--primary));
  --hrm-chat-search-icon: #727e8d;
  --hrm-chat-checkbox-border: #536071;
  --hrm-chat-selection-shadow: rgb(0 0 0 / 90%);
}

:global(.hrm-recruit-chat-html), :global(.hrm-recruit-chat-body) { height: 100vh; overflow: hidden; }
:global(.hrm-recruit-chat-body .relative.flex.min-h-full.w-full) { height: 100%; min-height: 0; overflow: hidden; }
:global(.hrm-recruit-chat-body #__vben_main_content) { min-height: 0; overflow: hidden; }
:global(.hrm-recruit-chat-modal .ant-modal) { transform: none !important; }
:global(.hrm-recruit-chat-modal .ant-modal-content) { background: var(--hrm-chat-dialog-bg); }
:global(.hrm-recruit-chat-modal .ant-modal-close) { color: var(--hrm-chat-muted-text); }
:global(.hrm-recruit-chat-modal .ant-modal-close:hover) { background: var(--hrm-chat-hover-bg); color: var(--hrm-chat-heading-text); }
:global(.hrm-recruit-chat-page) { display: flex; min-height: 0; flex: 1; flex-direction: column; overflow: hidden; }
.chat-workspace { display: flex; height: 100%; min-height: 0; flex-direction: column; gap: 14px; overflow: hidden; padding: 2px; color: var(--hrm-chat-text); }
.chat-header { position: relative; display: flex; min-height: 150px; align-items: center; justify-content: space-between; gap: 32px; overflow: hidden; padding: 28px 34px; border: 1px solid var(--hrm-chat-header-border); border-radius: 6px; background: linear-gradient(110deg, var(--hrm-chat-header-start) 0%, var(--hrm-chat-header-mid) 58%, var(--hrm-chat-header-end) 100%); box-shadow: var(--hrm-chat-shadow); }
.chat-header::after { position: absolute; top: -100px; right: 8%; width: 300px; height: 300px; border: 1px solid var(--hrm-chat-header-ring); border-radius: 50%; box-shadow: 0 0 0 26px var(--hrm-chat-header-ring-soft), 0 0 0 54px var(--hrm-chat-header-ring-soft); content: ''; pointer-events: none; }
.chat-heading { position: relative; z-index: 1; }
.chat-eyebrow { display: flex; align-items: center; gap: 8px; color: var(--hrm-chat-accent-text); font-size: 20px; font-weight: 700; letter-spacing: .12em; }
.live-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--hrm-chat-success); box-shadow: 0 0 0 4px var(--hrm-chat-success-bg); }
.chat-heading h1 { margin: 10px 0 6px; color: var(--hrm-chat-heading-text); font-size: 26px; font-weight: 650; letter-spacing: -.02em; }
.chat-heading p { margin: 0; color: var(--hrm-chat-muted-text); font-size: 15px; }
.chat-metrics { position: relative; z-index: 1; display: flex; min-width: 300px; align-items: stretch; border: 1px solid var(--hrm-chat-border-strong); border-radius: 5px; background: var(--hrm-chat-metrics-bg); }
.chat-metrics div { display: grid; min-width: 98px; padding: 16px 18px; gap: 4px; border-right: 1px solid var(--hrm-chat-border-strong); }
.chat-metrics div:last-child { border-right: 0; }
.chat-metrics strong { color: var(--hrm-chat-heading-text); font-size: 25px; font-weight: 650; line-height: 1; }
.chat-metrics span { color: var(--hrm-chat-subtle-text); font-size: 15px; white-space: nowrap; }
/*
 * AI 筛选输入区：夹在标题与统计之间，自己撑满中间的弹性空间。
 * position/z-index 与标题、统计保持一致，避免被 .chat-header::after 的装饰圆环压住。
 */
.ai-filter { position: relative; z-index: 1; display: grid; min-width: 0; flex: 1 1 360px; max-width: 620px; gap: 8px; }
.ai-filter-box { display: flex; height: 44px; align-items: center; gap: 10px; padding: 0 6px 0 16px; border: 1px solid var(--hrm-chat-border-strong); border-radius: 999px; background: var(--hrm-chat-metrics-bg); transition: border-color .18s ease, box-shadow .18s ease; }
.ai-filter-box:focus-within { border-color: var(--hrm-chat-accent); box-shadow: 0 0 0 3px rgb(22 119 255 / 12%); }
.ai-filter-box > svg { flex: 0 0 auto; color: var(--hrm-chat-accent-bright); font-size: 18px; }
.ai-filter-box input { min-width: 0; flex: 1; border: 0; background: transparent; color: var(--hrm-chat-text); font-size: 14px; outline: none; }
.ai-filter-box input::placeholder { color: var(--hrm-chat-faint-text); }
.ai-filter-run { height: 34px; flex: 0 0 auto; padding: 0 18px; border: 1px solid transparent; border-radius: 999px; background: linear-gradient(135deg, #1677ff, #0f9f9b); color: #fff; cursor: pointer; font-size: 13px; font-weight: 600; white-space: nowrap; transition: opacity .18s ease; }
.ai-filter-run:disabled { cursor: not-allowed; opacity: .45; }
.ai-filter-run:not(:disabled):hover { opacity: .88; }
.ai-filter-summary { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; font-size: 12px; line-height: 1.6; }
.ai-filter-criteria { display: inline-flex; align-items: center; gap: 5px; color: var(--hrm-chat-accent-bright); }
.ai-filter-summary b { color: var(--hrm-chat-strong-text); font-weight: 600; }
.ai-filter-unsupported { color: #d97706; }
.ai-filter-clear { padding: 0; border: 0; background: none; color: var(--hrm-chat-muted-text); cursor: pointer; font-size: 12px; text-decoration: underline; }
.ai-filter-clear:hover { color: var(--hrm-chat-accent-bright); }
.chat-panel { display: flex; min-height: 0; flex: 1; flex-direction: column; overflow: hidden; border: 1px solid var(--hrm-chat-border); border-radius: 6px; background: var(--hrm-chat-panel-bg); }
.chat-panel-toolbar { display: flex; min-height: 72px; align-items: center; justify-content: space-between; gap: 20px; overflow: hidden; padding: 14px 18px 0; border-bottom: 1px solid var(--hrm-chat-border); }
.toolbar-right { display: flex; align-items: center; gap: 14px; }
.profile-filter-bar { display: flex; align-items: center; gap: 22px; padding: 13px 18px; border-bottom: 1px solid var(--hrm-chat-border); background: var(--hrm-chat-subtle-bg); }
.profile-filter-heading { display: flex; min-width: 168px; align-items: center; gap: 9px; }
.profile-filter-heading > div { display: grid; gap: 3px; }
.profile-filter-heading strong { color: var(--hrm-chat-strong-text); font-size: 16px; font-weight: 600; }
.profile-filter-heading small { color: var(--hrm-chat-subtle-text); font-size: 14px; white-space: nowrap; }
.profile-filter-icon { display: grid; width: 28px; height: 28px; place-items: center; border: 1px solid var(--hrm-chat-accent-bright); border-radius: 5px; background: color-mix(in srgb, hsl(var(--primary)) 10%, transparent); color: var(--hrm-chat-accent-text); font-size: 17px; }
.profile-filter-fields { display: grid; min-width: 0; flex: 1; grid-template-columns: repeat(5, minmax(108px, 1fr)) auto; align-items: end; gap: 10px; }
.profile-filter-field { display: grid; min-width: 0; gap: 5px; }
.profile-filter-field > span { color: var(--hrm-chat-muted-text); font-size: 14px; }
.profile-filter-field select, .profile-filter-field input { width: 100%; height: 32px; min-width: 0; padding: 0 9px; border: 1px solid var(--hrm-chat-input-border); border-radius: 4px; outline: 0; background: var(--hrm-chat-input-bg); color: var(--hrm-chat-text); font: inherit; font-size: 15px; }
.profile-filter-field select { cursor: pointer; }
.profile-filter-field input::placeholder { color: var(--hrm-chat-faint-text); }
.profile-filter-field select:focus, .profile-filter-field input:focus { border-color: var(--hrm-chat-focus); box-shadow: 0 0 0 2px color-mix(in srgb, hsl(var(--primary)) 10%, transparent); }
.reset-filter-action { display: inline-flex; height: 32px; align-items: center; gap: 5px; padding: 0 10px; border: 1px solid var(--hrm-chat-border-strong); border-radius: 4px; background: transparent; color: var(--hrm-chat-muted-text); cursor: pointer; font-size: 15px; white-space: nowrap; }
.reset-filter-action:hover:not(:disabled) { border-color: var(--hrm-chat-accent-bright); background: color-mix(in srgb, hsl(var(--primary)) 9%, transparent); color: var(--hrm-chat-accent-text); }
.reset-filter-action:disabled { cursor: not-allowed; opacity: .38; }
.selection-tools { display: flex; align-items: center; gap: 9px; white-space: nowrap; }
.selection-count { color: var(--hrm-chat-muted-text); font-size: 15px; }
.bulk-delete-action, .delete-action { display: inline-flex; align-items: center; gap: 6px; border: 1px solid var(--hrm-chat-danger); border-radius: 4px; background: var(--hrm-chat-danger-bg); color: var(--hrm-chat-danger-text); cursor: pointer; font-size: 16px; transition: border-color .18s ease, background .18s ease, color .18s ease; }
.bulk-delete-action { height: 34px; padding: 0 11px; }
.bulk-delete-action:hover:not(:disabled), .delete-action:hover:not(:disabled) { border-color: var(--hrm-chat-danger); background: var(--hrm-chat-danger); color: #fff; }
.bulk-delete-action:disabled, .delete-action:disabled { cursor: not-allowed; opacity: .42; }
.filter-tabs { display: flex; min-width: 0; flex: 1 1 auto; align-self: stretch; align-items: flex-end; gap: 4px; overflow: hidden; }
.filter-tabs button { position: relative; display: flex; align-items: center; gap: 7px; height: 48px; padding: 0 10px; border: 0; background: transparent; color: var(--hrm-chat-muted-text); cursor: pointer; font-size: 15px; white-space: nowrap; }
.filter-tabs button::after { position: absolute; right: 14px; bottom: -1px; left: 14px; height: 2px; background: transparent; content: ''; }
.filter-tabs button:hover, .filter-tabs button.active { color: var(--hrm-chat-accent-bright); }
.filter-tabs button.active::after { background: var(--hrm-chat-accent); }
.filter-tabs b { min-width: 20px; padding: 2px 6px; border: 1px solid var(--hrm-chat-border-strong); border-radius: 10px; color: var(--hrm-chat-muted-text); font-size: 15px; font-weight: 500; }
.filter-tabs button.active b { border-color: color-mix(in srgb, hsl(var(--primary)) 40%, transparent); background: color-mix(in srgb, hsl(var(--primary)) 12%, transparent); color: var(--hrm-chat-accent-bright); }
.chat-search { display: flex; width: 226px; height: 34px; align-items: center; gap: 8px; padding: 0 10px; border: 1px solid var(--hrm-chat-border-strong); border-radius: 4px; background: var(--hrm-chat-input-bg); color: var(--hrm-chat-search-icon); }
.chat-search input { width: 100%; border: 0; outline: 0; background: transparent; color: var(--hrm-chat-text); font-size: 16px; }
.chat-search input::placeholder { color: var(--hrm-chat-faint-text); }
.chat-search button { display: grid; padding: 0; border: 0; background: transparent; color: var(--hrm-chat-search-icon); cursor: pointer; }
.conversation-viewport { min-height: 0; flex: 1; overflow: auto; }
.conversation-viewport::-webkit-scrollbar:horizontal { height: 0; }
.conversation-table { min-height: 100%; min-width: 1472px; }
.conversation-horizontal-scroll { flex: 0 0 14px; overflow-x: auto; overflow-y: hidden; border-top: 1px solid var(--hrm-chat-border); background: var(--hrm-chat-input-bg); }
.conversation-horizontal-track { height: 1px; min-width: 1472px; }
.conversation-row { display: grid; min-width: 1472px; grid-template-columns: 1.35fr 1.05fr .55fr .62fr .7fr 1fr .75fr 2.05fr 300px; }
.conversation-head { position: sticky; z-index: 2; top: 0; border-bottom: 1px solid var(--hrm-chat-border-strong); background: var(--hrm-chat-head-bg); color: var(--hrm-chat-muted-text); font-size: 16px; }
.conversation-head > div { padding: 15px 14px; font-weight: 600; }
.conversation-head > div:first-child, .conversation-data-row > div:first-child { padding-left: 22px; }
.name-heading { display: flex; align-items: center; gap: 9px; }
.action-heading { position: sticky; z-index: 6; right: 0; min-width: 300px; box-sizing: border-box; border-left: 1px solid var(--hrm-chat-border-strong); background: var(--hrm-chat-head-bg); box-shadow: -12px 0 18px -18px var(--hrm-chat-selection-shadow); text-align: center; }
.conversation-data-row { height: 60px; min-height: 60px; max-height: 60px; align-items: center; box-sizing: border-box; overflow: visible; border-bottom: 1px solid var(--hrm-chat-border); color: var(--hrm-chat-text); transition: background .18s ease; }
.conversation-data-row:hover, .conversation-data-row.selected { background: var(--hrm-chat-hover-bg); }
.conversation-data-row.selected { box-shadow: inset 2px 0 var(--hrm-chat-accent); }
.conversation-data-row > div { min-width: 0; min-height: 0; padding: 0 14px; }
.conversation-data-row > .row-action { position: sticky; z-index: 1; right: 0; min-width: 300px; box-sizing: border-box; border-left: 1px solid var(--hrm-chat-border-strong); background: var(--hrm-chat-panel-bg); box-shadow: -12px 0 18px -18px var(--hrm-chat-selection-shadow); }
.conversation-data-row:hover > .row-action, .conversation-data-row.selected > .row-action { background: var(--hrm-chat-hover-bg); }
.candidate-cell { position: relative; display: flex; align-items: center; gap: 10px; }
.selection-checkbox { width: 14px; height: 14px; flex: 0 0 auto; margin: 0; border: 1px solid var(--hrm-chat-checkbox-border); border-radius: 3px; accent-color: var(--hrm-chat-accent); cursor: pointer; }
.selection-checkbox:focus-visible { outline: 2px solid hsl(var(--primary) / 65%); outline-offset: 2px; }
.candidate-avatar-wrap { position: relative; width: 36px; height: 36px; flex: 0 0 auto; cursor: pointer; }
/*
 * 头像与姓名是「查看在线简历」的入口。只给 hover / 键盘焦点加一层强调色，
 * 不改默认外观，免得和行尾的「立即沟通」按钮抢注意力。
 */
.candidate-avatar-wrap:hover .candidate-avatar, .candidate-avatar-wrap:focus-visible .candidate-avatar,
.dialog-avatar:hover, .dialog-avatar:focus-visible { border-color: var(--hrm-chat-accent-bright); }
.candidate-copy strong, .dialog-name-line h2 { cursor: pointer; }
.candidate-copy strong:hover, .candidate-copy strong:focus-visible,
.dialog-name-line h2:hover, .dialog-name-line h2:focus-visible { color: var(--hrm-chat-accent-bright); outline: none; }
.candidate-avatar, .dialog-avatar { display: grid; width: 36px; height: 36px; flex: 0 0 auto; place-items: center; overflow: hidden; border: 1px solid var(--hrm-chat-avatar-border); border-radius: 50%; background: var(--hrm-chat-avatar-bg); color: var(--hrm-chat-avatar-text); font-size: 16px; font-weight: 700; }
.candidate-avatar img, .dialog-avatar img { width: 100%; height: 100%; object-fit: cover; }
.candidate-copy { display: grid; min-width: 0; gap: 5px; }
.candidate-meta { display: flex; min-width: 0; align-items: center; gap: 8px; }
/*
 * AI 匹配分徽标：分数色阶与 AI 弹窗、候选人详情页保持一致。
 * 没分析过时退化成低调的入口，不与「立即沟通」抢注意力。
 */
.ai-badge { display: inline-flex; height: 20px; flex: 0 0 auto; align-items: center; gap: 4px; padding: 0 8px; border: 1px solid transparent; border-radius: 999px; cursor: pointer; font-size: 12px; font-weight: 600; white-space: nowrap; }
.ai-badge.good { border-color: rgb(18 185 129 / 35%); background: var(--hrm-chat-success-bg); color: var(--hrm-chat-success); }
.ai-badge.mid { border-color: rgb(217 119 6 / 32%); background: rgb(217 119 6 / 10%); color: #d97706; }
.ai-badge.low { border-color: rgb(194 74 99 / 32%); background: var(--hrm-chat-danger-bg); color: var(--hrm-chat-danger); }
.ai-badge.idle { border-color: var(--hrm-chat-border-strong); background: transparent; color: var(--hrm-chat-muted-text); font-weight: 500; }
.ai-badge:hover { border-color: var(--hrm-chat-accent-bright); color: var(--hrm-chat-accent-bright); }
.candidate-copy strong, .post-cell strong { overflow: hidden; color: var(--hrm-chat-strong-text); font-size: 15px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.candidate-copy small { display: flex; align-items: center; gap: 5px; overflow: hidden; color: var(--hrm-chat-subtle-text); font-size: 15px; text-overflow: ellipsis; white-space: nowrap; }
.status-label i, .dialog-status i { width: 5px; height: 5px; border-radius: 50%; background: currentColor; }
.status-waiting { color: var(--hrm-chat-accent-bright) !important; }
.status-replied { color: var(--hrm-chat-success) !important; }
.status-failed { color: var(--hrm-chat-subtle-text) !important; }
.unread-count { position: absolute; top: -6px; right: -6px; min-width: 16px; padding: 1px 4px; border: 2px solid var(--hrm-chat-panel-bg); border-radius: 10px; background: var(--hrm-chat-accent); color: #fff; font-size: 13px; font-style: normal; line-height: 12px; text-align: center; }
.post-cell { display: grid; gap: 5px; }
.post-cell small { overflow: hidden; color: var(--hrm-chat-subtle-text); font-size: 15px; text-overflow: ellipsis; white-space: nowrap; }
.muted-cell, .school-cell { overflow: hidden; color: var(--hrm-chat-soft-text); font-size: 16px; text-overflow: ellipsis; white-space: nowrap; }
.school-cell { color: var(--hrm-chat-strong-text); }
.last-message-cell { display: grid; min-width: 0; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 7px; }
.last-message-cell p { overflow: hidden; margin: 0; color: var(--hrm-chat-soft-text); font-size: 16px; text-overflow: ellipsis; white-space: nowrap; }
.last-message-cell time { color: var(--hrm-chat-faint-text); font-size: 14px; white-space: nowrap; }
.message-sender { color: var(--hrm-chat-accent-text); font-size: 14px; white-space: nowrap; }
.row-action { display: grid; grid-template-columns: minmax(78px, 1fr) minmax(104px, 1.25fr) minmax(58px, .75fr); align-items: center; gap: 8px; padding: 0 10px !important; }
.chat-action { display: inline-flex; height: 34px; min-width: 0; align-items: center; justify-content: center; gap: 6px; padding: 0 8px; border: 1px solid var(--hrm-chat-accent-text); border-radius: 6px; background: color-mix(in srgb, hsl(var(--primary)) 12%, transparent); color: var(--hrm-chat-accent-bright); cursor: pointer; font-size: 15px; font-weight: 600; white-space: nowrap; transition: border-color .18s ease, background .18s ease, color .18s ease, transform .18s ease; }
.chat-action:hover { border-color: var(--hrm-chat-accent); background: var(--hrm-chat-accent); box-shadow: 0 5px 12px color-mix(in srgb, hsl(var(--primary)) 22%, transparent); color: #fff; transform: translateY(-1px); }
.convert-action { display: inline-flex; height: 34px; min-width: 0; align-items: center; justify-content: center; gap: 6px; padding: 0 8px; border: 1px solid var(--hrm-chat-success); border-radius: 6px; background: var(--hrm-chat-success-bg); color: var(--hrm-chat-success); cursor: pointer; font-size: 15px; font-weight: 600; white-space: nowrap; transition: border-color .18s ease, background .18s ease, color .18s ease, transform .18s ease; }
.convert-action:hover { border-color: var(--hrm-chat-success); background: var(--hrm-chat-success); box-shadow: 0 5px 12px color-mix(in srgb, hsl(var(--success)) 20%, transparent); color: #fff; transform: translateY(-1px); }
.converted-action { display: inline-flex; min-width: 0; align-items: center; justify-content: center; gap: 5px; color: var(--hrm-chat-success); font-size: 14px; font-weight: 600; white-space: nowrap; }
.delete-action { height: 34px; min-width: 0; padding: 0 8px; border-radius: 6px; font-size: 15px; font-weight: 600; white-space: nowrap; }
.delete-action:hover:not(:disabled) { box-shadow: 0 5px 12px color-mix(in srgb, hsl(var(--destructive)) 18%, transparent); transform: translateY(-1px); }
.table-state { display: grid; min-height: 260px; place-items: center; align-content: center; color: var(--hrm-chat-subtle-text); font-size: 15px; }
.table-empty { gap: 8px; }
.table-empty strong { color: var(--hrm-chat-strong-text); font-size: 16px; }
.table-empty p { margin: 0; color: var(--hrm-chat-subtle-text); font-size: 16px; }
.empty-icon { display: grid; width: 48px; height: 48px; margin-bottom: 4px; place-items: center; border: 1px solid var(--hrm-chat-border-strong); border-radius: 12px; background: var(--hrm-chat-chip-bg); color: var(--hrm-chat-accent-bright); font-size: 22px; }
.chat-panel-footer { display: flex; min-height: 58px; align-items: center; justify-content: space-between; gap: 16px; padding: 8px 18px; border-top: 1px solid var(--hrm-chat-border); color: var(--hrm-chat-subtle-text); font-size: 15px; }
.table-pagination { display: flex; align-items: center; justify-content: flex-end; gap: 5px; }
.page-size-picker { display: inline-flex; align-items: center; gap: 6px; margin-right: 8px; white-space: nowrap; }
.page-size-picker select { height: 29px; padding: 0 7px; border: 1px solid var(--hrm-chat-input-border); border-radius: 4px; outline: 0; background: var(--hrm-chat-input-bg); color: var(--hrm-chat-text); cursor: pointer; font: inherit; }
.page-indicator { margin-right: 5px; color: var(--hrm-chat-muted-text); white-space: nowrap; }
.table-pagination button { min-width: 29px; height: 29px; padding: 0 7px; border: 1px solid var(--hrm-chat-input-border); border-radius: 4px; background: var(--hrm-chat-chip-bg); color: var(--hrm-chat-soft-text); cursor: pointer; font-size: 14px; }
.table-pagination button:hover:not(:disabled), .table-pagination button.active { border-color: var(--hrm-chat-accent); background: color-mix(in srgb, hsl(var(--primary)) 16%, transparent); color: var(--hrm-chat-accent-bright); }
.table-pagination button:disabled { cursor: not-allowed; opacity: .38; }
.dialog-card { display: flex; height: min(86vh, 860px); max-height: calc(100vh - 32px); margin: -24px; flex-direction: column; overflow: hidden; border-radius: 7px; background: var(--hrm-chat-dialog-bg); color: var(--hrm-chat-text); }
:global(.hrm-recruit-chat-modal .ant-modal) { top: 16px; padding-bottom: 16px; }
.dialog-header { display: flex; min-height: 86px; flex: 0 0 auto; align-items: center; justify-content: space-between; gap: 20px; padding: 18px 76px 18px 24px; border-bottom: 1px solid var(--hrm-chat-border-strong); background: linear-gradient(105deg, var(--hrm-chat-dialog-header-start), var(--hrm-chat-dialog-header-end)); }
.dialog-person { display: flex; align-items: center; gap: 13px; min-width: 0; }
.dialog-avatar { width: 44px; height: 44px; font-size: 19px; }
.dialog-name-line { display: flex; align-items: center; gap: 10px; }
.dialog-name-line h2 { margin: 0; color: var(--hrm-chat-heading-text); font-size: 19px; font-weight: 650; }
.dialog-person p { margin: 5px 0 0; color: var(--hrm-chat-muted-text); font-size: 16px; }
.dialog-person p b { color: var(--hrm-chat-accent-text); font-weight: 500; }
.dialog-status { display: inline-flex; align-items: center; gap: 5px; font-size: 15px; }
.dialog-top-actions { display: flex; align-items: center; gap: 16px; }
.application-pill { display: inline-flex; align-items: center; gap: 5px; padding: 5px 9px; border: 1px solid color-mix(in srgb, hsl(var(--success)) 35%, transparent); border-radius: 4px; background: var(--hrm-chat-success-bg); color: var(--hrm-chat-success); font-size: 15px; }
.application-pill.ready { border-color: color-mix(in srgb, hsl(var(--primary)) 40%, transparent); background: color-mix(in srgb, hsl(var(--primary)) 10%, transparent); color: var(--hrm-chat-accent-bright); }
.dialog-profile-strip { display: flex; min-height: 64px; align-items: center; gap: 0; padding: 0 24px; border-bottom: 1px solid var(--hrm-chat-border-soft); background: var(--hrm-chat-dialog-subtle-bg); }
.dialog-profile-strip > div { display: grid; min-width: 120px; padding-right: 24px; gap: 5px; border-right: 1px solid var(--hrm-chat-border-soft); }
.dialog-profile-strip > div + div { padding-left: 24px; }
.dialog-profile-strip > div:last-child { flex: 1; border-right: 0; }
.dialog-profile-strip span { color: var(--hrm-chat-muted-text); font-size: 15px; }
.dialog-profile-strip strong { overflow: hidden; color: var(--hrm-chat-text); font-size: 16px; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.dialog-main { display: flex; min-height: 0; flex: 1; flex-direction: column; overflow: hidden; padding: 20px 24px 22px; }
.dialog-section-title { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.dialog-section-title span { color: var(--hrm-chat-strong-text); font-size: 15px; font-weight: 600; }
.dialog-section-title small { color: var(--hrm-chat-faint-text); font-size: 15px; }
.dialog-messages { display: flex; min-height: 0; flex: 1; flex-direction: column; gap: 16px; overflow-y: auto; padding: 5px 4px 14px 2px; }
.message-line { display: flex; align-items: flex-start; gap: 9px; max-width: 76%; }
.message-line.mine { align-self: flex-end; flex-direction: row-reverse; }
.message-avatar { display: grid; width: 28px; height: 28px; flex: 0 0 auto; place-items: center; border: 1px solid var(--hrm-chat-avatar-border); border-radius: 50%; background: var(--hrm-chat-avatar-bg); color: var(--hrm-chat-avatar-text); font-size: 14px; font-weight: 600; }
.message-line.mine .message-avatar { border-color: var(--hrm-chat-mine-border); background: var(--hrm-chat-mine-bg); color: var(--hrm-chat-mine-text); }
.message-body { min-width: 0; }
.message-line.mine .message-body { text-align: right; }
.message-meta { display: flex; align-items: center; gap: 8px; margin-bottom: 5px; color: var(--hrm-chat-muted-text); font-size: 14px; }
.message-line.mine .message-meta { justify-content: flex-end; }
.message-meta b { color: var(--hrm-chat-soft-text); font-weight: 500; }
.message-meta time { color: var(--hrm-chat-faint-text); }
.message-bubble { padding: 9px 12px; border: 1px solid var(--hrm-chat-border-strong); border-radius: 4px 10px 10px 10px; background: var(--hrm-chat-message-bg); color: var(--hrm-chat-text); font-size: 16px; line-height: 1.6; text-align: left; white-space: pre-wrap; word-break: break-word; }
.message-line.mine .message-bubble { border-color: var(--hrm-chat-mine-border); border-radius: 10px 4px 10px 10px; background: var(--hrm-chat-mine-bg); color: var(--hrm-chat-mine-text); }
.message-image { display: inline-flex; max-width: 250px; flex-direction: column; gap: 6px; color: inherit; text-decoration: none; }
.message-image img { display: block; max-width: 250px; max-height: 190px; border-radius: 4px; object-fit: contain; }
.message-image span { overflow: hidden; color: var(--hrm-chat-muted-text); font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.message-file { display: inline-flex; min-width: 230px; align-items: center; gap: 10px; color: inherit; text-decoration: none; }
.message-file-icon { font-size: 24px; line-height: 1; }
.message-file-copy { display: grid; min-width: 0; gap: 3px; }
.message-file-copy b, .message-file-copy em { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.message-file-copy b { color: var(--hrm-chat-strong-text); font-weight: 600; }
.message-file-copy em { color: var(--hrm-chat-muted-text); font-size: 14px; font-style: normal; }
.dialog-state { display: grid; min-height: 190px; place-items: center; align-content: center; color: var(--hrm-chat-subtle-text); font-size: 16px; }
.dialog-no-message { gap: 5px; }
.dialog-no-message > span { display: grid; width: 40px; height: 40px; margin-bottom: 3px; place-items: center; border: 1px dashed var(--hrm-chat-border-strong); border-radius: 10px; color: var(--hrm-chat-accent-bright); font-size: 20px; }
.dialog-no-message strong { color: var(--hrm-chat-soft-text); font-size: 16px; font-weight: 500; }
.dialog-no-message p { margin: 0; color: var(--hrm-chat-faint-text); font-size: 15px; }
.reply-composer { margin-top: 4px; padding: 13px 14px 11px; border: 1px solid var(--hrm-chat-border-strong); border-radius: 5px; background: var(--hrm-chat-composer-bg); }
.composer-head, .composer-foot { display: flex; align-items: center; justify-content: space-between; }
.composer-head { margin-bottom: 8px; color: var(--hrm-chat-text); font-size: 16px; }
.composer-head span { display: inline-flex; align-items: center; gap: 6px; }
.composer-head small { color: var(--hrm-chat-faint-text); font-size: 14px; }
.reply-composer :deep(.ant-input) { min-height: 74px !important; padding: 9px 10px; border-color: var(--hrm-chat-input-border) !important; border-radius: 4px; background: var(--hrm-chat-dialog-subtle-bg) !important; color: var(--hrm-chat-text) !important; font-size: 16px; resize: none; }
.reply-composer :deep(.ant-input::placeholder) { color: var(--hrm-chat-faint-text); }
.composer-foot { display: flex; justify-content: flex-end; margin-top: 10px; color: var(--hrm-chat-subtle-text); font-size: 14px; }
.composer-foot :deep(.ant-btn) { display: inline-flex; height: 31px; align-items: center; gap: 6px; border-radius: 4px; font-size: 16px; }
.reply-locked { display: flex; align-items: center; gap: 11px; margin-top: 4px; padding: 15px 16px; border: 1px solid var(--hrm-chat-border-strong); border-radius: 5px; background: var(--hrm-chat-composer-bg); color: var(--hrm-chat-subtle-text); }
.reply-locked > :deep(.iconify) { color: var(--hrm-chat-muted-text); font-size: 20px; }
.reply-locked div { display: grid; gap: 4px; }
.reply-locked strong { color: var(--hrm-chat-soft-text); font-size: 16px; font-weight: 500; }
.reply-locked span { color: var(--hrm-chat-faint-text); font-size: 15px; }
@media (max-width: 900px) {
  .chat-header { align-items: flex-start; flex-direction: column; }
  .chat-metrics { width: 100%; }
  .chat-metrics div { flex: 1; }
  .ai-filter { width: 100%; max-width: none; }
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
  .conversation-data-row, .chat-action, .convert-action, .delete-action, .bulk-delete-action { transition: none; }
}
</style>
