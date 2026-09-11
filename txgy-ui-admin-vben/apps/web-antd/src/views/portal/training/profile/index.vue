<script lang="ts" setup>
import type { TrainingLearnerApi } from '../learning/api';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Progress, Select, Table, Tag } from 'ant-design-vue';

import { getMyCheckins, getMySummary, getMyTracks } from '../learning/api';

defineOptions({ name: 'PortalTrainingProfile' });

const MEDIA_TYPE_OPTIONS = [
  { label: '全部类型', value: '' },
  { label: '视频', value: 'video' },
  { label: '文档', value: 'document' },
  { label: '直播', value: 'live' },
  { label: '回放', value: 'replay' },
];

const profileTab = ref('course');
const profileTabs = [
  { key: 'course', label: '课程进度' },
  { key: 'track', label: '学习轨迹' },
  { key: 'checkin', label: '打卡记录' },
];

const summary = ref<TrainingLearnerApi.TrackSummary>({});

const trackLoading = ref(false);
const trackList = ref<TrainingLearnerApi.TrackRow[]>([]);
const trackTotal = ref(0);
const trackQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  mediaType: '',
});

const checkins = ref<TrainingLearnerApi.CheckinRecord[]>([]);

const durationText = (seconds?: number) => {
  const total = seconds ?? 0;
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  if (h > 0) return `${h} 小时 ${m} 分`;
  if (m > 0) return `${m} 分 ${s} 秒`;
  return `${s} 秒`;
};

const statusText = (status?: number) => {
  if (status === 2) return '已完成';
  if (status === 1) return '学习中';
  return '待学习';
};
const statusColor = (status?: number) => {
  if (status === 2) return 'green';
  if (status === 1) return 'orange';
  return 'default';
};

async function loadSummary() {
  summary.value = (await getMySummary()) ?? {};
}

async function loadTracks() {
  trackLoading.value = true;
  try {
    const res = await getMyTracks({
      ...trackQuery,
      mediaType: trackQuery.mediaType || undefined,
    });
    trackList.value = res?.list ?? [];
    trackTotal.value = Number(res?.total ?? 0);
  } finally {
    trackLoading.value = false;
  }
}

async function loadCheckins() {
  checkins.value = (await getMyCheckins()) ?? [];
}

onMounted(() => {
  loadSummary();
  loadTracks();
  loadCheckins();
});
</script>

<template>
  <Page auto-content-height>
    <section class="profile-stats">
      <div class="stat stat--blue">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="9" />
            <path d="M12 7v5l3.5 2" />
          </svg>
        </div>
        <div class="stat-info">
          <div class="stat-value stat-value--sm">{{ durationText(summary.totalValidSeconds) }}</div>
          <div class="stat-label">累计有效学习时长</div>
        </div>
      </div>

      <div class="stat stat--green">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 3l2.4 5.8 6.3.6-4.8 4.2 1.5 6.2L12 16.9 6.6 19.8 8.1 13.6 3.3 9.4l6.3-.6z" />
          </svg>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ summary.totalCreditEarned ?? 0 }}<span class="stat-unit">/ {{ summary.creditTotal ?? 0 }} 学时</span></div>
          <div class="stat-label">已获学时</div>
        </div>
      </div>

      <div class="stat stat--purple">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M5 12.5l4.5 4.5L19 7" />
            <path d="M3 19h18" />
          </svg>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ summary.taskCompletedCount ?? 0 }}<span class="stat-unit">/ {{ summary.taskTotalCount ?? 0 }} 门</span></div>
          <div class="stat-label">已完成课程</div>
        </div>
      </div>

      <div class="stat stat--orange">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <rect x="3.5" y="5" width="17" height="15.5" rx="2" />
            <path d="M3.5 9.5h17M8 3.5v3M16 3.5v3" />
            <path d="M9 14l2 2 3.5-3.5" />
          </svg>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ summary.checkinCount ?? 0 }}<span class="stat-unit">次</span></div>
          <div class="stat-label">学习打卡次数</div>
        </div>
      </div>
    </section>

    <section class="profile-panel">
      <div class="tab-bar">
        <button
          v-for="tab in profileTabs"
          :key="tab.key"
          type="button"
          :class="{ active: profileTab === tab.key }"
          @click="profileTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </div>

      <div v-show="profileTab === 'course'">
        <Table
          row-key="courseId"
          :data-source="summary.courseRows ?? []"
          :pagination="false"
          :columns="[
            { title: '课程', dataIndex: 'courseTitle', key: 'courseTitle' },
            { title: '状态', key: 'status', width: 100, align: 'center' },
            { title: '进度', key: 'progress', width: 200 },
            { title: '已获学时', dataIndex: 'creditEarned', key: 'creditEarned', width: 120, align: 'center' },
          ]"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <Tag :color="statusColor(record.taskStatus)">
                {{ statusText(record.taskStatus) }}
              </Tag>
            </template>
            <template v-else-if="column.key === 'progress'">
              <Progress
                :percent="Number(record.progressPercent ?? 0)"
                size="small"
              />
            </template>
          </template>
        </Table>
        <div class="mt-4">
          <div class="mb-2 text-sm font-medium">学习类型分布</div>
          <Table
            row-key="mediaType"
            :data-source="summary.typeRows ?? []"
            :pagination="false"
            :columns="[
              { title: '学习类型', dataIndex: 'mediaTypeName', key: 'mediaTypeName' },
              { title: '会话数', dataIndex: 'sessionCount', key: 'sessionCount', width: 100, align: 'center' },
              { title: '有效时长', key: 'duration', width: 160, align: 'center' },
            ]"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'duration'">
                {{ durationText(record.totalValidSeconds) }}
              </template>
            </template>
          </Table>
        </div>
      </div>

      <div v-show="profileTab === 'track'">
        <div class="mb-3 flex items-center gap-3">
          <Select
            v-model:value="trackQuery.mediaType"
            class="!w-40"
            :options="MEDIA_TYPE_OPTIONS"
            @change="
              () => {
                trackQuery.pageNo = 1;
                loadTracks();
              }
            "
          />
        </div>
        <Table
          row-key="sessionId"
          :data-source="trackList"
          :loading="trackLoading"
          :pagination="{
            current: trackQuery.pageNo,
            pageSize: trackQuery.pageSize,
            total: trackTotal,
            showSizeChanger: true,
            showTotal: (total: number) => `共 ${total} 条`,
          }"
          :columns="[
            { title: '学习内容', key: 'name' },
            { title: '类型', dataIndex: 'mediaTypeName', key: 'mediaTypeName', width: 90, align: 'center' },
            { title: '开始时间', dataIndex: 'startAt', key: 'startAt', width: 180 },
            { title: '结束时间', dataIndex: 'endAt', key: 'endAt', width: 180 },
            { title: '有效时长', key: 'duration', width: 120, align: 'center' },
            { title: '折算学时', dataIndex: 'creditEarned', key: 'creditEarned', width: 110, align: 'center' },
          ]"
          @change="
            (pager: any) => {
              trackQuery.pageNo = pager.current;
              trackQuery.pageSize = pager.pageSize;
              loadTracks();
            }
          "
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'name'">
              {{ record.mediaName }}
              <span class="ml-1 text-xs text-gray-400">
                {{ record.courseTitle ? `（${record.courseTitle}）` : '' }}
              </span>
            </template>
            <template v-else-if="column.key === 'duration'">
              {{ durationText(record.validSeconds) }}
            </template>
          </template>
        </Table>
      </div>

      <div v-show="profileTab === 'checkin'">
        <Table
          row-key="id"
          :data-source="checkins"
          :pagination="false"
          :columns="[
            { title: '打卡时间', dataIndex: 'checkinAt', key: 'checkinAt', width: 200 },
            { title: '课程 ID', dataIndex: 'courseId', key: 'courseId', width: 200 },
            { title: '媒资类型', dataIndex: 'mediaType', key: 'mediaType', width: 120, align: 'center' },
            { title: '打卡时有效秒数', dataIndex: 'validSecondsBefore', key: 'validSecondsBefore', width: 160, align: 'center' },
          ]"
        />
      </div>
    </section>
  </Page>
