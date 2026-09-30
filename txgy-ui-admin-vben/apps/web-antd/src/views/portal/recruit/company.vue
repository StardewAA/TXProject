<script setup lang="ts">
import type { HrmRecruitCompanyApi } from '#/api/hrm/recruit/company';

import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

import { Pagination } from 'ant-design-vue';

import {
  getPortalRecruitCompanyList,
  type HrmPortalRecruitApi,
} from '#/api/hrm/portal/recruit';
import {
  getRecruitProfileResume,
  isRecruitProfileComplete,
} from '#/api/hrm/portal/resume';
import {
  getPortalRecruitCompany,
  getPortalRecruitCompanyPostPage,
} from '#/api/hrm/recruit/company';

import LocationCard from './components/location-card.vue';
import RecruitTopbar from './components/recruit-topbar.vue';
import RecruitProfileSetup from './profile-setup.vue';

const route = useRoute();
const router = useRouter();
const loading = ref(true);
const failed = ref(false);
const company = ref<HrmRecruitCompanyApi.Company>();
const posts = ref<HrmPortalRecruitApi.Post[]>([]);
const profileSetupOpen = ref(false);
const tenantId = Number(route.query.tenantId);
const products = computed<HrmRecruitCompanyApi.Product[]>(() => {
  try {
    const parsed = company.value?.products ? JSON.parse(company.value.products) : [];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
});
const album = computed<string[]>(() => {
  try {
    const parsed = company.value?.album ? JSON.parse(company.value.album) : [];
    return Array.isArray(parsed)
      ? parsed.filter((item): item is string => typeof item === 'string' && item.trim().length > 0)
      : [];
  } catch {
    return [];
  }
});
const monogram = computed(() => company.value?.name?.slice(0, 2) || '企');

/* ── 收藏企业 ────────────────────────────────────────────────────
   这一页是从企业目录新开标签页打开的，和首页不共享组件状态，只能通过
   localStorage 对齐 —— 键名和首页保持一致（portal-recruit-company-favorites），
   否则两边各存一份，收藏了在另一边看不到。 */
const COMPANY_FAVORITES_KEY = 'portal-recruit-company-favorites';
const favorited = ref(false);

function readFavoriteIds(): number[] {
  try {
    const saved = JSON.parse(localStorage.getItem(COMPANY_FAVORITES_KEY) || '[]');
    return Array.isArray(saved) ? saved.filter((id): id is number => typeof id === 'number') : [];
  } catch {
    return [];
  }
}

function syncFavorite() {
  favorited.value = readFavoriteIds().includes(tenantId);
}

function toggleFavorite() {
  const ids = readFavoriteIds();
  const next = ids.includes(tenantId)
    ? ids.filter((id) => id !== tenantId)
    : [...ids, tenantId];
  localStorage.setItem(COMPANY_FAVORITES_KEY, JSON.stringify(next));
  favorited.value = next.includes(tenantId);
}

/** 首页那个标签页里收藏/取消时，这一页跟着变 —— 两个标签页会同时开着。 */
function handleFavoriteStorage(event: StorageEvent) {
  if (event.key === COMPANY_FAVORITES_KEY) syncFavorite();
}

/* ── 职位列表分页 ────────────────────────────────────────────────
   岗位原来是整批平铺的：实测秦川一家 200 个岗位排成单列，页高 19267px，
   两列也还有 9000px。分页后一屏半翻完，页高回到正常量级。
   每页 20 条 = 两列 × 10 行，与招聘网站的分页粒度一致。 */
const postPage = ref(1);
const postPageSize = 20;
const pagedPosts = computed(() => posts.value.slice(
  (postPage.value - 1) * postPageSize,
  postPage.value * postPageSize,
));
const postsSectionRef = ref<HTMLElement>();

/** 翻页后把视口带回职位区顶部 —— 否则翻到第 8 页时人还停在半空中。 */
function scrollToPosts() {
  void nextTick(() => {
    postsSectionRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  });
}

function handlePostPageChange(page: number) {
  postPage.value = page;
  scrollToPosts();
}

/** 页头元信息行。行业 / 规模 / 性质 / 融资四项实测覆盖率都不高
    （20 家里只有 1~2 家填全），所以空的一项不占位，而不是留一排「—」。 */
const companyMeta = computed(() => [
  company.value?.industry,
  company.value?.scale,
  company.value?.nature,
  company.value?.financingStage,
].filter((item): item is string => Boolean(item && String(item).trim())));

/** 简介与工商档案都空时才提示。原来无条件显示，
    在秦川这种资料齐全的公司上就成了「企业简介及工商档案尚未完善」的自相矛盾。 */
const thinProfile = computed(() => !company.value?.summary && !company.value?.creditCode);

async function load() {
  if (!Number.isFinite(tenantId) || tenantId <= 0) {
    failed.value = true;
    loading.value = false;
    return;
  }
  loading.value = true;
  failed.value = false;
  try {
    const [companyProfile, firstPage] = await Promise.all([
      getPortalRecruitCompany(tenantId),
      getPortalRecruitCompanyPostPage({ tenantId, pageNo: 1, pageSize: 100 }),
    ]);
    const allPosts = [...firstPage.list];
    for (let pageNo = 2; allPosts.length < firstPage.total; pageNo += 1) {
      const nextPage = await getPortalRecruitCompanyPostPage({ tenantId, pageNo, pageSize: 100 });
      if (!nextPage.list.length) break;
      allPosts.push(...nextPage.list);
    }
    posts.value = allPosts;
    postPage.value = 1;
    const companyResult = companyProfile || (await getPortalRecruitCompanyList())
      .find((item) => (item.tenantId ?? item.id) === tenantId);
    if (!companyResult) {
      failed.value = true;
      return;
    }
    // 用 || 而不是 ??：企业档案里的 postCount 常是 0（未统计），
    // 而 ?? 只挡 null/undefined，0 会被原样留下 —— 页头于是写「0 个在招职位」，
    // 下面却列着两百条。
    company.value = { ...companyResult, id: tenantId, tenantId, postCount: companyResult.postCount || posts.value.length };
  } catch {
    failed.value = true;
  } finally {
    loading.value = false;
  }
}

/** 职位详情一律新开标签页，和职位列表页的「查看更多职位详情」保持一致 */
function openPost(id: number) {
  const { href } = router.resolve({
    name: 'PortalRecruitDetail',
    query: { id: String(id) },
  });
  window.open(href, '_blank', 'noopener,noreferrer');
}

function display(value?: null | number | string) {
  return value === null || value === undefined || String(value).trim() === '' ? '—' : value;
}

/* ── 岗位字段词表 ────────────────────────────────────────────────
   与职位页 index.vue 里的 jobNatureOptions / workTimeOptions /
   educationOptions 同源。那三个数组没有导出（`<script setup>` 不导出），
   这里只能再抄一份；改后端枚举时两处要一起改。

   salaryUnit === -1 表示面议，这条约定同样取自职位页。 */
function formatSalary(post: HrmPortalRecruitApi.Post) {
  if (post.salaryUnit === -1 || (post.minSalary ?? 0) < 0) return '面议';
  const { minSalary: min, maxSalary: max } = post;
  if (min == null && max == null) return '薪资面议';
  const k = (value?: number) => (value == null ? '' : `${value / 1000}K`);
  if (min != null && max != null) return `${k(min)}-${k(max)}`;
  return min != null ? `${k(min)}起` : `${k(max)}以内`;
}

function formatWorkTime(value?: number) {
  return ['经验不限', '1 年以下', '1-3 年', '3-5 年', '5-10 年', '10 年以上'][(value ?? 1) - 1] ?? '经验不限';
}

function formatEducation(value?: number) {
  return ['学历不限', '高中及以上', '大专及以上', '本科及以上', '硕士及以上', '博士'][(value ?? 1) - 1] ?? '学历不限';
}

function formatJobNature(value?: number) {
  return value ? (['', '全职', '实习', '兼职'][value] ?? '') : '';
}

onMounted(() => {
  syncFavorite();
  window.addEventListener('storage', handleFavoriteStorage);
  void ensureRecruitProfile();
  void load();
});

onUnmounted(() => {
  window.removeEventListener('storage', handleFavoriteStorage);
});

async function ensureRecruitProfile() {
  try {
    const resume = await getRecruitProfileResume();
    if (!isRecruitProfileComplete(resume)) profileSetupOpen.value = true;
  } catch (error) {
    console.warn('[PortalRecruitCompany] 检查求职资料失败', error);
  }
}

function handleProfileSetupCompleted() {
  profileSetupOpen.value = false;
}
</script>

<template>
  <main class="company-page">
    <RecruitTopbar active="companies" :themed="true" />
    <a-modal v-model:open="profileSetupOpen" :footer="null" :mask-closable="false" width="1180px" wrap-class-name="profile-setup-modal">
      <RecruitProfileSetup :embedded="true" @completed="handleProfileSetupCompleted" />
    </a-modal>

    <!-- 企业页头：封面带 + 半叠在带子下缘的 logo。
         招聘网站的企业主页都是这个形制（LinkedIn / Glassdoor / 拉勾 / 猎聘），
         进来先给一块品牌色的封面把企业「立」起来，而不是一行白底文字。 -->
    <header class="company-hero">
      <div class="hero-cover">
        <span class="hero-cover-kicker">企业主页</span>
        <!-- 在招数从页头右侧挪到封面带右上角：它是这块封面的「成绩」，和
             企业名分开读更像封面上的标牌；右下角留给收藏和 CTA 两个动作。 -->
        <div class="hero-cover-stat">
          <b>{{ posts.length }}</b>
          <small>个在招职位</small>
        </div>
      </div>
      <div class="hero-body">
        <span class="hero-logo">
          <img v-if="company?.logo" :src="company.logo" :alt="company.name" />
          <i v-else>{{ monogram }}</i>
        </span>
        <div class="hero-copy">
          <h1>{{ company?.name || '企业主页' }}</h1>
          <ul v-if="companyMeta.length" class="hero-meta">
            <li v-for="item in companyMeta" :key="item">{{ item }}</li>
          </ul>
        </div>
        <div class="hero-actions">
          <a-tooltip :title="favorited ? '取消收藏' : '收藏企业'">
            <button
              type="button"
              class="hero-favorite"
              :class="{ active: favorited }"
              :aria-pressed="favorited"
              :aria-label="favorited ? `取消收藏${company?.name ?? ''}` : `收藏${company?.name ?? ''}`"
              @click="toggleFavorite"
            >
              <IconifyIcon icon="lucide:star" />
            </button>
          </a-tooltip>
          <button v-if="posts.length" class="hero-cta" type="button" @click="scrollToPosts">
            查看全部职位<IconifyIcon icon="lucide:arrow-down" />
          </button>
        </div>
      </div>
    </header>

    <section v-if="loading" class="company-state">正在加载企业信息…</section>
    <section v-else-if="failed || !company" class="company-state">
      <strong>未找到该企业</strong>
      <a-button type="link" @click="router.push({ name: 'PortalRecruit', query: { section: 'companies' } })">浏览其他企业</a-button>
    </section>
    <template v-else>
      <p v-if="thinProfile" class="demo-notice">
        <IconifyIcon icon="lucide:info" />
        该企业的简介与工商档案尚未完善，当前仅展示公开招聘信息。
      </p>
      <div class="company-content">
        <div class="company-main-column">
          <section class="company-section">
            <div class="section-heading"><h2>公司简介</h2></div>
            <p class="summary" :class="{ empty: !company.summary }">{{ company.summary || '企业暂未提供简介。' }}</p>
          </section>

          <section v-if="products.length" class="company-section">
            <div class="section-heading"><h2>产品与服务</h2><span>{{ products.length }} 项</span></div>
            <div class="product-grid">
              <article v-for="(product, index) in products" :key="`${product.name}-${index}`" class="product-card">
                <img v-if="product.image" :src="product.image" :alt="product.name" loading="lazy" />
                <div class="product-copy"><h3>{{ product.name }}</h3><p>{{ product.intro || '—' }}</p></div>
              </article>
            </div>
          </section>

          <section v-if="album.length" class="company-section">
            <div class="section-heading"><h2>公司相册</h2><span>{{ album.length }} 张</span></div>
            <!-- 用 antd 的 Image.PreviewGroup 包住整格：点任意一张进预览，可在组内左右翻看其余几张 -->
            <a-image-preview-group>
              <div class="album-grid">
                <a-image
                  v-for="(image, index) in album"
                  :key="`${image}-${index}`"
                  :alt="`${company.name} 公司图片 ${index + 1}`"
                  :src="image"
                />
              </div>
            </a-image-preview-group>
          </section>

          <section v-if="company.address || company.longitude != null" class="company-section">
            <div class="section-heading"><h2>企业地址</h2></div>
            <LocationCard :address="company.address" :longitude="company.longitude" :latitude="company.latitude" />
          </section>

          <section ref="postsSectionRef" class="company-section company-posts">
            <div class="section-heading">
              <h2>公开招聘职位</h2>
              <span>{{ posts.length }} 个</span>
            </div>
            <div v-if="!posts.length" class="posts-empty">
              <IconifyIcon icon="lucide:briefcase-business" />
              <strong>该企业暂无公开职位</strong>
              <p>可以先浏览其他正在招聘的企业。</p>
            </div>
            <template v-else>
              <div class="post-grid">
                <button v-for="post in pagedPosts" :key="post.id" type="button" class="post-card" @click="openPost(post.id)">
                  <span class="post-head">
                    <strong class="post-name" :title="post.postName">{{ post.postName }}</strong>
                    <span class="post-salary">{{ formatSalary(post) }}</span>
                  </span>
                  <span class="post-meta">
                    <span><IconifyIcon icon="lucide:map-pin" />{{ post.areaName || '地点待确认' }}</span>
                    <span><IconifyIcon icon="lucide:clock" />{{ formatWorkTime(post.workTime) }}</span>
                    <span><IconifyIcon icon="lucide:graduation-cap" />{{ formatEducation(post.educationRequire) }}</span>
                  </span>
                  <span class="post-tags">
                    <span v-if="post.postTypeParentName" class="post-tag">{{ post.postTypeParentName }}</span>
                    <span v-if="formatJobNature(post.jobNature)" class="post-tag">{{ formatJobNature(post.jobNature) }}</span>
                    <span v-if="post.recruitNum" class="post-tag">招聘 {{ post.recruitNum }} 人</span>
                    <IconifyIcon class="post-go" icon="lucide:arrow-up-right" />
                  </span>
                </button>
              </div>
              <Pagination
                v-if="posts.length > postPageSize"
                :current="postPage"
                :page-size="postPageSize"
                :total="posts.length"
                :show-size-changer="false"
                class="company-pagination"
                @change="handlePostPageChange"
              />
            </template>
          </section>
        </div>

        <aside class="company-section company-facts">
          <div class="section-heading"><h2>工商信息</h2><span>{{ company.verified ? '已核实' : '信息待核实' }}</span></div>
          <dl>
            <div><dt>成立日期</dt><dd>{{ display(company.establishedDate) }}</dd></div>
            <div><dt>注册资本</dt><dd>{{ display(company.registeredCapital) }}</dd></div>
            <div><dt>信用代码</dt><dd>{{ display(company.creditCode) }}</dd></div>
            <div><dt>法定代表人</dt><dd>{{ display(company.legalPerson) }}</dd></div>
            <div><dt>企业性质</dt><dd>{{ display(company.nature) }}</dd></div>
            <div><dt>融资阶段</dt><dd>{{ display(company.financingStage) }}</dd></div>
            <div><dt>企业官网</dt><dd><a v-if="company.website" :href="company.website" target="_blank" rel="noopener noreferrer">访问官网</a><template v-else>—</template></dd></div>
          </dl>
          <p class="source-line">信息来源：{{ company.dataSource || '企业未提供' }} <a v-if="company.dataSourceUrl" :href="company.dataSourceUrl" target="_blank" rel="noopener noreferrer">查看来源</a></p>
        </aside>
      </div>
    </template>
  </main>
</template>

<style scoped>
/* 配色直接指向职位页那套 token。
   招聘门户的 token 定义在 index.vue 的 .recruit-page 上，而 scoped 样式带
   [data-v-xxx] 后缀、跨路由继承不到，所以这里必须自己再声明一份 ——
   与 detail.vue 同一处理。location-card 用的是 var(--job-*, 青色兜底)，
   这份声明一落地，它也会从青色切到蓝色。 */
.company-page {
  --job-ink: #1b2a3d;
  --job-muted: #5f7288;
  --job-blue: #2f6fb3;
  --job-blue-soft: #e8f1fb;
  --job-line: #d7e5f4;
  --job-green: #0c9653;
  --job-green-soft: #e6fcf1;
  --job-green-line: #bcebd2;
  /* 薪资用红：用户指定的例外，与职位页两处薪资同色 */
  --job-salary: #ff0000;
  --company-ease: cubic-bezier(0.22, 0.61, 0.36, 1);

  min-height: 100%;
  margin: -32px auto 0;
  padding: 0 0 34px;
  color: var(--job-ink);
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

/* ── 页头 ───────────────────────────────────────────────────── */
.company-hero {
  overflow: hidden;
  border: 1px solid var(--job-line);
  border-radius: 22px;
  background: #fff;
  box-shadow: 0 12px 32px rgb(27 42 61 / 6%);
}
/* 封面带：与首页 hero 同一套「渐变 + 网格」做法，只把色相从青橙换成职位页的蓝。
   网格两条压在最上层，靠渐变那层的半透明透出来。 */
.hero-cover {
  position: relative;
  height: 122px;
  background-image:
    repeating-linear-gradient(90deg, rgb(255 255 255 / 7%) 0 1px, transparent 1px 44px),
    repeating-linear-gradient(0deg, rgb(255 255 255 / 6%) 0 1px, transparent 1px 44px),
    radial-gradient(circle at 84% 6%, rgb(126 193 255 / 52%), transparent 52%),
    linear-gradient(118deg, #17395c 0%, var(--job-blue) 68%, #4d91d3 100%);
}
.hero-cover-kicker {
  position: absolute;
  top: 22px;
  left: 30px;
  color: rgb(255 255 255 / 82%);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.16em;
}
.hero-body {
  display: flex;
  align-items: flex-start;
  gap: 20px;
  padding: 0 30px 26px;
}
/* logo 半叠在封面下缘 —— 招聘网站企业页的标准做法，让主体和封面咬合上。
   但牌子不能做成死板的方框：这批企业的 logo 多是横版长条
   （实测天然尺寸 2362×945，2.5:1），塞进 104 方框里 contain 之后只剩
   86×34，整块白底看着像图没加载出来。改成「定高、宽度随长宽比自适应」：
   方版 logo 得到方牌，横版得到横牌，两种都有分量。 */
/* position: relative 是必须的，不是装饰。
   .hero-cover 为了给「企业主页」小字当定位参照，带了 position: relative；
   而定位元素一律画在非定位元素之上 —— 这里不给定位，封面带就会盖住
   牌子的上半部分（logo 半叠的效果直接变成「被切掉一半」）。
   两者 z-index 都是 auto，置为定位后按 DOM 顺序，牌子在封面之后 → 压在封面之上。 */
.hero-logo {
  position: relative;
  z-index: 1;
  display: flex;
  min-width: 104px;
  height: 104px;
  margin-top: -54px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  box-sizing: border-box;
  border: 4px solid #fff;
  border-radius: 22px;
  background: #fff;
  box-shadow: 0 10px 26px rgb(27 42 61 / 16%);
}
/* 高度按内容区给满（104 − 4×2 = 96），宽度再按各自长宽比撑开。
   给 68px 时方版 logo 只渲染成 68×68 塞在 104 的牌子里，四边都是空的。
   另需注意：这批 PNG 自带很重的透明边距（鸥鹏那张 2362×945 的图，
   实际墨迹只占画布 86%×58%），元素高给满也只能把可视线条撑到约一半 ——
   这是图源的问题，CSS 层面到顶了。 */
.hero-logo img {
  width: auto;
  height: 96px;
  max-width: 230px;
  margin: 0 14px;
  object-fit: contain;
}
/* 无 logo 时的首字块：撑满内容区（104 − 4×2 = 96），保持正方 */
.hero-logo i {
  display: grid;
  width: 96px;
  height: 96px;
  place-items: center;
  border-radius: 16px;
  background: linear-gradient(135deg, var(--job-blue), #4d91d3);
  color: #fff;
  font-size: 30px;
  font-style: normal;
  font-weight: 700;
  letter-spacing: 2px;
}
.hero-copy { min-width: 0; flex: 1; padding-top: 18px; }
.hero-copy h1 {
  margin: 0;
  color: var(--job-ink);
  font-size: 27px;
  font-weight: 700;
  line-height: 1.32;
  letter-spacing: -0.01em;
  overflow-wrap: anywhere;
}
/* 元信息行：招聘网站用「·」分隔的细体，比一排胶囊安静，也不会把
   只有一两项的公司撑成一片碎块 */
.hero-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0 0;
  padding: 0;
  color: var(--job-muted);
  font-size: 13px;
  list-style: none;
}
.hero-meta li { display: inline-flex; align-items: center; gap: 8px; }
.hero-meta li + li::before {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: #b8c6d6;
  content: '';
}
.hero-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-top: 24px;
  flex-shrink: 0;
}
/* 在招数落在深色封面上，配色得反过来：白色数字 + 半透明白标签。
   绿色在深蓝底上对比度不够，而且这一页的绿留给「动作」，数字是陈述不是动作。 */
/* 一行显示：数字和单位基线对齐。分两行时下面那行会把封面带的视觉重心压低，
   而封面带只有 122px，右上角本来就该是一枚扁的「标牌」而不是一摞字。 */
.hero-cover-stat {
  position: absolute;
  top: 24px;
  right: 30px;
  display: flex;
  align-items: baseline;
  gap: 6px;
  line-height: 1;
}
.hero-cover-stat b {
  color: #fff;
  font-size: 22px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.hero-cover-stat small { color: rgb(255 255 255 / 78%); font-size: 13px; }
.hero-cta {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 11px 20px;
  border: 0;
  border-radius: 11px;
  background: var(--job-blue);
  color: #fff;
  cursor: pointer;
  font: inherit;
  font-size: 14px;
  font-weight: 600;
  transition: background-color 160ms ease, transform 140ms ease;
}
/* 收藏星：和首页企业卡、职位页那几处同一颗 —— 裸星、无底板，
   未收藏灰色空心，收藏后填实变绿。填色只在 .active 里给，且必须打到
   <path> 上：它自带 fill="none" 表现属性，而表现属性赢过继承值，
   写在 <svg> 上不会传下去。 */
.hero-favorite {
  display: grid;
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  place-items: center;
  padding: 0;
  border: 0;
  background: transparent;
  color: #a3aebd;
  cursor: pointer;
  font-size: 19px;
  transition: color 160ms ease, transform 140ms ease;
}
.hero-favorite.active :deep(svg *) { fill: currentColor; }
.hero-favorite:hover { color: var(--job-green); transform: scale(1.14); }
.hero-favorite:active { transform: scale(0.92); }
.hero-favorite.active { color: var(--job-green); }
.hero-favorite:focus-visible { outline: 2px solid var(--job-green); outline-offset: 2px; border-radius: 6px; }

.hero-cta:hover { background: color-mix(in srgb, var(--job-blue) 84%, #000); }
.hero-cta:active { transform: scale(0.975); }
.hero-cta :deep(svg) { width: 15px; height: 15px; }

/* ── 状态提示 ───────────────────────────────────────────────── */
.demo-notice {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 16px 0 0;
  padding: 11px 15px;
  border: 1px solid var(--job-line);
  border-radius: 11px;
  background: var(--job-blue-soft);
  color: var(--job-muted);
  font-size: 13px;
}
.demo-notice :deep(svg) { width: 15px; height: 15px; flex-shrink: 0; color: var(--job-blue); }

/* ── 两栏骨架 ───────────────────────────────────────────────── */
.company-content {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  align-items: start;
  gap: 20px;
  margin-top: 20px;
}
.company-main-column { display: grid; gap: 18px; min-width: 0; }
.company-section {
  min-width: 0;
  padding: 22px 24px;
  border: 1px solid var(--job-line);
  border-radius: 16px;
  background: #fff;
}
.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  padding-left: 12px;
  border-left: 3px solid var(--job-blue);
  line-height: 1.2;
}
.section-heading h2 { margin: 0; color: var(--job-ink); font-size: 17px; font-weight: 700; }
.section-heading span { color: var(--job-muted); font-size: 12px; }
.summary { margin: 0; color: #3d5468; font-size: 14px; line-height: 1.95; white-space: pre-wrap; }
.summary.empty { color: var(--job-muted); }

/* ── 产品与服务 ─────────────────────────────────────────────── */
.product-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.product-card {
  display: flex;
  gap: 13px;
  padding: 14px;
  border: 1px solid var(--job-line);
  border-radius: 12px;
  background: #fcfdff;
  transition: border-color 160ms ease, background-color 160ms ease;
}
.product-card:hover { border-color: #b9d3ef; background: #f7fbff; }
.product-card img {
  width: 72px;
  height: 72px;
  flex-shrink: 0;
  border-radius: 10px;
  background: #eef3f9;
  object-fit: contain;
}
.product-copy { min-width: 0; }
.product-card h3 { margin: 0 0 6px; color: var(--job-ink); font-size: 15px; font-weight: 600; }
.product-card p { margin: 0; color: var(--job-muted); font-size: 13px; line-height: 1.7; }

/* ── 公司相册 ───────────────────────────────────────────────── */
.album-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
/*
 * a-image 把内容渲染进子组件，scoped 属性只落在它自己的根元素（.ant-image）上，
 * 加不到内部的 <img>。原来那几条 .album-grid img 因此一条都没生效 ——
 * 图片按原始比例铺排（高矮不一、直角、object-fit 退化成 fill）。
 * 三条都得走 :deep 才能穿透到子组件内部。
 */
.album-grid :deep(.ant-image) { display: block; width: 100%; }
.album-grid :deep(img) {
  width: 100%;
  height: 150px;
  border-radius: 11px;
  background: #eef3f9;
  object-fit: cover;
  transition: transform 200ms var(--company-ease);
}
.album-grid :deep(img:hover) { transform: scale(1.02); }
/* 预览遮罩默认是直角，盖在圆角图上会露出四个角 */
.album-grid :deep(.ant-image-mask) { border-radius: 11px; }

/* ── 工商信息（右栏） ───────────────────────────────────────── */
/* top 原来是 20px，而招聘门户顶栏实测 60px —— 吸顶后标题被压掉 40px。
   76 = 顶栏 60 + 16 呼吸。 */
.company-facts { position: sticky; top: 76px; }
.company-facts dl { display: grid; gap: 13px; margin: 0; }
.company-facts dl div { display: grid; grid-template-columns: 78px minmax(0, 1fr); gap: 8px; }
.company-facts dt { color: var(--job-muted); font-size: 13px; }
.company-facts dd { margin: 0; color: var(--job-ink); font-size: 13px; overflow-wrap: anywhere; }
.company-facts a, .source-line a { color: var(--job-blue); text-decoration: none; }
.company-facts a:hover, .source-line a:hover { text-decoration: underline; text-underline-offset: 3px; }
.source-line {
  margin: 18px 0 0;
  padding-top: 13px;
  border-top: 1px solid var(--job-line);
  color: var(--job-muted);
  font-size: 12px;
  line-height: 1.7;
}

/* ── 公开招聘职位 ───────────────────────────────────────────── */
/* 滚动吸附的落点：翻页后 scrollIntoView 不该把标题顶进导航条底下 */
.company-posts { scroll-margin-top: 76px; }
.post-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
/* 整张卡是一个 button：点哪儿都进职位详情（新标签页）。
   三行结构照搬职位页的卡片 —— 标题行（职位名 + 薪资）、元信息行（地点/经验/学历）、
   标签行（分类 / 性质 / 招聘人数）。 */
.post-card {
  display: grid;
  gap: 9px;
  width: 100%;
  padding: 15px 17px;
  border: 1px solid var(--job-line);
  border-radius: 13px;
  background: #fff;
  color: var(--job-ink);
  cursor: pointer;
  font: inherit;
  text-align: left;
  transition: border-color 160ms ease, box-shadow 160ms ease, transform 160ms ease;
}
.post-card:hover {
  border-color: var(--job-blue);
  box-shadow: 0 8px 22px rgb(47 111 179 / 10%);
  transform: translateY(-2px);
}
.post-head { display: flex; align-items: baseline; gap: 12px; }
.post-name {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  color: var(--job-ink);
  font-size: 16px;
  font-weight: 600;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 160ms ease;
}
.post-card:hover .post-name { color: var(--job-blue); }
.post-salary {
  flex-shrink: 0;
  color: var(--job-salary);
  font-size: 15px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.post-meta { display: flex; flex-wrap: wrap; gap: 6px 14px; color: var(--job-muted); font-size: 12px; }
.post-meta span { display: inline-flex; align-items: center; gap: 5px; min-width: 0; }
.post-meta :deep(svg) { width: 13px; height: 13px; flex-shrink: 0; color: #94a9c0; }
.post-tags { display: flex; align-items: center; flex-wrap: wrap; gap: 5px; }
/* 标签归「动作」层 —— 与职位页同色（绿色给岗位相关信息） */
.post-tag {
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--job-green-soft);
  color: var(--job-green);
  font-size: 11px;
  line-height: 18px;
}
.post-go {
  width: 15px;
  height: 15px;
  margin-left: auto;
  flex-shrink: 0;
  color: #c3cedd;
  transition: color 160ms ease, transform 160ms ease;
}
.post-card:hover .post-go { color: var(--job-green); transform: translate(2px, -2px); }

.posts-empty {
  display: grid;
  min-height: 190px;
  justify-items: center;
  align-content: center;
  gap: 8px;
  border: 1px dashed var(--job-line);
  border-radius: 13px;
  background: #fbfdff;
  text-align: center;
}
.posts-empty :deep(svg) { width: 32px; height: 32px; margin-bottom: 2px; color: #a9c2dc; }
.posts-empty strong { color: var(--job-ink); font-size: 15px; }
.posts-empty p { margin: 0; color: var(--job-muted); font-size: 13px; }

.company-pagination { display: flex; justify-content: center; flex-wrap: wrap; gap: 4px; margin-top: 22px; }
.company-pagination :deep(.ant-pagination-item),
.company-pagination :deep(.ant-pagination-item-link) { border-color: var(--job-line); background: #fff; }
.company-pagination :deep(.ant-pagination-item a),
.company-pagination :deep(.ant-pagination-item-link) { color: var(--job-muted); }
.company-pagination :deep(.ant-pagination-item-active) { border-color: var(--job-blue); background: var(--job-blue); }
.company-pagination :deep(.ant-pagination-item-active a) { color: #fff; }
.company-pagination :deep(.ant-pagination-disabled .ant-pagination-item-link) { color: #b6c4d4; }

/* ── 加载 / 未找到 ──────────────────────────────────────────── */
.company-state {
  display: grid;
  min-height: 260px;
  place-items: center;
  align-content: center;
  gap: 10px;
  margin-top: 20px;
  border: 1px solid var(--job-line);
  border-radius: 16px;
  background: #fff;
  color: var(--job-muted);
}

.company-page button:focus-visible,
.company-page a:focus-visible { outline: 2px solid var(--job-blue); outline-offset: 2px; }

/* ── 窄屏 ───────────────────────────────────────────────────── */
@media (max-width: 1180px) {
  .company-content { grid-template-columns: minmax(0, 1fr) 268px; }
  .product-grid { grid-template-columns: minmax(0, 1fr); }
}
@media (max-width: 900px) {
  .company-content { grid-template-columns: minmax(0, 1fr); }
  /* 单栏时工商信息排到最前 —— 它是最短的「事实卡」，比一屏长的职位列表先看到更有用 */
  .company-facts { position: static; grid-row: 1; }
  .hero-body { flex-wrap: wrap; }
  .hero-actions { width: 100%; padding-top: 4px; justify-content: flex-start; }
  .post-grid { grid-template-columns: minmax(0, 1fr); }
}
@media (max-width: 600px) {
  .company-page { padding-bottom: 20px; }
  .hero-cover { height: 92px; }
  .hero-cover-kicker { top: 16px; left: 16px; }
  .hero-cover-stat { top: 16px; right: 16px; gap: 4px; }
  .hero-cover-stat b { font-size: 17px; }
  .hero-cover-stat small { font-size: 11px; }
  .hero-body { gap: 13px; padding: 0 16px 18px; }
  .hero-logo { min-width: 72px; height: 72px; margin-top: -36px; border-width: 3px; border-radius: 16px; }
  .hero-logo img { height: 66px; max-width: 150px; margin: 0 10px; }
  .hero-logo i { width: 66px; height: 66px; border-radius: 12px; font-size: 22px; }
  .hero-copy { padding-top: 12px; }
  .hero-copy h1 { font-size: 20px; }
  .company-section { padding: 17px 16px; }
  .album-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (prefers-reduced-motion: reduce) {
  .post-card, .post-card:hover, .post-name, .post-go, .album-grid img, .hero-cta { transition: none; transform: none; }
}

/* 顶栏通栏：让出 portal-main 的 1180 限制，内容再各自对齐回 1180 基线。
   与 detail.vue 同一手法 —— 原来靠 width:100vw 反算，会多出一条横向滚动条。 */
.company-page > .company-hero,
.company-page > .company-content,
.company-page > .company-state,
.company-page > .demo-notice {
  width: min(1180px, calc(100% - 48px));
  margin-right: auto;
  margin-left: auto;
}
.company-page > .company-hero { margin-top: 0; }
.company-page > .company-state { margin-top: 20px; }
</style>

<style>
/* 非 scoped：这两条要打在框架的 .portal-main 上。
   首页靠框架给 .portal-main 加的 .portal-main--recruit-home 拿到 width:100%，
   企业页没有那个类，.portal-main--recruit 本身被限在 1180。
   用 :has() 认领「当前是企业页」再放开，和 detail.vue 的处理保持一致。 */
.portal-main--recruit:has(.company-page) {
  width: 100%;
}
.company-page > .recruit-topbar {
  width: 100%;
  margin-left: 0;
}
</style>
