<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import {
  Button,
  Card,
  Col,
  message,
  Progress,
  Row,
  Statistic,
  TabPane,
  Table,
  Tabs,
} from 'ant-design-vue';

import { getPersonalArchive, type PromotionApi } from '../api';

defineOptions({ name: 'TrainingPersonalArchive' });

const router = useRouter();
const activeKey = ref('hours');
const emptyLocale = { emptyText: '数据开发中（依赖模块三/四/五数据接入）' };

const hourColumns = [
  { title: '课程/资源', dataIndex: 'name' },
  { title: '学习类型', dataIndex: 'studyType', width: 100 },
  { title: '学时(小时)', dataIndex: 'hours', width: 100 },
  { title: '是否有效', dataIndex: 'valid', width: 90 },
  { title: '完成日期', dataIndex: 'finishDate', width: 110 },
];

const examColumns = [
  { title: '考试名称', dataIndex: 'examName' },
  { title: '得分', dataIndex: 'score', width: 80 },
  { title: '是否通过', dataIndex: 'passed', width: 90 },
  { title: '考试时间', dataIndex: 'examTime', width: 110 },
];

const certColumns = [
  { title: '证书名称', dataIndex: 'certName' },
  { title: '证书编号', dataIndex: 'certCode' },
  { title: '类型', dataIndex: 'certType', width: 110 },
  { title: '有效期至', dataIndex: 'expireTime', width: 110 },
  { title: '状态', dataIndex: 'status', width: 90 },
];

const vrColumns = [
  { title: 'VR 场景', dataIndex: 'sceneName' },
  { title: '实操得分', dataIndex: 'score', width: 90 },
  { title: '实操学时', dataIndex: 'hours', width: 90 },
  { title: '完成时间', dataIndex: 'finishTime', width: 110 },
];

const growth = ref<PromotionApi.Evaluation>();
const growthItems = computed(() => growth.value ? [{
  gaps: growth.value.rules.filter((item) => !item.passed).map((item) => `${item.ruleName}：${item.gap}`).join('；'),
  name: `${growth.value.currentLevelName} → ${growth.value.nextLevelName || '最高等级'}`,
  percent: Number(growth.value.progress),
}] : []);

onMounted(async () => {
  try {
    const data = await getPersonalArchive();
    growth.value = data.growth;
  } catch {
    message.warning('晋级档案尚未初始化，请联系培训管理员执行晋级门槛 SQL');
  }
});

function handleCertRemind() {
  // TODO[模块九-证书到期] 调用 POST /training/analytics/personal-archive/cert-expire-remind
  message.info('证书到期提醒功能开发中');
}

function goVerify() {
  router.push('/training/analytics/cert-verify');
}
</script>

<template>
  <Page>
    <div class="flex flex-col gap-4 pb-4">
      <section
        class="relative overflow-hidden rounded-2xl px-6 py-7 text-white shadow-sm md:px-9"
        style="background: linear-gradient(125deg, #16a34a 0%, #16a34adc 44%, #111827 115%)"
      >
        <div class="mb-2 text-sm text-white/75">云培训学院 / 模块 09 / 92（学员）</div>
        <h1 class="m-0 text-2xl font-semibold md:text-3xl">个人学习档案</h1>
        <p class="mb-0 mt-3 text-sm leading-6 text-white/80">
          课程学时明细、全部考试记录、电子证书与联合认证、VR 实操记录、人才成长进度与晋升条件缺口。
        </p>
      </section>

      <Row :gutter="16">
        <Col :span="6"><Card :bordered="false"><Statistic title="累计学时(小时)" :value="0" /></Card></Col>
        <Col :span="6"><Card :bordered="false"><Statistic title="考试次数" :value="0" /></Card></Col>
        <Col :span="6"><Card :bordered="false"><Statistic title="证书数量" :value="0" /></Card></Col>
        <Col :span="6"><Card :bordered="false"><Statistic title="VR 实操次数" :value="0" /></Card></Col>
      </Row>

      <Card :bordered="false">
        <Tabs v-model:active-key="activeKey">
          <TabPane key="hours" tab="学时明细">
            <Table row-key="id" size="small" :columns="hourColumns" :data-source="[]" :locale="emptyLocale" :pagination="false" />
          </TabPane>
          <TabPane key="exam" tab="考试记录">
            <Table row-key="id" size="small" :columns="examColumns" :data-source="[]" :locale="emptyLocale" :pagination="false" />
          </TabPane>
          <TabPane key="cert" tab="我的证书">
            <div class="mb-3 flex items-center gap-3">
              <Button @click="goVerify">证书验真入口</Button>
              <Button @click="handleCertRemind">证书到期提醒</Button>
            </div>
            <Table row-key="id" size="small" :columns="certColumns" :data-source="[]" :locale="emptyLocale" :pagination="false" />
          </TabPane>
          <TabPane key="vr" tab="VR 实操记录">
            <Table row-key="id" size="small" :columns="vrColumns" :data-source="[]" :locale="emptyLocale" :pagination="false" />
          </TabPane>
          <TabPane key="growth" tab="人才成长进度">
            <div class="flex flex-col gap-4 py-2">
              <div v-for="item in growthItems" :key="item.name">
                <div class="mb-1 flex justify-between text-sm">
                  <span>{{ item.name }}</span>
                  <span class="text-gray-500">{{ item.percent }}%</span>
                </div>
                <Progress :percent="item.percent" status="normal" stroke-color="#16a34a" />
                <div class="text-xs text-gray-400">晋升条件缺口：{{ item.gaps || '全部硬性条件已达标' }}</div>
              </div>
              <div v-if="!growthItems.length" class="py-6 text-center text-gray-400">正在读取晋级档案…</div>
            </div>
          </TabPane>
        </Tabs>
      </Card>
    </div>
  </Page>
</template>
