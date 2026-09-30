<script lang="ts" setup>
/**
 * 候选人 AI 辅助面板（A07 / A08 / A09）。
 *
 * 三个场景都以「应聘记录」为入口：简历取该应聘记录关联的候选人快照，
 * 因此切换应聘记录时结果会清空，避免把上一个岗位的结论张冠李戴。
 *
 * 只展示真实结果：模型未配置、额度不足或调用失败时给出明确原因，
 * 不生成任何模拟评分或编造结论；面板也不会改变候选人 / 应聘的任何状态。
 */
import { computed, onMounted, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { message } from 'ant-design-vue';

import {
  buildAgentIdempotencyKey,
  getRecruitAgentTask,
  submitApplicationMatch,
  submitCandidateSummary,
  submitInterviewQuestion,
  type HrmRecruitAgentApi,
} from '#/api/hrm/recruit/agent';
import {
  getRecruitApplicationPage,
  type HrmRecruitApplicationApi,
} from '#/api/hrm/recruit/application';

const props = defineProps<{ candidateId: number }>();

type Scene = 'match' | 'question' | 'summary';
type Phase = 'failed' | 'idle' | 'running' | 'succeeded';

const SCENES: Array<{ key: Scene; label: string; tip: string }> = [
  { key: 'summary', label: '候选人摘要', tip: '概括核心技能、工作年限与主要经历' },
  { key: 'match', label: '岗位匹配', tip: '按应聘职位给出优势、缺口、风险与待核实项' },
  { key: 'question', label: '面试问题', tip: '按专业、项目、岗位能力与核实四类生成问题' },
];

const DIMENSION_LABELS: Record<string, string> = {
  SKILL: '技能',
  EXPERIENCE: '经验',
  EDUCATION: '学历',
  RESPONSIBILITY: '职责',
  KEYWORD: '关键词',
};

const CATEGORY_LABELS: Record<string, string> = {
  PROFESSIONAL: '专业类',
  PROJECT: '项目类',
  COMPETENCY: '岗位能力类',
  VERIFICATION: '核实类',
};

const scene = ref<Scene>('summary');
const phase = ref<Phase>('idle');
const loadingApplications = ref(false);
const applications = ref<HrmRecruitApplicationApi.Application[]>([]);
const selectedApplicationId = ref<number>();
const taskResult = ref<HrmRecruitAgentApi.TaskResp>();
const errorMessage = ref('');

const currentScene = computed(
  () => SCENES.find((item) => item.key === scene.value)!,
);

const selectedApplication = computed(() =>
  applications.value.find((item) => item.id === selectedApplicationId.value),
);

const summaryResult = computed(() =>
  scene.value === 'summary'
    ? (taskResult.value?.result as HrmRecruitAgentApi.CandidateSummaryResult)
    : undefined,
);

const matchResult = computed(() =>
  scene.value === 'match'
    ? (taskResult.value?.result as HrmRecruitAgentApi.ApplicationMatchResult)
    : undefined,
);

const questionResult = computed(() =>
  scene.value === 'question'
    ? (taskResult.value?.result as HrmRecruitAgentApi.InterviewQuestionResult)
    : undefined,
);

const canSubmit = computed(
  () => Boolean(selectedApplicationId.value) && phase.value !== 'running',
);

async function loadApplications() {
  loadingApplications.value = true;
  try {
    const result = await getRecruitApplicationPage({
      candidateId: props.candidateId,
      pageNo: 1,
      pageSize: 100,
    });
    applications.value = result.list || [];
    if (!selectedApplicationId.value && applications.value.length > 0) {
      selectedApplicationId.value = applications.value[0]!.id;
    }
  } catch (error) {
    console.warn('[RecruitAgent] 加载应聘记录失败', error);
    applications.value = [];
  } finally {
    loadingApplications.value = false;
  }
}

async function submit() {
  if (!canSubmit.value) return;
  phase.value = 'running';
  errorMessage.value = '';
  taskResult.value = undefined;
  const payload = {
    applicationId: selectedApplicationId.value!,
    // 每次提交都用新键：重新分析必须真的重新调用模型，而不是复用上一次的任务
    idempotencyKey: buildAgentIdempotencyKey(),
  };
  try {
    let taskId: number;
    if (scene.value === 'summary') {
      taskId = await submitCandidateSummary(payload);
    } else if (scene.value === 'match') {
      taskId = await submitApplicationMatch(payload);
    } else {
      taskId = await submitInterviewQuestion(payload);
    }
    const detail = await getRecruitAgentTask(taskId);
    if (detail?.status === 'SUCCEEDED') {
      taskResult.value = detail;
      phase.value = 'succeeded';
      return;
    }
    errorMessage.value = detail?.errorMessage || '本次分析未完成，请稍后重试';
    phase.value = 'failed';
  } catch (error: any) {
    errorMessage.value = error?.message || '提交失败，请稍后重试';
    phase.value = 'failed';
    message.warning(errorMessage.value);
  }
}

function reset() {
  phase.value = 'idle';
  taskResult.value = undefined;
  errorMessage.value = '';
}

function switchScene(next: Scene) {
  if (scene.value === next) return;
  scene.value = next;
  reset();
}

/** 切换应聘记录必须清空结果，否则会把上一个岗位的结论显示在新岗位下 */
function handleApplicationChange() {
  reset();
}

function dimensionLabel(key: string) {
  return DIMENSION_LABELS[key] || key;
}

function categoryLabel(category: string) {
  return CATEGORY_LABELS[category] || category;
}

function scoreTone(score?: number) {
  if (score === undefined || score === null) return 'mid';
  if (score >= 80) return 'good';
  if (score >= 60) return 'mid';
  return 'low';
}

onMounted(loadApplications);
</script>

<template>
  <div class="agent-panel">
    <p class="agent-disclaimer">
      <IconifyIcon icon="lucide:info" />
      <span>
        AI 辅助分析，仅供参考。结果不会自动淘汰、录用或改变候选人、应聘、面试与 Offer 状态。
      </span>
    </p>

    <div class="agent-tabs" role="tablist">
      <button
        v-for="item in SCENES"
        :key="item.key"
        type="button"
        role="tab"
        :aria-selected="scene === item.key"
        :class="{ active: scene === item.key }"
        @click="switchScene(item.key)"
      >
        {{ item.label }}
      </button>
    </div>
    <p class="agent-tip">{{ currentScene.tip }}</p>

    <label class="agent-field">
      <span>分析对象（应聘记录）</span>
      <a-select
        v-model:value="selectedApplicationId"
        :loading="loadingApplications"
        placeholder="请选择一条应聘记录"
        :disabled="phase === 'running'"
        @change="handleApplicationChange"
      >
        <a-select-option
          v-for="item in applications"
          :key="item.id"
          :value="item.id"
        >
          {{ item.postName || `职位 #${item.postId}` }}
          {{ item.statusName ? `（${item.statusName}）` : '' }}
        </a-select-option>
      </a-select>
    </label>

    <p v-if="!loadingApplications && applications.length === 0" class="agent-hint warn">
      该候选人还没有应聘记录。AI 摘要、匹配与面试问题都以应聘记录为入口，请先建立应聘记录。
    </p>
    <p
      v-else-if="selectedApplication && !selectedApplication.resumeId"
      class="agent-hint warn"
    >
      该应聘记录没有关联简历，无法分析。
    </p>

    <div class="agent-actions">
      <a-button
        type="primary"
        :loading="phase === 'running'"
        :disabled="!canSubmit"
        @click="submit"
      >
        {{
          phase === 'running'
            ? '正在分析…'
            : phase === 'succeeded'
              ? '重新分析'
              : '开始分析'
        }}
      </a-button>
      <a-button v-if="phase === 'succeeded'" @click="reset">清空结果</a-button>
    </div>

    <!-- 分析进行中：结果区先给骨架屏占位，不要让用户只看到一个转圈的按钮（阶段四 4.5） -->
    <section v-if="phase === 'running'" aria-busy="true" class="agent-skeleton">
      <a-skeleton active :paragraph="{ rows: 5 }" />
    </section>

    <section v-if="phase === 'failed'" class="agent-failed">
      <IconifyIcon icon="lucide:triangle-alert" />
      <strong>本次分析未完成</strong>
      <p>{{ errorMessage }}</p>
    </section>

    <!-- A07 候选人摘要 -->
    <section
      v-else-if="scene === 'summary' && phase === 'succeeded' && summaryResult"
      class="agent-result"
    >
      <div class="agent-meta">
        <span v-if="taskResult?.model">模型：{{ taskResult.model }}</span>
        <span v-if="taskResult?.promptVersion">
          提示词版本：{{ taskResult.promptVersion }}
        </span>
        <span v-if="taskResult?.totalTokens">
          调用量：{{ taskResult.totalTokens }} tokens
        </span>
      </div>

      <div class="agent-kv">
        <div>
          <span>工作年限</span>
          <strong>{{ summaryResult.workYears }}</strong>
        </div>
        <div>
          <span>主要经历</span>
          <strong>{{ summaryResult.experienceSummary }}</strong>
        </div>
      </div>

      <div v-if="summaryResult.coreSkills?.length" class="agent-block">
        <strong>核心技能</strong>
        <ul>
          <li v-for="(item, index) in summaryResult.coreSkills" :key="index">
            {{ item }}
          </li>
        </ul>
      </div>

      <div v-if="summaryResult.achievements?.length" class="agent-block">
        <strong>主要成果</strong>
        <ul>
          <li v-for="(item, index) in summaryResult.achievements" :key="index">
            {{ item }}
          </li>
        </ul>
      </div>

      <div v-if="summaryResult.facts?.length" class="agent-block is-fact">
        <strong>简历事实</strong>
        <ul>
          <li v-for="(item, index) in summaryResult.facts" :key="index">
            <code>{{ item.field }}</code>
            <span>{{ item.note }}</span>
          </li>
        </ul>
      </div>

      <div v-if="summaryResult.missingInfo?.length" class="agent-block is-warn">
        <strong>缺失信息</strong>
        <ul>
          <li v-for="(item, index) in summaryResult.missingInfo" :key="index">
            {{ item }}
          </li>
        </ul>
      </div>

      <div v-if="summaryResult.inferences?.length" class="agent-block is-infer">
        <strong>AI 推断（需向候选人核实）</strong>
        <ul>
          <li v-for="(item, index) in summaryResult.inferences" :key="index">
            {{ item }}
          </li>
        </ul>
      </div>
    </section>

    <!-- A08 候选人与职位匹配 -->
    <section
      v-else-if="scene === 'match' && phase === 'succeeded' && matchResult"
      class="agent-result"
    >
      <div class="agent-score">
        <div class="agent-score-value" :class="`is-${scoreTone(matchResult.overallScore)}`">
          {{ matchResult.overallScore }}
        </div>
        <div class="agent-score-meta">
          <strong>匹配度</strong>
          <small v-if="taskResult?.model">模型：{{ taskResult.model }}</small>
          <small v-if="taskResult?.promptVersion">
            提示词版本：{{ taskResult.promptVersion }}
          </small>
        </div>
      </div>

      <div class="agent-dimensions">
        <article v-for="item in matchResult.dimensions" :key="item.key">
          <header>
            <strong>{{ dimensionLabel(item.key) }}</strong>
            <span :class="`is-${scoreTone(item.score)}`">{{ item.score }}</span>
          </header>
          <p>{{ item.comment }}</p>
          <ul class="agent-evidence">
            <li v-for="(evidence, index) in item.evidence" :key="index">
              <code>{{ evidence.field }}</code>
              <span>{{ evidence.note }}</span>
            </li>
          </ul>
        </article>
      </div>

      <div class="agent-lists">
        <div v-if="matchResult.matched?.length" class="agent-block is-good">
          <strong>优势（{{ matchResult.matched.length }}）</strong>
          <ul>
            <li v-for="(item, index) in matchResult.matched" :key="index">
              {{ item.point }}
              <small>{{ item.evidence }}</small>
            </li>
          </ul>
        </div>
        <div v-if="matchResult.missing?.length" class="agent-block is-risk">
          <strong>缺口（{{ matchResult.missing.length }}）</strong>
          <ul>
            <li v-for="(item, index) in matchResult.missing" :key="index">
              {{ item.point }}
              <small>{{ item.evidence }}</small>
            </li>
          </ul>
        </div>
        <div v-if="matchResult.risks?.length" class="agent-block is-risk">
          <strong>风险点（{{ matchResult.risks.length }}）</strong>
          <ul>
            <li v-for="(item, index) in matchResult.risks" :key="index">
              {{ item }}
            </li>
          </ul>
        </div>
        <div v-if="matchResult.toConfirm?.length" class="agent-block is-confirm">
          <strong>待核实（{{ matchResult.toConfirm.length }}）</strong>
          <ul>
            <li v-for="(item, index) in matchResult.toConfirm" :key="index">
              {{ item.point }}
              <small>{{ item.reason }}</small>
            </li>
          </ul>
        </div>
      </div>
    </section>

    <!-- A09 面试问题建议 -->
    <section
      v-else-if="scene === 'question' && phase === 'succeeded' && questionResult"
      class="agent-result"
    >
      <div class="agent-meta">
        <span v-if="taskResult?.model">模型：{{ taskResult.model }}</span>
        <span v-if="taskResult?.promptVersion">
          提示词版本：{{ taskResult.promptVersion }}
        </span>
      </div>

      <p
        v-if="(questionResult.questions?.length || 0) === 0"
        class="agent-hint"
      >
        本次没有生成问题，可稍后重新分析。
      </p>

      <article
        v-for="(item, index) in questionResult.questions"
        :key="index"
        class="agent-question"
      >
        <header>
          <span class="agent-question-category">{{ categoryLabel(item.category) }}</span>
          <strong>{{ item.question }}</strong>
        </header>
        <p><span class="agent-question-label">提问目的</span>{{ item.purpose }}</p>
        <p><span class="agent-question-label">关注点</span>{{ item.focus }}</p>
      </article>

      <p class="agent-footer-note">
        问题只作面试参考，不会自动写入面试评价；请勿询问与岗位能力无关的个人信息。
      </p>
    </section>
  </div>
</template>

<style scoped>
.agent-panel {
  display: grid;
  gap: 14px;
}

.agent-disclaimer {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  padding: 10px 12px;
  border-radius: 10px;
  color: hsl(var(--foreground));
  /* 问题 7：跟随主题（暗色下不再是一块浅绿色底） */
  background: color-mix(in srgb, hsl(var(--primary)) 10%, hsl(var(--card)));
  font-size: 13px;
  line-height: 1.6;
}

.agent-tabs {
  display: flex;
  gap: 6px;
  padding: 4px;
  border-radius: 10px;
  background: hsl(var(--muted));
}

.agent-tabs button {
  flex: 1;
  padding: 8px 12px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: hsl(var(--muted-foreground));
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  transition: background 160ms ease, color 160ms ease;
}

.agent-tabs button.active {
  color: hsl(var(--primary));
  background: hsl(var(--card));
  box-shadow: 0 2px 8px rgb(23 52 61 / 8%);
}

.agent-tip {
  margin: 0;
  color: hsl(var(--muted-foreground));
  font-size: 12px;
}

.agent-field {
  display: grid;
  gap: 8px;
  color: hsl(var(--foreground));
  font-size: 13px;
  font-weight: 600;
}

.agent-hint {
  margin: 0;
  color: hsl(var(--muted-foreground));
  font-size: 13px;
  line-height: 1.6;
}

.agent-hint.warn {
  padding: 10px 12px;
  border-radius: 8px;
  color: hsl(var(--warning));
  background: color-mix(in srgb, hsl(var(--warning)) 14%, transparent);
}

.agent-actions {
  display: flex;
  gap: 10px;
}

.agent-failed {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 24px 16px;
  border: 1px dashed color-mix(in srgb, hsl(var(--warning)) 45%, transparent);
  border-radius: 12px;
  background: hsl(var(--card));
  text-align: center;
}

.agent-failed > :deep(svg) {
  color: hsl(var(--warning));
  font-size: 24px;
}

.agent-failed strong {
  color: hsl(var(--foreground));
  font-size: 15px;
}

.agent-failed p {
  margin: 0;
  color: hsl(var(--muted-foreground));
  font-size: 13px;
  line-height: 1.7;
}

.agent-result {
  display: grid;
  gap: 12px;
}

.agent-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  color: hsl(var(--muted-foreground));
  font-size: 12px;
}

