<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';
import { useUserStore } from '@vben/stores';

import {
  getPortalRecruitApplicationPage,
} from '#/api/hrm/portal/recruit';
import { getPortalResumeList, type HrmPortalResumeApi } from '#/api/hrm/portal/resume';

const router = useRouter();
const userStore = useUserStore();
const loading = ref(true);
const resumes = ref<HrmPortalResumeApi.Resume[]>([]);
const applicationTotal = ref(0);

const userInfo = computed(() => userStore.userInfo);
const displayName = computed(
  () => userInfo.value?.nickname || userInfo.value?.username || '用户',
);
const resumeCount = computed(
  () => resumes.value.filter((resume) => resume.resumeType === 1).length,
);

const businessCards = computed(() => [
  {
    key: 'recruit',
    title: '我要找工作',
    eyebrow: 'HRM · CAREER',
    description: '管理简历、查看投递进度，把每一次职业机会掌握在手中。',
    stats: [{ value: String(applicationTotal.value), label: '次投递' }],
    action: '查看招聘进度',
    icon: 'lucide:briefcase-business',
    tone: 'recruit',
    path: '/portal/profile/recruit',
  },
  {
    key: 'training',
    title: '云培训学院',
    eyebrow: 'LEARNING · GROWTH',
    description: '进入课程、实操、考试与证书工作台，继续你的专业成长。',
    stats: [
      { value: '课程', label: '学习入口' },
      { value: '证书', label: '成长档案' },
    ],
    action: '进入云培训',
    icon: 'lucide:graduation-cap',
    tone: 'training',
    path: '/portal/training',
  },
  {
    key: 'bidding',
    title: '招标投标',
    eyebrow: 'PROJECT · PARTNERSHIP',
    description: '进入项目合作空间，后续可在这里跟进报名与投标进度。',
    stats: [
      { value: '项目', label: '合作入口' },
      { value: '投标', label: '服务入口' },
    ],
    action: '进入招投标',
    icon: 'lucide:landmark',
    tone: 'bidding',
    path: '/portal/bidding',
  },
]);

const todoItems = computed(() => [
  {
    title: resumeCount.value ? '简历资料已准备' : '完善一份求职简历',
    description: resumeCount.value
      ? `已保存 ${resumeCount.value} 份附件简历`
      : '上传附件后可直接投递岗位',
    action: '我要找工作',
    path: '/portal/profile/recruit',
    icon: resumeCount.value ? 'lucide:circle-check' : 'lucide:file-plus-2',
    done: resumeCount.value > 0,
  },
  {
    title: applicationTotal.value ? '查看招聘进度' : '探索新的岗位机会',
    description: applicationTotal.value
      ? `已有 ${applicationTotal.value} 条投递记录`
      : '浏览行业岗位并寻找合适机会',
    action: '去看看',
    path: applicationTotal.value ? '/portal/profile/recruit' : '/portal/recruit',
    icon: 'lucide:arrow-up-right',
    done: false,
  },
  {
    title: '继续专业成长',
    description: '课程、考试和证书请在云培训学院查看',
    action: '进入培训',
    path: '/portal/training',
    icon: 'lucide:sparkles',
    done: false,
  },
]);

function go(path: string) {
  void router.push(path);
}

async function loadOverview() {
  loading.value = true;
  try {
    const [resumeList, applicationPage] = await Promise.all([
      getPortalResumeList(),
      getPortalRecruitApplicationPage({ pageNo: 1, pageSize: 3 }),
    ]);
    resumes.value = resumeList || [];
    applicationTotal.value = applicationPage.total || 0;
  } catch (error) {
    resumes.value = [];
    applicationTotal.value = 0;
    console.warn('[PortalProfile] 加载工作台概览失败', error);
  } finally {
    loading.value = false;
  }
}

onMounted(loadOverview);
</script>

