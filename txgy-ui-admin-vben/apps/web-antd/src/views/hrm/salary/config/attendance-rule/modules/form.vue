<script lang="ts" setup>
import type { HrmSalaryAttendanceRuleApi } from '#/api/hrm/salary/attendance-rule';

import { computed, ref } from 'vue';

import { useVbenForm, useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import {
  createAttendanceRule,
  getAttendanceRule,
  updateAttendanceRule,
} from '#/api/hrm/salary/attendance-rule';
import { $t } from '#/locales';

import { useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<HrmSalaryAttendanceRuleApi.AttendanceRule>();
const getTitle = computed(() =>
  formData.value?.id
    ? $t('ui.actionTitle.edit', ['考勤计薪规则'])
    : $t('ui.actionTitle.create', ['考勤计薪规则']),
);

const [Form, formApi] = useVbenForm({
  commonConfig: { componentProps: { class: 'w-full' }, labelWidth: 130 },
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
        ? updateAttendanceRule(values)
        : createAttendanceRule(values));
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
    const data = modalApi.getData<HrmSalaryAttendanceRuleApi.AttendanceRule>();
    modalApi.lock();
    try {
      if (!data?.id) {
        formData.value = undefined;
        await formApi.resetForm();
        return;
      }
      formData.value = await getAttendanceRule(data.id);
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
