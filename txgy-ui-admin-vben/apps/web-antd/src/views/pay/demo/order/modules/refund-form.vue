<script lang="ts" setup>
import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { refundDemoOrder } from '#/api/pay/demo/order';
import { $t } from '#/locales';

import { REFUND_REASON_OPTIONS } from '../data';

const emit = defineEmits(['success']);

const orderId = ref<number>();
const reason = ref<string>();
const customReason = ref<string>();

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    // 校验：必须选择原因；选择"其他"时必须填写自定义原因
    if (!reason.value) {
      message.warning('请选择退款原因');
      return;
    }
    const finalReason =
      reason.value === '其他'
        ? customReason.value?.trim()
        : reason.value;
    if (!finalReason) {
      message.warning('请填写自定义退款原因');
      return;
    }
    modalApi.lock();
    try {
      await refundDemoOrder(orderId.value as number, finalReason);
      message.success('发起退款成功！');
      modalApi.close();
      emit('success');
    } finally {
      modalApi.unlock();
    }
  },
  onOpenChange(isOpen: boolean) {
    if (isOpen) {
      orderId.value = modalApi.getData<{ id: number }>().id;
      reason.value = undefined;
      customReason.value = undefined;
    }
  },
});
</script>

<template>
  <Modal class="w-[420px]" title="发起退款">
    <div class="flex flex-col gap-4 px-2">
      <div>
        <div class="mb-2">退款原因</div>
        <a-select
          v-model:value="reason"
          class="w-full"
          placeholder="请选择退款原因"
          :options="REFUND_REASON_OPTIONS"
        />
      </div>
      <div v-if="reason === '其他'">
        <div class="mb-2">自定义原因</div>
        <a-input
          v-model:value="customReason"
          placeholder="请输入退款原因（最多 100 字）"
          :maxlength="100"
          show-count
        />
      </div>
      <div class="text-sm opacity-60">
        提交后将按订单全额退款，退款结果以支付渠道实际处理为准。
      </div>
    </div>
  </Modal>
</template>
