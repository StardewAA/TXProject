<script lang="ts" setup>
import type { HrmRecruitFunnelApi } from '#/api/hrm/recruit/funnel';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  Alert,
  Card,
  Col,
  Progress,
  Row,
  Select,
  Statistic,
  Table,
} from 'ant-design-vue';

import { getRecruitFunnelList } from '#/api/hrm/recruit/funnel';

defineOptions({ name: 'HrmRecruitFunnel' });

const loading = ref(false);
const months = ref(6);
const list = ref<HrmRecruitFunnelApi.Funnel[]>([]);

/** 全局汇总（所有职位合计） */
const summary = computed(() => {
  const items = list.value;
  const sum = (key: keyof HrmRecruitFunnelApi.Funnel) =>
    items.reduce((total, item) => total + Number(item[key] || 0), 0);
  const applicationCount = sum('applicationCount');
  const onboardedCount = sum('onboardedCount');
  const withDays = items.filter((item) => Number(item.onboardedCount || 0) > 0);
  const avgDays =
    withDays.length === 0
      ? 0
      : Number(
          (
            withDays.reduce(
              (total, item) => total + Number(item.avgOnboardDays || 0),
              0,
            ) / withDays.length
          ).toFixed(1),
        );
  return {
    applicationCount,
    screeningPassedCount: sum('screeningPassedCount'),
    interviewCount: sum('interviewCount'),
    finalPassedCount: sum('finalPassedCount'),
    offerSentCount: sum('offerSentCount'),
    onboardedCount,
    onboardRate:
      applicationCount === 0
        ? 0
        : Number(((onboardedCount / applicationCount) * 100).toFixed(1)),
    avgDays,
  };
});

async function loadData() {
  loading.value = true;
  try {
    list.value = (await getRecruitFunnelList(months.value)) || [];
  } finally {
    loading.value = false;
  }
}

onMounted(loadData);
</script>

<template>
  <Page auto-content-height>
    <Card :bordered="false">
      <Alert
        class="mb-3"
        message="招聘漏斗：按职位统计 投递 → 筛选通过 → 进入面试 → 终面通过 → Offer 发出 → 已入职 的转化情况；阶段到达依据应聘状态流转日志，转化率按上一环节计算。"
        show-icon
        type="info"
      />

      <div class="mb-3 flex items-center gap-2">
        <span class="text-sm">统计区间</span>
        <Select
          v-model:value="months"
          :options="[
            { label: '最近 3 个月', value: 3 },
            { label: '最近 6 个月', value: 6 },
            { label: '最近 12 个月', value: 12 },
          ]"
          class="w-40"
          @change="loadData"
        />
      </div>

      <Row :gutter="16" class="mb-4">
        <Col :span="4">
          <Statistic title="投递总数" :value="summary.applicationCount" />
        </Col>
        <Col :span="4">
          <Statistic title="筛选通过" :value="summary.screeningPassedCount" />
        </Col>
        <Col :span="4">
          <Statistic title="进入面试" :value="summary.interviewCount" />
        </Col>
        <Col :span="4">
          <Statistic title="Offer 已发" :value="summary.offerSentCount" />
        </Col>
        <Col :span="4">
          <Statistic title="已入职" :value="summary.onboardedCount" />
        </Col>
        <Col :span="4">
          <Statistic
            title="平均入职耗时(天)"
            :value="summary.avgDays"
            :precision="1"
          />
        </Col>
      </Row>

      <Table
        :columns="[
          { title: '招聘职位', dataIndex: 'postName', minWidth: 220 },
          { title: '投递', dataIndex: 'applicationCount', width: 80 },
          { title: '筛选通过', dataIndex: 'screeningPassedCount', width: 100 },
          {
            title: '筛选通过率',
            dataIndex: 'screeningRate',
            width: 110,
            customRender: ({ text }: any) => `${text ?? 0}%`,
          },
          { title: '进入面试', dataIndex: 'interviewCount', width: 100 },
          {
            title: '面试进入率',
            dataIndex: 'interviewRate',
            width: 110,
            customRender: ({ text }: any) => `${text ?? 0}%`,
          },
          { title: '终面通过', dataIndex: 'finalPassedCount', width: 100 },
          { title: 'Offer已发', dataIndex: 'offerSentCount', width: 100 },
          {
            title: 'Offer发放率',
            dataIndex: 'offerRate',
            width: 110,
            customRender: ({ text }: any) => `${text ?? 0}%`,
          },
          { title: '已入职', dataIndex: 'onboardedCount', width: 90 },
          { title: '入职转化率', key: 'onboardRate', width: 160 },
          {
            title: '平均入职(天)',
            dataIndex: 'avgOnboardDays',
            width: 120,
          },
        ]"
        :data-source="list"
        :loading="loading"
        :pagination="false"
        :row-key="(row: HrmRecruitFunnelApi.Funnel) => String(row.postId)"
        :scroll="{ x: 1600 }"
        bordered
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'onboardRate'">
            <Progress
              :percent="Number(record.onboardRate || 0)"
              :format="(p: number) => `${p}%`"
              size="small"
              status="active"
            />
          </template>
        </template>
      </Table>
    </Card>
  </Page>
</template>
