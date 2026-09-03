<script lang="ts" setup>
import { computed } from 'vue';

import { useUserStore } from '@vben/stores';

const userStore = useUserStore();
const userInfo = computed(() => userStore.userInfo);
</script>

<template>
  <div class="portal-page">
    <section class="page-header">
      <p class="eyebrow">账号设置</p>
      <h1>个人中心</h1>
      <p>查看当前账号信息，后续可在此完善个人资料。</p>
    </section>
    <section class="profile-panel">
      <div class="profile-row">
        <span>用户名</span><strong>{{ userInfo?.username || '-' }}</strong>
      </div>
      <div class="profile-row">
        <span>昵称</span><strong>{{ userInfo?.nickname || '-' }}</strong>
      </div>
      <div class="profile-row">
        <span>用户编号</span><strong>{{ userInfo?.userId || '-' }}</strong>
      </div>
      <div class="profile-row">
        <span>账号角色</span
        ><strong>{{ userInfo?.roles?.join('、') || '普通员工' }}</strong>
      </div>
    </section>
  </div>
</template>

<style scoped>
.portal-page {
  display: grid;
  gap: 24px;
}
.page-header {
  padding: 28px 32px;
  border: 1px solid #e0e7ff;
  background: #f5f7ff;
}
.eyebrow {
  margin: 0 0 8px;
  color: #4f46e5;
  font-size: 13px;
  font-weight: 600;
}
h1 {
  margin: 0 0 8px;
  color: #111827;
  font-size: 28px;
}
.page-header p:last-child {
  margin: 0;
  color: #4b5563;
  font-size: 14px;
}
.profile-panel {
  padding: 8px 24px;
  border: 1px solid #e5e7eb;
  background: #fff;
}
.profile-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 58px;
  border-bottom: 1px solid #f3f4f6;
  color: #6b7280;
  font-size: 14px;
}
.profile-row:last-child {
  border-bottom: 0;
}
.profile-row strong {
  color: #1f2937;
  font-weight: 500;
  text-align: right;
}
</style>
