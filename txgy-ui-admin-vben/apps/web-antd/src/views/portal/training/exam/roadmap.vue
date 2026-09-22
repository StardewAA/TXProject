<script lang="ts" setup>
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Button } from 'ant-design-vue';

import {
  LEVEL_INTRO,
  LEVEL_PREREQ,
  ROADMAP_GROUPS,
  ROADMAP_LEVELS,
} from './roadmap-data';

defineOptions({ name: 'PortalTrainingExamRoadmap' });

const router = useRouter();

const activeGroupKey = ref(ROADMAP_GROUPS[0]!.key);
const activeJobName = ref(ROADMAP_GROUPS[0]!.jobs[0]!.name);
const expandedKey = ref(ROADMAP_GROUPS[0]!.key);

const activeGroup = computed(
  () => ROADMAP_GROUPS.find((g) => g.key === activeGroupKey.value) ?? ROADMAP_GROUPS[0]!,
);

const activeJob = computed(
  () => activeGroup.value.jobs.find((j) => j.name === activeJobName.value) ?? activeGroup.value.jobs[0]!,
);

const activeLevels = computed(
  () => ROADMAP_LEVELS.slice(0, ROADMAP_LEVELS.indexOf(activeJob.value.top as any) + 1),
);

function toggleGroup(key: string) {
  if (expandedKey.value === key) {
    expandedKey.value = '';
    return;
  }
  expandedKey.value = key;
  selectGroup(key);
}

function selectGroup(key: string) {
  activeGroupKey.value = key;
  expandedKey.value = key;
  const g = ROADMAP_GROUPS.find((x) => x.key === key);
  activeJobName.value = g?.jobs[0]?.name ?? '';
}

function goBack() {
  router.push('/portal/training/exam');
}

/** 查看详情 → 按当前技术方向（工种）进入认证详情 */
function goDetail(lv: string) {
  router.push({
    path: '/portal/training/exam/cert-detail',
    query: { field: activeGroup.value.key, job: activeJob.value.name, level: lv },
  });
}

