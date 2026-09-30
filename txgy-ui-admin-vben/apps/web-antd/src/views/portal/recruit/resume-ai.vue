<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';
import { useUserStore } from '@vben/stores';

import { message } from 'ant-design-vue';

import {
  buildGenerateIdempotencyKey,
  getRecruitAgentTask,
  submitResumeGenerate,
  type HrmPortalRecruitAgentApi,
} from '#/api/hrm/portal/recruit-agent';
import {
  buildResumeVersionIdempotencyKey,
  createPortalResumeVersion,
} from '#/api/hrm/portal/resume-version';
import {
  getPortalResumeList,
  isOnlineResume,
} from '#/api/hrm/portal/resume';

import ResumeAiPanel from './components/resume-ai-panel.vue';

const router = useRouter();
const userStore = useUserStore();
const displayName = computed(
  () => userStore.userInfo?.nickname || userStore.userInfo?.username || '用户',
);

type GenerateForm = {
  identity: 'student' | 'worker';
  targetRole: string;
  targetCity: string;
  education: string;
  experience: string;
  projects: string;
  skills: string;
  strengths: string;
};

type GeneratePhase = 'failed' | 'idle' | 'running' | 'succeeded';

const form = reactive<GenerateForm>({
  identity: 'worker',
  targetRole: '',
  targetCity: '',
  education: '',
  experience: '',
  projects: '',
  skills: '',
  strengths: '',
});
const phase = ref<GeneratePhase>('idle');
const loadingProfile = ref(true);
const errorMessage = ref('');
const taskResult = ref<HrmPortalRecruitAgentApi.TaskResp>();
const onlineResumeId = ref<number>();
const profile = ref<Record<string, any>>({});
const savedVersionId = ref<number>();
const savingDraft = ref(false);
const wizardStep = ref(1);
const wizardSteps = [
  { title: '你的身份', caption: '先选择当前状态' },
  { title: '求职目标', caption: '职位和城市' },
  { title: '教育经历', caption: '学校和专业' },
  { title: '工作经历', caption: '真实经历与成果' },
  { title: '项目经历', caption: '项目亮点' },
  { title: '能力优势', caption: '技能和优势' },
  { title: '生成简历', caption: '确认后开始生成' },
];

function emptyDraft(): HrmPortalRecruitAgentApi.GenerateResult {
  return {
    headline: '',
    summary: '',
    skills: [],
    experiences: [],
    projects: [],
    educationExperiences: [],
  };
}

const draft = reactive<HrmPortalRecruitAgentApi.GenerateResult>(emptyDraft());

function text(value: unknown) {
  return typeof value === 'string' ? value.trim() : '';
}

function joinLines(values: unknown[]) {
  return values.map(text).filter(Boolean).join('\n');
}

function profileToAnswers(content?: string) {
  if (!content) return;
  try {
    const parsed = JSON.parse(content) as Record<string, any>;
    profile.value = parsed;
    form.identity = parsed.identity === 'student' ? 'student' : 'worker';
    form.targetRole = text(parsed.expectedPostName || parsed.postTypeName);
    form.targetCity = Array.isArray(parsed.expectedCities)
      ? parsed.expectedCities.map(text).filter(Boolean).join('、')
      : text(parsed.city);
    form.skills = text(parsed.skills);
    form.strengths = text(parsed.strengths);
    form.experience = Array.isArray(parsed.experiences)
      ? parsed.experiences
          .map((item: Record<string, unknown>) =>
            [
              [item.company, item.position, item.start, item.end]
                .map(text)
                .filter(Boolean)
                .join(' | '),
              joinLines([item.description, item.achievements]),
            ]
              .filter(Boolean)
              .join('\n'),
          )
          .filter(Boolean)
          .join('\n\n')
      : '';
    form.projects = Array.isArray(parsed.projects)
      ? parsed.projects
          .map((item: Record<string, unknown>) =>
            [
              [item.name, item.role, item.start, item.end]
                .map(text)
                .filter(Boolean)
                .join(' | '),
              joinLines([item.description, item.achievements]),
            ]
              .filter(Boolean)
              .join('\n'),
          )
          .filter(Boolean)
          .join('\n\n')
      : '';
    form.education = Array.isArray(parsed.educationExperiences)
      ? parsed.educationExperiences
          .map((item: Record<string, unknown>) =>
            [
              [item.school, item.degree, item.major, item.start, item.end]
                .map(text)
                .filter(Boolean)
                .join(' | '),
              text(item.experience),
            ]
              .filter(Boolean)
              .join('\n'),
          )
          .filter(Boolean)
          .join('\n\n')
      : '';
  } catch {
    message.warning('在线简历内容格式异常，请重新填写生成信息');
  }
}

