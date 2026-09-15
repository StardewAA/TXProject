<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';

import { Empty, Table, Tag } from 'ant-design-vue';

import {
  getSchoolPage,
  getTrainClassPage,
} from '../../../training/cooperation/api';
import {
  COOP_MODE_TEXT,
  SCHOOL_CATEGORY_TEXT,
  schoolStatusColor,
  schoolStatusText,
  trainClassStatusColor,
  trainClassStatusText,
  TRAIN_CLASS_TYPE_TEXT,
} from '../../../training/cooperation/constants';

defineOptions({ name: 'PortalTrainingCooperation' });

const schoolLoading = ref(false);
const classLoading = ref(false);
const schools = ref<Record<string, any>[]>([]);
const trainClasses = ref<Record<string, any>[]>([]);

const activeSchoolCount = computed(
  () => schools.value.filter((item) => item.status === 1).length,
);
const trainingClassCount = computed(
  () => trainClasses.value.filter((item) => item.status === 1).length,
);
const studentCount = computed(() =>
  schools.value.reduce((sum, item) => sum + Number(item.studentCount ?? 0), 0),
);

const classColumns = [
  { title: '培训班名称', dataIndex: 'name', key: 'name' },
  { title: '合作院校', dataIndex: 'schoolName', key: 'schoolName' },
  { title: '培养类型', key: 'type', width: 120, align: 'center' },
  { title: '学员人数', dataIndex: 'studentCount', key: 'studentCount', width: 100, align: 'center' },
  { title: '开班时间', dataIndex: 'planStart', key: 'planStart', width: 160 },
  { title: '状态', key: 'status', width: 100, align: 'center' },
];

async function load() {
  schoolLoading.value = true;
  classLoading.value = true;
  try {
    const [schoolRes, classRes] = await Promise.all([
      getSchoolPage({ pageNo: 1, pageSize: 100 }),
      getTrainClassPage({ pageNo: 1, pageSize: 100 }),
    ]);
    schools.value = schoolRes?.list ?? [];
    trainClasses.value = classRes?.list ?? [];
  } catch {
    schools.value = [];
    trainClasses.value = [];
  } finally {
    schoolLoading.value = false;
    classLoading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="coop">
    <section class="banner">
      <div>
        <h2>校企合作专区</h2>
        <p>
          汇聚合作院校、定向培训班与联合培养项目，打通在校学习、企业实训与就业对接，
          实现校企人才共育与档案互通。
        </p>
      </div>
      <Tag color="blue">产教融合</Tag>
    </section>

    <section class="stats">
      <div class="stat stat--blue">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 3l9 5-9 5-9-5z" />
            <path d="M5 10.5V16c0 1.7 3.1 3 7 3s7-1.3 7-3v-5.5" />
          </svg>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ schools.length }}<span class="stat-unit">所</span></div>
          <div class="stat-label">合作院校 / 意向院校</div>
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
          <div class="stat-value">{{ activeSchoolCount }}<span class="stat-unit">所</span></div>
          <div class="stat-label">合作中院校</div>
        </div>
      </div>

      <div class="stat stat--orange">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="9" cy="8" r="3" />
            <path d="M2.5 19a6.5 6.5 0 0 1 13 0" />
            <path d="M16 5.5a3 3 0 0 1 0 5.8" />
            <path d="M17.5 13.5A6.5 6.5 0 0 1 21.5 19" />
          </svg>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ trainingClassCount }}<span class="stat-unit">个</span></div>
          <div class="stat-label">培训中定向班</div>
        </div>
      </div>

      <div class="stat stat--purple">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="8" r="3.5" />
            <path d="M4.5 19.5a7.5 7.5 0 0 1 15 0" />
          </svg>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ studentCount }}<span class="stat-unit">人</span></div>
          <div class="stat-label">在培学员数</div>
        </div>
      </div>
    </section>

    <section class="block">
      <div class="block-head">
        <h2>合作院校</h2>
        <span class="hint">共 {{ schools.length }} 所</span>
      </div>
      <div v-if="schools.length" class="school-grid">
        <article v-for="school in schools" :key="school.id" class="school">
          <div class="school-head">
            <strong>{{ school.name }}</strong>
            <Tag :color="schoolStatusColor(school.status)">
              {{ schoolStatusText(school.status) }}
            </Tag>
          </div>
          <div class="school-tags">
            <Tag>{{ SCHOOL_CATEGORY_TEXT[school.category] || '院校' }}</Tag>
            <Tag color="geekblue">{{ COOP_MODE_TEXT[school.coopMode] || '合作办学' }}</Tag>
            <Tag v-if="school.levelTag" color="gold">{{ school.levelTag }}</Tag>
          </div>
          <p class="school-desc">{{ school.remark || '—' }}</p>
          <div class="school-foot">
            <span>📍 {{ school.province }}{{ school.city }}</span>
            <span>在培 {{ school.studentCount ?? 0 }} 人</span>
          </div>
        </article>
      </div>
      <Empty v-else description="暂无合作院校" />
    </section>

    <section class="block">
      <div class="block-head">
        <h2>定向培训班</h2>
      </div>
      <Table
        row-key="id"
        :data-source="trainClasses"
        :loading="classLoading"
        :columns="classColumns"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'type'">
            {{ TRAIN_CLASS_TYPE_TEXT[record.type] || '定向班' }}
          </template>
          <template v-else-if="column.key === 'status'">
            <Tag :color="trainClassStatusColor(record.status)">
              {{ trainClassStatusText(record.status) }}
            </Tag>
          </template>
        </template>
        <template #emptyText>
          <Empty description="暂无定向培训班" />
        </template>
      </Table>
    </section>
  </div>
</template>

<style scoped>
.coop {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding-top: 18px;
}

.banner {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 20px 24px;
  background: linear-gradient(120deg, #eef4ff, #f7fbff);
  border: 1px solid #dbe5f4;
  border-radius: 12px;
}

.banner h2 {
  margin: 0;
  font-size: 18px;
}

.banner p {
  max-width: 760px;
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

.school-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.school {
  padding: 16px;
  background: #fff;
  border: 1px solid #e6ebf3;
  border-radius: 10px;
}

.school-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.school-head strong {
  color: #182742;
  font-size: 15px;
}

.school-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 10px 0;
}

.school-desc {
  min-height: 40px;
  margin: 0 0 10px;
  color: #78869c;
  font-size: 12px;
  line-height: 1.6;
}

.school-foot {
  display: flex;
  justify-content: space-between;
  color: #4d83ed;
  font-size: 12px;
}

@media (max-width: 1100px) {
  .school-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .school-grid {
    grid-template-columns: 1fr;
  }

  .stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
