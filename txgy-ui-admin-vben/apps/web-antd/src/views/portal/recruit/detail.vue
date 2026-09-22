<script lang="ts" setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

import { getTenantSimpleList } from '#/api/core/auth';
import { loadBaiduMapSdk } from '#/components/map';
import {
  getPortalRecruitPost,
  type HrmPortalRecruitApi,
} from '#/api/hrm/portal/recruit';

import RecruitApplyModal from './components/apply-modal.vue';
import { formatRecruitLocation, openRecruitLocationMap, UNKNOWN_LOCATION, resolveRecruitCompanyName } from './utils';

const route = useRoute();
const router = useRouter();
const loading = ref(true);
const post = ref<HrmPortalRecruitApi.Post>();
const loadError = ref(false);
const applyModalOpen = ref(false);
const locationMapRef = ref<HTMLElement>();
const locationMapLoading = ref(false);
const locationMapError = ref('');
let locationMap: any = null;

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
  return formatRecruitLocation(current);
}

function openLocationMap(current: HrmPortalRecruitApi.Post) {
  openRecruitLocationMap(formatLocation(current));
}

function openCompanyProfile() {
  if (!post.value?.tenantId) return;
  void router.push({
    name: 'PortalRecruitCompany',
    query: { tenantId: String(post.value.tenantId) },
  });
}

async function initLocationMap() {
  const location = post.value ? formatLocation(post.value) : '';
  if (!location || location === UNKNOWN_LOCATION || !locationMapRef.value) return;

  locationMapLoading.value = true;
  locationMapError.value = '';
  try {
    await loadBaiduMapSdk();
    if (!locationMapRef.value || !window.BMapGL) return;

    locationMap?.destroy?.();
    locationMap = new window.BMapGL.Map(locationMapRef.value);
    const fallbackPoint = new window.BMapGL.Point(116.404, 39.915);
    locationMap.centerAndZoom(fallbackPoint, 11);
    locationMap.enableScrollWheelZoom();
    locationMap.addControl(new window.BMapGL.NavigationControl());
    locationMap.addControl(new window.BMapGL.ZoomControl());

    const localSearch = new window.BMapGL.LocalSearch(locationMap, {
      pageCapacity: 1,
      onSearchComplete: (results: any) => {
        const point = results?._pois?.[0]?.point;
        if (!point) {
          locationMapError.value = '未找到该工作地点，请点击地址打开地图搜索';
          return;
        }
        const target = new window.BMapGL.Point(point.lng, point.lat);
        locationMap.clearOverlays();
        locationMap.addOverlay(new window.BMapGL.Marker(target));
        locationMap.centerAndZoom(target, 16);
      },
    });
    localSearch.search(location);
  } catch (error) {
    locationMapError.value =
      error instanceof Error ? error.message : '地图加载失败，请点击地址打开地图搜索';
  } finally {
    locationMapLoading.value = false;
  }
}

function getCompanyName(current: HrmPortalRecruitApi.Post) {
  return resolveRecruitCompanyName(current, tenantNames.value);
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
  await nextTick();
  void initLocationMap();
  if (route.query.apply === '1' && post.value) {
    applyModalOpen.value = true;
  }
});

onUnmounted(() => {
  locationMap?.destroy?.();
  locationMap = null;
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
              <dd>
                <button class="company-profile-link" type="button" @click="openCompanyProfile">
                  {{ getCompanyName(post) }}
                </button>
              </dd>
            </div>
            <div>
              <dt>工作地点</dt>
              <dd class="detail-location">
                <span>{{ formatLocation(post) }}</span>
                <a-button
                  v-if="formatLocation(post) !== '工作地点待确认'"
                  type="link"
                  size="small"
                  @click="openLocationMap(post)"
                >
                  在地图中查看
                </a-button>
              </dd>
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
      <section class="location-map-section" aria-labelledby="location-map-title">
        <div class="location-map-heading">
          <h2 id="location-map-title">工作地址地图</h2>
          <button class="location-map-link" type="button" @click="openLocationMap(post)">
            <IconifyIcon icon="lucide:external-link" />
            打开地图
          </button>
        </div>
        <div class="location-map-card">
          <div ref="locationMapRef" class="location-map-canvas"></div>
          <div v-if="locationMapLoading" class="location-map-state">地图加载中...</div>
          <div v-else-if="locationMapError" class="location-map-state error">{{ locationMapError }}</div>
        </div>
      </section>
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

.location-map-section {
  padding: 0 32px 32px;
  border: 1px solid var(--detail-line);
  background: #fff;
}

.company-profile-link {
  padding: 0;
  border: 0;
  color: var(--detail-blue);
  background: transparent;
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.company-profile-link:hover { text-decoration: underline; }

.location-map-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 24px 0 16px;
}

.location-map-heading h2 {
  margin: 0;
  color: var(--detail-ink);
  font-size: 22px;
}

.location-map-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0;
  border: 0;
  color: var(--detail-teal);
  background: transparent;
  cursor: pointer;
  font-size: 15px;
}

.location-map-link:hover {
  text-decoration: underline;
}

.location-map-card {
  position: relative;
  overflow: hidden;
  min-height: 300px;
  border: 1px solid var(--detail-line-soft);
  border-radius: 14px;
  background: #f5f8fa;
}

.location-map-canvas {
  height: 360px;
  width: 100%;
}

.location-map-state {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--detail-muted);
  background: rgb(245 248 250 / 82%);
  font-size: 14px;
}

.location-map-state.error {
  color: #b45309;
}

.detail-aside .location-map-section {
  margin-top: 24px;
  padding: 20px 0 0;
  border-top: 1px solid var(--detail-line-soft);
  border-right: 0;
  border-bottom: 0;
  border-left: 0;
}

.detail-aside .location-map-heading {
  padding: 0 0 12px;
}

.detail-aside .location-map-heading h2 {
  font-size: 17px;
}

.detail-aside .location-map-card {
  min-height: 220px;
  border-radius: 10px;
}

.detail-aside .location-map-canvas {
  height: 220px;
}

@media (max-width: 720px) {
  .location-map-section {
    padding: 0 18px 20px;
  }

  .location-map-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }

  .location-map-canvas {
    height: 280px;
  }

  .detail-aside .location-map-canvas {
    height: 220px;
  }
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
