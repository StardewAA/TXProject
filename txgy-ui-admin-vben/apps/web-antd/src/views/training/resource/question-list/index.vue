<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { TrainingResourceApi } from '../api';

import { ref } from 'vue';

import { Page, useVbenModal } from '@vben/common-ui';

import { useAccessStore } from '@vben/stores';

import { Button, message, Modal, Select, Tag } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { $t } from '#/locales';

import {
  changeQuestionStatus,
  deleteQuestion,
  getQuestion,
  getQuestionPage,
  getResourceTagList,
  importQuestions,
} from '../api';
import {
  difficultyText,
  QUESTION_DIFFICULTY_OPTIONS,
  QUESTION_TYPE_COLOR,
  QUESTION_TYPE_OPTIONS,
  QUESTION_TYPE_TEXT,
} from '../constants';
import QuestionFormContent from './question-form.vue';

defineOptions({ name: 'TrainingResourceQuestionList' });

const accessStore = useAccessStore();

// 标签筛选（独立于搜索表单）
const tagOptions = ref<any[]>([]);
const tagId = ref<number | undefined>();
async function loadTags() {
  const list = await getResourceTagList();
  tagOptions.value = list.map((item) => ({ label: item.name, value: item.id }));
}

function handleSearch() {
  gridApi.query();
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: [
      {
        fieldName: 'keyword',
        label: '关键词',
        component: 'Input',
        componentProps: {
          allowClear: true,
          placeholder: '题干关键词',
        },
      },
      {
        fieldName: 'qtype',
        label: '题型',
        component: 'Select',
        componentProps: {
          allowClear: true,
          placeholder: '全部题型',
          options: QUESTION_TYPE_OPTIONS,
        },
      },
      {
        fieldName: 'difficulty',
        label: '难度',
        component: 'Select',
        componentProps: {
          allowClear: true,
          placeholder: '全部难度',
          options: QUESTION_DIFFICULTY_OPTIONS,
        },
      },
      {
        fieldName: 'status',
        label: '状态',
        component: 'Select',
        componentProps: {
          allowClear: true,
          placeholder: '全部状态',
          options: [
            { label: '启用', value: 1 },
            { label: '停用', value: 2 },
          ],
        },
      },
    ],
  },
  gridOptions: {
    columns: [
      { field: 'stem', title: '题干', minWidth: 260, showOverflow: true },
      {
        field: 'qtype',
        title: '题型',
        width: 90,
        align: 'center',
        slots: { default: 'qtype' },
      },
      {
        field: 'difficulty',
        title: '难度',
        width: 70,
        align: 'center',
        slots: { default: 'difficulty' },
      },
      {
        field: 'answer',
        title: '答案',
        width: 130,
        showOverflow: true,
      },
      {
        field: 'tagNames',
        title: '标签',
        minWidth: 130,
        showOverflow: true,
        slots: { default: 'tags' },
      },
      {
        field: 'status',
        title: '状态',
        width: 80,
        align: 'center',
        slots: { default: 'status' },
      },
      { field: 'creatorName', title: '创建人', width: 110 },
      {
        field: 'createTime',
        title: '创建时间',
        width: 170,
        formatter: 'formatDateTime',
      },
      {
        title: '操作',
        width: 220,
        fixed: 'right',
        slots: { default: 'actions' },
      },
    ],
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          return await getQuestionPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            tagId: tagId.value,
            ...formValues,
          });
        },
      },
    },
    rowConfig: { keyField: 'id', isHover: true },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions<TrainingResourceApi.Question>,
});

// ========== 新增 / 编辑 ==========
const [QuestionFormModal, questionFormModal] = useVbenModal({
  connectedComponent: QuestionFormContent,
  destroyOnClose: true,
});

function handleCreate() {
  questionFormModal.setData(undefined).open();
}

function handleEdit(row: TrainingResourceApi.Question) {
  questionFormModal.setData(row.id).open();
}

// ========== 详情弹窗 ==========
const detailVisible = ref(false);
const detail = ref<TrainingResourceApi.Question>();
const detailOptions = ref<{ key: string; text: string; isCorrect: boolean }[]>([]);

async function handleView(row: TrainingResourceApi.Question) {
  const res = await getQuestion(row.id!);
  detail.value = res;
  try {
    detailOptions.value = res.optionsJson
      ? (JSON.parse(res.optionsJson) as any[])
      : [];
  } catch {
    detailOptions.value = [];
  }
  detailVisible.value = true;
}

// ========== 状态 ==========
async function handleToggleStatus(row: TrainingResourceApi.Question) {
  const enable = row.status === 2;
  await changeQuestionStatus(row.id!, enable ? 'ENABLE' : 'DISABLE');
  message.success(enable ? '试题已启用' : '试题已停用');
  handleSearch();
}

async function handleDelete(row: TrainingResourceApi.Question) {
  await deleteQuestion(row.id!);
  message.success($t('ui.actionMessage.deleteSuccess'));
  handleSearch();
}

// ========== Excel 导入 / 模板下载 ==========
const importErrors = ref<{ rowNo: number; error: string }[]>([]);
const importResultVisible = ref(false);

function triggerImport() {
  document.getElementById('question-import-file')?.click();
}

async function handleImportFile(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) {
    return;
  }
  const resp = await importQuestions(file);
  const success = resp.successCount ?? 0;
  const errors = resp.errors ?? [];
  message.success(`批量导入完成：成功 ${success} 条，失败 ${errors.length} 条`);
  if (errors.length > 0) {
    importErrors.value = errors;
    importResultVisible.value = true;
  }
  handleSearch();
}

