<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';
import { Pagination } from 'ant-design-vue';

import {
  getPortalRecruitPostPage,
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

function openApply(post: HrmPortalRecruitApi.Post) {
  applyPost.value = post;
  applyModalOpen.value = true;
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

onMounted(async () => {
  await loadPosts();
});
</script>

<template>
  <div class="portal-page recruit-page">
    <section class="recruit-hero">
      <div class="hero-copy">
        <p class="eyebrow">人才服务 · 职业机会</p>
        <h1>发现适合你的下一份工作</h1>
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
    </section>

    <a-tabs
      class="recruit-tabs"
    >
      <a-tab-pane key="posts" tab="岗位机会">
        <section class="filter-panel">
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
        <div v-else class="post-list">
          <article
            v-for="post in displayPosts"
            :key="post.id"
            class="post-card"
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
              <p class="post-excerpt">{{ getDescriptionExcerpt(post.description) }}</p>
            </div>
            <div class="post-card-actions">
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
        <Pagination
          v-if="postTotal"
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

.recruit-hero {
  display: grid;
  width: min(100%, 1280px);
  box-sizing: border-box;
  margin-inline: auto;
  gap: 24px;
  padding: 36px 42px 38px;
  border: 1px solid #dbeafe;
  background: #eff6ff;
}

.hero-copy {
  max-width: 760px;
}

.hero-copy h1,
.hero-copy p {
  margin: 0;
}

.hero-copy h1 {
  margin-bottom: 8px;
  color: #0f172a;
  font-size: 38px;
  line-height: 1.18;
}

.hero-copy > p:last-child {
  color: #475569;
  font-size: 17px;
  line-height: 1.7;
}

.eyebrow {
  margin: 0 0 8px;
  color: #2563eb;
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
  border-color: #bfdbfe;
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
  font-size: 16px;
}

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
  padding: 16px 18px;
  border: 1px solid #e5e7eb;
  background: #fff;
}

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
  padding: 22px 24px;
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
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: flex-end;
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
    padding: 24px 20px;
  }

  .hero-copy h1 {
    font-size: 28px;
  }

  .hero-copy > p:last-child {
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
    justify-content: flex-start;
  }

  .post-apply {
    width: 100%;
  }

}
</style>
