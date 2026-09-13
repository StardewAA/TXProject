<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

import { getTenantSimpleList } from '#/api/core/auth';
import {
  getPortalRecruitPost,
  type HrmPortalRecruitApi,
} from '#/api/hrm/portal/recruit';

import RecruitApplyModal from './components/apply-modal.vue';

const route = useRoute();
const router = useRouter();
const loading = ref(true);
const post = ref<HrmPortalRecruitApi.Post>();
const loadError = ref(false);
const applyModalOpen = ref(false);

function openChat() {
  if (!post.value?.id) return;
  void router.push({
    name: 'PortalRecruit',
    query: { section: 'messages', postId: String(post.value.id) },
  });
}
const tenantNames = ref<Record<number, string>>({});

const descriptionLines = computed(() => splitContent(post.value?.description));

const requirementItems = computed(() => {
  const current = post.value;
  if (!current) return [];
  const items = [
    current.jobNature ? `工作性质：${formatJobNature(current.jobNature)}` : '',
    current.workTime ? `工作经验：${formatWorkTime(current.workTime)}` : '',
    current.educationRequire
      ? `学历要求：${formatEducation(current.educationRequire)}`
      : '',
    formatAgeRequirement(current),
  ].filter(Boolean);
  return items.length ? items : ['具体要求请以招聘方发布内容为准。'];
});

function splitContent(value?: string) {
  const lines = (value || '暂无职位描述')
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
  return lines.length ? lines : ['暂无职位描述'];
}

function formatLocation(current: HrmPortalRecruitApi.Post) {
  return (
    current.areaName ||
    (current.areaId ? `地区 ${current.areaId}` : '工作地点待确认')
  );
}

function getCompanyName(current: HrmPortalRecruitApi.Post) {
  return (
    current.companyName ||
    current.companyShortName ||
    (current.tenantId ? tenantNames.value[current.tenantId] : undefined) ||
    '招聘企业待确认'
  );
}

async function loadTenantNames() {
  try {
    const tenants = await getTenantSimpleList();
    const tenantList = tenants as Array<{ id?: number; name?: string }>;
    tenantNames.value = Object.fromEntries(
      tenantList
        .filter((tenant) => tenant.id && tenant.name)
        .map((tenant) => [tenant.id, tenant.name]),
    );
  } catch (error) {
    console.warn('[PortalRecruitDetail] 加载发布企业名称失败', error);
  }
}

function formatJobNature(value?: number) {
  return (
    {
      1: '全职',
      2: '实习',
      3: '兼职',
    } as Record<number, string>
  )[value || 0] || '不限';
}

function formatWorkTime(value?: number) {
  return (
    {
      1: '经验不限',
      2: '1 年以下',
      3: '1-3 年',
      4: '3-5 年',
      5: '5-10 年',
      6: '10 年以上',
    } as Record<number, string>
  )[value || 0] || '经验不限';
}

function formatEducation(value?: number) {
  return (
    {
      1: '学历不限',
      2: '高中及以上',
      3: '大专及以上',
      4: '本科及以上',
      5: '硕士及以上',
      6: '博士',
    } as Record<number, string>
  )[value || 0] || '学历不限';
}

function formatAgeRequirement(current: HrmPortalRecruitApi.Post) {
  const hasMin = current.minAge != null && current.minAge !== -1;
  const hasMax = current.maxAge != null && current.maxAge !== -1;
  if (!hasMin && !hasMax) return '';
  if (hasMin && hasMax) return `年龄要求：${current.minAge}-${current.maxAge} 岁`;
  if (hasMin) return `年龄要求：${current.minAge} 岁以上`;
  return `年龄要求：${current.maxAge} 岁以下`;
}

async function loadPost() {
  const rawId = Array.isArray(route.query.id)
    ? route.query.id[0]
    : route.query.id;
  const id = Number(rawId);
  if (!Number.isInteger(id) || id <= 0) {
    loadError.value = true;
    loading.value = false;
    return;
  }

  try {
    post.value = await getPortalRecruitPost(id);
    if (!post.value) loadError.value = true;
  } catch (error) {
    loadError.value = true;
    console.warn('[PortalRecruitDetail] 加载岗位详情失败', error);
  } finally {
    loading.value = false;
  }
}

function backToRecruit() {
  void router.push({ name: 'PortalRecruit' });
}

onMounted(async () => {
  void loadTenantNames();
  await loadPost();
  if (route.query.apply === '1' && post.value) {
    applyModalOpen.value = true;
  }
});
</script>

