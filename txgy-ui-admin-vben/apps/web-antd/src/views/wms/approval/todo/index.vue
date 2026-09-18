<script lang="ts" setup>
import BpmTodoTask from '#/views/bpm/task/todo/index.vue';

const WMS_PROCESS_DEFINITION_KEYS =
  'wms_receipt_approval,wms_shipment_approval';
</script>

<template>
  <BpmTodoTask
    detail-route-name="WmsApprovalDetail"
    :process-definition-keys="WMS_PROCESS_DEFINITION_KEYS"
  />
</template>
