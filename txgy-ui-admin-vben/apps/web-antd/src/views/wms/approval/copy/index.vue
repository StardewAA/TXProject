<script lang="ts" setup>
import BpmCopyTask from '#/views/bpm/task/copy/index.vue';

const WMS_PROCESS_DEFINITION_KEYS =
  'wms_receipt_approval,wms_shipment_approval';
</script>

<template>
  <BpmCopyTask
    detail-route-name="WmsApprovalDetail"
    :process-definition-keys="WMS_PROCESS_DEFINITION_KEYS"
  />
</template>
