<script lang="ts" setup>
import { useRouter } from 'vue-router';

import { useUserStore } from '@vben/stores';

const router = useRouter();
const userStore = useUserStore();

function openService(path: string) {
  if (path === '/portal/recruit') {
    window.open(router.resolve(path).href, '_blank', 'noopener,noreferrer');
    return;
  }
  void router.push(path);
}

const services = [
  {
    title: '我要找工作',
    description: '查看已发布岗位、在线投递并跟进招聘进度。',
    path: '/portal/recruit',
  },
  {
    title: '云培训学院',
    description: '浏览企业课程、学习计划和培训记录。',
    path: '/portal/training',
  },
  {
    title: '招标投标',
    description: '了解项目公告、投标流程和合作机会。',
    path: '/portal/bidding',
  },
];

const recruitServices = [
  {
    title: '浏览职位',
    description: '按职位、行业和工作地点筛选岗位机会。',
    query: { section: 'jobs' },
  },
  {
    title: '企业名录',
    description: '查看参与招聘的企业与在招职位。',
    query: { section: 'companies' },
  },
  {
    title: '招聘沟通',
    description: '查看招聘方消息并继续沟通。',
    query: { section: 'messages' },
  },
  {
    title: '在线简历',
    description: '在线完善个人资料和求职意向，投递时无需重复填写。',
    query: { section: 'resume' },
  },
  {
    title: '应聘记录',
    description: '跟进投递、面试和 offer 进度。',
    path: '/portal/profile/recruit',
  },
];

function openRecruitService(service: (typeof recruitServices)[number]) {
  if (service.path) {
    void router.push(service.path);
    return;
  }
  void router.push({ name: 'PortalRecruit', query: service.query });
}
</script>

<template>
  <div class="portal-page">
    <section class="welcome-banner">
      <p class="eyebrow">员工服务中心</p>
      <h1>
        欢迎回来，{{
          userStore.userInfo?.nickname || userStore.userInfo?.username || '用户'
        }}
      </h1>
    </section>

    <section class="section-block recruit-services">
      <div class="section-heading">
        <div>
          <h2>求职服务</h2>
        </div>
        <button class="section-action" type="button" @click="openService('/portal/recruit')">
          进入招聘中心
        </button>
      </div>
      <div class="recruit-service-grid">
        <button
          v-for="service in recruitServices"
          :key="service.title"
          class="recruit-service-item"
          type="button"
          @click="openRecruitService(service)"
        >
          <span class="recruit-service-title">{{ service.title }}</span>
          <span class="recruit-service-description">{{ service.description }}</span>
          <span class="recruit-service-link">立即进入 →</span>
        </button>
      </div>
    </section>

    <section class="section-block">
      <div class="section-heading">
        <h2>常用服务</h2>
        <span>快速入口</span>
      </div>
      <div class="service-grid">
        <button
          v-for="service in services"
          :key="service.path"
          class="service-item"
          type="button"
          @click="openService(service.path)"
        >
          <span class="service-title">{{ service.title }}</span>
          <span class="service-description">{{ service.description }}</span>
          <span class="service-link">进入服务 →</span>
        </button>
      </div>
    </section>

    <section class="section-block">
      <div class="section-heading">
        <h2>我的待办</h2>
        <span>近期事项</span>
      </div>
      <div class="empty-state">当前暂无待办事项</div>
    </section>
  </div>
</template>

<style scoped>
.portal-page {
  display: grid;
  gap: 24px;
}

.welcome-banner {
  padding: 32px 36px;
  border: 1px solid #f2c58e;
  border-radius: 16px;
  background: linear-gradient(120deg, #fffaf3, #fff0dc 56%, #ffe0b5);
  box-shadow: 0 14px 30px rgb(183 92 24 / 10%);
}

.eyebrow {
  margin: 0 0 8px;
  color: #d86616;
  font-size: 35px;
  font-weight: 600;
}

h1,
h2,
p {
  margin-top: 0;
}

h1 {
  margin-bottom: 8px;
  color: #4a2916;
  font-size: 20px;
  line-height: 1.25;
}

.welcome-banner > p:last-child {
  margin-bottom: 0;
  color: #806956;
  font-size: 14px;
}

.section-block {
  padding: 24px;
  border: 1px solid #f0dac1;
  border-radius: 14px;
  background: #fff;
}

.section-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 18px;
}

