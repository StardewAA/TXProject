<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Button, Card, message, Progress, Select, Statistic, Table, Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';

import { getDashboardStat, getRecordPage, getStudentStatList } from '../api';
import { VR_ASSESS_METHOD_TEXT, VR_PRACTICE_SOURCE_OPTIONS } from '../constants';

defineOptions({ name: 'TrainingVrDashboard' });

const stat = ref<Awaited<ReturnType<typeof getDashboardStat>>>();
const loading = ref(false);

async function refreshStat() {
  loading.value = true;
  try {
    stat.value = await getDashboardStat();
  } finally {
    loading.value = false;
  }
}

onMounted(refreshStat);

const [StudentGrid] = useVbenVxeGrid({
  gridOptions: {
    columns: [
      { field: 'studentNo', title: '工号/学号', width: 110 },
      { field: 'studentName', title: '学员', width: 110 },
      { field: 'taskTotal', title: '任务数', width: 80, align: 'center' },
      { field: 'finishedCount', title: '已完成', width: 80, align: 'center' },
      { field: 'qualifiedCount', title: '合格次数', width: 90, align: 'center' },
      { field: 'totalHours', title: '累计学时(h)', width: 100, align: 'center' },
      { field: 'rate', title: '完成率', width: 110, align: 'center', slots: { default: 'rate' } },
    ] as VxeTableGridOptions['columns'],
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async () => {
          const rows = await getStudentStatList();
          return { list: rows, total: rows.length };
        },
      },
    },
    rowConfig: { keyField: 'studentName', isHover: true },
    toolbarConfig: { refresh: true },
  },
});

// ==================== 学员个人 VR 实操历史 ====================
const STUDENT_OPTIONS = ['张伟', '刘洋', '陈静', '赵磊', '孙丽'];
const historyStudent = ref('张伟');
const historyRows = ref<any[]>([]);
const historyLoading = ref(false);

async function loadHistory(studentName?: string) {
  const name = studentName ?? historyStudent.value;
  historyLoading.value = true;
  try {
    const result = await getRecordPage({ keyword: name, pageNo: 1, pageSize: 500 });
    historyRows.value = result.list;
  } finally {
    historyLoading.value = false;
  }
}

onMounted(() => {
  loadHistory();
});

function handleExport() {
  const rows = historyRows.value;
  if (!rows.length) {
    message.info('暂无记录可导出');
    return;
  }
  const header = ['记录编号', '学员', '项目', '类型', '判定方式', '得分', '是否合格', '获得学时', '完成时间'];
  const lines = rows.map((row) =>
    [
      row.recordNo,
      row.studentName,
      row.projectName,
      row.source === 1 ? '训练练习' : '正式考核',
      VR_ASSESS_METHOD_TEXT[row.method] ?? '-',
      row.score,
      row.qualified ? '合格' : '未合格',
      row.gainedHours,
      row.finishedAt,
    ].join(','),
  );
  const csv = '\uFEFF' + [header.join(','), ...lines].join('\n');
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `VR实操历史_${historyStudent.value}.csv`;
  link.click();
  URL.revokeObjectURL(url);
  message.success('导出成功');
}
</script>

