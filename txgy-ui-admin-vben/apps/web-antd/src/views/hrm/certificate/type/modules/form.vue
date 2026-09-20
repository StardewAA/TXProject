<script lang="ts" setup>
import type { HrmCertificateTypeApi } from '#/api/hrm/certificate';

import { computed, ref } from 'vue';

import { useVbenForm, useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import {
  createCertificateType,
  getCertificateType,
  updateCertificateType,
} from '#/api/hrm/certificate';
import { $t } from '#/locales';

import { useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<HrmCertificateTypeApi.CertificateType>();
const getTitle = computed(() =>
  formData.value?.id
    ? $t('ui.actionTitle.edit', ['证照类型'])
    : $t('ui.actionTitle.create', ['证照类型']),
);

const [Form, formApi] = useVbenForm({
  commonConfig: { componentProps: { class: 'w-full' }, labelWidth: 112 },
  layout: 'horizontal',
  schema: useFormSchema(),
  showDefaultActions: false,
  wrapperClass: 'grid-cols-2',
});

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;
    modalApi.lock();
    const values = await formApi.getValues();
    try {
      await (formData.value?.id
        ? updateCertificateType(values)
        : createCertificateType(values));
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
    const data = modalApi.getData<HrmCertificateTypeApi.CertificateType>();
    modalApi.lock();
    try {
      if (!data?.id) {
        await formApi.setValues({ warnDays: 60, sort: 0, status: 0 });
        return;
      }
      formData.value = await getCertificateType(data.id);
      await formApi.setValues(formData.value);
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-[760px]">
    <Form class="mx-4" />
  </Modal>
</template>
