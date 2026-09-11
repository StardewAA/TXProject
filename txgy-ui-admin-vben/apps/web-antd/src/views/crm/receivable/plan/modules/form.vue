<script lang="ts" setup>
import type { CrmReceivablePlanApi } from '#/api/crm/receivable/plan';

import { computed, ref } from 'vue';

import { useVbenForm, useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import ClueQuickParse from '../../../clue/modules/ClueQuickParse.vue';
import { dateToTs } from '#/utils/crm/clue-parse';
import {
  createReceivablePlan,
  getReceivablePlan,
  updateReceivablePlan,
} from '#/api/crm/receivable/plan';
import { $t } from '#/locales';

import CustomerInfoCard from '#/views/crm/components/customer-info-card.vue';

import { useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<CrmReceivablePlanApi.Plan>();
/** 当前选中的客户编号：用于展示"客户信息速览"卡片 */
const currentCustomerId = ref<number>();

/** 识别结果回填回款计划 */
async function onQuickParse(payload: { fields: any }) {
  const fields = payload.fields ?? {};
  const apply: Record<string, any> = {};
  if (fields.customerId) apply.customerId = fields.customerId;
  if (fields.contractId) apply.contractId = fields.contractId;
  if (fields.price != null) apply.price = fields.price;
  const ts = dateToTs(fields.returnTime);
  if (ts != null) apply.returnTime = ts;
  if (fields.returnType != null) apply.returnType = fields.returnType;
  if (fields.remindDays != null) apply.remindDays = fields.remindDays;
  if (fields.ownerUserId) apply.ownerUserId = fields.ownerUserId;
  if (fields.remark) apply.remark = fields.remark;
  await formApi.setValues(apply);
}
const getTitle = computed(() => {
  return formData.value?.id
    ? $t('ui.actionTitle.edit', ['回款计划'])
    : $t('ui.actionTitle.create', ['回款计划']);
});

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
  },
  wrapperClass: 'grid-cols-2',
  layout: 'horizontal',
  schema: useFormSchema(),
  showDefaultActions: false,
  // 选中客户后展示"客户信息速览"卡片（一键引用）
  handleValuesChange(values) {
    currentCustomerId.value = values.customerId;
  },
});

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    modalApi.lock();
    // 提交表单
    const data = (await formApi.getValues()) as CrmReceivablePlanApi.Plan;
    try {
      await (formData.value?.id
        ? updateReceivablePlan(data)
        : createReceivablePlan(data));
      // 关闭并提示
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
    // 加载数据
    const data = modalApi.getData<CrmReceivablePlanApi.Plan>();
    if (!data || !data.id) {
      // 设置到 values
      await formApi.setValues(data);
      return;
    }
    modalApi.lock();
    try {
      formData.value = await getReceivablePlan(data.id);
      // 设置到 values
      await formApi.setValues(formData.value);
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-2/5">
    <ClueQuickParse mode="plan" class="mx-4" @apply="onQuickParse" />
    <CustomerInfoCard :customer-id="currentCustomerId" />
    <Form class="mx-4" />
  </Modal>
</template>
