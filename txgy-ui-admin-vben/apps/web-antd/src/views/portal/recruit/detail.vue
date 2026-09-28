<script lang="ts" setup>
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

import { Skeleton } from 'ant-design-vue';

import { getTenantSimpleList } from '#/api/core/auth';
import {
  getPortalRecruitPost,
  type HrmPortalRecruitApi,
} from '#/api/hrm/portal/recruit';
import {
  getRecruitProfileResume,
  isRecruitProfileComplete,
} from '#/api/hrm/portal/resume';

import RecruitApplyModal from './components/apply-modal.vue';
import LocationCard from './components/location-card.vue';
import RecruitTopbar from './components/recruit-topbar.vue';
import RecruitProfileSetup from './profile-setup.vue';

const route = useRoute();
const router = useRouter();
const loading = ref(true);
const post = ref<HrmPortalRecruitApi.Post>();
const loadError = ref(false);
const applyModalOpen = ref(false);
const favorite = ref(false);
const profileSetupOpen = ref(false);

function getFavoriteIds() {
  try {
    const stored = JSON.parse(localStorage.getItem('portal-recruit-favorites') || '[]');
    return Array.isArray(stored) ? stored.filter((id): id is number => typeof id === 'number') : [];
  } catch {
    return [];
  }
}

function toggleFavorite() {
  if (!post.value) return;
  const favoriteIds = getFavoriteIds();
  favorite.value = !favorite.value;
  const next = favorite.value
    ? [...new Set([...favoriteIds, post.value.id])]
    : favoriteIds.filter((id) => id !== post.value?.id);
  localStorage.setItem('portal-recruit-favorites', JSON.stringify(next));
}

function openApplication() {
  applyModalOpen.value = true;
}

function openChat() {
  if (!post.value?.id) return;
  void router.push({
    name: 'PortalRecruit',
    query: { section: 'messages', postId: String(post.value.id) },
  });
}
const tenantNames = ref<Record<number, string>>({});

/**
 * 描述为空时返回空数组，由模板渲染空态块。
 * 以前这里往数组里塞一句「暂无职位描述」当正文，结果这一节和真有内容时
 * 长得一模一样，看不出是「没填」还是「就写了一行」。
 */
const descriptionLines = computed(() => {
  const raw = post.value?.description?.trim();
  if (!raw) return [];
  return raw
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
});

/**
 * 加载失败的图片地址。企业 logo 走本站接口（相对路径），招聘方头像是外链，
 * 实测全站有头像的岗位用的都是一个测试域名、取不到；img 失败时浏览器会留
 * 一个破图图标加 alt 文字，比不显示还难看，所以记下失败过的地址退回文字。
 */
const brokenImages = ref<Set<string>>(new Set());

function markImageBroken(src?: string) {
  if (!src || brokenImages.value.has(src)) return;
  brokenImages.value = new Set(brokenImages.value).add(src);
}

function canShowImage(src?: string) {
  return Boolean(src) && !brokenImages.value.has(src!);
}

/** 企业副标题：行业 / 规模 / 融资阶段，三者全空才落占位 */
const companyMeta = computed(() => {
  const current = post.value;
  if (!current) return '';
  return (
    [current.companyIndustry, current.companyScale, current.companyFinancingStage]
      .filter(Boolean)
      .join(' · ') || '企业信息待完善'
  );
});

/** 招聘方。ownerUserName 是唯一全量有值的「人」字段，头像是少数岗位才有 */
const recruiter = computed(() => {
  const current = post.value;
  if (!current?.ownerUserName) return undefined;
  return { name: current.ownerUserName, avatar: current.ownerUserAvatar || '' };
});

/**
 * Hero 紧挨职位名的那条条件串 —— 求职者扫读这一页的主路径。
 * 只放最常用来筛的字段；工作性质、招聘人数这些留给下面的「职位信息」表，
 * 两处不重复。
 */
