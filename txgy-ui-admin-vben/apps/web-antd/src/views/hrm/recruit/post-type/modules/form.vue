<script lang="ts" setup>
import type { HrmRecruitPostTypeApi } from '#/api/hrm/recruit/post/type';

import { computed, ref } from 'vue';

import { useVbenForm, useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import {
  createRecruitPostType,
  updateRecruitPostType,
} from '#/api/hrm/recruit/post/type';
import { $t } from '#/locales';

import { useFormSchema } from '../data';

const emit = defineEmits<{ success: [] }>();
const formData = ref<HrmRecruitPostTypeApi.RecruitPostType>();
const getTitle = computed(() => (formData.value?.id ? '编辑职位类型' : '新增职位类型'));

const [Form, formApi] = useVbenForm({
  commonConfig: { componentProps: { class: 'w-full' }, labelWidth: 90 },
  layout: 'horizontal',
  schema: useFormSchema(() => formData.value?.id),
  showDefaultActions: false,
});

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;
    modalApi.lock();
    try {
      const values = await formApi.getValues();
      const data = { ...values, parentId: values.parentId || 0, sort: values.sort ?? 0 } as HrmRecruitPostTypeApi.RecruitPostType;
      await (formData.value?.id ? updateRecruitPostType(data) : createRecruitPostType(data));
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
    const data = modalApi.getData<HrmRecruitPostTypeApi.RecruitPostType>();
    formData.value = data?.id ? data : undefined;
    await formApi.setValues(data?.id ? data : { name: '', parentId: 0, sort: 0 });
  },
});

</script>

<template>
  <Modal :title="getTitle" class="w-[620px]">
    <Form class="mx-4" />
  </Modal>
</template>
