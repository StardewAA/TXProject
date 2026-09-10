<script lang="ts" setup>
import type { TrainingLearnerApi } from '../learning/api';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Card, Progress, Select, Table, Tabs, Tag } from 'ant-design-vue';

import { getMyCheckins, getMySummary, getMyTracks } from '../learning/api';

defineOptions({ name: 'PortalTrainingProfile' });

const MEDIA_TYPE_OPTIONS = [
  { label: '全部类型', value: '' },
  { label: '视频', value: 'video' },
  { label: '文档', value: 'document' },
  { label: '直播', value: 'live' },
  { label: '回放', value: 'replay' },
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
    <div class="mb-4 grid grid-cols-2 gap-4 md:grid-cols-4">
      <Card size="small" class="text-center">
        <div class="text-2xl font-semibold text-blue-600">
          {{ durationText(summary.totalValidSeconds) }}
        </div>
        <div class="mt-1 text-xs text-gray-500">累计有效学习时长</div>
      </Card>
      <Card size="small" class="text-center">
        <div class="text-2xl font-semibold text-green-600">
          {{ summary.totalCreditEarned ?? 0 }}
        </div>
        <div class="mt-1 text-xs text-gray-500">
          已获学时 / {{ summary.creditTotal ?? 0 }}
        </div>
      </Card>
      <Card size="small" class="text-center">
        <div class="text-2xl font-semibold">
          {{ summary.taskCompletedCount ?? 0 }}/{{ summary.taskTotalCount ?? 0 }}
        </div>
        <div class="mt-1 text-xs text-gray-500">已完成课程</div>
      </Card>
      <Card size="small" class="text-center">
        <div class="text-2xl font-semibold text-purple-600">
          {{ summary.checkinCount ?? 0 }}
        </div>
        <div class="mt-1 text-xs text-gray-500">学习打卡次数</div>
      </Card>
    </div>

    <Tabs type="card">
      <Tabs.TabPane key="course" tab="课程进度">
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
      </Tabs.TabPane>

      <Tabs.TabPane key="track" tab="学习轨迹">
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
      </Tabs.TabPane>

      <Tabs.TabPane key="checkin" tab="打卡记录">
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
      </Tabs.TabPane>
    </Tabs>
  </Page>
</template>