async function loadProfile() {
  loadingProfile.value = true;
  try {
    const resumes = (await getPortalResumeList()) || [];
    const online = resumes.find(isOnlineResume);
    onlineResumeId.value = online?.id;
    profileToAnswers(online?.content);
  } catch (error: any) {
    message.error(error?.message || '加载求职资料失败');
  } finally {
    loadingProfile.value = false;
  }
}

function emptyExperience(): HrmPortalRecruitAgentApi.ResumeGenerateExperience {
  return { company: '', position: '', start: '', end: '', description: '', achievements: '' };
}

function emptyProject(): HrmPortalRecruitAgentApi.ResumeGenerateProject {
  return { name: '', role: '', start: '', end: '', description: '', achievements: '', highlight: '' };
}

function emptyEducation(): HrmPortalRecruitAgentApi.ResumeGenerateEducation {
  return { school: '', degree: '', major: '', start: '', end: '', experience: '' };
}

function normalizeDraft(result: HrmPortalRecruitAgentApi.GenerateResult) {
  return {
    headline: text(result.headline),
    summary: text(result.summary),
    skills: Array.isArray(result.skills) ? result.skills.map(text).filter(Boolean) : [],
    experiences: Array.isArray(result.experiences)
      ? result.experiences.map((item) => ({ ...emptyExperience(), ...item }))
      : [],
    projects: Array.isArray(result.projects)
      ? result.projects.map((item) => ({ ...emptyProject(), ...item }))
      : [],
    educationExperiences: Array.isArray(result.educationExperiences)
      ? result.educationExperiences.map((item) => ({ ...emptyEducation(), ...item }))
      : [],
  };
}

function addSkill() { draft.skills.push(''); }
function removeSkill(index: number) { draft.skills.splice(index, 1); }
function addExperience() { draft.experiences.push(emptyExperience()); }
function removeExperience(index: number) { draft.experiences.splice(index, 1); }
function addProject() { draft.projects.push(emptyProject()); }
function removeProject(index: number) { draft.projects.splice(index, 1); }
function addEducation() { draft.educationExperiences.push(emptyEducation()); }
function removeEducation(index: number) { draft.educationExperiences.splice(index, 1); }

const canGenerate = computed(
  () => Boolean(form.targetRole.trim()) && phase.value !== 'running' && !loadingProfile.value,
);

async function generate() {
  if (!canGenerate.value) {
    message.warning('请先填写期望职位');
    return;
  }
  phase.value = 'running';
  errorMessage.value = '';
  savedVersionId.value = undefined;
  taskResult.value = undefined;
  try {
    const taskId = await submitResumeGenerate({
      targetRole: form.targetRole.trim(),
      targetCity: form.targetCity.trim(),
      education: form.education.trim(),
      experience: form.experience.trim(),
      projects: form.projects.trim(),
      skills: form.skills.trim(),
      strengths: form.strengths.trim(),
      idempotencyKey: buildGenerateIdempotencyKey(),
    });
    const detail = await getRecruitAgentTask(taskId);
    if (detail?.status !== 'SUCCEEDED' || !detail.result) {
      errorMessage.value = detail?.errorMessage || '模型没有返回合格的简历草稿';
      phase.value = 'failed';
      return;
    }
    const normalized = normalizeDraft(detail.result as HrmPortalRecruitAgentApi.GenerateResult);
    Object.assign(draft, normalized);
    taskResult.value = detail;
    phase.value = 'succeeded';
  } catch (error: any) {
    errorMessage.value = error?.message || 'AI 服务暂不可用，请稍后重试';
    phase.value = 'failed';
  }
}

function resetGeneration() {
  phase.value = 'idle';
  wizardStep.value = 1;
  taskResult.value = undefined;
  errorMessage.value = '';
  savedVersionId.value = undefined;
}

