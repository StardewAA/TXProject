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
  Select,
  Statistic,
  TabPane,
  Table,
  Tabs,
} from 'ant-design-vue';

defineOptions({ name: 'TrainingEnterpriseReport' });

const activeKey = ref('archive');
const emptyLocale = { emptyText: '数据开发中（租户插件自动隔离，仅本企业数据）' };

const archiveColumns = [
  { title: '员工', dataIndex: 'userName' },
  { title: '部门/班组', dataIndex: 'deptName' },
  { title: '岗位', dataIndex: 'postName' },
  { title: '人才等级', dataIndex: 'talentLevel', width: 100 },
  { title: '累计学时(小时)', dataIndex: 'hours', width: 120 },
  { title: '证书数', dataIndex: 'certCount', width: 80 },
];

const superviseColumns = [
  { title: '培训任务', dataIndex: 'taskName' },
  { title: '类型', dataIndex: 'taskType', width: 100 },
  { title: '逾期人员数', dataIndex: 'overdueCount', width: 100 },
  { title: '督办状态', dataIndex: 'supervised', width: 100 },
];

const profileColumns = [
  { title: '员工', dataIndex: 'userName' },
  { title: '工种', dataIndex: 'workType' },
  { title: '基础资料完整度', dataIndex: 'completeRate', width: 130 },
  { title: '维护', dataIndex: 'action', width: 90 },
];

const projectId = ref('');

function handleExport() {
  // TODO[模块九-报表导出] 调用 GET /training/analytics/enterprise-report/export 下载 Excel
  message.info('导出功能开发中');
}

function handleProjectArchive() {
  // TODO[模块九-项目归档] 调用 GET /training/analytics/enterprise-report/project-archive
  message.info('项目归档报表功能开发中');
}
</script>

<template>
  <Page>
    <div class="flex flex-col gap-4 pb-4">
      <section
        class="relative overflow-hidden rounded-2xl px-6 py-7 text-white shadow-sm md:px-9"
        style="background: linear-gradient(125deg, #0891b2 0%, #0891b2dc 44%, #111827 115%)"
      >
        <div class="mb-2 text-sm text-white/75">云培训学院 / 模块 09 / 94（企业管理员，严格企业数据隔离）</div>
        <h1 class="m-0 text-2xl font-semibold md:text-3xl">企业培训报表与档案</h1>
        <p class="mb-0 mt-3 text-sm leading-6 text-white/80">
          本企业全员档案、学徒技师人才等级分布、多维报表导出、项目培训归档、任务督办与员工基础档案维护。
        </p>
      </section>

      <Row :gutter="16">
        <Col :span="6"><Card :bordered="false"><Statistic title="本企业员工数" :value="0" /></Card></Col>
        <Col :span="6"><Card :bordered="false"><Statistic title="参训人数" :value="0" /></Card></Col>
        <Col :span="6"><Card :bordered="false"><Statistic title="学时完成率" :value="0" suffix="%" /></Card></Col>
        <Col :span="6"><Card :bordered="false"><Statistic title="证书数量" :value="0" /></Card></Col>
      </Row>

      <Card :bordered="false">
        <Tabs v-model:active-key="activeKey">
          <TabPane key="archive" tab="全员档案清单">
            <div class="mb-3 text-xs text-gray-400">
              学徒技师人才等级分布：待接入模块四/五数据后展示（接口已就绪）
            </div>
            <Table row-key="id" size="small" :columns="archiveColumns" :data-source="[]" :locale="emptyLocale" :pagination="false" />
          </TabPane>

          <TabPane key="export" tab="培训统计报表导出">
            <div class="mb-3 flex flex-wrap items-center gap-3">
              <Select class="w-44" disabled placeholder="筛选维度：个人/班组" />
              <Select class="w-44" disabled placeholder="选择班组（开发中）" />
              <Button type="primary" @click="handleExport">导出 Excel 报表</Button>
            </div>
            <div class="text-xs text-gray-400">
              报表指标：参训人数、学时完成率、考试参与率、通过率、证书数量（导出功能开发中）
            </div>
          </TabPane>

          <TabPane key="project" tab="项目培训归档">
            <div class="mb-3 flex items-center gap-3">
              <Input
                v-model:value="projectId"
                class="w-72"
                placeholder="输入项目 ID，如 XM20260001"
              />
              <Button type="primary" @click="handleProjectArchive">生成归档报表</Button>
            </div>
            <div class="text-xs text-gray-400">
              输出该项目参建人员学时汇总、考试记录、证书清单（只展示本企业参建人员数据）
            </div>
          </TabPane>

          <TabPane key="supervise" tab="任务督办与档案维护">
            <Row :gutter="16">
              <Col :span="12">
                <Card :bordered="false" size="small" title="督办培训任务（岗前/复工）">
                  <Table
                    row-key="id"
                    size="small"
                    :columns="superviseColumns"
                    :data-source="[]"
                    :locale="emptyLocale"
                    :pagination="false"
                  />
                </Card>
              </Col>
              <Col :span="12">
                <Card :bordered="false" size="small" title="员工基础档案维护">
                  <Table
                    row-key="id"
                    size="small"
                    :columns="profileColumns"
                    :data-source="[]"
                    :locale="emptyLocale"
                    :pagination="false"
                  />
                </Card>
              </Col>
            </Row>
          </TabPane>
        </Tabs>
      </Card>
    </div>
  </Page>
</template>
