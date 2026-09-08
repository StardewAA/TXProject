<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { message, Modal } from 'ant-design-vue';
import { IconifyIcon } from '@vben/icons';
import { useAccessStore } from '@vben/stores';

import { getTenantSimpleList } from '#/api/core/auth';
import { uploadFile } from '#/api/infra/file';
import {
  getPortalResumeList,
  type HrmPortalResumeApi,
} from '#/api/hrm/portal/resume';
import {
  createPortalRecruitApplication,
  getPortalRecruitApplication,
  getPortalRecruitApplicationPage,
  getPortalRecruitPost,
  getPortalRecruitPostPage,
  getPortalRecruitPostTypeList,
  withdrawPortalRecruitApplication,
  type HrmPortalRecruitApi,
} from '#/api/hrm/portal/recruit';
import { useConversationStore } from '#/views/im/home/store/conversationStore';
import { useFriendStore } from '#/views/im/home/store/friendStore';
import {
  ImConversationType,
  ImFriendAddSource,
} from '#/views/im/utils/constants';
import { getFriendDisplayName } from '#/views/im/utils/user';

const loading = ref(false);
const applicationLoading = ref(false);
const postDetailLoading = ref(false);
const posts = ref<HrmPortalRecruitApi.Post[]>([]);
const applications = ref<HrmPortalRecruitApi.Application[]>([]);
const savedResumes = ref<HrmPortalResumeApi.Resume[]>([]);
const postTotal = ref(0);
const applicationTotal = ref(0);
const selectedPost = ref<HrmPortalRecruitApi.Post>();
const selectedApplication = ref<HrmPortalRecruitApi.Application>();
const postModalOpen = ref(false);
const applyModalOpen = ref(false);
const applicationModalOpen = ref(false);
const applySubmitting = ref(false);
const activeTab = ref('posts');
const keyword = ref('');
const sortBy = ref<'latest' | 'salary'>('latest');
const postPage = reactive<HrmPortalRecruitApi.PostPageParams>({
  pageNo: 1,
  pageSize: 8,
  postName: '',
  postTypeId: undefined,
  jobNature: undefined,
});
const applicationPage = reactive({ pageNo: 1, pageSize: 8 });
const applyForm = reactive<HrmPortalRecruitApi.ApplyReq>({
  postId: 0,
  name: '',
  mobile: '',
  sex: 1,
  education: 5,
  resumeFileName: '',
  resumeFileUrl: '',
});
const applyFormRef = ref();
const postTypes = ref<HrmPortalRecruitApi.PostType[]>([]);
const tenantNames = ref<Record<number, string>>({});
const router = useRouter();
const accessStore = useAccessStore();
const friendStore = useFriendStore();
const conversationStore = useConversationStore();

const savedResumeOptions = computed(() =>
  savedResumes.value.filter(
    (resume) => resume.resumeType === 1 && resume.fileUrl,
  ),
);

const jobNatureOptions = [
  { label: '不限', value: 'all' as const },
  { label: '全职', value: 1 as const },
  { label: '实习', value: 2 as const },
  { label: '兼职', value: 3 as const },
];

const sortOptions = [
  { label: '最新发布', value: 'latest' as const },
  { label: '薪资优先', value: 'salary' as const },
];

function getRequestErrorCode(error: unknown) {
  const requestError = error as {
    data?: { code?: number };
    response?: { data?: { code?: number } };
  };
  return requestError.data?.code ?? requestError.response?.data?.code;
}

const displayPosts = computed(() => {
  const result = [...posts.value];
  if (sortBy.value === 'salary') {
    result.sort((left, right) => {
      const leftSalary = left.maxSalary ?? left.minSalary ?? -1;
      const rightSalary = right.maxSalary ?? right.minSalary ?? -1;
      return rightSalary - leftSalary;
    });
  }
  return result;
});

const postTypeLabel = computed(() => {
  const current = postTypes.value.find(
    (item) => item.id === postPage.postTypeId,
  );
  return current?.name;
});

function formatSalary(post: HrmPortalRecruitApi.Post) {
  if (
    post.minSalary === -1 ||
    post.maxSalary === -1 ||
    (post.minSalary == null && post.maxSalary == null)
  ) {
    return '面议';
  }
  const min = post.minSalary == null ? post.maxSalary : post.minSalary;
  const max = post.maxSalary == null ? post.minSalary : post.maxSalary;
  const range = min === max ? `${min}` : `${min}-${max}`;
  const unit = post.salaryUnit === 2 ? '元/年' : '元/月';
  return `${range} ${unit}`;
}

function formatDate(value?: string) {
  return value
    ? new Date(value).toLocaleString('zh-CN', {
        hour12: false,
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      })
    : '-';
}

