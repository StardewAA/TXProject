<script lang="ts" setup>
import { ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Card, Col, Row, Select, Statistic, Table } from 'ant-design-vue';

defineOptions({ name: 'TrainingLecturerData' });

// TODO[模块九-讲师权限] 课程下拉数据源 = 讲师自己创建的课程列表
const myCourses = ref<any[]>([]);
const courseId = ref();
const emptyLocale = {
  emptyText: '数据开发中（仅展示讲师自己创建课程下的学员数据，无跨课程/企业级权限）',
};

const columns = [
  { title: '学员', dataIndex: 'userName' },
  { title: '课程', dataIndex: 'courseName' },
  { title: '学习进度', dataIndex: 'progress' },
  { title: '累计学时(小时)', dataIndex: 'hours', width: 120 },
  { title: '完成状态', dataIndex: 'finished', width: 100 },
  { title: '最近学习时间', dataIndex: 'lastTime', width: 120 },
];
</script>

<template>
  <Page>
    <div class="flex flex-col gap-4 pb-4">
      <section
        class="relative overflow-hidden rounded-2xl px-6 py-7 text-white shadow-sm md:px-9"
        style="background: linear-gradient(125deg, #db2777 0%, #db2777dc 44%, #111827 115%)"
      >
        <div class="mb-2 text-sm text-white/75">云培训学院 / 模块 09 / 93（认证讲师）</div>
        <h1 class="m-0 text-2xl font-semibold md:text-3xl">讲师数据权限</h1>
        <p class="mb-0 mt-3 text-sm leading-6 text-white/80">
          仅可查看自己创建课程下学员学习完成情况；无跨课程、企业级档案和报表查看权限。
        </p>
      </section>

      <Row :gutter="16">
        <Col :span="8"><Card :bordered="false"><Statistic title="我的课程数" :value="0" /></Card></Col>
        <Col :span="8"><Card :bordered="false"><Statistic title="覆盖学员数" :value="0" /></Card></Col>
        <Col :span="8"><Card :bordered="false"><Statistic title="平均完成率" :value="0" suffix="%" /></Card></Col>
      </Row>

      <Card :bordered="false" title="学员学习完成情况">
        <Select
          v-model:value="courseId"
          class="mb-3 w-72"
          :options="myCourses"
          placeholder="选择我的课程（开发中）"
          disabled
        />
        <Table
          row-key="id"
          size="small"
          :columns="columns"
          :data-source="[]"
          :locale="emptyLocale"
          :pagination="false"
        />
      </Card>
    </div>
  </Page>
</template>
