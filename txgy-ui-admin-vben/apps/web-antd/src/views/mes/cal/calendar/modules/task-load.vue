<script lang="ts" setup>
import type { MesCalTeamApi } from '#/api/mes/cal/team';
import type { MesProTaskApi } from '#/api/mes/pro/task';

import { computed, onMounted, ref } from 'vue';

import { Card, Table } from 'ant-design-vue';

import { getTeamList } from '#/api/mes/cal/team';
import { getTeamTaskLoad } from '#/api/mes/pro/task';

defineOptions({ name: 'MesCalTaskLoad' });

/** 班组列表（用于名称映射） */
const teams = ref<MesCalTeamApi.Team[]>([]);
/** 班组任务负载 */
const loadList = ref<
  {
    teamId: number;
    taskCount: number;
    quantityTotal: number;
    producedTotal: number;
  }[]
>([]);
const loading = ref(false);

const columns = [
  { title: '班组编号', dataIndex: 'teamId', key: 'teamId' },
  { title: '班组名称', dataIndex: 'teamName', key: 'teamName' },
  { title: '任务数', dataIndex: 'taskCount', key: 'taskCount', width: 90 },
  {
    title: '排产数量合计',
    dataIndex: 'quantityTotal',
    key: 'quantityTotal',
    width: 130,
  },
  {
    title: '已生产数量合计',
    dataIndex: 'producedTotal',
    key: 'producedTotal',
    width: 140,
  },
  {
    title: '完成率',
    dataIndex: 'rate',
    key: 'rate',
    width: 90,
  },
];

const tableData = computed(() =>
  loadList.value.map((item) => {
    const team = teams.value.find((t) => t.id === item.teamId);
    const rate =
      item.quantityTotal > 0
        ? Math.round((item.producedTotal / item.quantityTotal) * 100)
        : 0;
    return {
      ...item,
      teamName: team?.name ?? `班组${item.teamId}`,
      rate: `${rate}%`,
    };
  }),
);

async function loadData() {
  loading.value = true;
  try {
    [teams.value, loadList.value] = await Promise.all([
      getTeamList(),
      getTeamTaskLoad(),
    ]);
  } finally {
    loading.value = false;
  }
}

onMounted(loadData);
</script>

<template>
  <Card title="班组任务负载" class="mt-3">
    <template #extra>
      <span class="text-gray-400">统计已指定班组的排产任务（实时）</span>
    </template>
    <Table
      :columns="columns"
      :data-source="tableData"
      :loading="loading"
      :pagination="false"
      row-key="teamId"
      size="small"
    />
  </Card>
</template>
