<script lang="ts" setup>
import type { Directive } from 'vue';
import type { PortalHighlights, PortalHomeSections, PortalLibraryItem } from '#/api/cms/portal';
import type { PreviewSource, SectionKey } from './portal-content-meta';

import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

import { getPortalHighlights, getPortalHomeSections, getPortalLibrary } from '#/api/cms/portal';

import {
  RELATED_TITLE,
  SECTION_META,
  buildDescription,
  columnOf,
  detailQuery,
  sectionItems,
} from './portal-content-meta';

/**
 * part 控制本组件渲染哪些区块（首页据此调整模块顺序）：
 * - all：全部（默认）
 * - news：只渲染「公司新闻 / 通知公告 / 政策动态」区块
 * - rest：渲染推荐阅读 / 产品中心 / 关于天信 / 技术资料
 */
const props = withDefaults(defineProps<{ part?: 'all' | 'news' | 'rest' }>(), {
  part: 'all',
});
const showNews = computed(() => props.part !== 'rest');
const showRest = computed(() => props.part !== 'news');

/** 门户 CMS 系统页内容区块：两栏并排 + 每个栏目可进入列表页 */
const router = useRouter();

const highlights = ref<PortalHighlights>({ notices: [], policies: [] });
const sections = ref<PortalHomeSections>({
  about: [],
  banners: [],
  news: [],
  notices: [],
  products: [],
  techDocs: [],
});

const laws = ref<PortalLibraryItem[]>([]);
const policies = ref<PortalLibraryItem[]>([]);

const previewItem = ref<null | PreviewSource>(null);
const previewSection = ref<SectionKey>('products');

const previewData = computed(() => {
  const item = previewItem.value;
  if (!item) {
    return null;
  }
  const title = item.title || item.name || '内容详情';
  return {
    category: item.category,
    column: columnOf(item),
    description: buildDescription(item, title),
    imageCount: item.imageCount ?? 1,
    tags: item.tags,
    title,
    url: item.url,
  };
});

const relatedItems = computed(() => {
  const item = previewItem.value;
  if (!item) {
    return [];
  }
  return sectionItems(sections.value, highlights.value, previewSection.value)
    .filter((entry) => entry !== item)
    .slice(0, 6);
});

/** 政策动态：优先用门户 highlights，为空时回退到知识库政策列表 */
const policyFeed = computed(() => {
  if (highlights.value.policies.length > 0) {
    return highlights.value.policies;
  }
  return policies.value.map((item) => ({
    id: 0,
    title: item.title,
    department: item.issuer,
    date: item.date,
    contentType: '金融政策',
  }));
});

const newsTotal = computed(
  () =>
    sections.value.news.length +
    sections.value.notices.length +
    highlights.value.notices.length +
    highlights.value.policies.length,
);

function meta(key: SectionKey) {
  return SECTION_META[key];
}

function openPreview(item: PreviewSource, key: SectionKey) {
  previewItem.value = item;
  previewSection.value = key;
}

function closePreview() {
  previewItem.value = null;
}

function goDetail() {
  if (!previewItem.value) {
    return;
  }
  router.push({
    path: '/portal/content/detail',
    query: detailQuery(previewItem.value, previewSection.value),
  });
}

function goList(key: SectionKey) {
  router.push({ path: '/portal/content/list', query: { section: key } });
}

/** 法规库 / 政策库的「查看全部」：回到上方智能检索区 */
function scrollToSearch() {
  document.querySelector('.cms-modules')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

/** 把知识库条目适配成弹层可用的结构 */
function toPreviewSource(item: PortalLibraryItem): PreviewSource {
  const isLaw = (item.docCategory ?? 'LAW').toUpperCase() === 'LAW';
  return {
    category: isLaw ? '法律法规' : '金融政策',
    date: item.date,
    department: item.issuer,
    tags: isLaw ? '法律法规' : '金融政策',
    title: item.title,
    url: item.sourceUrl,
  };
}

/** 法规库 / 政策库条目点击：有原文直接打开，否则弹出详情 */
function openLibraryItem(item: PortalLibraryItem) {
  if (item.sourceUrl) {
    window.open(item.sourceUrl, '_blank', 'noopener');
    return;
  }
  openPreview(toPreviewSource(item), 'news');
}

/** 滚动渐显：鼠标下滑时内容缓慢浮出 */
let revealObserver: IntersectionObserver | undefined;

function getRevealObserver() {
  if (!revealObserver) {
    revealObserver = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('tx-reveal-in');
          } else {
            entry.target.classList.remove('tx-reveal-in');
          }
        });
      },
      { rootMargin: '0px 0px -12% 0px', threshold: 0.08 },
    );
  }
  return revealObserver;
}

