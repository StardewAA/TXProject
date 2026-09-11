<script lang="ts" setup>
import type { CrmProductApi } from '#/api/crm/product';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { createProduct, getProduct, updateProduct } from '#/api/crm/product';
import ClueQuickParse from '../../clue/modules/ClueQuickParse.vue';
import { $t } from '#/locales';

import { useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<CrmProductApi.Product>();

/** 识别结果回填产品信息 */
async function onQuickParse(payload: { fields: any }) {
  const fields = payload.fields ?? {};
  const apply: Record<string, any> = {};
  if (fields.name) apply.name = fields.name;
  if (fields.no) apply.no = fields.no;
  if (fields.price != null) apply.price = fields.price;
  if (fields.description) apply.description = fields.description;
  if (fields.unit != null) apply.unit = fields.unit;
  if (fields.status != null) apply.status = fields.status;
  if (fields.categoryId != null) apply.categoryId = fields.categoryId;
  if (fields.ownerUserId) apply.ownerUserId = fields.ownerUserId;
  await formApi.setValues(apply);
}
const getTitle = computed(() => {
  return formData.value?.id
    ? $t('ui.actionTitle.edit', ['产品'])
    : $t('ui.actionTitle.create', ['产品']);
});

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    formItemClass: 'col-span-2',
    labelWidth: 80,
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
    // 提交表单
    const data = (await formApi.getValues()) as CrmProductApi.Product;
    try {
      await (formData.value?.id ? updateProduct(data) : createProduct(data));
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
      return;
    }
    // 加载数据
    const data = modalApi.getData<CrmProductApi.Product>();
    if (!data || !data.id) {
      return;
    }
    modalApi.lock();
    try {
      formData.value = await getProduct(data.id);
      // 设置到 values
      await formApi.setValues(formData.value);
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-2/5">
    <ClueQuickParse mode="product" class="mx-4" @apply="onQuickParse" />
    <Form class="mx-4" />
  </Modal>
</template>