<template>
  <div class="portal-page recruit-detail-page">
    <button class="back-link" type="button" @click="backToRecruit">
      <IconifyIcon icon="lucide:arrow-left" />
      返回岗位机会
    </button>

    <section v-if="loading" class="detail-loading">
      <a-skeleton active :paragraph="{ rows: 10 }" />
    </section>

    <a-empty
      v-else-if="loadError || !post"
      class="detail-empty"
      description="岗位不存在或已下架"
    >
      <template #extra>
        <a-button type="primary" @click="backToRecruit">返回岗位机会</a-button>
      </template>
    </a-empty>

    <template v-else>
      <header class="detail-hero">
        <div>
          <p class="detail-eyebrow">职位机会</p>
          <h1>{{ post.postName }}</h1>
          <div class="detail-meta">
            <span>
              {{ formatLocation(post) }}
            </span>
            <span v-if="post.postTypeName">{{ post.postTypeName }}</span>
          </div>
        </div>
        <div class="detail-actions">
          <a-button @click="openChat">立即沟通</a-button>
        </div>
      </header>

      <main class="detail-content">
        <article class="detail-main">
          <section class="detail-section">
            <h2>职位描述</h2>
            <div class="detail-copy">
              <p v-for="(line, index) in descriptionLines" :key="index">
                {{ line }}
              </p>
            </div>
          </section>

          <section class="detail-section">
            <h2>职位要求</h2>
            <ul class="requirement-list">
              <li v-for="item in requirementItems" :key="item">{{ item }}</li>
            </ul>
          </section>
        </article>

        <aside class="detail-aside">
          <div class="aside-label">岗位信息</div>
          <dl>
            <div>
              <dt>发布企业</dt>
              <dd>{{ getCompanyName(post) }}</dd>
            </div>
            <div>
              <dt>工作地点</dt>
              <dd>{{ formatLocation(post) }}</dd>
            </div>
            <div>
              <dt>招聘人数</dt>
              <dd>{{ post.recruitNum || '若干' }}</dd>
            </div>
            <div v-if="post.deptName">
              <dt>招聘部门</dt>
              <dd>{{ post.deptName }}</dd>
            </div>
          </dl>
        </aside>
      </main>
    </template>

    <RecruitApplyModal v-model:open="applyModalOpen" :post="post" />
  </div>
</template>

<style scoped>
.portal-page {
  display: grid;
  gap: 20px;
}

.back-link {
  display: inline-flex;
  width: fit-content;
  align-items: center;
  gap: 6px;
  padding: 0;
  border: 0;
  color: #64748b;
  background: transparent;
  cursor: pointer;
  font-size: 14px;
  transition: color 160ms ease;
}

.back-link:hover,
.back-link:focus-visible {
  color: #d86616;
  outline: none;
}

.detail-loading,
.detail-empty {
  padding: 56px 32px;
  border: 1px solid #e5e7eb;
  background: #fff;
}

.detail-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 32px;
  padding: 34px 38px;
  border: 1px solid #dbe3ee;
  background: #fff;
}

.detail-eyebrow {
  margin: 0 0 8px;
  color: #d86616;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.detail-hero h1 {
  margin: 0;
  color: #0f172a;
  font-size: 30px;
  line-height: 1.3;
}

.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 14px;
  color: #64748b;
  font-size: 14px;
}

.detail-meta span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.detail-meta :deep(svg) {
  color: #2563eb;
}

.detail-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.detail-content {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px;
  gap: 20px;
  align-items: start;
}

.detail-main,
.detail-aside {
  border: 1px solid #e5e7eb;
  background: #fff;
}

.detail-main {
  padding: 32px 38px;
}

.detail-section + .detail-section {
  margin-top: 36px;
  padding-top: 30px;
  border-top: 1px solid #eef2f7;
}

.detail-section h2 {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 0 18px;
  color: #0f172a;
  font-size: 20px;
}

.detail-section h2::before {
  width: 4px;
  height: 22px;
  border-radius: 2px;
  background: #e87522;
  content: '';
}

.detail-copy {
  color: #475569;
  font-size: 15px;
  line-height: 1.9;
}

.detail-copy p {
  margin: 0 0 8px;
}

.detail-copy p:last-child {
  margin-bottom: 0;
}

.requirement-list {
  display: grid;
  gap: 12px;
  margin: 0;
  padding-left: 22px;
  color: #475569;
  font-size: 15px;
  line-height: 1.7;
}

.requirement-list li::marker {
  color: #d86616;
}

.detail-aside {
  padding: 24px;
}

.aside-label {
  padding-bottom: 14px;
  border-bottom: 1px solid #eef2f7;
  color: #0f172a;
  font-size: 16px;
  font-weight: 650;
}

.detail-aside dl {
  display: grid;
  gap: 18px;
  margin: 20px 0 0;
}

.detail-aside dl > div {
  display: grid;
  gap: 5px;
}

.detail-aside dt {
  color: #94a3b8;
  font-size: 12px;
}

.detail-aside dd {
  margin: 0;
  color: #334155;
  font-size: 14px;
}

@media (max-width: 900px) {
  .detail-content {
    grid-template-columns: 1fr;
  }

  .detail-aside {
    order: -1;
  }
}

@media (max-width: 640px) {
  .detail-hero {
    align-items: flex-start;
    flex-direction: column;
    padding: 24px 20px;
  }

  .detail-hero h1 {
    font-size: 25px;
  }

  .detail-main {
    padding: 24px 20px;
  }
}
.recruit-detail-page { color: #5d4636; }
.recruit-detail-page .detail-hero, .recruit-detail-page .detail-main, .recruit-detail-page .detail-section { border-color: #f4e5d7; background: #fff; }
.recruit-detail-page .detail-section h2, .recruit-detail-page .detail-section h3 { color: #5d341d; }
</style>
