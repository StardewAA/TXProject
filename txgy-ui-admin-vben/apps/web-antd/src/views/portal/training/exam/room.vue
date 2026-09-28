<script lang="ts" setup>
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { Button, Modal, Progress, Tag, message } from 'ant-design-vue';

import {
  getExamResult,
  saveExamAnswer,
  startQualificationExam,
  submitExam,
} from './api';

import type { TrainingExamApi } from './api';

defineOptions({ name: 'PortalTrainingExamRoom' });

const route = useRoute();
const router = useRouter();

const loading = ref(false);
const submitting = ref(false);
const error = ref('');
const attempt = ref<TrainingExamApi.Attempt | null>(null);
const result = ref<TrainingExamApi.Result | null>(null);

/** 已作答内容：题号 → 答案（客观题为选项数组，主观题为文本） */
const answers = ref<Record<number, unknown>>({});
const leftSeconds = ref(0);
let timer: ReturnType<typeof setInterval> | undefined;

const TYPE_NAME: Record<string, string> = {
  single: '单选题',
  multi: '多选题',
  judge: '判断题',
  essay: '实操问答',
  case: '案例分析题',
};

const questions = computed(() => attempt.value?.questions ?? []);
const totalScore = computed(() =>
  questions.value.reduce((sum, q) => sum + (q.score ?? 0), 0),
);
const answeredCount = computed(
  () =>
    questions.value.filter((q) => {
      const v = answers.value[q.id];
      return Array.isArray(v) ? v.length > 0 : Boolean(v);
    }).length,
);

