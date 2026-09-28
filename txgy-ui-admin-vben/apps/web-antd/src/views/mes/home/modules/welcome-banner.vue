<script lang="ts" setup>
import { computed, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';
import { useAccessStore, useUserStore } from '@vben/stores';

import { message } from 'ant-design-vue';

import { requestClient } from '#/api/request';

/**
 * 生产工作台欢迎横幅：
 * 左侧为时段问候与日期；右侧为「数据驾驶舱」入口按钮，
 * 点击后查询当前租户名下的 GoView 大屏项目（按项目名匹配），
 * 在新标签页打开 GoView 预览页（携带 accessToken/refreshToken，供 GoView 免登）。
 */

const PROJECT_NAME = 'MES 生产车间数字孪生大屏';

const userStore = useUserStore();
const accessStore = useAccessStore();
const opening = ref(false);

const nickname = computed(
  () => (userStore.userInfo?.nickname as string | undefined) ?? '用户',
);

/** 时段问候语 */
const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 6) return '凌晨好';
  if (hour < 9) return '早上好';
  if (hour < 12) return '上午好';
  if (hour < 14) return '中午好';
  if (hour < 18) return '下午好';
  return '晚上好';
});

/** 今天日期：YYYY年M月D日 星期X */
const todayText = computed(() => {
  const now = new Date();
  const week = ['日', '一', '二', '三', '四', '五', '六'] as const;
  return `今天是${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日 星期${week[now.getDay()]}`;
});

/** 打开数据驾驶舱（GoView 大屏预览页） */
async function openDashboard() {
  if (opening.value) return;
  opening.value = true;
  try {
    const goviewBase = String(import.meta.env.VITE_GOVIEW_URL ?? '').replace(
      /\/+$/,
      '',
    );
    if (!goviewBase) {
      message.warning('数据驾驶舱地址未配置（缺少 VITE_GOVIEW_URL）');
      return;
    }
    const page = await requestClient.get<{
      list: Array<{ id: number; name: string }>;
    }>('/report/go-view/project/my-page', {
      params: { pageNo: 1, pageSize: 100, name: PROJECT_NAME },
    });
    const project = (page?.list ?? []).find((it) => it.name === PROJECT_NAME);
    if (!project) {
      message.warning(`未找到大屏「${PROJECT_NAME}」，请联系管理员运行种子脚本`);
      return;
    }
    const query = `accessToken=${accessStore.accessToken}&refreshToken=${accessStore.refreshToken}`;
    window.open(
      `${goviewBase}?${query}#/chart/preview/${project.id}`,
      '_blank',
      'noopener',
    );
  } catch {
    message.error('打开数据驾驶舱失败，请稍后重试');
  } finally {
    opening.value = false;
  }
}
</script>

<template>
  <div class="welcome-banner mb-4">
    <div class="welcome-left">
      <div class="welcome-avatar">
        <IconifyIcon icon="ant-design:user-outlined" />
      </div>
      <div>
        <div class="welcome-title">{{ greeting }}，{{ nickname }}</div>
        <div class="welcome-sub">{{ todayText }}，祝您生产顺利、安全</div>
      </div>
    </div>

    <button :disabled="opening" class="cockpit-btn" @click="openDashboard">
      <IconifyIcon icon="ant-design:dashboard-outlined" class="cockpit-icon" />
      <span class="cockpit-text">{{ opening ? '打开中…' : '数据驾驶舱' }}</span>
      <IconifyIcon icon="ant-design:arrow-right-outlined" class="cockpit-arrow" />
    </button>
  </div>
</template>

<style scoped>
.welcome-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 24px;
  border-radius: 12px;
  background: linear-gradient(90deg, #f3f7ff 0%, #e3ecff 55%, #d7e3ff 100%);
  border: 1px solid #dbe6ff;
}

.welcome-left {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.welcome-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 46px;
  height: 46px;
  border-radius: 50%;
  font-size: 24px;
  color: #fff;
  background: linear-gradient(135deg, #6aa8ff, #3d6bff);
  box-shadow: 0 4px 12px rgb(61 107 255 / 35%);
}

.welcome-title {
  font-size: 20px;
  font-weight: 600;
  color: #1f2a44;
}

.welcome-sub {
  margin-top: 2px;
  font-size: 13px;
  color: #6b7690;
}

/* 数据驾驶舱：科技感发光按钮 */
.cockpit-btn {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  padding: 12px 26px;
  border: 1px solid #37e2ff;
  border-radius: 999px;
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 2px;
  color: #e8fbff;
  cursor: pointer;
  background:
    radial-gradient(120% 140% at 50% 0%, #123c66 0%, #0a1f3d 55%, #071629 100%);
  box-shadow:
    0 0 12px rgb(55 226 255 / 55%),
    inset 0 0 14px rgb(55 226 255 / 25%);
  text-shadow: 0 0 8px rgb(55 226 255 / 80%);
  transition:
    box-shadow 0.25s ease,
    transform 0.25s ease;
}

.cockpit-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow:
    0 0 20px rgb(55 226 255 / 80%),
    inset 0 0 20px rgb(55 226 255 / 35%);
}

.cockpit-btn:disabled {
  cursor: default;
  opacity: 0.7;
}

.cockpit-icon,
.cockpit-arrow {
  font-size: 18px;
  color: #37e2ff;
}

.cockpit-arrow {
  animation: cockpit-arrow-move 1.2s ease-in-out infinite;
}

@keyframes cockpit-arrow-move {
  0%,
  100% {
    transform: translateX(0);
  }

  50% {
    transform: translateX(4px);
  }
}
</style>