const heroMeta = computed(() => {
  const current = post.value;
  if (!current) return [];
  return [
    formatLocationCompact(current),
    formatWorkTime(current.workTime),
    formatEducation(current.educationRequire),
  ].filter(Boolean);
});

/**
 * 「职位信息」表。hero 已经有地点 / 经验 / 学历，企业卡已经有招聘公司，
 * 这里只列它们没覆盖的字段，避免同一屏里把同样的值列两遍。
 */
const jobInfoItems = computed(() => {
  const current = post.value;
  if (!current) return [];
  return [
    { label: '工作性质', value: formatJobNature(current.jobNature) },
    {
      label: '招聘人数',
      value: current.recruitNum ? `${current.recruitNum} 人` : '若干',
    },
    {
      label: '职位类型',
      value: current.postTypeParentName || current.postTypeName || '',
    },
    { label: '招聘部门', value: current.deptName || '' },
    {
      label: '最晚入职',
      value: formatLatestEntryTime(current.latestEntryTime),
    },
  ].filter((item) => Boolean(item.value));
});

function formatLocation(current: HrmPortalRecruitApi.Post) {
  return (
    current.workAddress ||
    current.areaName ||
    (current.areaId ? `地区 ${current.areaId}` : '工作地点待确认')
  );
}

/**
 * Hero 条件串专用的紧凑地点。省市区全称（陕西省 西安市 雁塔区）在职位名下面
 * 会占掉一整行，主流招聘站这里都用「西安·雁塔区」两段式。
 * 直辖市在 areaName 里会重复一段（北京市 北京市 东城区），先去重再截。
 * 与 index.vue 右栏详情同一套口径，两处保持一致。
 */
function formatLocationCompact(current?: HrmPortalRecruitApi.Post) {
  if (!current) return '';
  const parts = (current.areaName || '')
    .split(/\s+/)
    .filter(Boolean)
    .filter((part, index, list) => part !== list[index - 1]);
  if (!parts.length) {
    return current.areaId ? `地区 ${current.areaId}` : '工作地点待确认';
  }
  if (parts.length === 1) return parts[0]!.replace(/[省市]$/, '');
  const district = parts[parts.length - 1]!;
  const city = parts[parts.length - 2]!.replace(/[省市]$/, '');
  return `${city}·${district}`;
}

/** 企业主页和企业详情页一样新开标签页，所以那边也不需要返回键 */
function openCompanyProfile(current: HrmPortalRecruitApi.Post) {
  if (current.tenantId == null) return;
  const { href } = router.resolve({
    name: 'PortalRecruitCompany',
    query: { tenantId: String(current.tenantId) },
  });
  window.open(href, '_blank', 'noopener,noreferrer');
}

function formatSalary(current: HrmPortalRecruitApi.Post) {
  if (current.salaryUnit === -1 || (current.minSalary ?? 0) < 0) return '面议';
  const min = current.minSalary;
  const max = current.maxSalary;
  if (min == null && max == null) return '薪资面议';
  const format = (value?: number) => (value == null ? '' : `${value / 1000}K`);
  if (min != null && max != null) return `${format(min)}-${format(max)}`;
  return min != null ? `${format(min)}起` : `${format(max)}以内`;
}

/**
 * 最晚入职时间。后端 LocalDateTime 序列化后是毫秒时间戳（数字），
 * 不能直接当字符串处理，否则 .slice 会抛 TypeError 导致整页渲染失败。
 */
function formatLatestEntryTime(value?: number | string) {
  if (value === null || value === undefined || value === '') return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value).slice(0, 10);
  const pad = (part: number) => String(part).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
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
    if (post.value) favorite.value = getFavoriteIds().includes(post.value.id);
  } catch (error) {
    loadError.value = true;
    console.warn('[PortalRecruitDetail] 加载岗位详情失败', error);
  } finally {
    loading.value = false;
  }
}

