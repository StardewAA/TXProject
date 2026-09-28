<script lang="ts" setup>
import CmsTablePage from '../../_shared/cms-table-page.vue';
import { consultStatColumns, consultStatRows } from '../data';

defineOptions({ name: 'CmsConsultStat' });
</script>

<template>
  <CmsTablePage
    :columns="consultStatColumns"
    :rows="consultStatRows"
    :stats="[
      { label: '本周提问量', value: '1,082' },
      { label: '综合准确率', value: '92.6%' },
      { label: '无法回答率', value: '4.1%' },
      { label: '平均响应', value: '6.4秒' },
    ]"
    eyebrow="CMS 统计分析"
    title="咨询数据统计"
  />
</template>
