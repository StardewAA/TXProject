<script lang="ts" setup>
import type { CrmAfterSaleWarrantyApi } from '#/api/crm/after-sale/warranty';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import {
  createAfterSaleWarranty,
  getAfterSaleWarranty,
  updateAfterSaleWarranty,
} from '#/api/crm/after-sale/warranty';
import { $t } from '#/locales';

import { useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<CrmAfterSaleWarrantyApi.Warranty>();
const getTitle = computed(() => {
  return formData.value?.id
    ? $t('ui.actionTitle.edit', ['质保档案'])
    : $t('ui.actionTitle.create', ['质保档案']);
});

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
  },
  layout: 'horizontal',
  wrapperClass: 'grid-cols-2',
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
    const data = (await formApi.getValues()) as CrmAfterSaleWarrantyApi.Warranty & {
      /** 纯界面预览字段（见 data.ts），不提交给后端 */
      statusPreview?: string;
    };
    delete data.statusPreview;
    try {
      let result: CrmAfterSaleWarrantyApi.UpdateResp | undefined;
      if (formData.value?.id) {
        // 编辑：后端会按最新「到期日」重算质保状态，并返回变更详情
        result = await updateAfterSaleWarranty(data);
      } else {
        await createAfterSaleWarranty(data);
      }
      await modalApi.close();
      emit('success');
      // 如实反馈状态是否被自动更新，避免用户觉得「状态在界面上悄悄变了」
      if (result?.statusChanged) {
        message.success(result.message ?? $t('ui.actionMessage.operationSuccess'));
        if (result.needManualConfirm) {
          message.warning(
            '质保状态已从「已到期终止」恢复为有效状态，但此前因到期被停用的回访计划不会自动恢复，请到「回访计划」中确认是否需要重新启用',
          );
        }
      } else {
        message.success(
          result?.message ?? $t('ui.actionMessage.operationSuccess'),
        );
      }
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      formData.value = undefined;
      return;
    }
    const data = modalApi.getData<CrmAfterSaleWarrantyApi.Warranty>();
    if (!data || !data.id) {
      return;
    }
    modalApi.lock();
    try {
      formData.value = await getAfterSaleWarranty(data.id);
      await formApi.setValues(formData.value);
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-2/3">
    <Form class="mx-4" />
  </Modal>
</template>
