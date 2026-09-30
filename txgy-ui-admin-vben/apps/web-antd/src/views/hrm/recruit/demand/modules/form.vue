<script lang="ts" setup>
import type { HrmRecruitDemandApi } from '#/api/hrm/recruit/demand';

import { computed, ref } from 'vue';

import { useVbenForm, useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import {
  createRecruitDemand,
  getRecruitDemand,
  updateRecruitDemand,
} from '#/api/hrm/recruit/demand';
import { $t } from '#/locales';

import { useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<HrmRecruitDemandApi.RecruitDemand>();
const getTitle = computed(() =>
  formData.value?.id
    ? $t('ui.actionTitle.edit', ['用工需求单'])
    : $t('ui.actionTitle.create', ['用工需求单']),
);

const [Form, formApi] = useVbenForm({
  commonConfig: { componentProps: { class: 'w-full' }, labelWidth: 120 },
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
        ? updateRecruitDemand(values)
        : createRecruitDemand(values));
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
    const data = modalApi.getData<HrmRecruitDemandApi.RecruitDemand>();
    modalApi.lock();
    try {
      if (!data?.id) {
        formData.value = undefined;
        await formApi.resetForm();
        return;
      }
      formData.value = await getRecruitDemand(data.id);
      await formApi.setValues(formData.value);
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-[820px]">
    <Form class="mx-4" />
  </Modal>
</template>
