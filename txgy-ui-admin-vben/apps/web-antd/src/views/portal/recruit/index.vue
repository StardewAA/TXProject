<script lang="ts" setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';
import { useUserStore } from '@vben/stores';

import { Pagination } from 'ant-design-vue';

import {
  getPortalRecruitPostPage,
  getPortalRecruitPostTypeList,
  type HrmPortalRecruitApi,
} from '#/api/hrm/portal/recruit';
import {
  getRecruitProfileName,
  getRecruitProfileResume,
  isRecruitProfileComplete,
  parseRecruitProfileName,
} from '#/api/hrm/portal/resume';
import { AreaCascader } from '#/components/area';

import RecruitProfile from '../profile/recruit.vue';
import CompanyDirectory from './components/company-directory.vue';
import Messages from './messages.vue';
import OnlineResume from './online-resume.vue';
import RecruitProfileSetup from './profile-setup.vue';

const loading = ref(false);
const posts = ref<HrmPortalRecruitApi.Post[]>([]);
const postTotal = ref(0);
const keyword = ref('');
const sortBy = ref<'latest' | 'salary'>('latest');
const salaryRange = ref<string>();
const postTypes = ref<HrmPortalRecruitApi.PostType[]>([]);
const selectedPost = ref<HrmPortalRecruitApi.Post>();
const postPage = reactive<HrmPortalRecruitApi.PostPageParams>({
  pageNo: 1,
  pageSize: 10,
  postName: '',
  postTypeId: undefined,
  jobNature: undefined,
  areaId: undefined,
  workTime: undefined,
  educationRequire: undefined,
  salaryMin: undefined,
  salaryMax: undefined,
  salaryNegotiable: undefined,
});
const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
const profileDisplayName = ref('');
const profileChecked = ref(false);
const profileCompleted = ref(true);
const profileSetupOpen = ref(false);
type ProfileAction = {
  favoritePostId?: number;
  postId?: string;
  section?: 'messages' | 'resume' | 'user';
};
const pendingProfileAction = ref<ProfileAction>();
let profileCheckPromise: Promise<boolean> | undefined;
const navUserName = computed(() => {
  const name =
    profileDisplayName.value ||
    userStore.userInfo?.nickname ||
    userStore.userInfo?.username ||
    '用户';
  return /^1\d{10}$/.test(name) ? `${name.slice(0, 3)}****${name.slice(-4)}` : name;
});
const favoritePostIds = ref<number[]>([]);
const subnavScrolled = ref(false);
const selectedHomeCategory = ref<'technology' | number>();

const showAllPosts = computed(() => route.query.view === 'list');
const requestedSection = computed<'companies' | 'home' | 'jobs' | 'messages' | 'resume' | 'user'>(() => {
  const section = Array.isArray(route.query.section) ? route.query.section[0] : route.query.section;
  if (section === 'companies' || section === 'jobs' || section === 'messages' || section === 'resume' || section === 'user') return section;
  return 'home';
});
const activeSection = computed(() => {
  if (!profileChecked.value) return 'home';
  if (!profileCompleted.value && isProfileActionSection(requestedSection.value)) return 'home';
  return requestedSection.value;
});
const previewPosts = computed(() => displayPosts.value.slice(0, 9));
const homePreviewPosts = computed(() => {
  let result = displayPosts.value;
  if (selectedHomeCategory.value === 'technology') {
    result = result.filter((post) =>
      [post.postName, post.postTypeName, post.description]
        .filter(Boolean)
        .join(' ')
        .includes('技术'),
    );
  } else if (typeof selectedHomeCategory.value === 'number') {
    result = result.filter((post) => post.postTypeId === selectedHomeCategory.value);
  }
  return result.slice(0, 9);
});

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

const workTimeOptions = [
  { label: '经验不限', value: 1 },
  { label: '1 年以下', value: 2 },
  { label: '1-3 年', value: 3 },
  { label: '3-5 年', value: 4 },
  { label: '5-10 年', value: 5 },
  { label: '10 年以上', value: 6 },
];

const educationOptions = [
  { label: '学历不限', value: 1 },
  { label: '高中及以上', value: 2 },
  { label: '大专及以上', value: 3 },
  { label: '本科及以上', value: 4 },
  { label: '硕士及以上', value: 5 },
  { label: '博士', value: 6 },
];

const salaryOptions = [
  { label: '薪资不限', value: 'all' },
  { label: '5K 以下', value: '0-5000' },
  { label: '5K-10K', value: '5000-10000' },
  { label: '10K-20K', value: '10000-20000' },
  { label: '20K 以上', value: '20000-' },
  { label: '面议', value: 'negotiable' },
];

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

const isSearchMode = computed(
  () => Boolean(
    keyword.value.trim() ||
      postPage.postTypeId ||
      postPage.jobNature ||
      postPage.areaId ||
      postPage.workTime ||
      postPage.educationRequire ||
      postPage.salaryMin ||
      postPage.salaryMax ||
      postPage.salaryNegotiable,
  ),
);

function formatLocation(post: HrmPortalRecruitApi.Post) {
  return (
    post.areaName || (post.areaId ? `地区 ${post.areaId}` : '工作地点待确认')
  );
}

function formatProvinceLocation(post: HrmPortalRecruitApi.Post) {
  const location = post.areaName?.replace(/\s+/g, '').trim();
  if (!location) return post.areaId ? `地区 ${post.areaId}` : '工作地点待确认';
  const municipality = location.match(/^(北京市|上海市|天津市|重庆市)/);
  if (municipality) return municipality[1];
  const autonomousRegion = location.match(/^(内蒙古自治区|广西壮族自治区|西藏自治区|宁夏回族自治区|新疆维吾尔自治区)/);
  if (autonomousRegion) return autonomousRegion[1];
  const province = location.match(/^.+?省/);
  return province?.[0] || location;
}

function formatSalary(post: HrmPortalRecruitApi.Post) {
  if (post.salaryUnit === -1 || (post.minSalary ?? 0) < 0) return '面议';
  const min = post.minSalary;
  const max = post.maxSalary;
  if (min == null && max == null) return '薪资面议';
  const format = (value?: number) => (value == null ? '' : `${value / 1000}K`);
  if (min != null && max != null) return `${format(min)}-${format(max)}`;
  return min != null ? `${format(min)}起` : `${format(max)}以内`;
}

function formatWorkTime(value?: number) {
  return workTimeOptions.find((option) => option.value === value)?.label || '经验不限';
}

function formatEducation(value?: number) {
  return educationOptions.find((option) => option.value === value)?.label || '学历不限';
}

function selectPostType(id?: number) {
  postPage.postTypeId = id;
  refreshPosts();
}

function selectHomeCategory(category: 'technology' | number) {
  selectedHomeCategory.value = category;
}

function isProfileActionSection(section: string): section is 'messages' | 'resume' | 'user' {
  return ['messages', 'resume', 'user'].includes(section);
}

async function checkRecruitProfile() {
  if (profileChecked.value) return profileCompleted.value;
  if (!profileCheckPromise) {
    profileCheckPromise = getRecruitProfileResume()
      .then((resume) => {
        profileCompleted.value = isRecruitProfileComplete(resume);
        const name = parseRecruitProfileName(resume?.content);
        if (name) profileDisplayName.value = name;
        return profileCompleted.value;
      })
      .catch((error) => {
        console.warn('[PortalRecruit] 检查求职资料失败', error);
        profileCompleted.value = true;
        return true;
      })
      .finally(() => {
        profileChecked.value = true;
      });
  }
  return profileCheckPromise;
}

