<script lang="ts" setup>
import type { SystemMailAccountApi } from '#/api/system/mail/account';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import {
  createMailAccount,
  getMailAccount,
  updateMailAccount,
} from '#/api/system/mail/account';
import { $t } from '#/locales';

import { useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<SystemMailAccountApi.MailAccount>();
const getTitle = computed(() => {
  return formData.value?.id
    ? $t('ui.actionTitle.edit', ['邮箱账号'])
    : $t('ui.actionTitle.create', ['邮箱账号']);
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

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    modalApi.lock();
    // 提交表单
    const data =
      (await formApi.getValues()) as SystemMailAccountApi.MailAccount;
    // 密码（授权码）不再随查询接口返回，编辑时必然为空；
    // 新增场景必须显式填写，否则不知道该账号用什么授权码发信。
    if (!formData.value?.id && !data.password) {
      modalApi.unlock();
      message.error('新增邮箱账号时，密码（授权码）不能为空');
      return;
    }
    try {
      await (formData.value?.id
        ? updateMailAccount(data)
        : createMailAccount(data));
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
    const data = modalApi.getData<SystemMailAccountApi.MailAccount>();
    if (!data || !data.id) {
      return;
    }
    modalApi.lock();
    try {
      formData.value = await getMailAccount(data.id);
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
