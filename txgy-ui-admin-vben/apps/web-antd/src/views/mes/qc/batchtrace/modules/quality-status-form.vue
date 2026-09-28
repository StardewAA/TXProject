<script lang="ts" setup>
import type { MesWmBatchApi } from '#/api/mes/wm/batch';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { updateBatchQualityStatus } from '#/api/mes/wm/batch';
import { $t } from '#/locales';

import { useQualityStatusFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<MesWmBatchApi.Batch>();
const batchCode = ref<string>('');

const getTitle = computed(() =>
  $t('ui.actionTitle.edit', ['批次质量状态']),
);

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    labelWidth: 110,
  },
  layout: 'horizontal',
  schema: useQualityStatusFormSchema(),
  showDefaultActions: false,
});

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    const values = await formApi.getValues();
    if (values?.qualityStatus === undefined || values?.qualityStatus === null) {
      message.warning('请选择质量状态');
      return;
    }
    if (!formData.value?.id) {
      message.warning('批次编号缺失');
      return;
    }
    modalApi.lock();
    try {
      await updateBatchQualityStatus({
        id: formData.value.id,
        qualityStatus: values.qualityStatus as number,
      });
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
      batchCode.value = '';
      return;
    }
    const data = modalApi.getData<MesWmBatchApi.Batch>();
    formData.value = data;
    batchCode.value = data?.code ?? '';
    // 回显当前质量状态
    await formApi.setValues({ id: data?.id, qualityStatus: data?.qualityStatus });
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-1/3">
    <div class="mx-4 mb-2 text-sm text-gray-500">
      批次：{{ batchCode }}
    </div>
    <Form class="mx-4" />
  </Modal>
</template>
