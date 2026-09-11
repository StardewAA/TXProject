<script lang="ts" setup>
import type { CrmClueApi } from '#/api/crm/clue';

import { computed, nextTick, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { createClue, getClue, updateClue } from '#/api/crm/clue';
import { $t } from '#/locales';

import { useFormSchema } from '../data';

import ClueQuickParse from './ClueQuickParse.vue';
import type { ParsedClue } from '#/utils/crm/clue-parse';

const emit = defineEmits(['success']);
const formData = ref<CrmClueApi.Clue>();
const modalKey = ref(0);
const getTitle = computed(() => {
  return formData.value?.id
    ? $t('ui.actionTitle.edit', ['线索'])
    : $t('ui.actionTitle.create', ['线索']);
});

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    labelWidth: 100,
  },
  wrapperClass: 'grid-cols-2',
  layout: 'horizontal',
  schema: useFormSchema(),
  showDefaultActions: false,
});

/** 识别结果回填：先设置客户类别以加载对应行业下拉项，再回填行业等字段 */
async function onQuickParse(payload: { fields: ParsedClue }) {
  const { fields } = payload;
  const apply: Record<string, any> = {};
  for (const [k, v] of Object.entries(fields)) {
    if (v !== undefined && v !== null && v !== '') {
      apply[k] = v;
    }
  }
  if (apply.customerCategory != null) {
    await formApi.setFieldValue('customerCategory', apply.customerCategory);
    delete apply.customerCategory;
    await nextTick();
  }
  await formApi.setValues(apply);
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    try {
    const { valid } = await formApi.validate();
    if (!valid) {
      // OCR 只能识别名片/文本内容；客户来源、客户类别等系统字段需手动补选
      message.warning(
        '还有带红星标记的必填项未填（识别不到的请手动补选，如客户来源、客户类别）',
      );
      return;
    }
    modalApi.lock();
    // 提交表单
    const data = (await formApi.getValues()) as Record<string, any>;
    // 客户类别仅用于前端级联（非后端字段）；并校验行业与客户类别一致
    if (data.industryId != null && data.customerCategory != null) {
      const industryCategory = Number(data.industryId) >= 200 ? 2 : 1;
      if (industryCategory !== Number(data.customerCategory)) {
        data.industryId = undefined;
      }
    }
    delete data.customerCategory;
    try {
      await (formData.value?.id
        ? updateClue(data as any)
        : createClue(data as any));
      // 关闭并提示
      await modalApi.close();
      emit('success');
      message.success($t('ui.actionMessage.operationSuccess'));
    } finally {
      modalApi.unlock();
    }
    } catch (err: any) {
      console.error('[clue-save-error]', err);
      message.error(`保存失败：${err?.message ?? String(err) ?? '未知错误'}`);
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      formData.value = undefined;
      return;
    }
    // 加载数据
    const data = modalApi.getData<CrmClueApi.Clue>();
    if (!data || !data.id) {
      return;
    }
    modalApi.lock();
    try {
      formData.value = await getClue(data.id);
      // 设置到 values
      await formApi.setValues(formData.value);
      // 根据已选行业推导客户类别（下游 101-110 / 上游 201-206），回填级联第一项
      const ind = (formData.value as Record<string, any>)?.industryId;
      if (ind != null) {
        await formApi.setFieldValue(
          'customerCategory',
          Number(ind) >= 200 ? 2 : 1,
        );
      }
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-2/5">
    <ClueQuickParse :key="modalKey" class="mx-4" @apply="onQuickParse" />
    <Form class="mx-4" />
  </Modal>
</template>
