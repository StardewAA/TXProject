<script lang="ts" setup>
import type { HrmSalaryMonthRecordApi } from '#/api/hrm/salary/month-record';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Alert, Button, message, Spin, Table, Tag } from 'ant-design-vue';

import { getSalaryMonthOnMonth } from '#/api/hrm/salary/month-record';
import { exportRowsToExcel } from '#/views/hrm/utils/export-table';
import { formatHrmMoney } from '#/views/hrm/utils/format';

defineOptions({ name: 'HrmSalaryMonthOnMonth' });

const loading = ref(false);
const data = ref<HrmSalaryMonthRecordApi.MonthOnMonth>();
const recordId = ref<number>();

const [Modal, modalApi] = useVbenModal({
  footer: false,
  onOpenChange(isOpen) {
    if (!isOpen) {
      data.value = undefined;
      recordId.value = undefined;
      return;
    }
    const payload = modalApi.getData<{ recordId?: number }>();
    recordId.value = payload?.recordId;
    load();
  },
  title: '环比上月对比',
});

async function load() {
  if (!recordId.value) {
    return;
  }
  loading.value = true;
  try {
    data.value = await getSalaryMonthOnMonth(recordId.value);
  } finally {
    loading.value = false;
  }
}

defineExpose({
  open(id: number) {
    modalApi.setData({ recordId: id }).open();
  },
});

/** 汇总口径：本月 / 上月 / 增减 */
const summaryRows = computed(() => {
  const current = data.value?.current;
  const previous = data.value?.previous;
  const build = (
    label: string,
    currentValue?: number,
    previousValue?: number,
    formatter: (value?: number) => string = (value) =>
      formatHrmMoney(Number(value ?? 0)),
  ) => {
    const hasPrevious = data.value?.hasPrevious === true;
    const delta =
      currentValue === undefined || previousValue === undefined
        ? undefined
        : Number(currentValue) - Number(previousValue);
    return {
      label,
      current: formatter(currentValue),
      previous: hasPrevious ? formatter(previousValue) : '-',
      delta,
      deltaText:
        delta === undefined || !hasPrevious
          ? '-'
          : `${delta > 0 ? '+' : ''}${formatter(delta)}`,
    };
  };
  return [
    build(
      '发薪人数',
      current?.employeeCount,
      previous?.employeeCount,
      (value) => `${value ?? 0} 人`,
    ),
    build('应发工资', current?.expectedPaySalary, previous?.expectedPaySalary),
    build('实发工资', current?.realPaySalary, previous?.realPaySalary),
    build('个人所得税', current?.personalTax, previous?.personalTax),
    build(
      '个人社保',
      current?.personalInsuranceAmount,
      previous?.personalInsuranceAmount,
    ),
    build(
      '个人公积金',
      current?.personalProvidentFundAmount,
      previous?.personalProvidentFundAmount,
    ),
  ];
});

/** 变动样式：增=红（▲），减=绿（▼），持平/无对比=- */
function deltaClass(delta?: number) {
  if (delta === undefined || delta === null || Number(delta) === 0) {
    return 'text-muted-foreground';
  }
  return Number(delta) > 0 ? 'text-red-500' : 'text-green-600';
}

function deltaText(value?: number) {
  if (value === undefined || value === null) {
    return '-';
  }
  const num = Number(value);
  if (num === 0) {
    return '持平';
  }
  return `${num > 0 ? '▲ +' : '▼ '}${formatHrmMoney(num)}`;
}

const changeTypeMeta: Record<string, { color: string; label: string }> = {
  NEW: { color: 'blue', label: '本月新增' },
  LEFT: { color: 'default', label: '本月无记录' },
  CHANGED: { color: 'orange', label: '金额变动' },
  SAME: { color: 'green', label: '金额持平' },
};

/** 薪资项：只展示有变动的（`delta != 0`），最多 30 行 */
const changedOptionRows = computed(() =>
  (data.value?.optionRows ?? [])
    .filter((row) => Number(row.delta ?? 0) !== 0)
    .slice(0, 30),
);

/** 员工：只展示有增减或人员增减的，最多 50 行 */
const changedEmployeeRows = computed(() =>
  (data.value?.employeeRows ?? [])
    .filter((row) => row.changeType !== 'SAME')
    .slice(0, 50),
);

const optionColumns = [
  { dataIndex: 'name', title: '薪资项', width: 180 },
  {
    dataIndex: 'previousValue',
    title: '上月',
    width: 140,
    align: 'right' as const,
  },
  {
    dataIndex: 'currentValue',
    title: '本月',
    width: 140,
    align: 'right' as const,
  },
  { dataIndex: 'delta', title: '增减', width: 160, align: 'right' as const },
];

const employeeColumns = [
  { dataIndex: 'employeeName', title: '员工', width: 110 },
  { dataIndex: 'jobNumber', title: '工号', width: 130 },
  {
    dataIndex: 'previousRealPaySalary',
    title: '上月实发',
    width: 130,
    align: 'right' as const,
  },
  {
    dataIndex: 'currentRealPaySalary',
    title: '本月实发',
    width: 130,
    align: 'right' as const,
  },
  {
    dataIndex: 'delta',
    title: '增减',
    width: 170,
    align: 'right' as const,
  },
  { dataIndex: 'changeType', title: '类型', width: 120 },
];