/**
 * 回职位列表。
 *
 * 原先只推 { name: 'PortalRecruit' }，而 index.vue 的 requestedSection 在没有
 * section 参数时兜底成 'home' —— 也就是说按钮写着「返回岗位机会」，实际落的是
 * 首页。这里补上 section: 'jobs'，让去处和文案对得上。
 */
function backToRecruit() {
  void router.push({ name: 'PortalRecruit', query: { section: 'jobs' } });
}

onMounted(async () => {
  releaseStaleScrollLock();
  void ensureRecruitProfile();
  void loadTenantNames();
  await loadPost();
  if (route.query.apply === '1' && post.value) {
    applyModalOpen.value = true;
  }
});

onUnmounted(() => {
  releaseStaleScrollLock(true);
});

async function ensureRecruitProfile() {
  try {
    const resume = await getRecruitProfileResume();
    if (!isRecruitProfileComplete(resume)) profileSetupOpen.value = true;
  } catch (error) {
    console.warn('[PortalRecruitDetail] 检查求职资料失败', error);
  }
}

function handleProfileSetupCompleted() {
  profileSetupOpen.value = false;
}

function releaseStaleScrollLock(force = false) {
  if (profileSetupOpen.value && !force) return;
  document.body.style.removeProperty('overflow');
  document.body.style.removeProperty('overflow-y');
  document.body.style.removeProperty('padding-right');
}
</script>

<template>
  <div class="portal-page recruit-detail-page">
    <RecruitTopbar active="jobs" :themed="true" />
    <a-modal v-model:open="profileSetupOpen" :footer="null" :mask-closable="false" width="1180px" wrap-class-name="profile-setup-modal">
      <RecruitProfileSetup :embedded="true" @completed="handleProfileSetupCompleted" />
    </a-modal>
    <section v-if="loading" class="detail-loading">
      <Skeleton active :paragraph="{ rows: 10 }" />
    </section>

    <a-empty
      v-else-if="loadError || !post"
      class="detail-empty"
      description="岗位不存在或已下架"
    >
      <template #extra>
        <a-button type="primary" @click="backToRecruit">浏览其他职位</a-button>
      </template>
    </a-empty>

    <template v-else>
      <header class="detail-hero">
        <div class="detail-hero-main">
          <h1>{{ post.postName }}</h1>
          <ul class="detail-meta">
            <li v-for="item in heroMeta" :key="item">{{ item }}</li>
          </ul>
        </div>
        <div class="detail-hero-side">
          <span class="detail-salary">{{ formatSalary(post) }}</span>
          <div class="detail-actions">
            <a-tooltip :title="favorite ? '取消收藏' : '收藏职位'">
              <button
                class="detail-favorite"
                :class="{ active: favorite }"
                type="button"
                :aria-pressed="favorite"
                @click="toggleFavorite"
              >
                <IconifyIcon icon="lucide:star" />
                {{ favorite ? '已收藏' : '收藏' }}
              </button>
            </a-tooltip>
            <button class="detail-chat" type="button" @click="openChat">
              <IconifyIcon icon="lucide:message-circle" /> 立即沟通
            </button>
          </div>
        </div>
      </header>

      <main class="detail-content">
        <article class="detail-main">
          <section class="detail-section">
            <h2>职位描述</h2>
            <div v-if="descriptionLines.length" class="detail-copy">
              <p v-for="(line, index) in descriptionLines" :key="index">
                {{ line }}
              </p>
            </div>
            <!-- 相当一部分岗位没填描述，这里按常态做：给一块有边界的底，
                 而不是吊一行灰字，让这一节仍然读得出结构。 -->
            <div v-else class="detail-blank">
              <IconifyIcon icon="lucide:file-text" />
              <p>
                <strong>招聘方未填写职位描述</strong>
                <span>岗位职责、班组安排、食宿与待遇等细节，可在沟通中直接向招聘方了解。</span>
              </p>
            </div>
          </section>

          <section class="detail-section">
            <h2>职位信息</h2>
            <dl class="job-info-grid">
              <div v-for="item in jobInfoItems" :key="item.label">
                <dt>{{ item.label }}</dt>
                <dd>{{ item.value }}</dd>
              </div>
            </dl>
          </section>

          <section class="detail-section">
            <h2>工作地址</h2>
            <p class="location-note">以下为企业办公地址，具体工作地点请与招聘方确认。</p>
            <LocationCard
              :address="formatLocation(post)"
              :longitude="post.longitude"
              :latitude="post.latitude"
            />
          </section>
        </article>

        <!-- 右栏。企业卡是每个招聘站详情页都有的东西，而这一页原来的企业名
             只以「职位信息」表里一行的形式出现。粘住跟随，滚到哪都能跳企业主页。 -->
        <aside class="detail-rail">
          <section class="employer-card">
            <div class="employer-head">
              <span class="employer-logo">
                <img
                  v-if="canShowImage(post.companyLogo)"
                  :src="post.companyLogo"
                  :alt="`${getCompanyName(post)}企业 Logo`"
                  @error="markImageBroken(post.companyLogo)"
                />
                <template v-else>{{ getCompanyName(post).slice(0, 1) }}</template>
              </span>
              <span class="employer-text">
                <strong>{{ getCompanyName(post) }}</strong>
                <small>{{ companyMeta }}</small>
              </span>
            </div>

            <div v-if="recruiter" class="employer-recruiter">
              <span class="employer-recruiter-avatar">
                <img
                  v-if="canShowImage(recruiter.avatar)"
                  :src="recruiter.avatar"
                  :alt="recruiter.name"
                  @error="markImageBroken(recruiter.avatar)"
                />
                <template v-else>{{ recruiter.name.slice(0, 1) }}</template>
              </span>
              <span class="employer-recruiter-text">
                <strong>{{ recruiter.name }}</strong>
                <small>招聘方</small>
              </span>
            </div>

            <button class="employer-link" type="button" @click="openCompanyProfile(post)">
              查看企业主页
              <IconifyIcon icon="lucide:arrow-right" />
            </button>
          </section>
        </aside>
      </main>
    </template>

    <RecruitApplyModal v-model:open="applyModalOpen" :post="post" />
  </div>