.agent-kv {
  display: grid;
  grid-template-columns: minmax(0, 160px) minmax(0, 1fr);
  gap: 12px;
}

.agent-kv > div {
  display: grid;
  gap: 4px;
  padding: 12px 14px;
  border: 1px solid hsl(var(--border));
  border-radius: 10px;
  background: hsl(var(--card));
}

.agent-kv span {
  color: hsl(var(--muted-foreground));
  font-size: 12px;
}

.agent-kv strong {
  color: hsl(var(--foreground));
  font-size: 13px;
  font-weight: 600;
  line-height: 1.7;
}

.agent-block {
  padding: 12px 14px;
  border: 1px solid hsl(var(--border));
  border-radius: 10px;
  background: hsl(var(--card));
}

.agent-block strong {
  display: block;
  margin-bottom: 6px;
  color: hsl(var(--foreground));
  font-size: 13px;
}

.agent-block ul {
  display: grid;
  gap: 6px;
  margin: 0;
  padding-left: 16px;
  color: hsl(var(--foreground));
  font-size: 12px;
  line-height: 1.7;
}

.agent-block small {
  display: block;
  color: hsl(var(--muted-foreground));
  font-size: 11px;
  line-height: 1.5;
}

.agent-block code {
  margin-right: 6px;
  padding: 0 5px;
  border-radius: 4px;
  color: hsl(var(--primary));
  background: hsl(var(--muted));
  font-size: 11px;
}

