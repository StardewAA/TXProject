<script lang="ts" setup>
import type { CrmBusinessStatusApi } from '#/api/crm/business/status';

import { computed, nextTick, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Input, InputNumber, message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  createBusinessStatus,
  getBusinessStatus,
  STANDARD_STAGES,
  updateBusinessStatus,
} from '#/api/crm/business/status';
import { $t } from '#/locales';

import { useFormColumns, useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<CrmBusinessStatusApi.BusinessStatus>();
const getTitle = computed(() => {
  return formData.value?.id
    ? $t('ui.actionTitle.edit', ['商机状态'])
    : $t('ui.actionTitle.create', ['商机状态']);
});

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    formItemClass: 'col-span-2',
    labelWidth: 80,
  },
  layout: 'horizontal',
  schema: useFormSchema(),
  showDefaultActions: false,
});

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    modalApi.lock();
    // 提交表单
    const data =
      (await formApi.getValues()) as CrmBusinessStatusApi.BusinessStatus;
    try {
      // 仅提交普通阶段：赢单/输单已不再作为商机阶段（改由「赢单」「输单」按钮驱动）
      data.statuses = (formData.value?.statuses || []).filter(
        (item) => !item.endStatus,
      );
      await (formData.value?.id
        ? updateBusinessStatus(data)
        : createBusinessStatus(data));
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
      return;
    }
    // 加载数据
    const data = modalApi.getData<CrmBusinessStatusApi.BusinessStatus>();
    modalApi.lock();
    try {
      if (!data || !data.id) {
        formData.value = {
          id: undefined,
          name: '',
          deptIds: [],
          // 新建时预置标准 5 阶段，与商机管理保持一致
          statuses: STANDARD_STAGES.map((item) => ({ ...item })),
        };
      } else {
        formData.value = await getBusinessStatus(data.id);
        if (!formData.value?.statuses?.length) {
          formData.value.statuses = STANDARD_STAGES.map((item) => ({ ...item }));
        }
      }
      // 设置到 values
      await formApi.setValues(formData.value as any);
      await gridApi.grid.reloadData(formData.value!.statuses as any);
    } finally {
      modalApi.unlock();
    }
  },
});

/** 添加阶段 */
async function handleAddStatus() {
  formData.value!.statuses!.push({
    name: '',
    percent: undefined,
  });
  await nextTick();
  await gridApi.grid.reloadData(formData.value!.statuses as any);
}

/** 删除状态 */
async function deleteStatusArea(row: any, rowIndex: number) {
  await gridApi.grid.remove(row);
  formData.value!.statuses!.splice(rowIndex, 1);
  await gridApi.grid.reloadData(formData.value!.statuses as any);
}

/** 表格配置 */
const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions: {
    editConfig: {
      trigger: 'click',
      mode: 'cell',
    },
    columns: useFormColumns(),
    data: formData.value?.statuses
      ? [...formData.value.statuses, ...DEFAULT_STATUSES]
      : undefined,
    border: true,
    showOverflow: true,
    autoResize: true,
    keepSource: true,
    rowConfig: {
      keyField: 'row_id',
      isHover: true,
    },
    pagerConfig: {
      enabled: false,
    },
    toolbarConfig: {
      enabled: false,
    },
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-1/2">
    <Form class="mx-4">
      <div class="mb-2 rounded bg-gray-50 p-2 text-xs text-gray-500">
        商机阶段已标准化为 5 个固定阶段：商机待评估(5%) / 初步接洽(10%) /
        需求确认(40%) / 方案评估(60%) /
        商务谈判(70%)。「赢单」「输单」不再作为阶段，请使用商机列表的「赢单」「输单」按钮，此配置仅用于历史数据兼容与统计口径。
      </div>
      <template #statuses>
        <Grid class="w-full">
          <template #endStatus="{ row, rowIndex }">
            <span>
              {{ row.endStatus ? '结束' : `阶段${rowIndex + 1}` }}
            </span>
          </template>
          <template #name="{ row }">
            <Input
              v-if="!row.endStatus"
              v-model:value="row.name"
              placeholder="请输入状态名"
            />
            <span v-else>{{ row.name }}</span>
          </template>
          <template #percent="{ row }">
            <InputNumber
              v-if="!row.endStatus"
              v-model:value="row.percent"
              :min="0"
              :max="100"
              :precision="2"
              placeholder="请输入赢单率"
            />
            <span v-else>{{ row.percent }}</span>
          </template>
          <template #actions="{ row, rowIndex }">
            <TableAction
              :actions="[
                {
                  label: $t('ui.actionTitle.create'),
                  type: 'link',
                  ifShow: () => !row.endStatus,
                  onClick: handleAddStatus,
                },
                {
                  label: $t('common.delete'),
                  type: 'link',
                  danger: true,
                  ifShow: () => !row.endStatus,
                  popConfirm: {
                    title: $t('ui.actionMessage.deleteConfirm', [row.name]),
                    confirm: deleteStatusArea.bind(null, row, rowIndex),
                  },
                },
              ]"
            />
          </template>
        </Grid>
      </template>
    </Form>
  </Modal>
</template>
