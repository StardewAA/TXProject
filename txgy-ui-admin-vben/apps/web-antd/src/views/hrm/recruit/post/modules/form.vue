<script lang="ts" setup>
import type { HrmRecruitPostApi } from '#/api/hrm/recruit/post';

import { computed, ref } from 'vue';

import { useVbenForm, useVbenModal } from '@vben/common-ui';

import { Button, message } from 'ant-design-vue';

import {
  createRecruitPost,
  getRecruitPost,
  updateRecruitPost,
} from '#/api/hrm/recruit/post';
import { $t } from '#/locales';
import {
  AGE_UNLIMITED_VALUE,
  HrmRecruitJobNature,
  HrmRecruitPostEducation,
  HrmRecruitPostStatus,
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

/**
 * 保存职位。
 *
 * Q-003 / I-007：新建时既可「保存为草稿」（不发布到招聘门户），也可「保存并发布」；
 * 编辑不改状态（状态只能在列表里变更）。
 *
 * @param targetStatus 新建时的目标状态；undefined 表示编辑（保持原状态）
 * @returns 是否保存成功（校验不通过返回 false，弹窗保持打开）
 */
async function submit(targetStatus?: number): Promise<boolean> {
    const { valid } = await formApi.validate();
    if (!valid) {
      return false;
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
      return false;
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
      return false;
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
      if (formData.value?.id) {
        // 编辑：不传状态，避免把表单里的旧状态覆盖回库
        delete (data as { status?: number }).status;
      } else {
        data.status = targetStatus ?? HrmRecruitPostStatus.DRAFT;
      }
      await (formData.value?.id
        ? updateRecruitPost(data)
        : createRecruitPost(data));
      await modalApi.close();
      // 新建后把最终状态回传父组件：保存为草稿时父组件会切到「草稿」页签，避免"存了却看不到"
      emit('success', formData.value?.id ? undefined : data.status);
      message.success($t('ui.actionMessage.operationSuccess'));
      return true;
    } finally {
      modalApi.unlock();
    }
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    // 新建：保存并发布；编辑：仅保存
    await submit(formData.value?.id ? undefined : HrmRecruitPostStatus.PUBLISHED);
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      formData.value = undefined;
      return;
    }
    const data = modalApi.getData<HrmRecruitPostApi.RecruitPost>();
    if (!data?.id) {
      await formApi.setValues({
        // 说明：新建职位默认落「草稿」，需在列表点「发布」后才出现在招聘门户
        postName: '',
        jobNature: HrmRecruitJobNature.FULL_TIME,
        workTime: 0,
        educationRequire: HrmRecruitPostEducation.UNLIMITED,
        salaryUnit: HrmRecruitSalaryUnit.MONTH,
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

/** 新建：保存为草稿（不发布到招聘门户） */
async function handleSaveDraft() {
  await submit(HrmRecruitPostStatus.DRAFT);
}
</script>

<template>
  <Modal
    :title="getTitle"
    :confirm-text="formData?.id ? '保存' : '保存并发布'"
    class="w-[1040px]"
  >
    <a-alert
      v-if="!formData?.id"
      class="mx-4 mb-3"
      type="info"
      show-icon
      message="新建职位默认保存为「草稿」，不会出现在招聘门户"
      description="确认无误可点「保存并发布」直接发布；也可先「保存为草稿」，稍后在列表点「发布」。已关闭的职位不可重新发布。"
    />
    <Form class="mx-4" />
    <template #prepend-footer>
      <Button v-if="!formData?.id" @click="handleSaveDraft">保存为草稿</Button>
    </template>
  </Modal>
</template>
