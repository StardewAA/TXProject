<script lang="ts" setup>
import type { CrmAfterSaleTicketApi } from '#/api/crm/after-sale/ticket';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import dayjs from 'dayjs';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import {
  createAfterSaleTicket,
  getAfterSaleTicket,
  updateAfterSaleTicket,
} from '#/api/crm/after-sale/ticket';
import { $t } from '#/locales';

import { useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<CrmAfterSaleTicketApi.Ticket>();
const getTitle = computed(() => {
  return formData.value?.id
    ? $t('ui.actionTitle.edit', ['问题工单'])
    : $t('ui.actionTitle.create', ['问题工单']);
});

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
  },
  layout: 'horizontal',
  wrapperClass: 'grid-cols-2',
  schema: useFormSchema(),
  showDefaultActions: false,
});

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    modalApi.lock();
    const data = (await formApi.getValues()) as CrmAfterSaleTicketApi.Ticket;
    // 后端 LocalDateTime 只认毫秒时间戳：提交前把表单里的日期字符串转成毫秒
    if (data.startTime) {
      data.startTime = dayjs(data.startTime).valueOf();
    }
    try {
      await (formData.value?.id
        ? updateAfterSaleTicket(data)
        : createAfterSaleTicket(data));
      await modalApi.close();
      emit('success');
      message.success($t('ui.actionMessage.operationSuccess'));
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      formData.value = undefined;
      return;
    }
    const data = modalApi.getData<CrmAfterSaleTicketApi.Ticket>();
    if (!data || !data.id) {
      return;
    }
    modalApi.lock();
    try {
      formData.value = await getAfterSaleTicket(data.id);
      // 详情返回的 startTime 是毫秒时间戳：转成 DatePicker 可解析的日期字符串再回填
      await formApi.setValues({
        ...formData.value,
        startTime: formData.value.startTime
          ? dayjs(Number(formData.value.startTime)).format(
              'YYYY-MM-DD HH:mm:ss',
            )
          : undefined,
      });
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-2/3">
    <Form class="mx-4" />
  </Modal>
</template>
