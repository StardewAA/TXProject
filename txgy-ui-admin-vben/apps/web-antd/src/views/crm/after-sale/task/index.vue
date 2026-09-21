<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CrmAfterSaleVisitTaskApi } from '#/api/crm/after-sale/task';

import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page, useVbenModal } from '@vben/common-ui';
import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';

import { message, Modal } from 'ant-design-vue';
import dayjs from 'dayjs';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { getAfterSaleConfig } from '#/api/crm/after-sale/config';
import {
  deleteAfterSaleVisitTask,
  getAfterSaleVisitTaskPage,
} from '#/api/crm/after-sale/task';
import { $t } from '#/locales';

import RecordForm from '../record/modules/form.vue';
import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';

/** 任务状态字典（用于列表状态列文案） */
const statusLabelMap = new Map(
  getDictOptions(DICT_TYPE.CRM_AFTER_SALE_VISIT_TASK_STATUS, 'number').map(
    (item) => [item.value, item.label],
  ),
);

function getStatusLabel(status?: number) {
  return statusLabelMap.get(status as number) ?? '-';
}

/**
 * 任务状态配色标识：不同状态用不同颜色的填充方框区分，便于在列表中快速识别
 *
 * - 1 待处理：黄色
 * - 2 已完成：绿色
 * - 3 已逾期：红色
 * - 其它：灰色兜底（字典新增状态时不会丢失展示）
 */
const STATUS_BADGE_CLASS_MAP: Record<number, string> = {
  1: 'bg-amber-500 text-white',
  2: 'bg-green-500 text-white',
  3: 'bg-red-500 text-white',
};
const STATUS_BADGE_FALLBACK_CLASS = 'bg-gray-400 text-white';

/** 状态方框配色（已逾期以实时判定为准，定时任务尚未落库时同样标红） */
function getStatusBadgeClass(row: CrmAfterSaleVisitTaskApi.VisitTask) {
  if (row.status !== 2 && isOverdue(row)) {
    return STATUS_BADGE_CLASS_MAP[3]!;
  }
  return STATUS_BADGE_CLASS_MAP[row.status as number] ?? STATUS_BADGE_FALLBACK_CLASS;
}

/** 状态方框文案（已逾期以实时判定为准，与配色口径保持一致） */
function getStatusBadgeLabel(row: CrmAfterSaleVisitTaskApi.VisitTask) {
  if (row.status !== 2 && isOverdue(row)) {
    return statusLabelMap.get(3) ?? '已逾期';
  }
  return getStatusLabel(row.status);
}

/**
 * 任务在时间轴上所处的阶段（用于按钮控制与提交提示）
 *
 * - locked ：当前日期 < 计划回访日期 —— 硬时间锁，禁止提交
 * - overdue：当前日期 > 窗口期截止日期 —— 已逾期，允许提交但需二次确认
 * - window ：窗口期内（计划回访日期 ≤ 今天 ≤ 窗口期截止日期）—— 允许正常回访
 * - done   ：已完成，无需操作
 */
type VisitStage = 'done' | 'locked' | 'overdue' | 'window';

function getVisitStage(row: CrmAfterSaleVisitTaskApi.VisitTask): VisitStage {
  if (row.status === 2) {
    return 'done';
  }
  const today = dayjs().startOf('day');
  const taskDate = row.taskDate ? dayjs(row.taskDate).startOf('day') : null;
  // 窗口期截止日期为空（历史数据）时，按计划回访日期兜底
  const windowEnd = row.windowEndDate
    ? dayjs(row.windowEndDate).startOf('day')
    : taskDate;
  if (taskDate && today.isBefore(taskDate)) {
    return 'locked';
  }
  if (windowEnd && today.isAfter(windowEnd)) {
    return 'overdue';
  }
  return 'window';
}

/** 是否已逾期（含定时任务尚未落库状态的情况，列表即时标红） */
function isOverdue(row: CrmAfterSaleVisitTaskApi.VisitTask) {
  return getVisitStage(row) === 'overdue';
}

/**
 * 售后设置：是否允许提前提交回访记录
 *
 * 开启后「未到计划回访日期」不再拦截提交（按钮不置灰），仅保留逾期提示；
 * 与后端硬时间锁判定同源（CrmAfterSaleVisitTaskService#validateTaskArrived）
 */
const allowEarlyVisit = ref(false);

onMounted(async () => {
  try {
    const config = await getAfterSaleConfig();
    allowEarlyVisit.value = config?.allowEarlyVisit === true;
  } catch {
    // 取不到配置时按「不允许提前提交」处理，保持硬时间锁
  }
});

/** 硬时间锁是否生效：未到计划回访日期，且售后设置未开启「允许提前提交回访」 */
function isLocked(row: CrmAfterSaleVisitTaskApi.VisitTask) {
  return getVisitStage(row) === 'locked' && !allowEarlyVisit.value;
}

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

// 复用回访记录表单：任务完成后自动生成的记录，可直接打开填写
const [RecordFormModal, recordFormModalApi] = useVbenModal({
  connectedComponent: RecordForm,
  destroyOnClose: true,
});

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

const { push } = useRouter();

