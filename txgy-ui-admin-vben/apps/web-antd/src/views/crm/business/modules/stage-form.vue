<script lang="ts" setup>
import type { CrmBusinessApi } from '#/api/crm/business';
import type { CrmBusinessStatusApi } from '#/api/crm/business/status';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message, Select, SelectOption } from 'ant-design-vue';

import { updateBusinessStatus } from '#/api/crm/business';
import { getBusinessStatusSimpleList } from '#/api/crm/business/status';
import { $t } from '#/locales';

const emit = defineEmits(['success']);

/** 当前操作的商机 */
const business = ref<CrmBusinessApi.Business>();
/** 商机所属状态组下的阶段列表 */
const statusList = ref<CrmBusinessStatusApi.BusinessStatusType[]>([]);
/** 选中的阶段编号 */
const selectedStatusId = ref<number>();

/** 根据阶段名称推导结束状态：1-赢单，2-输单 */
function resolveEndStatus(name?: string): number | undefined {
  if (name === '赢单') {
    return 1;
  }
  if (name === '输单') {
    return 2;
  }
  return undefined;
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    if (!selectedStatusId.value) {
      message.warning('请选择商机阶段');
      return;
    }
    const row = business.value;
    if (!row?.id) {
      return;
    }
    const target = statusList.value.find(
      (item) => item.id === selectedStatusId.value,
    );
    modalApi.lock();
    try {
      await updateBusinessStatus({
        id: row.id,
        statusId: selectedStatusId.value,
        endStatus: resolveEndStatus(target?.name),
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
      business.value = undefined;
      statusList.value = [];
      selectedStatusId.value = undefined;
      return;
    }
    const data = modalApi.getData<CrmBusinessApi.Business>();
    if (!data?.id) {
      return;
    }
    business.value = data;
    // 已结束的商机不预选阶段
    selectedStatusId.value =
      data.endStatus === null || data.endStatus === undefined
        ? data.statusId
        : undefined;
    modalApi.lock();
    try {
      statusList.value = await getBusinessStatusSimpleList(
        data.statusTypeId ?? 0,
      );
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal title="更改商机阶段" class="w-2/5">
    <div class="mx-4">
      <div class="mb-3 text-sm text-gray-500">
        商机：{{ business?.name }}（当前阶段：{{ business?.statusName || '未设置' }}）
      </div>
      <Select
        v-model:value="selectedStatusId"
        class="w-full"
        placeholder="请选择商机阶段"
        allow-clear
      >
        <SelectOption
          v-for="item in statusList"
          :key="item.id"
          :value="item.id"
        >
          {{ item.name }}
          <span v-if="item.percent !== null && item.percent !== undefined">
            （赢单率：{{ item.percent }}%）
          </span>
        </SelectOption>
      </Select>
      <div class="mt-2 text-xs text-gray-400">
        提示：选择「赢单 / 输单」会同时结束该商机，结束后不可再更改阶段。
      </div>
    </div>
  </Modal>
</template>