const wizardCanContinue = computed(() => {
  if (wizardStep.value === 2) return Boolean(form.targetRole.trim());
  return true;
});

function nextWizardStep() {
  if (!wizardCanContinue.value) {
    message.warning('请先填写期望职位');
    return;
  }
  wizardStep.value = Math.min(wizardSteps.length, wizardStep.value + 1);
}

function previousWizardStep() {
  wizardStep.value = Math.max(1, wizardStep.value - 1);
}

function selectWizardStep(step: number) {
  if (step <= wizardStep.value) wizardStep.value = step;
}

function buildSavedContent() {
  const source = { ...profile.value };
  const targetCities = form.targetCity.split(/[、,，]/).map((item) => item.trim()).filter(Boolean);
  return {
    ...source,
    identity: form.identity,
    expectedPostName: form.targetRole.trim() || source.expectedPostName || '',
    expectedCities: targetCities.length ? targetCities : source.expectedCities || [],
    strengths: draft.summary || source.strengths || '',
    skills: draft.skills.filter(Boolean).join('、'),
    experiences: draft.experiences.map((item, index) => ({
      id: `ai-${Date.now()}-${index}`,
      company: item.company,
      industry: '',
      department: '',
      position: item.position,
      start: item.start,
      end: item.end,
      description: item.description,
      achievements: item.achievements,
    })),
    projects: draft.projects.map((item, index) => ({
      id: `ai-project-${Date.now()}-${index}`,
      name: item.name,
      role: item.role,
      link: '',
      start: item.start,
      end: item.end,
      description: item.description,
      achievements: [item.achievements, item.highlight].filter(Boolean).join('；'),
      highlight: Boolean(item.highlight),
    })),
    educationExperiences: draft.educationExperiences.map((item, index) => ({
      id: `ai-education-${Date.now()}-${index}`,
      degree: item.degree,
      school: item.school,
      major: item.major,
      schoolType: '',
      start: item.start,
      end: item.end,
      experience: item.experience,
    })),
  };
}

async function saveDraft() {
  if (!onlineResumeId.value || savingDraft.value) {
    message.warning('请先保存在线求职资料，再固化 AI 简历');
    return;
  }
  savingDraft.value = true;
  try {
    savedVersionId.value = await createPortalResumeVersion({
      sourceResumeId: onlineResumeId.value,
      createType: 1,
      changeSummary: 'AI 生成后手动确认的简历草稿',
      content: JSON.stringify(buildSavedContent()),
      idempotencyKey: buildResumeVersionIdempotencyKey('ai-generate'),
    });
    message.success('已保存为新的简历版本，可在投递时选择');
  } catch (error: any) {
    message.error(error?.message || '保存简历版本失败');
  } finally {
    savingDraft.value = false;
  }
}

function backToResume() {
  void router.push({ name: 'PortalRecruit', query: { section: 'resume' } });
}

function openTemplate() {
  void router.push({ name: 'PortalRecruitAttachmentBuilder' });
}

function openMyResume() {
  backToResume();
}