const vReveal: Directive<HTMLElement> = {
  mounted(element) {
    if (typeof IntersectionObserver === 'undefined') {
      element.classList.add('tx-reveal-in');
      return;
    }
    element.classList.add('tx-reveal');
    getRevealObserver().observe(element);
  },
  unmounted(element) {
    revealObserver?.unobserve(element);
  },
};

/** 首页会把本组件渲染两次（新闻区块 / 其余区块），用模块级缓存避免重复请求 */
let sectionsPromise: null | Promise<PortalHomeSections> = null;
let highlightsPromise: null | Promise<PortalHighlights> = null;

function loadSections() {
  if (!sectionsPromise) {
    sectionsPromise = getPortalHomeSections().catch((error) => {
      sectionsPromise = null;
      throw error;
    });
  }
  return sectionsPromise;
}

function loadHighlights() {
  if (!highlightsPromise) {
    highlightsPromise = getPortalHighlights().catch((error) => {
      highlightsPromise = null;
      throw error;
    });
  }
  return highlightsPromise;
}

let lawsPromise: null | Promise<PortalLibraryItem[]> = null;
let policiesPromise: null | Promise<PortalLibraryItem[]> = null;

function loadLaws() {
  if (!lawsPromise) {
    lawsPromise = getPortalLibrary('LAW', 6).catch((error) => {
      lawsPromise = null;
      throw error;
    });
  }
  return lawsPromise;
}

function loadPolicies() {
  if (!policiesPromise) {
    policiesPromise = getPortalLibrary('FINANCE_POLICY', 6).catch((error) => {
      policiesPromise = null;
      throw error;
    });
  }
  return policiesPromise;
}

onMounted(async () => {
  try {
    sections.value = await loadSections();
  } catch {
    // 内容区块加载失败不影响主页面
  }
  try {
    highlights.value = await loadHighlights();
  } catch {
    // 公告/政策加载失败不影响页面
  }
  if (!showRest.value) {
    return;
  }
  try {
    laws.value = await loadLaws();
  } catch {
    // 法规库加载失败忽略
  }
  try {
    policies.value = await loadPolicies();
  } catch {
    // 政策库加载失败忽略
  }
});

onBeforeUnmount(() => {
  revealObserver?.disconnect();
  revealObserver = undefined;
});
</script>

