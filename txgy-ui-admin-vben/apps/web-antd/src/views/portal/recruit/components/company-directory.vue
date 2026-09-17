<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { RouterLink } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

import { Modal, Pagination } from 'ant-design-vue';

import {
  getPortalRecruitCompanyList,
  getPortalRecruitPostPage,
  type HrmPortalRecruitApi,
} from '#/api/hrm/portal/recruit';

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
const detailOpen = ref(false);
const detailPage = ref(1);
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
  const parts = post.areaName?.trim().split(/\s+/) || [];
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
}

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
            <span class="company-detail-eyebrow">企业招聘档案</span>
            <h2>{{ selectedCompany?.name }}</h2>
            <div class="company-detail-meta">
              <span :class="{ muted: !selectedCompany?.posts.length }"><i></i>{{ selectedCompany?.posts.length ? '正在招聘' : '暂无公开职位' }}</span>
              <span>{{ selectedCompany?.posts.length || 0 }} 个公开职位</span>
              <span v-if="selectedCompanyCities.length">{{ selectedCompanyCities.slice(0, 2).join(' · ') }}</span>
            </div>
          </div>
        </header>

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
  --company-accent: #e87522;
  --company-ink: #4a2916;
  --company-muted: #8b7462;
  --company-soft: #fff0df;
  --company-line: #f0dac1;
  color: var(--company-ink);
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.company-directory-intro { position: relative; display: flex; align-items: flex-end; justify-content: space-between; gap: 32px; overflow: hidden; padding: 42px 34px 32px; border: 1px solid #f4e5d7; border-bottom: 0; border-radius: 22px 22px 0 0; background: linear-gradient(120deg, #fffaf4 0%, #fffdfb 62%, #fff3e1 100%); }
.company-directory-intro::after { position: absolute; right: 7%; bottom: -90px; width: 260px; height: 260px; border: 1px solid #f1c58f; border-radius: 50%; box-shadow: 0 0 0 22px #f8dfc255, 0 0 0 44px #f8dfc222; content: ''; }
.company-intro-copy { position: relative; z-index: 1; }
.company-intro-label { display: inline-flex; align-items: center; gap: 8px; color: #c85e18; font-size: 11px; font-weight: 700; letter-spacing: .16em; }
.company-intro-label i { width: 7px; height: 7px; border-radius: 50%; background: var(--company-accent); box-shadow: 0 0 0 4px #f9dfc6; }
.company-intro-copy h1 { margin: 13px 0 7px; color: #3f2718; font-size: clamp(26px, 3vw, 36px); font-weight: 700; letter-spacing: -.045em; line-height: 1.15; }
.company-intro-copy p { margin: 0; color: var(--company-muted); font-size: 14px; }
.company-directory-stats { position: relative; z-index: 1; display: flex; align-items: center; gap: 18px; padding: 13px 18px; border: 1px solid #f1dfcf; border-radius: 13px; background: #ffffffc9; box-shadow: 0 8px 25px rgb(172 95 28 / 7%); backdrop-filter: blur(8px); }
.company-directory-stats div { display: grid; gap: 3px; min-width: 54px; }
.company-directory-stats strong { color: #c85e18; font-family: Georgia, 'Times New Roman', serif; font-size: 23px; font-weight: 700; line-height: 1; }
.company-directory-stats span { color: #937f70; font-size: 11px; white-space: nowrap; }
.company-directory-stats > i { width: 1px; height: 29px; background: #ead6c2; }
.company-filters { padding: 24px 34px 23px; border: 1px solid #f4e5d7; border-bottom: 0; background: #fff; }
.company-search { display: flex; width: min(100%, 1040px); height: 58px; margin-bottom: 28px; overflow: hidden; border: 2px solid var(--company-accent); border-radius: 12px; }
.search-scope { display: flex; align-items: center; padding: 0 17px; border-right: 1px solid #f2dcc7; background: #fffaf5; }
.search-scope select { max-width: 140px; border: 0; background: #fff; color: var(--company-accent); cursor: pointer; font: inherit; font-size: 16px; }
.company-search input { width: 0; min-width: 0; flex: 1; padding: 0 18px; border: 0; background: #fff; color: var(--company-ink); font: inherit; font-size: 17px; }
.company-search input::placeholder { color: #9aa6ad; }
.company-search input:focus { outline: none; }
.company-search:focus-within { outline: 3px solid #b8eeea; outline-offset: 3px; }
.search-submit { width: 126px; flex-shrink: 0; border: 0; background: var(--company-accent); color: #fff; cursor: pointer; font-size: 21px; font-weight: 700; letter-spacing: 2px; }
.company-filter-row { display: flex; align-items: flex-start; gap: 18px; padding: 8px 0; font-size: 14px; line-height: 28px; }
.filter-label { width: 84px; flex-shrink: 0; color: #6e5545; font-size: 14px; font-weight: 600; }
.filter-options { display: flex; min-width: 0; flex: 1; flex-wrap: wrap; gap: 4px 7px; }
.filter-options button { padding: 0 10px; border: 0; border-radius: 999px; background: transparent; color: var(--company-ink); cursor: pointer; font: inherit; line-height: 28px; transition: color 160ms ease, background-color 160ms ease; }
.filter-options button.active { background: var(--company-soft); color: var(--company-accent); font-weight: 700; }
.filter-options button:hover { background: #fff7ef; color: var(--company-accent); }
.clear-filters { margin-left: auto; padding: 0; flex-shrink: 0; border: 0; background: none; color: var(--company-muted); cursor: pointer; font: inherit; line-height: 28px; }
.clear-filters:hover { color: var(--company-accent); }
.company-results { min-height: 400px; padding: 27px 24px 31px; border: 1px solid #f4e5d7; border-radius: 0 0 22px 22px; background: linear-gradient(180deg, #fff5e8 0%, #fffaf4 80%); }
.company-results-toolbar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 14px; margin-bottom: 24px; }
.company-sort { display: flex; align-items: center; gap: 7px; padding: 5px 7px; border: 1px solid #f1dfcf; border-radius: 10px; background: #ffffffc9; box-shadow: 0 5px 16px rgb(172 95 28 / 4%); }
.company-sort-label { display: inline-flex; align-items: center; gap: 9px; padding: 0 12px 0 3px; font-size: 15px; }
.company-sort-label :deep(svg) { padding: 5px; border-radius: 7px; background: var(--company-accent); color: #fff; width: 29px; height: 29px; }
.company-sort button { padding: 6px 12px; border: 0; border-radius: 7px; background: none; color: var(--company-muted); cursor: pointer; font: inherit; }
.company-sort button.active { background: var(--company-accent); color: #fff; }
.company-result-count { color: var(--company-muted); font-size: 13px; }
.company-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 20px; }
.company-card { position: relative; min-width: 0; overflow: hidden; border: 1px solid #f2e1d2; border-radius: 16px; background: #fff; box-shadow: 0 5px 15px rgb(172 95 28 / 3%); transition: transform 180ms ease, border-color 180ms ease, box-shadow 180ms ease; }
.company-card::before { position: absolute; top: 0; right: 20px; left: 20px; height: 3px; border-radius: 0 0 4px 4px; background: #f5dcc2; content: ''; transition: background-color 180ms ease; }
.company-card:hover { border-color: #f2bd7b; box-shadow: 0 6px 22px rgb(183 92 24 / 10%); }
.company-card:hover::before { background: linear-gradient(90deg, #ef9a4a, #d86616); }
.company-card-main { display: flex; width: 100%; min-height: 138px; align-items: center; gap: 15px; padding: 22px 20px; border: 0; background: #fff; color: inherit; cursor: pointer; text-align: left; }
.company-monogram { display: grid; width: 62px; height: 62px; flex-shrink: 0; place-items: center; overflow: hidden; border: 1px solid #d1eeeb; border-radius: 13px; background: var(--company-soft); color: #079a93; font-family: 'Microsoft YaHei', sans-serif; font-size: 22px; font-weight: 700; letter-spacing: 1px; }
.company-monogram img { width: 100%; height: 100%; object-fit: contain; }
.company-monogram.muted { border-color: var(--company-line); background: #f3f7f9; color: #66828e; }
.company-summary { display: grid; min-width: 0; gap: 12px; }
.company-name { display: -webkit-box; overflow: hidden; -webkit-line-clamp: 2; -webkit-box-orient: vertical; font-family: 'Microsoft YaHei', sans-serif; font-size: 16px; font-weight: 500; line-height: 1.55; overflow-wrap: anywhere; }
.company-tags { display: flex; flex-wrap: wrap; gap: 5px; color: var(--company-muted); font-size: 11px; }
.company-tags > span { max-width: 100%; overflow: hidden; padding: 2px 6px; border-radius: 4px; background: #f5f7f8; text-overflow: ellipsis; white-space: nowrap; }
.company-tags .hiring-tag { color: #079a93; background: var(--company-soft); }
.company-hot-post, .company-no-post { display: flex; min-height: 53px; align-items: center; gap: 10px; padding: 13px 20px; border-top: 1px solid #f7e8dc; background: linear-gradient(90deg, #fffaf4, #fffdfb); font-size: 12px; }
.hot-label { padding-right: 10px; flex-shrink: 0; border-right: 1px solid #dce4e8; color: var(--company-muted); }
.hot-name { min-width: 0; overflow: hidden; color: #c85e18; text-overflow: ellipsis; white-space: nowrap; }
.hot-salary { margin-left: auto; flex-shrink: 0; color: #8b7462; font-variant-numeric: tabular-nums; }
.company-hot-post:hover .hot-name { text-decoration: underline; text-underline-offset: 3px; }
.company-no-post { justify-content: space-between; color: #94a0a7; }
.company-skeleton { padding: 24px; border-radius: 16px; background: #fff; }
.company-empty { display: grid; min-height: 270px; justify-items: center; align-content: center; gap: 12px; padding: 28px 16px; border-radius: 16px; background: #fff; text-align: center; }
.company-empty > :deep(svg) { width: 38px; height: 38px; margin-bottom: 4px; color: #84b3b5; }
.company-empty h3, .company-empty p { margin: 0; }
.company-empty h3 { font-size: 17px; }
.company-empty p { color: #77868c; }
.company-empty button { margin-top: 8px; padding: 8px 18px; border: 1px solid #08b6ad; border-radius: 7px; background: #fff; color: #079a93; cursor: pointer; }
.company-pagination { display: flex; justify-content: center; flex-wrap: wrap; gap: 4px; margin-top: 25px; }
.company-pagination :deep(.ant-pagination-item),
.company-pagination :deep(.ant-pagination-item-link) { border-color: #cde3e4; background: #fff; }
.company-pagination :deep(.ant-pagination-item a),
.company-pagination :deep(.ant-pagination-item-link) { color: #526b73; }
.company-pagination :deep(.ant-pagination-item-active) { border-color: #d86616; background: #d86616; }
.company-pagination :deep(.ant-pagination-item-active a) { color: #fff; }
.company-pagination :deep(.ant-pagination-disabled .ant-pagination-item-link) { color: #a8b7bd; }
.company-detail { color: #123f49; font-size: 16px; }
.company-detail-header { position: relative; display: flex; align-items: center; gap: 18px; overflow: hidden; padding: 34px 40px 29px; border-bottom: 1px solid #f3dfcc; background: linear-gradient(118deg, #fff4e5 0%, #fffaf4 60%, #fffdfb 100%); }
.company-detail-header::after { position: absolute; right: -42px; bottom: -86px; width: 205px; height: 205px; border: 1px solid #efc38f; border-radius: 50%; box-shadow: 0 0 0 17px #f7dfc655, 0 0 0 34px #f7dfc622; content: ''; }
.company-detail-monogram { position: relative; z-index: 1; display: grid; width: 76px; height: 76px; flex-shrink: 0; place-items: center; overflow: hidden; border: 1px solid #f4bc79; border-radius: 19px; background: #fff0df; color: #123f49; font-size: 27px; font-weight: 700; letter-spacing: 1px; box-shadow: 0 9px 17px rgb(183 92 24 / 10%); }
.company-detail-monogram img { width: 100%; height: 100%; object-fit: contain; }
.company-detail-monogram.muted { border-color: #e0e7e8; background: #f3f7f7; color: #6c8185; box-shadow: none; }
.company-detail-heading { position: relative; z-index: 1; min-width: 0; }
.company-detail-eyebrow { display: block; margin-bottom: 8px; color: #123f49; font-size: 12px; font-weight: 700; letter-spacing: .14em; }
.company-detail-heading h2 { max-width: 570px; margin: 0; color: #123f49; font-size: 26px; font-weight: 700; line-height: 1.35; overflow-wrap: anywhere; }
.company-detail-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 8px 16px; margin-top: 11px; color: #425d64; font-size: 14px; }
.company-detail-meta > span { display: inline-flex; align-items: center; gap: 6px; }
.company-detail-meta > span:first-child { color: #123f49; font-weight: 700; }
.company-detail-meta > span:first-child i { width: 6px; height: 6px; border-radius: 50%; background: #e87522; box-shadow: 0 0 0 3px #f9dfc6; }
.company-detail-meta > span:first-child.muted { color: #74878a; font-weight: 500; }
.company-detail-meta > span:first-child.muted i { background: #9babad; box-shadow: 0 0 0 3px #e5eeee; }
.company-detail-toolbar { display: flex; align-items: flex-end; justify-content: space-between; gap: 18px; padding: 25px 40px 15px; }
.company-detail-kicker { color: #123f49; font-size: 13px; font-weight: 700; letter-spacing: .15em; }
.company-detail-toolbar p { margin: 7px 0 0; color: #425d64; font-size: 15px; }
.company-detail-tip { display: inline-flex; align-items: center; gap: 6px; color: #425d64; font-size: 13px; white-space: nowrap; }
.company-detail-tip :deep(svg) { width: 14px; height: 14px; }
.company-detail-post-list { padding: 0 26px 8px; }
.company-detail-post { display: grid; grid-template-columns: 39px minmax(0, 1fr) auto; align-items: center; gap: 14px; min-height: 73px; padding: 13px 14px; border-top: 1px solid #f1e5dc; border-radius: 10px; color: #123f49; transition: background-color 160ms ease, transform 160ms ease; }
.company-detail-post:hover { background: #eaf6ff; color: #123f49; transform: translateX(3px); }
.company-detail-post-index { color: #526d73; font-family: Georgia, 'Times New Roman', serif; font-size: 19px; font-weight: 700; }
.company-detail-post-copy { min-width: 0; }
.company-detail-post strong { display: block; color: #123f49; font-size: 17px; font-weight: 600; line-height: 1.45; overflow-wrap: anywhere; }
.company-detail-post small { display: flex; align-items: center; min-width: 0; gap: 7px; margin-top: 6px; color: #425d64; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.company-detail-post small :deep(svg) { width: 13px; height: 13px; flex-shrink: 0; color: #0f8f8c; }
.company-detail-post small i { width: 3px; height: 3px; flex-shrink: 0; border-radius: 50%; background: #d9bba0; }
.company-detail-post-reward { display: inline-flex; align-items: center; gap: 11px; color: #123f49; }
.company-detail-post-reward b { flex-shrink: 0; color: #123f49; font-size: 16px; font-variant-numeric: tabular-nums; }
.company-detail-post-reward :deep(svg) { width: 15px; height: 15px; color: #0f8f8c; transition: transform 160ms ease; }
.company-detail-post:hover .company-detail-post-reward :deep(svg) { transform: translate(2px, -2px); }
.company-detail .company-empty { min-height: 230px; margin: 0 26px 22px; border: 1px dashed #edcfb1; background: #fffaf5; }
.company-detail .company-empty > :deep(svg) { color: #0f8f8c; }
.company-detail .company-empty h3 { color: #123f49; font-size: 19px; }
.company-detail .company-empty p { color: #425d64; font-size: 16px; }
.company-detail .company-pagination { font-size: 16px; }
.company-detail .company-pagination :deep(.ant-pagination-item a),
.company-detail .company-pagination :deep(.ant-pagination-item-link) { font-size: 16px; }
.company-directory button:focus-visible, .company-directory a:focus-visible, .company-detail a:focus-visible { outline: 2px solid #2f80ed; outline-offset: -2px; }
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
.company-directory { color: #5d4636; background: #fffdfb; }
.company-directory .company-filters, .company-directory .company-card, .company-directory .company-empty { border-color: #f4e5d7; background: #fff; }
.company-directory .search-submit, .company-directory .company-sort button.active { background: linear-gradient(135deg, #ef9a4a, #d86616); }
.company-directory .company-card:hover { border-color: #efc38f; box-shadow: 0 8px 22px rgb(183 92 24 / 8%); }
.company-directory .company-monogram, .company-directory .company-tags .hiring-tag { background: #fff0df; color: #c85e18; }

:global(.company-detail-modal .ant-modal) { max-width: calc(100vw - 24px); padding-bottom: 0; }
:global(.company-detail-modal .ant-modal-content) { overflow: hidden; padding: 0; border: 1px solid #f1dfcf; border-radius: 24px; background: #fffdfb; box-shadow: 0 26px 80px rgb(76 42 18 / 22%); }
:global(.company-detail-modal .ant-modal-body) { padding: 0; }
:global(.company-detail-modal .ant-modal-close) { top: 16px; right: 16px; z-index: 3; width: 34px; height: 34px; border-radius: 50%; color: #425d64; background: #ffffffbf; transition: color 160ms ease, background-color 160ms ease, transform 160ms ease; }
:global(.company-detail-modal .ant-modal-close:hover) { color: #123f49; background: #e8f7f8; transform: rotate(8deg); }

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
  .company-detail-monogram { width: 61px; height: 61px; border-radius: 15px; font-size: 22px; }
  .company-detail-heading h2 { padding-right: 24px; font-size: 22px; }
  .company-detail-eyebrow { font-size: 11px; }
  .company-detail-toolbar { align-items: flex-start; flex-direction: column; gap: 9px; padding: 21px 24px 13px; }
  .company-detail-post-list { padding: 0 14px 8px; }
  .company-detail-post { grid-template-columns: 28px minmax(0, 1fr); gap: 10px; padding: 12px 8px; }
  .company-detail-post-reward { grid-column: 2; justify-content: space-between; margin-top: -5px; }
  .company-detail .company-empty { margin-inline: 14px; }
}
@media (prefers-reduced-motion: reduce) {
  .filter-options button, .company-card, .company-detail-post, .company-detail-post-reward :deep(svg), :global(.company-detail-modal .ant-modal-close) { transition: none; }
}

.company-directory {
  --company-accent: #0f8f8c;
  --company-ink: #123f49;
  --company-muted: #5f7f87;
  --company-soft: #e8f7f8;
  --company-line: #d7e9ed;
  color: var(--company-ink);
  background: #fff;
}

.company-directory-intro {
  border-color: var(--company-line);
  background: linear-gradient(120deg, #f5fbfc 0%, #fff 62%, #eaf3ff 100%);
}
.company-directory-intro::after { border-color: #9bdce1; box-shadow: 0 0 0 22px rgb(155 220 225 / 34%), 0 0 0 44px rgb(155 220 225 / 14%); }
.company-intro-label,
.company-directory-stats strong,
.company-detail-eyebrow,
.company-detail-kicker,
.company-detail-meta > span:first-child { color: #123f49; }
.company-intro-label i { background: #0f8f8c; box-shadow: 0 0 0 4px #d5f1f3; }
.company-intro-copy h1,
.company-detail-heading h2,
.company-detail-post strong { color: #123f49; }
.company-directory-stats { border-color: var(--company-line); box-shadow: 0 8px 25px rgb(18 63 73 / 7%); }
.company-directory-stats > i { background: var(--company-line); }
.company-filters,
.company-results { border-color: var(--company-line); }
.company-search { border-color: #0f8f8c; }
.search-scope { border-right-color: var(--company-line); background: #f5fbfc; }
.search-scope select { color: #0f8f8c; }
.search-submit,
.company-sort button.active { background: linear-gradient(135deg, #0f9f9b, #2f80ed); }
.filter-label { color: #4f7079; }
.filter-options button.active { color: #0f8f8c; background: #e8f7f8; }
.filter-options button:hover { color: #0f8f8c; background: #eaf6ff; }
.company-results { background: linear-gradient(180deg, #f3fbfc 0%, #fff 80%); }
.company-sort { border-color: var(--company-line); }
.company-card { border-color: var(--company-line); box-shadow: 0 5px 15px rgb(18 63 73 / 4%); }
.company-card::before { background: #b9e7ee; }
.company-card:hover { border-color: #7ccbd2; box-shadow: 0 8px 22px rgb(15 143 140 / 12%); }
.company-card:hover::before { background: linear-gradient(90deg, #0f8f8c, #2f80ed); }
.company-monogram,
.company-tags .hiring-tag { border-color: #b9e7ee; color: #0f8f8c; background: #e8f7f8; }
.company-hot-post,
.company-no-post { border-top-color: var(--company-line); background: linear-gradient(90deg, #f5fbfc, #fff); }
.hot-name { color: #0f8f8c; }
.company-pagination :deep(.ant-pagination-item-active) { border-color: #0f8f8c; background: #0f8f8c; }
.company-detail-header { border-bottom-color: var(--company-line); background: linear-gradient(118deg, #eaf6ff 0%, #f5fbfc 60%, #fff 100%); }
.company-detail-header::after { border-color: #9bdce1; box-shadow: 0 0 0 17px rgb(155 220 225 / 34%), 0 0 0 34px rgb(155 220 225 / 14%); }
.company-detail-monogram { border-color: #b9e7ee; color: #123f49; background: #e8f7f8; box-shadow: 0 9px 17px rgb(15 143 140 / 10%); }
.company-detail-meta > span:first-child i { background: #0f8f8c; box-shadow: 0 0 0 3px #d5f1f3; }
.company-detail-post { border-top-color: var(--company-line); color: #123f49; }
.company-detail-post:hover { background: #eaf6ff; color: #123f49; }
.company-detail-post-reward { color: #123f49; }
.company-directory button:focus-visible,
.company-directory a:focus-visible,
.company-detail a:focus-visible { outline-color: #2f80ed; }
:global(.company-detail-modal .ant-modal-content) { border-color: var(--company-line); background: #fff; box-shadow: 0 26px 80px rgb(18 63 73 / 18%); }
:global(.company-detail-modal .ant-modal-close:hover) { color: #0f8f8c; background: #e8f7f8; }
.company-directory .search-submit,
.company-directory .company-sort button.active { background: linear-gradient(135deg, #0f9f9b, #2f80ed); }
.company-directory .company-monogram,
.company-directory .company-tags .hiring-tag { border-color: #b9e7ee; color: #0f8f8c; background: #e8f7f8; }
.company-directory .company-hot-post,
.company-directory .company-no-post { color: #5f7f87; }
.company-directory .hot-salary,
.company-directory .company-detail-post-reward b { color: #123f49; }
</style>
