<script lang="ts" setup>
import type { CrmAfterSaleTicketApi } from '#/api/crm/after-sale/ticket';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { updateAfterSaleTicketStatus } from '#/api/crm/after-sale/ticket';

import { useFinishFormSchema } from '../data';

const emit = defineEmits(['success']);
const ticketId = ref<number>();

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
  },
  layout: 'horizontal',
  schema: useFinishFormSchema(),
  showDefaultActions: false,
});

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    modalApi.lock();
    try {
      const values = await formApi.getValues();
      await updateAfterSaleTicketStatus(
        ticketId.value!,
        3,
        values.content as string,
      );
      await modalApi.close();
      emit('success');
      message.success('工单已完成');
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      return;
    }
    const data = modalApi.getData<CrmAfterSaleTicketApi.Ticket>();
    ticketId.value = data?.id;
  },
});
</script>

<template>
  <Modal title="完成工单" class="w-1/2">
    <Form class="mx-4" />
  </Modal>
</template>
