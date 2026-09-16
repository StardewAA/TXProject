<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { TrainingResourceApi } from '../api';

import { nextTick, ref } from 'vue';

import { Page, useVbenModal } from '@vben/common-ui';

import { Modal, message, Spin, Switch, Tag, TreeSelect } from 'ant-design-vue';
import mammoth from 'mammoth/mammoth.browser';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { $t } from '#/locales';

import {
  buildDocumentPreviewUrl,
  changeDocumentStatus,
  deleteDocument,
  getDocumentPage,
  getResourceCategoryTree,
  reportDocumentReading,
} from '../api';
import {
  formatFileSize,
  RESOURCE_STATUS_OPTIONS,
  statusColor,
  statusText,
} from '../constants';
import DocFormContent from './doc-form.vue';

defineOptions({ name: 'TrainingResourceDocList' });

// 分类与归档筛选（独立于搜索表单）
const categoryOptions = ref<any[]>([]);
const categoryId = ref<number | undefined>();
const includeArchived = ref(false);

function buildCategoryTree(nodes?: TrainingResourceApi.CategoryNode[]): any[] | undefined {
  if (!nodes?.length) {
    return undefined;
  }
  return nodes.map((node) => ({
    title: node.name,
    value: node.id,
    children: buildCategoryTree(node.children),
  }));
}

async function loadCategories() {
  const tree = await getResourceCategoryTree('doc');
  categoryOptions.value = buildCategoryTree(tree) || [];
}

function handleSearch() {
  gridApi.query();
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: [
      {
        fieldName: 'keyword',
        label: '标题',
        component: 'Input',
        componentProps: {
          allowClear: true,
          placeholder: '请输入文档标题',
        },
      },
      {
        fieldName: 'status',
        label: '状态',
        component: 'Select',
        componentProps: {
          allowClear: true,
          placeholder: '全部状态',
          options: RESOURCE_STATUS_OPTIONS,
        },
      },
    ],
  },
  gridOptions: {
    columns: [
      { field: 'title', title: '标题', minWidth: 200, showOverflow: true },
      { field: 'fileType', title: '类型', width: 80, align: 'center' },
      {
        field: 'fileSize',
        title: '大小',
        width: 110,
        formatter: ({ cellValue }) => formatFileSize(cellValue),
      },
      {
        field: 'pageCount',
        title: '页数',
        width: 80,
        align: 'center',
        formatter: ({ cellValue }) =>
          cellValue === null || cellValue === undefined ? '-' : cellValue,
      },
      { field: 'estMinutes', title: '预估(分)', width: 90, align: 'center' },
      { field: 'viewCount', title: '预览数', width: 80, align: 'center' },
      {
        field: 'status',
        title: '状态',
        width: 90,
        slots: { default: 'status' },
      },
      { field: 'uploaderName', title: '上传人', width: 110 },
      {
        field: 'createTime',
        title: '上传时间',
        width: 170,
        formatter: 'formatDateTime',
      },
      {
        title: '操作',
        width: 240,
        fixed: 'right',
        slots: { default: 'actions' },
      },
    ],
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          return await getDocumentPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            categoryId: categoryId.value,
            includeArchived: includeArchived.value,
            ...formValues,
          });
        },
      },
    },
    rowConfig: { keyField: 'id', isHover: true },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions<TrainingResourceApi.DocumentResource>,
});

// ========== 上传 / 编辑弹窗 ==========
const [DocFormModal, docFormModal] = useVbenModal({
  connectedComponent: DocFormContent,
  destroyOnClose: true,
});

function handleUpload() {
  docFormModal.setData(null).open();
}

function handleEdit(row: TrainingResourceApi.DocumentResource) {
  docFormModal.setData(row).open();
}

// ========== 预览（页内弹窗，不下载） ==========
/** 办公文档（Word/WPS）：前端渲染预览；PDF/图片：iframe 内联直出 */
const OFFICE_EXTS = ['doc', 'docx', 'wps'];

const previewState = ref<{
  open: boolean;
  title: string;
  fileType: string;
  url: string;
  office: boolean;
  loading: boolean;
  error: string;
}>({
  open: false,
  title: '',
  fileType: '',
  url: '',
  office: false,
  loading: false,
  error: '',
});
const docxContainer = ref<HTMLDivElement>();

