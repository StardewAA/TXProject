<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';
import { useUserStore } from '@vben/stores';

import { message } from 'ant-design-vue';

import {
  getPortalResumeList,
  isOnlineResume,
  uploadGeneratedResume,
} from '#/api/hrm/portal/resume';
import { uploadHrmRecruitFile } from '#/api/hrm/portal/recruit/file';


type TemplateKey = 'classic' | 'modern' | 'compact';
type WorkItem = {
  company: string;
  position: string;
  period: string;
  description: string;
};
type EducationItem = {
  school: string;
  degree: string;
  major: string;
  period: string;
};
type BuilderForm = {
  name: string;
  position: string;
  phone: string;
  email: string;
  city: string;
  strengths: string;
  skills: string;
  projects: string;
  works: WorkItem[];
  education: EducationItem[];
};

const router = useRouter();
const userStore = useUserStore();
const displayName = computed(
  () => userStore.userInfo?.nickname || userStore.userInfo?.username || '用户',
);
const loading = ref(true);
const saving = ref(false);
const template = ref<TemplateKey>('classic');
const title = ref('我的附件简历');
const form = reactive<BuilderForm>(createDefaultForm());

const templates: Array<{ key: TemplateKey; label: string; description: string }> = [
  { key: 'classic', label: '经典商务', description: '信息清晰，适合正式投递' },
  { key: 'modern', label: '现代清爽', description: '突出个人优势与技能' },
  { key: 'compact', label: '高效一页', description: '适合快速浏览与批量投递' },
];

function createDefaultForm(): BuilderForm {
  return {
    name: '', position: '', phone: '', email: '', city: '', strengths: '', skills: '', projects: '',
    works: [], education: [],
  };
}

function stringValue(value: unknown) {
  return typeof value === 'string' ? value : value == null ? '' : String(value);
}

function normalizeWork(value: unknown): WorkItem {
  const item = value && typeof value === 'object' ? value as Record<string, unknown> : {};
  return {
    company: stringValue(item.company),
    position: stringValue(item.position),
    period: [stringValue(item.start), stringValue(item.end)].filter(Boolean).join(' - '),
    description: [stringValue(item.description), stringValue(item.achievements)].filter(Boolean).join('\n'),
  };
}

function normalizeEducation(value: unknown): EducationItem {
  const item = value && typeof value === 'object' ? value as Record<string, unknown> : {};
  return {
    school: stringValue(item.school),
    degree: stringValue(item.degree || item.education),
    major: stringValue(item.major),
    period: [stringValue(item.start), stringValue(item.end)].filter(Boolean).join(' - '),
  };
}

function loadProfileContent(content?: string) {
  if (!content) return;
  try {
    const value = JSON.parse(content) as Record<string, unknown>;
    form.name = stringValue(value.name);
    form.position = stringValue(value.expectedPostName || value.postTypeName);
    form.phone = stringValue(value.mobile || value.phone);
    form.email = stringValue(value.email);
    form.city = Array.isArray(value.expectedCities) ? value.expectedCities.map(stringValue).join('、') : stringValue(value.city);
    form.strengths = stringValue(value.strengths);
    form.skills = stringValue(value.skills);
    form.projects = Array.isArray(value.projects)
      ? value.projects.map((item) => {
          const project = item && typeof item === 'object' ? item as Record<string, unknown> : {};
          return [stringValue(project.name), stringValue(project.role), stringValue(project.description), stringValue(project.achievements)].filter(Boolean).join('\n');
        }).join('\n\n')
      : '';
    form.works = Array.isArray(value.experiences) ? value.experiences.map(normalizeWork) : [];
    form.education = Array.isArray(value.educationExperiences) ? value.educationExperiences.map(normalizeEducation) : [];
    if (!form.works.length && (value.company || value.position)) form.works = [normalizeWork(value)];
    if (!form.education.length && (value.school || value.major || value.education)) form.education = [normalizeEducation(value)];
  } catch {
    message.warning('在线简历内容格式异常，请重新编辑附件内容');
  }
}

async function loadProfile() {
  loading.value = true;
  try {
    const resumes = await getPortalResumeList();
    loadProfileContent(resumes.find(isOnlineResume)?.content);
  } catch (error: any) {
    message.error(error?.message || '加载求职资料失败');
  } finally {
    loading.value = false;
  }
}

function addWork() {
  form.works.push({ company: '', position: '', period: '', description: '' });
}

