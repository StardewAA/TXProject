<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';
import { useAccessStore, useUserStore } from '@vben/stores';

import { message } from 'ant-design-vue';

import {
  createPortalRecruitApplication,
  getPortalRecruitApplicationPage,
  getPortalRecruitPost,
  type HrmPortalRecruitApi,
} from '#/api/hrm/portal/recruit';
import {
  getRecruitChatConversationList,
  getRecruitChatMessageList,
  type HrmPortalRecruitChatApi,
  markRecruitChatRead,
  openRecruitChatConversation,
  sendRecruitChatMessage,
} from '#/api/hrm/portal/recruit/chat';
import {
  getPortalResumeList,
  type HrmPortalResumeApi,
} from '#/api/hrm/portal/resume';
import { uploadFile } from '#/api/infra/file';
import { getUserProfile } from '#/api/system/user/profile';
import { useAuthStore } from '#/store';

import RecruitApplyModal from './components/apply-modal.vue';

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
const moreFilterOpen = ref(false);
const activeMenuId = ref<number>();
const imageInput = ref<HTMLInputElement>();
const postDetails = ref<Record<number, HrmPortalRecruitApi.Post>>({});
const interviewPostIds = ref<number[]>([]);
const pinnedConversationIds = ref<number[]>([]);
const ignoredConversationIds = ref<number[]>([]);
const removedConversationIds = ref<number[]>([]);
const conversationRemarks = ref<Record<number, string>>({});
const interviewFilterLoaded = ref(false);
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
    if (tab.value === 'interview' && !interviewPostIds.value.includes(item.postId)) return false;
    if (!q) return true;
    return [item.peerUserName, companyName(item), recruiterPosition(item), item.lastContent]
      .filter(Boolean)
      .some((value) => String(value).includes(q));
  });
});
const moreFilterSelected = computed(() =>
  ['applied', 'communication', 'interview'].includes(tab.value),
);
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

function applicationStatusLabel(item?: HrmPortalRecruitChatApi.Conversation) {
  if (item?.applicationStatus === 'SUBMITTED') return '已投递';
  if (item?.applicationStatus === 'READY_TO_SUBMIT') return '待投递';
  return '';
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

function companyName(item: HrmPortalRecruitChatApi.Conversation) {
  const post = postDetails.value[item.postId];
  return post?.companyShortName || post?.companyName || '招聘企业';
}

function recruiterPosition(item: HrmPortalRecruitChatApi.Conversation) {
  return postDetails.value[item.postId]?.postName || item.postName || '招聘职位';
}

function resumeMeta(content: string) {
  const [title, fileName, fileUrl] = content.split('|');
  return { title: title || '简历', fileName, fileUrl };
}

function imageMeta(content: string) {
  const [prefix, encodedName, fileUrl] = content.split('|');
  if (prefix !== '__HRM_IMAGE__' || !fileUrl) return undefined;
  return {
    fileName: encodedName ? decodeURIComponent(encodedName) : '聊天图片',
    fileUrl,
  };
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

function markApplicationSubmitted(conversationId: number) {
  const conversation = conversations.value.find((item) => item.id === conversationId);
  if (conversation) conversation.applicationStatus = 'SUBMITTED';
}

function viewPost() {
  const postId = activeConversation.value?.postId;
  if (!postId) return;
  void router.push({ name: 'PortalRecruitDetail', query: { id: String(postId) } });
}

function chooseTab(value: typeof tab.value) {
  tab.value = value;
  moreFilterOpen.value = false;
}

function toggleMoreFilter() {
  moreFilterOpen.value = !moreFilterOpen.value;
  activeMenuId.value = undefined;
}

function toggleConversationMenu(id: number) {
  activeMenuId.value = activeMenuId.value === id ? undefined : id;
  moreFilterOpen.value = false;
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
    interviewPostIds.value = (result.list || [])
      .filter((item) => item.interviews?.length)
      .map((item) => item.postId);
    interviewFilterLoaded.value = true;
  } catch {
    interviewPostIds.value = [];
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
    if (error?.response?.status !== 401) {
      message.error(error?.message || '加载沟通记录失败');
    }
  } finally {
    if (requestId === conversationListRequestId) loading.value = false;
  }
}

async function selectConversation(id: number) {
  const requestId = ++selectionRequestId;
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
    const uploaded = await uploadFile({ file, directory: 'hrm/recruit/chat' });
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
    await createPortalRecruitApplication(data);
    markApplicationSubmitted(conversationId);
    await sendResumeMessage(data, conversationId);
    message.success('简历已直接投递，可在个人中心查看进度');
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
) {
  const targetConversationId =
    conversationId || applicationConversationId.value || activeId.value;
  if (!targetConversationId || !data.resumeFileUrl) return;
  const conversation = conversations.value.find(
    (item) => item.id === targetConversationId,
  );
  if (!conversation) return;
  if (data.postId !== conversation.postId) return;
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
    conversation.applicationStatus = 'SUBMITTED';
    await nextTick();
    if (activeId.value === targetConversationId && conversationPane.value) {
      conversationPane.value.scrollTop = conversationPane.value.scrollHeight;
    }
  } catch (error: any) {
    message.warning(error?.message || '简历已投递，但沟通消息发送失败');
  }
}