function formatRelativeDate(value?: string) {
  if (!value) return '近期发布';
  const date = new Date(value);
  const now = new Date();
  const diffDays = Math.floor(
    (Date.UTC(now.getFullYear(), now.getMonth(), now.getDate()) -
      Date.UTC(date.getFullYear(), date.getMonth(), date.getDate())) /
      86_400_000,
  );
  if (diffDays <= 0) return '今天发布';
  if (diffDays === 1) return '昨天发布';
  if (diffDays < 7) return `${diffDays} 天前发布`;
  return formatDate(value).slice(0, 10);
}

function formatJobNature(value?: number) {
  return (
    jobNatureOptions.find((item) => item.value === value)?.label ||
    '工作性质不限'
  );
}

function formatWorkTime(value?: number) {
  return (
    (
      {
        1: '经验不限',
        2: '1 年以下',
        3: '1-3 年',
        4: '3-5 年',
        5: '5-10 年',
        6: '10 年以上',
      } as Record<number, string>
    )[value || 0] || '经验不限'
  );
}

function formatEducation(value?: number) {
  return (
    (
      {
        1: '学历不限',
        2: '高中及以上',
        3: '大专及以上',
        4: '本科及以上',
        5: '硕士及以上',
        6: '博士',
      } as Record<number, string>
    )[value || 0] || '学历不限'
  );
}

function formatLocation(post: HrmPortalRecruitApi.Post) {
  return (
    post.areaName || (post.areaId ? `地区 ${post.areaId}` : '工作地点待确认')
  );
}

function getCompanyName(post: HrmPortalRecruitApi.Post) {
  return (
    post.companyName ||
    post.companyShortName ||
    (post.tenantId ? tenantNames.value[post.tenantId] : undefined) ||
    '企业招聘'
  );
}

function getCompanyInitial(post: HrmPortalRecruitApi.Post) {
  return getCompanyName(post).slice(0, 1).toUpperCase();
}

function getPostTags(post: HrmPortalRecruitApi.Post) {
  return [
    formatJobNature(post.jobNature),
    formatWorkTime(post.workTime),
    formatEducation(post.educationRequire),
  ];
}

function isUrgent(post: HrmPortalRecruitApi.Post) {
  return post.emergencyLevel === 1;
}

function handleSearch() {
  postPage.postName = keyword.value.trim();
  postPage.pageNo = 1;
  void loadPosts();
}

function handleJobNatureChange(event: { target: { value: number | 'all' } }) {
  const value = event.target.value;
  postPage.jobNature = value === 'all' ? undefined : value;
  postPage.pageNo = 1;
  void loadPosts();
}

function handlePostTypeChange() {
  postPage.pageNo = 1;
  void loadPosts();
}

function resetFilters() {
  keyword.value = '';
  postPage.postName = '';
  postPage.postTypeId = undefined;
  postPage.jobNature = undefined;
  postPage.pageNo = 1;
  sortBy.value = 'latest';
  void loadPosts();
}

async function loadSavedResumes() {
  if (!accessStore.accessToken) {
    savedResumes.value = [];
    return;
  }
  try {
    savedResumes.value = (await getPortalResumeList()) || [];
  } catch (error) {
    savedResumes.value = [];
    console.warn('[PortalRecruit] 加载已保存简历失败', error);
  }
}

async function openApply(post: HrmPortalRecruitApi.Post) {
  Object.assign(applyForm, {
    postId: post.id,
    resumeFileName: '',
    resumeFileUrl: '',
    resumeRemark: '',
    sourceRemark: '',
  });
  await loadSavedResumes();
  const latestResume = savedResumeOptions.value[0];
  if (latestResume) {
    applyForm.resumeFileName = latestResume.fileName || latestResume.title;
    applyForm.resumeFileUrl = latestResume.fileUrl || '';
  }
  applyModalOpen.value = true;
}

function selectSavedResume(resumeId: number) {
  const resume = savedResumeOptions.value.find((item) => item.id === resumeId);
  if (!resume) return;
  applyForm.resumeFileName = resume.fileName || resume.title;
  applyForm.resumeFileUrl = resume.fileUrl || '';
}

function handleSavedResumeChange(value?: string) {
  if (!value) {
    applyForm.resumeFileName = '';
    applyForm.resumeFileUrl = '';
    return;
  }
  const resume = savedResumeOptions.value.find((item) => item.fileUrl === value);
  if (resume) selectSavedResume(resume.id);
}

async function loadPosts() {
  loading.value = true;
  try {
    const result = await getPortalRecruitPostPage(postPage);
    posts.value = result.list || [];
    postTotal.value = result.total || 0;
  } catch (error) {
    posts.value = [];
    postTotal.value = 0;
    console.warn('[PortalRecruit] 加载岗位失败', error);
  } finally {
    loading.value = false;
  }
}

async function loadApplications() {
  applicationLoading.value = true;
  try {
    const result = await getPortalRecruitApplicationPage(applicationPage);
    applications.value = result.list || [];
    applicationTotal.value = result.total || 0;
  } catch (error) {
    applications.value = [];
    applicationTotal.value = 0;
    console.warn('[PortalRecruit] 加载投递记录失败', error);
  } finally {
    applicationLoading.value = false;
  }
}

