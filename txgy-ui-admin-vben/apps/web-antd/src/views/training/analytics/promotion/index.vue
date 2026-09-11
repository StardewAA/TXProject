<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';
import {
  Alert, Button, Card, Col, DatePicker, Form, FormItem, Input, InputNumber,
  Modal, Progress, Row, Select, Statistic, Table, Tag, message,
} from 'ant-design-vue';
import dayjs, { type Dayjs } from 'dayjs';

import PageHeaderBar from '../components/page-header-bar.vue';

import {
  getPromotionContext, getPromotionEmployees, getPromotionEvaluation, getPromotionProfile,
  getPromotionRules, reportPromotionMetric, runPromotionEvaluation, savePromotionProfile,
  updatePromotionRule, type PromotionApi,
} from '../api';

defineOptions({ name: 'TrainingPromotionThreshold' });

const loading = ref(false);
const context = ref<PromotionApi.Context>({ levels: [], paths: [], sources: {} });
const employees = ref<PromotionApi.Employee[]>([]);
const selectedUserId = ref<number>();
const selectedPath = ref<string>();
const rules = ref<PromotionApi.Rule[]>([]);
const evaluation = ref<PromotionApi.Evaluation>();
const profile = reactive<{ currentLevel: string; jobType: string; levelSince: Dayjs }>({
  currentLevel: 'APPRENTICE', jobType: '', levelSince: dayjs(),
});
const metricModal = ref(false);
const ruleModal = ref(false);
const metric = reactive({ evidence: '', metricValue: 0, ruleKey: '', sourceModule: '' });
const editingRule = reactive({ hard: true, id: 0, operator: '>=', ruleValue: 0, status: 0 });

const pathOptions = computed(() => context.value.paths.map((p) => ({ label: p.name, value: `${p.fromLevel}|${p.toLevel}` })));
const employeeOptions = computed(() => employees.value.map((u) => ({ label: `${u.nickname || u.username}（${u.username}）`, value: u.id })));
const displayedRules = computed(() => {
  const activeEvaluationPath = evaluation.value?.nextLevel
    ? `${evaluation.value.currentLevel}|${evaluation.value.nextLevel}` : '';
  return selectedPath.value === activeEvaluationPath ? evaluation.value?.rules || [] : rules.value;
});
const columns = [
  { title: '门槛指标', dataIndex: 'ruleName', width: 180 },
  { title: '要求', key: 'target', width: 120 },
  { title: '数据来源', key: 'source', width: 150 },
  { title: '当前值', key: 'actual', width: 110 },
  { title: '结果/差距', key: 'result' },
  { title: '操作', key: 'action', width: 150 },
];
function resultText(record: any) {
  if (record.passed) return '达标';
  if (!record.available) return `暂无${context.value.sources[record.sourceModule] || '相关'}数据`;
  return record.gap || '待评估';
}

async function loadPathRules() {
  if (!selectedPath.value) return;
  const [fromLevel, toLevel] = selectedPath.value.split('|');
  rules.value = await getPromotionRules({ fromLevel: fromLevel!, toLevel: toLevel! });
}

async function chooseUser() {
  if (!selectedUserId.value) return;
  const [p, e] = await Promise.all([getPromotionProfile(selectedUserId.value), getPromotionEvaluation(selectedUserId.value)]);
  profile.currentLevel = p.currentLevel;
  profile.jobType = p.jobType || '';
  profile.levelSince = dayjs(p.levelSince);
  evaluation.value = e;
  if (e.nextLevel) selectedPath.value = `${e.currentLevel}|${e.nextLevel}`;
  await loadPathRules();
}

async function saveProfile() {
  if (!selectedUserId.value) return message.warning('请先选择人员');
  await savePromotionProfile({ userId: selectedUserId.value, currentLevel: profile.currentLevel, levelSince: profile.levelSince.format('YYYY-MM-DD'), jobType: profile.jobType });
  message.success('晋级档案已保存'); await chooseUser();
}

async function evaluate() {
  if (!selectedUserId.value) return message.warning('请先选择人员');
  loading.value = true;
  try { evaluation.value = await runPromotionEvaluation(selectedUserId.value); message.success('评估完成并已留存快照'); }
  finally { loading.value = false; }
}

function openMetric(row: any) {
  metric.ruleKey = row.ruleKey; metric.metricValue = Number(row.actualValue || 0);
  metric.sourceModule = row.sourceModule; metric.evidence = ''; metricModal.value = true;
}
async function saveMetric() {
  if (!selectedUserId.value) return;
  await reportPromotionMetric({ ...metric, userId: selectedUserId.value });
  metricModal.value = false; message.success('考核数据已保存'); await chooseUser();
}
function openRule(row: any) {
  editingRule.id = row.id; editingRule.operator = row.operator; editingRule.ruleValue = Number(row.ruleValue);
  editingRule.hard = Boolean(row.isHard); editingRule.status = row.status; ruleModal.value = true;
}
async function saveRule() {
  await updatePromotionRule(editingRule); ruleModal.value = false; message.success('企业门槛已更新'); await loadPathRules();
}

