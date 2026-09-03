<template>
  <div class="portal-page">
    <section class="page-header">
      <p class="eyebrow">人才服务</p>
      <h1>招聘应聘</h1>
      <p>岗位浏览、简历投递和应聘进度将在这里集中管理。</p>
    </section>
    <section class="placeholder-panel">
      <h2>招聘服务即将开放</h2>
      <p>后续将接入职位列表、简历管理、投递记录和面试安排。</p>
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
  border: 1px solid #dbeafe;
  background: #eff6ff;
}
.eyebrow {
  margin: 0 0 8px;
  color: #2563eb;
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
.placeholder-panel {
  padding: 64px 24px;
  border: 1px solid #e5e7eb;
  background: #fff;
  text-align: center;
}
.placeholder-panel h2 {
  margin: 0 0 10px;
  color: #1f2937;
  font-size: 20px;
}
.placeholder-panel p {
  margin: 0;
  color: #6b7280;
  font-size: 14px;
}
</style>
