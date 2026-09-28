<script lang="ts" setup>
import type { PortalHighlights, PortalHomeSections } from '#/api/cms/portal';
import type { PreviewSource, SectionKey } from '../components/portal-content-meta';

import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { getPortalHighlights, getPortalHomeSections } from '#/api/cms/portal';

import {
  RELATED_TITLE,
  SECTION_META,
  buildDescription,
  columnOf,
  detailQuery,
  isPdfUrl,
  matchItem,
  sectionItems,
} from '../components/portal-content-meta';

defineOptions({ name: 'PortalContentDetail' });

const route = useRoute();
const router = useRouter();

const sectionKey = computed<SectionKey>(() => {
  const key = String(route.query.section ?? 'news') as SectionKey;
  return key in SECTION_META ? key : 'news';
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
const urlParam = computed(() => String(route.query.url ?? ''));
const idParam = computed(() => String(route.query.id ?? ''));

const current = computed<PreviewSource | undefined>(
  () =>
    items.value.find((item) => matchItem(item, urlParam.value, idParam.value)) ??
    (items.value.length > 0 ? items.value[0] : undefined),
);

const title = computed(
  () => current.value?.title || current.value?.name || String(route.query.title ?? '内容详情'),
);
const description = computed(() => (current.value ? buildDescription(current.value, title.value) : ''));
const related = computed(() => items.value.filter((item) => item !== current.value).slice(0, 8));
const pdf = computed(() => isPdfUrl(current.value?.url));

function openItem(item: PreviewSource) {
  router.push({ path: '/portal/content/detail', query: detailQuery(item, sectionKey.value) });
}

function goList() {
  router.push({ path: '/portal/content/list', query: { section: sectionKey.value } });
}

onMounted(async () => {
  try {
    sections.value = await getPortalHomeSections();
  } catch {
    // 忽略加载失败
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
  <div class="tx-detail-page">
    <section class="tx-band">
      <div class="tx-inner">
        <div class="tx-panel">
          <div class="tx-bar">
            <button class="tx-back" type="button" @click="goList">← 返回{{ meta.title }}</button>
            <span class="tx-crumb">CMS 系统 / {{ meta.title }} / 详情</span>
          </div>

          <div class="tx-body">
            <div class="tx-media">
              <img v-if="current?.url && !pdf" :alt="title" :src="current.url" />
              <a
                v-else-if="pdf"
                class="tx-media-pdf"
                :href="current?.url"
                rel="noopener noreferrer"
                target="_blank"
              >
                <b>PDF</b>
                <span>点击打开技术资料文件</span>
              </a>
              <div v-else class="tx-media-plain">该条目暂无图片附件，以下为文字说明。</div>
            </div>

            <div class="tx-info">
              <span class="tx-column">{{ current ? columnOf(current) : meta.title }}</span>
              <h1>{{ title }}</h1>
              <p class="tx-desc">{{ description }}</p>

              <dl class="tx-meta">
                <template v-if="current?.category">
                  <dt>素材分类</dt>
                  <dd>{{ current.category }}</dd>
                </template>
                <template v-if="current?.tags">
                  <dt>素材路径</dt>
                  <dd>{{ current.tags }}</dd>
                </template>
                <template v-if="current?.imageCount">
                  <dt>图片数量</dt>
                  <dd>{{ current.imageCount }} 张</dd>
                </template>
                <template v-if="current?.date">
                  <dt>发布日期</dt>
                  <dd>{{ current.date }}</dd>
                </template>
                <template v-if="current?.department">
                  <dt>发布单位</dt>
                  <dd>{{ current.department }}</dd>
                </template>
              </dl>

              <div class="tx-actions">
                <a
                  v-if="current?.url"
                  class="tx-btn tx-btn--primary"
                  :href="current.url"
                  rel="noopener noreferrer"
                  target="_blank"
                >
                  {{ pdf ? '打开文件' : '查看原图' }}
                </a>
                <button class="tx-btn" type="button" @click="goList">返回列表</button>
              </div>
            </div>
          </div>

          <div v-if="related.length" class="tx-related">
            <h2>{{ RELATED_TITLE[sectionKey] }}</h2>
            <ul>
              <li v-for="item in related" :key="item.url || item.id">
                <a href="javascript:void(0)" @click="openItem(item)">{{ item.title || item.name }}</a>
                <span class="d">{{ item.date || item.category }}</span>
              </li>
            </ul>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.tx-detail-page {
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
  padding: 22px 30px 32px;
  background: #fff;
  border-top: 4px solid var(--tx-gold);
  box-shadow: 0 18px 40px rgb(43 36 32 / 10%);
}

.tx-bar {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--tx-line);
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

.tx-crumb {
  font-size: 13px;
  color: #999;
}

.tx-body {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(340px, 1fr);
  gap: 30px;
  padding: 26px 0 30px;
}

.tx-media {
  display: grid;
  overflow: hidden;
  background: #14161c;
  place-items: center;
}

.tx-media img {
  display: block;
  width: 100%;
  max-height: 560px;
  object-fit: contain;
}

.tx-media-pdf {
  display: grid;
  gap: 10px;
  padding: 90px 40px;
  color: #fff;
  place-items: center;
}

.tx-media-pdf b {
  font-family: monospace;
  font-size: 40px;
}

.tx-media-pdf span {
  font-size: 14px;
  color: rgb(255 255 255 / 70%);
}

.tx-media-plain {
  padding: 90px 40px;
  font-size: 14px;
  color: #cbd5e1;
  text-align: center;
}

.tx-info {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.tx-column {
  align-self: flex-start;
  padding: 4px 14px;
  font-size: 12.5px;
  color: var(--tx-red);
  letter-spacing: 1px;
  background: #fdf2f4;
  border: 1px solid #f0d3da;
  border-radius: 20px;
}

.tx-info h1 {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.5;
  color: #2b2b2b;
}

.tx-desc {
  font-size: 15px;
  line-height: 2;
  color: #555;
  text-align: justify;
}

.tx-meta {
  display: grid;
  grid-template-columns: 84px 1fr;
  gap: 8px 12px;
  padding-top: 16px;
  margin-top: auto;
  font-size: 13px;
  border-top: 1px dashed var(--tx-line);
}

.tx-meta dt {
  color: #999;
}

.tx-meta dd {
  color: #555;
  overflow-wrap: anywhere;
}

.tx-actions {
  display: flex;
  gap: 12px;
  margin-top: 4px;
}

.tx-btn {
  padding: 10px 22px;
  font-size: 13.5px;
  color: var(--tx-red);
  cursor: pointer;
  background: #fff;
  border: 1px solid var(--tx-red);
  border-radius: 4px;
  transition: all 0.25s ease;
}

.tx-btn:hover {
  color: #fff;
  background: var(--tx-red);
}

.tx-btn--primary {
  color: #fff;
  background: var(--tx-red);
}

.tx-btn--primary:hover {
  background: #7a1426;
  border-color: #7a1426;
}

/* 相关文章 */
.tx-related {
  padding-top: 22px;
  border-top: 1px solid var(--tx-line);
}

.tx-related h2 {
  padding-left: 14px;
  margin-bottom: 14px;
  font-size: 19px;
  font-weight: 700;
  color: var(--tx-red);
  letter-spacing: 2px;
  border-left: 5px solid var(--tx-gold);
}

.tx-related ul {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0 34px;
  list-style: none;
}

.tx-related li {
  display: flex;
  gap: 12px;
  justify-content: space-between;
  padding: 11px 0;
  font-size: 14px;
  border-bottom: 1px dashed var(--tx-line);
}

.tx-related li a {
  flex: 1;
  overflow: hidden;
  color: #2b2b2b;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tx-related li a::before {
  padding-right: 8px;
  color: var(--tx-gold);
  content: '○';
}

.tx-related li a:hover {
  color: var(--tx-red);
}

.tx-related li .d {
  flex-shrink: 0;
  font-size: 12.5px;
  color: #aaa;
}

@media (max-width: 1024px) {
  .tx-body {
    grid-template-columns: minmax(0, 1fr);
  }

  .tx-related ul {
    grid-template-columns: 1fr;
  }

  .tx-panel {
    padding: 16px 16px 24px;
  }
}
</style>

