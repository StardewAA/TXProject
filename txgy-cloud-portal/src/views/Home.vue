<script setup>
const modules = [
  { label: '标准规范', path: '/search?type=standard' },
  { label: '材料参数', path: '/search?type=material' },
  { label: '工艺案例', path: '/search?type=process' },
  { label: '定额资料', path: '/search?type=quota' },
  { label: '政策资讯', path: '/search?type=policy' },
];
</script>

<template>
  <section class="hero">
    <h1>让天下没有难做的防腐保温工程</h1>
    <p>标准、材料、工艺、定额、政策一站式查询。</p>
    <div class="module-grid">
      <RouterLink
        v-for="module in modules"
        :key="module.path"
        :to="module.path"
        class="module-card"
      >
        {{ module.label }}
      </RouterLink>
    </div>
  </section>
</template>

<style scoped>
.hero {
  padding: 48px;
  border-radius: 24px;
  background:
    linear-gradient(120deg, rgb(37 99 235 / 16%), transparent 60%),
    #fff;
}

h1 {
  margin: 0;
  font-size: 34px;
}

.hero p {
  color: #475569;
  font-size: 16px;
}

.module-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
  margin-top: 28px;
}

.module-card {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 90px;
  border: 1px solid #dbeafe;
  border-radius: 18px;
  background: #fff;
  color: #1d4ed8;
  font-size: 16px;
  font-weight: 600;
  text-decoration: none;
}

.module-card:hover {
  border-color: #60a5fa;
}
</style>