async function handleApplicationSubmitted(data: HrmPortalRecruitApi.ApplyReq) {
  const conversationId = applicationConversationId.value || activeId.value;
  if (!conversationId) return;
  resumeSending.value = true;
  try {
    markApplicationSubmitted(conversationId);
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
  await bootChat();
});

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer);
});
</script>

<template>
  <div class="message-page" :class="{ embedded }">
    <header v-if="!embedded" class="message-topbar">
      <button class="brand" type="button" @click="router.push({ name: 'PortalRecruit' })">天信人才</button>
      <nav>
        <button type="button" @click="router.push({ name: 'PortalRecruit' })">首页</button>
        <button type="button" @click="router.push({ name: 'PortalRecruit', query: { section: 'jobs' } })">职位</button>
        <button type="button" @click="router.push({ name: 'PortalRecruit', query: { section: 'companies' } })">公司</button>
      </nav>
      <div class="top-actions">
        <span class="active">消息</span>
        <button type="button" @click="router.push({ name: 'PortalRecruit', query: { section: 'resume' } })">简历</button>
        <span>{{ displayName }}</span>
        <span class="avatar">●</span>
      </div>
    </header>

    <main v-if="!loggedIn" class="message-shell empty-shell">
      <div class="empty-pane">
        <div class="empty-art">💬</div>
        <h2>登录后即可与 HR 实时沟通</h2>
        <p>投递岗位、发送简历、查看回复都在这里完成</p>
        <button class="empty-action" type="button" @click="goLogin">去登录</button>
      </div>
    </main>

    <main v-else class="message-shell">
      <aside class="contact-pane">
        <label class="contact-search">
          <input v-model="keyword" type="search" placeholder="搜索30天内的联系人" />
          <IconifyIcon icon="lucide:search" />
        </label>
        <div class="tabs">
          <button type="button" :class="{ selected: tab === 'all' }" @click="chooseTab('all')">全部</button>
          <button type="button" :class="{ selected: tab === 'unread' }" @click="chooseTab('unread')">
            未读
            <i v-if="unreadTotal" class="tab-badge">{{ unreadTotal > 99 ? '99+' : unreadTotal }}</i>
          </button>
          <div class="more-filter">
            <button type="button" :class="{ selected: moreFilterSelected }" @click.stop="toggleMoreFilter">更多⌄</button>
            <div v-if="moreFilterOpen" class="filter-menu" @click.stop>
              <button type="button" :class="{ active: tab === 'communication' }" @click="chooseTab('communication')">仅沟通</button>
              <button type="button" :class="{ active: tab === 'applied' }" @click="chooseTab('applied')">有投递</button>
              <button type="button" :class="{ active: tab === 'interview' }" @click="chooseTab('interview')">有面试</button>
            </div>
          </div>
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
            :class="{ chosen: activeId === item.id }"
            @click="selectConversation(item.id)"
            @keydown="handleConversationKeydown($event, item.id)"
            role="button"
            tabindex="0"
          >
            <span class="contact-body">
              <b>{{ recruiterName(item) }}</b>
              <small>{{ companyName(item) }}</small>
              <em>{{ recruiterPosition(item) }}</em>
            </span>
            <span class="contact-actions" @click.stop>
              <button type="button" class="more-trigger" @click.stop="toggleConversationMenu(item.id)">更多⌄</button>
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
          <div>
            <b>{{ recruiterName(activeConversation) }}</b>
            <span>{{ activeConversation.postName }}</span>
            <i class="conversation-status" :class="statusClass(activeConversation)">{{ statusLabel(activeConversation) }}</i>
            <i v-if="applicationStatusLabel(activeConversation)" class="conversation-status application-status">{{ applicationStatusLabel(activeConversation) }}</i>
          </div>
        </div>
        <div class="job-strip">
          <b>{{ activeConversation.postName || '沟通岗位' }}</b>
          <strong>{{ formatSalary(activeConversation) }}</strong>
          <span>{{ activeConversation.areaName || '地点待确认' }}</span>
          <button class="job-view-button" type="button" @click="viewPost">查看职位 ›</button>
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
            <div class="bubble" :class="item.mine ? 'mine' : 'theirs'">
              <a
                v-if="item.contentType === 2"
                class="resume-card"
                :href="resumeMeta(item.content).fileUrl || undefined"
                target="_blank"
                rel="noreferrer"
              >
                <span class="resume-icon">📄</span>
                <span>
                  <b>{{ resumeMeta(item.content).title }}</b>
                  <em>{{ resumeMeta(item.content).fileName || '附件简历' }}</em>
                </span>
              </a>
              <template v-else-if="imageMeta(item.content)">
                <a
                  class="image-card"
                  :href="imageMeta(item.content)?.fileUrl"
                  target="_blank"
                  rel="noreferrer"
                >
                  <img :src="imageMeta(item.content)?.fileUrl" :alt="imageMeta(item.content)?.fileName" />
                  <span>{{ imageMeta(item.content)?.fileName }}</span>
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
          <div class="empty-art">📭</div>
          <h2>还没有沟通会话</h2>
          <p>在职位列表点击「立即沟通」，会在这里打开与 HR 的对话</p>
          <button class="empty-action ghost" type="button" @click="goJobs">去看职位</button>
        </div>
      </section>
    </main>
    <RecruitApplyModal
      v-model:open="applyModalOpen"
      :post="applicationPost"
      @submitted="handleApplicationSubmitted"
    />
  </div>
