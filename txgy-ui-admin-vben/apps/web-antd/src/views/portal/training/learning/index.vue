<script lang="ts" setup>
import type { TrainingLearnerApi } from './api';

import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { Button, Modal, Progress, Tag } from 'ant-design-vue';

import { getCourseDetail, getMyCourses } from './api';

defineOptions({ name: 'PortalTrainingLearning' });

const router = useRouter();

const loading = ref(false);
const group = ref<TrainingLearnerApi.MyCoursesGroup>({});
const activeTab = ref('learning');

function statusColor(status?: number) {
  if (status === 2) return 'green';
  if (status === 1) return 'orange';
  return 'default';
}
function statusText(status?: number) {
  if (status === 2) return '已完成';
  if (status === 1) return '学习中';
  return '待学习';
}

async function load() {
  loading.value = true;
  try {
    group.value = (await getMyCourses()) ?? {};
  } finally {
    loading.value = false;
  }
}

// ==================== 课程详情 ====================
const detailVisible = ref(false);
const detailLoading = ref(false);
const detail = ref<TrainingLearnerApi.CourseDetail | null>(null);

async function openDetail(card: TrainingLearnerApi.CourseCard) {
  detailVisible.value = true;
  detailLoading.value = true;
  try {
    detail.value = (await getCourseDetail(card.courseId!)) ?? null;
  } finally {
    detailLoading.value = false;
  }
}

function startStudy(item: TrainingLearnerApi.MediaItem) {
  if (!item.mediaId || !item.mediaType) return;
  detailVisible.value = false;
  router.push({
    path: '/portal/training/study',
    query: {
      courseId: String(detail.value?.courseId ?? ''),
      mediaId: String(item.mediaId),
      mediaType: item.mediaType,
      title: item.name ?? '',
    },
  });
}

function goCourses() {
  router.push('/portal/training/courses');
}

const tabs = computed(() => [
  { key: 'learning', label: '学习中', list: group.value.learningCourses ?? [] },
  { key: 'todo', label: '待学习', list: group.value.todoCourses ?? [] },
  {
    key: 'completed',
    label: '已完成',
    list: group.value.completedCourses ?? [],
  },
]);

const currentList = computed(
  () => tabs.value.find((t) => t.key === activeTab.value)?.list ?? [],
);

onMounted(load);
</script>