<template>
  <div class="tx-sections">
    <!-- 法规库 / 政策库（与上方通知公告 / 政策动态同栅格对齐） -->
    <div v-if="showRest && (laws.length || policies.length)" class="tx-row tx-row--board">
      <section id="sec-law" v-reveal class="tx-half tx-half--left tx-band--plain">
        <div class="tx-half-inner">
          <div class="tx-board">
            <div class="tx-board-head">
              <span class="tx-board-tag">法律法规</span>
              <h2>法规库</h2>
              <a class="tx-board-more" href="javascript:void(0)" @click="scrollToSearch">查看全部 <b>→</b></a>
            </div>
            <ul class="tx-board-list">
              <li v-for="item in laws" :key="'law-' + item.title">
                <span class="tx-board-ic"><IconifyIcon icon="solar:document-text-linear" /></span>
                <a href="javascript:void(0)" @click="openLibraryItem(item)">{{ item.title }}</a>
                <span class="tx-board-meta">{{ [item.issuer, item.date].filter(Boolean).join(' · ') }}</span>
              </li>
            </ul>
          </div>
        </div>
      </section>

      <section id="sec-policy-lib" v-reveal class="tx-half tx-half--right tx-band--plain">
        <div class="tx-half-inner">
          <div class="tx-board">
            <div class="tx-board-head">
              <span class="tx-board-tag tx-board-tag--green">金融政策</span>
              <h2>政策库</h2>
              <a class="tx-board-more" href="javascript:void(0)" @click="scrollToSearch">查看全部 <b>→</b></a>
            </div>
            <ul class="tx-board-list">
              <li v-for="item in policies" :key="'pol-' + item.title">
                <span class="tx-board-ic"><IconifyIcon icon="solar:document-text-linear" /></span>
                <a href="javascript:void(0)" @click="openLibraryItem(item)">{{ item.title }}</a>
                <span class="tx-board-meta">{{ [item.issuer, item.date].filter(Boolean).join(' · ') }}</span>
              </li>
            </ul>
          </div>
        </div>
      </section>
    </div>

    <!-- 第一行：推荐阅读 + 关于天信 -->
    <div
      v-if="showRest && (sections.banners.length || sections.products.length)"
      class="tx-row"
    >
      <section id="sec-featured" v-if="sections.banners.length" v-reveal class="tx-half tx-half--left tx-band--gold">
        <div class="tx-half-inner">
          <div class="tx-panel">
            <div class="tx-head">
              <div class="tx-head-main">
                <h2>{{ meta('banners').title }}</h2>
                <span class="en">{{ meta('banners').en }}</span>
              </div>
              <a class="tx-more" href="javascript:void(0)" @click="goList('banners')">更多<b>+</b></a>
            </div>
            <p class="tx-intro">{{ meta('banners').intro }}</p>
            <div class="tx-media-grid tx-media-grid--stack">
              <a
                v-for="(item, index) in sections.banners"
                :key="item.url"
                v-reveal
                class="tx-media-cell"
                :style="{ '--tx-delay': `${index * 80}ms` }"
                @click="openPreview(item, 'banners')"
              >
                <img :alt="item.name" :src="item.url" loading="lazy" />
                <span class="cap">{{ item.title || item.name }}</span>
              </a>
            </div>
          </div>
        </div>
      </section>

      <section id="sec-products" v-if="sections.products.length" v-reveal class="tx-half tx-half--right tx-half--lower tx-band--red">
        <div class="tx-half-inner">
          <div class="tx-panel">
            <div class="tx-head">
              <div class="tx-head-main">
                <h2>{{ meta('products').title }}</h2>
                <span class="en">{{ meta('products').en }}</span>
              </div>
              <a class="tx-more" href="javascript:void(0)" @click="goList('products')">更多<b>+</b></a>
            </div>
            <p class="tx-intro">{{ meta('products').intro }}</p>
            <div class="tx-prod-grid">
              <article
                v-for="(item, index) in sections.products.slice(0, 6)"
                :key="item.url"
                v-reveal
                class="tx-prod"
                :style="{ '--tx-delay': `${(index % 3) * 80}ms` }"
                @click="openPreview(item, 'products')"
              >
                <div class="ph"><img :alt="item.name" :src="item.url" loading="lazy" /></div>
                <div class="pc">
                  <span class="tag">{{ item.category }}</span>
                  <h3>{{ item.title }}</h3>
                  <p>{{ item.imageCount && item.imageCount > 1 ? `${item.imageCount} 张图片` : '查看详情' }}</p>
                </div>
              </article>
            </div>
          </div>
        </div>
      </section>
    </div>

    <!-- 第二行：通知公告 / 政策动态（卡片样式，参照法规库 / 政策库） -->
    <div v-if="showNews && newsTotal" class="tx-row tx-row--board">
      <section id="sec-notice" v-reveal class="tx-half tx-half--left tx-band--plain">
        <div class="tx-half-inner">
          <div class="tx-board">
            <div class="tx-board-head">
              <span class="tx-board-tag">通知公告</span>
              <h2>通知公告</h2>
              <a class="tx-board-more" href="javascript:void(0)" @click="goList('news')">查看全部 <b>→</b></a>
            </div>
            <ul class="tx-board-list">
              <li v-for="item in highlights.notices" :key="'n' + item.id">
                <span class="tx-board-ic"><IconifyIcon icon="solar:document-text-linear" /></span>
                <a href="javascript:void(0)" @click="openPreview(item, 'news')">{{ item.title }}</a>
                <span class="tx-board-meta">{{ item.date }}</span>
              </li>
              <li
                v-for="item in sections.notices.slice(0, Math.max(0, 6 - highlights.notices.length))"
                :key="item.url"
              >
                <span class="tx-board-ic"><IconifyIcon icon="solar:document-text-linear" /></span>
                <a href="javascript:void(0)" @click="openPreview(item, 'news')">{{ item.title }}</a>
                <span class="tx-board-meta">{{ item.category }}</span>
              </li>
            </ul>
            <p v-if="!highlights.notices.length && !sections.notices.length" class="empty">暂无公告</p>
          </div>
        </div>
      </section>

      <section id="sec-policy-news" v-reveal class="tx-half tx-half--right tx-band--plain">
        <div class="tx-half-inner">
          <div class="tx-board">
            <div class="tx-board-head">
              <span class="tx-board-tag tx-board-tag--green">政策动态</span>
              <h2>政策动态</h2>
              <a class="tx-board-more" href="javascript:void(0)" @click="goList('news')">查看全部 <b>→</b></a>
            </div>
            <ul class="tx-board-list">
              <li v-for="item in policyFeed" :key="'p-' + item.title">
                <span class="tx-board-ic"><IconifyIcon icon="solar:document-text-linear" /></span>
                <a href="javascript:void(0)" @click="openPreview(item, 'news')">{{ item.title }}</a>
                <span class="tx-board-meta">
                  {{ [item.department, item.date].filter(Boolean).join(' · ') }}
                </span>
              </li>
            </ul>
            <p v-if="!policyFeed.length" class="empty">暂无政策</p>
          </div>
        </div>
      </section>
    </div>

    <!-- 第三行：产品中心 + 技术资料 -->
    <div
      v-if="showRest && (sections.about.length || sections.techDocs.length)"
      class="tx-row"
    >
      <section id="sec-about" v-if="sections.about.length" v-reveal class="tx-half tx-half--left tx-band--cream">
        <div class="tx-half-inner">
          <div class="tx-panel">
            <div class="tx-head">
              <div class="tx-head-main">
                <h2>{{ meta('about').title }}</h2>
                <span class="en">{{ meta('about').en }}</span>
              </div>
              <a class="tx-more" href="javascript:void(0)" @click="goList('about')">更多<b>+</b></a>
            </div>
            <p class="tx-intro">{{ meta('about').intro }}</p>
            <div class="tx-media-grid">
              <figure
                v-for="(item, index) in sections.about.slice(0, 6)"
                :key="item.url"
                v-reveal
                class="tx-media-cell tx-media-cell--cap"
                :style="{ '--tx-delay': `${(index % 3) * 80}ms` }"
                @click="openPreview(item, 'about')"
              >
                <img :alt="item.name" :src="item.url" loading="lazy" />
                <figcaption>{{ item.category }}</figcaption>
              </figure>
            </div>
          </div>
        </div>
      </section>

      <section id="sec-tech" v-if="sections.techDocs.length" v-reveal class="tx-half tx-half--right tx-half--lower tx-band--gold">
        <div class="tx-half-inner">
          <div class="tx-panel">
            <div class="tx-head">
              <div class="tx-head-main">
                <h2>{{ meta('techDocs').title }}</h2>
                <span class="en">{{ meta('techDocs').en }}</span>
              </div>
              <a class="tx-more" href="javascript:void(0)" @click="goList('techDocs')">更多<b>+</b></a>
            </div>
            <p class="tx-intro">{{ meta('techDocs').intro }}</p>
            <div class="tx-std-list">
              <a
                v-for="item in sections.techDocs.slice(0, 8)"
                :key="item.url"
                v-reveal
                class="tx-std-item"
                :href="item.url"
                rel="noopener noreferrer"
                target="_blank"
              >
                <b>PDF</b>
                <span>{{ item.title }}</span>
              </a>
            </div>
          </div>
        </div>
      </section>
    </div>

    <!-- 详情弹层：大图 + 文字描述 + 相关文章 -->
    <div v-if="previewData" class="tx-lightbox" @click.self="closePreview">
      <div class="tx-lightbox-inner">
        <div class="tx-lightbox-media">
          <img v-if="previewData.url" :alt="previewData.title" :src="previewData.url" />
          <div v-else class="tx-lightbox-plain">该条目暂无图片，以下为文字说明。</div>
        </div>

        <div class="tx-lightbox-info">
          <button class="tx-lightbox-close" type="button" @click="closePreview">×</button>
          <span class="tx-lightbox-column">{{ previewData.column }}</span>
          <h3>{{ previewData.title }}</h3>
          <p class="tx-lightbox-desc">{{ previewData.description }}</p>

          <dl class="tx-lightbox-meta">
            <template v-if="previewData.category">
              <dt>素材分类</dt>
              <dd>{{ previewData.category }}</dd>
            </template>
            <template v-if="previewData.tags">
              <dt>素材路径</dt>
              <dd>{{ previewData.tags }}</dd>
            </template>
            <template v-if="previewData.url">
              <dt>图片数量</dt>
              <dd>{{ previewData.imageCount }} 张</dd>
            </template>
          </dl>

          <div class="tx-lightbox-actions">
            <a
              v-if="previewData.url"
              class="tx-lb-btn tx-lb-btn--ghost"
              :href="previewData.url"
              rel="noopener noreferrer"
              target="_blank"
            >
              查看原图
            </a>
            <button class="tx-lb-btn tx-lb-btn--primary" type="button" @click="goDetail">
              进入详情页看原文
            </button>
          </div>

          <div v-if="relatedItems.length" class="tx-lightbox-related">
            <h4>{{ RELATED_TITLE[previewSection] }}</h4>
            <ul>
              <li v-for="item in relatedItems" :key="item.url || item.id">
                <a href="javascript:void(0)" @click="openPreview(item, previewSection)">
                  {{ item.title || item.name }}
                </a>
              </li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.tx-sections {
  --tx-red: #9e1b32;
  --tx-red-dark: #7a1426;
  --tx-gold: #c9a961;
  --tx-ink: #2b2b2b;
  --tx-bg: #f5f3ef;
  --tx-line: #e3ddd3;

  margin-top: 18px;
  color: var(--tx-ink);
}