async function loadPostTypes() {
  try {
    postTypes.value = (await getPortalRecruitPostTypeList()) || [];
  } catch (error) {
    postTypes.value = [];
    console.warn('[PortalRecruit] 加载职位类型失败', error);
  }
}

async function loadTenantNames() {
  try {
    const tenants = (await getTenantSimpleList()) || [];
    tenantNames.value = Object.fromEntries(
      tenants
        .filter((tenant) => tenant.id && tenant.name)
        .map((tenant) => [tenant.id, tenant.name]),
    );
  } catch (error) {
    tenantNames.value = {};
    console.warn('[PortalRecruit] 加载企业名称失败', error);
  }
}

async function showPost(post: HrmPortalRecruitApi.Post) {
  postDetailLoading.value = true;
  postModalOpen.value = true;
  try {
    selectedPost.value = await getPortalRecruitPost(post.id);
  } catch (error) {
    postModalOpen.value = false;
    console.warn('[PortalRecruit] 加载岗位详情失败', error);
  } finally {
    postDetailLoading.value = false;
  }
}

async function showApplication(application: HrmPortalRecruitApi.Application) {
  selectedApplication.value = await getPortalRecruitApplication(application.id);
  applicationModalOpen.value = true;
}

async function contactHr(
  target: HrmPortalRecruitApi.Application | HrmPortalRecruitApi.Post,
) {
  if (!accessStore.accessToken) {
    message.warning('请先登录后再咨询 HR');
    return;
  }

  const hrUserId = target.ownerUserId;
  if (!hrUserId) {
    message.warning('该岗位暂未配置可联系的 HR');
    return;
  }

  let friend;
  try {
    await friendStore.fetchFriendList(true);
    friend = friendStore.getFriend(hrUserId);
  } catch (error) {
    // 好友列表接口异常时，按岗位负责人 ID 单独拉取，避免联系人列表的瞬时故障阻断咨询。
    console.warn('[PortalRecruit] 加载好友列表失败，尝试单独获取 HR', error);
    await friendStore.fetchFriendInfo(hrUserId);
    friend = friendStore.getFriend(hrUserId);
    if (!friend) {
      message.error('暂时无法加载 HR 联系人，请稍后重试');
      return;
    }
  }

  if (!friendStore.isActiveFriend(hrUserId)) {
    try {
      const requestId = await friendStore.applyFriendRequest({
        toUserId: hrUserId,
        applyContent: `您好，我想咨询「${target.postName || '该岗位'}」的招聘安排。`,
        addSource: ImFriendAddSource.RECRUITMENT,
      });
      if (requestId !== null) {
        message.info('已向 HR 提交好友申请，请等待对方同意后再聊天');
        return;
      }

      // 单向好友恢复会返回 null；重新拉取后必须确认关系已生效，不能直接创建不可发送的会话。
      await friendStore.fetchFriendInfo(hrUserId);
      friend = friendStore.getFriend(hrUserId);
    } catch (error) {
      console.warn('[PortalRecruit] 发起 HR 好友申请失败', error);
      if (getRequestErrorCode(error) === 1_040_610_005) {
        try {
          await friendStore.fetchFriendList(true);
          friend = friendStore.getFriend(hrUserId);
        } catch (refreshError) {
          console.warn('[PortalRecruit] 同步已存在的 HR 联系人失败', refreshError);
        }
        if (friendStore.isActiveFriend(hrUserId)) {
          conversationStore.openConversation(
            hrUserId,
            ImConversationType.PRIVATE,
            friend
              ? getFriendDisplayName(friend)
              : target.ownerUserName || '招聘 HR',
            friend?.avatar || target.ownerUserAvatar || '',
            { silent: !!friend?.silent },
          );
          await router.push({ name: 'ImHomeConversation' });
          return;
        }
        message.info('您已是该 HR 的好友，联系人信息同步后可继续咨询');
        return;
      }
      message.error('发起 HR 咨询失败，请稍后重试');
      return;
    }
  }

  if (!friendStore.isActiveFriend(hrUserId)) {
    message.info('HR 联系关系正在同步，请稍后重试');
    return;
  }

  conversationStore.openConversation(
    hrUserId,
    ImConversationType.PRIVATE,
    friend ? getFriendDisplayName(friend) : target.ownerUserName || '招聘 HR',
    friend?.avatar || target.ownerUserAvatar || '',
    { silent: !!friend?.silent },
  );
  await router.push({ name: 'ImHomeConversation' });
}

async function submitApplication() {
  await applyFormRef.value?.validate();
  if (!applyForm.resumeFileUrl) {
    message.warning('请先上传简历文件');
    return;
  }
  applySubmitting.value = true;
  try {
    await createPortalRecruitApplication(applyForm);
    message.success('应聘投递成功');
    applyModalOpen.value = false;
    activeTab.value = 'applications';
    await loadApplications();
  } finally {
    applySubmitting.value = false;
  }
}

