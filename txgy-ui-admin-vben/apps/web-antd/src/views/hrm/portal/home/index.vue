<script lang="ts" setup>
import type { HrmHomeApi } from '#/api/hrm/home';
import type { HrmPortalEmployeeApi } from '#/api/hrm/portal/employee';
import type { HrmPortalSalarySlipApi } from '#/api/hrm/portal/salary/slip';

import { onActivated, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { Col, Row, Spin } from 'ant-design-vue';

import { getEmployee } from '#/api/hrm/portal/employee';
import { getEmployeeHomeCalendar } from '#/api/hrm/portal/home/calendar';
import { getUnreadSalarySlipSummary } from '#/api/hrm/portal/salary/slip';
import HrmHomeCalendar from '#/views/hrm/home/components/calendar.vue';
import { HrmHomeCalendarItemType } from '#/views/hrm/utils/constants';
import { checkHrmPortalAccess } from '#/views/hrm/utils/employee';

import EmployeeSurvey from './components/employee-survey.vue';
import QuickActions from './components/quick-actions.vue';

defineOptions({ name: 'HrmPortalHome' });

const router = useRouter();
const accessible = ref(false);
const loading = ref(false);
const employee = ref<HrmPortalEmployeeApi.PortalEmployee>();
const salarySlipSummary = ref<HrmPortalSalarySlipApi.UnreadSummary>();
const calendarRef = ref<InstanceType<typeof HrmHomeCalendar>>();

/** 员工端仅为个人备忘展示具体时间 */
function isCalendarItemTimeVisible(item: HrmHomeApi.HomeCalendarItem) {
  return item.type === HrmHomeCalendarItemType.NOTE;
}

/** 刷新员工工作台 */
async function refreshAll() {
  loading.value = true;
  try {
    const [employeeData, salarySummary] = await Promise.all([
      getEmployee(),
      getUnreadSalarySlipSummary(),
      calendarRef.value?.refresh(),
    ]);
    employee.value = employeeData;
    salarySlipSummary.value = salarySummary;
  } finally {
    loading.value = false;
  }
}

/**
 * 员工端页面初始化
 *
 * 说明：员工端页面原先只挂在 onActivated 上，若该页没有被 KeepAlive 缓存（例如直接输入地址进入、
 * 或标签页未被标记缓存），onActivated 不会触发，页面就会一直空白。这里改为首次进入用 onMounted
 * 兜底、缓存命中后再用 onActivated 刷新。
 */
async function initPortalPage() {
  accessible.value = await checkHrmPortalAccess(router);
  if (!accessible.value) {
    return;
  }
  await refreshAll();
}

onMounted(initPortalPage);
onActivated(() => {
  if (accessible.value) {
    void refreshAll();
  }
});
</script>

<template>
  <Page v-if="accessible">
    <Spin :spinning="loading">
      <Row :gutter="[16, 16]" align="top">
        <!-- 员工端以手机访问为主：小屏单列堆叠，中屏以上恢复 16/8 分栏（阶段五 5.2） -->
        <Col :span="16" :xs="24" :sm="24" :md="16">
          <EmployeeSurvey
            :employee="employee"
            :salary-slip-summary="salarySlipSummary"
          />
          <!-- 员工自助「我要」磁贴（I-022）：高频入口 2 次点击可达 -->
          <QuickActions />
        </Col>
        <Col :span="8" :xs="24" :sm="24" :md="8">
          <HrmHomeCalendar
            ref="calendarRef"
            :get-calendar-items="getEmployeeHomeCalendar"
            :show-item-time="isCalendarItemTimeVisible"
          />
        </Col>
      </Row>
    </Spin>
  </Page>
</template>
