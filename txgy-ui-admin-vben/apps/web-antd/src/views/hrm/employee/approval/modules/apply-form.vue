<script lang="ts" setup>
import type { HrmEmployeeApprovalApi } from '#/api/hrm/employee/approval';

import { reactive, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import {
  DatePicker,
  Form,
  FormItem,
  Input,
  message,
  Select,
  Textarea,
} from 'ant-design-vue';

import {
  HrmEmployeeApprovalTypeOptions,
  startEmployeeApproval,
} from '#/api/hrm/employee/approval';
import EmployeeSelect from '#/views/hrm/employee/components/employee-select.vue';

const emit = defineEmits(['success']);
const submitting = ref(false);
const formState = reactive<HrmEmployeeApprovalApi.StartApproval>({
  employeeId: undefined as any,
  type: 1,
  reason: '',
  effectTime: undefined,
  remark: '',
});

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    if (!formState.employeeId) {
      message.warning('请先选择员工');
      return;
    }
    submitting.value = true;
    modalApi.lock();
    try {
      await startEmployeeApproval({ ...formState });
      message.success('申请已提交，等待审批');
      await modalApi.close();
      emit('success');
      formState.reason = '';
      formState.remark = '';
    } finally {
      submitting.value = false;
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal class="w-[720px]" title="发起异动审批申请">
    <Form class="mx-4" :label-col="{ span: 5 }" :model="formState">
      <FormItem label="员工" required>
        <EmployeeSelect v-model="formState.employeeId" placeholder="请选择员工" />
      </FormItem>
      <FormItem label="审批类型" required>
        <Select
          v-model:value="formState.type"
          :options="[...HrmEmployeeApprovalTypeOptions]"
        />
      </FormItem>
      <FormItem label="期望生效时间">
        <DatePicker
          v-model:value="formState.effectTime"
          class="w-full"
          format="YYYY-MM-DD HH:mm:ss"
          placeholder="例如：2026-10-01 00:00:00"
          show-time
          value-format="YYYY-MM-DD HH:mm:ss"
        />
      </FormItem>
      <FormItem label="申请说明">
        <Input
          v-model:value="formState.reason"
          :maxlength="200"
          placeholder="例如：试用期考核合格，申请转正"
        />
      </FormItem>
      <FormItem label="备注">
        <Textarea v-model:value="formState.remark" :maxlength="500" :rows="3" />
      </FormItem>
    </Form>
  </Modal>
</template>
