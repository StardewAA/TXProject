<script lang="ts" setup>
import type { AiModelModelApi } from '#/api/ai/model/model';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Alert, message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { createModel, getModel, updateModel } from '#/api/ai/model/model';
import { $t } from '#/locales';

import { MODEL_ID_REFERENCE, useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<AiModelModelApi.Model>();
const getTitle = computed(() => {
  return formData.value?.id
    ? $t('ui.actionTitle.edit', ['模型配置'])
    : $t('ui.actionTitle.create', ['模型配置']);
});

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    formItemClass: 'col-span-2',
    labelWidth: 120,
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
    const data = (await formApi.getValues()) as AiModelModelApi.Model;
    try {
      await (formData.value?.id ? updateModel(data) : createModel(data));
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
    const data = modalApi.getData<AiModelModelApi.Model>();
    if (!data || !data.id) {
      return;
    }
    modalApi.lock();
    try {
      formData.value = await getModel(data.id);
      // 设置到 values
      await formApi.setValues(formData.value);
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-2/5">
    <!-- 工具提示需悬停才可见，而「模型标识」出错率最高，故把常用写法直接铺出来供照抄 -->
    <Alert class="mx-4 mb-3" show-icon type="info">
      <template #message>
        各厂商「模型标识」写法不同，务必照抄（区分大小写）
      </template>
      <template #description>
        <div class="grid grid-cols-2 gap-x-4 gap-y-0.5 text-xs">
          <div v-for="item in MODEL_ID_REFERENCE" :key="item.platform">
            <span class="text-gray-500">{{ item.platform }}：</span>
            <span>{{ item.model }}</span>
          </div>
        </div>
      </template>
    </Alert>
    <Form class="mx-4" />
  </Modal>
</template>
