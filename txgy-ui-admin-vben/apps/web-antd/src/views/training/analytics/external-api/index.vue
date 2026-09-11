<script lang="ts" setup>
import { ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  Button,
  Card,
  Col,
  Input,
  message,
  Row,
  TabPane,
  Table,
  Tabs,
} from 'ant-design-vue';

import PageHeaderBar from '../../components/page-header-bar.vue';

defineOptions({ name: 'TrainingExternalApi' });

const activeKey = ref('dispatch');
const emptyLocale = { emptyText: '数据开发中（外部对接链路未启用）' };

const projectId = ref('');
const projectUserIds = ref('');
const forceCourseIds = ref('');
const statusUserIds = ref('');
const archiveDept = ref('');

const statusColumns = [
  { title: '人员ID', dataIndex: 'userId' },
  { title: '姓名', dataIndex: 'userName' },
  { title: '培训任务', dataIndex: 'taskName' },
  { title: '完成状态', dataIndex: 'status', width: 100 },
];

const archiveColumns = [
  { title: '人员ID', dataIndex: 'userId' },
  { title: '姓名(脱敏)', dataIndex: 'userName' },
  { title: '累计学时', dataIndex: 'hours', width: 100 },
  { title: '证书数', dataIndex: 'certCount', width: 90 },
  { title: '档案完整度', dataIndex: 'completeRate', width: 110 },
];

function handleDispatch() {
  // TODO[模块九-任务下发] POST /training/analytics/external-api/task/dispatch
  if (!projectId.value || !projectUserIds.value) {
    message.warning('请填写项目 ID 与人员 ID');
    return;
  }
  message.info('任务下发功能开发中');
}

function handleQueryStatus() {
  // TODO[模块九-状态查询] GET /training/analytics/external-api 状态查询
  message.info('状态查询功能开发中');
}

function handleArchives() {
  // TODO[模块九-档案获取] GET /training/analytics/external-api/archive/batch（单次≤200条，敏感字段脱敏）
  message.info('批量档案获取功能开发中');
}
</script>

<template>
  <Page>
    <div class="flex flex-col gap-4 pb-4">
      <PageHeaderBar
        crumb="云培训学院 / 模块 09 / 96"
        title="外部系统对接"
        description="接收外部工程项目管理系统请求：培训任务下发、人员培训状态查询/推送、脱敏培训档案批量获取。"
      />

      <Card :bordered="false">
        <Tabs v-model:active-key="activeKey">
          <TabPane key="dispatch" tab="培训任务下发">
            <div class="flex max-w-3xl flex-col gap-3">
              <Input v-model:value="projectId" placeholder="项目 ID，如 XM20260001" />
              <Input v-model:value="projectUserIds" placeholder="人员 ID 列表（逗号分隔）" />
              <Input v-model:value="forceCourseIds" placeholder="强制课程 ID 列表（选填，逗号分隔）" />
              <Button type="primary" class="w-40" @click="handleDispatch">下发培训任务</Button>
              <div class="text-xs text-gray-400">
                请求做参数有效性校验，支持超时重试，受接口鉴权与限流 QPS 控制
              </div>
            </div>
          </TabPane>

          <TabPane key="status" tab="培训状态查询 / 推送">
            <div class="mb-3 flex flex-wrap items-center gap-3">
              <Input
                v-model:value="statusUserIds"
                class="max-w-md"
                placeholder="人员 ID 列表（逗号分隔）"
              />
              <Button type="primary" @click="handleQueryStatus">查询完成状态</Button>
            </div>
            <Table
              row-key="id"
              size="small"
              :columns="statusColumns"
              :data-source="[]"
              :locale="emptyLocale"
              :pagination="false"
            />
          </TabPane>

          <TabPane key="archive" tab="获取人员培训档案">
            <Row :gutter="16" class="mb-3">
              <Col :span="8">
                <Input v-model:value="archiveDept" placeholder="按部门/班组筛选（选填）" />
              </Col>
              <Col :span="4">
                <Button type="primary" @click="handleArchives">批量获取</Button>
              </Col>
            </Row>
            <div class="mb-3 text-xs text-gray-400">单次最多返回 200 条，敏感字段自动脱敏输出</div>
            <Table
              row-key="id"
              size="small"
              :columns="archiveColumns"
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
