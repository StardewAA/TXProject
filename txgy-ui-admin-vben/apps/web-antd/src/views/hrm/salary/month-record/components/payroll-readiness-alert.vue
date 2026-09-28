<script lang="ts" setup>
import type { HrmSalaryMonthRecordApi } from '#/api/hrm/salary/month-record';

import { ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { useVbenModal } from '@vben/common-ui';

import { Alert, Button } from 'ant-design-vue';

import { getSalaryPayrollReadiness } from '#/api/hrm/salary/month-record';

import PayrollReadinessEmployeeList from './payroll-readiness-employee-list.vue';

defineOptions({ name: 'HrmSalaryPayrollReadinessAlert' });

const props = defineProps<{
  monthRecordId?: number;
}>();

const readiness = ref<HrmSalaryMonthRecordApi.PayrollReadiness>();
const router = useRouter();

/** 一键跳转到「薪资组」为未入组员工分配薪资组（Q-013 / I-008） */
function goSalaryGroup() {
  router.push('/hrm/salary/config/group');
}

/** 一键跳转到「薪资档案」为未定薪员工建档 */
function goSalaryEmployeeInfo() {
  router.push('/hrm/salary/employee-info');
}

const [NoSalaryGroupModal, noSalaryGroupModalApi] = useVbenModal({
  destroyOnClose: true,
  footer: false,
  title: '未加入薪资组的员工',
});

const [NoSalaryModal, noSalaryModalApi] = useVbenModal({
  destroyOnClose: true,
  footer: false,
  title: '未设置薪资档案的员工',
});

const [AttendanceMissingModal, attendanceMissingModalApi] = useVbenModal({
  destroyOnClose: true,
  footer: false,
  title: '当月无考勤数据的员工',
});

async function refresh() {
  if (!props.monthRecordId) {
    readiness.value = undefined;
    return;
  }
  readiness.value = await getSalaryPayrollReadiness(props.monthRecordId);
}

watch(
  () => props.monthRecordId,
  () => {
    refresh();
  },
  { immediate: true },
);

defineExpose({ refresh });
</script>

<template>
  <div>
    <Alert
      v-if="readiness?.noSalaryGroupEmployeeCount"
      show-icon
      type="warning"
      :style="{ marginTop: '8px' }"
    >
      <template #message>
        有
        {{ readiness.noSalaryGroupEmployeeCount }}
        名员工未加入任何薪资组，无法参与工资核算（核算时会阻断，除非勾选「忽略未入组人员」）。
        <Button type="link" @click="noSalaryGroupModalApi.open()">
          查看员工
        </Button>
        <Button type="link" @click="goSalaryGroup">去分配薪资组</Button>
      </template>
    </Alert>
    <Alert
      v-if="readiness?.noSalaryEmployeeCount"
      show-icon
      type="warning"
      :style="{ marginTop: '8px' }"
    >
      <template #message>
        有 {{ readiness.noSalaryEmployeeCount }}
        名员工没有生效薪资档案，将优先继承上月工资；无上月工资时按 0 核算。
        <Button type="link" @click="noSalaryModalApi.open()">查看员工</Button>
        <Button type="link" @click="goSalaryEmployeeInfo">去建薪资档案</Button>
      </template>
    </Alert>

    <Alert
      v-if="readiness?.attendanceMissingEmployeeCount"
      show-icon
      type="warning"
      :style="{ marginTop: '8px' }"
    >
      <template #message>
        有 {{ readiness.attendanceMissingEmployeeCount }}
        名员工当月没有任何考勤数据（出勤 0 天），缺勤/迟到扣款会按 0 核算，建议先核对打卡与请假数据。
        <Button type="link" @click="attendanceMissingModalApi.open()">
          查看员工
        </Button>
      </template>
    </Alert>

    <Alert
      v-if="readiness?.salaryOptionUnitMissingCount"
      show-icon
      type="info"
      :style="{ marginTop: '8px' }"
    >
      <template #message>
        有 {{ readiness.salaryOptionUnitMissingCount }} 个启用中的计薪项未配置单位：{{
          readiness.salaryOptionUnitMissingNames?.join('、')
        }}（建议到「薪资设置 → 工资表设置」补齐，选择计薪项时可自动带出单位）
      </template>
    </Alert>

    <NoSalaryGroupModal class="w-[860px]">
      <PayrollReadinessEmployeeList :list="readiness?.noSalaryGroupEmployees" />
    </NoSalaryGroupModal>
    <NoSalaryModal class="w-[860px]">
      <PayrollReadinessEmployeeList :list="readiness?.noSalaryEmployees" />
    </NoSalaryModal>
    <AttendanceMissingModal class="w-[860px]">
      <PayrollReadinessEmployeeList :list="readiness?.attendanceMissingEmployees" />
    </AttendanceMissingModal>
  </div>
</template>
