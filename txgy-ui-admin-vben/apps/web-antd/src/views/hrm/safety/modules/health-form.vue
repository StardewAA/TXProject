<script lang="ts" setup>
import type { HrmSafetyApi } from '#/api/hrm/safety';

import { ref } from 'vue';

import { message } from 'ant-design-vue';

import {
  createHealthCheck,
  HrmHealthCheckResultOptions,
  HrmHealthCheckTypeOptions,
  updateHealthCheck,
} from '#/api/hrm/safety';
import { FileUpload } from '#/components/upload';
import EmployeeSelect from '#/views/hrm/employee/components/employee-select.vue';

const emit = defineEmits(['success']);
const open = ref(false);
const submitting = ref(false);
const form = ref<HrmSafetyApi.HealthCheck>({});

function openForm(record?: HrmSafetyApi.HealthCheck) {
  form.value = record
    ? { ...record }
    : {
        checkType: 1,
        result: 1,
        checkDate: new Date().toISOString().slice(0, 10),
      };
  open.value = true;
}

async function handleSubmit() {
  if (!form.value.employeeId || !form.value.checkDate || !form.value.result) {
    message.warning('请选择员工、体检日期与体检结论');
    return;
  }
  submitting.value = true;
  try {
    await (form.value.id
      ? updateHealthCheck(form.value)
      : createHealthCheck(form.value));
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
    :title="form.id ? '修改体检记录' : '新增体检记录'"
    width="640px"
    @ok="handleSubmit"
  >
    <a-form class="mt-3" layout="vertical">
      <a-form-item label="员工" required>
        <EmployeeSelect v-model="form.employeeId" placeholder="请选择员工" />
      </a-form-item>
      <a-form-item label="体检类型" required>
        <a-select
          v-model:value="form.checkType"
          :options="[...HrmHealthCheckTypeOptions]"
        />
      </a-form-item>
      <a-form-item label="体检日期" required>
        <a-date-picker
          v-model:value="form.checkDate"
          class="w-full"
          value-format="YYYY-MM-DD"
        />
      </a-form-item>
      <a-form-item label="体检机构">
        <a-input v-model:value="form.institution" :maxlength="128" />
      </a-form-item>
      <a-form-item label="体检结论" required>
        <a-select
          v-model:value="form.result"
          :options="[...HrmHealthCheckResultOptions]"
        />
        <span class="text-muted-foreground ml-2 text-xs">
          结论不合格或需复查未复查 → 不允许进场
        </span>
      </a-form-item>
      <a-form-item label="复查日期">
        <a-date-picker
          v-model:value="form.recheckDate"
          class="w-full"
          value-format="YYYY-MM-DD"
        />
      </a-form-item>
      <a-form-item label="有效期至">
        <a-date-picker
          v-model:value="form.validUntil"
          class="w-full"
          value-format="YYYY-MM-DD"
        />
      </a-form-item>
      <a-form-item label="体检报告附件">
        <FileUpload
          v-model="form.fileUrls"
          :max-number="5"
          :max-size="20"
          :multiple="true"
          directory="hrm/safety/health"
        />
      </a-form-item>
    </a-form>
  </a-modal>
</template>
