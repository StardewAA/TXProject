<script lang="ts" setup>
import type { ReportDatasetApi } from '#/api/report/dataset';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import {
  createDataset,
  getDataset,
  updateDataset,
} from '#/api/report/dataset';
import { $t } from '#/locales';

import { useFormSchema } from '../data';
import ParamEditor from './param-editor.vue';

const emit = defineEmits(['success']);
const formData = ref<ReportDatasetApi.Dataset>();
const params = ref<ReportDatasetApi.Param[]>([]);
const isPlatformDataset = computed(() => formData.value?.tenantId === 0);
const getTitle = computed(() => {
  return formData.value?.id
    ? $t('ui.actionTitle.edit', ['报表数据集'])
    : $t('ui.actionTitle.create', ['报表数据集']);
});

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    formItemClass: 'col-span-2',
    labelWidth: 140,
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
    if (params.value.some((item) => !item.name)) {
      message.warning('参数名不能为空');
      return;
    }
    modalApi.lock();
    try {
      const data = (await formApi.getValues()) as ReportDatasetApi.Dataset;
      data.params = params.value;
      await (formData.value?.id
        ? updateDataset(data)
        : createDataset(data));
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
      params.value = [];
      return;
    }
    const data = modalApi.getData<ReportDatasetApi.Dataset>();
    if (!data || !data.id) {
      params.value = [];
      return;
    }
    modalApi.lock();
    try {
      formData.value = await getDataset(data.id);
      params.value = formData.value.params ?? [];
      await formApi.setValues(formData.value);
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-[820px]">
    <a-alert
      v-if="isPlatformDataset"
      class="mx-4 mb-2"
      message="平台级数据集（归属平台）仅平台管理员可维护，当前编辑会因权限被拒绝"
      show-icon
      type="warning"
    />
    <Form class="mx-4" />
    <div class="mx-4 mt-4">
      <ParamEditor v-model="params" />
    </div>
  </Modal>
</template>
