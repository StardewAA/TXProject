<script lang="ts" setup>
import type { HrmDeptHeadcountApi } from '#/api/hrm/employee/headcount';

import { computed, ref } from 'vue';

import { useVbenForm, useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import {
  createDeptHeadcount,
  getDeptHeadcount,
  updateDeptHeadcount,
} from '#/api/hrm/employee/headcount';
import { $t } from '#/locales';

import { useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<HrmDeptHeadcountApi.DeptHeadcount>();
const getTitle = computed(() =>
  formData.value?.id
    ? $t('ui.actionTitle.edit', ['部门编制'])
    : $t('ui.actionTitle.create', ['部门编制']),
);

const [Form, formApi] = useVbenForm({
  commonConfig: { componentProps: { class: 'w-full' }, labelWidth: 110 },
  layout: 'horizontal',
  schema: useFormSchema(),
  showDefaultActions: false,
  wrapperClass: 'grid-cols-1',
});

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;
    modalApi.lock();
    const values = await formApi.getValues();
    try {
      await (formData.value?.id
        ? updateDeptHeadcount(values)
        : createDeptHeadcount(values));
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
    const data = modalApi.getData<HrmDeptHeadcountApi.DeptHeadcount>();
    modalApi.lock();
    try {
      if (!data?.id) {
        formData.value = undefined;
        await formApi.resetForm();
        return;
      }
      formData.value = await getDeptHeadcount(data.id);
      await formApi.setValues(formData.value);
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-[640px]">
    <Form class="mx-4" />
  </Modal>
</template>
