<script lang="ts" setup>
/**
 * AI 简历助手面板。
 *
 * 三个场景共用同一组输入（本人简历版本 + 目标职位）：
 * - 简历诊断（A01）：完整度、清晰度、专业性、岗位匹配
 * - 职位匹配（A05）：技能、经验、学历、职责、关键词，以及已匹配 / 缺失 / 待确认
 * - 简历优化（A02）：按表达方向产出可逐项确认的改写建议，只改写表述、不新增事实
 *
 * 只展示真实结果：模型未配置、额度不足或调用失败时给出明确原因，
 * 不生成任何模拟评分或编造建议。
 */
import { computed, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { message } from 'ant-design-vue';

import { getPortalRecruitPostPage } from '#/api/hrm/portal/recruit';
import {
  applyResumeOptimize,
  buildAnalyzeIdempotencyKey,
  buildOptimizeApplyKey,
  getRecruitAgentTask,
  submitJobMatch,
  submitResumeAnalyze,
  submitResumeOptimize,
  type HrmPortalRecruitAgentApi,
} from '#/api/hrm/portal/recruit-agent';
import {
  getPortalResumeList,
  isGeneratedResume,
  isOnlineResume,
  isUploadedResume,
  type HrmPortalResumeApi,
} from '#/api/hrm/portal/resume';
import {
  getPortalResumeVersionList,
  type HrmPortalResumeVersionApi,
} from '#/api/hrm/portal/resume-version';

import {
  applyDecision,
  applyDecisionToAll,
  buildChangeSummary,
  buildDecisionPayload,
  canSave,
  createInitialDecisions,
  summarize,
  updateFinalValue as applyDecisionFinalValue,
  type Decision,
  type DecisionMap,
} from './resume-optimize-decisions';

const props = withDefaults(
  defineProps<{
    open: boolean;
    /** 独立路由页使用内嵌面板，保留与弹窗完全相同的 AI 能力 */
    embedded?: boolean;
  }>(),
  { embedded: false },
);

const emit = defineEmits<{
  /** 用户点击「简历版本」链接，请求外层打开版本面板 */
  openVersions: [];
  'update:open': [value: boolean];
}>();

const modalOpen = computed({
  get: () => props.open,
  set: (value: boolean) => emit('update:open', value),
});

function handleModalUpdate(value: boolean) {
  if (!props.embedded) modalOpen.value = value;
}

type Scene = 'analyze' | 'match' | 'optimize';
type Phase = 'failed' | 'idle' | 'running' | 'succeeded';
type SourceKind = 'online' | 'uploaded' | 'generated';

const SOURCE_OPTIONS: Array<{ label: string; value: SourceKind }> = [
  { label: '分析在线简历', value: 'online' },
  { label: '分析已上传的附件', value: 'uploaded' },
  { label: '分析制作生成的简历', value: 'generated' },
];

/** 表达方向选项，与后端 styleInstruction 对应 */
const STYLE_OPTIONS: Array<{
  label: string;
  value: HrmPortalRecruitAgentApi.OptimizeStyle;
}> = [
  { label: '专业', value: 'PROFESSIONAL' },
  { label: '简洁', value: 'CONCISE' },
  { label: '技术', value: 'TECHNICAL' },
  { label: '管理', value: 'MANAGEMENT' },
];

/** 优化模块选项，与后端 ALLOWED_MODULES 对应 */
const MODULE_OPTIONS: Array<{ label: string; value: string }> = [
  { label: '基本信息', value: 'BASIC_INFO' },
  { label: '求职意向', value: 'JOB_INTENTION' },
  { label: '教育经历', value: 'EDUCATION' },
  { label: '工作经历', value: 'WORK_EXPERIENCE' },
  { label: '项目经历', value: 'PROJECT_EXPERIENCE' },
  { label: '技能证书', value: 'SKILLS' },
  { label: '自我介绍', value: 'SELF_INTRO' },
];

const scene = ref<Scene>('analyze');
const phase = ref<Phase>('idle');
const loadingOptions = ref(false);
const versions = ref<HrmPortalResumeVersionApi.Version[]>([]);
const resumes = ref<HrmPortalResumeApi.Resume[]>([]);
const posts = ref<Array<{ id: number; postName: string }>>([]);
const selectedVersionId = ref<number>();
const selectedResumeId = ref<number>();
const sourceKind = ref<SourceKind>('online');
const selectedPostId = ref<number>();
const optimizeStyle =
  ref<HrmPortalRecruitAgentApi.OptimizeStyle>('PROFESSIONAL');
const optimizeModules = ref<string[]>([]);
const taskResult = ref<HrmPortalRecruitAgentApi.TaskResp>();
const errorMessage = ref('');

/** 同一次操作复用同一个幂等键，重复点击不会重复调用模型 */
let currentIdempotencyKey = '';

const dimensionLabels: Record<string, string> = {
  // 诊断
  COMPLETENESS: '完整度',
  CLARITY: '清晰度',
  PROFESSIONALISM: '专业性',
  JOB_MATCH: '岗位匹配',
  // 匹配
  SKILL: '技能',
  EXPERIENCE: '经验',
  EDUCATION: '学历',
  RESPONSIBILITY: '职责',
  KEYWORD: '关键词',
};

const suggestionModuleLabels: Record<string, string> = {
  BASIC_INFO: '基本信息',
  JOB_INTENTION: '求职意向',
  EDUCATION: '教育经历',
  WORK_EXPERIENCE: '工作经历',
  PROJECT_EXPERIENCE: '项目经历',
  SKILLS: '技能证书',
  SELF_INTRO: '自我介绍',
};

const canSubmit = computed(
  () =>
    Boolean((scene.value === 'optimize' ? selectedVersionId.value : selectedResumeId.value && hasAnalyzableContent(selectedResume.value)) && selectedPostId.value) &&
    phase.value !== 'running',
);
const sourceRows = computed(() => resumes.value.filter((resume) =>
  sourceKind.value === 'online' ? isOnlineResume(resume) : sourceKind.value === 'uploaded' ? isUploadedResume(resume) : isGeneratedResume(resume),
));
const sourceResumes = computed(() => sourceRows.value.filter(hasAnalyzableContent));
const sourceHasUnusableRows = computed(() => sourceRows.value.length > 0 && sourceResumes.value.length === 0);
const selectedResume = computed(() => resumes.value.find((item) => item.id === selectedResumeId.value));
const optimizeDisabled = computed(() => Boolean(selectedResume.value && !isStructuredSource(selectedResume.value)));

function hasAnalyzableContent(resume?: HrmPortalResumeApi.Resume) {
  const content = resume?.content?.trim();
  if (!content) return false;
  if (!content.startsWith('{')) return content.length >= 50;
  try {
    const parsed = JSON.parse(content);
    return Boolean(parsed && typeof parsed === 'object' && Object.keys(parsed).length);
  } catch {
    return false;
  }
}

function isStructuredSource(resume?: HrmPortalResumeApi.Resume) {
  const content = resume?.content?.trim();
  if (!content?.startsWith('{')) return false;
  try {
    const parsed = JSON.parse(content);
    return Boolean(parsed && typeof parsed === 'object');
  } catch {
    return false;
  }
}

function sourceEmptyMessage() {
  if (sourceKind.value === 'online') return '还没有保存在线简历。请先在“在线简历”页填写并保存，或改选“分析已上传的附件”。';
  if (sourceKind.value === 'uploaded') return '还没有可用于分析的上传附件。可在“附件管理”中上传 PDF/Word，上传时会自动提取文本。';
  return '还没有制作生成的简历。点击“制作附件简历”导出 Word 后会自动出现在这里。';
}

/** 诊断结果（含优势/缺失/风险/建议） */
const analyzeResult = computed(() =>
  scene.value === 'analyze'
    ? (taskResult.value?.result as HrmPortalRecruitAgentApi.AnalyzeResult)
    : undefined,
);

/** 匹配结果（含已匹配/缺失/待确认） */
const matchResult = computed(() =>
  scene.value === 'match'
    ? (taskResult.value?.result as HrmPortalRecruitAgentApi.MatchResult)
    : undefined,
);

/** 优化结果（建议项列表） */
const optimizeResult = computed(() =>
  scene.value === 'optimize'
    ? (taskResult.value?.result as HrmPortalRecruitAgentApi.OptimizeResult)
    : undefined,
);

/** 诊断与匹配共用的分项维度 */
const dimensions = computed(
  () => analyzeResult.value?.dimensions || matchResult.value?.dimensions || [],
);

/** 优化场景有结果但没有建议，说明确实没有可改之处 */
const optimizeEmpty = computed(
  () =>
    scene.value === 'optimize' &&
    phase.value === 'succeeded' &&
    (optimizeResult.value?.items?.length ?? 0) === 0,
);

/* ===== A03 逐项确认 ===== */

// 决定、统计与提交载荷的计算逻辑抽在纯函数模块里，组件只负责渲染与调用
const decisions = ref<DecisionMap>(createInitialDecisions());
const saving = ref(false);
const savedVersionId = ref<number>();

const optimizeItems = computed(() => optimizeResult.value?.items || []);

const decisionStats = computed(() =>
  summarize(decisions.value, optimizeItems.value.length),
);

const canSubmitDecisions = computed(() =>
  canSave(decisionStats.value, optimizeItems.value.length, saving.value),
);

/** 生成建议后初始化确认状态：全部为未决定 */
function initDecisions() {
  decisions.value = createInitialDecisions();
  savedVersionId.value = undefined;
}

function setDecision(index: number, decision: Decision) {
  const item = optimizeItems.value[index];
  if (!item) return;
  decisions.value = applyDecision(decisions.value, index, decision, item);
}

function updateFinalValue(index: number, value: string) {
  decisions.value = applyDecisionFinalValue(decisions.value, index, value);
}

function setAll(decision: 'ACCEPT' | 'REJECT') {
  decisions.value = applyDecisionToAll(decisions.value, optimizeItems.value, decision);
}

/** 页面内撤销：清空全部决定，回到未确认状态 */
function undoDecisions() {
  decisions.value = createInitialDecisions();
  savedVersionId.value = undefined;
}

async function saveDecisions() {
  if (!canSubmitDecisions.value || !taskResult.value) return;
  saving.value = true;
  try {
    const newVersionId = await applyResumeOptimize({
      sourceVersionId: selectedVersionId.value!,
      taskId: taskResult.value.taskId,
      changeSummary: buildChangeSummary(decisionStats.value),
      idempotencyKey: buildOptimizeApplyKey(),
      decisions: buildDecisionPayload(optimizeItems.value, decisions.value),
    });
    savedVersionId.value = newVersionId;
    message.success('已保存为新的简历版本，原版本保持不变');
  } catch (error: any) {
    message.error(error?.message || '保存失败，请稍后重试');
  } finally {
    saving.value = false;
  }
}

function dimensionLabel(key: string) {
  return dimensionLabels[key] || key;
}

function moduleLabel(module: string) {
  return suggestionModuleLabels[module] || module;
}

function scoreTone(score?: number) {
  if (score === undefined || score === null) return 'mid';
  if (score >= 80) return 'good';
  if (score >= 60) return 'mid';
  return 'low';
}

async function loadOptions() {
  loadingOptions.value = true;
  try {
    const [versionList, resumeList, postPage] = await Promise.all([
      getPortalResumeVersionList(),
      getPortalResumeList(),
      getPortalRecruitPostPage({ pageNo: 1, pageSize: 100 }),
    ]);
    versions.value = (versionList || []).filter((item) => item.status === 0);
    resumes.value = resumeList || [];
    posts.value = (postPage?.list || []).map((item) => ({
      id: item.id,
      postName: item.postName,
    }));
    if (!selectedVersionId.value && versions.value.length > 0) {
      selectedVersionId.value = versions.value[0]!.id;
    }
    if (!selectedResumeId.value || !sourceResumes.value.some((item) => item.id === selectedResumeId.value)) selectedResumeId.value = sourceResumes.value[0]?.id;
    if (!selectedPostId.value && posts.value.length > 0) {
      selectedPostId.value = posts.value[0]!.id;
    }
  } catch (error) {
    console.warn('[RecruitAgent] 加载选项失败', error);
    versions.value = [];
    resumes.value = [];
    posts.value = [];
  } finally {
    loadingOptions.value = false;
  }
}

async function submit() {
  if (!canSubmit.value) return;
  phase.value = 'running';
  errorMessage.value = '';
  taskResult.value = undefined;
  currentIdempotencyKey = buildAnalyzeIdempotencyKey();
  const base = {
    ...(scene.value === 'optimize'
      ? { resumeVersionId: selectedVersionId.value! }
      : { resumeId: selectedResumeId.value! }),
    postId: selectedPostId.value!,
    idempotencyKey: currentIdempotencyKey,
  };
  try {
    let taskId: number;
    if (scene.value === 'analyze') {
      taskId = await submitResumeAnalyze(base);
    } else if (scene.value === 'match') {
      taskId = await submitJobMatch(base);
    } else {
      taskId = await submitResumeOptimize({
        ...base,
        style: optimizeStyle.value,
        modules: optimizeModules.value,
      });
    }
    const detail = await getRecruitAgentTask(taskId);
    if (detail?.status === 'SUCCEEDED') {
      taskResult.value = detail;
      phase.value = 'succeeded';
      // 优化场景需要重置逐项确认状态
      if (scene.value === 'optimize') initDecisions();
      return;
    }
    // 未成功时如实展示失败原因，不展示任何编造内容
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
  decisions.value = {};
  savedVersionId.value = undefined;
}

function switchSourceKind(next: SourceKind) {
  sourceKind.value = next;
  selectedResumeId.value = undefined;
  phase.value = 'idle';
  taskResult.value = undefined;
  errorMessage.value = '';
  selectedResumeId.value = sourceResumes.value[0]?.id;
}

function switchScene(next: Scene) {
  if (scene.value === next) return;
  if (next === 'optimize' && optimizeDisabled.value) return;
  scene.value = next;
  // 切换场景后清掉上一场景的结果，避免张冠李戴
  reset();
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      reset();
      void loadOptions();
    }
  },
  // 面板若以打开状态初始挂载，也要加载选项，不能只依赖状态变化
  { immediate: true },
);
</script>

