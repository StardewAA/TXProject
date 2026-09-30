<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';
import { useAccessStore, useUserStore } from '@vben/stores';

import { Modal as AntModal, message } from 'ant-design-vue';

import { getTenantSimpleList } from '#/api/core/auth';
import {
  acceptPortalRecruitOffer,
  getPortalRecruitApplicationPage,
  getPortalRecruitPost,
  type HrmPortalRecruitApi,
  rejectPortalRecruitOffer,
} from '#/api/hrm/portal/recruit';
import {
  getRecruitChatConversationList,
  getRecruitChatMessageList,
  type HrmPortalRecruitChatApi,
  markRecruitChatRead,
  openRecruitChatConversation,
  sendRecruitChatMessage,
} from '#/api/hrm/portal/recruit/chat';
import { getHrmRecruitChatAttachment } from '#/api/hrm/portal/recruit/chat-attachment';
import { uploadHrmRecruitFile } from '#/api/hrm/portal/recruit/file';
import {
  getPortalResumeList,
  type HrmPortalResumeApi,
} from '#/api/hrm/portal/resume';
import { getUserProfile } from '#/api/system/user/profile';
import { useAuthStore } from '#/store';

import RecruitApplyModal from './components/apply-modal.vue';
import RecruitTopbar from './components/recruit-topbar.vue';

defineProps<{ embedded?: boolean }>();

type ConversationPreferences = {
  ignored: number[];
  pinned: number[];
  remarks: Record<number, string>;
  removed: number[];
};

const route = useRoute();
const router = useRouter();
const accessStore = useAccessStore();
const userStore = useUserStore();
const authStore = useAuthStore();

const loading = ref(false);
const sending = ref(false);
const resumeSending = ref(false);
const imageUploading = ref(false);
const keyword = ref('');
const tab = ref<'all' | 'applied' | 'communication' | 'interview' | 'unread'>('all');
const draft = ref('');
const conversations = ref<HrmPortalRecruitChatApi.Conversation[]>([]);
const messages = ref<HrmPortalRecruitChatApi.ChatMessage[]>([]);
const activeId = ref<number>();
const applyModalOpen = ref(false);
const applicationConversationId = ref<number>();
const applicationPostId = ref<number>();
const activeMenuId = ref<number>();
const imageInput = ref<HTMLInputElement>();
const postDetails = ref<Record<number, HrmPortalRecruitApi.Post>>({});
const interviewApplicationIds = ref<number[]>([]);
const pinnedConversationIds = ref<number[]>([]);
const ignoredConversationIds = ref<number[]>([]);
const removedConversationIds = ref<number[]>([]);
const conversationRemarks = ref<Record<number, string>>({});
const interviewFilterLoaded = ref(false);
const offersByApplicationId = ref<Record<number, HrmPortalRecruitApi.Offer>>({});
const conversationPane = ref<HTMLElement>();
let pollTimer: ReturnType<typeof setInterval> | undefined;
let selectionRequestId = 0;
let conversationListRequestId = 0;

const activeConversation = computed(() =>
  conversations.value.find((item) => item.id === activeId.value),
);
const canSendResume = computed(() => {
  const conversation = activeConversation.value;
  return Boolean(
    conversation &&
      activeId.value &&
      conversation.applyQualified === true &&
      conversation.status !== 3 &&
      conversation.applicationStatus !== 'SUBMITTED' &&
      !sending.value &&
      !resumeSending.value,
  );
});
const orderedConversations = computed(() => {
  return [...conversations.value].sort((left, right) => {
    const leftPinned = pinnedConversationIds.value.includes(left.id) ? 1 : 0;
    const rightPinned = pinnedConversationIds.value.includes(right.id) ? 1 : 0;
    return rightPinned - leftPinned;
  });
});
const filteredConversations = computed(() => {
  const q = keyword.value.trim();
  return orderedConversations.value.filter((item) => {
    if (
      ignoredConversationIds.value.includes(item.id) ||
      removedConversationIds.value.includes(item.id)
    ) {
      return false;
    }
    if (tab.value === 'unread' && !item.unreadCount) return false;
    if (
      tab.value === 'communication' &&
      (!(item.lastTime || item.lastContent) || item.applicationStatus === 'SUBMITTED')
    ) {
      return false;
    }
    if (tab.value === 'applied' && item.applicationStatus !== 'SUBMITTED') return false;
    if (tab.value === 'interview' && !interviewApplicationIds.value.includes(item.applicationId || 0)) return false;
    if (!q) return true;
    return [item.peerUserName, companyName(item), recruiterPosition(item), item.lastContent]
      .filter(Boolean)
      .some((value) => String(value).includes(q));
  });
});
const loggedIn = computed(() => Boolean(accessStore.accessToken));
const displayName = computed(
  () => userStore.userInfo?.nickname || userStore.userInfo?.username || '用户',
);
const unreadTotal = computed(() =>
  conversations.value.reduce((sum, item) => sum + (item.unreadCount || 0), 0),
);
const canSend = computed(
  () =>
    Boolean(
      draft.value.trim() &&
        activeId.value &&
        !sending.value &&
        activeConversation.value?.status !== 3,
    ),
);

function statusLabel(item: HrmPortalRecruitChatApi.Conversation) {
  if (item.status === 0) return item.hrSide ? '等待求职者消息' : '待发起';
  if (item.status === 1) return item.hrSide ? '等待招聘方回复' : '等待回复';
  if (item.status === 2) return '招聘方已回复';
  if (item.status === 3) return '沟通已结束';
  return '等待回复';
}

function statusClass(item: HrmPortalRecruitChatApi.Conversation) {
  if (item.status === 0) return 'pending';
  if (item.status === 2) return 'qualified';
  if (item.status === 3) return 'ended';
  return 'waiting';
}

function offerStatusLabel(status?: string) {
  return {
    APPROVED: '审批通过，待发送',
    APPROVING: '招聘方审批中',
    CANDIDATE_REJECTED: '求职者已拒绝',
    CONFIRMED: '已接受，待入职',
    DRAFT: '待提交审批',
    EXPIRED: '已过期',
    REJECTED: '审批未通过',
    SENT: '待确认',
    WITHDRAWN: '已撤回',
  }[status || ''] || '处理中';
}

function offerForConversation(item?: HrmPortalRecruitChatApi.Conversation) {
  return item?.applicationId
    ? offersByApplicationId.value[item.applicationId]
    : undefined;
}

async function acceptOffer(item: HrmPortalRecruitChatApi.Conversation) {
  const offer = offerForConversation(item);
  if (!offer) return;
  try {
    await acceptPortalRecruitOffer(offer.id);
    message.success('Offer 已接受，已进入待入职');
    await loadConversations(item.id);
    interviewFilterLoaded.value = false;
    await loadInterviewPostIds();
  } catch (error: any) {
    message.error(error?.message || '接受 Offer 失败，请刷新后重试');
  }
}

/**
 * 问题 9：录用通知邮件里的「同意 / 拒绝」链接落地处理
 *
 * <p>邮件带 {@code offerId} 与 {@code offerAction=accept|reject}，用户登录后落到这里：
 * 先在会话列表里定位该 Offer 对应的沟通会话，再弹出确认框，确认后调用既有的
 * 接受/拒绝接口（后台状态随之更新）。处理完清掉查询参数，避免刷新重复触发。</p>
 */
async function handleOfferMailAction() {
  const rawOfferId = Array.isArray(route.query.offerId)
    ? route.query.offerId[0]
    : route.query.offerId;
  const rawAction = Array.isArray(route.query.offerAction)
    ? route.query.offerAction[0]
    : route.query.offerAction;
  const offerId = Number(rawOfferId);
  if (!offerId || (rawAction !== 'accept' && rawAction !== 'reject')) {
    return;
  }
  const target = conversations.value.find(
    (item) => offerForConversation(item)?.id === offerId,
  );
  if (!target) {
    message.warning('未找到该录用通知对应的沟通会话，请到「应聘记录」中查看');
    return;
  }
  await selectConversation(target.id);
  const offer = offerForConversation(target);
  if (offer?.approvalStatus !== 'SENT') {
    message.info('该录用通知已处理，无需重复操作');
  } else if (rawAction === 'accept') {
    AntModal.confirm({
      title: '确认接受录用通知？',
      content: '接受后进入待入职流程，HR 会与您联系入职事宜。',
      okText: '确认接受',
      cancelText: '再想想',
      onOk: async () => {
        await acceptOffer(target);
      },
    });
  } else {
    await rejectOffer(target);
  }
  // 清掉邮件链接参数，避免刷新页面时重复弹窗
  const query = { ...route.query };
  delete query.offerAction;
  delete query.offerId;
  void router.replace({ query });
}


function rejectOffer(item: HrmPortalRecruitChatApi.Conversation) {
  const offer = offerForConversation(item);
  if (!offer) return;
  AntModal.confirm({
    title: '确认拒绝 Offer？',
    content: '拒绝后本次应聘流程将结束。',
    okText: '确认拒绝',
    cancelText: '取消',
    onOk: async () => {
      try {
        await rejectPortalRecruitOffer(offer.id, '求职者拒绝 Offer');
        message.success('Offer 已拒绝');
        await loadConversations(item.id);
        interviewFilterLoaded.value = false;
        await loadInterviewPostIds();
      } catch (error: any) {
        message.error(error?.message || '拒绝 Offer 失败，请刷新后重试');
      }
    },
  });
}

