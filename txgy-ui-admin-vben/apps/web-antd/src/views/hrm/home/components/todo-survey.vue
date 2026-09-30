<script lang="ts" setup>
import type { HrmHomeApi } from '#/api/hrm/home';

import { computed } from 'vue';
import { useRouter } from 'vue-router';

import { useAccess } from '@vben/access';

import { Card } from 'ant-design-vue';

import {
  HrmEmployeeStatusTab,
  HrmEmployeeTodoType,
} from '#/views/hrm/utils/constants';

defineOptions({ name: 'HrmHomeTodoSurvey' });

const props = defineProps<{
  survey?: HrmHomeApi.HrHomeTodoSurvey;
}>();

type TodoAction =
  | 'birthday'
  | 'contract'
  | 'entry'
  | 'interview'
  | 'leave'
  | 'leaveApprove'
  | 'employeeApproval'
  | 'performance'
  | 'regular'
  | 'salary';

const router = useRouter();
const { hasAccessByCodes } = useAccess();
const canQueryEmployee = hasAccessByCodes(['hrm:employee:query']);
const canQuerySalary = hasAccessByCodes(['hrm:salary:month-record:query']);
// I-021：跨模块待办中心按各自模块权限显隐
const canQueryLeave = hasAccessByCodes(['hrm:attendance:leave:query']);
const canApproveEmployee = hasAccessByCodes(['hrm:employee:approval:approve']);
const canQueryCandidate = hasAccessByCodes(['hrm:recruit:candidate:query']);
const canQueryPerformance = hasAccessByCodes(['hrm:performance:plan:query']);

const todoItems = computed(() => [
  {
    action: 'leaveApprove' as TodoAction,
    disabled: !canQueryLeave,
    label: '待批请假',
    unit: '条',
    value: props.survey?.toLeaveApproveCount || 0,
  },
  {
    action: 'employeeApproval' as TodoAction,
    disabled: !canApproveEmployee,
    label: '待批异动',
    unit: '条',
    value: props.survey?.toEmployeeApprovalCount || 0,
  },
  {
    action: 'interview' as TodoAction,
    disabled: !canQueryCandidate,
    label: '待面试',
    unit: '人',
    value: props.survey?.toInterviewCount || 0,
  },
  {
    action: 'performance' as TodoAction,
    disabled: !canQueryPerformance,
    label: '绩效进行中',
    unit: '个',
    value: props.survey?.toPerformanceCount || 0,
  },
  {
    action: 'salary' as TodoAction,
    disabled: !canQuerySalary || !props.survey?.toSalaryComputeCount,
    label: '待核算薪资',
    unit: '条',
    value: props.survey?.toSalaryComputeCount || 0,
  },
  {
    action: 'leave' as TodoAction,
    disabled: !canQueryEmployee,
    label: '待离职',
    unit: '人',
    value: props.survey?.toLeaveCount || 0,
  },
  {
    action: 'contract' as TodoAction,
    disabled: !canQueryEmployee,
    label: '合同到期',
    unit: '人',
    value: props.survey?.toExpireContractCount || 0,
  },
  {
    action: 'regular' as TodoAction,
    disabled: !canQueryEmployee,
    label: '待转正',
    unit: '人',
    value: props.survey?.toRegularCount || 0,
  },
  {
    action: 'entry' as TodoAction,
    disabled: !canQueryEmployee,
    label: '待入职',
    unit: '人',
    value: props.survey?.toEntryCount || 0,
  },
  {
    action: 'birthday' as TodoAction,
    disabled: !canQueryEmployee,
    label: '生日',
    unit: '人',
    value: props.survey?.toBirthdayCount || 0,
  },
]);

/** 打开待办对应的业务列表 */
function goTodo(action: TodoAction) {
  // I-021：招聘 / 请假 / 异动 / 绩效 四类跨模块待办直接跳对应页
  if (action === 'leaveApprove' && canQueryLeave) {
    router.push('/hrm/attendance/leave');
    return;
  }
  if (action === 'employeeApproval' && canApproveEmployee) {
    router.push('/hrm/employee/approval');
    return;
  }
  if (action === 'interview' && canQueryCandidate) {
    router.push('/hrm/recruit/candidate');
    return;
  }
  if (action === 'performance' && canQueryPerformance) {
    router.push('/hrm/performance/plan');
    return;
  }
  if (action === 'salary') {
    if (canQuerySalary) {
      router.push({ name: 'HrmSalaryMonthRecord' });
    }
    return;
  }
  if (!canQueryEmployee) {
    return;
  }
  const employeeFilters = {
    birthday: {
      statusCategory: HrmEmployeeStatusTab.ACTIVE,
      todoType: HrmEmployeeTodoType.BIRTHDAY,
    },
    contract: {
      statusCategory: HrmEmployeeStatusTab.ACTIVE,
      todoType: HrmEmployeeTodoType.CONTRACT_EXPIRE,
    },
    entry: {
      statusCategory: HrmEmployeeStatusTab.PENDING_ENTRY,
      todoType: HrmEmployeeTodoType.PENDING_ENTRY,
    },
    leave: {
      statusCategory: HrmEmployeeStatusTab.PENDING_LEAVE,
      todoType: HrmEmployeeTodoType.PENDING_LEAVE,
    },
    regular: {
      statusCategory: HrmEmployeeStatusTab.ACTIVE,
      todoType: HrmEmployeeTodoType.REGULAR,
    },
  };
  router.push({ name: 'HrmEmployee', query: employeeFilters[action] });
}
</script>

<template>
  <Card title="待办中心">
    <div class="grid grid-cols-3 gap-y-5">
      <button
        v-for="todo in todoItems"
        :key="todo.label"
        :disabled="todo.disabled"
        class="relative flex min-h-[78px] flex-col items-center justify-center border-0 bg-transparent"
        :class="todo.disabled ? 'cursor-default' : 'group cursor-pointer'"
        type="button"
        @click="goTodo(todo.action)"
      >
        <strong class="text-[22px] leading-7 group-hover:text-primary">
          {{ todo.value }}
        </strong>
        <span
          class="text-muted-foreground mt-1.5 text-[13px] group-hover:text-primary"
        >
          {{ todo.label }}
        </span>
        <small
          class="text-muted-foreground/70 absolute left-[calc(50%+18px)] top-[18px]"
        >
          {{ todo.unit }}
        </small>
      </button>
    </div>
  </Card>
</template>
