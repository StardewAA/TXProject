<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { message, Modal } from 'ant-design-vue';

import {
  getPortalRecruitPostTypeList,
  type HrmPortalRecruitApi,
} from '#/api/hrm/portal/recruit';
import {
  createPortalResume,
  getPortalResumeList,
  RECRUIT_PROFILE_TITLE,
  type HrmPortalResumeApi,
  uploadPortalResume,
} from '#/api/hrm/portal/resume';
import { uploadFile } from '#/api/infra/file';
import { requestClient } from '#/api/request';

type Identity = 'student' | 'worker';

type WorkExperience = {
  id: string;
  company: string;
  industry: string;
  department: string;
  position: string;
  start: string;
  end: string;
  description: string;
  achievements: string;
};

type ProjectExperience = {
  id: string;
  name: string;
  role: string;
  link: string;
  start: string;
  end: string;
  description: string;
  achievements: string;
  highlight: boolean;
};

type EducationExperience = {
  id: string;
  degree: string;
  school: string;
  major: string;
  schoolType: string;
  start: string;
  end: string;
  experience: string;
};

type ResumeForm = {
  name: string;
  birthMonth: string;
  mobile: string;
  email: string;
  identity: Identity;
  employmentStatus: string;
  domicile: string;
  strengths: string;
  jobType: string;
  expectedPostId?: number;
  expectedPostName: string;
  industry: string;
  salary: string;
  expectedCities: string[];
  experiences: WorkExperience[];
  projects: ProjectExperience[];
  educationExperiences: EducationExperience[];
  skills: string;
};

type PositionOption = {
  id?: number;
  name: string;
};

const sectionItems = [
  { id: 'personal', label: '个人信息', caption: '联系方式与状态', icon: 'lucide:user-round' },
  { id: 'strengths', label: '个人优势', caption: '一句话介绍自己', icon: 'lucide:sparkles' },
  { id: 'expectation', label: '期望职位', caption: '下一站想去哪里', icon: 'lucide:compass' },
  { id: 'experience', label: '工作 / 实习经历', caption: '沉淀过的经验', icon: 'lucide:briefcase-business' },
  { id: 'project', label: '项目经历', caption: '代表性成果', icon: 'lucide:folder-kanban' },
  { id: 'education', label: '教育经历', caption: '学历与在校经历', icon: 'lucide:graduation-cap' },
  { id: 'skills', label: '专业技能', caption: '能力关键词', icon: 'lucide:badge-check' },
] as const;

const cityOptions = [
  '北京', '上海', '广州', '深圳', '杭州', '南京', '苏州', '成都', '重庆',
  '西安', '武汉', '郑州', '天津', '青岛', '济南', '合肥', '厦门', '福州',
  '长沙', '南昌', '太原', '石家庄', '呼和浩特', '沈阳', '大连', '长春',
  '哈尔滨', '昆明', '贵阳', '兰州', '银川', '乌鲁木齐', '延安', '榆林',
] as const;

const industryOptions = [
  '防腐保温', '建筑工程', '能源 / 化工', '装备制造', '新材料', '房地产 / 物业',
  '互联网 / IT', '物流运输', '教育培训', '金融服务', '医疗健康', '其它行业',
];

const jobTypeOptions = [
  { label: '不限', value: 'all' },
  { label: '全职', value: 'fulltime' },
  { label: '实习', value: 'internship' },
  { label: '兼职', value: 'parttime' },
];

const salaryOptions = [
  { label: '不限', value: 'all' },
  { label: '3000 元以下', value: 'below-3k' },
  { label: '3000-5000 元', value: '3k-5k' },
  { label: '5000-10000 元', value: '5k-10k' },
  { label: '10000-20000 元', value: '10k-20k' },
  { label: '20000-50000 元', value: '20k-50k' },
  { label: '50000 元以上', value: 'above-50k' },
];

const degreeOptions = ['初中及以下', '中专 / 中技', '高中', '大专', '本科', '硕士', '博士'];
const schoolTypeOptions = ['全日制', '非全日制'];
const fallbackPositions: PositionOption[] = [
  { name: '工程管理' },
  { name: '项目经理' },
  { name: '施工员' },
  { name: '技术员' },
  { name: '质量管理' },
  { name: '采购专员' },
  { name: '行政人事' },
];

const workerStatusOptions = ['离职-随时到岗', '在职-考虑机会', '在职-月内到岗'];
const studentStatusOptions = ['离校-随时到岗', '在校-考虑机会', '在校-月内到岗'];
const now = new Date();
const birthMonthOptions = Array.from({ length: now.getFullYear() - 1959 }, (_, yearIndex) => {
  const year = now.getFullYear() - yearIndex;
  return Array.from({ length: 12 }, (_, monthIndex) => {
    const month = String(monthIndex + 1).padStart(2, '0');
    return { label: `${year}年${month}月`, value: `${year}-${month}` };
  });
}).flat();
const yearOptions = Array.from({ length: now.getFullYear() - 1979 + 6 }, (_, index) => now.getFullYear() + 5 - index);
const skillSuggestions = ['办公软件', '工程制图', '项目管理', '施工组织', '质量验收', '沟通协调'];
const MAX_RESUME_SIZE = 10 * 1024 * 1024;
const ACCEPTED_RESUME_EXTENSIONS = ['pdf', 'doc', 'docx'];

const loading = ref(true);
const submitting = ref(false);
const uploadSubmitting = ref(false);
const activeSection = ref<(typeof sectionItems)[number]['id']>('personal');
const lastSavedAt = ref('');
const postTypes = ref<HrmPortalRecruitApi.PostType[]>([]);
const attachments = ref<HrmPortalResumeApi.Resume[]>([]);
const uploadModalOpen = ref(false);
const uploadInput = ref<HTMLInputElement>();
const selectedUploadFile = ref<File>();
const uploadForm = reactive({ title: '', remark: '' });
const positionValue = ref('');
const experienceModalOpen = ref(false);
const experienceIndex = ref(-1);
const projectModalOpen = ref(false);
const projectIndex = ref(-1);
const educationModalOpen = ref(false);
const educationIndex = ref(-1);
const editingExperience = ref(createWorkExperience());
const editingProject = ref(createProjectExperience());
const editingEducation = ref(createEducationExperience());

const form = reactive<ResumeForm>(createDefaultForm());

const statusOptions = computed(() =>
  form.identity === 'student' ? studentStatusOptions : workerStatusOptions,
);
const positionOptions = computed<PositionOption[]>(() => {
  const children = postTypes.value.filter((item) => item.parentId);
  const source = children.length ? children : postTypes.value;
  return source.length ? source : fallbackPositions;
});
const completionItems = computed(() => ({
  personal: Boolean(form.name.trim() && form.birthMonth && form.mobile.trim() && form.identity && form.employmentStatus && form.domicile),
  strengths: Boolean(form.strengths.trim()),
  expectation: Boolean(form.expectedPostName && form.industry && form.expectedCities.length),
  experience: form.experiences.length > 0,
  project: form.projects.length > 0,
  education: form.educationExperiences.length > 0,
  skills: Boolean(form.skills.trim()),
}));
const completedSections = computed(() => Object.values(completionItems.value).filter(Boolean).length);
const completionRate = computed(() => Math.round((completedSections.value / sectionItems.length) * 100));
const profileStatusLabel = computed(() => (lastSavedAt.value ? `已保存于 ${lastSavedAt.value}` : '尚未保存'));