</template>

<style scoped>
.message-page {
  min-height: 100vh;
  background: #fff8ef;
  color: #26343d;
}

.message-page.embedded {
  min-height: calc(100vh - 120px);
}

.message-topbar {
  height: 56px;
  display: flex;
  align-items: center;
  padding: 0 11%;
  background: linear-gradient(110deg, #d86616, #a84b1d);
  color: #fff;
  gap: 42px;
}

.brand {
  font-size: 18px;
  font-weight: 700;
  color: #fff;
}

.message-topbar button {
  border: 0;
  background: none;
  color: #eef8f8;
  cursor: pointer;
}

.message-topbar nav {
  display: flex;
  gap: 28px;
}

.top-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 20px;
}

.top-actions .active {
  color: #f49a45;
}

.avatar {
  font-size: 24px;
  color: #ddd;
}

.message-shell {
  display: grid;
  grid-template-columns: 370px 1fr;
  max-width: 1180px;
  height: calc(100vh - 56px);
  margin: auto;
  background: #fff;
  box-shadow: 0 0 18px #d9e9eb;
  overflow: hidden;
}

.message-page.embedded .message-shell {
  height: calc(100vh - 120px);
  max-width: none;
  box-shadow: 0 8px 24px rgb(17 65 73 / 8%);
  border: 1px solid #e0eeee;
  border-radius: 12px;
}

.empty-shell {
  grid-template-columns: 1fr;
  place-items: center;
}

.contact-pane {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-right: 1px solid #e0eeee;
  background: #fff;
}

.contact-search {
  margin: 20px;
  border: 1px solid #d7e2e4;
  border-radius: 4px;
  padding: 0 12px;
  color: #9aa9b5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  background: #f8fbfb;
}

.contact-search input {
  flex: 1;
  height: 36px;
  border: 0;
  outline: none;
  background: transparent;
  color: #26343d;
  font-size: 13px;
}

.contact-search input::placeholder {
  color: #9aa9b5;
}

.tabs {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 20px 15px;
  border-bottom: 1px solid #edf2f3;
  color: #65737c;
  font-size: 13px;
}

.tabs button,
.tabs span {
  padding: 5px 10px;
  border: 0;
  background: none;
  color: inherit;
  font-size: 13px;
}

.tabs button {
  cursor: pointer;
  border-radius: 4px;
}

.more-filter {
  position: relative;
}

.filter-menu,
.conversation-menu {
  position: absolute;
  z-index: 10;
  display: grid;
  min-width: 108px;
  padding: 5px;
  border: 1px solid #e2e9ea;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 8px 22px rgb(38 52 61 / 14%);
}

.filter-menu {
  top: calc(100% + 4px);
  left: 0;
}

.filter-menu button,
.conversation-menu button {
  padding: 8px 10px;
  border-radius: 5px;
  color: #52646d;
  text-align: left;
  white-space: nowrap;
}

