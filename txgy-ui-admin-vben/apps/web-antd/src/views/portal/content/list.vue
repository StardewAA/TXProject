<script lang="ts" setup>
import type { PortalHighlights, PortalHomeSections } from '#/api/cms/portal';
import type { PreviewSource, SectionKey } from '../components/portal-content-meta';

import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { getPortalHighlights, getPortalHomeSections } from '#/api/cms/portal';

import {
  SECTION_META,
  columnOf,
  detailQuery,
  isPdfUrl,
  newsItems,
  sectionItems,
} from '../components/portal-content-meta';

defineOptions({ name: 'PortalContentList' });

const route = useRoute();
const router = useRouter();

const sectionKey = computed<SectionKey>(() => {
  const key = String(route.query.section ?? 'products') as SectionKey;
  return key in SECTION_META ? key : 'products';
});
const meta = computed(() => SECTION_META[sectionKey.value]);

const sections = ref<PortalHomeSections>({
  about: [],
  banners: [],
  news: [],
  notices: [],
  products: [],
  techDocs: [],
});
const highlights = ref<PortalHighlights>({ notices: [], policies: [] });
const loading = ref(true);

const items = computed(() => sectionItems(sections.value, highlights.value, sectionKey.value));

/** 新闻动态按公司新闻 / 通知公告 / 政策动态分组 */
const groups = computed(() => {
  if (sectionKey.value !== 'news') {
    return [];
  }
  const all = newsItems(sections.value, highlights.value);
  return ['公司新闻', '通知公告', '政策动态'].map((group) => ({
    group,
    items: all.filter((item) => item.group === group),
  }));
});

function openItem(item: PreviewSource) {
  router.push({ path: '/portal/content/detail', query: detailQuery(item, sectionKey.value) });
}

function goBack() {
  router.push('/portal/content');
}

onMounted(async () => {
  try {
    sections.value = await getPortalHomeSections();
  } catch {
    // 忽略加载失败，页面展示为空列表
  }
  try {
    highlights.value = await getPortalHighlights();
  } catch {
    // 忽略加载失败
  }
  loading.value = false;
});
</script>

<template>
  <div class="tx-list-page">
    <section class="tx-band">
      <div class="tx-inner">
        <div class="tx-panel">
          <div class="tx-head">
            <div class="tx-head-main">
              <button class="tx-back" type="button" @click="goBack">← 返回</button>
              <h2>{{ meta.title }}</h2>
              <span class="en">{{ meta.en }}</span>
            </div>
            <span class="tx-count">共 {{ items.length }} 条</span>
          </div>

          <p class="tx-intro">{{ meta.intro }}</p>

          <!-- 新闻动态：三栏列表 -->
          <div v-if="sectionKey === 'news'" class="tx-groups">
            <div v-for="group in groups" :key="group.group" class="tx-group">
              <h3>{{ group.group }} <small>{{ group.items.length }} 条</small></h3>
              <ul>
                <li v-for="item in group.items" :key="item.url || item.id">
                  <a href="javascript:void(0)" @click="openItem(item)">{{ item.title }}</a>
                  <span class="d">{{ item.date || item.category }}</span>
                </li>
              </ul>
              <p v-if="!group.items.length" class="tx-empty">暂无内容</p>
            </div>
          </div>

          <!-- 其他栏目：卡片网格 -->
          <div v-else class="tx-grid">
            <article
              v-for="item in items"
              :key="item.url || item.id"
              class="tx-card"
              @click="openItem(item)"
            >
              <div v-if="isPdfUrl(item.url)" class="tx-card-ph tx-card-ph--pdf">PDF</div>
              <div v-else-if="item.url" class="tx-card-ph">
                <img :alt="item.name || item.title" :src="item.url" loading="lazy" />
              </div>
              <div class="tx-card-body">
                <span class="tx-card-tag">{{ item.category || columnOf(item) }}</span>
                <h3>{{ item.title || item.name }}</h3>
                <p>{{ item.imageCount && item.imageCount > 1 ? `${item.imageCount} 张图片` : '查看详情' }}</p>
              </div>
            </article>
          </div>

          <p v-if="!loading && items.length === 0" class="tx-empty">该栏目暂无内容。</p>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.tx-list-page {
  --tx-red: #9e1b32;
  --tx-gold: #c9a961;
  --tx-line: #e3ddd3;

  margin-top: -8px;
}