</template>

<style scoped>
.profile-stats {
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

.stat-value--sm {
  font-size: 20px;
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

.profile-panel {
  padding: 18px 22px 22px;
  background: linear-gradient(120deg, #eef4ff 0%, #f7fbff 100%);
  border: 1px solid #dbe5f4;
  border-radius: 12px;
}

/* 覆盖 antd 暗色主题下的深色表格，融入渐变蓝白卡片 */
.profile-panel :deep(.ant-table),
.profile-panel :deep(.ant-table-container),
.profile-panel :deep(.ant-table-content),
.profile-panel :deep(.ant-table-header),
.profile-panel :deep(.ant-table-body) {
  background: transparent;
}

.profile-panel :deep(.ant-table) {
  color: #334155;
}

.profile-panel :deep(.ant-table-thead > tr > th) {
  color: #475569;
  background: rgb(255 255 255 / 62%);
  border-bottom: 1px solid #d7e2f2;
}

.profile-panel :deep(.ant-table-thead > tr > th::before) {
  background-color: rgb(215 226 242 / 90%) !important;
}

.profile-panel :deep(.ant-table-tbody > tr > td) {
  color: #1e293b;
  background: transparent;
  border-bottom: 1px solid rgb(215 226 242 / 70%);
}

.profile-panel :deep(.ant-table-tbody > tr.ant-table-row:hover > td),
.profile-panel :deep(.ant-table-tbody > tr > td.ant-table-cell-row-hover) {
  background: rgb(255 255 255 / 55%);
}

.profile-panel :deep(.ant-table-placeholder > td),
.profile-panel :deep(.ant-table-placeholder:hover > td) {
  background: transparent;
}

.profile-panel :deep(.ant-empty-description) {
  color: #8a97ac;
}

.profile-panel :deep(.ant-progress-text) {
  color: #475569;
}

.profile-panel :deep(.ant-pagination) {
  color: #475569;
}

.profile-panel :deep(.ant-pagination-item) {
  background: rgb(255 255 255 / 70%);
  border-color: #d7e2f2;
}

.profile-panel :deep(.ant-pagination-item a) {
  color: #475569;
}

.profile-panel :deep(.ant-pagination-item-active) {
  background: #2f6bff;
  border-color: #2f6bff;
}

.profile-panel :deep(.ant-pagination-item-active a) {
  color: #fff;
}

.profile-panel :deep(.ant-pagination-prev .ant-pagination-item-link),
.profile-panel :deep(.ant-pagination-next .ant-pagination-item-link) {
  color: #475569;
  background: rgb(255 255 255 / 70%);
  border-color: #d7e2f2;
}

.tab-bar {
  display: flex;
  gap: 4px;
  margin: 0 -8px 18px;
  padding: 0 8px;
  border-bottom: 1px solid #eaeef5;
}

.tab-bar button {
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

@media (max-width: 760px) {
  .profile-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>