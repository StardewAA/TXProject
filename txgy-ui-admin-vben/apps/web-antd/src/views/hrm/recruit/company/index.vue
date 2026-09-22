<script lang="ts" setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { useAccessStore } from '@vben/stores';
import { Button, Card, Empty, Input, Spin, Tag, message } from 'ant-design-vue';

import { getTenantSimpleList } from '#/api/core/auth';
import {
  getRecruitCompanyProfile,
  saveRecruitCompanyProfile,
  type HrmRecruitCompanyApi,
} from '#/api/hrm/recruit/company';
import { getRecruitPostPage, type HrmRecruitPostApi } from '#/api/hrm/recruit/post';
import { loadBaiduMapSdk } from '#/components/map';

defineOptions({ name: 'HrmRecruitCompany' });

const router = useRouter();
const accessStore = useAccessStore();
const activeTab = ref<'about' | 'jobs'>('about');
const loading = ref(true);
const mapLoading = ref(false);
const mapError = ref('');
const companyName = ref('');
const posts = ref<HrmRecruitPostApi.RecruitPost[]>([]);
const profile = ref<HrmRecruitCompanyApi.Profile>({});
const savingProfile = ref(false);
const mapRef = ref<HTMLElement>();
let map: any = null;

const locations = computed(() => {
  const values = posts.value
    .map((post) => post.areaName?.trim())
    .filter((value): value is string => Boolean(value));
  return [...new Set(values)];
});
const primaryLocation = computed(() => locations.value[0] || '暂无数据');
const industry = computed(() => {
  if (profile.value.industry?.trim()) return profile.value.industry.trim();
  const values = posts.value
    .map((post) => post.postTypeName?.trim())
    .filter((value): value is string => Boolean(value));
  return [...new Set(values)].slice(0, 3).join('、') || '暂无数据';
});
const profileFields = [
  ['companyName', '企业名称'], ['companyType', '企业类型'],
  ['legalRepresentative', '法定代表人'], ['establishDate', '成立日期'],
  ['businessStatus', '经营状态'], ['registeredCapital', '注册资本'],
  ['registeredAddress', '注册地址'], ['region', '所属地区'],
  ['unifiedSocialCreditCode', '统一社会信用代码'], ['industry', '所属行业'],
] as const;

function resolveCompanyName(savedName: string | undefined, tenantName: string) {
  const saved = savedName?.trim();
  if (!saved || (saved !== tenantName && tenantName.startsWith(saved))) {
    return tenantName;
  }
  return saved;
}

function formatSalary(post: HrmRecruitPostApi.RecruitPost) {
  if (post.salaryUnit === -1 || post.minSalary === -1 || post.maxSalary === -1) return '面议';
  if (post.minSalary == null && post.maxSalary == null) return '薪资面议';
  const format = (value?: number) => (value == null ? '' : `${value / 1000}K`);
  if (post.minSalary != null && post.maxSalary != null) return `${format(post.minSalary)}-${format(post.maxSalary)}`;
  return post.minSalary != null ? `${format(post.minSalary)}起` : `${format(post.maxSalary)}以内`;
}

function openJob(post: HrmRecruitPostApi.RecruitPost) {
  if (post.id) void router.push({ name: 'HrmRecruitPostDetail', params: { id: post.id } });
}

function openMapPage(location = primaryLocation.value) {
  if (location === '暂无数据') return;
  window.open(`https://map.baidu.com/search/${encodeURIComponent(location)}`, '_blank', 'noopener,noreferrer');
}

async function loadCompany() {
  try {
    const tenants = await getTenantSimpleList();
    const tenantName = (tenants as Array<{ id: number; name?: string }>).find((tenant) => tenant.id === accessStore.tenantId)?.name || '当前企业';
    const savedProfile = await getRecruitCompanyProfile();
    profile.value = {
      ...savedProfile,
      companyName: savedProfile.companyNameCustomized
        ? savedProfile.companyName
        : resolveCompanyName(savedProfile.companyName, tenantName),
    };
    companyName.value = profile.value.companyName || tenantName;
    const result = await getRecruitPostPage({ pageNo: 1, pageSize: 100 });
    posts.value = result.list || [];
  } catch (error) {
    message.error('企业信息加载失败');
    console.warn('[HrmRecruitCompany] load failed', error);
  } finally {
    loading.value = false;
  }
}