/* 滚动渐显 */
.tx-reveal {
  opacity: 0;
  transform: translateY(52px);
  transition:
    opacity 1.4s cubic-bezier(0.16, 1, 0.3, 1),
    transform 1.4s cubic-bezier(0.16, 1, 0.3, 1);
  transition-delay: var(--tx-delay, 0ms);
  will-change: opacity, transform;
}

.tx-reveal-in {
  opacity: 1;
  transform: translateY(0);
}

@media (prefers-reduced-motion: reduce) {
  .tx-reveal {
    opacity: 1;
    transition: none;
    transform: none;
  }
}

/* 彩色底板 */
.tx-band--plain {
  background: transparent;
}

.tx-band--gold {
  background: transparent;
}

.tx-band--cream {
  background: transparent;
}

.tx-band--red {
  background: transparent;
}

/* 两栏并排的小区段 */
.tx-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  width: 100vw;
  min-width: 0;
  margin-right: calc(50% - 50vw);
  margin-left: calc(50% - 50vw);
}

.tx-half {
  display: flex;
  min-width: 0;
  padding: 44px 0;
}

.tx-half--left {
  justify-content: flex-end;
}

.tx-half--right {
  justify-content: flex-start;
}

/* 取消错位：左右两栏上下对齐 */
.tx-half--lower {
  padding-top: 0;
}

