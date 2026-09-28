<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { HrmRecruitPostApi } from '#/api/hrm/recruit/post';
import type { HrmRecruitPostStatusValue } from '#/views/hrm/utils/constants';

import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { DocAlert, Page, useVbenModal } from '@vben/common-ui';
import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';

import { Button, message, Tabs } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  getRecruitPostPage,
  getRecruitPostStatusCount,
  updateRecruitPostStatus,
} from '#/api/hrm/recruit/post';
import { $t } from '#/locales';
import { HrmRecruitPostStatus } from '#/views/hrm/utils/constants';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';
import StopForm from './modules/stop-form.vue';

defineOptions({ name: 'HrmRecruitPost' });

const { push } = useRouter();

const activeStatus = ref(String(HrmRecruitPostStatus.PUBLISHED));
const statusCounts = ref<HrmRecruitPostApi.StatusCount[]>([]);

const statusTabOptions = computed(() => {
  const countMap: Record<number, number> = {};
  for (const item of statusCounts.value) {
    countMap[item.status] = item.count;
  }
  return getDictOptions(DICT_TYPE.HRM_RECRUIT_POST_STATUS, 'number').map(
    (item) => ({
      label: item.label,
      value: String(item.value),
      count: countMap[Number(item.value)] ?? 0,
    }),
  );
});

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});
const [StopModal, stopModalApi] = useVbenModal({
  connectedComponent: StopForm,
  destroyOnClose: true,
});

/** 刷新列表和状态统计 */
async function handleRefresh() {
  await Promise.all([gridApi.query(), getStatusCounts()]);
}

/** 查询状态统计（不带 status） */
async function getStatusCounts() {
  const formValues = await gridApi.formApi.getValues();
  statusCounts.value = await getRecruitPostStatusCount({
    ...formValues,
  });
}

/** 切换状态页签 */
function handleStatusTabChange(key: number | string) {
  activeStatus.value = String(key);
  handleRefresh();
}

/** 新增 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 新建/编辑完成：保存为草稿时自动切到「草稿」页签，便于继续编辑或发布 */
async function handleFormSuccess(status?: number) {
  if (status === HrmRecruitPostStatus.DRAFT) {
    activeStatus.value = String(HrmRecruitPostStatus.DRAFT);
  }
  await handleRefresh();
}

/** 编辑 */
function handleEdit(row: HrmRecruitPostApi.RecruitPost) {
  formModalApi.setData(row).open();
}

/** 详情 */
function handleDetail(id?: number) {
  if (!id) {
    return;
  }
  push({ name: 'HrmRecruitPostDetail', params: { id } });
}

/** 是否已发布 */
function isPublished(row: HrmRecruitPostApi.RecruitPost) {
  return row.status === HrmRecruitPostStatus.PUBLISHED;
}

/** 是否已暂停 */
function isPaused(row: HrmRecruitPostApi.RecruitPost) {
  return row.status === HrmRecruitPostStatus.PAUSED;
}

/** 是否草稿（Q-003 / I-007：草稿不出现在门户，发布后才对外可见） */
function isDraft(row: HrmRecruitPostApi.RecruitPost) {
  return row.status === HrmRecruitPostStatus.DRAFT;
}

/** 打开需要填写原因的状态变更表单 */
function openStatusForm(
  row: HrmRecruitPostApi.RecruitPost,
  status: HrmRecruitPostStatusValue,
) {
  if (!row.id) {
    return;
  }
  stopModalApi.setData({ id: row.id, status }).open();
}

/** 重新发布职位 */
async function handlePublish(row: HrmRecruitPostApi.RecruitPost) {
  if (!row.id) {
    return;
  }
  await updateRecruitPostStatus({
    id: row.id,
    status: HrmRecruitPostStatus.PUBLISHED,
  });
  message.success($t('ui.actionMessage.operationSuccess'));
  await handleRefresh();
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
          return await getRecruitPostPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            status: Number(activeStatus.value) as HrmRecruitPostStatusValue,
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
  } as VxeTableGridOptions<HrmRecruitPostApi.RecruitPost>,
});

/** 初始化 */
onMounted(async () => {
  await getStatusCounts();
});
</script>

<template>
  <Page auto-content-height>
    <template #doc>
      <DocAlert
        title="【招聘】招聘管理"
        url="https://doc.iocoder.cn/hrm/recruit/"
      />
    </template>
    <FormModal @success="handleFormSuccess" />
    <StopModal @success="handleRefresh" />

    <Grid class="recruit-post-grid">
      <template #toolbar-actions>
        <div class="recruit-post-status-tabs">
          <Tabs
            :active-key="activeStatus"
            class="w-full"
            @change="handleStatusTabChange"
          >
            <Tabs.TabPane
              v-for="item in statusTabOptions"
              :key="item.value"
              :tab="`${item.label}（${item.count}）`"
            />
          </Tabs>
        </div>
      </template>
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '新增',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['hrm:recruit:post:create'],
              onClick: handleCreate,
            },
          ]"
        />
      </template>
      <template #postName="{ row }">
        <Button type="link" @click="handleDetail(row.id)">
          {{ row.postName }}
        </Button>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['hrm:recruit:post:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: '暂停',
              type: 'link',
              auth: ['hrm:recruit:post:update'],
              ifShow: isPublished(row),
              onClick: openStatusForm.bind(
                null,
                row,
                HrmRecruitPostStatus.PAUSED,
              ),
            },
            {
              label: '发布',
              type: 'link',
              auth: ['hrm:recruit:post:update'],
              ifShow: isPaused(row) || isDraft(row),
              onClick: handlePublish.bind(null, row),
            },
            {
              label: '关闭',
              type: 'link',
              danger: true,
              auth: ['hrm:recruit:post:update'],
              ifShow: isPublished(row) || isPaused(row) || isDraft(row),
              onClick: openStatusForm.bind(
                null,
                row,
                HrmRecruitPostStatus.CLOSED,
              ),
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>

<style scoped>
.recruit-post-grid :deep(.vxe-toolbar) {
  flex-wrap: wrap;
  row-gap: 0;
  align-items: center;
}

.recruit-post-grid :deep(.vxe-buttons--wrapper) {
  flex: 1 0 100%;
  width: 100%;
  min-width: 100%;
  max-width: 100%;
  overflow: hidden;
}

.recruit-post-grid :deep(.vxe-tools--wrapper),
.recruit-post-grid :deep(.vxe-tools--operate) {
  flex: 1 1 auto;
  flex-wrap: nowrap;
  justify-content: flex-end;
  margin-left: auto;
}

.recruit-post-status-tabs {
  width: 100%;
  min-width: 0;
  overflow: hidden;
}

.recruit-post-status-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 0;
}

.recruit-post-status-tabs :deep(.ant-tabs-nav::before) {
  border-bottom: none;
}
</style>
