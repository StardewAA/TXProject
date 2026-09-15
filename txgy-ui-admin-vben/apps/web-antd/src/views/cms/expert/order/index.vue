<script lang="ts" setup>
import CmsTablePage from '../../_shared/cms-table-page.vue';
import { orderColumns, orderRows } from '../data';

defineOptions({ name: 'CmsExpertOrder' });
</script>

<template>
  <CmsTablePage
    :actions="[{ label: '订单详情' }, { label: '催办' }]"
    :columns="orderColumns"
    :rows="orderRows"
    :show-action="true"
    :show-status="true"
    :stats="[
      { label: '本月订单', value: 96 },
      { label: '服务中', value: 18 },
      { label: '待结算', value: 7 },
      { label: '订单金额', value: '¥ 8.6万' },
    ]"
    eyebrow="CMS 专家管理"
    title="咨询订单管理"
  />
</template>
