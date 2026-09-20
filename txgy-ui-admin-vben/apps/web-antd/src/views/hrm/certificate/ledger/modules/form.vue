<script lang="ts" setup>
import type { HrmCertificateLedgerApi } from '#/api/hrm/certificate/ledger';

import { computed, ref } from 'vue';

import { useVbenForm, useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import {
  createCertificateLedger,
  getCertificateLedger,
  updateCertificateLedger,
} from '#/api/hrm/certificate/ledger';
import { $t } from '#/locales';

import { useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<HrmCertificateLedgerApi.CertificateLedger>();
const getTitle = computed(() =>
  formData.value?.id
    ? $t('ui.actionTitle.edit', ['证件台账'])
    : $t('ui.actionTitle.create', ['证件台账']),
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

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    modalApi.lock();
    try {
      const values = await formApi.getValues();
      const data: HrmCertificateLedgerApi.CertificateLedger = {
        id: values.id,
        employeeId: values.employeeId,
        typeId: values.typeId,
        name: values.name,
        no: values.no,
        level: values.level,
        startTime: values.startTime,
        endTime: values.endTime,
        reviewTime: values.reviewTime,
        issuingAuthority: values.issuingAuthority,
        issuingTime: values.issuingTime,
        attachmentUrl: values.attachmentUrl,
        sort: values.sort,
        remark: values.remark,
      };
      await (formData.value?.id
        ? updateCertificateLedger(data)
        : createCertificateLedger(data));
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
    const data = modalApi.getData<HrmCertificateLedgerApi.CertificateLedger>();
    if (!data?.id) {
      await formApi.setValues({
        id: undefined,
        employeeId: undefined,
        typeId: undefined,
        name: '',
        no: '',
        level: '',
        startTime: undefined,
        endTime: undefined,
        reviewTime: undefined,
        issuingAuthority: '',
        issuingTime: undefined,
        attachmentUrl: '',
        sort: 0,
        remark: '',
      });
      return;
    }
    modalApi.lock();
    try {
      formData.value = await getCertificateLedger(data.id);
      await formApi.setValues(formData.value);
      // 台账不允许变更归属员工，编辑时锁定该字段
      formApi.updateSchema([
        {
          fieldName: 'employeeId',
          componentProps: { disabled: true },
        },
      ]);
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-[760px]">
    <Form class="mx-4" />
  </Modal>
</template>
