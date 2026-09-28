<script setup lang="ts">
import { ref, watch } from 'vue';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  DescriptionsItem,
  Empty,
  Space,
  Spin,
  Tag,
  message,
} from 'ant-design-vue';

import type { FinancePolicyInterpretationView } from '#/api/cms/finance-policy';
import {
  generateFinancePolicyInterpretation,
  getFinancePolicyInterpretation,
} from '#/api/cms/finance-policy';

const props = withDefaults(
  defineProps<{ portal?: boolean; reviewId: string }>(),
  { portal: false },
);
const emit = defineEmits<{ locatePage: [pageNumber?: number] }>();

const loading = ref(false);
const view = ref<FinancePolicyInterpretationView>();

async function load() {
  loading.value = true;
  try {
    view.value = await getFinancePolicyInterpretation(props.reviewId, props.portal);
  } catch {
    view.value = undefined;
  } finally {
    loading.value = false;
  }
}

async function generate() {
  loading.value = true;
  try {
    view.value = await generateFinancePolicyInterpretation(props.reviewId);
    message.success('金融政策结构化解读已生成。');
  } catch (error: any) {
    message.error(error?.msg || 'AI 解读生成失败，请检查模型场景配置与原文切片。');
  } finally {
    loading.value = false;
  }
}

watch(() => [props.reviewId, props.portal], load, { immediate: true });
</script>

<template>
  <Card class="interpretation-card" title="金融政策 AI 结构化解读">
    <template #extra>
      <Space>
        <Tag v-if="view" color="green">
          已生成
        </Tag>
        <Button v-if="!portal" :loading="loading" type="primary" @click="generate">
          {{ view ? '重新生成' : '生成 AI 解读' }}
        </Button>
      </Space>
    </template>
    <Spin :spinning="loading">
      <Empty v-if="!view" description="该政策尚未生成结构化解读" />
      <template v-else>
        <Alert :message="view.disclaimer" show-icon type="warning" />
        <p class="summary">{{ view.interpretation.summary }}</p>
        <Descriptions bordered :column="2" size="small">
          <DescriptionsItem label="适用地区">
            {{ view.interpretation.applicableRegions.join('、') || '原文未明确' }}
          </DescriptionsItem>
          <DescriptionsItem label="支持方式">
            {{ view.interpretation.supportMethods.join('、') || '原文未明确' }}
          </DescriptionsItem>
          <DescriptionsItem label="支持金额" :span="2">
            {{ view.interpretation.amountDescription || '原文未明确' }}
          </DescriptionsItem>
          <DescriptionsItem label="申报开始">
            {{ view.interpretation.applicationPeriod.start || '原文未明确' }}
          </DescriptionsItem>
          <DescriptionsItem label="申报截止">
            {{ view.interpretation.applicationPeriod.end || '原文未明确' }}
          </DescriptionsItem>
        </Descriptions>
        <div class="interpretation-grid">
          <section>
            <h4>适用条件</h4>
            <ul><li v-for="item in view.interpretation.eligibility" :key="item">{{ item }}</li></ul>
          </section>
          <section>
            <h4>风险提示</h4>
            <ul><li v-for="item in view.interpretation.risks" :key="item">{{ item }}</li></ul>
          </section>
        </div>
        <section class="citations">
          <h4>原文引用</h4>
          <article v-for="(citation, index) in view.interpretation.citations" :key="citation.chunkId">
            <Button type="link" @click="emit('locatePage', citation.pageNumber)">
              [{{ index + 1 }}] {{ citation.pageNumber ? `定位第 ${citation.pageNumber} 页` : citation.chunkId }}
            </Button>
            <q>{{ citation.quote }}</q>
          </article>
        </section>
      </template>
    </Spin>
  </Card>
</template>

<style scoped>
.interpretation-card { margin: 16px 0; }
.summary { margin: 14px 0; color: #263a56; font-size: 15px; line-height: 1.8; }
.interpretation-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-top: 14px; }
.interpretation-grid section, .citations { padding: 14px; background: rgb(247 250 253 / 70%); border: 1px solid rgb(118 146 183 / 17%); border-radius: 12px; }
h4 { margin: 0 0 8px; color: #1a3153; }
ul { padding-left: 20px; margin: 0; color: #52647b; line-height: 1.8; }
.citations { margin-top: 14px; }
.citations article { display: grid; gap: 4px; padding: 9px 0; border-top: 1px solid rgb(118 146 183 / 14%); }
.citations article:first-of-type { border-top: 0; }
.citations q { color: #52647b; line-height: 1.7; }
@media (max-width: 800px) { .interpretation-grid { grid-template-columns: 1fr; } }
</style>
