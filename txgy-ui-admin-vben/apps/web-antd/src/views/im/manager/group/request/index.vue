<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { ImManagerGroupRequestApi } from '#/api/im/manager/group/request';

import { ref } from 'vue';

import { confirm, Page } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteManagerGroupRequest,
  getManagerGroupRequestPage,
} from '#/api/im/manager/group/request';
import {
  formatGroupLabel,
  formatUserLabel,
} from '#/views/im/manager/utils/format';

import {
  useGroupRequestGridColumns,
  useGroupRequestGridFormSchema,
} from '../data';
import Detail from './modules/detail.vue';

defineOptions({ name: 'ImManagerGroupRequest' });

const detailRef = ref<InstanceType<typeof Detail>>();

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 打开详情 */
function handleDetail(row: ImManagerGroupRequestApi.GroupRequest) {
  detailRef.value?.open(row);
}

/** 删除申请记录（治理动作：仅删除申请本身，不影响已建立的群成员关系） */
async function handleDelete(row: ImManagerGroupRequestApi.GroupRequest) {
  await confirm(
    `确认删除「${row.userNickname || row.userId}」申请加入「${row.groupName || row.groupId}」的申请记录吗？仅删除申请记录，不影响已建立的群成员关系。`,
  );
  await deleteManagerGroupRequest(row.id);
  message.success('删除成功');
  handleRefresh();
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGroupRequestGridFormSchema(),
  },
  gridOptions: {
    columns: useGroupRequestGridColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          return await getManagerGroupRequestPage({
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
  } as VxeTableGridOptions<ImManagerGroupRequestApi.GroupRequest>,
});
</script>

<template>
  <Page auto-content-height>
    <Detail ref="detailRef" />
    <Grid table-title="加群申请列表">
      <template #group="{ row }">
        {{ formatGroupLabel(row.groupName, row.groupId) }}
      </template>
      <template #user="{ row }">
        {{ formatUserLabel(row.userNickname, row.userId) }}
      </template>
      <template #inviter="{ row }">
        <template v-if="row.inviterUserId">
          {{ formatUserLabel(row.inviterNickname, row.inviterUserId) }}
        </template>
        <span v-else>主动申请</span>
      </template>
      <template #handler="{ row }">
        <template v-if="row.handleUserId">
          {{ formatUserLabel(row.handleNickname, row.handleUserId) }}
        </template>
        <span v-else>-</span>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '详情',
              type: 'link',
              icon: ACTION_ICON.VIEW,
              auth: ['im:manager:group-request:query'],
              onClick: handleDetail.bind(null, row),
            },
            {
              label: '删除',
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['im:manager:group-request:delete'],
              onClick: handleDelete.bind(null, row),
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
