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
    title: 'CMS系统',
    description: '检索已发布法规，或提交内容申请审查发布。',
    path: '/portal/content',
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
  gap: 26px;
}

.welcome-banner {
  position: relative;
  min-height: 228px;
  padding: 42px 46px;
  overflow: hidden;
  color: #fff;
  background:
    radial-gradient(circle at 88% 0%, rgb(201 169 97 / 22%), transparent 34%),
    linear-gradient(125deg, #14243f, #1a2b4a 62%, #2c4470);
  border: 1px solid rgb(255 255 255 / 12%);
  border-radius: 26px;
  box-shadow: 0 26px 64px rgb(26 43 74 / 20%);
}

.welcome-banner::before {
  position: absolute;
  top: -118px;
  right: -36px;
  width: 300px;
  height: 300px;
  content: '';
  border: 1px solid rgb(255 255 255 / 13%);
  border-radius: 50%;
  box-shadow:
    0 0 0 48px rgb(255 255 255 / 3%),
    0 0 0 96px rgb(255 255 255 / 2%);
}

.welcome-banner > * {
  position: relative;
  z-index: 1;
}

.eyebrow {
  display: inline-flex;
  padding: 7px 12px;
  margin: 0 0 18px;
  font-size: 11px;
  font-weight: 650;
  color: #dfc48a;
  letter-spacing: 0.08em;
  background: rgb(255 255 255 / 9%);
  border: 1px solid rgb(255 255 255 / 15%);
  border-radius: 999px;
  backdrop-filter: blur(10px);
}

h1,
h2,
p {
  margin-top: 0;
}

h1 {
  margin-bottom: 10px;
  font-family: 'Noto Serif SC', 'Songti SC', SimSun, serif;
  font-size: 38px;
  font-weight: 650;
  line-height: 1.2;
  color: #fff;
  letter-spacing: 0.02em;
}

.welcome-banner > p:last-child {
  margin-bottom: 0;
  font-size: 13px;
  line-height: 1.7;
  color: rgb(229 239 252 / 72%);
}

.section-block {
  padding: 28px;
  background: rgb(255 253 248 / 68%);
  border: 1px solid rgb(255 255 255 / 78%);
  border-radius: 21px;
  box-shadow:
    0 18px 48px rgb(44 70 105 / 8%),
    inset 0 1px 0 rgb(255 255 255 / 90%);
  backdrop-filter: blur(20px) saturate(135%);
}

.section-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 18px;
}

.section-heading h2 {
  margin-bottom: 0;
  font-family: 'Noto Serif SC', 'Songti SC', SimSun, serif;
  font-size: 19px;
  font-weight: 650;
  color: #1a2b4a;
}

.section-heading span {
  font-size: 13px;
  color: #9ca3af;
}

.service-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.service-item {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-height: 176px;
  padding: 22px;
  overflow: hidden;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: rgb(255 255 255 / 58%);
  border: 1px solid rgb(121 149 185 / 17%);
  border-radius: 16px;
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 88%);
  transition:
    border-color 180ms ease,
    box-shadow 180ms ease,
    transform 180ms ease;
}

.service-item:hover {
  border-color: rgb(201 169 97 / 48%);
  box-shadow: 0 16px 32px rgb(26 43 74 / 11%);
  transform: translateY(-4px);
}

.service-title {
  font-family: 'Noto Serif SC', 'Songti SC', SimSun, serif;
  font-size: 16px;
  font-weight: 680;
  color: #1a2b4a;
}

.service-description {
  margin-top: 10px;
  font-size: 12px;
  line-height: 1.7;
  color: #78869a;
}

.service-link {
  padding-top: 18px;
  margin-top: auto;
  font-size: 12px;
  font-weight: 650;
  color: #a88840;
}

.empty-state {
  padding: 30px 16px;
  font-size: 12px;
  color: #96a2b3;
  text-align: center;
  background: rgb(244 248 252 / 46%);
  border: 1px dashed rgb(112 145 187 / 27%);
  border-radius: 14px;
}

@media (max-width: 760px) {
  h1 {
    font-size: 26px;
  }

  .service-grid {
    grid-template-columns: 1fr;
  }

  .welcome-banner {
    min-height: 200px;
    padding: 34px 26px;
    border-radius: 21px;
  }
}

@media (max-width: 1100px) {
  .service-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
