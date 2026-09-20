<script lang="ts" setup>
import type { HrmCertificateWarningApi } from '#/api/hrm/certificate/warning';

import { computed, ref } from 'vue';

import { useVbenForm, useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { handleCertificateWarning } from '#/api/hrm/certificate/warning';
import { $t } from '#/locales';

import { HrmCertificateWarningStatus } from '../../constants';
import { getWarningTypeLabel, useHandleFormSchema } from '../data';

const emit = defineEmits(['success']);
const warning = ref<HrmCertificateWarningApi.CertificateWarning>();
const title = computed(() =>
  warning.value?.status === HrmCertificateWarningStatus.PENDING
    ? '处理证件预警'
    : '重新启用证件预警',
);

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    labelWidth: 92,
  },
  layout: 'horizontal',
  schema: useHandleFormSchema(HrmCertificateWarningStatus.PENDING),
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
      await handleCertificateWarning({
        id: warning.value!.id!,
        status: values.status,
        handleRemark: values.handleRemark,
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
      warning.value = undefined;
      return;
    }
    const data =
      modalApi.getData<HrmCertificateWarningApi.CertificateWarning>();
    if (!data?.id) {
      return;
    }
    warning.value = data;
    // 目标状态的可选项随当前状态变化：待处理只能处理或忽略，已处理/已忽略只能重新启用
    formApi.updateSchema(useHandleFormSchema(data.status));
    await formApi.setValues({
      id: data.id,
      status:
        data.status === HrmCertificateWarningStatus.PENDING
          ? HrmCertificateWarningStatus.HANDLED
          : HrmCertificateWarningStatus.PENDING,
      handleRemark: '',
    });
  },
});
</script>

<template>
  <Modal :title="title" class="w-[620px]">
    <div v-if="warning" class="text-muted-foreground mb-3 mx-4 text-sm">
      <div>
        员工：{{ warning.employeeName || '-' }}（{{ warning.employeeJobNumber || '-' }}）
      </div>
      <div>
        证件：{{ warning.certificateName || '-' }}
        <span v-if="warning.certificateNo">（{{ warning.certificateNo }}）</span>
      </div>
      <div>
        预警类型：{{ getWarningTypeLabel(warning.type) }}，剩余天数：{{
          warning.remainDays ?? '-'
        }}
      </div>
    </div>
    <Form class="mx-4" />
  </Modal>
</template>
