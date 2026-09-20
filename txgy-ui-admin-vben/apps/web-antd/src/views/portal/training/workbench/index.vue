<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Button, Empty, Progress, Tag } from 'ant-design-vue';

import { getMyCourses } from '../learning/api';
import { TRAINING_LEVELS, useTrainingDirection } from '../use-training-direction';
import AcademyHome from './academy-home.vue';

defineOptions({ name: 'PortalTrainingWorkbench' });

const router = useRouter();
const { state, levelName, load: loadDirection } = useTrainingDirection();

const loading = ref(false);
const group = ref<Record<string, any>>({});

/** 学习中 + 待学习，按最近优先取前 5 门作为「继续学习」 */
const continueList = computed(() =>
  [...(group.value.learningCourses ?? []), ...(group.value.todoCourses ?? [])].slice(
    0,
    5,
  ),
);

const completion = computed(() => {
  const total = group.value.totalCount ?? 0;
  if (!total) return 0;
  return Math.round(((group.value.completedCount ?? 0) / total) * 100);
});

const directionText = computed(() =>
  state.value.selected.length ? state.value.selected.join('、') : '尚未选择学习方向',
);

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
  } catch {
    group.value = {};
  } finally {
    loading.value = false;
  }
}

function goLearning() {
  router.push('/portal/training/learning');
}

function goCourses() {
  router.push('/portal/training/courses');
}

onMounted(() => {
  loadDirection();
  load();
});
</script>

<template>
  <AcademyHome />
  <div v-if="false" class="workbench">
    <section class="stats">
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

    <section class="block">
      <div class="block-head">
        <h2>继续学习</h2>
        <Button type="link" size="small" @click="goLearning">全部课程</Button>
      </div>
      <div v-if="continueList.length" class="course-grid">
        <div v-for="item in continueList" :key="item.courseId" class="course-card">
          <div class="course-cover">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <path d="M8 5.5v13l10-6.5z" />
            </svg>
            <em>{{ item.credit ?? 0 }} 学时</em>
          </div>
          <div class="course-body">
            <h3>{{ item.title }}</h3>
            <p>
              {{ item.lecturer || '讲师待配置' }} · 已完成
              {{ item.doneCount ?? 0 }}/{{ item.totalCount ?? 0 }} 项
            </p>
            <Progress
              class="course-progress"
              :percent="Number(item.progressPercent ?? 0)"
              size="small"
            />
            <footer>
              <Tag :color="statusColor(item.status)">{{ statusText(item.status) }}</Tag>
              <Button size="small" type="primary" @click="goLearning">
                {{ item.status === 1 ? '继续学习' : '开始学习' }}
              </Button>
            </footer>
          </div>
        </div>
      </div>
      <Empty v-else description="暂无进行中的课程，去课程中心挑一门吧">
        <Button type="primary" @click="goCourses">浏览课程</Button>
      </Empty>
    </section>

    <section class="block">
      <div class="block-head">
        <h2>成长进度</h2>
        <span class="hint">当前方向：{{ directionText }}</span>
      </div>
      <div class="growth">
        <div class="growth-progress">
          <Progress type="circle" :percent="completion" :size="92" />
          <div class="growth-text">
            <div class="growth-title">课程完成率 {{ completion }}%</div>
            <div class="growth-sub">
              已完成 {{ group.completedCount ?? 0 }} / {{ group.totalCount ?? 0 }} 门课程
            </div>
          </div>
        </div>
        <ol class="ladder">
          <li
            v-for="(name, index) in TRAINING_LEVELS"
            :key="name"
            :class="{ now: index === state.level, passed: index < state.level }"
          >
            <span class="dot">{{ index + 1 }}</span>
            {{ name }}
            <em v-if="index === state.level">{{ levelName }}</em>
          </li>
        </ol>
      </div>
    </section>
  </div>
</template>

<style scoped>
.workbench {
  display: flex;
  flex-direction: column;
  gap: 22px;
  padding-top: 20px;
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
  color: var(--c-icon, #005c9f);
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
  --c-icon: #005c9f;
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
  padding: 20px 0;
  border-top: 1px solid #e6ebf3;
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
  background: #005c9f;
  border-radius: 2px;
  content: '';
}

.hint {
  color: #7d899f;
  font-size: 12px;
}

.course-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(282px, 1fr));
  gap: 24px;
}

.course-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgb(16 24 40 / 8%);
  transition: transform 0.18s, box-shadow 0.18s;
}

.course-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 26px rgb(31 72 135 / 18%);
}

.course-cover {
  position: relative;
  display: grid;
  height: 158px;
  place-items: center;
  color: #b6c4d6;
  background: #f9f9f9;
}

.course-cover svg {
  width: 42px;
  height: 42px;
}

.course-cover em {
  position: absolute;
  right: 10px;
  bottom: 10px;
  padding: 2px 8px;
  color: #fff;
  font-size: 12px;
  font-style: normal;
  background: rgb(0 0 0 / 45%);
  border-radius: 4px;
}

.course-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  padding: 16px;
}

.course-body h3 {
  display: -webkit-box;
  min-height: 48px;
  margin: 0;
  color: #333;
  font-size: 16px;
  font-weight: 700;
  line-height: 24px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.course-body p {
  margin: 10px 0 0;
  color: #98a2b3;
  font-size: 12px;
}

.course-body footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: auto;
  padding-top: 14px;
}

.course-progress {
  margin-top: 12px;
}

.growth {
  display: grid;
  grid-template-columns: minmax(220px, 280px) 1fr;
  gap: 28px;
  align-items: center;
  padding: 22px 24px;
  background: #fff;
  border: 1px solid #eaeef5;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgb(16 24 40 / 4%);
}

.growth-progress {
  display: flex;
  gap: 16px;
  align-items: center;
}

.growth-title {
  font-weight: 600;
}

.growth-sub {
  margin-top: 4px;
  color: #7d899f;
  font-size: 12px;
}

.ladder {
  display: flex;
  padding: 0;
  margin: 0;
  list-style: none;
  border-top: 2px solid #ccd6eb;
}

.ladder li {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 6px;
  align-items: center;
  padding-top: 14px;
  color: #8592a8;
  font-size: 13px;
}

.ladder li .dot {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  margin-top: -24px;
  font-size: 12px;
  background: #fff;
  border: 2px solid #ccd6eb;
  border-radius: 50%;
}

.ladder li.passed .dot {
  color: #fff;
  background: #005c9f;
  border-color: #005c9f;
}

.ladder li.now {
  color: #005c9f;
  font-weight: 700;
}

.ladder li.now .dot {
  color: #fff;
  background: #005c9f;
  border-color: #bcd2ff;
  box-shadow: 0 0 0 4px rgb(0 92 159 / 15%);
}

.ladder li em {
  color: #005c9f;
  font-size: 11px;
  font-style: normal;
}

@media (max-width: 1100px) {
  .growth {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
