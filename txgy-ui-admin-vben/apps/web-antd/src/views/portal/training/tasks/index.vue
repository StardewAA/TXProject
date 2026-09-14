<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Button, Empty, Progress, Table, Tag } from 'ant-design-vue';

import { getMyCourses } from '../learning/api';
import { useTrainingDirection } from '../use-training-direction';

defineOptions({ name: 'PortalTrainingTasks' });

const router = useRouter();
const { state, levelName, load: loadDirection } = useTrainingDirection();

const loading = ref(false);
const group = ref<Record<string, any>>({});

/** 待学习 / 学习中 / 已完成 三类任务合并为任务清单 */
const taskList = computed(() => {
  const tag = (list: any[], status: number) =>
    (list ?? []).map((item) => ({ ...item, taskStatus: status }));
  return [
    ...tag(group.value.learningCourses, 1),
    ...tag(group.value.todoCourses, 0),
    ...tag(group.value.completedCourses, 2),
  ];
});

const qualified = computed(() => {
  const total = group.value.totalCount ?? 0;
  if (!total) return 0;
  return Math.round(((group.value.completedCount ?? 0) / total) * 100);
});

const columns = [
  { title: '培训任务（课程）', dataIndex: 'title', key: 'title' },
  { title: '讲师', dataIndex: 'lecturer', key: 'lecturer', width: 130 },
  { title: '任务状态', key: 'status', width: 110, align: 'center' },
  { title: '学习进度', key: 'progress', width: 200 },
  { title: '学时', key: 'credit', width: 120, align: 'center' },
  { title: '操作', key: 'action', width: 110, align: 'center' },
];

function statusColor(status?: number) {
  if (status === 2) return 'green';
  if (status === 1) return 'orange';
  return 'default';
}
function statusText(status?: number) {
  if (status === 2) return '已完成';
  if (status === 1) return '进行中';
  return '待开始';
}

function goLearning() {
  router.push('/portal/training/learning');
}

async function load() {
  loading.value = true;
  try {
    group.value = (await getMyCourses()) ?? {};
  } catch {
    group.value = {};
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadDirection();
  load();
});
</script>

<template>
  <div class="tasks">
    <section class="intro">
      <div>
        <h2>培训任务与上岗</h2>
        <p>
          按岗位能力要求下发的培训任务在这里统一跟踪，完成任务并达到成长等级要求后，
          可进入上岗资格认定流程。
        </p>
      </div>
      <div class="qualify">
        <Progress type="circle" :percent="qualified" :size="86" />
        <div class="qualify-text">
          <strong>上岗资格达成度 {{ qualified }}%</strong>
          <span>当前成长等级：{{ levelName }}</span>
        </div>
      </div>
    </section>

    <section class="stats">
      <div class="stat stat--orange">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="9" />
            <path d="M12 7v5l3.5 2" />
          </svg>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ group.todoCount ?? 0 }}<span class="stat-unit">门</span></div>
          <div class="stat-label">待开始任务</div>
        </div>
      </div>

      <div class="stat stat--blue">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M8 5.5v13l10-6.5z" />
          </svg>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ group.learningCount ?? 0 }}<span class="stat-unit">门</span></div>
          <div class="stat-label">进行中任务</div>
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
          <div class="stat-label">已完成任务</div>
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

    <section class="block">
      <div class="block-head">
        <h2>任务清单</h2>
        <span class="hint">
          学习方向：{{ state.selected.length ? state.selected.join('、') : '未设置' }}
        </span>
      </div>
      <Table
        row-key="courseId"
        :data-source="taskList"
        :loading="loading"
        :columns="columns"
        :pagination="{ pageSize: 10 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <Tag :color="statusColor(record.taskStatus)">
              {{ statusText(record.taskStatus) }}
            </Tag>
          </template>
          <template v-else-if="column.key === 'progress'">
            <Progress :percent="Number(record.progressPercent ?? 0)" size="small" />
          </template>
          <template v-else-if="column.key === 'credit'">
            {{ record.creditEarned ?? 0 }} / {{ record.creditTotal ?? record.credit ?? 0 }}
          </template>
          <template v-else-if="column.key === 'action'">
            <Button type="link" size="small" @click="goLearning">
              {{ record.taskStatus === 1 ? '继续学习' : '去学习' }}
            </Button>
          </template>
        </template>
        <template #emptyText>
          <Empty description="暂无下发的培训任务" />
        </template>
      </Table>
    </section>
  </div>
