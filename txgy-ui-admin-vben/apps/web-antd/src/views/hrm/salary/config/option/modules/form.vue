<script lang="ts" setup>
import type { Rule } from 'ant-design-vue/es/form';

import type { HrmSalaryOptionApi } from '#/api/hrm/salary/config/option';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Form, Input, message, Select } from 'ant-design-vue';

import {
  createSalaryOption,
  getSalaryOptionList,
  updateSalaryOption,
} from '#/api/hrm/salary/config/option';
import { $t } from '#/locales';
import { HrmSalaryOptionCategoryCode } from '#/views/hrm/utils/constants';

defineOptions({ name: 'HrmSalaryOptionForm' });

const emit = defineEmits(['success']);

const formRef = ref();
const optionList = ref<HrmSalaryOptionApi.SalaryOption[]>([]);
const isEdit = ref(false);
const formData = ref<HrmSalaryOptionApi.SaveReq>({
  parentCode: undefined,
  name: '',
  unit: '',
  remark: '',
});

const categoryList = computed(() =>
  optionList.value.filter(
    (item) =>
      item.parentCode === HrmSalaryOptionCategoryCode.ROOT &&
      !item.systemFlag &&
      item.enabled,
  ),
);

const formRules: Record<string, Rule[]> = {
  parentCode: [
    { required: true, message: '工资项分类不能为空', trigger: 'change' },
  ],
  name: [{ required: true, message: '工资项名称不能为空', trigger: 'blur' }],
};

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    await formRef.value?.validate();
    modalApi.lock();
    try {
      await (isEdit.value
        ? updateSalaryOption({
            id: (formData.value as any).id,
            name: formData.value.name,
            unit: formData.value.unit,
            remark: formData.value.remark,
          })
        : createSalaryOption(formData.value));
      message.success($t('ui.actionMessage.operationSuccess'));
      await modalApi.close();
      emit('success');
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) return;
    const data = modalApi.getData<{
      parentCode?: number;
      record?: HrmSalaryOptionApi.SalaryOption;
    }>();
    optionList.value = await getSalaryOptionList();
    if (data?.record) {
      isEdit.value = true;
      formData.value = {
        id: data.record.id,
        parentCode: data.record.parentCode,
        name: data.record.name,
        unit: data.record.unit || '',
        remark: data.record.remark || '',
      } as HrmSalaryOptionApi.SaveReq;
      return;
    }
    isEdit.value = false;
    formData.value = { parentCode: data?.parentCode, name: '', unit: '', remark: '' };
  },
});
</script>

<template>
  <Modal
    :title="
      isEdit
        ? $t('ui.actionTitle.edit', ['工资项'])
        : $t('ui.actionTitle.create', ['工资项'])
    "
    class="w-[560px]"
  >
    <Form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      class="mx-4"
      label-width="96px"
    >
      <Form.Item label="工资项分类" name="parentCode">
        <Select
          v-model:value="formData.parentCode"
          :options="
            categoryList.map((item) => ({ label: item.name, value: item.code }))
          "
          disabled
          class="w-full"
          placeholder="请选择工资项分类"
        />
      </Form.Item>
      <Form.Item label="工资项名称" name="name">
        <Input
          v-model:value="formData.name"
          :maxlength="64"
          placeholder="请输入工资项名称"
        />
      </Form.Item>
      <Form.Item label="单位" name="unit">
        <Input
          v-model:value="formData.unit"
          :maxlength="16"
          placeholder="如：元/月、元/天、次、%（选择该计薪项时自动带出）"
        />
      </Form.Item>
      <Form.Item label="备注" name="remark">
        <Input.TextArea
          v-model:value="formData.remark"
          :rows="3"
          :maxlength="255"
          placeholder="请输入备注"
        />
      </Form.Item>
    </Form>
  </Modal>
</template>
