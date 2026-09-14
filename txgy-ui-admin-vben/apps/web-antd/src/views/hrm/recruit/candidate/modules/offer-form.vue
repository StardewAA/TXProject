<script lang="ts" setup>
import { ref } from 'vue';

import { useVbenForm, useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { createRecruitOffer, submitRecruitOffer } from '#/api/hrm/recruit/offer';

const emit = defineEmits(['success']);
const applicationId = ref(0);
const postId = ref(0);

const [Form, formApi] = useVbenForm({
  commonConfig: { componentProps: { class: 'w-full' }, labelWidth: 110 },
  layout: 'horizontal',
  schema: [
    { fieldName: 'salaryAmount', label: 'Offer 薪资', component: 'InputNumber', componentProps: { min: 0, class: 'w-full' } },
    { fieldName: 'salaryUnit', label: '薪资单位', component: 'Select', componentProps: { options: [{ label: '元/月', value: 1 }, { label: '元/年', value: 2 }] } },
    { fieldName: 'plannedOnboardDate', label: '计划入职日', component: 'DatePicker' },
    { fieldName: 'validUntil', label: 'Offer 有效期', component: 'DatePicker' },
    { fieldName: 'remark', label: 'Offer 备注', component: 'Textarea', componentProps: { rows: 4, maxlength: 500, showCount: true } },
  ],
  showDefaultActions: false,
});

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    modalApi.lock();
    try {
      const values = await formApi.getValues();
      const offerId = await createRecruitOffer({
        applicationId: applicationId.value,
        postId: postId.value,
        salaryAmount: values.salaryAmount,
        salaryUnit: values.salaryUnit,
        plannedOnboardDate: values.plannedOnboardDate,
        validUntil: values.validUntil,
        remark: values.remark,
      });
      await submitRecruitOffer(offerId);
      await modalApi.close();
      emit('success');
      message.success('Offer 已提交审批');
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      applicationId.value = 0;
      postId.value = 0;
      return;
    }
    const data = modalApi.getData<{ applicationId: number; postId: number }>();
    if (!data) return;
    applicationId.value = data.applicationId;
    postId.value = data.postId;
    await formApi.setValues({ salaryUnit: 1, salaryAmount: undefined, plannedOnboardDate: undefined, validUntil: undefined, remark: '' });
  },
});
</script>

<template>
  <Modal title="创建 Offer" class="w-[680px]">
    <Form class="mx-4" />
  </Modal>
</template>
