<script lang="ts" setup>
import type { HrmPolicyApi } from '#/api/hrm/policy';

import { computed, ref } from 'vue';

import { useVbenForm, useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import {
  createPolicy,
  getPolicy,
  updatePolicy,
} from '#/api/hrm/policy';
import { $t } from '#/locales';

import { useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<HrmPolicyApi.Policy>();
const getTitle = computed(() =>
  formData.value?.id
    ? $t('ui.actionTitle.edit', ['政策制度'])
    : $t('ui.actionTitle.create', ['政策制度']),
);

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: { class: 'w-full' },
    labelWidth: 92,
  },
  layout: 'horizontal',
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
    const values = await formApi.getValues();
    try {
      await (formData.value?.id ? updatePolicy(values) : createPolicy(values));
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
    const data = modalApi.getData<HrmPolicyApi.Policy>();
    if (!data?.id) {
      await formApi.setValues({
        category: 'other',
        scope: 'tenant',
        status: 0,
        tags: [],
      });
      return;
    }
    modalApi.lock();
    try {
      formData.value = await getPolicy(data.id);
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