async function handleResumeUpload(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file) return;
  const extension = file.name.split('.').pop()?.toLowerCase();
  if (!extension || !['pdf', 'doc', 'docx'].includes(extension)) {
    message.warning('仅支持 PDF、DOC、DOCX 格式的简历');
    return;
  }
  if (file.size > 10 * 1024 * 1024) {
    message.warning('简历文件不能超过 10MB');
    return;
  }
  const result = await uploadFile({ file, directory: 'hrm/recruit/resume' });
  applyForm.resumeFileName = file.name;
  applyForm.resumeFileUrl = result as string;
  message.success('简历上传成功');
}

function withdraw(application: HrmPortalRecruitApi.Application) {
  Modal.confirm({
    title: '确认撤回这条应聘？',
    content: '撤回后可再次投递其他职位。',
    onOk: async () => {
      await withdrawPortalRecruitApplication(application.id, '用户主动撤回');
      message.success('应聘已撤回');
      await loadApplications();
    },
  });
}

function handlePostPageChange(page: number, pageSize: number) {
  postPage.pageNo = page;
  postPage.pageSize = pageSize;
  void loadPosts();
}

function handleApplicationPageChange(page: number, pageSize: number) {
  applicationPage.pageNo = page;
  applicationPage.pageSize = pageSize;
  void loadApplications();
}

function handleTabChange(key: string | number) {
  activeTab.value = String(key);
  if (activeTab.value === 'applications' && !applications.value.length) {
    void loadApplications();
  }
}

onMounted(async () => {
  await Promise.all([
    loadPostTypes(),
    loadTenantNames(),
    loadPosts(),
    loadApplications(),
  ]);
});
</script>

