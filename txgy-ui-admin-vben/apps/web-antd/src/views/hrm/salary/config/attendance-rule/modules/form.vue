<script lang="ts" setup>
import type { HrmSalaryAttendanceRuleApi } from '#/api/hrm/salary/attendance-rule';

import { computed, ref } from 'vue';

import { useVbenForm, useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import {
  createAttendanceRule,
  getAttendanceRule,
  updateAttendanceRule,
} from '#/api/hrm/salary/attendance-rule';
import {
  getSalaryRateLimitList,
  HrmSalaryRateLimitBizType,
} from '#/api/hrm/salary/config/rate-limit';
import { $t } from '#/locales';

import { useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<HrmSalaryAttendanceRuleApi.AttendanceRule>();
/** 各计薪项的比例上限（%），来自「薪资管理 → 比例上限」配置 */
const rateLimits = ref<Record<string, number | undefined>>({});
const getTitle = computed(() =>
  formData.value?.id
    ? $t('ui.actionTitle.edit', ['考勤计薪规则'])
    : $t('ui.actionTitle.create', ['考勤计薪规则']),
);

const [Form, formApi] = useVbenForm({
  commonConfig: { componentProps: { class: 'w-full' }, labelWidth: 130 },
  layout: 'horizontal',
  schema: useFormSchema(),
  showDefaultActions: false,
  wrapperClass: 'grid-cols-2',
});

/** 读取比例上限配置：上限由 HR 按项维护，不再写死在表单里 */
async function loadRateLimits() {
  const map: Record<string, number | undefined> = {};
  try {
    const list = await getSalaryRateLimitList(
      HrmSalaryRateLimitBizType.ATTENDANCE_RULE,
    );
    for (const item of list || []) {
      // status === 0 表示启用；停用表示该项不做上限校验
      if (
        item?.itemCode &&
        (item.status ?? 0) === 0 &&
        item.rateLimit !== null &&
        item.rateLimit !== undefined
      ) {
        map[item.itemCode] = Number(item.rateLimit);
      }
    }
  } catch {
    // 配置读取失败时按兜底上限 100% 处理，不阻塞表单
  }
  rateLimits.value = map;
  await formApi.updateSchema(useFormSchema(map));
}

/** 提交前按配置上限复核，给出「哪一项、填了多少、上限多少」的明确提示 */
function findRateLimitError(
  values: HrmSalaryAttendanceRuleApi.AttendanceRule,
) {
  const items: Array<{ code: string; name: string; value?: number }> = [
    { code: 'absenceDeductRate', name: '缺勤扣款比例', value: values.absenceDeductRate },
    {
      code: 'fullAttendanceBonusRate',
      name: '满勤奖比例',
      value: values.fullAttendanceBonusRate,
    },
    { code: 'personalLeaveRate', name: '事假扣款比例', value: values.personalLeaveRate },
    { code: 'sickLeaveRate', name: '病假扣款比例', value: values.sickLeaveRate },
  ];
  for (const item of items) {
    const limit = rateLimits.value[item.code];
    if (
      limit !== undefined &&
      item.value !== null &&
      item.value !== undefined &&
      Number(item.value) > limit
    ) {
      return `${item.name}填写 ${item.value}% 超过该项上限 ${limit}%，请到「薪资管理 → 比例上限」调整上限后再保存`;
    }
  }
  return undefined;
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;
    const values = await formApi.getValues();
    const rateLimitError = findRateLimitError(values);
    if (rateLimitError) {
      message.error(rateLimitError);
      return;
    }
    modalApi.lock();
    try {
      await (formData.value?.id
        ? updateAttendanceRule(values)
        : createAttendanceRule(values));
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
    const data = modalApi.getData<HrmSalaryAttendanceRuleApi.AttendanceRule>();
    modalApi.lock();
    try {
      await loadRateLimits();
      if (!data?.id) {
        formData.value = undefined;
        await formApi.resetForm();
        return;
      }
      formData.value = await getAttendanceRule(data.id);
      await formApi.setValues(formData.value);
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-[760px]">
    <Form class="mx-4" />
  </Modal>
</template>