<template>
  <Page auto-content-height>
    <section class="learning-stats">
      <div class="stat stat--blue">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M3 5.5A1.5 1.5 0 0 1 4.5 4h11A1.5 1.5 0 0 1 17 5.5v13A1.5 1.5 0 0 1 15.5 20h-11A1.5 1.5 0 0 1 3 18.5z" />
            <path d="M6 4v16" />
            <path d="M17 8h3.5A1.5 1.5 0 0 1 22 9.5v8A1.5 1.5 0 0 1 20.5 19H17" />
          </svg>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ group.totalCount ?? 0 }}<span class="stat-unit">门</span></div>
          <div class="stat-label">我的课程</div>
        </div>
      </div>

      <div class="stat stat--orange">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="9" />
            <path d="M12 7v5l3.5 2" />
          </svg>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ group.learningCount ?? 0 }}<span class="stat-unit">门</span></div>
          <div class="stat-label">学习中</div>
        </div>
      </div>

      <div class="stat stat--green">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M5 12.5l4.5 4.5L19 7" />
            <path d="M3 19h18" />
          </svg>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ group.completedCount ?? 0 }}<span class="stat-unit">门</span></div>
          <div class="stat-label">已完成</div>
        </div>
      </div>

      <div class="stat stat--purple">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 3l2.4 5.8 6.3.6-4.8 4.2 1.5 6.2L12 16.9 6.6 19.8 8.1 13.6 3.3 9.4l6.3-.6z" />
          </svg>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ group.creditEarned ?? 0 }}<span class="stat-unit">/ {{ group.creditTotal ?? 0 }} 学时</span></div>
          <div class="stat-label">已获学时</div>
        </div>
      </div>
    </section>

    <section class="learning-list">
      <div class="tab-bar">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          type="button"
          :class="{ active: activeTab === tab.key }"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
          <span class="tab-count">{{ tab.list.length }}</span>
        </button>
      </div>

      <div v-if="currentList.length" class="course-grid">
        <div
          v-for="card in currentList"
          :key="card.courseId"
          class="course-card"
          @click="openDetail(card)"
        >
          <div class="cover">
            <img v-if="card.coverUrl" :src="card.coverUrl" alt="cover" />
            <div v-else class="cover-fallback">暂无封面</div>
          </div>
          <div class="body">
            <div class="title-row">
              <div class="title" :title="card.title">{{ card.title }}</div>
              <Tag :color="statusColor(card.status)">
                {{ statusText(card.status) }}
              </Tag>
            </div>
            <div class="meta">
              {{ card.lecturer || '—' }} · {{ card.credit ?? 0 }} 学时
            </div>
            <Progress
              class="progress"
              :percent="Number(card.progressPercent ?? 0)"
              size="small"
            />
            <div class="foot">
              <span class="foot-meta">已完成 {{ card.doneCount ?? 0 }}/{{ card.totalCount ?? 0 }} 项</span>
              <Button type="link" size="small" @click.stop="openDetail(card)">
                继续学习
              </Button>
            </div>
          </div>
        </div>
      </div>

      <div v-else class="empty">
        <div class="empty-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
          </svg>
        </div>
        <div class="empty-title">暂无课程</div>
        <div class="empty-desc">这个分类下还没有课程，去课程中心挑一门吧</div>
        <Button type="primary" @click="goCourses">浏览课程</Button>
      </div>
    </section>

    <Modal
      v-model:open="detailVisible"
      :title="detail?.title || '课程详情'"
      :width="720"
      :footer="null"
      wrap-class-name="training-light-modal"
    >
      <div v-if="detailLoading" class="py-8 text-center text-gray-400">
        加载中...
      </div>
      <div v-else-if="detail">
        <div class="mb-3 text-sm text-gray-500">
          {{ detail.lecturer || '—' }} · 进度
          {{ detail.progressPercent ?? 0 }}% · 已获
          {{ detail.creditEarned ?? 0 }}/{{ detail.creditTotal ?? 0 }} 学时
        </div>
        <div class="flex flex-col gap-2">
          <div
            v-for="item in detail.medias ?? []"
            :key="item.mediaId"
            class="flex items-center justify-between rounded border px-3 py-2"
          >
            <div class="min-w-0">
              <div class="truncate text-sm">
                <Tag color="blue">{{ item.mediaTypeName }}</Tag>
                {{ item.name }}
              </div>
              <div class="mt-1 text-xs text-gray-400">
                <template v-if="item.mediaType === 'document'">
                  已覆盖 {{ item.docCoveredPages ?? 0 }}/{{
                    item.docTotalPages ?? '-'
                  }}
                  页
                </template>
                <template v-else-if="item.mediaType === 'video'">
                  有效学习 {{ item.validSeconds ?? 0 }} 秒 / 时长
                  {{ item.durationSeconds ?? '-' }} 秒
                </template>
                <template v-else>该类型暂不支持在线学习</template>
              </div>
            </div>
            <div class="flex items-center gap-2">
              <Tag :color="item.completed ? 'green' : 'default'">
                {{ item.completed ? '已完成' : '未完成' }}
              </Tag>
              <Button
                v-if="item.mediaType === 'video' || item.mediaType === 'document'"
                type="primary"
                size="small"
                @click="startStudy(item)"
              >
                开始学习
              </Button>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="py-10 text-center text-gray-400">
        暂无学习详情
      </div>
    </Modal>
  </Page>
</template>

<style scoped>
.learning-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}

.stat {
  display: flex;
  gap: 14px;
  align-items: center;
  padding: 18px 20px;
  background: #fff;
  border: 1px solid #eaeef5;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgb(16 24 40 / 4%);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.stat:hover {
  border-color: #d8e2f2;
  box-shadow: 0 6px 18px rgb(16 24 40 / 8%);
}

