<script setup lang="ts">
/**
 * 前台门户跳转壳（菜单 60006604「前台门户」→ 同 SPA 跳转 /portal/bidding）。
 * 为什么用 vue-router.replace 而不是 window.location：门户与后台是同一个 SPA，
 * 全量刷新会重走启动流程；router 跳转保留 accessStore 里的 accessToken，
 * 门户内投标报名 / 文件下载等需鉴权接口不丢登录态。
 */
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';

const router = useRouter();

onMounted(() => {
  void router.replace('/portal/bidding');
});
</script>

<template>
  <div class="portal-redirect">
    <div class="portal-redirect-card">
      <div class="portal-redirect-title">正在前往 招标投标 前台门户…</div>
      <div class="portal-redirect-sub">未自动跳转？<a href="/portal/bidding">点此进入公告大厅</a></div>
    </div>
  </div>
</template>

<style scoped>
.portal-redirect {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
}
.portal-redirect-card {
  text-align: center;
  padding: 32px 48px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--fill-quaternary);
}
.portal-redirect-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 8px;
}
.portal-redirect-sub {
  font-size: 12px;
  color: var(--text-3);
}
</style>