<template>
  <div class="portal-page recruit-page">
    <section class="recruit-hero">
      <div class="hero-copy">
        <p class="eyebrow">人才服务 · 职业机会</p>
        <h1>发现适合你的下一份工作</h1>
        <p>浏览企业正在招聘的职位，投递简历并及时跟进招聘进度。</p>
      </div>
      <div class="hero-search">
        <a-input
          v-model:value="keyword"
          size="large"
          allow-clear
          placeholder="搜索职位名称，例如：项目经理、Java 工程师"
          @press-enter="handleSearch"
        >
          <template #prefix>
            <IconifyIcon icon="lucide:search" />
          </template>
        </a-input>
        <a-button type="primary" size="large" @click="handleSearch">
          搜索职位
        </a-button>
      </div>
      <div class="hero-stats">
        <span
          ><strong>{{ postTotal }}</strong> 个公开职位</span
        >
        <span
          ><strong>{{ postTypeLabel || '多种' }}</strong> 职业方向</span
        >
        <span>每日更新岗位机会</span>
      </div>
    </section>

    <a-tabs
      v-model:active-key="activeTab"
      class="recruit-tabs"
      @change="handleTabChange"
    >
      <a-tab-pane key="posts" tab="岗位机会">
        <section class="filter-panel">
          <div class="filter-row">
            <span class="filter-label">工作性质</span>
            <a-radio-group
              :value="postPage.jobNature ?? 'all'"
              button-style="solid"
              size="small"
              @change="handleJobNatureChange"
            >
              <a-radio-button
                v-for="option in jobNatureOptions"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </a-radio-button>
            </a-radio-group>
          </div>
          <div class="filter-row filter-row-secondary">
            <span class="filter-label">职位类型</span>
            <a-select
              v-model:value="postPage.postTypeId"
              allow-clear
              class="type-select"
              placeholder="全部职位类型"
              @change="handlePostTypeChange"
            >
              <a-select-option
                v-for="item in postTypes"
                :key="item.id"
                :value="item.id"
              >
                {{ item.name }}
              </a-select-option>
            </a-select>
            <span class="filter-label sort-label">排序</span>
            <a-select v-model:value="sortBy" class="sort-select">
              <a-select-option
                v-for="option in sortOptions"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </a-select-option>
            </a-select>
            <a-button type="link" class="reset-button" @click="resetFilters">
              <IconifyIcon icon="lucide:rotate-ccw" />
              重置筛选
            </a-button>
          </div>
        </section>

        <div v-if="loading" class="post-list post-list-loading">
          <div v-for="item in 4" :key="item" class="post-card">
            <a-skeleton active :paragraph="{ rows: 3 }" />
          </div>
        </div>
        <div v-else-if="!displayPosts.length" class="portal-empty">
          <span class="portal-empty-icon">
            <IconifyIcon icon="lucide:search-x" />
          </span>
          <strong>暂时没有符合条件的职位</strong>
          <p>试试调整工作性质、职位类型或搜索关键词</p>
        </div>
        <div v-else class="post-list">
          <article
            v-for="post in displayPosts"
            :key="post.id"
            class="post-card"
            tabindex="0"
            @click="showPost(post)"
            @keydown.enter="showPost(post)"
          >
            <div class="post-card-main">
              <div class="post-title-row">
                <h2>{{ post.postName }}</h2>
                <a-tag v-if="isUrgent(post)" color="error">急聘</a-tag>
              </div>
              <p class="post-salary">{{ formatSalary(post) }}</p>
              <div class="post-meta">
                <span>
                  <IconifyIcon icon="lucide:map-pin" />
                  {{ formatLocation(post) }}
                </span>
                <span>
                  <IconifyIcon icon="lucide:briefcase-business" />
                  {{ formatJobNature(post.jobNature) }}
                </span>
                <span>{{ formatRelativeDate(post.createTime) }}</span>
              </div>
              <div class="post-tags">
                <a-tag v-for="tag in getPostTags(post)" :key="tag">
                  {{ tag }}
                </a-tag>
              </div>
            </div>
            <div class="post-company">
              <img
                v-if="post.companyLogo"
                :src="post.companyLogo"
                class="company-logo"
                alt=""
              />
              <span v-else class="company-logo company-logo-fallback">
                {{ getCompanyInitial(post) }}
              </span>
              <div>
                <strong>{{ getCompanyName(post) }}</strong>
                <span>{{ post.deptName || '招聘团队' }}</span>
              </div>
            </div>
            <div class="post-card-actions">
              <span class="post-created">{{
                formatRelativeDate(post.createTime)
              }}</span>
              <div>
                <a-button type="link" size="small" @click.stop="showPost(post)">
                  查看详情
                </a-button>
                <a-button
                  type="link"
                  size="small"
                  @click.stop="contactHr(post)"
                >
                  <IconifyIcon icon="lucide:message-circle" />
                  咨询 HR
                </a-button>
                <a-button
                  type="primary"
                  size="small"
                  @click.stop="openApply(post)"
                >
                  立即投递
                </a-button>
              </div>
            </div>
          </article>
        </div>
        <a-pagination
          v-if="postTotal"
          :current="postPage.pageNo"
          :page-size="postPage.pageSize"
          :total="postTotal"
          class="post-pagination"
          show-size-changer
          :page-size-options="['8', '16', '24']"
          @change="handlePostPageChange"
        />
      </a-tab-pane>

      <a-tab-pane key="applications" tab="我的应聘">
        <div
          v-if="applicationLoading"
          class="application-list application-list-loading"
        >
          <a-skeleton
            v-for="item in 3"
            :key="item"
            active
            :paragraph="{ rows: 2 }"
          />
        </div>
        <div v-else-if="!applications.length" class="portal-empty">
          <span class="portal-empty-icon">
            <IconifyIcon icon="lucide:clipboard-list" />
          </span>
          <strong>还没有投递记录</strong>
          <p>去岗位机会中看看，找到合适的职位后立即投递</p>
        </div>
        <div v-else class="application-list">
          <article
            v-for="application in applications"
            :key="application.id"
            class="application-card"
          >
            <div class="application-main">
              <div class="application-title-row">
                <h2>{{ application.postName || '招聘职位' }}</h2>
                <a-tag
                  :color="
                    application.status === 'WITHDRAWN'
                      ? 'default'
                      : application.status === 'ELIMINATED'
                        ? 'error'
                        : 'processing'
                  "
                >
                  {{ application.statusName || application.status }}
                </a-tag>
              </div>
              <p class="application-meta">
                投递于 {{ formatDate(application.createTime) }}
                <span v-if="application.candidateName"
                  >· {{ application.candidateName }}</span
                >
              </p>
              <p v-if="application.resumeFileName" class="application-resume">
                <IconifyIcon icon="lucide:file-text" />
                {{ application.resumeFileName }}
              </p>
            </div>
            <div class="application-actions">
              <a-button
                type="link"
                size="small"
                @click="showApplication(application)"
              >
                查看进度
              </a-button>
              <a-button
                type="link"
                size="small"
                @click="contactHr(application)"
              >
                联系 HR
              </a-button>
              <a-button
                v-if="
                  ['SUBMITTED', 'SCREENING', 'INTERVIEWING'].includes(
                    application.status,
                  )
                "
                type="link"
                danger
                size="small"
                @click="withdraw(application)"
              >
                撤回应聘
              </a-button>
            </div>
          </article>
        </div>
        <a-pagination
          v-if="applicationTotal"
          :current="applicationPage.pageNo"
          :page-size="applicationPage.pageSize"
          :total="applicationTotal"
          class="post-pagination"
          @change="handleApplicationPageChange"
        />
      </a-tab-pane>
    </a-tabs>

    <a-modal
      v-model:open="postModalOpen"
      :confirm-loading="postDetailLoading"
      title="岗位详情"
      width="760px"
      @cancel="selectedPost = undefined"
    >
      <template v-if="selectedPost">
        <div class="post-detail-header">
          <div>
            <div class="post-title-row">
              <h2>{{ selectedPost.postName }}</h2>
              <a-tag v-if="isUrgent(selectedPost)" color="error">急聘</a-tag>
            </div>
            <p class="post-salary">{{ formatSalary(selectedPost) }}</p>
            <p class="detail-company">
              {{ getCompanyName(selectedPost) }} ·
              {{ selectedPost.deptName || '招聘团队' }}
            </p>
          </div>
          <span class="detail-date">{{
            formatRelativeDate(selectedPost.createTime)
          }}</span>
        </div>
        <div class="detail-tags">
          <a-tag v-for="tag in getPostTags(selectedPost)" :key="tag">{{
            tag
          }}</a-tag>
        </div>
        <a-descriptions class="detail-descriptions" :column="2" bordered>
          <a-descriptions-item label="工作地点">{{
            formatLocation(selectedPost)
          }}</a-descriptions-item>
          <a-descriptions-item label="招聘人数">{{
            selectedPost.recruitNum || '若干'
          }}</a-descriptions-item>
          <a-descriptions-item label="工作经验">{{
            formatWorkTime(selectedPost.workTime)
          }}</a-descriptions-item>
          <a-descriptions-item label="学历要求">{{
            formatEducation(selectedPost.educationRequire)
          }}</a-descriptions-item>
          <a-descriptions-item label="最迟到岗">{{
            formatDate(selectedPost.latestEntryTime)
          }}</a-descriptions-item>
          <a-descriptions-item label="职位类型">{{
            selectedPost.postTypeName || '-'
          }}</a-descriptions-item>
        </a-descriptions>
        <section class="detail-section">
          <h3>职位描述</h3>
          <p>{{ selectedPost.description || '暂无职位描述' }}</p>
        </section>
      </template>
      <template #footer>
        <a-button @click="postModalOpen = false">关闭</a-button>
        <a-button
          v-if="selectedPost"
          @click="contactHr(selectedPost)"
        >
          <IconifyIcon icon="lucide:message-circle" />
          咨询 HR
        </a-button>
        <a-button
          v-if="selectedPost"
          type="primary"
          @click="
            postModalOpen = false;
            openApply(selectedPost);
          "
        >
          立即投递
        </a-button>
      </template>
    </a-modal>

    <a-modal
      v-model:open="applyModalOpen"
      title="在线投递"
      width="640px"
      :confirm-loading="applySubmitting"
      @ok="submitApplication"
    >
      <div class="apply-intro">
        <IconifyIcon icon="lucide:send" />
        完善基础信息并上传简历，HR 会在招聘流程中及时联系你。
      </div>
      <a-form ref="applyFormRef" :model="applyForm" layout="vertical">
        <div class="form-grid">
          <a-form-item
            label="姓名"
            name="name"
            :rules="[{ required: true, message: '请输入姓名' }]"
          >
            <a-input
              v-model:value="applyForm.name"
              placeholder="请输入真实姓名"
            />
          </a-form-item>
          <a-form-item
            label="手机号"
            name="mobile"
            :rules="[{ required: true, message: '请输入手机号' }]"
          >
            <a-input
              v-model:value="applyForm.mobile"
              placeholder="用于接收招聘沟通"
            />
          </a-form-item>
          <a-form-item label="性别" name="sex">
            <a-radio-group v-model:value="applyForm.sex">
              <a-radio :value="1">男</a-radio>
              <a-radio :value="2">女</a-radio>
            </a-radio-group>
          </a-form-item>
          <a-form-item label="学历" name="education">
            <a-select v-model:value="applyForm.education">
              <a-select-option :value="4">大专</a-select-option>
              <a-select-option :value="5">本科</a-select-option>
              <a-select-option :value="6">硕士</a-select-option>
              <a-select-option :value="7">博士</a-select-option>
            </a-select>
          </a-form-item>
        </div>
        <a-form-item label="邮箱"
          ><a-input v-model:value="applyForm.email"
        /></a-form-item>
        <div class="form-grid">
          <a-form-item label="毕业院校"
            ><a-input v-model:value="applyForm.graduateSchool"
          /></a-form-item>
          <a-form-item label="最近工作单位"
            ><a-input v-model:value="applyForm.latestWorkPlace"
          /></a-form-item>
        </div>
        <a-form-item label="简历文件" required>
          <a-select
            v-if="savedResumeOptions.length"
            class="saved-resume-select"
            :value="applyForm.resumeFileUrl"
            allow-clear
            placeholder="优先选择个人中心已保存的简历"
            @change="handleSavedResumeChange"
          >
            <a-select-option
              v-for="resume in savedResumeOptions"
              :key="resume.id"
              :value="resume.fileUrl"
            >
              {{ resume.title }}{{ resume.fileName ? `（${resume.fileName}）` : '' }}
            </a-select-option>
          </a-select>
          <label class="apply-upload">
            <IconifyIcon icon="lucide:cloud-upload" />
            <span>{{
              applyForm.resumeFileName ||
              '选择 PDF / DOC / DOCX 简历（不超过 10MB）'
            }}</span>
            <input
              type="file"
              accept=".pdf,.doc,.docx"
              @change="handleResumeUpload"
            />
          </label>
        </a-form-item>
        <a-form-item label="补充说明"
          ><a-textarea v-model:value="applyForm.sourceRemark" :rows="3"
        /></a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="applicationModalOpen"
      title="应聘进度"
      :footer="null"
      width="680px"
    >
      <template v-if="selectedApplication">
        <a-descriptions :column="2" bordered>
          <a-descriptions-item label="职位">{{
            selectedApplication.postName
          }}</a-descriptions-item>
          <a-descriptions-item label="当前状态">{{
            selectedApplication.statusName || selectedApplication.status
          }}</a-descriptions-item>
          <a-descriptions-item label="投递时间">{{
            formatDate(selectedApplication.createTime)
          }}</a-descriptions-item>
          <a-descriptions-item label="联系方式">{{
            selectedApplication.mobileMasked
          }}</a-descriptions-item>
        </a-descriptions>
        <a-timeline class="timeline">
          <a-timeline-item color="green"
            >已投递：{{
              formatDate(selectedApplication.createTime)
            }}</a-timeline-item
          >
          <a-timeline-item
            v-for="interview in selectedApplication.interviews || []"
            :key="interview.id"
            color="blue"
          >
            第 {{ interview.stageNumber }} 轮面试：{{
              formatDate(interview.interviewTime)
            }}，{{ interview.address || '线上' }}
          </a-timeline-item>
          <a-timeline-item v-if="selectedApplication.offer" color="green">
            Offer：{{ selectedApplication.offer.approvalStatus || '处理中' }}
            <span v-if="selectedApplication.offer.plannedOnboardDate"
              >，计划
              {{ selectedApplication.offer.plannedOnboardDate }} 入职</span
            >
          </a-timeline-item>
        </a-timeline>
        <a-button
          type="primary"
          @click="
            applicationModalOpen = false;
            contactHr(selectedApplication);
          "
        >
          联系 HR
        </a-button>
      </template>
    </a-modal>
  </div>
