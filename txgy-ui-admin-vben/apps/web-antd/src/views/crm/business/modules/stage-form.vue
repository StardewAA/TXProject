<script lang="ts" setup>
import type { CrmBusinessApi } from '#/api/crm/business';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';

import { message, Select, SelectOption } from 'ant-design-vue';

import { updateBusinessStage } from '#/api/crm/business';
import { $t } from '#/locales';

const emit = defineEmits(['success']);

/** 当前操作的商机 */
const business = ref<CrmBusinessApi.Business>();
/** 选中的商机阶段（1~5） */
const selectedStage = ref<number>();

/** 商机阶段字典（标准化 5 个阶段，不含赢单/输单） */
const stageOptions = getDictOptions(DICT_TYPE.CRM_BUSINESS_STAGE, 'number');
/** 阶段 → 预估赢单率（与后端 CrmBusinessStageEnum 保持一致） */
const stagePercents: Record<number, number> = {
  1: 5,
  2: 10,
  3: 40,
  4: 60,
  5: 70,
};

/** 当前阶段名称 */
const currentStageLabel = computed(
  () =>
    stageOptions.find(
      (item: any) => Number(item.value) === Number(business.value?.stage),
    )?.label ?? '未设置',
);

/** 目标阶段的预估赢单率与状态组提示 */
const targetHint = computed(() => {
  const stage = Number(selectedStage.value);
  if (!stage) {
    return '';
  }
  return `预估赢单率 ${stagePercents[stage]}%，商机状态组自动变为「${
    stage === 1 ? '未开始' : '进行中'
  }」`;
});

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const row = business.value;
    if (!row?.id) {
      return;
    }
    if (!selectedStage.value) {
      message.warning('请选择商机阶段');
      return;
    }
    if (Number(selectedStage.value) === Number(row.stage)) {
      message.warning('商机已处于该阶段');
      return;
    }
    modalApi.lock();
    try {
      await updateBusinessStage(row.id, Number(selectedStage.value));
      await modalApi.close();
      emit('success');
      message.success($t('ui.actionMessage.operationSuccess'));
    } finally {
      modalApi.unlock();
    }
  },
  onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      business.value = undefined;
      selectedStage.value = undefined;
      return;
    }
    const data = modalApi.getData<CrmBusinessApi.Business>();
    if (!data?.id) {
      return;
    }
    business.value = data;
    selectedStage.value = data.stage;
  },
});
</script>

<template>
  <Modal title="更改商机阶段" class="w-2/5">
    <div class="mx-4">
      <div class="mb-3 text-sm text-gray-500">
        商机：{{ business?.name }}（当前阶段：{{ currentStageLabel }}）
      </div>
      <Select
        v-model:value="selectedStage"
        class="w-full"
        placeholder="请选择商机阶段"
        allow-clear
      >
        <SelectOption
          v-for="item in stageOptions"
          :key="item.value"
          :value="item.value"
        >
          {{ item.label }}（预估赢单率：{{ stagePercents[Number(item.value)] }}%）
        </SelectOption>
      </Select>
      <div v-if="targetHint" class="mt-2 text-xs text-gray-400">
        选择后：{{ targetHint }}
      </div>
      <div class="mt-2 text-xs text-gray-400">
        提示：赢单 / 输单请使用列表中的「赢单」「输单」按钮；商机关闭后不可再更改阶段。
      </div>
    </div>
  </Modal>
</template>
