<script lang="ts" setup>
import type { HrmEmployeeApprovalApi } from '#/api/hrm/employee/approval';

import { reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  Button,
  Card,
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

defineOptions({ name: 'HrmEmployeeApproval' });

const submitting = ref(false);
const formState = reactive<HrmEmployeeApprovalApi.StartApproval>({
  employeeId: undefined as any,
  type: 1,
  reason: '',
  effectTime: undefined,
  remark: '',
});

async function handleSubmit() {
  if (!formState.employeeId) {
    message.warning('请先选择员工');
    return;
  }
  submitting.value = true;
  try {
    const processInstanceId = await startEmployeeApproval({ ...formState });
    message.success(`审批已发起，流程实例：${processInstanceId}`);
    formState.reason = '';
    formState.remark = '';
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <Page auto-content-height>
    <Card :bordered="false" title="发起员工异动审批">
      <div class="mb-4 text-muted-foreground text-sm leading-6">
        发起后会进入工作流审批（审批中心可查看进度）。审批通过后：<b>转正</b>自动生效；
        <b>调岗/晋升</b>与<b>离职</b>回到对应的异动记录/离职单确认生效。
        <br />
        流程标识需与「工作流程 → 流程管理」里发布的 key 一致：
        <code>hrm_regular_approval</code> / <code>hrm_transfer_approval</code> /
        <code>hrm_quit_approval</code>
      </div>
      <Form :label-col="{ span: 4 }" :model="formState" class="max-w-[760px]">
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
            show-time
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="例如：2026-10-01 00:00:00"
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
          <Textarea v-model:value="formState.remark" :rows="3" :maxlength="500" />
        </FormItem>
        <FormItem :wrapper-col="{ offset: 4 }">
          <Button :loading="submitting" type="primary" @click="handleSubmit">
            发起审批
          </Button>
        </FormItem>
      </Form>
    </Card>
  </Page>
</template>
