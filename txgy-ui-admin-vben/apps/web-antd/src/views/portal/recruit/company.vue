<script lang="ts" setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';
import { Empty, Tag } from 'ant-design-vue';

import { getTenantSimpleList } from '#/api/core/auth';
import { getPortalRecruitCompanyProfile, type HrmRecruitCompanyApi } from '#/api/hrm/recruit/company';
import { getPortalRecruitPostPage, type HrmPortalRecruitApi } from '#/api/hrm/portal/recruit';
import { loadBaiduMapSdk } from '#/components/map';

const route = useRoute();
const router = useRouter();
const activeTab = ref<'about' | 'jobs'>('about');
const companyName = ref('招聘企业');
const profile = ref<HrmRecruitCompanyApi.Profile>({});
const posts = ref<HrmPortalRecruitApi.Post[]>([]);
const mapRef = ref<HTMLElement>();
const mapError = ref('');
let map: any = null;

const locations = computed(() => [...new Set(posts.value.map((post) => post.areaName?.trim()).filter(Boolean) as string[])]);
const location = computed(() => profile.value.registeredAddress || locations.value[0] || '暂无数据');
const fields = computed(() => [
  ['企业名称', profile.value.companyName || companyName.value],
  ['企业类型', profile.value.companyType],
  ['法定代表人', profile.value.legalRepresentative],
  ['成立日期', profile.value.establishDate],
  ['经营状态', profile.value.businessStatus],
  ['注册资本', profile.value.registeredCapital],
  ['注册地址', profile.value.registeredAddress],
  ['所属地区', profile.value.region],
  ['统一社会信用代码', profile.value.unifiedSocialCreditCode],
  ['所属行业', profile.value.industry],
].filter(([, value]) => value));

function resolveCompanyName(savedName: string | undefined, tenantName: string) {
  const saved = savedName?.trim();
  if (!saved || (saved !== tenantName && tenantName.startsWith(saved))) return tenantName;
  return saved;
}

function openPost(id?: number) {
  if (id) void router.push({ name: 'PortalRecruitDetail', query: { id: String(id) } });
}

function openMap() {
  if (location.value === '暂无数据') return;
  window.open(`https://map.baidu.com/search/${encodeURIComponent(location.value)}`, '_blank', 'noopener,noreferrer');
}

async function init() {
  const tenantId = Number(route.query.tenantId);
  if (!Number.isSafeInteger(tenantId) || tenantId <= 0) return;
  const [tenantList, nextProfile, firstPage] = await Promise.all([
    getTenantSimpleList(),
    getPortalRecruitCompanyProfile(tenantId),
    getPortalRecruitPostPage({ pageNo: 1, pageSize: 100 }),
  ]);
  companyName.value = (tenantList as Array<{ id: number; name?: string }>).find((tenant) => tenant.id === tenantId)?.name || companyName.value;
  profile.value = {
    ...(nextProfile || {}),
    companyName: nextProfile?.companyNameCustomized
      ? nextProfile.companyName
      : resolveCompanyName(nextProfile?.companyName, companyName.value),
  };
  posts.value = (firstPage.list || []).filter((post: HrmPortalRecruitApi.Post) => post.tenantId === tenantId);
  await nextTick();
  if (!mapRef.value || location.value === '暂无数据') return;
  try {
    await loadBaiduMapSdk();
    if (!window.BMapGL) return;
    map = new window.BMapGL.Map(mapRef.value);
    map.centerAndZoom(new window.BMapGL.Point(116.404, 39.915), 11);
    map.enableScrollWheelZoom();
    const search = new window.BMapGL.LocalSearch(map, {
      pageCapacity: 1,
      onSearchComplete: (result: any) => {
        const point = result?._pois?.[0]?.point;
        if (!point) { mapError.value = '未找到该企业地址'; return; }
        const target = new window.BMapGL.Point(point.lng, point.lat);
        map.clearOverlays();
        map.addOverlay(new window.BMapGL.Marker(target));
        map.centerAndZoom(target, 16);
      },
    });
    search.search(location.value);
  } catch (error) {
    mapError.value = error instanceof Error ? error.message : '地图加载失败';
  }
}

onMounted(() => void init());
onUnmounted(() => { map?.destroy?.(); map = null; });
</script>