.tx-half-inner {
  display: flex;
  align-items: stretch;
  width: 100%;
  min-width: 0;
  max-width: 800px;
  padding: 0 12px;
}

/* 通栏小区段 */
.tx-sec {
  position: relative;
  width: 100vw;
  padding: 46px 0;
  margin-right: calc(50% - 50vw);
  margin-left: calc(50% - 50vw);
  overflow: hidden;
}

.tx-inner {
  max-width: 1600px;
  padding: 0 24px;
  margin: 0 auto;
}

/* 卡片 */
.tx-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  width: 100%;
  min-width: 0;
  padding: 26px 28px 30px;
  background: #fff;
  border-top: 4px solid var(--tx-gold);
  box-shadow: 0 18px 40px rgb(43 36 32 / 10%);
}

.tx-band--cream .tx-panel {
  border-top-color: var(--tx-red);
}

.tx-panel.tx-panel--dark {
  background: #fff;
  border: 1px solid #e3e8ef;
  border-top: 4px solid var(--tx-gold);
  box-shadow: 0 1px 2px rgb(10 37 64 / 6%);
}
/* 区块标题 */

.tx-head {
  position: relative;
  display: flex;
  gap: 14px;
  align-items: flex-end;
  justify-content: space-between;
  padding-bottom: 12px;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--tx-line);
}

.tx-head::after {
  position: absolute;
  bottom: -1px;
  left: 0;
  width: 110px;
  height: 3px;
  content: '';
  background: linear-gradient(90deg, var(--tx-red), var(--tx-gold));
}

.tx-head-main {
  display: flex;
  gap: 10px;
  align-items: baseline;
  min-width: 0;
}

.tx-head h2 {
  padding-left: 14px;
  font-size: 25px;
  font-weight: 700;
  color: var(--tx-red);
  letter-spacing: 3px;
  white-space: nowrap;
  border-left: 5px solid var(--tx-gold);
}

