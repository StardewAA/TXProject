<script lang="ts" setup>
import type { HrmCertificateTypeApi } from '#/api/hrm/certificate/type';

import { computed, ref, watch } from 'vue';

import { useVbenForm, useVbenModal } from '@vben/common-ui';
import { CommonStatusEnum } from '@vben/constants';

import { message } from 'ant-design-vue';

import {
  createCertificateType,
  getCertificateType,
  updateCertificateType,
} from '#/api/hrm/certificate/type';
import { $t } from '#/locales';

import { HrmCertificateDefaultAdvanceDays } from '../../constants';
import { useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<HrmCertificateTypeApi.CertificateType>();
const getTitle = computed(() =>
  formData.value?.id
    ? $t('ui.actionTitle.edit', ['证件类型'])
    : $t('ui.actionTitle.create', ['证件类型']),
);

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    labelWidth: 112,
  },
  layout: 'horizontal',
  schema: useFormSchema(),
  showDefaultActions: false,
});

// 特种作业必然需要复审，勾选后自动打开复审开关，避免提交时被后端拦截
watch(
  () => formApi.form.values?.specialOperation,
  (specialOperation: boolean) => {
    if (specialOperation) {
      formApi.setFieldValue('needReview', true);
    }
  },
);

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    modalApi.lock();
    try {
      const values = await formApi.getValues();
      const data: HrmCertificateTypeApi.CertificateType = {
        id: values.id,
        name: values.name,
        code: values.code,
        specialOperation: values.specialOperation,
        needReview: values.needReview,
        reviewCycleMonths: values.needReview
          ? values.reviewCycleMonths
          : undefined,
        advanceDays: values.advanceDays,
        requireAttachment: values.requireAttachment,
        status: values.status,
        sort: values.sort,
        remark: values.remark,
      };
      await (formData.value?.id
        ? updateCertificateType(data)
        : createCertificateType(data));
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
    const data = modalApi.getData<HrmCertificateTypeApi.CertificateType>();
    if (!data?.id) {
      await formApi.setValues({
        id: undefined,
        name: '',
        code: '',
        specialOperation: false,
        needReview: false,
        reviewCycleMonths: undefined,
        advanceDays: HrmCertificateDefaultAdvanceDays,
        requireAttachment: false,
        status: CommonStatusEnum.ENABLE,
        sort: 0,
        remark: '',
      });
      return;
    }
    modalApi.lock();
    try {
      formData.value = await getCertificateType(data.id);
      await formApi.setValues(formData.value);
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-[680px]">
    <Form class="mx-4" />
  </Modal>
</template>