onMounted(async () => {
  loading.value = true;
  try {
    const [ctx, users] = await Promise.all([getPromotionContext(), getPromotionEmployees()]);
    context.value = ctx; employees.value = users;
    selectedPath.value = ctx.paths[0] ? `${ctx.paths[0].fromLevel}|${ctx.paths[0].toLevel}` : undefined;
    await loadPathRules();
  } finally { loading.value = false; }
});
</script>

<template>
  <Page>
    <div class="flex flex-col gap-4 pb-4">
      <PageHeaderBar
        crumb="云培训学院 / 人才成长体系"
        title="晋级门槛与关联评估"
        description="依据岗位等级要求，综合评估学习成果、实操能力、工作表现和人才培养情况。"
      />

      <Alert show-icon type="info" message="系统将自动汇总已有业务数据；暂未记录的考核结果可在门槛列表中补充。" />

      <Card title="人员晋级档案" :bordered="false">
        <Row :gutter="16">
          <Col :span="7"><div class="mb-1 text-xs text-gray-500">选择人员</div><Select v-model:value="selectedUserId" show-search :options="employeeOptions" class="w-full" @change="chooseUser" /></Col>
          <Col :span="5"><div class="mb-1 text-xs text-gray-500">当前等级</div><Select v-model:value="profile.currentLevel" :options="context.levels" class="w-full" /></Col>
          <Col :span="5"><div class="mb-1 text-xs text-gray-500">进入本级日期</div><DatePicker v-model:value="profile.levelSince" class="w-full" /></Col>
          <Col :span="4"><div class="mb-1 text-xs text-gray-500">工种</div><Input v-model:value="profile.jobType" placeholder="例如：防腐工" /></Col>
          <Col :span="3" class="flex items-end"><Button type="primary" block @click="saveProfile">保存档案</Button></Col>
        </Row>
      </Card>

      <Row v-if="evaluation" :gutter="16">
        <Col :span="6"><Card :bordered="false"><Statistic title="当前等级" :value="evaluation.currentLevelName" /></Card></Col>
        <Col :span="6"><Card :bordered="false"><Statistic title="目标等级" :value="evaluation.nextLevelName || '最高等级'" /></Card></Col>
        <Col :span="6"><Card :bordered="false"><Statistic title="已达标项目" :value="`${evaluation.passedCount || 0}/${evaluation.totalCount || 0}`" /></Card></Col>
        <Col :span="6"><Card :bordered="false"><Progress type="circle" :percent="Number(evaluation.progress)" :size="82" /></Card></Col>
      </Row>

      <Card :bordered="false">
        <div class="mb-4 flex items-center gap-3">
          <Select v-model:value="selectedPath" :options="pathOptions" class="w-72" @change="loadPathRules" />
          <Button type="primary" :loading="loading" :disabled="!selectedUserId" @click="evaluate">执行晋级评估</Button>
          <Tag v-if="evaluation" :color="evaluation.qualified ? 'success' : 'warning'">{{ evaluation.qualified ? '全部硬性门槛已达标' : '尚有门槛未达标' }}</Tag>
        </div>
        <Table row-key="ruleKey" size="middle" :columns="columns" :data-source="displayedRules" :pagination="false">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'target'">{{ record.operator }} {{ record.ruleValue }}{{ record.unit }}</template>
            <template v-else-if="column.key === 'source'"><Tag>{{ context.sources[record.sourceModule] || record.sourceModule }}</Tag></template>
            <template v-else-if="column.key === 'actual'">{{ record.available ? `${record.actualValue}${record.unit || ''}` : '—' }}</template>
            <template v-else-if="column.key === 'result'"><Tag :color="record.passed ? 'success' : 'warning'">{{ resultText(record) }}</Tag></template>
            <template v-else-if="column.key === 'action'"><Button type="link" size="small" @click="openMetric(record)">录入结果</Button><Button type="link" size="small" @click="openRule(record)">调整门槛</Button></template>
          </template>
        </Table>
      </Card>
    </div>

    <Modal v-model:open="metricModal" title="录入考核结果" ok-text="保存" @ok="saveMetric">
      <Form layout="vertical"><FormItem label="考核项目"><Input :value="displayedRules.find((item) => item.ruleKey === metric.ruleKey)?.ruleName" disabled /></FormItem><FormItem label="考核结果"><InputNumber v-model:value="metric.metricValue" class="w-full" /></FormItem><FormItem label="数据来源"><Select v-model:value="metric.sourceModule" :options="Object.entries(context.sources).map(([value,label]) => ({ value, label }))" /></FormItem><FormItem label="记录说明"><Input v-model:value="metric.evidence" placeholder="填写记录编号或情况说明" /></FormItem></Form>
    </Modal>
    <Modal v-model:open="ruleModal" title="调整本企业门槛" ok-text="保存" @ok="saveRule">
      <Form layout="vertical"><FormItem label="比较符"><Select v-model:value="editingRule.operator" :options="['>=','<=','='].map(value => ({value,label:value}))" /></FormItem><FormItem label="目标值"><InputNumber v-model:value="editingRule.ruleValue" class="w-full" /></FormItem></Form>
    </Modal>
  </Page>
</template>