</template>

<style scoped>
.portal-page {
  display: grid;
  gap: 20px;
}

.recruit-hero {
  display: grid;
  gap: 20px;
  padding: 32px 36px 26px;
  border: 1px solid #dbeafe;
  background: #eff6ff;
}

.hero-copy h1,
.hero-copy p {
  margin: 0;
}

.hero-copy h1 {
  margin-bottom: 8px;
  color: #0f172a;
  font-size: 30px;
  line-height: 1.25;
}

.hero-copy > p:last-child {
  color: #475569;
  font-size: 14px;
}

.eyebrow {
  margin: 0 0 8px;
  color: #2563eb;
  font-size: 13px;
  font-weight: 600;
}

.hero-search {
  display: flex;
  gap: 10px;
  max-width: 720px;
}

.hero-search .ant-input-affix-wrapper {
  flex: 1;
  border-color: #bfdbfe;
}

.hero-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  align-items: start;
  gap: 16px;
  color: #64748b;
  font-size: 13px;
}

.hero-stats span {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  grid-template-rows: auto auto;
  min-width: 0;
  gap: 3px;
  align-content: start;
  justify-items: start;
  line-height: 1.4;
}

.hero-stats strong {
  display: block;
  color: #1d4ed8;
  font-size: 17px;
  line-height: 1.2;
}