<template>
  <Page auto-content-height>
    <div class="flex flex-col gap-3 p-1">
      <!-- 统计卡片 -->
      <div class="grid grid-cols-3 gap-3 xl:grid-cols-6" v-if="stat">
        <Card size="small">
          <Statistic title="VR 实操任务" :value="stat.taskTotal" />
        </Card>
        <Card size="small">
          <Statistic title="已完成任务" :value="stat.taskFinished" :value-style="{ color: '#16a34a' }" />
        </Card>
        <Card size="small">
          <Statistic title="覆盖学员数" :value="stat.studentTotal" />
        </Card>
        <Card size="small">
          <Statistic title="实操记录数" :value="stat.recordTotal" />
        </Card>
        <Card size="small">
          <Statistic title="整体合格率" :value="stat.qualifiedRate" suffix="%" :value-style="{ color: '#2563eb' }" />
        </Card>
        <Card size="small">
          <Statistic title="累计获得学时(h)" :value="stat.totalHours" :value-style="{ color: '#16a34a' }" />
        </Card>
      </div>

      <div class="grid grid-cols-1 gap-3 xl:grid-cols-3">
        <!-- 学员完成情况（左 2 列） -->
        <Card class="xl:col-span-2" title="学员 VR 实操完成情况" size="small">
          <StudentGrid table-title="学员维度汇总">
            <template #rate="{ row }">
              <span class="font-medium text-green-600">{{ row.rate }}%</span>
            </template>
          </StudentGrid>
        </Card>

        <!-- 右侧：分数分布 + 未完成清单 -->
        <div class="flex flex-col gap-3">
          <Card title="实操成绩分布" size="small">
            <div v-for="bucket in stat?.scoreBuckets ?? []" :key="bucket.label" class="mb-2 flex items-center gap-2">
              <span class="w-14 text-xs text-gray-500">{{ bucket.label }} 分</span>
              <Progress
                :percent="stat && stat.recordTotal ? Math.round((bucket.count / stat.recordTotal) * 100) : 0"
                size="small"
                class="!flex-1"
                :stroke-color="bucket.count ? '#4f46e5' : '#e5e7eb'"
              />
              <span class="w-6 text-right text-xs text-gray-400">{{ bucket.count }}</span>
            </div>
          </Card>
          <Card title="未完成任务提醒（前 10 条）" size="small">
            <Table
              :columns="[
                { title: '学员', dataIndex: 'studentName', width: 80 },
                { title: '任务', dataIndex: 'taskName', ellipsis: true },
                { title: '截止', dataIndex: 'deadline', width: 100 },
              ]"
              :data-source="stat?.pendingStudentList ?? []"
              :pagination="false"
              size="small"
              row-key="deadline"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.dataIndex === 'deadline'">
                  <Tag color="warning">{{ record.deadline }}</Tag>
                </template>
              </template>
            </Table>
          </Card>
        </div>
      </div>

      <!-- 学员个人实操历史 -->
      <Card title="学员个人 VR 实操历史（同步个人学习档案）" size="small">
        <div class="mb-3 flex items-center justify-between">
          <div class="flex items-center gap-3">
            <span class="text-sm text-gray-500">选择学员：</span>
            <Select v-model:value="historyStudent" style="width: 160px" @change="() => loadHistory()">
              <Select.Option v-for="name in STUDENT_OPTIONS" :key="name" :value="name">
                {{ name }}
              </Select.Option>
            </Select>
            <span class="text-xs text-gray-400">合格实操将同步计入个人电子学习档案的学时与技能记录。</span>
          </div>
          <Button type="primary" ghost @click="handleExport">导出 CSV</Button>
        </div>
        <Table
          :columns="[
            { title: '记录编号', dataIndex: 'recordNo', width: 170 },
            { title: '学员', dataIndex: 'studentName', width: 90 },
            { title: '实操项目', dataIndex: 'projectName', ellipsis: true },
            { title: '类型', dataIndex: 'source', width: 100 },
            { title: '判定方式', dataIndex: 'method', width: 100 },
            { title: '得分', dataIndex: 'score', width: 80 },
            { title: '是否合格', dataIndex: 'qualified', width: 90 },
            { title: '获得学时', dataIndex: 'gainedHours', width: 90 },
            { title: '完成时间', dataIndex: 'finishedAt', width: 160 },
          ]"
          :data-source="historyRows"
          :loading="historyLoading"
          :pagination="{ pageSize: 8, showTotal: (total) => `共 ${total} 条` }"
          size="small"
          row-key="id"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'source'">
              <Tag :color="record.source === 1 ? 'geekblue' : 'purple'">
                {{ record.source === 1 ? '训练练习' : '正式考核' }}
              </Tag>
            </template>
            <template v-else-if="column.dataIndex === 'method'">
              {{ VR_ASSESS_METHOD_TEXT[record.method] ?? '-' }}
            </template>
            <template v-else-if="column.dataIndex === 'qualified'">
              <Tag :color="record.qualified ? 'success' : 'error'">{{ record.qualified ? '合格' : '未合格' }}</Tag>
            </template>
            <template v-else-if="column.dataIndex === 'score'">
              <span :class="record.qualified ? 'text-green-600' : 'text-red-500'">{{ record.score }}</span>
            </template>
          </template>
        </Table>
      </Card>
    </div>
  </Page>
</template>
