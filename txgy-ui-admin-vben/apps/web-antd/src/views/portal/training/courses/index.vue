<script lang="ts" setup>
import type { TrainingLearnerApi } from '../learning/api';

import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import defaultCourseCover from '#/assets/imgs/academy/safety-course.png';

import {
  enrollSelf,
  getPortalCourseCategoryTree,
  getPortalCoursePage,
} from '../learning/api';

defineOptions({ name: 'PortalTrainingCourses' });
const props = defineProps<{ subModule?: string }>();

const route = useRoute();
const router = useRouter();
const categories = ref<TrainingLearnerApi.PortalCourseCategory[]>([]);
const courses = ref<TrainingLearnerApi.PortalCourse[]>([]);
const directionId = ref<number>();
const topicId = ref<number>();
const detailId = ref<number>();
const keyword = ref('');
const sortMode = ref<'comprehensive' | 'hours' | 'latest'>('comprehensive');
const pageNo = ref(1);
const pageSize = 12;
const total = ref(0);
const loading = ref(false);
const categoryLoading = ref(false);
const categoryError = ref('');
const errorMessage = ref('');
const actionMessage = ref('');
const enrollingId = ref<number>();
let searchTimer: ReturnType<typeof setTimeout> | undefined;

const activeDirections = computed(() =>
  categories.value.filter((item) => item.status !== 'inactive'),
);
const currentTopics = computed(() =>
  activeDirections.value
    .find((item) => item.id === directionId.value)
    ?.children?.filter((item) => item.status !== 'inactive') ?? [],
);
const currentDetails = computed(() =>
  currentTopics.value
    .find((item) => item.id === topicId.value)
    ?.children?.filter((item) => item.status !== 'inactive') ?? [],
);

const categoryMap = computed(() => {
  const map = new Map<number, TrainingLearnerApi.PortalCourseCategory>();
  const visit = (items: TrainingLearnerApi.PortalCourseCategory[]) => {
    items.forEach((item) => {
      if (item.id) map.set(item.id, item);
      visit(item.children ?? []);
    });
  };
  visit(categories.value);
  return map;
});

function courseTopicName(categoryId?: number) {
  if (!categoryId) return '未分类';
  const leaf = categoryMap.value.get(categoryId);
  if (!leaf) return '未分类';
  if (leaf.level === 2) return leaf.name || '未分类';
  return categoryMap.value.get(leaf.parentId || 0)?.name || leaf.name || '未分类';
}

function presetCode() {
  if (props.subModule === 'safety' || props.subModule === 'beginner') return 'A';
  if (props.subModule === 'manual') return 'G';
  const queryCode = String(route.query.category || '');
  return queryCode || undefined;
}

function presetTopicCode() {
  if (props.subModule === 'beginner') return 'A01';
  if (props.subModule === 'manual') return 'G01';
  return String(route.query.group || '') || undefined;
}

function applyPreset() {
  const direction = activeDirections.value.find((item) => item.code === presetCode());
  directionId.value = direction?.id;
  topicId.value = direction?.children?.find((item) => item.code === presetTopicCode())?.id;
  detailId.value = undefined;
}

async function loadCategories() {
  categoryLoading.value = true;
  categoryError.value = '';
  try {
    categories.value = (await getPortalCourseCategoryTree()) ?? [];
    applyPreset();
    return true;
  } catch {
    categories.value = [];
    directionId.value = undefined;
    topicId.value = undefined;
    detailId.value = undefined;
    categoryError.value = '课程分类加载失败，请重试';
    return false;
  } finally {
    categoryLoading.value = false;
  }
}

async function reloadCategories() {
  const loaded = await loadCategories();
  if (loaded) await loadCourses();
}

async function loadCourses() {
  loading.value = true;
  errorMessage.value = '';
  try {
    const params: Parameters<typeof getPortalCoursePage>[0] = {
      pageNo: pageNo.value,
      pageSize,
    };
    const selectedCategoryId = detailId.value ?? topicId.value ?? directionId.value;
    if (selectedCategoryId) params.categoryId = selectedCategoryId;
    if (keyword.value.trim()) params.keyword = keyword.value.trim();
    if (sortMode.value === 'latest') params.sortField = 'latest';
    if (sortMode.value === 'hours') params.sortField = 'credit';
    if (params.sortField) params.sortOrder = 'desc';
    const page = await getPortalCoursePage(params);
    courses.value = page?.list ?? [];
    total.value = Number(page?.total ?? 0);
  } catch {
    courses.value = [];
    total.value = 0;
    errorMessage.value = '课程加载失败，请稍后重试';
  } finally {
    loading.value = false;
  }
}