async function renderOfficePreview() {
  previewState.value.loading = true;
  previewState.value.error = '';
  try {
    // 预览接口匿名可访问，直接取文件流
    const res = await fetch(previewState.value.url);
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`);
    }
    const arrayBuffer = await res.arrayBuffer();
    await nextTick(); // 等待弹窗容器挂载
    const el = docxContainer.value;
    if (!el) return;
    el.innerHTML = '';
    // docx → HTML 前端渲染（mammoth）
    const result = await mammoth.convertToHtml({ arrayBuffer });
    el.innerHTML = result.value || '<p style="color:#999">文档内容为空</p>';
  } catch {
    previewState.value.error =
      '该文档格式暂不支持在线预览（老版 .doc / .wps 需下载后查看）';
  } finally {
    previewState.value.loading = false;
  }
}

function handlePreview(row: TrainingResourceApi.DocumentResource) {
  const ext = (row.fileType || '').toLowerCase();
  previewState.value = {
    open: true,
    title: row.title || row.fileName || '文档预览',
    fileType: ext,
    url: buildDocumentPreviewUrl(row.id!),
    office: OFFICE_EXTS.includes(ext),
    loading: OFFICE_EXTS.includes(ext),
    error: '',
  };
  reportDocumentReading(row.id!).catch(() => {});
  if (OFFICE_EXTS.includes(ext)) {
    renderOfficePreview();
  }
}

// ========== 状态流转 ==========
async function handleStatus(
  row: TrainingResourceApi.DocumentResource,
  action: string,
  tip: string,
) {
  await changeDocumentStatus(row.id!, action);
  message.success(tip);
  handleSearch();
}

async function handleDelete(row: TrainingResourceApi.DocumentResource) {
  await deleteDocument(row.id!);
  message.success($t('ui.actionMessage.deleteSuccess'));
  handleSearch();
}

function buildActions(row: TrainingResourceApi.DocumentResource) {
  const actions: any[] = [
    {
      label: '预览',
      type: 'link',
      icon: ACTION_ICON.VIEW,
      onClick: () => handlePreview(row),
    },
    {
      label: '编辑',
      type: 'link',
      icon: ACTION_ICON.EDIT,
      onClick: () => handleEdit(row),
    },
  ];
  const status = row.status;
  if (status !== 4) {
    if (status === 1 || status === 3) {
      actions.push({
        label: '上线',
        type: 'link',
        popConfirm: {
          title: '确认上线该文档？',
          confirm: () => handleStatus(row, 'ONLINE', '文档已上线'),
        },
      });
    }
    if (status === 1 || status === 2) {
      actions.push({
        label: '下架',
        type: 'link',
        danger: true,
        popConfirm: {
          title: '确认下架该文档？',
          confirm: () => handleStatus(row, 'OFFLINE', '文档已下架'),
        },
      });
    }
    actions.push({
      label: '归档',
      type: 'link',
      popConfirm: {
        title: '归档后不在默认列表展示，确认归档？',
        confirm: () => handleStatus(row, 'ARCHIVE', '文档已归档'),
      },
    });
  }
  actions.push({
    label: '删除',
    type: 'link',
    danger: true,
    icon: ACTION_ICON.DELETE,
    popConfirm: {
      title: '被课程引用时无法删除，确认删除？',
      confirm: () => handleDelete(row),
    },
  });
  return actions;
}

loadCategories();
</script>

<template>
  <Page auto-content-height>
    <DocFormModal @success="handleSearch" />

    <!-- 页内预览弹窗：PDF/图片 iframe 直出；Word/WPS 前端渲染 -->
    <Modal
      v-model:open="previewState.open"
      :title="`预览 - ${previewState.title}`"
      :width="960"
      :footer="null"
    >
      <div v-if="previewState.loading" class="py-16 text-center">
        <Spin size="large" />
        <div class="mt-3 text-gray-400">文档加载中，请稍候…</div>
      </div>
      <div
        v-if="previewState.error"
        class="py-16 text-center text-gray-500"
      >
        {{ previewState.error }}
      </div>
      <iframe
        v-if="!previewState.office && !previewState.loading && !previewState.error"
        :src="previewState.url"
        class="h-[72vh] w-full rounded border-0 bg-white"
        :title="previewState.title"
      />
      <div
        v-show="previewState.office && !previewState.loading && !previewState.error"
        ref="docxContainer"
        class="docx-preview-box h-[72vh] overflow-auto rounded bg-white p-6"
      />
    </Modal>

    <!-- 分类 / 归档 筛选 -->
    <div class="mb-3 flex flex-wrap items-center gap-3">
      <TreeSelect
        v-model:value="categoryId"
        class="!w-56"
        :allow-clear="true"
        tree-default-expand-all
        placeholder="全部分类"
        :tree-data="categoryOptions"
        @change="handleSearch"
      />
      <span class="text-sm text-gray-500">包含归档</span>
      <Switch v-model:checked="includeArchived" @change="handleSearch" />
    </div>

    <Grid
      table-title="文档资源库（PDF/Word/WPS/图片 ≤50MB，上传即上线，自动识别页数）"
    >
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['文档']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              onClick: handleUpload,
            },
          ]"
        />
      </template>

      <template #fileType="{ row }">
        <Tag color="blue">{{ (row.fileType || '').toUpperCase() }}</Tag>
      </template>
      <template #status="{ row }">
        <Tag :color="statusColor(row.status) || 'default'">
          {{ statusText(row.status) }}
        </Tag>
      </template>
      <template #actions="{ row }">
        <TableAction :actions="buildActions(row)" />
      </template>
    </Grid>
  </Page>
</template>

<style scoped>
.docx-preview-box {
  color: #333 !important;
}
.docx-preview-box :deep(*) {
  color: #333 !important;
  background-color: transparent !important;
}
.docx-preview-box :deep(table) {
  border-collapse: collapse;
}
.docx-preview-box :deep(td),
.docx-preview-box :deep(th) {
  border: 1px solid #ccc;
  padding: 4px 8px;
}
</style>
