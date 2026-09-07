<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { MesProProjectApi } from '#/api/mes/pro/project';

import { ref } from 'vue';

import { Page, useVbenModal } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import { Button, message } from 'ant-design-vue';

import {
  ACTION_ICON,
  TableAction,
  useVbenVxeGrid,
} from '#/adapter/vxe-table';
import {
  deleteProject,
  exportProject,
  forceCloseProject,
  getProjectPage,
  resumeProject,
} from '#/api/mes/pro/project';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import ImportForm from './modules/import-form.vue';
import ProjectDetail from './modules/project-detail.vue';
import Form from './modules/project-form.vue';

defineOptions({ name: 'MesProProjectIndex' });

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

const detailRef = ref<InstanceType<typeof ProjectDetail>>(); // 项目详情弹窗

const [ImportModal, importModalApi] = useVbenModal({
  connectedComponent: ImportForm,
  destroyOnClose: true,
});

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 创建项目 */
function handleCreate() {
  formModalApi.setData({ formType: 'create' }).open();
}

/** 查看项目详情 */
function handleView(row: MesProProjectApi.Project) {
  detailRef.value?.open(row);
}

/** 编辑项目 */
function handleEdit(row: MesProProjectApi.Project) {
  formModalApi.setData({ formType: 'update', id: row.id }).open();
}

/** 删除项目 */
async function handleDelete(row: MesProProjectApi.Project) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.name]),
    duration: 0,
  });
  try {
    await deleteProject(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.name]));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 导出项目 */
async function handleExport() {
  const data = await exportProject(await gridApi.formApi.getValues());
  downloadFileFromBlobPart({ fileName: '项目管理.xls', source: data });
}

/** 导入项目 */
function handleImport() {
  importModalApi.open();
}

/** 恢复项目（已暂停 -> 进行中） */
async function handleResume(row: MesProProjectApi.Project) {
  await resumeProject(row.id!);
  message.success('项目已恢复为进行中');
  handleRefresh();
}

/** 强制结项 */
async function handleForceClose(row: MesProProjectApi.Project) {
  await forceCloseProject(row.id!);
  message.success('项目已强制结项');
  handleRefresh();
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
          return await getProjectPage({
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
  } as VxeTableGridOptions<MesProProjectApi.Project>,
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <ImportModal @success="handleRefresh" />
    <ProjectDetail ref="detailRef" @success="handleRefresh" />

    <Grid table-title="项目列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['项目']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['mes:pro-project:create'],
              onClick: handleCreate,
            },
            {
              label: $t('ui.actionTitle.import', ['项目']),
              type: 'primary',
              icon: ACTION_ICON.UPLOAD,
              auth: ['mes:pro-project:import'],
              onClick: handleImport,
            },
            {
              label: $t('ui.actionTitle.export'),
              type: 'primary',
              icon: ACTION_ICON.DOWNLOAD,
              auth: ['mes:pro-project:export'],
              onClick: handleExport,
            },
          ]"
        />
      </template>
      <template #code="{ row }">
        <Button type="link" @click="handleView(row)">
          {{ row.code }}
        </Button>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.view'),
              type: 'link',
              icon: ACTION_ICON.VIEW,
              onClick: handleView.bind(null, row),
            },
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['mes:pro-project:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: '恢复',
              type: 'link',
              auth: ['mes:pro-project:update'],
              ifShow: row.status === 3,
              onClick: handleResume.bind(null, row),
            },
            {
              label: '强制结项',
              type: 'link',
              danger: true,
              auth: ['mes:pro-project:update'],
              ifShow: row.status !== 2 && row.status !== 4,
              popConfirm: {
                title: `确认将项目「${row.name}」强制结项为已完成？`,
                confirm: handleForceClose.bind(null, row),
              },
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['mes:pro-project:delete'],
              popConfirm: {
                title: $t('ui.actionMessage.deleteConfirm', [row.name]),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
