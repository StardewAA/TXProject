<script lang="ts" setup>
import type { MesHomeApi } from '#/api/mes/home';

import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { DocAlert, Page } from '@vben/common-ui';

import { Col, Row } from 'ant-design-vue';

import { getHomeSummary } from '#/api/mes/home';

import { defaultSummary } from './data';
import AiAssistant from './modules/ai-assistant.vue';
import AlertPanel from './modules/alert-panel.vue';
import AndonAnalysis from './modules/andon-analysis.vue';
import CheckMaintenAnalysis from './modules/check-mainten-analysis.vue';
import DefectAnalysis from './modules/defect-analysis.vue';
import DeliveryAnalysis from './modules/delivery-analysis.vue';
import FeedbackUserAnalysis from './modules/feedback-user-analysis.vue';
import KpiCards from './modules/kpi-cards.vue';
import MachineryStatusChart from './modules/machinery-status-chart.vue';
import ProductionTrend from './modules/production-trend.vue';
import QcPassRateChart from './modules/qc-pass-rate-chart.vue';
import RepairAnalysis from './modules/repair-analysis.vue';
import Shortcuts from './modules/shortcuts.vue';
import TodoPanel from './modules/todo-panel.vue';
import WorkOrderChart from './modules/work-order-chart.vue';

const router = useRouter();
const summary = ref<MesHomeApi.Summary>(defaultSummary); // 首页汇总统计

/** 跳转到目标页面（按路由 name） */
function handleNavigate(name: string) {
  router.push({ name });
}

/** 加载首页汇总统计 */
async function loadSummary() {
  summary.value = await getHomeSummary();
}

onMounted(() => {
  loadSummary();
});
</script>

<template>
  <Page>
    <template #doc>
      <DocAlert
        title="MES 手册（功能开启）"
        url="https://doc.iocoder.cn/mes/build/"
      />
    </template>

    <!-- 第一行：核心 KPI 汇总卡片 -->
    <KpiCards :summary="summary" class="mb-4" @navigate="handleNavigate" />

    <!-- AI 智能助手：右下角悬浮窗（组件内部自带悬浮球/面板定位，不占页面布局） -->
    <AiAssistant />

    <!-- 我的待办任务：报工等单据审批统一在「工作流程-待办任务」处理，首页直达 -->
    <Row :gutter="16" class="mb-4">
      <Col :lg="24" :md="24" :sm="24" :xl="24" :xs="24" class="mb-4">
        <TodoPanel @navigate="handleNavigate" />
      </Col>
    </Row>

    <!-- 第三行：生产趋势 + 待办异常 -->
    <Row :gutter="16" class="mb-4">
      <Col :lg="16" :md="24" :sm="24" :xl="16" :xs="24" class="mb-4">
        <ProductionTrend />
      </Col>
      <Col :lg="8" :md="24" :sm="24" :xl="8" :xs="24" class="mb-4">
        <AlertPanel :summary="summary" @navigate="handleNavigate" />
      </Col>
    </Row>

    <!-- 第四行：工单分布 + 快捷入口 -->
    <Row :gutter="16" class="mb-4">
      <Col :lg="12" :md="24" :sm="24" :xl="12" :xs="24" class="mb-4">
        <WorkOrderChart />
      </Col>
      <Col :lg="12" :md="24" :sm="24" :xl="12" :xs="24" class="mb-4">
        <Shortcuts @navigate="handleNavigate" />
      </Col>
    </Row>

    <!-- 第五行：设备状态分布 + 质检合格率 -->
    <Row :gutter="16" class="mb-4">
      <Col :lg="12" :md="24" :sm="24" :xl="12" :xs="24" class="mb-4">
        <MachineryStatusChart />
      </Col>
      <Col :lg="12" :md="24" :sm="24" :xl="12" :xs="24" class="mb-4">
        <QcPassRateChart />
      </Col>
    </Row>

    <!-- 第六行：安灯呼叫分析 + 维修工单分析 -->
    <Row :gutter="16" class="mb-4">
      <Col :lg="12" :md="24" :sm="24" :xl="12" :xs="24" class="mb-4">
        <AndonAnalysis />
      </Col>
      <Col :lg="12" :md="24" :sm="24" :xl="12" :xs="24" class="mb-4">
        <RepairAnalysis />
      </Col>
    </Row>

    <!-- 第七行：点检保养执行 + 不良缺陷分析 -->
    <Row :gutter="16" class="mb-4">
      <Col :lg="12" :md="24" :sm="24" :xl="12" :xs="24" class="mb-4">
        <CheckMaintenAnalysis />
      </Col>
      <Col :lg="12" :md="24" :sm="24" :xl="12" :xs="24" class="mb-4">
        <DefectAnalysis />
      </Col>
    </Row>

    <!-- 第八行：生产交付达成 + 人员报工产出 -->
    <Row :gutter="16">
      <Col :lg="12" :md="24" :sm="24" :xl="12" :xs="24" class="mb-4">
        <DeliveryAnalysis />
      </Col>
      <Col :lg="12" :md="24" :sm="24" :xl="12" :xs="24" class="mb-4">
        <FeedbackUserAnalysis />
      </Col>
    </Row>
  </Page>
</template>
