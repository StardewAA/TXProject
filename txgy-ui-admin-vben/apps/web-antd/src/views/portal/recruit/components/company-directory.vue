<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { RouterLink } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

import { Modal, Pagination } from 'ant-design-vue';

import {
  getPortalRecruitCompanyProfile,
  getPortalRecruitCompanyList,
  getPortalRecruitPostPage,
  type HrmPortalRecruitApi,
} from '#/api/hrm/portal/recruit';
import type { HrmRecruitCompanyApi } from '#/api/hrm/recruit/company';
import { formatRecruitLocation } from '../utils';

interface CompanyEntry extends HrmPortalRecruitApi.Company {
  posts: HrmPortalRecruitApi.Post[];
  logo?: string;
}

const companies = ref<CompanyEntry[]>([]);
const loading = ref(true);
const loadFailed = ref(false);
const keyword = ref('');
const searchTerm = ref('');
const searchScope = ref('all');
const city = ref('');
const postType = ref('');
const hiring = ref('');
const postCount = ref('');
const sort = ref('hiring');
const page = ref(1);
const pageSize = 12;
const selectedCompany = ref<CompanyEntry>();
const selectedProfile = ref<Record<string, string | undefined> & HrmRecruitCompanyApi.Profile>({});
const detailOpen = ref(false);
const detailPage = ref(1);
/** 从招聘首页「热招企业」跳转过来时，待打开的企业 id（companies 加载完成后消费） */
const pendingOpenCompanyId = ref<number>();
const detailPageSize = 6;

const hiringCompanyCount = computed(() => companies.value.filter((company) => company.posts.length > 0).length);
const totalPostCount = computed(() => companies.value.reduce((total, company) => total + company.posts.length, 0));
const selectedCompanyCities = computed(() => [...new Set(
  selectedCompany.value?.posts.map(getCity).filter(Boolean) || [],
)]);

const hiringOptions = [
  { label: '不限', value: '' },
  { label: '正在招聘', value: 'hiring' },
  { label: '暂无公开职位', value: 'none' },
];
const countOptions = [
  { label: '不限', value: '' },
  { label: '1–9 个', value: '1-9' },
  { label: '10–49 个', value: '10-49' },
  { label: '50 个以上', value: '50+' },
];

function getCity(post: HrmPortalRecruitApi.Post) {
  const parts = formatRecruitLocation(post).trim().split(/\s+/) || [];
  return parts[1] || parts[0] || '';
}

function getPostType(post: HrmPortalRecruitApi.Post) {
  return post.postTypeName?.trim() || '未分类';
}

const cities = computed(() => [...new Set(
  companies.value.flatMap((company) => company.posts.map(getCity)).filter(Boolean),
)].sort((left, right) => left.localeCompare(right, 'zh-CN')));
const postTypes = computed(() => [...new Set(
  companies.value.flatMap((company) => company.posts.map(getPostType)),
)].sort((left, right) => left.localeCompare(right, 'zh-CN')));

const filteredCompanies = computed(() => {
  const term = searchTerm.value.toLocaleLowerCase();
  const result = companies.value.flatMap((company) => {
    const count = company.posts.length;
    if (hiring.value === 'hiring' && count === 0) return [];
    if (hiring.value === 'none' && count > 0) return [];
    if (postCount.value === '1-9' && (count < 1 || count > 9)) return [];
    if (postCount.value === '10-49' && (count < 10 || count > 49)) return [];
    if (postCount.value === '50+' && count < 50) return [];
    const companyMatches = company.name.toLocaleLowerCase().includes(term);
    if (term && searchScope.value === 'company' && !companyMatches) return [];
    const searchPosts = Boolean(term && (searchScope.value === 'post' || (
      searchScope.value === 'all' && !companyMatches
    )));
    const matchingPosts = company.posts.filter((post) => (
      (!city.value || getCity(post) === city.value)
      && (!postType.value || getPostType(post) === postType.value)
      && (!searchPosts || post.postName.toLocaleLowerCase().includes(term))
    ));
    if ((city.value || postType.value || searchPosts) && !matchingPosts.length) return [];
    return [{ ...company, posts: matchingPosts, totalPosts: count }];
  });
  return result.sort((left, right) => (
    (sort.value === 'hiring' ? right.totalPosts - left.totalPosts : 0)
    || left.name.localeCompare(right.name, 'zh-CN')
    || left.id - right.id
  ));
});

const visibleCompanies = computed(() => filteredCompanies.value.slice(
  (page.value - 1) * pageSize, page.value * pageSize,
));
const detailPosts = computed(() => selectedCompany.value?.posts.slice(
  (detailPage.value - 1) * detailPageSize, detailPage.value * detailPageSize,
) || []);
const activeFilters = computed(() => Boolean(
  searchTerm.value || city.value || postType.value || hiring.value || postCount.value,
));

watch([searchTerm, searchScope, city, postType, hiring, postCount, sort], () => {
  page.value = 1;
});

function search() {
  searchTerm.value = keyword.value.trim();
  page.value = 1;
}

