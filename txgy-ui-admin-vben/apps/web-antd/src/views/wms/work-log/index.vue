<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { InfraWorkLogApi } from '#/api/infra/work-log';

import { onMounted, ref, toRaw } from 'vue';

import { Page, useVbenDrawer } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import { Alert, Button, message, RadioButton, RadioGroup, Spin, Tag } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { exportWorkLog, getWorkLogPage, getWorkLogScope } from '#/api/infra/work-log';

import { ACTION_LABELS, useGridColumns, useGridFormSchema } from './data';
import { buildWorkLogQuery, getWarehouseArea, humanizeBusinessKey, humanizeWorkSummary } from './helpers';
import Detail from './modules/detail.vue';

defineOptions({ name: 'WmsWorkLog' });

const currentScope = ref<InfraWorkLogApi.Scope>('SELF');
const canViewAll = ref(false);
const scopeLoading = ref(true);
const scopeError = ref('');
const listError = ref('');
const exportLoading = ref(false);

const [DetailDrawer, detailDrawerApi] = useVbenDrawer({
  connectedComponent: Detail,
  destroyOnClose: true,
});

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(false),
  },
  gridOptions: {
    columns: useGridColumns(),
    emptyText: '暂无符合条件的工作留痕',
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          listError.value = '';
          try {
            return await getWorkLogPage(
              buildWorkLogQuery(currentScope.value, {
                pageNo: page.currentPage,
                pageSize: page.pageSize,
                ...formValues,
              }),
            );
          } catch {
            listError.value = '工作留痕加载失败，请稍后重试。';
            return { list: [], total: 0 };
          }
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
  } as VxeTableGridOptions<InfraWorkLogApi.WorkLog>,
});

/** 获取服务端判定的可查询范围；失败时安全保留个人视图。 */
async function loadScope() {
  scopeLoading.value = true;
  scopeError.value = '';
  try {
    const scope = await getWorkLogScope();
    canViewAll.value = scope.canViewAll === true;
    if (!canViewAll.value && currentScope.value === 'ALL') {
      currentScope.value = 'SELF';
      await handleScopeChange();
    }
  } catch {
    canViewAll.value = false;
    scopeError.value = '全体成员查询范围暂时无法确认，当前仅显示我的留痕。';
  } finally {
    scopeLoading.value = false;
  }
}

/** 切换范围时清除人员条件，并从第一页重新查询。 */
async function handleScopeChange() {
  if (currentScope.value === 'SELF') {
    await gridApi.formApi.setFieldValue('userId', undefined);
  }
  gridApi.formApi.setState({
    schema: useGridFormSchema(currentScope.value === 'ALL'),
  });
  const formValues = await gridApi.formApi.getValues();
  gridApi.formApi.setLatestSubmissionValues(toRaw(formValues));
  await gridApi.reload(formValues);
}

/** 重新加载列表。 */
function handleRetry() {
  gridApi.query();
}

/** 查看单条留痕，详情接口继续携带当前范围做服务端鉴权。 */
function handleDetail(row: InfraWorkLogApi.WorkLog) {
  detailDrawerApi.setData({ id: row.id, scope: currentScope.value }).open();
}

/** 导出当前范围和当前筛选条件。 */
async function handleExport() {
  exportLoading.value = true;
  const hideLoading = message.loading({
    content: '正在生成工作留痕文件…',
    duration: 0,
  });
  try {
    const formValues = await gridApi.formApi.getValues();
    const data = await exportWorkLog(buildWorkLogQuery(currentScope.value, formValues));
    downloadFileFromBlobPart({
      fileName: `工作留痕-${currentScope.value === 'SELF' ? '我的记录' : '全体成员'}.xlsx`,
      source: data,
    });
    message.success('工作留痕已导出');
  } catch {
    // requestClient 已显示服务端的具体原因（例如超过单次导出上限）。
  } finally {
    hideLoading();
    exportLoading.value = false;
  }
}

onMounted(loadScope);
</script>

<template>
  <Page auto-content-height>
    <DetailDrawer />

    <Alert v-if="scopeError" class="mb-3" :message="scopeError" type="warning" show-icon />
    <Alert v-if="listError" class="mb-3" :message="listError" type="error" show-icon>
      <template #action>
        <Button size="small" @click="handleRetry">重新加载</Button>
      </template>
    </Alert>

    <Grid table-title="工作留痕">
      <template #toolbar-tools>
        <div class="flex items-center gap-3">
          <Spin :spinning="scopeLoading" size="small">
            <RadioGroup
              v-if="canViewAll"
              v-model:value="currentScope"
              button-style="solid"
              size="small"
              @change="handleScopeChange"
            >
              <RadioButton value="SELF">我的留痕</RadioButton>
              <RadioButton value="ALL">全体成员</RadioButton>
            </RadioGroup>
            <Tag v-else color="blue">我的留痕</Tag>
          </Spin>
          <TableAction
            :actions="[
              {
                label: '导出当前结果',
                type: 'primary',
                icon: ACTION_ICON.DOWNLOAD,
                loading: exportLoading,
                onClick: handleExport,
              },
            ]"
          />
        </div>
      </template>

      <template #userName="{ row }">
        <div class="py-1">
          <div>{{ row.userName || `用户 ${row.userId}` }}</div>
          <div v-if="row.userName" class="text-xs text-muted-foreground">编号 {{ row.userId }}</div>
        </div>
      </template>
      <template #module="{ row }">
        {{ getWarehouseArea(row.requestUrl) }}
      </template>
      <template #action="{ row }">
        <Tag>{{ ACTION_LABELS[row.action] || row.action || '-' }}</Tag>
      </template>
      <template #summary="{ row }">
        <div class="py-1 leading-5">
          <div>{{ humanizeWorkSummary(row.action, row.summary, row.requestUrl) }}</div>
          <div v-if="humanizeBusinessKey(row.businessKey)" class="mt-1 break-all text-xs text-muted-foreground">
            相关资料：{{ humanizeBusinessKey(row.businessKey) }}
          </div>
        </div>
      </template>
      <template #result="{ row }">
        <Tag :color="row.resultCode === 0 ? 'success' : 'error'">
          {{ row.resultCode === 0 ? '成功' : '失败' }}
        </Tag>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '详情',
              type: 'link',
              icon: ACTION_ICON.VIEW,
              onClick: handleDetail.bind(null, row),
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