<template>
  <component
    :is="props.embedded ? 'section' : 'a-modal'"
    v-bind="props.embedded
      ? { class: 'resume-ai-page-card' }
      : { open: modalOpen, title: 'AI 简历助手', footer: null, width: '760px', wrapClassName: 'resume-ai-modal' }"
    @update:open="handleModalUpdate"
  >
    <div class="ai-panel">
      <div class="ai-tabs" role="tablist">
        <button
          type="button"
          role="tab"
          :aria-selected="scene === 'analyze'"
          :class="{ active: scene === 'analyze' }"
          @click="switchScene('analyze')"
        >
          简历诊断
        </button>
        <button
          type="button"
          role="tab"
          :aria-selected="scene === 'match'"
          :class="{ active: scene === 'match' }"
          @click="switchScene('match')"
        >
          职位匹配
        </button>
        <button
          type="button"
          role="tab"
          :aria-selected="scene === 'optimize'"
          :class="{ active: scene === 'optimize', disabled: optimizeDisabled }"
          :disabled="optimizeDisabled"
          :title="optimizeDisabled ? '纯文本简历暂不支持优化保存，请选择在线简历或制作生成的简历' : undefined"
          @click="switchScene('optimize')"
        >
          简历优化
        </button>
      </div>

      <p class="ai-disclaimer">
        <IconifyIcon icon="lucide:info" />
        <span>AI 辅助分析，仅供参考。分析结果不会自动投递、淘汰或改变任何招聘状态。</span>
      </p>

      <!-- 选择分析对象 -->
      <section v-if="phase === 'idle' || phase === 'running'" class="ai-form">
        <label class="ai-field">
          <span>{{ scene === 'optimize' ? '用于优化的简历版本' : '分析来源' }}</span>
          <div v-if="scene !== 'optimize'" class="source-switch" role="group" aria-label="分析来源">
            <button
              v-for="item in SOURCE_OPTIONS"
              :key="item.value"
              type="button"
              :class="{ active: sourceKind === item.value }"
              :disabled="phase === 'running'"
              :aria-pressed="sourceKind === item.value"
              @click="switchSourceKind(item.value)"
            >
              {{ item.label }}
            </button>
          </div>
          <a-select
            v-if="scene === 'optimize'"
            v-model:value="selectedVersionId"
            :loading="loadingOptions"
            placeholder="请先保存一个简历版本"
            :disabled="phase === 'running'"
          >
            <a-select-option v-for="item in versions" :key="item.id" :value="item.id">
              V{{ item.versionNo }}（{{ item.changeSummary || '未填写修改摘要' }}）
            </a-select-option>
          </a-select>
          <a-select
            v-else
            v-model:value="selectedResumeId"
            :loading="loadingOptions"
            placeholder="请选择简历来源"
            :disabled="phase === 'running' || (sourceKind === 'online' && sourceResumes.length === 1)"
          >
            <a-select-option v-for="item in sourceResumes" :key="item.id" :value="item.id">
              {{ sourceKind === 'online' && sourceResumes.length === 1 ? `在线简历（${item.title || '求职资料'}）` : item.title || item.fileName || '未命名简历' }}
            </a-select-option>
          </a-select>
          <small v-if="scene !== 'optimize' && selectedResume && !hasAnalyzableContent(selectedResume)" class="ai-hint warn">该来源的简历尚未提取到可分析内容，暂不能进行分析。</small>
        </label>

        <label class="ai-field">
          <span>目标职位</span>
          <a-select
            v-model:value="selectedPostId"
            :loading="loadingOptions"
            placeholder="请选择已发布的职位"
            show-search
            option-filter-prop="children"
            :disabled="phase === 'running'"
          >
            <a-select-option v-for="item in posts" :key="item.id" :value="item.id">
              {{ item.postName }}
            </a-select-option>
          </a-select>
        </label>

        <!-- 优化场景额外需要表达方向与优化范围 -->
        <template v-if="scene === 'optimize'">
          <label class="ai-field">
            <span>表达方向</span>
            <a-radio-group
              v-model:value="optimizeStyle"
              button-style="solid"
              :disabled="phase === 'running'"
            >
              <a-radio-button
                v-for="item in STYLE_OPTIONS"
                :key="item.value"
                :value="item.value"
              >
                {{ item.label }}
              </a-radio-button>
            </a-radio-group>
          </label>

          <label class="ai-field">
            <span>优化范围 <small>不选表示整份优化</small></span>
            <a-select
              v-model:value="optimizeModules"
              mode="multiple"
              placeholder="整份优化"
              :disabled="phase === 'running'"
            >
              <a-select-option
                v-for="item in MODULE_OPTIONS"
                :key="item.value"
                :value="item.value"
              >
                {{ item.label }}
              </a-select-option>
            </a-select>
          </label>
        </template>

        <p v-if="!loadingOptions && scene === 'optimize' && versions.length === 0" class="ai-hint warn">
          还没有可用的简历版本。请先
          <button type="button" class="ai-hint-link" @click="emit('openVersions')">
            打开「简历版本」
          </button>
          把当前简历保存为版本，再回来分析。
        </p>
        <p v-else-if="!loadingOptions && scene !== 'optimize' && sourceResumes.length === 0" class="ai-hint warn">{{ sourceHasUnusableRows ? `这个来源下有 ${sourceRows.length} 份简历未提取到内容（如扫描件或旧版 Word），暂不能用于分析；建议删除后重新上传。` : sourceEmptyMessage() }}</p>
        <p v-else-if="!loadingOptions && posts.length === 0" class="ai-hint warn">
          暂时没有可选的已发布职位。
        </p>

        <a-button
          type="primary"
          :loading="phase === 'running'"
          :disabled="!canSubmit"
          @click="submit"
        >
          {{
            phase === 'running'
              ? '正在分析…'
              : scene === 'analyze'
                ? '开始诊断'
                : scene === 'match'
                  ? '开始匹配'
                  : '生成优化建议'
          }}
        </a-button>
      </section>

      <!-- 失败：如实展示原因 -->
      <section v-else-if="phase === 'failed'" class="ai-failed">
        <IconifyIcon icon="lucide:triangle-alert" />
        <strong>本次分析未完成</strong>
        <p>{{ errorMessage }}</p>
        <div class="ai-actions">
          <a-button @click="reset">重新选择</a-button>
          <a-button type="primary" @click="submit">重试</a-button>
        </div>
      </section>

      <!-- 优化：建议项列表 -->
      <section v-else-if="scene === 'optimize' && phase === 'succeeded'" class="ai-result">
        <div class="ai-optimize-head">
          <strong>优化建议</strong>
          <small v-if="taskResult?.model">模型：{{ taskResult.model }}</small>
        </div>

        <p v-if="optimizeEmpty" class="ai-hint">
          本次没有产出建议，可能当前简历表达已经比较充分，或该版本内容较少。
        </p>

        <article
          v-for="(item, index) in optimizeItems"
          :key="index"
          class="ai-suggestion-item"
          :class="{
            'is-accepted': decisions[index]?.decision === 'ACCEPT',
            'is-rejected': decisions[index]?.decision === 'REJECT',
            'is-adjusted': decisions[index]?.decision === 'ADJUST',
          }"
        >
          <header>
            <span class="ai-suggestion-module">{{ moduleLabel(item.module) }}</span>
            <code>{{ item.fieldPath }}</code>
            <span
              v-if="decisions[index]"
              class="ai-decision-badge"
              :class="`is-${decisions[index]!.decision.toLowerCase()}`"
            >
              {{
                decisions[index]!.decision === 'ACCEPT'
                  ? '已接受'
                  : decisions[index]!.decision === 'REJECT'
                    ? '已拒绝'
                    : '已调整'
              }}
            </span>
          </header>

          <div class="ai-diff">
            <p class="ai-diff-row is-before">
              <span class="ai-diff-tag">原值</span>
              <span>{{ item.originalValue }}</span>
            </p>
            <p
              v-if="decisions[index]?.decision === 'REJECT'"
              class="ai-diff-row is-kept"
            >
              <span class="ai-diff-tag">最终值</span>
              <span>保持原值不变</span>
            </p>
            <p v-else class="ai-diff-row is-after">
              <span class="ai-diff-tag">最终值</span>
              <span>{{ decisions[index]?.finalValue || item.suggestedValue }}</span>
            </p>
          </div>

          <small class="ai-suggestion-reason">
            建议值：{{ item.suggestedValue }}
            <br />
            理由：{{ item.reason }}
          </small>

          <div class="ai-decision-actions">
            <a-button
              size="small"
              :type="decisions[index]?.decision === 'ACCEPT' ? 'primary' : 'default'"
              @click="setDecision(index, 'ACCEPT')"
            >
              接受
            </a-button>
            <a-button
              size="small"
              :type="decisions[index]?.decision === 'ADJUST' ? 'primary' : 'default'"
              @click="setDecision(index, 'ADJUST')"
            >
              手工调整
            </a-button>
            <a-button
              size="small"
              :type="decisions[index]?.decision === 'REJECT' ? 'primary' : 'default'"
              @click="setDecision(index, 'REJECT')"
            >
              拒绝
            </a-button>
          </div>

          <a-textarea
            v-if="decisions[index]?.decision === 'ADJUST'"
            :value="decisions[index]!.finalValue"
            :rows="3"
            :maxlength="2000"
            placeholder="在建议基础上修改，或填写你自己的表述"
            @update:value="(value: string) => updateFinalValue(index, value)"
          />
        </article>

        <div v-if="optimizeItems.length" class="ai-optimize-footer">
          <div class="ai-decision-summary">
            共 {{ optimizeItems.length }} 项：已决定 {{ decisionStats.decided }} 项
            （接受 {{ decisionStats.accepted }}、调整 {{ decisionStats.adjusted }}、拒绝
            {{ decisionStats.rejected }}）
            <span v-if="decisionStats.pending > 0" class="ai-decision-pending">
              ，还有 {{ decisionStats.pending }} 项待确认
            </span>
          </div>
          <div class="ai-decision-bulk">
            <a-button size="small" @click="setAll('ACCEPT')">全部接受</a-button>
            <a-button size="small" @click="setAll('REJECT')">全部拒绝</a-button>
            <a-button size="small" :disabled="decisionStats.decided === 0" @click="undoDecisions">
              撤销全部
            </a-button>
          </div>
        </div>

        <p v-if="savedVersionId" class="ai-saved-tip">
          <IconifyIcon icon="lucide:circle-check" />
          <span>已保存为新的简历版本，可在上方「简历版本」中查看；原版本保持不变。</span>
        </p>

        <p class="ai-footer-note">
          建议只改写表述，不会新增公司、时间、学历、证书、技能与成果。
          逐项确认后才会生成新版本，原简历不会被改动。
        </p>

        <div class="ai-actions">
          <a-button @click="reset">重新生成</a-button>
          <a-button
            v-if="optimizeItems.length"
            type="primary"
            :loading="saving"
            :disabled="!canSubmitDecisions"
            @click="saveDecisions"
          >
            {{ canSubmitDecisions ? '保存为新版本' : '请逐项确认' }}
          </a-button>
        </div>
      </section>

      <!-- 诊断 / 匹配：分项 + 依据 -->
      <section
        v-else-if="phase === 'succeeded' && dimensions.length"
        class="ai-result"
      >
        <div class="ai-score">
          <div class="ai-score-value" :class="`is-${scoreTone(taskResult?.overallScore)}`">
            {{ taskResult?.overallScore }}
          </div>
          <div class="ai-score-meta">
            <strong>{{ scene === 'analyze' ? '综合得分' : '匹配度' }}</strong>
            <small v-if="taskResult?.model">模型：{{ taskResult.model }}</small>
            <small v-if="taskResult?.promptVersion">
              提示词版本：{{ taskResult.promptVersion }}
            </small>
          </div>
        </div>

        <div class="ai-dimensions">
          <article v-for="item in dimensions" :key="item.key" class="ai-dimension">
            <header>
              <strong>{{ dimensionLabel(item.key) }}</strong>
              <span :class="`is-${scoreTone(item.score)}`">{{ item.score }}</span>
            </header>
            <p class="ai-dimension-comment">{{ item.comment }}</p>
            <ul class="ai-evidence">
              <li v-for="(evidence, index) in item.evidence" :key="index">
                <code>{{ evidence.field }}</code>
                <span>{{ evidence.note }}</span>
              </li>
            </ul>
          </article>
        </div>

        <!-- 诊断：优势 / 缺失 / 风险 -->
        <div v-if="analyzeResult" class="ai-lists">
          <div v-if="analyzeResult.strengths?.length" class="ai-list is-good">
            <strong>优势</strong>
            <ul><li v-for="(t, i) in analyzeResult.strengths" :key="i">{{ t }}</li></ul>
          </div>
          <div v-if="analyzeResult.missingInfo?.length" class="ai-list is-warn">
            <strong>缺失信息</strong>
            <ul><li v-for="(t, i) in analyzeResult.missingInfo" :key="i">{{ t }}</li></ul>
          </div>
          <div v-if="analyzeResult.risks?.length" class="ai-list is-risk">
            <strong>风险点</strong>
            <ul><li v-for="(t, i) in analyzeResult.risks" :key="i">{{ t }}</li></ul>
          </div>
        </div>

        <!-- 匹配：已匹配 / 缺失 / 待确认 -->
        <div v-if="matchResult" class="ai-lists">
          <div v-if="matchResult.matched?.length" class="ai-list is-good">
            <strong>已匹配（{{ matchResult.matched.length }}）</strong>
            <ul>
              <li v-for="(item, i) in matchResult.matched" :key="i">
                {{ item.point }}
                <small class="ai-point-evidence">{{ item.evidence }}</small>
              </li>
            </ul>
          </div>
          <div v-if="matchResult.missing?.length" class="ai-list is-risk">
            <strong>未满足（{{ matchResult.missing.length }}）</strong>
            <ul>
              <li v-for="(item, i) in matchResult.missing" :key="i">
                {{ item.point }}
                <small class="ai-point-evidence">{{ item.evidence }}</small>
              </li>
            </ul>
          </div>
          <div v-if="matchResult.toConfirm?.length" class="ai-list is-confirm">
            <strong>待确认（{{ matchResult.toConfirm.length }}）</strong>
            <ul>
              <li v-for="(item, i) in matchResult.toConfirm" :key="i">
                {{ item.point }}
                <small class="ai-point-evidence">{{ item.reason }}</small>
              </li>
            </ul>
          </div>
        </div>

        <!-- 诊断：修改建议 -->
        <div v-if="analyzeResult?.suggestions?.length" class="ai-suggestions">
          <strong>修改建议</strong>
          <article v-for="(item, index) in analyzeResult.suggestions" :key="index">
            <header>
              <span class="ai-suggestion-module">{{ moduleLabel(item.module) }}</span>
              <code>{{ item.fieldPath }}</code>
            </header>
            <p>{{ item.advice }}</p>
            <small>依据：{{ item.reason }}</small>
          </article>
        </div>

        <p class="ai-footer-note">
          {{ taskResult?.disclaimer || 'AI 辅助分析，仅供参考' }}
        </p>

        <div class="ai-actions">
          <a-button @click="reset">
            {{ scene === 'analyze' ? '重新诊断' : '重新匹配' }}
          </a-button>
        </div>
      </section>
    </div>
  </component>
