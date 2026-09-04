<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import { Button, Card, Col, message, Row, Statistic, Table, TabPane, Tabs } from 'ant-design-vue';

defineOptions({ name: 'TrainingPlatformDashboard' });

const exportLoading = ref(false);
const tabKey = ref('dashboard');

const platformChartRef = ref<EchartsUIType>();
const talentChartRef = ref<EchartsUIType>();
const { renderEcharts: renderPlatformChart } = useEcharts(platformChartRef);
const { renderEcharts: renderTalentChart } = useEcharts(talentChartRef);

const summaryItems = [
  { title: '工种培训覆盖率', value: '- %' },
  { title: '课程访问量', value: '-' },
  { title: '考试通过率', value: '- %' },
  { title: '证书发放总量', value: '-' },
  { title: 'VR 实操完成率', value: '- %' },
];

const manualLogColumns = [
  { title: '学员', dataIndex: 'targetUserName' },
  { title: '变更字段', dataIndex: 'fieldName' },
  { title: '变更前', dataIndex: 'beforeValue' },
  { title: '变更后', dataIndex: 'afterValue' },
  { title: '操作人', dataIndex: 'operatorName', width: 100 },
  { title: '备注', dataIndex: 'remark' },
];

const apiLogColumns = [
  { title: '接入方', dataIndex: 'appName' },
  { title: '接口', dataIndex: 'apiPath' },
  { title: '耗时(ms)', dataIndex: 'costMs', width: 90 },
  { title: '结果', dataIndex: 'success', width: 80 },
  { title: '调用时间', dataIndex: 'callTime', width: 120 },
];

function renderPlaceholders() {
  renderPlatformChart({
    title: { left: 'center', text: '全平台指标（开发中占位）', textStyle: { fontSize: 13 } },
    xAxis: { type: 'category', data: ['覆盖率', '访问量', '通过率', '证书', 'VR'] },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', data: [0, 0, 0, 0, 0], itemStyle: { color: '#475569' } }],
  });
  renderTalentChart({
    title: { left: 'center', text: '人才等级分布（开发中占位）', textStyle: { fontSize: 13 } },
    series: [
      {
        type: 'pie',
        radius: '60%',
        data: [{ name: '暂无数据', value: 1 }],
      },
    ],
  });
}

function handleExport() {
  exportLoading.value = true;
  setTimeout(() => {
    exportLoading.value = false;
    message.info('导出功能开发中');
  }, 300);
}

onMounted(() => {
  renderPlaceholders();
});
</script>

<template>
  <Page>
    <div class="flex flex-col gap-4 pb-4">
      <section
        class="relative overflow-hidden rounded-2xl px-6 py-7 text-white shadow-sm md:px-9"
        style="background: linear-gradient(125deg, #0f172a 0%, #1e293b 44%, #111827 115%)"
      >
        <div class="flex flex-col justify-between gap-4 md:flex-row md:items-center">
          <div>
            <div class="mb-2 text-sm text-white/75">云培训学院 / 模块 09 / 95（仅超级管理员）</div>
            <h1 class="m-0 text-2xl font-semibold md:text-3xl">全平台看板与治理</h1>
            <p class="mb-0 mt-3 text-sm leading-6 text-white/80">
              平台级汇总统计与治理配置。跨租户真实查询将在后续迭代接入。
            </p>
          </div>
          <div class="flex shrink-0 items-center gap-3">
            <Button :loading="exportLoading" ghost @click="handleExport"> 导出 </Button>
          </div>
        </div>
      </section>

      <Tabs v-model:active-key="tabKey">
        <TabPane key="dashboard" tab="全平台大数据看板">
          <Row :gutter="16" class="mb-4">
            <Col v-for="item in summaryItems" :key="item.title" :span="5">
              <Card :bordered="false">
                <Statistic :title="item.title" :value="item.value" />
              </Card>
            </Col>
          </Row>
          <Row :gutter="16">
            <Col :span="12">
              <Card :bordered="false">
                <EchartsUI ref="platformChartRef" style="height: 300px" />
              </Card>
            </Col>
            <Col :span="12">
              <Card :bordered="false">
                <EchartsUI ref="talentChartRef" style="height: 300px" />
              </Card>
            </Col>
          </Row>
        </TabPane>

        <TabPane key="governance" tab="治理配置（骨架）">
          <Card title="档案人工变更（留痕审计，不可删除）" :bordered="false" class="mb-4">
            <div class="mb-3 flex items-center gap-3">
              <Button disabled> 人工补录 / 变更档案 </Button>
              <span class="text-xs text-gray-400">
                每次修改记录操作人、修改前后值、时间、备注（表 training_archive_manual_log 已建）
              </span>
            </div>
            <Table
              row-key="id"
              size="small"
              :columns="manualLogColumns"
              :data-source="[]"
              :locale="{ emptyText: '数据开发中' }"
              :pagination="false"
            />
          </Card>
          <Card title="证书模板、学时规则配置" :bordered="false" class="mb-4">
            <div class="text-sm text-gray-500">
              证书模板与通用学时（含 VR 实操学时）规则配置（表 training_cert_template 已建，功能开发中）。
            </div>
          </Card>
          <Card title="外部 API 接口管理" :bordered="false" class="mb-4">
            <div class="mb-3 text-sm text-gray-500">
              对外接口鉴权、限流 QPS、降级开关、黑名单（表 training_external_api_config 已建，功能开发中）。
            </div>
            <Card :bordered="false" size="small" title="接口调用日志">
              <Table
                row-key="id"
                size="small"
                :columns="apiLogColumns"
                :data-source="[]"
                :locale="{ emptyText: '数据开发中' }"
                :pagination="false"
              />
            </Card>
          </Card>
          <Card title="租户与企业数据隔离配置" :bordered="false">
            <!-- TODO[模块九-集团视角] 跨租户真实查询与隔离配置维护 -->
            <div class="text-sm text-gray-500">
              维护租户数据隔离配置项，管控企业间数据访问边界（接口已注册，功能开发中）。
            </div>
          </Card>
        </TabPane>
      </Tabs>
    </div>
  </Page>
</template>
