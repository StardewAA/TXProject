<script lang="ts" setup>
import type { TrainingCooperationApi } from '../api';

import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { ref } from 'vue';

import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import { Button, Descriptions, DescriptionsItem, message, Modal, Tag } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';

import { getArchiveDetail, getArchivePage } from '../api';
import { SKILL_LEVEL_OPTIONS, STUDENT_STAGE_OPTIONS } from '../constants';

defineOptions({ name: 'TrainingCoopArchive' });

const [Grid] = useVbenVxeGrid({
  formOptions: {
    schema: [
      {
        component: 'Input',
        componentProps: { allowClear: true, placeholder: '请输入学员姓名' },
        fieldName: 'studentName',
        label: '学员姓名',
      },
      {
        component: 'Input',
        componentProps: { allowClear: true, placeholder: '请输入院校名称' },
        fieldName: 'schoolName',
        label: '院校名称',
      },
      {
        component: 'Select',
        componentProps: { allowClear: true, options: STUDENT_STAGE_OPTIONS },
        fieldName: 'stage',
        label: '学员阶段',
      },
    ] satisfies VbenFormSchema[],
  },
  gridOptions: {
    columns: [
      { field: 'archiveNo', title: '档案编号', width: 130 },
      { field: 'studentNo', title: '学号', width: 120 },
      { field: 'studentName', title: '姓名', width: 90 },
      { field: 'schoolName', title: '院校', minWidth: 160, showOverflow: true },
      { field: 'major', title: '专业', minWidth: 140, showOverflow: true },
      { field: 'stage', title: '阶段', width: 100, slots: { default: 'stage' } },
      { field: 'courseCount', title: '课程', width: 70, align: 'center' },
      { field: 'trainedHours', title: '学时', width: 80, align: 'center' },
      { field: 'vrQualified', title: 'VR实操达标', width: 100, slots: { default: 'vr' } },
      { field: 'mobileMasked', title: '联系电话(脱敏)', width: 140 },
      { title: '操作', width: 100, fixed: 'right', slots: { default: 'actions' } },
    ] as VxeTableGridOptions['columns'],
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) =>
          await getArchivePage({
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

// ========== 档案详情（脱敏展示） ==========
const detailOpen = ref(false);
const detail = ref<TrainingCooperationApi.Archive>();

async function openDetail(row: TrainingCooperationApi.Archive) {
  detail.value = await getArchiveDetail(row.id!);
  detailOpen.value = true;
}

function handleExport() {
  message.info('演示环境：导出 Excel 报表待后端接口接入后开放');
}
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="校企学员档案互通（81 校企合作管理 · 档案归档与查询，敏感字段脱敏）">
      <template #toolbar-tools>
        <Button type="primary" @click="handleExport">
          <template #icon><IconifyIcon icon="lucide:download" /></template>
          导出档案
        </Button>
      </template>
      <template #stage="{ row }">
        <Tag>
          {{ STUDENT_STAGE_OPTIONS.find((item) => item.value === row.stage)?.label || '-' }}
        </Tag>
      </template>
      <template #vr="{ row }">
        <Tag :color="row.vrQualified ? 'success' : 'error'">
          {{ row.vrQualified ? '合格' : '未达标' }}
        </Tag>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '查看',
              type: 'link',
              icon: ACTION_ICON.VIEW,
              onClick: () => openDetail(row),
            },
          ]"
        />
      </template>
    </Grid>

    <Modal v-model:open="detailOpen" title="学员培训档案详情" :footer="null" width="720px">
      <Descriptions v-if="detail" bordered :column="2" size="small" class="pt-2">
        <DescriptionsItem label="档案编号">{{ detail.archiveNo || '-' }}</DescriptionsItem>
        <DescriptionsItem label="学号">{{ detail.studentNo || '-' }}</DescriptionsItem>
        <DescriptionsItem label="姓名">{{ detail.studentName || '-' }}</DescriptionsItem>
        <DescriptionsItem label="性别">{{ detail.gender || '-' }}</DescriptionsItem>
        <DescriptionsItem label="院校">{{ detail.schoolName || '-' }}</DescriptionsItem>
        <DescriptionsItem label="专业">{{ detail.major || '-' }}</DescriptionsItem>
        <DescriptionsItem label="学员阶段">
          {{ STUDENT_STAGE_OPTIONS.find((item) => item.value === detail.stage)?.label || '-' }}
        </DescriptionsItem>
        <DescriptionsItem label="技能定级">
          {{ SKILL_LEVEL_OPTIONS.find((item) => item.value === detail.skillLevel)?.label ?? '-' }}
        </DescriptionsItem>
        <DescriptionsItem label="累计学时">{{ detail.trainedHours ?? 0 }} 小时</DescriptionsItem>
        <DescriptionsItem label="完成课程">{{ detail.courseCount ?? 0 }} 门</DescriptionsItem>
        <DescriptionsItem label="VR 实操达标">
          <Tag :color="detail.vrQualified ? 'success' : 'error'">
            {{ detail.vrQualified ? '合格' : '未达标（不可报考联合认证）' }}
          </Tag>
        </DescriptionsItem>
        <DescriptionsItem label="联系电话">{{ detail.mobileMasked || '-' }}</DescriptionsItem>
        <DescriptionsItem label="证书/认证" :span="2">{{ detail.certNames || '暂无' }}</DescriptionsItem>
        <DescriptionsItem label="最近更新" :span="2">{{ detail.updateTime || '-' }}</DescriptionsItem>
      </Descriptions>
    </Modal>
  </Page>
</template>
