<script lang="ts" setup>
import type { PageParam } from '@vben/request';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { HrmAttendanceLeaveApi } from '#/api/hrm/attendance/leave';

import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { confirm, DocAlert, Page, useVbenModal } from '@vben/common-ui';
import { buildSortingField } from '@vben/request';
import { downloadFileFromBlobPart, formatDateTime } from '@vben/utils';

import { Button, message, Modal, Textarea } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  approveAttendanceLeave,
  exportAttendanceLeave,
  getAttendanceLeavePage,
} from '#/api/hrm/attendance/leave';

import HrLeaveForm from './modules/hr-leave-form.vue';
import {
  buildLeaveQueryParams,
  useGridColumns,
  useGridFormSchema,
} from './data';

defineOptions({ name: 'HrmAttendanceLeave' });

const router = useRouter();
const exportLoading = ref(false);

/** 代提 / 补录请假（Q-004 / I-006） */
const [HrLeaveModal, hrLeaveModalApi] = useVbenModal({
  connectedComponent: HrLeaveForm,
  destroyOnClose: true,
});

function handleQuickCreate() {
  hrLeaveModalApi.open();
}

function handleRefresh() {
  gridApi.query();
}

function handleProcessDetail(processInstanceId?: string) {
  if (!processInstanceId) {
    return;
  }
  router.push({
    name: 'BpmProcessInstanceDetail',
    query: { id: processInstanceId },
  });
}

/**
 * 人工审批：员工端发起的请假统一走手动审批，由 HR 在这里终审。
 * 历史上走工作流的申请（processInstanceId 非 MANUAL- 前缀）不在此处理，
 * 仍需在「审批进度」里办理，避免流程实例与请假状态不一致。
 */
const auditOpen = ref(false);
const auditLoading = ref(false);
const auditRow = ref<HrmAttendanceLeaveApi.AttendanceLeave>();
const auditComment = ref('');

function handleAudit(row: HrmAttendanceLeaveApi.AttendanceLeave) {
  auditRow.value = row;
  auditComment.value = '';
  auditOpen.value = true;
}

async function doAudit(approved: boolean) {
  const row = auditRow.value;
  if (!row?.id) {
    return;
  }
  if (!approved && !auditComment.value.trim()) {
    message.warning('驳回请假申请必须填写原因');
    return;
  }
  auditLoading.value = true;
  try {
    await approveAttendanceLeave({
      id: row.id,
      approved,
      reason: auditComment.value.trim() || undefined,
    });
    message.success(approved ? '已通过' : '已驳回');
    auditOpen.value = false;
    gridApi.query();
  } finally {
    auditLoading.value = false;
  }
}

async function handleExport() {
  exportLoading.value = true;
  try {
    await confirm({
      content: '确认导出当前筛选条件下的请假记录吗？',
      title: '导出确认',
    });
    const formValues = await gridApi.formApi.getValues();
    const data = await exportAttendanceLeave({
      pageNo: 1,
      pageSize: 100,
      ...buildLeaveQueryParams(formValues),
    } as PageParam);
    downloadFileFromBlobPart({ fileName: '请假记录.xls', source: data });
  } catch {
  } finally {
    exportLoading.value = false;
  }
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
    handleValuesChange(_values, fieldsChanged) {
      if (fieldsChanged.includes('month')) {
        handleRefresh();
      }
    },
  },
  gridOptions: {
    columns: useGridColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page, sorts }, formValues) => {
          return await getAttendanceLeavePage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...buildLeaveQueryParams(formValues),
            ...buildSortingField(sorts),
          });
        },
      },
      sort: true,
    },
    sortConfig: {
      remote: true,
      multiple: false,
    },
    rowConfig: {
      keyField: 'id',
      isHover: true,
    },
    toolbarConfig: {
      refresh: true,
      search: true,
    },
  } as VxeTableGridOptions<HrmAttendanceLeaveApi.AttendanceLeave>,
});
</script>

<template>
  <Page auto-content-height>
    <template #doc>
      <DocAlert
        title="【考勤】考勤管理"
        url="https://doc.iocoder.cn/hrm/attendance/"
      />
    </template>
    <HrLeaveModal @success="handleRefresh" />
    <Grid table-title="请假记录">
      <!-- 空态：给出下一步动作（阶段六 6.3） -->
      <template #emptyText>
        <div class="py-8 text-center text-muted-foreground">
          暂无请假申请；员工在员工端提交后会出现在这里
        </div>
      </template>
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '代提请假',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['hrm:attendance:leave:update'],
              onClick: handleQuickCreate,
            },
            {
              label: '导出',
              type: 'default',
              icon: ACTION_ICON.DOWNLOAD,
              auth: ['hrm:attendance:leave:export'],
              loading: exportLoading,
              onClick: handleExport,
            },
          ]"
        />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '审批',
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['hrm:attendance:leave:update'],
              ifShow:
                row.approvalStatus === 1 &&
                String(row.processInstanceId ?? '').startsWith('MANUAL-'),
              onClick: handleAudit.bind(null, row),
            },
            {
              label: '审批进度',
              type: 'link',
              icon: ACTION_ICON.VIEW,
              ifShow:
                !!row.processInstanceId &&
                !String(row.processInstanceId).startsWith('MANUAL-'),
              onClick: handleProcessDetail.bind(null, row.processInstanceId),
            },
          ]"
        />
      </template>
    </Grid>

    <Modal
      v-model:open="auditOpen"
      :confirm-loading="auditLoading"
      :footer="null"
      title="请假审批"
      width="560px"
    >
      <div v-if="auditRow" class="mb-3 leading-7">
        <p>
          员工：{{ auditRow.employeeName }}（{{ auditRow.jobNumber }}）｜ 类型：{{
            auditRow.type
          }}
          ｜ 天数：{{ auditRow.day }}
        </p>
        <p>
          请假时间：{{ formatDateTime(auditRow.startTime) }} ~
          {{ formatDateTime(auditRow.endTime) }}
        </p>
        <p>请假事由：{{ auditRow.reason || '-' }}</p>
      </div>
      <Textarea
        v-model:value="auditComment"
        :maxlength="500"
        :rows="3"
        placeholder="审批意见（驳回时必填；通过可不填）"
      />
      <div class="mt-4 text-right">
        <Button danger :loading="auditLoading" @click="doAudit(false)">
          驳回
        </Button>
        <Button
          class="ml-2"
          :loading="auditLoading"
          type="primary"
          @click="doAudit(true)"
        >
          通过
        </Button>
      </div>
    </Modal>
  </Page>
</template>
