<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';

import { Button, Empty, Progress, Table, Tag, message } from 'ant-design-vue';

import { getMySummary, getVrRecords } from '../learning/api';

defineOptions({ name: 'PortalTrainingVr' });

/** VR 实训场景库（由后台「VR 资源项目管理」下发，前端先按行业工艺预置展示） */
const VR_SCENES = [
  {
    code: 'VR-01',
    name: '储罐内壁防腐喷涂',
    mode: '沉浸式操作',
    minutes: 45,
    level: '中级工',
    desc: '在虚拟储罐内完成表面处理、底漆与面漆喷涂全流程演练。',
  },
  {
    code: 'VR-02',
    name: '管道保温层包覆',
    mode: '沉浸式操作',
    minutes: 40,
    level: '初级工',
    desc: '按工艺要求完成岩棉/聚氨酯保温层与金属保护壳包覆。',
  },
  {
    code: 'VR-03',
    name: '受限空间作业安全演练',
    mode: '安全演练',
    minutes: 30,
    level: '学徒',
    desc: '模拟储罐/塔器内部作业的气体检测、通风与监护人流程。',
  },
  {
    code: 'VR-04',
    name: '高处脚手架搭设与验收',
    mode: '安全演练',
    minutes: 35,
    level: '中级工',
    desc: '演练脚手架搭设、剪刀撑布置与挂牌验收要点。',
  },
];

const summary = ref<Record<string, any>>({});
const records = ref<any[]>([]);
const loading = ref(false);

const durationText = computed(() => {
  const total = Number(summary.value.totalValidSeconds ?? 0);
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  return h > 0 ? `${h} 小时 ${m} 分` : `${m} 分`;
});

const scoreColumns = [
  { title: '实训场景', dataIndex: 'sceneName', key: 'sceneName' },
  { title: '关联课程', dataIndex: 'courseTitle', key: 'courseTitle' },
  { title: '成绩', dataIndex: 'score', key: 'score', width: 90, align: 'center' },
  { title: '有效时长', key: 'duration', width: 120, align: 'center' },
  { title: '状态', key: 'completed', width: 100, align: 'center' },
];

function formatSeconds(seconds?: number) {
  const total = Number(seconds ?? 0);
  const m = Math.floor(total / 60);
  const s = total % 60;
  return m > 0 ? `${m} 分 ${s} 秒` : `${s} 秒`;
}

function startScene(scene: Record<string, any>) {
  message.info(`「${scene.name}」VR 实训舱正在联调，开通后可一键进入沉浸式实训。`);
}

async function load() {
  loading.value = true;
  try {
    const [s, r] = await Promise.all([getMySummary(), getVrRecords()]);
    summary.value = s ?? {};
    records.value = r ?? [];
  } catch {
    summary.value = {};
    records.value = [];
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="vr">
    <section class="banner">
      <div>
        <h2>VR 虚拟仿真实操训练</h2>
        <p>
          用虚拟仿真还原防腐保温一线工序与高危作业场景，支持零风险反复练习，
          成绩同步至个人学习档案。
        </p>
      </div>
      <Tag color="geekblue">沉浸式实训</Tag>
    </section>

    <section class="stats">
      <div class="stat stat--blue">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="9" />
            <path d="M12 7v5l3.5 2" />
          </svg>
        </div>
        <div class="stat-info">
          <div class="stat-value stat-value--sm">{{ durationText }}</div>
          <div class="stat-label">累计有效学习时长</div>
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
          <div class="stat-value">{{ summary.taskCompletedCount ?? 0 }}<span class="stat-unit">门</span></div>
          <div class="stat-label">已完成课程</div>
        </div>
      </div>

      <div class="stat stat--orange">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <rect x="2.5" y="8" width="19" height="8.5" rx="2.5" />
            <path d="M8.5 16.5L7 20M15.5 16.5L17 20" />
          </svg>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ records.length }}<span class="stat-unit">条</span></div>
          <div class="stat-label">VR 实训记录</div>
        </div>
      </div>

      <div class="stat stat--purple">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 3l2.4 5.8 6.3.6-4.8 4.2 1.5 6.2L12 16.9 6.6 19.8 8.1 13.6 3.3 9.4l6.3-.6z" />
          </svg>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ summary.totalCreditEarned ?? 0 }}<span class="stat-unit">学时</span></div>
          <div class="stat-label">已获实训学时</div>
        </div>
      </div>
    </section>

    <section class="block">
      <div class="block-head">
        <h2>实训场景</h2>
        <span class="hint">共 {{ VR_SCENES.length }} 个场景</span>
      </div>
      <div class="scene-grid">
        <article v-for="scene in VR_SCENES" :key="scene.code" class="scene">
          <div class="scene-cover">
            <span>{{ scene.code }}</span>
            <Tag color="blue">{{ scene.mode }}</Tag>
          </div>
          <div class="scene-body">
            <h3>{{ scene.name }}</h3>
            <p>{{ scene.desc }}</p>
            <div class="scene-meta">
              <span>建议等级：{{ scene.level }}</span>
              <span>约 {{ scene.minutes }} 分钟</span>
            </div>
            <Button type="primary" size="small" @click="startScene(scene)">
              开始实训
            </Button>
          </div>
        </article>
      </div>
    </section>

    <section class="block">
      <div class="block-head">
        <h2>我的实训记录</h2>
      </div>
      <Table
        row-key="id"
        :data-source="records"
        :loading="loading"
        :columns="scoreColumns"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'duration'">
            {{ formatSeconds(record.validSeconds) }}
          </template>
          <template v-else-if="column.key === 'completed'">
            <Tag :color="record.completed ? 'green' : 'default'">
              {{ record.completed ? '已达标' : '进行中' }}
            </Tag>
          </template>
        </template>
        <template #emptyText>
          <Empty description="暂无 VR 实训记录，完成一次实训后自动同步" />
        </template>
      </Table>
    </section>
  </div>
</template>

<style scoped>
.vr {
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

.scene-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.scene {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
  border: 1px solid #dbe5f4;
  border-radius: 10px;
}

.scene-cover {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  height: 84px;
  padding: 12px;
  color: #2f6bff;
  font-family: ui-monospace, monospace;
  font-weight: 700;
  background: linear-gradient(135deg, #e3edff, #cddffb);
}

.scene-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  padding: 14px;
}

.scene-body h3 {
  margin: 0;
  font-size: 15px;
}

.scene-body p {
  flex: 1;
  margin: 8px 0 12px;
  color: #78869c;
  font-size: 12px;
  line-height: 1.6;
}

.scene-meta {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
  color: #4d83ed;
  font-size: 12px;
}

@media (max-width: 1280px) {
  .scene-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
