<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import type { TrainingAnalyticsApi } from './api';

import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import { Button, Card, Col, Row, Statistic, Table, Tag } from 'ant-design-vue';
import dayjs from 'dayjs';

import { getAnalyticsDashboard } from './api';

defineOptions({ name: 'TrainingAnalytics' });

const router = useRouter();
const loading = ref(false);
const dashboard = ref<TrainingAnalyticsApi.Dashboard>();

const courseChartRef = ref<EchartsUIType>();
const trendChartRef = ref<EchartsUIType>();
const { renderEcharts: renderCourseChart } = useEcharts(courseChartRef);
const { renderEcharts: renderTrendChart } = useEcharts(trendChartRef);

const COURSE_STATUS_ITEMS = [
  { key: 'onShelf', name: '已上架', color: '#16a34a' },
  { key: 'pendingReview', name: '待审核', color: '#f59e0b' },
  { key: 'approved', name: '待上架', color: '#2563eb' },
  { key: 'draft', name: '草稿', color: '#9ca3af' },
  { key: 'offShelf', name: '已下架', color: '#ef4444' },
] as const;

const logColumns = [
  { title: '操作人', dataIndex: 'userName', width: 120 },
  { title: '动作', dataIndex: 'action', width: 120 },
  { title: '目标类型', dataIndex: 'targetType', width: 140 },
  { title: '操作时间', dataIndex: 'createTime' },
];

function formatTime(value?: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-';
}

function renderCharts() {
  const data = dashboard.value;
  if (!data) {
    return;
  }
  const course = (data.course ?? {}) as Record<string, number>;
  renderCourseChart({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, left: 'center' },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        itemStyle: { borderRadius: 6 },
        label: { show: false },
        data: COURSE_STATUS_ITEMS.map((item) => ({
          itemStyle: { color: item.color },
          name: item.name,
          value: Number(course[item.key] ?? 0),
        })),
      },
    ],
  });
  const trend = data.operationTrend7d ?? [];
  renderTrendChart({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 16, top: 24, bottom: 28 },
    xAxis: { type: 'category', data: trend.map((item) => item.date) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        type: 'bar',
        barMaxWidth: 32,
        itemStyle: { borderRadius: [4, 4, 0, 0], color: '#2563eb' },
        data: trend.map((item) => item.count),
      },
    ],
  });
}

