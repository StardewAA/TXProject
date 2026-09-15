<script setup lang="ts">
import type { LawAnswer, LawSearchItem } from '#/api/cms/law';

import { computed, nextTick, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import {
  Alert,
  AutoComplete,
  Button,
  Card,
  Input,
  message,
  Pagination,
  Select,
  Spin,
  Tabs,
  Tag,
} from 'ant-design-vue';

import { answerLaw, searchLaws, suggestLaws } from '#/api/cms/law';

const props = withDefaults(defineProps<{ portal?: boolean }>(), {
  portal: false,
});

type SearchFeature = 'answer' | 'law' | 'standard';

const feature = ref<SearchFeature>('answer');
const keyword = ref('');
const status = ref<string>();
const asOfDate = ref('');
const issuingAuthority = ref('');
const effectiveDateFrom = ref('');
const effectiveDateTo = ref('');
const suggestionOptions = ref<{ label: string; value: string }[]>([]);
let suggestionTimer: ReturnType<typeof setTimeout> | undefined;
const loading = ref(false);
const answer = ref<LawAnswer>();
const rows = ref<LawSearchItem[]>([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(10);
const error = ref('');
const searchInputRef = ref<{ focus: () => void }>();

const statusOptions = [
  { label: '全部效力', value: '' },
  { label: '现行有效', value: 'EFFECTIVE' },
  { label: '尚未生效', value: 'NOT_YET_EFFECTIVE' },
  { label: '已废止', value: 'REPEALED' },
  { label: '已失效', value: 'EXPIRED' },
];

const statusNames: Record<string, string> = {
  EFFECTIVE: '现行有效',
  EXPIRED: '已失效',
  NOT_YET_EFFECTIVE: '尚未生效',
  REPEALED: '已废止',
  UNKNOWN: '待核实',
};

const hotKeywords = [
  '安全生产责任',
  '建筑工程质量',
  '消防安全',
  '挥发性有机物治理',
  '工业碳达峰',
];

const hasResult = computed(() =>
  feature.value === 'answer' ? Boolean(answer.value) : rows.value.length > 0,
);

const searchPlaceholder = computed(() => {
  if (feature.value === 'standard') {
    return '输入标准号、标准名称或条款，如：GB/T 23257-2017';
  }
  if (feature.value === 'law') {
    return '输入法规名称、发布机关或条款关键词';
  }
  return '输入法规问题，如：企业安全生产的主要责任有哪些？';
});

async function run() {
  const query = keyword.value.trim();
  if (!query) {
    message.warning('请输入要查询的法规问题或关键词。');
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    if (feature.value === 'answer') {
      answer.value = await answerLaw(query, 5, props.portal);
    } else {
      const result = await searchLaws(
        {
          asOfDate: asOfDate.value || undefined,
          issuingAuthority: issuingAuthority.value.trim() || undefined,
          effectiveDateFrom: effectiveDateFrom.value || undefined,
          effectiveDateTo: effectiveDateTo.value || undefined,
          keyword: query,
          lawType: feature.value === 'standard' ? 'STANDARD' : 'LAW',
          pageNo: pageNo.value,
          pageSize: pageSize.value,
          status: (status.value || undefined) as any,
        },
        props.portal,
      );
      rows.value = result.list || [];
      total.value = Number(result.total || 0);
      if (!rows.value.length) message.info('未找到已审查并发布的相关原文。');
    }
  } catch (exception) {
    console.error(exception);
    error.value = '检索服务暂不可用，请检查 CMS 服务和 Elasticsearch。';
  } finally {
    loading.value = false;
  }
}

watch(keyword, (value) => {
  if (suggestionTimer) clearTimeout(suggestionTimer);
  const query = value.trim();
  if (!query) {
    suggestionOptions.value = [];
    return;
  }
  suggestionTimer = setTimeout(async () => {
    try {
      const suggestions = await suggestLaws(query, props.portal);
      suggestionOptions.value = suggestions.slice(0, 5).map((item) => ({
        value: item.title,
        label: `${item.title}${item.documentNo ? ` · ${item.documentNo}` : ''}${item.issuingAuthority ? ` · ${item.issuingAuthority}` : ''}`,
      }));
    } catch {
      suggestionOptions.value = [];
    }
  }, 250);
});

async function changePage(page: number, size: number) {
  pageNo.value = page;
  pageSize.value = size;
  await run();
}

function score(value?: number) {
  return Number(value || 0).toFixed(4);
}

function quickSearch(value: string) {
  keyword.value = value;
  run();
}

async function switchEntry(nextFeature: SearchFeature) {
  feature.value = nextFeature;
  error.value = '';
  pageNo.value = 1;
  await nextTick();
  searchInputRef.value?.focus();
}
</script>

<template>
  <section class="cms-glass-content law-search-panel">
    <section class="law-hero">
      <div class="law-hero-inner">
        <div class="law-hero-eyebrow">智 能 政 策 法 规 服 务 平 台</div>
        <h1>一键读懂政策 <em>·</em> 精准查询标准 <em>·</em> 全面检索法规</h1>
        <p>
          汇聚法律法规、政策文件及国家标准，以真实知识库和原文引用辅助合规决策。
        </p>

        <div class="hero-search">
          <AutoComplete
            v-model:value="keyword"
            :options="suggestionOptions"
            class="hero-keyword"
            @select="run"
          >
            <Input
              ref="searchInputRef"
              :placeholder="searchPlaceholder"
              @press-enter="run"
            />
          </AutoComplete>
          <Button :loading="loading" class="hero-search-button" @click="run">
            <IconifyIcon icon="solar:magnifer-linear" />
            搜索
          </Button>
        </div>

        <div class="hot-keywords">
          <span>热门搜索：</span>
          <button
            v-for="item in hotKeywords"
            :key="item"
            type="button"
            @click="quickSearch(item)"
          >
            {{ item }}
          </button>
        </div>
      </div>
    </section>

    <section class="quick-entry">
      <button
        :aria-pressed="feature === 'answer'"
        :class="{ 'entry-card--active': feature === 'answer' }"
        class="entry-card"
        type="button"
        @click="switchEntry('answer')"
      >
        <span class="entry-icon"><IconifyIcon icon="solar:chat-round-dots-linear" /></span>
        <span class="entry-copy">
          <strong>法规智能问答</strong>
          <small>基于已发布原文生成带引用的回答</small>
        </span>
        <span class="entry-arrow">→</span>
      </button>
      <button
        :aria-pressed="feature === 'standard'"
        :class="{ 'entry-card--active': feature === 'standard' }"
        class="entry-card entry-card--gold"
        type="button"
        @click="switchEntry('standard')"
      >
        <span class="entry-icon"><IconifyIcon icon="solar:document-text-linear" /></span>
        <span class="entry-copy">
          <strong>国家标准查询</strong>
          <small>按标准号、条款及效力状态精准检索</small>
        </span>
        <span class="entry-arrow">→</span>
      </button>
      <button
        :aria-pressed="feature === 'law'"
        :class="{ 'entry-card--active': feature === 'law' }"
        class="entry-card entry-card--blue"
        type="button"
        @click="switchEntry('law')"
      >
        <span class="entry-icon"><IconifyIcon icon="solar:scale-linear" /></span>
        <span class="entry-copy">
          <strong>法律法规检索</strong>
          <small>查询现行法规、发布机关和原文依据</small>
        </span>
        <span class="entry-arrow">→</span>
      </button>
    </section>

    <Card :bordered="false" class="search-card">
      <Tabs v-model:active-key="feature">
        <Tabs.TabPane key="answer" tab="法规智能问答" />
        <Tabs.TabPane key="standard" tab="国家标准查询" />
        <Tabs.TabPane key="law" tab="法律法规检索" />
      </Tabs>

      <div v-if="feature !== 'answer'" class="filters">
        <Tag class="fixed-type-tag">
          {{ feature === 'standard' ? '国家标准' : '法律法规' }}
        </Tag>
        <Select
          v-model:value="status"
          :options="statusOptions"
          placeholder="全部效力"
        />
        <Input
          v-model:value="issuingAuthority"
          class="authority-input"
          placeholder="发布机关"
        />
        <label>查询时点 <input v-model="asOfDate" type="date" /></label>
        <label>实施日期从 <input v-model="effectiveDateFrom" type="date" /></label>
        <label>至 <input v-model="effectiveDateTo" type="date" /></label>
        <Button :loading="loading" @click="run">应用筛选</Button>
      </div>

      <p class="scope-tip">
        只检索已通过审查且已由管理员发布的原文，回答不替代原文依据。
      </p>
    </Card>

    <Alert v-if="error" :message="error" show-icon type="error" />

    <Spin :spinning="loading">
      <Card v-if="feature === 'answer' && answer" title="智能回答">
        <template #extra>
          <Tag :color="answer.generated ? 'green' : 'orange'">
            {{ answer.generated ? answer.model || '已生成' : '原文降级回答' }}
          </Tag>
          <Tag :color="answer.semanticAvailable ? 'blue' : 'default'">
            {{ answer.semanticAvailable ? '混合检索' : '关键词检索' }}
          </Tag>
        </template>
        <Alert
          v-if="answer.warning"
          :message="answer.warning"
          class="answer-warning"
          show-icon
          type="warning"
        />
        <div class="answer-text">{{ answer.answer }}</div>
        <h3>原文引用</h3>
        <div v-if="answer.citations.length" class="citation-list">
          <article
            v-for="citation in answer.citations"
            :key="citation.sourceId"
            class="citation"
          >
            <div class="citation-heading">
              <strong>[{{ citation.index }}] {{ citation.title }}</strong>
              <Tag>
{{
                citation.documentNo || citation.articleNo || '原文片段'
              }}
</Tag>
            </div>
            <p>{{ citation.quote }}</p>
            <div class="citation-meta">
              <span>相关度 {{ score(citation.score) }}</span>
              <a
                :href="citation.sourceUrl"
                rel="noopener noreferrer"
                target="_blank"
                >查看官方原文</a>
            </div>
          </article>
        </div>
        <p v-else class="empty">当前没有可引用的原文。</p>
      </Card>

      <Card
        v-else-if="feature !== 'answer' && (hasResult || total === 0)"
        :title="
          feature === 'standard' ? '国家标准查询结果' : '法律法规检索结果'
        "
      >
        <div v-if="rows.length" class="result-list">
          <article v-for="row in rows" :key="row.sourceId" class="result-item">
            <div class="result-heading">
              <div>
                <h3>{{ row.title }}</h3>
                <span>{{ row.documentNo || '-' }} ·
                  {{ row.articleNo || '原文片段' }}</span>
              </div>
              <Tag :color="row.status === 'EFFECTIVE' ? 'green' : 'orange'">
                {{ statusNames[row.status] || row.status }}
              </Tag>
            </div>
            <p>{{ row.content }}</p>
            <div class="citation-meta">
              <span>{{ row.issuingAuthority || '发布机关未标注' }} · 相关度
                {{ score(row.score) }}</span>
              <a :href="row.sourceUrl" rel="noopener noreferrer" target="_blank">官方来源</a>
            </div>
          </article>
          <Pagination
            :current="pageNo"
            :page-size="pageSize"
            :total="total"
            show-size-changer
            @change="changePage"
          />
        </div>
        <p v-else class="empty">输入检索条件后查询已发布原文。</p>
      </Card>
    </Spin>
  </section>
</template>

<style scoped>
.law-search-panel {
  display: grid;
  gap: 24px;
}

.law-hero {
  position: relative;
  min-height: 292px;
  padding: 44px 32px 58px;
  overflow: hidden;
  color: #fff;
  background:
    radial-gradient(circle at 88% -4%, rgb(201 169 97 / 18%), transparent 32%),
    linear-gradient(135deg, #1a2b4a, #243a63 64%, #2c4470);
  border: 1px solid rgb(255 255 255 / 10%);
  border-radius: 16px;
  box-shadow: 0 16px 38px rgb(26 43 74 / 18%);
}

.law-hero::before {
  position: absolute;
  top: -160px;
  right: -80px;
  width: 430px;
  height: 430px;
  pointer-events: none;
  content: '';
  border: 1px solid rgb(201 169 97 / 11%);
  border-radius: 50%;
  box-shadow:
    0 0 0 70px rgb(201 169 97 / 3%),
    0 0 0 140px rgb(201 169 97 / 2%);
}

.law-hero-inner {
  position: relative;
  z-index: 1;
  max-width: 940px;
  margin: 0 auto;
  text-align: center;
}

.law-hero-eyebrow {
  margin-bottom: 14px;
  font-size: 11px;
  font-weight: 600;
  color: #dfc48a;
  letter-spacing: 0.32em;
}

.law-hero h1 {
  margin: 0;
  font-family: 'Noto Serif SC', 'Songti SC', SimSun, serif;
  font-size: clamp(26px, 2.5vw, 36px);
  font-weight: 700;
  line-height: 1.45;
  color: #fff;
  letter-spacing: 0.04em;
}

.law-hero h1 em {
  font-style: normal;
  color: #c9a961;
}

.law-hero p {
  margin: 10px 0 25px;
  font-size: 13px;
  line-height: 1.8;
  color: rgb(255 255 255 / 60%);
}

.hero-search {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 104px;
  align-items: center;
  max-width: 780px;
  min-height: 54px;
  padding: 4px;
  margin: 0 auto;
  overflow: hidden;
  background: rgb(255 255 255 / 96%);
  border: 1px solid rgb(255 255 255 / 40%);
  border-radius: 8px;
  box-shadow: 0 12px 32px rgb(5 15 34 / 28%);
}

.hero-keyword {
  width: 100%;
}

.hero-keyword :deep(.ant-input) {
  height: 42px;
  padding-inline: 18px;
  color: #22252a;
  background: transparent !important;
  border: 0 !important;
  border-radius: 0 !important;
  box-shadow: none !important;
}

.hero-search-button {
  height: 42px;
  color: #1a2b4a !important;
  background: linear-gradient(135deg, #dfc48a, #c9a961) !important;
  border: 0 !important;
  border-radius: 6px !important;
  box-shadow: none !important;
}

.hot-keywords {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  align-items: center;
  justify-content: center;
  margin-top: 16px;
  font-size: 11px;
}

.hot-keywords > span {
  color: rgb(255 255 255 / 42%);
}

.hot-keywords button {
  padding: 3px 9px;
  font-size: 11px;
  color: rgb(255 255 255 / 70%);
  background: rgb(255 255 255 / 5%);
  border: 1px solid rgb(255 255 255 / 15%);
  border-radius: 999px;
  transition:
    color 160ms ease,
    background 160ms ease,
    border-color 160ms ease;
}

.hot-keywords button:hover {
  color: #dfc48a;
  background: rgb(201 169 97 / 9%);
  border-color: rgb(201 169 97 / 55%);
}

.quick-entry {
  position: relative;
  z-index: 2;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  padding: 0 26px;
  margin-top: -52px;
}

.entry-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 14px;
  align-items: center;
  min-height: 116px;
  padding: 19px 20px;
  color: #1a2b4a;
  text-align: left;
  background: rgb(255 254 250 / 90%);
  border: 1px solid rgb(224 221 213 / 86%);
  border-top: 3px solid #1a2b4a;
  border-radius: 9px;
  box-shadow: 0 10px 26px rgb(26 43 74 / 12%);
  backdrop-filter: blur(18px);
  transition:
    box-shadow 180ms ease,
    transform 180ms ease;
}

.entry-card--gold {
  border-top-color: #c9a961;
}

.entry-card--blue {
  border-top-color: #3b5a8e;
}

.entry-card:hover {
  box-shadow: 0 16px 32px rgb(26 43 74 / 17%);
  transform: translateY(-4px);
}

.entry-card--active {
  border-color: rgb(201 169 97 / 78%);
  box-shadow:
    0 16px 34px rgb(26 43 74 / 18%),
    0 0 0 3px rgb(201 169 97 / 14%);
  transform: translateY(-4px);
}

.entry-card--active .entry-arrow {
  color: #9d7d38;
  transform: translateX(3px);
}

.entry-icon {
  display: grid;
  place-items: center;
  width: 43px;
  height: 43px;
  font-size: 23px;
  color: #1a2b4a;
  background: rgb(26 43 74 / 7%);
  border-radius: 5px;
}

.entry-card--gold .entry-icon {
  color: #9d7d38;
  background: rgb(201 169 97 / 13%);
}

.entry-card--blue .entry-icon {
  color: #3b5a8e;
  background: rgb(59 90 142 / 10%);
}

.entry-copy {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}

.entry-copy strong {
  font-family: 'Noto Serif SC', 'Songti SC', SimSun, serif;
  font-size: 15px;
  font-weight: 700;
}

.entry-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 10px;
  color: #7d7b76;
  white-space: nowrap;
}

.entry-arrow {
  font-size: 17px;
  color: #aaa59b;
  transition:
    color 180ms ease,
    transform 180ms ease;
}

.entry-card:hover .entry-arrow {
  color: #a88840;
  transform: translateX(3px);
}

.search-card {
  background:
    radial-gradient(circle at 92% 0%, rgb(201 169 97 / 15%), transparent 36%),
    rgb(255 253 248 / 68%) !important;
  border: 1px solid rgb(255 255 255 / 82%) !important;
}

.search-card::after {
  position: absolute;
  top: 23px;
  right: 26px;
  width: 8px;
  height: 8px;
  content: '';
  background: #c9a961;
  border-radius: 50%;
  box-shadow: 0 0 0 5px rgb(201 169 97 / 12%);
}

.search-row {
  max-width: 980px;
}

.search-row :deep(.ant-input-search) {
  padding: 4px;
  background: rgb(255 255 255 / 62%);
  border: 1px solid rgb(119 150 190 / 20%);
  border-radius: 15px;
  box-shadow:
    0 10px 30px rgb(48 78 118 / 8%),
    inset 0 1px 0 rgb(255 255 255 / 90%);
}

.search-row :deep(.ant-input-affix-wrapper) {
  min-height: 43px;
  background: transparent !important;
  border: 0 !important;
  box-shadow: none !important;
}

.search-row :deep(.ant-input-search-button) {
  min-width: 92px;
  height: 43px;
  font-weight: 700;
  color: #1a2b4a !important;
  background: #c9a961 !important;
  border-color: #c9a961 !important;
  border-radius: 11px !important;
  box-shadow: none;
}

.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding: 14px;
  margin-top: 15px;
  background: rgb(244 241 233 / 52%);
  border: 1px solid rgb(117 148 187 / 14%);
  border-radius: 14px;
}

.filters :deep(.ant-select) {
  width: 160px;
}

.fixed-type-tag {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding-inline: 14px;
  margin-inline-end: 0;
  font-weight: 600;
  color: #8b6a25;
  background: rgb(201 169 97 / 12%);
  border-color: rgb(201 169 97 / 34%);
}

.filters .authority-input {
  width: 180px;
}

.filters label {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  font-size: 12px;
  color: #607087;
}

.filters input {
  min-height: 32px;
  padding: 4px 8px;
  border: 1px solid rgb(116 144 182 / 24%);
  border-radius: 9px;
}

.scope-tip {
  display: flex;
  gap: 7px;
  align-items: center;
  margin: 13px 0 0;
  font-size: 11px;
  color: #7d8ba0;
}

.scope-tip::before {
  width: 5px;
  height: 5px;
  content: '';
  background: #c9a961;
  border-radius: 50%;
}

.answer-warning {
  margin-bottom: 14px;
}

.answer-text {
  padding: 20px 22px;
  font-size: 14px;
  line-height: 1.9;
  color: #23344d;
  white-space: pre-wrap;
  background: linear-gradient(
    105deg,
    rgb(248 244 234 / 78%),
    rgb(255 255 255 / 35%)
  );
  border: 1px solid rgb(110 154 211 / 17%);
  border-left: 3px solid #c9a961;
  border-radius: 0 13px 13px 0;
}

h3 {
  margin: 22px 0 12px;
  font-size: 15px;
  font-weight: 650;
  color: #21324b;
}

.citation-list,
.result-list {
  display: grid;
  gap: 12px;
}

.citation,
.result-item {
  padding: 17px 18px;
  background: rgb(255 255 255 / 54%);
  border: 1px solid rgb(120 149 186 / 16%);
  border-radius: 14px;
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 75%);
  transition:
    border-color 180ms ease,
    box-shadow 180ms ease,
    transform 180ms ease;
}

.citation:hover,
.result-item:hover {
  border-color: rgb(67 126 204 / 27%);
  box-shadow: 0 12px 26px rgb(44 75 115 / 7%);
  transform: translateY(-2px);
}

.citation-heading,
.result-heading,
.citation-meta {
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

.citation p,
.result-item p {
  margin: 12px 0;
  line-height: 1.78;
  color: #45566d;
  white-space: pre-wrap;
}

.citation-meta {
  font-size: 12px;
  color: #7a899e;
}

.result-heading h3 {
  margin: 0 0 5px;
}

.result-heading span {
  font-size: 12px;
  color: #8390a2;
}

.empty {
  padding: 36px;
  color: #94a3b8;
  text-align: center;
}

.dark .answer-text {
  color: #dce9f8;
  background: rgb(25 56 94 / 35%);
}

.dark .citation,
.dark .result-item {
  background: rgb(17 33 54 / 52%);
}

.dark .citation p,
.dark .result-item p,
.dark h3 {
  color: #d5e0ed;
}

@media (max-width: 720px) {
  .law-hero {
    padding: 34px 18px 48px;
  }

  .hero-search {
    grid-template-columns: 1fr 82px;
  }

  .quick-entry {
    grid-template-columns: 1fr;
    padding: 0 10px;
    margin-top: -38px;
  }

  .entry-card {
    min-height: 92px;
  }

  .citation-heading,
  .result-heading,
  .citation-meta {
    flex-direction: column;
  }
}
</style>
