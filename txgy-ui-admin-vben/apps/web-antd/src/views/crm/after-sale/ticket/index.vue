<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CrmAfterSaleTicketApi } from '#/api/crm/after-sale/ticket';

import { onActivated, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page, useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteAfterSaleTicket,
  getAfterSaleTicketPage,
  updateAfterSaleTicketStatus,
} from '#/api/crm/after-sale/ticket';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import AssignForm from './modules/assign.vue';
import FinishForm from './modules/finish.vue';
import Form from './modules/form.vue';

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

const [AssignModal, assignModalApi] = useVbenModal({
  connectedComponent: AssignForm,
  destroyOnClose: true,
});

const [FinishModal, finishModalApi] = useVbenModal({
  connectedComponent: FinishForm,
  destroyOnClose: true,
});

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

const { push } = useRouter();

/** 查看问题工单详情（跳转详情页，待办「工单超时预警」等来源同样跳此处） */
function handleDetail(row: CrmAfterSaleTicketApi.Ticket) {
  push({ name: 'CrmAfterSaleTicketDetail', params: { id: row.id } });
}

/** 创建问题工单 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/**
 * 支持从 CRM 首页快捷入口跳转过来时自动打开「新建」弹窗（?action=create）
 *
 * 首页的「新建工单」不再自己弹表单，而是进入售后工单模块并自动打开本页的新建弹窗，
 * 用户能同时获得工单列表的上下文。
 *
 * 用 onMounted + onActivated 而非 watch：列表页被 keep-alive 缓存，
 * 二次进入时组件不会重新 setup，只有在 onActivated 里检查才生效。
 * 打开后立即清掉 query，避免刷新 / 返回时反复弹出。
 */
const route = useRoute();

function checkCreateAction() {
  if (route.query.action !== 'create') {
    return;
  }
  // 清掉 URL 上的标记：用原生 history 改写地址栏，**不能**用 router.replace ——
  // 那会触发一次路由导航，把紧接着要打开的弹窗重置掉。
  const url = new URL(window.location.href);
  url.searchParams.delete('action');
  window.history.replaceState(window.history.state, '', url.toString());
  // 弹窗容器需在本页挂载完成后才能渲染，稍等一拍再打开
  setTimeout(() => {
    handleCreate();
  }, 300);
}

onMounted(checkCreateAction);
onActivated(checkCreateAction);

/** 编辑问题工单 */
function handleEdit(row: CrmAfterSaleTicketApi.Ticket) {
  formModalApi.setData(row).open();
}

/** 指派处理人 */
function handleAssign(row: CrmAfterSaleTicketApi.Ticket) {
  assignModalApi.setData(row).open();
}

/** 完成工单（弹窗填写工单记录，未填写不允许完成） */
function handleFinish(row: CrmAfterSaleTicketApi.Ticket) {
  finishModalApi.setData(row).open();
}

/** 删除问题工单 */
async function handleDelete(row: CrmAfterSaleTicketApi.Ticket) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.no]),
    duration: 0,
  });
  try {
    await deleteAfterSaleTicket(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.no]));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 变更工单状态 */
async function handleStatus(row: CrmAfterSaleTicketApi.Ticket, status: number) {
  const hideLoading = message.loading({
    content: '正在更新工单状态...',
    duration: 0,
  });
  try {
    await updateAfterSaleTicketStatus(row.id!, status);
    message.success('工单状态已更新');
    handleRefresh();
  } finally {
    hideLoading();
  }
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
          return await getAfterSaleTicketPage({
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
  } as VxeTableGridOptions<CrmAfterSaleTicketApi.Ticket>,
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <AssignModal @success="handleRefresh" />
    <FinishModal @success="handleRefresh" />
    <Grid table-title="售后问题工单列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['问题工单']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['crm:after-sale-ticket:create'],
              onClick: handleCreate,
            },
          ]"
        />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '详情',
              type: 'link',
              auth: ['crm:after-sale-ticket:query'],
              onClick: handleDetail.bind(null, row),
            },
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['crm:after-sale-ticket:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: '指派',
              type: 'link',
              auth: ['crm:after-sale-ticket:update'],
              onClick: handleAssign.bind(null, row),
            },
            {
              label: '完成',
              type: 'link',
              auth: ['crm:after-sale-ticket:update'],
              ifShow: row.status !== 3 && row.status !== 4,
              onClick: handleFinish.bind(null, row),
            },
            {
              label: '关闭',
              type: 'link',
              auth: ['crm:after-sale-ticket:update'],
              ifShow: row.status !== 4,
              onClick: handleStatus.bind(null, row, 4),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['crm:after-sale-ticket:delete'],
              popConfirm: {
                title: $t('ui.actionMessage.deleteConfirm', [row.no]),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
