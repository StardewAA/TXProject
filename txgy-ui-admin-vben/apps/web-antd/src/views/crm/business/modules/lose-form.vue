<script lang="ts" setup>
import type { CrmBusinessApi } from '#/api/crm/business';

import { computed, ref } from 'vue';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';
import { useVbenModal } from '@vben/common-ui';

import { message, Select, SelectOption, Textarea } from 'ant-design-vue';

import { loseBusiness } from '#/api/crm/business';

const emit = defineEmits(['success']);

/** 当前操作的商机 */
const business = ref<CrmBusinessApi.Business>();
/** 丢单原因（必填） */
const lostReason = ref<number>();
/** 丢单备注（选填） */
const lostRemark = ref('');

const reasonOptions = getDictOptions(DICT_TYPE.CRM_BUSINESS_LOST_REASON, 'number');

/** 选中「其他」时提示补充说明 */
const isOther = computed(() => Number(lostReason.value) === 6);

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const row = business.value;
    if (!row?.id) {
      return;
    }
    if (!lostReason.value) {
      message.warning('请选择丢单原因');
      return;
    }
    if (isOther.value && !lostRemark.value.trim()) {
      message.warning('丢单原因选择「其他」时，请在丢单备注中说明');
      return;
    }
    modalApi.lock();
    try {
      await loseBusiness({
        id: row.id,
        lostReason: Number(lostReason.value),
        lostRemark: lostRemark.value || undefined,
      });
      await modalApi.close();
      emit('success');
      message.success('已标记为丢单，该商机的关键字段已锁定');
    } finally {
      modalApi.unlock();
    }
  },
  onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      business.value = undefined;
      lostReason.value = undefined;
      lostRemark.value = '';
      return;
    }
    const data = modalApi.getData<CrmBusinessApi.Business>();
    if (!data?.id) {
      return;
    }
    business.value = data;
  },
});
</script>

<template>
  <Modal title="商机输单" class="w-2/5">
    <div class="mx-4 space-y-3">
      <div class="text-sm text-gray-500">
        商机：{{ business?.name }}（当前阶段：{{
          business?.stageName || '未设置'
        }}）
      </div>
      <div>
        <div class="mb-1 text-sm">
          <span class="text-red-500">*</span> 丢单原因
        </div>
        <Select
          v-model:value="lostReason"
          class="w-full"
          placeholder="请选择丢单原因"
          allow-clear
        >
          <SelectOption
            v-for="item in reasonOptions"
            :key="item.value"
            :value="item.value"
          >
            {{ item.label }}
          </SelectOption>
        </Select>
      </div>
      <div>
        <div class="mb-1 text-sm">
          丢单备注
          <span v-if="isOther" class="text-red-500">（选「其他」时必填）</span>
        </div>
        <Textarea
          v-model:value="lostRemark"
          :rows="3"
          placeholder="补充说明丢单的具体情况，便于复盘"
        />
      </div>
      <div class="text-xs text-gray-400">
        提交后：商机状态组=已丢单、预估赢单率=0%，并锁定名称/客户/阶段/金额等关键字段。
      </div>
    </div>
  </Modal>
</template>
