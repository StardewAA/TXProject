<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { TrainingResourceApi } from '../api';

import { nextTick, ref } from 'vue';

import { Page, useVbenModal } from '@vben/common-ui';

import { Modal, message, Spin, Switch, Tag, TreeSelect } from 'ant-design-vue';
import mammoth from 'mammoth/mammoth.browser';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { $t } from '#/locales';
import { fetchFileBlob } from '#/utils/fetchFile';

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
      {
        field: 'estMinutes',
        title: '预估阅读时长（分钟）',
        width: 160,
        align: 'center',
        formatter: ({ cellValue }) =>
          cellValue === null || cellValue === undefined ? '-' : cellValue,
      },
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
        width: 310,
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
  // 取流与渲染分开 try：取流失败要说清原因（后端 msg / HTTP 码），
  // 渲染失败才是「格式不支持」，混在一起会把「账号未登录」误报成格式问题
  let arrayBuffer: ArrayBuffer;
  try {
    // 预览接口匿名可访问，但仍走 fetchFileBlob：本框架失败响应是 HTTP 200 + {code,msg}，
    // 裸 res.ok 会把错误 JSON 当成 docx 字节喂给 mammoth（表现为"格式暂不支持"的误导提示）
    const blob = await fetchFileBlob(previewState.value.url);
    arrayBuffer = await blob.arrayBuffer();
  } catch (e) {
    previewState.value.error = `文档取流失败：${(e as Error).message || String(e)}`;
    previewState.value.loading = false;
    return;
  }
  try {
    await nextTick(); // 等待弹窗容器挂载
    const el = docxContainer.value;
    if (!el) return;
    el.innerHTML = '';
    // docx → HTML 前端渲染（mammoth）
    const result = await mammoth.convertToHtml({ arrayBuffer });
    // 用 A4 纸张容器承载正文（mammoth 只输出裸 HTML，缺少页面排版，需外层补纸张样式）
    el.innerHTML = `<div class="docx-doc-page">${
      result.value || '<p class="docx-empty">文档内容为空</p>'
    }</div>`;
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
        class="docx-preview-box h-[72vh] overflow-auto rounded"
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
      table-title="文档资源库（PDF/Word/WPS/图片 ≤50MB，支持一次上传多个，上传即上线，自动识别页数）"
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
/* 预览容器：灰色底 + 居中 A4 白纸，还原 Word 阅读观感 */
.docx-preview-box {
  background-color: #f5f5f5;
  padding: 16px;
}
.docx-preview-box :deep(.docx-doc-page) {
  box-sizing: border-box;
  max-width: 794px; /* A4 210mm @96dpi ≈ 794px */
  margin: 0 auto;
  padding: 48px 56px;
  background-color: #fff;
  box-shadow: 0 1px 4px rgb(0 0 0 / 12%);
  color: #333;
  font-family:
    'Microsoft YaHei', 'PingFang SC', SimSun, Arial, sans-serif;
  font-size: 14px;
  line-height: 1.8;
  word-break: break-word;
  overflow-wrap: break-word;
}
.docx-preview-box :deep(.docx-doc-page img) {
  max-width: 100%;
  height: auto;
}
.docx-preview-box :deep(.docx-doc-page p) {
  margin: 0 0 0.6em;
  text-align: justify;
}
.docx-preview-box :deep(.docx-doc-page h1),
.docx-preview-box :deep(.docx-doc-page h2),
.docx-preview-box :deep(.docx-doc-page h3),
.docx-preview-box :deep(.docx-doc-page h4),
.docx-preview-box :deep(.docx-doc-page h5),
.docx-preview-box :deep(.docx-doc-page h6) {
  margin: 1.2em 0 0.6em;
  color: #222;
  font-weight: 600;
  line-height: 1.4;
}
.docx-preview-box :deep(.docx-doc-page h1) {
  font-size: 24px;
}
.docx-preview-box :deep(.docx-doc-page h2) {
  font-size: 20px;
}
.docx-preview-box :deep(.docx-doc-page h3) {
  font-size: 17px;
}
.docx-preview-box :deep(.docx-doc-page h4),
.docx-preview-box :deep(.docx-doc-page h5),
.docx-preview-box :deep(.docx-doc-page h6) {
  font-size: 15px;
}
.docx-preview-box :deep(.docx-doc-page ul),
.docx-preview-box :deep(.docx-doc-page ol) {
  margin: 0.6em 0;
  padding-left: 1.8em;
}
.docx-preview-box :deep(.docx-doc-page li) {
  margin: 0.2em 0;
}
.docx-preview-box :deep(.docx-doc-page table) {
  width: 100%;
  margin: 0.8em 0;
  table-layout: fixed;
  border-collapse: collapse;
}
.docx-preview-box :deep(.docx-doc-page td),
.docx-preview-box :deep(.docx-doc-page th) {
  padding: 6px 8px;
  border: 1px solid #ccc;
  word-break: break-all;
  line-height: 1.6;
}
.docx-preview-box :deep(.docx-doc-page th) {
  background-color: #fafafa;
  font-weight: 600;
}
.docx-preview-box :deep(.docx-doc-page a) {
  color: #1677ff;
}
.docx-preview-box :deep(.docx-empty) {
  color: #999;
}
</style>
