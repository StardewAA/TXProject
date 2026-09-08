<script lang="ts" setup>
/**
 * 个人中心（投标人，二期占位）
 * 二期口径：仅投标人自身信息 —— 可查看相应表与必填项（实名认证等先做入口按钮）。
 */
import { useRouter } from 'vue-router';

const router = useRouter();
</script>

<template>
  <div class="ph-page">
    <div class="ph-card">
      <h1>个人中心（投标人）</h1>
      <p>二期建设中 —— 展示自身主体信息与办理入口；如实名认证等必填事项，将在此提供入口按钮引导办理。</p>
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
  color: #5f6b7a;
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
