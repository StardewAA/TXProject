<script lang="ts" setup>
import type { HrmEmployeeApi } from '#/api/hrm/employee';
import type { HrmEmployeeChangeRecordApi } from '#/api/hrm/employee/change-record';

import { h, onMounted, ref } from 'vue';

import { useAccess } from '@vben/access';
import { useVbenModal } from '@vben/common-ui';

import { Button, message, Modal, Popconfirm, Table, Tag, Textarea } from 'ant-design-vue';

import {
  cancelEmployeeChangeRecord,
  getEmployeeChangeRecordList,
} from '#/api/hrm/employee/change-record';
import {
  formatHrmDateTime,
  formatHrmEmployeeChangeType,
} from '#/views/hrm/utils/format';

import PositionChangeForm from '../../modules/position-change-form.vue';

const props = defineProps<{
  employee: HrmEmployeeApi.Employee;
  employeeId: number;
}>();
const emit = defineEmits(['success']);
const { hasAccessByCodes } = useAccess();

const loading = ref(false);
const list = ref<HrmEmployeeChangeRecordApi.EmployeeChangeRecord[]>([]);

/** 异动记录「新增」仅办理调岗，与源 EmployeeChangeRecordList 一致 */
const [PositionChangeModal, positionChangeModalApi] = useVbenModal({
  connectedComponent: PositionChangeForm,
  destroyOnClose: true,
});

async function getList() {
  loading.value = true;
  try {
    list.value = await getEmployeeChangeRecordList(props.employeeId);
  } finally {
    loading.value = false;
  }
}

function openForm() {
  positionChangeModalApi
    .setData({ employee: props.employee, mode: 'transfer' })
    .open();
}

async function handleSuccess() {
  await getList();
  emit('success');
}

/** 撤销尚未生效的异动：填原因后置为「已撤销」，不再生效 */
function handleCancel(record: HrmEmployeeChangeRecordApi.EmployeeChangeRecord) {
  let reason = '';
  Modal.confirm({
    title: '撤销该异动',
    content: () => {
      return h(
        'div',
        { class: 'flex flex-col gap-2' },
        [
          h(
            'div',
            { class: 'text-xs text-gray-500' },
            '仅未生效的异动可撤销；撤销后该记录保留在台账中，用于追溯。',
          ),
          h(Textarea, {
            rows: 3,
            placeholder: '请填写撤销原因，如：生效日期填错，重新发起',
            onChange: (e: any) => {
              reason = e.target.value;
            },
          }),
        ],
      );
    },
    async onOk() {
      if (!reason.trim()) {
        message.warning('请填写撤销原因');
        throw new Error('请填写撤销原因');
      }
      await cancelEmployeeChangeRecord({ id: record.id!, reason });
      message.success('异动已撤销');
      await getList();
      emit('success');
    },
  });
}

onMounted(getList);
defineExpose({ getList });
</script>
<template>
  <div>
    <div
      v-if="hasAccessByCodes(['hrm:employee:update'])"
      class="mb-3 flex justify-end"
    >
      <Button type="primary" @click="openForm">新增</Button>
    </div>
    <Table
      :loading="loading"
      :data-source="list"
      :pagination="false"
      :row-key="(r) => r.id"
      :scroll="{ x: 2200 }"
      bordered
      size="small"
      :columns="[
        {
          title: '异动类型',
          dataIndex: 'type',
          width: 120,
          customRender: ({ record }) =>
            formatHrmEmployeeChangeType(record.type),
        },
        {
          title: '来源',
          dataIndex: 'sourceName',
          width: 100,
          customRender: ({ record }: any) => record.sourceName || '-',
        },
        {
          title: '状态',
          key: 'status',
          width: 100,
          customRender: ({ record }: any) =>
            record.canceled
              ? h(Tag, { color: 'default' }, () => '已撤销')
              : record.applied
                ? h(Tag, { color: 'success' }, () => '已生效')
                : h(Tag, { color: 'warning' }, () => '待生效'),
        },
        { title: '原部门', dataIndex: 'oldDeptName', width: 120 },
        { title: '新部门', dataIndex: 'newDeptName', width: 120 },
        { title: '原岗位', dataIndex: 'oldPostName', width: 120 },
        { title: '新岗位', dataIndex: 'newPostName', width: 120 },
        { title: '原职级', dataIndex: 'oldPostLevel', width: 100 },
        { title: '新职级', dataIndex: 'newPostLevel', width: 100 },
        { title: '原工作地点', dataIndex: 'oldWorkAddress', width: 140 },
        { title: '新工作地点', dataIndex: 'newWorkAddress', width: 140 },
        {
          title: '原直属上级',
          dataIndex: 'oldLeaderEmployeeName',
          width: 120,
        },
        {
          title: '新直属上级',
          dataIndex: 'newLeaderEmployeeName',
          width: 120,
        },
        {
          title: '生效日期',
          dataIndex: 'effectTime',
          width: 120,
          customRender: ({ text }) => formatHrmDateTime(text),
        },
        { title: '备注', dataIndex: 'remark', width: 160 },
        {
          title: '经办 / 审批',
          key: 'audit',
          width: 170,
          customRender: ({ record }: any) => {
            const parts: string[] = [];
            if (record.operatorName) {
              parts.push(`经办：${record.operatorName}`);
            }
            if (record.approverName) {
              parts.push(`审批：${record.approverName}`);
            }
            return parts.join(' / ') || '-';
          },
        },
        {
          title: '审批通过时间',
          dataIndex: 'approveTime',
          width: 160,
          customRender: ({ text }: any) => formatHrmDateTime(text),
        },
        {
          title: '撤销信息',
          key: 'cancelInfo',
          width: 180,
          customRender: ({ record }: any) =>
            record.canceled
              ? `${record.cancelUserName || '-'}：${record.cancelReason || '-'}`
              : '-',
        },
        {
          title: '操作',
          key: 'action',
          width: 90,
          fixed: 'right',
          customRender: ({ record }: any) => {
            // 只有「未生效且未撤销」的异动可以撤销
            if (!hasAccessByCodes(['hrm:employee:update'])) return '-';
            if (record.applied || record.canceled) return '-';
            return h(
              Button,
              {
                size: 'small',
                danger: true,
                type: 'link',
                onClick: () => handleCancel(record),
              },
              () => '撤销',
            );
          },
        },
      ]"
    />
    <PositionChangeModal @success="handleSuccess" />
  </div>
</template>
