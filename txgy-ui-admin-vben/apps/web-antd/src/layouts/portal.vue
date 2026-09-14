<script lang="ts" setup>
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useUserStore } from '@vben/stores';

import txgyLogo from '#/assets/imgs/logo.png';
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
  { label: '个人中心', path: '/portal/profile' },
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
  <div
    class="portal-shell portal-light"
    :class="{ 'portal-shell--recruit': isRecruitPortal }"
  >
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
  background: hsl(var(--background-deep));
  color: hsl(var(--foreground));
}

/* 所有门户页（首页/招聘/培训/招投标/个人中心）统一固定浅色设计：锁定浅色主题变量，
   避免深色模式下外壳变深、与页面自身浅色内容脱节。 */
.portal-light {
  --background: 0 0% 100%;
  --background-deep: 216 20.11% 95.47%;
  --foreground: 210 6% 21%;
  --card: 0 0% 100%;
  --border: 240 5.9% 90%;
  --muted: 240 4.8% 95.9%;
  --muted-foreground: 240 3.8% 46.1%;
  --primary: 212 100% 45%;
  --primary-foreground: 0 0% 98%;
  --destructive: 359.33 100% 65.1%;
}

.portal-shell--recruit {
  background: #f4f6f8;
}

.portal-header {
  display: flex;
  align-items: center;
  min-height: 64px;
  padding: 0 36px;
  border-bottom: 1px solid hsl(var(--border));
  background: hsl(var(--card));
  gap: 32px;
}

.portal-brand {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
  gap: 10px;
  color: hsl(var(--foreground));
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
  color: hsl(var(--muted-foreground));
  cursor: pointer;
  font-size: 14px;
  white-space: nowrap;
}

.portal-nav button:hover,
.portal-nav button.active {
  color: #d86616;
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
  color: hsl(var(--foreground));
  font-size: 14px;
}

.portal-logout {
  border: 0;
  background: transparent;
  color: hsl(var(--muted-foreground));
  cursor: pointer;
  font-size: 13px;
}

.portal-logout:hover {
  color: hsl(var(--destructive));
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
  color: hsl(var(--muted-foreground));
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