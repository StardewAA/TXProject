<script lang="ts" setup>
import type { CrmAfterSaleTicketApi } from '#/api/crm/after-sale/ticket';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { assignAfterSaleTicket } from '#/api/crm/after-sale/ticket';
import { $t } from '#/locales';

import { useAssignFormSchema } from '../data';

const emit = defineEmits(['success']);
const ticketId = ref<number>();

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
  },
  layout: 'horizontal',
  schema: useAssignFormSchema(),
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
      await assignAfterSaleTicket(ticketId.value!, values.assigneeUserId);
      await modalApi.close();
      emit('success');
      message.success($t('ui.actionMessage.operationSuccess'));
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
    await formApi.setValues({ assigneeUserId: data?.assigneeUserId });
  },
});
</script>

<template>
  <Modal title="指派处理人" class="w-1/3">
    <Form class="mx-4" />
  </Modal>
</template>