async function selectDirection(id?: number) {
  directionId.value = id;
  topicId.value = undefined;
  detailId.value = undefined;
  pageNo.value = 1;
  await loadCourses();
}

async function selectTopic(id?: number) {
  topicId.value = id;
  detailId.value = undefined;
  pageNo.value = 1;
  await loadCourses();
}

async function selectDetail(id?: number) {
  detailId.value = id;
  pageNo.value = 1;
  await loadCourses();
}

async function selectSort(mode: typeof sortMode.value) {
  sortMode.value = mode;
  pageNo.value = 1;
  await loadCourses();
}

async function changePage(next: number) {
  pageNo.value = next;
  await loadCourses();
}

function clearFilters() {
  directionId.value = undefined;
  topicId.value = undefined;
  detailId.value = undefined;
  keyword.value = '';
  pageNo.value = 1;
  loadCourses();
}

async function startLearning(item: TrainingLearnerApi.PortalCourse) {
  if (!item.id || enrollingId.value) return;
  enrollingId.value = item.id;
  actionMessage.value = '';
  try {
    await enrollSelf(item.id);
    await router.push('/portal/training/learning');
  } catch {
    actionMessage.value = `《${item.title || '该课程'}》选学失败，请稍后重试。`;
  } finally {
    enrollingId.value = undefined;
  }
}

watch(keyword, () => {
  if (searchTimer) clearTimeout(searchTimer);
  searchTimer = setTimeout(() => {
    pageNo.value = 1;
    loadCourses();
  }, 400);
});

watch(() => [props.subModule, route.query.category, route.query.group], async () => {
  applyPreset();
  pageNo.value = 1;
  await loadCourses();
});

onMounted(async () => {
  const loaded = await loadCategories();
  if (loaded) await loadCourses();
});
</script>

