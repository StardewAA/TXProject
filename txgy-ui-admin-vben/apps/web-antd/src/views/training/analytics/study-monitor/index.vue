<script lang="ts" setup>
import { ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  Button,
  Card,
  Col,
  message,
  Row,
  Statistic,
  TabPane,
  Table,
  Tabs,
  Tag,
} from 'ant-design-vue';

import PageHeaderBar from '../../components/page-header-bar.vue';

defineOptions({ name: 'TrainingStudyMonitor' });

// TODO[模块九-学习行为] 对接 /admin-api/training/analytics/study-monitor/page
const activeKey = ref('behavior');
const selectedIds = ref<number[]>([]);

const behaviorColumns = [
  { title: '学员', dataIndex: 'userName' },
  { title: '学习类型', dataIndex: 'studyType', width: 100 },
  { title: '资源名称', dataIndex: 'resourceTitle' },
  { title: '学习时长(秒)', dataIndex: 'studySeconds', width: 110 },
  { title: '有效学时(秒)', dataIndex: 'validSeconds', width: 110 },
  { title: '状态', dataIndex: 'validFlag', width: 90 },
  { title: '学习日期', dataIndex: 'studyDate', width: 110 },
];

const unfinishedColumns = [
  { title: '学员', dataIndex: 'userName' },
  { title: '部门', dataIndex: 'deptName' },
  { title: '培训任务', dataIndex: 'taskName' },
  { title: '完成进度', dataIndex: 'progress' },
];

const emptyLocale = { emptyText: '数据开发中（依赖学习行为日志采集上线）' };

function handleRemind() {
  if (selectedIds.value.length === 0) {
    message.warning('请先勾选需要督促的人员');
    return;
  }
  // TODO[模块九-学习督促] 调用 POST /training/analytics/study-monitor/remind
  message.info(`批量督促功能开发中（已选 ${selectedIds.value.length} 人）`);
}
</script>

<template>
  <Page>
    <div class="flex flex-col gap-4 pb-4">
      <PageHeaderBar
        crumb="云培训学院 / 模块 09 / 91（管理员）"
        title="学时管控与学习监管"
        description="全量学习行为日志记录，结合防刷课规则计算有效学时，区分正常学时与刷课异常标记。"
      />

      <Row :gutter="16">
        <Col :span="6"><Card :bordered="false"><Statistic title="学习总人次" :value="0" /></Card></Col>
        <Col :span="6"><Card :bordered="false"><Statistic title="有效学时合计(小时)" :value="0" /></Card></Col>
        <Col :span="6"><Card :bordered="false"><Statistic title="刷课异常标记数" :value="0" /></Card></Col>
        <Col :span="6"><Card :bordered="false"><Statistic title="未完成任务人员" :value="0" /></Card></Col>
      </Row>

      <Card :bordered="false">
        <Tabs v-model:active-key="activeKey">
          <TabPane key="behavior" tab="全量学习行为日志">
            <Table
              row-key="id"
              size="small"
              :columns="behaviorColumns"
              :data-source="[]"
              :locale="emptyLocale"
              :pagination="false"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'validFlag'">
                  <Tag :color="record.validFlag ? 'green' : 'red'">
                    {{ record.validFlag ? '正常' : '异常' }}
                  </Tag>
                </template>
              </template>
            </Table>
          </TabPane>
          <TabPane key="unfinished" tab="未完成人员清单">
            <div class="mb-3 flex items-center gap-3">
              <Button type="primary" @click="handleRemind">批量消息督促</Button>
              <span class="text-xs text-gray-400">
                勾选人员后批量推送站内督促消息，推送日志留痕（开发中）
              </span>
            </div>
            <Table
              row-key="id"
              size="small"
              :columns="unfinishedColumns"
              :data-source="[]"
              :locale="emptyLocale"
              :pagination="false"
            />
          </TabPane>
        </Tabs>
      </Card>
    </div>
  </Page>
</template>
