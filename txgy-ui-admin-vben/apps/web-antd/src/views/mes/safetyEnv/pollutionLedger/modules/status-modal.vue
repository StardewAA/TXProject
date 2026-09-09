<script lang="ts" setup>
import type { MesPollutionLedgerApi } from '#/api/mes/safetyEnv/pollutionLedger';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message, Tag } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { updatePollutionLedgerStatus } from '#/api/mes/safetyEnv/pollutionLedger';
import { $t } from '#/locales';

import {
  LEDGER_FLOW_OPTIONS,
  LEDGER_STATUS_MAP,
  useStatusFormSchema,
} from '../data';

const emit = defineEmits(['success']);
/** 待流转台账行 */
const row = ref<MesPollutionLedgerApi.Ledger | null>(null);

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    formItemClass: 'col-span-1',
    labelWidth: 120,
  },
  layout: 'horizontal',
  schema: useStatusFormSchema(),
  showDefaultActions: false,
  wrapperClass: 'grid-cols-2',
});

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    if (!row.value) {
      return;
    }
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    modalApi.lock();
    const values = (await formApi.getValues()) as MesPollutionLedgerApi.StatusPayload;
    try {
      // 流转到处置中/已回用/已排放/已处置(终态自动清批次污染戳)
      await updatePollutionLedgerStatus({
        id: row.value.id!,
        status: values.status,
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
      row.value = null;
      await formApi.resetForm();
      return;
    }
    const data = modalApi.getData<{ row: MesPollutionLedgerApi.Ledger }>();
    row.value = data.row;
    formApi.setValues({ status: undefined });
  },
});
</script>

<template>
  <Modal title="处置流转" class="w-1/2">
    <div v-if="row" class="mx-4 mb-3 flex items-center gap-2">
      <span class="text-sm text-muted-foreground">
        批次 {{ row.batchNo ?? '-' }}（{{ row.itemName ?? '-' }}）
      </span>
      <Tag :color="LEDGER_STATUS_MAP[row.status ?? '']?.color">
        {{ LEDGER_STATUS_MAP[row.status ?? '']?.text ?? row.status }}
      </Tag>
      <span class="text-sm text-muted-foreground">去向：{{ row.location ?? '-' }}</span>
    </div>
    <Form class="mx-4" />
  </Modal>
</template>
