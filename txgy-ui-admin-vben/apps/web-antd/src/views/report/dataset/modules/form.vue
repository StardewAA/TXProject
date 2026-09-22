<script lang="ts" setup>
import type { ReportDatasetApi } from '#/api/report/dataset';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import {
  createDataset,
  getDataset,
  getEnabledDatasetList,
  updateDataset,
} from '#/api/report/dataset';
import { $t } from '#/locales';

import { DEFAULT_BIZ_GROUPS, useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<ReportDatasetApi.Dataset>();
const isPlatformDataset = computed(() => formData.value?.tenantId === 0);
const getTitle = computed(() => {
  return formData.value?.id
    ? $t('ui.actionTitle.edit', ['报表数据集'])
    : $t('ui.actionTitle.create', ['报表数据集']);
});

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    formItemClass: 'col-span-2',
    labelWidth: 140,
  },
  layout: 'horizontal',
  schema: useFormSchema(),
  showDefaultActions: false,
});

/** 分组候选项：预置分组 + 已有数据集用过的分组（下拉里可直接输入新分组名） */
async function loadBizGroupOptions() {
  let groups = [...DEFAULT_BIZ_GROUPS];
  try {
    const list = await getEnabledDatasetList();
    const merged = new Set(groups);
    list.forEach((item) => {
      (item.bizGroups ?? []).forEach((group) => merged.add(group));
    });
    groups = [...merged];
  } catch {
    // 拉取失败时退回预置候选项，不阻塞表单
  }
  formApi.updateSchema([
    {
      fieldName: 'bizGroups',
      componentProps: {
        options: groups.map((group) => ({ label: group, value: group })),
      },
    },
  ]);
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    modalApi.lock();
    try {
      const data = (await formApi.getValues()) as ReportDatasetApi.Dataset;
      // 普通用户界面不展示 SQL 相关字段；编辑保存时保留原始值，避免清空后端已配置的 SQL
      if (formData.value?.id) {
        // 编码只读：保持原编码不变，不因编辑名称/分类/分组而重新生成
        data.code = formData.value.code;
        data.sqlText = formData.value.sqlText;
        data.tenantColumn = formData.value.tenantColumn;
        data.maxRows = formData.value.maxRows;
        data.timeoutSeconds = formData.value.timeoutSeconds;
        data.params = formData.value.params ?? [];
      }
      await (formData.value?.id
        ? updateDataset(data)
        : createDataset(data));
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
    void loadBizGroupOptions();
    const data = modalApi.getData<ReportDatasetApi.Dataset>();
    if (!data || !data.id) {
      return;
    }
    modalApi.lock();
    try {
      formData.value = await getDataset(data.id);
      await formApi.setValues(formData.value);
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-[820px]">
    <a-alert
      v-if="isPlatformDataset"
      class="mx-4 mb-2"
      message="平台级数据集（归属平台）仅平台管理员可维护，当前编辑会因权限被拒绝"
      show-icon
      type="warning"
    />
    <Form class="mx-4" />
  </Modal>
</template>
