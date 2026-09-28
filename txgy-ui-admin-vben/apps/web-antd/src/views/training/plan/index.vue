<script lang="ts" setup>
import type { TrainingPlanApi } from './api';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { useRouter } from 'vue-router';

import { Page, useVbenModal } from '@vben/common-ui';

import { Button, Dropdown, message, Tag } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { $t } from '#/locales';

import {
  cancelClass,
  deleteClass,
  finishClass,
  getClassPage,
  startClass,
} from './api';
import {
  CLASS_STATUS_OPTIONS,
  CLASS_TYPE_TEXT,
  useGridColumns,
  useGridFormSchema,
} from './data';
import ClassDetailContent from './modules/class-detail.vue';
import FormModalContent from './modules/form.vue';
import ImportAllocateContent from './modules/import-allocate.vue';

defineOptions({ name: 'TrainingPlan' });

const router = useRouter();

const statusColorMap: Record<number, string> = {};
CLASS_STATUS_OPTIONS.forEach((item) => {
  statusColorMap[item.value] = item.color as string;
});

/** 刷新表格 */
function handleRefresh() {
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
          return await getClassPage({
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
  } as VxeTableGridOptions<TrainingPlanApi.Class>,
});

// ========== 新增/编辑弹窗 ==========
const [FormModal, formModal] = useVbenModal({
  connectedComponent: FormModalContent,
  destroyOnClose: true,
});

function handleCreate() {
  formModal.setData(null).open();
}

function handleEdit(row: TrainingPlanApi.Class) {
  formModal.setData(row).open();
}

// ========== 培训班详情弹窗（定课清单等） ==========
const [DetailModal, detailModal] = useVbenModal({
  connectedComponent: ClassDetailContent,
  destroyOnClose: true,
});

function handleView(row: TrainingPlanApi.Class) {
  detailModal.setData(row).open();
}

// ========== 批量导入学员并手动指定培训班 ==========
const [ImportAllocateModal, importAllocateModal] = useVbenModal({
  connectedComponent: ImportAllocateContent,
  destroyOnClose: true,
});

function handleImportAllocate() {
  importAllocateModal.open();
}

// ========== 子模块跳转（3-2 ~ 3-5 由独立菜单承载） ==========
const SUB_MODULE_PATH: Record<string, string> = {
  student: '/training/plan/student',
  progress: '/training/plan/progress',
  remind: '/training/plan/remind',
  qualification: '/training/plan/qualification',
};

function buildSubMenu(row: TrainingPlanApi.Class) {
  return [
    { key: 'student', label: '学员维护' },
    { key: 'progress', label: '学习进度' },
    { key: 'remind', label: '学习督促' },
    { key: 'qualification', label: '资格核查' },
  ];
}

function goSubModule(row: TrainingPlanApi.Class, key: string) {
  const path = SUB_MODULE_PATH[key];
  if (!path) {
    return;
  }
  router.push({ path, query: { classId: row.id } });
}

// ========== 状态机动作 ==========
async function handleStart(row: TrainingPlanApi.Class) {
  await startClass(row.id!);
  message.success('已开班，可进行学员管理与督促');
  handleRefresh();
}

async function handleFinish(row: TrainingPlanApi.Class) {
  await finishClass(row.id!);
  message.success('培训班已结业');
  handleRefresh();
}

async function handleCancel(row: TrainingPlanApi.Class) {
  await cancelClass(row.id!);
  message.success('培训班已取消');
  handleRefresh();
}

async function handleDelete(row: TrainingPlanApi.Class) {
  await deleteClass(row.id!);
  message.success($t('ui.actionMessage.deleteSuccess'));
  handleRefresh();
}

// ========== 行操作（查看/编辑 + 状态流转 + 更多子模块入口） ==========
function buildActions(row: TrainingPlanApi.Class) {
  const actions: any[] = [
    {
      label: '详情',
      type: 'link',
      icon: ACTION_ICON.VIEW,
      onClick: () => handleView(row),
    },
  ];
  const status = row.status;
  if (status === 0) {
    // 草稿：编辑 / 开班 / 删除
    actions.push(
      {
        label: '编辑',
        type: 'link',
        icon: ACTION_ICON.EDIT,
        onClick: () => handleEdit(row),
      },
      {
        label: '开班',
        type: 'link',
        icon: ACTION_ICON.VIEW,
        popConfirm: {
          title: '确认开班？开班后课程清单锁定',
          confirm: () => handleStart(row),
        },
      },
      {
        label: '删除',
        type: 'link',
        danger: true,
        icon: ACTION_ICON.DELETE,
        popConfirm: {
          title: '确认删除该培训班？',
          confirm: () => handleDelete(row),
        },
      },
    );
  } else if (status === 1) {
    // 进行中：结业 / 取消
    actions.push(
      {
        label: '结业',
        type: 'link',
        icon: ACTION_ICON.EDIT,
        popConfirm: {
          title: '确认培训班结业？',
          confirm: () => handleFinish(row),
        },
      },
      {
        label: '取消',
        type: 'link',
        danger: true,
        icon: ACTION_ICON.DELETE,
        popConfirm: {
          title: '确认取消该培训班？',
          confirm: () => handleCancel(row),
        },
      },
    );
  } else if (status === 2 || status === 3) {
    // 已结束（已完成 / 已取消）：允许删除清理
    actions.push({
      label: '删除',
      type: 'link',
      danger: true,
      icon: ACTION_ICON.DELETE,
      popConfirm: {
        title: '确认删除该培训班？删除后不可恢复',
        confirm: () => handleDelete(row),
      },
    });
  }
  return actions;
}
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <DetailModal />
    <ImportAllocateModal @success="handleRefresh" />

    <Grid table-title="培训班管理（模块三 · 3-1 培训班创建）">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['培训班']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              onClick: handleCreate,
            },
            {
              label: '批量导入学员并指定培训班',
              onClick: handleImportAllocate,
            },
          ]"
        />
      </template>

      <template #type="{ row }">
        <span>{{ row.type != null ? (CLASS_TYPE_TEXT[row.type] || '-') : '-' }}</span>
      </template>
      <template #status="{ row }">
        <Tag
          :color="row.status != null ? (statusColorMap[row.status] || 'default') : 'default'"
        >
          {{ CLASS_STATUS_OPTIONS.find((item) => item.value === row.status)?.label }}
        </Tag>
      </template>
      <template #actions="{ row }">
        <div class="flex items-center">
          <TableAction :actions="buildActions(row)" />
          <Dropdown
            :menu="{
              items: buildSubMenu(row),
              onClick: ({ key }) => goSubModule(row, key),
            }"
          >
            <Button type="link" size="small">
              更多
            </Button>
          </Dropdown>
        </div>
      </template>
    </Grid>
  </Page>
</template>
