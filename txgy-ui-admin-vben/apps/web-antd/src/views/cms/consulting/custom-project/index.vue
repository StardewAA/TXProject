<script lang="ts" setup>
import CmsTablePage from '../../_shared/cms-table-page.vue';
import { projectColumns, projectRows } from '../data';

defineOptions({ name: 'CmsCustomProject' });
</script>

<template>
  <CmsTablePage
    :actions="[{ label: '项目详情' }, { label: '派单', type: 'primary' }]"
    :columns="projectColumns"
    :rows="projectRows"
    :show-action="true"
    :show-status="true"
    :stats="[
      { label: '本月项目', value: 12 },
      { label: '待报价', value: 3 },
      { label: '服务中', value: 5 },
      { label: '项目金额', value: '¥ 6.2万' },
    ]"
    eyebrow="CMS 咨询服务"
    primary-label="新建定制咨询"
    title="定制咨询项目"
  />
</template>