function openAiAssistant() {
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function openAnalysis() {
  document.getElementById('ai-analysis')?.scrollIntoView({ behavior: 'smooth' });
}

onMounted(() => void loadProfile());
</script>

<template>
  <div class="portal-page recruit-ai-page">
    <header class="cv-platform-nav">
      <button class="cv-platform-brand" type="button" @click="backToResume"><span class="cv-platform-mark">TX</span><strong>天信简历</strong></button>
      <nav aria-label="简历工作台导航">
        <button type="button" @click="openTemplate">简历模板</button>
        <button type="button" @click="openAnalysis">简历分析</button>
        <button type="button" @click="openMyResume">我的简历</button>
        <button class="active" type="button" @click="openAiAssistant">AI简历助手</button>
      </nav>
      <div class="cv-platform-account"><span>{{ displayName }}</span><button type="button" @click="backToResume">返回招聘</button></div>
    </header>
    <main class="recruit-ai-main">
      <header class="recruit-ai-heading">
        <button type="button" class="page-back" @click="backToResume">
          <IconifyIcon icon="lucide:arrow-left" /><span>返回在线简历</span>
        </button>
        <div class="heading-copy"><p class="heading-eyebrow">AI CAREER ASSISTANT</p><h1>AI简历</h1><p>智能简历生成</p></div>
        <div class="heading-badge"><IconifyIcon icon="lucide:sparkles" /><span>回答几个问题，生成一份可编辑的简历草稿</span></div>
      </header>

      <section class="generator-shell">
        <div class="generator-topline"><div><span class="section-kicker">SMART RESUME</span><h2>先说说你的求职目标</h2><p>AI 只整理你提供的经历，生成后每一项都可以继续修改。</p></div><span class="privacy-note"><IconifyIcon icon="lucide:shield-check" /> 联系方式不会发送给 AI</span></div>
        <div class="generator-layout">
          <aside class="generator-steps" aria-label="生成步骤">
            <div class="step active"><span>01</span><strong>回答问题</strong><small>告诉 AI 你的经历</small></div>
            <div class="step" :class="{ active: phase === 'succeeded' }"><span>02</span><strong>编辑草稿</strong><small>逐项检查和补充</small></div>
            <div class="step" :class="{ active: Boolean(savedVersionId) }"><span>03</span><strong>保存版本</strong><small>投递时直接选择</small></div>
          </aside>
          <div class="generator-content">
            <form v-if="phase === 'idle' || phase === 'running'" class="generator-form" @submit.prevent="generate">
              <div class="form-row two-col"><label class="generator-field"><span>期望职位 <b>*</b></span><input v-model="form.targetRole" maxlength="100" placeholder="例如：项目经理" :disabled="phase === 'running'" /></label><label class="generator-field"><span>期望城市</span><input v-model="form.targetCity" maxlength="100" placeholder="例如：北京、天津" :disabled="phase === 'running'" /></label></div>
              <label class="generator-field"><span>教育经历</span><textarea v-model="form.education" rows="3" maxlength="2000" placeholder="学校、学历、专业、在校经历，按你的实际情况填写" :disabled="phase === 'running'" /></label>
              <label class="generator-field"><span>工作 / 实习经历</span><textarea v-model="form.experience" rows="5" maxlength="8000" placeholder="公司、职位、工作时间、负责内容和实际成果。没有工作经历可以填写实习或校园经历" :disabled="phase === 'running'" /></label>
              <label class="generator-field"><span>项目经历</span><textarea v-model="form.projects" rows="4" maxlength="6000" placeholder="项目名称、你的角色、做了什么、得到什么结果" :disabled="phase === 'running'" /></label>
              <div class="form-row two-col"><label class="generator-field"><span>专业技能</span><textarea v-model="form.skills" rows="4" maxlength="3000" placeholder="例如：施工组织、AutoCAD、项目管理" :disabled="phase === 'running'" /></label><label class="generator-field"><span>个人优势</span><textarea v-model="form.strengths" rows="4" maxlength="3000" placeholder="你最擅长什么？用真实经历说明" :disabled="phase === 'running'" /></label></div>
              <div class="generator-footer"><small v-if="loadingProfile">正在读取在线求职资料…</small><small v-else>已有内容已自动填入，你可以直接修改后生成。</small><button type="submit" class="generate-button" :disabled="!canGenerate"><IconifyIcon icon="lucide:sparkles" />{{ phase === 'running' ? '正在生成…' : '生成 AI 简历' }}</button></div>
            </form>

            <section v-else-if="phase === 'failed'" class="generation-failed"><IconifyIcon icon="lucide:triangle-alert" /><h3>这次没有生成成功</h3><p>{{ errorMessage }}</p><button type="button" class="outline-button" @click="resetGeneration">返回修改回答</button></section>

            <section v-else class="draft-workspace">
              <div class="draft-toolbar"><div><strong>草稿已生成</strong><small v-if="taskResult?.model">模型：{{ taskResult.model }}</small></div><div class="draft-toolbar-actions"><button type="button" class="outline-button" @click="resetGeneration">重新生成</button><button type="button" class="save-button" :disabled="savingDraft" @click="saveDraft"><IconifyIcon icon="lucide:save" />{{ savingDraft ? '保存中…' : '保存为简历版本' }}</button></div></div>
              <p class="draft-note"><IconifyIcon icon="lucide:info" /> AI 生成内容仅来自你的回答，请逐项核对后再保存。姓名、电话和邮箱沿用在线求职资料。</p>
              <div class="draft-section"><div class="draft-section-title"><strong>简历标题</strong><small>可以直接修改</small></div><input v-model="draft.headline" class="draft-input" maxlength="200" /></div>
              <div class="draft-section"><div class="draft-section-title"><strong>个人简介</strong></div><textarea v-model="draft.summary" class="draft-input" rows="4" maxlength="1200" /></div>
              <div class="draft-section"><div class="draft-section-title"><strong>专业技能</strong><button type="button" class="text-button" @click="addSkill"><IconifyIcon icon="lucide:plus" /> 添加技能</button></div><div class="skill-editor"><div v-for="(_, index) in draft.skills" :key="index" class="skill-row"><input v-model="draft.skills[index]" maxlength="500" /><button type="button" aria-label="删除技能" @click="removeSkill(index)"><IconifyIcon icon="lucide:x" /></button></div><p v-if="!draft.skills.length" class="empty-draft">没有识别到技能，可手动添加。</p></div></div>
              <div class="draft-section"><div class="draft-section-title"><strong>工作经历</strong><button type="button" class="text-button" @click="addExperience"><IconifyIcon icon="lucide:plus" /> 添加经历</button></div><article v-for="(item, index) in draft.experiences" :key="index" class="draft-entry"><div class="entry-header"><strong>经历 {{ index + 1 }}</strong><button type="button" aria-label="删除工作经历" @click="removeExperience(index)"><IconifyIcon icon="lucide:trash-2" /></button></div><div class="form-row two-col"><label class="generator-field"><span>公司</span><input v-model="item.company" /></label><label class="generator-field"><span>职位</span><input v-model="item.position" /></label><label class="generator-field"><span>开始时间</span><input v-model="item.start" /></label><label class="generator-field"><span>结束时间</span><input v-model="item.end" /></label></div><label class="generator-field"><span>工作内容</span><textarea v-model="item.description" rows="3" /></label><label class="generator-field"><span>实际成果</span><textarea v-model="item.achievements" rows="2" /></label></article><p v-if="!draft.experiences.length" class="empty-draft">没有识别到工作经历，可手动添加。</p></div>
              <div class="draft-section"><div class="draft-section-title"><strong>项目经历</strong><button type="button" class="text-button" @click="addProject"><IconifyIcon icon="lucide:plus" /> 添加项目</button></div><article v-for="(item, index) in draft.projects" :key="index" class="draft-entry"><div class="entry-header"><strong>项目 {{ index + 1 }}</strong><button type="button" aria-label="删除项目" @click="removeProject(index)"><IconifyIcon icon="lucide:trash-2" /></button></div><div class="form-row two-col"><label class="generator-field"><span>项目名称</span><input v-model="item.name" /></label><label class="generator-field"><span>你的角色</span><input v-model="item.role" /></label><label class="generator-field"><span>开始时间</span><input v-model="item.start" /></label><label class="generator-field"><span>结束时间</span><input v-model="item.end" /></label></div><label class="generator-field"><span>项目内容</span><textarea v-model="item.description" rows="3" /></label><label class="generator-field"><span>成果与亮点</span><textarea v-model="item.achievements" rows="2" /></label></article><p v-if="!draft.projects.length" class="empty-draft">没有识别到项目经历，可手动添加。</p></div>
              <div class="draft-section"><div class="draft-section-title"><strong>教育经历</strong><button type="button" class="text-button" @click="addEducation"><IconifyIcon icon="lucide:plus" /> 添加教育经历</button></div><article v-for="(item, index) in draft.educationExperiences" :key="index" class="draft-entry"><div class="entry-header"><strong>教育经历 {{ index + 1 }}</strong><button type="button" aria-label="删除教育经历" @click="removeEducation(index)"><IconifyIcon icon="lucide:trash-2" /></button></div><div class="form-row two-col"><label class="generator-field"><span>学校</span><input v-model="item.school" /></label><label class="generator-field"><span>学历</span><input v-model="item.degree" /></label><label class="generator-field"><span>专业</span><input v-model="item.major" /></label><label class="generator-field"><span>时间</span><input v-model="item.start" placeholder="开始 - 结束" /></label></div><label class="generator-field"><span>在校经历</span><textarea v-model="item.experience" rows="2" /></label></article><p v-if="!draft.educationExperiences.length" class="empty-draft">没有识别到教育经历，可手动添加。</p></div>
              <p v-if="savedVersionId" class="saved-version"><IconifyIcon icon="lucide:circle-check" /> 已保存为简历版本 #{{ savedVersionId }}</p>
            </section>
          </div>
        </div>
      </section>

      <section id="ai-analysis" class="analysis-section"><div class="analysis-heading"><div><span class="section-kicker">AI REVIEW</span><h2>简历分析</h2></div><p>选择已有简历和职位，查看真实分析结果。</p></div><ResumeAiPanel :open="true" :embedded="true" /></section>
    </main>
  </div>
</template>

<style scoped>
.recruit-ai-page { min-height: 100vh; background: #f4f8f8; }
.recruit-ai-main { width: min(1220px, calc(100% - 40px)); margin: 0 auto; padding: 28px 0 70px; }
.recruit-ai-heading { display: flex; align-items: flex-end; gap: 24px; margin-bottom: 22px; }
.page-back { display: inline-flex; align-items: center; gap: 6px; align-self: flex-start; min-height: 34px; padding: 0; border: 0; background: transparent; color: #547179; cursor: pointer; font-size: 14px; }
.page-back:hover { color: #c93227; }
.heading-copy { display: grid; gap: 4px; }
.heading-eyebrow, .section-kicker { margin: 0; color: #98abad; font-size: 11px; letter-spacing: .14em; }
.heading-copy h1 { margin: 0; color: #173e49; font-size: 30px; line-height: 1.15; }
.heading-copy p:last-child { margin: 0; color: #688087; font-size: 14px; }
.heading-badge, .privacy-note { display: inline-flex; align-items: center; gap: 7px; color: #0f766e; font-size: 12px; }
.heading-badge { margin-left: auto; padding: 9px 12px; border: 1px solid #d4ece8; border-radius: 8px; background: #eefaf7; }
.generator-shell, .analysis-section { border: 1px solid #dfeaea; border-radius: 12px; background: #fff; box-shadow: 0 12px 28px rgb(23 52 61 / 6%); }
.generator-shell { overflow: hidden; }
.generator-topline { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; padding: 26px 30px 22px; border-bottom: 1px solid #e6eeee; background: #fbfdfd; }
.generator-topline h2, .analysis-heading h2 { margin: 5px 0 0; color: #173e49; font-size: 22px; }
.generator-topline p, .analysis-heading p { margin: 7px 0 0; color: #71878c; font-size: 13px; }
.privacy-note { padding-top: 4px; white-space: nowrap; }
.generator-layout { display: grid; grid-template-columns: 210px minmax(0, 1fr); }
.generator-steps { display: grid; align-content: start; gap: 4px; padding: 26px 16px; border-right: 1px solid #e6eeee; background: #f8fbfb; }
.step { display: grid; grid-template-columns: 34px 1fr; column-gap: 9px; padding: 12px 9px; border-radius: 8px; color: #91a4a8; }
.step span { grid-row: span 2; color: #b3c1c3; font-size: 11px; font-weight: 700; }
.step strong { font-size: 13px; font-weight: 600; }
.step small { margin-top: 3px; color: #9aabad; font-size: 11px; }
.step.active { color: #0f766e; background: #eaf7f4; }
.step.active span { color: #0f9d91; }
.generator-content { min-width: 0; padding: 24px 28px 28px; }
.generator-form, .draft-workspace { display: grid; gap: 17px; }
.form-row { display: grid; gap: 14px; }
.two-col { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.generator-field { display: grid; gap: 7px; color: #375860; font-size: 13px; font-weight: 600; }
.generator-field span b { color: #c93227; font-weight: 600; }
.generator-field input, .generator-field textarea, .draft-input, .skill-row input { width: 100%; box-sizing: border-box; border: 1px solid #d9e6e6; border-radius: 6px; padding: 9px 11px; outline: none; background: #fff; color: #203e46; font: inherit; font-size: 13px; font-weight: 400; line-height: 1.55; resize: vertical; }
.generator-field input:focus, .generator-field textarea:focus, .draft-input:focus, .skill-row input:focus { border-color: #0f9d91; box-shadow: 0 0 0 2px #e4f6f3; }
.generator-field input:disabled, .generator-field textarea:disabled { background: #f6f9f9; cursor: wait; }
.generator-footer { display: flex; align-items: center; justify-content: space-between; gap: 14px; padding-top: 3px; }
.generator-footer small { color: #8b9da1; font-size: 12px; }
.generate-button, .save-button, .outline-button { display: inline-flex; align-items: center; justify-content: center; gap: 7px; min-height: 38px; padding: 0 16px; border-radius: 7px; cursor: pointer; font: inherit; font-size: 13px; }
.generate-button, .save-button { border: 1px solid #0f766e; background: #0f766e; color: #fff; }
.generate-button:disabled, .save-button:disabled { cursor: wait; opacity: .55; }
.outline-button { border: 1px solid #cddede; background: #fff; color: #46636a; }
.outline-button:hover { border-color: #0f9d91; color: #0f766e; }
.generation-failed { display: grid; justify-items: center; gap: 9px; padding: 42px 16px; text-align: center; }
.generation-failed > :deep(svg) { color: #d97706; font-size: 28px; }
.generation-failed h3 { margin: 0; color: #173e49; font-size: 16px; }
.generation-failed p { max-width: 520px; margin: 0 0 7px; color: #7d9299; font-size: 13px; line-height: 1.7; }
.draft-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 14px; padding-bottom: 3px; }
.draft-toolbar > div:first-child { display: grid; gap: 4px; }
.draft-toolbar strong { color: #173e49; font-size: 16px; }
.draft-toolbar small { color: #9aabad; font-size: 11px; }
.draft-toolbar-actions { display: flex; gap: 8px; }
.draft-note, .saved-version { display: flex; align-items: flex-start; gap: 7px; margin: 0; padding: 10px 12px; border-radius: 7px; color: #547179; background: #f2faf8; font-size: 12px; line-height: 1.6; }
.saved-version { color: #047857; background: #eaf7ee; }
.draft-section { display: grid; gap: 11px; padding: 16px; border: 1px solid #e2ecec; border-radius: 9px; background: #fff; }
.draft-section-title, .entry-header { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.draft-section-title strong, .entry-header strong { color: #36565e; font-size: 14px; }
.draft-section-title small { color: #9aabad; font-size: 11px; }
.text-button { display: inline-flex; align-items: center; gap: 4px; padding: 0; border: 0; background: transparent; color: #0f766e; cursor: pointer; font: inherit; font-size: 12px; }
.skill-editor { display: grid; gap: 7px; }
.skill-row { display: flex; gap: 7px; }
.skill-row button, .entry-header button { display: grid; width: 28px; height: 28px; flex: 0 0 auto; place-items: center; padding: 0; border: 0; background: transparent; color: #9aabad; cursor: pointer; }
.skill-row button:hover, .entry-header button:hover { color: #c93227; }
.draft-entry { display: grid; gap: 12px; padding: 13px; border: 1px solid #e5eeee; border-radius: 7px; background: #fbfdfd; }
.empty-draft { margin: 0; color: #9aabad; font-size: 12px; }
.analysis-section { margin-top: 24px; padding: 24px; }
.analysis-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; margin-bottom: 17px; }
.analysis-heading p { margin: 0; }
@media (max-width: 820px) { .recruit-ai-main { width: min(100% - 24px, 680px); padding-top: 18px; } .recruit-ai-heading { display: grid; gap: 12px; align-items: start; } .heading-badge { margin-left: 0; } .generator-topline, .analysis-heading { display: grid; gap: 9px; } .privacy-note { padding-top: 0; } .generator-layout { grid-template-columns: 1fr; } .generator-steps { grid-template-columns: repeat(3, minmax(0, 1fr)); border-right: 0; border-bottom: 1px solid #e6eeee; padding: 12px; } .step { grid-template-columns: 26px 1fr; padding: 9px; } .step small { display: none; } }
@media (max-width: 580px) { .generator-topline, .generator-content, .analysis-section { padding-left: 16px; padding-right: 16px; } .two-col { grid-template-columns: 1fr; } .generator-footer, .draft-toolbar, .draft-toolbar-actions { align-items: stretch; flex-direction: column; } .draft-toolbar-actions { display: grid; grid-template-columns: 1fr 1fr; } .draft-toolbar-actions button { padding: 0 8px; } .generator-footer small { line-height: 1.5; } }

.cv-platform-nav { display: flex; align-items: center; min-height: 58px; padding: 0 max(24px, calc((100vw - 1240px) / 2)); gap: 34px; background: #0b0d16; color: #f8fafc; }
.cv-platform-brand { display: inline-flex; align-items: center; gap: 9px; padding: 0; border: 0; background: transparent; color: inherit; cursor: pointer; font: inherit; }
.cv-platform-mark { display: grid; width: 30px; height: 30px; place-items: center; border-radius: 9px; background: #41d3a3; color: #07101a; font-size: 12px; font-weight: 800; }
.cv-platform-brand strong { font-size: 16px; letter-spacing: .02em; }
.cv-platform-nav nav { display: flex; align-items: stretch; align-self: stretch; gap: 26px; }
.cv-platform-nav nav button, .cv-platform-account button { position: relative; border: 0; background: transparent; color: #cbd5e1; cursor: pointer; font: inherit; font-size: 13px; }
.cv-platform-nav nav button { padding: 0 2px; }
.cv-platform-nav nav button::after { position: absolute; right: 0; bottom: 0; left: 0; height: 2px; background: #a6f34f; content: ''; opacity: 0; }
.cv-platform-nav nav button:hover, .cv-platform-nav nav button.active { color: #fff; }
.cv-platform-nav nav button.active::after { opacity: 1; }
.cv-platform-account { display: inline-flex; align-items: center; gap: 16px; margin-left: auto; color: #aeb7c7; font-size: 12px; }
.cv-platform-account button { padding: 7px 12px; border: 1px solid #354052; border-radius: 999px; }
.cv-platform-account button:hover { border-color: #a6f34f; color: #d8ffae; }
.recruit-ai-page { background: #050817; }
.recruit-ai-main { padding-top: 30px; }
.recruit-ai-heading { color: #f8fafc; }
.recruit-ai-heading .heading-copy h1 { color: #fff; }
.recruit-ai-heading .heading-copy p:last-child, .recruit-ai-heading .page-back { color: #aeb7c7; }
.recruit-ai-heading .heading-badge { border-color: #2f5b5c; background: #102a2b; color: #9df0c4; }
.generator-shell { border-color: #293349; background: #101625; box-shadow: 0 18px 60px rgb(0 0 0 / 24%); }
.generator-topline { border-color: #293349; background: #131b2d; }
.generator-topline h2, .analysis-heading h2 { color: #f8fafc; }
.generator-topline p, .analysis-heading p { color: #9eabc0; }
.generator-layout { background: #101625; }
.generator-steps { border-color: #293349; background: #0d1321; }
.generator-content { color: #e2e8f0; }
.step { color: #77849a; }
.step.active { background: #153033; color: #a6f34f; }
.generator-field { color: #dbe5ef; }
.generator-field input, .generator-field textarea, .draft-input, .skill-row input { border-color: #334158; background: #0b1220; color: #f8fafc; }
.generator-field input:focus, .generator-field textarea:focus, .draft-input:focus, .skill-row input:focus { border-color: #57d6b0; box-shadow: 0 0 0 2px rgb(87 214 176 / 18%); }
.generator-field input:disabled, .generator-field textarea:disabled { background: #111a2a; }
.privacy-note { color: #9df0c4; }
.generate-button, .save-button { border-color: #70d86d; background: #70d86d; color: #07101a; font-weight: 700; }
.outline-button { border-color: #3b4b62; background: transparent; color: #d5deea; }
.outline-button:hover { border-color: #9df0c4; color: #9df0c4; }
.draft-section, .draft-entry { border-color: #334158; background: #111a2a; }
.draft-section-title strong, .entry-header strong, .draft-toolbar strong { color: #f8fafc; }
.text-button { color: #9df0c4; }
.draft-note { color: #b8c8d2; background: #123031; }
.saved-version { color: #b7f5cb; background: #12342b; }
.analysis-section { border-color: #293349; background: #101625; }
.section-kicker, .heading-eyebrow, .ai-kicker { color: #8de9b7; }
@media (max-width: 700px) { .cv-platform-nav { flex-wrap: wrap; gap: 0 18px; padding: 0 16px; } .cv-platform-nav nav { order: 3; width: 100%; height: 42px; gap: 18px; overflow-x: auto; } .cv-platform-nav nav button { white-space: nowrap; } .cv-platform-account { margin-left: auto; } }
</style>