const employeeTableData = computed(() =>
  changedEmployeeRows.value.map((row) => {
    const current = row.currentRealPaySalary;
    const previous = row.previousRealPaySalary;
    const delta =
      current === undefined || previous === undefined
        ? undefined
        : Number(current) - Number(previous);
    return { ...row, delta };
  }),
);

/** 导出员工级别环比（面板内数据已全量取回，无需后端分页） */
function handleExport() {
  const rows = data.value?.employeeRows ?? [];
  if (rows.length === 0) {
    message.warning('暂无可导出的环比数据');
    return;
  }
  const count = exportRowsToExcel<HrmSalaryMonthRecordApi.MonthOnMonthEmployeeRow>(
    `${data.value?.title ?? '工资表'}环比上月`,
    [
      { title: '员工姓名', value: (row) => row.employeeName },
      { title: '工号', value: (row) => row.jobNumber },
      {
        title: '上月应发',
        value: (row) => row.previousExpectedPaySalary,
      },
      {
        title: '上月实发',
        value: (row) => row.previousRealPaySalary,
      },
      { title: '本月应发', value: (row) => row.currentExpectedPaySalary },
      { title: '本月实发', value: (row) => row.currentRealPaySalary },
      {
        title: '实发增减',
        value: (row) => {
          if (
            row.currentRealPaySalary === undefined ||
            row.previousRealPaySalary === undefined
          ) {
            return '';
          }
          return (
            Number(row.currentRealPaySalary) -
            Number(row.previousRealPaySalary)
          ).toFixed(2);
        },
      },
      {
        title: '类型',
        value: (row) =>
          changeTypeMeta[row.changeType ?? '']?.label ?? row.changeType,
      },
    ],
    rows,
  );
  message.success(`已导出 ${count} 条环比明细`);
}
</script>

<template>
  <Modal class="w-[1100px]">
    <Spin :spinning="loading">
      <Alert
        v-if="data && !data.hasPrevious"
        class="mb-3"
        message="上月没有工资表，无法环比"
        description="请先创建并核算上月工资表，再回到这里查看环比变化。"
        show-icon
        type="info"
      />
      <Alert
        v-else-if="data"
        class="mb-3"
        :message="`本月：${data.title || ''}　对比上月：${data.previousTitle || ''}`"
        description="红色 ▲ 表示比上月增加，绿色 ▼ 表示比上月减少；点击「导出环比」可导出员工级明细。"
        show-icon
        type="info"
      />

      <Table
        :columns="[
          { dataIndex: 'label', title: '汇总项', width: 140 },
          { dataIndex: 'previous', title: '上月', width: 160 },
          { dataIndex: 'current', title: '本月', width: 160 },
          { dataIndex: 'deltaText', title: '增减', width: 180 },
        ]"
        :data-source="summaryRows"
        :pagination="false"
        class="mb-4"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'deltaText'">
            <span :class="deltaClass(record.delta)">{{ record.deltaText }}</span>
          </template>
        </template>
      </Table>

      <div class="mb-2 font-medium">
        薪资项变动（按变动金额排序，最多 30 项）
      </div>
      <Table
        :columns="optionColumns"
        :data-source="changedOptionRows"
        :pagination="false"
        class="mb-4"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'previousValue'">
            {{ record.previousValue === undefined ? '-' : formatHrmMoney(record.previousValue) }}
          </template>
          <template v-else-if="column.dataIndex === 'currentValue'">
            {{ record.currentValue === undefined ? '-' : formatHrmMoney(record.currentValue) }}
          </template>
          <template v-else-if="column.dataIndex === 'delta'">
            <span :class="deltaClass(record.delta)">
              {{ deltaText(record.delta) }}
            </span>
          </template>
        </template>
      </Table>

      <div class="mb-2 flex items-center justify-between">
        <span class="font-medium">
          员工变动（按实发变动金额排序，最多 50 人）
        </span>
        <Button size="small" @click="handleExport">导出环比明细</Button>
      </div>
      <Table
        :columns="employeeColumns"
        :data-source="employeeTableData"
        :pagination="false"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'previousRealPaySalary'">
            {{ record.previousRealPaySalary === undefined ? '-' : formatHrmMoney(record.previousRealPaySalary) }}
          </template>
          <template v-else-if="column.dataIndex === 'currentRealPaySalary'">
            {{ record.currentRealPaySalary === undefined ? '-' : formatHrmMoney(record.currentRealPaySalary) }}
          </template>
          <template v-else-if="column.dataIndex === 'delta'">
            <span :class="deltaClass(record.delta)">
              {{ deltaText(record.delta) }}
            </span>
          </template>
          <template v-else-if="column.dataIndex === 'changeType'">
            <Tag :color="changeTypeMeta[record.changeType || '']?.color">
              {{ changeTypeMeta[record.changeType || '']?.label || record.changeType }}
            </Tag>
          </template>
        </template>
      </Table>
    </Spin>
  </Modal>
</template>