async function handleDownloadTemplate() {
  const resp = await fetch('/admin-api/training/question/import-template', {
    headers: { Authorization: `Bearer ${accessStore.accessToken}` },
  });
  if (!resp.ok) {
    message.error('模板下载失败，请重新登录后重试');
    return;
  }
  const blob = await resp.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = '试题导入模板.xlsx';
  link.click();
  URL.revokeObjectURL(url);
}

function buildActions(row: TrainingResourceApi.Question) {
  return [
    {
      label: '详情',
      type: 'link',
      icon: ACTION_ICON.VIEW,
      onClick: () => handleView(row),
    },
    {
      label: '编辑',
      type: 'link',
      icon: ACTION_ICON.EDIT,
      onClick: () => handleEdit(row),
    },
    {
      label: row.status === 1 ? '停用' : '启用',
      type: 'link',
      danger: row.status === 1,
      popConfirm: {
        title: row.status === 1 ? '停用后组卷将不可用，确认？' : '确认启用该试题？',
        confirm: () => handleToggleStatus(row),
      },
    },
    {
      label: '删除',
      type: 'link',
      danger: true,
      icon: ACTION_ICON.DELETE,
      popConfirm: {
        title: '被试卷引用时无法删除，确认删除？',
        confirm: () => handleDelete(row),
      },
    },
  ];
}

loadTags();
</script>

<template>
  <Page auto-content-height>
    <QuestionFormModal @success="handleSearch" />

    <!-- 隐藏的导入 input -->
    <input
      id="question-import-file"
      class="hidden"
      type="file"
      accept=".xlsx"
      @change="handleImportFile"
    />

    <!-- 标签筛选 -->
    <div class="mb-3 flex flex-wrap items-center gap-3">
      <Select
        v-model:value="tagId"
        class="!w-56"
        :allow-clear="true"
        placeholder="全部标签"
        :options="tagOptions"
        @change="handleSearch"
      />
    </div>

    <Grid table-title="考试题库（单选/多选/判断/实操问答/案例分析）">
      <template #toolbar-tools>
        <Button class="mr-2" @click="handleDownloadTemplate">
          下载导入模板
        </Button>
        <Button class="mr-2" @click="triggerImport">Excel 导入</Button>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['试题']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              onClick: handleCreate,
            },
          ]"
        />
      </template>

      <template #qtype="{ row }">
        <Tag :color="QUESTION_TYPE_COLOR[row.qtype] || 'default'">
          {{ QUESTION_TYPE_TEXT[row.qtype] }}
        </Tag>
      </template>
      <template #difficulty="{ row }">
        <Tag
          :color="
            row.difficulty === 1
              ? 'success'
              : row.difficulty === 3
                ? 'error'
                : 'warning'
          "
        >
          {{ difficultyText(row.difficulty) }}
        </Tag>
      </template>
      <template #tags="{ row }">
        <template v-if="row.tagNames">
          <Tag
            v-for="name in row.tagNames.split(',')"
            :key="name"
            color="cyan"
          >
            {{ name }}
          </Tag>
        </template>
        <span v-else>-</span>
      </template>
      <template #status="{ row }">
        <Tag :color="row.status === 1 ? 'success' : 'default'">
          {{ row.status === 1 ? '启用' : '停用' }}
        </Tag>
      </template>
      <template #actions="{ row }">
        <TableAction :actions="buildActions(row)" />
      </template>
    </Grid>

    <!-- 试题详情弹窗 -->
    <Modal
      v-model:open="detailVisible"
      :title="`试题详情 - #${detail?.id || ''}`"
      :width="760"
      :footer="null"
      destroy-on-close
    >
      <div v-if="detail" class="flex flex-col gap-3">
        <div>
          <Tag :color="QUESTION_TYPE_COLOR[detail.qtype] || 'default'">
            {{ QUESTION_TYPE_TEXT[detail.qtype] }}
          </Tag>
          <Tag
            :color="
              detail.difficulty === 1
                ? 'success'
                : detail.difficulty === 3
                  ? 'error'
                  : 'warning'
            "
          >
            {{ difficultyText(detail.difficulty) }}
          </Tag>
        </div>
        <div class="text-base">{{ detail.stem }}</div>
        <div v-if="detailOptions.length" class="flex flex-col gap-1">
          <div
            v-for="opt in detailOptions"
            :key="opt.key"
            class="rounded border border-gray-100 px-2 py-1"
          >
            <Tag :color="opt.isCorrect ? 'green' : 'default'">
              {{ opt.key }}
            </Tag>
            {{ opt.text }}
            <span v-if="opt.isCorrect" class="text-xs text-green-600">
              ✓ 正确
            </span>
          </div>
        </div>
        <div>
          <span class="text-gray-500">答案：</span>
          <span class="text-blue-600">{{ detail.answer }}</span>
        </div>
        <div v-if="detail.analysis">
          <span class="text-gray-500">解析：</span>{{ detail.analysis }}
        </div>
        <div v-if="detail.tagNames">
          <span class="text-gray-500">标签：</span>
          <Tag
            v-for="name in detail.tagNames.split(',')"
            :key="name"
            color="cyan"
          >
            {{ name }}
          </Tag>
        </div>
      </div>
    </Modal>

    <!-- 导入失败明细弹窗 -->
    <Modal
      v-model:open="importResultVisible"
      title="导入失败明细"
      :width="560"
      :footer="null"
    >
      <div v-if="importErrors.length" class="max-h-[50vh] overflow-y-auto">
        <div
          v-for="item in importErrors"
          :key="item.rowNo"
          class="border-b border-gray-100 py-1 text-sm"
        >
          <Tag color="red">第 {{ item.rowNo }} 行</Tag>
          {{ item.error }}
        </div>
      </div>
    </Modal>
  </Page>
</template>
