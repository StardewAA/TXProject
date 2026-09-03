<script lang="ts" setup>
import CmsTablePage from '../../_shared/cms-table-page.vue';
import { pushColumns, pushRows } from '../data';

defineOptions({ name: 'CmsCrawlerPush' });
</script>

<template>
  <CmsTablePage
    :actions="[{ label: '查看明细' }, { label: '重新推送', type: 'primary' }]"
    :columns="pushColumns"
    :rows="pushRows"
    :show-action="true"
    :show-status="true"
    :stats="[
      { label: '今日推送', value: 3 },
      { label: '成功用户', value: 186 },
      { label: '部分成功', value: 1 },
      { label: '失败', value: 1 },
    ]"
    eyebrow="CMS 政策采集"
    title="推送记录"
  />
</template>