function scrollToLevel(i: number) {
  document.getElementById(`level-${i}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}
</script>

<template>
  <div class="roadmap">
    <nav class="crumb">
      <span @click="goBack">认证</span>
      <em>›</em>
      <span class="on">职业认证进阶路径</span>
    </nav>

    <div class="page-head">
      <div class="page-head-text">
        <h1 class="page-title">技术方向</h1>
        <p class="page-desc">
          覆盖五大岗位序列人员，沿初级工、中级工、高级工、技师、高级技师五个等级逐级进阶，
          每一级均配套体系化培训课程与认证考试，助力技能人才成长。
        </p>
      </div>
      <Button class="back-btn" shape="round" @click="goBack">返回职业认证 →</Button>
    </div>

    <div class="layout">
      <aside class="side">
        <div v-for="g in ROADMAP_GROUPS" :key="g.key" class="group">
          <p
            :class="['group-name', { on: activeGroupKey === g.key, open: expandedKey === g.key }]"
            @click="toggleGroup(g.key)"
          >
            <span>{{ g.name }}</span>
            <em class="arrow">›</em>
          </p>
          <div v-show="expandedKey === g.key" class="job-list">
            <a
              v-for="j in g.jobs"
              :key="j.name"
              :class="['job', { on: activeGroupKey === g.key && activeJobName === j.name }]"
              @click="selectGroup(g.key); activeJobName = j.name"
            >{{ j.name }}</a>
          </div>
        </div>
      </aside>

      <main class="main">
        <h2 class="job-title">{{ activeJob.name }}</h2>
        <p class="job-skill">关键技能：{{ activeJob.skill }}</p>

        <div class="level-banner">
          <template v-for="(lv, i) in activeLevels" :key="lv">
            <a
              :class="['level-card', `t${i + 1}`]"
              :href="`#level-${i + 1}`"
              @click.prevent="scrollToLevel(i + 1)"
            >
              <img :src="`/level/l${i + 1}.png`" :alt="lv" />
              <span class="level-card-name">{{ lv }}</span>
            </a>
            <span v-if="i < activeLevels.length - 1" class="banner-arrow">→</span>
          </template>
        </div>

        <div class="cards">
          <div
            v-for="(lv, i) in activeLevels"
            :id="`level-${i + 1}`"
            :key="lv"
            class="cert-card"
            style="scroll-margin-top: 24px"
          >
            <h3 class="cert-name">{{ lv }}</h3>
            <p class="cert-desc">{{ LEVEL_INTRO[lv]?.(activeJob.skill) }}</p>
            <a class="detail" @click="goDetail(lv)">查看详情 »</a>
            <div class="prereq">
              <span class="prereq-label">认证前提:</span>
              <p>{{ LEVEL_PREREQ[lv] }}</p>
            </div>
          </div>
        </div>

      </main>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.roadmap {
  max-width: 1200px;
  padding: 0 0 32px;
}

.crumb {
  margin-bottom: 8px;
  color: #8c8c8c;
  font-size: 13px;

  span {
    cursor: pointer;

    &:hover {
      color: #1257c9;
    }
  }

  em {
    margin: 0 6px;
    font-style: normal;
  }

  .on {
    color: #262626;
    cursor: default;
  }
}

.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

.page-head-text {
  min-width: 0;
}

.back-btn {
  flex-shrink: 0;
  color: #1257c9;
  background: #eef4ff;
  border: 1px solid #bcd4ff;

  &:hover {
    color: #fff;
    background: #1257c9;
    border-color: #1257c9;
  }
}

.page-title {
  margin: 12px 0 10px;
  font-size: 30px;
  font-weight: 700;
  color: #1f2329;
  text-align: left;
}

.page-desc {
  margin: 0 0 24px;
  color: #646a73;
  font-size: 14px;
  line-height: 1.8;
  text-align: left;
}

.layout {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.side {
  position: sticky;
  top: 16px;
  flex-shrink: 0;
  width: 230px;
  max-height: calc(100vh - 32px);
  padding: 12px;
  overflow-y: auto;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 2px 12px rgb(15 34 67 / 8%);
}

.group + .group {
  margin-top: 14px;
  padding-top: 10px;
  border-top: 1px solid #f0f0f0;
}

.group-name {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 0 6px;
  padding: 6px 10px;
  font-size: 15px;
  font-weight: 700;
  color: #1f2329;
  cursor: pointer;
  user-select: none;
  border-radius: 6px;

  &:hover {
    background: #f5f7fa;
  }

  &.on {
    color: #1257c9;
    background: #eef4ff;
  }

  .arrow {
    color: #8c8c8c;
    font-size: 14px;
    font-style: normal;
    transition: transform 0.2s;
  }

  &.open .arrow {
    transform: rotate(90deg);
  }
}

.job-list {
  overflow: hidden;
}

.job {
  display: block;
  padding: 5px 10px 5px 18px;
  color: #595959;
  font-size: 13px;
  cursor: pointer;
  border-radius: 6px;

  &:hover {
    color: #1257c9;
    background: #f5f7fa;
  }

  &.on {
    color: #1257c9;
    font-weight: 600;
    background: #eef4ff;
  }
}

.main {
  flex: 1;
  min-width: 0;
  padding: 24px;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 2px 12px rgb(15 34 67 / 8%);
}

.job-title {
  margin: 0 0 4px;
  font-size: 24px;
  font-weight: 700;
  color: #c7000b;
  text-align: center;
}

.job-skill {
  margin: 0 0 20px;
  color: #8c8c8c;
  font-size: 13px;
  text-align: center;
}

.level-banner {
  display: flex;
  gap: 10px;
  align-items: stretch;
  margin-bottom: 22px;
}

.level-card {
  position: relative;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  cursor: pointer;
  border-radius: 8px;

  img {
    display: block;
    width: 100%;
    height: 110px;
    object-fit: cover;
    transition: transform 0.3s;
  }

  &:hover img {
    transform: scale(1.05);
  }

  .level-card-name {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-size: 20px;
    font-weight: 700;
    letter-spacing: 2px;
    text-shadow: 0 2px 8px rgb(0 0 0 / 45%);
  }

  /* 各等级配色蒙层，与华为五色风格呼应 */
  &::after {
    position: absolute;
    inset: 0;
    content: '';
    pointer-events: none;
  }

  &.t1::after {
    background: linear-gradient(160deg, rgb(18 87 201 / 35%), rgb(10 42 107 / 35%));
  }

  &.t2::after {
    background: linear-gradient(160deg, rgb(38 166 91 / 35%), rgb(15 90 50 / 35%));
  }

  &.t3::after {
    background: linear-gradient(160deg, rgb(0 150 160 / 35%), rgb(0 90 100 / 35%));
  }

  &.t4::after {
    background: linear-gradient(160deg, rgb(230 145 30 / 35%), rgb(160 90 10 / 35%));
  }

  &.t5::after {
    background: linear-gradient(160deg, rgb(130 80 200 / 35%), rgb(70 40 130 / 35%));
  }
}

.banner-arrow {
  align-self: center;
  flex-shrink: 0;
  color: #8c8c8c;
  font-size: 20px;
}

.cards {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.cert-card {
  padding: 18px;
  background: #fafbfc;
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  transition: box-shadow 0.2s, border-color 0.2s;

  &:hover {
    border-color: #bcd4ff;
    box-shadow: 0 6px 18px rgb(15 34 67 / 10%);
  }
}

.cert-name {
  margin: 0 0 8px;
  font-size: 19px;
  font-weight: 700;
  color: #c7000b;
}

.cert-desc {
  margin: 0 0 10px;
  color: #595959;
  font-size: 13px;
  line-height: 1.8;
}

.detail {
  display: block;
  margin-bottom: 12px;
  color: #1257c9;
  font-size: 13px;
  text-align: right;
  cursor: pointer;

  &:hover {
    text-decoration: underline;
  }
}

.prereq {
  padding-top: 10px;
  border-top: 1px dashed #e6e6e6;

  .prereq-label {
    color: #c7000b;
    font-size: 13px;
    font-weight: 600;
  }

  p {
    margin: 4px 0 0;
    color: #8c8c8c;
    font-size: 13px;
  }
}

.actions {
  margin-top: 24px;
  text-align: center;
}
</style>