async function saveProfile() {
  savingProfile.value = true;
  try {
    await saveRecruitCompanyProfile({
      ...profile.value,
      companyName: profile.value.companyName?.trim() || companyName.value,
      companyNameCustomized: true,
    });
    message.success('企业档案已保存');
    await loadCompany();
  } finally {
    savingProfile.value = false;
  }
}

async function initMap() {
  if (!mapRef.value || !locations.value.length) return;
  mapLoading.value = true;
  mapError.value = '';
  try {
    await loadBaiduMapSdk();
    if (!mapRef.value || !window.BMapGL) return;
    map = new window.BMapGL.Map(mapRef.value);
    map.centerAndZoom(new window.BMapGL.Point(116.404, 39.915), 11);
    map.enableScrollWheelZoom();
    map.addControl(new window.BMapGL.NavigationControl());
    map.addControl(new window.BMapGL.ZoomControl());
    const search = new window.BMapGL.LocalSearch(map, {
      pageCapacity: 1,
      onSearchComplete: (result: any) => {
        const point = result?._pois?.[0]?.point;
        if (!point) { mapError.value = '未找到工作地点，请点击地址打开地图搜索'; return; }
        const target = new window.BMapGL.Point(point.lng, point.lat);
        map.clearOverlays();
        map.addOverlay(new window.BMapGL.Marker(target));
        map.centerAndZoom(target, 14);
      },
    });
    search.search(locations.value[0]);
  } catch (error) {
    mapError.value = error instanceof Error ? error.message : '地图加载失败，请点击地址打开地图搜索';
  } finally {
    mapLoading.value = false;
  }
}

onMounted(async () => {
  await loadCompany();
  await nextTick();
  void initMap();
});

onUnmounted(() => {
  map?.destroy?.();
  map = null;
});
</script>

<template>
  <div class="company-page">
    <Spin :spinning="loading">
      <header class="company-hero">
        <div>
          <h1>{{ companyName || '当前企业' }}</h1>
          <p>{{ posts.length ? `${posts.length} 个在招职位` : '暂无公开职位' }} · {{ industry }}</p>
        </div>
        <Tag color="blue">招聘企业</Tag>
      </header>

      <nav class="company-tabs" aria-label="企业信息页签">
        <button :class="{ active: activeTab === 'about' }" type="button" @click="activeTab = 'about'">公司简介</button>
        <button :class="{ active: activeTab === 'jobs' }" type="button" @click="activeTab = 'jobs'">在招职位: {{ posts.length }}</button>
      </nav>

      <template v-if="activeTab === 'about'">
        <Card title="工商信息" class="company-card">
          <template #extra><Button type="primary" :loading="savingProfile" @click="saveProfile">保存档案</Button></template>
          <div class="profile-grid">
            <div v-for="[field, label] in profileFields" :key="field" class="profile-field">
              <span>{{ label }}</span>
              <Input v-model:value="profile[field]" :placeholder="`请输入${label}`" />
            </div>
          </div>
          <div class="profile-wide-field">
            <span>经营范围</span>
            <Input.TextArea v-model:value="profile.businessScope" :rows="3" placeholder="请输入经营范围，没有数据可留空" />
          </div>
          <div class="profile-wide-field">
            <span>企业简介</span>
            <Input.TextArea v-model:value="profile.introduction" :rows="3" placeholder="请输入企业简介，没有数据可留空" />
          </div>
          <div class="profile-note">未维护的字段允许为空；保存后个人端职位详情中的企业档案会同步展示为只读内容。</div>
        </Card>

        <Card title="公司地址" class="company-card location-card">
          <div v-if="locations.length" class="location-heading">
            <span class="location-name">{{ primaryLocation }}</span>
            <a-button type="link" @click="openMapPage()">在地图中打开</a-button>
          </div>
          <Empty v-else description="暂无已发布职位工作地点" />
          <div v-if="locations.length" class="map-shell">
            <div ref="mapRef" class="map-canvas"></div>
            <div v-if="mapLoading" class="map-state">地图加载中...</div>
            <div v-else-if="mapError" class="map-state error">{{ mapError }}</div>
          </div>
          <div v-if="locations.length > 1" class="location-list">
            <button v-for="location in locations" :key="location" type="button" @click="openMapPage(location)">{{ location }}</button>
          </div>
        </Card>
      </template>

      <Card v-else title="在招职位" class="company-card jobs-card">
        <Empty v-if="!posts.length" description="当前暂无在招职位" />
        <div v-else class="job-list">
          <button v-for="post in posts" :key="post.id" class="job-item" type="button" @click="openJob(post)">
            <span class="job-main"><strong>{{ post.postName }}</strong><small>{{ post.deptName || '招聘部门待确认' }} · {{ post.areaName || '工作地点待确认' }}</small></span>
            <span class="job-side"><b>{{ formatSalary(post) }}</b><i>查看职位详情 →</i></span>
          </button>
        </div>
      </Card>
    </Spin>
  </div>
