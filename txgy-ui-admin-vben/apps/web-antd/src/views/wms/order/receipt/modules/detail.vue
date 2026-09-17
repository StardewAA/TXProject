<script setup lang="ts">
import { ref } from 'vue';
import { useVbenModal } from '@vben/common-ui';
import ReceiptDetail from '../bpm-detail.vue';
const id = ref<number>();
const [Modal, modalApi] = useVbenModal({
  onOpenChange(open) {
    id.value = open ? modalApi.getData<{ id: number }>().id : undefined;
  },
});
</script>
<template>
  <Modal
    title="入库单详情 / 审批与执行结果"
    class="w-3/4"
    :show-cancel-button="false"
    :show-confirm-button="false"
  >
    <ReceiptDetail v-if="id" :id="id" @navigate="modalApi.close()" />
  </Modal>
</template>