</template>
<style scoped>
.resume-ai-page-card {
  display: block;
  width: min(100%, 980px);
  margin: 0 auto;
  padding: 24px;
  border: 1px solid #e1eceb;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 16px 36px rgb(23 52 61 / 8%);
}

.ai-panel {
  display: grid;
  gap: 16px;
}

.ai-tabs {
  display: flex;
  gap: 6px;
  padding: 4px;
  border-radius: 10px;
  background: #f2f7f7;
}

.ai-tabs button {
  flex: 1;
  padding: 8px 12px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #5f7a80;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  transition: background 160ms ease, color 160ms ease;
}

.ai-tabs button.active {
  color: #0f766e;
  background: #fff;
  box-shadow: 0 2px 8px rgb(23 52 61 / 8%);
}

.ai-tabs button:focus-visible {
  outline: 2px solid #4de0d2;
  outline-offset: 2px;
}

.ai-disclaimer {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  padding: 10px 12px;
  border-radius: 10px;
  color: #475569;
  background: #f4fbf9;
  font-size: 13px;
  line-height: 1.6;
}

.ai-form {
  display: grid;
  gap: 16px;
}

.ai-field {
  display: grid;
  gap: 8px;
  color: #17343d;
  font-size: 14px;
  font-weight: 600;
}

