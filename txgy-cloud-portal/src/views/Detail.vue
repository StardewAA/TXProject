<script setup>
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { getCmsContentDetail } from '../api/cms';

const route = useRoute();
const loading = ref(true);
const detail = ref(null);

const fallbackDetail = {
  id: 1,
  type: 'standard',
  title: '设备及管道绝热技术通则',
  summary: '适用于设备、管道及工业设施的绝热与保冷工程。',
  content:
    '标准正文将在后端 CMS 接口联调后展示。当前页面结构已预留标题、类型、正文、发布时间等字段。',
  updateTime: '2026-09-03',
};

async function loadDetail() {
  loading.value = true;
  try {
    detail.value = await getCmsContentDetail(
      route.params.type,
      route.params.id,
    );
  } catch {
    detail.value = fallbackDetail;
  } finally {
    loading.value = false;
  }
}

onMounted(loadDetail);
</script>

<template>
  <section class="detail-page">
    <template v-if="!loading && detail">
      <RouterLink to="/search" class="back">返回搜索</RouterLink>
      <p class="type">{{ detail.type }}</p>
      <h1>{{ detail.title }}</h1>
      <p class="time">更新时间：{{ detail.updateTime || '-' }}</p>
      <p class="summary">{{ detail.summary }}</p>
      <article>{{ detail.content }}</article>
    </template>
    <p v-else>加载中...</p>
  </section>
</template>

<style scoped>
.detail-page {
  padding: 28px;
  border-radius: 20px;
  background: #fff;
}

.back {
  color: #1d4ed8;
  text-decoration: none;
}

.type {
  color: #2563eb;
  font-weight: 600;
}

h1 {
  margin: 8px 0;
}

.time,
.summary {
  color: #64748b;
}

article {
  margin-top: 16px;
  color: #334155;
  line-height: 1.9;
}
</style>