.recruit-tabs {
  min-height: 420px;
}

.filter-panel {
  display: grid;
  gap: 14px;
  margin-bottom: 16px;
  padding: 16px 18px;
  border: 1px solid #e5e7eb;
  background: #fff;
}

.filter-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.filter-row-secondary {
  padding-top: 14px;
  border-top: 1px solid #f1f5f9;
}

.filter-label {
  min-width: 60px;
  color: #64748b;
  font-size: 13px;
}

.sort-label {
  margin-left: auto;
}

.type-select {
  min-width: 180px;
}

.sort-select {
  width: 130px;
}

.reset-button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.post-list {
  display: grid;
  gap: 12px;
}

.post-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(180px, 0.32fr) auto;
  gap: 18px;
  padding: 20px 22px;
  border: 1px solid #e5e7eb;
  background: #fff;
  cursor: pointer;
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease,
    transform 160ms ease;
}

.post-card:hover,
.post-card:focus-visible {
  border-color: #93c5fd;
  box-shadow: 0 8px 24px rgb(30 64 175 / 9%);
  outline: none;
  transform: translateY(-1px);
}

.post-title-row,
.application-title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.post-title-row h2,
.application-title-row h2 {
  margin: 0;
  color: #111827;
  font-size: 18px;
  font-weight: 650;
}

.post-salary {
  margin: 10px 0 8px;
  color: #e11d48;
  font-size: 20px;
  font-weight: 650;
}

.post-meta,
.application-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.post-meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.post-tags,
.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 14px;
}

.post-tags :deep(.ant-tag),
.detail-tags :deep(.ant-tag) {
  margin: 0;
  color: #64748b;
  border-color: #e2e8f0;
  background: #f8fafc;
}

