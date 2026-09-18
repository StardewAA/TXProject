<!-- 质保即将到期（待办来源：售后质保）—— 实时查询，不预生成待办记录 -->
<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CrmAfterSaleWarrantyApi } from '#/api/crm/after-sale/warranty';

import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Button } from 'ant-design-vue';

import { TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  AFTER_SALE_WARRANTY_EXPIRY_TYPE_ABOUT_TO_EXPIRE,
  getAfterSaleWarrantyPage,
} from '#/api/crm/after-sale/warranty';
import { getSimpleUserList } from '#/api/system/user';

const { push } = useRouter();

/** 售后责任人昵称映射：列表展示待办归属人（后端质保档案接口不返回昵称） */
const serviceUserNameMap = ref(new Map<number, string>());

onMounted(async () => {
  try {
    const users = await getSimpleUserList();
    serviceUserNameMap.value = new Map(
      (users ?? []).map((user: { id: number; nickname: string }) => [
        user.id,
        user.nickname,
      ]),
    );
  } catch {
    // 取不到昵称时回退展示用户编号
  }
});

/** 待办归属人：质保档案的售后责任人 */
function getServiceUserName(row: CrmAfterSaleWarrantyApi.Warranty) {
  if (!row.serviceUserId) {
    return '未设置';
  }
  return serviceUserNameMap.value.get(row.serviceUserId) ?? String(row.serviceUserId);
}

/** 打开质保档案详情（携带质保档案 ID，跳转售后模块的质保档案详情页） */
function handleWarrantyDetail(row: CrmAfterSaleWarrantyApi.Warranty) {
  push({ name: 'CrmAfterSaleWarrantyDetail', params: { id: row.id } });
}

const columns: VxeTableGridOptions<CrmAfterSaleWarrantyApi.Warranty>['columns'] = [
  {
    field: 'todoContent',
    title: '待办事项',
    minWidth: 380,
    fixed: 'left',
    slots: { default: 'todoContent' },
  },
  {
    field: 'no',
    title: '质保编号',
    minWidth: 170,
    slots: { default: 'no' },
  },
  {
    field: 'projectName',
    title: '工程项目名称',
    minWidth: 200,
  },
  {
    field: 'customerName',
    title: '客户名称',
    minWidth: 160,
  },
  {
    field: 'warrantyEndDate',
    title: '质保到期日期',
    minWidth: 140,
  },
  {
    field: 'remainDays',
    title: '剩余天数',
    minWidth: 110,
    slots: { default: 'remainDays' },
  },
  {
    field: 'serviceUserName',
    title: '归属人（售后责任人）',
    minWidth: 160,
    slots: { default: 'serviceUserName' },
  },
  {
    title: '操作',
    width: 120,
    fixed: 'right',
    slots: { default: 'actions' },
  },
];

const [Grid] = useVbenVxeGrid({
  gridOptions: {
    columns,
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }) => {
          // 实时查询：仅「质保中 + 本人及下属负责 + 到期日在提前提醒天数内」，
          // 提前提醒天数由后端读取售后设置，改动后刷新即生效
          return await getAfterSaleWarrantyPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            expiryType: AFTER_SALE_WARRANTY_EXPIRY_TYPE_ABOUT_TO_EXPIRE,
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
      search: false,
    },
  } as VxeTableGridOptions<CrmAfterSaleWarrantyApi.Warranty>,
});
</script>

<template>
  <Grid>
    <template #todoContent="{ row }">
      <span class="text-gray-700 dark:text-gray-200">
        【质保即将到期】客户 {{ row.customerName ?? '-' }}，质保档案将于
        {{ row.warrantyEndDate ?? '-' }} 到期
      </span>
    </template>
    <template #no="{ row }">
      <Button type="link" @click="handleWarrantyDetail(row)">
        {{ row.no }}
      </Button>
    </template>
    <template #remainDays="{ row }">
      <span :class="row.remainDays <= 7 ? 'font-semibold text-red-500' : ''">
        {{ row.remainDays ?? '-' }}
      </span>
    </template>
    <template #serviceUserName="{ row }">
      {{ getServiceUserName(row) }}
    </template>
    <template #actions="{ row }">
      <TableAction
        :actions="[
          {
            label: '查看档案',
            type: 'link',
            auth: ['crm:after-sale-warranty:query'],
            onClick: handleWarrantyDetail.bind(null, row),
          },
        ]"
      />
    </template>
  </Grid>
</template>
