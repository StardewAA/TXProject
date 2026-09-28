<script lang="ts" setup>
import type { HrmEmployeeApprovalApi } from '#/api/hrm/employee/approval';

import { reactive, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import {
  InputNumber,
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
import { getSimpleDeptList } from '#/api/system/dept';

/** 目标部门下拉（调岗/晋升用） */
const deptOptions = ref<Array<{ label: string; value: number }>>([]);
async function loadDepts() {
  if (deptOptions.value.length > 0) return;
  const list = await getSimpleDeptList();
  deptOptions.value = list.map((item: any) => ({
    label: item.name,
    value: item.id,
  }));
}

/** 离职类型与原因（与后端枚举保持一致） */
const QUIT_TYPE_OPTIONS = [
  { label: '主动离职', value: 1 },
  { label: '被动离职', value: 2 },
  { label: '退休', value: 3 },
];
const QUIT_REASON_OPTIONS = [
  { label: '家庭原因', value: 1 }, { label: '身体原因', value: 2 },
  { label: '薪资原因', value: 3 }, { label: '交通不便', value: 4 },
  { label: '工作压力', value: 5 }, { label: '管理问题', value: 6 },
  { label: '无晋升机会', value: 7 }, { label: '职业规划', value: 8 },
  { label: '合同到期放弃续签', value: 9 }, { label: '其他个人原因', value: 10 },
  { label: '试用期内辞退', value: 11 }, { label: '违反公司条例', value: 12 },
  { label: '组织调整/裁员', value: 13 }, { label: '绩效不达标辞退', value: 14 },
  { label: '合同到期不续签', value: 15 }, { label: '其他原因', value: 16 },
];

const emit = defineEmits(['success']);
const submitting = ref(false);
const formState = reactive<HrmEmployeeApprovalApi.StartApproval>({
  employeeId: undefined as any,
  type: 1,
  reason: '',
  effectTime: undefined,
  remark: '',
  targetDeptId: undefined,
  targetPostName: '',
  targetPostLevel: '',
  targetLeaderEmployeeId: undefined,
  targetWorkAddress: '',
  quitType: undefined,
  quitReason: undefined,
  salarySettlementTime: undefined,
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
          @change="loadDepts"
        />
      </FormItem>

      <!-- 调岗/晋升：目标任职信息（审批通过后据此真正落地） -->
      <template v-if="formState.type === 2">
        <FormItem label="目标部门">
          <Select
            v-model:value="formState.targetDeptId"
            :options="deptOptions"
            allow-clear
            placeholder="请选择目标部门"
          />
        </FormItem>
        <FormItem label="目标岗位">
          <Input
            v-model:value="formState.targetPostName"
            :maxlength="128"
            placeholder="例如：保温班组长"
          />
        </FormItem>
        <FormItem label="目标职级">
          <Input
            v-model:value="formState.targetPostLevel"
            :maxlength="64"
            placeholder="例如：P3"
          />
        </FormItem>
        <FormItem label="目标上级">
          <EmployeeSelect
            v-model="formState.targetLeaderEmployeeId"
            placeholder="请选择目标直属上级"
          />
        </FormItem>
        <FormItem label="目标工作地址">
          <Input
            v-model:value="formState.targetWorkAddress"
            :maxlength="255"
            placeholder="例如：陕西省西安市高陵区泾渭工业园"
          />
        </FormItem>
      </template>

      <!-- 离职：离职类型/原因/结算时间 -->
      <template v-if="formState.type === 3">
        <FormItem label="离职类型" required>
          <Select
            v-model:value="formState.quitType"
            :options="QUIT_TYPE_OPTIONS"
            placeholder="请选择离职类型"
          />
        </FormItem>
        <FormItem label="离职原因">
          <Select
            v-model:value="formState.quitReason"
            :options="QUIT_REASON_OPTIONS"
            allow-clear
            placeholder="退休可不填"
          />
        </FormItem>
        <FormItem label="薪资结算时间">
          <DatePicker
            v-model:value="formState.salarySettlementTime"
            class="w-full"
            format="YYYY-MM-DD HH:mm:ss"
            placeholder="不填则与期望生效时间一致"
            show-time
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </FormItem>
      </template>
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