function applyFavorite(postId: number) {
  favoritePostIds.value = favoritePostIds.value.includes(postId)
    ? favoritePostIds.value.filter((id) => id !== postId)
    : [...favoritePostIds.value, postId];
  localStorage.setItem('portal-recruit-favorites', JSON.stringify(favoritePostIds.value));
}

function runPendingProfileAction() {
  const action = pendingProfileAction.value;
  pendingProfileAction.value = undefined;
  if (!action) return;
  if (action.favoritePostId != null) {
    applyFavorite(action.favoritePostId);
    return;
  }
  if (action.section) {
    void router.push({
      name: 'PortalRecruit',
      query: {
        section: action.section,
        ...(action.postId ? { postId: action.postId } : {}),
      },
    });
  }
}

async function requestProfileAction(action: ProfileAction) {
  pendingProfileAction.value = action;
  if (await checkRecruitProfile()) {
    runPendingProfileAction();
    return;
  }
  profileSetupOpen.value = true;
}

function handleProfileSetupCompleted() {
  profileCompleted.value = true;
  profileChecked.value = true;
  profileSetupOpen.value = false;
  void getRecruitProfileName().then((name) => {
    if (name) profileDisplayName.value = name;
  });
  runPendingProfileAction();
}

function cancelProfileSetup() {
  profileSetupOpen.value = false;
  pendingProfileAction.value = undefined;
}

function openGatedSection(section: 'messages' | 'resume' | 'user', postId?: string) {
  void requestProfileAction({ section, postId });
}

function openChat(post: HrmPortalRecruitApi.Post) {
  openGatedSection('messages', String(post.id));
}

function selectPost(post: HrmPortalRecruitApi.Post) {
  selectedPost.value = post;
}

function isFavorite(post: HrmPortalRecruitApi.Post) {
  return favoritePostIds.value.includes(post.id);
}

function toggleFavorite(post: HrmPortalRecruitApi.Post) {
  if (profileChecked.value && profileCompleted.value) {
    applyFavorite(post.id);
    return;
  }
  void requestProfileAction({ favoritePostId: post.id });
}

function handleSearch() {
  postPage.postName = keyword.value.trim();
  postPage.pageNo = 1;
  void router.push({ name: 'PortalRecruit', query: { section: 'jobs' } });
  void loadPosts();
}

function selectSection(section: 'companies' | 'home' | 'jobs') {
  void router.push({
    name: 'PortalRecruit',
    query: section === 'home' ? {} : { section },
  });
}

function handleJobNatureChange(value?: 'all' | number) {
  postPage.jobNature = value === 'all' ? undefined : value;
  refreshPosts();
}

function handleFilterChange() {
  refreshPosts();
}

function handleSalaryChange(value?: string) {
  postPage.salaryMin = undefined;
  postPage.salaryMax = undefined;
  postPage.salaryNegotiable = undefined;
  if (value === 'negotiable') {
    postPage.salaryNegotiable = true;
  } else if (value && value !== 'all') {
    const [min, max] = value.split('-');
    postPage.salaryMin = min ? Number(min) : undefined;
    postPage.salaryMax = max ? Number(max) : undefined;
  }
  refreshPosts();
}

function refreshPosts() {
  postPage.pageNo = 1;
  void loadPosts();
}

function resetFilters() {
  keyword.value = '';
  postPage.postName = '';
  postPage.postTypeId = undefined;
  postPage.jobNature = undefined;
  postPage.areaId = undefined;
  postPage.workTime = undefined;
  postPage.educationRequire = undefined;
  postPage.salaryMin = undefined;
  postPage.salaryMax = undefined;
  postPage.salaryNegotiable = undefined;
  salaryRange.value = undefined;
  postPage.pageNo = 1;
  sortBy.value = 'latest';
  void loadPosts();
}

async function loadPosts() {
  loading.value = true;
  try {
    const result = await getPortalRecruitPostPage(postPage);
    posts.value = result.list || [];
    postTotal.value = result.total || 0;
    selectedPost.value = posts.value[0];
  } catch (error) {
    posts.value = [];
    postTotal.value = 0;
    console.warn('[PortalRecruit] 加载岗位失败', error);
  } finally {
    loading.value = false;
  }
}

function handlePostPageChange(page: number, pageSize: number) {
  postPage.pageNo = page;
  postPage.pageSize = pageSize;
  void loadPosts();
}

function formatPostTotal(total: number) {
  return `共 ${total} 个岗位`;
}

function showMorePosts() {
  router.push({ name: 'PortalRecruit', query: { section: 'jobs', view: 'list' } });
}

function syncSubnavScrollState() {
  subnavScrolled.value = window.scrollY > 8;
}

onMounted(async () => {
  syncSubnavScrollState();
  window.addEventListener('scroll', syncSubnavScrollState, { passive: true });
  try {
    const saved = JSON.parse(localStorage.getItem('portal-recruit-favorites') || '[]');
    if (Array.isArray(saved)) favoritePostIds.value = saved.filter((id): id is number => typeof id === 'number');
  } catch {}
  await Promise.all([
    loadPosts(),
    getPortalRecruitPostTypeList()
      .then((result) => {
        postTypes.value = result || [];
      })
      .catch(() => {
        postTypes.value = [];
      }),
    checkRecruitProfile(),
  ]);
  const requestedProfileSection = requestedSection.value;
  if (!profileCompleted.value && isProfileActionSection(requestedProfileSection) && !pendingProfileAction.value) {
    const postId = Array.isArray(route.query.postId) ? route.query.postId[0] : route.query.postId;
    pendingProfileAction.value = { section: requestedProfileSection, postId: postId || undefined };
    profileSetupOpen.value = true;
  }
});

onUnmounted(() => {
  window.removeEventListener('scroll', syncSubnavScrollState);
});
</script>

