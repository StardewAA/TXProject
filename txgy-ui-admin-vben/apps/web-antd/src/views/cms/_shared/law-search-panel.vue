<script setup lang="ts">
import { computed, ref } from 'vue';

import {
  Alert,
  Button,
  Card,
  Input,
  Pagination,
  Select,
  Spin,
  Tabs,
  Tag,
  message,
} from 'ant-design-vue';

import type { LawAnswer, LawSearchItem } from '#/api/cms/law';
import { answerLaw, searchLaws } from '#/api/cms/law';

const props = withDefaults(defineProps<{ portal?: boolean }>(), {
  portal: false,
});

const mode = ref<'answer' | 'original'>('answer');
const keyword = ref('');
const lawType = ref<string>();
const status = ref<string>();
const asOfDate = ref('');
const loading = ref(false);
const answer = ref<LawAnswer>();
const rows = ref<LawSearchItem[]>([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(10);
const error = ref('');

const typeOptions = [
  { label: '全部类型', value: '' },
  { label: '法律法规', value: 'LAW' },
  { label: '国家标准', value: 'STANDARD' },
  { label: '政策文件', value: 'POLICY' },
];
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

const hasResult = computed(() =>
  mode.value === 'answer' ? Boolean(answer.value) : rows.value.length > 0,
);

async function run() {
  const query = keyword.value.trim();
  if (!query) {
    message.warning('请输入要查询的法规问题或关键词。');
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    if (mode.value === 'answer') {
      answer.value = await answerLaw(query, 5, props.portal);
    } else {
      const result = await searchLaws(
        {
          asOfDate: asOfDate.value || undefined,
          keyword: query,
          lawType: (lawType.value || undefined) as any,
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

async function changePage(page: number, size: number) {
  pageNo.value = page;
  pageSize.value = size;
  await run();
}

function score(value?: number) {
  return Number(value || 0).toFixed(4);
}
</script>

<template>
  <section class="law-search-panel">
    <Card :bordered="false" class="search-card">
      <Tabs v-model:active-key="mode">
        <Tabs.TabPane key="answer" tab="智能问答" />
        <Tabs.TabPane key="original" tab="原文检索" />
      </Tabs>

      <div class="search-row">
        <Input.Search
          v-model:value="keyword"
          :loading="loading"
          :placeholder="mode === 'answer' ? '例：企业安全生产的主要责任有哪些？' : '输入标准号、条款或关键词'"
          enter-button="查询"
          size="large"
          @search="run"
        />
      </div>

      <div v-if="mode === 'original'" class="filters">
        <Select v-model:value="lawType" :options="typeOptions" placeholder="全部类型" />
        <Select v-model:value="status" :options="statusOptions" placeholder="全部效力" />
        <label>查询时点 <input v-model="asOfDate" type="date" /></label>
        <Button :loading="loading" @click="run">应用筛选</Button>
      </div>

      <p class="scope-tip">只检索已通过审查且已由管理员发布的原文，回答不替代原文依据。</p>
    </Card>

    <Alert v-if="error" :message="error" show-icon type="error" />

    <Spin :spinning="loading">
      <Card v-if="mode === 'answer' && answer" title="智能回答">
        <template #extra>
          <Tag :color="answer.generated ? 'green' : 'orange'">
            {{ answer.generated ? answer.model || '已生成' : '原文降级回答' }}
          </Tag>
          <Tag :color="answer.semanticAvailable ? 'blue' : 'default'">
            {{ answer.semanticAvailable ? '混合检索' : '关键词检索' }}
          </Tag>
        </template>
        <Alert v-if="answer.warning" :message="answer.warning" class="answer-warning" show-icon type="warning" />
        <div class="answer-text">{{ answer.answer }}</div>
        <h3>原文引用</h3>
        <div v-if="answer.citations.length" class="citation-list">
          <article v-for="citation in answer.citations" :key="citation.sourceId" class="citation">
            <div class="citation-heading">
              <strong>[{{ citation.index }}] {{ citation.title }}</strong>
              <Tag>{{ citation.documentNo || citation.articleNo || '原文片段' }}</Tag>
            </div>
            <p>{{ citation.quote }}</p>
            <div class="citation-meta">
              <span>相关度 {{ score(citation.score) }}</span>
              <a :href="citation.sourceUrl" rel="noopener noreferrer" target="_blank">查看官方原文</a>
            </div>
          </article>
        </div>
        <p v-else class="empty">当前没有可引用的原文。</p>
      </Card>

      <Card v-else-if="mode === 'original' && (hasResult || total === 0)" title="原文检索结果">
        <div v-if="rows.length" class="result-list">
          <article v-for="row in rows" :key="row.sourceId" class="result-item">
            <div class="result-heading">
              <div>
                <h3>{{ row.title }}</h3>
                <span>{{ row.documentNo || '-' }} · {{ row.articleNo || '原文片段' }}</span>
              </div>
              <Tag :color="row.status === 'EFFECTIVE' ? 'green' : 'orange'">
                {{ statusNames[row.status] || row.status }}
              </Tag>
            </div>
            <p>{{ row.content }}</p>
            <div class="citation-meta">
              <span>{{ row.issuingAuthority || '发布机关未标注' }} · 相关度 {{ score(row.score) }}</span>
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
.law-search-panel { display: grid; gap: 16px; }
.search-card { border: 1px solid #dbeafe; background: linear-gradient(135deg, #eff6ff, #fff 70%); }
.search-row { max-width: 920px; }
.filters { display: flex; align-items: center; margin-top: 14px; gap: 10px; flex-wrap: wrap; }
.filters :deep(.ant-select) { width: 160px; }
.filters label { display: inline-flex; align-items: center; gap: 8px; color: #475569; }
.filters input { min-height: 32px; padding: 4px 8px; border: 1px solid #d9d9d9; border-radius: 6px; }
.scope-tip { margin: 12px 0 0; color: #64748b; font-size: 13px; }
.answer-warning { margin-bottom: 14px; }
.answer-text { padding: 18px; border-left: 4px solid #2563eb; background: #f8fafc; color: #172033; font-size: 15px; line-height: 1.9; white-space: pre-wrap; }
h3 { margin: 20px 0 12px; color: #172033; font-size: 16px; }
.citation-list, .result-list { display: grid; gap: 12px; }
.citation, .result-item { padding: 16px; border: 1px solid #e2e8f0; border-radius: 10px; background: #fff; }
.citation-heading, .result-heading, .citation-meta { display: flex; justify-content: space-between; gap: 12px; }
.citation p, .result-item p { margin: 12px 0; color: #334155; line-height: 1.75; white-space: pre-wrap; }
.citation-meta { color: #64748b; font-size: 13px; }
.result-heading h3 { margin: 0 0 5px; }
.result-heading span { color: #64748b; font-size: 13px; }
.empty { padding: 30px; color: #94a3b8; text-align: center; }
@media (max-width: 720px) {
  .citation-heading, .result-heading, .citation-meta { flex-direction: column; }
}
</style>
