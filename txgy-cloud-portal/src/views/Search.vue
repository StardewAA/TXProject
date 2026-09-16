<script setup>
import { ref } from 'vue';
import { useRoute } from 'vue-router';

import { searchCmsContent } from '../api/cms';

const route = useRoute();
const type = ref(String(route.query.type || 'standard'));
const keyword = ref('');
const result = ref([]);
const loading = ref(false);

const mockResult = [
  {
    id: 1,
    type: 'standard',
    title: 'GB/T 4272-2008 设备及管道绝热技术通则',
    summary: '设备、管道绝热工程通用技术要求。',
    updateTime: '2026-09-03',
  },
  {
    id: 2,
    type: 'policy',
    title: '工业设备及管道绝热节能标准宣贯工作安排',
    summary: '推动新版绝热标准在行业落地应用。',
    updateTime: '2026-09-03',
  },
];

async function doSearch() {
  if (!keyword.value.trim()) {
    result.value = [];
    return;
  }
  loading.value = true;
  try {
    result.value = await searchCmsContent({
      keyword: keyword.value.trim(),
      type: type.value,
      pageNum: 1,
      pageSize: 20,
    });
  } catch {
    result.value = mockResult;
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <section class="search-page">
    <h1>智能检索</h1>
    <div class="search-box">
      <select v-model="type">
        <option value="standard">标准规范</option>
        <option value="material">材料参数</option>
        <option value="process">工艺案例</option>
        <option value="quota">定额资料</option>
        <option value="policy">政策资讯</option>
      </select>
      <input v-model="keyword" placeholder="输入关键词" />
      <button :disabled="loading" @click="doSearch">查询</button>
    </div>
    <RouterLink
      v-for="item in result"
      :key="item.id"
      :to="`/content/${item.type}/${item.id}`"
      class="result-card"
    >
      <strong>{{ item.title }}</strong>
      <span>{{ item.summary }}</span>
      <small>更新时间：{{ item.updateTime || '-' }}</small>
    </RouterLink>
    <p v-if="result.length === 0" class="tip">
      请输入关键词体验检索入口。
    </p>
  </section>
</template>

<style scoped>
.search-page {
  padding: 28px;
  border-radius: 20px;
  background: #fff;
}

h1 {
  margin: 0 0 18px;
}

.search-box {
  display: flex;
  gap: 10px;
}

select,
input {
  height: 44px;
  padding: 0 12px;
  border: 1px solid #cbd5e1;
  border-radius: 12px;
}

select {
  width: 150px;
}

input {
  flex: 1;
}

button {
  padding: 0 22px;
  border: 0;
  border-radius: 12px;
  background: #1d4ed8;
  color: #fff;
  cursor: pointer;
}

.result-card {
  display: flex;
  margin-top: 16px;
  padding: 14px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  flex-direction: column;
  gap: 6px;
}

.tip {
  color: #94a3b8;
}
</style>