.tx-band {
  width: 100vw;
  padding: 40px 0 48px;
  margin-right: calc(50% - 50vw);
  margin-left: calc(50% - 50vw);
  background: linear-gradient(135deg, #fbf6ea 0%, #f0e3c8 100%);
}

.tx-inner {
  max-width: 1600px;
  padding: 0 24px;
  margin: 0 auto;
}

.tx-panel {
  padding: 26px 30px 34px;
  background: #fff;
  border-top: 4px solid var(--tx-gold);
  box-shadow: 0 18px 40px rgb(43 36 32 / 10%);
}

.tx-head {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--tx-line);
}

.tx-head-main {
  display: flex;
  gap: 14px;
  align-items: center;
}

.tx-head h2 {
  padding-left: 14px;
  font-size: 26px;
  font-weight: 700;
  color: var(--tx-red);
  letter-spacing: 4px;
  border-left: 5px solid var(--tx-gold);
}

.tx-head .en {
  font-size: 13px;
  color: #b3aa9b;
  letter-spacing: 2px;
  text-transform: uppercase;
}

.tx-back {
  padding: 7px 16px;
  font-size: 13px;
  color: var(--tx-red);
  cursor: pointer;
  background: #fdf2f4;
  border: 1px solid #f0d3da;
  border-radius: 20px;
}

.tx-back:hover {
  color: #fff;
  background: var(--tx-red);
  border-color: var(--tx-red);
}

.tx-count {
  font-size: 13px;
  color: #999;
}

.tx-intro {
  padding: 16px 0 22px;
  font-size: 14px;
  line-height: 1.9;
  color: #666;
}

/* 卡片网格 */
.tx-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.tx-card {
  overflow: hidden;
  cursor: pointer;
  background: #fff;
  border: 1px solid var(--tx-line);
  transition: all 0.3s cubic-bezier(0.22, 0.61, 0.36, 1);
}

.tx-card:hover {
  box-shadow: 0 10px 26px rgb(158 27 50 / 16%);
  transform: translateY(-4px);
}

.tx-card-ph {
  height: 180px;
  overflow: hidden;
  background: #eee;
}

.tx-card-ph img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.35s;
}

.tx-card:hover .tx-card-ph img {
  transform: scale(1.06);
}

.tx-card-ph--pdf {
  display: grid;
  font-family: monospace;
  font-size: 30px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #9e1b32, #7a1426);
  place-items: center;
}

.tx-card-body {
  padding: 14px 16px 16px;
}

.tx-card-tag {
  display: inline-block;
  padding: 1px 8px;
  font-size: 12px;
  color: var(--tx-red);
  border: 1px solid var(--tx-red);
}

.tx-card-body h3 {
  margin: 8px 0 6px;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.6;
  color: #2b2b2b;
}

.tx-card-body p {
  font-size: 12.5px;
  color: #999;
}

/* 新闻分组 */
.tx-groups {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 34px;
}

.tx-group h3 {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 10px;
  margin-bottom: 10px;
  font-size: 18px;
  color: var(--tx-red);
  border-bottom: 2px solid var(--tx-red);
}

.tx-group h3 small {
  font-size: 12px;
  font-weight: 400;
  color: #aaa;
}

.tx-group ul {
  list-style: none;
}

.tx-group li {
  display: flex;
  gap: 10px;
  justify-content: space-between;
  padding: 10px 0;
  font-size: 14px;
  border-bottom: 1px dashed var(--tx-line);
}

.tx-group li a {
  flex: 1;
  overflow: hidden;
  color: #2b2b2b;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tx-group li a::before {
  padding-right: 8px;
  color: var(--tx-gold);
  content: '○';
}

.tx-group li a:hover {
  color: var(--tx-red);
}

.tx-group li .d {
  flex-shrink: 0;
  font-size: 12.5px;
  color: #aaa;
}

.tx-empty {
  padding: 18px 0;
  font-size: 13px;
  color: #aaa;
}

@media (max-width: 1024px) {
  .tx-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .tx-groups {
    grid-template-columns: 1fr;
  }

  .tx-panel {
    padding: 18px 16px 24px;
  }
}
</style>

