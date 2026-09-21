<script lang="ts" setup>
import type { HrmEmployeeBlacklistApi } from '#/api/hrm/employee/blacklist';

import { computed, ref } from 'vue';

import { useVbenForm, useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import {
  createEmployeeBlacklist,
  getEmployeeBlacklist,
  updateEmployeeBlacklist,
} from '#/api/hrm/employee/blacklist';
import { $t } from '#/locales';

import { useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<HrmEmployeeBlacklistApi.Blacklist>();
const getTitle = computed(() =>
  formData.value?.id
    ? $t('ui.actionTitle.edit', ['黑名单'])
    : $t('ui.actionTitle.create', ['黑名单']),
);

const [Form, formApi] = useVbenForm({
  commonConfig: { componentProps: { class: 'w-full' }, labelWidth: 100 },
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
        ? updateEmployeeBlacklist(values)
        : createEmployeeBlacklist(values));
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
    const data = modalApi.getData<HrmEmployeeBlacklistApi.Blacklist>();
    modalApi.lock();
    try {
      if (!data?.id) {
        formData.value = undefined;
        await formApi.resetForm();
        return;
      }
      formData.value = await getEmployeeBlacklist(data.id);
      await formApi.setValues(formData.value);
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-[720px]">
    <Form class="mx-4" />
  </Modal>
</template>
