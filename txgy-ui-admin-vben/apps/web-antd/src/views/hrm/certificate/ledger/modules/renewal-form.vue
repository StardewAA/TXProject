<script lang="ts" setup>
import type { HrmCertificateLedgerApi } from '#/api/hrm/certificate/ledger';

import { computed, ref } from 'vue';

import { useVbenForm, useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { createCertificateRenewal } from '#/api/hrm/certificate/ledger';
import { $t } from '#/locales';

import { useRenewalFormSchema } from '../data';

const emit = defineEmits(['success']);
const certificate = ref<HrmCertificateLedgerApi.CertificateLedger>();
const title = computed(() =>
  certificate.value?.name
    ? `续证/复审 - ${certificate.value.name}`
    : '续证/复审',
);

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    labelWidth: 112,
  },
  layout: 'horizontal',
  schema: useRenewalFormSchema(),
  showDefaultActions: false,
});

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    const values = await formApi.getValues();
    // 复审必须填写复审日期，续证不允许携带该字段，与后端校验保持一致
    if (values.type === 2 && !values.reviewTime) {
      message.warning('复审类型必须填写复审日期');
      return;
    }
    modalApi.lock();
    try {
      await createCertificateRenewal(certificate.value!.id!, {
        type: values.type,
        newNo: values.newNo,
        newStartTime: values.newStartTime,
        newEndTime: values.newEndTime,
        reviewTime: values.type === 2 ? values.reviewTime : undefined,
        issuingAuthority: values.issuingAuthority,
        issuingTime: values.issuingTime,
        attachmentUrl: values.attachmentUrl,
        remark: values.remark,
      });
      await modalApi.close();
      emit('success');
      message.success($t('ui.actionMessage.operationSuccess'));
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      certificate.value = undefined;
      return;
    }
    const data = modalApi.getData<HrmCertificateLedgerApi.CertificateLedger>();
    if (!data?.id) {
      return;
    }
    certificate.value = data;
    await formApi.setValues({
      type: 1,
      newNo: data.no ?? '',
      newStartTime: undefined,
      newEndTime: undefined,
      reviewTime: undefined,
      issuingAuthority: data.issuingAuthority ?? '',
      issuingTime: undefined,
      attachmentUrl: '',
      remark: '',
    });
  },
});
</script>

<template>
  <Modal :title="title" class="w-[720px]">
    <div class="text-muted-foreground mb-3 mx-4 text-sm">
      续证与复审只追加历史记录，不覆盖原证件；办理后证件台账的有效期、编号与附件将更新为新值。
    </div>
    <Form class="mx-4" />
  </Modal>
</template>