.tx-head .en {
  overflow: hidden;
  font-size: 12.5px;
  color: #b3aa9b;
  letter-spacing: 1px;
  text-overflow: ellipsis;
  text-transform: uppercase;
  white-space: nowrap;
}

.tx-more {
  display: inline-flex;
  flex-shrink: 0;
  gap: 6px;
  align-items: center;
  font-size: 14px;
  color: var(--tx-red);
  cursor: pointer;
}

.tx-more b {
  display: grid;
  width: 19px;
  height: 19px;
  font-size: 13.5px;
  line-height: 1;
  color: #fff;
  background: var(--tx-red);
  place-items: center;
}

.tx-more:hover {
  color: var(--tx-red-dark);
}

/* 栏目说明文字 */
.tx-intro {
  margin: -6px 0 20px;
  font-size: 13.5px;
  line-height: 1.9;
  color: #8d867b;
}

.tx-panel--dark .tx-intro {
  color: #8d867b;
}

.tx-panel--dark .tx-head {
  border-bottom-color: var(--tx-line);
}

.tx-panel--dark .tx-head h2 {
  color: var(--tx-red);
}

.tx-panel--dark .tx-head .en {
  color: #b3aa9b;
}

.tx-panel--dark .tx-more {
  color: var(--tx-red);
}

.tx-panel--dark .tx-more b {
  color: #fff;
  background: var(--tx-red);
}

/* 图片格子（推荐阅读 / 关于天信） */
.tx-media-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  flex: 1;
  grid-auto-rows: minmax(150px, 1fr);
}

.tx-media-grid--stack {
  grid-template-columns: 1fr;
}

.tx-media-cell {
  position: relative;
  overflow: hidden;
  cursor: pointer;
  background: #eee;
}

.tx-media-cell img {
  display: block;
  width: 100%;
  min-height: 150px;
  height: 100%;
  object-fit: cover;
  transition: transform 0.45s cubic-bezier(0.22, 0.61, 0.36, 1);
}

.tx-media-cell:hover img {
  transform: scale(1.07);
}

.tx-media-cell .cap {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  padding: 20px 10px 8px;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(transparent, rgb(0 0 0 / 72%));
}

.tx-media-cell--cap figcaption {
  padding: 8px 10px;
  font-size: 12.5px;
  color: #666;
  background: var(--tx-bg);
}

/* 带说明的图片格：图片占满剩余高度，说明固定在底部 */
.tx-media-cell--cap {
  display: flex;
  flex-direction: column;
}

.tx-media-cell--cap img {
  flex: 1;
  height: auto;
  min-height: 120px;
}

/* 产品 */
.tx-prod-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  flex: 1;
  grid-auto-rows: 1fr;
}

.tx-prod {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  cursor: pointer;
  background: #fff;
  border: 1px solid var(--tx-line);
  transition: all 0.3s cubic-bezier(0.22, 0.61, 0.36, 1);
}

.tx-prod:hover {
  box-shadow: 0 8px 24px rgb(158 27 50 / 15%);
  transform: translateY(-3px);
}

.tx-prod .ph {
  min-height: 146px;
  flex: 1;
  overflow: hidden;
  background: #eee;
}

.tx-prod .ph img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.35s;
}

.tx-prod:hover .ph img {
  transform: scale(1.06);
}

.tx-prod .pc {
  padding: 10px 12px 12px;
}

.tx-prod .pc h3 {
  margin: 6px 0 4px;
  font-size: 15px;
  font-weight: 600;
  color: var(--tx-ink);
}

.tx-prod .pc p {
  font-size: 12.5px;
  color: #999;
}

.tx-prod .tag {
  display: inline-block;
  padding: 1px 7px;
  font-size: 12px;
  color: var(--tx-red);
  border: 1px solid var(--tx-red);
}

/* 技术资料 */
.tx-std-list {
  display: grid;
  flex: 1;
  gap: 10px;
  grid-auto-rows: 1fr;
}
.tx-std-item {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 11px 14px;
  font-size: 14px;
  background: #fff;
  border-left: 3px solid var(--tx-gold);
  transition: all 0.25s;
}

.tx-std-item:hover {
  border-left-color: var(--tx-red);
  box-shadow: 0 6px 18px rgb(158 27 50 / 10%);
  transform: translateX(3px);
}