<template>
  <div class="portal-page workspace-page">
    <section class="workspace-hero">
      <div class="hero-identity">
        <div>
          <h1>你好，{{ displayName }}</h1>
          <p>把工作机会、专业成长和项目合作，收进一个清晰的工作台。</p>
        </div>
      </div>
    </section>
    <section class="business-grid" aria-label="三大业务概览">
      <button
        v-for="card in businessCards"
        :key="card.key"
        :class="['business-card', `business-card-${card.tone}`]"
        type="button"
        @click="go(card.path)"
      >
        <div class="card-topline">
          <span class="card-icon"><IconifyIcon :icon="card.icon" /></span>
          <span class="card-arrow"><IconifyIcon icon="lucide:arrow-up-right" /></span>
        </div>
        <p class="card-eyebrow">{{ card.eyebrow }}</p>
        <h3>{{ card.title }}</h3>
        <p class="card-description">{{ card.description }}</p>
        <div class="card-stats">
          <div v-for="stat in card.stats" :key="stat.label">
            <strong>{{ loading && card.key === 'recruit' ? '—' : stat.value }}</strong>
            <span>{{ stat.label }}</span>
          </div>
        </div>
        <span class="card-action">{{ card.action }} <IconifyIcon icon="lucide:arrow-right" /></span>
      </button>
    </section>

    <section class="workspace-columns">
      <div class="workspace-panel">
        <div class="panel-heading">
          <div>
            <p class="section-eyebrow">NEXT ACTIONS</p>
            <h2>我的待办 / 消息提醒</h2>
          </div>
          <span class="panel-count">{{ todoItems.length }} 项</span>
        </div>
        <div class="todo-list">
          <button v-for="item in todoItems" :key="item.title" class="todo-item" type="button" @click="go(item.path)">
            <span :class="['todo-icon', { done: item.done }]"><IconifyIcon :icon="item.icon" /></span>
            <span class="todo-copy"><strong>{{ item.title }}</strong><small>{{ item.description }}</small></span>
            <span class="todo-action">{{ item.action }} <IconifyIcon icon="lucide:chevron-right" /></span>
          </button>
        </div>
      </div>

    </section>

  </div>
</template>

