<script lang="ts" setup>
import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Form, FormItem, Input, InputNumber, message, Modal, Tag } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';

import { getRecordPage, retryRecord, reviewRecord } from '../api';
import { VR_ASSESS_METHOD_TEXT, VR_PRACTICE_SOURCE_OPTIONS } from '../constants';

defineOptions({ name: 'TrainingVrAssess' });

const [AssessGrid, assessGridApi] = useVbenVxeGrid({
  formOptions: {
    schema: [
      {
        component: 'Input',
        componentProps: { allowClear: true, placeholder: '学员 / 记录编号 / 项目' },
        fieldName: 'keyword',
        label: '关键字',
      },
      {
        component: 'Select',
        componentProps: { allowClear: true, options: VR_PRACTICE_SOURCE_OPTIONS },
        fieldName: 'source',
        label: '记录类型',
      },
      {
        component: 'Select',
        componentProps: {
          allowClear: true,
          options: [
            { label: '已合格', value: true },
            { label: '未合格', value: false },
          ],
        },
        fieldName: 'qualified',
        label: '合格状态',
      },
    ] satisfies VbenFormSchema[],
  },
  gridOptions: {
    columns: [
      { field: 'recordNo', title: '记录编号', width: 165, showOverflow: true },
      { field: 'studentName', title: '学员', width: 90 },
      { field: 'projectName', title: 'VR 实操项目', minWidth: 170, showOverflow: true },
      { field: 'source', title: '记录类型', width: 90, slots: { default: 'source' } },
      { field: 'method', title: '判定方式', width: 90, slots: { default: 'method' } },
      { field: 'score', title: '当前得分', width: 90, align: 'center', slots: { default: 'score' } },
      { field: 'qualified', title: '是否合格', width: 90, slots: { default: 'qualified' } },
      { field: 'gainedHours', title: '获得学时', width: 85, align: 'center' },
      { field: 'finishedAt', title: '完成时间', width: 155 },
      { field: 'reviewer', title: '复核人', width: 85 },
      { field: 'reviewTime', title: '复核时间', width: 155 },
      { title: '操作', width: 210, fixed: 'right', slots: { default: 'actions' } },
    ] as VxeTableGridOptions['columns'],
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) =>
          await getRecordPage({ pageNo: page.currentPage, pageSize: page.pageSize, ...formValues }),
      },
    },
    rowConfig: { keyField: 'id', isHover: true },
    toolbarConfig: { refresh: true, search: true },
  },
});

const reviewOpen = ref(false);
const reviewSaving = ref(false);
const reviewRecordRow = ref<any>();
const reviewForm = reactive<Record<string, any>>({});

function openReview(row: any) {
  reviewRecordRow.value = row;
  Object.keys(reviewForm).forEach((key) => delete reviewForm[key]);
  reviewForm.score = row.score;
  reviewForm.remark = '';
  reviewOpen.value = true;
}

async function handleReviewSave() {
  if (reviewForm.score == null) {
    message.warning('请填写复核后得分');
    return;
  }
  if (reviewForm.score > 100 || reviewForm.score < 0) {
    message.warning('得分应在 0-100 之间');
    return;
  }
  reviewSaving.value = true;
  try {
    await reviewRecord(reviewRecordRow.value.id, {
      score: reviewForm.score,
      remark: reviewForm.remark || '人工复核通过',
    });
    message.success('复核完成，已记录复核人、复核时间与修改前后分数');
    reviewOpen.value = false;
    assessGridApi.query();
  } finally {
    reviewSaving.value = false;
  }
}

function handleRetry(row: any) {
  retryRecord(row.id);
  message.success('学员已重练重考，成绩已刷新并标记合格');
  assessGridApi.query();
}

function buildActions(row: any) {
  const actions: any[] = [];
  if (row.method === 1) {
    actions.push({
      label: '人工复核',
      type: 'link',
      icon: ACTION_ICON.EDIT,
      onClick: () => openReview(row),
    });
  }
  if (!row.qualified && row.retryable) {
    actions.push({
      label: '重练重考',
      type: 'link',
      danger: true,
      icon: ACTION_ICON.VIEW,
      popConfirm: {
        title: '确认该学员已完成重练重考？将刷新考核记录并标记合格',
        confirm: () => handleRetry(row),
      },
    });
  }
  return actions;
}
</script>

<template>
  <Page auto-content-height>
    <AssessGrid table-title="VR 实操考核（74 · 自动评分 / 人工复核 / 不合格重练重考）">
      <template #source="{ row }">
        <Tag :color="row.source === 1 ? 'geekblue' : 'purple'">
          {{ row.source === 1 ? '训练练习' : '正式考核' }}
        </Tag>
      </template>
      <template #method="{ row }">
        <Tag :color="row.method === 2 ? 'orange' : 'default'">{{ VR_ASSESS_METHOD_TEXT[row.method] || '-' }}</Tag>
      </template>
      <template #score="{ row }">
        <span :class="row.qualified ? 'text-green-600 font-medium' : 'text-red-500 font-medium'">
          {{ row.score }}
        </span>
        <span v-if="row.beforeScore != null && row.beforeScore !== row.score" class="ml-1 text-xs text-gray-400 line-through">
          {{ row.beforeScore }}
        </span>
      </template>
      <template #qualified="{ row }">
        <Tag :color="row.qualified ? 'success' : 'error'">{{ row.qualified ? '合格' : '未合格' }}</Tag>
      </template>
      <template #actions="{ row }">
        <TableAction :actions="buildActions(row)" />
      </template>
    </AssessGrid>

    <!-- 人工复核弹窗 -->
    <Modal v-model:open="reviewOpen" title="人工复核 VR 实操成绩" :confirm-loading="reviewSaving" width="560px" @ok="handleReviewSave">
      <Form layout="vertical" class="pt-2">
        <div class="mb-3 rounded bg-gray-50 px-3 py-2 text-sm">
          <div class="text-gray-500">
            {{ reviewRecordRow?.studentName }} · {{ reviewRecordRow?.projectName }}
            <span class="ml-2 text-xs text-gray-400">({{ reviewRecordRow?.recordNo }})</span>
          </div>
        </div>
        <div class="grid grid-cols-2 gap-x-4">
          <FormItem label="自动评分结果（复核前）">
            <InputNumber :value="reviewRecordRow?.score" disabled class="!w-full" />
          </FormItem>
          <FormItem label="人工复核得分（复核后）" required>
            <InputNumber v-model:value="reviewForm.score" :min="0" :max="100" class="!w-full" />
          </FormItem>
        </div>
        <FormItem label="复核意见">
          <Input.TextArea v-model:value="reviewForm.remark" :rows="3" placeholder="如：安全阀操作顺序存疑，扣除对应动作分" />
        </FormItem>
        <div class="text-xs text-gray-400">
          复核提交后系统将记录复核人（当前用户）、复核时间及修改前后的分数，形成完整留痕。
        </div>
      </Form>
    </Modal>
  </Page>
</template>
