<script lang="ts" setup>
import type { TrainingCooperationApi } from '../api';

import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Form, FormItem, Input, InputNumber, message, Modal, Select, Tag } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';

import { deleteAward, getAwardPage, issueAwardCert, publishAward, saveAwardScore } from '../api';
import { AWARD_LEVEL_OPTIONS, AWARD_LEVEL_TEXT, CERT_STATUS_TEXT } from '../constants';

defineOptions({ name: 'TrainingCoopAward' });

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: (): VbenFormSchema[] =>
      [
        {
          component: 'Input',
          componentProps: { allowClear: true, placeholder: '请输入大赛名称' },
          fieldName: 'contestName',
          label: '大赛名称',
        },
        {
          component: 'Input',
          componentProps: { allowClear: true, placeholder: '请输入学员姓名' },
          fieldName: 'studentName',
          label: '学员姓名',
        },
        {
          component: 'Select',
          componentProps: {
            allowClear: true,
            options: [
              { label: '未公示', value: 0 },
              { label: '已公示', value: 1 },
            ],
          },
          fieldName: 'published',
          label: '公示状态',
        },
      ] satisfies VbenFormSchema[],
  },
  gridOptions: {
    columns: [
      { field: 'contestName', title: '大赛名称', minWidth: 200, showOverflow: true },
      { field: 'workTitle', title: '作品名称', minWidth: 180, showOverflow: true },
      { field: 'studentName', title: '获奖人', width: 100 },
      { field: 'avgScore', title: '均分', width: 80, align: 'center' },
      { field: 'level', title: '获奖等级', width: 100, slots: { default: 'level' } },
      { field: 'judgeDetail', title: '评委留痕', minWidth: 180, showOverflow: true },
      { field: 'published', title: '公示状态', width: 90, slots: { default: 'published' } },
      { field: 'certStatus', title: '电子证书', width: 90, slots: { default: 'cert' } },
      { title: '操作', width: 200, fixed: 'right', slots: { default: 'actions' } },
    ] as VxeTableGridOptions['columns'],
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) =>
          await getAwardPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
          }),
      },
    },
    rowConfig: { keyField: 'id', isHover: true },
    toolbarConfig: { refresh: true, search: true },
  },
});

// ========== 评审打分弹窗（留痕不可删除） ==========
const scoreOpen = ref(false);
const saving = ref(false);
const scoreRow = ref<TrainingCooperationApi.Award>();
const scoreForm = reactive<Record<string, any>>({});

function openScore(row: TrainingCooperationApi.Award) {
  scoreRow.value = row;
  scoreForm.avgScore = row.avgScore ?? 0;
  scoreForm.level = row.level ?? 1;
  scoreForm.judgeDetail = row.judgeDetail ?? '';
  scoreOpen.value = true;
}

async function handleSaveScore() {
  if (scoreForm.avgScore == null) {
    message.warning('请填写均分');
    return;
  }
  saving.value = true;
  try {
    await saveAwardScore(scoreRow.value!.id!, {
      avgScore: scoreForm.avgScore,
      level: scoreForm.level ?? 1,
      judgeDetail: scoreForm.judgeDetail || undefined,
    });
    message.success('评审分数已保存（留痕不可修改）');
    scoreOpen.value = false;
    gridApi.query();
  } finally {
    saving.value = false;
  }
}

// ========== 行操作 ==========
async function handlePublish(row: TrainingCooperationApi.Award) {
  await publishAward(row.id!);
  message.success('获奖名单已公示，进入公示期');
  gridApi.query();
}

async function handleIssueCert(row: TrainingCooperationApi.Award) {
  await issueAwardCert(row.id!);
  message.success('电子证书已发放，获奖人可在学员端查看/下载');
  gridApi.query();
}

async function handleDelete(row: TrainingCooperationApi.Award) {
  const ok = await deleteAward(row.id!);
  if (!ok) {
    message.error('已公示的获奖记录不可删除');
    return;
  }
  message.success('删除成功');
  gridApi.query();
}

function buildActions(row: TrainingCooperationApi.Award) {
  const actions: any[] = [];
  if (row.published === 0) {
    actions.push(
      {
        label: '登记评分',
        type: 'link',
        icon: ACTION_ICON.EDIT,
        onClick: () => openScore(row),
      },
      {
        label: '发布公示',
        type: 'link',
        icon: ACTION_ICON.VIEW,
        popConfirm: { title: '确认公示该获奖记录？公示后不可删除', confirm: () => handlePublish(row) },
      },
      {
        label: '删除',
        type: 'link',
        danger: true,
        icon: ACTION_ICON.DELETE,
        popConfirm: { title: '确认删除该条评审数据？', confirm: () => handleDelete(row) },
      },
    );
  } else if (row.certStatus === 0) {
    actions.push({
      label: '发放证书',
      type: 'link',
      icon: ACTION_ICON.VIEW,
      popConfirm: { title: '确认向获奖人发放电子证书？', confirm: () => handleIssueCert(row) },
    });
  }
  return actions;
}
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="评审管理 · 公示与奖励（82 创新大赛专区 · 评委打分留痕/获奖公示/电子证书）">
      <template #level="{ row }">
        <Tag v-if="row.level" color="gold">{{ AWARD_LEVEL_TEXT[row.level] || '-' }}</Tag>
        <span v-else>-</span>
      </template>
      <template #published="{ row }">
        <Tag :color="row.published === 1 ? 'success' : 'warning'">
          {{ row.published === 1 ? '已公示' : '未公示' }}
        </Tag>
      </template>
      <template #cert="{ row }">
        <Tag :color="row.certStatus === 1 ? 'success' : 'default'">
          {{ CERT_STATUS_TEXT[row.certStatus ?? 0] || '-' }}
        </Tag>
      </template>
      <template #actions="{ row }">
        <TableAction :actions="buildActions(row)" />
      </template>
    </Grid>

    <Modal
      v-model:open="scoreOpen"
      title="登记评审评分（留痕）"
      :confirm-loading="saving"
      @ok="handleSaveScore"
    >
      <Form layout="vertical" class="pt-2">
        <FormItem label="作品">
          <Input :value="scoreRow?.workTitle" disabled />
        </FormItem>
        <FormItem label="平均得分" required>
          <InputNumber
            v-model:value="scoreForm.avgScore"
            :min="0"
            :max="100"
            :step="0.5"
            class="!w-full"
          />
        </FormItem>
        <FormItem label="拟获奖等级">
          <Select v-model:value="scoreForm.level" :options="AWARD_LEVEL_OPTIONS" />
        </FormItem>
        <FormItem label="评审意见（评委留痕）">
          <Input.TextArea
            v-model:value="scoreForm.judgeDetail"
            :rows="3"
            placeholder="如 韩工 91 / 陈教授 90 / 刘工 90.8"
          />
        </FormItem>
      </Form>
    </Modal>
  </Page>
</template>
