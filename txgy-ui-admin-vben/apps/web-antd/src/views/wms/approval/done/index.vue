<script lang="ts" setup>
import BpmDoneTask from '#/views/bpm/task/done/index.vue';

const WMS_PROCESS_DEFINITION_KEYS =
  'wms_receipt_approval,wms_shipment_approval';
</script>

<template>
  <BpmDoneTask
    detail-route-name="WmsApprovalDetail"
    :process-definition-keys="WMS_PROCESS_DEFINITION_KEYS"
    :withdraw-enabled="false"
  />
</template>
