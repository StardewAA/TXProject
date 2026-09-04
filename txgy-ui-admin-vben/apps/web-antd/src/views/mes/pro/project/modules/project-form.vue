<script lang="ts" setup>
import type { FormType } from '../data';
import type { MesProProjectApi } from '#/api/mes/pro/project';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import {
  createProject,
  getProject,
  updateProject,
} from '#/api/mes/pro/project';
import { $t } from '#/locales';

import { useFormSchema } from '../data';

defineOptions({ name: 'MesProProjectForm' });

const emit = defineEmits(['success']);
const formType = ref<FormType>('create');
const formData = ref<MesProProjectApi.Project>();

const isEditable = computed(() =>
  ['create', 'update'].includes(formType.value),
); // 是否可编辑（可保存）
const getTitle = computed(() => {
  switch (formType.value) {
    case 'detail': {
      return $t('ui.actionTitle.view', ['项目']);
    }
    case 'update': {
      return $t('ui.actionTitle.edit', ['项目']);
    }
    default: {
      return $t('ui.actionTitle.create', ['项目']);
    }
  }
});

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    formItemClass: 'col-span-1',
    labelWidth: 110,
  },
  layout: 'horizontal',
  schema: [],
  showDefaultActions: false,
  wrapperClass: 'grid-cols-3',
});

/** 重新挂载 schema 并按表单态控制底部确认按钮 */
function applySchema() {
  formApi.setState({ schema: useFormSchema(formType.value) });
  modalApi.setState({ showConfirmButton: isEditable.value });
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    if (!isEditable.value) {
      await modalApi.close();
      return;
    }
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    modalApi.lock();
    const data = (await formApi.getValues()) as MesProProjectApi.Project;
    try {
      if (formData.value?.id) {
        await updateProject({ ...formData.value, ...data });
      } else {
        await createProject(data);
      }
      emit('success');
      message.success($t('ui.actionMessage.operationSuccess'));
      await modalApi.close();
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      formData.value = undefined;
      return;
    }
    const data = modalApi.getData<{ formType: FormType; id?: number }>();
    formType.value = data.formType;
    applySchema();
    await formApi.resetForm();
    if (data?.id) {
      modalApi.lock();
      try {
        formData.value = await getProject(data.id);
        // 设置到 values
        await formApi.setValues(formData.value);
      } finally {
        modalApi.unlock();
      }
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-2/3">
    <Form class="mx-4" />
  </Modal>
</template>
