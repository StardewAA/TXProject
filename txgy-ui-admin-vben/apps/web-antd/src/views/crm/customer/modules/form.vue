<script lang="ts" setup>
import type { CrmCustomerApi } from '#/api/crm/customer';

import { computed, ref } from 'vue';

import { useVbenForm, useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import {
  createCustomer,
  getCustomer,
  updateCustomer,
} from '#/api/crm/customer';
import { $t } from '#/locales';

import { useFormSchema } from '../data';
import ClueQuickParse from '../../clue/modules/ClueQuickParse.vue';
import type { ParsedClue } from '#/utils/crm/clue-parse';

const emit = defineEmits(['success']);
const formData = ref<CrmCustomerApi.Customer>();
const modalKey = ref(0);
const getTitle = computed(() => {
  return formData.value?.id
    ? $t('ui.actionTitle.edit', ['客户'])
    : $t('ui.actionTitle.create', ['客户']);
});

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    labelWidth: 100,
  },
  wrapperClass: 'grid-cols-2',
  layout: 'horizontal',
  schema: useFormSchema(),
  showDefaultActions: false,
});

/** 识别结果回填：把解析出的字段设置到客户表单（客户表单无 customerCategory 字段，忽略） */
async function onQuickParse(payload: { fields: ParsedClue }) {
  const { fields } = payload;
  const apply: Record<string, any> = {};
  for (const [k, v] of Object.entries(fields)) {
    if (v !== undefined && v !== null && v !== '') {
      apply[k] = v;
    }
  }
  delete apply.customerCategory;
  await formApi.setValues(apply);
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    modalApi.lock();
    // 提交表单
    const data = (await formApi.getValues()) as CrmCustomerApi.Customer;
    try {
      await (formData.value?.id ? updateCustomer(data) : createCustomer(data));
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
    const data = modalApi.getData<CrmCustomerApi.Customer>();
    if (!data || !data.id) {
      return;
    }
    modalApi.lock();
    try {
      formData.value = await getCustomer(data.id);
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
    <ClueQuickParse :key="modalKey" class="mx-4" @apply="onQuickParse" />
    <Form class="mx-4" />
  </Modal>
</template>