function removeWork(index: number) {
  form.works.splice(index, 1);
}

function addEducation() {
  form.education.push({ school: '', degree: '', major: '', period: '' });
}

function removeEducation(index: number) {
  form.education.splice(index, 1);
}

function serializeBuilder() {
  return JSON.stringify({ template: template.value, ...form, generatedAt: new Date().toISOString() });
}

function displayLines(value: string) {
  return value.split(/\r?\n/).filter(Boolean);
}

async function buildDocx() {
  const docxModule = await import('docx');
  const docx = (docxModule as any).default ?? docxModule;
  const accent = template.value === 'modern' ? '0F766E' : template.value === 'compact' ? '334155' : 'B45309';
  const children: any[] = [
    new docx.Paragraph({ text: form.name || '我的简历', heading: docx.HeadingLevel.TITLE, alignment: docx.AlignmentType.CENTER }),
    new docx.Paragraph({ text: [form.position, form.phone, form.email, form.city].filter(Boolean).join('  |  '), alignment: docx.AlignmentType.CENTER }),
  ];
  const addSection = (heading: string, lines: string[]) => {
    if (!lines.length) return;
    children.push(new docx.Paragraph({ children: [new docx.TextRun({ text: heading, bold: true, color: accent, size: 26 })] }));
    for (const line of lines) children.push(new docx.Paragraph({ text: line, spacing: { after: 90 } }));
  };
  addSection('个人优势', displayLines(form.strengths));
  addSection('专业技能', displayLines(form.skills));
  addSection('工作经历', form.works.flatMap((item) => [
    [item.company, item.position, item.period].filter(Boolean).join('  |  '),
    ...displayLines(item.description),
  ].filter(Boolean)));
  addSection('项目经历', displayLines(form.projects));
  addSection('教育经历', form.education.map((item) => [item.school, item.degree, item.major, item.period].filter(Boolean).join('  |  ')));
  const document = new docx.Document({ sections: [{ children }] });
  return docx.Packer.toBlob(document);
}

async function downloadDraft() {
  if (!form.name.trim()) {
    message.warning('请先填写姓名');
    return;
  }
  try {
    const blob = await buildDocx();
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `${title.value.trim() || form.name || '我的附件简历'}.docx`;
    anchor.click();
    URL.revokeObjectURL(url);
    message.success('附件简历已下载');
  } catch (error: any) {
    message.error(error?.message || '下载失败，请稍后重试');
  }
}

