<script lang="ts" setup>
import type { FormInstance } from 'ant-design-vue';
import type { Rule } from 'ant-design-vue/es/form';

import { nextTick, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';
import { fromTimestampPickerValue, toTimestampPickerValue } from '@vben/utils';

import {
  Alert,
  DatePicker,
  Form,
  FormItem,
  Input,
  InputNumber,
  message,
  Select,
  Switch,
} from 'ant-design-vue';

import { createAttendanceLeaveByHr } from '#/api/hrm/attendance/leave';
import EmployeeSelect from '#/views/hrm/employee/components/employee-select.vue';

defineOptions({ name: 'HrmAttendanceLeaveHrForm' });

const emit = defineEmits<{ success: [] }>();

const formRef = ref<FormInstance>();
const formData = ref<{
  approveImmediately: boolean;
  day: number;
  employeeId?: number;
  endTime?: number;
  reason: string;
  remark?: string;
  startTime?: number;
  type?: string;
}>({
  approveImmediately: true,
  day: 1,
  employeeId: undefined,
  endTime: undefined,
  reason: '',
  remark: '',
  startTime: undefined,
  type: undefined,
});
const leaveTypeOptions = getDictOptions(
  DICT_TYPE.HRM_ATTENDANCE_LEAVE_TYPE,
  'string',
);

const formRules: Record<string, Rule[]> = {
  employeeId: [{ required: true, message: '请选择员工', trigger: 'change' }],
  type: [{ required: true, message: '请选择请假类型', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [
    { required: true, message: '请选择结束时间', trigger: 'change' },
    {
      validator: async (_rule, value) => {
        if (
          value &&
          formData.value.startTime &&
          Number(value) <= Number(formData.value.startTime)
        ) {
          throw new Error('结束时间必须晚于开始时间');
        }
      },
      trigger: 'change',
    },
  ],
  day: [{ required: true, message: '请输入请假天数', trigger: 'blur' }],
  reason: [{ required: true, message: '请输入请假事由', trigger: 'blur' }],
};

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    await submitForm();
  },
  onOpenChange(isOpen) {
    if (!isOpen) {
      resetForm();
    }
  },
  title: '代提 / 补录请假',
});

/** 打开弹窗 */
function open() {
  resetForm();
  modalApi.open();
}

defineExpose({ open });

/** 提交 */
async function submitForm() {
  if (!formRef.value) {
    return;
  }
  try {
    await formRef.value.validate();
  } catch {
    return;
  }
  modalApi.lock();
  try {
    await createAttendanceLeaveByHr({
      approveImmediately: formData.value.approveImmediately,
      day: Number(formData.value.day),
      employeeId: Number(formData.value.employeeId),
      endTime: Number(formData.value.endTime),
      reason: formData.value.reason,
      remark: formData.value.remark,
      startTime: Number(formData.value.startTime),
      type: formData.value.type as string,
    });
    message.success(
      formData.value.approveImmediately
        ? '请假已补录并直接生效'
        : '请假已提交，等待审批',
    );
    await modalApi.close();
    emit('success');
  } finally {
    modalApi.unlock();
  }
}

/** 重置 */
async function resetForm() {
  formData.value = {
    approveImmediately: true,
    day: 1,
    employeeId: undefined,
    endTime: undefined,
    reason: '',
    remark: '',
    startTime: undefined,
    type: undefined,
  };
  await nextTick();
  formRef.value?.resetFields();
}
</script>

<template>
  <Modal class="w-[640px]">
    <Alert
      class="mb-3"
      message="HR 代提 / 补录请假"
      description="用于员工未能在员工端提交的场景（如现场口头请假、纸质假条补录）。默认直接生效；也可选择进入审批流程。"
      show-icon
      type="info"
    />
    <Form
      ref="formRef"
      :label-col="{ style: { width: '110px' } }"
      :model="formData"
      :rules="formRules"
    >
      <FormItem label="员工" name="employeeId">
        <EmployeeSelect v-model:value="formData.employeeId" />
      </FormItem>
      <FormItem label="请假类型" name="type">
        <Select
          v-model:value="formData.type"
          allow-clear
          class="w-full"
          :options="leaveTypeOptions"
          placeholder="请选择请假类型"
        />
      </FormItem>
      <FormItem label="开始时间" name="startTime">
        <DatePicker
          :value="toTimestampPickerValue(formData.startTime)"
          class="w-full"
          format="YYYY-MM-DD HH:mm:ss"
          placeholder="请选择开始时间"
          show-time
          value-format="x"
          @update:value="formData.startTime = fromTimestampPickerValue($event)"
        />
      </FormItem>
      <FormItem label="结束时间" name="endTime">
        <DatePicker
          :value="toTimestampPickerValue(formData.endTime)"
          class="w-full"
          format="YYYY-MM-DD HH:mm:ss"
          placeholder="请选择结束时间"
          show-time
          value-format="x"
          @update:value="formData.endTime = fromTimestampPickerValue($event)"
        />
      </FormItem>
      <FormItem label="请假天数" name="day">
        <InputNumber
          v-model:value="formData.day"
          class="w-full"
          :min="0.01"
          :precision="2"
          placeholder="请输入请假天数"
          :step="0.5"
        />
      </FormItem>
      <FormItem label="请假事由" name="reason">
        <Input.TextArea
          v-model:value="formData.reason"
          :maxlength="300"
          placeholder="请输入请假事由"
          :rows="3"
          show-count
        />
      </FormItem>
      <FormItem label="备注" name="remark">
        <Input.TextArea
          v-model:value="formData.remark"
          :maxlength="500"
          placeholder="请输入备注"
          :rows="2"
          show-count
        />
      </FormItem>
      <FormItem label="直接生效" name="approveImmediately">
        <Switch
          v-model:checked="formData.approveImmediately"
          checked-children="已通过"
          un-checked-children="走审批"
        />
        <span class="ml-2 text-muted-foreground">
          补录历史假选「已通过」；需要走审批选「走审批」
        </span>
      </FormItem>
    </Form>
  </Modal>
</template>
