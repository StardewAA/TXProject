<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { HrmPerformancePlanApi } from '#/api/hrm/performance/plan';

import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { confirm, DocAlert, Page } from '@vben/common-ui';

import { message, Tabs, Tag } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  archivePerformancePlan,
  deletePerformancePlan,
  getPerformancePlanPage,
  getPerformancePlanStatusCount,
  openPerformancePlanScoring,
  startPerformancePlanInterview,
  terminatePerformancePlan,
} from '#/api/hrm/performance/plan';
import { $t } from '#/locales';

import {
  formatStageCountLabel,
  HrmPerformancePlanStatus,
  useGridColumns,
  useGridFormSchema,
  useStatusTabs,
} from './data';

defineOptions({ name: 'HrmPerformancePlan' });

const router = useRouter();
const statusCount = ref<Record<number, number>>({});
const activeStatus = ref<number>(HrmPerformancePlanStatus.NOT_STARTED);

function getStageCountList(plan: HrmPerformancePlanApi.PerformancePlan) {
  return Object.entries(plan.stageCountMap || {})
    .map(([stageType, count]) => ({ stageType: Number(stageType), count }))
    .filter((item) => item.count > 0)
    .toSorted((left, right) => left.stageType - right.stageType);
}

async function loadStatusCount(formValues?: Record<string, unknown>) {
  statusCount.value = await getPerformancePlanStatusCount({
    pageNo: 1,
    pageSize: 10,
    name: formValues?.name as string,
  });
}

function handleRefresh() {
  gridApi.query();
  loadStatusCount(gridApi.formApi?.getLatestSubmissionValues?.());
}

function openForm(type: string, id?: number) {
  router.push({ name: 'HrmPerformancePlanForm', query: { type, id } });
}

function openDetail(id: number, tab?: string) {
  router.push({
    name: 'HrmPerformancePlanDetail',
    params: { id },
    query: tab ? { tab } : undefined,
  });
}

async function handleAction(
  plan: HrmPerformancePlanApi.PerformancePlan,
  action: 'archive' | 'interview' | 'score' | 'terminate',
) {
  const actionName = {
    score: '开始评分',
    interview: '发起绩效面谈',
    archive: '归档',
    terminate: '终止考核',
  }[action];
  await confirm(`确认${actionName}"${plan.name}"？`);
  if (action === 'score') await openPerformancePlanScoring(plan.id!);
  else if (action === 'interview')
    await startPerformancePlanInterview(plan.id!);
  else if (action === 'archive') await archivePerformancePlan(plan.id!);
  else await terminatePerformancePlan(plan.id!);
  message.success($t('ui.actionMessage.operationSuccess'));
  handleRefresh();
}

async function handleDelete(plan: HrmPerformancePlanApi.PerformancePlan) {
  await confirm(`确认删除绩效计划"${plan.name}"？`);
  await deletePerformancePlan(plan.id!);
  message.success($t('ui.actionMessage.deleteSuccess'));
  handleRefresh();
}

function buildActions(row: HrmPerformancePlanApi.PerformancePlan) {
  const actions: Record<string, unknown>[] = [
    {
      label: '查看考核设置',
      type: 'link',
      onClick: () => openForm('view', row.id),
    },
  ];
  if (row.status === HrmPerformancePlanStatus.NOT_STARTED) {
    actions.push(
      {
        label: '删除考核',
        type: 'link',
        danger: true,
        auth: ['hrm:performance:plan:delete'],
        popConfirm: {
          title: `确认删除"${row.name}"？`,
          confirm: () => handleDelete(row),
        },
      },
      {
        label: '检查并开启考核',
        type: 'link',
        auth: ['hrm:performance:plan:update'],
        onClick: () => openDetail(row.id!, 'employees'),
      },
    );
  }
  if (row.status === HrmPerformancePlanStatus.RUNNING) {
    if (row.scoringReady) {
      actions.push({
        label: '开始评分',
        type: 'link',
        auth: ['hrm:performance:plan:update'],
        onClick: () => handleAction(row, 'score'),
      });
    }
    if (row.interviewReady) {
      actions.push({
        label: '发起绩效面谈',
        type: 'link',
        auth: ['hrm:performance:plan:update'],
        onClick: () => handleAction(row, 'interview'),
      });
    }
    if (row.archiveReady) {
      actions.push({
        label: '归档',
        type: 'link',
        auth: ['hrm:performance:plan:update'],
        onClick: () => handleAction(row, 'archive'),
      });
    }
    actions.push(
      {
        label: '终止考核',
        type: 'link',
        danger: true,
        auth: ['hrm:performance:plan:update'],
        onClick: () => handleAction(row, 'terminate'),
      },
      {
        label: '考核结果',
        type: 'link',
        onClick: () => openDetail(row.id!, 'employees'),
      },
    );
  }
  if (row.status === HrmPerformancePlanStatus.TERMINATED) {
    actions.push({
      label: '考核记录',
      type: 'link',
      onClick: () => openDetail(row.id!, 'employees'),
    });
  }
  if (row.status === HrmPerformancePlanStatus.ARCHIVED) {
    actions.push({
      label: '删除',
      type: 'link',
      danger: true,
      auth: ['hrm:performance:plan:delete'],
      popConfirm: {
        title: `确认删除"${row.name}"？`,
        confirm: () => handleDelete(row),
      },
    });
  }
  return actions;
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
    submitOnChange: false,
  },
  gridOptions: {
    columns: useGridColumns(),
    height: 'auto',
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          await loadStatusCount(formValues);
          return getPerformancePlanPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            status: activeStatus.value,
            ...formValues,
          });
        },
      },
    },
    rowConfig: { keyField: 'id', isHover: true },
  } as VxeTableGridOptions<HrmPerformancePlanApi.PerformancePlan>,
});

