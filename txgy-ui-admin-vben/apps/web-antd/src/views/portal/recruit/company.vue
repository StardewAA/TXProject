<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { IconifyIcon } from '@vben/icons';
import { getPortalRecruitCompanyList, getPortalRecruitPostPage, type HrmPortalRecruitApi } from '#/api/hrm/portal/recruit';

const route = useRoute();
const router = useRouter();
const loading = ref(true);
const company = ref<HrmPortalRecruitApi.Company>();
const posts = ref<HrmPortalRecruitApi.Post[]>([]);
const companyId = Number(route.query.id);
const companyName = computed(() => company.value?.name || '企业主页');

async function load() {
  loading.value = true;
  try {
    const [companies, page] = await Promise.all([
      getPortalRecruitCompanyList(),
      getPortalRecruitPostPage({ pageNo: 1, pageSize: 100 }),
    ]);
    company.value = companies.find((item) => item.id === companyId);
    posts.value = page.list.filter((post) => post.tenantId === companyId);
  } finally {
    loading.value = false;
  }
}

function openPost(id: number) {
  void router.push({ name: 'PortalRecruitDetail', query: { id: String(id) } });
}

onMounted(() => void load());
</script>

<template>
  <main class="company-page">
    <header class="company-hero">
      <button type="button" class="back-button" aria-label="返回企业列表" @click="router.back()"><IconifyIcon icon="lucide:arrow-left" /></button>
      <div class="company-mark"><IconifyIcon icon="lucide:building-2" /></div>
      <div><p class="eyebrow">企业主页</p><h1>{{ companyName }}</h1><p>{{ posts.length }} 个公开招聘职位</p></div>
    </header>
    <section v-if="loading" class="company-state">正在加载企业信息…</section>
    <section v-else-if="!company" class="company-state"><strong>未找到该企业</strong><a-button type="link" @click="router.push({ name: 'PortalRecruit', query: { section: 'companies' } })">返回企业目录</a-button></section>
    <template v-else>
      <section class="company-section"><div class="section-heading"><h2>公开职位</h2><span>{{ posts.length }} 个</span></div><div v-if="!posts.length" class="empty">暂无公开职位</div><div v-else class="post-grid"><article v-for="post in posts" :key="post.id" class="post-card" @click="openPost(post.id)"><div><h3>{{ post.postName }}</h3><p>{{ post.areaName || post.workAddress || '工作地点待确认' }}</p></div><strong>{{ post.minSalary && post.maxSalary ? `${post.minSalary / 1000}K-${post.maxSalary / 1000}K` : '薪资面议' }}</strong><IconifyIcon icon="lucide:arrow-up-right" /></article></div></section>
      <section class="company-section"><div class="section-heading"><h2>企业信息</h2></div><p class="company-note">职位信息由企业在招聘域公开发布，具体岗位要求与工作地址以职位详情为准。</p></section>
    </template>
  </main>
</template>

<style scoped>
.company-page{min-height:100%;padding:32px;max-width:1080px;margin:0 auto;color:#123f49}.company-hero{display:flex;align-items:center;gap:18px;padding:28px;border-radius:16px;background:linear-gradient(135deg,#e9f7f6,#fff7ed);border:1px solid #dcecea}.back-button{display:grid;place-items:center;width:36px;height:36px;border:0;border-radius:8px;background:#fff;color:#123f49;cursor:pointer}.company-mark{display:grid;place-items:center;width:64px;height:64px;border-radius:14px;background:#0f766e;color:#fff;font-size:28px}.eyebrow{margin:0 0 4px;color:#0f766e;font-size:12px}.company-hero h1{margin:0;font-size:28px}.company-hero p:last-child{margin:6px 0 0;color:#64748b}.company-section{margin-top:24px;padding:22px;border:1px solid #e2edec;border-radius:12px;background:#fff}.section-heading{display:flex;justify-content:space-between;align-items:center;margin-bottom:14px}.section-heading h2{margin:0;font-size:18px}.section-heading span{color:#8497a3}.post-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.post-card{position:relative;display:flex;align-items:center;gap:12px;padding:16px;border:1px solid #e2edec;border-radius:10px;cursor:pointer}.post-card:hover{border-color:#68b9b0;background:#f7fcfb}.post-card h3{margin:0;font-size:15px}.post-card p{margin:6px 0 0;color:#8497a3;font-size:12px}.post-card strong{margin-left:auto;color:#b45309;font-size:13px;white-space:nowrap}.post-card>svg{color:#0f766e}.company-state,.empty{display:grid;place-items:center;gap:8px;min-height:180px;color:#8497a3}.company-note{margin:0;color:#64748b;line-height:1.8}@media(max-width:680px){.company-page{padding:16px}.post-grid{grid-template-columns:1fr}.company-hero h1{font-size:22px}}
</style>
