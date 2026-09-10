<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';
import { Pagination } from 'ant-design-vue';

import {
  getPortalRecruitPostPage,
  getPortalRecruitPostTypeList,
  type HrmPortalRecruitApi,
} from '#/api/hrm/portal/recruit';
import { AreaCascader } from '#/components/area';
import RecruitApplyModal from './components/apply-modal.vue';

const loading = ref(false);
const posts = ref<HrmPortalRecruitApi.Post[]>([]);
const postTotal = ref(0);
const applyModalOpen = ref(false);
const applyPost = ref<HrmPortalRecruitApi.Post>();
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

const showAllPosts = computed(() => route.query.view === 'list');
const previewPosts = computed(() => displayPosts.value.slice(0, 9));

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

function getDescriptionExcerpt(description?: string) {
  const excerpt = (description || '岗位职责与工作内容请以职位详情为准。')
    .replace(/\s+/g, ' ')
    .trim();
  return excerpt.length > 110 ? `${excerpt.slice(0, 110)}…` : excerpt;
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

function openApply(post: HrmPortalRecruitApi.Post) {
  applyPost.value = post;
  applyModalOpen.value = true;
}

function selectPost(post: HrmPortalRecruitApi.Post) {
  selectedPost.value = post;
}

function handleSearch() {
  postPage.postName = keyword.value.trim();
  postPage.pageNo = 1;
  void loadPosts();
}

function handleJobNatureChange(value?: number | 'all') {
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
  router.push({ name: 'PortalRecruit', query: { view: 'list' } });
}

onMounted(async () => {
  await Promise.all([
    loadPosts(),
    getPortalRecruitPostTypeList()
      .then((result) => {
        postTypes.value = result || [];
      })
      .catch(() => {
        postTypes.value = [];
      }),
  ]);
});
</script>

<template>
  <div class="portal-page recruit-page">
    <section class="recruit-hero">
      <div class="hero-copy">
        <p class="eyebrow">天信人才服务 · 职业机会</p>
        <h1>发现适合你的下一份工作</h1>
        <p class="hero-note">连接防腐保温行业优质企业，让专业经验找到更好的去处。</p>
      </div>
      <div class="hero-visual" aria-hidden="true">
        <div class="hero-orbit hero-orbit-one"></div>
        <div class="hero-orbit hero-orbit-two"></div>
        <div class="hero-profile-card">
          <span class="hero-avatar">职</span>
          <span><b>专业人才</b><small>正在寻找新机会</small></span>
          <i>↗</i>
        </div>
        <div class="hero-role-card"><span>匹配职位</span><b>项目管理 · 延安</b></div>
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

    <a-tabs
      class="recruit-tabs"
    >
      <a-tab-pane key="posts" tab="岗位机会">
        <section class="filter-panel">
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
        <div v-else-if="!displayPosts.length" class="portal-empty">
          <span class="portal-empty-icon">
            <IconifyIcon icon="lucide:search-x" />
          </span>
          <strong>暂时没有符合条件的职位</strong>
          <p>试试调整工作性质、职位类型或搜索关键词</p>
        </div>
        <div v-else-if="!isSearchMode && !showAllPosts" class="home-job-grid">
          <article
            v-for="post in previewPosts"
            :key="post.id"
            class="post-card"
            @click="selectPost(post)"
          >
            <div class="post-card-main">
              <div class="post-title-row">
                <button
                  class="post-title"
                  type="button"
                  @click="router.push({ name: 'PortalRecruitDetail', query: { id: post.id } })"
                >
                  {{ post.postName }}
                </button>
                <span class="post-location">
                  <IconifyIcon icon="lucide:map-pin" />
                  <span>{{ formatLocation(post) }}</span>
                </span>
              </div>
              <div class="post-company-row">
                <span>{{ post.companyShortName || post.companyName || '行业企业' }}</span>
                <span v-if="post.postTypeName" class="post-type-tag">{{ post.postTypeName }}</span>
              </div>
              <p class="post-excerpt">{{ getDescriptionExcerpt(post.description) }}</p>
              <div class="post-tags">
                <span>{{ post.jobNature === 2 ? '实习' : post.jobNature === 3 ? '兼职' : '全职' }}</span>
                <span>{{ formatEducation(post.educationRequire) }}</span>
                <span>{{ formatWorkTime(post.workTime) }}</span>
              </div>
            </div>
            <div class="post-card-actions">
              <strong class="post-salary">{{ formatSalary(post) }}</strong>
              <a-button
                class="post-apply"
                type="primary"
                @click="openApply(post)"
              >
                立即投递
              </a-button>
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
                <a-button type="primary" @click="openApply(selectedPost)">立即投递</a-button>
              </div>
            </div>
            <section class="search-detail-section">
              <h3>职位描述</h3>
              <p>{{ getDescriptionExcerpt(selectedPost.description) }}</p>
              <p v-for="(line, index) in (selectedPost.description || '').split(/\r?\n/).slice(0, 8)" :key="index">
                {{ line }}
              </p>
            </section>
            <a-button type="link" @click="router.push({ name: 'PortalRecruitDetail', query: { id: selectedPost.id } })">
              查看完整职位详情 →
            </a-button>
          </article>
        </div>
        <a-button
          v-if="!isSearchMode && !showAllPosts && postTotal > previewPosts.length"
          class="show-more-posts"
          type="default"
          @click="showMorePosts"
        >
          查看更多
        </a-button>
        <Pagination
          v-if="postTotal && (isSearchMode || showAllPosts)"
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

    <RecruitApplyModal
      v-model:open="applyModalOpen"
      :post="applyPost"
    />

  </div>
</template>

<style scoped>
.portal-page {
  display: grid;
  gap: 20px;
}

.recruit-page {
  --recruit-ink: #17231f;
  --recruit-muted: #71807a;
  --recruit-line: #e8eeeb;
  --recruit-accent: #08b6ad;
  --recruit-accent-soft: #e9f8f6;
}

.recruit-hero {
  position: relative;
  display: grid;
  width: min(100%, 1280px);
  box-sizing: border-box;
  margin-inline: auto;
  gap: 24px;
  padding: 42px 52px 28px;
  border: 1px solid #dcebe8;
  border-radius: 16px;
  background: linear-gradient(135deg, #eff9f7 0%, #f8fbfa 62%, #e4f4f0 100%);
  box-shadow: 0 18px 46px rgb(35 105 185 / 8%);
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
.hero-orbit { position: absolute; border: 1px solid rgb(8 182 173 / 23%); border-radius: 50%; }
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
  box-shadow: 0 12px 28px rgb(46 112 101 / 12%);
}
.hero-profile-card { top: 34px; left: 22px; }
.hero-profile-card > span:not(.hero-avatar) { display: grid; gap: 3px; }
.hero-profile-card b { color: #19342e; font-size: 13px; }
.hero-profile-card small { color: #81918b; font-size: 11px; }
.hero-profile-card i { color: var(--recruit-accent); font-size: 18px; font-style: normal; }
.hero-avatar { display: grid; width: 28px; height: 28px; place-items: center; border-radius: 9px; color: #fff; background: var(--recruit-accent); font-size: 13px; font-weight: 700; }
.hero-role-card { right: 2px; bottom: 4px; display: grid; gap: 3px; padding: 9px 12px; }
.hero-role-card span { color: #87968f; font-size: 10px; }
.hero-role-card b { color: #355a50; font-size: 12px; }

.hero-copy h1,
.hero-copy p {
  margin: 0;
}

.hero-copy h1 {
  margin-bottom: 8px;
  color: #0f172a;
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
  font-size: 16px;
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
  border-color: #b9e1db;
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
  color: #087f77;
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

.home-job-grid .post-card {
  grid-template-columns: 1fr;
  gap: 18px;
  min-height: 204px;
  padding: 24px 28px 0;
  overflow: hidden;
  cursor: pointer;
}

.home-job-grid .post-card-main { min-width: 0; }
.home-job-grid .post-card-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-inline: -28px;
  padding: 13px 28px;
  background: #f5fbfa;
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
.search-detail-section { padding: 28px 0 12px; color: #596862; font-size: 15px; line-height: 1.9; }
.search-detail-section h3 { margin: 0 0 14px; color: var(--recruit-ink); font-size: 18px; }
.search-detail-section p { margin: 0 0 7px; }

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
</style>
