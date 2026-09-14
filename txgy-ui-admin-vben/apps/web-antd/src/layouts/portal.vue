<script lang="ts" setup>
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useUserStore } from '@vben/stores';

import { useAuthStore } from '#/store';

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
const authStore = useAuthStore();

const navItems = [
  { label: '首页', path: '/portal/home' },
  { label: 'CMS系统', path: '/portal/content' },
  { label: '招聘应聘', path: '/portal/recruit' },
  { label: '云培训学院', path: '/portal/training' },
  { label: '招标投标', path: '/portal/bidding' },
  { label: '个人中心', path: '/portal/profile' },
];

const displayName = computed(
  () => userStore.userInfo?.nickname || userStore.userInfo?.username || '用户',
);

function isActive(path: string) {
  return route.path === path || route.path.startsWith(`${path}/`);
}

async function handleLogout() {
  await authStore.logout(false);
}
</script>

<template>
  <div class="portal-shell">
    <header class="portal-header">
      <div class="portal-brand" @click="router.push('/portal/home')">
        <span class="portal-brand-mark">防</span>
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

    <main class="portal-main">
      <RouterView />
    </main>

    <footer class="portal-footer">© 防腐保温智慧平台 · 员工服务中心</footer>
  </div>
</template>

<style scoped>
.portal-shell {
  min-height: 100vh;
  color: #1f2937;
  background:
    radial-gradient(circle at 8% 4%, rgb(229 219 191 / 48%), transparent 27%),
    radial-gradient(circle at 92% 28%, rgb(209 218 232 / 45%), transparent 25%),
    #f7f5f0;
}

.portal-header {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  gap: 32px;
  align-items: center;
  min-height: 70px;
  padding: 0 36px;
  background: rgb(26 43 74 / 95%);
  border-bottom: 3px solid #c9a961;
  box-shadow: 0 8px 26px rgb(26 43 74 / 16%);
  backdrop-filter: blur(18px) saturate(140%);
}

.portal-brand {
  display: inline-flex;
  flex-shrink: 0;
  gap: 10px;
  align-items: center;
  font-size: 16px;
  font-weight: 650;
  color: #fff;
  cursor: pointer;
}

.portal-brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 35px;
  height: 35px;
  font-size: 16px;
  font-weight: 700;
  color: #1a2b4a;
  background: linear-gradient(145deg, #dfc48a, #b89448);
  border-radius: 10px;
  box-shadow: 0 8px 18px rgb(8 18 36 / 28%);
}

.portal-nav {
  display: flex;
  gap: 4px;
  align-items: stretch;
  align-self: stretch;
  overflow-x: auto;
}

.portal-nav button {
  position: relative;
  min-width: 88px;
  height: 38px;
  padding: 0 13px;
  font-size: 14px;
  color: rgb(255 255 255 / 65%);
  white-space: nowrap;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 10px;
}

.portal-nav button:hover,
.portal-nav button.active {
  color: #dfc48a;
  background: rgb(201 169 97 / 12%);
}

.portal-nav button.active::after {
  position: absolute;
  right: calc(50% - 2px);
  bottom: 4px;
  left: auto;
  width: 4px;
  height: 4px;
  content: '';
  background: #c9a961;
  border-radius: 50%;
}

.portal-user {
  display: inline-flex;
  gap: 14px;
  align-items: center;
  margin-left: auto;
  white-space: nowrap;
}

.portal-user-name {
  font-size: 14px;
  color: rgb(255 255 255 / 84%);
}

.portal-logout {
  font-size: 13px;
  color: rgb(255 255 255 / 55%);
  cursor: pointer;
  background: transparent;
  border: 0;
}

.portal-logout:hover {
  color: #dc2626;
}

.portal-main {
  width: min(1240px, calc(100% - 48px));
  min-height: calc(100vh - 112px);
  padding: 34px 0 50px;
  margin: 0 auto;
}

.portal-footer {
  padding: 20px 24px;
  font-size: 12px;
  color: #9ca3af;
  text-align: center;
}

@media (max-width: 900px) {
  .portal-header {
    flex-wrap: wrap;
    gap: 0 20px;
    padding: 12px 20px 0;
  }

  .portal-user {
    padding-bottom: 12px;
    margin-left: auto;
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
