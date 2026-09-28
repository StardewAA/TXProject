<script lang="ts" setup>
import CmsTablePage from '../../_shared/cms-table-page.vue';
import { knowledgeColumns, knowledgeRows } from '../data';

defineOptions({ name: 'CmsAiKnowledge' });
</script>

<template>
  <CmsTablePage
    :actions="[{ label: '切片管理' }, { label: '重建向量' }]"
    :columns="knowledgeColumns"
    :rows="knowledgeRows"
    :show-action="true"
    :show-status="true"
    :stats="[
      { label: '知识源总数', value: 86 },
      { label: '可用切片', value: '8,620' },
      { label: '同步中', value: 3 },
      { label: '解析失败', value: 2 },
    ]"
    eyebrow="CMS 咨询服务"
    primary-label="上传知识源"
    title="AI 知识库管理"
  />
</template>