.agent-block.is-good {
  border-color: color-mix(in srgb, hsl(var(--success)) 40%, transparent);
}

.agent-block.is-good strong {
  color: hsl(var(--success));
}

.agent-block.is-warn {
  border-color: color-mix(in srgb, hsl(var(--warning)) 40%, transparent);
}

.agent-block.is-warn strong {
  color: hsl(var(--warning));
}

.agent-block.is-risk {
  border-color: color-mix(in srgb, hsl(var(--destructive)) 40%, transparent);
}

.agent-block.is-risk strong {
  color: hsl(var(--destructive));
}

.agent-block.is-confirm {
  border-color: color-mix(in srgb, hsl(var(--primary)) 40%, transparent);
}

.agent-block.is-confirm strong {
  color: hsl(var(--primary));
}

.agent-block.is-fact {
  border-color: color-mix(in srgb, hsl(var(--primary)) 40%, transparent);
}

.agent-block.is-fact strong {
  color: hsl(var(--primary));
}

.agent-block.is-infer {
  border-color: color-mix(in srgb, #6d28d9 40%, transparent);
}

.agent-block.is-infer strong {
  color: color-mix(in srgb, #6d28d9 80%, hsl(var(--foreground)));
}

.agent-score {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 18px;
  border: 1px solid hsl(var(--border));
  border-radius: 12px;
  background: hsl(var(--card));
}

.agent-score-value {
  display: grid;
  width: 66px;
  height: 66px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 50%;
  font-size: 26px;
  font-weight: 700;
}

.agent-score-value.is-good {
  color: hsl(var(--success));
  background: color-mix(in srgb, hsl(var(--success)) 16%, transparent);
}

.agent-score-value.is-mid {
  color: color-mix(in srgb, #0e7490 75%, hsl(var(--foreground)));
  background: color-mix(in srgb, #0e7490 16%, transparent);
}

.agent-score-value.is-low {
  color: hsl(var(--warning));
  background: color-mix(in srgb, hsl(var(--warning)) 18%, transparent);
}

.agent-score-meta {
  display: grid;
  gap: 4px;
}

.agent-score-meta strong {
  color: hsl(var(--foreground));
  font-size: 15px;
}

.agent-score-meta small {
  color: hsl(var(--muted-foreground));
  font-size: 12px;
}

.agent-dimensions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.agent-dimensions article {
  display: grid;
  gap: 8px;
  padding: 14px;
  border: 1px solid hsl(var(--border));
  border-radius: 12px;
  background: hsl(var(--card));
}

.agent-dimensions article header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.agent-dimensions article header strong {
  color: hsl(var(--foreground));
  font-size: 14px;
}

.agent-dimensions article header span {
  font-size: 18px;
  font-weight: 700;
}

.agent-dimensions article header span.is-good {
  color: hsl(var(--success));
}

.agent-dimensions article header span.is-mid {
  color: color-mix(in srgb, #0e7490 75%, hsl(var(--foreground)));
}

.agent-dimensions article header span.is-low {
  color: hsl(var(--warning));
}

.agent-dimensions article p {
  margin: 0;
  color: hsl(var(--foreground));
  font-size: 13px;
  line-height: 1.6;
}

.agent-evidence {
  display: grid;
  gap: 5px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.agent-evidence li {
  display: flex;
  gap: 7px;
  color: hsl(var(--muted-foreground));
  font-size: 12px;
  line-height: 1.6;
}

.agent-evidence code {
  flex: 0 0 auto;
  padding: 0 5px;
  border-radius: 4px;
  color: hsl(var(--primary));
  background: hsl(var(--muted));
  font-size: 11px;
}

.agent-lists {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.agent-question {
  display: grid;
  gap: 6px;
  padding: 14px;
  border: 1px solid hsl(var(--border));
  border-radius: 10px;
  background: hsl(var(--card));
}

.agent-question header {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.agent-question-category {
  flex: 0 0 auto;
  padding: 1px 8px;
  border-radius: 999px;
  color: hsl(var(--primary));
  background: hsl(var(--muted));
  font-size: 12px;
}

.agent-question header strong {
  color: hsl(var(--foreground));
  font-size: 14px;
  line-height: 1.6;
}

.agent-question p {
  margin: 0;
  color: hsl(var(--foreground));
  font-size: 13px;
  line-height: 1.7;
}

.agent-question-label {
  margin-right: 6px;
  color: hsl(var(--muted-foreground));
  font-size: 12px;
}

.agent-footer-note {
  margin: 0;
  color: hsl(var(--muted-foreground));
  font-size: 12px;
  text-align: center;
}

@media (max-width: 640px) {
  .agent-dimensions,
  .agent-lists,
  .agent-kv {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