.tx-std-item b {
  flex-shrink: 0;
  font-family: monospace;
  color: var(--tx-red);
}

.tx-std-item span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 通知公告 / 政策动态：白卡片列表（参照法规库 / 政策库样式） */
.tx-row--board .tx-half {
  padding: 22px 0;
}

.tx-board {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  max-width: 100%;
  padding: 24px 26px;
  background: #fff;
  border: 1px solid #e3e8ef;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgb(10 37 64 / 6%);
}

.tx-board-head {
  display: flex;
  gap: 12px;
  align-items: center;
  padding-bottom: 14px;
  margin-bottom: 4px;
  border-bottom: 1px solid #eef1f6;
}

.tx-board-tag {
  flex-shrink: 0;
  padding: 3px 10px;
  font-size: 12.5px;
  font-weight: 600;
  color: #1a4480;
  background: #e8f0fb;
  border-radius: 6px;
}

.tx-board-tag--green {
  color: #17663a;
  background: #e6f4ec;
}

.tx-board-head h2 {
  flex: 1;
  font-size: 20px;
  font-weight: 700;
  color: #0a2540;
}

.tx-board-more {
  flex-shrink: 0;
  font-size: 14px;
  color: #1a4480;
}

.tx-board-more b {
  font-weight: 400;
}

.tx-board-more:hover {
  color: #0a2540;
}

.tx-board-list {
  list-style: none;
}

.tx-board-list li {
  display: flex;
  gap: 14px;
  align-items: center;
  padding: 13px 0;
  font-size: 15px;
  border-bottom: 1px solid #f0f3f8;
}

.tx-board-list li:last-child {
  border-bottom: 0;
}

.tx-board-ic {
  display: grid;
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  font-size: 18px;
  color: #1a4480;
  background: #eef3fa;
  border-radius: 9px;
  place-items: center;
}

.tx-board-list li a {
  flex: 1;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  color: #1f2937;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tx-board-list li a:hover {
  color: #1a4480;
}

.tx-board-meta {
  flex-shrink: 0;
  font-size: 13.5px;
  color: #8a93a3;
}

/* 新闻三栏 */
.tx-news-cols {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 34px;
  flex: 1;
}

.tx-nc h3 {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 10px;
  margin-bottom: 10px;
  font-size: 19px;
  color: var(--tx-red);
  border-bottom: 2px solid var(--tx-red);
}

.tx-nc h3 small {
  font-size: 12.5px;
  font-weight: 400;
  color: #aaa;
}

.tx-nc ul {
  list-style: none;
}

.tx-nc li {
  display: flex;
  gap: 10px;
  justify-content: space-between;
  padding: 9px 0;
  font-size: 14.5px;
  border-bottom: 1px dashed var(--tx-line);
}

