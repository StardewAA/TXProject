<script lang="ts" setup>
import type { MesSetPollutionCheckApi } from '#/api/mes/safetyEnv/pollutionCheck';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Alert, message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { reviewPollutionCheck } from '#/api/mes/safetyEnv/pollutionCheck';
import { $t } from '#/locales';

import { AI_RESULT_MAP, useReviewFormSchema } from '../data';

const emit = defineEmits(['success']);
/** 待复核记录(含 AI 初筛结果，只读) */
const row = ref<MesSetPollutionCheckApi.PollutionCheck | null>(null);

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    formItemClass: 'col-span-1',
    labelWidth: 120,
  },
  layout: 'horizontal',
  schema: useReviewFormSchema(),
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
    const values = (await formApi.getValues()) as MesSetPollutionCheckApi.ReviewPayload;
    try {
      // 终态收口：无污染/有污染 + 处置与去向(留空由后端按环节×结论取默认)
      await reviewPollutionCheck({
        id: row.value.id!,
        reviewResult: values.reviewResult,
        disposition: values.disposition,
        storageMethod: values.storageMethod,
        location: values.location,
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
    const data = modalApi.getData<{ row: MesSetPollutionCheckApi.PollutionCheck }>();
    row.value = data.row;
    formApi.setValues({ reviewResult: undefined });
  },
});
</script>

<template>
  <Modal title="人工复核（终态：无污染 / 有污染）" class="w-2/3">
    <Alert
      v-if="row"
      class="mx-4 mb-3"
      :type="
        row.aiResult === 'CLEAN'
          ? 'success'
          : row.aiResult === 'POLLUTED'
            ? 'error'
            : 'warning'
      "
      :show-icon="true"
      :message="`记录 ${row.recordNo ?? ''}（${row.itemName ?? '-'}）`"
      :description="`AI 初筛：${AI_RESULT_MAP[row.aiResult ?? '']?.text ?? row.aiResult ?? '-'}（置信度 ${row.aiConfidence ?? '-'}%）｜ 判定依据：${row.aiReason ?? '-'}｜ AI 推荐存储方法：${row.suggestedStorage ?? '-'}`"
    />
    <Form class="mx-4" />
  </Modal>
</template>
