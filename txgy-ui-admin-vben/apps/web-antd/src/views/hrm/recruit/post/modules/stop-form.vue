<script lang="ts" setup>
import { computed } from 'vue';

import { useVbenForm, useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { updateRecruitPostStatus } from '#/api/hrm/recruit/post';
import { $t } from '#/locales';
import {
  HrmRecruitPostStatus,
  type HrmRecruitPostStatusValue,
} from '#/views/hrm/utils/constants';

import { useStopFormSchema } from '../data';

const emit = defineEmits(['success']);
const targetStatus = computed<HrmRecruitPostStatusValue | undefined>(
  () => modalApi.getData<{ status?: HrmRecruitPostStatusValue }>()?.status,
);
const modalTitle = computed(() =>
  targetStatus.value === HrmRecruitPostStatus.CLOSED ? '关闭职位' : '暂停职位',
);

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    labelWidth: 90,
  },
  layout: 'horizontal',
  schema: useStopFormSchema(),
  showDefaultActions: false,
});

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    const values = await formApi.getValues();
    const stopReason = String(values.stopReason ?? '').trim();
    if (!stopReason) {
      message.warning('状态变更原因不能为空');
      return;
    }
    if (stopReason.length > 255) {
      message.warning('状态变更原因不能超过 255 个字符');
      return;
    }
    const data = modalApi.getData<{
      id: number;
      status: HrmRecruitPostStatusValue;
    }>();
    if (
      !data?.id ||
      (data.status !== HrmRecruitPostStatus.PAUSED &&
        data.status !== HrmRecruitPostStatus.CLOSED)
    ) {
      return;
    }
    modalApi.lock();
    try {
      await updateRecruitPostStatus({
        id: data.id,
        status: data.status,
        stopReason,
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
      return;
    }
    await formApi.setValues({ stopReason: '' });
  },
});
</script>

<template>
  <Modal :title="modalTitle" class="w-[520px]">
    <Form class="mx-4" />
  </Modal>
</template>
