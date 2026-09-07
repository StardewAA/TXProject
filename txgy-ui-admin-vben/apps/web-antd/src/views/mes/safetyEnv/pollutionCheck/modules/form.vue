<script lang="ts" setup>
import type { MesSetPollutionCheckApi } from '#/api/mes/safetyEnv/pollutionCheck';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Alert, Button, message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import {
  createPollutionCheck,
  getPollutionCheck,
  prescreenPollutionCheck,
  updatePollutionCheck,
} from '#/api/mes/safetyEnv/pollutionCheck';
import { $t } from '#/locales';

import { AI_RESULT_MAP, useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formType = ref<'create' | 'update'>('create');
const getTitle = computed(() =>
  formType.value === 'update'
    ? $t('ui.actionTitle.edit', ['污染判定'])
    : $t('ui.actionTitle.create', ['污染判定']),
);
/** AI 初筛预览(不落库) */
const aiSuggestion = ref<MesSetPollutionCheckApi.AiSuggestion | null>(null);
const previewing = ref(false);

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    formItemClass: 'col-span-1',
    labelWidth: 120,
  },
  layout: 'horizontal',
  schema: useFormSchema(),
  showDefaultActions: false,
  wrapperClass: 'grid-cols-3',
});

/** AI 初筛预览：按当前填写的物料名调用后端(不落库) */
async function handlePrescreen() {
  const values = (await formApi.getValues()) as MesSetPollutionCheckApi.PollutionCheck;
  if (!values.itemName) {
    message.warning('请先填写物料/产品名称');
    return;
  }
  previewing.value = true;
  aiSuggestion.value = null;
  try {
    aiSuggestion.value = await prescreenPollutionCheck(values);
  } finally {
    previewing.value = false;
  }
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    modalApi.lock();
    // 提交表单；后端自动执行 AI 初筛并落为待复核
    const data = (await formApi.getValues()) as MesSetPollutionCheckApi.PollutionCheck;
    try {
      await (formType.value === 'update'
        ? updatePollutionCheck(data)
        : createPollutionCheck(data));
      await modalApi.close();
      emit('success');
      message.success($t('ui.actionMessage.operationSuccess'));
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      formType.value = 'create';
      aiSuggestion.value = null;
      return;
    }
    const data = modalApi.getData<{ formType: 'create' | 'update'; id?: number }>();
    formType.value = data.formType;
    if (!data.id) {
      return;
    }
    modalApi.lock();
    try {
      const row = await getPollutionCheck(data.id);
      // 仅待复核行允许编辑：回填基础字段
      await formApi.setValues(row);
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-2/3">
    <div class="mx-4 mb-3 flex items-start gap-3">
      <Button :loading="previewing" @click="handlePrescreen">AI 初筛预览</Button>
      <Alert
        v-if="aiSuggestion"
        class="flex-1"
        :type="
          aiSuggestion.aiResult === 'CLEAN'
            ? 'success'
            : aiSuggestion.aiResult === 'POLLUTED'
              ? 'error'
              : 'warning'
        "
        :show-icon="true"
        :message="`AI 初筛：${AI_RESULT_MAP[aiSuggestion.aiResult ?? '']?.text ?? aiSuggestion.aiResult}（置信度 ${aiSuggestion.aiConfidence ?? '-'}%）`"
        :description="`判定依据：${aiSuggestion.aiReason ?? '-'}；推荐存储方法：${aiSuggestion.suggestedStorage ?? '-'}`"
      />
    </div>
    <Form class="mx-4" />
  </Modal>
</template>
