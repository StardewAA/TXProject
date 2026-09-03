<script setup>
import { computed, ref } from 'vue';
import { useRoute } from 'vue-router';

const route = useRoute();
const type = ref(String(route.query.type || 'standard'));
const keyword = ref('');
const result = computed(() => {
  if (!keyword.value.trim()) {
    return [];
  }
  return [
    {
      id: 1,
      title: `${keyword.value}相关结果`,
      summary: '门户工程已初始化，接入 ES 和 CMS 接口后替换为真实数据。',
    },
  ];
});
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
      <button>查询</button>
    </div>
    <div v-for="item in result" :key="item.id" class="result-card">
      <strong>{{ item.title }}</strong>
      <span>{{ item.summary }}</span>
    </div>
    <p v-if="result.length === 0" class="tip">请输入关键词体验检索入口。</p>
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
