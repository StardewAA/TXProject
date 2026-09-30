<script lang="ts" setup>
import type { HrmSafetyApi } from '#/api/hrm/safety';

import { ref } from 'vue';

import { message } from 'ant-design-vue';

import {
  createSafetyEducation,
  HrmSafetyEducationLevelOptions,
  updateSafetyEducation,
} from '#/api/hrm/safety';
import { FileUpload } from '#/components/upload';
import EmployeeSelect from '#/views/hrm/employee/components/employee-select.vue';

const emit = defineEmits(['success']);
const open = ref(false);
const submitting = ref(false);
const form = ref<HrmSafetyApi.SafetyEducation>({});

async function openForm(record?: HrmSafetyApi.SafetyEducation) {
  form.value = record
    ? { ...record }
    : {
        level: 1,
        qualified: true,
        educationDate: new Date().toISOString().slice(0, 10),
      };
  open.value = true;
}

async function handleSubmit() {
  if (!form.value.employeeId || !form.value.level || !form.value.educationDate) {
    message.warning('请选择员工、教育级别并填写教育日期');
    return;
  }
  submitting.value = true;
  try {
    await (form.value.id
      ? updateSafetyEducation(form.value)
      : createSafetyEducation(form.value));
    message.success('保存成功');
    open.value = false;
    emit('success');
  } finally {
    submitting.value = false;
  }
}

defineExpose({ open: openForm });
</script>

<template>
  <a-modal
    v-model:open="open"
    :confirm-loading="submitting"
    :title="form.id ? '修改安全教育记录' : '新增安全教育记录'"
    width="680px"
    @ok="handleSubmit"
  >
    <a-form class="mt-3" layout="vertical">
      <a-form-item label="员工" required>
        <EmployeeSelect v-model="form.employeeId" placeholder="请选择员工" />
      </a-form-item>
      <a-form-item label="教育级别" required>
        <a-select
          v-model:value="form.level"
          :options="[...HrmSafetyEducationLevelOptions]"
        />
      </a-form-item>
      <a-form-item label="教育日期" required>
        <a-date-picker
          v-model:value="form.educationDate"
          class="w-full"
          value-format="YYYY-MM-DD"
        />
      </a-form-item>
      <a-form-item label="教育人">
        <a-input v-model:value="form.educatorName" :maxlength="64" />
      </a-form-item>
      <a-form-item label="学时">
        <a-input-number v-model:value="form.hours" :min="0" class="w-full" />
      </a-form-item>
      <a-form-item label="考核成绩">
        <a-input-number v-model:value="form.examScore" :min="0" class="w-full" />
      </a-form-item>
      <a-form-item label="是否合格">
        <a-switch
          v-model:checked="form.qualified"
          checked-children="合格"
          un-checked-children="不合格"
        />
        <span class="text-muted-foreground ml-2 text-xs">
          记录公司级 / 项目级 / 班组级三级教育的完成情况
        </span>
      </a-form-item>
      <a-form-item label="教育内容">
        <a-textarea v-model:value="form.content" :rows="3" :maxlength="1000" />
      </a-form-item>
      <a-form-item label="签字表 / 试卷 / 照片">
        <FileUpload
          v-model="form.fileUrls"
          :max-number="5"
          :max-size="20"
          :multiple="true"
          directory="hrm/safety/education"
        />
      </a-form-item>
    </a-form>
  </a-modal>
</template>
