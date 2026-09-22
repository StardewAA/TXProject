<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { AiKnowledgeKnowledgeApi } from '#/api/ai/knowledge/knowledge';

import { useRouter } from 'vue-router';

import { DocAlert, Page, useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteKnowledge,
  getKnowledgePage,
} from '#/api/ai/knowledge/knowledge';
import { reindexKnowledgeSegment } from '#/api/ai/knowledge/segment';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 创建知识库 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑知识库 */
function handleEdit(row: AiKnowledgeKnowledgeApi.Knowledge) {
  formModalApi.setData(row).open();
}

/** 删除知识库 */
async function handleDelete(row: AiKnowledgeKnowledgeApi.Knowledge) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.name]),
    duration: 0,
  });
  try {
    await deleteKnowledge(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.name]));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 跳转到知识库文档页面 */
const router = useRouter();
function handleDocument(id: number) {
  router.push({
    name: 'AiKnowledgeDocument',
    query: { knowledgeId: id },
  });
}

/** 跳转到文档召回测试页面 */
function handleRetrieval(id: number) {
  router.push({
    name: 'AiKnowledgeRetrieval',
    query: { id },
  });
}

/**
 * 重建向量索引
 *
 * SQL 批量导入的分段没有向量，检索不到；向量库重启后内存向量也会丢。
 * 该动作把知识库下所有分段重新向量化（运维动作，权限 ai:knowledge:update）。
 */
async function handleReindex(row: AiKnowledgeKnowledgeApi.Knowledge) {
  const hideLoading = message.loading({
    content: `正在重建「${row.name}」的向量索引…`,
    duration: 0,
  });
  try {
    await reindexKnowledgeSegment(row.id!);
    message.success('向量索引已重建，现在可以正常召回该知识库内容');
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
          return await getKnowledgePage({
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
  } as VxeTableGridOptions<AiKnowledgeKnowledgeApi.Knowledge>,
});
</script>

<template>
  <Page auto-content-height>
    <template #doc>
      <DocAlert title="AI 手册" url="https://doc.iocoder.cn/ai/build/" />
    </template>
    <FormModal @success="handleRefresh" />
    <Grid table-title="AI 知识库列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['AI 知识库']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['ai:knowledge:create'],
              onClick: handleCreate,
            },
          ]"
        />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['ai:knowledge:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: $t('ui.widgets.document'),
              type: 'link',
              icon: ACTION_ICON.BOOK,
              auth: ['ai:knowledge:query'],
              onClick: handleDocument.bind(null, row.id),
            },
            {
              label: '召回测试',
              type: 'link',
              icon: ACTION_ICON.SEARCH,
              auth: ['ai:knowledge:query'],
              onClick: handleRetrieval.bind(null, row.id),
            },
            {
              label: '重建向量索引',
              type: 'link',
              icon: ACTION_ICON.REFRESH,
              auth: ['ai:knowledge:update'],
              onClick: handleReindex.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['ai:knowledge:delete'],
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