</template>

<style scoped>
/* 页面骨架。原先删掉返回按钮后忘了这条还在 —— 它只定义在 index.vue 的
   scoped 块里，这边不写就没有 grid、也没有间距，顶栏会和 Hero 直接贴死。 */
.portal-page {
  display: grid;
  gap: 20px;
}

/*
 * detail.vue 是独立路由（recruit/detail），不在 index.vue 的 .recruit-page 里，
 * 继承不到那边 scoped 定义的 --job-* 变量，只能在这里再声明一遍。
 * 值与 index.vue 的 .recruit-page 逐字一致，改那边时记得同步这里。
 * 分工不变：淡蓝承载结构与信息，淡绿只承载动作，红只给薪资。
 */
.recruit-detail-page {
  --job-ink: #1b2a3d;
  --job-muted: #5f7288;
  --job-blue: #2f6fb3;
  --job-blue-soft: #e8f1fb;
  --job-line: #d7e5f4;
  --job-green: #0c9653;
  --job-green-soft: #e6fcf1;
  --job-green-line: #bcebd2;
  --job-salary: #ff0000;

  /*
   * 根不自己收窄到 1180，改由下面的子元素各自收窄。
   *
   * 顶栏（recruit-topbar）本身的规则是 width: 100% —— 它靠铺满父级来通栏出血。
   * 一旦父级只有 1180，就得写 width: 100vw; margin-left: calc((100% - 100vw)/2)
   * 去反算，而 100vw 把滚动条宽度也算进去，于是整页常年挂着一条横向滚动条；
   * 再用 overflow 裁掉，又会连顶栏一起裁窄（clip 同样生效）。
   * 让根铺满，顶栏用原本的 100% 就自然通栏，和职位列表页走的是同一条路。
   */
  width: 100%;
  margin: 0;
  margin-top: -32px;
  box-sizing: border-box;
  color: var(--job-ink);
}

