<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { ImManagerFriendRequestApi } from '#/api/im/manager/friend/request';

import { ref } from 'vue';

import { confirm, Page } from '@vben/common-ui';

import { ElMessage } from 'element-plus';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteManagerFriendRequest,
  getManagerFriendRequestPage,
} from '#/api/im/manager/friend/request';
import { formatUserLabel } from '#/views/im/manager/utils/format';

import {
  useFriendRequestGridColumns,
  useFriendRequestGridFormSchema,
} from '../data';
import Detail from './modules/detail.vue';

defineOptions({ name: 'ImManagerFriendRequest' });

const detailRef = ref<InstanceType<typeof Detail>>();

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 打开详情 */
function handleDetail(row: ImManagerFriendRequestApi.FriendRequest) {
  detailRef.value?.open(row);
}

/** 删除申请记录（治理动作：仅删除申请本身，不影响已建立的好友关系） */
async function handleDelete(row: ImManagerFriendRequestApi.FriendRequest) {
  await confirm(
    `确认删除「${row.fromNickname || row.fromUserId}」发给「${row.toNickname || row.toUserId}」的申请记录吗？仅删除申请记录，不影响双方已建立的好友关系。`,
  );
  await deleteManagerFriendRequest(row.id);
  ElMessage.success('删除成功');
  handleRefresh();
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useFriendRequestGridFormSchema(),
  },
  gridOptions: {
    columns: useFriendRequestGridColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          return await getManagerFriendRequestPage({
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
  } as VxeTableGridOptions<ImManagerFriendRequestApi.FriendRequest>,
});
</script>

<template>
  <Page auto-content-height>
    <Detail ref="detailRef" />
    <Grid table-title="好友申请列表">
      <template #fromUser="{ row }">
        {{ formatUserLabel(row.fromNickname, row.fromUserId) }}
      </template>
      <template #toUser="{ row }">
        {{ formatUserLabel(row.toNickname, row.toUserId) }}
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '详情',
              link: true,
              icon: ACTION_ICON.VIEW,
              auth: ['im:manager:friend-request:query'],
              onClick: handleDetail.bind(null, row),
            },
            {
              label: '删除',
              link: true,
              type: 'danger',
              icon: ACTION_ICON.DELETE,
              auth: ['im:manager:friend-request:delete'],
              onClick: handleDelete.bind(null, row),
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