.section-heading h2 {
  margin-bottom: 0;
  color: #4a2916;
  font-size: 18px;
}

.section-heading span {
  color: #9ca3af;
  font-size: 13px;
}

.section-action {
  padding: 7px 12px;
  border: 0;
  border-radius: 6px;
  background: linear-gradient(135deg, #ef8b35, #d86616);
  color: #fff;
  box-shadow: 0 6px 12px rgb(216 102 22 / 18%);
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
}

.section-action:hover {
  background: linear-gradient(135deg, #d86616, #b94e13);
}

.service-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.recruit-service-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.recruit-service-item {
  display: flex;
  min-height: 142px;
  padding: 18px;
  border: 1px solid #f2d4ad;
  border-radius: 8px;
  background: linear-gradient(145deg, #fffaf3, #fff0df);
  color: inherit;
  cursor: pointer;
  flex-direction: column;
  align-items: flex-start;
  text-align: left;
}

.recruit-service-item:hover {
  border-color: #efb36a;
  box-shadow: 0 8px 18px rgb(183 92 24 / 12%);
}

.recruit-service-title {
  color: #5d341d;
  font-size: 15px;
  font-weight: 650;
}

.recruit-service-description {
  margin-top: 9px;
  color: #806956;
  font-size: 13px;
  line-height: 1.55;
}

.recruit-service-link {
  margin-top: auto;
  color: #d86616;
  font-size: 13px;
  font-weight: 600;
}

.service-item {
  display: flex;
  min-height: 160px;
  padding: 20px;
  border: 1px solid #f0dac1;
  border-radius: 12px;
  background: #fff;
  color: inherit;
  cursor: pointer;
  flex-direction: column;
  align-items: flex-start;
  text-align: left;
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease;
}

.service-item:hover {
  border-color: #efb36a;
  box-shadow: 0 4px 14px rgb(183 92 24 / 12%);
}

.service-title {
  color: #4a2916;
  font-size: 16px;
  font-weight: 650;
}

.service-description {
  margin-top: 10px;
  color: #806956;
  font-size: 13px;
  line-height: 1.6;
}

.service-link {
  margin-top: auto;
  padding-top: 18px;
  color: #d86616;
  font-size: 13px;
  font-weight: 600;
}

.empty-state {
  padding: 32px 16px;
  border: 1px dashed #e6b77e;
  border-radius: 10px;
  color: #a48268;
  font-size: 14px;
  text-align: center;
}

@media (max-width: 760px) {
  h1 {
    font-size: 26px;
  }

  .service-grid {
    grid-template-columns: 1fr;
  }

  .recruit-service-grid {
    grid-template-columns: 1fr;
  }
}

@media (min-width: 761px) and (max-width: 1100px) {
  .recruit-service-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

.welcome-banner { background: linear-gradient(120deg, #fff 0%, #fffdfb 68%, #fff7ed 100%); border-color: #f7e8d6; box-shadow: 0 10px 24px rgb(183 92 24 / 5%); }
.section-block { border-color: #f5eadf; background: #fff; }
.section-action { background: #fff; color: #cf6b27; box-shadow: none; }
.section-action:hover { background: #fffaf4; }
.recruit-service-item, .service-item { border-color: #f7eadc; background: #fff; }
.recruit-service-item:hover, .service-item:hover { border-color: #f2d5b7; box-shadow: 0 8px 18px rgb(183 92 24 / 6%); }
.recruit-service-link, .service-link { color: #cf6b27; }
.empty-state { border-color: #f3dfca; }
</style>