.source-switch {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 4px;
  padding: 4px;
  border: 1px solid #d7e9e9;
  border-radius: 10px;
  background: #f3f8f8;
}

.source-switch button {
  min-height: 38px;
  padding: 6px 10px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: #5b747b;
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.35;
  transition: background 160ms ease, color 160ms ease, box-shadow 160ms ease;
}

.source-switch button:hover:not(:disabled) {
  color: #0f777d;
  background: #e8f7f8;
}

.source-switch button.active {
  color: #0f777d;
  background: #fff;
  box-shadow: 0 2px 8px rgb(23 52 61 / 8%);
}

.source-switch button:focus-visible {
  outline: 2px solid #4de0d2;
  outline-offset: 2px;
}

.source-switch button:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

@media (max-width: 600px) {
  .source-switch {
    grid-template-columns: 1fr;
  }
}

.ai-hint {
  margin: 0;
  color: #7d9299;
  font-size: 13px;
  line-height: 1.6;
}

.ai-hint.warn {
  padding: 10px 12px;
  border-radius: 8px;
  color: #b45309;
  background: #fffbeb;
}

.ai-hint-link {
  margin: 0 2px;
  padding: 0;
  border: 0;
  background: transparent;
  color: #0e7490;
  cursor: pointer;
  font-size: inherit;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.ai-hint-link:hover {
  color: #155e75;
}

.ai-failed {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 28px 16px;
  border: 1px dashed #f0d3a8;
  border-radius: 12px;
  background: #fffdf7;
  text-align: center;
}

.ai-failed > :deep(svg) {
  color: #d97706;
  font-size: 26px;
}

.ai-failed strong {
  color: #17343d;
  font-size: 15px;
}

.ai-failed p {
  margin: 0;
  color: #7d9299;
  font-size: 13px;
  line-height: 1.7;
}

.ai-score {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 18px;
  border: 1px solid #e2edec;
  border-radius: 12px;
  background: #fff;
}

.ai-score-value {
  display: grid;
  width: 66px;
  height: 66px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 50%;
  font-size: 26px;
  font-weight: 700;
}

.ai-score-value.is-good {
  color: #047857;
  background: #e8f6ee;
}

.ai-score-value.is-mid {
  color: #0e7490;
  background: #e6f4f8;
}

.ai-score-value.is-low {
  color: #b45309;
  background: #fef3c7;
}

.ai-score-meta {
  display: grid;
  gap: 4px;
}

.ai-score-meta strong {
  color: #17343d;
  font-size: 15px;
}

.ai-score-meta small {
  color: #8497a3;
  font-size: 12px;
}

.ai-dimensions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.ai-dimension {
  display: grid;
  gap: 8px;
  padding: 14px;
  border: 1px solid #e2edec;
  border-radius: 12px;
  background: #fff;
}

.ai-dimension header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ai-dimension header strong {
  color: #17343d;
  font-size: 14px;
}

.ai-dimension header span {
  font-size: 18px;
  font-weight: 700;
}

.ai-dimension header span.is-good {
  color: #047857;
}

.ai-dimension header span.is-mid {
  color: #0e7490;
}

.ai-dimension header span.is-low {
  color: #b45309;
}

.ai-dimension-comment {
  margin: 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.6;
}

.ai-evidence {
  display: grid;
  gap: 5px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.ai-evidence li {
  display: flex;
  gap: 7px;
  color: #7d9299;
  font-size: 12px;
  line-height: 1.6;
}

.ai-evidence code {
  flex: 0 0 auto;
  padding: 0 5px;
  border-radius: 4px;
  color: #0f766e;
  background: #eef7f6;
  font-size: 11px;
}

.ai-lists {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.ai-list {
  padding: 12px 14px;
  border-radius: 12px;
  background: #fbfdfd;
}

.ai-list strong {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
}

.ai-list ul {
  display: grid;
  gap: 6px;
  margin: 0;
  padding-left: 16px;
  color: #475569;
  font-size: 12px;
  line-height: 1.6;
}

.ai-point-evidence {
  display: block;
  margin-top: 2px;
  color: #8497a3;
  font-size: 11px;
  line-height: 1.5;
}

.ai-list.is-good {
  border: 1px solid #d6efe0;
}

.ai-list.is-good strong {
  color: #047857;
}

.ai-list.is-warn {
  border: 1px solid #f2e2c4;
}

.ai-list.is-warn strong {
  color: #b45309;
}

.ai-list.is-risk {
  border: 1px solid #f3d9d9;
}

.ai-list.is-risk strong {
  color: #b91c1c;
}

.ai-list.is-confirm {
  border: 1px solid #d7e3f4;
}

.ai-list.is-confirm strong {
  color: #1d4ed8;
}

.ai-suggestions {
  display: grid;
  gap: 10px;
}

.ai-suggestions > strong {
  color: #17343d;
  font-size: 14px;
}

.ai-suggestions article {
  display: grid;
  gap: 5px;
  padding: 12px 14px;
  border: 1px solid #e2edec;
  border-radius: 10px;
  background: #fff;
}

.ai-suggestions article header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-suggestion-module {
  padding: 1px 8px;
  border-radius: 999px;
  color: #0f766e;
  background: #eef7f6;
  font-size: 12px;
}

.ai-suggestions article code {
  color: #8497a3;
  font-size: 11px;
}

.ai-suggestions article p {
  margin: 0;
  color: #334155;
  font-size: 13px;
  line-height: 1.7;
}

.ai-suggestions article small {
  color: #8497a3;
  font-size: 12px;
  line-height: 1.6;
}

.ai-footer-note {
  margin: 0;
  color: #8497a3;
  font-size: 12px;
  text-align: center;
}

/* ===== 优化建议 ===== */

.ai-optimize-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.ai-optimize-head strong {
  color: #17343d;
  font-size: 15px;
}

.ai-optimize-head small {
  color: #8497a3;
  font-size: 12px;
}

.ai-suggestion-item {
  display: grid;
  gap: 8px;
  padding: 14px;
  border: 1px solid #e2edec;
  border-radius: 12px;
  background: #fff;
}

.ai-suggestion-item header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-suggestion-item header code {
  color: #8497a3;
  font-size: 11px;
}

.ai-diff {
  display: grid;
  gap: 6px;
}

.ai-diff-row {
  display: flex;
  gap: 8px;
  margin: 0;
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.7;
}

.ai-diff-row.is-before {
  color: #64748b;
  background: #f6f8f9;
}

.ai-diff-row.is-after {
  color: #0f3f3a;
  background: #eef7f6;
}

.ai-diff-tag {
  flex: 0 0 auto;
  color: #8497a3;
  font-size: 11px;
  font-weight: 600;
}

.ai-diff-row.is-after .ai-diff-tag {
  color: #0f766e;
}

.ai-suggestion-reason {
  color: #8497a3;
  font-size: 12px;
  line-height: 1.6;
}

/* ===== A03 逐项确认 ===== */

.ai-suggestion-item.is-accepted {
  border-color: #b7e0c9;
}

.ai-suggestion-item.is-rejected {
  border-color: #e6ecee;
  opacity: 0.72;
}

.ai-suggestion-item.is-adjusted {
  border-color: #b9d8ef;
}

.ai-decision-badge {
  margin-left: auto;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 11px;
}

.ai-decision-badge.is-accept {
  color: #047857;
  background: #e8f6ee;
}

.ai-decision-badge.is-reject {
  color: #64748b;
  background: #f1f5f9;
}

.ai-decision-badge.is-adjust {
  color: #1d4ed8;
  background: #e6f0fb;
}

.ai-diff-row.is-kept {
  color: #64748b;
  background: #f6f8f9;
}

.ai-decision-actions {
  display: flex;
  gap: 8px;
}

.ai-optimize-footer {
  display: grid;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid #e2edec;
  border-radius: 10px;
  background: #fbfdfd;
}

.ai-decision-summary {
  color: #475569;
  font-size: 13px;
  line-height: 1.6;
}

.ai-decision-pending {
  color: #b45309;
}

.ai-decision-bulk {
  display: flex;
  gap: 8px;
}

.ai-saved-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  padding: 10px 12px;
  border-radius: 10px;
  color: #047857;
  background: #e8f6ee;
  font-size: 13px;
  line-height: 1.6;
}

.ai-actions {
  display: flex;
  justify-content: center;
  gap: 10px;
}

@media (max-width: 640px) {
  .ai-dimensions,
  .ai-lists {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
