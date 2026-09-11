<!-- 分配给我的线索 -->
<script lang="ts" setup>
import { useUserStore } from '@vben/stores';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CrmClueApi } from '#/api/crm/clue';

import { useRouter } from 'vue-router';

import { Button } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getCluePage } from '#/api/crm/clue';
import { useGridColumns } from '#/views/crm/clue/data';

import { FOLLOWUP_STATUS, SCENE_TYPES } from '../data';

const { push } = useRouter();

/** 打开线索详情 */
function handleDetail(row: CrmClueApi.Clue) {
  push({ name: 'CrmClueDetail', params: { id: row.id } });
}

const userStore = useUserStore();
const isManager =
  userStore.userRoles?.some(
    (r) => r === 'tenant_admin' || r === 'crm_admin',
  ) ?? false;
const [Grid] = useVbenVxeGrid({
  formOptions: {
    schema: [
      {
        fieldName: 'followUpStatus',
        label: '状态',
        component: 'RadioGroup',
        componentProps: {
          allowClear: true,
          options: FOLLOWUP_STATUS,
        },
        defaultValue: false,
      },
      {
        fieldName: 'sceneType',
        label: '归属',
        component: 'Select',
        componentProps: { allowClear: true, options: SCENE_TYPES },
        defaultValue: isManager ? 3 : 1,
      },
    ],
  },
  gridOptions: {
    columns: useGridColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          return await getCluePage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            sceneType: 1,
            transformStatus: false,
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
  } as VxeTableGridOptions<CrmClueApi.Clue>,
});
</script>

<template>
  <Grid>
    <template #name="{ row }">
      <Button type="link" @click="handleDetail(row)">{{ row.name }}</Button>
    </template>
    <template #actions="{ row }">
      <Button type="link" @click="handleDetail(row)">查看详情</Button>
    </template>
  </Grid>
</template>
