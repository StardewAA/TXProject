<script lang="ts" setup>
import type { WmsItemApi } from '#/api/wms/md/item';
import type { QualityReport } from '#/api/wms/md/item/quality-report';

import { computed, nextTick, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { ElMessage } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import { createItem, getItem, updateItem } from '#/api/wms/md/item';
import { $t } from '#/locales';

import { useFormSchema } from '../data';
import SkuForm from './sku-form.vue';
import QualityReportPanel from './quality-report-panel.vue';

defineOptions({ name: 'WmsItemForm' });

const emit = defineEmits(['success']);
const formData = ref<WmsItemApi.Item>();
const skuFormRef = ref<any>();
const initialQualityReport = ref<QualityReport>();
const currentQualityReport = ref<QualityReport>();
function handleQualityChange(value?: QualityReport) {
  if (formData.value?.id) currentQualityReport.value = value;
  else initialQualityReport.value = value;
}
const getTitle = computed(() => {
  return formData.value?.id
    ? $t('ui.actionTitle.edit', ['商品'])
    : $t('ui.actionTitle.create', ['商品']);
});

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    labelWidth: 88,
  },
  wrapperClass: 'grid-cols-2',
  layout: 'horizontal',
  schema: [],
  showDefaultActions: false,
});

async function resetSkuForm(item?: WmsItemApi.Item) {
  await nextTick();
  await skuFormRef.value?.setRows(item?.skus);
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    try {
      skuFormRef.value?.validate();
    } catch (error) {
      ElMessage.warning((error as Error).message);
      return;
    }
    modalApi.lock();
    // 提交表单
    const data = (await formApi.getValues()) as WmsItemApi.Item;
    data.skus = skuFormRef.value?.getRows() || [];
    if (!formData.value?.id && initialQualityReport.value) {
      data.initialQualityReport = initialQualityReport.value;
    }
    try {
      await (formData.value?.id ? updateItem(data) : createItem(data));
      // 关闭并提示
      await modalApi.close();
      emit('success');
      ElMessage.success($t('ui.actionMessage.operationSuccess'));
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      formData.value = undefined;
      initialQualityReport.value = undefined;
      currentQualityReport.value = undefined;
      return;
    }
    formApi.setState({ schema: useFormSchema(formApi) });
    await resetSkuForm();
    initialQualityReport.value = undefined;
    currentQualityReport.value = undefined;
    // 加载数据
    const data = modalApi.getData<WmsItemApi.Item>();
    if (!data || !data.id) {
      return;
    }
    modalApi.lock();
    try {
      formData.value = await getItem(data.id);
      currentQualityReport.value = formData.value.currentQualityReport;
      // 设置到 values
      await formApi.setValues(formData.value);
      await resetSkuForm(formData.value);
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-3/4">
    <Form class="mx-4" />
    <QualityReportPanel
      :current="currentQualityReport"
      :initial="initialQualityReport"
      :item-id="formData?.id"
      @change="handleQualityChange"
    />
    <SkuForm ref="skuFormRef" class="mx-4 mt-4" />
  </Modal>
</template>
