<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { HrmEmployeeApprovalApi } from '#/api/hrm/employee/approval';

import { ref } from 'vue';

import { Page, useVbenModal } from '@vben/common-ui';

import { message, Modal, Tag, Textarea } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  approveEmployeeApproval,
  getEmployeeApprovalPage,
  HrmEmployeeApprovalStatusOptions,
} from '#/api/hrm/employee/approval';
import { $t } from '#/locales';

import ApplyForm from '../modules/apply-form.vue';

defineOptions({ name: 'HrmEmployeeApprovalList' });

const TYPE_LABEL: Record<number, string> = {
  1: '转正',
  2: '调岗/晋升',
  3: '离职',
};
const STATUS_COLOR: Record<number, string> = {
  1: 'warning',
  2: 'success',
  3: 'error',
};

const [ApplyModal, applyModalApi] = useVbenModal({
  connectedComponent: ApplyForm,
  destroyOnClose: true,
});

function handleRefresh() {
  gridApi.query();
}

// ===== 审批（通过/驳回）=====
const auditOpen = ref(false);
const auditLoading = ref(false);
const auditRow = ref<HrmEmployeeApprovalApi.ApprovalRecord>();
const auditComment = ref('');

function handleAudit(row: HrmEmployeeApprovalApi.ApprovalRecord) {
  auditRow.value = row;
  auditComment.value = '';
  auditOpen.value = true;
}

async function doAudit(approved: boolean) {
  if (!auditRow.value?.id) {
    return;
  }
  if (!approved && !auditComment.value) {
    message.warning('驳回时请填写审批意见');
    return;
  }
  auditLoading.value = true;
  try {
    await approveEmployeeApproval({
      id: auditRow.value.id,
      employeeId: auditRow.value.employeeId,
      type: auditRow.value.type,
      approved,
      comment: auditComment.value,
    });
    message.success(approved ? '已通过' : '已驳回');
    auditOpen.value = false;
    handleRefresh();
  } finally {
    auditLoading.value = false;
  }
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: [
      {
        fieldName: 'status',
        label: '状态',
        component: 'Select',
        componentProps: {
          options: [...HrmEmployeeApprovalStatusOptions],
          placeholder: '请选择状态',
          allowClear: true,
        },
      },
      {
        fieldName: 'type',
        label: '审批类型',
        component: 'Select',
        componentProps: {
          options: Object.entries(TYPE_LABEL).map(([value, label]) => ({
            label,
            value: Number(value),
          })),
          placeholder: '请选择类型',
          allowClear: true,
        },
      },
    ],
  },
  gridOptions: {
    columns: [
      { field: 'employeeName', title: '员工姓名', width: 110 },
      { field: 'jobNumber', title: '工号', width: 120 },
      {
        field: 'type',
        title: '审批类型',
        width: 120,
        formatter: ({ cellValue }) => TYPE_LABEL[cellValue as number] ?? '-',
      },
      { field: 'reason', title: '申请说明', minWidth: 220 },
      { field: 'effectTime', title: '期望生效时间', width: 170 },
      { field: 'applicantName', title: '申请人', width: 100 },
      { field: 'applyTime', title: '申请时间', width: 170 },
      {
        field: 'status',
        title: '状态',
        width: 100,
        slots: { default: 'status' },
      },
      { field: 'approverName', title: '审批人', width: 100 },
      { field: 'approveComment', title: '审批意见', minWidth: 180 },
      { title: '操作', width: 140, fixed: 'right', slots: { default: 'actions' } },
    ],
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          return await getEmployeeApprovalPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
          });
        },
      },
    },
    rowConfig: { keyField: 'id', isHover: true },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions<HrmEmployeeApprovalApi.ApprovalRecord>,
});
</script>

<template>
  <Page auto-content-height>
    <ApplyModal @success="handleRefresh" />
    <Grid table-title="员工异动审批（手动审批：发起 → 待审批 → 通过/驳回）">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '发起审批申请',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['hrm:employee:approval:start'],
              onClick: () => applyModalApi.setData(null).open(),
            },
          ]"
        />
      </template>
      <template #status="{ row }">
        <Tag :color="STATUS_COLOR[row.status as number] ?? 'default'">
          {{
            HrmEmployeeApprovalStatusOptions.find(
              (item) => item.value === row.status,
            )?.label ?? '-'
          }}
        </Tag>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '审批',
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['hrm:employee:approval:approve'],
              disabled: row.status !== 1,
              onClick: () => handleAudit(row),
            },
          ]"
        />
      </template>
    </Grid>

    <Modal
      v-model:open="auditOpen"
      :confirm-loading="auditLoading"
      :footer="null"
      title="异动审批"
      width="560px"
    >
      <div v-if="auditRow" class="mb-3 leading-7">
        <p>
          员工：{{ auditRow.employeeName }}（{{ auditRow.jobNumber }}）｜
          类型：{{ TYPE_LABEL[auditRow.type as number] }}
        </p>
        <p>申请说明：{{ auditRow.reason || '-' }}</p>
        <p>期望生效时间：{{ auditRow.effectTime || '-' }}</p>
      </div>
      <Textarea
        v-model:value="auditComment"
        :maxlength="500"
        :rows="3"
        placeholder="审批意见（驳回时必填）"
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
