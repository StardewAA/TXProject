<script lang="ts" setup>
import type { CrmBusinessApi } from '#/api/crm/business';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import {
  DatePicker,
  InputNumber,
  message,
  Textarea,
} from 'ant-design-vue';

import { winBusiness } from '#/api/crm/business';

const emit = defineEmits(['success']);

/** 当前操作的商机 */
const business = ref<CrmBusinessApi.Business>();
/** 签约日期（毫秒时间戳） */
const signDate = ref<any>();
/** 签约金额（元） */
const signAmount = ref<number>();
/** 赢单备注 */
const remark = ref('');

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const row = business.value;
    if (!row?.id) {
      return;
    }
    // 签约日期、签约金额必填
    if (!signDate.value) {
      message.warning('请选择签约日期');
      return;
    }
    if (
      signAmount.value === null ||
      signAmount.value === undefined ||
      Number(signAmount.value) < 0
    ) {
      message.warning('请填写正确的签约金额');
      return;
    }
    modalApi.lock();
    try {
      await winBusiness({
        id: row.id,
        signDate: Number(signDate.value),
        signAmount: Number(signAmount.value),
        remark: remark.value || undefined,
      });
      await modalApi.close();
      emit('success');
      message.success('已标记为赢单，该商机的关键字段已锁定');
    } finally {
      modalApi.unlock();
    }
  },
  onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      business.value = undefined;
      signDate.value = undefined;
      signAmount.value = undefined;
      remark.value = '';
      return;
    }
    const data = modalApi.getData<CrmBusinessApi.Business>();
    if (!data?.id) {
      return;
    }
    business.value = data;
    // 签约金额默认取商机总金额，减少手工输入
    signAmount.value = data.totalPrice ?? undefined;
  },
});
</script>

<template>
  <Modal title="商机赢单" class="w-2/5">
    <div class="mx-4 space-y-3">
      <div class="text-sm text-gray-500">
        商机：{{ business?.name }}（当前阶段：{{
          business?.stageName || '未设置'
        }}）
      </div>
      <div>
        <div class="mb-1 text-sm">
          <span class="text-red-500">*</span> 签约日期
        </div>
        <DatePicker
          v-model:value="signDate"
          class="w-full"
          value-format="x"
          placeholder="请选择签约日期"
        />
      </div>
      <div>
        <div class="mb-1 text-sm">
          <span class="text-red-500">*</span> 签约金额（元）
        </div>
        <InputNumber
          v-model:value="signAmount"
          class="w-full"
          :min="0"
          :precision="2"
          placeholder="请输入签约金额"
        />
      </div>
      <div>
        <div class="mb-1 text-sm">赢单备注</div>
        <Textarea v-model:value="remark" :rows="3" placeholder="选填" />
      </div>
      <div class="text-xs text-gray-400">
        提交后：商机状态组=已赢单、预估赢单率=100%，并锁定名称/客户/阶段/金额等关键字段。
      </div>
    </div>
  </Modal>
</template>
