<script lang="ts" setup>
import type { TrainingLearnerApi } from './api';

import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { Button, Card, Empty, Modal, Progress, Tabs, Tag } from 'ant-design-vue';

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

const tabs = computed(() => [
  { key: 'learning', label: '学习中', list: group.value.learningCourses ?? [] },
  { key: 'todo', label: '待学习', list: group.value.todoCourses ?? [] },
  {
    key: 'completed',
    label: '已完成',
    list: group.value.completedCourses ?? [],
  },
]);

onMounted(load);
</script>

<template>
  <Page auto-content-height>
    <div class="mb-4 grid grid-cols-2 gap-4 md:grid-cols-4">
      <Card size="small" class="text-center">
        <div class="text-2xl font-semibold">{{ group.totalCount ?? 0 }}</div>
        <div class="mt-1 text-xs text-gray-500">我的课程</div>
      </Card>
      <Card size="small" class="text-center">
        <div class="text-2xl font-semibold text-orange-500">
          {{ group.learningCount ?? 0 }}
        </div>
        <div class="mt-1 text-xs text-gray-500">学习中</div>
      </Card>
      <Card size="small" class="text-center">
        <div class="text-2xl font-semibold text-green-600">
          {{ group.completedCount ?? 0 }}
        </div>
        <div class="mt-1 text-xs text-gray-500">已完成</div>
      </Card>
      <Card size="small" class="text-center">
        <div class="text-2xl font-semibold text-blue-600">
          {{ group.creditEarned ?? 0 }}
        </div>
        <div class="mt-1 text-xs text-gray-500">
          已获学时 / {{ group.creditTotal ?? 0 }}
        </div>
      </Card>
    </div>

    <Tabs v-model:active-key="activeTab" type="card">
      <Tabs.TabPane
        v-for="tab in tabs"
        :key="tab.key"
        :tab="`${tab.label}(${tab.list.length})`"
      >
        <div v-if="tab.list.length" class="grid gap-4 md:grid-cols-3">
          <Card
            v-for="card in tab.list"
            :key="card.courseId"
            hoverable
            size="small"
            @click="openDetail(card)"
          >
            <template #cover>
              <div class="h-32 w-full overflow-hidden bg-gray-100">
                <img
                  v-if="card.coverUrl"
                  :src="card.coverUrl"
                  class="h-full w-full object-cover"
                  alt="cover"
                />
                <div
                  v-else
                  class="flex h-full items-center justify-center text-gray-400"
                >
                  暂无封面
                </div>
              </div>
            </template>
            <div class="flex items-start justify-between gap-2">
              <div class="truncate font-medium">{{ card.title }}</div>
              <Tag :color="statusColor(card.status)">
                {{ statusText(card.status) }}
              </Tag>
            </div>
            <div class="mt-1 text-xs text-gray-500">
              {{ card.lecturer || '—' }} · {{ card.credit ?? 0 }} 学时
            </div>
            <Progress
              class="mt-2"
              :percent="Number(card.progressPercent ?? 0)"
              size="small"
            />
            <div class="mt-1 flex justify-between text-xs text-gray-400">
              <span>已完成 {{ card.doneCount ?? 0 }}/{{ card.totalCount ?? 0 }} 项</span>
              <Button type="link" size="small" @click.stop="openDetail(card)">
                继续学习
              </Button>
            </div>
          </Card>
        </div>
        <Empty v-else description="暂无课程" />
      </Tabs.TabPane>
    </Tabs>

    <Modal
      v-model:open="detailVisible"
      :title="detail?.title || '课程详情'"
      :width="720"
      :footer="null"
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
      <Empty v-else description="暂无学习详情" />
    </Modal>
  </Page>
</template>
