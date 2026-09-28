<script lang="ts" setup>
import type { HrmPortalRecruitChatApi } from '#/api/hrm/portal/recruit/chat';
import type { HrmRecruitAgentApi } from '#/api/hrm/recruit/agent';

import { computed, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { analyzeConversationAiMatch } from '#/api/hrm/portal/recruit/chat';
import {
  buildAgentIdempotencyKey,
  getRecruitAgentTask,
} from '#/api/hrm/recruit/agent';

const props = defineProps<{
  conversation?: HrmPortalRecruitChatApi.Conversation;
  open: boolean;
}>();

const emit = defineEmits<{
  /** 产生了新的分析结果，父级需要刷新列表以更新匹配分徽标 */
  analyzed: [];
  'update:open': [value: boolean];
}>();

const modalOpen = computed({
  get: () => props.open,
  set: (value: boolean) => emit('update:open', value),
});

type Phase = 'failed' | 'idle' | 'running' | 'succeeded';

const phase = ref<Phase>('idle');
const task = ref<HrmRecruitAgentApi.TaskResp>();
const errorMessage = ref('');

const matchResult = computed<HrmRecruitAgentApi.ApplicationMatchResult | undefined>(
  () => task.value?.result as HrmRecruitAgentApi.ApplicationMatchResult | undefined,
);
const displayName = computed(
  () =>
    props.conversation?.candidateName ||
    props.conversation?.peerUserName ||
    '该求职者',
);
const modalTitle = computed(() => `${displayName.value} · AI 应聘匹配分析`);

/** 维度 key 是模型按提示词白名单输出的固定枚举 */
const DIMENSION_LABELS: Record<string, string> = {
  EDUCATION: '教育背景',
  EXPERIENCE: '工作经验',
  KEYWORD: '关键词匹配',
  RESPONSIBILITY: '岗位职责',
  SKILL: '专业技能',
};

function dimensionLabel(key: string) {
  return DIMENSION_LABELS[key] || key;
}

/** 与候选人详情页 AI 辅助保持同一套评分色调 */
function scoreTone(score?: number) {
  if (score === undefined || score === null) return 'mid';
  if (score >= 80) return 'good';
  if (score >= 60) return 'mid';
  return 'low';
}

function reset() {
  phase.value = 'idle';
  task.value = undefined;
  errorMessage.value = '';
}

async function loadTask(taskId: number) {
  const detail = await getRecruitAgentTask(taskId);
  if (detail?.status === 'SUCCEEDED') {
    task.value = detail;
    phase.value = 'succeeded';
    return;
  }
  errorMessage.value = detail?.errorMessage || '本次分析未完成，请稍后重试';
  phase.value = 'failed';
}

/** 重新跑一次：务必换新幂等键，否则服务端会命中上一次任务而不调用模型 */
async function runAnalysis() {
  const conversationId = props.conversation?.id;
  if (!conversationId || phase.value === 'running') return;
  reset();
  phase.value = 'running';
  try {
    const taskId = await analyzeConversationAiMatch({
      conversationId,
      idempotencyKey: buildAgentIdempotencyKey(),
    });
    await loadTask(taskId);
    if (phase.value === 'succeeded') emit('analyzed');
  } catch (error: any) {
    errorMessage.value = error?.msg || error?.message || 'AI 分析失败，请稍后重试';
    phase.value = 'failed';
  }
}

async function prepare() {
  reset();
  const conversationId = props.conversation?.id;
  if (!conversationId) return;
  if (!props.conversation?.applicationId) {
    errorMessage.value = '该会话尚未建立应聘记录，请先转为候选人后再分析';
    phase.value = 'failed';
    return;
  }
  // 已经分析过就直接读结果，避免重复消耗模型额度；要重跑请点「重新分析」
  if (props.conversation?.aiTaskId) {
    phase.value = 'running';
    try {
      await loadTask(props.conversation.aiTaskId);
    } catch (error: any) {
      errorMessage.value = error?.msg || error?.message || '读取分析结果失败';
      phase.value = 'failed';
    }
    return;
  }
  await runAnalysis();
}

watch(
  () => [props.open, props.conversation?.id],
  ([open]) => {
    if (open) void prepare();
  },
);
</script>

<template>
  <a-modal
    v-model:open="modalOpen"
    :title="modalTitle"
    width="880px"
    wrap-class-name="candidate-ai-match-modal"
  >
    <div v-if="phase === 'running'" class="ai-state">
      <a-spin />
      <span>AI 正在比对简历与岗位要求…</span>
    </div>

    <div v-else-if="phase === 'failed'" class="ai-state">
      <span class="ai-state-icon">
        <IconifyIcon icon="lucide:triangle-alert" />
      </span>
      <strong>{{ errorMessage }}</strong>
    </div>

    <template v-else-if="matchResult">
      <p class="ai-disclaimer">
        <IconifyIcon icon="lucide:info" />
        AI 辅助分析，仅供参考。结果不会自动淘汰、录用或改变候选人、应聘、面试与 Offer
        状态。
      </p>

      <header class="ai-score-row">
        <div class="ai-score" :class="scoreTone(matchResult.overallScore)">
          <strong>{{ matchResult.overallScore }}</strong>
          <small>匹配分</small>
        </div>
        <div class="ai-score-meta">
          <p>应聘岗位：{{ conversation?.postName || '招聘职位' }}</p>
          <p>
            模型：{{ task?.model || '—' }} · 提示词 {{ task?.promptVersion || '—' }}
          </p>
          <p v-if="task?.totalTokens">本次消耗 {{ task.totalTokens }} tokens</p>
        </div>
      </header>

      <section v-if="matchResult.dimensions?.length" class="ai-block">
        <h4>维度评分</h4>
        <div class="ai-dimensions">
          <article
            v-for="dimension in matchResult.dimensions"
            :key="dimension.key"
            class="ai-dimension"
          >
            <div class="ai-dimension-head">
              <span>{{ dimensionLabel(dimension.key) }}</span>
              <b :class="scoreTone(dimension.score)">{{ dimension.score }}</b>
            </div>
            <p>{{ dimension.comment }}</p>
            <ul v-if="dimension.evidence?.length">
              <li v-for="(item, index) in dimension.evidence" :key="index">
                <code>{{ item.field }}</code>{{ item.note }}
              </li>
            </ul>
          </article>
        </div>
      </section>

      <section class="ai-columns">
        <div class="ai-block">
          <h4>已满足 <em>{{ matchResult.matched?.length || 0 }}</em></h4>
          <p v-if="!matchResult.matched?.length" class="ai-empty">无</p>
          <ul v-else class="ai-list">
            <li v-for="(item, index) in matchResult.matched" :key="index">
              <strong>{{ item.point }}</strong>
              <span>{{ item.evidence }}</span>
            </li>
          </ul>
        </div>
        <div class="ai-block">
          <h4>未满足 <em>{{ matchResult.missing?.length || 0 }}</em></h4>
          <p v-if="!matchResult.missing?.length" class="ai-empty">无</p>
          <ul v-else class="ai-list">
            <li v-for="(item, index) in matchResult.missing" :key="index">
              <strong>{{ item.point }}</strong>
              <span>{{ item.evidence }}</span>
            </li>
          </ul>
        </div>
      </section>

      <section class="ai-columns">
        <div class="ai-block">
          <h4>风险点 <em>{{ matchResult.risks?.length || 0 }}</em></h4>
          <p v-if="!matchResult.risks?.length" class="ai-empty">无</p>
          <ul v-else class="ai-list plain">
            <li v-for="(item, index) in matchResult.risks" :key="index">
              <span>{{ item }}</span>
            </li>
          </ul>
        </div>
        <div class="ai-block">
          <h4>待核实 <em>{{ matchResult.toConfirm?.length || 0 }}</em></h4>
          <p v-if="!matchResult.toConfirm?.length" class="ai-empty">无</p>
          <ul v-else class="ai-list">
            <li v-for="(item, index) in matchResult.toConfirm" :key="index">
              <strong>{{ item.point }}</strong>
              <span>{{ item.reason }}</span>
            </li>
          </ul>
        </div>
      </section>
    </template>

    <template #footer>
      <a-button @click="modalOpen = false">关闭</a-button>
      <a-button
        type="primary"
        :loading="phase === 'running'"
        :disabled="!conversation?.applicationId"
        @click="runAnalysis"
      >
        {{ matchResult ? '重新分析' : '开始分析' }}
      </a-button>
    </template>
  </a-modal>
</template>

<style scoped>
.ai-state {
  display: grid;
  min-height: 220px;
  place-items: center;
  align-content: center;
  gap: 12px;
  color: #6b7a8c;
  font-size: 13px;
  text-align: center;
}

.ai-state-icon {
  display: grid;
  width: 48px;
  height: 48px;
  place-items: center;
  border-radius: 50%;
  background: #fdf0e6;
  color: #d97706;
  font-size: 22px;
}

.ai-state strong {
  max-width: 560px;
  color: #1f2d3d;
  font-size: 15px;
  font-weight: 500;
  line-height: 1.7;
}

.ai-disclaimer {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  margin: 0 0 14px;
  padding: 9px 12px;
  border: 1px solid #dbe8f6;
  border-radius: 8px;
  background: #f5faff;
  color: #5b7591;
  font-size: 12px;
  line-height: 1.7;
}

.ai-score-row {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 14px;
  padding: 18px 20px;
  border: 1px solid #e6eef7;
  border-radius: 10px;
  background: linear-gradient(120deg, #f7fbff, #eef5fd);
}

.ai-score {
  display: grid;
  width: 78px;
  height: 78px;
  flex: 0 0 auto;
  place-items: center;
  align-content: center;
  gap: 2px;
  border: 3px solid currentcolor;
  border-radius: 50%;
  background: #fff;
}

.ai-score strong {
  font-size: 26px;
  font-weight: 700;
  line-height: 1;
}

.ai-score small {
  font-size: 11px;
}

.ai-score.good {
  color: #16a34a;
}

.ai-score.mid {
  color: #d97706;
}

.ai-score.low {
  color: #dc2626;
}

.ai-score-meta p {
  margin: 0 0 4px;
  color: #5b6b7d;
  font-size: 13px;
}

.ai-score-meta p:last-child {
  margin-bottom: 0;
}

.ai-block {
  margin-bottom: 14px;
  padding: 14px 16px;
  border: 1px solid #eaeff5;
  border-radius: 10px;
  background: #fff;
}

.ai-block h4 {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 0 0 12px;
  color: #1f2d3d;
  font-size: 14px;
  font-weight: 600;
}

.ai-block h4 em {
  padding: 1px 7px;
  border-radius: 999px;
  background: #eef3f9;
  color: #6b7a8c;
  font-size: 11px;
  font-style: normal;
  font-weight: 500;
}

.ai-dimensions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.ai-dimension {
  padding: 11px 13px;
  border: 1px solid #f0f4f9;
  border-radius: 8px;
  background: #fafcfe;
}

.ai-dimension-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 6px;
  color: #2f5d73;
  font-size: 13px;
  font-weight: 600;
}

.ai-dimension-head b {
  font-size: 16px;
  font-weight: 700;
}

.ai-dimension-head b.good,
.ai-dimension b.good {
  color: #16a34a;
}

.ai-dimension-head b.mid,
.ai-dimension b.mid {
  color: #d97706;
}

.ai-dimension-head b.low,
.ai-dimension b.low {
  color: #dc2626;
}

.ai-dimension p {
  margin: 0 0 6px;
  color: #4e5d6e;
  font-size: 13px;
  line-height: 1.7;
}

.ai-dimension ul {
  margin: 0;
  padding-left: 16px;
  color: #8a99ab;
  font-size: 12px;
  line-height: 1.7;
}

.ai-dimension code {
  margin-right: 5px;
  padding: 1px 5px;
  border-radius: 4px;
  background: #eef3f9;
  color: #5b7591;
  font-size: 11px;
}

.ai-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.ai-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.ai-list li {
  display: grid;
  gap: 2px;
  padding: 8px 0;
  border-top: 1px dashed #eef2f7;
}

.ai-list li:first-child {
  padding-top: 0;
  border-top: none;
}

.ai-list strong {
  color: #1f2d3d;
  font-size: 13px;
  font-weight: 600;
}

.ai-list span {
  color: #6b7a8c;
  font-size: 12px;
  line-height: 1.7;
}

.ai-list.plain span {
  color: #b45309;
  font-size: 13px;
}

.ai-empty {
  margin: 0;
  color: #9aa8b6;
  font-size: 13px;
}

@media (max-width: 700px) {
  .ai-dimensions,
  .ai-columns {
    grid-template-columns: 1fr;
  }
}
</style>