function createId() {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function createWorkExperience(): WorkExperience {
  return {
    id: createId(), company: '', industry: '', department: '', position: '', start: '', end: '', description: '', achievements: '',
  };
}

function createProjectExperience(): ProjectExperience {
  return {
    id: createId(), name: '', role: '', link: '', start: '', end: '', description: '', achievements: '', highlight: false,
  };
}

function createEducationExperience(): EducationExperience {
  return {
    id: createId(), degree: '本科', school: '', major: '', schoolType: '全日制', start: '', end: '', experience: '',
  };
}

function createDefaultForm(): ResumeForm {
  return {
    name: '', birthMonth: '', mobile: '', email: '', identity: 'worker', employmentStatus: workerStatusOptions[2]!, domicile: '', strengths: '',
    jobType: 'fulltime', expectedPostName: '', industry: '', salary: 'all', expectedCities: [], experiences: [], projects: [], educationExperiences: [], skills: '',
  };
}

function asString(value: unknown) {
  return typeof value === 'string' ? value : value == null ? '' : String(value);
}

function normalizeWorkExperience(value: unknown): WorkExperience {
  const item = value && typeof value === 'object' ? value as Record<string, unknown> : {};
  return {
    id: asString(item.id) || createId(), company: asString(item.company), industry: asString(item.industry), department: asString(item.department),
    position: asString(item.position), start: asString(item.start || item.jobStart), end: asString(item.end || item.jobEnd),
    description: asString(item.description || item.content), achievements: asString(item.achievements),
  };
}

function normalizeProjectExperience(value: unknown): ProjectExperience {
  const item = value && typeof value === 'object' ? value as Record<string, unknown> : {};
  return {
    id: asString(item.id) || createId(), name: asString(item.name), role: asString(item.role), link: asString(item.link), start: asString(item.start), end: asString(item.end),
    description: asString(item.description), achievements: asString(item.achievements), highlight: Boolean(item.highlight),
  };
}

function normalizeEducationExperience(value: unknown): EducationExperience {
  const item = value && typeof value === 'object' ? value as Record<string, unknown> : {};
  return {
    id: asString(item.id) || createId(), degree: asString(item.degree || item.education) || '本科', school: asString(item.school), major: asString(item.major),
    schoolType: asString(item.schoolType) || '全日制', start: asString(item.start || item.educationStart), end: asString(item.end || item.educationEnd), experience: asString(item.experience || item.inSchoolExperience),
  };
}

function normalizeProfile(value: Record<string, unknown>): ResumeForm {
  const defaults = createDefaultForm();
  const oldExperience = value.company || value.position || value.jobStart || value.workStart
    ? [normalizeWorkExperience({ company: value.company, position: value.position, start: value.jobStart || value.workStart, end: value.jobEnd })]
    : [];
  const oldEducation = value.school || value.major || value.education
    ? [normalizeEducationExperience(value)]
    : [];
  const expectedCities = Array.isArray(value.expectedCities)
    ? value.expectedCities.map(asString).filter(Boolean).slice(0, 3)
    : asString(value.city).split(/[,，、]/).map((city) => city.trim()).filter(Boolean).slice(0, 3);
  const experiences = Array.isArray(value.experiences) ? value.experiences.map(normalizeWorkExperience) : oldExperience;
  const projects = Array.isArray(value.projects) ? value.projects.map(normalizeProjectExperience) : [];
  const educationExperiences = Array.isArray(value.educationExperiences)
    ? value.educationExperiences.map(normalizeEducationExperience)
    : oldEducation;
  const identity: Identity = value.identity === 'student' ? 'student' : 'worker';
  const identityStatuses = identity === 'student' ? studentStatusOptions : workerStatusOptions;
  const employmentStatus = asString(value.employmentStatus);
  return {
    ...defaults,
    name: asString(value.name), birthMonth: asString(value.birthMonth), mobile: asString(value.mobile || value.phone), email: asString(value.email), identity,
    employmentStatus: identityStatuses.includes(employmentStatus) ? employmentStatus : identityStatuses[2]!, domicile: asString(value.domicile), strengths: asString(value.strengths),
    jobType: asString(value.jobType) || 'fulltime', expectedPostId: typeof value.expectedPostId === 'number' ? value.expectedPostId : typeof value.postTypeId === 'number' ? value.postTypeId : undefined,
    expectedPostName: asString(value.expectedPostName || value.postTypeName), industry: asString(value.industry || value.expectedIndustry), salary: asString(value.salary) || 'all', expectedCities,
    experiences, projects, educationExperiences, skills: asString(value.skills),
  };
}

function readOnlineProfile(resumes: HrmPortalResumeApi.Resume[]) {
  const profileResume = resumes.find((resume) => resume.resumeType === 2 && [RECRUIT_PROFILE_TITLE, '在线简历'].includes(resume.title));
  if (!profileResume?.content) return undefined;
  try {
    const value = JSON.parse(profileResume.content);
    return value && typeof value === 'object' ? value as Record<string, unknown> : undefined;
  } catch {
    return undefined;
  }
}

function syncPositionValue() {
  positionValue.value = form.expectedPostId ? String(form.expectedPostId) : form.expectedPostName;
}

async function loadResume() {
  loading.value = true;
  try {
    const [resumes, types] = await Promise.all([getPortalResumeList(), getPortalRecruitPostTypeList()]);
    postTypes.value = types || [];
    attachments.value = (resumes || []).filter((resume) => resume.resumeType === 1);
    const profile = readOnlineProfile(resumes || []);
    if (profile) Object.assign(form, normalizeProfile(profile));
    syncPositionValue();
  } catch (error) {
    console.warn('[OnlineResume] 加载在线简历失败', error);
  } finally {
    loading.value = false;
  }
}

const visibleAttachments = computed(() => [...attachments.value].sort((left, right) => {
  const leftTime = left.createTime ? new Date(left.createTime).getTime() : 0;
  const rightTime = right.createTime ? new Date(right.createTime).getTime() : 0;
  return rightTime - leftTime;
}));

function getAttachmentExtension(resume: HrmPortalResumeApi.Resume) {
  return resume.fileName?.split('.').pop()?.toLowerCase() || 'file';
}

function formatAttachmentDate(value?: string) {
  if (!value) return '时间待补充';
  return new Date(value).toLocaleString('zh-CN', {
    hour12: false,
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function getResumeFileUrl(fileUrl?: string) {
  if (!fileUrl) return '';
  return fileUrl.includes('/infra/file/') && fileUrl.includes('/preview/')
    ? fileUrl.replace('/preview/', '/get/')
    : fileUrl;
}

async function previewAttachment(fileUrl?: string) {
  if (!fileUrl) return;
  const previewWindow = window.open('', '_blank');
  if (!previewWindow) {
    message.warning('请允许打开新窗口后重试');
    return;
  }
  try {
    const blob = await requestClient.download<Blob>(getResumeFileUrl(fileUrl));
    if (!(blob instanceof Blob)) throw new TypeError('简历文件响应无效');
    const objectUrl = URL.createObjectURL(blob);
    previewWindow.location.href = objectUrl;
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
  } catch (error) {
    previewWindow.close();
    console.warn('[OnlineResume] 预览简历失败', error);
    message.error('简历预览失败，请稍后重试');
  }
}

function openUploadModal() {
  uploadModalOpen.value = true;
}

function validateResumeFile(file: File) {
  const extension = file.name.split('.').pop()?.toLowerCase();
  if (!extension || !ACCEPTED_RESUME_EXTENSIONS.includes(extension)) {
      message.warning('仅支持常见文档格式的简历');
    return false;
  }
  if (file.size > MAX_RESUME_SIZE) {
    message.warning('简历文件不能超过 10 兆');
    return false;
  }
  return true;
}

function handleFileChange(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file || !validateResumeFile(file)) {
    selectedUploadFile.value = undefined;
    return;
  }
  selectedUploadFile.value = file;
  if (!uploadForm.title.trim()) uploadForm.title = file.name.replace(/\.[^.]+$/, '');
}

function clearSelectedFile() {
  selectedUploadFile.value = undefined;
  if (uploadInput.value) uploadInput.value.value = '';
}

function resetUploadForm() {
  uploadForm.title = '';
  uploadForm.remark = '';
  clearSelectedFile();
}

function cancelUpload() {
  if (!uploadSubmitting.value) {
    uploadModalOpen.value = false;
    resetUploadForm();
  }
}

async function handleUploadResume() {
  const file = selectedUploadFile.value;
  if (!uploadForm.title.trim()) {
    message.warning('请输入简历标题');
    return;
  }
  if (!file) {
    message.warning('请选择简历附件');
    return;
  }
  uploadSubmitting.value = true;
  try {
    const fileUrl = (await uploadFile({ file, directory: 'hrm/personal/resume' })) as string;
    await uploadPortalResume({
      title: uploadForm.title.trim(),
      fileName: file.name,
      fileUrl,
      remark: uploadForm.remark.trim() || undefined,
    });
    message.success('简历附件已保存');
    uploadModalOpen.value = false;
    resetUploadForm();
    await loadResume();
  } catch (error) {
    const reason = error instanceof Error ? error.message : '';
    message.error(reason || '简历上传失败，请稍后重试');
  } finally {
    uploadSubmitting.value = false;
  }
}

function selectSection(id: (typeof sectionItems)[number]['id']) {
  activeSection.value = id;
  document.getElementById(`resume-section-${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function setIdentity(identity: Identity) {
  form.identity = identity;
  if (!statusOptions.value.includes(form.employmentStatus)) form.employmentStatus = statusOptions.value[2]!;
}

function handlePositionChange(value: string) {
  const option = positionOptions.value.find((item) => String(item.id ?? item.name) === value);
  form.expectedPostId = option?.id;
  form.expectedPostName = option?.name || value;
}

function handleCitiesChange(values: string[]) {
  if (values.length > 3) {
    message.info('期望工作城市最多选择 3 个');
    form.expectedCities = values.slice(0, 3);
    return;
  }
  form.expectedCities = values;
}

function formatPeriod(start: string, end: string) {
  if (!start && !end) return '时间待补充';
  return `${start || '—'} 至 ${end || '至今'}`;
}

function openExperience(index = -1) {
  experienceIndex.value = index;
  editingExperience.value = index >= 0 ? { ...form.experiences[index]! } : createWorkExperience();
  experienceModalOpen.value = true;
}

function saveExperience() {
  if (!editingExperience.value.company.trim() || !editingExperience.value.position.trim()) {
    message.warning('请填写公司名称和职位名称');
    return;
  }
  if (experienceIndex.value >= 0) form.experiences.splice(experienceIndex.value, 1, { ...editingExperience.value });
  else form.experiences.push({ ...editingExperience.value });
  experienceModalOpen.value = false;
}

function removeExperience(index: number) {
  Modal.confirm({ title: '删除这段工作经历？', content: '删除后不会影响已经保存的其它简历内容。', okText: '删除', cancelText: '取消', onOk: () => form.experiences.splice(index, 1) });
}

function openProject(index = -1) {
  projectIndex.value = index;
  editingProject.value = index >= 0 ? { ...form.projects[index]! } : createProjectExperience();
  projectModalOpen.value = true;
}

function saveProject() {
  if (!editingProject.value.name.trim() || !editingProject.value.role.trim()) {
    message.warning('请填写项目名称和项目角色');
    return;
  }
  if (projectIndex.value >= 0) form.projects.splice(projectIndex.value, 1, { ...editingProject.value });
  else form.projects.push({ ...editingProject.value });
  projectModalOpen.value = false;
}

function removeProject(index: number) {
  Modal.confirm({ title: '删除这个项目经历？', okText: '删除', cancelText: '取消', onOk: () => form.projects.splice(index, 1) });
}

function openEducation(index = -1) {
  educationIndex.value = index;
  editingEducation.value = index >= 0 ? { ...form.educationExperiences[index]! } : createEducationExperience();
  educationModalOpen.value = true;
}

function saveEducation() {
  if (!editingEducation.value.school.trim() || !editingEducation.value.major.trim()) {
    message.warning('请填写学校名称和专业');
    return;
  }
  if (educationIndex.value >= 0) form.educationExperiences.splice(educationIndex.value, 1, { ...editingEducation.value });
  else form.educationExperiences.push({ ...editingEducation.value });
  educationModalOpen.value = false;
}

function removeEducation(index: number) {
  Modal.confirm({ title: '删除这段教育经历？', okText: '删除', cancelText: '取消', onOk: () => form.educationExperiences.splice(index, 1) });
}

function addSkill(skill: string) {
  const current = form.skills.split(/[、,，\n]/).map((item) => item.trim()).filter(Boolean);
  if (!current.includes(skill)) form.skills = [...current, skill].join('、');
}

function serializeProfile() {
  const firstExperience = form.experiences[0];
  const firstEducation = form.educationExperiences[0];
  return {
    ...form,
    city: form.expectedCities.join('、'),
    postTypeId: form.expectedPostId,
    postTypeName: form.expectedPostName,
    expectedIndustry: form.industry,
    company: firstExperience?.company || '',
    jobStart: firstExperience?.start || '',
    jobEnd: firstExperience?.end || '',
    workStart: firstExperience?.start || '',
    education: firstEducation?.degree || '',
    school: firstEducation?.school || '',
    major: firstEducation?.major || '',
    schoolType: firstEducation?.schoolType || '',
    educationStart: firstEducation?.start || '',
    educationEnd: firstEducation?.end || '',
    completedAt: new Date().toISOString(),
  };
}

async function saveResume() {
  if (!form.name.trim() || !form.mobile.trim()) {
    message.warning('请先在个人信息中填写姓名和电话');
    selectSection('personal');
    return;
  }
  submitting.value = true;
  try {
    await createPortalResume({ title: RECRUIT_PROFILE_TITLE, content: JSON.stringify(serializeProfile()), remark: '在线简历' });
    lastSavedAt.value = new Date().toLocaleString('zh-CN', { hour12: false, month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
    message.success('在线简历已保存');
  } finally {
    submitting.value = false;
  }
}

onMounted(loadResume);
</script>

<template>
  <div class="online-resume-page">
    <aside class="resume-index-panel">
      <div class="resume-index-brand"><span class="brand-mark">简</span><div><strong>在线简历</strong><small>求职服务</small></div></div>
      <div class="completion-card">
        <div class="completion-card-top"><span>简历完成度</span><strong>{{ completionRate }}%</strong></div>
        <div class="completion-track"><i :style="{ width: `${completionRate}%` }"></i></div>
        <small>{{ completedSections }}/{{ sectionItems.length }} 个模块已完善</small>
      </div>
      <nav class="resume-index-nav" aria-label="简历目录">
        <p>简历目录</p>
        <button v-for="(item, index) in sectionItems" :key="item.id" :class="{ active: activeSection === item.id }" type="button" @click="selectSection(item.id)">
          <span class="nav-number">{{ String(index + 1).padStart(2, '0') }}</span>
          <span class="nav-copy"><strong>{{ item.label }}</strong><small>{{ item.caption }}</small></span>
          <IconifyIcon v-if="completionItems[item.id]" class="nav-done" icon="lucide:check" />
        </button>
      </nav>
      <div class="index-tip"><IconifyIcon icon="lucide:lightbulb" /><p>好的简历不需要面面俱到，把最能代表你的经历写清楚就好。</p></div>
    </aside>

    <main class="resume-editor">
      <header class="resume-editor-header">
        <div><p class="resume-breadcrumb">我要找工作 <span>/</span> 求职资料 <span>/</span> 在线简历</p><h1>把你的职业故事，写给下一个机会</h1><p class="resume-subtitle">完善在线简历，投递岗位时无需重复填写资料。</p></div>
        <div class="header-save-area"><span :class="['save-state', { saved: lastSavedAt }] "><i></i>{{ profileStatusLabel }}</span><a-button type="primary" :loading="submitting" @click="saveResume">保存简历</a-button></div>
      </header>

      <div v-if="loading" class="resume-loading"><a-skeleton active :paragraph="{ rows: 4 }" /><a-skeleton active :paragraph="{ rows: 5 }" /></div>

      <template v-else>
        <div class="resume-notice"><IconifyIcon icon="lucide:info" /><span>以下内容会用于在线投递和招聘沟通。带 <em>*</em> 的项目建议优先完成，邮箱为选填项。</span></div>

        <section id="resume-section-personal" class="resume-section">
          <div class="section-title-row"><div class="section-title"><span class="section-icon"><IconifyIcon icon="lucide:user-round" /></span><div><p>第一部分</p><h2>个人信息</h2><span>让招聘方快速了解你，并在合适的时候联系你。</span></div></div><span class="section-status" :class="{ done: completionItems.personal }">{{ completionItems.personal ? '已完善' : '待完善' }}</span></div>
          <div class="field-grid personal-grid">
            <label class="field required"><span>姓名</span><a-input v-model:value="form.name" maxlength="30" placeholder="请输入真实姓名" /></label>
            <label class="field required"><span>出生年月</span><a-select v-model:value="form.birthMonth" show-search placeholder="请选择出生年月"><a-select-option v-for="item in birthMonthOptions" :key="item.value" :value="item.value">{{ item.label }}</a-select-option></a-select></label>
            <label class="field required"><span>电话</span><a-input v-model:value="form.mobile" maxlength="20" placeholder="请输入常用联系电话" /></label>
            <label class="field"><span>邮箱 <small>选填</small></span><a-input v-model:value="form.email" maxlength="80" placeholder="用于接收招聘方联系邮件" /></label>
            <label class="field required"><span>我的身份</span><a-select :value="form.identity" placeholder="请选择身份" @change="setIdentity"><a-select-option value="worker">职场人</a-select-option><a-select-option value="student">学生</a-select-option></a-select></label>
            <label class="field required"><span>当前求职状态</span><a-select v-model:value="form.employmentStatus" placeholder="请选择当前状态"><a-select-option v-for="item in statusOptions" :key="item" :value="item">{{ item }}</a-select-option></a-select></label>
            <label class="field required personal-wide"><span>户籍地</span><a-select v-model:value="form.domicile" show-search placeholder="请选择户籍所在城市"><a-select-option v-for="city in cityOptions" :key="city" :value="city">{{ city }}</a-select-option></a-select></label>
          </div>
        </section>

        <section id="resume-section-strengths" class="resume-section">
          <div class="section-title-row"><div class="section-title"><span class="section-icon"><IconifyIcon icon="lucide:sparkles" /></span><div><p>第二部分</p><h2>个人优势</h2><span>用几句话说清楚，你最擅长解决什么问题。</span></div></div><span class="section-status" :class="{ done: completionItems.strengths }">{{ completionItems.strengths ? '已完善' : '待完善' }}</span></div>
          <label class="field field-full"><span>个人优势 <small>建议 3-5 句话</small></span><a-textarea v-model:value="form.strengths" :maxlength="500" :rows="5" show-count placeholder="例如：5 年防腐保温工程经验，熟悉施工组织、质量验收与现场协调，能够独立推进项目从开工到交付。" /></label>
        </section>

        <section id="resume-section-expectation" class="resume-section">
          <div class="section-title-row"><div class="section-title"><span class="section-icon"><IconifyIcon icon="lucide:compass" /></span><div><p>第三部分</p><h2>期望职位</h2><span>明确你的下一站，帮助岗位匹配更精准。</span></div></div><span class="section-status" :class="{ done: completionItems.expectation }">{{ completionItems.expectation ? '已完善' : '待完善' }}</span></div>
          <div class="field-grid expectation-grid">
            <label class="field"><span>求职类型</span><a-select v-model:value="form.jobType"><a-select-option v-for="item in jobTypeOptions" :key="item.value" :value="item.value">{{ item.label }}</a-select-option></a-select></label>
            <label class="field"><span>期望职位</span><a-select v-model:value="positionValue" show-search placeholder="请选择期望职位" @change="handlePositionChange"><a-select-option v-for="item in positionOptions" :key="item.id ?? item.name" :value="String(item.id ?? item.name)">{{ item.name }}</a-select-option></a-select></label>
            <label class="field"><span>期望行业</span><a-select v-model:value="form.industry" show-search placeholder="请选择期望行业"><a-select-option v-for="item in industryOptions" :key="item" :value="item">{{ item }}</a-select-option></a-select></label>
            <label class="field"><span>薪资要求</span><a-select v-model:value="form.salary"><a-select-option v-for="item in salaryOptions" :key="item.value" :value="item.value">{{ item.label }}</a-select-option></a-select></label>
            <label class="field field-full"><span>期望工作城市 <small>最多选择 3 个城市</small></span><a-select :value="form.expectedCities" mode="multiple" :max-tag-count="3" show-search placeholder="请选择期望工作的城市" @change="handleCitiesChange"><a-select-option v-for="city in cityOptions" :key="city" :value="city">{{ city }}</a-select-option></a-select></label>
          </div>
        </section>

        <section id="resume-section-experience" class="resume-section">
          <div class="section-title-row"><div class="section-title"><span class="section-icon"><IconifyIcon icon="lucide:briefcase-business" /></span><div><p>第四部分</p><h2>工作 / 实习经历</h2><span>从最近一段经历开始，突出职责和可验证的成果。</span></div></div><a-button class="add-button" type="link" @click="openExperience()"><IconifyIcon icon="lucide:plus" /> 添加经历</a-button></div>
          <div v-if="!form.experiences.length" class="empty-module"><span class="empty-icon"><IconifyIcon icon="lucide:briefcase-business" /></span><strong>还没有工作或实习经历</strong><p>如果你是应届生，可以先添加实习经历，或跳过此模块。</p><a-button type="primary" ghost @click="openExperience()">添加第一段经历</a-button></div>
          <div v-else class="entry-list"><article v-for="(item, index) in form.experiences" :key="item.id" class="entry-card"><div class="entry-card-top"><div><strong>{{ item.position || '未填写职位' }}</strong><span>{{ item.company || '未填写公司' }}</span></div><div class="entry-card-actions"><small>{{ formatPeriod(item.start, item.end) }}</small><button type="button" aria-label="编辑工作经历" @click="openExperience(index)"><IconifyIcon icon="lucide:pen-line" /></button><button type="button" aria-label="删除工作经历" @click="removeExperience(index)"><IconifyIcon icon="lucide:trash-2" /></button></div></div><div class="entry-meta"><span v-if="item.industry">{{ item.industry }}</span><span v-if="item.department">{{ item.department }}</span></div><p v-if="item.description" class="entry-description">{{ item.description }}</p><p v-if="item.achievements" class="entry-achievement"><b>工作业绩</b>{{ item.achievements }}</p></article></div>
        </section>

        <section id="resume-section-project" class="resume-section">
          <div class="section-title-row"><div class="section-title"><span class="section-icon"><IconifyIcon icon="lucide:folder-kanban" /></span><div><p>第五部分</p><h2>项目经历</h2><span>展示你参与过的项目，以及你在其中承担的角色。</span></div></div><a-button class="add-button" type="link" @click="openProject()"><IconifyIcon icon="lucide:plus" /> 添加项目</a-button></div>
          <div v-if="!form.projects.length" class="empty-module"><span class="empty-icon"><IconifyIcon icon="lucide:folder-plus" /></span><strong>还没有项目经历</strong><p>从最能体现专业能力的项目写起，描述你的角色与结果。</p><a-button type="primary" ghost @click="openProject()">添加第一个项目</a-button></div>
          <div v-else class="entry-list"><article v-for="(item, index) in form.projects" :key="item.id" class="entry-card"><div class="entry-card-top"><div><strong>{{ item.name || '未填写项目名称' }} <em v-if="item.highlight">重点项目</em></strong><span>{{ item.role || '未填写项目角色' }}</span></div><div class="entry-card-actions"><small>{{ formatPeriod(item.start, item.end) }}</small><button type="button" aria-label="编辑项目经历" @click="openProject(index)"><IconifyIcon icon="lucide:pen-line" /></button><button type="button" aria-label="删除项目经历" @click="removeProject(index)"><IconifyIcon icon="lucide:trash-2" /></button></div></div><a v-if="item.link" class="entry-link" :href="item.link" target="_blank" rel="noreferrer">{{ item.link }}</a><p v-if="item.description" class="entry-description">{{ item.description }}</p><p v-if="item.achievements" class="entry-achievement"><b>项目业绩</b>{{ item.achievements }}</p></article></div>
        </section>

        <section id="resume-section-education" class="resume-section">
          <div class="section-title-row"><div class="section-title"><span class="section-icon"><IconifyIcon icon="lucide:graduation-cap" /></span><div><p>第六部分</p><h2>教育经历</h2><span>学校、专业和在校经历，构成你的专业底色。</span></div></div><a-button class="add-button" type="link" @click="openEducation()"><IconifyIcon icon="lucide:plus" /> 添加经历</a-button></div>
          <div v-if="!form.educationExperiences.length" class="empty-module"><span class="empty-icon"><IconifyIcon icon="lucide:graduation-cap" /></span><strong>还没有教育经历</strong><p>填写最高学历，也可以继续补充多段教育经历。</p><a-button type="primary" ghost @click="openEducation()">添加教育经历</a-button></div>
          <div v-else class="entry-list"><article v-for="(item, index) in form.educationExperiences" :key="item.id" class="entry-card"><div class="entry-card-top"><div><strong>{{ item.degree || '未填写学历' }} · {{ item.school || '未填写学校' }}</strong><span>{{ item.major || '未填写专业' }}<i v-if="item.schoolType"> · {{ item.schoolType }}</i></span></div><div class="entry-card-actions"><small>{{ formatPeriod(item.start, item.end) }}</small><button type="button" aria-label="编辑教育经历" @click="openEducation(index)"><IconifyIcon icon="lucide:pen-line" /></button><button type="button" aria-label="删除教育经历" @click="removeEducation(index)"><IconifyIcon icon="lucide:trash-2" /></button></div></div><p v-if="item.experience" class="entry-description">{{ item.experience }}</p></article></div>
        </section>

        <section id="resume-section-skills" class="resume-section">
          <div class="section-title-row"><div class="section-title"><span class="section-icon"><IconifyIcon icon="lucide:badge-check" /></span><div><p>第七部分</p><h2>专业技能</h2><span>写下岗位最关心的工具、方法和专业能力。</span></div></div><span class="section-status" :class="{ done: completionItems.skills }">{{ completionItems.skills ? '已完善' : '待完善' }}</span></div>
          <label class="field field-full"><span>专业技能 <small>可用顿号或换行分隔</small></span><a-textarea v-model:value="form.skills" :maxlength="1000" :rows="5" show-count placeholder="例如：熟悉施工组织设计、工程质量验收、工程制图，具备项目现场协调和团队沟通能力。" /></label>
          <div class="skill-suggestions"><span>参考技能</span><button v-for="skill in skillSuggestions" :key="skill" type="button" @click="addSkill(skill)"><IconifyIcon icon="lucide:plus" /> {{ skill }}</button></div>
        </section>

        <footer class="resume-bottom-actions"><span><IconifyIcon icon="lucide:shield-check" /> 内容仅用于求职服务，保存后可随时修改</span><a-button type="primary" :loading="submitting" @click="saveResume">保存在线简历</a-button></footer>
      </template>
    </main>

    <aside class="resume-attachments-panel" aria-label="附件管理">
      <div class="attachments-header">
        <div>
          <p>简历附件</p>
          <h2>附件管理</h2>
        </div>
        <a-button class="attachments-add" type="text" aria-label="上传简历附件" @click="openUploadModal">
          <IconifyIcon icon="lucide:plus" />
        </a-button>
      </div>

      <div class="attachments-summary">
        <span>简历附件</span>
        <strong>{{ visibleAttachments.length }}</strong>
        <small>支持常见文档格式 · 单个不超过 10 兆</small>
      </div>

      <div v-if="loading" class="attachments-loading">
        <a-skeleton v-for="item in 2" :key="item" active :paragraph="{ rows: 2 }" />
      </div>
      <div v-else-if="!visibleAttachments.length" class="attachments-empty">
        <span><IconifyIcon icon="lucide:file-plus-2" /></span>
        <strong>还没有附件简历</strong>
         <p>上传文档或扫描件简历，投递时即可直接使用。</p>
        <a-button type="primary" ghost @click="openUploadModal">上传第一份简历</a-button>
      </div>
      <div v-else class="attachments-list">
        <article v-for="resume in visibleAttachments" :key="resume.id" class="attachment-item">
          <span :class="['attachment-file-icon', `is-${getAttachmentExtension(resume)}`]"><IconifyIcon icon="lucide:file-text" /></span>
          <div class="attachment-copy">
            <strong :title="resume.title">{{ resume.title }}</strong>
            <small :title="resume.fileName || resume.title">{{ resume.fileName || '附件简历' }}</small>
            <small>{{ formatAttachmentDate(resume.createTime) }}</small>
          </div>
          <a-button v-if="resume.fileUrl" class="attachment-preview" type="text" aria-label="预览简历" @click="previewAttachment(resume.fileUrl)">
            <IconifyIcon icon="lucide:ellipsis-vertical" />
          </a-button>
        </article>
      </div>

      <button class="attachment-upload-cta" type="button" @click="openUploadModal">
        <span class="attachment-upload-cta-icon"><IconifyIcon icon="lucide:upload-cloud" /></span>
        <span><strong>添加附件简历</strong><small>让招聘方更快了解你</small></span>
        <IconifyIcon class="attachment-upload-cta-arrow" icon="lucide:arrow-up-right" />
      </button>
    </aside>

    <a-modal
      v-model:open="uploadModalOpen"
      title="上传附件简历"
      ok-text="上传并保存"
      cancel-text="取消"
      :confirm-loading="uploadSubmitting"
      :mask-closable="false"
      wrap-class-name="resume-upload-modal"
      @cancel="cancelUpload"
      @ok="handleUploadResume"
    >
      <div class="attachment-upload-form">
        <p class="attachment-upload-help">上传后，投递岗位时可以直接选择这份附件简历。</p>
        <label class="field required">
          <span>简历标题</span>
          <a-input v-model:value="uploadForm.title" maxlength="60" placeholder="例如：项目经理简历" />
        </label>
        <label class="field">
          <span>备注 <small>选填</small></span>
          <a-input v-model:value="uploadForm.remark" maxlength="120" placeholder="例如：2026 年最新版" />
        </label>
        <label class="attachment-file-picker" :class="{ 'has-file': selectedUploadFile }">
          <input ref="uploadInput" type="file" accept=".pdf,.doc,.docx" @change="handleFileChange" />
          <IconifyIcon :icon="selectedUploadFile ? 'lucide:file-check-2' : 'lucide:cloud-upload'" />
          <strong>{{ selectedUploadFile?.name || '选择简历文件' }}</strong>
          <small>{{ selectedUploadFile ? '已选择，可点击重新选择' : '点击选择本地文件' }}</small>
        </label>
      </div>
    </a-modal>

    <a-modal v-model:open="experienceModalOpen" :title="experienceIndex >= 0 ? '编辑工作 / 实习经历' : '添加工作 / 实习经历'" width="720px" @ok="saveExperience">
      <div class="modal-fields"><label class="field required"><span>公司名称</span><a-input v-model:value="editingExperience.company" placeholder="例如：北京华品博睿网络技术有限公司" /></label><label class="field"><span>所属行业</span><a-select v-model:value="editingExperience.industry" show-search placeholder="选择所属行业"><a-select-option v-for="item in industryOptions" :key="item" :value="item">{{ item }}</a-select-option></a-select></label><label class="field"><span>所属部门 <small>选填</small></span><a-input v-model:value="editingExperience.department" placeholder="例如：产品部" /></label><label class="field required"><span>职位名称</span><a-input v-model:value="editingExperience.position" placeholder="例如：项目经理" /></label><label class="field"><span>在职时间</span><div class="date-range"><a-input v-model:value="editingExperience.start" type="month" /><span>至</span><a-input v-model:value="editingExperience.end" type="month" placeholder="至今可留空" /></div></label><label class="field field-full"><span>工作内容</span><a-textarea v-model:value="editingExperience.description" :maxlength="3000" :rows="4" show-count placeholder="描述主要负责的工作内容、使用的方法和承担的职责。" /></label><label class="field field-full"><span>工作业绩 <small>选填</small></span><a-textarea v-model:value="editingExperience.achievements" :maxlength="1000" :rows="3" show-count placeholder="例如：优化施工流程，项目交付周期缩短 20%。" /></label></div>
    </a-modal>

    <a-modal v-model:open="projectModalOpen" :title="projectIndex >= 0 ? '编辑项目经历' : '添加项目经历'" width="720px" @ok="saveProject">
       <div class="modal-fields"><label class="field required"><span>项目名称</span><a-input v-model:value="editingProject.name" placeholder="例如：管道保温智能巡检项目" /></label><label class="field required"><span>项目角色</span><a-input v-model:value="editingProject.role" placeholder="例如：项目负责人 / 界面设计师" /></label><label class="field"><span>项目链接 <small>选填</small></span><a-input v-model:value="editingProject.link" placeholder="例如：项目官网或作品链接" /></label><label class="field"><span>项目时间</span><div class="date-range"><a-input v-model:value="editingProject.start" type="month" /><span>至</span><a-input v-model:value="editingProject.end" type="month" placeholder="至今可留空" /></div></label><label class="field field-full"><span>项目描述</span><a-textarea v-model:value="editingProject.description" :maxlength="3000" :rows="4" show-count placeholder="描述项目背景、目标和你负责的具体工作。" /></label><label class="field field-full"><span>项目业绩 <small>选填</small></span><a-textarea v-model:value="editingProject.achievements" :maxlength="1000" :rows="3" show-count placeholder="描述最终结果、数据变化或获得的认可。" /></label><label class="modal-checkbox"><a-checkbox v-model:checked="editingProject.highlight" /> 将此项目标记为重点项目</label></div>
    </a-modal>

    <a-modal v-model:open="educationModalOpen" :title="educationIndex >= 0 ? '编辑教育经历' : '添加教育经历'" width="720px" @ok="saveEducation">
      <div class="modal-fields"><label class="field"><span>学历</span><a-select v-model:value="editingEducation.degree"><a-select-option v-for="item in degreeOptions" :key="item" :value="item">{{ item }}</a-select-option></a-select></label><label class="field required"><span>学校名称</span><a-input v-model:value="editingEducation.school" placeholder="例如：北京大学" /></label><label class="field required"><span>专业</span><a-input v-model:value="editingEducation.major" placeholder="例如：计算机科学与技术" /></label><label class="field"><span>学校类型</span><a-select v-model:value="editingEducation.schoolType"><a-select-option v-for="item in schoolTypeOptions" :key="item" :value="item">{{ item }}</a-select-option></a-select></label><label class="field"><span>时间段</span><div class="date-range"><a-select v-model:value="editingEducation.start" placeholder="选择年份"><a-select-option v-for="year in yearOptions" :key="year" :value="String(year)">{{ year }}年</a-select-option></a-select><span>至</span><a-select v-model:value="editingEducation.end" placeholder="选择年份"><a-select-option v-for="year in yearOptions" :key="year" :value="String(year)">{{ year }}年</a-select-option></a-select></div></label><label class="field field-full"><span>在校经历 <small>选填</small></span><a-textarea v-model:value="editingEducation.experience" :maxlength="3000" :rows="4" show-count placeholder="例如：在校担任学生会部长，获得校级奖学金，主修课程包括……" /></label></div>
    </a-modal>
  </div>
</template>

<style scoped>
 .online-resume-page { --resume-ink: #3f2a1f; --resume-muted: #8d7668; --resume-line: #f1e4da; --resume-soft: #fff8f1; --resume-orange: #e47b32; --resume-orange-deep: #c45d1d; display: grid; grid-template-columns: 224px minmax(0, 1fr) 276px; gap: 24px; align-items: start; color: var(--resume-ink); }
.resume-index-panel { position: sticky; top: 20px; display: grid; gap: 20px; padding: 20px 14px 16px; border: 1px solid var(--resume-line); border-radius: 16px; background: #fff; box-shadow: 0 12px 32px rgb(180 104 54 / 6%); }
.resume-index-brand { display: flex; align-items: center; gap: 10px; padding: 2px 8px; }
.brand-mark { display: grid; width: 34px; height: 34px; place-items: center; border-radius: 10px; background: var(--resume-orange); color: #fff; font-size: 17px; font-weight: 700; }
.resume-index-brand strong, .resume-index-brand small { display: block; }
.resume-index-brand strong { font-size: 16px; }
 .resume-index-brand small { margin-top: 3px; color: #bca89a; font-size: 12px; letter-spacing: .08em; }
.completion-card { padding: 13px 12px; border-radius: 10px; background: var(--resume-soft); }
 .completion-card-top { display: flex; align-items: center; justify-content: space-between; color: var(--resume-muted); font-size: 14px; }
.completion-card-top strong { color: var(--resume-orange-deep); font-size: 18px; }
.completion-track { height: 5px; margin: 10px 0 7px; overflow: hidden; border-radius: 3px; background: #f3e9e1; }
.completion-track i { display: block; height: 100%; border-radius: inherit; background: var(--resume-orange); transition: width .25s ease; }
 .completion-card small { color: #b29e91; font-size: 12px; }
 .resume-index-nav > p { margin: 0 10px 4px; color: #c0aa9c; font-size: 12px; font-weight: 700; letter-spacing: .08em; }
.resume-index-nav { display: grid; gap: 2px; }
.resume-index-nav button { display: flex; width: 100%; min-height: 51px; align-items: center; gap: 8px; padding: 7px 8px; border: 0; border-radius: 9px; background: transparent; color: inherit; cursor: pointer; text-align: left; transition: background .18s ease, transform .18s ease; }
.resume-index-nav button:hover, .resume-index-nav button.active { background: #fff7f0; transform: translateX(2px); }
 .nav-number { width: 22px; color: #c7afa0; font-size: 12px; }
.resume-index-nav button.active .nav-number { color: var(--resume-orange); font-weight: 700; }
.nav-copy { display: grid; min-width: 0; gap: 3px; }
 .nav-copy strong { font-size: 14px; font-weight: 600; }
 .nav-copy small { color: #ae988a; font-size: 12px; }
.nav-done { margin-left: auto; color: var(--resume-orange); font-size: 14px; }
.index-tip { display: flex; gap: 8px; padding: 12px 10px; border-top: 1px solid #f5eae2; color: #b28e77; }
.index-tip :deep(svg) { flex: 0 0 auto; color: var(--resume-orange); font-size: 15px; }
 .index-tip p { margin: 0; font-size: 12px; line-height: 1.6; }
.resume-editor { display: grid; gap: 16px; min-width: 0; }
.resume-editor-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; padding: 7px 4px 5px; }
 .resume-breadcrumb { margin: 0 0 11px; color: #b8a397; font-size: 12px; }
.resume-breadcrumb span { padding: 0 6px; color: #d6c6bc; }
.resume-editor-header h1 { margin: 0; color: #39251c; font-size: 28px; letter-spacing: -.04em; line-height: 1.25; }
.resume-subtitle { margin: 8px 0 0; color: var(--resume-muted); font-size: 13px; }
.header-save-area { display: flex; flex-shrink: 0; align-items: center; gap: 14px; }
 .save-state { display: inline-flex; align-items: center; gap: 6px; color: #b09b8e; font-size: 12px; white-space: nowrap; }
.save-state i { width: 6px; height: 6px; border-radius: 50%; background: #c5b8b1; }
.save-state.saved i { background: #6eae89; }
.resume-editor :deep(.ant-btn-primary) { border-color: var(--resume-orange); background: var(--resume-orange); box-shadow: 0 7px 16px rgb(228 123 50 / 18%); }
.resume-editor :deep(.ant-btn-primary:hover) { border-color: var(--resume-orange-deep); background: var(--resume-orange-deep); }
 .resume-notice { display: flex; align-items: center; gap: 9px; min-height: 42px; padding: 0 15px; border: 1px solid #f6e6d8; border-radius: 9px; background: #fffdfb; color: #9e8373; font-size: 13px; }
.resume-notice :deep(svg) { color: var(--resume-orange); font-size: 16px; }
.resume-notice em { color: var(--resume-orange-deep); font-style: normal; }
.resume-loading { display: grid; gap: 16px; padding: 24px; border: 1px solid var(--resume-line); border-radius: 12px; background: #fff; }
.resume-section { scroll-margin-top: 24px; padding: 22px 24px 25px; border: 1px solid var(--resume-line); border-radius: 13px; background: #fff; box-shadow: 0 4px 15px rgb(180 104 54 / 3%); }
.section-title-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 22px; }
.section-title { display: flex; align-items: flex-start; gap: 12px; }
.section-icon { display: grid; width: 35px; height: 35px; flex: 0 0 auto; place-items: center; border-radius: 10px; background: var(--resume-soft); color: var(--resume-orange); font-size: 17px; }
 .section-title p { margin: 0 0 4px; color: #c1a99a; font-size: 12px; font-weight: 700; letter-spacing: .08em; }
 .section-title h2 { margin: 0; color: #432d20; font-size: 20px; letter-spacing: -.02em; }
 .section-title h2 + span { display: block; margin-top: 5px; color: #a08a7c; font-size: 13px; }
 .section-status { padding: 5px 9px; border-radius: 5px; color: #b8a194; background: #fbf8f5; font-size: 12px; white-space: nowrap; }
.section-status.done { color: #628e74; background: #f2faf5; }
.field-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px 20px; }
 .field { display: grid; min-width: 0; gap: 8px; color: #6e594c; font-size: 14px; }
.field > span { color: #6c5649; font-weight: 600; }
.field > span::before { content: ''; }
.field.required > span::before { margin-right: 4px; color: var(--resume-orange); content: '*'; }
 .field > span small { margin-left: 4px; color: #b7a297; font-size: 12px; font-weight: 400; }
.field-full, .personal-wide { grid-column: 1 / -1; }
 .resume-editor :deep(.ant-input), .resume-editor :deep(.ant-select-selector) { min-height: 40px; border-color: #eee1d8 !important; border-radius: 7px !important; background: #fffdfb !important; color: #493328; font-size: 14px; box-shadow: none !important; }
.resume-editor :deep(.ant-input:hover), .resume-editor :deep(.ant-input:focus), .resume-editor :deep(.ant-select-focused .ant-select-selector), .resume-editor :deep(.ant-select-selector:hover) { border-color: #e9b78e !important; }
.resume-editor :deep(textarea.ant-input) { min-height: 104px; padding: 11px 12px; line-height: 1.7; }
.resume-editor :deep(.ant-select) { width: 100%; }
.resume-editor :deep(.ant-select-selection-placeholder), .resume-editor :deep(.ant-input::placeholder) { color: #c1b1a8; }
.expectation-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
 .add-button { height: 32px; padding: 0 5px; color: var(--resume-orange-deep); font-size: 14px; }
.add-button:hover { color: var(--resume-orange); }
.add-button :deep(svg) { margin-right: 3px; }
.empty-module { display: grid; justify-items: center; gap: 8px; padding: 26px 20px; border: 1px dashed #f0ded1; border-radius: 9px; background: #fffdfb; text-align: center; }
.empty-icon { display: grid; width: 36px; height: 36px; place-items: center; border-radius: 50%; background: var(--resume-soft); color: #da9360; font-size: 17px; }
 .empty-module strong { color: #705546; font-size: 15px; }
 .empty-module p { margin: 0 0 5px; color: #b09a8d; font-size: 13px; }
.resume-editor :deep(.ant-btn-background-ghost) { border-color: #efbb92; color: var(--resume-orange-deep); background: #fffaf5; }
.entry-list { display: grid; gap: 10px; }
.entry-card { padding: 16px 17px; border: 1px solid #f0e4db; border-radius: 9px; background: #fffdfa; }
.entry-card:hover { border-color: #efc19e; }
.entry-card-top { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; }
.entry-card-top > div:first-child { display: grid; gap: 5px; min-width: 0; }
 .entry-card-top strong { color: #4c3528; font-size: 15px; }
 .entry-card-top strong em { margin-left: 5px; padding: 3px 5px; border-radius: 4px; color: #bc681f; background: #fff1e3; font-size: 11px; font-style: normal; font-weight: 500; }
 .entry-card-top span { color: #a28777; font-size: 13px; }
.entry-card-top span i { font-style: normal; }
.entry-card-actions { display: flex; flex: 0 0 auto; align-items: center; gap: 8px; }
 .entry-card-actions small { margin-right: 4px; color: #aa9284; font-size: 12px; }
.entry-card-actions button { display: grid; width: 25px; height: 25px; place-items: center; border: 0; border-radius: 5px; background: transparent; color: #b79f91; cursor: pointer; }
.entry-card-actions button:hover { background: var(--resume-soft); color: var(--resume-orange-deep); }
.entry-meta { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; }
 .entry-meta span { padding: 4px 8px; border-radius: 4px; color: #a88874; background: #fff5ed; font-size: 12px; }
 .entry-description, .entry-achievement { margin: 12px 0 0; color: #856f62; font-size: 13px; line-height: 1.75; white-space: pre-line; }
.entry-achievement { padding-top: 10px; border-top: 1px solid #f4eae4; }
.entry-achievement b { margin-right: 8px; color: #bd6a2a; font-weight: 600; }
 .entry-link { display: block; margin-top: 9px; overflow: hidden; color: var(--resume-orange-deep); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.skill-suggestions { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-top: 13px; }
 .skill-suggestions > span { color: #b09a8c; font-size: 12px; }
 .skill-suggestions button { padding: 5px 9px; border: 1px solid #f0e1d7; border-radius: 5px; background: #fff; color: #9c7966; cursor: pointer; font-size: 12px; }
.skill-suggestions button:hover { border-color: #edbd99; color: var(--resume-orange-deep); }
 .skill-suggestions button :deep(svg) { margin-right: 2px; font-size: 12px; }
 .resume-bottom-actions { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 3px 4px 14px; color: #aa978d; font-size: 12px; }
 .resume-bottom-actions > span { display: inline-flex; align-items: center; gap: 5px; }
 .resume-bottom-actions :deep(svg) { color: #75a184; font-size: 14px; }
 .resume-attachments-panel { position: sticky; top: 20px; display: grid; gap: 16px; min-width: 0; padding: 20px 16px 16px; border: 1px solid var(--resume-line); border-radius: 16px; background: #fff; box-shadow: 0 12px 32px rgb(180 104 54 / 6%); }
 .attachments-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; padding: 0 4px 14px; border-bottom: 1px solid #f5eae2; }
  .attachments-header p { margin: 0 0 5px; color: #c1a99a; font-size: 12px; font-weight: 700; letter-spacing: .08em; }
  .attachments-header h2 { margin: 0; color: #432d20; font-size: 20px; }
 .attachments-add { display: grid; width: 32px; height: 32px; place-items: center; padding: 0; border-radius: 8px; color: var(--resume-orange-deep); }
 .attachments-add:hover { background: var(--resume-soft); color: var(--resume-orange); }
 .attachments-add :deep(svg) { font-size: 20px; }
 .attachments-summary { display: grid; grid-template-columns: 1fr auto; align-items: center; gap: 4px 8px; padding: 12px; border-radius: 10px; background: var(--resume-soft); }
  .attachments-summary span { color: #856f62; font-size: 13px; }
 .attachments-summary strong { color: var(--resume-orange-deep); font-size: 20px; line-height: 1; }
  .attachments-summary small { grid-column: 1 / -1; color: #b09a8d; font-size: 12px; line-height: 1.5; }
 .attachments-loading { display: grid; gap: 14px; }
 .attachments-empty { display: grid; justify-items: center; gap: 8px; padding: 18px 8px 20px; border: 1px dashed #f0ded1; border-radius: 9px; background: #fffdfb; text-align: center; }
 .attachments-empty > span { display: grid; width: 38px; height: 38px; place-items: center; border-radius: 50%; background: var(--resume-soft); color: #da9360; font-size: 18px; }
  .attachments-empty strong { color: #705546; font-size: 15px; }
  .attachments-empty p { margin: 0 0 4px; color: #b09a8d; font-size: 13px; line-height: 1.6; }
  .attachments-empty :deep(.ant-btn) { height: 32px; padding-inline: 11px; font-size: 13px; }
 .attachments-list { display: grid; gap: 10px; }
 .attachment-item { display: grid; grid-template-columns: 36px minmax(0, 1fr) 24px; align-items: center; gap: 9px; padding: 10px 7px; border-bottom: 1px solid #f5eae2; }
 .attachment-item:last-child { border-bottom: 0; }
 .attachment-file-icon { display: grid; width: 36px; height: 42px; place-items: center; border-radius: 8px; color: #fff; background: #d9824a; font-size: 18px; }
 .attachment-file-icon.is-pdf { background: #ed6262; }
 .attachment-file-icon.is-doc, .attachment-file-icon.is-docx { background: #5e91df; }
 .attachment-copy { display: grid; min-width: 0; gap: 3px; }
 .attachment-copy strong, .attachment-copy small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .attachment-copy strong { color: #594033; font-size: 13px; font-weight: 600; }
  .attachment-copy small { color: #ae988a; font-size: 12px; }
 .attachment-preview { display: grid; width: 24px; height: 28px; place-items: center; padding: 0; color: #b8a194; }
 .attachment-preview:hover { color: var(--resume-orange-deep); background: var(--resume-soft); }
 .attachment-upload-cta { display: flex; align-items: center; gap: 9px; width: 100%; padding: 13px 10px; border: 1px solid #f2ddcd; border-radius: 11px; background: linear-gradient(135deg, #fff9f2, #fff5ea); color: inherit; cursor: pointer; text-align: left; transition: border-color .18s ease, transform .18s ease, box-shadow .18s ease; }
 .attachment-upload-cta:hover { border-color: #efbd96; box-shadow: 0 8px 18px rgb(180 104 54 / 8%); transform: translateY(-1px); }
 .attachment-upload-cta-icon { display: grid; width: 31px; height: 31px; flex: 0 0 auto; place-items: center; border-radius: 9px; background: #ffe9d4; color: var(--resume-orange-deep); font-size: 15px; }
 .attachment-upload-cta > span:nth-child(2) { display: grid; min-width: 0; gap: 3px; }
  .attachment-upload-cta strong { color: #694633; font-size: 14px; }
  .attachment-upload-cta small { color: #b18e78; font-size: 12px; }
 .attachment-upload-cta-arrow { margin-left: auto; color: #d69261; font-size: 14px; }
 .attachment-upload-form { display: grid; gap: 16px; padding: 4px 0 10px; }
  .attachment-upload-help { margin: 0; padding: 10px 12px; border-radius: 7px; background: #fff8f1; color: #a17f6b; font-size: 13px; line-height: 1.6; }
 .attachment-file-picker { display: grid; min-height: 116px; justify-items: center; align-content: center; gap: 5px; padding: 15px; border: 1px dashed #efbb92; border-radius: 9px; background: #fffaf5; color: var(--resume-orange); cursor: pointer; text-align: center; }
 .attachment-file-picker:hover, .attachment-file-picker.has-file { border-color: var(--resume-orange); background: var(--resume-soft); }
 .attachment-file-picker input { display: none; }
 .attachment-file-picker > svg { font-size: 25px; }
  .attachment-file-picker strong { max-width: 90%; overflow: hidden; color: #684738; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
  .attachment-file-picker small { color: #ad9485; font-size: 12px; }
 .modal-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px 20px; padding: 8px 0 16px; }
.modal-fields .field-full, .modal-checkbox { grid-column: 1 / -1; }
.date-range { display: grid; grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr); align-items: center; gap: 9px; }
 .date-range > span { color: #a28c7e; font-size: 13px; }
 .modal-checkbox { color: #80685a; font-size: 13px; }
 @media (max-width: 1180px) { .online-resume-page { grid-template-columns: 190px minmax(0, 1fr); gap: 16px; } .resume-attachments-panel { grid-column: 2; position: static; } }
 @media (max-width: 1000px) { .resume-editor-header { align-items: flex-start; flex-direction: column; } .header-save-area { width: 100%; justify-content: space-between; } }
 @media (max-width: 760px) { .online-resume-page { display: block; } .resume-index-panel { position: static; margin-bottom: 16px; padding: 14px; } .resume-attachments-panel { margin-top: 16px; } .resume-index-nav { display: flex; overflow-x: auto; gap: 4px; padding-bottom: 2px; } .resume-index-nav > p, .index-tip { display: none; } .resume-index-nav button { min-width: 126px; min-height: 45px; } .nav-copy small, .nav-done { display: none; } .resume-editor-header h1 { font-size: 23px; } .resume-section { padding: 18px 16px 20px; } .field-grid, .expectation-grid, .modal-fields { grid-template-columns: 1fr; } .field-full, .personal-wide, .modal-fields .field-full, .modal-checkbox { grid-column: auto; } .section-title-row { align-items: flex-start; } .section-title h2 + span { max-width: 240px; line-height: 1.5; } .entry-card-top { gap: 10px; flex-direction: column; } .entry-card-actions { width: 100%; justify-content: space-between; } .entry-card-actions small { margin-right: auto; } .resume-bottom-actions { align-items: flex-start; flex-direction: column; } }
@media (prefers-reduced-motion: reduce) { .resume-index-nav button, .completion-track i { transition: none; } }
</style>
