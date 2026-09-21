<script lang="ts" setup>
import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { ErpLogisticsApi } from '#/api/erp/logistics';

import { useVbenModal } from '@vben/common-ui';
import { message, Tag } from 'ant-design-vue';

import { TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  getLogisticsEventPage,
  retryLogisticsEvent,
} from '#/api/erp/logistics';

const [Modal, modalApi] = useVbenModal({
  footer: false,
  fullscreenButton: true,
  onOpenChange(isOpen) {
    if (isOpen) {
      gridApi.query();
    }
  },
});

const eventTypeOptions = [
  { label: '物流任务', value: 'TASK' },
  { label: '关键节点', value: 'NODE' },
  { label: '签收结果', value: 'RECEIPT' },
];

const processStatusOptions = [
  { label: '处理成功', value: 'SUCCESS' },
  { label: '待补偿', value: 'PENDING_COMPENSATION' },
  { label: '已拒绝', value: 'REJECTED' },
];

const formSchema: VbenFormSchema[] = [
  {
    fieldName: 'logisticsTaskNo',
    label: '物流任务号',
    component: 'Input',
    componentProps: { allowClear: true, placeholder: '请输入物流任务号' },
  },
  {
    fieldName: 'eventType',
    label: '事件类型',
    component: 'Select',
    componentProps: { allowClear: true, options: eventTypeOptions },
  },
  {
    fieldName: 'processStatus',
    label: '处理状态',
    component: 'Select',
    componentProps: { allowClear: true, options: processStatusOptions },
  },
];

function getProcessStatusText(status: string) {
  return processStatusOptions.find((item) => item.value === status)?.label ?? status;
}

function getProcessStatusColor(status: string) {
  if (status === 'SUCCESS') return 'success';
  if (status === 'PENDING_COMPENSATION') return 'warning';
  return 'error';
}

async function handleRetry(row: ErpLogisticsApi.EventLog) {
  await retryLogisticsEvent(row.id);
  message.success('事件已重新处理');
  gridApi.query();
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: { schema: formSchema },
  gridOptions: {
    columns: [
      { field: 'logisticsTaskNo', title: '物流任务号', minWidth: 180, fixed: 'left' },
      { field: 'eventType', title: '事件类型', width: 100, formatter: ({ cellValue }) => eventTypeOptions.find((item) => item.value === cellValue)?.label ?? cellValue },
      { field: 'eventVersion', title: '版本', width: 80 },
      { field: 'processStatus', title: '处理状态', width: 110, slots: { default: 'status' } },
      { field: 'processMessage', title: '处理说明', minWidth: 240, showOverflow: 'tooltip' },
      { field: 'createTime', title: '记录时间', width: 165, formatter: 'formatDate' },
      { title: '操作', width: 90, fixed: 'right', slots: { default: 'actions' } },
    ],
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => getLogisticsEventPage({
          pageNo: page.currentPage,
          pageSize: page.pageSize,
          ...formValues,
        }),
      },
    },
    rowConfig: { isHover: true, keyField: 'id' },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions<ErpLogisticsApi.EventLog>,
});
</script>

<template>
  <Modal class="w-[1080px]" title="物流事件补偿">
    <div class="p-4">
      <Grid table-title="事件处理记录">
        <template #status="{ row }">
          <Tag :color="getProcessStatusColor(row.processStatus)">
            {{ getProcessStatusText(row.processStatus) }}
          </Tag>
        </template>
        <template #actions="{ row }">
          <TableAction
            :actions="[
              {
                label: '重试',
                type: 'link',
                icon: 'ep:refresh-right',
                auth: ['erp:logistics:compensate'],
                ifShow: () => row.processStatus === 'PENDING_COMPENSATION',
                popConfirm: {
                  title: '确认按原始请求重新处理该事件吗？',
                  confirm: handleRetry.bind(null, row),
                },
              },
            ]"
          />
        </template>
      </Grid>
    </div>
  </Modal>
</template>