function resetFilters() {
  keyword.value = '';
  searchTerm.value = '';
  searchScope.value = 'all';
  city.value = '';
  postType.value = '';
  hiring.value = '';
  postCount.value = '';
  page.value = 1;
}

function openCompany(company: CompanyEntry) {
  selectedCompany.value = company;
  detailPage.value = 1;
  detailOpen.value = true;
  selectedProfile.value = {};
  void getPortalRecruitCompanyProfile(company.id).then((profile) => {
    selectedProfile.value = profile || {};
  }).catch(() => {
    selectedProfile.value = {};
  });
}

function openCompanyById(id: number) {
  const company = companies.value.find((item) => item.id === id);
  if (company) {
    openCompany(company);
    return;
  }
  // 从招聘首页点击「热招企业」跳转过来时，本组件刚挂载、companies 仍在异步加载，
  // 此时直接查找必然落空。先记下目标 id，等 loadCompanies 完成后自动打开。
  pendingOpenCompanyId.value = id;
}

defineExpose({ openCompanyById });

function formatSalary(post: HrmPortalRecruitApi.Post) {
  if (post.salaryUnit === -1 || (post.minSalary ?? 0) < 0 || (post.maxSalary ?? 0) < 0) return '面议';
  const format = (value: number) => `${Number((value / 1000).toFixed(2))}K`;
  if (post.minSalary != null && post.maxSalary != null) {
    return `${format(post.minSalary)}–${format(post.maxSalary)}`;
  }
  if (post.minSalary != null) return `${format(post.minSalary)}起`;
  if (post.maxSalary != null) return `${format(post.maxSalary)}以内`;
  return '薪资面议';
}

async function loadCompanies() {
  loading.value = true;
  loadFailed.value = false;
  try {
    const [companyList, firstPage] = await Promise.all([
      getPortalRecruitCompanyList(),
      getPortalRecruitPostPage({ pageNo: 1, pageSize: 100 }),
    ]);
    const allPosts = [...firstPage.list];
    for (let pageNo = 2; allPosts.length < firstPage.total; pageNo += 1) {
      const nextPage = await getPortalRecruitPostPage({ pageNo, pageSize: 100 });
      if (!nextPage.list.length) break;
      allPosts.push(...nextPage.list);
    }
    const postsByCompany = new Map<number, Map<number, HrmPortalRecruitApi.Post>>();
    for (const post of allPosts) {
      if (post.tenantId == null) continue;
      const companyPosts = postsByCompany.get(post.tenantId) || new Map();
      companyPosts.set(post.id, post);
      postsByCompany.set(post.tenantId, companyPosts);
    }
    companies.value = companyList.map((company) => ({
      id: company.id,
      name: company.name,
      posts: [...(postsByCompany.get(company.id)?.values() || [])]
        .sort((left, right) => right.id - left.id),
      logo: [...(postsByCompany.get(company.id)?.values() || [])].find((post) => post.companyLogo)?.companyLogo,
    }));
    // 消费「从招聘首页热招企业跳转过来」的待打开请求
    if (pendingOpenCompanyId.value != null) {
      const targetId = pendingOpenCompanyId.value;
      pendingOpenCompanyId.value = undefined;
      const target = companies.value.find((item) => item.id === targetId);
      if (target) openCompany(target);
    }
  } catch {
    companies.value = [];
    loadFailed.value = true;
  } finally {
    loading.value = false;
  }
}

onMounted(loadCompanies);
</script>