</template>

<style scoped>
.company-page { display: grid; gap: 16px; padding-bottom: 24px; }
.company-hero { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; padding: 28px 32px; border-radius: 10px; background: linear-gradient(135deg, #e7f3ff, #f7fbff); }
.company-hero h1 { margin: 0; color: #102a43; font-size: 30px; line-height: 1.2; }
.company-hero p { margin: 10px 0 0; color: #5b7083; font-size: 14px; }
.company-tabs { display: flex; gap: 30px; padding: 0 28px; border-bottom: 1px solid #dbe7f0; background: #fff; }
.company-tabs button { position: relative; padding: 15px 4px 13px; border: 0; color: #52677a; background: transparent; cursor: pointer; font-size: 16px; }
.company-tabs button.active { color: #1677ff; font-weight: 600; }
.company-tabs button.active::after { position: absolute; right: 0; bottom: -1px; left: 0; height: 3px; border-radius: 3px 3px 0 0; background: #1677ff; content: ''; }
.company-card { border-radius: 10px; }
.profile-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 28px 42px; }
.profile-field { display: grid; gap: 8px; min-width: 0; }
.profile-field span { color: #8496a7; font-size: 13px; }
.profile-field strong { overflow-wrap: anywhere; color: #1f3549; font-size: 15px; font-weight: 500; line-height: 1.6; }
.profile-note { margin-top: 28px; color: #9aaabd; font-size: 12px; }
.profile-wide-field { display: grid; gap: 8px; margin-top: 24px; }
.profile-wide-field > span { color: #8496a7; font-size: 13px; }
.location-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.location-name { color: #123b52; font-size: 16px; font-weight: 600; }
.map-shell { position: relative; overflow: hidden; min-height: 360px; border: 1px solid #e2e8f0; border-radius: 8px; background: #f8fafc; }
.map-canvas { height: 360px; width: 100%; }
.map-state { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; color: #64748b; background: rgb(248 250 252 / 82%); }
.map-state.error { color: #b45309; }
.location-list { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
.location-list button { padding: 5px 10px; border: 1px solid #d7e3ee; border-radius: 4px; color: #42657d; background: #f8fbfe; cursor: pointer; font-size: 12px; }
.job-list { display: grid; gap: 10px; }
.job-item { display: flex; align-items: center; justify-content: space-between; gap: 24px; width: 100%; padding: 18px 20px; border: 1px solid #e2eaf2; border-radius: 8px; color: inherit; background: #fff; cursor: pointer; text-align: left; transition: border-color 160ms ease, box-shadow 160ms ease; }
.job-item:hover { border-color: #91caff; box-shadow: 0 6px 18px rgb(22 119 255 / 10%); }
.job-main { display: grid; gap: 7px; min-width: 0; }
.job-main strong { overflow-wrap: anywhere; color: #123b52; font-size: 16px; }
.job-main small { color: #8496a7; font-size: 13px; }
.job-side { display: grid; flex: 0 0 auto; gap: 6px; justify-items: end; }
.job-side b { color: #1677ff; font-size: 15px; }
.job-side i { color: #7b91a4; font-size: 12px; font-style: normal; }
@media (max-width: 800px) { .profile-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .job-item { align-items: flex-start; flex-direction: column; } .job-side { justify-items: start; } }
@media (max-width: 520px) { .company-hero { padding: 22px 18px; } .company-hero h1 { font-size: 23px; } .profile-grid { grid-template-columns: 1fr; } .map-canvas { height: 280px; } }
</style>
