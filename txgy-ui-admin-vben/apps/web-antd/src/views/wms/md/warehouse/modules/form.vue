<script lang="ts" setup>
import type { WmsWarehouseApi } from '#/api/wms/md/warehouse';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import {
  createWarehouse,
  getWarehouse,
  updateWarehouse,
} from '#/api/wms/md/warehouse';
import { $t } from '#/locales';

import { useFormSchema } from '../data';

defineOptions({ name: 'WmsWarehouseForm' });

const emit = defineEmits(['success']);
const formData = ref<WmsWarehouseApi.Warehouse>();
const readonly = ref(false);
const detailSchema = [
  { fieldName: 'name', label: '仓库名称', component: 'Input' },
  { fieldName: 'code', label: '仓库编号', component: 'Input' },
  { fieldName: 'warehouseType', label: '仓库类型', component: 'Input' },
  { fieldName: 'sort', label: '排序', component: 'Input' },
  { fieldName: 'managerDisplay', label: '仓库负责人', component: 'Input' },
  { fieldName: 'memberDisplay', label: '仓库员工', component: 'Textarea' },
  { fieldName: 'remark', label: '备注', component: 'Textarea' },
];
const getTitle = computed(() => {
  if (readonly.value) return '仓库详情';
  return formData.value?.id
    ? $t('ui.actionTitle.edit', ['仓库'])
    : $t('ui.actionTitle.create', ['仓库']);
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
  schema: [],
  showDefaultActions: false,
});

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    if (readonly.value) return;
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    modalApi.lock();
    // 提交表单
    const data = (await formApi.getValues()) as WmsWarehouseApi.Warehouse;
    if (data.managerUserId) {
      data.memberUserIds = Array.from(
        new Set([data.managerUserId, ...(data.memberUserIds ?? [])]),
      );
    }
    try {
      await (formData.value?.id
        ? updateWarehouse(data)
        : createWarehouse(data));
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
      readonly.value = false;
      modalApi.setState({ footer: true });
      return;
    }
    const data = modalApi.getData<WmsWarehouseApi.Warehouse>();
    readonly.value = Boolean((data as any)?.readonly);
    modalApi.setState({ footer: !readonly.value });
    formApi.setState({
      schema: readonly.value ? detailSchema : useFormSchema(formApi),
      commonConfig: { disabled: readonly.value },
    });
    if (!data || !data.id) {
      await formApi.setValues({ sort: 0 });
      return;
    }
    // 详情直接使用列表行数据，避免查询权限不足时再次请求 /get 导致 403。
    if (readonly.value) {
      formData.value = data;
      const memberUserIds = (formData.value.memberUserIds ?? []).filter(
        (userId) => userId !== formData.value?.managerUserId,
      );
      await formApi.setValues({
        ...formData.value,
        managerDisplay: formData.value.managerName
          ? `${formData.value.managerUserId ?? ''} · ${formData.value.managerName}`
          : String(formData.value.managerUserId ?? ''),
        memberDisplay: memberUserIds.join('、'),
      });
      return;
    }
    // 加载数据
    modalApi.lock();
    try {
      formData.value = await getWarehouse(data.id);
      formData.value.memberUserIds = (formData.value.memberUserIds ?? []).filter(
        (userId) => userId !== formData.value?.managerUserId,
      );
      // 设置到 values
      await formApi.setValues(formData.value);
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-1/3">
    <Form class="mx-4" />
  </Modal>
</template>