.stat-icon {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: 44px;
  height: 44px;
  color: var(--c-icon, #2f6bff);
  background: var(--c-icon-bg, #eef3ff);
  border-radius: 12px;
}

.stat-icon svg {
  width: 22px;
  height: 22px;
}

.stat-info {
  min-width: 0;
  flex: 1;
}

.stat-value {
  color: #1b2437;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.1;
}

.stat-unit {
  margin-left: 3px;
  color: #98a2b3;
  font-size: 12px;
  font-weight: 500;
}

.stat-label {
  margin-top: 4px;
  color: #667085;
  font-size: 13px;
}

.stat--blue {
  --c-icon: #2f6bff;
  --c-icon-bg: #eef3ff;
}
.stat--orange {
  --c-icon: #ea7b0c;
  --c-icon-bg: #fff5e8;
}
.stat--green {
  --c-icon: #16a34a;
  --c-icon-bg: #eefaf2;
}
.stat--purple {
  --c-icon: #7a3ddc;
  --c-icon-bg: #f4edff;
}

.learning-list {
  padding: 18px 22px 22px;
  background: #fff;
  border: 1px solid #eaeef5;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgb(16 24 40 / 4%);
}

.tab-bar {
  display: flex;
  gap: 4px;
  margin: 0 -8px 18px;
  padding: 0 8px;
  border-bottom: 1px solid #eaeef5;
}

.tab-bar button {
  position: relative;
  padding: 10px 16px;
  background: transparent;
  border: 0;
  color: #667085;
  font-size: 14px;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  transition: color 0.2s, border-color 0.2s;
}

.tab-bar button:hover {
  color: #1b2437;
}

.tab-bar button.active {
  color: #1b2437;
  font-weight: 600;
  border-bottom-color: #2f6bff;
}

.tab-bar .tab-count {
  margin-left: 4px;
  color: #98a2b3;
  font-size: 12px;
}

.tab-bar button.active .tab-count {
  color: #2f6bff;
}

.course-grid {
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
}

.course-card {
  cursor: pointer;
  background: #fff;
  border: 1px solid #eaeef5;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 1px 2px rgb(16 24 40 / 4%);
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s;
}

.course-card:hover {
  border-color: #d3e0f7;
  box-shadow: 0 10px 24px rgb(16 24 40 / 8%);
  transform: translateY(-2px);
}

.course-card .cover {
  height: 140px;
  background: #f4f6fa;
}

.course-card .cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.course-card .cover-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #98a2b3;
  font-size: 13px;
}

.course-card .body {
  padding: 14px 16px 16px;
}

.course-card .title-row {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  justify-content: space-between;
}

.course-card .title {
  min-width: 0;
  flex: 1;
  color: #1b2437;
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.course-card .meta {
  margin-top: 6px;
  color: #8892a6;
  font-size: 12px;
}

.course-card .progress {
  margin-top: 10px;
}

.course-card .foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
}

.course-card .foot-meta {
  color: #98a2b3;
  font-size: 12px;
}

.empty {
  padding: 48px 20px;
  text-align: center;
  background: #fff;
  border: 1px dashed #e1e6f0;
  border-radius: 12px;
}

.empty-icon {
  display: inline-grid;
  place-items: center;
  width: 64px;
  height: 64px;
  margin-bottom: 14px;
  color: #2f6bff;
  background: #eef3ff;
  border-radius: 50%;
}

.empty-icon svg {
  width: 28px;
  height: 28px;
}

.empty-title {
  color: #1b2437;
  font-size: 15px;
  font-weight: 600;
}

.empty-desc {
  margin: 6px 0 18px;
  color: #8892a6;
  font-size: 13px;
}

@media (max-width: 760px) {
  .learning-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>

<style>
.training-light-modal .ant-modal-content{background:#fff;color:#1e293b}
.training-light-modal .ant-modal-header{margin-bottom:12px;background:transparent}
.training-light-modal .ant-modal-title{color:#1b2437}
.training-light-modal .ant-modal-close{color:#667085}
.training-light-modal .ant-input,.training-light-modal textarea.ant-input{color:#1e293b;background:#fff;border-color:#d7e2f2}
</style>