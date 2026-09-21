<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CrmClueApi } from '#/api/crm/clue';

import { onActivated, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { DocAlert, Page, useVbenModal } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import { Button, message, Tabs } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteClue,
  exportClue,
  getCluePage,
  transformClue,
} from '#/api/crm/clue';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';
import ImportForm from './modules/import-form.vue';

const { push } = useRouter();
const sceneType = ref('1');

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

const [ImportModal, importModalApi] = useVbenModal({
  connectedComponent: ImportForm,
  destroyOnClose: true,
});

/** 批量导入线索（Excel） */
function handleImport() {
  importModalApi.open();
}

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 导出表格 */
async function handleExport() {
  const data = await exportClue({
    sceneType: sceneType.value,
    ...(await gridApi.formApi.getValues()),
  });
  downloadFileFromBlobPart({ fileName: '线索.xls', source: data });
}

/** 创建线索 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/**
 * 支持从 CRM 首页快捷入口跳转过来时自动打开「新建」弹窗（?action=create）
 *
 * 首页的「新增线索」不再自己弹表单，而是进入线索模块并自动打开本页的新建弹窗，
 * 用户能同时获得线索列表的上下文。
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

/** 编辑线索 */
function handleEdit(row: CrmClueApi.Clue) {
  formModalApi.setData(row).open();
}

/** 删除线索 */
async function handleDelete(row: CrmClueApi.Clue) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.name]),
    duration: 0,
  });
  try {
    await deleteClue(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.name]));
    handleRefresh();
  } catch {
    hideLoading();
  }
}

/** 线索直接转化为客户（列表操作，无需进入详情页） */
async function handleTransform(row: CrmClueApi.Clue) {
  if (row.transformStatus) {
    message.warning('该线索已转化，不能重复转化');
    return;
  }
  const hideLoading = message.loading({
    content: `正在将【${row.name}】转化为客户...`,
    duration: 0,
  });
  try {
    await transformClue(row.id!);
    message.success(`线索【${row.name}】已转化为客户`);
    handleRefresh();
  } catch (e: any) {
    // 后端在“已转化”等情况下会返回具体错误信息
    message.error(e?.message ?? '转化为客户失败，请稍后重试');
  } finally {
    hideLoading();
  }
}

/** 查看线索详情 */
function handleDetail(row: CrmClueApi.Clue) {
  push({ name: 'CrmClueDetail', params: { id: row.id } });
}

/** 处理场景类型的切换 */
function handleChangeSceneType(key: number | string) {
  sceneType.value = key.toString();
  gridApi.query();
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
          return await getCluePage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            sceneType: sceneType.value,
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
  } as VxeTableGridOptions<CrmClueApi.Clue>,
});
</script>

<template>
  <Page auto-content-height>
    <template #doc>
      <DocAlert
        title="【线索】线索管理"
        url="https://doc.iocoder.cn/crm/clue/"
      />
      <DocAlert
        title="【通用】数据权限"
        url="https://doc.iocoder.cn/crm/permission/"
      />
    </template>

    <FormModal @success="handleRefresh" />
    <Grid>
      <template #toolbar-actions>
        <Tabs class="w-full" @change="handleChangeSceneType">
          <Tabs.TabPane tab="我负责的" key="1" />
          <Tabs.TabPane tab="我参与的" key="2" />
          <Tabs.TabPane tab="下属负责的" key="3" />
        </Tabs>
      </template>
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['线索']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['crm:clue:create'],
              onClick: handleCreate,
            },
            {
              label: $t('ui.actionTitle.export'),
              type: 'primary',
              icon: ACTION_ICON.DOWNLOAD,
              auth: ['crm:clue:export'],
              onClick: handleExport,
            },
            {
              label: '批量导入',
              type: 'primary',
              auth: ['crm:clue:import'],
              onClick: handleImport,
            },
          ]"
        />
      </template>
      <template #name="{ row }">
        <Button type="link" @click="handleDetail(row)">
          {{ row.name }}
        </Button>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['crm:clue:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: '转客户',
              type: 'link',
              auth: ['crm:clue:update'],
              ifShow: !row.transformStatus,
              popConfirm: {
                title: `确认将线索【${row.name}】转化为客户？`,
                confirm: handleTransform.bind(null, row),
              },
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['crm:clue:delete'],
              popConfirm: {
                title: $t('ui.actionMessage.deleteConfirm', [row.name]),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
    <ImportModal @success="handleRefresh" />
  </Page>
</template>