function handleStatusChange(key: number | string) {
  activeStatus.value = Number(key);
  gridApi.query();
}

/**
 * 其他有数据的页签（Q-014 / I-014）。
 *
 * 角标数与列表 total 同源（同一个 status-count 接口 + 同一套筛选条件）。
 * 默认落在「未开始」页签时若该状态为空，这里直接列出真正有数据的页签并支持一键切换，
 * 避免"看到角标有数、点进去却是空列表"的困惑。
 */
const otherStatusTabs = computed(() =>
  useStatusTabs(statusCount.value).filter(
    (tab) => tab.value !== activeStatus.value && tab.count > 0,
  ),
);

onMounted(() => loadStatusCount());
</script>

<template>
  <Page auto-content-height>
    <template #doc>
      <DocAlert
        title="【绩效】绩效模板、绩效计划"
        url="https://doc.iocoder.cn/hrm/performance/template-plan/"
      />
    </template>
    <Grid class="performance-plan-grid">
      <template #toolbar-actions>
        <div class="performance-plan-status-tabs">
          <Tabs
            :active-key="activeStatus"
            class="w-full"
            @change="handleStatusChange"
          >
            <Tabs.TabPane
              v-for="tab in useStatusTabs(statusCount)"
              :key="tab.value"
              :tab="`${tab.label}（${tab.count}）`"
            />
          </Tabs>
        </div>
      </template>
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['考核计划']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['hrm:performance:plan:create'],
              onClick: () => openForm('create'),
            },
          ]"
        />
      </template>
      <template #planName="{ row }">
        <a class="text-primary cursor-pointer" @click="openDetail(row.id!)">
          {{ row.name }}
        </a>
      </template>
      <template #emptyText>
        <div class="py-8 text-center text-muted-foreground">
          <div>当前页签暂无绩效计划</div>
          <div v-if="otherStatusTabs.length" class="mt-3">
            其他页签有数据：
            <Tag
              v-for="tab in otherStatusTabs"
              :key="tab.value"
              class="cursor-pointer"
              color="blue"
              @click="handleStatusChange(tab.value)"
            >
              {{ tab.label }}（{{ tab.count }}）
            </Tag>
          </div>
        </div>
      </template>
      <template #stageCount="{ row }">
        <div v-if="getStageCountList(row).length" class="flex flex-wrap gap-1">
          <Tag v-for="item in getStageCountList(row)" :key="item.stageType">
            {{ formatStageCountLabel(item.stageType, item.count) }}
          </Tag>
        </div>
        <span v-else>-</span>
      </template>
      <template #actions="{ row }">
        <TableAction :actions="buildActions(row)" />
      </template>
    </Grid>
  </Page>
</template>

<style scoped>
.performance-plan-grid :deep(.vxe-toolbar) {
  flex-wrap: wrap;
  row-gap: 0;
  align-items: center;
}

.performance-plan-grid :deep(.vxe-buttons--wrapper) {
  flex: 1 0 100%;
  width: 100%;
  min-width: 100%;
  max-width: 100%;
  overflow: hidden;
}

.performance-plan-grid :deep(.vxe-tools--wrapper),
.performance-plan-grid :deep(.vxe-tools--operate) {
  flex: 1 1 auto;
  flex-wrap: nowrap;
  justify-content: flex-end;
  margin-left: auto;
}

.performance-plan-status-tabs {
  width: 100%;
  min-width: 0;
  overflow: hidden;
}

.performance-plan-status-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 0;
}

.performance-plan-status-tabs :deep(.ant-tabs-nav::before) {
  border-bottom: none;
}
</style>
