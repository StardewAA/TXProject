<script lang="ts" setup>
import CmsTablePage from '../../_shared/cms-table-page.vue';
import { reviewColumns, reviewRows } from '../data';

defineOptions({ name: 'CmsExpertReview' });
</script>

<template>
  <CmsTablePage
    :actions="[{ label: '通过入驻', type: 'primary' }, { label: '驳回', danger: true }]"
    :columns="reviewColumns"
    :rows="reviewRows"
    :show-action="true"
    :show-status="true"
    :stats="[
      { label: '待审核申请', value: 2 },
      { label: '本月通过', value: 18 },
      { label: '本月驳回', value: 5 },
      { label: '专家总数', value: 236 },
    ]"
    eyebrow="CMS 专家管理"
    primary-label="批量处理"
    title="专家入驻审核"
  />
</template>
