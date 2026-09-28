<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { ImManagerFriendApi } from '#/api/im/manager/friend';

import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { confirm, Page } from '@vben/common-ui';
import { CommonStatusEnum } from '@vben/constants';

import { ElMessage } from 'element-plus';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteManagerFriend,
  getManagerFriendPage,
} from '#/api/im/manager/friend';
import { formatUserLabel } from '#/views/im/manager/utils/format';

import { useFriendGridColumns, useFriendGridFormSchema } from './data';
import Detail from './modules/detail.vue';

defineOptions({ name: 'ImManagerFriend' });

const router = useRouter();
const detailRef = ref<InstanceType<typeof Detail>>();

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 打开详情 */
function handleDetail(row: ImManagerFriendApi.Friend) {
  detailRef.value?.open(row);
}

/** 查看私聊消息 */
function handleConversation(row: ImManagerFriendApi.Friend) {
  router.push({
    name: 'ImPrivateMessage',
    query: {
      senderId: row.userId,
      receiverId: row.friendUserId,
    },
  });
}

/** 解除好友关系（双向） */
async function handleDelete(row: ImManagerFriendApi.Friend) {
  await confirm(
    `确认解除「${row.userNickname || row.userId}」与「${row.friendNickname || row.friendUserId}」的好友关系吗？解除后双方好友列表均不再可见，且不可恢复。`,
  );
  await deleteManagerFriend(row.id);
  ElMessage.success('解除成功');
  handleRefresh();
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useFriendGridFormSchema(),
  },
  gridOptions: {
    columns: useFriendGridColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          return await getManagerFriendPage({
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
  } as VxeTableGridOptions<ImManagerFriendApi.Friend>,
});
</script>

<template>
  <Page auto-content-height>
    <Detail ref="detailRef" />
    <Grid table-title="好友关系列表">
      <template #user="{ row }">
        {{ formatUserLabel(row.userNickname, row.userId) }}
      </template>
      <template #friend="{ row }">
        {{ formatUserLabel(row.friendNickname, row.friendUserId) }}
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '详情',
              link: true,
              icon: ACTION_ICON.VIEW,
              auth: ['im:manager:friend:query'],
              onClick: handleDetail.bind(null, row),
            },
            {
              label: '查看对话',
              link: true,
              icon: ACTION_ICON.VIEW,
              onClick: handleConversation.bind(null, row),
            },
            {
              label: '解除关系',
              link: true,
              type: 'danger',
              icon: ACTION_ICON.DELETE,
              auth: ['im:manager:friend:delete'],
              ifShow: row.status === CommonStatusEnum.ENABLE,
              onClick: handleDelete.bind(null, row),
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