<template>
  <div class="courses">
    <div v-if="categoryError" class="load-error category-error">
      <span>{{ categoryError }}</span>
      <button type="button" @click="reloadCategories">重新加载分类</button>
    </div>
    <section v-else class="filters-wrap">
      <div class="filters">
        <label>专业方向</label>
        <button
          type="button"
          :class="{ chosen: directionId === undefined }"
          @click="selectDirection()"
        >
          全部
        </button>
        <button
          v-for="item in activeDirections"
          :key="item.id"
          type="button"
          :class="{ chosen: directionId === item.id }"
          @click="selectDirection(item.id)"
        >
          {{ item.name }}
        </button>
        <span v-if="categoryLoading" class="filter-loading">正在加载分类…</span>
      </div>
      <div v-if="directionId !== undefined" class="filters">
        <label>课程专题</label>
        <button
          type="button"
          :class="{ chosen: topicId === undefined }"
          @click="selectTopic()"
        >
          全部
        </button>
        <button
          v-for="item in currentTopics"
          :key="item.id"
          type="button"
          :class="{ chosen: topicId === item.id }"
          @click="selectTopic(item.id)"
        >
          {{ item.name }}
        </button>
      </div>
      <div v-if="topicId !== undefined && currentDetails.length" class="filters">
        <label>细分课程</label>
        <button
          type="button"
          :class="{ chosen: detailId === undefined }"
          @click="selectDetail()"
        >
          全部
        </button>
        <button
          v-for="item in currentDetails"
          :key="item.id"
          type="button"
          :class="{ chosen: detailId === item.id }"
          @click="selectDetail(item.id)"
        >
          {{ item.name }}
        </button>
      </div>
      <div class="course-toolbar">
        <input v-model="keyword" placeholder="搜索课程名称 / 编号 / 讲师" />
        <span class="course-total">共 {{ total }} 门课程</span>
        <div class="sort-tabs" aria-label="课程排序">
          <button type="button" :class="{ chosen: sortMode === 'comprehensive' }" @click="selectSort('comprehensive')">综合</button>
          <button type="button" :class="{ chosen: sortMode === 'latest' }" @click="selectSort('latest')">最新</button>
          <button type="button" :class="{ chosen: sortMode === 'hours' }" @click="selectSort('hours')">学时</button>
        </div>
      </div>
    </section>

    <p v-if="actionMessage" class="message">{{ actionMessage }}</p>
    <div v-if="!categoryError && errorMessage" class="load-error">
      <span>{{ errorMessage }}</span>
      <button type="button" @click="loadCourses">重新加载</button>
    </div>

    <div v-if="!categoryError && loading" class="course-grid" aria-label="课程加载中">
      <div v-for="index in 8" :key="index" class="course-skeleton"><i></i><b></b><span></span><span></span></div>
    </div>
    <div v-else-if="!categoryError && !errorMessage && courses.length" class="course-grid">
      <article v-for="item in courses" :key="item.id" class="course-card">
        <div class="course-cover">
          <img :src="item.coverUrl || defaultCourseCover" :alt="item.title || '课程封面'" />
          <span>{{ item.isRequired ? '必修' : '自主学习' }}</span>
        </div>
        <div class="course-body">
          <h3>{{ item.title }}</h3>
          <p>{{ item.code || '暂无编号' }} · {{ courseTopicName(item.categoryId) }}</p>
          <div class="course-meta"><span>{{ item.credit ?? 0 }} 学时</span><span>{{ item.lecturer || '讲师待配置' }}</span></div>
          <footer>
            <span>{{ item.isRequired ? '必修课程' : '自主学习' }}</span>
            <button
              type="button"
              :disabled="Boolean(enrollingId)"
              @click="startLearning(item)"
            >
              {{ enrollingId === item.id ? '正在选学…' : '开始学习' }}
            </button>
          </footer>
        </div>
      </article>
    </div>
    <div v-else-if="!categoryError && !errorMessage" class="course-empty">
      <strong>暂无符合条件的课程</strong>
      <span>可以调整专业方向、课程专题或搜索关键词</span>
      <button type="button" @click="clearFilters">清除筛选</button>
    </div>

    <nav v-if="!categoryError && !loading && total > pageSize" class="pagination" aria-label="课程分页">
      <button type="button" :disabled="pageNo <= 1" @click="changePage(pageNo - 1)">上一页</button>
      <span>第 {{ pageNo }} / {{ Math.ceil(total / pageSize) }} 页</span>
      <button type="button" :disabled="pageNo >= Math.ceil(total / pageSize)" @click="changePage(pageNo + 1)">下一页</button>
    </nav>
  </div>
</template>

<style scoped>
.filters-wrap {
  padding: 2px 0 16px;
  border-bottom: 1px solid #dfe3eb;
}

.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 5px 10px;
  align-items: center;
  margin: 13px 0;
}

.filters label {
  width: 72px;
  color: #303b4d;
  font-size: 13px;
  font-weight: 600;
}

.filters button {
  padding: 6px 9px;
  color: #667085;
  cursor: pointer;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 5px;
  transition: color 0.16s, background-color 0.16s;
}

.filters button:hover {
  color: #f06b22;
  background: #fff8f2;
}

.filters button.chosen {
  color: #e85f17;
  font-weight: 600;
  background: #fff3e9;
  border-color: transparent;
}

input {
  width: 300px;
  height: 36px;
  padding: 0 12px;
  color: #263750;
  background: #fff;
  border: 1px solid #d9e0ec;
  border-radius: 4px;
  outline: none;
}

input:focus {
  border-color: #6c98f5;
  box-shadow: 0 0 0 2px rgb(47 107 255 / 9%);
}

.course-toolbar {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: flex-end;
  margin-top: 16px;
}

.course-toolbar input {
  margin-right: auto;
}

.course-total {
  color: #8792a5;
  font-size: 12px;
}

.sort-tabs {
  display: flex;
  align-items: center;
}

.sort-tabs button {
  padding: 5px 12px;
  color: #68768c;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-left: 1px solid #e3e7ee;
}

.sort-tabs button:first-child {
  border-left: 0;
}

.sort-tabs button.chosen {
  color: #245fec;
  font-weight: 600;
}