async function saveAttachment() {
  if (saving.value) return;
  if (!form.name.trim() || (!form.phone.trim() && !form.email.trim())) {
    message.warning('请至少填写姓名，以及电话或邮箱');
    return;
  }
  saving.value = true;
  try {
    const blob = await buildDocx();
    const fileName = `${title.value.trim() || form.name || '我的附件简历'}.docx`;
    const fileUrl = await uploadHrmRecruitFile(
      new File([blob], fileName, { type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' }),
      'resume',
    );
    await uploadGeneratedResume({
      title: title.value.trim() || form.name || '我的附件简历',
      fileName,
      fileUrl,
      content: serializeBuilder(),
    });
    message.success('附件简历已保存，可在投递时直接选择');
  } catch (error: any) {
    message.error(error?.message || '保存失败，请稍后重试');
  } finally {
    saving.value = false;
  }
}

function backToResume() {
  void router.push({ name: 'PortalRecruit', query: { section: 'resume' } });
}

function openAi() {
  void router.push({ name: 'PortalRecruitResumeAi' });
}

function openAnalysis() {
  void router.push({ name: 'PortalRecruitResumeAi', hash: '#ai-analysis' });
}

function openMyResume() {
  backToResume();
}

onMounted(() => void loadProfile());
</script>

<template>
  <div class="portal-page attachment-builder-page">
    <RecruitTopbar :active="'resume'" :display-name="displayName" :themed="true" />
    <main class="builder-main">
      <header class="builder-header">
        <button type="button" class="page-back" @click="backToResume">
          <IconifyIcon icon="lucide:arrow-left" />
          <span>返回在线简历</span>
        </button>
        <div>
          <p class="heading-eyebrow">RESUME TEMPLATE STUDIO</p>
          <h1>附件简历制作</h1>
          <p>打造HR的专业简历</p>
        </div>
        <div class="builder-actions">
          <button type="button" class="secondary-action" @click="downloadDraft">
            <IconifyIcon icon="lucide:download" /> 下载 Word
          </button>
          <button type="button" class="primary-action" :disabled="saving || loading" @click="saveAttachment">
            <IconifyIcon icon="lucide:save" /> {{ saving ? '保存中…' : '保存附件简历' }}
          </button>
        </div>
      </header>

      <div v-if="loading" class="builder-loading">正在加载在线简历…</div>
      <div v-else class="builder-layout">
        <section class="builder-editor">
          <div class="editor-section template-section">
            <div class="section-heading"><strong>选择模板</strong><small>实时预览，保存后可直接投递</small></div>
            <div class="template-grid">
              <button v-for="item in templates" :key="item.key" type="button" class="template-card" :class="{ active: template === item.key }" @click="template = item.key">
                <span class="template-swatch" :class="`swatch-${item.key}`"></span>
                <strong>{{ item.label }}</strong><small>{{ item.description }}</small>
              </button>
            </div>
            <label class="field"><span>附件名称</span><input v-model="title" maxlength="60" /></label>
          </div>

          <div class="editor-section">
            <div class="section-heading"><strong>基本信息</strong><small>招聘方会优先查看这部分</small></div>
            <div class="field-grid">
              <label class="field"><span>姓名</span><input v-model="form.name" placeholder="请输入姓名" /></label>
              <label class="field"><span>期望职位</span><input v-model="form.position" placeholder="例如：项目经理" /></label>
              <label class="field"><span>电话</span><input v-model="form.phone" placeholder="请输入联系电话" /></label>
              <label class="field"><span>邮箱</span><input v-model="form.email" placeholder="请输入邮箱" /></label>
              <label class="field field-wide"><span>期望城市</span><input v-model="form.city" placeholder="例如：北京、天津" /></label>
            </div>
          </div>

          <div class="editor-section">
            <div class="section-heading"><strong>个人优势与技能</strong><small>每行一条，便于HR快速扫描</small></div>
            <label class="field"><span>个人优势</span><textarea v-model="form.strengths" rows="4" placeholder="填写与你应聘岗位相关的优势和成果" /></label>
            <label class="field"><span>专业技能</span><textarea v-model="form.skills" rows="3" placeholder="填写技能、证书或工具，建议用换行分隔" /></label>
          </div>

          <div class="editor-section">
            <div class="section-heading"><strong>工作经历</strong><button type="button" class="text-action" @click="addWork"><IconifyIcon icon="lucide:plus" /> 添加经历</button></div>
            <article v-for="(item, index) in form.works" :key="index" class="repeat-editor">
              <div class="repeat-head"><strong>经历 {{ index + 1 }}</strong><button type="button" aria-label="删除工作经历" @click="removeWork(index)"><IconifyIcon icon="lucide:trash-2" /></button></div>
              <div class="field-grid"><label class="field"><span>公司</span><input v-model="item.company" /></label><label class="field"><span>职位</span><input v-model="item.position" /></label><label class="field field-wide"><span>时间</span><input v-model="item.period" placeholder="例如：2022.03 - 至今" /></label><label class="field field-wide"><span>工作内容与业绩</span><textarea v-model="item.description" rows="3" /></label></div>
            </article>
            <p v-if="!form.works.length" class="empty-tip">还没有工作经历，可点击右侧“添加经历”。</p>
          </div>

          <div class="editor-section">
            <div class="section-heading"><strong>项目经历</strong><small>可选</small></div>
            <textarea v-model="form.projects" rows="5" placeholder="填写项目名称、职责和结果，每个项目之间空一行" />
          </div>

          <div class="editor-section">
            <div class="section-heading"><strong>教育经历</strong><button type="button" class="text-action" @click="addEducation"><IconifyIcon icon="lucide:plus" /> 添加教育经历</button></div>
            <article v-for="(item, index) in form.education" :key="index" class="repeat-editor">
              <div class="repeat-head"><strong>教育经历 {{ index + 1 }}</strong><button type="button" aria-label="删除教育经历" @click="removeEducation(index)"><IconifyIcon icon="lucide:trash-2" /></button></div>
              <div class="field-grid"><label class="field"><span>学校</span><input v-model="item.school" /></label><label class="field"><span>学历</span><input v-model="item.degree" /></label><label class="field"><span>专业</span><input v-model="item.major" /></label><label class="field"><span>时间</span><input v-model="item.period" /></label></div>
            </article>
            <p v-if="!form.education.length" class="empty-tip">还没有教育经历，可点击右侧“添加教育经历”。</p>
          </div>
        </section>

        <aside class="builder-preview-wrap">
          <div class="preview-label"><strong>简历预览</strong><small>{{ templates.find((item) => item.key === template)?.label }}</small></div>
          <article class="resume-preview" :class="`preview-${template}`">
            <header class="preview-header"><h2>{{ form.name || '你的姓名' }}</h2><p>{{ [form.position, form.phone, form.email, form.city].filter(Boolean).join('  |  ') || '期望职位  |  联系电话  |  邮箱' }}</p></header>
            <section v-if="form.strengths" class="preview-section"><h3>个人优势</h3><p v-for="(line, index) in displayLines(form.strengths)" :key="index">{{ line }}</p></section>
            <section v-if="form.skills" class="preview-section"><h3>专业技能</h3><p v-for="(line, index) in displayLines(form.skills)" :key="index">{{ line }}</p></section>
            <section v-if="form.works.length" class="preview-section"><h3>工作经历</h3><div v-for="(item, index) in form.works" :key="index" class="preview-entry"><strong>{{ item.company || '公司名称' }}<span>{{ item.position }}</span></strong><small>{{ item.period }}</small><p v-for="(line, lineIndex) in displayLines(item.description)" :key="lineIndex">{{ line }}</p></div></section>
            <section v-if="form.projects" class="preview-section"><h3>项目经历</h3><p v-for="(line, index) in displayLines(form.projects)" :key="index">{{ line }}</p></section>
            <section v-if="form.education.length" class="preview-section"><h3>教育经历</h3><div v-for="(item, index) in form.education" :key="index" class="preview-entry"><strong>{{ item.school || '学校名称' }}<span>{{ [item.degree, item.major].filter(Boolean).join(' · ') }}</span></strong><small>{{ item.period }}</small></div></section>
            <div v-if="!form.strengths && !form.skills && !form.works.length && !form.projects && !form.education.length" class="preview-empty">填写左侧内容后，这里会实时生成附件简历预览</div>
          </article>
        </aside>
      </div>
    </main>
  </div>
</template>

<style scoped>
.attachment-builder-page { min-height: 100vh; background: #f5f8f8; }
.builder-main { width: min(1240px, calc(100% - 40px)); margin: 0 auto; padding: 26px 0 60px; }
.builder-header { display: grid; grid-template-columns: 180px minmax(0, 1fr) auto; align-items: end; gap: 20px; margin-bottom: 22px; }
.page-back { display: inline-flex; align-items: center; align-self: start; gap: 6px; min-height: 34px; padding: 0; border: 0; background: transparent; color: #547179; cursor: pointer; font-size: 14px; }
.page-back:hover { color: #c93227; }
.heading-eyebrow { margin: 0 0 4px; color: #9bafb2; font-size: 11px; letter-spacing: .14em; }
.builder-header h1 { margin: 0; color: #173e49; font-size: 30px; line-height: 1.15; }
.builder-header p:last-child { margin: 5px 0 0; color: #688087; font-size: 14px; }
.builder-actions { display: flex; gap: 9px; }
.builder-actions button { display: inline-flex; align-items: center; gap: 7px; min-height: 38px; padding: 0 14px; border-radius: 7px; cursor: pointer; font: inherit; font-size: 13px; }
.secondary-action { border: 1px solid #cddfe0; background: #fff; color: #46636a; }
.primary-action { border: 1px solid #0f766e; background: #0f766e; color: #fff; }
.primary-action:disabled { cursor: wait; opacity: .6; }
.builder-layout { display: grid; grid-template-columns: minmax(0, 1fr) minmax(360px, 470px); align-items: start; gap: 22px; }
.builder-editor { display: grid; gap: 14px; }
.editor-section { display: grid; gap: 14px; padding: 18px; border: 1px solid #e0ebeb; border-radius: 10px; background: #fff; }
.section-heading { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; }
.section-heading strong { color: #173e49; font-size: 15px; }
.section-heading small { color: #8a9da1; font-size: 12px; }
.template-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 9px; }
.template-card { display: grid; gap: 6px; padding: 11px; border: 1px solid #dce8e8; border-radius: 8px; background: #fff; color: #36565e; cursor: pointer; text-align: left; }
.template-card.active { border-color: #0f766e; box-shadow: 0 0 0 2px #d9f1ec; }
.template-card strong { font-size: 13px; }
.template-card small { color: #82969b; font-size: 11px; line-height: 1.45; }
.template-swatch { display: block; width: 100%; height: 32px; border-radius: 5px; background: #b45309; }
.swatch-modern { background: #0f766e; }
.swatch-compact { background: #334155; }
.field-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.field { display: grid; gap: 6px; color: #547179; font-size: 12px; }
.field-wide { grid-column: 1 / -1; }
.field input, .field textarea, .editor-section > textarea { width: 100%; box-sizing: border-box; border: 1px solid #d9e5e5; border-radius: 6px; padding: 8px 10px; outline: none; background: #fff; color: #203e46; font: inherit; font-size: 13px; line-height: 1.55; resize: vertical; }
.field input:focus, .field textarea:focus, .editor-section > textarea:focus { border-color: #0f9d91; box-shadow: 0 0 0 2px #e4f6f3; }
.text-action { display: inline-flex; align-items: center; gap: 4px; padding: 0; border: 0; background: transparent; color: #0f766e; cursor: pointer; font: inherit; font-size: 12px; }
.repeat-editor { display: grid; gap: 10px; padding: 12px; border: 1px solid #e4eded; border-radius: 7px; background: #fbfdfd; }
.repeat-head { display: flex; justify-content: space-between; align-items: center; color: #547179; font-size: 12px; }
.repeat-head button { display: grid; width: 25px; height: 25px; place-items: center; padding: 0; border: 0; background: transparent; color: #9bafb2; cursor: pointer; }
.repeat-head button:hover { color: #c93227; }
.empty-tip, .builder-loading { margin: 0; color: #8a9da1; font-size: 13px; }
.builder-loading { padding: 30px; border: 1px solid #e0ebeb; border-radius: 10px; background: #fff; text-align: center; }
.builder-preview-wrap { position: sticky; top: 86px; display: grid; gap: 9px; }
.preview-label { display: flex; justify-content: space-between; align-items: baseline; color: #173e49; font-size: 14px; }
.preview-label small { color: #8a9da1; font-size: 12px; }
.resume-preview { min-height: 640px; padding: 36px 34px; border: 1px solid #dae5e5; background: #fff; box-shadow: 0 14px 32px rgb(23 52 61 / 8%); color: #334155; }
.preview-header { padding-bottom: 18px; border-bottom: 2px solid #b45309; text-align: center; }
.preview-header h2 { margin: 0; color: #173e49; font-size: 27px; }
.preview-header p { margin: 8px 0 0; color: #64748b; font-size: 11px; line-height: 1.6; }
.preview-section { display: grid; gap: 5px; margin-top: 18px; }
.preview-section h3 { margin: 0 0 3px; color: #b45309; font-size: 13px; letter-spacing: .04em; }
.preview-section p { margin: 0; color: #475569; font-size: 11px; line-height: 1.65; white-space: pre-wrap; }
.preview-entry { display: grid; gap: 2px; }
.preview-entry strong { display: flex; justify-content: space-between; gap: 8px; color: #334155; font-size: 12px; }
.preview-entry strong span { color: #64748b; font-weight: 500; }
.preview-entry small { color: #94a3b8; font-size: 10px; }
.preview-modern .preview-header { border-bottom-color: #0f766e; text-align: left; }
.preview-modern .preview-section h3 { color: #0f766e; }
.preview-compact { padding: 25px 28px; }
.preview-compact .preview-header { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; border-bottom-color: #334155; text-align: left; }
.preview-compact .preview-header p { margin: 0; text-align: right; }
.preview-compact .preview-section { margin-top: 13px; }
.preview-compact .preview-section h3 { color: #334155; font-size: 12px; }
.preview-empty { display: grid; min-height: 300px; place-items: center; color: #a1b1b4; font-size: 12px; text-align: center; }
@media (max-width: 980px) { .builder-header { grid-template-columns: 1fr; align-items: start; gap: 12px; } .builder-actions { justify-content: flex-start; } .builder-layout { grid-template-columns: minmax(0, 1fr); } .builder-preview-wrap { position: static; } }
@media (max-width: 560px) { .builder-main { width: min(100% - 24px, 600px); padding-top: 18px; } .template-grid, .field-grid { grid-template-columns: 1fr; } .field-wide { grid-column: auto; } .builder-actions { display: grid; grid-template-columns: 1fr 1fr; } .builder-actions button { justify-content: center; padding: 0 8px; } .resume-preview { min-height: 540px; padding: 26px 20px; } }
</style>
