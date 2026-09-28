<script setup lang="ts">
import type { LawAnswer, LawSearchItem } from '#/api/cms/law';

import { computed, nextTick, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

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

import {
  answerLaw,
  downloadLawOriginal,
  searchLaws,
  suggestLaws,
} from '#/api/cms/law';
import FinancePolicyInterpretation from '../review/parts/record/finance-policy-interpretation.vue';

const props = withDefaults(defineProps<{ portal?: boolean }>(), {
  portal: false,
});


const router = useRouter();
type SearchFeature = 'answer' | 'finance' | 'law' | 'standard';

const feature = ref<SearchFeature>('answer');
const keyword = ref('');
const status = ref<string>();
const asOfDate = ref('');
const issuingAuthority = ref('');
const effectiveDateFrom = ref('');
const effectiveDateTo = ref('');
const advancedFiltersVisible = ref(false);
const suggestionOptions = ref<{ label: string; value: string }[]>([]);
let suggestionTimer: ReturnType<typeof setTimeout> | undefined;
const loading = ref(false);
const answer = ref<LawAnswer>();
const financeReviewId = ref('');
const conversation = ref<Array<{ question: string; response: LawAnswer }>>([]);
const rows = ref<LawSearchItem[]>([]);
const appliedKeyword = ref('');
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

/** 门户首页快捷导航：点击滚动到下方对应板块 */
const portalSections = [
  { id: 'sec-notice', label: '通知公告' },
  { id: 'sec-policy-news', label: '政策动态' },
  { id: 'sec-law', label: '法规库' },
  { id: 'sec-policy-lib', label: '政策库' },
  { id: 'sec-featured', label: '推荐阅读' },
  { id: 'sec-products', label: '产品中心' },
  { id: 'sec-about', label: '关于天信' },
  { id: 'sec-tech', label: '技术资料' },
];

function scrollToSection(id: string) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

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
    if (feature.value === 'answer' || feature.value === 'finance') {
      if (feature.value === 'finance') financeReviewId.value = '';
      const response = await answerLaw(
        query,
        5,
        props.portal,
        conversation.value.map((turn) => ({
          answer: turn.response.answer.slice(0, 4000),
          question: turn.question,
        })),
        feature.value === 'finance' ? 'FINANCE_POLICY' : 'ALL',
      );
      answer.value = response;
      if (feature.value === 'finance') {
        financeReviewId.value =
          response.citations.find((citation) => citation.reviewId)?.reviewId || '';
      }
      appliedKeyword.value = query;
      conversation.value.push({ question: query, response });
    } else {
      const result = await searchLaws(
        {
          asOfDate: asOfDate.value || undefined,
          issuingAuthority: issuingAuthority.value.trim() || undefined,
          effectiveDateFrom: effectiveDateFrom.value || undefined,
          effectiveDateTo: effectiveDateTo.value || undefined,
          docCategory: feature.value === 'finance' ? 'FINANCE_POLICY' : undefined,
          keyword: query,
          lawType:
            feature.value === 'standard'
              ? 'STANDARD'
              : feature.value === 'law'
                ? 'LAW'
                : undefined,
          pageNo: pageNo.value,
          pageSize: pageSize.value,
          status: (status.value || undefined) as any,
        },
        props.portal,
      );
      rows.value = result.list || [];
      appliedKeyword.value = query;
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

/** 官方来源只允许 HTTPS 政府网站；历史 /files/... 路径由“本地原件”入口处理。 */
function officialSourceUrl(value?: string) {
  if (!value) return undefined;
  try {
    const url = new URL(value);
    const hostname = url.hostname.toLowerCase();
    return url.protocol === 'https:' &&
      (hostname === 'gov.cn' || hostname.endsWith('.gov.cn'))
      ? url.toString()
      : undefined;
  } catch {
    return undefined;
  }
}

function clearConversation() {
  conversation.value = [];
  answer.value = undefined;
  financeReviewId.value = '';
  keyword.value = '';
  suggestionOptions.value = [];
  nextTick(() => searchInputRef.value?.focus());
}

/** 转义 HTML，避免 v-html 注入。 */
function escapeHtml(value: string) {
  return value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
}

/** 关键字检索结果高亮：命中词以 <mark> 包裹。 */
function highlightText(value?: string) {
  const source = escapeHtml(value ?? '');
  const keyword = appliedKeyword.value.trim();
  if (!keyword) {
    return source;
  }
  const terms = keyword
    .split(/\\s+/)
    .filter((item) => item.length > 0)
    .map((item) => item.replace(/[.*+?^${}()|[\\]\\\\]/g, '\\\\$&'))
    .sort((left, right) => right.length - left.length);
  if (!terms.length) {
    return source;
  }
  return source.replace(new RegExp(`(${terms.join('|')})`, 'gi'), '<mark>$1</mark>');
}

async function openLocalOriginal(reviewId?: string, pageNumber?: number) {
  if (!reviewId) return;
  try {
    const file = await downloadLawOriginal(reviewId, props.portal);
    const url = URL.createObjectURL(file);
    window.open(pageNumber ? `${url}#page=${pageNumber}` : url, '_blank', 'noopener');
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
  } catch (exception) {
    console.error(exception);
    message.error('本地原件读取失败，请稍后重试。');
  }
}

async function switchEntry(nextFeature: SearchFeature) {
  feature.value = nextFeature;
  answer.value = undefined;
  financeReviewId.value = '';
  conversation.value = [];
  error.value = '';
  pageNo.value = 1;
  advancedFiltersVisible.value = false;
  await nextTick();
  searchInputRef.value?.focus();
}

/** 跳转专家咨询 */
function goExpert() {
  router.push('/portal/expert');
}

/** 跳转专家入驻申请 */
function goExpertApply() {
  router.push('/portal/expert/apply');
}

async function clearAdvancedFilters() {
  status.value = undefined;
  issuingAuthority.value = '';
  asOfDate.value = '';
  effectiveDateFrom.value = '';
  effectiveDateTo.value = '';
  pageNo.value = 1;
  if (keyword.value.trim()) await run();
}
</script>

<template>
  <section class="cms-glass-content law-search-panel">
    <section class="law-hero">
      <div class="law-hero-inner">
        <div class="law-hero-eyebrow">防 腐 保 温 行 业 智 能 服 务 平 台</div>
        <h1>防腐保温行业政策法规<em> · </em>金融服务平台</h1>
        <p>
          权威法规检索 · 精准政策匹配 · 智能 AI 解读，汇聚法律法规、金融政策与国家标准。
        </p>
        <div class="hero-search">
          <div class="hero-mode">
            <select v-model="feature" aria-label="选择检索模块">
              <option value="answer">法规智能问答</option>
              <option value="standard">国家标准查询</option>
              <option value="law">法律法规检索</option>
              <option value="finance">金融政策库</option>
            </select>
          </div>
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

        <div v-if="props.portal" class="expert-entries">
          <button class="expert-entry" type="button" @click="goExpert">
            <span class="expert-icon"><IconifyIcon icon="solar:user-speak-linear" /></span>
            <span class="expert-copy">
              <strong>专家咨询</strong>
              <small>在线预约防腐保温行业专家，一对一解答技术选型与施工方案问题</small>
            </span>
            <span class="expert-arrow">→</span>
          </button>

          <button class="expert-entry" type="button" @click="goExpertApply">
            <span class="expert-icon"><IconifyIcon icon="solar:user-plus-linear" /></span>
            <span class="expert-copy">
              <strong>专家入驻申请</strong>
              <small>填 4 项信息即可提交，实名核验通过后自动上架接受企业预约</small>
            </span>
            <span class="expert-arrow">→</span>
          </button>
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

        <div v-if="props.portal" class="hot-keywords hot-keywords--nav">
          <span>快速导航：</span>
          <button
            v-for="item in portalSections"
            :key="item.id"
            type="button"
            @click="scrollToSection(item.id)"
          >
            {{ item.label }}
          </button>
        </div>
      </div>
    </section>


    <Card :bordered="false" class="search-card">
      <Tabs v-model:active-key="feature">
        <Tabs.TabPane key="answer" tab="法规智能问答" />
        <Tabs.TabPane key="standard" tab="国家标准查询" />
        <Tabs.TabPane key="law" tab="法律法规检索" />
        <Tabs.TabPane key="finance" tab="金融政策库" />
      </Tabs>

      <div v-if="feature === 'law' || feature === 'standard'" class="keyword-mode-bar">
        <div>
          <Tag class="fixed-type-tag">
            {{ feature === 'standard' ? '国家标准' : '法律法规' }}
          </Tag>
          <span>当前使用 ES 关键词精准检索</span>
        </div>
        <Button
          size="small"
          @click="advancedFiltersVisible = !advancedFiltersVisible"
        >
          <IconifyIcon
            :icon="advancedFiltersVisible ? 'solar:alt-arrow-up-linear' : 'solar:tuning-2-linear'"
          />
          {{ advancedFiltersVisible ? '收起筛选' : '高级筛选' }}
        </Button>
      </div>

      <div
        v-if="feature !== 'answer' && advancedFiltersVisible"
        class="filters"
      >
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
        <Button @click="clearAdvancedFilters">清空筛选</Button>
      </div>

      <div v-else class="answer-scope">
        <Tag color="blue">{{ feature === 'finance' ? '金融政策 AI 解读' : 'RAG 智能问答' }}</Tag>
        <small v-if="feature === 'finance'">
          先由大模型基于金融政策原文生成解读（贴息标准、适用对象、申报要点），下方同时返回对应政策原文与原件页码。
        </small>
        <small v-else>自动融合关键词与向量召回，并由大模型基于已发布原文生成带引用回答。</small>
      </div>

      <p class="scope-tip">
        只检索已通过审查且已由管理员发布的原文，回答不替代原文依据。
        <template v-if="feature === 'answer' || feature === 'finance'">
          当前对话保留最近 6 轮上下文，可直接使用“该条”“其中”等方式追问。
        </template>
      </p>
    </Card>

    <Alert v-if="error" :message="error" show-icon type="error" />

    <Spin :spinning="loading">
      <Card
        v-if="feature === 'answer' && conversation.length > 1"
        class="conversation-history"
        title="本次对话记录"
      >
        <article
          v-for="(turn, index) in conversation.slice(0, -1)"
          :key="`${index}-${turn.question}`"
          class="conversation-turn"
        >
          <strong>第 {{ index + 1 }} 轮 · {{ turn.question }}</strong>
          <p>{{ turn.response.answer }}</p>
        </article>
      </Card>

      <FinancePolicyInterpretation
        v-if="feature === 'finance' && answer && financeReviewId"
        :auto-generate="!props.portal"
        :portal="props.portal"
        :review-id="financeReviewId"
        @locate-page="(pageNumber) => openLocalOriginal(financeReviewId, pageNumber)"
      />

      <Card
        v-else-if="feature === 'finance' && answer"
        title="金融政策 AI 结构化解读"
      >
        <Alert
          message="没有匹配到可生成结构化解读的已发布金融政策，请换一个更具体的政策名称或关键词。"
          show-icon
          type="info"
        />
      </Card>

      <Card
        v-else-if="feature === 'answer' && answer"
        :title="`智能回答 · 第 ${conversation.length} 轮`"
      >
        <template #extra>
          <Tag :color="answer.generated ? 'green' : 'orange'">
            {{ answer.generated ? answer.model || '已生成' : '原文降级回答' }}
          </Tag>
          <Tag :color="answer.semanticAvailable ? 'blue' : 'default'">
            {{ answer.semanticAvailable ? 'RAG 混合召回' : 'RAG 关键词降级召回' }}
          </Tag>
          <Tag color="geekblue">
            已发布知识库
          </Tag>
          <Button size="small" @click="clearConversation">新对话</Button>
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
            <p v-html="highlightText(citation.quote)" />
            <div class="citation-meta">
              <span>相关度 {{ score(citation.score) }}</span>
              <a
                v-if="officialSourceUrl(citation.sourceUrl)"
                :href="officialSourceUrl(citation.sourceUrl)"
                rel="noopener noreferrer"
                target="_blank"
                >查看官方原文</a>
              <span v-else class="source-unavailable">官方来源待补充</span>
              <Button
                v-if="citation.localOriginalAvailable && citation.reviewId"
                size="small"
                type="link"
                @click="openLocalOriginal(citation.reviewId, citation.pageNumber)"
              >
                本地原件{{ citation.pageNumber ? ` · 第 ${citation.pageNumber} 页` : '' }}
              </Button>
            </div>
          </article>
        </div>
        <p v-else class="empty">当前没有可引用的原文。</p>
      </Card>

      <Card
        v-else-if="(feature === 'law' || feature === 'standard') && (hasResult || total === 0)"
        :title="
          feature === 'standard' ? '国家标准查询结果' : '法律法规检索结果'
        "
      >
        <div v-if="rows.length" class="result-list">
          <article v-for="row in rows" :key="row.sourceId" class="result-item">
            <div class="result-heading">
              <div>
                <h3 v-html="highlightText(row.title)" />
                <span>{{ row.documentNo || '-' }} ·
                  {{ row.articleNo || '原文片段' }}</span>
              </div>
              <Tag :color="row.status === 'EFFECTIVE' ? 'green' : 'orange'">
                {{ statusNames[row.status] || row.status }}
              </Tag>
            </div>
            <p v-html="highlightText(row.content)" />
            <div class="citation-meta">
              <span>{{ row.issuingAuthority || '发布机关未标注' }} · 相关度
                {{ score(row.score) }}</span>
              <a
                v-if="officialSourceUrl(row.sourceUrl)"
                :href="officialSourceUrl(row.sourceUrl)"
                rel="noopener noreferrer"
                target="_blank"
              >官方来源</a>
              <span v-else class="source-unavailable">官方来源待补充</span>
              <Button
                v-if="row.localOriginalAvailable && row.reviewId"
                size="small"
                type="link"
                @click="openLocalOriginal(row.reviewId, row.pageNumber)"
              >
                本地原件{{ row.pageNumber ? ` · 第 ${row.pageNumber} 页` : '' }}
              </Button>
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

.answer-scope {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 12px;
  color: var(--cms-text-secondary, #526174);
}

.answer-scope small {
  opacity: 0.78;
}

.conversation-history :deep(.ant-card-body) {
  display: grid;
  gap: 12px;
}

.conversation-turn {
  border-left: 3px solid #3d6fd8;
  border-radius: 8px;
  background: rgb(255 255 255 / 48%);
  padding: 12px 16px;
}

.conversation-turn p {
  margin: 8px 0 0;
  color: var(--cms-text-secondary, #526174);
  white-space: pre-wrap;
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
  grid-template-columns: minmax(0, 1fr) 140px;
  align-items: center;
  max-width: 1000px;
  min-height: 72px;
  padding: 6px;
  margin: 0 auto;
  overflow: hidden;
  background: #fff;
  border: 2px solid #1a2b4a;
  border-radius: 12px;
  box-shadow: 0 18px 44px rgb(5 15 34 / 32%);
}

.hero-keyword {
  width: 100%;
}

.hero-keyword :deep(.ant-input) {
  height: 58px;
  font-size: 16px;
  padding-inline: 20px;
  color: #22252a;
  background: transparent !important;
  border: 0 !important;
  border-radius: 0 !important;
  box-shadow: none !important;
}

.hero-search-button {
  height: 58px;
  font-size: 17px !important;
  font-weight: 700 !important;
  color: #fff !important;
  background: linear-gradient(135deg, #1a4480, #0a2540) !important;
  border: 0 !important;
  border-radius: 9px !important;
  box-shadow: 0 8px 20px rgb(10 37 64 / 28%) !important;
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
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  padding: 0 26px;
  margin-top: -52px;
}

@media (max-width: 1500px) {
  .quick-entry {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.entry-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 14px;
  align-items: center;
  min-height: 126px;
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
  width: 48px;
  height: 48px;
  font-size: 25px;
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
  font-size: 16.5px;
  font-weight: 700;
}

.entry-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11.5px;
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

.keyword-mode-bar {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  padding: 10px 2px 0;
  margin-top: 8px;
  color: #607087;
}

.keyword-mode-bar > div {
  display: flex;
  gap: 10px;
  align-items: center;
  font-size: 12px;
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
.result-item :deep(mark) {
  padding: 0 2px;
  color: #b03a2e;
  background: #fdecc8;
  border-radius: 3px;
}
/* ===== 奶白灰浅色主题：五模块置于搜索框上方 ===== */
.law-hero {
  color: #1f2937;
  background:
    radial-gradient(circle at 88% -6%, rgb(201 169 97 / 14%), transparent 34%),
    linear-gradient(180deg, #fdfcfa 0%, #f4f1ea 100%);
  border: 1px solid #ece6da;
  box-shadow: 0 14px 34px rgb(26 43 74 / 8%);
}

.law-hero::before {
  border-color: rgb(201 169 97 / 16%);
  box-shadow:
    0 0 0 70px rgb(201 169 97 / 6%),
    0 0 0 140px rgb(201 169 97 / 4%);
}

.law-hero-inner {
  max-width: 1280px;
}

.law-hero-eyebrow {
  color: #a8883f;
}

.law-hero h1 {
  color: #1a2b4a;
}

.law-hero h1 em {
  color: #b89448;
}

.law-hero p {
  color: #6b7280;
}

/* 五个模块：一行对齐，位于搜索框上方 */
.quick-entry {
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  padding: 0;
  margin: 26px auto 22px;
  text-align: left;
}

.entry-card {
  min-height: 104px;
  padding: 15px;
  background: #fff;
  border: 1px solid #ece6da;
  border-top: 3px solid #1a2b4a;
  border-radius: 10px;
  box-shadow: 0 8px 20px rgb(26 43 74 / 7%);
  backdrop-filter: none;
}

.entry-card--expert {
  border-top-color: #9e1b32;
}

.entry-card--expert .entry-icon {
  color: #9e1b32;
  background: rgb(158 27 50 / 8%);
}

.entry-icon {
  width: 42px;
  height: 42px;
  font-size: 21px;
}

.entry-copy strong {
  font-size: 14.5px;
}

.entry-copy small {
  font-size: 11px;
  line-height: 1.55;
  white-space: normal;
}

/* 搜索框（门户主题：放大 + 醒目） */
.hero-search {
  border: 2px solid #1a2b4a;
  box-shadow: 0 18px 44px rgb(10 37 64 / 30%);
}

.hero-search-button {
  color: #fff !important;
  background: linear-gradient(135deg, #1a2b4a, #2c4470) !important;
}

.hot-keywords > span {
  color: #9aa0aa;
}

.hot-keywords button {
  color: #5a6373;
  background: #fff;
  border-color: #e6e1d7;
}

.hot-keywords button:hover {
  color: #9e1b32;
  background: rgb(158 27 50 / 5%);
  border-color: rgb(158 27 50 / 30%);
}

.hot-keywords--nav {
  margin-top: 10px;
}

/* 查法规 / 找政策 */
.hero-actions {
  display: flex;
  gap: 14px;
  justify-content: center;
  margin-top: 22px;
}

.hero-action {
  min-width: 136px;
  padding: 11px 26px;
  font-size: 15px;
  font-weight: 600;
  color: #1a2b4a;
  cursor: pointer;
  background: #fff;
  border: 1px solid #dcd5c7;
  border-radius: 6px;
  transition: all 0.22s ease;
}

.hero-action:hover {
  color: #1a2b4a;
  border-color: #1a2b4a;
}

.hero-action--primary {
  color: #fff;
  background: linear-gradient(135deg, #1a2b4a, #2c4470);
  border-color: transparent;
}

.hero-action--primary:hover {
  color: #fff;
  box-shadow: 0 10px 24px rgb(26 43 74 / 22%);
}

@media (max-width: 1500px) {
  .quick-entry {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1024px) {
  .quick-entry {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .quick-entry {
    grid-template-columns: 1fr;
  }

  .hero-actions {
    flex-direction: column;
  }
}

/* ===== 搜索框左侧模块选择 + 搜索框下方专家咨询 ===== */
.hero-search {
  grid-template-columns: 220px minmax(0, 1fr) 140px;
}

.hero-mode {
  display: flex;
  align-items: center;
  height: 58px;
  border-right: 1px solid #e7e2d8;
}

.hero-mode select {
  width: 100%;
  height: 52px;
  font-size: 15px;
  padding: 0 8px 0 14px;
  font-size: 14px;
  font-weight: 600;
  color: #1a2b4a;
  cursor: pointer;
  background: transparent;
  border: none;
  outline: none;
}

.expert-entries {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  max-width: 660px;
  margin: 10px auto 0;
}

@media (max-width: 900px) {
  .expert-entries {
    grid-template-columns: 1fr;
  }
}

.expert-entry {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 100%;
  padding: 7px 11px;
  margin: 0;
  text-align: left;
  cursor: pointer;
  background: #fff;
  border: 1px solid #ece6da;
  border-left: 3px solid #9e1b32;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgb(26 43 74 / 6%);
  transition: all 0.25s ease;
}

.expert-entry:hover {
  box-shadow: 0 12px 26px rgb(158 27 50 / 12%);
  transform: translateY(-2px);
}

.expert-icon {
  display: grid;
  flex-shrink: 0;
  width: 26px;
  height: 26px;
  font-size: 13px;
  color: #9e1b32;
  background: rgb(158 27 50 / 8%);
  border-radius: 6px;
  place-items: center;
}

.expert-copy {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.expert-copy strong {
  font-size: 12px;
  font-weight: 700;
  color: #1a2b4a;
}

.expert-copy small {
  font-size: 10px;
  line-height: 1.5;
  color: #6b7280;
}

.expert-arrow {
  font-size: 12px;
  color: #9e1b32;
}

@media (max-width: 720px) {
  .hero-search {
    grid-template-columns: 1fr;
    gap: 8px;
    padding: 10px;
  }

  .hero-mode {
    border-right: none;
    border-bottom: 1px solid #e7e2d8;
  }
}

</style>