<template>
  <div class="portal-page recruit-page">
    <nav :class="{ 'is-scrolled': subnavScrolled }" class="recruit-subnav" aria-label="我要找工作导航">
      <div class="recruit-nav-links">
      <button :class="{ active: activeSection === 'home' }" type="button" @click="selectSection('home')">首页</button>
      <button :class="{ active: activeSection === 'jobs' }" type="button" @click="selectSection('jobs')">职位</button>
      <button :class="{ active: activeSection === 'companies' }" type="button" @click="selectSection('companies')">公司</button>
      </div>
      <div class="recruit-nav-account">
        <button type="button" @click="openGatedSection('messages')"><IconifyIcon icon="lucide:bell" /> 消息</button>
        <button type="button" @click="openGatedSection('resume')">在线简历</button>
        <button class="recruit-account-button" type="button" aria-label="打开我的应聘中心" @click="openGatedSection('user')">
          <span class="recruit-account-name">{{ navUserName }}</span>
        </button>
      </div>
    </nav>

    <a-modal
      v-model:open="profileSetupOpen"
      :footer="null"
      :mask-closable="false"
      width="1180px"
      wrap-class-name="profile-setup-modal"
      @cancel="cancelProfileSetup"
    >
      <RecruitProfileSetup :embedded="true" @completed="handleProfileSetupCompleted" />
    </a-modal>

    <Messages v-if="activeSection === 'messages'" embedded />
    <OnlineResume v-else-if="activeSection === 'resume'" />
    <RecruitProfile v-else-if="activeSection === 'user'" section="user" embedded />

    <section v-if="activeSection === 'home'" class="recruit-hero">
      <div class="hero-copy">
        <p class="eyebrow">人才服务 · 职业机会</p>
        <h1>发现适合你的下一份工作</h1>
      </div>
      <div class="hero-visual" aria-hidden="true">
        <div class="hero-orbit hero-orbit-one"></div>
        <div class="hero-orbit hero-orbit-two"></div>
        <div class="hero-profile-card">
          <span class="hero-avatar">职</span>
          <span><b>专业人才</b><small>正在寻找新机会</small></span>
          <i>↗</i>
        </div>
        <div class="hero-role-card"><span>匹配职位</span><b>项目管理 · </b></div>
      </div>
      <div class="hero-search">
        <a-input
          v-model:value="keyword"
          size="large"
          allow-clear
          placeholder="搜索职位名称、关键字..."
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
      <div class="quick-searches">
        <span>热门职位</span>
        <button
          v-for="type in postTypes.slice(0, 6)"
          :key="type.id"
          type="button"
          @click="selectPostType(type.id)"
        >
          {{ type.name }}
        </button>
        <span v-if="!postTypes.length" class="quick-searches-muted">工程管理 · 采购 · 资料员 · 行政人事</span>
      </div>
    </section>

    <section v-if="activeSection === 'home'" class="recruit-banner-grid">
      <article class="banner-card banner-primary">
        <div><span class="banner-label"></span><h2>专业人才，遇见好工作</h2><p>聚焦防腐保温产业链，精准连接企业与人才</p><button type="button" @click="selectSection('jobs')">浏览全部职位 →</button></div>
        <div class="banner-art"><span>⚙</span><i>✦</i><b>↗</b></div>
      </article>
      <article class="banner-card banner-secondary">
        <div><span class="banner-label">企业招聘服务</span><h2>发布职位，快速招到人</h2><p>专业顾问为企业提供人才匹配支持</p><button type="button" @click="selectSection('companies')">查看合作企业 →</button></div>
        <div class="banner-art banner-art-alt"><span>▦</span><i>+</i></div>
      </article>
    </section>

    <a-tabs
      v-if="activeSection === 'home' || activeSection === 'jobs'"
      :class="{ 'home-recruit-tabs': activeSection === 'home' }"
      class="recruit-tabs"
    >
      <a-tab-pane key="posts" tab="岗位机会">
        <div v-if="activeSection === 'home'" class="home-jobs-heading">
          <h2>热招职位</h2>
          <div class="home-job-categories">
            <button :class="{ active: selectedHomeCategory === 'technology' }" type="button" @click="selectHomeCategory('technology')">技术</button>
            <button v-for="type in postTypes.slice(0, 8)" :key="type.id" :class="{ active: selectedHomeCategory === type.id }" type="button" @click="selectHomeCategory(type.id)">{{ type.name }}</button>
          </div>
        </div>
        <section v-if="activeSection === 'jobs'" class="filter-panel">
          <div class="filter-heading">
            <div>
              <span class="section-kicker">岗位机会</span>
              <strong>筛选你感兴趣的职位</strong>
            </div>
            <span class="result-count">{{ formatPostTotal(postTotal) }}</span>
          </div>
          <div class="filter-toolbar">
            <AreaCascader
              v-model="postPage.areaId"
              allow-clear
              class="filter-control area-control"
              :change-on-select="true"
              placeholder="地址"
              popup-class-name="recruit-filter-dropdown"
              :selectable-levels="[1, 2, 3]"
              show-search
              @update:model-value="handleFilterChange"
            />
            <a-select
              v-model:value="postPage.postTypeId"
              allow-clear
              class="filter-control"
              placeholder="职位类型"
              popup-class-name="recruit-filter-dropdown"
              @change="handleFilterChange"
            >
              <a-select-option
                v-for="type in postTypes"
                :key="type.id"
                :value="type.id"
              >
                {{ type.name }}
              </a-select-option>
            </a-select>
            <a-select
              :value="postPage.jobNature ?? 'all'"
              allow-clear
              class="filter-control"
              placeholder="求职类型"
              popup-class-name="recruit-filter-dropdown"
              @change="handleJobNatureChange"
            >
              <a-select-option
                v-for="option in jobNatureOptions"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </a-select-option>
            </a-select>
            <a-select
              v-model:value="salaryRange"
              allow-clear
              class="filter-control"
              placeholder="薪资待遇"
              popup-class-name="recruit-filter-dropdown"
              @change="handleSalaryChange"
            >
              <a-select-option
                v-for="option in salaryOptions"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </a-select-option>
            </a-select>
            <a-select
              v-model:value="postPage.workTime"
              allow-clear
              class="filter-control"
              placeholder="工作经验"
              :options="workTimeOptions"
              popup-class-name="recruit-filter-dropdown"
              @change="handleFilterChange"
            />
            <a-select
              v-model:value="postPage.educationRequire"
              allow-clear
              class="filter-control"
              placeholder="学历要求"
              :options="educationOptions"
              popup-class-name="recruit-filter-dropdown"
              @change="handleFilterChange"
            />
            <a-select
              v-model:value="sortBy"
              class="filter-control sort-control"
              popup-class-name="recruit-filter-dropdown"
              placeholder="最新发布"
            >
              <a-select-option
                v-for="option in sortOptions"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </a-select-option>
            </a-select>
          </div>
          <div class="filter-actions">
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
        <div v-else-if="!displayPosts.length || (activeSection === 'home' && !isSearchMode && !showAllPosts && !homePreviewPosts.length)" class="portal-empty">
          <span class="portal-empty-icon">
            <IconifyIcon icon="lucide:search-x" />
          </span>
          <strong>暂时没有符合条件的职位</strong>
          <p>试试调整工作性质、职位类型或搜索关键词</p>
        </div>
        <div v-else-if="activeSection === 'home' && !isSearchMode && !showAllPosts" class="home-job-grid">
          <article
            v-for="post in homePreviewPosts"
            :key="post.id"
            class="post-card"
            role="link"
            tabindex="0"
            @click="router.push({ name: 'PortalRecruitDetail', query: { id: post.id } })"
            @keydown.enter="router.push({ name: 'PortalRecruitDetail', query: { id: post.id } })"
          >
            <div class="post-card-main">
              <div class="post-title-row">
                <strong class="post-title">{{ post.postName }}</strong>
                <strong class="post-salary post-salary-top">{{ formatSalary(post) }}</strong>
              </div>
              <div class="post-tags">
                <span>{{ formatProvinceLocation(post) }}</span>
                <span>{{ formatWorkTime(post.workTime) }}</span>
                <span>{{ formatEducation(post.educationRequire) }}</span>
              </div>
            </div>
            <div class="post-card-actions">
              <span class="company-meta-detail">{{ post.postTypeName || '行业企业' }}</span>
            </div>
          </article>
        </div>
        <div v-else class="search-results-layout">
          <div class="search-result-list">
            <div class="search-result-heading">
              <strong>搜索结果</strong>
              <span>{{ formatPostTotal(postTotal) }}</span>
            </div>
            <article
              v-for="post in displayPosts"
              :key="post.id"
              class="search-post-card"
              :class="{ selected: selectedPost?.id === post.id }"
              @click="selectPost(post)"
            >
              <div class="search-post-topline">
                <strong>{{ post.postName }}</strong>
                <b>{{ formatSalary(post) }}</b>
              </div>
              <div class="search-post-meta">
                <span>{{ formatLocation(post) }}</span>
                <span>{{ formatWorkTime(post.workTime) }}</span>
                <span>{{ formatEducation(post.educationRequire) }}</span>
              </div>
              <div class="search-post-company">
                {{ post.companyShortName || post.companyName || '行业企业' }}
              </div>
              <button class="search-favorite" :class="{ active: isFavorite(post) }" type="button" @click.stop="toggleFavorite(post)">
                <IconifyIcon icon="lucide:star" /> {{ isFavorite(post) ? '已收藏' : '收藏' }}
              </button>
            </article>
          </div>
          <article v-if="selectedPost" class="search-detail-card">
            <div class="search-detail-header">
              <div>
                <p class="detail-eyebrow">职位机会</p>
                <h2>{{ selectedPost.postName }}</h2>
                <div class="search-post-meta">
                  <span>{{ formatLocation(selectedPost) }}</span>
                  <span>{{ formatWorkTime(selectedPost.workTime) }}</span>
                  <span>{{ formatEducation(selectedPost.educationRequire) }}</span>
                </div>
              </div>
              <div class="search-detail-actions">
                <strong>{{ formatSalary(selectedPost) }}</strong>
                <div class="detail-action-row">
                  <a-button class="detail-favorite" @click="toggleFavorite(selectedPost)">
                    <IconifyIcon icon="lucide:star" /> {{ isFavorite(selectedPost) ? '已收藏' : '收藏' }}
                  </a-button>
                  <a-button type="primary" @click="openChat(selectedPost)">立即沟通</a-button>
                </div>
              </div>
            </div>
            <section class="search-detail-section">
              <h3>职位描述</h3>
              <p v-for="(line, index) in (selectedPost.description || '岗位职责与工作内容请以职位详情为准。').split(/\r?\n/)" :key="index">
                {{ line }}
              </p>
            </section>
            <section class="search-detail-section detail-summary">
              <h3>职位信息</h3>
              <p>招聘公司：{{ selectedPost.companyShortName || selectedPost.companyName || '行业企业' }}</p>
              <p>工作地点：{{ formatLocation(selectedPost) }}</p>
              <p>工作经验：{{ formatWorkTime(selectedPost.workTime) }} / 学历要求：{{ formatEducation(selectedPost.educationRequire) }}</p>
            </section>
          </article>
        </div>
        <a-button
          v-if="activeSection === 'home' && !isSearchMode && !showAllPosts && postTotal > previewPosts.length"
          class="show-more-posts"
          type="default"
          @click="showMorePosts"
        >
          查看更多
        </a-button>
        <Pagination
          v-if="postTotal && (activeSection === 'jobs' || isSearchMode || showAllPosts)"
          :current="postPage.pageNo"
          :page-size="postPage.pageSize"
          :page-size-options="['10', '15', '20']"
          :total="postTotal"
          class="post-pagination"
          show-quick-jumper
          show-size-changer
          :show-total="formatPostTotal"
          @change="handlePostPageChange"
        />
      </a-tab-pane>
