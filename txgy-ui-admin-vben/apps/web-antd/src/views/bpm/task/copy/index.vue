<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { BpmProcessInstanceApi } from '#/api/bpm/processInstance';

import { DocAlert, Page } from '@vben/common-ui';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { getProcessInstanceCopyPage } from '#/api/bpm/processInstance';
import { $t } from '#/locales';
import { router } from '#/router';
import { buildApprovalDetailLocation } from '#/views/bpm/approval-route';

import { useGridColumns, useGridFormSchema } from './data';

defineOptions({ name: 'BpmCopyTask' });

const props = withDefaults(defineProps<{
  detailRouteName?: string;
  processDefinitionKeys?: string;
}>(), {
  detailRouteName: 'BpmProcessInstanceDetail',
});

/** 任务详情 */
function handleDetail(row: BpmProcessInstanceApi.ProcessInstanceCopyRespVO) {
  router.push(buildApprovalDetailLocation(
    props.detailRouteName,
    row.processInstanceId,
    undefined,
    row.activityId,
  ));
}

const [Grid] = useVbenVxeGrid({
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
          return await getProcessInstanceCopyPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            processDefinitionKeys: props.processDefinitionKeys,
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
  } as VxeTableGridOptions<BpmProcessInstanceApi.ProcessInstanceCopyRespVO>,
});
</script>

<template>
  <Page auto-content-height>
    <template #doc>
      <DocAlert
        title="审批转办、委派、抄送"
        url="https://doc.iocoder.cn/bpm/task-delegation-and-cc/"
      />
    </template>

    <Grid table-title="抄送任务">
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.detail'),
              type: 'link',
              icon: ACTION_ICON.VIEW,
              auth: ['bpm:task:query'],
              onClick: handleDetail.bind(null, row),
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