<style scoped>
.workspace-page { gap: 28px; }
.workspace-hero { display: flex; min-height: 138px; align-items: center; padding: 30px 36px; border: 1px solid #f1b974; background: linear-gradient(120deg,#ef8b35 0%,#d86616 58%,#b94e13 100%); color:#fff; border:0; border-radius:18px; box-shadow:0 16px 32px rgb(183 92 24 / 22%); }
.hero-identity, .card-topline, .panel-heading, .todo-item { display: flex; align-items: center; }
.section-eyebrow, .card-eyebrow { margin: 0 0 7px; color: #d86616; font-size: 11px; font-weight: 700; letter-spacing: .12em; }
.workspace-hero h1, .section-intro h2, .panel-heading h2 { margin: 0; color: #4a2916; }
.workspace-hero h1 { color: #fff; }
.workspace-hero h1 { font-size: 30px; line-height: 1.25; }
.hero-identity p:last-child { margin: 10px 0 0; color: #fff4e5; font-size: 20px; }
.section-intro { display: flex; align-items: flex-end; gap: 20px; }
.section-intro h2, .panel-heading h2 { font-size: 21px; }
.section-intro > p { margin: 0; color: #94a3b8; font-size: 13px; }
.business-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); column-gap: 24px; row-gap: 30px; margin-top: 24px; margin-bottom: 28px; }
.business-card { position: relative; display: flex; min-height: 286px; padding: 24px; border: 1px solid #f0dac1; border-radius: 12px; background: #fff; color: inherit; cursor: pointer; flex-direction: column; align-items: flex-start; overflow: hidden; text-align: left; transition: border-color 180ms ease, box-shadow 180ms ease, transform 180ms ease; }
.business-card::after { position: absolute; right: 0; bottom: 0; left: 0; height: 4px; background: #e87522; content: ''; opacity: .9; }
.business-card::before { position: absolute; top: -72px; right: -54px; width: 150px; height: 150px; border-radius: 50%; background: #ffe0b5; content: ''; opacity: .45; }
.business-card-recruit { background: linear-gradient(145deg, #fff 18%, #fff4e4 100%); }
.business-card-training { background: linear-gradient(145deg, #fff 18%, #f4fffc 100%); }
.business-card-bidding { background: linear-gradient(145deg, #fff 18%, #fffaf0 100%); }
.business-card:hover, .business-card:focus-visible { border-color: #efb36a; box-shadow: 0 14px 30px rgb(183 92 24 / 14%); outline: none; transform: translateY(-3px); }
.business-card-training::after { background: #0f766e; }
.business-card-bidding::after { background: #d97706; }
.business-card-training::before { background: #ccfbf1; }
.business-card-bidding::before { background: #fef3c7; }
.card-topline { width: 100%; justify-content: space-between; }
.card-icon { position: relative; z-index: 1; display: grid; width: 46px; height: 46px; place-items: center; border-radius: 12px; color: #d86616; background: #fff0df; font-size: 23px; }
.business-card-training .card-icon { color: #0f766e; background: #ccfbf1; }
.business-card-bidding .card-icon { color: #b45309; background: #fef3c7; }
.card-arrow { color: #94a3b8; font-size: 19px; }
.card-eyebrow { margin-top: 26px; }
.business-card-training .card-eyebrow { color: #0f766e; }
.business-card-bidding .card-eyebrow { color: #b45309; }
.business-card h3 { margin: 0; color: #0f172a; font-size: 23px; }
.card-description { max-width: 290px; margin: 9px 0 0; color: #64748b; font-size: 13px; line-height: 1.75; }
.card-stats { display: flex; gap: 34px; margin-top: auto; padding-top: 22px; }
.card-stats div { display: grid; gap: 4px; }
.card-stats strong { color: #1e3a8a; font-size: 19px; }
.business-card-training .card-stats strong { color: #115e59; }
.business-card-bidding .card-stats strong { color: #92400e; }
.card-stats span { color: #94a3b8; font-size: 11px; }
.card-action { display: inline-flex; align-items: center; gap: 6px; margin-top: 20px; color: #d86616; font-size: 13px; font-weight: 650; }
.business-card-training .card-action { color: #0f766e; }
.business-card-bidding .card-action { color: #b45309; }
.workspace-columns { display: block; }
.workspace-panel { border: 1px solid #f0dac1; background: #fffdf9; }
.workspace-panel { padding: 24px; border-radius: 12px; }
.panel-heading { justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.panel-count { padding: 5px 9px; color: #c85e18; background: #fff0df; font-size: 12px; }
.todo-list { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); column-gap: 18px; row-gap: 26px; }
.todo-item { width: 100%; border: 0; background: transparent; color: inherit; cursor: pointer; text-align: left; }
.todo-item { min-height: 118px; gap: 11px; padding: 17px; border: 1px solid #f3dfc6; border-radius: 10px; background: #fffaf3; flex-direction: column; align-items: flex-start; transition: border-color 160ms ease, box-shadow 160ms ease, transform 160ms ease; }
.todo-item:hover, .todo-item:focus-visible { border-color: #efb36a; background: #fff; box-shadow: 0 8px 18px rgb(183 92 24 / 10%); outline: none; transform: translateY(-2px); }
.todo-icon { display: grid; flex-shrink: 0; place-items: center; }
.todo-icon { width: 34px; height: 34px; color: #d86616; background: #fff0df; }
.todo-icon.done { color: #c85e18; background: #ffe2bd; }
.todo-copy { display: grid; min-width: 0; gap: 4px; }
.todo-copy { width: 100%; flex: 1; }
.todo-copy strong { overflow: hidden; color: #5d341d; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.todo-copy small { overflow: hidden; color: #a48268; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.todo-action { display: inline-flex; align-items: center; gap: 3px; color: #d86616; font-size: 12px; white-space: nowrap; }
@media (max-width: 900px) { .business-grid, .todo-list { grid-template-columns: 1fr; } }
@media (max-width: 640px) { .workspace-hero, .section-intro { align-items: flex-start; flex-direction: column; } .workspace-hero { padding: 24px 20px; } .business-grid { grid-template-columns: 1fr; } .workspace-panel { padding: 20px 16px; } .todo-item { align-items: flex-start; } .todo-action { margin-left: auto; } }

.workspace-hero { background: linear-gradient(120deg, #fff 0%, #fffdfb 72%, #fff3e4 100%); color: #4a2916; border: 1px solid #f7e8d6; box-shadow: 0 12px 26px rgb(183 92 24 / 6%); }
.workspace-hero h1 { color: #4a2916; }
.hero-identity p:last-child { color: #806956; }
.business-card, .workspace-panel { border-color: #f5e5d3; background: #fff; }
.business-card-recruit { background: linear-gradient(145deg, #fff 0%, #fffaf4 100%); }
.business-card::after { background: linear-gradient(90deg, #f3c28b, #e87522); }
.business-card:hover, .business-card:focus-visible { border-color: #efcfaa; box-shadow: 0 10px 24px rgb(183 92 24 / 7%); }
.card-icon, .todo-icon { color: #d97831; background: #fff7ed; }
.card-action, .todo-action { color: #cf6b27; }
.todo-item { border-color: #f7eadc; background: #fffdfa; }
.todo-item:hover, .todo-item:focus-visible { border-color: #efd1af; box-shadow: 0 8px 18px rgb(183 92 24 / 6%); }
</style>
