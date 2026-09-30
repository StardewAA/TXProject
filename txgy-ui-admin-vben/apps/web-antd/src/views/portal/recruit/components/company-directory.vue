<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { RouterLink, useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

import { Modal, Pagination, Select, Skeleton } from 'ant-design-vue';

import {
  getPortalRecruitCompanyList,
  getPortalRecruitPostPage,
  type HrmPortalRecruitApi,
} from '#/api/hrm/portal/recruit';
import { getAreaTree, type SystemAreaApi } from '#/api/system/area';

interface CompanyEntry extends HrmPortalRecruitApi.Company {
  posts: HrmPortalRecruitApi.Post[];
  logo?: string;
}

const companies = ref<CompanyEntry[]>([]);
const router = useRouter();
const loading = ref(true);
const loadFailed = ref(false);
const keyword = ref('');
const searchTerm = ref('');
const searchScope = ref('all');
const scopeOptions = [
  { label: '职位 / 公司', value: 'all' },
  { label: '公司名称', value: 'company' },
  { label: '职位名称', value: 'post' },
];
const city = ref('');
const postType = ref('');
const financing = ref('');
const scale = ref('');
const sort = ref('hiring');
const page = ref(1);
/** 每页条数。3 列 × 6 行。
    分页器在 filteredCompanies.length > pageSize 时才渲染，
    所以当前 20 家会分成 18 + 2 两页。 */
const pageSize = 18;
const selectedCompany = ref<CompanyEntry>();
const detailOpen = ref(false);
const detailPage = ref(1);
const detailPageSize = 6;

/** 「正在招聘」的判定：该企业至少有一个公开职位。合作企业数直接用 companies.length，不必再算。 */
const hiringCompanyCount = computed(() => companies.value.filter((company) => company.posts.length > 0).length);
const selectedCompanyCities = computed(() => [...new Set(
  selectedCompany.value?.posts.map(getCity).filter(Boolean) || [],
)]);

/** 在招职位总数。companies.value 上只有 posts（totalPosts 是筛选后才算的），直接数数组长度。 */
const totalPostCount = computed(() => companies.value.reduce(
  (sum, company) => sum + company.posts.length,
  0,
));

/** 在招职位铺开的城市数。去重后计数 —— 「分布在 N 座城市」比「N 个岗位」更能说明摊子有多大。 */
const hiringCityCount = computed(() => new Set(
  companies.value.flatMap((company) => company.posts.map(getCity).filter(Boolean)),
).size);

/**
 * 页头那三张统计卡。
 *
 * <p>形制取自参考站（deephire「AI简历」）页头并排的三张白卡：彩色图标块 + 粗标题 + 灰描述。
 * 但内容是换过的 —— 那三张在原站是「AI 诊断 / 优化 / 生成」三个功能入口，本页没有对应功能，
 * 照搬等于硬造三个入口。这里填的是这一页真实拥有的三个数，位置和内部结构一字未改。</p>
 *
 * <p>三个数全部由上面已取回的数据派生，不额外发请求。</p>
 */
const companyStats = computed(() => [
  {
    icon: 'lucide:building-2',
    label: '合作企业',
    note: '已入驻招聘门户',
    tone: 'blue',
    unit: '家',
    value: companies.value.length,
  },
  {
    icon: 'lucide:briefcase-business',
    label: '正在招聘',
    note: '当前有公开职位',
    tone: 'green',
    unit: '家',
    value: hiringCompanyCount.value,
  },
  {
    icon: 'lucide:trending-up',
    label: '在招职位',
    note: `分布在 ${hiringCityCount.value} 座城市`,
    tone: 'amber',
    unit: '个',
    value: totalPostCount.value,
  },
]);

/** 融资阶段是固定档位，与后台企业表单的下拉选项同源（views/hrm/recruit/company/index.vue）。
    写死而不从数据派生：当前没有企业填过这个字段，派生会得到一个只有「不限」的空筛选器。 */
const financingStages = ['不融资', '股权融资', '上市融资'] as const;

/** 企业规模在后台是自由文本输入（placeholder「如：50-99 人」），写法不统一，
    实测同一档出现过 "100-500人" 与 "100-500 人" 两种。折叠掉空白后再去重，
    否则筛选器里会并排出现两个其实相等的选项。 */
function normalizeScale(value?: string) {
  return value?.replace(/\s+/g, '') || '';
}

function getCity(post: HrmPortalRecruitApi.Post) {
  const parts = post.areaName?.trim().split(/\s+/) || [];
  return parts[1] || parts[0] || '';
}

/** areaName 形如「陕西省 西安市 雁塔区」「北京市 北京市 石景山区」，首段就是省/直辖市。 */
function getProvince(post: HrmPortalRecruitApi.Post) {
  return post.areaName?.trim().split(/\s+/)[0] || '';
}

/**
 * 职位类型按二级分类展示，与职位页筛选行同一套词表。
 *
 * 岗位挂的是三级工种（防腐工、瓦工…），二级名由后端沿 parentId 折上去后下发
 * （见 HrmPortalRecruitPostRespVO.postTypeParentName）—— 门户分类目录只暴露到二级，
 * 三级到二级的父子关系前端拿不到，所以这层折算必须放在后端。
 *
 * 解析不出二级的一律归「未分类」，**不回退到 postTypeName**：
 * 那 14 个岗位挂在根类型上（旧停用根 3000-3004 等），回退会把「管理类」「技术类」
 * 这类一级根名混进筛选行，和职位页的二级词表就对不上了。
 */
/** 卡片上展示的招聘城市，取自岗位所在地，最多两个。
    公司档案自身的 areaName 20 家全为空，城市只能从岗位派生。 */
function companyCities(company: CompanyEntry) {
  return [...new Set(company.posts.map(getCity).filter(Boolean))].slice(0, 2);
}

function getPostType(post: HrmPortalRecruitApi.Post) {
  return post.postTypeParentName?.trim() || '未分类';
}

/**
 * 筛选选项的排序与截断。
 *
 * 排序按「命中多少家公司」降序 —— 35 个城市里大多数只挂着 1 家公司，
 * 按字典序会把它们平铺开，热门城市反而被冲散；招聘网站也都是热门优先。
 * 同分的再按名称排，保证顺序稳定。
 */
function companyHitCounts(collect: (post: HrmPortalRecruitApi.Post) => string) {
  const counts = new Map<string, number>();
  for (const company of companies.value) {
    for (const hit of new Set(company.posts.map(collect).filter(Boolean))) {
      counts.set(hit, (counts.get(hit) ?? 0) + 1);
    }
  }
  return counts;
}

function rankByCompanyHit(collect: (post: HrmPortalRecruitApi.Post) => string) {
  const isFallback = (name: string) => (name === '未分类' ? 1 : 0);
  return [...companyHitCounts(collect).entries()]
    .sort((left, right) => (
      // 「未分类」是兜底桶不是分类，命中再多也排最后
      isFallback(left[0]) - isFallback(right[0])
      || right[1] - left[1]
      || left[0].localeCompare(right[0], 'zh-CN')
    ))
    .map(([name]) => name);
}

/** 折叠时每行保留的选项数，超出的收进「更多」。 */
const COLLAPSED_OPTIONS = 8;
const expandedRows = ref<Record<string, boolean>>({});

function toggleFilterRow(key: string) {
  expandedRows.value[key] = !expandedRows.value[key];
}

/** 折叠时只给前 N 个；已选中的那个无条件带上，否则选了长尾项后它会从行里消失。 */
function visibleOptions(key: string, options: string[], active: string) {
  if (expandedRows.value[key]) return options;
  const head = options.slice(0, COLLAPSED_OPTIONS);
  return active && !head.includes(active) ? [...head, active] : head;
}

const cities = computed(() => rankByCompanyHit(getCity));
const postTypes = computed(() => rankByCompanyHit(getPostType));

/* ── 搜索 + 筛选区吸顶 ────────────────────────────────────────
   与职位页的吸顶工作台同一套做法：滚动时整块钉在导航条之下。
   判定方式靠 sticky 自身的特性 —— 吸住之后 getBoundingClientRect().top
   会被钳在 top 值上，据此判断不会自激震荡（比记滚动位置稳）。 */
const stickyRef = ref<HTMLElement>();
const filtersPinned = ref(false);
/** 钉住后是否手动展开了筛选行。拿下吸顶就复位，免得下次进来还是展开态。 */
const filtersExpanded = ref(false);
watch(filtersPinned, (pinned) => {
  if (!pinned) filtersExpanded.value = false;
});

/** 已选筛选，给吸顶后的摘要行用：一眼看清在筛什么，每项都能单独点掉。 */
const activeFilterChips = computed(() => {
  const chips: { clear: () => void; key: string; label: string }[] = [];
  if (city.value) chips.push({ key: 'city', label: city.value, clear: () => { city.value = ''; } });
  if (postType.value) chips.push({ key: 'postType', label: postType.value, clear: () => { postType.value = ''; } });
  if (financing.value) chips.push({ key: 'financing', label: financing.value, clear: () => { financing.value = ''; } });
  if (scale.value) chips.push({ key: 'scale', label: scale.value, clear: () => { scale.value = ''; } });
  return chips;
});
const COMPANY_TOPBAR_FALLBACK = 60;
let companyTopbarHeight = COMPANY_TOPBAR_FALLBACK;

function syncCompanyFiltersPin() {
  const el = stickyRef.value;
  if (!el) return;
  // 已经钉住时留一点回弹余量，避免在临界点上反复横跳
  const slack = filtersPinned.value ? 12 : 0;
  filtersPinned.value = el.getBoundingClientRect().top <= companyTopbarHeight + slack;
}

function syncCompanyTopbarHeight() {
  const root = stickyRef.value?.closest('.recruit-page');
  const bar = root?.querySelector<HTMLElement>(':scope > .recruit-topbar');
  const height = bar?.offsetHeight || COMPANY_TOPBAR_FALLBACK;
  if (height === companyTopbarHeight) return;
  companyTopbarHeight = height;
  root?.style.setProperty('--company-topbar-h', `${height}px`);
}

function handleCompanyFiltersScroll() {
  syncCompanyFiltersPin();
}

function handleCompanyFiltersResize() {
  syncCompanyTopbarHeight();
  syncCompanyFiltersPin();
}

/* ── 全国城市菜单 ──────────────────────────────────────────────
   行内只铺热门城市（有职位的那几个），长尾以及「有职位城市之外」的选择
   走这个菜单。数据来自系统地区树，与职位页的地址筛选同一个接口。 */
interface CityGroup { cities: { name: string }[]; province: string }

const cityGroups = ref<CityGroup[]>([]);
const cityPanelOpen = ref(false);
const cityKeyword = ref('');

/**
 * 三级地区树折算成「省 → 市」两级。
 * 直辖市（北京/上海/天津/重庆）在树里省下面直接挂区，没有市这一级，
 * 判定方式是看省的第一个孩子有没有孩子：有 → 下面才是市；没有 → 省本身就是市。
 */
function buildCityGroups(tree: SystemAreaApi.Area[]): CityGroup[] {
  const groups: CityGroup[] = [];
  for (const province of tree) {
    const kids = province.children ?? [];
    const hasCityLevel = kids.length > 0 && (kids[0]?.children?.length ?? 0) > 0;
    const cities = (hasCityLevel ? kids : [province]).map((item) => ({ name: item.name }));
    if (cities.length > 0) {
      groups.push({ province: province.name, cities });
    }
  }
  return groups;
}

async function loadCityGroups() {
  try {
    cityGroups.value = buildCityGroups((await getAreaTree()) ?? []);
  } catch {
    // 取不到就退化成只有热门城市，菜单本身仍可用
    cityGroups.value = [];
  }
}

/** 搜索时摊平成一张表，带上省名 —— 「新城区」这类重名光看市名分不清属于哪。 */
const citySearchResults = computed(() => {
  const keyword = cityKeyword.value.trim();
  if (!keyword) return [];
  const out: { name: string; province: string }[] = [];
  for (const group of cityGroups.value) {
    for (const item of group.cities) {
      if (item.name.includes(keyword)) out.push({ name: item.name, province: group.province });
      if (out.length >= 80) return out;
    }
  }
  return out;
});

/** 按岗位数统计（不是公司数）—— 菜单要回答的是「哪儿活儿多」。 */
function buildPostCounts(collect: (post: HrmPortalRecruitApi.Post) => string) {
  const counts = new Map<string, number>();
  for (const company of companies.value) {
    for (const post of company.posts) {
      const key = collect(post);
      if (key) counts.set(key, (counts.get(key) ?? 0) + 1);
    }
  }
  return counts;
}

const provincePostCounts = computed(() => buildPostCounts(getProvince));
const cityPostCounts = computed(() => buildPostCounts(getCity));

/**
 * 菜单里的省按岗位数降序 —— 地区树本身是行政区划顺序（香港、澳门、北京、天津、河北…），
 * 照它铺会让岗位最多的陕西沉到中段。省内的市同理排一遍，热门的排前面。
 * 没有岗位的省份 count 都是 0，sort 稳定，它们保持树里的原顺序垫底。
 */
const sortedCityGroups = computed(() => {
  return [...cityGroups.value]
    .map((group) => ({
      ...group,
      cities: [...group.cities].sort(
        (left, right) =>
          (cityPostCounts.value.get(right.name) ?? 0) - (cityPostCounts.value.get(left.name) ?? 0),
      ),
    }))
    .sort(
      (left, right) =>
        (provincePostCounts.value.get(right.province) ?? 0) - (provincePostCounts.value.get(left.province) ?? 0),
    );
});

/** 行内展示的城市：热门若干 + 当前选中项（选中的可能压根没有职位，必须带出来）。 */
const cityChips = computed(() => {
  const head = cities.value.slice(0, COLLAPSED_OPTIONS);
  if (city.value && !head.includes(city.value)) return [...head, city.value];
  return head;
});

function pickCity(name: string) {
  city.value = name;
  closeCityMenu();
}

function closeCityMenu() {
  cityPanelOpen.value = false;
  cityKeyword.value = '';
}

/** 点面板外侧或按 Esc 关掉 —— 否则选完之后面板会一直挂在那儿挡住筛选行。 */
function handleCityMenuDismiss(event: Event) {
  if (!cityPanelOpen.value) return;
  if (event instanceof KeyboardEvent) {
    if (event.key === 'Escape') closeCityMenu();
    return;
  }
  const target = event.target as HTMLElement | null;
  // 触发器自己也在 .city-menu 里，它由 @click 负责开合
  if (!target?.closest?.('.city-menu')) closeCityMenu();
}

/** 规模选项按数值升序（"50-99人" 排在 "500人以上" 前面），纯字典序会排反。 */
const scales = computed(() => [...new Set(
  companies.value.map((company) => normalizeScale(company.scale)).filter(Boolean),
)].sort((left, right) => (
  (Number.parseInt(left, 10) || 0) - (Number.parseInt(right, 10) || 0)
  || left.localeCompare(right, 'zh-CN')
)));

const filteredCompanies = computed(() => {
  const term = searchTerm.value.toLocaleLowerCase();
  const result = companies.value.flatMap((company) => {
    const count = company.posts.length;
    if (financing.value && company.financingStage !== financing.value) return [];
    if (scale.value && normalizeScale(company.scale) !== scale.value) return [];
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
  searchTerm.value || city.value || postType.value || financing.value || scale.value,
));

watch([searchTerm, searchScope, city, postType, financing, scale, sort], () => {
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
  financing.value = '';
  scale.value = '';
  page.value = 1;
}

function openCompany(company: CompanyEntry) {
  openCompanyProfile(company);
}

/**
 * 企业主页新开一个标签页。这样企业页不用自带返回入口 —— 关掉标签页即可，
 * 企业目录还停在原位（筛选条件、滚动位置都不丢）。
 * 必须在这里同步调 window.open，一旦先 await 就会被当成非用户手势拦掉。
 */
function openCompanyProfile(company?: CompanyEntry) {
  const selected = company || selectedCompany.value;
  if (!selected) return;
  const { href } = router.resolve({
    name: 'PortalRecruitCompany',
    query: { tenantId: String(selected.id) },
  });
  window.open(href, '_blank', 'noopener,noreferrer');
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
    const [companyResult, firstPage] = await Promise.all([
      getPortalRecruitCompanyList(),
      getPortalRecruitPostPage({ pageNo: 1, pageSize: 100 }),
    ]);
    const pageCount = Math.ceil(firstPage.total / 100);
    const remainingPages = await Promise.all(
      Array.from({ length: Math.max(0, pageCount - 1) }, (_, index) =>
        getPortalRecruitPostPage({ pageNo: index + 2, pageSize: 100 }),
      ),
    );
    const allPosts = [
      ...firstPage.list,
      ...remainingPages.flatMap((result) => result.list),
    ];
    const postsByCompany = new Map<number, Map<number, HrmPortalRecruitApi.Post>>();
    for (const post of allPosts) {
      if (post.tenantId == null) continue;
      const companyPosts = postsByCompany.get(post.tenantId) || new Map();
      companyPosts.set(post.id, post);
      postsByCompany.set(post.tenantId, companyPosts);
    }
    companies.value = companyResult.map((company) => {
      const tenantId = company.tenantId ?? company.id;
      const companyPosts = tenantId == null
        ? []
        : [...(postsByCompany.get(tenantId)?.values() || [])]
            .sort((left, right) => right.id - left.id);
      return {
        ...company,
        id: tenantId,
        tenantId,
        name: company.name,
        posts: companyPosts,
        logo: company.logo || companyPosts.find((post) => post.companyLogo)?.companyLogo,
      };
    });
  } catch {
    companies.value = [];
    loadFailed.value = true;
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void loadCompanies();
  // 与公司列表并行取，互不阻塞 —— 地区树拿不到也不影响列表
  void loadCityGroups();
  document.addEventListener('click', handleCityMenuDismiss);
  document.addEventListener('keydown', handleCityMenuDismiss);
  document.addEventListener('scroll', handleCompanyFiltersScroll, { capture: true, passive: true });
  window.addEventListener('resize', handleCompanyFiltersResize, { passive: true });
  syncCompanyTopbarHeight();
  syncCompanyFiltersPin();
});

onUnmounted(() => {
  document.removeEventListener('click', handleCityMenuDismiss);
  document.removeEventListener('keydown', handleCityMenuDismiss);
  document.removeEventListener('scroll', handleCompanyFiltersScroll, { capture: true });
  window.removeEventListener('resize', handleCompanyFiltersResize);
});
</script>

<template>
  <section class="company-directory" aria-label="招聘公司">
    <!-- 筛选区和结果工具栏合成一个吸顶单元。
         两块分开吸顶不行：筛选区的收拢动画会让高度在 260ms 内从 340 变到 216，
         下面那块的 top 基准却是写死的；而且展开「更多 23」时筛选区还会长高，
         固定偏移当场就错。包一层让高度自己撑，top 永远对得上。 -->
    <div ref="stickyRef" class="company-sticky" :class="{ 'is-pinned': filtersPinned }">
    <div class="company-filters">
      <!-- 页头。形制取自参考站（deephire「AI简历」）的页头：居中主标题 + 一行灰副标题，
           下面并排三张白卡（彩色图标块 + 粗标题 + 灰描述）。位置和卡片内部结构照搬，
           内容换了 —— 原站那三张是「AI 诊断 / 优化 / 生成」三个功能入口，本页没有
           对应功能，硬造三个入口就是编内容；改成三张统计卡，填这一页真有的三个数。

           口径与结果工具栏的「共 N 家公司」分开：这里是全站概览（不随筛选变），
           那里是筛选后的命中数。 -->
      <header v-if="!loading && !loadFailed" class="company-intro">
        <h1>发现好公司</h1>
        <p class="company-intro-sub">看看哪些企业正在招人，直接找到适合你的那一份工作</p>
        <ul class="company-stats" role="status">
          <li v-for="stat in companyStats" :key="stat.label" class="company-stat">
            <span class="company-stat-icon" :class="`is-${stat.tone}`" aria-hidden="true">
              <IconifyIcon :icon="stat.icon" />
            </span>
            <span class="company-stat-copy">
              <span class="company-stat-label">{{ stat.label }}</span>
              <span class="company-stat-note">{{ stat.note }}</span>
            </span>
            <span class="company-stat-value">{{ stat.value }}<small>{{ stat.unit }}</small></span>
          </li>
        </ul>
      </header>

      <form class="company-search" role="search" @submit.prevent="search">
        <label class="search-scope">
          <span class="sr-only">搜索范围</span>
          <!-- 原来这里是原生 <select>：它的下拉列表由浏览器渲染，CSS 够不着，
               永远是系统默认的灰蓝方块。换成 antd Select 才能跟着主题走。 -->
          <Select
            v-model:value="searchScope"
            class="search-scope-select"
            :bordered="false"
            :options="scopeOptions"
            popup-class-name="company-scope-dropdown"
          />
        </label>
        <span class="search-field">
          <IconifyIcon class="search-field-icon" icon="lucide:search" />
          <input v-model="keyword" aria-label="搜索职位或公司" placeholder="搜索职位、公司" type="search" />
        </span>
        <button class="search-submit" type="submit">搜索</button>
      </form>

      <!-- 钉住后四行筛选收起来，只留这一行。
           sticky 的遮挡量 = 已滚动距离，吸顶条越矮，被它盖住的内容越少；
           四行铺开时钉住高度 272px，收起来降到 ~110px（与职位页同级）。 -->
      <div v-if="filtersPinned && !filtersExpanded" class="company-filter-summary">
        <button class="filter-summary-toggle" type="button" @click="filtersExpanded = true">
          <IconifyIcon icon="lucide:sliders-horizontal" />
          筛选<i v-if="activeFilterChips.length">{{ activeFilterChips.length }}</i>
          <IconifyIcon icon="lucide:chevron-down" />
        </button>
        <span v-for="chip in activeFilterChips" :key="chip.key" class="filter-summary-chip">
          {{ chip.label }}
          <button type="button" :aria-label="`取消${chip.label}`" @click="chip.clear()">
            <IconifyIcon icon="lucide:x" />
          </button>
        </span>
        <button class="clear-filters" type="button" @click="resetFilters">清空筛选条件</button>
      </div>

      <template v-else>
      <div class="company-filter-row">
        <span class="filter-label">招聘地点：</span>
        <div class="filter-options" role="group" aria-label="招聘地点">
          <button type="button" :class="{ active: !city }" :aria-pressed="!city" @click="city = ''">不限</button>
          <button v-for="option in cityChips" :key="option" type="button" :class="{ active: city === option }" :aria-pressed="city === option" @click="city = option">{{ option }}</button>
        </div>
        <!-- 行内只铺有职位的热门城市；其余（含全国任何一个市）走这个菜单 -->
        <div class="city-menu">
          <button class="filter-toggle" type="button" :aria-expanded="cityPanelOpen" @click="cityPanelOpen = !cityPanelOpen">
            {{ city && !cities.includes(city) ? city : '全国城市' }}
            <IconifyIcon icon="lucide:chevron-down" />
          </button>

          <div v-if="cityPanelOpen" class="city-menu-panel" role="dialog" aria-label="选择城市">
            <label class="city-menu-search">
              <IconifyIcon icon="lucide:search" />
              <input v-model="cityKeyword" type="search" placeholder="搜索城市，如：延安" aria-label="搜索城市" />
            </label>

            <div class="city-menu-body">
              <!-- 搜索态：摊平成一张表 -->
              <template v-if="cityKeyword.trim()">
                <p v-if="!citySearchResults.length" class="city-menu-empty">没有匹配的城市</p>
                <button v-for="item in citySearchResults" :key="`${item.province}-${item.name}`" type="button" :class="{ active: city === item.name }" @click="pickCity(item.name)">
                  {{ item.name }}<small>{{ item.province }}</small>
                </button>
              </template>
              <!-- 浏览态：按省分组 -->
              <template v-else>
                <div v-for="group in sortedCityGroups" :key="group.province" class="city-menu-group">
                  <!-- 带上岗位数，否则「为什么这个省排最前」看不出来 -->
                  <span class="city-menu-province">
                    {{ group.province }}
                    <i v-if="provincePostCounts.get(group.province)">{{ provincePostCounts.get(group.province) }} 个岗位</i>
                  </span>
                  <div class="city-menu-cities">
                    <button v-for="item in group.cities" :key="item.name" type="button" :class="{ active: city === item.name, hot: cityPostCounts.has(item.name) }" @click="pickCity(item.name)">
                      {{ item.name }}<i v-if="cityPostCounts.has(item.name)">{{ cityPostCounts.get(item.name) }}</i>
                    </button>
                  </div>
                </div>
                <p v-if="!cityGroups.length" class="city-menu-empty">城市列表加载失败，可先使用上方热门城市</p>
              </template>
            </div>
          </div>
        </div>
      </div>
      <div class="company-filter-row">
        <span class="filter-label">职位类型：</span>
        <div class="filter-options" role="group" aria-label="职位类型">
          <button type="button" :class="{ active: !postType }" :aria-pressed="!postType" @click="postType = ''">不限</button>
          <button v-for="option in visibleOptions('postType', postTypes, postType)" :key="option" type="button" :class="{ active: postType === option }" :aria-pressed="postType === option" @click="postType = option">{{ option }}</button>
        </div>
        <button v-if="postTypes.length > COLLAPSED_OPTIONS" class="filter-toggle" type="button" :aria-expanded="!!expandedRows.postType" @click="toggleFilterRow('postType')">
          {{ expandedRows.postType ? '收起' : `更多 ${postTypes.length}` }}
          <IconifyIcon :icon="expandedRows.postType ? 'lucide:chevron-up' : 'lucide:chevron-down'" />
        </button>
      </div>
      <div class="company-filter-row">
        <span class="filter-label">融资规模：</span>
        <div class="filter-options" role="group" aria-label="融资规模">
          <button type="button" :class="{ active: !financing }" :aria-pressed="!financing" @click="financing = ''">不限</button>
          <button v-for="option in financingStages" :key="option" type="button" :class="{ active: financing === option }" :aria-pressed="financing === option" @click="financing = option">{{ option }}</button>
        </div>
      </div>
      <div class="company-filter-row">
        <span class="filter-label">公司规模：</span>
        <div class="filter-options" role="group" aria-label="公司规模">
          <button type="button" :class="{ active: !scale }" :aria-pressed="!scale" @click="scale = ''">不限</button>
          <button v-for="option in scales" :key="option" type="button" :class="{ active: scale === option }" :aria-pressed="scale === option" @click="scale = option">{{ option }}</button>
        </div>
        <button class="clear-filters" type="button" @click="resetFilters">清空筛选条件</button>
      </div>
      </template>
    </div>

      <!-- 区块头：左标题 + 计数，右排序 —— 与参考站「我的简历 / 上传附件简历」
           同一形制（那边右侧是深色胶囊按钮，这里右侧是排序胶囊）。
           原来这一行只有一个孤零零的「共 N 家公司」，没有区块名，
           滚到这里不知道自己在看什么。 -->
      <div class="company-results-toolbar">
        <div class="company-results-head">
          <h2 class="company-results-heading">企业名录</h2>
          <span v-if="!loading && !loadFailed" class="company-result-count" role="status">
            共 <strong>{{ filteredCompanies.length }}</strong> 家
          </span>
        </div>
        <div class="company-sort" role="group" aria-label="排序方式">
          <IconifyIcon class="company-sort-icon" icon="lucide:arrow-down-up" />
          <button type="button" :class="{ active: sort === 'hiring' }" :aria-pressed="sort === 'hiring'" @click="sort = 'hiring'">在招优先</button>
          <button type="button" :class="{ active: sort === 'name' }" :aria-pressed="sort === 'name'" @click="sort = 'name'">名称排序</button>
        </div>
      </div>
    </div>

    <div class="company-results" :aria-busy="loading">
      <div v-if="loading" class="company-grid" aria-label="正在加载公司信息">
        <div v-for="item in 8" :key="item" class="company-skeleton"><Skeleton active :paragraph="{ rows: 2 }" /></div>
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
        <article
          v-for="(company, index) in visibleCompanies"
          :key="company.id"
          class="company-card"
          :style="{ '--enter-index': index }"
        >
          <button class="company-card-main" type="button" :aria-label="`进入${company.name}企业主页`" @click="openCompany(company)">
            <span class="company-monogram" :class="{ muted: !company.totalPosts }" aria-hidden="true">
              <img v-if="company.logo" :src="company.logo" :alt="`${company.name} logo`" />
              <span v-else>{{ company.name.slice(0, 2) }}</span>
            </span>
            <span class="company-summary">
              <span class="company-name" :title="company.name">{{ company.name }}</span>
              <span class="company-tags">
                <span v-if="company.totalPosts" class="hiring-tag"><b>{{ company.totalPosts }}</b> 个在招职位</span>
                <span v-else class="idle-tag">暂无公开职位</span>
                <span v-for="option in companyCities(company)" :key="option" class="city-tag">{{ option }}</span>
              </span>
            </span>
            <!-- 头部整块可点进企业主页，但「可点」在视觉上是隐形的。
                 补一个箭头把它说出来 —— hover 时才染色，静止时不抢戏。 -->
            <IconifyIcon class="company-go" icon="lucide:chevron-right" aria-hidden="true" />
          </button>

          <!-- 卡片主体压在「在招职位」上：公司自身的行业 / 规模 / 融资字段基本是空的，
               岗位才是这批数据里真正的内容。取前两条，求职者一眼看到「有没有我干的活」。 -->
          <div v-if="company.posts.length" class="company-hot-posts">
            <!-- target="_blank" 会让 RouterLink 跳过 router.push、交给浏览器原生打开新页
                 （见 vue-router 的 guardEvent：target 不是 _self 时直接 return） -->
            <RouterLink
              v-for="post in company.posts.slice(0, 2)"
              :key="post.id"
              class="company-hot-post"
              :to="{ name: 'PortalRecruitDetail', query: { id: post.id } }"
              target="_blank"
              rel="noopener noreferrer"
            >
              <span class="hot-name" :title="post.postName">{{ post.postName }}</span>
              <!-- 原来这一行只有「职位名 ⋯ 薪资」，中间空出一大片：两端对齐在窄卡里
                   读起来像两个孤立的数据。补上地点凑成三列，并让薪资列定宽右对齐，
                   上下两行就并成了表格，一扫就能比。 -->
              <span class="hot-city">{{ getCity(post) || '地点待定' }}</span>
              <span class="hot-salary">{{ formatSalary(post) }}</span>
            </RouterLink>
          </div>
          <div v-else class="company-no-post">
            <IconifyIcon icon="lucide:briefcase-business" />
            <span>这家公司暂时没有开放岗位</span>
          </div>
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
            <p class="company-detail-caption">{{ activeFilters ? `符合当前筛选条件的职位 · ${selectedCompany?.posts.length || 0} 个职位` : '这家企业正在寻找这样的伙伴' }}</p>
          </div>
          <div class="company-detail-toolbar-actions"><span class="company-detail-tip">点击岗位查看详情 <IconifyIcon icon="lucide:arrow-down-right" /></span><button type="button" class="company-profile-link" @click="openCompanyProfile()">查看完整企业主页 <IconifyIcon icon="lucide:arrow-up-right" /></button></div>
        </div>

        <div v-if="!selectedCompany?.posts.length" class="company-empty">
          <IconifyIcon icon="lucide:briefcase-business" />
          <h3>这家公司暂无公开职位</h3>
          <p>你可以浏览其他正在招聘的公司。</p>
        </div>
        <div v-else class="company-detail-post-list">
          <RouterLink v-for="(post, index) in detailPosts" :key="post.id" class="company-detail-post" :to="{ name: 'PortalRecruitDetail', query: { id: post.id } }" target="_blank" rel="noopener noreferrer">
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
.company-detail-toolbar-actions { display: flex; align-items: center; gap: 14px; }
.company-profile-link { display: inline-flex; align-items: center; gap: 6px; padding: 8px 12px; border: 1px solid var(--company-line); border-radius: 9px; background: #fff; color: var(--company-accent); cursor: pointer; font: inherit; font-size: 12px; }
.company-profile-link:hover { background: var(--company-soft); }

/* 顶部原来由已移除的 .company-directory-intro 提供圆角，现在交给筛选区自己。
   边框已去掉：面板与筛选区都透明后，那圈 1px 轮廓成了唯一的视觉元素，
   而它上下半截来自不同规则、颜色并不一致（暖 #f4e5d7 vs 青 --company-line）。
   圆角保留 —— 当前不显形，但日后若恢复底色或描边，形状还在。

   吸顶：整块（概览 + 搜索 + 四行筛选）钉在导航条之下，滚动时操作不被滚走。
   z-index 30 —— 低于导航条的 40，重叠时导航条压在上面。 */
/* 吸顶单元：筛选区 + 结果工具栏。合并成一块的原因见模板里的注释。
   z-index 30 —— 低于导航条的 40，重叠时导航条压在上面。 */
.company-sticky {
  position: sticky;
  z-index: 30;
  top: var(--company-topbar-h, 60px);
}
/* 静止时整块是透明的（先前特意做的），吸顶后必须自己补一层底，
   否则结果列表会从筛选行背后穿过去。 */
.company-sticky::before {
  position: absolute;
  z-index: -1;
  inset: 0;
  border-radius: 22px 22px 0 0;
  background: rgb(255 255 255 / 94%);
  box-shadow: 0 10px 24px rgb(27 42 61 / 8%);
  backdrop-filter: blur(16px) saturate(140%);
  content: '';
  opacity: 0;
  transition: opacity 260ms var(--company-ease), border-radius 260ms var(--company-ease);
}
/* 吸顶时铺满整页宽度。
   静止时它是网格的第 2 列（1180px），两侧各留一条浅灰边沟（1fr 列）；
   一旦钉住、卡片从边沟里滚过去，1180 的窄条就显得没做完。
   做法是负 margin 把盒子撑到 100vw，再用等量正 padding 把内容推回原来的
   1180 列 —— 只有底色铺开，筛选行和工具栏的位置一格没动。
   （百分比对内联方向的 margin / padding 都是按包含块宽度算，所以两个 100%
   指的都是那 1180 列，互为逆运算。） */
.company-sticky.is-pinned {
  margin-inline: calc((100% - 100vw) / 2);
  padding-inline: calc((100vw - 100%) / 2);
}
.company-sticky.is-pinned::before { opacity: 1; border-radius: 0; }
@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .company-sticky::before { background: #fff; }
}

.company-filters {
  padding: 24px 34px 23px;
  border-radius: 22px 22px 0 0;
  background: transparent;
  transition: padding 260ms var(--company-ease);
}

/* 钉住后收一收：概览让位、搜索框变矮、行距变紧。
   公司页的筛选区有 340px，不收拾一下会占掉半屏。 */
.company-sticky.is-pinned .company-filters { padding: 14px 34px 10px; }
/* 页头整块收起。max-height 管不住 padding，得一起归零，
   否则吸顶后仍留一截空白。 */
.company-sticky.is-pinned .company-intro {
  max-height: 0;
  padding-top: 0;
  padding-bottom: 0;
  opacity: 0;
  overflow: hidden;
}
.company-sticky.is-pinned .company-search { height: 46px; margin-bottom: 8px; }
.company-sticky.is-pinned .company-filter-row { padding: 3px 0; }

/* ── 滚动吸附：已移除，勿加回 ─────────────────────────────────
   原先在 html 上挂 scroll-snap-type: y proximity，把最近的卡片吸到吸顶条下方，
   治的是「停下来第一行被切一半」。但那套和吸顶互相喂：
   吸附位移常超过 syncCompanyFiltersPin 留的 12px slack → is-pinned 掉回 false
   → 吸顶条从 162px 弹回 562px、snap 同时被撤 → 滚动位置再变 → 又吸顶。
   实测位置在 0 / 86 / 256 / 45 / 255 之间反复回跳，就是用户报的「向下滑动会震荡」。
   把 snap 关掉后同一段滚动回跳 0 次。
   「切一半」只在停下时看得见，回跳是操作层面的问题 —— 取舍上留平滑。 */

/* 钉住后的摘要行：替代四行筛选，把吸顶条压到 ~110px。
   显示「筛选 (N)」+ 每个已选项的胶囊（点 × 单独取消）+ 清空。 */
.company-filter-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  padding: 2px 0;
}
.filter-summary-toggle {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  gap: 5px;
  padding: 0 11px;
  border: 1px solid var(--company-line);
  border-radius: 999px;
  background: #fff;
  color: var(--company-ink);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  line-height: 26px;
  transition: border-color 160ms ease, color 160ms ease;
}
.filter-summary-toggle:hover { border-color: var(--company-accent); color: var(--company-accent); }
.filter-summary-toggle :deep(svg) { width: 14px; height: 14px; }
.filter-summary-toggle i {
  padding: 0 5px;
  border-radius: 999px;
  background: var(--company-accent);
  color: #fff;
  font-size: 10px;
  font-style: normal;
  font-weight: 600;
  line-height: 15px;
}
.filter-summary-chip {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 0 4px 0 10px;
  border-radius: 999px;
  background: var(--company-soft);
  color: var(--company-accent);
  font-size: 12px;
  line-height: 26px;
}
.filter-summary-chip button {
  display: inline-flex;
  align-items: center;
  padding: 3px;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: inherit;
  cursor: pointer;
  transition: background-color 140ms ease;
}
.filter-summary-chip button:hover { background: rgb(47 111 179 / 16%); }
.filter-summary-chip button :deep(svg) { width: 12px; height: 12px; }
.company-sticky.is-pinned .company-results-toolbar { padding-bottom: 12px; }

/* ── 页头 ──────────────────────────────────────────────────────
   居中三件套：主标题、一行副标题、三张统计卡。形制对齐参考站，见模板注释。
   整块是 .company-intro，吸顶时一起收起（见上面的 is-pinned 规则）。 */
.company-intro {
  display: grid;
  justify-items: center;
  gap: 9px;
  /* 上限只服务于收起动画的起点，不是布局约束（真实高度约 197px）。
     380 那版留了太多余量：max-height 从 380 降到 197 之前内容一点都不动，
     收起动画前 48% 是空转。300 把空转压到 34%，同时仍留着 100px 让
     副标题换行或统计卡长高时不至于被裁。 */
  max-height: 300px;
  padding: 6px 2px 26px;
  text-align: center;
  transition: max-height 260ms var(--company-ease), padding 260ms var(--company-ease), opacity 180ms ease;
}
.company-intro h1 {
  margin: 0;
  color: var(--company-ink);
  font-size: 30px;
  font-weight: 700;
  line-height: 1.24;
  letter-spacing: -0.02em;
}
.company-intro-sub {
  margin: 0;
  max-width: 34em;
  color: var(--company-muted);
  font-size: 14px;
  line-height: 1.65;
}

/* 统计卡：图标块在左、标题与描述在中间、数字贴右。
   数字走 Georgia 衬线 —— 不是新引入的字体：企业详情弹窗里的 01/02 序号
   已经用它，是这一页既有的「编号语汇」，放大到统计卡上是同一套话往下说。 */
.company-stats {
  display: grid;
  width: min(100%, 1040px);
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin: 13px 0 0;
  padding: 0;
  list-style: none;
}
.company-stat {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 13px;
  padding: 15px 17px;
  border: 1px solid var(--company-hair);
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 1px 2px rgb(27 42 61 / 4%);
  text-align: left;
}
/* 三块图标底各走一个色层：蓝=规模、绿=动作、琥珀=机会。
   绿色那格与页面其它绿色同义（有活可干），不是新造的一层。 */
.company-stat-icon {
  display: grid;
  width: 42px;
  height: 42px;
  flex-shrink: 0;
  place-items: center;
  border-radius: 12px;
  font-size: 20px;
}
.company-stat-icon.is-blue { color: var(--company-accent); background: var(--company-soft); }
.company-stat-icon.is-green { color: var(--job-green); background: var(--job-green-soft); }
.company-stat-icon.is-amber { color: #b4741a; background: #fdf1de; }
.company-stat-copy { display: grid; min-width: 0; flex: 1; gap: 2px; }
.company-stat-label { color: var(--company-ink); font-size: 14px; font-weight: 650; }
.company-stat-note {
  overflow: hidden;
  color: var(--company-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.company-stat-value {
  flex-shrink: 0;
  color: var(--company-accent);
  font-family: Georgia, 'Times New Roman', serif;
  font-size: 24px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: 1;
}
.company-stat-value small { margin-left: 2px; font-size: 12px; font-weight: 600; }

.company-search { transition: height 260ms var(--company-ease), margin 260ms var(--company-ease), border-color 180ms ease, box-shadow 180ms ease; }
.company-filter-row { transition: padding 260ms var(--company-ease); }
/* 形制对齐首页 .hero-search：54px 高、圆角胶囊、输入框内嵌搜索图标、实心按钮。
   只有配色仍取本页的 --company-accent —— 首页那套 --banner-color 是广告轮播的主题色，
   会随轮播换色，公司页没有轮播，照搬会得到一个自己会变色的搜索框。 */
/* 居中：原来只写死 1040px 宽、左对齐，右侧空出 140px，和右对齐的概览
   一左一右把版面拉散。1040 居中后两侧各留 70px，和页头、统计卡、卡片同一条中轴。 */
/* 边框由 2px 实心蓝改成 1px 发丝线 + 一层浅蓝投影。
   原来那圈 2px 的饱和蓝在 1040px 宽上是一条很重的横带，和参考站那种
   「白卡浮在浅色画布上」的轻法相反 —— 框本身不再是装饰，边界交给投影。 */
.company-search { display: flex; width: min(100%, 1040px); height: 54px; margin: 0 auto 26px; overflow: hidden; border: 1px solid var(--company-hair); border-radius: 14px; background: #fff; box-shadow: 0 4px 16px rgb(47 111 179 / 7%); }
.search-scope { display: flex; align-items: center; padding: 0 17px; border-right: 1px solid var(--company-hair); background: var(--company-tint); }
/* 搜索范围：antd Select（原来的原生 select 下拉样式改不动，见模板注释） */
.search-scope-select { min-width: 112px; }
.search-scope-select :deep(.ant-select-selector) { padding: 0 !important; background: transparent !important; }
.search-scope-select :deep(.ant-select-selection-item) { color: var(--company-accent); font-size: 15px; font-weight: 600; }
.search-scope-select :deep(.ant-select-arrow) { inset-inline-end: 0; color: var(--company-accent); transition: color 160ms ease, transform 200ms ease; }
.search-scope-select:hover :deep(.ant-select-arrow) { color: var(--company-accent-deep); }
.search-scope-select.ant-select-open :deep(.ant-select-arrow) { transform: rotate(180deg); }
.search-field { display: flex; align-items: center; gap: 10px; min-width: 0; flex: 1; padding: 0 18px; background: #fff; }
.search-field-icon { flex-shrink: 0; color: var(--company-accent); font-size: 17px; }
.company-search input { width: 0; min-width: 0; flex: 1; padding: 0; border: 0; background: transparent; color: var(--company-ink); font: inherit; font-size: 17px; }
.company-search input::placeholder { color: #9aa6ad; }
.company-search input:focus { outline: none; }
/* 聚焦反馈统一在文件末尾的改版块里给（蓝色柔光），这里不再保留青色轮廓 */
.search-submit { min-width: 104px; flex-shrink: 0; border: 0; background: var(--company-accent); color: #fff; cursor: pointer; font-size: 16px; font-weight: 700; letter-spacing: 0.04em; transition: background-color 160ms ease; }
/* 首页那颗是纯色 + 悬停加深，不是渐变，这里跟着走；
   渐变仍留给 .company-sort button.active，那是本页自己的排序按钮。 */
.search-submit:hover, .search-submit:focus-visible { background: color-mix(in srgb, var(--company-accent) 80%, var(--company-ink) 20%); }
.company-filter-row { display: flex; align-items: flex-start; gap: 18px; padding: 8px 0; font-size: 14px; line-height: 28px; }
.filter-label { width: 84px; flex-shrink: 0; color: #6e5545; font-size: 14px; font-weight: 600; }
.filter-options { display: flex; min-width: 0; flex: 1; flex-wrap: wrap; gap: 4px 7px; }
.filter-options button { padding: 0 10px; border: 0; border-radius: 999px; background: transparent; color: var(--company-ink); cursor: pointer; font: inherit; line-height: 28px; transition: color 160ms ease, background-color 160ms ease; }
.filter-options button.active { background: var(--company-soft); color: var(--company-accent); font-weight: 700; }
.filter-options button:hover { background: #fff7ef; color: var(--company-accent); }
.clear-filters { margin-left: auto; padding: 0; flex-shrink: 0; border: 0; background: none; color: var(--company-muted); cursor: pointer; font: inherit; line-height: 28px; }
.clear-filters:hover { color: var(--company-accent); }
/* 结果区不再做左右内缩：公司卡片要与首页、职位页的卡片落在同一条内容基线
   （首页 .home-job-card / 职位页 .search-post-card 都紧贴容器边），
   否则整块会比另外两页右移 25px（24px padding + 1px border）。 */
.company-results { min-height: 400px; padding: 27px 0 31px; border-radius: 0 0 22px 22px; background: transparent; }
/* 工具栏与卡片同一条左基线，但保留 16px 内衬，避免文字贴住面板边框。 */
/* 工具栏已并入吸顶单元，内边距跟筛选行对齐（原来是 0 16px，配合 .company-results 的排版） */
.company-results-toolbar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 14px; padding: 0 34px 22px; transition: padding 260ms var(--company-ease); }
.company-sort { display: inline-flex; align-items: center; gap: 3px; padding: 4px; border: 1px solid var(--company-hair); border-radius: 999px; background: #fff; box-shadow: 0 1px 2px rgb(27 42 61 / 4%); }
/* 原来这里是一个蓝底白图标方块 + 「发现好公司」文字。那句话搬到页头去了，
   方块留着就成了纯装饰 —— 换成一个不占位的细线条图标，控件收回成一枚分段选择器。 */
.company-sort-icon { width: 15px; height: 15px; margin: 0 5px 0 9px; flex-shrink: 0; color: var(--company-muted); }
.company-sort button { padding: 5px 14px; border: 0; border-radius: 999px; background: none; color: var(--company-muted); cursor: pointer; font: inherit; font-size: 13px; transition: background-color 160ms ease, color 160ms ease; }
.company-sort button.active { background: var(--company-accent); color: #fff; font-weight: 600; }
.company-sort button:not(.active):hover { background: var(--company-soft); color: var(--company-accent); }
/* 区块头左侧：区块名 + 计数。两者同排、基线对齐 —— 计数是标题的量词，
   分开两端就和标题脱了关系。 */
.company-results-head { display: flex; align-items: baseline; gap: 11px; }
.company-results-heading {
  margin: 0;
  color: var(--company-ink);
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.01em;
}
.company-result-count { color: var(--company-muted); font-size: 13px; }
/* 计数走统计卡那套 Georgia 衬线数字，同一页里「数」只有一种长相 */
.company-result-count strong {
  margin: 0 3px;
  color: var(--company-accent);
  font-family: Georgia, 'Times New Roman', serif;
  font-size: 19px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.company-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.company-card { position: relative; min-width: 0; overflow: hidden; border: 1px solid #f2e1d2; border-radius: 16px; background: #fff; box-shadow: 0 5px 15px rgb(172 95 28 / 3%); transition: transform 180ms ease, border-color 180ms ease, box-shadow 180ms ease; }
.company-card::before { position: absolute; top: 0; right: 20px; left: 20px; height: 3px; border-radius: 0 0 4px 4px; background: #f5dcc2; content: ''; transition: background-color 180ms ease; }
.company-card:hover { border-color: #f2bd7b; box-shadow: 0 6px 22px rgb(183 92 24 / 10%); }
.company-card:hover::before { background: linear-gradient(90deg, #ef9a4a, #d86616); }
.company-card-main { display: flex; width: 100%; min-height: 138px; align-items: center; gap: 15px; padding: 22px 20px; border: 0; background: #fff; color: inherit; cursor: pointer; text-align: left; }
.company-monogram { display: grid; width: 62px; height: 62px; flex-shrink: 0; place-items: center; overflow: hidden; border: 1px solid var(--company-line); border-radius: 13px; background: var(--company-soft); color: var(--company-accent); font-family: 'Microsoft YaHei', sans-serif; font-size: 22px; font-weight: 700; letter-spacing: 1px; }
.company-monogram img { width: 100%; height: 100%; object-fit: contain; }
.company-monogram.muted { border-color: var(--company-line); background: #f3f7f9; color: #66828e; }
.company-summary { display: grid; min-width: 0; gap: 12px; }
.company-name { display: -webkit-box; overflow: hidden; -webkit-line-clamp: 2; -webkit-box-orient: vertical; font-family: 'Microsoft YaHei', sans-serif; font-size: 16px; font-weight: 500; line-height: 1.55; overflow-wrap: anywhere; }
.company-tags { display: flex; flex-wrap: wrap; gap: 5px; color: var(--company-muted); font-size: 11px; }
.company-tags > span { max-width: 100%; overflow: hidden; padding: 2px 6px; border-radius: 4px; background: #f5f7f8; text-overflow: ellipsis; white-space: nowrap; }
.company-tags .hiring-tag { color: var(--company-accent); background: var(--company-soft); }
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
.company-empty button { margin-top: 8px; padding: 8px 18px; border: 1px solid var(--company-accent); border-radius: 7px; background: #fff; color: var(--company-accent); cursor: pointer; }
.company-pagination { display: flex; justify-content: center; flex-wrap: wrap; gap: 4px; margin-top: 25px; }
.company-pagination :deep(.ant-pagination-item),
.company-pagination :deep(.ant-pagination-item-link) { border-color: #cde3e4; background: #fff; }
.company-pagination :deep(.ant-pagination-item a),
.company-pagination :deep(.ant-pagination-item-link) { color: #526b73; }
.company-pagination :deep(.ant-pagination-item-active) { border-color: #d86616; background: #d86616; }
.company-pagination :deep(.ant-pagination-item-active a) { color: #fff; }
.company-pagination :deep(.ant-pagination-disabled .ant-pagination-item-link) { color: #a8b7bd; }
.company-detail { color: #3f2a1d; }
.company-detail-header { position: relative; display: flex; align-items: center; gap: 18px; overflow: hidden; padding: 34px 40px 29px; border-bottom: 1px solid #f3dfcc; background: linear-gradient(118deg, #fff4e5 0%, #fffaf4 60%, #fffdfb 100%); }
.company-detail-header::after { position: absolute; right: -42px; bottom: -86px; width: 205px; height: 205px; border: 1px solid #efc38f; border-radius: 50%; box-shadow: 0 0 0 17px #f7dfc655, 0 0 0 34px #f7dfc622; content: ''; }
.company-detail-monogram { position: relative; z-index: 1; display: grid; width: 76px; height: 76px; flex-shrink: 0; place-items: center; overflow: hidden; border: 1px solid #f4bc79; border-radius: 19px; background: #fff0df; color: #c85e18; font-size: 25px; font-weight: 700; letter-spacing: 1px; box-shadow: 0 9px 17px rgb(183 92 24 / 10%); }
.company-detail-monogram img { width: 100%; height: 100%; object-fit: contain; }
.company-detail-monogram.muted { border-color: #e0e7e8; background: #f3f7f7; color: #6c8185; box-shadow: none; }
.company-detail-heading { position: relative; z-index: 1; min-width: 0; }
.company-detail-eyebrow { display: block; margin-bottom: 8px; color: #c85e18; font-size: 10px; font-weight: 700; letter-spacing: .14em; }
.company-detail-heading h2 { max-width: 570px; margin: 0; color: #3f2718; font-size: 24px; font-weight: 700; line-height: 1.35; overflow-wrap: anywhere; }
.company-detail-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 8px 16px; margin-top: 11px; color: #907a6b; font-size: 12px; }
.company-detail-meta > span { display: inline-flex; align-items: center; gap: 6px; }
.company-detail-meta > span:first-child { color: #c85e18; font-weight: 700; }
.company-detail-meta > span:first-child i { width: 6px; height: 6px; border-radius: 50%; background: #e87522; box-shadow: 0 0 0 3px #f9dfc6; }
.company-detail-meta > span:first-child.muted { color: #74878a; font-weight: 500; }
.company-detail-meta > span:first-child.muted i { background: #9babad; box-shadow: 0 0 0 3px #e5eeee; }
.company-detail-mark { position: relative; z-index: 1; display: grid; gap: 1px; margin: 0 4px 0 auto; color: #d98743; font-family: Georgia, 'Times New Roman', serif; font-size: 27px; font-weight: 700; line-height: .85; letter-spacing: .08em; opacity: .72; }
.company-detail-mark span { margin-left: 13px; color: #e4ae79; font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif; font-size: 9px; letter-spacing: .28em; }
.company-detail-toolbar { display: flex; align-items: flex-end; justify-content: space-between; gap: 18px; padding: 25px 40px 15px; }
.company-detail-kicker { color: #c85e18; font-size: 11px; font-weight: 700; letter-spacing: .15em; }
.company-detail-toolbar p { margin: 7px 0 0; color: #6f5b4e; font-size: 13px; }
.company-detail-tip { display: inline-flex; align-items: center; gap: 6px; color: #ae927e; font-size: 11px; white-space: nowrap; }
.company-detail-tip :deep(svg) { width: 14px; height: 14px; }
.company-detail-post-list { padding: 0 26px 8px; }
.company-detail-post { display: grid; grid-template-columns: 39px minmax(0, 1fr) auto; align-items: center; gap: 14px; min-height: 73px; padding: 13px 14px; border-top: 1px solid #f1e5dc; border-radius: 10px; color: #3f2a1d; transition: background-color 160ms ease, transform 160ms ease; }
.company-detail-post:hover { background: #fff6ed; color: #3f2a1d; transform: translateX(3px); }
.company-detail-post-index { color: #ddb68e; font-family: Georgia, 'Times New Roman', serif; font-size: 17px; font-weight: 700; }
.company-detail-post-copy { min-width: 0; }
.company-detail-post strong { display: block; color: #453126; font-size: 15px; font-weight: 600; line-height: 1.45; overflow-wrap: anywhere; }
.company-detail-post small { display: flex; align-items: center; min-width: 0; gap: 7px; margin-top: 6px; color: #9a8677; font-size: 11px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.company-detail-post small :deep(svg) { width: 13px; height: 13px; flex-shrink: 0; color: #d98a47; }
.company-detail-post small i { width: 3px; height: 3px; flex-shrink: 0; border-radius: 50%; background: #d9bba0; }
.company-detail-post-reward { display: inline-flex; align-items: center; gap: 11px; color: #d86616; }
.company-detail-post-reward b { flex-shrink: 0; font-size: 14px; font-variant-numeric: tabular-nums; }
.company-detail-post-reward :deep(svg) { width: 15px; height: 15px; color: #e1a16b; transition: transform 160ms ease; }
.company-detail-post:hover .company-detail-post-reward :deep(svg) { transform: translate(2px, -2px); }
.company-detail .company-empty { min-height: 230px; margin: 0 26px 22px; border: 1px dashed #edcfb1; background: #fffaf5; }
.company-detail .company-empty > :deep(svg) { color: #e39a5a; }
.company-directory button:focus-visible, .company-directory a:focus-visible, .company-detail a:focus-visible { outline: 2px solid #d86616; outline-offset: -2px; }
@media (max-width: 1200px) {
  .company-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .clear-filters { align-self: flex-end; }
}
@media (max-width: 900px) {
  .company-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .company-filter-row { flex-wrap: wrap; gap: 8px; }
  .clear-filters { margin-left: 92px; }
  .company-filters { padding: 22px 20px; }
  /* 三张统计卡在 900px 仍排一行，但 700px 上下每张只剩 ~186px ——
     图标块和数字各收一档，标题钉死不换行（换行会让三张卡高度参差），
     让位给数字；描述行带省略号，挤不下时截的是它，那是三行里最次要的。 */
  .company-intro h1 { font-size: 26px; }
  .company-stats { gap: 11px; }
  .company-stat { gap: 10px; padding: 12px 13px; }
  .company-stat-icon { width: 38px; height: 38px; border-radius: 11px; font-size: 18px; }
  .company-stat-label { white-space: nowrap; }
  .company-stat-value { font-size: 21px; }
}
@media (max-width: 600px) {
  .company-grid { grid-template-columns: minmax(0, 1fr); gap: 14px; }
  .company-filters { padding: 18px 14px; }
  /* 统计卡改一列（三张并排在 360px 宽里每张只剩 100px，图标块就要占掉一半），
     整块因此长到约 318px，收起动画的起点跟着抬高。 */
  .company-intro { gap: 7px; max-height: 380px; padding: 2px 2px 20px; }
  .company-intro h1 { font-size: 23px; }
  .company-intro-sub { font-size: 13px; }
  .company-stats { grid-template-columns: minmax(0, 1fr); gap: 9px; margin-top: 10px; }
  .company-search { height: 50px; margin-bottom: 18px; }
  .search-scope { padding: 0 7px; }
  .search-scope-select { min-width: 88px; }
  .search-scope-select :deep(.ant-select-selection-item) { font-size: 12px; }
  .company-search input { padding: 0 8px; font-size: 13px; }
  .search-submit { width: 64px; font-size: 16px; }
  .company-filter-row { font-size: 12px; }
  .filter-label { width: 72px; font-size: 13px; }
  .filter-options { gap: 3px 14px; }
  .company-results { padding: 18px 0 24px; }
  .company-results-toolbar { padding: 0 10px; }
  .company-sort { gap: 2px; }
  .company-sort button { padding: 6px 8px; font-size: 12px; }
  .company-card-main { min-height: 125px; }
  .company-name { font-size: 17px; }
}
@media (prefers-reduced-motion: reduce) {
  .company-card { animation: none; transition: none; }
  .filter-options button,
  .filter-toggle :deep(svg),
  .city-menu-body button,
  .company-hot-post,
  .hot-name,
  .hot-salary { transition: none; }
}
/* 筛选区跟着面板一起透明（露页面灰底），卡片与空态仍留白 —— 两者不再共用一条规则 */
.company-directory .company-filters { background: transparent; }
.company-directory .company-empty { border-color: var(--company-line); background: #fff; }

:global(.company-detail-modal .ant-modal) { max-width: calc(100vw - 24px); padding-bottom: 0; }
:global(.company-detail-modal .ant-modal-content) { overflow: hidden; padding: 0; border: 1px solid #f1dfcf; border-radius: 24px; background: #fffdfb; box-shadow: 0 26px 80px rgb(76 42 18 / 22%); }
:global(.company-detail-modal .ant-modal-body) { padding: 0; }
:global(.company-detail-modal .ant-modal-close) { top: 16px; right: 16px; z-index: 3; width: 34px; height: 34px; border-radius: 50%; color: #9e7e66; background: #ffffffbf; transition: color 160ms ease, background-color 160ms ease, transform 160ms ease; }
:global(.company-detail-modal .ant-modal-close:hover) { color: #c85e18; background: #fff0df; transform: rotate(8deg); }

@media (max-width: 600px) {
  .company-filters { padding: 18px 14px 17px; border-radius: 16px 16px 0 0; }
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

.company-directory {
  /* 配色直接指向职位页那套 token（定义在 index.vue 的 .recruit-page 上）。
     两页共用一份，改一边另一边自动跟随，不会各抄一套值后走样。 */
  --company-accent: var(--job-blue);
  --company-ink: var(--job-ink);
  --company-muted: var(--job-muted);
  --company-soft: var(--job-blue-soft);
  --company-line: var(--job-line);
  /* 浅蓝分层。参考站的画布是「极浅灰 + 纯白卡」，这里翻成浅蓝：
     画布泛蓝、卡片纯白，两者靠一条比 --job-line 更浅的发丝线拉开。
     为什么不直接把 --company-line 调浅：它指向 --job-line，职位页也在用，
     改一处两页跟着变 —— 那正是这个 token 要避免的事。 */
  --company-hair: #e3eefb;
  /* 浅色块底（搜索框左侧的范围选择区） */
  --company-tint: #f6faff;
  /* 悬停/聚焦时的加深色，由主色推导，改主色时这里自动跟随 */
  --company-accent-deep: color-mix(in srgb, var(--job-blue) 78%, #0b2a4a 22%);
  --company-ease: cubic-bezier(0.22, 0.61, 0.36, 1);
  color: var(--company-ink);
  /* 页头那片浅蓝。居中收在 32rem（512px）以内淡尽 —— 本栏宽 1180px，
     半宽 590px，所以辉光到左右边缘已经是透明的，不会在栏位边界切出一条直边。
     用径向渐变而不是给整栏铺底色也是这个原因：整栏铺会在 1180px 处断掉，
     两侧的边沟仍是页面底色，接缝一眼可见。 */
  background: radial-gradient(circle at 50% 0, rgb(47 111 179 / 9%), transparent 32rem);
  /* 关掉滚动锚定 —— 否则吸顶会和浏览器打架，滚到临界点就抖个不停。
     机制：吸顶块收拢时高度骤减（340→216），Chrome 的 scroll anchoring
     为了「保持可见内容稳定」会自动把 scrollY 反向补偿同样的距离，
     于是滚回临界点之上 → 取消吸顶 → 展开 → 再次补偿 → 死循环。
     实测在临界点附近每秒翻转几十次（滚动位置在 2↔30 之间横跳）。
     作用域必须罩住下面的卡片：锚点是从这些卡片里挑的，
     只写在 .company-sticky 上没用（实测 135 次翻转，几乎等于没改）。 */
  overflow-anchor: none;
}

/* ── 公司卡片 ─────────────────────────────────────────────────
   数据现实决定形态：公司自身的 logo / 行业 / 规模 / 融资覆盖率极低
   （20 家里 logo 2、行业 2、规模 2、融资阶段 0），所以不做 LinkedIn 那种
   「行业 · 规模 · 融资」元信息行 —— 照搬会 18/20 是空白。
   改成以岗位为主体：那是这批数据里真正的内容（每家 1~200 个在招职位）。 */

/* 列数已在上面的 .company-grid 基础规则里改成 3 列（4 列时每张只 280px，
   塞不下「公司名 + 标签 + 两条职位」）；窄屏的 3/2/1 列由既有媒体查询接管，
   这里不再重复声明，否则会盖掉它们 —— 媒体查询不增加权重。 */

/* ── 配色分层：沿用职位页的分工 ───────────────────────────────
   蓝 = 结构与信息（边框、公司名、logo、选中态）
   绿 = 动作（在招职位行、岗位标签、有岗位的城市）
   红 = 薪资
   之前整页只有蓝，静态信息和可点的东西混在一个颜色里，看着发闷、
   也看不出哪儿能点。绿只出现在「点了会走」的地方，本身就是一种指路。 */

.company-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  /* 发丝线而不是 --company-line：卡片是纯白、画布是浅蓝，两者本来就有色差，
     再压一条 #d7e5f4 的实边会把卡片框死；参考站的白卡几乎只靠投影浮起来。 */
  border-color: var(--company-hair);
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 1px 2px rgb(27 42 61 / 4%);
  /* backwards 而不是 both：both 会让 100% 关键帧的 transform 一直占着，
     :hover 的 translateY(-2px) 就被动画锁死失效了 */
  animation: company-card-settle 420ms cubic-bezier(0.22, 0.61, 0.36, 1) backwards;
  animation-delay: calc(var(--enter-index, 0) * 38ms);
  transition: border-color 160ms ease, box-shadow 160ms ease, transform 160ms ease;
}
/* 错峰入场：一屏 12 张同时淡入会糊成一片，隔 38ms 依次落位才有节奏 */
@keyframes company-card-settle {
  0% { opacity: 0; transform: translateY(18px) scale(0.985); }
  62% { opacity: 1; transform: translateY(-4px) scale(1.004); }
  100% { opacity: 1; transform: translateY(0) scale(1); }
}
.company-card:hover {
  border-color: var(--company-accent);
  box-shadow: 0 10px 26px rgb(47 111 179 / 12%);
  transform: translateY(-2px);
}
/* 公司名跟着卡片悬停变蓝 —— 整张卡片可点，让它读起来像个链接 */
.company-card:hover .company-name { color: var(--company-accent); }
.company-card:hover .company-monogram { border-color: var(--company-accent); }
/* 原来顶上那条彩色渐显条与蓝色体系冲突，去掉 */
.company-card::before { display: none; }
.company-card:hover::before { display: none; }

.company-card-main {
  /* 基础规则留了 min-height: 138px，那是给两行公司名预留的；
     这里公司名单行截断，头部按内容收高，否则中间空出一大块。 */
  min-height: 0;
  align-items: flex-start;
  gap: 12px;
  padding: 18px 18px 14px;
  margin-inline: 0;
  background: transparent;
  text-align: left;
}
/* logo 与首行文字顶部对齐：公司名长短不一，居中对齐会让两者看起来错位。 */
/* 首字块是「公司墙」的视觉锚点，46px 配 15px 的公司名偏小、撑不起卡片。
   加到 52px 并把公司名提到 17px/700，头部才有分量。 */
.company-monogram {
  flex-shrink: 0;
  width: 52px;
  height: 52px;
  border: 1px solid var(--company-line);
  border-radius: 14px;
  color: var(--company-accent);
  /* 白底：企业 logo 多为透明底 PNG，浅蓝底会在图形四周透出一圈蓝，
     和卡片白底割裂。边框留着，白底靠它和卡片区分。 */
  background: #fff;
  font-size: 19px;
  font-weight: 700;
}
/* 内衬 3px：企业 logo 多是横版（实测天信那枚 2362×945，2.5:1），
   contain 塞进正方块里两头留白，贴边反而显得字块比图形大。 */
.company-monogram img { box-sizing: border-box; padding: 3px; }
.company-summary { min-width: 0; gap: 9px; }
.company-name {
  display: block;
  overflow: hidden;
  color: var(--company-ink);
  font-size: 17px;
  font-weight: 700;
  line-height: 1.35;
  letter-spacing: -0.005em;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.company-tags { gap: 6px; }
.company-tags span {
  padding: 2px 8px;
  border-radius: 999px;
  color: var(--company-muted);
  background: #f4f7fb;
  font-size: 11px;
  line-height: 18px;
}
/* 岗位标签归「动作」层 —— 职位页同样是绿色给职位标签。
   数字单独提权：一家公司有多少活可干，是这张卡里最该被读到的信息，
   原来它和旁边的城市标签一样大、一样淡。 */
.company-tags .hiring-tag {
  display: inline-flex;
  align-items: baseline;
  gap: 3px;
  padding: 2px 9px;
  border-color: transparent;
  color: var(--job-green);
  background: var(--job-green-soft);
  font-weight: 600;
}
.company-tags .hiring-tag b { font-size: 13px; font-weight: 700; font-variant-numeric: tabular-nums; }
.company-tags .idle-tag { color: #8a99a8; background: #f1f4f7; }

/* 进入企业主页的提示箭头。头部整块是 button，但「能点」不会自己说出来。
   绝对定位而非 flex 项：它一旦参与流，就吃掉 30px（18 + gap 12）宽度 ——
   实测正好把「200 个在招职位 + 榆林市 + 乌鲁木齐市」这一行挤到换行，
   同行三张卡片高度因此参差。 */
.company-card-main { position: relative; }
.company-go {
  position: absolute;
  top: 20px;
  right: 16px;
  width: 18px;
  height: 18px;
  color: #c3cedd;
  transition: color 160ms ease, transform 160ms ease;
}
.company-card:hover .company-go { color: var(--job-green); transform: translateX(3px); }

/* 在招职位列表：卡片主体 */
.company-hot-posts {
  display: flex;
  flex-direction: column;
  margin-top: auto;
  border-top: 1px solid var(--company-hair);
}
/* 三列：职位名吃剩余宽度、地点与薪资各自定宽。
   薪资列给 min-width 是为了让上下两行的「6.8K-9.8K」「7K-10K」右边缘对齐 ——
   两端对齐时它们各自缩在自己的字宽里，比不出大小。 */
.company-hot-post {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(74px, auto);
  align-items: center;
  gap: 10px;
  padding: 11px 18px;
  /* 基础规则给每一行都加了暖色上边框(#f7e8dc)和暖色渐变底，这里清掉：
     分隔线统一交给 .company-hot-posts 和 + 兄弟选择器，颜色才归 --job-line */
  border-top: 0;
  background: transparent;
  color: var(--company-ink);
  transition: background-color 140ms ease;
}
.company-hot-post + .company-hot-post { border-top: 1px solid var(--company-hair); }
.company-hot-post:last-child { border-radius: 0 0 15px 15px; }
/* 职位行是卡片里唯一点了会走的地方，用绿 + 右移提示「可以进去」 */
.company-hot-post:hover { background: var(--job-green-soft); }
.company-hot-post:hover .hot-name {
  color: var(--job-green);
  text-decoration: underline;
  text-underline-offset: 3px;
}
.company-hot-post:hover .hot-salary { transform: translateX(3px); }
.hot-name {
  overflow: hidden;
  min-width: 0;
  color: var(--company-ink);
  font-size: 14px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 140ms ease;
}
/* 地点列：补上原来缺失的那一格。职位名与薪资分居两端时，
   卡片中间空出一大片，两行之间也没有共同的可比项。 */
.hot-city {
  overflow: hidden;
  color: var(--company-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 薪资用与职位页同一个 token（当前 #ff0000），两页一致 */
.hot-salary {
  justify-self: end;
  color: var(--job-salary);
  font-size: 13px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  text-align: right;
  transition: transform 160ms ease;
}
.company-no-post {
  display: flex;
  /* 基础规则里是 space-between（原设计「暂无公开职位 / 敬请关注」分居两端），
     改成图标 + 一句话后必须收回来，否则文字被推到最右边、和图标隔着整个卡片。 */
  justify-content: flex-start;
  align-items: center;
  gap: 8px;
  margin-top: auto;
  padding: 17px 18px;
  border-top: 1px solid var(--company-hair);
  background: transparent;
  color: var(--company-muted);
  font-size: 12px;
}
.company-no-post :deep(svg) { width: 15px; height: 15px; flex-shrink: 0; color: #a9b8c6; }

/* ── 筛选区与工具栏 ────────────────────────────────────────── */
.filter-label { color: var(--company-muted); }
.filter-options button.active {
  color: var(--company-accent);
  background: var(--company-soft);
  font-weight: 600;
}
.filter-options button:hover { color: var(--company-accent); background: var(--company-soft); }

/* 长选项行（35 个城市 / 24 个类型）折叠成一行，其余收进这个按钮 ——
   整片 chip 墙原来占 468px，把结果区压到首屏之外。 */
.filter-toggle {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  gap: 2px;
  padding: 0 6px;
  border: 0;
  background: transparent;
  color: var(--company-accent);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  line-height: 28px;
  white-space: nowrap;
  transition: color 160ms ease;
}
.filter-toggle:hover { color: var(--company-ink); }
.filter-toggle :deep(svg) { width: 14px; height: 14px; transition: transform 200ms ease; }
/* 展开时箭头翻转，与职位页筛选胶囊同一套语言 */
.filter-toggle[aria-expanded='true'] { color: var(--company-ink); }
.filter-toggle[aria-expanded='true'] :deep(svg) { transform: rotate(180deg); }

/* ── 微交互 ───────────────────────────────────────────────────
   悬停抬 1px、按下缩 3%，让点击有「按到了」的手感。
   位移都刻意做小，否则整片筛选区会跟着一起晃。 */
.filter-options button { transition: color 160ms ease, background-color 160ms ease, transform 150ms ease; }
.filter-options button:hover { transform: translateY(-1px); }
.filter-options button:active { transform: translateY(0) scale(0.96); }
/* 已选中那颗不再抬：它已经是终态，再动会显得没落定 */
.filter-options button.active:hover { transform: none; }

/* 清空筛选条件是「动作」，归绿色层，与职位标签、职位行同一族 */
.clear-filters { color: var(--job-green); border-radius: 999px; padding: 0 8px; transition: color 160ms ease, background-color 160ms ease; }
.clear-filters:hover { color: var(--job-green); background: var(--job-green-soft); }

.city-menu-body button:hover { transform: translateY(-1px); }
.city-menu-body button:active { transform: scale(0.96); }

/* ── 搜索框交互 ────────────────────────────────────────────────
   与职位页搜索框同一套反馈：悬停边框加深、聚焦加一圈柔光，
   图标与占位文字跟着动 —— 否则点进去只有光标在闪，反馈太弱。 */
.company-search { transition: border-color 180ms ease, box-shadow 180ms ease; }
/* 悬停只把发丝线提亮一档。这里原来直接跳到 --company-accent-deep（78% 主色 + 深藏青），
   配 2px 边框时不显，换成 1px 之后那圈深蓝反而像一条被选中的边。 */
.company-search:hover { border-color: #b9d4f0; }
.company-search:focus-within {
  border-color: var(--company-accent);
  outline: none;
  box-shadow: 0 0 0 4px rgb(47 111 179 / 13%);
}
.search-field-icon { transition: transform 180ms ease; }
.company-search:focus-within .search-field-icon { transform: scale(1.08); }
.company-search input::placeholder { transition: color 180ms ease; }
.company-search:focus-within input::placeholder { color: #b3c2d3; }
.search-submit { transition: background-color 180ms ease, transform 120ms ease; }
.search-submit:active { transform: scale(0.975); }

/* 范围下拉的弹层挂在 body 下，够不到作用域里的 --job-* 变量，只能写字面量。
   下面这几个值就是 --job-line / --job-ink / --job-blue / --job-blue-soft，
   改职位页配色时这里要跟着改。

   背景必须显式写白：这个应用里 antd 弹层的背景 token 解析出来是深色
   rgb(36,36,36)，职位页那边是靠 .recruit-filter-dropdown 那个 class 压成白的
   （见 index.vue 的 :global 块）。这里没有那个 class，不写就会露出一块黑板。 */
:global(.company-scope-dropdown.ant-select-dropdown) {
  padding: 6px;
  border: 1px solid #d7e5f4;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 14px 32px rgb(27 42 61 / 12%);
}
:global(.company-scope-dropdown .ant-select-item) {
  border-radius: 8px;
  color: #1b2a3d !important;
  background: transparent !important;
  font-size: 14px;
  transition: background-color 140ms ease;
}
:global(.company-scope-dropdown .ant-select-item-option-active:not(.ant-select-item-option-selected)) {
  color: #1b2a3d !important;
  background: #e8f1fb !important;
}
:global(.company-scope-dropdown .ant-select-item-option-selected) {
  color: #2f6fb3 !important;
  background: #e8f1fb !important;
  font-weight: 600;
}

/* ── 全国城市菜单 ──────────────────────────────────────────────
   行内铺不下全国三百多个市，有职位的热门城市留在行里，其余收进这个面板：
   按省分组、可搜索，有在招职位的城市标个数字，点之前就知道会不会是空结果。 */
.city-menu { position: relative; flex-shrink: 0; }
.city-menu-panel {
  position: absolute;
  z-index: 20;
  top: calc(100% + 6px);
  right: 0;
  display: flex;
  width: 580px;
  max-width: min(580px, calc(100vw - 48px));
  max-height: 380px;
  flex-direction: column;
  border: 1px solid var(--company-line);
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 18px 44px rgb(27 42 61 / 16%);
}
.city-menu-search {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 8px;
  padding: 11px 14px;
  border-bottom: 1px solid var(--company-line);
}
.city-menu-search :deep(svg) { flex-shrink: 0; width: 15px; height: 15px; color: var(--company-muted); }
.city-menu-search input {
  width: 100%;
  border: 0;
  background: transparent;
  color: var(--company-ink);
  font: inherit;
  font-size: 13px;
  outline: none;
}
.city-menu-search input::placeholder { color: #9aa8b8; }

.city-menu-body {
  overflow-y: auto;
  padding: 6px 10px 12px;
  overscroll-behavior: contain;
  scrollbar-color: #c4d8ee transparent;
  scrollbar-width: thin;
}
.city-menu-group { padding: 6px 0; }
.city-menu-province {
  display: block;
  margin-bottom: 4px;
  color: var(--company-muted);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
}
.city-menu-cities { display: flex; flex-wrap: wrap; gap: 4px; }
.city-menu-body button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0 9px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--company-ink);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  line-height: 26px;
  transition: color 140ms ease, background-color 140ms ease, transform 150ms ease;
}
.city-menu-body button:hover { color: var(--company-accent); background: var(--company-soft); }
.city-menu-body button.active { color: #fff; background: var(--company-accent); font-weight: 600; }
/* 角标表示「这儿有活儿」，归绿色动作层；没岗位的城市不显示角标 */
.city-menu-body button i {
  padding: 0 5px;
  border-radius: 999px;
  background: var(--job-green-soft);
  color: var(--job-green);
  font-size: 10px;
  font-style: normal;
  font-weight: 600;
  line-height: 15px;
}
.city-menu-body button.hot:hover i { background: var(--job-green); color: #fff; }
.city-menu-body button.active i { background: rgb(255 255 255 / 26%); color: #fff; }
.city-menu-body button small { color: var(--company-muted); font-size: 10px; }
.city-menu-empty { margin: 14px 4px; color: var(--company-muted); font-size: 12px; }
.company-sort { border-color: var(--company-hair); background: #fff; }
.company-sort button.active { color: #fff; background: var(--company-accent); }
.company-pagination :deep(.ant-pagination-item-active) {
  border-color: var(--company-accent);
  background: var(--company-accent);
}

/* ── 企业详情弹窗 ──────────────────────────────────────────── */
.company-detail-eyebrow,
.company-detail-kicker,
.company-detail-meta > span:first-child,
.company-detail-post-reward { color: var(--company-accent); }
.company-detail-heading h2,
.company-detail-post strong { color: var(--company-ink); }
.company-detail-header {
  border-bottom-color: var(--company-line);
  background: linear-gradient(118deg, var(--company-soft) 0%, #fff 62%);
}
.company-detail-header::after {
  border-color: var(--company-line);
  box-shadow: 0 0 0 17px rgb(47 111 179 / 8%), 0 0 0 34px rgb(47 111 179 / 4%);
}
.company-detail-monogram {
  border-color: var(--company-line);
  color: var(--company-accent);
  /* 与卡片上的 .company-monogram 保持一致的白底 */
  background: #fff;
  box-shadow: 0 9px 17px rgb(47 111 179 / 10%);
}
.company-detail-meta > span:first-child i {
  background: var(--company-accent);
  box-shadow: 0 0 0 3px var(--company-soft);
}
.company-detail-post { border-top-color: var(--company-line); color: var(--company-ink); }
.company-detail-post:hover { color: var(--company-ink); background: var(--company-soft); }
.company-directory button:focus-visible,
.company-directory a:focus-visible,
.company-detail a:focus-visible { outline-color: var(--company-accent); }
:global(.company-detail-modal .ant-modal-content) {
  border-color: var(--company-line);
  background: #fff;
  box-shadow: 0 26px 80px rgb(27 42 61 / 18%);
}
:global(.company-detail-modal .ant-modal-close:hover) {
  color: var(--company-accent);
  background: var(--company-soft);
}
.company-directory .company-detail-post-reward b { color: var(--job-salary); }
</style>
