<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { HrmSalaryAttendanceRuleApi } from '#/api/hrm/salary/attendance-rule';

import { ref } from 'vue';

import { Page, useVbenModal } from '@vben/common-ui';

import { Button, Card, InputNumber, message, Modal, Space } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  calcAttendanceSalary,
  deleteAttendanceRule,
  getAttendanceRulePage,
} from '#/api/hrm/salary/attendance-rule';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';

defineOptions({ name: 'HrmSalaryAttendanceRule' });

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

function handleRefresh() {
  gridApi.query();
}

function handleCreate() {
  formModalApi.setData(null).open();
}

function handleEdit(row: HrmSalaryAttendanceRuleApi.AttendanceRule) {
  formModalApi.setData(row).open();
}

async function handleDelete(row: HrmSalaryAttendanceRuleApi.AttendanceRule) {
  await deleteAttendanceRule(row.id!);
  message.success($t('ui.actionMessage.deleteSuccess'));
  handleRefresh();
}

// ===== 计薪试算 =====
const calcOpen = ref(false);
const calcLoading = ref(false);
const calcForm = ref({
  baseSalary: 8000,
  absentDays: 0,
  personalLeaveDays: 0,
  sickLeaveDays: 0,
  lateTimes: 0,
});
const calcResult = ref<HrmSalaryAttendanceRuleApi.CalcResult>();

/**
 * 天数输入显示格式化（缺勤/事假/病假）
 *
 * 口径（业务确认 2026-09-24）：**最多 1 位小数，整数默认不带小数点**——
 * 输入 3 显示「3」（不是 3.0、更不是 3.00），输入 3.5 显示「3.5」。
 */
function formatDayInput(value: any) {
  if (value === '' || value === null || value === undefined) {
    return '';
  }
  const num = Number(value);
  if (Number.isNaN(num)) {
    return String(value);
  }
  return Number.isInteger(num) ? String(num) : num.toFixed(1);
}

async function handleCalc(row: HrmSalaryAttendanceRuleApi.AttendanceRule) {
  calcResult.value = undefined;
  calcOpen.value = true;
  await doCalc(row.id);
}

async function doCalc(ruleId?: number) {
  calcLoading.value = true;
  try {
    calcResult.value = await calcAttendanceSalary({
      ruleId,
      ...calcForm.value,
    });
  } finally {
    calcLoading.value = false;
  }
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: { schema: useGridFormSchema() },
  gridOptions: {
    columns: useGridColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          return await getAttendanceRulePage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
          });
        },
      },
    },
    rowConfig: { keyField: 'id', isHover: true },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions<HrmSalaryAttendanceRuleApi.AttendanceRule>,
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <Grid table-title="考勤计薪规则（出勤/缺勤按比例折算，HR 可配）">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '新增计薪规则',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['hrm:salary:attendance-rule:create'],
              onClick: handleCreate,
            },
          ]"
        />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '计薪试算',
              type: 'link',
              auth: ['hrm:salary:attendance-rule:query'],
              onClick: () => handleCalc(row),
            },
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['hrm:salary:attendance-rule:update'],
              onClick: () => handleEdit(row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              auth: ['hrm:salary:attendance-rule:delete'],
              popConfirm: {
                title: $t('ui.actionMessage.deleteConfirm', [row.name]),
                confirm: () => handleDelete(row),
              },
            },
          ]"
        />
      </template>
    </Grid>

    <Modal
      v-model:open="calcOpen"
      :confirm-loading="calcLoading"
      title="计薪试算"
      width="720px"
      @ok="doCalc()"
    >
      <Card :bordered="false" size="small">
        <Space class="mb-3" wrap>
          <span>工资基数</span>
          <InputNumber v-model:value="calcForm.baseSalary" :min="0" />
          <span>缺勤天数</span>
          <InputNumber
            v-model:value="calcForm.absentDays"
            :formatter="formatDayInput"
            :min="0"
            :precision="1"
            :step="0.5"
          />
          <span>事假天数</span>
          <InputNumber
            v-model:value="calcForm.personalLeaveDays"
            :formatter="formatDayInput"
            :min="0"
            :precision="1"
            :step="0.5"
          />
          <span>病假天数</span>
          <InputNumber
            v-model:value="calcForm.sickLeaveDays"
            :formatter="formatDayInput"
            :min="0"
            :precision="1"
            :step="0.5"
          />
          <span>迟到次数</span>
          <InputNumber v-model:value="calcForm.lateTimes" :min="0" />
          <Button type="primary" :loading="calcLoading" @click="doCalc()">试算</Button>
        </Space>
      </Card>
      <div v-if="calcResult" class="mt-3 leading-7">
        <p>规则：{{ calcResult.ruleName }}（满勤 {{ calcResult.fullAttendanceDays }} 天）</p>
        <p>日薪：{{ calcResult.dailyWage }} ｜ 折算缺勤：{{ calcResult.absentDaysTotal }} 天</p>
        <p>
          缺勤扣 {{ calcResult.absenceDeductAmount }} ｜ 事假扣
          {{ calcResult.personalLeaveDeductAmount }} ｜ 病假扣
          {{ calcResult.sickLeaveDeductAmount }} ｜ 扣款合计
          <span class="text-red-500">{{ calcResult.totalDeductAmount }}</span>
        </p>
        <p>
          满勤奖：{{ calcResult.fullAttendanceBonusAmount }}（{{ calcResult.fullAttendance ? '满勤' : '非满勤' }}）｜
          <b>试算应发：{{ calcResult.payableSalary }}</b>
        </p>
        <p class="text-xs text-gray-500">{{ calcResult.remark }}</p>
      </div>
    </Modal>
  </Page>
</template>