<template>
  <section class="company-directory" aria-label="招聘公司">
    <header class="company-directory-intro">
      <div class="company-intro-copy">
        <span class="company-intro-label"><i></i>企业招聘地图 · TALENT MAP</span>
        <h1>找到与你同频的企业</h1>
        <p>浏览行业伙伴的公开职位，选择下一站职业方向。</p>
      </div>
      <div class="company-directory-stats" aria-label="企业招聘概览">
        <div><strong>{{ loading ? '—' : companies.length }}</strong><span>合作企业</span></div>
        <i></i>
        <div><strong>{{ loading ? '—' : hiringCompanyCount }}</strong><span>正在招聘</span></div>
        <i></i>
        <div><strong>{{ loading ? '—' : totalPostCount }}</strong><span>公开职位</span></div>
      </div>
    </header>

    <div class="company-filters">
      <form class="company-search" role="search" @submit.prevent="search">
        <label class="search-scope">
          <span class="sr-only">搜索范围</span>
          <select v-model="searchScope">
            <option value="all">职位 / 公司</option>
            <option value="company">公司名称</option>
            <option value="post">职位名称</option>
          </select>
        </label>
        <input v-model="keyword" aria-label="搜索职位或公司" placeholder="搜索职位、公司" type="search" />
        <button class="search-submit" type="submit">搜索</button>
      </form>

      <div class="company-filter-row">
        <span class="filter-label">招聘地点：</span>
        <div class="filter-options" role="group" aria-label="招聘地点">
          <button type="button" :class="{ active: !city }" :aria-pressed="!city" @click="city = ''">不限</button>
          <button v-for="option in cities" :key="option" type="button" :class="{ active: city === option }" :aria-pressed="city === option" @click="city = option">{{ option }}</button>
        </div>
      </div>
      <div class="company-filter-row">
        <span class="filter-label">职位类型：</span>
        <div class="filter-options" role="group" aria-label="职位类型">
          <button type="button" :class="{ active: !postType }" :aria-pressed="!postType" @click="postType = ''">不限</button>
          <button v-for="option in postTypes" :key="option" type="button" :class="{ active: postType === option }" :aria-pressed="postType === option" @click="postType = option">{{ option }}</button>
        </div>
      </div>
      <div class="company-filter-row">
        <span class="filter-label">招聘状态：</span>
        <div class="filter-options" role="group" aria-label="招聘状态">
          <button v-for="option in hiringOptions" :key="option.value" type="button" :class="{ active: hiring === option.value }" :aria-pressed="hiring === option.value" @click="hiring = option.value">{{ option.label }}</button>
        </div>
      </div>
      <div class="company-filter-row">
        <span class="filter-label">在招职位：</span>
        <div class="filter-options" role="group" aria-label="在招职位">
          <button v-for="option in countOptions" :key="option.value" type="button" :class="{ active: postCount === option.value }" :aria-pressed="postCount === option.value" @click="postCount = option.value">{{ option.label }}</button>
        </div>
        <button class="clear-filters" type="button" @click="resetFilters">清空筛选条件</button>
      </div>
    </div>

    <div class="company-results" :aria-busy="loading">
      <div class="company-results-toolbar">
        <div class="company-sort">
          <span class="company-sort-label"><IconifyIcon icon="lucide:building-2" />发现好公司</span>
          <button type="button" :class="{ active: sort === 'hiring' }" :aria-pressed="sort === 'hiring'" @click="sort = 'hiring'">在招优先</button>
          <button type="button" :class="{ active: sort === 'name' }" :aria-pressed="sort === 'name'" @click="sort = 'name'">名称排序</button>
        </div>
        <span v-if="!loading && !loadFailed" class="company-result-count" role="status">共 {{ filteredCompanies.length }} 家公司</span>
      </div>

      <div v-if="loading" class="company-grid" aria-label="正在加载公司信息">
        <div v-for="item in 8" :key="item" class="company-skeleton"><a-skeleton active :paragraph="{ rows: 2 }" /></div>
      </div>
      <div v-else-if="loadFailed" class="company-empty" role="alert">
        <IconifyIcon icon="lucide:cloud-off" />
        <h3>公司信息加载失败</h3>
        <p>请稍后重试，获取最新的公司与职位信息。</p>
        <button type="button" @click="loadCompanies">重新加载</button>
      </div>
      <div v-else-if="!filteredCompanies.length" class="company-empty">
        <IconifyIcon icon="lucide:search-x" />
        <h3>{{ activeFilters ? '暂无符合条件的公司' : '暂无公司信息' }}</h3>
        <p>{{ activeFilters ? '试试其他关键词，或调整招聘地点与职位类型。' : '公司信息更新后将在这里展示。' }}</p>
        <button v-if="activeFilters" type="button" @click="resetFilters">清空筛选条件</button>
      </div>
      <div v-else class="company-grid">
        <article v-for="company in visibleCompanies" :key="company.id" class="company-card">
          <button class="company-card-main" type="button" :aria-label="`查看${company.name}的职位`" @click="openCompany(company)">
            <span class="company-monogram" :class="{ muted: !company.totalPosts }" aria-hidden="true">
              <img v-if="company.logo" :src="company.logo" :alt="`${company.name} logo`" />
              <span v-else>{{ company.name.slice(0, 2) }}</span>
            </span>
            <span class="company-summary">
              <span class="company-name" :title="company.name">{{ company.name }}</span>
              <span class="company-tags">
                <span :class="{ 'hiring-tag': company.totalPosts }">{{ company.totalPosts ? `${company.totalPosts} 个在招职位` : '暂无公开职位' }}</span>
                <span v-if="company.posts[0]">{{ getPostType(company.posts[0]) }}</span>
              </span>
            </span>
          </button>
          <RouterLink v-if="company.posts[0]" class="company-hot-post" :to="{ name: 'PortalRecruitDetail', query: { id: company.posts[0].id } }">
            <span class="hot-label">在招</span>
            <span class="hot-name" :title="company.posts[0].postName">{{ company.posts[0].postName }}</span>
            <span class="hot-salary">{{ formatSalary(company.posts[0]) }}</span>
          </RouterLink>
          <div v-else class="company-no-post"><span>暂无公开职位</span><span>敬请关注</span></div>
        </article>
      </div>
      <Pagination v-if="!loading && !loadFailed && filteredCompanies.length > pageSize" v-model:current="page" class="company-pagination" :page-size="pageSize" :total="filteredCompanies.length" :show-size-changer="false" />
    </div>

    <Modal v-model:open="detailOpen" :title="null" :footer="null" :width="820" centered wrap-class-name="company-detail-modal">
      <div class="company-detail">
        <header class="company-detail-header">
          <div class="company-detail-monogram" :class="{ muted: !selectedCompany?.posts.length }" aria-hidden="true">
            <img v-if="selectedCompany?.logo" :src="selectedCompany.logo" :alt="`${selectedCompany.name} logo`" />
            <span v-else>{{ selectedCompany?.name.slice(0, 2) }}</span>
          </div>
          <div class="company-detail-heading">
            <span class="company-detail-eyebrow">企业招聘档案 / OPEN ROLES</span>
            <h2>{{ selectedCompany?.name }}</h2>
            <div class="company-detail-meta">
              <span :class="{ muted: !selectedCompany?.posts.length }"><i></i>{{ selectedCompany?.posts.length ? '正在招聘' : '暂无公开职位' }}</span>
              <span>{{ selectedCompany?.posts.length || 0 }} 个公开职位</span>
              <span v-if="selectedCompanyCities.length">{{ selectedCompanyCities.slice(0, 2).join(' · ') }}</span>
            </div>
          </div>
          <span class="company-detail-mark" aria-hidden="true">TX<span>HR</span></span>
        </header>

        <section class="company-profile-readonly">
          <div class="company-profile-title">工商信息</div>
          <div v-if="Object.values(selectedProfile).some(Boolean)" class="company-profile-grid">
            <div v-for="item in [
              ['企业名称', selectedProfile.companyName || selectedCompany?.name],
              ['企业类型', selectedProfile.companyType],
              ['法定代表人', selectedProfile.legalRepresentative],
              ['成立日期', selectedProfile.establishDate],
              ['经营状态', selectedProfile.businessStatus],
              ['注册资本', selectedProfile.registeredCapital],
              ['注册地址', selectedProfile.registeredAddress],
              ['所属行业', selectedProfile.industry],
            ].filter(([, value]) => value)" :key="item[0]">
              <small>{{ item[0] }}</small><strong>{{ item[1] }}</strong>
            </div>
          </div>
          <p v-if="selectedProfile.introduction" class="company-profile-intro">{{ selectedProfile.introduction }}</p>
          <div v-if="!Object.values(selectedProfile).some(Boolean)" class="company-profile-empty">企业档案尚未维护</div>
        </section>

        <div class="company-detail-toolbar">
          <div>
            <span class="company-detail-kicker">职位机会</span>
            <p class="company-detail-caption">{{ activeFilters ? '符合当前筛选条件的职位' : '这家企业正在寻找这样的伙伴' }} · {{ selectedCompany?.posts.length || 0 }} 个</p>
          </div>
          <span class="company-detail-tip">点击岗位查看详情 <IconifyIcon icon="lucide:arrow-down-right" /></span>
        </div>

        <div v-if="!selectedCompany?.posts.length" class="company-empty">
          <IconifyIcon icon="lucide:briefcase-business" />
          <h3>这家公司暂无公开职位</h3>
          <p>你可以浏览其他正在招聘的公司。</p>
        </div>
        <div v-else class="company-detail-post-list">
          <RouterLink v-for="(post, index) in detailPosts" :key="post.id" class="company-detail-post" :to="{ name: 'PortalRecruitDetail', query: { id: post.id } }">
            <span class="company-detail-post-index">{{ String((detailPage - 1) * detailPageSize + index + 1).padStart(2, '0') }}</span>
            <span class="company-detail-post-copy"><strong>{{ post.postName }}</strong><small><IconifyIcon icon="lucide:map-pin" />{{ post.areaName || '地点待确认' }}<i></i>{{ getPostType(post) }}</small></span>
            <span class="company-detail-post-reward"><b>{{ formatSalary(post) }}</b><IconifyIcon icon="lucide:arrow-up-right" /></span>
          </RouterLink>
        </div>
        <Pagination v-if="(selectedCompany?.posts.length || 0) > detailPageSize" v-model:current="detailPage" class="company-pagination" :page-size="detailPageSize" :total="selectedCompany?.posts.length" :show-size-changer="false" size="small" />
      </div>
    </Modal>
  </section>