.post-company {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  padding-left: 18px;
  border-left: 1px solid #f1f5f9;
}

.post-company > div {
  display: grid;
  min-width: 0;
  gap: 5px;
}

.post-company strong,
.post-company span:not(.company-logo) {
  overflow: hidden;
  color: #334155;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.post-company span:not(.company-logo) {
  color: #94a3b8;
}

.company-logo {
  display: grid;
  width: 42px;
  height: 42px;
  flex-shrink: 0;
  place-items: center;
  border: 1px solid #dbeafe;
  color: #2563eb;
  background: #eff6ff;
  font-size: 17px;
  font-weight: 650;
  object-fit: cover;
}

.post-card-actions {
  display: flex;
  min-width: 148px;
  flex-direction: column;
  align-items: flex-end;
  justify-content: space-between;
  gap: 8px;
}

.post-card-actions > div {
  display: flex;
  align-items: center;
  gap: 2px;
  white-space: nowrap;
}

.post-created,
.detail-date {
  color: #94a3b8;
  font-size: 12px;
}

.post-pagination {
  margin: 20px 0 4px;
  text-align: right;
}

.portal-empty {
  display: grid;
  width: 100%;
  box-sizing: border-box;
  grid-template-columns: minmax(0, 1fr);
  min-height: 220px;
  place-items: center;
  align-content: center;
  justify-items: center;
  gap: 8px;
  padding: 32px 20px;
  border: 1px dashed #d1d5db;
  background: #fff;
  color: #64748b;
  text-align: center;
}

.portal-empty-icon {
  display: grid;
  width: 52px;
  min-width: 52px;
  height: 52px;
  min-height: 52px;
  flex: 0 0 52px;
  place-items: center;
  border-radius: 50%;
  color: #2563eb;
  background: #eff6ff;
  font-size: 24px;
}

.portal-empty-icon :deep(svg) {
  width: 24px;
  height: 24px;
}

.portal-empty strong {
  color: #334155;
  font-size: 15px;
  font-weight: 600;
}

.portal-empty p {
  margin: 0;
  color: #94a3b8;
  font-size: 13px;
}

.post-list-loading .post-card {
  cursor: default;
}

.application-list {
  display: grid;
  gap: 12px;
}

.application-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 20px 22px;
  border: 1px solid #e5e7eb;
  background: #fff;
}

.application-meta {
  margin-top: 9px;
}

.application-resume {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin: 12px 0 0;
  color: #2563eb;
  font-size: 13px;
}

.application-actions {
  display: flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 2px;
}

.post-detail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.post-detail-header .post-salary {
  margin-bottom: 6px;
}

.detail-company {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.detail-descriptions {
  margin-top: 18px;
}

.detail-section {
  margin-top: 24px;
}

.detail-section h3 {
  margin: 0 0 8px;
  color: #1f2937;
  font-size: 15px;
}

.detail-section p {
  margin: 0;
  min-height: 100px;
  color: #475569;
  line-height: 1.75;
  white-space: pre-wrap;
}

.apply-intro {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 18px;
  padding: 10px 12px;
  color: #475569;
  background: #f8fafc;
  font-size: 13px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.apply-upload {
  display: flex;
  align-items: center;
  gap: 9px;
  min-height: 48px;
  padding: 10px 14px;
  border: 1px dashed #93c5fd;
  color: #2563eb;
  background: #eff6ff;
  cursor: pointer;
  font-size: 13px;
}

.apply-upload span {
  overflow: hidden;
  color: #475569;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.apply-upload input {
  display: none;
}

.timeline {
  margin-top: 24px;
}

@media (max-width: 900px) {
  .post-card {
    grid-template-columns: minmax(0, 1fr) minmax(150px, 0.35fr);
  }

  .post-card-actions {
    grid-column: 1 / -1;
    min-width: 0;
    flex-direction: row;
    align-items: center;
  }
}

@media (max-width: 640px) {
  .recruit-hero {
    padding: 24px 20px;
  }

  .hero-copy h1 {
    font-size: 24px;
  }

  .hero-search {
    flex-direction: column;
  }

  .hero-search .ant-btn {
    width: 100%;
  }

  .hero-stats {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .filter-row-secondary {
    align-items: flex-start;
  }

  .sort-label {
    margin-left: 0;
  }

  .type-select,
  .sort-select {
    width: calc(100% - 70px);
  }

  .post-card {
    grid-template-columns: 1fr;
    gap: 14px;
    padding: 17px;
  }

  .post-company {
    padding: 14px 0 0;
    border-top: 1px solid #f1f5f9;
    border-left: 0;
  }

  .post-card-actions {
    align-items: flex-start;
    flex-direction: column;
  }

  .application-card {
    align-items: flex-start;
    flex-direction: column;
    padding: 17px;
  }

  .application-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .form-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .post-detail-header {
    flex-direction: column;
    gap: 8px;
  }
}
</style>