</a-tabs>

    <CompanyDirectory v-else-if="activeSection === 'companies'" />

    <section v-if="activeSection === 'home'" class="service-section">
      <div class="service-heading"><div><span class="section-kicker">行业服务</span><h2>一站式职业发展支持</h2></div><span>从求职到成长，天信人才服务陪伴每一步</span></div>
      <div class="service-grid">
        <article><span class="service-icon">⌕</span><h3>行业岗位库</h3><p>汇聚防腐、保温、工程管理等细分岗位，持续更新真实机会。</p><a @click="selectSection('jobs')">探索岗位 →</a></article>
        <article><span class="service-icon">▣</span><h3>人才能力提升</h3><p>精选行业课程与实操培训，帮助你提升专业技能与竞争力。</p><a @click="router.push('/portal/training')">进入云培训 →</a></article>
        <article><span class="service-icon">◎</span><h3>企业招聘顾问</h3><p>为企业提供职位发布、人才筛选、招聘流程等专业服务。</p><a @click="selectSection('companies')">了解企业服务 →</a></article>
        <article><span class="service-icon">↗</span><h3>职业成长社区</h3><p>连接行业专家与同行，分享经验，发现更多发展可能。</p><a>加入行业社区 →</a></article>
      </div>
    </section>
</div>
</template>

<style scoped>
.portal-page {
  display: grid;
  gap: 10px;
}

.recruit-page {
  display: grid;
  grid-template-columns: minmax(16px, 1fr) minmax(0, 1180px) minmax(16px, 1fr);
  margin-top: -32px;
  --recruit-ink: #4a2916;
  --recruit-muted: #876f5e;
  --recruit-line: #f0dac1;
  --recruit-accent: #e87522;
  --recruit-accent-soft: #fff0df;
}

.recruit-page > * { grid-column: 2; min-width: 0; }
.recruit-page > .recruit-subnav { grid-column: 1 / -1; width: 100vw; margin-left: calc((100% - 100vw) / 2); padding-left: max(56px, calc((100vw - 1180px) / 2 + 32px)); padding-right: max(56px, calc((100vw - 1180px) / 2 + 32px)); }

