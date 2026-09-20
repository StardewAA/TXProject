<script lang="ts" setup>
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';
import { useAccessStore, useUserStore } from '@vben/stores';

import txgyLogo from '#/assets/imgs/logo.png';
import { useAuthStore } from '#/store';

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
const authStore = useAuthStore();
const accessStore = useAccessStore();

/** 是否已登录：公告页（meta.ignoreAccess）免登录可看，头部必须能匿名渲染，不能假设有 userInfo。 */
const isLoggedIn = computed(() => Boolean(accessStore.accessToken));

/** 匿名访客点「登录」：带 redirect 回当前页。 */
function goLogin() {
  router.push({ path: LOGIN_PATH, query: { redirect: encodeURIComponent(route.fullPath) } });
}

const navItems = [
  { label: '首页', path: '/portal/home' },
  { label: 'CMS系统', path: '/portal/content' },
  { label: '我要找工作', path: '/portal/recruit' },
  { label: '云培训学院', path: '/portal/training' },
  { label: '招标投标', path: '/portal/bidding' },
];

const displayName = computed(
  () => userStore.userInfo?.nickname || userStore.userInfo?.username || '用户',
);

const isRecruitPortal = computed(
  () =>
    route.path.startsWith('/portal/recruit') ||
    route.path.startsWith('/portal/profile/recruit'),
);
const isTrainingPortal = computed(() => route.path.startsWith('/portal/training'));

const isRecruitHome = computed(() => {
  if (route.path !== '/portal/recruit') return false;
  const section = Array.isArray(route.query.section)
    ? route.query.section[0]
    : route.query.section;
  return !section || section === 'home';
});

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
    :class="{
      'portal-shell--recruit': isRecruitPortal,
      'portal-shell--training': isTrainingPortal,
    }"
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
        <template v-if="isLoggedIn">
          <span class="portal-user-name">{{ displayName }}</span>
          <button class="portal-logout" type="button" @click="handleLogout">
            退出登录
          </button>
        </template>
        <button v-else class="portal-logout" type="button" @click="goLogin">登录</button>
      </div>
    </header>

    <main
      class="portal-main"
      :class="{
        'portal-main--home': route.path.startsWith('/portal/home'),
        'portal-main--training': route.path.startsWith('/portal/training'),
        'portal-main--recruit': isRecruitPortal,
        'portal-main--recruit-home': isRecruitHome,
      }"
    >
      <RouterView />
    </main>

    <footer v-if="!isRecruitHome" class="portal-footer">© 防腐保温智慧平台 · 员工服务中心</footer>
  </div>
</template>

<style scoped>
.portal-shell {
  position: relative;
  min-height: 100vh;
  background: hsl(var(--background-deep));
  color: hsl(var(--foreground));
}

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
  font-size: 15px;
  white-space: nowrap;
}

.portal-nav button:hover,
.portal-nav button.active {
  color: #0f8f8c;
}

.portal-nav button.active::after {
  position: absolute;
  right: 14px;
  bottom: 0;
  left: 14px;
  height: 2px;
  background: linear-gradient(90deg, #0f8f8c, #2f80ed);
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
  font-size: 14px;
}

.portal-logout:hover {
  color: #0f8f8c;
}

.portal-main {
  width: min(1180px, calc(100% - 48px));
  min-height: calc(100vh - 112px);
  margin: 0 auto;
  padding: 32px 0 48px;
}

.portal-main--training {
  width: min(1320px, calc(100% - 120px));
  max-width: 1320px;
  margin-right: auto;
  margin-left: auto;
  padding: 0 0 48px;
}

.portal-shell--training {
  background: #fff;
}

.portal-shell--training .portal-header {
  min-height: 72px;
  padding-right: max(48px, calc((100% - 1320px) / 2));
  padding-left: max(48px, calc((100% - 1320px) / 2));
  gap: 18px;
}

.portal-shell--training .portal-brand {
  gap: 8px;
  font-size: 15px;
}

.portal-shell--training .portal-brand-logo {
  width: 32px;
  height: 32px;
}

.portal-shell--training .portal-nav button {
  min-width: 74px;
  padding: 0 8px;
  font-size: 13px;
}

.portal-shell--training .portal-user { gap: 9px; }

.portal-shell--training .portal-footer {
  display: none;
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

.portal-main--recruit {
  position: relative;
}

.portal-main--recruit-home {
  display: flex;
  width: 100%;
  min-height: calc(100vh - 64px);
  margin: 0;
  padding: 0;
}

.portal-main--recruit-home :deep(.recruit-page) {
  flex: 1;
  min-height: 100%;
}

.portal-shell--recruit :deep(.ant-btn-primary) {
  border-color: #0f8f8c;
  background: linear-gradient(135deg, #0f9f9b, #2f80ed);
  box-shadow: 0 6px 14px rgb(15 143 140 / 18%);
}

.portal-shell--recruit :deep(.ant-btn-primary:hover) {
  border-color: #0b777c;
  background: linear-gradient(135deg, #0b8587, #2563c7);
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
