<script lang="ts" setup>
import BpmProcessInstanceMy from '#/views/bpm/processInstance/index.vue';

const WMS_PROCESS_DEFINITION_KEYS =
  'wms_receipt_approval,wms_shipment_approval';
</script>

<template>
  <BpmProcessInstanceMy
    detail-route-name="WmsApprovalDetail"
    :process-definition-keys="WMS_PROCESS_DEFINITION_KEYS"
  />
</template>
