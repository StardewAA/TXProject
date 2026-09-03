<script lang="ts" setup>
import { useRouter } from 'vue-router';

import { useUserStore } from '@vben/stores';

const router = useRouter();
const userStore = useUserStore();

const services = [
  {
    title: '招聘应聘',
    description: '查看岗位机会、提交应聘信息并跟进招聘进度。',
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
      <p>从这里快速访问平台服务，查看与你相关的工作信息。</p>
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
          @click="router.push(service.path)"
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
  border: 1px solid #dbeafe;
  background: #eff6ff;
}

.eyebrow {
  margin: 0 0 8px;
  color: #2563eb;
  font-size: 13px;
  font-weight: 600;
}

h1,
h2,
p {
  margin-top: 0;
}

h1 {
  margin-bottom: 8px;
  color: #111827;
  font-size: 32px;
  line-height: 1.25;
}

.welcome-banner > p:last-child {
  margin-bottom: 0;
  color: #4b5563;
  font-size: 14px;
}

.section-block {
  padding: 24px;
  border: 1px solid #e5e7eb;
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
  color: #111827;
  font-size: 18px;
}

.section-heading span {
  color: #9ca3af;
  font-size: 13px;
}

.service-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.service-item {
  display: flex;
  min-height: 160px;
  padding: 20px;
  border: 1px solid #e5e7eb;
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
  border-color: #93c5fd;
  box-shadow: 0 4px 14px rgb(37 99 235 / 10%);
}

.service-title {
  color: #1f2937;
  font-size: 16px;
  font-weight: 650;
}

.service-description {
  margin-top: 10px;
  color: #6b7280;
  font-size: 13px;
  line-height: 1.6;
}

.service-link {
  margin-top: auto;
  padding-top: 18px;
  color: #2563eb;
  font-size: 13px;
  font-weight: 600;
}

.empty-state {
  padding: 32px 16px;
  border: 1px dashed #d1d5db;
  color: #9ca3af;
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
}
</style>