.recruit-banner-grid { display:grid; grid-template-columns:1.35fr 1fr; gap:16px; margin-top:2px; }
.banner-card { min-height:190px; border-radius:18px; padding:25px 28px; display:flex; justify-content:space-between; overflow:hidden; position:relative; box-shadow:0 10px 28px rgb(29 73 75 / 8%); }
.banner-primary { color:#fff; background:linear-gradient(115deg,#d85f18,#ef8b35 62%,#ffc169); }
.banner-secondary { color:#5d341d; background:linear-gradient(115deg,#fff8ee,#ffe6bd); }
.banner-label { font-size:16px; letter-spacing:.08em; opacity:.82; }
.banner-card h2 { margin:12px 0 7px; font-size:25px; letter-spacing:-.03em; }
.banner-card p { margin:0; opacity:.8; font-size:20px; }
.banner-card button { margin-top:20px; border:0; border-radius:18px; padding:8px 15px; color:inherit; background:#ffffff2b; cursor:pointer; }
.banner-secondary button { background:#fff; color:#c85e18; }
.banner-art { width:150px; display:grid; place-items:center; position:relative; font-size:70px; opacity:.8; }
.banner-art i,.banner-art b { position:absolute; font-size:25px; font-style:normal; }
.banner-art i { top:15px; right:20px; }.banner-art b { bottom:18px; left:10px; }
.banner-art-alt span { font-size:86px; color:#e78a36; }.banner-art-alt i { color:#d65d16; }

.service-section { padding:18px 0 30px; }
.service-heading { display:flex; justify-content:space-between; align-items:end; margin-bottom:16px; }
.service-heading h2 { margin:7px 0 0; color:var(--recruit-ink); font-size:25px; }
.service-heading > span { color:var(--recruit-muted); font-size:13px; }
.service-grid { display:grid; grid-template-columns:repeat(4,1fr); gap:14px; }
.service-grid article { padding:20px; border:1px solid var(--recruit-line); border-radius:14px; background:#fff; transition:transform .2s,box-shadow .2s; }
.service-grid article:hover { transform:translateY(-3px); box-shadow:0 12px 25px rgb(183 92 24 / 12%); }
.service-icon { display:grid; width:38px; height:38px; place-items:center; border-radius:11px; color:#c85e18; background:#fff0df; font-size:22px; }
.service-grid h3 { margin:14px 0 7px; font-size:16px; color:var(--recruit-ink); }.service-grid p { min-height:42px; margin:0 0 13px; color:var(--recruit-muted); font-size:12px; line-height:1.7; }.service-grid a { color:#c85e18; font-size:12px; cursor:pointer; }

.recruit-subnav {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 26px;
  min-height: 54px;
  width: 100%;
  margin-left: 0;
  padding: 0 24px;
  border-block: 1px solid rgb(238 225 211 / 90%);
  border-radius: 0;
  background: rgb(255 255 255 / 82%);
  box-shadow: 0 8px 20px rgb(183 92 24 / 7%);
  backdrop-filter: blur(16px) saturate(115%);
  -webkit-backdrop-filter: blur(16px) saturate(115%);
  transition: background-color 220ms ease, box-shadow 220ms ease, backdrop-filter 220ms ease;
}

.recruit-subnav.is-scrolled {
  background: rgb(255 255 255 / 92%);
  box-shadow: 0 10px 28px rgb(183 92 24 / 10%);
  backdrop-filter: blur(18px) saturate(145%);
  -webkit-backdrop-filter: blur(18px) saturate(145%);
}

.recruit-nav-links, .recruit-nav-account { display: flex; align-items: center; }
.recruit-nav-links { gap: 24px; }
.recruit-nav-account { margin-left: auto; gap: 22px; }
.recruit-nav-account button { display: inline-flex; align-items: center; gap: 7px; }
.recruit-account-name { max-width: 120px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.recruit-subnav button {
  position: relative;
  height: 52px;
  padding: 0 4px;
  border: 0;
  background: transparent;
  color: #875222;
  cursor: pointer;
  font-size: 14px;
  transition: color 180ms ease, background-color 180ms ease;
}

.recruit-subnav button:focus-visible { outline: 2px solid #4de0d2; outline-offset: 3px; border-radius: 4px; }
@media (max-width: 640px) {
  .home-jobs-heading { align-items: flex-start; flex-direction: column; gap: 14px; }
  .recruit-page { margin-top: -24px; }
  .recruit-subnav { flex-wrap: wrap; gap: 0 16px; padding: 0 14px; }
  .recruit-nav-links { gap: 20px; }
  .recruit-nav-account { width: 100%; justify-content: flex-end; gap: 20px; border-top: 1px solid #ffffff18; }
  .recruit-nav-account button { height: 44px; }
}
@media (prefers-reduced-motion: reduce) { .recruit-subnav, .recruit-subnav button { transition: none; } }

.recruit-subnav button:hover,
.recruit-subnav button.active {
  color: #d86616;
}

.recruit-subnav button.active::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 3px;
  border-radius: 3px 3px 0 0;
  background: #ef8b35;
  content: '';
}

.recruit-empty-section {
  display: grid;
  min-height: 360px;
  place-items: center;
  align-content: center;
  gap: 10px;
  border: 1px solid var(--recruit-line);
  border-radius: 16px;
  background: #fff;
  color: var(--recruit-muted);
  text-align: center;
}

.recruit-empty-section h2,
.recruit-empty-section p { margin: 0; }
.recruit-empty-section h2 { color: var(--recruit-ink); font-size: 20px; }
.recruit-empty-icon {
  display: grid;
  width: 52px;
  height: 52px;
  place-items: center;
  border-radius: 16px;
  color: #fff;
  background: var(--recruit-accent);
  font-size: 22px;
  font-weight: 700;
}

.recruit-hero {
  position: relative;
  display: grid;
  width: min(100%, 1280px);
  box-sizing: border-box;
  margin-inline: auto;
  gap: 24px;
  padding: 42px 52px 28px;
  border: 1px solid #f2d4ad;
  border-radius: 16px;
  background: linear-gradient(135deg, #fffaf3 0%, #fff6ea 62%, #ffe7c5 100%);
  box-shadow: 0 18px 46px rgb(183 92 24 / 10%);
}

.hero-copy {
  max-width: 760px;
}

.hero-visual {
  position: absolute;
  top: 22px;
  right: 34px;
  width: 250px;
  height: 142px;
  pointer-events: none;
}
.hero-orbit { position: absolute; border: 1px solid rgb(232 117 34 / 28%); border-radius: 50%; }
.hero-orbit-one { inset: 12px 22px 2px 32px; transform: rotate(22deg); }
.hero-orbit-two { inset: 0 4px 25px 60px; transform: rotate(-26deg); }
.hero-profile-card,
.hero-role-card {
  position: absolute;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid rgb(255 255 255 / 90%);
  border-radius: 12px;
  background: rgb(255 255 255 / 92%);
  box-shadow: 0 12px 28px rgb(183 92 24 / 12%);
}
.hero-profile-card { top: 34px; left: 22px; }
.hero-profile-card > span:not(.hero-avatar) { display: grid; gap: 3px; }
.hero-profile-card b { color: #5d341d; font-size: 13px; }
.hero-profile-card small { color: #a48268; font-size: 11px; }
.hero-profile-card i { color: var(--recruit-accent); font-size: 18px; font-style: normal; }
.hero-avatar { display: grid; width: 28px; height: 28px; place-items: center; border-radius: 9px; color: #fff; background: var(--recruit-accent); font-size: 13px; font-weight: 700; }
.hero-role-card { right: 2px; bottom: 4px; display: grid; gap: 3px; padding: 9px 12px; }
.hero-role-card span { color: #a48268; font-size: 10px; }
.hero-role-card b { color: #80502d; font-size: 12px; }

.hero-copy h1,
.hero-copy p {
  margin: 0;
}

.hero-copy h1 {
  margin-bottom: 8px;
  color: #4a2916;
  font-size: clamp(32px, 4vw, 46px);
  line-height: 1.18;
}

.hero-note {
  margin: 0;
  color: #475569;
  font-size: 17px;
  line-height: 1.7;
}

.eyebrow {
  margin: 0 0 8px;
  color: var(--recruit-accent);
  font-size: 22px;
  font-weight: 600;
}

.hero-search {
  display: flex;
  width: min(100%, 1040px);
  gap: 14px;
}

.hero-search .ant-input-affix-wrapper {
  flex: 1;
  min-height: 54px;
  padding-inline: 16px;
  background: #fff !important;
  border-color: #efc38e;
}

.hero-search :deep(.ant-input) {
  color: #334155;
  background: #fff !important;
}

.hero-search :deep(.ant-input::placeholder) {
  color: #94a3b8;
}

.hero-search .ant-btn {
  min-width: 132px;
  height: 54px;
  padding-inline: 24px;
  border-color: var(--recruit-accent);
  background: var(--recruit-accent);
  font-size: 16px;
}

.quick-searches {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 12px;
  color: #64748b;
  font-size: 13px;
}

.quick-searches button {
  padding: 5px 12px;
  border: 0;
  border-radius: 999px;
  color: #c85e18;
  background: #fff;
  cursor: pointer;
  font-size: 13px;
}

.quick-searches button:hover,
.quick-searches button:focus-visible {
  color: #fff;
  background: var(--recruit-accent);
  outline: none;
}

.quick-searches-muted { color: #64748b; }

.recruit-tabs {
  min-height: 420px;
}

.filter-panel {
  display: grid;
  width: min(100%, 1280px);
  box-sizing: border-box;
  gap: 14px;
  margin-bottom: 16px;
  margin-inline: auto;
  padding: 20px 22px;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  background: #fff;
}

.filter-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #eef2f7;
}

.filter-heading > div { display: grid; gap: 4px; }
.section-kicker { color: #2563eb; font-size: 12px; font-weight: 700; letter-spacing: .08em; }
.filter-heading strong { color: #0f172a; font-size: 18px; }
.result-count { color: #94a3b8; font-size: 13px; }

.filter-toolbar {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
}

.filter-control {
  width: 100%;
  min-width: 0;
}

.area-control :deep(.ant-select),
.area-control :deep(.ant-cascader-picker) {
  width: 100%;
  border-color: #dbe3ee;
  background: #fff !important;
}

.filter-panel :deep(.ant-select-selector) {
  border-color: #dbe3ee !important;
  background: #fff !important;
}

.filter-panel :deep(.ant-select-selection-item),
.filter-panel :deep(.ant-select-selection-placeholder),
.filter-panel :deep(.ant-cascader-picker-label) {
  color: #475569;
}

.filter-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.filter-label {
  min-width: 60px;
  color: #64748b;
  font-size: 13px;
}

.filter-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 14px;
  border-top: 1px solid #f1f5f9;
}

.reset-button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

:global(.recruit-filter-dropdown),
:global(.recruit-filter-dropdown .ant-select-dropdown),
:global(.recruit-filter-dropdown .ant-cascader-menus),
:global(.recruit-filter-dropdown .ant-cascader-menu) {
  color: #334155 !important;
  background: #fff !important;
}

:global(.recruit-filter-dropdown .ant-select-item),
:global(.recruit-filter-dropdown .ant-cascader-menu-item) {
  color: #334155 !important;
  background: #fff !important;
}

:global(.recruit-filter-dropdown .ant-select-item-option-active),
:global(.recruit-filter-dropdown .ant-select-item-option-selected),
:global(.recruit-filter-dropdown .ant-cascader-menu-item-active) {
  color: #1d4ed8 !important;
  background: #eff6ff !important;
}

.home-job-grid {
  display: grid;
  width: min(100%, 1280px);
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
  margin-inline: auto;
}

.home-jobs-heading { display: flex; align-items: end; justify-content: space-between; width: min(100%, 1180px); margin: 8px auto 18px; }
.home-jobs-heading h2 { margin: 0; color: #4a2916; font-size: 24px; }
.home-jobs-heading p { margin: 6px 0 0; color: #a48268; font-size: 13px; }
.home-job-categories { display: flex; gap: 8px; flex-wrap: wrap; }
.home-job-categories button { padding: 7px 14px; border: 0; border-radius: 999px; background: #fff; color: #8c7564; cursor: pointer; font-size: 13px; }
.home-job-categories button:hover, .home-job-categories button.active { background: #fff0df; color: #d86616; }

.home-job-grid .post-card {
  grid-template-columns: 1fr;
  gap: 18px;
  min-height: 228px;
  padding: 22px 22px 0;
  overflow: hidden;
  cursor: pointer;
}

.home-job-grid .post-card-main { min-width: 0; }
.home-job-grid .post-card-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-inline: -22px;
  padding: 13px 22px;
  background: #fffaf4;
}
.home-job-grid .post-apply { display: none; }

.show-more-posts {
  display: block;
  width: 276px;
  height: 60px;
  margin: 16px auto 4px;
  border: 0;
  border-radius: 12px;
  background: #d8f3f5;
  color: #08a6b3;
  font-size: 20px;
}

.show-more-posts:hover,
.show-more-posts:focus-visible {
  border: 0;
  background: #c6edf0;
  color: #078d99;
}

.search-results-layout {
  display: grid;
  width: min(100%, 1280px);
  grid-template-columns: minmax(320px, 410px) minmax(0, 1fr);
  gap: 18px;
  margin-inline: auto;
}

.search-result-list,
.search-detail-card {
  min-width: 0;
  border: 1px solid var(--recruit-line);
  border-radius: 12px;
  background: #fff;
}

.search-result-list { overflow: hidden; }
.search-result-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px;
  border-bottom: 1px solid var(--recruit-line);
}
.search-result-heading strong { color: var(--recruit-ink); font-size: 17px; }
.search-result-heading span { color: var(--recruit-muted); font-size: 13px; }
.search-post-card {
  display: grid;
  gap: 10px;
  padding: 18px 20px;
  border-bottom: 1px solid #f0f3f2;
  cursor: pointer;
  transition: background 160ms ease;
}
.search-post-card:last-child { border-bottom: 0; }
.search-post-card:hover,
.search-post-card.selected { background: var(--recruit-accent-soft); }
.search-post-topline,
.search-detail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.search-post-topline strong { color: var(--recruit-ink); font-size: 16px; }
.search-post-topline b,
.search-detail-actions strong { color: #ef665c; font-size: 17px; white-space: nowrap; }
.search-post-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  color: var(--recruit-muted);
  font-size: 13px;
}
.search-post-company { color: #52615c; font-size: 13px; }
.search-detail-card { padding: 30px 34px; }
.search-detail-header { padding-bottom: 26px; border-bottom: 1px solid var(--recruit-line); }
.search-detail-header h2 { margin: 0 0 12px; color: var(--recruit-ink); font-size: 28px; }
.search-detail-actions { display: grid; justify-items: end; gap: 16px; }
.search-detail-actions .ant-btn { min-width: 112px; background: var(--recruit-accent); border-color: var(--recruit-accent); }
.detail-action-row { display: flex; gap: 10px; }
.detail-action-row .ant-btn { height: 40px; }
.detail-favorite { color: var(--recruit-accent) !important; background: #fff !important; border-color: var(--recruit-accent) !important; }
.favorite-button,
.search-favorite { display: inline-flex; align-items: center; gap: 5px; border: 0; color: #81908b; background: transparent; cursor: pointer; font-size: 13px; }
.favorite-button:hover,
.favorite-button.active,
.search-favorite:hover,
.search-favorite.active { color: #f2a93b; }
.search-favorite { justify-self: start; padding: 0; }
.search-detail-section { padding: 28px 0 12px; color: #596862; font-size: 15px; line-height: 1.9; }
.search-detail-section h3 { margin: 0 0 14px; color: var(--recruit-ink); font-size: 18px; }
.search-detail-section p { margin: 0 0 7px; }
.detail-summary { padding-top: 10px; border-top: 1px solid var(--recruit-line); }

.post-list {
  display: grid;
  width: min(100%, 1280px);
  margin-inline: auto;
  gap: 12px;
}

.post-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  width: min(100%, 1280px);
  align-items: center;
  justify-self: center;
  gap: 28px;
  padding: 22px 26px;
  border-radius: 12px;
  border: 1px solid #dbe3ee;
  background: #fff;
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease,
    transform 160ms ease;
}

.post-card:hover,
.post-card:has(.post-title:hover) {
  border-color: #93c5fd;
  box-shadow: 0 8px 24px rgb(30 64 175 / 9%);
  transform: translateY(-1px);
}

.post-title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 16px;
}

.post-title {
  display: inline-flex;
  max-width: 100%;
  padding: 0;
  border: 0;
  color: #0f172a;
  background: transparent;
  cursor: pointer;
  font: inherit;
  font-size: 20px;
  font-weight: 700;
  line-height: 1.35;
  text-align: left;
  transition: color 160ms ease;
}

.post-company-row,
.post-tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 12px;
  color: #64748b;
  font-size: 13px;
}

.post-company-row { margin-top: 9px; color: #475569; font-weight: 600; }
.post-type-tag,
.post-tags span { padding: 3px 8px; border-radius: 4px; background: #f1f5f9; font-weight: 400; }
.post-excerpt { margin-top: 9px; }
.post-tags { margin-top: 12px; }

.post-title:hover,
.post-title:focus-visible {
  color: #2563eb;
  outline: none;
  text-decoration: underline;
  text-underline-offset: 4px;
}

.post-location {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #64748b;
  font-size: 14px;
}

.post-location :deep(svg) {
  color: #2563eb;
  font-size: 16px;
}

.post-excerpt {
  margin: 10px 0 0;
  max-width: 900px;
  overflow: hidden;
  color: #64748b;
  font-size: 14px;
  line-height: 1.65;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.post-card-actions {
  display: grid;
  min-width: 0;
  align-items: center;
  justify-content: flex-end;
}

.post-salary {
  color: #f05b52;
  font-size: 20px;
  white-space: nowrap;
}

.post-apply {
  min-width: 112px;
  height: 42px;
  padding-inline: 24px;
  border-color: #2563eb;
  border-radius: 6px;
  background: #2563eb;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
}

.post-apply:hover,
.post-apply:focus-visible {
  border-color: #1d4ed8;
  background: #1d4ed8;
  color: #fff;
}

.post-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 28px auto 4px;
  color: #2563eb;
}

.post-pagination :deep(.ant-pagination-item),
.post-pagination :deep(.ant-pagination-prev .ant-pagination-item-link),
.post-pagination :deep(.ant-pagination-next .ant-pagination-item-link) {
  border-color: #bfdbfe;
  background: #fff;
}

.post-pagination :deep(.ant-pagination-item a),
.post-pagination :deep(.ant-pagination-prev .ant-pagination-item-link),
.post-pagination :deep(.ant-pagination-next .ant-pagination-item-link) {
  color: #2563eb;
}

.post-pagination :deep(.ant-pagination-item:hover),
.post-pagination :deep(.ant-pagination-prev:hover .ant-pagination-item-link),
.post-pagination :deep(.ant-pagination-next:hover .ant-pagination-item-link) {
  border-color: #2563eb;
  background: #eff6ff;
}

.post-pagination :deep(.ant-pagination-item-active) {
  border-color: #2563eb;
  background: #2563eb;
}

.post-pagination :deep(.ant-pagination-item-active a),
.post-pagination :deep(.ant-pagination-item-active:hover a) {
  color: #fff;
}

.post-pagination :deep(.ant-pagination-options),
.post-pagination :deep(.ant-pagination-options-quick-jumper) {
  color: #2563eb;
}

.post-pagination :deep(.ant-pagination-options-size-changer .ant-select-selector),
.post-pagination :deep(.ant-pagination-options-quick-jumper input) {
  border-color: #bfdbfe;
  color: #2563eb;
  background: #fff;
}

.post-pagination :deep(.ant-pagination-options-size-changer:hover .ant-select-selector),
.post-pagination :deep(.ant-pagination-options-quick-jumper input:hover),
.post-pagination :deep(.ant-pagination-options-quick-jumper input:focus) {
  border-color: #2563eb;
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

@media (max-width: 900px) {
  .recruit-banner-grid { grid-template-columns:1fr; }
  .service-grid { grid-template-columns:repeat(2,1fr); }
  .service-heading { display:block; }.service-heading > span { display:block; margin-top:8px; }
}
@media (max-width: 560px) {
  .service-grid { grid-template-columns:1fr; }
  .banner-card { min-height:170px; padding:20px; }.banner-art { width:100px; font-size:55px; }
}
@media (min-width: 641px) and (max-width: 900px) {
  .hero-visual { opacity: .38; right: 14px; }

  .home-job-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .search-results-layout { grid-template-columns: 1fr; }

  .filter-toolbar {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .post-card {
    gap: 20px;
    padding: 20px;
  }
}

@media (max-width: 640px) {
  .recruit-hero {
    padding: 28px 20px 22px;
  }

  .hero-copy h1 {
    font-size: 28px;
  }

  .hero-visual { display: none; }

  .hero-note {
    font-size: 15px;
  }

  .hero-search {
    width: 100%;
    flex-direction: column;
  }

  .hero-search .ant-btn {
    width: 100%;
    height: 50px;
  }

  .filter-toolbar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .home-job-grid { grid-template-columns: 1fr; }

  .filter-heading { align-items: flex-start; flex-direction: column; gap: 6px; }

  .search-detail-card { padding: 22px 20px; }
  .search-detail-header { flex-direction: column; }
  .search-detail-actions { width: 100%; justify-items: stretch; }
  .search-detail-actions .ant-btn { width: 100%; }
  .detail-action-row { width: 100%; }
  .detail-action-row .ant-btn { flex: 1; }

  .post-card {
    grid-template-columns: 1fr;
    gap: 12px;
    padding: 15px;
  }

  .post-title {
    font-size: 18px;
  }

  .post-title-row {
    align-items: flex-start;
    gap: 8px 12px;
  }

  .post-card-actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .post-apply {
    width: 100%;
  }

}
.recruit-page { color: #5d4636; }
.recruit-page .filter-panel, .recruit-page .post-card, .recruit-page .search-detail-card, .recruit-page .recruit-tabs :deep(.ant-tabs-content-holder) { border-color: #f4e5d7; background: #fff; box-shadow: 0 8px 22px rgb(183 92 24 / 4%); }
.recruit-page .section-kicker, .recruit-page .search-detail-section h3 { color: #d86616; }
.recruit-page .post-apply, .recruit-page .search-detail-actions .ant-btn-primary, .recruit-page .show-more-posts { border-color: #efc89f; background: linear-gradient(135deg, #f8d8b2, #efb77d); color: #8a4a21; box-shadow: 0 5px 12px rgb(183 92 24 / 8%); }
.recruit-page .post-apply:hover, .recruit-page .search-detail-actions .ant-btn-primary:hover, .recruit-page .show-more-posts:hover { border-color: #e5ae77; background: linear-gradient(135deg, #efb77d, #e99d5b); }
.recruit-page .post-tags span, .recruit-page .search-post-card.selected { background: #fff7ed; }
.recruit-page .recruit-tabs :deep(.ant-tabs-nav) { display: none; }
.home-jobs-heading { display: grid; width: 100%; justify-items: center; padding: 10px 75px 10px; border-bottom: 1px solid #f2e7dc; }
.home-jobs-heading h2 { width: 100%; justify-self: stretch; margin-bottom: 22px; text-align: center; font-size: 30px; font-weight: 800; letter-spacing: -.02em; }
.home-job-categories { display: flex; justify-content: center; gap: 34px; width: 100%; }
.recruit-page .home-recruit-tabs :deep(.ant-tabs-content-holder) { background: transparent; box-shadow: none; }
.home-job-grid { gap: 16px; }
.home-job-grid .post-card { min-height: 232px; border: 1px solid #eee4da; border-radius: 10px; background: #f4f6f8; box-shadow: 0 4px 14px rgb(65 43 26 / 4%); transition: transform 160ms ease, box-shadow 160ms ease, border-color 160ms ease; }
.home-job-grid .post-card:hover { border-color: #efcba8; box-shadow: 0 12px 24px rgb(183 92 24 / 10%); transform: translateY(-2px); }
.home-job-grid .post-title { color: #2f241e; font-size: 17px; font-weight: 700; }
.home-job-grid .post-title-row { justify-content: space-between; }
.home-job-grid .post-salary-top { display: block; color: #f05a4f; font-size: 16px; }
.home-job-grid .post-card-main { display: grid; align-content: start; gap: 14px; }
.home-job-grid .post-tags { display: flex; gap: 8px; margin-top: 0; }
.home-job-grid .post-tags span { padding: 4px 9px; border-radius: 3px; background: #fff; color: #806956; font-size: 12px; }
.home-job-grid .post-title:hover { color: #d86616; }
.home-job-grid .post-location { color: #8d7a6c; font-size: 12px; }
.home-job-grid .post-company-row { color: #6b5748; font-size: 13px; }
.home-job-grid .post-type-tag, .home-job-grid .post-tags span { background: #faf5f0; color: #806956; }
.home-job-grid .post-excerpt { color: #9a8879; font-size: 12px; }
.home-job-grid .post-card-actions { margin-inline: -22px; border-top: 1px solid #eee4da; background: #f4f6f8; }
.home-job-grid .post-salary { color: #e45f45; font-size: 18px; }
.home-job-grid .favorite-button { color: #a18e7d; }
.post-company-meta, .company-meta-detail { display: inline-flex; align-items: center; color: #6f6258; font-size: 12px; }
.post-company-meta { gap: 8px; }
.company-logo { display: grid; width: 24px; height: 24px; place-items: center; overflow: hidden; border-radius: 3px; background: #fff; color: #d86616; font-weight: 700; }
.company-logo img { width: 100%; height: 100%; object-fit: contain; }
.company-meta-detail { margin-left: auto; }
.company-meta-detail i { margin: 0 7px; color: #d7c8bb; font-style: normal; }
.show-more-posts { width: 210px; height: 46px; border: 1px solid #f0c9a2; border-radius: 8px; background: linear-gradient(135deg, #fff7ed, #ffe9cf); color: #cc6a2a; font-size: 15px; }
.show-more-posts:hover, .show-more-posts:focus-visible { border-color: #eeb37b; background: linear-gradient(135deg, #ffedda, #ffdfb9); color: #b9581d; }
:global(.profile-setup-modal .ant-modal-content), :global(.profile-setup-modal .ant-modal-body) { background: #fff; }
:global(.profile-setup-modal .ant-modal-content) { padding: 0; }
:global(.profile-setup-modal .ant-modal-header) { background: #fff; }
:global(.profile-setup-modal .ant-modal-close), :global(.profile-setup-modal .ant-modal-close .anticon) { color: #000 !important; }
:global(.profile-setup-modal .ant-modal-close:hover), :global(.profile-setup-modal .ant-modal-close:focus-visible) { color: #000 !important; }

.recruit-page {
  --recruit-ink: #17343d;
  --recruit-muted: #6a7d82;
  --recruit-line: #dce8e7;
  --recruit-accent: #c96632;
  --recruit-accent-soft: #edf7f5;
  gap: 18px;
  padding-bottom: 24px;
  background: radial-gradient(circle at 80% 3%, rgb(211 239 232 / 62%), transparent 26rem);
}

.recruit-subnav {
  min-height: 60px;
  border-color: rgb(220 232 231 / 90%);
  background: rgb(255 255 255 / 90%);
  box-shadow: 0 8px 24px rgb(23 52 61 / 8%);
}

.recruit-subnav button { height: 58px; color: #587178; font-weight: 600; }
.recruit-subnav button:hover,
.recruit-subnav button.active { color: var(--recruit-ink); }
.recruit-subnav button.active::after { background: linear-gradient(90deg, #c96632, #e6a05b); }
.recruit-nav-account button { color: #587178; }
.recruit-nav-account button:hover { color: var(--recruit-accent); }

.recruit-hero {
  min-height: 290px;
  padding: 48px 56px 34px;
  border: 0;
  border-radius: 24px;
  overflow: hidden;
  background-color: #163a45;
  background-image:
    linear-gradient(120deg, rgb(19 55 65 / 98%), rgb(26 78 83 / 94%) 62%, rgb(201 102 50 / 88%)),
    repeating-linear-gradient(90deg, rgb(255 255 255 / 5%) 0 1px, transparent 1px 42px),
    repeating-linear-gradient(0deg, rgb(255 255 255 / 4%) 0 1px, transparent 1px 42px);
  box-shadow: 0 22px 44px rgb(23 52 61 / 18%);
}

.hero-copy h1 { max-width: 680px; color: #fff; font-size: clamp(34px, 4vw, 52px); letter-spacing: -.045em; }
.eyebrow { color: #8de0d1; font-size: 14px; font-weight: 700; letter-spacing: .16em; }
.hero-note { color: rgb(255 255 255 / 72%); }
.hero-visual { opacity: .88; }
.hero-orbit { border-color: rgb(141 224 209 / 42%); }
.hero-profile-card,
.hero-role-card { border-color: rgb(255 255 255 / 20%); background: rgb(255 255 255 / 14%); box-shadow: 0 16px 30px rgb(0 0 0 / 18%); backdrop-filter: blur(10px); }
.hero-profile-card b, .hero-role-card b { color: #fff; }
.hero-profile-card small, .hero-role-card span { color: rgb(255 255 255 / 68%); }
.hero-avatar { background: #c96632; }
.hero-profile-card i { color: #8de0d1; }
.hero-search .ant-input-affix-wrapper { border: 0; box-shadow: 0 8px 20px rgb(0 0 0 / 12%); }
.hero-search .ant-btn { border-color: #c96632; background: #c96632; box-shadow: 0 8px 18px rgb(0 0 0 / 16%); }
.hero-search .ant-btn:hover { border-color: #df7c43; background: #df7c43; }
.quick-searches { color: rgb(255 255 255 / 68%); }
.quick-searches button { color: #dff8f3; background: rgb(255 255 255 / 12%); }
.quick-searches button:hover, .quick-searches button:focus-visible { color: #17343d; background: #8de0d1; }

.recruit-banner-grid { gap: 18px; }
.banner-card { min-height: 204px; border-radius: 20px; box-shadow: 0 14px 30px rgb(23 52 61 / 9%); }
.banner-primary { background: linear-gradient(120deg, #1b5560, #287c7c); }
.banner-secondary { background: linear-gradient(120deg, #fff9ef, #f4e6d4); }
.banner-card h2 { letter-spacing: -.035em; }
.banner-card p { font-size: 15px; line-height: 1.6; }
.banner-card button { transition: transform .18s ease, background-color .18s ease; }
.banner-card button:hover { transform: translateY(-2px); }

.home-jobs-heading { padding: 18px 0 20px; border-bottom-color: var(--recruit-line); }
.home-jobs-heading h2 { color: var(--recruit-ink); font-size: 28px; }
.home-job-categories { gap: 10px; }
.home-job-categories button { border: 1px solid transparent; color: #6a7d82; background: transparent; }
.home-job-categories button:hover, .home-job-categories button.active { border-color: #b9e2da; color: #16756f; background: #edf7f5; }

.home-job-grid { gap: 18px; }
.home-job-grid .post-card { min-height: 214px; border: 1px solid var(--recruit-line); border-radius: 16px; background: #fff; box-shadow: 0 8px 20px rgb(23 52 61 / 6%); }
.home-job-grid .post-card:hover { border-color: #9ed4cc; box-shadow: 0 16px 30px rgb(23 52 61 / 11%); transform: translateY(-4px); }
.home-job-grid .post-title { color: var(--recruit-ink); font-size: 18px; }
.home-job-grid .post-salary-top { color: var(--recruit-accent); font-weight: 800; }
.home-job-grid .post-tags span { border-radius: 999px; color: #5c777b; background: #f1f7f5; }
.home-job-grid .post-card-actions { border-top-color: var(--recruit-line); background: #f7fbfa; }
.company-meta-detail { color: #587178; }

.service-section { padding-top: 24px; }
.service-heading h2 { color: var(--recruit-ink); }
.service-grid article { border-color: var(--recruit-line); border-radius: 16px; box-shadow: 0 6px 18px rgb(23 52 61 / 5%); }
.service-grid article:hover { border-color: #9ed4cc; box-shadow: 0 14px 28px rgb(23 52 61 / 10%); }
.service-icon { color: #16756f; background: #edf7f5; }
.service-grid h3 { color: var(--recruit-ink); }
.service-grid a { color: var(--recruit-accent); font-weight: 650; }

.filter-panel,
.search-result-list,
.search-detail-card { border-color: var(--recruit-line); border-radius: 16px; box-shadow: 0 8px 22px rgb(23 52 61 / 6%); }
.filter-heading { border-bottom-color: var(--recruit-line); }
.section-kicker { color: #16756f; }
.filter-heading strong, .search-result-heading strong, .search-detail-header h2 { color: var(--recruit-ink); }
.search-post-card:hover, .search-post-card.selected { background: #edf7f5; }
.search-post-topline b, .search-detail-actions strong { color: var(--recruit-accent); }
.search-detail-actions .ant-btn-primary { border-color: var(--recruit-accent); background: var(--recruit-accent); }
.search-detail-actions .ant-btn-primary:hover { border-color: #df7c43; background: #df7c43; }

@media (max-width: 640px) {
  .recruit-hero { min-height: 0; padding: 34px 22px 26px; border-radius: 18px; }
  .hero-copy h1 { font-size: 34px; }
  .recruit-banner-grid { gap: 12px; }
  .banner-card { border-radius: 16px; }
}
</style>