</template>

<style scoped>
.tasks {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding-top: 18px;
}

.intro {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
  padding: 20px 24px;
  background: linear-gradient(120deg, #eef4ff, #f7fbff);
  border: 1px solid #dbe5f4;
  border-radius: 12px;
}

.intro h2 {
  margin: 0;
  font-size: 18px;
}

.intro p {
  max-width: 680px;
  margin: 8px 0 0;
  color: #67748b;
  line-height: 1.7;
}

.qualify {
  display: flex;
  gap: 14px;
  align-items: center;
  flex-shrink: 0;
}

.qualify-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.qualify-text strong {
  color: #172033;
}

.qualify-text span {
  color: #7d899f;
  font-size: 12px;
}

.stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
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

.block {
  padding: 18px 20px;
  background: linear-gradient(120deg, #eef4ff 0%, #f7fbff 100%);
  border: 1px solid #dbe5f4;
  border-radius: 12px;
}

/* 覆盖 antd 暗色主题下的深色表格，融入渐变蓝白卡片 */
.block :deep(.ant-table),
.block :deep(.ant-table-container),
.block :deep(.ant-table-content),
.block :deep(.ant-table-header),
.block :deep(.ant-table-body) {
  background: transparent;
}

.block :deep(.ant-table) {
  color: #334155;
}

.block :deep(.ant-table-thead > tr > th) {
  color: #475569;
  background: rgb(255 255 255 / 62%);
  border-bottom: 1px solid #d7e2f2;
}

.block :deep(.ant-table-thead > tr > th::before) {
  background-color: rgb(215 226 242 / 90%) !important;
}

.block :deep(.ant-table-tbody > tr > td) {
  color: #1e293b;
  background: transparent;
  border-bottom: 1px solid rgb(215 226 242 / 70%);
}

.block :deep(.ant-table-tbody > tr.ant-table-row:hover > td),
.block :deep(.ant-table-tbody > tr > td.ant-table-cell-row-hover) {
  background: rgb(255 255 255 / 55%);
}

.block :deep(.ant-table-placeholder > td),
.block :deep(.ant-table-placeholder:hover > td) {
  background: transparent;
}

.block :deep(.ant-empty-description) {
  color: #8a97ac;
}

.block :deep(.ant-progress-text) {
  color: #475569;
}

.block :deep(.ant-pagination) {
  color: #475569;
}

.block :deep(.ant-pagination-item) {
  background: rgb(255 255 255 / 70%);
  border-color: #d7e2f2;
}

.block :deep(.ant-pagination-item a) {
  color: #475569;
}

.block :deep(.ant-pagination-item-active) {
  background: #2f6bff;
  border-color: #2f6bff;
}

.block :deep(.ant-pagination-item-active a) {
  color: #fff;
}

.block :deep(.ant-pagination-prev .ant-pagination-item-link),
.block :deep(.ant-pagination-next .ant-pagination-item-link) {
  color: #475569;
  background: rgb(255 255 255 / 70%);
  border-color: #d7e2f2;
}

.block-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.block-head h2 {
  display: flex;
  gap: 10px;
  align-items: center;
  font-size: 17px;
}

.block-head h2::before {
  width: 4px;
  height: 16px;
  background: #2f6bff;
  border-radius: 2px;
  content: '';
}

.hint {
  color: #7d899f;
  font-size: 12px;
}

@media (max-width: 900px) {
  .intro {
    flex-direction: column;
    align-items: flex-start;
  }

  .stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
