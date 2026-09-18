<script lang="ts" setup>
import type { WmsShortageApi } from '#/api/mes/wms-shortage';

import { onBeforeUnmount, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Badge, Tag } from 'ant-design-vue';

import { TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  getShortagePage,
  getUnreadCount,
  markShortageRead,
} from '#/api/mes/wms-shortage';

import { SHORTAGE_STATUS_OPTIONS, useGridColumns } from './data';

const unreadCount = ref(0);
let unreadTimer: ReturnType<typeof setInterval> | undefined;

async function refreshUnreadCount() {
  try {
    unreadCount.value = await getUnreadCount();
  } catch {
    // 轮询失败静默
  }
}

onMounted(() => {
  refreshUnreadCount();
  unreadTimer = setInterval(refreshUnreadCount, 60_000);
});

onBeforeUnmount(() => {
  if (unreadTimer) clearInterval(unreadTimer);
});

async function handleMarkRead(row: WmsShortageApi.Message) {
  await markShortageRead(row.eventId, row.eventVersion!);
  refreshUnreadCount();
  gridApi.query();
}

function statusLabel(value: string) {
  return SHORTAGE_STATUS_OPTIONS.find((i) => i.value === value)?.label ?? value;
}

function statusColor(value: string) {
  return SHORTAGE_STATUS_OPTIONS.find((i) => i.value === value)?.color ?? 'default';
}

const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions: {
    columns: useGridColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }) => {
          const res = await getShortagePage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
          });
          return { list: res.list, total: res.total };
        },
      },
    },
    rowConfig: { keyField: 'eventId', isHover: true },
    toolbarConfig: { refresh: true, search: false },
  } as any,
});
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="缺料消息（WMS 推送）">
      <template #toolbar-tools>
        <Badge :count="unreadCount" :offset="[0, 2]">
          <span class="mr-2">未读</span>
        </Badge>
      </template>
      <template #unread="{ row }">
        <Badge v-if="row.unread" status="processing" />
      </template>
      <template #status="{ row }">
        <Tag :color="statusColor(row.shortageStatus)">
          {{ statusLabel(row.shortageStatus) }}
        </Tag>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '标记已读',
              type: 'link',
              auth: ['mes:wms-shortage:query'],
              ifShow: row.unread,
              onClick: handleMarkRead.bind(null, row),
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>

