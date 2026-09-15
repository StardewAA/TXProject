<script lang="ts" setup>
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useUserStore } from '@vben/stores';

import txgyLogo from '#/assets/imgs/txgy-logo.png';
import { useAuthStore } from '#/store';

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
const authStore = useAuthStore();

const navItems = [
  { label: '首页', path: '/portal/home' },
  { label: '我要找工作', path: '/portal/recruit' },
  { label: '云培训学院', path: '/portal/training' },
  { label: '招标投标', path: '/portal/bidding' },
];

const displayName = computed(
  () => userStore.userInfo?.nickname || userStore.userInfo?.username || '用户',
);
const isRecruitPortal = computed(() =>
  route.path.startsWith('/portal/recruit') || route.path.startsWith('/portal/profile/recruit'),
);

function isActive(path: string) {
  return route.path === path || route.path.startsWith(`${path}/`);
}

async function handleLogout() {
  await authStore.logout(false);
}
</script>

<template>
  <div :class="{ 'portal-shell--recruit': isRecruitPortal }" class="portal-shell">
    <header class="portal-header">
      <div class="portal-brand" @click="router.push('/portal/home')">
        <img :src="txgyLogo" alt="天信管业" class="portal-brand-logo" />
        <span>防腐保温智慧平台</span>
      </div>

      <nav class="portal-nav" aria-label="员工端导航">
        <button
          v-for="item in navItems"
          :key="item.path"
          :class="{ active: isActive(item.path) }"
          type="button"
          @click="router.push(item.path)"
        >
          {{ item.label }}
        </button>
      </nav>

      <div class="portal-user">
        <span class="portal-user-name">{{ displayName }}</span>
        <button class="portal-logout" type="button" @click="handleLogout">
          退出登录
        </button>
      </div>
    </header>

    <main
      class="portal-main"
      :class="{
        'portal-main--home': route.path.startsWith('/portal/home'),
        'portal-main--training': route.path.startsWith('/portal/training'),
        'portal-main--recruit': isRecruitPortal,
      }"
    >
      <RouterView />
    </main>

    <footer class="portal-footer">© 防腐保温智慧平台 · 员工服务中心</footer>
  </div>
</template>

<style scoped>
.portal-shell {
  position: relative;
  min-height: 100vh;
  background: #f4f6f8;
  color: #1f2937;
}

.portal-shell--recruit {
  background: #f4f6f8;
}

.portal-header {
  display: flex;
  align-items: center;
  min-height: 64px;
  padding: 0 36px;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
  gap: 32px;
}

.portal-brand {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
  gap: 10px;
  color: #111827;
  cursor: pointer;
  font-size: 16px;
  font-weight: 650;
}

.portal-brand-logo {
  width: 34px;
  height: 34px;
  object-fit: contain;
}

.portal-nav {
  display: flex;
  align-items: stretch;
  align-self: stretch;
  gap: 4px;
  overflow-x: auto;
}

.portal-nav button {
  position: relative;
  min-width: 88px;
  padding: 0 14px;
  border: 0;
  background: transparent;
  color: #111827;
  cursor: pointer;
  font-size: 15px;
  white-space: nowrap;
}

.portal-nav button:hover,
.portal-nav button.active {
  color: #111827;
}

.portal-nav button.active::after {
  position: absolute;
  right: 14px;
  bottom: 0;
  left: 14px;
  height: 2px;
  background: linear-gradient(90deg, #ef8b35, #d86616);
  content: '';
}

.portal-user {
  display: inline-flex;
  align-items: center;
  gap: 14px;
  margin-left: auto;
  white-space: nowrap;
}

.portal-user-name {
  color: #111827;
  font-size: 15px;
}

.portal-logout {
  border: 0;
  background: transparent;
  color: #111827;
  cursor: pointer;
  font-size: 14px;
}

.portal-logout:hover {
  color: #dc2626;
}

.portal-main {
  width: min(1180px, calc(100% - 48px));
  min-height: calc(100vh - 112px);
  margin: 0 auto;
  padding: 32px 0 48px;
}

.portal-main--training {
  width: calc(100% - 64px);
  max-width: none;
  margin-right: 32px;
  margin-left: 32px;
  padding-top: 32px;
}

.portal-main.portal-main--home {
  width: 100%;
  min-height: calc(100vh - 64px);
  margin: 0;
  padding: 0;
}

.portal-main.portal-main--home + .portal-footer {
  display: none;
}

.portal-main--recruit { position: relative; }

.portal-shell--recruit :deep(.ant-btn-primary) {
  border-color: #d86616;
  background: linear-gradient(135deg, #ef8b35, #d86616);
  box-shadow: 0 6px 14px rgb(216 102 22 / 18%);
}

.portal-shell--recruit :deep(.ant-btn-primary:hover) {
  border-color: #b94e13;
  background: linear-gradient(135deg, #d86616, #b94e13);
}

.portal-footer {
  padding: 20px 24px;
  color: #9ca3af;
  font-size: 12px;
  text-align: center;
}

@media (max-width: 900px) {
  .portal-header {
    flex-wrap: wrap;
    gap: 0 20px;
    padding: 12px 20px 0;
  }

  .portal-user {
    margin-left: auto;
    padding-bottom: 12px;
  }

  .portal-nav {
    order: 3;
    width: 100%;
    min-height: 44px;
  }

  .portal-nav button {
    min-width: auto;
    padding: 0 12px;
  }

  .portal-main {
    width: min(100% - 32px, 680px);
    padding-top: 24px;
  }
}
</style>
