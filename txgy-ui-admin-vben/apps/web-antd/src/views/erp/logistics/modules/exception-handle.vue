<script lang="ts" setup>
import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { message, Textarea } from 'ant-design-vue';

import { handleLogisticsException } from '#/api/erp/logistics';

const emit = defineEmits(['success']);
const exceptionId = ref<number>();
const handleResult = ref('');

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    if (!exceptionId.value || !handleResult.value.trim()) {
      message.error('请填写处理结果');
      return;
    }
    modalApi.lock();
    try {
      await handleLogisticsException({
        id: exceptionId.value,
        handleResult: handleResult.value.trim(),
      });
      message.success('异常处理完成');
      modalApi.close();
      emit('success');
    } finally {
      modalApi.unlock();
    }
  },
  onOpenChange(isOpen) {
    if (!isOpen) return;
    const data = modalApi.getData<{ id: number }>();
    exceptionId.value = data?.id;
    handleResult.value = '';
  },
});
</script>

<template>
  <Modal class="w-[520px]" title="处理物流异常" confirm-text="确认处理">
    <div class="p-4">
      <div class="mb-1 text-sm">处理结果</div>
      <Textarea
        v-model:value="handleResult"
        :maxlength="1024"
        :rows="5"
        placeholder="请输入核实情况和处理结论"
        show-count
      />
    </div>
  </Modal>
</template>
