<script lang="ts" setup>
import { onMounted, ref } from 'vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getRecruitApplicationPage } from '#/api/hrm/recruit/application';

const props = defineProps<{ candidateId: number }>();
const loading = ref(false);

const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions: {
    columns: [
      { field: 'postName', title: '应聘岗位', minWidth: 180 },
      { field: 'statusName', title: '当前状态', width: 130 },
      { field: 'statusUpdateTime', title: '状态更新时间', width: 180, formatter: 'formatDateTime' },
      { field: 'createTime', title: '投递时间', width: 180, formatter: 'formatDateTime' },
      { field: 'resumeId', title: '简历编号', width: 110 },
      { field: 'eliminateReason', title: '淘汰原因', minWidth: 180 },
      { field: 'withdrawReason', title: '撤回原因', minWidth: 180 },
    ],
    data: [],
    keepSource: true,
    pagerConfig: { enabled: false },
    toolbarConfig: { enabled: false },
  },
});

async function loadApplications() {
  loading.value = true;
  try {
    const result = await getRecruitApplicationPage({
      candidateId: props.candidateId,
      pageNo: 1,
      pageSize: 100,
    });
    await gridApi.grid?.reloadData(result.list || []);
  } finally {
    loading.value = false;
  }
}

onMounted(loadApplications);
</script>

<template>
  <div :class="{ 'pointer-events-none opacity-60': loading }">
    <Grid />
  </div>
</template>