.message {
  display: inline-block;
  padding: 6px 10px;
  margin: 16px 0 0;
  color: #245fec;
  background: rgb(234 242 255 / 74%);
}

.filter-loading { color: #98a2b3; font-size: 12px; }
.category-error { margin-top: 12px; }

.load-error,
.course-empty {
  display: flex;
  min-height: 180px;
  flex-direction: column;
  gap: 10px;
  align-items: center;
  justify-content: center;
  color: #7b8799;
}

.load-error button,
.course-empty button,
.pagination button {
  padding: 7px 14px;
  color: #e85f17;
  cursor: pointer;
  background: #fff7f0;
  border: 1px solid #f2d1bc;
  border-radius: 5px;
}

.course-empty strong { color: #354155; font-size: 15px; }
.course-empty span { font-size: 13px; }

.course-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
  padding: 20px 0;
}

.course-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 286px;
  overflow: hidden;
  background: #fff;
  border: 1px solid #dbe5f4;
  border-radius: 10px;
  transition: transform 0.18s, box-shadow 0.18s;
}

.course-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 12px 24px rgb(31 72 135 / 14%);
}

.course-skeleton {
  min-height: 286px;
  overflow: hidden;
  background: #fff;
  border: 1px solid #edf0f4;
  border-radius: 10px;
}

.course-skeleton i,
.course-skeleton b,
.course-skeleton span {
  display: block;
  background: linear-gradient(90deg, #f1f3f6 25%, #f8f9fb 50%, #f1f3f6 75%);
  background-size: 200% 100%;
  animation: skeleton-loading 1.2s infinite;
}

.course-skeleton i { height: 118px; }
.course-skeleton b { width: 70%; height: 16px; margin: 18px 14px 12px; }
.course-skeleton span { width: 55%; height: 11px; margin: 10px 14px; }

@keyframes skeleton-loading { to { background-position: -200% 0; } }

.course-cover {
  position: relative;
  height: 118px;
  overflow: hidden;
  color: #fff;
  background: linear-gradient(135deg, #304b66, #567991);
}

.course-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.course-cover > span {
  position: absolute;
  top: 10px;
  left: 10px;
  padding: 3px 7px;
  color: #fff;
  font-size: 11px;
  background: rgb(22 34 50 / 70%);
  border-radius: 3px;
}

.cover-fallback {
  display: flex;
  height: 100%;
  flex-direction: column;
  justify-content: flex-end;
  padding: 18px;
  background:
    linear-gradient(125deg, rgb(13 37 58 / 25%), transparent 58%),
    repeating-linear-gradient(135deg, transparent 0 25px, rgb(255 255 255 / 5%) 25px 26px);
}

.cover-fallback small {
  margin-bottom: 5px;
  color: rgb(255 255 255 / 66%);
  font-size: 11px;
  letter-spacing: 0.12em;
}

.cover-fallback strong {
  font-size: 16px;
  font-weight: 500;
}

.course-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  padding: 14px;
}

.course-body h3 {
  display: -webkit-box;
  min-height: 44px;
  margin: 0;
  color: #182742;
  font-size: 15px;
  line-height: 22px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.course-body p,
.course-body small {
  margin: 8px 0 0;
  color: #78869c;
  font-size: 12px;
}

.course-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
  color: #66768d;
  font-size: 12px;
}

.course-meta span:last-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.course-body footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: auto;
  padding-top: 14px;
}

.course-body footer span {
  color: #4d83ed;
  font-size: 12px;
}

.course-body footer button {
  padding: 6px 9px;
  color: #2865ec;
  cursor: pointer;
  background: #edf4ff;
  border: 0;
  border-radius: 4px;
}

.pagination {
  display: flex;
  gap: 14px;
  align-items: center;
  justify-content: center;
  padding: 4px 0 24px;
  color: #68768c;
  font-size: 13px;
}

.pagination button:disabled {
  color: #b7bec9;
  cursor: not-allowed;
  background: #f7f8fa;
  border-color: #e6e9ee;
}

@media (max-width: 1450px) {
  .course-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .course-toolbar {
    flex-wrap: wrap;
  }

  .course-toolbar input {
    width: 100%;
  }

  .course-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