.filter-menu button:hover,
.filter-menu button.active,
.conversation-menu button:hover {
  background: #fff0df;
  color: #d86616;
}

.tabs .selected {
  background: #fff0df;
  color: #d86616;
  border-radius: 4px;
}

.tab-badge,
.unread {
  display: inline-flex;
  min-width: 16px;
  height: 16px;
  margin-left: 4px;
  padding: 0 4px;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: #ff4d4f;
  color: #fff;
  font-size: 11px;
  font-style: normal;
  line-height: 1;
}

.contact-list {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.contact-empty {
  padding: 48px 24px;
  color: #94a1a8;
  text-align: center;
  font-size: 13px;
}

.contact-empty strong {
  display: block;
  margin-bottom: 8px;
  color: #52646d;
  font-size: 15px;
}

.contact-empty p {
  margin: 0;
  line-height: 1.6;
}

.contact {
  position: relative;
  width: 100%;
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 17px 20px;
  border: 0;
  border-bottom: 1px solid #f0f3f4;
  background: #fff;
  text-align: left;
  cursor: pointer;
}

.contact.chosen {
  background: #f1f1f1;
}

.contact:hover {
  background: #f7fbfb;
}

.contact-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 5px;
  overflow: hidden;
}

.contact-body b {
  overflow: hidden;
  font-size: 14px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.contact-body small {
  overflow: hidden;
  color: #65747a;
  font-weight: 400;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.contact-body em {
  font-style: normal;
  color: #94a1a8;
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.contact-actions {
  position: relative;
  flex-shrink: 0;
}

.more-trigger {
  padding: 5px 10px;
  border: 1px solid #d7e2e4;
  border-radius: 5px;
  background: #fff;
  color: #65747a;
  font-size: 12px;
  cursor: pointer;
}

.more-trigger:hover {
  border-color: #e8a164;
  color: #d86616;
}

.conversation-menu {
  top: calc(100% + 6px);
  right: 0;
}

.contact-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  flex-shrink: 0;
}

.contact time,
.contact-meta time {
  color: #a6b0b5;
  font-size: 12px;
}

.contact-status,
.conversation-status {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 2px 7px;
  font-size: 11px;
  font-style: normal;
  line-height: 1.4;
  white-space: nowrap;
}

.contact-status.pending,
.conversation-status.pending {
  background: #fff3e6;
  color: #c45d13;
}

.contact-status.waiting,
.conversation-status.waiting {
  background: #f0f4f5;
  color: #65747a;
}

.contact-status.qualified,
.conversation-status.qualified {
  background: #ecfdf5;
  color: #047857;
}

.contact-status.ended,
.conversation-status.ended {
  background: #f5f5f5;
  color: #8a959b;
}

.contact-application-status,
.conversation-status.application-status {
  background: #eff6ff;
  color: #2563eb;
}

.contact-application-status {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 2px 7px;
  font-size: 11px;
  font-style: normal;
  line-height: 1.4;
  white-space: nowrap;
}

.chat-pane {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background: #fff;
}

.chat-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 22px 30px 14px;
}

.chat-head b {
  font-size: 17px;
}

.chat-head span {
  margin-left: 18px;
  color: #52646d;
}

.chat-head .conversation-status {
  margin-left: 12px;
  vertical-align: 2px;
}

.chat-head a {
  border: 0;
  background: none;
  color: #d86616;
  font-size: 13px;
  cursor: pointer;
}

.chat-hint {
  margin: 8px 18px 0;
  padding: 8px 14px;
  border-radius: 8px;
  background: #fff7ed;
  color: #9a3412;
  font-size: 13px;
}

.chat-hint.ok {
  background: #ecfdf5;
  color: #047857;
}

.chat-hint.fail {
  background: #fef2f2;
  color: #b91c1c;
}

.job-strip {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 15px;
  margin: 0 18px;
  padding: 12px 18px;
  background: #fff8ef;
  border-radius: 10px;
}

.job-strip strong {
  color: #d86616;
}

.job-strip span {
  color: #65747a;
}

.job-strip button {
  border: 0;
  background: none;
  color: #65747a;
  cursor: pointer;
}

.job-strip .job-view-button {
  margin-left: auto;
}

.conversation {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 34px 30px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: linear-gradient(180deg, #fff 0%, #f7fbfb 100%);
}

.chat-empty {
  margin: auto;
  color: #a3afb5;
  font-size: 13px;
}

.date {
  text-align: center;
  color: #a3afb5;
  font-size: 13px;
  margin: 8px 0;
}

.bubble {
  max-width: 58%;
  padding: 12px 14px;
  border-radius: 8px;
  line-height: 1.65;
  font-size: 14px;
  white-space: pre-wrap;
  word-break: break-word;
}

.mine {
  margin-left: auto;
  background: #ffe2bd;
  border-bottom-right-radius: 2px;
}

.theirs {
  background: #f4f4f4;
  border-bottom-left-radius: 2px;
}

.resume-card {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 220px;
  color: inherit;
  text-decoration: none;
}

.resume-icon {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  border-radius: 8px;
  background: #fff;
  font-size: 18px;
}

.resume-card b,
.resume-card em {
  display: block;
}

.resume-card em {
  color: #65747a;
  font-size: 12px;
  font-style: normal;
}

.image-card {
  display: grid;
  gap: 6px;
  color: inherit;
  text-decoration: none;
}

.image-card img {
  display: block;
  max-width: 260px;
  max-height: 220px;
  border-radius: 6px;
  object-fit: contain;
}

.image-card span {
  color: #65747a;
  font-size: 12px;
}

.composer {
  border-top: 1px solid #e7eeee;
  padding: 12px 30px 18px;
  position: relative;
  background: #fff;
}

.tools {
  display: flex;
  gap: 20px;
  align-items: center;
  color: #69767b;
  font-size: 18px;
}

.tools button {
  border: 1px solid #d6e0e2;
  background: #fff;
  border-radius: 4px;
  padding: 5px 12px;
  color: #63747b;
  cursor: pointer;
  font-size: 13px;
}

.tools .tool-icon {
  display: inline-grid;
  width: 22px;
  height: 22px;
  padding: 0;
  place-items: center;
  border: 0;
  background: transparent;
  color: inherit;
  font-size: 18px;
}

.tools .tool-icon:disabled {
  border: 0;
  background: transparent;
}

.image-input {
  display: none;
}

.resume-tool-wrap {
  display: inline-flex;
}

.tools button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.composer textarea {
  width: 100%;
  height: 72px;
  border: 0;
  outline: none;
  resize: none;
  margin-top: 8px;
  padding-right: 88px;
  color: #26343d;
  font-size: 14px;
  line-height: 1.6;
  background: transparent;
}

.send {
  position: absolute;
  right: 30px;
  bottom: 18px;
  border: 0;
  background: #f5f7f7;
  color: #c4cbcd;
  padding: 8px 20px;
  border-radius: 5px;
  cursor: not-allowed;
}

.send.ready {
  background: #e87522;
  color: #fff;
  cursor: pointer;
}

.empty-chat {
  place-items: center;
}

.empty-pane {
  display: grid;
  place-items: center;
  align-content: center;
  gap: 10px;
  padding: 48px 24px;
  text-align: center;
  color: #65737c;
}

.empty-art {
  font-size: 42px;
}

.empty-pane h2 {
  margin: 8px 0 0;
  color: #26343d;
  font-size: 20px;
}

.empty-pane p {
  margin: 0;
  max-width: 360px;
  line-height: 1.7;
}

.empty-action {
  margin-top: 8px;
  border: 0;
  border-radius: 6px;
  padding: 8px 18px;
  background: #e87522;
  color: #fff;
  cursor: pointer;
}

.empty-action.ghost {
  background: #fff0df;
  color: #c85e18;
}

@media (max-width: 800px) {
  .message-shell,
  .message-page.embedded .message-shell {
    grid-template-columns: 1fr;
    height: calc(100vh - 56px);
    border-radius: 0;
  }

  .contact-pane {
    display: none;
  }

  .message-topbar {
    padding: 0 16px;
  }

  .message-topbar nav {
    display: none;
  }

  .chat-head {
    padding: 16px;
  }

  .conversation {
    padding: 20px;
  }

  .bubble {
    max-width: 85%;
  }
}
.message-page { color: #5d4636; background: #fffdfb; }
.message-page .message-shell, .message-page .conversation, .message-page .conversation-list { border-color: #f4e5d7; background: #fff; }
.message-page .conversation-item.active, .message-page .message-composer { background: #fff7ed; }
.message-page .send-button, .message-page .message-send, .message-page :deep(.ant-btn-primary) { border-color: #efc89f; background: linear-gradient(135deg, #f8d8b2, #efb77d); color: #8a4a21; }
.message-page .send-button:hover, .message-page .message-send:hover, .message-page :deep(.ant-btn-primary:hover) { background: linear-gradient(135deg, #efb77d, #e99d5b); }
</style>