/* 顶栏铺满整行（框架那边已由非 scoped 块放开 .portal-main--recruit 的宽度）。
   它自身的 padding max(24px, calc((100vw - 1180px)/2)) 正好把内容对齐到 1180 栏，
   和首页同一条公式、同一个位置，所以这里什么都不用覆盖。 */
.recruit-detail-page > .recruit-topbar {
  width: 100%;
  margin-left: 0;
}

/* 除顶栏外的直接子元素统一收到 1180 居中，窄屏留 24px 边距 */
.recruit-detail-page > .detail-loading,
.recruit-detail-page > .detail-empty,
.recruit-detail-page > .detail-hero,
.recruit-detail-page > .detail-content {
  width: min(1180px, calc(100% - 48px));
  margin-inline: auto;
}

/*
 * 页面顶端不再有返回按钮：详情页一律从新标签页打开，关掉即可，
 * 原来的列表页还停在原位。站内想回列表走顶栏的「职位」，那条是
 * router.push({ section: 'jobs' })，见 recruit-topbar.vue 的 go()。
 */

/* ---------------- 返回 ---------------- */

/* ---------------- 加载 / 空态 ---------------- */

.detail-loading,
.detail-empty {
  padding: 56px 32px;
  border: 1px solid var(--job-line);
  border-radius: 16px;
  background: #fff;
}

/* ---------------- Hero ---------------- */

.detail-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 32px;
  padding: 30px 34px;
  border: 1px solid var(--job-line);
  border-radius: 16px;
  background: linear-gradient(120deg, #fff 0%, #fafdff 62%, var(--job-blue-soft) 100%);
}

.detail-hero-main {
  min-width: 0;
}

.detail-hero h1 {
  margin: 0;
  color: var(--job-ink);
  font-size: 30px;
  font-weight: 600;
  line-height: 1.3;
}

/* 条件串用竖线分隔，和右栏详情、职位列表保持同一套读法 */
.detail-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 14px;
  margin: 14px 0 0;
  padding: 0;
  list-style: none;
  color: var(--job-muted);
  font-size: 14px;
}

.detail-meta li {
  position: relative;
  padding-left: 15px;
}

.detail-meta li::before {
  position: absolute;
  top: 50%;
  left: 0;
  width: 1px;
  height: 11px;
  background: var(--job-line);
  content: '';
  transform: translateY(-50%);
}

.detail-meta li:first-child {
  padding-left: 0;
}

.detail-meta li:first-child::before {
  display: none;
}

.detail-hero-side {
  display: grid;
  flex: 0 0 auto;
  justify-items: end;
  gap: 12px;
}

.detail-salary {
  color: var(--job-salary);
  font-size: 22px;
  font-weight: 700;
}

