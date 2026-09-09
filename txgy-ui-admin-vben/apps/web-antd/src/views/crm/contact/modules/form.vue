<script lang="ts" setup>
import type { CrmContactApi } from '#/api/crm/contact';

import { computed, ref } from 'vue';

import { useVbenForm, useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { createContact, getContact, updateContact } from '#/api/crm/contact';
import { $t } from '#/locales';

import { useFormSchema } from '../data';

import ClueQuickParse from '../../clue/modules/ClueQuickParse.vue';
import type { ParsedClue } from '#/utils/crm/clue-parse';

const emit = defineEmits(['success']);
const formData = ref<CrmContactApi.Contact>();
const getTitle = computed(() => {
  return formData.value?.id
    ? $t('ui.actionTitle.edit', ['联系人'])
    : $t('ui.actionTitle.create', ['联系人']);
});

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
  },
  wrapperClass: 'grid-cols-2',
  layout: 'horizontal',
  schema: useFormSchema(),
  showDefaultActions: false,
});

/** 识别结果回填到联系人新增表单 */
async function onQuickParse(payload: { fields: ParsedClue }) {
  const { fields } = payload;
  const apply: Record<string, any> = {};
  for (const key of [
    'name',
    'mobile',
    'telephone',
    'wechat',
    'email',
    'qq',
    'customerId',
  ] as const) {
    let v: any = fields[key];
    if (v === undefined || v === null || v === '') continue;
    // qq 后端是 Long 且 DB 为 32 位整数，OCR 可能误把错误码等当 QQ 填进来
    if (key === 'qq') {
      v = String(v).replace(/\D/g, '');
      const qqNum = Number(v);
      // QQ 号长度有限且不能超过 int 上限；超范围视为误识别，不填
      if (
        !v ||
        v.length > 11 ||
        !Number.isSafeInteger(qqNum) ||
        qqNum > 2147483647
      ) {
        continue;
      }
    }
    apply[key] = v;
  }
  await formApi.setValues(apply);
  message.success('识别完成，请核对信息并补选所属客户后保存');
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    modalApi.lock();
    // 提交表单
    const data = (await formApi.getValues()) as CrmContactApi.Contact;
    try {
      await (formData.value?.id ? updateContact(data) : createContact(data));
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
    const data = modalApi.getData<CrmContactApi.Contact>();
    if (!data) {
      return;
    }
    modalApi.lock();
    try {
      formData.value = data.id ? await getContact(data.id) : data;
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
    <ClueQuickParse class="mx-4" @apply="onQuickParse" />
    <Form class="mx-4" />
  </Modal>
</template>