async function loadDashboard() {
  loading.value = true;
  try {
    dashboard.value = await getAnalyticsDashboard();
    renderCharts();
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadDashboard();
});
</script>
<template>
  <Page>
    <div class="flex flex-col gap-4 pb-4">
      <section
        class="relative overflow-hidden rounded-2xl px-6 py-7 text-white shadow-sm md:px-9"
        style="background: linear-gradient(125deg, #475569 0%, #475569dc 44%, #111827 115%)"
      >
        <div class="flex flex-col justify-between gap-4 md:flex-row md:items-center">
          <div>
            <div class="mb-2 text-sm text-white/75">云培训学院 / 模块 09</div>
            <h1 class="m-0 text-2xl font-semibold md:text-3xl">数据统计与档案归档</h1>
            <p class="mb-0 mt-3 text-sm leading-6 text-white/80">
              汇总培训投入、过程和成效数据，形成可追溯的培训档案（首期：统计看板）。
            </p>
          </div>
          <div class="shrink-0">
            <Button ghost @click="router.push('/training/workbench')">
              返回工作台
            </Button>
          </div>
        </div>
      </section>

      <Card title="课程规模" :loading="loading" :bordered="false">
        <Row :gutter="16">
          <Col :span="4"><Statistic title="课程总数" :value="dashboard?.course?.total ?? 0" /></Col>
          <Col :span="4"><Statistic title="已上架" :value="dashboard?.course?.onShelf ?? 0" /></Col>
          <Col :span="4"><Statistic title="待审核" :value="dashboard?.course?.pendingReview ?? 0" /></Col>
          <Col :span="4"><Statistic title="待上架" :value="dashboard?.course?.approved ?? 0" /></Col>
          <Col :span="4"><Statistic title="草稿" :value="dashboard?.course?.draft ?? 0" /></Col>
          <Col :span="4"><Statistic title="媒资绑定" :value="dashboard?.course?.mediaTotal ?? 0" /></Col>
        </Row>
      </Card>

      <Row :gutter="16">
        <Col :span="12">
          <Card title="资源规模" :loading="loading" :bordered="false">
            <Row :gutter="16">
              <Col :span="5"><Statistic title="文档总数" :value="dashboard?.resource?.documentTotal ?? 0" /></Col>
              <Col :span="5"><Statistic title="在线文档" :value="dashboard?.resource?.documentOnline ?? 0" /></Col>
              <Col :span="5"><Statistic title="视频总数" :value="dashboard?.resource?.videoTotal ?? 0" /></Col>
              <Col :span="5"><Statistic title="在线视频" :value="dashboard?.resource?.videoOnline ?? 0" /></Col>
              <Col :span="4"><Statistic title="试题总数" :value="dashboard?.resource?.questionTotal ?? 0" /></Col>
            </Row>
          </Card>
        </Col>
        <Col :span="12">
          <Card title="互动与日志" :loading="loading" :bordered="false">
            <Row :gutter="16">
              <Col :span="6"><Statistic title="课程评论" :value="dashboard?.activity?.commentTotal ?? 0" /></Col>
              <Col :span="6"><Statistic title="审计日志" :value="dashboard?.activity?.auditLogTotal ?? 0" /></Col>
              <Col :span="6"><Statistic title="资源操作日志" :value="dashboard?.activity?.operationLogTotal ?? 0" /></Col>
              <Col :span="6"><Statistic title="近 7 天操作" :value="dashboard?.activity?.operationLogLast7d ?? 0" /></Col>
            </Row>
          </Card>
        </Col>
      </Row>

      <Row :gutter="16">
        <Col :span="10">
          <Card title="课程状态分布" :bordered="false">
            <EchartsUI ref="courseChartRef" style="height: 320px" />
          </Card>
        </Col>
        <Col :span="14">
          <Card title="近 7 天资源操作趋势" :bordered="false">
            <EchartsUI ref="trendChartRef" style="height: 320px" />
          </Card>
        </Col>
      </Row>

      <Row :gutter="16">
        <Col :span="10">
          <Card title="文档资源分类分布" :loading="loading" :bordered="false">
            <template v-if="(dashboard?.documentCategoryDistribution?.length ?? 0) > 0">
              <div
                v-for="item in dashboard?.documentCategoryDistribution"
                :key="item.category"
                class="mb-3"
              >
                <div class="mb-1 flex justify-between text-sm">
                  <span>{{ item.category }}</span>
                  <span class="text-gray-500">{{ item.count }} 份</span>
                </div>
                <div class="h-2 rounded bg-gray-100 dark:bg-gray-700">
                  <div
                    class="h-2 rounded bg-blue-500"
                    :style="{
                      width: `${Math.min(
                        100,
                        (item.count * 100) /
                          Math.max(1, dashboard?.documentCategoryDistribution?.[0]?.count ?? 1),
                      )}%`,
                    }"
                  ></div>
                </div>
              </div>
            </template>
            <div v-else class="py-6 text-center text-sm text-gray-400">暂无文档数据</div>
          </Card>
        </Col>
        <Col :span="14">
          <Card title="最近操作日志（Top 10）" :loading="loading" :bordered="false">
            <Table
              row-key="id"
              size="small"
              :columns="logColumns"
              :data-source="dashboard?.recentLogs ?? []"
              :pagination="false"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'action'">
                  <Tag color="blue">{{ record.action }}</Tag>
                </template>
                <template v-else-if="column.key === 'createTime'">
                  {{ formatTime(record.createTime) }}
                </template>
              </template>
            </Table>
          </Card>
        </Col>
      </Row>
    </div>
  </Page>
</template>
