<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { HrmRecruitPostTypeApi } from '#/api/hrm/recruit/post/type';

import { computed, ref } from 'vue';

import { confirm, Page, useVbenModal } from '@vben/common-ui';
import { DICT_TYPE } from '@vben/constants';
import { getDictLabel } from '@vben/hooks';
import { handleTree } from '@vben/utils';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteRecruitPostType,
  getRecruitPostTypeList,
  updateRecruitPostTypeStatus,
} from '#/api/hrm/recruit/post/type';
import { $t } from '#/locales';

import { STANDARD_ROOT_NAMES, useGridColumns } from './data';
import Form from './modules/form.vue';

defineOptions({ name: 'HrmRecruitPostType' });

const [FormModal, formModalApi] = useVbenModal({ connectedComponent: Form, destroyOnClose: true });
const allTypes = ref<HrmRecruitPostTypeApi.RecruitPostType[]>([]);
const typeStats = computed(() => {
  const roots = allTypes.value.filter((item) => !item.parentId).length;
  const children = allTypes.value.filter((item) => item.parentId).length;
  return { roots, children, total: allTypes.value.length };
});
function handleRefresh() { gridApi.query(); }
function handleCreate() { formModalApi.setData(null).open(); }
function handleEdit(row: HrmRecruitPostTypeApi.RecruitPostType) { formModalApi.setData(row).open(); }
async function handleDelete(row: HrmRecruitPostTypeApi.RecruitPostType) {
  await confirm(`确认删除职位类型“${row.name}”吗？`);
  await deleteRecruitPostType(row.id!);
  message.success($t('ui.actionMessage.operationSuccess'));
  handleRefresh();
}
async function handleStatusChange(status: number, row: HrmRecruitPostTypeApi.RecruitPostType) {
  try { await confirm(`确认要${getDictLabel(DICT_TYPE.COMMON_STATUS, status)}职位类型“${row.name}”吗？`); }
  catch { return false; }
  await updateRecruitPostTypeStatus({ id: row.id!, status });
  message.success($t('ui.actionMessage.operationSuccess'));
  return true;
}

const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions: {
    columns: useGridColumns(handleStatusChange),
    height: 'auto',
    keepSource: true,
    // 该接口返回的是完整数组（非分页结构），必须关掉分页器；
    // 否则 vxe 的 proxy 拿不到 list/total，会判定为 0 条并渲染空表格。
    pagerConfig: {
      enabled: false,
    },
    treeConfig: {
      expandAll: true,
      parentField: 'parentId',
      reserve: true,
      rowField: 'id',
      transform: true,
    },
    proxyConfig: {
      ajax: {
        query: async () => {
          // 与「组织管理」页一致：先把扁平列表构造成树再交给表格。
          const source = await getRecruitPostTypeList();
          const sourceById = new Map(source.map((item) => [item.id, item]));
          const standardRootIds = new Set(source.filter((item) => !item.parentId && STANDARD_ROOT_NAMES.has(item.name)).map((item) => item.id));
          const isStandardNode = (item: HrmRecruitPostTypeApi.RecruitPostType) => {
            let current = item;
            while (current.parentId) {
              const parent = sourceById.get(current.parentId);
              if (!parent) return false;
              current = parent;
            }
            return standardRootIds.has(current.id);
          };
          const list = source.filter(isStandardNode);
          allTypes.value = list;
          const labels = new Map(list.map((item) => [item.id, item.name]));
          const enriched = list.map((item) => ({
            ...item,
            levelLabel: item.parentId ? '二级 / 职位' : '一级分类',
            parentLabel: item.parentId ? labels.get(item.parentId) || '未找到父级' : '标准目录根节点',
          }));
          return handleTree(enriched);
        },
      },
    },
    rowConfig: { keyField: 'id', isHover: true },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions<HrmRecruitPostTypeApi.RecruitPostType>,
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <section class="post-type-intro" aria-labelledby="post-type-title">
      <div>
        <h1 id="post-type-title">职位类型</h1>
      </div>
      <div class="post-type-stats" aria-label="职位类型统计">
        <div><strong>{{ typeStats.roots }}</strong><span>一级分类</span></div>
        <div><strong>{{ typeStats.children }}</strong><span>二级分类及职位</span></div>
        <div><strong>{{ typeStats.total }}</strong><span>目录节点</span></div>
      </div>
    </section>
    <Grid>
      <template #toolbar-tools>
        <TableAction :actions="[{ label: '新增', type: 'primary', icon: ACTION_ICON.ADD, auth: ['hrm:recruit:post-type:create'], onClick: handleCreate }]" />
      </template>
      <template #actions="{ row }">
        <TableAction :actions="[
          { label: $t('common.edit'), type: 'link', icon: ACTION_ICON.EDIT, auth: ['hrm:recruit:post-type:update'], onClick: handleEdit.bind(null, row) },
          { label: $t('common.delete'), type: 'link', danger: true, icon: ACTION_ICON.DELETE, auth: ['hrm:recruit:post-type:delete'], onClick: handleDelete.bind(null, row) },
        ]" />
      </template>
    </Grid>
  </Page>
</template>

<style scoped>
.post-type-intro {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 32px;
  margin-bottom: 14px;
  padding: 16px 20px;
  border: 1px solid hsl(var(--border));
  border-left: 3px solid hsl(var(--primary));
  border-radius: 6px;
  background: hsl(var(--card));
}
.post-type-intro h1 { margin: 0; color: hsl(var(--foreground)); font-size: 18px; line-height: 1.4; font-weight: 600; }
.post-type-stats { display: flex; gap: 26px; }
.post-type-stats div { min-width: 92px; padding-left: 18px; border-left: 1px solid hsl(var(--border)); }
.post-type-stats strong { display: block; color: hsl(var(--foreground)); font-size: 20px; line-height: 1.2; font-weight: 600; }
.post-type-stats span { color: hsl(var(--muted-foreground)); font-size: 13px; white-space: nowrap; }
@media (max-width: 700px) {
  .post-type-intro { align-items: flex-start; flex-direction: column; gap: 14px; }
  .post-type-stats { width: 100%; justify-content: space-between; gap: 8px; }
  .post-type-stats div { min-width: 0; padding-left: 10px; }
}
</style>
