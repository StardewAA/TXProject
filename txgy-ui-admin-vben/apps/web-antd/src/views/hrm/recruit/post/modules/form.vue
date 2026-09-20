<script lang="ts" setup>
import type { HrmRecruitPostApi } from '#/api/hrm/recruit/post';

import { computed, ref } from 'vue';

import { useVbenForm, useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import {
  createRecruitPost,
  getRecruitPost,
  updateRecruitPost,
} from '#/api/hrm/recruit/post';
import { $t } from '#/locales';
import {
  AGE_UNLIMITED_VALUE,
  HrmRecruitEmergencyLevel,
  HrmRecruitJobNature,
  HrmRecruitPostEducation,
  HrmRecruitSalaryUnit,
  SALARY_NEGOTIABLE_UNIT_VALUE,
  SALARY_NEGOTIABLE_VALUE,
} from '#/views/hrm/utils/constants';

import { useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<HrmRecruitPostApi.RecruitPost>();
const getTitle = computed(() =>
  formData.value?.id ? '编辑招聘职位' : '新建招聘职位',
);

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    labelWidth: 112,
  },
  wrapperClass: 'grid-cols-2',
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
    const values = await formApi.getValues();
    if (
      !values.salaryNegotiable &&
      values.minSalary !== undefined &&
      values.minSalary !== null &&
      values.maxSalary !== undefined &&
      values.maxSalary !== null &&
      values.minSalary > values.maxSalary
    ) {
      message.warning('最低薪资不能大于最高薪资');
      return;
    }
    if (
      !values.ageUnlimited &&
      values.minAge !== undefined &&
      values.minAge !== null &&
      values.maxAge !== undefined &&
      values.maxAge !== null &&
      values.minAge > values.maxAge
    ) {
      message.warning('最小年龄不能大于最大年龄');
      return;
    }
    modalApi.lock();
    const data: HrmRecruitPostApi.RecruitPost = {
      ...(values as HrmRecruitPostApi.RecruitPost),
      postName: values.postName,
      minSalary: values.salaryNegotiable
        ? SALARY_NEGOTIABLE_VALUE
        : values.minSalary,
      maxSalary: values.salaryNegotiable
        ? SALARY_NEGOTIABLE_VALUE
        : values.maxSalary,
      salaryUnit: values.salaryNegotiable
        ? SALARY_NEGOTIABLE_UNIT_VALUE
        : values.salaryUnit,
      minAge: values.ageUnlimited ? AGE_UNLIMITED_VALUE : values.minAge,
      maxAge: values.ageUnlimited ? AGE_UNLIMITED_VALUE : values.maxAge,
      interviewEmployeeIds: values.interviewEmployeeIds ?? [],
    };
    try {
      await (formData.value?.id
        ? updateRecruitPost(data)
        : createRecruitPost(data));
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
    const data = modalApi.getData<HrmRecruitPostApi.RecruitPost>();
    if (!data?.id) {
      await formApi.setValues({
        // 说明：后端新建职位会直接发布（忽略状态入参），见模板中的提示
        postName: '',
        jobNature: HrmRecruitJobNature.FULL_TIME,
        workTime: 0,
        educationRequire: HrmRecruitPostEducation.UNLIMITED,
        salaryUnit: HrmRecruitSalaryUnit.MONTH,
        emergencyLevel: HrmRecruitEmergencyLevel.URGENT,
        interviewEmployeeIds: [],
        channelId: undefined,
        salaryNegotiable: false,
        ageUnlimited: false,
      });
      return;
    }
    modalApi.lock();
    try {
      formData.value = await getRecruitPost(data.id);
      const salaryNegotiable =
        formData.value.salaryUnit === SALARY_NEGOTIABLE_UNIT_VALUE ||
        (formData.value.minSalary === SALARY_NEGOTIABLE_VALUE &&
          formData.value.maxSalary === SALARY_NEGOTIABLE_VALUE);
      const ageUnlimited =
        formData.value.minAge === AGE_UNLIMITED_VALUE &&
        formData.value.maxAge === AGE_UNLIMITED_VALUE;
      await formApi.setValues({
        ...formData.value,
        interviewEmployeeIds: formData.value.interviewEmployeeIds ?? [],
        salaryNegotiable,
        ageUnlimited,
        minSalary: salaryNegotiable ? undefined : formData.value.minSalary,
        maxSalary: salaryNegotiable ? undefined : formData.value.maxSalary,
        salaryUnit: salaryNegotiable
          ? HrmRecruitSalaryUnit.MONTH
          : formData.value.salaryUnit,
        minAge: ageUnlimited ? undefined : formData.value.minAge,
        maxAge: ageUnlimited ? undefined : formData.value.maxAge,
      });
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-[1040px]">
    <a-alert
      v-if="!formData?.id"
      class="mx-4 mb-3"
      type="info"
      show-icon
      message="保存后职位将直接发布到招聘门户"
      description="如需暂停招聘，请在职位列表中将状态改为「已暂停」；已关闭的职位不可重新发布。"
    />
    <Form class="mx-4" />
  </Modal>
</template>
