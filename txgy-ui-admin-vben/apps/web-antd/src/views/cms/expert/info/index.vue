<script lang="ts" setup>
import CmsTablePage from '../../_shared/cms-table-page.vue';
import { expertColumns, expertRows } from '../data';

defineOptions({ name: 'CmsExpertInfo' });
</script>

<template>
  <CmsTablePage
    :actions="[{ label: '编辑' }, { label: '停用', danger: true }]"
    :columns="expertColumns"
    :rows="expertRows"
    :show-action="true"
    :show-status="true"
    :stats="[
      { label: '启用专家', value: 201 },
      { label: '停用专家', value: 35 },
      { label: '平均评分', value: 4.8 },
      { label: '累计服务', value: '1,024单' },
    ]"
    eyebrow="CMS 专家管理"
    primary-label="新增专家"
    title="专家信息维护"
  />
</template>
