<script lang="ts" setup>
import CmsTablePage from '../../_shared/cms-table-page.vue';
import { chatColumns, chatRows } from '../data';

defineOptions({ name: 'CmsAiChatRecord' });
</script>

<template>
  <CmsTablePage
    :actions="[{ label: '对话详情' }, { label: '标记劣质', danger: true }]"
    :columns="chatColumns"
    :rows="chatRows"
    :show-action="true"
    :show-status="true"
    :stats="[
      { label: '今日咨询', value: 186 },
      { label: '优质回答', value: '92.6%' },
      { label: '标记劣质', value: 7 },
      { label: '无法回答率', value: '4.1%' },
    ]"
    eyebrow="CMS 咨询服务"
    title="AI 对话记录"
  />
</template>