<template>
  <div class="portal-company-page">
    <button class="back-link" type="button" @click="router.back()"><IconifyIcon icon="lucide:arrow-left" /> 返回职位详情</button>
    <header class="company-hero">
      <div><span>企业招聘档案</span><h1>{{ profile.companyName || companyName }}</h1><p>{{ posts.length }} 个在招职位 · {{ profile.industry || '行业信息待完善' }}</p></div>
      <Tag color="blue">只读企业信息</Tag>
    </header>
    <nav class="company-tabs">
      <button :class="{ active: activeTab === 'about' }" type="button" @click="activeTab = 'about'">公司简介</button>
      <button :class="{ active: activeTab === 'jobs' }" type="button" @click="activeTab = 'jobs'">在招职位: {{ posts.length }}</button>
    </nav>
    <template v-if="activeTab === 'about'">
    <section class="company-section">
      <h2>工商信息</h2>
      <div v-if="fields.length" class="profile-grid"><div v-for="[label, value] in fields" :key="label"><small>{{ label }}</small><strong>{{ value }}</strong></div></div>
      <Empty v-else description="企业暂未维护工商信息" />
      <p v-if="profile.introduction" class="company-copy">{{ profile.introduction }}</p>
    </section>
    <section class="company-section">
      <h2>公司地址</h2>
      <button v-if="location !== '暂无数据'" class="address-link" type="button" @click="openMap"><IconifyIcon icon="lucide:map-pin" /> {{ location }}</button>
      <div v-if="location !== '暂无数据'" class="map-shell"><div ref="mapRef" class="map-canvas"></div><div v-if="mapError" class="map-error">{{ mapError }}</div></div>
    </section>
    </template>
    <section v-else class="company-section jobs-section">
      <h2>在招职位</h2>
      <Empty v-if="!posts.length" description="当前暂无在招职位" />
      <button v-for="post in posts" :key="post.id" class="job-item" type="button" @click="openPost(post.id)"><span><strong>{{ post.postName }}</strong><small>{{ post.areaName || '工作地点待确认' }} · {{ post.postTypeName || '职位类型待确认' }}</small></span><b>查看职位详情 →</b></button>
    </section>
  </div>
</template>

<style scoped>
.portal-company-page { display: grid; gap: 18px; color: #496b7a; }
.back-link { display: inline-flex; align-items: center; gap: 6px; width: fit-content; padding: 0; border: 0; color: #64748b; background: transparent; cursor: pointer; }
.company-hero { display: flex; justify-content: space-between; gap: 20px; padding: 30px 36px; border: 1px solid #d4e7ef; border-radius: 12px; background: linear-gradient(120deg, #f5fcff, #fff); }
.company-hero span { color: #0f8f8c; font-size: 13px; font-weight: 700; }
.company-hero h1 { margin: 9px 0; color: #123b52; font-size: 30px; }
.company-hero p { margin: 0; color: #7593a0; }
.company-tabs { display: flex; gap: 30px; padding: 0 28px; border-bottom: 1px solid #d4e7ef; }
.company-tabs button { position: relative; padding: 15px 4px 13px; border: 0; color: #7593a0; background: transparent; cursor: pointer; font: inherit; }
.company-tabs button.active { border-bottom: 3px solid #2f80ed; color: #2f80ed; }
.company-section { padding: 28px 36px; border: 1px solid #d4e7ef; border-radius: 12px; background: #fff; }
.company-section h2 { margin: 0 0 22px; color: #123b52; font-size: 21px; }
.profile-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 24px 42px; }.profile-grid div { display: grid; gap: 7px; }.profile-grid small { color: #8496a7; }.profile-grid strong { color: #1f3549; font-weight: 500; overflow-wrap: anywhere; }.company-copy { margin: 28px 0 0; line-height: 1.8; white-space: pre-wrap; }
.address-link { display: inline-flex; align-items: center; gap: 7px; padding: 0; border: 0; color: #0f8f8c; background: transparent; cursor: pointer; font-size: 16px; }.map-shell { position: relative; overflow: hidden; margin-top: 16px; border-radius: 10px; }.map-canvas { height: 360px; }.map-error { position: absolute; inset: 0; display: grid; place-items: center; background: rgb(255 255 255 / 80%); color: #b45309; }
.job-item { display: flex; justify-content: space-between; gap: 18px; width: 100%; padding: 17px 0; border: 0; border-bottom: 1px solid #e7f1f5; color: inherit; background: transparent; cursor: pointer; text-align: left; }.job-item span { display: grid; gap: 7px; }.job-item strong { color: #123b52; font-size: 16px; }.job-item small { color: #8496a7; }.job-item b { color: #2f80ed; font-weight: 500; white-space: nowrap; }.job-item:hover strong { color: #0f8f8c; }
@media (max-width: 700px) { .company-hero, .company-section { padding: 22px 18px; }.company-hero h1 { font-size: 24px; }.profile-grid { grid-template-columns: 1fr; }.job-item { align-items: flex-start; flex-direction: column; }.map-canvas { height: 280px; } }
</style>
