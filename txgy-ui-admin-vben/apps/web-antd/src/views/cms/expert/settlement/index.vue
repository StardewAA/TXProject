<script lang="ts" setup>
import CmsTablePage from '../../_shared/cms-table-page.vue';
import { settlementColumns, settlementRows } from '../data';

defineOptions({ name: 'CmsExpertSettlement' });
</script>

<template>
  <CmsTablePage
    :actions="[{ label: '确认结算', type: 'primary' }, { label: '驳回', danger: true }]"
    :columns="settlementColumns"
    :rows="settlementRows"
    :show-action="true"
    :show-status="true"
    :stats="[
      { label: '本月应结算', value: '¥ 12.9万' },
      { label: '待审核结算', value: 6 },
      { label: '已结算', value: 58 },
      { label: '服务费率', value: '10%' },
    ]"
    eyebrow="CMS 专家管理"
    primary-label="发起批量结算"
    title="结算管理"
  />
</template>
