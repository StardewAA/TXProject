<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';

import { Button, Empty, Progress, Table, Tag, message } from 'ant-design-vue';

import ExamWorkspace from '../../../training/components/exam-workspace.vue';
import { getMyCourses, getMySummary } from '../learning/api';

defineOptions({ name: 'PortalTrainingExam' });

const examVisible = ref(false);
const loading = ref(false);
const summary = ref<Record<string, any>>({});
const group = ref<Record<string, any>>({});

/** 已结业课程（含结业进度），作为证书申领依据 */
const completedCourses = computed(() => group.value.completedCourses ?? []);

const qualifyCount = computed(
  () => completedCourses.value.filter((item: any) => (item.progressPercent ?? 0) >= 100).length,
);

const completionPercent = computed(() => {
  const total = summary.value.taskTotalCount ?? group.value.totalCount ?? 0;
  if (!total) return 0;
  return Math.round((Number(summary.value.taskCompletedCount ?? 0) / total) * 100);
});

const columns = [
  { title: '课程名称', dataIndex: 'title', key: 'title' },
  { title: '讲师', dataIndex: 'lecturer', key: 'lecturer', width: 140 },
  { title: '进度', key: 'progress', width: 220 },
  { title: '已获学时', dataIndex: 'creditEarned', key: 'creditEarned', width: 110, align: 'center' },
  { title: '结业状态', key: 'status', width: 120, align: 'center' },
  { title: '操作', key: 'action', width: 130, align: 'center' },
];

function claimCert(course: Record<string, any>) {
  message.success(`「${course.title}」结业证明申请已提交，审核通过后可在个人档案下载。`);
}

async function load() {
  loading.value = true;
  try {
    const [g, s] = await Promise.all([getMyCourses(), getMySummary()]);
    group.value = g ?? {};
    summary.value = s ?? {};
  } catch {
    group.value = {};
    summary.value = {};
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="exam">
    <section class="banner">
      <div>
        <h2>考试与证书</h2>
        <p>
          完成课程学习并通过结业考核后，可申领电子结业证明与技能等级证书，
          证书信息与个人学习档案、校企联合认证互通。
        </p>
      </div>
    </section>

    <section class="stats">
      <div class="stat stat--green">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M5 12.5l4.5 4.5L19 7" />
            <path d="M3 19h18" />
          </svg>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ summary.taskCompletedCount ?? 0 }}<span class="stat-unit">门</span></div>
          <div class="stat-label">已完成课程</div>
        </div>
      </div>

      <div class="stat stat--purple">
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

      <div class="stat stat--blue">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M6 3h12v12H6z" />
            <path d="M9 7h6M9 10.5h4" />
            <circle cx="12" cy="17.5" r="3" />
            <path d="M10.5 20L10 23l2-1.2L14 23l-.5-3" />
          </svg>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ qualifyCount }}<span class="stat-unit">门</span></div>
          <div class="stat-label">可申领结业证明</div>
        </div>
      </div>

      <div class="stat stat--orange">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M4 18V9M10 18V5M16 18v-6M22 18H2" />
          </svg>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ completionPercent }}<span class="stat-unit">%</span></div>
          <div class="stat-label">结业进度</div>
        </div>
      </div>
    </section>

    <section class="block">
      <div class="block-head">
        <h2>结业与证书</h2>
        <span class="hint">课程进度达 100% 即可申领结业证明</span>
      </div>
      <Table
        row-key="courseId"
        :data-source="completedCourses"
        :loading="loading"
        :columns="columns"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'progress'">
            <Progress :percent="Number(record.progressPercent ?? 0)" size="small" />
          </template>
          <template v-else-if="column.key === 'status'">
            <Tag :color="(record.progressPercent ?? 0) >= 100 ? 'green' : 'orange'">
              {{ (record.progressPercent ?? 0) >= 100 ? '已结业' : '学习中' }}
            </Tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <Button
              type="link"
              size="small"
              :disabled="(record.progressPercent ?? 0) < 100"
              @click="claimCert(record)"
            >
              申领证书
            </Button>
          </template>
        </template>
        <template #emptyText>
          <Empty description="暂无已完成课程，完成学习后可在此申领证书" />
        </template>
      </Table>
    </section>

    <section class="block">
      <div class="block-head">
        <h2>在线考试系统</h2>
        <Button v-if="!examVisible" type="primary" @click="examVisible = true">
          进入在线考试
        </Button>
        <Button v-else @click="examVisible = false">收起考试系统</Button>
      </div>
      <ExamWorkspace v-if="examVisible" mode="learner" />
      <div v-else class="exam-placeholder">
        <p>接入考试与认证服务，支持在线答题、实操考核与电子证书发放。</p>
        <Button type="primary" @click="examVisible = true">进入在线考试</Button>
      </div>
    </section>

    <section class="block">
      <div class="block-head">
        <h2>在线考试说明</h2>
      </div>
      <div class="rules">
        <div class="rule">
          <strong>考试方式</strong>
          <p>课程内嵌考试在完成全部媒资学习后解锁，进入考试后计时不暂停。</p>
        </div>
        <div class="rule">
          <strong>成绩认定</strong>
          <p>考试与实操成绩按课程考核规则加权，达到合格线方可计入结业学时。</p>
        </div>
        <div class="rule">
          <strong>证书发放</strong>
          <p>结业证明与技能等级证书采用电子证书，可在个人档案与校企合作专区查验。</p>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.exam {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding-top: 18px;
}

.banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 18px;
  padding: 20px 24px;
  background: linear-gradient(120deg, #eef4ff 0%, #f7fbff 100%);
  border: 1px solid #dbe5f4;
  border-radius: 12px;
}

.banner h2 {
  margin: 0;
  font-size: 18px;
}

.banner p {
  max-width: 720px;
  margin: 8px 0 0;
  color: #67748b;
  line-height: 1.7;
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

.exam-placeholder {
  display: flex;
  flex-direction: column;
  gap: 14px;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  color: #67748b;
  text-align: center;
  background: rgb(255 255 255 / 65%);
  border: 1px dashed #bcd0ee;
  border-radius: 10px;
}

.exam-placeholder p {
  margin: 0;
}

.rules {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.rule {
  padding: 16px;
  background: #fff;
  border: 1px solid #e6ebf3;
  border-radius: 10px;
}

.rule strong {
  color: #182742;
}

.rule p {
  margin: 8px 0 0;
  color: #78869c;
  font-size: 13px;
  line-height: 1.7;
}

@media (max-width: 900px) {
  .rules {
    grid-template-columns: 1fr;
  }

  .stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
