<script lang="ts" setup>
import type { SystemUserApi } from '#/api/system/user';

import { computed, ref } from 'vue';

import { useAccess } from '@vben/access';
import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import {
  assignUserRole,
  getUserRoleList,
} from '#/api/system/permission';
import { createUser, getUser, updateUser } from '#/api/system/user';
import { $t } from '#/locales';

import { useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<SystemUserApi.User>();
const { hasAccessByCodes } = useAccess();
const canAssignRole = hasAccessByCodes([
  'system:permission:assign-user-role',
]);
const getTitle = computed(() => {
  return formData.value?.id
    ? $t('ui.actionTitle.edit', ['用户'])
    : $t('ui.actionTitle.create', ['用户']);
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
  schema: useFormSchema(canAssignRole),
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
    const values = await formApi.getValues();
    const { roleIds, ...data } = values;
    try {
      if (formData.value?.id) {
        await updateUser(data as SystemUserApi.User);
        if (canAssignRole) {
          await assignUserRole({
            userId: formData.value.id,
            roleIds: roleIds ?? [],
          });
        }
      } else {
        const userId = await createUser(data as SystemUserApi.User);
        if (canAssignRole && roleIds?.length) {
          await assignUserRole({ userId, roleIds });
        }
      }
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
    const data = modalApi.getData<SystemUserApi.User>();
    if (!data || !data.id) {
      return;
    }
    modalApi.lock();
    try {
      const [user, roleIds] = await Promise.all([
        getUser(data.id),
        canAssignRole ? getUserRoleList(data.id) : Promise.resolve(undefined),
      ]);
      formData.value = user;
      // 设置到 values
      await formApi.setValues({
        ...user,
        ...(canAssignRole ? { roleIds } : {}),
      });
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