function formatSalary(item?: HrmPortalRecruitChatApi.Conversation) {
  if (!item) return '';
  if (item.salaryUnit === -1 || (item.minSalary ?? 0) < 0) return '面议';
  const min = item.minSalary;
  const max = item.maxSalary;
  if (min == null && max == null) return '薪资面议';
  const format = (value?: number) => (value == null ? '' : `${value / 1000}K`);
  if (min != null && max != null) return `${format(min)}-${format(max)}`;
  return min != null ? `${format(min)}起` : `${format(max)}以内`;
}

function formatTime(value?: string) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const now = new Date();
  const sameDay = date.toDateString() === now.toDateString();
  const hh = String(date.getHours()).padStart(2, '0');
  const mm = String(date.getMinutes()).padStart(2, '0');
  if (sameDay) return `${hh}:${mm}`;
  return `${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${hh}:${mm}`;
}

function recruiterName(item: HrmPortalRecruitChatApi.Conversation) {
  return item.peerUserName || (item.hrSide ? '候选人' : '招聘人');
}

/**
 * 会话列表要显示招聘企业名。岗位接口只回传 tenantId、不带 companyName，
 * 所以按租户名兜底（与职位详情页 getCompanyName 的口径一致），
 * 否则所有会话都会显示成占位文案。
 */
const tenantNames = ref<Record<number, string>>({});

async function loadTenantNames() {
  try {
    const tenants = (await getTenantSimpleList()) as Array<{ id?: number; name?: string }>;
    tenantNames.value = Object.fromEntries(
      tenants
        .filter((tenant) => tenant.id && tenant.name)
        .map((tenant) => [tenant.id, tenant.name]),
    );
  } catch (error) {
    console.warn('[PortalRecruitMessages] 加载企业名称失败', error);
  }
}

function companyName(item: HrmPortalRecruitChatApi.Conversation) {
  const post = postDetails.value[item.postId];
  return (
    post?.companyShortName ||
    post?.companyName ||
    (post?.tenantId ? tenantNames.value[post.tenantId] : undefined) ||
    '招聘企业待确认'
  );
}

function recruiterPosition(item: HrmPortalRecruitChatApi.Conversation) {
  return postDetails.value[item.postId]?.postName || item.postName || '招聘职位';
}

/* --------------------------------------------------------------------
 * 会话列表与会话头部的展示派生值。
 * 会话接口只给「谁、哪个岗、最后说了什么、几时说的」，而列表要像招聘
 * 网站那样一眼扫清这几件事，所以先在这一层拼成人话，模板里只做取值。
 * ------------------------------------------------------------------ */

/**
 * 头像是「人」不是「公司」：只认招聘方本人的头像，没有就退回姓氏方块。
 * 不要拿企业 logo 兜底 —— logo 多是横版带字，压进 42px 方块后糊到看不清，
 * 而且同一家企业的多个会话会连成一片一模一样的图，反而分不出谁是谁。
 */
function avatarOf(item: HrmPortalRecruitChatApi.Conversation) {
  return item.peerUserAvatar || '';
}

function initialOf(item: HrmPortalRecruitChatApi.Conversation) {
  return (recruiterName(item) || companyName(item) || '招').trim().slice(0, 1);
}

/** 直辖市的 areaName 会写成「北京市 北京市 东城区」，去掉前后重复的一段 */
function formatArea(value?: string) {
  const parts = (value || '').split(/\s+/).filter(Boolean);
  const unique = parts.filter((part, index) => part !== parts[index - 1]);
  return unique.join(' ') || '地点待确认';
}

