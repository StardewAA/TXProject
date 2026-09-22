<script lang="ts" setup>
import { useRouter } from 'vue-router';

import { Button } from 'ant-design-vue';

defineOptions({ name: 'Fallback404Demo' });

const router = useRouter();

function goBack() {
  if (window.history.length > 1) {
    router.back();
  } else {
    router.push('/');
  }
}
</script>

<template>
  <div class="not-found">
    <div class="code">404</div>
    <h2>哎呀！未找到页面</h2>
    <p>抱歉，我们无法找到您要找的页面。</p>
    <Button type="primary" @click="goBack">← 返回上一页</Button>
  </div>
</template>

<style lang="scss" scoped>
.not-found {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  text-align: center;

  .code {
    color: #f0f2f5;
    font-size: 120px;
    font-weight: 700;
    line-height: 1;
    text-shadow: 0 6px 18px rgb(0 0 0 / 6%);
    user-select: none;
  }

  h2 {
    margin: 12px 0 6px;
    color: #1f2329;
    font-size: 24px;
    font-weight: 600;
  }

  p {
    margin: 0 0 24px;
    color: #8c8c8c;
    font-size: 14px;
  }
}
</style>