const timeText = computed(() => {
  const s = Math.max(0, leftSeconds.value);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(
    sec,
  ).padStart(2, '0')}`;
});

/** Python 返回的是 UTC 朴素时间，按 UTC 解析 */
function parseUtc(value?: string) {
  if (!value) return 0;
  return Date.parse(`${value.replace(' ', 'T')}Z`) || 0;
}

function startCountdown() {
  const deadline = parseUtc(attempt.value?.deadline_at);
  const offset = parseUtc(attempt.value?.server_now) - Date.now();
  const tick = () => {
    leftSeconds.value = Math.floor((deadline - Date.now() - offset) / 1000);
    if (leftSeconds.value <= 0) {
      stopCountdown();
      submit(true);
    }
  };
  tick();
  timer = setInterval(tick, 1000);
}

function stopCountdown() {
  if (timer) clearInterval(timer);
  timer = undefined;
}

/** 载入已有答卷（刷新页面后恢复作答） */
function loadAnswers() {
  const map: Record<number, unknown> = {};
  for (const [key, value] of Object.entries(attempt.value?.answers ?? {})) {
    map[Number(key)] = value;
  }
  answers.value = map;
}

async function startExam() {
  const workType = String(route.query.job ?? '');
  const level = String(route.query.level ?? '');
  if (!workType || !level) {
    error.value = '缺少工种或等级参数，无法开始考试';
    return;
  }
  await doStart(workType, level);
}

async function doStart(workType: string, level: string) {
  loading.value = true;
  error.value = '';
  try {
    attempt.value = await startQualificationExam({
      work_type: workType,
      level,
      duration_min: 90,
    });
    loadAnswers();
    startCountdown();
  } catch (e: any) {
    error.value = e?.message || '考试启动失败，请稍后重试';
  } finally {
    loading.value = false;
  }
}

function isObjective(q: TrainingExamApi.Question) {
  return ['single', 'multi', 'judge'].includes(q.q_type);
}

function picked(q: TrainingExamApi.Question, key: string) {
  const value = answers.value[q.id];
  return Array.isArray(value) && value.includes(key);
}

async function choose(q: TrainingExamApi.Question, key: string) {
  const value = answers.value[q.id];
  let next: unknown;
  if (q.q_type === 'single') {
    next = [key];
  } else if (Array.isArray(value)) {
    next = value.includes(key) ? value.filter((x) => x !== key) : [...value, key];
  } else {
    next = [key];
  }
  answers.value[q.id] = next;
  await persist(q, next);
}

async function chooseJudge(q: TrainingExamApi.Question, value: boolean) {
  const next = [value];
  answers.value[q.id] = next;
  await persist(q, next);
}

/** 主观题防抖保存：输入即入状态（避免重渲染丢字），800ms 后落库，失焦立即落库 */
const textSaveTimers = new Map<number, ReturnType<typeof setTimeout>>();
const textDirty = new Set<number>();

function onText(q: TrainingExamApi.Question, value: string) {
  answers.value[q.id] = value;
  textDirty.add(q.id);
  const timer = textSaveTimers.get(q.id);
  if (timer) clearTimeout(timer);
  textSaveTimers.set(
    q.id,
    setTimeout(() => persistText(q), 800),
  );
}

async function persistText(q: TrainingExamApi.Question) {
  const timer = textSaveTimers.get(q.id);
  if (timer) {
    clearTimeout(timer);
    textSaveTimers.delete(q.id);
  }
  if (!textDirty.has(q.id) || !attempt.value) return;
  try {
    await saveExamAnswer(attempt.value.attempt_id, q.id, answers.value[q.id]);
    textDirty.delete(q.id);
  } catch (e: any) {
    message.warning(e?.message || '答案保存失败');
  }
}

/** 交卷前把尚未落库的主观题答案全部冲刷 */
async function flushPendingText() {
  for (const q of questions.value) {
    if (textDirty.has(q.id)) await persistText(q);
  }
}

async function persist(q: TrainingExamApi.Question, value: unknown) {
  if (!attempt.value) return;
  try {
    await saveExamAnswer(attempt.value.attempt_id, q.id, value);
  } catch (e: any) {
    message.warning(e?.message || '答案保存失败');
  }
}

async function submit(auto = false) {
  if (!attempt.value || submitting.value) return;
  if (!auto) {
    const unanswered = questions.value.length - answeredCount.value;
    if (unanswered > 0) {
      const ok = await new Promise<boolean>((resolve) => {
        Modal.confirm({
          title: '确认交卷',
          content: `还有 ${unanswered} 题未作答，确认现在交卷吗？`,
          okText: '确认交卷',
          cancelText: '继续答题',
          onOk: () => resolve(true),
          onCancel: () => resolve(false),
        });
      });
      if (!ok) return;
    }
  }
  submitting.value = true;
  try {
    await flushPendingText();
    await submitExam(attempt.value.attempt_id);
    stopCountdown();
    result.value = await getExamResult(attempt.value.attempt_id);
    message.success(
      auto
        ? '考试时间到，已自动交卷'
        : result.value?.status === 'graded'
          ? '交卷成功，已完成阅卷'
          : '交卷成功，主观题批阅中',
    );
  } catch (e: any) {
    message.error(e?.message || '交卷失败，请稍后重试');
  } finally {
    submitting.value = false;
  }
}

/** 点击答题卡题号：定位到对应题目（右侧题目区独立滚动） */
function scrollToQuestion(id: number) {
  document
    .getElementById(`q-${id}`)
    ?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function goBack() {
  const job = String(route.query.job ?? attempt.value?.work_type ?? '');
  router.push({
    path: '/portal/training/exam/booking',
    query: { job },
  });
}

onMounted(async () => {
  const attemptId = String(route.query.attempt ?? '');
  if (attemptId) {
    // 已有答卷：优先查看成绩，未批阅/进行中则进入答题
    try {
      const data = await getExamResult(attemptId);
      if (data.status === 'graded') {
        result.value = data;
        return;
      }
    } catch {
      // 未批阅则继续答题
    }
  }
  await startExam();
});

onUnmounted(() => {
  stopCountdown();
  for (const timer of textSaveTimers.values()) clearTimeout(timer);
  textSaveTimers.clear();
});
</script>

<template>
  <Page auto-content-height>
    <div class="exam-room">
      <div v-if="loading" class="state">试卷生成中…</div>

      <div v-else-if="error" class="state state--error">
        <p>{{ error }}</p>
        <Button type="primary" @click="goBack">返回认证考试</Button>
      </div>

      <!-- 成绩 -->
      <section v-else-if="result" class="panel result-panel">
        <h2>考试成绩</h2>
        <div class="score-row">
          <div class="score">
            <b>{{ result.total_score ?? 0 }}</b>
            <span>/ {{ totalScore || 100 }} 分</span>
          </div>
          <Tag :color="result.is_pass ? 'green' : 'red'">
            {{ result.is_pass ? '通过' : '未通过' }}
          </Tag>
          <span class="score-meta">
            客观题 {{ result.objective_score ?? 0 }} 分 · 主观题
            {{ result.subjective_score ?? 0 }} 分
          </span>
        </div>
        <p v-if="result.status !== 'graded'" class="pending">
          主观题批阅中，批阅完成后可查看最终成绩。
        </p>
        <div class="result-actions">
          <Button type="primary" @click="goBack">返回认证考试</Button>
        </div>
      </section>

      <!-- 答题 -->
      <template v-else-if="attempt">
        <header class="exam-head">
          <div>
            <h2>{{ attempt.paper || '工种资格认证考试' }}</h2>
            <p class="sub">
              {{ attempt.work_type }} · {{ attempt.level }} · 共
              {{ questions.length }} 题 · 满分 {{ totalScore }} 分 · 合格
              {{ attempt.pass_score ?? 60 }} 分
            </p>
          </div>
          <div class="exam-timer">
            <span class="label">剩余时间</span>
            <b :class="{ danger: leftSeconds < 300 }">{{ timeText }}</b>
          </div>
        </header>

        <div class="exam-body">
          <aside class="sheet">
            <div class="sheet-head">
              <span>答题卡</span>
              <span class="sheet-progress">
                已答 {{ answeredCount }}/{{ questions.length }}
              </span>
            </div>
            <Progress :percent="Math.round((answeredCount / Math.max(1, questions.length)) * 100)" size="small" />
            <div class="sheet-grid">
              <button
                v-for="(q, i) in questions"
                :key="q.id"
                type="button"
                class="sheet-no"
                :class="{
                  done:
                    Array.isArray(answers[q.id])
                      ? (answers[q.id] as unknown[]).length > 0
                      : Boolean(answers[q.id]),
                }"
                @click="scrollToQuestion(q.id)"
              >
                {{ i + 1 }}
              </button>
            </div>
            <Button
              class="submit-btn"
              type="primary"
              block
              :loading="submitting"
              @click="submit(false)"
            >
              提交答卷
            </Button>
          </aside>

          <div class="questions">
            <div
              v-for="(q, i) in questions"
              :id="`q-${q.id}`"
              :key="q.id"
              class="question"
            >
              <div class="q-head">
                <span class="q-no">第 {{ i + 1 }} 题</span>
                <Tag color="blue">{{ TYPE_NAME[q.q_type] || q.q_type }}</Tag>
                <span class="q-score">{{ q.score }} 分</span>
              </div>
              <div class="q-stem">{{ q.stem }}</div>

              <div v-if="q.q_type === 'judge'" class="q-options">
                <Button
                  :type="answers[q.id]?.[0] === true ? 'primary' : 'default'"
                  size="small"
                  @click="chooseJudge(q, true)"
                >
                  正确
                </Button>
                <Button
                  :type="answers[q.id]?.[0] === false ? 'primary' : 'default'"
                  size="small"
                  @click="chooseJudge(q, false)"
                >
                  错误
                </Button>
              </div>

              <div v-else-if="isObjective(q)" class="q-options">
                <div
                  v-for="opt in q.options ?? []"
                  :key="opt.key"
                  class="q-option"
                  :class="{ on: picked(q, opt.key) }"
                  @click="choose(q, opt.key)"
                >
                  <span class="opt-key">{{ opt.key }}</span>
                  <span class="opt-text">{{ opt.text }}</span>
                </div>
              </div>

              <textarea
                v-else
                class="q-text"
                rows="4"
                placeholder="请在此作答"
                :value="(answers[q.id] as string) ?? ''"
                @input="onText(q, ($event.target as HTMLTextAreaElement).value)"
                @blur="persistText(q)"
              />
            </div>
          </div>
        </div>
      </template>
    </div>
  </Page>
</template>

<style scoped>
.exam-room {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding-bottom: 8px;
}

.state {
  padding: 60px;
  color: #8c8c8c;
  text-align: center;
}

.state--error {
  display: flex;
  flex-direction: column;
  gap: 14px;
  align-items: center;
  color: #cf1322;
}

.panel {
  padding: 24px 28px;
  background: #fff;
  border: 1px solid #eaeef5;
  border-radius: 12px;
}

.result-panel {
  max-height: 100%;
  overflow-y: auto;
}

.result-panel h2 {
  margin: 0 0 16px;
  font-size: 18px;
}

.score-row {
  display: flex;
  gap: 12px;
  align-items: baseline;
}

.score b {
  color: #1257c9;
  font-size: 34px;
}

.score span {
  margin-left: 4px;
  color: #98a2b3;
  font-size: 13px;
}

.score-meta {
  color: #667085;
  font-size: 13px;
}

.pending {
  margin: 12px 0 0;
  color: #d46b08;
  font-size: 13px;
}

.result-actions {
  margin-top: 20px;
}

.exam-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 22px;
  margin-bottom: 14px;
  background: #fff;
  border: 1px solid #eaeef5;
  border-radius: 12px;
}

.exam-head h2 {
  margin: 0;
  font-size: 17px;
}

.exam-head .sub {
  margin: 4px 0 0;
  color: #8892a6;
  font-size: 12px;
}

.exam-timer {
  text-align: right;
}

.exam-timer .label {
  display: block;
  color: #98a2b3;
  font-size: 12px;
}

.exam-timer b {
  color: #1b2437;
  font-size: 22px;
  font-variant-numeric: tabular-nums;
}

.exam-timer b.danger {
  color: #cf1322;
}

.exam-body {
  display: grid;
  gap: 14px;
  grid-template-columns: 240px minmax(0, 1fr);
  flex: 1;
  min-height: 0;
}

/* 答题卡固定在左侧：不随题目区滚动，内容超高时自身内部滚动 */
.sheet {
  display: flex;
  flex-direction: column;
  max-height: 100%;
  padding: 16px;
  overflow-y: auto;
  background: #fff;
  border: 1px solid #eaeef5;
  border-radius: 12px;
}

.sheet-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
}

.sheet-progress {
  color: #98a2b3;
}

.sheet-grid {
  display: grid;
  gap: 6px;
  grid-template-columns: repeat(5, 1fr);
  margin: 12px 0 16px;
}

.sheet-no {
  display: grid;
  place-items: center;
  height: 28px;
  color: #667085;
  font-size: 12px;
  cursor: pointer;
  background: #f4f6fa;
  border: none;
  border-radius: 6px;
  transition: background 0.15s, color 0.15s;
}

.sheet-no:hover {
  color: #2f6bff;
  background: #e6efff;
}

.sheet-no.done {
  color: #fff;
  background: #2f6bff;
}

/* 题目区独立滚动，答题卡不受影响 */
.questions {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
  padding-right: 10px;
  overflow-y: auto;
}

.question {
  padding: 18px 22px;
  scroll-margin-top: 12px;
  background: #fff;
  border: 1px solid #eaeef5;
  border-radius: 12px;
}

.q-head {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}

.q-no {
  font-size: 13px;
  font-weight: 600;
}

.q-score {
  margin-left: auto;
  color: #98a2b3;
  font-size: 12px;
}

.q-stem {
  margin-bottom: 12px;
  color: #1b2437;
  font-size: 14px;
  line-height: 1.8;
}

.q-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.q-option {
  display: flex;
  gap: 10px;
  padding: 10px 12px;
  color: #475467;
  font-size: 13px;
  cursor: pointer;
  background: #fbfcfe;
  border: 1px solid #eaeef5;
  border-radius: 8px;
  transition: all 0.15s;
}

.q-option:hover {
  border-color: #b9cdfb;
}

.q-option.on {
  color: #1257c9;
  background: #f0f6ff;
  border-color: #2f6bff;
}

.opt-key {
  font-weight: 600;
}

.q-text {
  width: 100%;
  padding: 10px 12px;
  color: #1e293b;
  font-size: 13px;
  background: #fbfcfe;
  border: 1px solid #d7e2f2;
  border-radius: 8px;
}

@media (width <= 900px) {
  .exam-body {
    grid-template-columns: minmax(0, 1fr);
  }

  .sheet {
    position: static;
  }
}
</style>
