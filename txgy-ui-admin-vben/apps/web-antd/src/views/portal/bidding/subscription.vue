<script lang="ts" setup>
/**
 * 项目订阅 · 跟踪（二期占位）
 * 二期口径：订阅与项目跟踪一体化；点已订阅项目可查看该项目全部历史公告（时间线）；
 * 未读红点 / 短信提醒另行排期（数据面已有 notice-follow 域）。
 */
import { useRouter } from 'vue-router';

const router = useRouter();
</script>

<template>
  <div class="ph-page">
    <div class="ph-card">
      <h1>项目订阅 · 跟踪</h1>
      <p>二期建设中 —— 订阅后即可跟踪项目；在订阅列表点击项目，可查看该项目发布过的全部历史公告。</p>
      <button class="back" @click="router.push('/portal/bidding').catch(() => {})">‹ 返回公告大厅</button>
    </div>
  </div>
</template>

<style scoped>
.ph-page {
  background: #f3f5f7;
  min-height: 100%;
  padding: 40px 22px;
}
.ph-card {
  max-width: 560px;
  margin: 0 auto;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 48px 32px;
  text-align: center;
}
.ph-card h1 {
  margin: 0 0 12px;
  font-size: 20px;
  color: #1e2832;
}
.ph-card p {
  color: #7a8591;
  font-size: 13.5px;
  line-height: 1.8;
  margin: 0 0 22px;
}
.back {
  font-size: 13px;
  color: #17539a;
  font-weight: 700;
  cursor: pointer;
  border: 1px solid #c4d7ec;
  background: #eef4fb;
  border-radius: 4px;
  padding: 6px 14px;
}
</style>