.tx-nc li a {
  flex: 1;
  overflow: hidden;
  color: var(--tx-ink);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tx-nc li a::before {
  padding-right: 8px;
  color: var(--tx-gold);
  content: '○';
}

.tx-nc li a:hover {
  color: var(--tx-red);
}

.tx-nc li .d {
  flex-shrink: 0;
  font-size: 13px;
  color: #aaa;
}

.empty {
  padding: 14px 0;
  font-size: 13px;
  color: #aaa;
}

/* 红板块配色 */
.tx-panel--dark .tx-nc h3 {
  color: #fff;
  border-bottom-color: rgb(255 255 255 / 35%);
}

.tx-panel--dark .tx-nc h3 small {
  color: rgb(255 255 255 / 55%);
}

.tx-panel--dark .tx-nc li {
  border-bottom-color: rgb(255 255 255 / 16%);
}

.tx-panel--dark .tx-nc li a {
  color: #f4ece2;
}

.tx-panel--dark .tx-nc li a:hover {
  color: var(--tx-gold);
}

.tx-panel--dark .tx-nc li .d {
  color: rgb(255 255 255 / 55%);
}

.tx-panel--dark .empty {
  color: rgb(255 255 255 / 55%);
}

/* 详情弹层（放大版） */
.tx-lightbox {
  position: fixed;
  inset: 0;
  z-index: 300;
  display: grid;
  padding: 28px;
  background: rgb(0 0 0 / 74%);
  place-items: center;
  animation: txLightboxIn 0.28s ease both;
}

@keyframes txLightboxIn {
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
}

.tx-lightbox-inner {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 460px;
  width: min(1440px, 96vw);
  max-height: 92vh;
  overflow: hidden;
  background: #fff;
  border-radius: 6px;
  box-shadow: 0 30px 80px rgb(0 0 0 / 50%);
}

.tx-lightbox-media {
  display: grid;
  min-height: 420px;
  background: #14161c;
  place-items: center;
}

.tx-lightbox-media img {
  display: block;
  width: 100%;
  max-height: 92vh;
  object-fit: contain;
}

.tx-lightbox-plain {
  padding: 90px 40px;
  font-size: 15px;
  color: #cbd5e1;
  text-align: center;
}

.tx-lightbox-info {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 34px 32px 28px;
  overflow-y: auto;
}

.tx-lightbox-column {
  align-self: flex-start;
  padding: 4px 14px;
  font-size: 12.5px;
  color: var(--tx-red);
  letter-spacing: 1px;
  background: #fdf2f4;
  border: 1px solid #f0d3da;
  border-radius: 20px;
}

.tx-lightbox-info h3 {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.5;
  color: var(--tx-ink);
}

.tx-lightbox-desc {
  font-size: 14.5px;
  line-height: 2;
  color: #555;
  text-align: justify;
}

.tx-lightbox-meta {
  display: grid;
  grid-template-columns: 80px 1fr;
  gap: 7px 12px;
  padding-top: 14px;
  font-size: 12.5px;
  border-top: 1px dashed var(--tx-line);
}

.tx-lightbox-meta dt {
  color: #999;
}

.tx-lightbox-meta dd {
  color: #555;
  overflow-wrap: anywhere;
}

.tx-lightbox-actions {
  display: flex;
  gap: 12px;
}

.tx-lb-btn {
  padding: 10px 20px;
  font-size: 13.5px;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.25s ease;
}

.tx-lb-btn--ghost {
  color: var(--tx-red);
  background: #fff;
  border: 1px solid var(--tx-red);
}

.tx-lb-btn--ghost:hover {
  color: #fff;
  background: var(--tx-red);
}

.tx-lb-btn--primary {
  color: #fff;
  background: var(--tx-red);
  border: 1px solid var(--tx-red);
}

.tx-lb-btn--primary:hover {
  background: var(--tx-red-dark);
  border-color: var(--tx-red-dark);
}

.tx-lightbox-related {
  padding-top: 16px;
  margin-top: auto;
  border-top: 1px dashed var(--tx-line);
}

.tx-lightbox-related h4 {
  padding-left: 10px;
  margin-bottom: 10px;
  font-size: 15px;
  font-weight: 700;
  color: var(--tx-red);
  letter-spacing: 2px;
  border-left: 4px solid var(--tx-gold);
}

.tx-lightbox-related ul {
  list-style: none;
}

.tx-lightbox-related li {
  padding: 8px 0;
  font-size: 13.5px;
  border-bottom: 1px dashed var(--tx-line);
}

.tx-lightbox-related li a {
  display: block;
  overflow: hidden;
  color: #444;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tx-lightbox-related li a::before {
  padding-right: 8px;
  color: var(--tx-gold);
  content: '○';
}

.tx-lightbox-related li a:hover {
  color: var(--tx-red);
}

.tx-lightbox-close {
  position: absolute;
  top: 14px;
  right: 16px;
  display: grid;
  width: 30px;
  height: 30px;
  font-size: 20px;
  line-height: 1;
  color: #666;
  cursor: pointer;
  background: #f4f4f5;
  border: none;
  border-radius: 50%;
  place-items: center;
}

.tx-lightbox-close:hover {
  color: #fff;
  background: var(--tx-red);
}

@media (max-width: 1200px) {
  .tx-row {
    grid-template-columns: 1fr;
  }

  .tx-half--left,
  .tx-half--right {
    justify-content: center;
  }

  .tx-half--lower {
    padding-top: 32px;
  }

  .tx-half-inner {
    max-width: 1600px;
    padding: 0 24px;
  }
}

@media (max-width: 1024px) {
  .tx-news-cols,
  .tx-media-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .tx-prod-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .tx-half {
    padding: 30px 0;
  }

  .tx-lightbox {
    padding: 14px;
  }

  .tx-lightbox-inner {
    grid-template-columns: minmax(0, 1fr);
    overflow-y: auto;
  }

  .tx-lightbox-media img {
    max-height: 46vh;
  }
}
</style>