/** 列表时间精确到天即可，只有当天才显示时刻，免得一行被时分秒撑宽 */
function formatListTime(value?: string) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const now = new Date();
  const pad = (value: number) => String(value).padStart(2, '0');
  if (date.toDateString() === now.toDateString()) {
    return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }
  const monthDay = `${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
  return date.getFullYear() === now.getFullYear()
    ? monthDay
    : `${date.getFullYear()}-${monthDay}`;
}

/**
 * 最后一条消息的摘要。图片和简历在 content 里是竖线拼接的载荷，
 * 原样铺进列表会露出一整串文件地址，所以先翻译成人话。
 */
function lastMessagePreview(item: HrmPortalRecruitChatApi.Conversation) {
  const raw = item.lastContent;
  if (!raw) return item.lastTime ? '暂无文本消息' : '还没有消息，打个招呼吧';
  if (raw.startsWith('__HRM_IMAGE__|')) return '[图片]';
  const resume = getHrmRecruitChatAttachment(raw, 2);
  if (resume) return `[简历] ${resume.fileName}`;
  return item.lastSenderUserId === currentUserId() ? `我：${raw}` : raw;
}

/**
 * 应聘进度轨 —— 这一页的签名元素。
 * 会话上拿得到的只有投递状态、面试记录、Offer 三处标记，拼成求职者
 * 真正关心的四段路：沟通 → 投递 → 面试 → Offer。放在会话头部，
 * 「和这家走到哪一步了」不必翻聊天记录就能看见。
 */
const STAGES = ['沟通', '投递', '面试', 'Offer'] as const;

function stageIndexOf(item?: HrmPortalRecruitChatApi.Conversation) {
  if (!item) return 0;
  const applicationId = item.applicationId || 0;
  if (applicationId && offersByApplicationId.value[applicationId]) return 3;
  if (applicationId && interviewApplicationIds.value.includes(applicationId)) {
    return 2;
  }
  if (item.applicationStatus === 'SUBMITTED') return 1;
  return 0;
}

/**
 * 进度轨旁边的一句话：轨道说「到哪了」，这句说「接下来做什么」。
 * 面试那段说「已进入面试环节」而不是「面试安排中」—— 面试接口只回传
 * 有没有面试记录，不区分待面试和已面完等结果，写成「安排中」在面试
 * 已经结束的人那里就是错的。
 */
function stageCaption(item?: HrmPortalRecruitChatApi.Conversation) {
  if (!item) return '';
  const index = stageIndexOf(item);
  if (index === 3) return '已进入 Offer 环节';
  if (index === 2) return '已进入面试环节';
  if (index === 1) return '简历已投递，等待 HR 查看';
  if (item.applyQualified) return 'HR 已回复，可以发送简历';
  return '等待 HR 回复后可投递简历';
}

function showStamp(index: number) {
  const current = messages.value[index];
  if (!current?.sendTime) return false;
  if (index === 0) return true;
  const prev = messages.value[index - 1];
  if (!prev?.sendTime) return true;
  return (
    Math.abs(new Date(current.sendTime).getTime() - new Date(prev.sendTime).getTime()) >
    5 * 60 * 1000
  );
}

/**
 * 同一个人连着发的消息算一组：组内收窄间距、只有组尾那条留尖角。
 * 聊天界面里这一步比任何配色都管用 —— 「哪几句是一口气说完的」一眼就分得出来。
 */
function isGroupEnd(index: number) {
  const current = messages.value[index];
  const next = messages.value[index + 1];
  if (!current || !next) return true;
  if (next.mine !== current.mine) return true;
  // 附件气泡没有底盘，和文字气泡贴在一起会糊成一团，强制分组
  const currentHasAttachment = Boolean(
    getHrmRecruitChatAttachment(current.content, current.contentType),
  );
  const nextHasAttachment = Boolean(
    getHrmRecruitChatAttachment(next.content, next.contentType),
  );
  if (currentHasAttachment !== nextHasAttachment) return true;
  const gap =
    new Date(next.sendTime || 0).getTime() -
    new Date(current.sendTime || 0).getTime();
  return !Number.isFinite(gap) || gap > 5 * 60 * 1000;
}

/** 附件或可显示文本都没有的消息不渲染气泡，避免消息流出现空气泡 */
function hasDisplayableContent(item: HrmPortalRecruitChatApi.ChatMessage) {
  if (getHrmRecruitChatAttachment(item.content, item.contentType)) return true;
  return Boolean(item.content && String(item.content).trim());
}

/** 窄屏下从会话详情返回会话列表 */
function backToList() {
  activeId.value = undefined;
}

function goLogin() {
  void router.push({ name: 'Login', query: { redirect: route.fullPath } });
}

function goJobs() {
  void router.push({ name: 'PortalRecruit', query: { section: 'jobs' } });
}

function currentUserId() {
  const info = userStore.userInfo as null | { id?: number | string; userId?: number | string };
  const id = Number(info?.id ?? info?.userId);
  return Number.isFinite(id) && id > 0 ? id : undefined;
}

function conversationPreferencesKey() {
  return `portal-recruit-chat-preferences:${currentUserId() || 'session'}`;
}

function loadConversationPreferences() {
  try {
    const saved = JSON.parse(
      localStorage.getItem(conversationPreferencesKey()) || '{}',
    ) as Partial<ConversationPreferences>;
    pinnedConversationIds.value = Array.isArray(saved.pinned)
      ? saved.pinned.filter((id): id is number => typeof id === 'number')
      : [];
    ignoredConversationIds.value = Array.isArray(saved.ignored)
      ? saved.ignored.filter((id): id is number => typeof id === 'number')
      : [];
    removedConversationIds.value = Array.isArray(saved.removed)
      ? saved.removed.filter((id): id is number => typeof id === 'number')
      : [];
    conversationRemarks.value =
      saved.remarks && typeof saved.remarks === 'object' ? saved.remarks : {};
  } catch {
    pinnedConversationIds.value = [];
    ignoredConversationIds.value = [];
    removedConversationIds.value = [];
    conversationRemarks.value = {};
  }
}

function saveConversationPreferences() {
  try {
    localStorage.setItem(
      conversationPreferencesKey(),
      JSON.stringify({
        ignored: ignoredConversationIds.value,
        pinned: pinnedConversationIds.value,
        removed: removedConversationIds.value,
        remarks: conversationRemarks.value,
      } satisfies ConversationPreferences),
    );
  } catch {
  }
}

function openApplicationModal(conversationId: number) {
  const conversation = conversations.value.find((item) => item.id === conversationId);
  if (!conversation) return;
  applicationConversationId.value = conversation.id;
  applicationPostId.value = conversation.postId;
  applyModalOpen.value = true;
}

/** 职位详情一律新开标签页，沟通页这边也不破例 —— 会话不能被顶掉 */
function viewPost() {
  const postId = activeConversation.value?.postId;
  if (!postId) return;
  const { href } = router.resolve({
    name: 'PortalRecruitDetail',
    query: { id: String(postId) },
  });
  window.open(href, '_blank', 'noopener,noreferrer');
}

function chooseTab(value: typeof tab.value) {
  tab.value = value;
}

function toggleConversationMenu(id: number) {
  activeMenuId.value = activeMenuId.value === id ? undefined : id;
}

function isPinned(item: HrmPortalRecruitChatApi.Conversation) {
  return pinnedConversationIds.value.includes(item.id);
}

function sendMessageFromMenu(item: HrmPortalRecruitChatApi.Conversation) {
  activeMenuId.value = undefined;
  void selectConversation(item.id);
}

function togglePinned(item: HrmPortalRecruitChatApi.Conversation) {
  const pinned = isPinned(item);
  pinnedConversationIds.value = pinned
    ? pinnedConversationIds.value.filter((id) => id !== item.id)
    : [item.id, ...pinnedConversationIds.value];
  saveConversationPreferences();
  activeMenuId.value = undefined;
  message.success(pinned ? '已取消置顶' : '已置顶会话');
}

function editRemark(item: HrmPortalRecruitChatApi.Conversation) {
  const value = window.prompt('请输入会话备注', conversationRemarks.value[item.id] || '');
  if (value === null) return;
  conversationRemarks.value = { ...conversationRemarks.value, [item.id]: value.trim() };
  saveConversationPreferences();
  activeMenuId.value = undefined;
  message.success('备注已保存');
}

function markNotInterested(item: HrmPortalRecruitChatApi.Conversation) {
  ignoredConversationIds.value = [
    ...new Set([...ignoredConversationIds.value, item.id]),
  ];
  saveConversationPreferences();
  activeMenuId.value = undefined;
  if (activeId.value === item.id) {
    activeId.value = undefined;
    messages.value = [];
    selectionRequestId += 1;
  }
  message.success('已标记为不感兴趣');
}

function removeConversation(item: HrmPortalRecruitChatApi.Conversation) {
  removedConversationIds.value = [
    ...new Set([...removedConversationIds.value, item.id]),
  ];
  saveConversationPreferences();
  activeMenuId.value = undefined;
  if (activeId.value === item.id) {
    activeId.value = undefined;
    messages.value = [];
    selectionRequestId += 1;
  }
  message.success('会话已从列表删除');
}

function handleConversationKeydown(event: KeyboardEvent, id: number) {
  if (event.key !== 'Enter' && event.key !== ' ') return;
  event.preventDefault();
  void selectConversation(id);
}

async function loadPostDetails(list: HrmPortalRecruitChatApi.Conversation[]) {
  const postIds = [...new Set(list.map((item) => item.postId).filter(Boolean))];
  const missingIds = postIds.filter((id) => !postDetails.value[id]);
  if (!missingIds.length) return;
  const results = await Promise.all(
    missingIds.map(async (id) => {
      try {
        return await getPortalRecruitPost(id);
      } catch {
        return undefined;
      }
    }),
  );
  const next = { ...postDetails.value };
  results.forEach((post) => {
    if (post?.id) next[post.id] = post;
  });
  postDetails.value = next;
}

async function loadInterviewPostIds() {
  if (interviewFilterLoaded.value) return;
  try {
    const result = await getPortalRecruitApplicationPage({ pageNo: 1, pageSize: 100 });
    interviewApplicationIds.value = (result.list || [])
      .filter((item) => item.interviews?.length)
      .map((item) => item.id);
    const offers: Record<number, HrmPortalRecruitApi.Offer> = {};
    (result.list || []).forEach((item) => {
      if (item.offer) {
        offers[item.id] = item.offer;
      }
    });
    offersByApplicationId.value = offers;
    interviewFilterLoaded.value = true;
  } catch {
    interviewApplicationIds.value = [];
  }
}

async function loadConversations(preferredId?: number) {
  if (!loggedIn.value) return;
  const requestId = ++conversationListRequestId;
  loading.value = true;
  try {
    const result = (await getRecruitChatConversationList()) || [];
    if (requestId !== conversationListRequestId) return;
    conversations.value = result;
    void loadPostDetails(conversations.value);
    void loadInterviewPostIds();
    const requestedId = preferredId || activeId.value;
    const requestedConversation = conversations.value.find(
      (item) => item.id === requestedId,
    );
    const requestedIsVisible =
      requestedConversation &&
      !ignoredConversationIds.value.includes(requestedConversation.id) &&
      !removedConversationIds.value.includes(requestedConversation.id);
    const nextId = requestedIsVisible
      ? requestedConversation.id
      : filteredConversations.value[0]?.id;
    if (nextId) {
      await selectConversation(nextId);
    } else {
      activeId.value = undefined;
      messages.value = [];
    }
  } catch (error: any) {
    // ⚠️ 本框架鉴权失败返回的是 **HTTP 200 + body.code=401**，不是 HTTP 401。
    // 原先按 error.response.status 判 401 会**永远为真**（status 是 200）→ 未登录时多弹一次错误提示。
    // 必须判 body 的 code（与 api/request.ts 的 error?.data?.code === 401 口径一致）。
    const bizCode = error?.data?.code ?? error?.response?.data?.code;
    if (bizCode !== 401) {
      message.error(error?.message || '加载沟通记录失败');
    }
  } finally {
    if (requestId === conversationListRequestId) loading.value = false;
  }
}

async function selectConversation(id: number) {
  const requestId = ++selectionRequestId;
  activeMenuId.value = undefined;
  activeId.value = id;
  messages.value = [];
  try {
    const list = await getRecruitChatMessageList({ conversationId: id, limit: 200 });
    if (requestId !== selectionRequestId || activeId.value !== id) return;
    messages.value = list || [];
  } catch (error: any) {
    if (requestId === selectionRequestId && activeId.value === id) {
      message.error(error?.message || '加载消息失败');
    }
    return;
  }
  if (requestId !== selectionRequestId || activeId.value !== id) return;
  await markRecruitChatRead(id).catch(() => undefined);
  if (requestId !== selectionRequestId || activeId.value !== id) return;
  const current = conversations.value.find((item) => item.id === id);
  if (current) current.unreadCount = 0;
  await nextTick();
  if (conversationPane.value) {
    conversationPane.value.scrollTop = conversationPane.value.scrollHeight;
  }
}

async function openFromPost(postId: number) {
  try {
    const conversation = await openRecruitChatConversation(postId);
    await loadConversations(conversation.id);
  } catch (error: any) {
    message.error(error?.message || '打开沟通失败');
    await loadConversations();
  }
}

async function sendText() {
  const content = draft.value.trim();
  if (!content || !activeId.value || sending.value) return;
  const conversationId = activeId.value;
  sending.value = true;
  try {
    const created = await sendRecruitChatMessage({
      conversationId,
      contentType: 1,
      content,
    });
    if (activeId.value === conversationId) {
      draft.value = '';
      messages.value.push(created);
    }
    const current = conversations.value.find((item) => item.id === conversationId);
    if (current) {
      current.lastContent = content;
      current.lastTime = created.sendTime;
      current.lastSenderUserId = created.senderUserId;
      if (!current.hrSide && current.status === 0) current.status = 1;
    }
    await nextTick();
    if (activeId.value === conversationId && conversationPane.value) {
      conversationPane.value.scrollTop = conversationPane.value.scrollHeight;
    }
  } catch (error: any) {
    message.error(error?.message || '发送失败');
  } finally {
    sending.value = false;
  }
}

function triggerImageUpload() {
  if (!activeId.value || activeConversation.value?.status === 3) {
    message.warning('当前会话暂不可发送图片');
    return;
  }
  imageInput.value?.click();
}

async function handleImageUpload(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file || !activeId.value) return;
  const conversationId = activeId.value;
  if (!file.type.startsWith('image/')) {
    message.warning('请选择图片文件');
    return;
  }
  if (file.size > 10 * 1024 * 1024) {
    message.warning('图片大小不能超过 10MB');
    return;
  }
  imageUploading.value = true;
  try {
    const uploaded = await uploadHrmRecruitFile(file, 'chat');
    const fileUrl = typeof uploaded === 'string' ? uploaded : '';
    if (!fileUrl) throw new Error('图片上传未返回文件地址');
    const content = ['__HRM_IMAGE__', encodeURIComponent(file.name), fileUrl].join('|');
    const created = await sendRecruitChatMessage({
      conversationId,
      contentType: 1,
      content,
    });
    if (activeId.value === conversationId) messages.value.push(created);
    const current = conversations.value.find((item) => item.id === conversationId);
    if (current) {
      current.lastContent = content;
      current.lastTime = created.sendTime;
      current.lastSenderUserId = created.senderUserId;
      if (!current.hrSide && current.status === 0) current.status = 1;
    }
    await nextTick();
    if (activeId.value === conversationId && conversationPane.value) {
      conversationPane.value.scrollTop = conversationPane.value.scrollHeight;
    }
  } catch (error: any) {
    message.error(error?.message || '图片上传失败');
  } finally {
    imageUploading.value = false;
  }
}

function handleComposerKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter') return;
  if (event.ctrlKey || event.metaKey) return;
  event.preventDefault();
  void sendText();
}

function readRecruitProfile(resumes: HrmPortalResumeApi.Resume[]) {
  const profileResume = resumes.find(
    (resume) => resume.title === '求职资料（系统）',
  );
  if (!profileResume?.content) return {};
  try {
    const profile = JSON.parse(profileResume.content);
    return profile && typeof profile === 'object' ? profile : {};
  } catch {
    return {};
  }
}

function profileEducationValue(value?: unknown) {
  const education = String(value || '').replace(/[\s/]+/g, '');
  const educationMap: Record<string, number> = {
    初中及以下: 3,
    中专: 3,
    中专中技: 3,
    高中: 3,
    大专: 4,
    本科: 5,
    硕士: 6,
    博士: 7,
  };
  return educationMap[education] || 5;
}

function profileAge(value?: unknown) {
  if (typeof value !== 'string' || !value) return undefined;
  const year = Number(value.slice(0, 4));
  const month = Number(value.slice(5, 7));
  if (!year || !month) return undefined;
  const now = new Date();
  return Math.max(
    0,
    now.getFullYear() - year - (now.getMonth() + 1 < month ? 1 : 0),
  );
}

function profileWorkTime(value?: unknown) {
  if (typeof value === 'number') return value;
  if (typeof value !== 'string' || !value) return undefined;
  const year = Number(value.slice(0, 4));
  return year ? Math.max(0, new Date().getFullYear() - year) : undefined;
}

function directApplicationData(
  resume: HrmPortalResumeApi.Resume,
  resumes: HrmPortalResumeApi.Resume[],
  profileUser?: { email?: string; mobile?: string; nickname?: string; phone?: string; username?: string },
  postId?: number,
): HrmPortalRecruitApi.ApplyReq | undefined {
  const profile = readRecruitProfile(resumes) as Record<string, any>;
  const user = (profileUser || userStore.userInfo) as null | Record<string, unknown>;
  const mobile = [
    profile.mobile,
    user?.mobile,
    user?.phone,
  ].find(
    (value) =>
      typeof value === 'string' &&
      /^(\+?0?\d{2,4}-?)?\d{6,11}$/.test(value.trim()),
  );
  if (typeof mobile !== 'string' || !mobile.trim()) return undefined;
  return {
    postId: postId || activeConversation.value?.postId || 0,
    name: String(profile.name || user?.nickname || user?.username || '求职者'),
    mobile: mobile.trim(),
    sex: Number(profile.gender) === 2 ? 2 : 1,
    age: profileAge(profile.birthMonth),
    email: String(profile.email || user?.email || ''),
    workTime: profileWorkTime(profile.workTime ?? profile.jobStart ?? profile.workStart),
    education: profileEducationValue(profile.education),
    graduateSchool: String(profile.school || ''),
    latestWorkPlace: String(profile.company || ''),
    resumeFileName: resume.fileName || resume.title || '简历',
    resumeFileUrl: resume.fileUrl || '',
    resumeId: resume.id,
    resumeRemark: resume.remark,
  };
}

async function sendResume() {
  if (!activeId.value || sending.value || resumeSending.value) return;
  const conversationId = activeId.value;
  const conversation = conversations.value.find((item) => item.id === conversationId);
  if (!conversation) return;
  if (conversation.status === 3) {
    message.warning('沟通已结束，无法发送简历');
    return;
  }
  if (!conversation.applyQualified) {
    message.warning('需等待招聘方回复后才能发送简历');
    return;
  }
  if (conversation.applicationStatus === 'SUBMITTED') {
    message.info('该岗位已投递简历');
    return;
  }

  resumeSending.value = true;
  try {
    const allResumes = (await getPortalResumeList()) || [];
    const resumes = allResumes.filter(
      (resume) => resume.resumeType === 1 && resume.fileUrl,
    );
    if (resumes.length !== 1) {
      openApplicationModal(conversationId);
      return;
    }
    const resume = resumes[0];
    if (!resume) return;
    const profileUser = await getUserProfile().catch(() => undefined);
    const data = directApplicationData(resume, allResumes, profileUser, conversation.postId);
    if (!data) {
      message.warning('请先在求职资料中完善可联系的手机号');
      openApplicationModal(conversationId);
      return;
    }
    if (await sendResumeMessage(data, conversationId)) {
      message.success('简历已发送，等待 HR 手动转为候选人');
    }
  } catch (error: any) {
    message.error(error?.message || '投递失败');
  } finally {
    resumeSending.value = false;
  }
}

const applicationPost = computed<HrmPortalRecruitApi.Post | undefined>(() => {
  const conversation = conversations.value.find(
    (item) => item.id === applicationConversationId.value,
  );
  const postId = applicationPostId.value || conversation?.postId;
  if (!postId) return undefined;
  return { id: postId, postName: conversation?.postName || '招聘职位' };
});

async function sendResumeMessage(
  data: HrmPortalRecruitApi.ApplyReq,
  conversationId?: number,
): Promise<boolean> {
  const targetConversationId =
    conversationId || applicationConversationId.value || activeId.value;
  if (!targetConversationId || !data.resumeFileUrl) return false;
  const conversation = conversations.value.find(
    (item) => item.id === targetConversationId,
  );
  if (!conversation || data.postId !== conversation.postId) return false;
  try {
    const content = [
      data.resumeFileName || '简历',
      data.resumeFileName || '',
      data.resumeFileUrl,
    ].join('|');
    const created = await sendRecruitChatMessage({
      conversationId: targetConversationId,
      contentType: 2,
      content,
    });
    if (activeId.value === targetConversationId) messages.value.push(created);
    await nextTick();
    if (activeId.value === targetConversationId && conversationPane.value) {
      conversationPane.value.scrollTop = conversationPane.value.scrollHeight;
    }
    return true;
  } catch (error: any) {
    message.warning(error?.message || '简历已投递，但沟通消息发送失败');
    return false;
  }
}

async function handleApplicationSubmitted(data: HrmPortalRecruitApi.ApplyReq) {
  const conversationId = applicationConversationId.value || activeId.value;
  if (!conversationId) return;
  resumeSending.value = true;
  try {
    await sendResumeMessage(data, conversationId);
  } finally {
    resumeSending.value = false;
  }
}

async function poll() {
  if (!activeId.value || !loggedIn.value) return;
  const conversationId = activeId.value;
  try {
    const lastId = messages.value[messages.value.length - 1]?.id;
    const incoming = await getRecruitChatMessageList({
      conversationId,
      minId: lastId,
      limit: 50,
    });
    if (activeId.value !== conversationId) return;
    if (incoming?.length) {
      const known = new Set(messages.value.map((item) => item.id));
      const fresh = incoming.filter((item) => !known.has(item.id));
      if (fresh.length) {
        messages.value.push(...fresh);
        await nextTick();
        if (conversationPane.value) {
          conversationPane.value.scrollTop = conversationPane.value.scrollHeight;
        }
      }
    }
    const latestConversations = await getRecruitChatConversationList();
    if (activeId.value !== conversationId) return;
    conversations.value = latestConversations || conversations.value;
  } catch {
    // ignore polling errors
  }
}

async function ensureSession() {
  if (!loggedIn.value) return false;
  if (!userStore.userInfo) {
    try {
      await authStore.fetchUserInfo();
    } catch {
      // 门户只持久化 token，用户信息失败时仍按已登录继续拉会话
    }
  }
  return true;
}

async function bootChat() {
  if (!(await ensureSession())) return;
  loadConversationPreferences();
  const postId = Number(route.query.postId);
  if (postId) await openFromPost(postId);
  else await loadConversations();
  if (!pollTimer) pollTimer = setInterval(() => void poll(), 2500);
}

let applicationRefreshTimer: ReturnType<typeof setInterval> | undefined;

function refreshApplications() {
  if (document.hidden || !loggedIn.value) return;
  interviewFilterLoaded.value = false;
  void loadInterviewPostIds();
}

watch(
  () => route.query.postId,
  async (postId) => {
    if (!postId || !loggedIn.value) return;
    await openFromPost(Number(postId));
  },
);

watch(loggedIn, async (ok, wasOk) => {
  if (ok && !wasOk) await bootChat();
});

watch(applyModalOpen, (open) => {
  if (!open) {
    applicationConversationId.value = undefined;
    applicationPostId.value = undefined;
  }
});

onMounted(async () => {
  void loadTenantNames();
  await bootChat();
  // 问题 9：处理录用通知邮件里的「同意 / 拒绝」链接（登录后二次确认再落库）
  await handleOfferMailAction();
  applicationRefreshTimer = setInterval(refreshApplications, 10_000);
});

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer);
  if (applicationRefreshTimer) clearInterval(applicationRefreshTimer);
});
</script>

<template>
  <div class="message-page" :class="{ embedded }" @keydown.esc="activeMenuId = undefined">
    <RecruitTopbar v-if="!embedded" active="messages" :display-name="displayName" :themed="true" />

    <main v-if="!loggedIn" class="message-shell empty-shell">
      <div class="empty-pane">
        <div class="empty-art"><IconifyIcon icon="lucide:message-circle" /></div>
        <h2>登录后即可与 HR 实时沟通</h2>
        <p>投递岗位、发送简历、查看回复都在这里完成</p>
        <button class="empty-action" type="button" @click="goLogin">去登录</button>
      </div>
    </main>

    <main v-else class="message-shell" :class="{ 'has-active-chat': activeConversation }">
      <aside class="contact-pane">
        <label class="contact-search">
          <input v-model="keyword" type="search" placeholder="搜索30天内的联系人" />
          <IconifyIcon icon="lucide:search" />
        </label>
        <div class="tabs" role="tablist" aria-label="会话筛选">
          <button type="button" role="tab" :aria-selected="tab === 'all'" :class="{ selected: tab === 'all' }" @click="chooseTab('all')">全部</button>
          <button type="button" role="tab" :aria-selected="tab === 'unread'" :class="{ selected: tab === 'unread' }" @click="chooseTab('unread')">
            未读<i v-if="unreadTotal" class="tab-badge">{{ unreadTotal > 99 ? '99+' : unreadTotal }}</i>
          </button>
          <button type="button" role="tab" :aria-selected="tab === 'communication'" :class="{ selected: tab === 'communication' }" @click="chooseTab('communication')">仅沟通</button>
          <button type="button" role="tab" :aria-selected="tab === 'applied'" :class="{ selected: tab === 'applied' }" @click="chooseTab('applied')">有投递</button>
          <button type="button" role="tab" :aria-selected="tab === 'interview'" :class="{ selected: tab === 'interview' }" @click="chooseTab('interview')">有面试</button>
        </div>
        <div class="contact-list">
          <div v-if="loading && !conversations.length" class="contact-empty">正在加载沟通记录...</div>
          <div v-else-if="!filteredConversations.length" class="contact-empty">
            <strong>暂无会话</strong>
            <p>去职位页点「立即沟通」，即可开始和 HR 对话</p>
          </div>
          <div
            v-for="item in filteredConversations"
            :key="item.id"
            class="contact"
            :class="{ chosen: activeId === item.id, unread: Boolean(item.unreadCount) }"
            @click="selectConversation(item.id)"
            @keydown="handleConversationKeydown($event, item.id)"
            role="button"
            tabindex="0"
          >
            <span class="contact-avatar">
              <img v-if="avatarOf(item)" :src="avatarOf(item)" :alt="recruiterName(item)" />
              <template v-else>{{ initialOf(item) }}</template>
            </span>
            <span class="contact-body">
              <span class="contact-title">
                <b v-if="isPinned(item)" class="pin-mark" title="已置顶">置顶</b>
                <b class="contact-name">{{ conversationRemarks[item.id] || recruiterName(item) }}</b>
                <time>{{ formatListTime(item.lastTime) }}</time>
              </span>
              <span class="contact-sub">
                {{ companyName(item) }}<i>·</i>{{ recruiterPosition(item) }}
              </span>
              <span class="contact-preview">
                <em>{{ lastMessagePreview(item) }}</em>
                <i v-if="item.unreadCount" class="unread-badge">{{ item.unreadCount > 99 ? '99+' : item.unreadCount }}</i>
              </span>
            </span>
            <span class="contact-actions" @click.stop>
              <button
                type="button"
                class="more-trigger"
                :aria-expanded="activeMenuId === item.id"
                aria-label="会话操作"
                @click.stop="toggleConversationMenu(item.id)"
              >
                <IconifyIcon :icon="activeMenuId === item.id ? 'lucide:x' : 'lucide:more-horizontal'" />
              </button>
              <span v-if="activeMenuId === item.id" class="conversation-menu" @click.stop>
                <button type="button" @click="togglePinned(item)">{{ isPinned(item) ? '取消置顶' : '置顶' }}</button>
                <button type="button" @click="editRemark(item)">备注</button>
                <button type="button" @click="sendMessageFromMenu(item)">发送消息</button>
                <button type="button" @click="markNotInterested(item)">显示不感兴趣</button>
                <button type="button" @click="removeConversation(item)">删除</button>
              </span>
            </span>
          </div>
        </div>
      </aside>

      <section v-if="activeConversation" class="chat-pane">
        <div class="chat-head">
          <button class="chat-back" type="button" aria-label="返回会话列表" @click="backToList">
            <IconifyIcon icon="lucide:chevron-left" /> 返回
          </button>
          <span class="chat-avatar">
            <img v-if="avatarOf(activeConversation)" :src="avatarOf(activeConversation)" :alt="recruiterName(activeConversation)" />
            <template v-else>{{ initialOf(activeConversation) }}</template>
          </span>
          <div class="chat-identity">
            <div class="chat-name-row">
              <b>{{ conversationRemarks[activeConversation.id] || recruiterName(activeConversation) }}</b>
              <i class="conversation-status" :class="statusClass(activeConversation)">{{ statusLabel(activeConversation) }}</i>
            </div>
            <span class="chat-sub">
              {{ companyName(activeConversation) }}<i>·</i>{{ recruiterPosition(activeConversation) }}
            </span>
          </div>
        </div>

        <!--
          应聘上下文合成一张卡。原先「职位条」和「进度轨」是两个独立盒子叠在
          会话头部，加起来吃掉两百来像素，底下真正的对话反被挤扁；合成一张卡
          之后两行共用一个边框，「应聘什么」和「走到哪一步」也终于挨在一起。
        -->
        <div class="apply-card">
          <div class="apply-job">
            <span class="apply-eyebrow">应聘职位</span>
            <b>{{ activeConversation.postName || '沟通岗位' }}</b>
            <strong>{{ formatSalary(activeConversation) }}</strong>
            <span class="apply-area">{{ formatArea(activeConversation.areaName) }}</span>
            <button class="apply-view" type="button" @click="viewPost">查看职位 ›</button>
          </div>
          <div class="apply-progress">
            <div class="stage-rail">
              <span
                v-for="(label, index) in STAGES"
                :key="label"
                class="stage"
                :class="{ done: index <= stageIndexOf(activeConversation), current: index === stageIndexOf(activeConversation) }"
              >
                <i class="stage-dot"></i><em>{{ label }}</em>
              </span>
            </div>
            <span class="stage-caption">{{ stageCaption(activeConversation) }}</span>
          </div>
        </div>
        <div v-if="offerForConversation(activeConversation)" class="offer-card">
          <div>
            <strong>招聘 Offer</strong>
            <span>{{ offerForConversation(activeConversation)?.offerNo || '待确认' }}</span>
          </div>
          <small>{{ offerForConversation(activeConversation)?.approvalStatus === 'SENT' ? '请在有效期内确认 Offer，接受后进入待入职。' : offerStatusLabel(offerForConversation(activeConversation)?.approvalStatus) }}</small>
          <div v-if="offerForConversation(activeConversation)?.approvalStatus === 'SENT'" class="offer-actions">
            <button type="button" @click="acceptOffer(activeConversation)">接受 Offer</button>
            <button type="button" class="reject" @click="rejectOffer(activeConversation)">拒绝 Offer</button>
          </div>
          <span v-else class="offer-state">{{ offerStatusLabel(offerForConversation(activeConversation)?.approvalStatus) }}</span>
        </div>
        <div v-if="activeConversation.status === 0" class="chat-hint">
          {{ activeConversation.hrSide ? '等待求职者主动发起沟通。' : '请先发送消息，主动发起沟通。' }}
        </div>
        <div v-else-if="activeConversation.status === 3" class="chat-hint fail">
          沟通已结束{{ activeConversation.endReason ? `：${activeConversation.endReason}` : '' }}
        </div>
        <div ref="conversationPane" class="conversation">
          <div v-if="!messages.length" class="chat-empty">还没有消息，打个招呼开始沟通吧</div>
          <template v-for="(item, index) in messages" :key="item.id">
            <div v-if="showStamp(index)" class="date">{{ formatTime(item.sendTime) }}</div>
            <div
              v-if="hasDisplayableContent(item)"
              class="bubble"
              :class="[
                item.mine ? 'mine' : 'theirs',
                {
                  'has-attachment': Boolean(getHrmRecruitChatAttachment(item.content, item.contentType)),
                  tight: index > 0 && !isGroupEnd(index - 1),
                },
              ]"
            >
              <a
                v-if="getHrmRecruitChatAttachment(item.content, item.contentType)?.type === 'resume'"
                class="resume-card"
                :href="getHrmRecruitChatAttachment(item.content, item.contentType)?.previewUrl"
                target="_blank"
                rel="noreferrer"
              >
                <span class="resume-icon">📄</span>
                <span>
                  <b>{{ getHrmRecruitChatAttachment(item.content, item.contentType)?.title }}</b>
                  <em>{{ getHrmRecruitChatAttachment(item.content, item.contentType)?.fileName }}</em>
                </span>
              </a>
              <template v-else-if="getHrmRecruitChatAttachment(item.content, item.contentType)?.type === 'image'">
                <a
                  class="image-card"
                  :href="getHrmRecruitChatAttachment(item.content, item.contentType)?.previewUrl"
                  target="_blank"
                  rel="noreferrer"
                >
                  <img :src="getHrmRecruitChatAttachment(item.content, item.contentType)?.previewUrl" :alt="getHrmRecruitChatAttachment(item.content, item.contentType)?.fileName" />
                </a>
              </template>
              <template v-else>{{ item.content }}</template>
            </div>
          </template>
        </div>
        <div class="composer">
          <div class="tools">
            <button
              type="button"
              class="tool-icon"
              :disabled="imageUploading || !activeId || activeConversation.status === 3"
              aria-label="上传图片"
              @click="triggerImageUpload"
            >
              <IconifyIcon icon="lucide:image" />
            </button>
            <input ref="imageInput" class="image-input" type="file" accept="image/*" @change="handleImageUpload" />
            <span class="resume-tool-wrap" title="等待招聘方回复后，即可获得投递资格">
              <button type="button" :disabled="!canSendResume" @click="sendResume">发简历</button>
            </span>
          </div>
          <textarea
            v-model="draft"
            placeholder="按 Enter 发送，Ctrl+Enter 换行"
            :disabled="sending || !activeId || activeConversation.status === 3"
            @keydown="handleComposerKeydown"
          ></textarea>
          <button class="send" type="button" :class="{ ready: canSend }" :disabled="!canSend" @click="sendText">
            发送
          </button>
        </div>
      </section>

      <section v-else class="chat-pane empty-chat">
        <div class="empty-pane">
          <div class="empty-art"><IconifyIcon icon="lucide:inbox" /></div>
          <h2>还没有沟通会话</h2>
          <p>在职位列表点击「立即沟通」，会在这里打开与 HR 的对话</p>
          <button class="empty-action ghost" type="button" @click="goJobs">去看职位</button>
        </div>
      </section>
    </main>
    <RecruitApplyModal
      v-model:open="applyModalOpen"
      :chat-only="true"
      :post="applicationPost"
      @submitted="handleApplicationSubmitted"
    />
  </div>
</template>

<style scoped>
/*
 * 配色沿用职位页那套 --job-* token（定义在 index.vue 的 .recruit-page 上），
 * 本页只负责落地「淡蓝承载结构、淡绿承载动作、红只给薪资和未读」的分工，
 * 不再自带色板。组件始终由 index.vue 以 embedded 方式渲染
 * （路由 recruit/messages 也重定向到 ?section=messages），token 必定继承得到。
 */
.message-page {
  /*
   * 职位页那套 token 只有「深/浅」两档，铺大面积浅底时要么太浓要么得现编
   * 中间色。这里从 token 派生两档，把 #fbfdff / #3a7cc0 这类职位页色板里
   * 根本没有的色值收干净 —— 改 token 时这一页跟着走，不会再各走各的。
   */
  --msg-surface: color-mix(in srgb, var(--job-blue-soft) 42%, #fff);
  --msg-surface-deep: color-mix(in srgb, var(--job-blue-soft) 72%, #fff);
  /* 加深档一律往黑里压，不另配深色值 —— 加黑不偏色相，也就不会又冒出
     两个色板里没有的十六进制 */
  --msg-blue-deep: color-mix(in srgb, var(--job-blue) 86%, #000);
  --msg-green-deep: color-mix(in srgb, var(--job-green) 82%, #000);
  --msg-blue-ring: color-mix(in srgb, var(--job-blue) 12%, transparent);
  --msg-blue-cast: color-mix(in srgb, var(--job-blue) 20%, transparent);
  --msg-green-cast: color-mix(in srgb, var(--job-green) 26%, transparent);
  --msg-ink-cast: color-mix(in srgb, var(--job-ink) 6%, transparent);
  /* 应聘进度轨单独走绿：它表示的是「走过多远」这种达成，不是结构信息。
     集中在这三行，要换回蓝只改这里。 */
  --msg-progress: var(--job-green);
  --msg-progress-line: var(--job-green-line);
  --msg-progress-ring: color-mix(in srgb, var(--job-green) 18%, transparent);
  color: var(--job-ink);
}

/*
 * embedded 下面板要吃掉整屏：index.vue 的 .recruit-page--messages 把所在行
 * 设成了 minmax(0, 1fr)，这里用 grid 单行接住，双方面板就跟着撑满，
 * 页面高度刚好一屏、下面不留空壳。
 */
.message-page.embedded {
  display: grid;
  grid-template-rows: minmax(0, 1fr);
}

/* ---------------- 外壳 ---------------- */

.message-shell {
  display: grid;
  grid-template-columns: 356px minmax(0, 1fr);
  /* 行高必须夹死：留成 auto 的话，左栏会话列表的内容高度会从下面顶上来，
     把整块面板撑高，flex/overflow 那一套都拦不住 */
  grid-template-rows: minmax(0, 1fr);
  height: min(780px, calc(100dvh - 150px));
  max-width: 1180px;
  margin: auto;
  border: 1px solid var(--job-line);
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 18px 44px color-mix(in srgb, var(--job-ink) 10%, transparent);
  overflow: hidden;
}

.message-page.embedded .message-shell {
  height: 100%;
  /*
   * 必须显式给宽度：.message-shell 带着 margin: auto，在 block 里那是居中，
   * 一旦父级变成 grid，auto 外边距会让 item 收缩成 fit-content 并居中，
   * 面板会从 1180 缩到内容宽度。
   */
  width: 100%;
  /* 视口再矮也不把会话区压没，宁可让页面出现滚动条 */
  min-height: 520px;
}

.empty-shell {
  grid-template-columns: 1fr;
  place-items: center;
}

/* ---------------- 左栏：会话列表 ---------------- */

.contact-pane {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-right: 1px solid var(--job-line);
  background: var(--msg-surface);
}

.contact-search {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 16px 16px 12px;
  padding: 0 12px;
  border: 1px solid var(--job-line);
  border-radius: 10px;
  background: #fff;
  color: var(--job-muted);
  transition: border-color 160ms ease, box-shadow 160ms ease;
}

.contact-search:focus-within {
  border-color: var(--job-blue);
  box-shadow: 0 0 0 3px var(--msg-blue-ring);
}

.contact-search input {
  flex: 1;
  min-width: 0;
  height: 38px;
  border: 0;
  outline: none;
  background: transparent;
  color: var(--job-ink);
  font-size: 13px;
}

.contact-search input::placeholder {
  color: var(--job-muted);
}

.contact-search input::-webkit-search-cancel-button {
  display: none;
}

/* 五个视角一次铺开。原先「仅沟通 / 有投递 / 有面试」藏在「更多⌄」里，
   而这三个恰恰是求职者最常用来筛进度的，藏起来等于没有。 */
.tabs {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 16px 12px;
  border-bottom: 1px solid var(--job-line);
  overflow-x: auto;
  scrollbar-width: none;
}

.tabs::-webkit-scrollbar {
  display: none;
}

.tabs button {
  flex: 0 0 auto;
  padding: 5px 11px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  color: var(--job-muted);
  font-size: 12.5px;
  line-height: 1.5;
  white-space: nowrap;
  cursor: pointer;
  transition: color 160ms ease, background 160ms ease, border-color 160ms ease;
}

.tabs button:hover {
  background: var(--job-blue-soft);
  color: var(--job-blue);
}

.tabs button.selected {
  border-color: var(--job-blue);
  background: var(--job-blue);
  color: #fff;
}

.tabs button:focus-visible {
  outline: 2px solid var(--job-blue);
  outline-offset: 2px;
}

/* 未读计数用实心蓝。色板里没有红，而实心蓝在整片淡蓝底上已经足够跳出来，
   再引一个红就是第三种颜色了。 */
.tab-badge,
.unread-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 999px;
  background: var(--job-blue);
  color: #fff;
  font-size: 11px;
  font-style: normal;
  line-height: 1;
}

.tab-badge {
  margin-left: 5px;
  vertical-align: 1px;
}

.tabs button.selected .tab-badge {
  background: #fff;
  color: var(--job-blue);
}

.contact-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
}

.contact-empty {
  padding: 56px 24px;
  color: var(--job-muted);
  text-align: center;
  font-size: 13px;
}

.contact-empty strong {
  display: block;
  margin-bottom: 8px;
  color: var(--job-ink);
  font-size: 15px;
}

.contact-empty p {
  margin: 0;
  line-height: 1.7;
}

.contact {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--job-line);
  background: transparent;
  cursor: pointer;
  transition: background 160ms ease;
}

.contact:hover {
  background: #fff;
}

.contact.chosen {
  background: #fff;
  box-shadow: inset 3px 0 0 var(--job-blue);
}

.contact:focus-visible {
  outline: 2px solid var(--job-blue);
  outline-offset: -2px;
}

.contact-avatar,
.chat-avatar {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 12px;
  background: var(--job-blue-soft);
  color: var(--job-blue);
  font-weight: 600;
  overflow: hidden;
}

.contact-avatar {
  width: 42px;
  height: 42px;
  font-size: 16px;
}

.chat-avatar {
  width: 44px;
  height: 44px;
  font-size: 17px;
}

.contact-avatar img,
.chat-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.contact-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.contact-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}

.contact-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: var(--job-ink);
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.contact.unread .contact-name {
  font-weight: 700;
}

.pin-mark {
  flex: 0 0 auto;
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--job-green-soft);
  color: var(--job-green);
  font-size: 10px;
  font-weight: 600;
  line-height: 1.6;
}

.contact-title time {
  flex: 0 0 auto;
  color: var(--job-muted);
  font-size: 11.5px;
  transition: opacity 160ms ease;
}

.contact-sub {
  overflow: hidden;
  color: var(--job-muted);
  font-size: 12.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.contact-sub i {
  margin: 0 5px;
  color: var(--job-line);
  font-style: normal;
}

.contact-preview {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.contact-preview em {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: var(--job-muted);
  font-size: 12.5px;
  font-style: normal;
  opacity: 0.82;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.contact.chosen .contact-preview em {
  opacity: 1;
}

/* 「更多」收成悬停才现的图标。原先每行挂一颗「更多⌄」文字胶囊，
   六行叠下来比会话本身还抢眼。 */
.contact-actions {
  position: absolute;
  z-index: 2;
  top: 10px;
  right: 12px;
}

.more-trigger {
  display: grid;
  width: 26px;
  height: 26px;
  place-items: center;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: var(--job-muted);
  font-size: 16px;
  cursor: pointer;
  opacity: 0;
  transition: opacity 160ms ease, color 160ms ease, background 160ms ease,
    border-color 160ms ease;
}

.contact:hover .more-trigger,
.contact:focus-within .more-trigger,
.more-trigger[aria-expanded='true'] {
  opacity: 1;
}

.contact:hover .contact-title time,
.contact:focus-within .contact-title time {
  opacity: 0;
}

.more-trigger:hover {
  border-color: var(--job-line);
  background: #fff;
  color: var(--job-blue);
}

/* 触屏没有悬停，操作入口必须常驻 */
@media (hover: none) {
  .more-trigger {
    opacity: 1;
  }

  .contact-title time {
    display: none;
  }
}

.conversation-menu {
  position: absolute;
  z-index: 20;
  top: calc(100% + 6px);
  right: 0;
  display: grid;
  min-width: 132px;
  padding: 6px;
  border: 1px solid var(--job-line);
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 14px 32px color-mix(in srgb, var(--job-ink) 14%, transparent);
}

.conversation-menu button {
  padding: 8px 10px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--job-ink);
  font-size: 13px;
  text-align: left;
  white-space: nowrap;
  cursor: pointer;
  transition: background 140ms ease, color 140ms ease;
}

.conversation-menu button:hover {
  background: var(--job-blue-soft);
  color: var(--job-blue);
}

/* ---------------- 右栏：会话 ---------------- */

.chat-pane {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background: #fff;
}

.chat-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px 26px 14px;
}

.chat-back {
  display: none;
  align-items: center;
  gap: 2px;
  padding: 4px 0;
  border: 0;
  background: transparent;
  color: var(--job-blue);
  font-size: 13px;
  cursor: pointer;
}

.chat-identity {
  min-width: 0;
}

.chat-name-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.chat-name-row b {
  overflow: hidden;
  color: var(--job-ink);
  font-size: 17px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-sub {
  display: block;
  margin-top: 3px;
  overflow: hidden;
  color: var(--job-muted);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-sub i {
  margin: 0 6px;
  color: var(--job-line);
  font-style: normal;
}

.conversation-status {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  padding: 2px 9px;
  border-radius: 999px;
  font-size: 11.5px;
  font-style: normal;
  line-height: 1.6;
  white-space: nowrap;
}

.conversation-status.pending {
  background: var(--job-blue-soft);
  color: var(--job-blue);
}

/* 四个状态都在淡蓝一档里分深浅，不用中性灰 —— 色板里本来就没有灰，
   混进去会让头部看着比职位页「脏」一格。只有「招聘方已回复」给绿色，
   对应职位页把标签类信息交给绿的用法。 */
.conversation-status.waiting {
  background: var(--msg-surface-deep);
  color: var(--job-muted);
}

.conversation-status.qualified {
  background: var(--job-green-soft);
  color: var(--job-green);
}

.conversation-status.ended {
  background: var(--msg-surface-deep);
  color: color-mix(in srgb, var(--job-muted) 72%, #fff);
}

/* 应聘上下文卡：上半「应聘什么」，下半「走到哪了」。
   进度轨是本页的签名元素 —— 走过的段用蓝色连起来，当前段点一圈光晕，
   右侧一句话说「接下来做什么」。轨道回答「到哪了」，那句话回答「做什么」。 */
.apply-card {
  margin: 0 26px 14px;
  border: 1px solid var(--job-line);
  border-radius: 14px;
  background: linear-gradient(
    150deg,
    color-mix(in srgb, var(--job-blue-soft) 46%, #fff),
    color-mix(in srgb, var(--job-blue-soft) 78%, #fff)
  );
  overflow: hidden;
}

.apply-job {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 12px;
  padding: 12px 16px;
}

.apply-eyebrow {
  flex: 0 0 auto;
  padding: 2px 8px;
  border-radius: 6px;
  background: #fff;
  color: var(--job-blue);
  font-size: 11.5px;
  font-weight: 600;
}

.apply-job b {
  color: var(--job-ink);
  font-size: 14.5px;
}

.apply-job strong {
  color: var(--job-salary);
  font-size: 14.5px;
}

.apply-area {
  color: var(--job-muted);
  font-size: 13px;
}

.apply-view {
  margin-left: auto;
  padding: 4px 0;
  border: 0;
  background: transparent;
  color: var(--job-blue);
  font-size: 13px;
  cursor: pointer;
}

.apply-view:hover {
  text-decoration: underline;
}

/* 下半格用虚线和更亮的底分开：和职位信息同属一张卡，但不抢它的重量 */
.apply-progress {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 16px;
  border-top: 1px dashed var(--job-line);
  background: rgb(255 255 255 / 66%);
}

.stage-rail {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
}

.stage {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 6px;
}

.stage + .stage::before {
  display: block;
  width: 24px;
  height: 1.5px;
  margin: 0 8px;
  border-radius: 1px;
  background: var(--msg-progress-line);
  content: '';
  transition: background 240ms ease;
}

.stage-dot {
  display: block;
  width: 9px;
  height: 9px;
  border: 1.5px solid var(--msg-progress-line);
  border-radius: 50%;
  background: #fff;
  transition: background 240ms ease, border-color 240ms ease,
    box-shadow 240ms ease;
}

.stage em {
  color: var(--job-muted);
  font-size: 12.5px;
  font-style: normal;
  white-space: nowrap;
}

.stage.done .stage-dot {
  border-color: var(--msg-progress);
  background: var(--msg-progress);
}

.stage.done em {
  color: var(--job-ink);
  font-weight: 600;
}

/* 进入某一段的那条线，只有这一段本身走过了才染色。
   写成 `.done + .stage` 的话，光「沟通」完成就会把通往「投递」那段也点亮，
   读起来像是已经投过简历了。 */
.stage + .stage.done::before {
  background: var(--msg-progress);
}

.stage.current .stage-dot {
  box-shadow: 0 0 0 4px var(--msg-progress-ring);
}

.stage-caption {
  margin-left: auto;
  color: var(--job-muted);
  font-size: 12.5px;
  white-space: nowrap;
}

/*
 * Offer 卡本身是「信息」不是「动作」，按 70/30 的分工整块走蓝，
 * 绿色只留给真正要点的那个按钮。整块染绿会让这块和「发简历」「发送」
 * 抢同一个语义，绿色就不再等于「可以点」了。
 */
.offer-card {
  display: grid;
  gap: 8px;
  margin: 0 26px 14px;
  padding: 14px 16px;
  border: 1px solid var(--job-line);
  border-radius: 12px;
  background: var(--msg-surface-deep);
  color: var(--job-ink);
}

.offer-card > div:first-child,
.offer-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.offer-card > div:first-child strong {
  color: var(--job-blue);
}

.offer-card > div:first-child span,
.offer-card small,
.offer-state {
  color: var(--job-muted);
  font-size: 13px;
}

.offer-actions button {
  padding: 7px 14px;
  border: 1px solid var(--job-green);
  border-radius: 8px;
  background: var(--job-green);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
  transition: background 160ms ease;
}

.offer-actions button:hover {
  background: var(--msg-green-deep);
}

/* 拒绝是次要动作，退成描边；不引红色 —— 色板里没有「危险色」这一档，
   临时调一个红来又会让它和薪资、未读各说各话 */
.offer-actions button.reject {
  border-color: var(--job-line);
  background: #fff;
  color: var(--job-muted);
}

.offer-actions button.reject:hover {
  border-color: var(--job-muted);
  background: #fff;
  color: var(--job-ink);
}

.chat-hint {
  margin: 0 26px 12px;
  padding: 9px 14px;
  border-radius: 10px;
  background: var(--job-blue-soft);
  color: var(--job-blue);
  font-size: 12.5px;
}

/* 「沟通已结束」是终态而不是报错，退回中性的一档就够了。
   （原 .chat-hint.ok 在模板里没有引用，一并删掉。） */
.chat-hint.fail {
  background: var(--msg-surface-deep);
  color: var(--job-muted);
}

.conversation {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
  margin-top: 2px;
  padding: 22px 26px 28px;
  /* 顶部一段很淡的冷色，让空会话区看着像「等着写字的纸」，
     而不是一块没渲染完的白 */
  background: linear-gradient(
    180deg,
    var(--msg-surface-deep),
    var(--msg-surface) 220px
  );
  overflow-y: auto;
}

/*
 * 不做底部对齐。消息一律从上往下排，新会话的第一条就落在顶部 —— 之前用
 * margin-top: auto 把内容整体压到底部，结果新会话一打开是个空箱子，
 * 第一条消息也从底下冒出来。消息多到溢出时由 JS 在选中/发送后把
 * scrollTop 推到 scrollHeight，最新的照样在视线里。
 */

/* 空态做成和日期一样的胶囊，跟着消息流从顶部起，不再在整块区域正中飘着 */
.chat-empty {
  align-self: center;
  padding: 4px 14px;
  border-radius: 999px;
  background: rgb(255 255 255 / 82%);
  box-shadow: 0 1px 2px var(--msg-ink-cast);
  color: var(--job-muted);
  font-size: 11.5px;
}

.date {
  align-self: center;
  margin: 8px 0;
  padding: 3px 12px;
  border-radius: 999px;
  background: rgb(255 255 255 / 82%);
  box-shadow: 0 1px 2px var(--msg-ink-cast);
  color: var(--job-muted);
  font-size: 11.5px;
}

.bubble {
  width: fit-content;
  max-width: 60%;
  padding: 10px 14px;
  border-radius: 14px;
  font-size: 14px;
  line-height: 1.68;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

/* 组内再收紧 6px（.conversation 的 10px 间距被抵消掉 6） */
.bubble.tight {
  margin-top: -6px;
}

.mine {
  margin-left: auto;
  /* 贴近说话人的那只角始终收成 4px，就是气泡的「尖」 */
  border-bottom-right-radius: 4px;
  background: linear-gradient(135deg, var(--job-blue), var(--msg-blue-deep));
  box-shadow: 0 2px 8px var(--msg-blue-cast);
  color: #fff;
}

.theirs {
  border: 1px solid var(--job-line);
  border-bottom-left-radius: 4px;
  background: #fff;
  box-shadow: 0 1px 3px var(--msg-ink-cast);
  color: var(--job-ink);
}

/* 组内相接的一侧也收角。不收的话，上下两个 14px 圆角隔着 4px 对望，
   读起来是「两个挨得很近的独立气泡」而不是「一口气说完的一段」。 */
.mine.tight {
  border-top-right-radius: 4px;
}

.theirs.tight {
  border-top-left-radius: 4px;
}

/* 附件气泡不自带底盘，让卡片自己当画布，免得白卡片压在蓝气泡上 */
.bubble.has-attachment {
  padding: 0;
  border: 0;
  background: transparent;
}

.resume-card {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 232px;
  padding: 12px 14px;
  border: 1px solid var(--job-line);
  border-radius: 12px;
  background: #fff;
  color: var(--job-ink);
  text-decoration: none;
  transition: border-color 160ms ease, box-shadow 160ms ease;
}

.resume-card:hover {
  border-color: var(--job-blue);
  box-shadow: 0 6px 18px var(--msg-blue-cast);
}

.resume-card > span:not(.resume-icon) {
  min-width: 0;
}

.resume-icon {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 10px;
  background: var(--job-blue-soft);
  color: var(--job-blue);
  font-size: 17px;
}

.resume-card b {
  display: block;
  overflow: hidden;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resume-card em {
  display: block;
  margin-top: 2px;
  overflow: hidden;
  color: var(--job-muted);
  font-size: 12px;
  font-style: normal;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.image-card {
  display: grid;
  padding: 6px;
  border: 1px solid var(--job-line);
  border-radius: 12px;
  background: #fff;
}

.image-card img {
  display: block;
  max-width: 260px;
  max-height: 220px;
  border-radius: 8px;
  object-fit: contain;
}

/* ---------------- 输入区 ---------------- */

/* 工具栏放到输入框下面，和主流 IM 一致：先写字，再从工具条挑动作 */
.composer {
  display: grid;
  grid-template-areas:
    'input send'
    'tools tools';
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: 10px 12px;
  padding: 12px 26px 16px;
  border-top: 1px solid var(--job-line);
  background: #fff;
}

.tools {
  grid-area: tools;
  display: flex;
  align-items: center;
  gap: 10px;
}

.tools button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 12px;
  border: 1px solid var(--job-line);
  border-radius: 8px;
  background: #fff;
  color: var(--job-muted);
  font-size: 12.5px;
  cursor: pointer;
  transition: border-color 160ms ease, color 160ms ease, background 160ms ease;
}

.tools button:hover:not(:disabled) {
  border-color: var(--job-blue);
  background: var(--job-blue-soft);
  color: var(--job-blue);
}

.tools .tool-icon {
  width: 30px;
  height: 30px;
  justify-content: center;
  padding: 0;
  border: 0;
  background: transparent;
  font-size: 18px;
}

.image-input {
  display: none;
}

.resume-tool-wrap {
  display: inline-flex;
}

.resume-tool-wrap button:not(:disabled) {
  border-color: var(--job-green-line);
  background: var(--job-green-soft);
  color: var(--job-green);
}

.resume-tool-wrap button:hover:not(:disabled) {
  border-color: var(--job-green);
  background: var(--job-green-soft);
  color: var(--job-green);
}

.tools button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.composer textarea {
  grid-area: input;
  width: 100%;
  height: 76px;
  box-sizing: border-box;
  margin: 0;
  padding: 10px 12px;
  border: 1px solid var(--job-line);
  border-radius: 10px;
  outline: none;
  background: var(--msg-surface);
  color: var(--job-ink);
  font-family: inherit;
  font-size: 14px;
  line-height: 1.6;
  resize: none;
  transition: border-color 160ms ease, box-shadow 160ms ease, background 160ms ease;
}

.composer textarea::placeholder {
  color: var(--job-muted);
}

.composer textarea:hover {
  border-color: color-mix(in srgb, var(--job-line) 55%, var(--job-blue));
}

.composer textarea:focus {
  border-color: var(--job-blue);
  background: #fff;
  box-shadow: 0 0 0 3px var(--msg-blue-ring);
}

/* 不可用时退到淡蓝一档，而不是中性灰 —— 灰在色板里没有对应档位 */
.send {
  grid-area: send;
  min-width: 84px;
  height: 40px;
  padding: 0 20px;
  border: 0;
  border-radius: 10px;
  background: var(--msg-surface-deep);
  color: color-mix(in srgb, var(--job-muted) 52%, #fff);
  font-size: 14px;
  cursor: not-allowed;
  transition: background 160ms ease, color 160ms ease, box-shadow 160ms ease;
}

.send.ready {
  background: var(--job-green);
  color: #fff;
  cursor: pointer;
}

.send.ready:hover {
  background: var(--msg-green-deep);
  box-shadow: 0 8px 18px var(--msg-green-cast);
}

/* ---------------- 空态 ---------------- */

.empty-chat {
  place-items: center;
}

.empty-pane {
  display: grid;
  place-items: center;
  align-content: center;
  gap: 10px;
  padding: 48px 24px;
  color: var(--job-muted);
  text-align: center;
}

.empty-art {
  display: grid;
  width: 66px;
  height: 66px;
  place-items: center;
  border-radius: 22px;
  background: var(--job-blue-soft);
  color: var(--job-blue);
  font-size: 28px;
}

.empty-pane h2 {
  margin: 10px 0 0;
  color: var(--job-ink);
  font-size: 19px;
}

.empty-pane p {
  margin: 0;
  max-width: 380px;
  font-size: 13.5px;
  line-height: 1.75;
}

.empty-action {
  margin-top: 10px;
  padding: 9px 20px;
  border: 0;
  border-radius: 10px;
  background: var(--job-green);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  transition: background 160ms ease, box-shadow 160ms ease;
}

.empty-action:hover {
  background: var(--msg-green-deep);
  box-shadow: 0 8px 18px var(--msg-green-cast);
}

.empty-action.ghost {
  border: 1px solid var(--job-line);
  background: #fff;
  color: var(--job-blue);
}

.empty-action.ghost:hover {
  border-color: var(--job-blue);
  background: var(--job-blue-soft);
  box-shadow: none;
}

/* ---------------- 窄屏 ---------------- */

@media (max-width: 900px) {
  .message-shell {
    grid-template-columns: 1fr;
    border-radius: 12px;
  }

  .contact-pane {
    display: none;
  }

  .message-shell:not(.has-active-chat) .contact-pane {
    display: flex;
  }

  .message-shell:not(.has-active-chat) .chat-pane {
    display: none;
  }

  .chat-back {
    display: inline-flex;
  }

  .chat-head {
    padding: 14px 16px 12px;
  }

  .apply-card,
  .offer-card,
  .chat-hint {
    margin-inline: 16px;
  }

  /* 窄屏先保轨道本身，说明文字让位 */
  .stage-caption {
    display: none;
  }

  .conversation {
    padding: 18px 16px 22px;
  }

  .bubble {
    max-width: 84%;
  }

  .composer {
    padding-inline: 16px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .message-page *,
  .message-page *::before,
  .message-page *::after {
    transition-duration: 1ms !important;
  }
}
</style>
