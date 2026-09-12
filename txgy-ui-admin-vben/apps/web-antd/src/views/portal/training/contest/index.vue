<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';

import { Empty, Table, Tag } from 'ant-design-vue';

import { getAwardPage, getContestPage, getWorkPage } from '../../../training/cooperation/api';
import {
  AWARD_LEVEL_TEXT,
  CONTEST_TYPE_TEXT,
  contestStatusColor,
  contestStatusText,
  WORK_TYPE_TEXT,
} from '../../../training/cooperation/constants';

defineOptions({ name: 'PortalTrainingContest' });

const contestLoading = ref(false);
const contests = ref<Record<string, any>[]>([]);
const awards = ref<Record<string, any>[]>([]);
const works = ref<Record<string, any>[]>([]);

const openSignCount = computed(
  () => contests.value.filter((item) => item.status === 1).length,
);
const publishedAwards = computed(() =>
  awards.value.filter((item) => item.published === 1),
);

const awardColumns = [
  { title: '大赛名称', dataIndex: 'contestName', key: 'contestName' },
  { title: '获奖作品', dataIndex: 'workTitle', key: 'workTitle' },
  { title: '获奖人', dataIndex: 'studentName', key: 'studentName', width: 120 },
  { title: '院校', dataIndex: 'schoolName', key: 'schoolName', width: 200 },
  { title: '获奖等级', key: 'level', width: 110, align: 'center' },
  { title: '综合得分', dataIndex: 'avgScore', key: 'avgScore', width: 100, align: 'center' },
];

const workColumns = [
  { title: '成果名称', dataIndex: 'title', key: 'title' },
  { title: '成果形态', key: 'type', width: 120, align: 'center' },
  { title: '归属赛事', dataIndex: 'contestName', key: 'contestName' },
  { title: '完成人', dataIndex: 'owner', key: 'owner', width: 120 },
  { title: '年度', dataIndex: 'year', key: 'year', width: 90, align: 'center' },
];

async function load() {
  contestLoading.value = true;
  try {
    const [contestRes, awardRes, workRes] = await Promise.all([
      getContestPage({ pageNo: 1, pageSize: 100 }),
      getAwardPage({ pageNo: 1, pageSize: 100 }),
      getWorkPage({ pageNo: 1, pageSize: 100 }),
    ]);
    contests.value = contestRes?.list ?? [];
    awards.value = awardRes?.list ?? [];
    works.value = workRes?.list ?? [];
  } catch {
    contests.value = [];
    awards.value = [];
    works.value = [];
  } finally {
    contestLoading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="contest">
    <section class="banner">
      <div>
        <h2>创新大赛专区</h2>
        <p>
          面向企业员工与校企合作院校学生的创新大赛、技能比武与工艺技术赛平台，
          支持在线报名、作品提交、专家评审、获奖公示与成果入库。
        </p>
      </div>
      <Tag color="purple">以赛促学</Tag>
    </section>

    <section class="stats">
      <div class="stat stat--purple">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M7 4h10v4.5a5 5 0 0 1-10 0z" />
            <path d="M7 5H4.5a2.5 2.5 0 0 0 2.5 4M17 5h2.5a2.5 2.5 0 0 1-2.5 4" />
            <path d="M12 13.5V17M8.5 20.5h7" />
          </svg>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ contests.length }}<span class="stat-unit">项</span></div>
          <div class="stat-label">大赛 / 赛事总数</div>
        </div>
      </div>

      <div class="stat stat--blue">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M4 20l1-4L16.5 4.5a2.1 2.1 0 0 1 3 3L8 19z" />
            <path d="M14.5 6.5l3 3" />
          </svg>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ openSignCount }}<span class="stat-unit">项</span></div>
          <div class="stat-label">报名中赛事</div>
        </div>
      </div>

      <div class="stat stat--orange">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="14.5" r="5.5" />
            <path d="M12 11.8l1 2.1 2.3.3-1.7 1.6.4 2.3-2-1.1-2 1.1.4-2.3-1.7-1.6 2.3-.3z" />
            <path d="M8.5 4L10 8.5M15.5 4L14 8.5" />
          </svg>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ publishedAwards.length }}<span class="stat-unit">条</span></div>
          <div class="stat-label">获奖公示</div>
        </div>
      </div>

      <div class="stat stat--green">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
          </svg>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ works.length }}<span class="stat-unit">项</span></div>
          <div class="stat-label">成果库成果</div>
        </div>
      </div>
    </section>

    <section class="block">
      <div class="block-head">
        <h2>赛事列表</h2>
      </div>
      <div v-if="contests.length" class="contest-grid">
        <article v-for="item in contests" :key="item.id" class="contest-card">
          <div class="contest-head">
            <strong>{{ item.name }}</strong>
            <Tag :color="contestStatusColor(item.status)">
              {{ contestStatusText(item.status) }}
            </Tag>
          </div>
          <div class="contest-tags">
            <Tag color="geekblue">{{ CONTEST_TYPE_TEXT[item.type] || '赛事' }}</Tag>
            <Tag v-if="item.orgName">{{ item.orgName }}</Tag>
          </div>
          <p class="contest-theme">{{ item.theme || item.targetRange || '—' }}</p>
          <div class="contest-foot">
            <span>比赛日：{{ item.contestDate || '待定' }}</span>
            <span>报名：{{ item.signStart || '—' }} ~ {{ item.signEnd || '—' }}</span>
          </div>
        </article>
      </div>
      <Empty v-else description="暂无赛事" />
    </section>

    <section class="block">
      <div class="block-head">
        <h2>获奖公示</h2>
      </div>
      <Table
        row-key="id"
        :data-source="publishedAwards"
        :loading="contestLoading"
        :columns="awardColumns"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'level'">
            <Tag :color="record.level === 1 ? 'gold' : record.level === 2 ? 'silver' : 'orange'">
              {{ AWARD_LEVEL_TEXT[record.level] || '获奖' }}
            </Tag>
          </template>
        </template>
        <template #emptyText>
          <Empty description="暂无获奖公示" />
        </template>
      </Table>
    </section>

    <section class="block">
      <div class="block-head">
        <h2>大赛成果库</h2>
      </div>
      <Table
        row-key="id"
        :data-source="works"
        :loading="contestLoading"
        :columns="workColumns"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'type'">
            {{ WORK_TYPE_TEXT[record.type] || '成果' }}
          </template>
        </template>
        <template #emptyText>
          <Empty description="暂无成果" />
        </template>
      </Table>
    </section>
  </div>
</template>

<style scoped>
.contest {
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
  background: linear-gradient(120deg, #f3eeff, #f8f7ff);
  border: 1px solid #e3dbf6;
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
  background: #7c3aed;
  border-radius: 2px;
  content: '';
}

.contest-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.contest-card {
  padding: 16px;
  background: #fff;
  border: 1px solid #e6ebf3;
  border-radius: 10px;
}

.contest-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.contest-head strong {
  color: #182742;
  font-size: 15px;
}

.contest-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 10px 0;
}

.contest-theme {
  min-height: 40px;
  margin: 0 0 10px;
  color: #78869c;
  font-size: 12px;
  line-height: 1.6;
}

.contest-foot {
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: #7c3aed;
  font-size: 12px;
}

@media (max-width: 1100px) {
  .contest-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .contest-grid {
    grid-template-columns: 1fr;
  }

  .stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