.detail-actions {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

/* 收藏是次级动作，走描边蓝；绿在这一页只留给「立即沟通」 */
.detail-favorite {
  display: inline-flex;
  min-width: 124px;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 40px;
  padding: 0 20px;
  border: 1px solid var(--job-line);
  border-radius: 999px;
  background: #fff;
  color: var(--job-blue);
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: border-color 140ms ease, background-color 140ms ease;
}

.detail-favorite:hover {
  border-color: var(--job-blue);
  background: var(--job-blue-soft);
}

.detail-favorite.active {
  border-color: var(--job-green-line);
  background: var(--job-green-soft);
  color: var(--job-green);
}
/* 星形只在已收藏时填实 —— 未收藏不写 fill，<path> 上那条 fill="none"
   表现属性就恢复作用，保持空心轮廓。填色必须打到 path 上：表现属性虽然
   输给 CSS 规则，却赢过继承值，写 svg 上无效。 */
.detail-favorite.active :deep(svg *) { fill: currentColor; }

.detail-favorite:focus-visible,
.detail-chat:focus-visible,
.employer-link:focus-visible {
  outline: 2px solid var(--job-blue);
  outline-offset: 2px;
}

.detail-chat {
  display: inline-flex;
  min-width: 132px;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 40px;
  padding: 0 22px;
  border: 1px solid var(--job-green);
  border-radius: 999px;
  background: var(--job-green);
  box-shadow: 0 6px 14px rgb(12 150 83 / 18%);
  color: #fff;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  transition: background-color 140ms ease, box-shadow 140ms ease;
}

.detail-chat:hover {
  background: color-mix(in srgb, var(--job-green) 82%, #000);
  box-shadow: 0 8px 18px rgb(12 150 83 / 26%);
}

/* ---------------- 两栏 ---------------- */

/* 主内容 + 右栏。单栏时右侧一千多像素宽全空着，而企业信息只能缩在
   「职位信息」表里当一行 —— 主流招聘详情页都是这个两栏结构。 */
.detail-content {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  align-items: start;
  gap: 20px;
}

.detail-main {
  min-width: 0;
  padding: 28px 34px 32px;
  border: 1px solid var(--job-line);
  border-radius: 16px;
  background: #fff;
}

.detail-section + .detail-section {
  margin-top: 30px;
  padding-top: 26px;
  border-top: 1px solid var(--job-line);
}

/* 章节标题带一道左侧色条：这一页要滚好几屏，只把字加大是不够的。
   色条是结构不是动作，走蓝。 */
.detail-section h2 {
  display: flex;
  align-items: center;
  gap: 9px;
  margin: 0 0 16px;
  color: var(--job-ink);
  font-size: 17px;
  font-weight: 600;
}

.detail-section h2::before {
  flex: 0 0 auto;
  width: 3px;
  height: 16px;
  border-radius: 999px;
  background: var(--job-blue);
  content: '';
}

.detail-copy {
  color: var(--job-muted);
  font-size: 15px;
  line-height: 1.9;
}

.detail-copy p {
  margin: 0 0 8px;
}

.detail-copy p:last-child {
  margin-bottom: 0;
}

/* 描述空态：虚线蓝底 + 图标，和右栏详情同一套做法 */
.detail-blank {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px 18px;
  border: 1px dashed var(--job-line);
  border-radius: 12px;
  background: var(--job-blue-soft);
}

.detail-blank :deep(svg) {
  flex: 0 0 auto;
  margin-top: 2px;
  color: var(--job-blue);
  font-size: 18px;
}

.detail-blank p {
  display: grid;
  gap: 5px;
  margin: 0;
}

.detail-blank strong {
  color: var(--job-ink);
  font-size: 14px;
}

.detail-blank span {
  color: var(--job-muted);
  font-size: 13px;
  line-height: 1.7;
}

.job-info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 32px;
  margin: 0;
}

.job-info-grid > div {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 12px;
  align-items: baseline;
  padding: 11px 0;
  border-bottom: 1px dashed var(--job-line);
}

.job-info-grid dt {
  color: var(--job-muted);
  font-size: 13.5px;
}

.job-info-grid dd {
  min-width: 0;
  margin: 0;
  color: var(--job-ink);
  font-size: 13.5px;
  font-weight: 500;
  overflow-wrap: anywhere;
}

.location-note {
  margin: 0 0 14px;
  color: var(--job-muted);
  font-size: 13px;
  line-height: 1.7;
}

/* ---------------- 右栏：企业卡 ---------------- */

.detail-rail {
  position: sticky;
  /* 让开吸顶导航条 */
  top: 76px;
  display: grid;
  gap: 14px;
}

.employer-card {
  display: grid;
  gap: 14px;
  padding: 18px;
  border: 1px solid var(--job-line);
  border-radius: 16px;
  background: #fff;
}

.employer-head {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
}

.employer-logo,
.employer-recruiter-avatar {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 12px;
  background: var(--job-blue-soft);
  overflow: hidden;
  color: var(--job-blue);
  font-weight: 700;
}

.employer-logo {
  width: 46px;
  height: 46px;
  border: 1px solid var(--job-line);
  background: #fff;
  /* contain 而不是 cover：企业 logo 多是横版带字（实测 2362×945），
     cover 会把两头裁掉，只剩中间一截认不出是谁家 */
  object-fit: contain;
  padding: 4px;
  font-size: 18px;
}

.employer-logo img,
.employer-recruiter-avatar img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.employer-recruiter-avatar {
  width: 38px;
  height: 38px;
  font-size: 15px;
}

.employer-text,
.employer-recruiter-text {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.employer-text strong,
.employer-recruiter-text strong {
  overflow: hidden;
  color: var(--job-ink);
  font-size: 15px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.employer-recruiter-text strong {
  font-size: 14px;
}

.employer-text small,
.employer-recruiter-text small {
  overflow: hidden;
  color: var(--job-muted);
  font-size: 12.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 招聘方和企业在同一张卡里：都是「这活儿是谁在招」的一部分。
   用虚线分隔而不是再加一张卡 —— 窄栏里叠两张卡会显得很碎。 */
.employer-recruiter {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
  padding-top: 14px;
  border-top: 1px dashed var(--job-line);
}

.employer-link {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  padding: 10px 16px;
  border: 1px solid var(--job-line);
  border-radius: 10px;
  background: #fff;
  color: var(--job-blue);
  cursor: pointer;
  font-size: 13.5px;
  font-weight: 600;
  transition: border-color 140ms ease, background-color 140ms ease;
}

.employer-link:hover {
  border-color: var(--job-blue);
  background: var(--job-blue-soft);
}

/* ---------------- 窄屏 ---------------- */

@media (max-width: 1000px) {
  .detail-content {
    grid-template-columns: minmax(0, 1fr);
  }

  /* 单栏时企业卡排到正文之后，不再需要吸顶 */
  .detail-rail {
    position: static;
  }
}

@media (max-width: 900px) {
  .detail-hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .detail-hero-side {
    width: 100%;
    justify-items: start;
  }

  .detail-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 640px) {
  .detail-hero {
    padding: 22px 20px;
  }

  .detail-hero h1 {
    font-size: 25px;
  }

  .detail-main {
    padding: 22px 20px 26px;
  }

  .detail-actions {
    width: 100%;
  }

  .detail-actions button {
    flex: 1 1 auto;
  }

  .job-info-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .job-info-grid > div {
    grid-template-columns: 84px minmax(0, 1fr);
  }
}

@media (prefers-reduced-motion: reduce) {
  .recruit-detail-page * {
    transition-duration: 1ms !important;
  }
}
</style>

<!-- 非 scoped：这两条要打在框架的 .portal-main 上 -->
<style>
/*
 * 让门户内容区在职位详情页也铺满，和首页统一。
 *
 * 首页靠框架给 .portal-main 加的 .portal-main--recruit-home 拿到 width: 100%，
 * 于是顶栏通栏、内容再靠 padding 对齐到 1180 栏。详情页没有这个类，.portal-main
 * 被框架的 width: min(1180px, calc(100% - 48px)) 锁在 1180，顶栏也就跟着只有 1180。
 *
 * 不能改框架文件，所以在页面自己的非 scoped 块里补一条 —— 和 index.vue 里
 * :has(.recruit-page--jobs) 那几条是同一个做法。:has() 把作用域锁死在
 * 「页面里存在 .recruit-detail-page 时」，其它分区不受影响；
 * 特异性 (0,2,0) 高于框架原规则，不需要 !important。
 */
.portal-main--recruit:has(.recruit-detail-page) {
  width: 100%;
}
</style>