</template>

<style scoped>
.company-directory {
  --company-accent: #4194e8;
  --company-ink: #16304a;
  --company-muted: #62768b;
  --company-soft: #dfefff;
  --company-line: #c1d8f0;
  color: var(--company-ink);
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.company-directory-intro { position: relative; display: flex; align-items: flex-end; justify-content: space-between; gap: 32px; overflow: hidden; padding: 42px 34px 32px; border: 1px solid #d7e6f4; border-bottom: 0; border-radius: 22px 22px 0 0; background: linear-gradient(120deg, #f4faff 0%, #fbfdff 62%, #e1f0ff 100%); }
.company-profile-readonly { margin: 20px 0 22px; padding: 22px 24px; border: 1px solid var(--company-line); border-radius: 12px; background: #f5faff; }
.company-profile-title { margin-bottom: 18px; color: var(--company-ink); font-size: 18px; font-weight: 700; }
.company-profile-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 18px 28px; }
.company-profile-grid div { display: grid; gap: 5px; min-width: 0; }
.company-profile-grid small { color: var(--company-muted); font-size: 12px; }
.company-profile-grid strong { overflow-wrap: anywhere; color: var(--company-ink); font-size: 14px; font-weight: 500; }
.company-profile-intro { margin: 18px 0 0; color: var(--company-muted); line-height: 1.7; white-space: pre-wrap; }
.company-profile-empty { color: var(--company-muted); font-size: 13px; }
@media (max-width: 720px) { .company-profile-grid { grid-template-columns: 1fr; } }
.company-directory-intro::after { position: absolute; right: 7%; bottom: -90px; width: 260px; height: 260px; border: 1px solid #8fc0f1; border-radius: 50%; box-shadow: 0 0 0 22px #c2ddf855, 0 0 0 44px #c2ddf822; content: ''; }
.company-intro-copy { position: relative; z-index: 1; }
.company-intro-label { display: inline-flex; align-items: center; gap: 8px; color: #3880c8; font-size: 11px; font-weight: 700; letter-spacing: .16em; }
.company-intro-label i { width: 7px; height: 7px; border-radius: 50%; background: var(--company-accent); box-shadow: 0 0 0 4px #c6e0f9; }
.company-intro-copy h1 { margin: 13px 0 7px; color: #182c3f; font-size: clamp(26px, 3vw, 36px); font-weight: 700; letter-spacing: -.045em; line-height: 1.15; }
.company-intro-copy p { margin: 0; color: var(--company-muted); font-size: 14px; }
.company-directory-stats { position: relative; z-index: 1; display: flex; align-items: center; gap: 18px; padding: 13px 18px; border: 1px solid #cfe0f1; border-radius: 13px; background: #ffffffc9; box-shadow: 0 8px 25px rgb(172 95 28 / 7%); backdrop-filter: blur(8px); }
.company-directory-stats div { display: grid; gap: 3px; min-width: 54px; }
.company-directory-stats strong { color: #3880c8; font-family: Georgia, 'Times New Roman', serif; font-size: 23px; font-weight: 700; line-height: 1; }
.company-directory-stats span { color: #708293; font-size: 11px; white-space: nowrap; }
.company-directory-stats > i { width: 1px; height: 29px; background: #c2d6ea; }
.company-filters { padding: 24px 34px 23px; border: 1px solid #d7e6f4; border-bottom: 0; background: #fff; }
.company-search { display: flex; width: min(100%, 1040px); height: 58px; margin-bottom: 28px; overflow: hidden; border: 2px solid var(--company-accent); border-radius: 12px; }
.search-scope { display: flex; align-items: center; padding: 0 17px; border-right: 1px solid #c7dcf2; background: #f5faff; }
.search-scope select { max-width: 140px; border: 0; background: #fff; color: var(--company-accent); cursor: pointer; font: inherit; font-size: 16px; }
.company-search input { width: 0; min-width: 0; flex: 1; padding: 0 18px; border: 0; background: #fff; color: var(--company-ink); font: inherit; font-size: 17px; }
.company-search input::placeholder { color: #9aa6ad; }
.company-search input:focus { outline: none; }
.company-search:focus-within { outline: 3px solid #b8d3ee; outline-offset: 3px; }
.search-submit { width: 126px; flex-shrink: 0; border: 0; background: var(--company-accent); color: #fff; cursor: pointer; font-size: 21px; font-weight: 700; letter-spacing: 2px; }
.company-filter-row { display: flex; align-items: flex-start; gap: 18px; padding: 8px 0; font-size: 14px; line-height: 28px; }
.filter-label { width: 84px; flex-shrink: 0; color: #455a6e; font-size: 14px; font-weight: 600; }
.filter-options { display: flex; min-width: 0; flex: 1; flex-wrap: wrap; gap: 4px 7px; }
.filter-options button { padding: 0 10px; border: 0; border-radius: 999px; background: transparent; color: var(--company-ink); cursor: pointer; font: inherit; line-height: 28px; transition: color 160ms ease, background-color 160ms ease; }
.filter-options button.active { background: var(--company-soft); color: var(--company-accent); font-weight: 700; }
.filter-options button:hover { background: #eff7ff; color: var(--company-accent); }
.clear-filters { margin-left: auto; padding: 0; flex-shrink: 0; border: 0; background: none; color: var(--company-muted); cursor: pointer; font: inherit; line-height: 28px; }
.clear-filters:hover { color: var(--company-accent); }
.company-results { min-height: 400px; padding: 27px 24px 31px; border: 1px solid #d7e6f4; border-radius: 0 0 22px 22px; background: linear-gradient(180deg, #e8f3ff 0%, #f4faff 80%); }
.company-results-toolbar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 14px; margin-bottom: 24px; }
.company-sort { display: flex; align-items: center; gap: 7px; padding: 5px 7px; border: 1px solid #cfe0f1; border-radius: 10px; background: #ffffffc9; box-shadow: 0 5px 16px rgb(172 95 28 / 4%); }
.company-sort-label { display: inline-flex; align-items: center; gap: 9px; padding: 0 12px 0 3px; font-size: 15px; }
.company-sort-label :deep(svg) { padding: 5px; border-radius: 7px; background: var(--company-accent); color: #fff; width: 29px; height: 29px; }
.company-sort button { padding: 6px 12px; border: 0; border-radius: 7px; background: none; color: var(--company-muted); cursor: pointer; font: inherit; }
.company-sort button.active { background: var(--company-accent); color: #fff; }
.company-result-count { color: var(--company-muted); font-size: 13px; }
.company-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 20px; }
.company-card { position: relative; min-width: 0; overflow: hidden; border: 1px solid #d2e2f2; border-radius: 16px; background: #fff; box-shadow: 0 5px 15px rgb(172 95 28 / 3%); transition: transform 180ms ease, border-color 180ms ease, box-shadow 180ms ease; }
.company-card::before { position: absolute; top: 0; right: 20px; left: 20px; height: 3px; border-radius: 0 0 4px 4px; background: #c2dcf5; content: ''; transition: background-color 180ms ease; }
.company-card:hover { border-color: #7bb6f2; box-shadow: 0 6px 22px rgb(183 92 24 / 10%); }
.company-card:hover::before { background: linear-gradient(90deg, #4a9cef, #3c8ad8); }
.company-card-main { display: flex; width: 100%; min-height: 138px; align-items: center; gap: 15px; padding: 22px 20px; border: 0; background: #fff; color: inherit; cursor: pointer; text-align: left; }
.company-monogram { display: grid; width: 62px; height: 62px; flex-shrink: 0; place-items: center; overflow: hidden; border: 1px solid #d1e0ee; border-radius: 13px; background: var(--company-soft); color: #2b639a; font-family: 'Microsoft YaHei', sans-serif; font-size: 22px; font-weight: 700; letter-spacing: 1px; }
.company-monogram img { width: 100%; height: 100%; object-fit: contain; }
.company-monogram.muted { border-color: var(--company-line); background: #f3f7f9; color: #66828e; }
.company-summary { display: grid; min-width: 0; gap: 12px; }
.company-name { display: -webkit-box; overflow: hidden; -webkit-line-clamp: 2; -webkit-box-orient: vertical; font-family: 'Microsoft YaHei', sans-serif; font-size: 16px; font-weight: 500; line-height: 1.55; overflow-wrap: anywhere; }
.company-tags { display: flex; flex-wrap: wrap; gap: 5px; color: var(--company-muted); font-size: 11px; }
.company-tags > span { max-width: 100%; overflow: hidden; padding: 2px 6px; border-radius: 4px; background: #f5f7f8; text-overflow: ellipsis; white-space: nowrap; }
.company-tags .hiring-tag { color: #2b639a; background: var(--company-soft); }
.company-hot-post, .company-no-post { display: flex; min-height: 53px; align-items: center; gap: 10px; padding: 13px 20px; border-top: 1px solid #dceaf7; background: linear-gradient(90deg, #f4faff, #fbfdff); font-size: 12px; }
.hot-label { padding-right: 10px; flex-shrink: 0; border-right: 1px solid #dce4e8; color: var(--company-muted); }
.hot-name { min-width: 0; overflow: hidden; color: #3880c8; text-overflow: ellipsis; white-space: nowrap; }
.hot-salary { margin-left: auto; flex-shrink: 0; color: #62768b; font-variant-numeric: tabular-nums; }
.company-hot-post:hover .hot-name { text-decoration: underline; text-underline-offset: 3px; }
.company-no-post { justify-content: space-between; color: #94a0a7; }
.company-skeleton { padding: 24px; border-radius: 16px; background: #fff; }
.company-empty { display: grid; min-height: 270px; justify-items: center; align-content: center; gap: 12px; padding: 28px 16px; border-radius: 16px; background: #fff; text-align: center; }
.company-empty > :deep(svg) { width: 38px; height: 38px; margin-bottom: 4px; color: #849cb5; }
.company-empty h3, .company-empty p { margin: 0; }
.company-empty h3 { font-size: 17px; }
.company-empty p { color: #77868c; }
.company-empty button { margin-top: 8px; padding: 8px 18px; border: 1px solid #3374b6; border-radius: 7px; background: #fff; color: #2b639a; cursor: pointer; }
.company-pagination { display: flex; justify-content: center; flex-wrap: wrap; gap: 4px; margin-top: 25px; }
.company-pagination :deep(.ant-pagination-item),
.company-pagination :deep(.ant-pagination-item-link) { border-color: #cdd9e4; background: #fff; }
.company-pagination :deep(.ant-pagination-item a),
.company-pagination :deep(.ant-pagination-item-link) { color: #526273; }
.company-pagination :deep(.ant-pagination-item-active) { border-color: #3c8ad8; background: #3c8ad8; }
.company-pagination :deep(.ant-pagination-item-active a) { color: #fff; }
.company-pagination :deep(.ant-pagination-disabled .ant-pagination-item-link) { color: #a8b7bd; }
.company-detail { color: #1d2e3f; }
.company-detail-header { position: relative; display: flex; align-items: center; gap: 18px; overflow: hidden; padding: 34px 40px 29px; border-bottom: 1px solid #cce0f3; background: linear-gradient(118deg, #e5f2ff 0%, #f4faff 60%, #fbfdff 100%); }
.company-detail-header::after { position: absolute; right: -42px; bottom: -86px; width: 205px; height: 205px; border: 1px solid #8fbfef; border-radius: 50%; box-shadow: 0 0 0 17px #c6def755, 0 0 0 34px #c6def722; content: ''; }
.company-detail-monogram { position: relative; z-index: 1; display: grid; width: 76px; height: 76px; flex-shrink: 0; place-items: center; overflow: hidden; border: 1px solid #79b6f4; border-radius: 19px; background: #dfefff; color: #3880c8; font-size: 25px; font-weight: 700; letter-spacing: 1px; box-shadow: 0 9px 17px rgb(183 92 24 / 10%); }
.company-detail-monogram img { width: 100%; height: 100%; object-fit: contain; }
.company-detail-monogram.muted { border-color: #e0e4e8; background: #f3f5f7; color: #6c7985; box-shadow: none; }
.company-detail-heading { position: relative; z-index: 1; min-width: 0; }
.company-detail-eyebrow { display: block; margin-bottom: 8px; color: #3880c8; font-size: 10px; font-weight: 700; letter-spacing: .14em; }
.company-detail-heading h2 { max-width: 570px; margin: 0; color: #182c3f; font-size: 24px; font-weight: 700; line-height: 1.35; overflow-wrap: anywhere; }
.company-detail-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 8px 16px; margin-top: 11px; color: #6b7e90; font-size: 12px; }
.company-detail-meta > span { display: inline-flex; align-items: center; gap: 6px; }
.company-detail-meta > span:first-child { color: #3880c8; font-weight: 700; }
.company-detail-meta > span:first-child i { width: 6px; height: 6px; border-radius: 50%; background: #4194e8; box-shadow: 0 0 0 3px #c6e0f9; }
.company-detail-meta > span:first-child.muted { color: #747f8a; font-weight: 500; }
.company-detail-meta > span:first-child.muted i { background: #9ba4ad; box-shadow: 0 0 0 3px #e5eaee; }
.company-detail-mark { position: relative; z-index: 1; display: grid; gap: 1px; margin: 0 4px 0 auto; color: #438ed9; font-family: Georgia, 'Times New Roman', serif; font-size: 27px; font-weight: 700; line-height: .85; letter-spacing: .08em; opacity: .72; }
.company-detail-mark span { margin-left: 13px; color: #79aee4; font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif; font-size: 9px; letter-spacing: .28em; }
.company-detail-toolbar { display: flex; align-items: flex-end; justify-content: space-between; gap: 18px; padding: 25px 40px 15px; }
.company-detail-kicker { color: #3880c8; font-size: 11px; font-weight: 700; letter-spacing: .15em; }
.company-detail-toolbar p { margin: 7px 0 0; color: #4e5e6f; font-size: 13px; }
.company-detail-tip { display: inline-flex; align-items: center; gap: 6px; color: #7e96ae; font-size: 11px; white-space: nowrap; }
.company-detail-tip :deep(svg) { width: 14px; height: 14px; }
.company-detail-post-list { padding: 0 26px 8px; }
.company-detail-post { display: grid; grid-template-columns: 39px minmax(0, 1fr) auto; align-items: center; gap: 14px; min-height: 73px; padding: 13px 14px; border-top: 1px solid #dce6f1; border-radius: 10px; color: #1d2e3f; transition: background-color 160ms ease, transform 160ms ease; }
.company-detail-post:hover { background: #edf6ff; color: #1d2e3f; transform: translateX(3px); }
.company-detail-post-index { color: #8eb6dd; font-family: Georgia, 'Times New Roman', serif; font-size: 17px; font-weight: 700; }
.company-detail-post-copy { min-width: 0; }
.company-detail-post strong { display: block; color: #263545; font-size: 15px; font-weight: 600; line-height: 1.45; overflow-wrap: anywhere; }
.company-detail-post small { display: flex; align-items: center; min-width: 0; gap: 7px; margin-top: 6px; color: #77889a; font-size: 11px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.company-detail-post small :deep(svg) { width: 13px; height: 13px; flex-shrink: 0; color: #4790d9; }
.company-detail-post small i { width: 3px; height: 3px; flex-shrink: 0; border-radius: 50%; background: #a0bcd9; }
.company-detail-post-reward { display: inline-flex; align-items: center; gap: 11px; color: #3c8ad8; }
.company-detail-post-reward b { flex-shrink: 0; font-size: 14px; font-variant-numeric: tabular-nums; }
.company-detail-post-reward :deep(svg) { width: 15px; height: 15px; color: #6ba6e1; transition: transform 160ms ease; }
.company-detail-post:hover .company-detail-post-reward :deep(svg) { transform: translate(2px, -2px); }
.company-detail .company-empty { min-height: 230px; margin: 0 26px 22px; border: 1px dashed #b1cfed; background: #f5faff; }
.company-detail .company-empty > :deep(svg) { color: #5a9ee3; }
.company-directory button:focus-visible, .company-directory a:focus-visible, .company-detail a:focus-visible { outline: 2px solid #3c8ad8; outline-offset: -2px; }
@media (max-width: 1200px) {
  .company-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .clear-filters { align-self: flex-end; }
}
@media (max-width: 900px) {
  .company-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .company-filter-row { flex-wrap: wrap; gap: 8px; }
  .clear-filters { margin-left: 92px; }
  .company-filters { padding: 22px 20px; }
}
@media (max-width: 600px) {
  .company-grid { grid-template-columns: minmax(0, 1fr); gap: 14px; }
  .company-filters { padding: 18px 14px; }
  .company-search { height: 50px; margin-bottom: 18px; }
  .search-scope { padding: 0 7px; }
  .search-scope select { font-size: 12px; }
  .company-search input { padding: 0 8px; font-size: 13px; }
  .search-submit { width: 64px; font-size: 16px; }
  .company-filter-row { font-size: 12px; }
  .filter-label { width: 72px; font-size: 13px; }
  .filter-options { gap: 3px 14px; }
  .company-results { padding: 18px 12px 24px; }
  .company-sort { gap: 2px; }
  .company-sort-label { padding-right: 4px; font-size: 13px; }
  .company-sort button { padding: 6px 8px; font-size: 12px; }
  .company-card-main { min-height: 125px; }
  .company-name { font-size: 17px; }
}
@media (prefers-reduced-motion: reduce) {
  .company-card { transition: none; }
}
.company-directory { color: #364a5d; background: #fbfdff; }
.company-directory .company-filters, .company-directory .company-card, .company-directory .company-empty { border-color: #d7e6f4; background: #fff; }
.company-directory .search-submit, .company-directory .company-sort button.active { background: linear-gradient(135deg, #4a9cef, #3c8ad8); }
.company-directory .company-card:hover { border-color: #8fbfef; box-shadow: 0 8px 22px rgb(183 92 24 / 8%); }
.company-directory .company-monogram, .company-directory .company-tags .hiring-tag { background: #dfefff; color: #3880c8; }

:global(.company-detail-modal .ant-modal) { max-width: calc(100vw - 24px); padding-bottom: 0; }
:global(.company-detail-modal .ant-modal-content) { overflow: hidden; padding: 0; border: 1px solid #cfe0f1; border-radius: 24px; background: #fbfdff; box-shadow: 0 26px 80px rgb(76 42 18 / 22%); }
:global(.company-detail-modal .ant-modal-body) { padding: 0; }
:global(.company-detail-modal .ant-modal-close) { top: 16px; right: 16px; z-index: 3; width: 34px; height: 34px; border-radius: 50%; color: #66829e; background: #ffffffbf; transition: color 160ms ease, background-color 160ms ease, transform 160ms ease; }
:global(.company-detail-modal .ant-modal-close:hover) { color: #3880c8; background: #dfefff; transform: rotate(8deg); }

@media (max-width: 900px) {
  .company-directory-intro { align-items: flex-start; flex-direction: column; gap: 24px; }
  .company-directory-stats { align-self: stretch; justify-content: space-around; }
}
@media (max-width: 600px) {
  .company-directory-intro { padding: 30px 20px 24px; }
  .company-intro-copy h1 { font-size: 28px; }
  .company-directory-stats { gap: 10px; padding: 12px 10px; }
  .company-directory-stats div { min-width: 0; }
  .company-directory-stats strong { font-size: 20px; }
  .company-filters { padding: 18px 14px 17px; }
  .company-results { border-radius: 0 0 16px 16px; }
  .company-detail-header { align-items: flex-start; padding: 30px 24px 23px; }
  .company-detail-monogram { width: 61px; height: 61px; border-radius: 15px; font-size: 20px; }
  .company-detail-heading h2 { padding-right: 24px; font-size: 20px; }
  .company-detail-eyebrow { font-size: 9px; }
  .company-detail-mark { display: none; }
  .company-detail-toolbar { align-items: flex-start; flex-direction: column; gap: 9px; padding: 21px 24px 13px; }
  .company-detail-post-list { padding: 0 14px 8px; }
  .company-detail-post { grid-template-columns: 28px minmax(0, 1fr); gap: 10px; padding: 12px 8px; }
  .company-detail-post-reward { grid-column: 2; justify-content: space-between; margin-top: -5px; }
  .company-detail .company-empty { margin-inline: 14px; }
}
@media (prefers-reduced-motion: reduce) {
  .filter-options button, .company-card, .company-detail-post, .company-detail-post-reward :deep(svg), :global(.company-detail-modal .ant-modal-close) { transition: none; }
}
</style>
