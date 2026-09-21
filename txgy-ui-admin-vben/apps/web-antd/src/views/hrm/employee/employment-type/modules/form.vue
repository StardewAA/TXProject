<script lang="ts" setup>
import type { HrmEmploymentTypeApi } from '#/api/hrm/employment-type';

import { computed, ref } from 'vue';

import { useVbenForm, useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { getContractTemplateList } from '#/api/hrm/contract/template';
import {
  createEmploymentType,
  getEmploymentType,
  updateEmploymentType,
} from '#/api/hrm/employment-type';
import { $t } from '#/locales';

import { useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<HrmEmploymentTypeApi.EmploymentType>();
const getTitle = computed(() =>
  formData.value?.id
    ? $t('ui.actionTitle.edit', ['用工形式'])
    : $t('ui.actionTitle.create', ['用工形式']),
);

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: { class: 'w-full' },
    labelWidth: 110,
  },
  layout: 'horizontal',
  schema: useFormSchema(),
  showDefaultActions: false,
  wrapperClass: 'grid-cols-2',
});

/** 加载可选的合同模板（只取劳动合同类） */
async function loadTemplateOptions() {
  const list = await getContractTemplateList(1);
  await formApi.updateSchema([
    {
      fieldName: 'contractTemplateId',
      componentProps: {
        options: list.map((item) => ({
          label: item.name ?? `模板 ${item.id}`,
          value: item.id!,
        })),
      },
    },
  ]);
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    modalApi.lock();
    const values = await formApi.getValues();
    try {
      await (formData.value?.id
        ? updateEmploymentType(values)
        : createEmploymentType(values));
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
    const data = modalApi.getData<HrmEmploymentTypeApi.EmploymentType>();
    modalApi.lock();
    try {
      await loadTemplateOptions();
      if (!data?.id) {
        await formApi.setValues({
          joinRegular: true,
          needFaceClock: false,
          needWorkDay: false,
          salaryMode: 1,
          sort: 0,
          status: 0,
        });
        return;
      }
      formData.value = await getEmploymentType(data.id);
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
