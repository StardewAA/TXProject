<script lang="ts" setup>
import type { HrmPortalEmployeeApi } from '#/api/hrm/portal/employee';
import type { HrmPortalSalarySlipApi } from '#/api/hrm/portal/salary/slip';

import { computed } from 'vue';
import { useRouter } from 'vue-router';

import { useAccess } from '@vben/access';
import { useUserStore } from '@vben/stores';

import { Avatar, Button, Card, Empty } from 'ant-design-vue';
import dayjs from 'dayjs';

import { formatHrmDate } from '#/views/hrm/utils/format';

defineOptions({ name: 'HrmPortalEmployeeSurvey' });

const props = defineProps<{
  employee?: HrmPortalEmployeeApi.PortalEmployee;
  salarySlipSummary?: HrmPortalSalarySlipApi.UnreadSummary;
}>();

const router = useRouter();
const userStore = useUserStore();
const { hasAccessByCodes } = useAccess();

/** 当前登录账号名，便于 HR 定位"是哪个账号没有员工档案" */
const loginUsername = computed(() => userStore.userInfo?.username || '-');

/** 前往员工档案（HR 给自己或同事建档并绑定账号） */
function goEmployeeArchive() {
  router.push({ name: 'HrmEmployee' });
}

const showRegularDate = computed(() => {
  const regularTime = props.employee?.regularTime;
  return regularTime ? dayjs().isBefore(dayjs(regularTime)) : false;
});

/** 前往我的工资条 */
function goSalarySlip() {
  router.push({ name: 'HrmPortalSalarySlip' });
}
</script>

<template>
  <Card class="min-h-[130px]">
    <template v-if="employee">
      <div class="flex items-start gap-8 px-5 py-4">
        <Avatar :size="64" :src="employee.avatar">
          {{ employee.name?.slice(0, 1) }}
        </Avatar>
        <div class="min-w-0 pt-1">
          <div class="text-xl font-bold">Hi，{{ employee.name }}</div>
          <div class="text-muted-foreground mt-3">
            这是你在{{ employee.deptName || '部门' }}的第
            <b class="text-foreground font-bold">{{
              employee.entryDay || 0
            }}</b>
            天
          </div>
          <div class="text-muted-foreground mt-5 leading-8">
            <span>
              部门
              <b class="text-foreground font-bold">
                {{ employee.deptName || '未设置' }}
              </b>
              ，
            </span>
            <span>
              岗位
              <b class="text-foreground font-bold">
                {{ employee.postName || '未设置' }}
              </b>
              ，
            </span>
            <span>
              工号
              <b class="text-foreground font-bold">
                {{ employee.jobNumber || '未设置' }}
              </b>
              ，
            </span>
            <span v-if="employee.entryTime">
              <b class="text-foreground font-bold">
                {{ formatHrmDate(employee.entryTime) }}
              </b>
              入职
            </span>
            <span v-if="showRegularDate">
              ，将于
              <b class="text-foreground font-bold">
                {{ formatHrmDate(employee.regularTime) }}
              </b>
              转正
            </span>
          </div>
          <Button
            v-if="salarySlipSummary?.reminder"
            class="mt-2 h-auto whitespace-normal p-0"
            type="link"
            @click="goSalarySlip"
          >
            {{ salarySlipSummary.reminder }} &gt;&gt;
          </Button>
        </div>
      </div>
    </template>
    <div v-else class="py-4">
      <Empty description="">
        <template #description>
          <div class="text-sm leading-6">
            <div class="mb-1 font-medium">
              当前登录账号（{{ loginUsername }}）还没有员工档案
            </div>
            <div class="text-muted-foreground">
              员工端按你的「员工档案」展示考勤、工资条与社保。请 HR 在
              「员工管理 → 员工档案」中为你建档并绑定该账号；若你已有后台账号，
              可用「从后台用户建档」一键补齐。
            </div>
          </div>
        </template>
        <Button
          v-if="hasAccessByCodes(['hrm:employee:create'])"
          class="mt-2"
          @click="goEmployeeArchive"
        >
          去员工档案建档
        </Button>
      </Empty>
    </div>
  </Card>
</template>