/** 查看回访任务详情（跳转详情页，待办「任务提醒」等来源同样跳此处） */
function handleDetail(row: CrmAfterSaleVisitTaskApi.VisitTask) {
  push({ name: 'CrmAfterSaleVisitTaskDetail', params: { id: row.id } });
}

/** 创建回访任务 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑回访任务 */
function handleEdit(row: CrmAfterSaleVisitTaskApi.VisitTask) {
  formModalApi.setData(row).open();
}

/** 删除回访任务 */
async function handleDelete(row: CrmAfterSaleVisitTaskApi.VisitTask) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.warrantyNo]),
    duration: 0,
  });
  try {
    await deleteAfterSaleVisitTask(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.warrantyNo]));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 打开回访记录表单（新增模式）：回填任务编号与质保档案并置灰 */
function openRecordForm(row: CrmAfterSaleVisitTaskApi.VisitTask) {
  recordFormModalApi
    .setData({ taskId: row.id, warrantyId: row.warrantyId })
    .open();
}

/**
 * 提交回访记录（【回访完成】入口）
 *
 * 点击时先弹出确认提示；确认后打开回访记录表单（新增模式），
 * 自动回填任务编号与质保档案并置灰，此时记录尚未入库、任务状态保持「待处理」；
 * 表单保存成功后才会新增回访记录，并把该任务更新为「已完成」并生成下一期任务。
 *
 * 三态交互：
 * 1）未到计划回访日期：按钮置灰，此处兜底拦截并提示；
 * 2）窗口期内：常规确认后填写；
 * 3）已超出窗口期：按钮仍可点击，提交前弹窗提示任务已逾期。
 */
function handleFillRecord(row: CrmAfterSaleVisitTaskApi.VisitTask) {
  // 硬时间锁：未到计划回访日期且未开启「允许提前提交回访」时拦截
  if (isLocked(row)) {
    message.warning(
      `未到计划回访日期（${row.taskDate}），暂不允许提交回访记录`,
    );
    return;
  }
  const stage = getVisitStage(row);
  if (stage === 'overdue') {
    Modal.confirm({
      title: '逾期提示',
      content: `该任务的窗口期已于 ${row.windowEndDate ?? row.taskDate} 截止，任务已逾期。是否继续提交回访记录？`,
      okText: '继续提交',
      cancelText: '取消',
      onOk() {
        openRecordForm(row);
      },
    });
    return;
  }
  Modal.confirm({
    title: '确认提示',
    content: '是否确认本次回访已完成，前往填写回访记录？',
    okText: '确定',
    cancelText: '取消',
    onOk() {
      openRecordForm(row);
    },
  });
}

/** 查看/编辑已有的回访记录 */
function handleViewRecord(row: CrmAfterSaleVisitTaskApi.VisitTask) {
  if (!row.recordId) {
    message.warning('该任务暂无回访记录');
    return;
  }
  recordFormModalApi.setData({ id: row.recordId }).open();
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
  },
  gridOptions: {
    columns: useGridColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          return await getAfterSaleVisitTaskPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
          });
        },
      },
    },
    rowConfig: {
      keyField: 'id',
      isHover: true,
    },
    toolbarConfig: {
      refresh: true,
      search: true,
    },
  } as VxeTableGridOptions<CrmAfterSaleVisitTaskApi.VisitTask>,
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <RecordFormModal @success="handleRefresh" />
    <Grid table-title="回访任务列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['回访任务']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['crm:after-sale-visit-task:create'],
              onClick: handleCreate,
            },
          ]"
        />
      </template>
      <template #status="{ row }">
        <span
          class="inline-flex items-center rounded px-2 py-0.5 text-xs font-medium whitespace-nowrap"
          :class="getStatusBadgeClass(row)"
        >
          {{ getStatusBadgeLabel(row) }}
        </span>
      </template>
      <template #recordStatus="{ row }">
        <span v-if="row.recordStatus === 1" style="color: #fa8c16">待填写</span>
        <span v-else-if="row.recordStatus === 2">已填写</span>
        <span v-else>—</span>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '详情',
              type: 'link',
              auth: ['crm:after-sale-visit-task:query'],
              onClick: handleDetail.bind(null, row),
            },
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['crm:after-sale-visit-task:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: '填写回访记录',
              type: 'link',
              auth: ['crm:after-sale-visit-record:create'],
              // 未完成的任务（待处理 / 已逾期）可填写，点击后确认再进入表单
              ifShow: row.status !== 2,
              // 硬时间锁：当前日期 < 计划回访日期时置灰（售后设置开启「允许提前提交回访」时不置灰）
              disabled: isLocked(row),
              tooltip: isLocked(row)
                ? `未到计划回访日期（${row.taskDate}），暂不允许提交回访记录`
                : '',
              onClick: handleFillRecord.bind(null, row),
            },
            {
              label: '查看回访记录',
              type: 'link',
              auth: ['crm:after-sale-visit-record:query'],
              ifShow: !!row.recordId,
              onClick: handleViewRecord.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['crm:after-sale-visit-task:delete'],
              popConfirm: {
                title: $t('ui.actionMessage.deleteConfirm', [row.warrantyNo]),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
