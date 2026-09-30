<script lang="ts" setup>
import type { WmsItemApi } from '#/api/wms/md/item';

import { computed, nextTick, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { createItem, getItem, updateItem } from '#/api/wms/md/item';
import { $t } from '#/locales';

import { MANAGE_DEFAULTS, MANAGE_FIELDS, useFormSchema } from '../data';
import SkuForm from './sku-form.vue';
import QualityReportPanel from './quality-report-panel.vue';

defineOptions({ name: 'WmsItemForm' });

const emit = defineEmits(['success']);
const formData = ref<WmsItemApi.Item>();
const skuFormRef = ref<any>();
const initialQualityReport = ref<any>();

/**
 * 后端字段 → 表单值：把 4 个管理开关字段收进 manage 对象。
 *
 * manage 为对象类型，setValues 是深合并，若不清空会残留上一条商品的值，
 * 因此这里对全部开关做显式兜底（缺失一律 false）。
 */
function toFormValues(data: Partial<WmsItemApi.Item> = {}) {
  const manage: Record<string, boolean> = { ...MANAGE_DEFAULTS };
  for (const field of MANAGE_FIELDS) {
    manage[field] = !!data[field as keyof WmsItemApi.Item];
  }
  return { ...data, manage };
}

/** 表单值 → 后端字段：把 manage 对象摊平成 4 个开关字段 */
function toSubmitValues(values: WmsItemApi.Item) {
  const { manage, ...rest } = values as WmsItemApi.Item & {
    manage?: Record<string, boolean>;
  };
  const data: Record<string, any> = { ...rest };
  for (const field of MANAGE_FIELDS) {
    data[field] = !!(manage ?? {})[field];
  }
  return data as WmsItemApi.Item;
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
    // 116：可容纳 5 字标签 + 必填星号 + help 图标（「管理方式」带 ⓘ）
    labelWidth: 116,
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
      message.warning((error as Error).message);
      return;
    }
    modalApi.lock();
    // 提交表单
    const values = await formApi.getValues();
    const data = toSubmitValues(values as WmsItemApi.Item);
    data.skus = skuFormRef.value?.getRows() || [];
    if (!formData.value?.id) data.initialQualityReport = initialQualityReport.value;
    try {
      await (formData.value?.id ? updateItem(data) : createItem(data));
      // 关闭并提示
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
      initialQualityReport.value = undefined;
      return;
    }
    formApi.setState({ schema: useFormSchema(formApi) });
    await resetSkuForm();
    // 加载数据
    const data = modalApi.getData<WmsItemApi.Item>();
    if (!data || !data.id) {
      // 新增：清空上一条记录残留的值，再写入默认值
      await formApi.resetForm();
      await formApi.setValues(toFormValues());
      return;
    }
    modalApi.lock();
    try {
      formData.value = await getItem(data.id);
      // 设置到 values
      await formApi.resetForm();
      await formApi.setValues(toFormValues(formData.value));
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
      :current="formData?.currentQualityReport"
      :initial="initialQualityReport"
      :item-id="formData?.id"
      @change="initialQualityReport = $event"
    />
    <SkuForm ref="skuFormRef" class="mx-4 mt-4" />
  </Modal>
</template>

<style scoped>
/* 标签不换行，且不被压缩
   注意：本表单用的是 @vben-core/shadcn-ui 的 FormLabel（渲染为
   [data-slot="form-label"]），不是 antd 的 .ant-form-item-label。 */
:deep([data-slot='form-label']) {
  flex-shrink: 0;
  white-space: nowrap;
}
</style>
