<script lang="ts" setup>
import {
  computed,
  nextTick,
  onMounted,
  onUnmounted,
  reactive,
  ref,
  watch,
} from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Cascader, message, Modal, Skeleton } from 'ant-design-vue';

import {
  getPortalRecruitPostTypeList,
  type HrmPortalRecruitApi,
} from '#/api/hrm/portal/recruit';
import { uploadHrmRecruitFile } from '#/api/hrm/portal/recruit/file';
import { getHrmRecruitFilePreviewUrl } from '#/api/hrm/portal/recruit/file-url';
import {
  createPortalResume,
  deletePortalResume,
  getPortalResumeList,
  isGeneratedResume,
  isUploadedResume,
  RECRUIT_PROFILE_TITLE,
  type HrmPortalResumeApi,
  uploadGeneratedResume,
  uploadPortalResume,
} from '#/api/hrm/portal/resume';
import { requestClient } from '#/api/request';
import { getAreaTree, type SystemAreaApi } from '#/api/system/area';
import { AreaCascader } from '#/components/area';

import MonthPicker from './components/month-picker.vue';
import { setRecruitAvatar } from './components/recruit-avatar';
import ResumeAiPanel from './components/resume-ai-panel.vue';
import ResumeSection from './components/resume-section.vue';
import {
  buildResumeVersionIdempotencyKey,
  createPortalResumeVersion,
  getPortalResumeVersionList,
  type HrmPortalResumeVersionApi,
} from '#/api/hrm/portal/resume-version';
import {
  isUnhandledResumeToolRequest,
  type ResumeToolRequest,
} from './components/resume-tools';
import { intakeResumeFile } from './components/resume-intake';

/**
 * 父级（index.vue 右上角简历工具菜单）传入的动作请求。
 * 由 nonce 保证同一次点击只被消费一次，属性残留不会反复打开弹窗。
 */
const props = defineProps<{
  pendingAction?: ResumeToolRequest;
}>();

const emit = defineEmits<{
  actionHandled: [];
}>();

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
  avatar: string;
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

type AreaTreeNode = SystemAreaApi.Area & {
  children?: AreaTreeNode[];
  disabled?: boolean;
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
const birthMonthMinYear = 1960;
const birthMonthMaxYear = now.getFullYear();
const skillSuggestions = ['办公软件', '工程制图', '项目管理', '施工组织', '质量验收', '沟通协调'];
const MAX_RESUME_SIZE = 10 * 1024 * 1024;
const MAX_AVATAR_SIZE = 5 * 1024 * 1024;
const ACCEPTED_RESUME_EXTENSIONS = ['pdf', 'doc', 'docx'];

type ResumeSectionId = (typeof sectionItems)[number]['id'];

const loading = ref(true);
const submitting = ref(false);
const uploadSubmitting = ref(false);
const activeSection = ref<ResumeSectionId>('personal');
const lastSavedAt = ref('');
let sectionSyncFrame: number | undefined;
const postTypes = ref<HrmPortalRecruitApi.PostType[]>([]);
const attachments = ref<HrmPortalResumeApi.Resume[]>([]);
const uploadModalOpen = ref(false);
const previewOpen = ref(false);
/** 附件管理面板已从在线简历页移到「简历」下拉入口，按需弹出 */
const attachmentsModalOpen = ref(false);
const uploadInput = ref<HTMLInputElement>();
const selectedUploadFile = ref<File>();
const uploadForm = reactive({ title: '', remark: '' });
const avatarInput = ref<HTMLInputElement>();
const avatarUploading = ref(false);
const generatedModalOpen = ref(false);
const generatedSubmitting = ref(false);
const generatedTitle = ref('');
const positionValue = ref('');
const domicileAreaId = ref<number>();
const areaTree = ref<AreaTreeNode[]>([]);
const expectedCityAreaIds = ref<Array<number | undefined>>([]);
const expectedCityPickerPath = ref<number[]>([]);
const expectedCityPickerOpen = ref(false);
const expectedCitySearchValue = ref('');
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

const areaFieldNames = {
  children: 'children',
  label: 'name',
  value: 'id',
};

function applyAreaSelectableLevels(
  tree: AreaTreeNode[],
  selectableLevels: number[],
  depth = 1,
): AreaTreeNode[] {
  return tree.map((area) => ({
    ...area,
    disabled: !selectableLevels.includes(depth),
    children: area.children?.length
      ? applyAreaSelectableLevels(area.children, selectableLevels, depth + 1)
      : undefined,
  }));
}

const expectedCityAreaOptions = computed(() =>
  applyAreaSelectableLevels(areaTree.value, [1, 2, 3]),
);

const statusOptions = computed(() =>
  form.identity === 'student' ? studentStatusOptions : workerStatusOptions,
);
const positionOptions = computed<PositionOption[]>(() => {
  const children = postTypes.value.filter((item) => item.parentId);
  const source = children.length ? children : postTypes.value;
  return source.length ? source : fallbackPositions;
});
const completionItems = computed(() => ({
  personal: Boolean(form.name.trim() && form.birthMonth && form.mobile.trim() && form.email.trim() && form.identity && form.employmentStatus && form.domicile),
  strengths: Boolean(form.strengths.trim()),
  expectation: Boolean(form.expectedPostName && form.industry && form.expectedCities.length),
  experience: form.experiences.length > 0,
  project: form.projects.length > 0,
  education: form.educationExperiences.length > 0,
  skills: Boolean(form.skills.trim()),
}));
const completedSections = computed(() => Object.values(completionItems.value).filter(Boolean).length);
const completionRate = computed(() => Math.round((completedSections.value / sectionItems.length) * 100));
/**
 * 完成度分段条的读屏文案。
 * 七段本身是纯视觉，靠这一句把「哪几个模块还没填」讲清楚；
 * 分段不做成按钮：下方目录已经承担跳转，两处都能点会重复。
 */
const completionSummary = computed(
  () =>
    `简历完成度 ${completionRate.value}%：${sectionItems
      .map(
        (item) =>
          `${item.label}${completionItems.value[item.id] ? '已完善' : '未完善'}`,
      )
      .join('，')}`,
);
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
    avatar: '', name: '', birthMonth: '', mobile: '', email: '', identity: 'worker', employmentStatus: workerStatusOptions[2]!, domicile: '', strengths: '',
    jobType: 'fulltime', expectedPostName: '', industry: '', salary: 'all', expectedCities: [], experiences: [], projects: [], educationExperiences: [], skills: '',
  };
}

function asString(value: unknown) {
  return typeof value === 'string' ? value : value == null ? '' : String(value);
}

function normalizeBirthMonth(value: unknown) {
  const birthMonth = asString(value).trim();
  const match = birthMonth.match(/^(\d{4})\s*(?:年|-|\/|\.)\s*(\d{1,2})\s*月?$/);
  if (!match) return birthMonth;
  const month = Number(match[2]);
  return month >= 1 && month <= 12 ? `${match[1]}-${String(month).padStart(2, '0')}` : birthMonth;
}

function findAreaPath(tree: AreaTreeNode[], areaId?: number): AreaTreeNode[] | undefined {
  if (areaId === undefined) return undefined;
  for (const area of tree) {
    if (area.id === areaId) return [area];
    const childPath = area.children?.length ? findAreaPath(area.children, areaId) : undefined;
    if (childPath) return [area, ...childPath];
  }
}

function findAreaIdByName(tree: AreaTreeNode[], name: string): number | undefined {
  const normalizedName = name.replace(/特别行政区|自治区|省|市|区|县/g, '').trim();
  for (const area of tree) {
    const normalizedAreaName = area.name.replace(/特别行政区|自治区|省|市|区|县/g, '').trim();
    if ((area.name === name || normalizedAreaName === normalizedName) && area.id !== undefined) return area.id;
    const childId = area.children?.length ? findAreaIdByName(area.children, name) : undefined;
    if (childId !== undefined) return childId;
  }
}

function getAreaNamePath(areaId?: number) {
  return findAreaPath(areaTree.value, areaId)?.map((area) => area.name) || [];
}

function getDomicileLookupName(value: string) {
  const parts = value.split(/\s*[\/／、,，]\s*/).map((part) => part.trim()).filter(Boolean);
  return parts.at(-1) || value.trim();
}

function syncDomicileArea(areaId?: number) {
  const matchedAreaId = areaId ?? findAreaIdByName(areaTree.value, getDomicileLookupName(form.domicile));
  domicileAreaId.value = matchedAreaId;
  const areaNamePath = getAreaNamePath(matchedAreaId);
  if (areaNamePath.length) form.domicile = areaNamePath.join(' / ');
}

function handleDomicileChange(areaId?: number) {
  domicileAreaId.value = areaId;
  const areaNamePath = getAreaNamePath(areaId);
  form.domicile = areaNamePath.join(' / ');
}

function syncExpectedCityAreas(areaIds: unknown) {
  const storedAreaIds = Array.isArray(areaIds) ? areaIds : [];
  expectedCityAreaIds.value = form.expectedCities.map((city, index) => {
    const storedAreaId = Number(storedAreaIds[index]);
    const matchedAreaId = Number.isInteger(storedAreaId) && findAreaPath(areaTree.value, storedAreaId)
      ? storedAreaId
      : findAreaIdByName(areaTree.value, getDomicileLookupName(city));
    const areaNamePath = getAreaNamePath(matchedAreaId);
    if (areaNamePath.length) form.expectedCities[index] = areaNamePath.join(' / ');
    return matchedAreaId;
  });
}

function handleExpectedCityChange(areaId?: number) {
  if (areaId === undefined) return;
  if (expectedCityAreaIds.value.some((selectedAreaId) => selectedAreaId === areaId)) {
    message.info('该期望工作城市已选择');
    return;
  }
  if (form.expectedCities.length >= 3) {
    message.info('期望工作城市最多选择 3 个');
    return;
  }
  const areaNamePath = getAreaNamePath(areaId);
  if (!areaNamePath.length) return;
  form.expectedCities.push(areaNamePath.join(' / '));
  expectedCityAreaIds.value.push(areaId);
}

function clearExpectedCityPicker() {
  expectedCityPickerPath.value = [];
  expectedCitySearchValue.value = '';
}

function handleExpectedCitySearch(value: string) {
  expectedCitySearchValue.value = value;
}

function handleExpectedCityPathChange(value: unknown) {
  const path = Array.isArray(value) ? value : [];
  const leafValue = path.at(-1);
  const areaId = typeof leafValue === 'number' ? leafValue : Number(leafValue);
  if (!path.length || Number.isNaN(areaId)) return;
  expectedCityPickerOpen.value = false;
  clearExpectedCityPicker();
  handleExpectedCityChange(areaId);
}

function handleExpectedCityPopupVisibleChange(open: boolean) {
  expectedCityPickerOpen.value = open;
  if (!open) clearExpectedCityPicker();
}

function removeExpectedCity(index: number) {
  form.expectedCities.splice(index, 1);
  expectedCityAreaIds.value.splice(index, 1);
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
    // 头像由本人上传后存在这里。以前它只从 JSON 里读出来展示、保存时不写回，
    // 于是「资料完善里传的头像，在在线简历页点一次保存就没了」。
    avatar: asString(value.avatar),
    name: asString(value.name), birthMonth: normalizeBirthMonth(value.birthMonth), mobile: asString(value.mobile || value.phone), email: asString(value.email), identity,
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
    try {
      areaTree.value = (await getAreaTree()) as AreaTreeNode[];
    } catch (error) {
      console.warn('[OnlineResume] 加载户籍地区失败', error);
    }
    attachments.value = (resumes || []).filter((resume) => resume.resumeType === 1);
    // 记录在线简历草稿编号，固化版本时作为来源
    onlineDraftId.value = (resumes || []).find(
      (resume) =>
        resume.resumeType === 2 &&
        [RECRUIT_PROFILE_TITLE, '在线简历'].includes(resume.title),
    )?.id;
    const profile = readOnlineProfile(resumes || []);
    if (profile) Object.assign(form, normalizeProfile(profile));
    const profileAreaId = profile && typeof profile.domicileAreaId === 'number' ? profile.domicileAreaId : undefined;
    syncDomicileArea(profileAreaId);
    syncExpectedCityAreas(profile?.expectedCityAreaIds);
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
const uploadedAttachments = computed(() => visibleAttachments.value.filter(isUploadedResume));
const generatedAttachments = computed(() => visibleAttachments.value.filter(isGeneratedResume));

function hasAnalyzableContent(resume: HrmPortalResumeApi.Resume) {
  const content = resume.content?.trim();
  if (!content) return false;
  if (content.startsWith('{')) {
    try {
      const parsed = JSON.parse(content);
      return Boolean(parsed && typeof parsed === 'object' && Object.keys(parsed).length);
    } catch {
      return false;
    }
  }
  return content.length >= 50;
}

function attachmentAvatarUrl(resume: HrmPortalResumeApi.Resume) {
  return resume.avatarUrl ? getHrmRecruitFilePreviewUrl(resume.avatarUrl) : '';
}

const latestAttachmentAvatar = computed(() => {
  const resume = visibleAttachments.value.find((item) => item.avatarUrl);
  return resume ? attachmentAvatarUrl(resume) : '';
});
/**
 * 头像来源优先级：本人上传的 > 附件简历里提取出来的。
 * 上传的那张排在最前，否则保存完头像还是显示附件里那张，看着像没生效。
 */
const personalAvatarUrl = computed(
  () => getHrmRecruitFilePreviewUrl(form.avatar) || latestAttachmentAvatar.value,
);
const avatarHint = computed(() => {
  if (form.avatar) return '已上传新头像，点「保存简历」后生效';
  if (latestAttachmentAvatar.value) return '当前是附件简历里提取的头像，可上传自己的替换';
  // 没有头像时不放操作说明：头像块本身可点，悬停还有相机遮罩，够了
  return '';
});

/** 上传头像。与「资料完善」用同一个接口与尺寸限制（category=avatar，服务端上限 10MB） */
async function handleAvatarUpload(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  if (!file.type.startsWith('image/') || file.size > MAX_AVATAR_SIZE) {
    message.warning('请选择 5MB 以内的图片');
    input.value = '';
    return;
  }
  avatarUploading.value = true;
  try {
    form.avatar = await uploadHrmRecruitFile(file, 'avatar');
    // 顶栏是另一个组件，同步推过去，不用等下一次请求
    setRecruitAvatar(form.avatar);
    message.success('头像已上传，点「保存简历」后生效');
  } finally {
    avatarUploading.value = false;
    input.value = '';
  }
}

function removeAvatar() {
  form.avatar = '';
  setRecruitAvatar('');
  message.success('已移除上传的头像，点「保存简历」后生效');
}

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
  return getHrmRecruitFilePreviewUrl(fileUrl);
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

function handleAttachmentAction(
  action: string | number,
  resume: HrmPortalResumeApi.Resume,
) {
  if (String(action) === 'preview') {
    void previewAttachment(resume.fileUrl);
    return;
  }
  if (String(action) !== 'delete') return;

  Modal.confirm({
    title: '删除附件简历',
    content: `确定删除“${resume.title}”吗？删除后无法恢复。`,
    okText: '删除',
    cancelText: '取消',
    okType: 'danger',
    async onOk() {
      try {
        const deleted = await deletePortalResume(resume.id);
        if (!deleted) throw new Error('附件不存在或已删除');
        attachments.value = attachments.value.filter((item) => item.id !== resume.id);
        message.success('附件简历已删除');
      } catch (error) {
        const reason = error instanceof Error ? error.message : '';
        message.error(reason || '删除失败，请稍后重试');
        throw error;
      }
    },
  });
}

function handleAttachmentMenuClick(
  event: { key: string | number },
  resume: HrmPortalResumeApi.Resume,
) {
  handleAttachmentAction(event.key, resume);
}

function openUploadModal() {
  uploadModalOpen.value = true;
}

/** 「简历」下拉里的「附件管理」入口：打开附件管理面板 */
function openAttachmentsModal() {
  attachmentsModalOpen.value = true;
}

/* ===== 个人简历版本 ===== */

/** 当前在线简历草稿编号，作为固化版本的来源 */
const onlineDraftId = ref<number>();
const versionPanelOpen = ref(false);
const versionLoading = ref(false);
const versionSaving = ref(false);
const resumeVersions = ref<HrmPortalResumeVersionApi.Version[]>([]);

async function loadResumeVersions() {
  versionLoading.value = true;
  try {
    resumeVersions.value =
      (await getPortalResumeVersionList(onlineDraftId.value)) || [];
  } catch (error) {
    console.warn('[OnlineResume] 加载简历版本失败', error);
    resumeVersions.value = [];
  } finally {
    versionLoading.value = false;
  }
}

async function openVersionPanel() {
  versionPanelOpen.value = true;
  await loadResumeVersions();
}

function formatVersionTime(value?: string) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—';
}

/**
 * 把当前在线简历固化为新版本。
 *
 * 服务端按幂等键去重，因此重复点击不会生成多份版本；
 * 固化只读取当前草稿内容，不会改动草稿本身。
 */
async function saveResumeVersion() {
  if (versionSaving.value) return;
  const content = JSON.stringify(serializeProfile());
  if (!content || content === '{}') {
    message.warning('请先完善在线简历后再保存版本');
    return;
  }
  versionSaving.value = true;
  try {
    await createPortalResumeVersion({
      sourceResumeId: onlineDraftId.value,
      createType: 1,
      changeSummary: `手动固化 ${new Date().toLocaleString('zh-CN')}`,
      idempotencyKey: buildResumeVersionIdempotencyKey('manual'),
    });
    message.success('已保存为新的简历版本');
    await loadResumeVersions();
  } catch (error: any) {
    message.error(error?.message || '保存简历版本失败');
  } finally {
    versionSaving.value = false;
  }
}

/* ===== AI 简历助手 ===== */

const aiPanelOpen = ref(false);

function openAiPanel() {
  aiPanelOpen.value = true;
}

/** AI 面板里点击「简历版本」链接：先关掉 AI 面板再打开版本面板，重新打开时版本列表会刷新 */
function handleOpenVersionsFromAi() {
  aiPanelOpen.value = false;
  void openVersionPanel();
}

/** 已消费的动作请求 nonce，避免同一次请求被反复执行 */
const handledResumeActionNonce = ref(0);

function runPendingResumeAction() {
  const request = props.pendingAction;
  if (!isUnhandledResumeToolRequest(request, handledResumeActionNonce.value)) return;
  handledResumeActionNonce.value = request.nonce;
  if (request.action === 'upload') openAttachmentsModal();
  if (request.action === 'edit') void focusResumeEditor();
  if (request.action === 'version') void openVersionPanel();
  if (request.action === 'ai') openAiPanel();
  emit('actionHandled');
}

watch(() => props.pendingAction, runPendingResumeAction, { immediate: true });

/** 简历页工具条里的「在线编辑」：等正文渲染完成后定位到第一段简历内容 */
async function focusResumeEditor() {
  await nextTick();
  selectSection('personal');
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
    const fileUrl = await uploadHrmRecruitFile(file, 'resume');
    // 提取正文后再让 AI 整理成结构化字段：纯文本入库的话，预览只能把全文
    // 挂在「个人优势」下，逐字段的 AI 建议也定位不到
    const intake = await intakeResumeFile(file);
    let avatarUrl: string | undefined;
    if (intake.avatar) {
      try {
        avatarUrl = await uploadHrmRecruitFile(new File([intake.avatar], `${file.name}-avatar.jpg`, { type: 'image/jpeg' }), 'resume-avatar');
      } catch (error) {
        console.warn('[OnlineResume] 上传简历头像失败，继续保存附件', error);
      }
    }
    await uploadPortalResume({
      title: uploadForm.title.trim(),
      fileName: file.name,
      fileUrl,
      remark: uploadForm.remark.trim() || undefined,
      content: intake.content || undefined,
      avatarUrl,
    });
    const bodyState = !intake.content
      ? '未提取到可分析正文'
      : intake.structured
        ? '正文已整理成可编辑字段'
        : intake.transcribed
          ? '已用 AI 识别扫描件正文'
          : '已提取正文';
    const avatarState = avatarUrl ? '和头像' : '';
    message.success(`简历附件已保存，${bodyState}${avatarState}`);
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

async function createGeneratedAttachment() {
  if (generatedSubmitting.value) return;
  const content = JSON.stringify(serializeProfile());
  if (content.length < 50) {
    message.warning('请先完善在线简历，再制作附件简历');
    selectSection('personal');
    return;
  }
  generatedSubmitting.value = true;
  try {
    const docxModule = await import('docx');
    const docx = (docxModule as any).default ?? docxModule;
    const lines = [
      form.name && `姓名：${form.name}`,
      form.expectedPostName && `期望职位：${form.expectedPostName}`,
      form.strengths && `个人优势：${form.strengths}`,
      form.skills && `专业技能：${form.skills}`,
      ...form.experiences.map((item) => `${item.company || '工作经历'} ${item.position || ''}\n${item.description || ''}\n${item.achievements || ''}`),
      ...form.educationExperiences.map((item) => `${item.school || '教育经历'} ${item.major || ''} ${item.degree || ''}`),
    ].filter(Boolean).join('\n\n');
    const document = new docx.Document({ sections: [{ children: lines.split(/\n+/).map((line: string) => new docx.Paragraph({ children: [new docx.TextRun(line)] })) }] });
    const blob = await docx.Packer.toBlob(document);
    const fileName = `${(generatedTitle.value.trim() || form.name || '我的附件简历')}.docx`;
    const fileUrl = await uploadHrmRecruitFile(new File([blob], fileName, { type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' }), 'resume');
    await uploadGeneratedResume({ title: generatedTitle.value.trim() || form.name || '我的附件简历', fileName, fileUrl, content, avatarUrl: form.avatar || undefined });
    generatedModalOpen.value = false;
    generatedTitle.value = '';
    await loadResume();
    message.success('附件简历已生成');
  } catch (error: any) {
    message.error(error?.message || '制作附件简历失败，请稍后重试');
  } finally {
    generatedSubmitting.value = false;
  }
}

/**
 * 吸顶导航是 sticky 的，模块顶部必须停靠在它下方，否则会被导航盖住一部分。
 * 高度随断点变化，所以运行时量取，不写死。
 *
 * 注意这里量的是 .recruit-topbar —— 本页顶部吸着的只有它（60px）。
 * 原先只找职位页那条 .recruit-subnav，而本组件根本不渲染它，取不到就按 0 算，
 * 落点只剩 16px，模块顶部有 44px 藏在导航条底下。两个选择器都算上，
 * 将来这个组件挂到带二级导航的页面也不会再算漏。
 */
function getSectionScrollOffset() {
  const height = ['.recruit-topbar', '.recruit-subnav'].reduce(
    (sum, selector) =>
      sum + (document.querySelector(selector)?.getBoundingClientRect().height ?? 0),
    0,
  );
  return height + 16;
}

function selectSection(id: ResumeSectionId) {
  activeSection.value = id;
  const target = document.getElementById(`resume-section-${id}`);
  if (!target) return;
  // 不用 scrollIntoView：它的落点由 CSS scroll-margin-top 决定，无法跟随
  // 吸顶导航的实际高度，导致模块顶部被遮挡。
  const top =
    target.getBoundingClientRect().top + window.scrollY - getSectionScrollOffset();
  window.scrollTo({ top: Math.max(0, top), behavior: 'smooth' });
}

/**
 * 根据右侧分区在页面中的位置同步左侧目录，避免只在点击目录时才更新高亮。
 * 判定锚点必须与 selectSection 的落点一致（同一个吸顶偏移量），否则高亮
 * 会和实际可见的分区错位。
 */
function syncActiveSectionFromScroll() {
  if (sectionSyncFrame !== undefined) return;
  sectionSyncFrame = window.requestAnimationFrame(() => {
    sectionSyncFrame = undefined;
    const anchor = getSectionScrollOffset() + 1;
    let current: ResumeSectionId = sectionItems[0].id;
    for (const item of sectionItems) {
      const section = document.getElementById(`resume-section-${item.id}`);
      if (section && section.getBoundingClientRect().top <= anchor) {
        current = item.id;
      }
    }
    // 最后一个分区在页面滚到底时也到不了锚点位置，会导致它永远高亮不到，
    // 因此额外判断「已滚到底」直接选中最后一项（页面本身可滚动时才生效，
    // 避免内容不足一屏时误判）。
    const doc = document.documentElement;
    const scrollable = doc.scrollHeight - window.innerHeight > 4;
    if (scrollable && window.scrollY + window.innerHeight >= doc.scrollHeight - 4) {
      current = sectionItems[sectionItems.length - 1]!.id;
    }
    if (activeSection.value !== current) activeSection.value = current;
  });
}

/** 预览弹窗里把枚举值翻成人话 */
const jobTypeLabel = computed(
  () => jobTypeOptions.find((item) => item.value === form.jobType)?.label ?? '不限',
);
const salaryLabel = computed(
  () => salaryOptions.find((item) => item.value === form.salary)?.label ?? '不限',
);

function setIdentity(identity: Identity) {
  form.identity = identity;
  if (!statusOptions.value.includes(form.employmentStatus)) form.employmentStatus = statusOptions.value[2]!;
}

function handlePositionChange(value: string) {
  const option = positionOptions.value.find((item) => String(item.id ?? item.name) === value);
  form.expectedPostId = option?.id;
  form.expectedPostName = option?.name || value;
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
    domicileAreaId: domicileAreaId.value,
    expectedCityAreaIds: expectedCityAreaIds.value,
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
  if (!form.name.trim() || !form.mobile.trim() || !form.email.trim()) {
    message.warning('请先在个人信息中填写姓名、电话和邮箱');
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

onMounted(async () => {
  await loadResume();
  await nextTick();
  window.addEventListener('scroll', syncActiveSectionFromScroll, { passive: true });
  window.addEventListener('resize', syncActiveSectionFromScroll, { passive: true });
  syncActiveSectionFromScroll();
});

onUnmounted(() => {
  window.removeEventListener('scroll', syncActiveSectionFromScroll);
  window.removeEventListener('resize', syncActiveSectionFromScroll);
  if (sectionSyncFrame !== undefined) window.cancelAnimationFrame(sectionSyncFrame);
});
</script>

<template>
  <div class="online-resume-page">
    <aside class="resume-index-panel">
      <div class="completion-card">
        <div class="completion-card-top"><span>简历完成度</span><strong>{{ completionRate }}%</strong></div>
        <div class="completion-segments" role="img" :aria-label="completionSummary">
          <i
            v-for="item in sectionItems"
            :key="item.id"
            :class="{ done: completionItems[item.id], current: activeSection === item.id }"
          ></i>
        </div>
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
      <div class="resume-toolbar">
        <strong>我的在线简历</strong>
        <div class="resume-toolbar-actions">
          <button type="button" @click="previewOpen = true">预览</button>
          <span class="resume-toolbar-divider" aria-hidden="true"></span>
          <button type="button" @click="openUploadModal">导入已有简历</button>
          <span class="resume-toolbar-divider" aria-hidden="true"></span>
          <button type="button" @click="generatedModalOpen = true">导出为附件简历</button>
        </div>
      </div>
      <div v-if="loading" class="resume-loading"><Skeleton active :paragraph="{ rows: 4 }" /><Skeleton active :paragraph="{ rows: 5 }" /></div>

      <template v-else>
        <ResumeSection
          section-id="personal"
          icon="lucide:user-round"
          title="个人信息"
          description="让招聘方快速了解你，并在合适的时候联系你。"
          :done="completionItems.personal"
        >
          <template #aside>
            <div class="resume-personal-avatar">
              <button v-if="form.avatar" class="resume-avatar-remove" type="button" @click="removeAvatar">
                <IconifyIcon icon="lucide:x" />移除
              </button>
              <button
                class="resume-avatar-trigger"
                type="button"
                :title="avatarHint"
                :aria-label="personalAvatarUrl ? '更换头像' : '上传头像'"
                :aria-busy="avatarUploading"
                @click="avatarInput?.click()"
              >
                <img v-if="personalAvatarUrl" :src="personalAvatarUrl" alt="个人头像" />
                <span v-else class="resume-personal-avatar-placeholder"><IconifyIcon icon="lucide:user-round" /></span>
                <span class="resume-avatar-mask" aria-hidden="true">
                  <IconifyIcon :icon="avatarUploading ? 'lucide:loader-circle' : 'lucide:camera'" />
                </span>
              </button>
              <input ref="avatarInput" hidden type="file" accept="image/*" @change="handleAvatarUpload" />
            </div>
          </template>

          <div class="field-grid personal-grid">
              <label class="field required"><span>姓名</span><a-input v-model:value="form.name" maxlength="30" placeholder="请输入真实姓名" /></label>
              <label class="field required"><span>出生年月</span><MonthPicker v-model="form.birthMonth" placeholder="请选择出生年月" :min-year="birthMonthMinYear" :max-year="birthMonthMaxYear" /></label>
              <label class="field required"><span>电话</span><a-input v-model:value="form.mobile" maxlength="20" placeholder="请输入常用联系电话" /></label>
              <label class="field required"><span>邮箱</span><a-input v-model:value="form.email" maxlength="80" placeholder="用于接收招聘方联系邮件" /></label>
              <label class="field required"><span>我的身份</span><a-select :value="form.identity" popup-class-name="resume-select-dropdown" placeholder="请选择身份" @change="setIdentity"><a-select-option value="worker">职场人</a-select-option><a-select-option value="student">学生</a-select-option></a-select></label>
              <label class="field required"><span>当前求职状态</span><a-select v-model:value="form.employmentStatus" popup-class-name="resume-select-dropdown" placeholder="请选择当前状态"><a-select-option v-for="item in statusOptions" :key="item" :value="item">{{ item }}</a-select-option></a-select></label>
              <label class="field required"><span>户籍地</span><AreaCascader v-model="domicileAreaId" allow-clear class="domicile-area-cascader" :change-on-select="true" placeholder="请选择户籍所在地区" popup-class-name="recruit-filter-dropdown" :selectable-levels="[1, 2, 3]" show-search @update:model-value="handleDomicileChange" /></label>
          </div>
        </ResumeSection>

        <ResumeSection
          section-id="strengths"
          icon="lucide:sparkles"
          title="个人优势"
          description="用几句话说清楚，你最擅长解决什么问题。"
          :done="completionItems.strengths"
        >
          <label class="field field-full"><span>个人优势 <small>建议 3-5 句话</small></span><a-textarea v-model:value="form.strengths" :maxlength="500" :rows="5" show-count placeholder="例如：5 年防腐保温工程经验，熟悉施工组织、质量验收与现场协调，能够独立推进项目从开工到交付。" /></label>
        </ResumeSection>

        <ResumeSection
          section-id="expectation"
          icon="lucide:compass"
          title="期望职位"
          description="明确你的下一站，帮助岗位匹配更精准。"
          :done="completionItems.expectation"
        >
          <div class="field-grid expectation-grid">
            <label class="field"><span>求职类型</span><a-select v-model:value="form.jobType" popup-class-name="resume-select-dropdown"><a-select-option v-for="item in jobTypeOptions" :key="item.value" :value="item.value">{{ item.label }}</a-select-option></a-select></label>
            <label class="field"><span>期望职位</span><a-select v-model:value="positionValue" popup-class-name="resume-select-dropdown" show-search placeholder="请选择期望职位" @change="handlePositionChange"><a-select-option v-for="item in positionOptions" :key="item.id ?? item.name" :value="String(item.id ?? item.name)">{{ item.name }}</a-select-option></a-select></label>
            <label class="field"><span>期望行业</span><a-select v-model:value="form.industry" popup-class-name="resume-select-dropdown" show-search placeholder="请选择期望行业"><a-select-option v-for="item in industryOptions" :key="item" :value="item">{{ item }}</a-select-option></a-select></label>
            <label class="field"><span>薪资要求</span><a-select v-model:value="form.salary" popup-class-name="resume-select-dropdown"><a-select-option v-for="item in salaryOptions" :key="item.value" :value="item.value">{{ item.label }}</a-select-option></a-select></label>
            <div class="field field-full"><span>期望工作城市 <small>最多选择 3 个城市</small></span><div class="expected-cities-picker"><div v-if="form.expectedCities.length" class="expected-city-tags"><button v-for="(city, index) in form.expectedCities" :key="`${city}-${index}`" class="expected-city-tag" type="button" @click="removeExpectedCity(index)"><span>{{ city }}</span><IconifyIcon icon="lucide:x" /></button></div><Cascader v-model:value="expectedCityPickerPath" class="expected-city-cascader" :change-on-select="true" :disabled="form.expectedCities.length >= 3" :field-names="areaFieldNames" :options="expectedCityAreaOptions" :placeholder="form.expectedCities.length >= 3 ? '已选择 3 个城市' : '请选择期望工作的城市'" popup-class-name="recruit-filter-dropdown" :open="expectedCityPickerOpen" :search-value="expectedCitySearchValue" show-search @change="handleExpectedCityPathChange" @search="handleExpectedCitySearch" @dropdown-visible-change="handleExpectedCityPopupVisibleChange" /></div></div>
          </div>
        </ResumeSection>

        <ResumeSection
          section-id="experience"
          icon="lucide:briefcase-business"
          title="工作 / 实习经历"
          description="从最近一段经历开始，突出职责和可验证的成果。"
          :done="completionItems.experience"
        >
          <template #actions>
            <a-button class="add-button" type="link" @click="openExperience()"><IconifyIcon icon="lucide:plus" /> 添加经历</a-button>
          </template>
          <div v-if="!form.experiences.length" class="empty-module"><span class="empty-icon"><IconifyIcon icon="lucide:briefcase-business" /></span><strong>还没有工作或实习经历</strong><p>如果你是应届生，可以先添加实习经历，或跳过此模块。</p><a-button type="primary" ghost @click="openExperience()">添加第一段经历</a-button></div>
          <div v-else class="entry-list"><article v-for="(item, index) in form.experiences" :key="item.id" class="entry-card"><div class="entry-card-top"><div><strong>{{ item.position || '未填写职位' }}</strong><span>{{ item.company || '未填写公司' }}</span></div><div class="entry-card-actions"><small>{{ formatPeriod(item.start, item.end) }}</small><button type="button" aria-label="编辑工作经历" @click="openExperience(index)"><IconifyIcon icon="lucide:pen-line" /></button><button type="button" aria-label="删除工作经历" @click="removeExperience(index)"><IconifyIcon icon="lucide:trash-2" /></button></div></div><div class="entry-meta"><span v-if="item.industry">{{ item.industry }}</span><span v-if="item.department">{{ item.department }}</span></div><p v-if="item.description" class="entry-description">{{ item.description }}</p><p v-if="item.achievements" class="entry-achievement"><b>工作业绩</b>{{ item.achievements }}</p></article></div>
        </ResumeSection>

        <ResumeSection
          section-id="project"
          icon="lucide:folder-kanban"
          title="项目经历"
          description="展示你参与过的项目，以及你在其中承担的角色。"
          :done="completionItems.project"
        >
          <template #actions>
            <a-button class="add-button" type="link" @click="openProject()"><IconifyIcon icon="lucide:plus" /> 添加项目</a-button>
          </template>
          <div v-if="!form.projects.length" class="empty-module"><span class="empty-icon"><IconifyIcon icon="lucide:folder-plus" /></span><strong>还没有项目经历</strong><p>从最能体现专业能力的项目写起，描述你的角色与结果。</p><a-button type="primary" ghost @click="openProject()">添加第一个项目</a-button></div>
          <div v-else class="entry-list"><article v-for="(item, index) in form.projects" :key="item.id" class="entry-card"><div class="entry-card-top"><div><strong>{{ item.name || '未填写项目名称' }} <em v-if="item.highlight">重点项目</em></strong><span>{{ item.role || '未填写项目角色' }}</span></div><div class="entry-card-actions"><small>{{ formatPeriod(item.start, item.end) }}</small><button type="button" aria-label="编辑项目经历" @click="openProject(index)"><IconifyIcon icon="lucide:pen-line" /></button><button type="button" aria-label="删除项目经历" @click="removeProject(index)"><IconifyIcon icon="lucide:trash-2" /></button></div></div><a v-if="item.link" class="entry-link" :href="item.link" target="_blank" rel="noreferrer">{{ item.link }}</a><p v-if="item.description" class="entry-description">{{ item.description }}</p><p v-if="item.achievements" class="entry-achievement"><b>项目业绩</b>{{ item.achievements }}</p></article></div>
        </ResumeSection>

        <ResumeSection
          section-id="education"
          icon="lucide:graduation-cap"
          title="教育经历"
          description="学校、专业和在校经历，构成你的专业底色。"
          :done="completionItems.education"
        >
          <template #actions>
            <a-button class="add-button" type="link" @click="openEducation()"><IconifyIcon icon="lucide:plus" /> 添加经历</a-button>
          </template>
          <div v-if="!form.educationExperiences.length" class="empty-module"><span class="empty-icon"><IconifyIcon icon="lucide:graduation-cap" /></span><strong>还没有教育经历</strong><p>填写最高学历，也可以继续补充多段教育经历。</p><a-button type="primary" ghost @click="openEducation()">添加教育经历</a-button></div>
          <div v-else class="entry-list"><article v-for="(item, index) in form.educationExperiences" :key="item.id" class="entry-card"><div class="entry-card-top"><div><strong>{{ item.degree || '未填写学历' }} · {{ item.school || '未填写学校' }}</strong><span>{{ item.major || '未填写专业' }}<i v-if="item.schoolType"> · {{ item.schoolType }}</i></span></div><div class="entry-card-actions"><small>{{ formatPeriod(item.start, item.end) }}</small><button type="button" aria-label="编辑教育经历" @click="openEducation(index)"><IconifyIcon icon="lucide:pen-line" /></button><button type="button" aria-label="删除教育经历" @click="removeEducation(index)"><IconifyIcon icon="lucide:trash-2" /></button></div></div><p v-if="item.experience" class="entry-description">{{ item.experience }}</p></article></div>
        </ResumeSection>

        <ResumeSection
          section-id="skills"
          icon="lucide:badge-check"
          title="专业技能"
          description="写下岗位最关心的工具、方法和专业能力。"
          :done="completionItems.skills"
        >
          <label class="field field-full"><span>专业技能 <small>可用顿号或换行分隔</small></span><a-textarea v-model:value="form.skills" :maxlength="1000" :rows="5" show-count placeholder="例如：熟悉施工组织设计、工程质量验收、工程制图，具备项目现场协调和团队沟通能力。" /></label>
          <div class="skill-suggestions"><span>参考技能</span><button v-for="skill in skillSuggestions" :key="skill" type="button" @click="addSkill(skill)"><IconifyIcon icon="lucide:plus" /> {{ skill }}</button></div>
        </ResumeSection>

        <footer class="resume-bottom-actions"><span><IconifyIcon icon="lucide:shield-check" /> 内容仅用于求职服务，保存后可随时修改</span><a-button type="primary" :loading="submitting" @click="saveResume">保存在线简历</a-button></footer>
      </template>
    </main>

    <!-- 只读预览：按简历的排版把当前表单内容读出来，数据就是 form，不额外渲染任何东西 -->
    <a-modal
      v-model:open="previewOpen"
      title="我的在线简历"
      :footer="null"
      width="1000px"
      wrap-class-name="resume-preview-modal"
    >
      <article class="resume-preview">
        <header class="resume-preview-head">
          <span class="resume-preview-avatar">
            <img v-if="personalAvatarUrl" :src="personalAvatarUrl" alt="" />
            <IconifyIcon v-else icon="lucide:user-round" />
          </span>
          <div>
            <h3>{{ form.name || '未填写姓名' }}</h3>
            <p>{{ [form.mobile, form.email, form.employmentStatus].map((item) => item?.trim()).filter(Boolean).join(' · ') || '未填写联系方式' }}</p>
            <p v-if="form.domicile">户籍地：{{ form.domicile }}</p>
          </div>
        </header>

        <section class="resume-preview-block">
          <h4>个人优势</h4>
          <p class="resume-preview-text">{{ form.strengths || '未填写' }}</p>
        </section>

        <section class="resume-preview-block">
          <h4>期望职位</h4>
          <dl class="resume-preview-grid">
            <div><dt>求职类型</dt><dd>{{ jobTypeLabel }}</dd></div>
            <div><dt>期望职位</dt><dd>{{ form.expectedPostName || '未填写' }}</dd></div>
            <div><dt>期望行业</dt><dd>{{ form.industry || '未填写' }}</dd></div>
            <div><dt>薪资要求</dt><dd>{{ salaryLabel }}</dd></div>
          </dl>
          <p v-if="form.expectedCities.length" class="resume-preview-tags">
            <span v-for="city in form.expectedCities" :key="city">{{ city }}</span>
          </p>
        </section>

        <section class="resume-preview-block">
          <h4>工作 / 实习经历</h4>
          <p v-if="!form.experiences.length" class="resume-preview-text">未填写</p>
          <article v-for="item in form.experiences" :key="item.id" class="resume-preview-entry">
            <div class="resume-preview-entry-head">
              <strong>{{ item.position || '未填写职位' }} · {{ item.company || '未填写公司' }}</strong>
              <small>{{ formatPeriod(item.start, item.end) }}</small>
            </div>
            <p v-if="item.description" class="resume-preview-text">{{ item.description }}</p>
            <p v-if="item.achievements" class="resume-preview-text"><b>工作业绩</b>{{ item.achievements }}</p>
          </article>
        </section>

        <section class="resume-preview-block">
          <h4>项目经历</h4>
          <p v-if="!form.projects.length" class="resume-preview-text">未填写</p>
          <article v-for="item in form.projects" :key="item.id" class="resume-preview-entry">
            <div class="resume-preview-entry-head">
              <strong>{{ item.name || '未填写项目名称' }} · {{ item.role || '未填写角色' }}</strong>
              <small>{{ formatPeriod(item.start, item.end) }}</small>
            </div>
            <p v-if="item.description" class="resume-preview-text">{{ item.description }}</p>
            <p v-if="item.achievements" class="resume-preview-text"><b>项目业绩</b>{{ item.achievements }}</p>
          </article>
        </section>

        <section class="resume-preview-block">
          <h4>教育经历</h4>
          <p v-if="!form.educationExperiences.length" class="resume-preview-text">未填写</p>
          <article v-for="item in form.educationExperiences" :key="item.id" class="resume-preview-entry">
            <div class="resume-preview-entry-head">
              <strong>{{ item.degree || '未填写学历' }} · {{ item.school || '未填写学校' }}</strong>
              <small>{{ formatPeriod(item.start, item.end) }}</small>
            </div>
            <p class="resume-preview-text">{{ item.major || '未填写专业' }}<template v-if="item.schoolType"> · {{ item.schoolType }}</template></p>
            <p v-if="item.experience" class="resume-preview-text">{{ item.experience }}</p>
          </article>
        </section>

        <section class="resume-preview-block">
          <h4>专业技能</h4>
          <p class="resume-preview-text">{{ form.skills || '未填写' }}</p>
        </section>
      </article>
    </a-modal>

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

    <!-- 附件管理：原先固定挂在在线简历页右侧，现改为由「简历」下拉的入口按需弹出。
         内容与样式沿用原来那块面板（已上传统计 / 附件列表 / 添加入口）。
         标题行不再放「+」：它与下方的「添加附件简历」入口重复。 -->
    <a-modal
      v-model:open="attachmentsModalOpen"
      title="附件管理"
      :footer="null"
      width="860px"
      wrap-class-name="resume-attachments-modal"
    >
      <div class="attachments-modal-body">
        <div class="attachments-summary">
          <span>附件简历</span>
          <strong>{{ visibleAttachments.length }}/10</strong>
          <small>已上传 {{ uploadedAttachments.length }}/5 · 制作生成 {{ generatedAttachments.length }}/5 · 单个不超过 10 兆</small>
        </div>

        <div v-if="loading" class="attachments-loading"><Skeleton active :paragraph="{ rows: 4 }" /></div>
        <div v-else class="attachments-columns">
          <section class="attachments-column">
            <header><strong>已上传附件</strong><small>{{ uploadedAttachments.length }}/5</small></header>
            <div v-if="!uploadedAttachments.length" class="attachments-empty"><span><IconifyIcon icon="lucide:file-plus-2" /></span><p>还没有上传附件简历</p><a-button type="primary" ghost @click="openUploadModal">上传附件</a-button></div>
            <div v-else class="attachments-list">
            <article v-for="resume in uploadedAttachments" :key="resume.id" class="attachment-item">
            <img v-if="resume.avatarUrl" class="attachment-thumb" :src="attachmentAvatarUrl(resume)" alt="简历头像" />
            <span v-else :class="['attachment-file-icon', `is-${getAttachmentExtension(resume)}`]"><IconifyIcon icon="lucide:file-text" /></span>
            <div class="attachment-copy">
              <strong :title="resume.title">{{ resume.title }}</strong>
              <small :title="resume.fileName || resume.title">{{ resume.fileName || '附件简历' }}</small>
              <small>{{ formatAttachmentDate(resume.createTime) }}</small>
              <small v-if="!hasAnalyzableContent(resume)" class="attachment-warn">未提取到内容，暂不能用于 AI 分析；建议删除后重新上传</small>
            </div>
            <a-dropdown overlay-class-name="resume-attachment-actions" placement="bottomRight" trigger="['click']">
              <a-button class="attachment-preview" type="text" aria-label="附件操作">
                <IconifyIcon icon="lucide:ellipsis-vertical" />
              </a-button>
              <template #overlay>
                <a-menu @click="handleAttachmentMenuClick($event, resume)">
                  <a-menu-item key="preview" :disabled="!resume.fileUrl">在线查看</a-menu-item>
                  <a-menu-item key="delete" danger>删除</a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
            </article>
            </div>
          </section>
          <section class="attachments-column">
            <header><strong>制作生成附件</strong><small>{{ generatedAttachments.length }}/5</small></header>
            <div v-if="!generatedAttachments.length" class="attachments-empty"><span><IconifyIcon icon="lucide:sparkles" /></span><p>还没有制作生成的附件简历</p><a-button type="primary" ghost @click="generatedModalOpen = true">制作附件简历</a-button></div>
            <div v-else class="attachments-list">
              <article v-for="resume in generatedAttachments" :key="resume.id" class="attachment-item">
                <img v-if="resume.avatarUrl" class="attachment-thumb" :src="attachmentAvatarUrl(resume)" alt="简历头像" />
                <span v-else :class="['attachment-file-icon', `is-${getAttachmentExtension(resume)}`]"><IconifyIcon icon="lucide:file-check-2" /></span>
                <div class="attachment-copy"><strong :title="resume.title">{{ resume.title }}</strong><small>{{ resume.fileName || '制作生成附件' }}</small><small>{{ formatAttachmentDate(resume.createTime) }}</small><small v-if="!hasAnalyzableContent(resume)" class="attachment-warn">未提取到内容，暂不能用于 AI 分析；建议删除后重新制作</small></div>
                <a-button class="attachment-preview" type="text" aria-label="删除" @click="handleAttachmentAction('delete', resume)"><IconifyIcon icon="lucide:trash-2" /></a-button>
              </article>
            </div>
          </section>
        </div>

        <button class="attachment-upload-cta" type="button" @click="openUploadModal">
          <span class="attachment-upload-cta-icon"><IconifyIcon icon="lucide:upload-cloud" /></span>
          <span><strong>添加附件简历</strong><small>让招聘方更快了解你</small></span>
          <IconifyIcon class="attachment-upload-cta-arrow" icon="lucide:arrow-up-right" />
        </button>
        <button class="attachment-generate-cta" type="button" @click="generatedModalOpen = true">
          <span class="attachment-upload-cta-icon"><IconifyIcon icon="lucide:sparkles" /></span>
          <span><strong>制作附件简历</strong><small>从在线简历生成可投递的 Word 附件</small></span>
          <IconifyIcon class="attachment-upload-cta-arrow" icon="lucide:arrow-up-right" />
        </button>
      </div>
    </a-modal>

    <a-modal v-model:open="generatedModalOpen" title="制作附件简历" ok-text="生成并保存" cancel-text="取消" :confirm-loading="generatedSubmitting" wrap-class-name="resume-generate-modal" @ok="createGeneratedAttachment">
      <div class="generated-resume-form">
        <p>系统会根据当前在线简历生成一份 Word 附件，并自动放入“制作生成”栏。</p>
        <a-input v-model:value="generatedTitle" maxlength="60" placeholder="附件标题，例如：项目经理简历 2026" />
      </div>
    </a-modal>

    <ResumeAiPanel v-model:open="aiPanelOpen" @open-versions="handleOpenVersionsFromAi" />

    <a-modal
      v-model:open="versionPanelOpen"
      title="简历版本"
      :footer="null"
      width="640px"
      wrap-class-name="resume-version-modal"
    >
      <div class="version-panel">
        <p class="version-panel-help">
          版本是当前在线简历的固化副本，投递时可直接选用。保存版本不会改动你的在线简历，
          之后继续编辑简历也不会影响已经保存的版本。
        </p>

        <div class="version-panel-actions">
          <a-button
            type="primary"
            :loading="versionSaving"
            @click="saveResumeVersion"
          >
            把当前简历保存为新版本
          </a-button>
          <a-button :loading="versionLoading" @click="loadResumeVersions">
            刷新
          </a-button>
        </div>

        <div v-if="versionLoading" class="version-panel-empty">正在加载版本…</div>
        <div v-else-if="!resumeVersions.length" class="version-panel-empty">
          还没有保存过版本。完善在线简历后点击上方按钮即可生成第一个版本。
        </div>
        <ul v-else class="version-list">
          <li v-for="version in resumeVersions" :key="version.id" class="version-item">
            <div class="version-item-main">
              <strong>V{{ version.versionNo }}</strong>
              <span class="version-item-summary">
                {{ version.changeSummary || '未填写修改摘要' }}
              </span>
            </div>
            <div class="version-item-meta">
              <span>{{ formatVersionTime(version.createTime) }}</span>
              <span v-if="version.status !== 0" class="version-item-invalid">已失效</span>
            </div>
          </li>
        </ul>
      </div>
    </a-modal>

    <a-modal v-model:open="experienceModalOpen" :title="experienceIndex >= 0 ? '编辑工作 / 实习经历' : '添加工作 / 实习经历'" width="720px" @ok="saveExperience">
      <div class="modal-fields"><label class="field required"><span>公司名称</span><a-input v-model:value="editingExperience.company" placeholder="例如：北京华品博睿网络技术有限公司" /></label><label class="field"><span>所属行业</span><a-select v-model:value="editingExperience.industry" popup-class-name="resume-select-dropdown" show-search placeholder="选择所属行业"><a-select-option v-for="item in industryOptions" :key="item" :value="item">{{ item }}</a-select-option></a-select></label><label class="field"><span>所属部门 <small>选填</small></span><a-input v-model:value="editingExperience.department" placeholder="例如：产品部" /></label><label class="field required"><span>职位名称</span><a-input v-model:value="editingExperience.position" placeholder="例如：项目经理" /></label><label class="field field-full"><span>在职时间</span><div class="date-range"><MonthPicker v-model="editingExperience.start" placeholder="请选择入职时间" :min-year="now.getFullYear() - 59" :max-year="now.getFullYear()" /><span>至</span><MonthPicker v-model="editingExperience.end" placeholder="至今可留空" :min-year="now.getFullYear() - 59" :max-year="now.getFullYear()" /></div></label><label class="field field-full"><span>工作内容</span><a-textarea v-model:value="editingExperience.description" :maxlength="3000" :rows="4" show-count placeholder="描述主要负责的工作内容、使用的方法和承担的职责。" /></label><label class="field field-full"><span>工作业绩 <small>选填</small></span><a-textarea v-model:value="editingExperience.achievements" :maxlength="1000" :rows="3" show-count placeholder="例如：优化施工流程，项目交付周期缩短 20%。" /></label></div>
    </a-modal>

    <a-modal v-model:open="projectModalOpen" :title="projectIndex >= 0 ? '编辑项目经历' : '添加项目经历'" width="720px" @ok="saveProject">
       <div class="modal-fields"><label class="field required"><span>项目名称</span><a-input v-model:value="editingProject.name" placeholder="例如：管道保温智能巡检项目" /></label><label class="field required"><span>项目角色</span><a-input v-model:value="editingProject.role" placeholder="例如：项目负责人 / 界面设计师" /></label><label class="field"><span>项目链接 <small>选填</small></span><a-input v-model:value="editingProject.link" placeholder="例如：项目官网或作品链接" /></label><label class="field"><span>项目时间</span><div class="date-range"><MonthPicker v-model="editingProject.start" placeholder="请选择开始时间" :min-year="now.getFullYear() - 59" :max-year="now.getFullYear()" /><span>至</span><MonthPicker v-model="editingProject.end" placeholder="至今可留空" :min-year="now.getFullYear() - 59" :max-year="now.getFullYear()" /></div></label><label class="field field-full"><span>项目描述</span><a-textarea v-model:value="editingProject.description" :maxlength="3000" :rows="4" show-count placeholder="描述项目背景、目标和你负责的具体工作。" /></label><label class="field field-full"><span>项目业绩 <small>选填</small></span><a-textarea v-model:value="editingProject.achievements" :maxlength="1000" :rows="3" show-count placeholder="描述最终结果、数据变化或获得的认可。" /></label><label class="modal-checkbox"><a-checkbox v-model:checked="editingProject.highlight" /> 将此项目标记为重点项目</label></div>
    </a-modal>

    <a-modal v-model:open="educationModalOpen" :title="educationIndex >= 0 ? '编辑教育经历' : '添加教育经历'" width="720px" @ok="saveEducation">
       <div class="modal-fields"><label class="field"><span>学历</span><a-select v-model:value="editingEducation.degree" popup-class-name="resume-select-dropdown"><a-select-option v-for="item in degreeOptions" :key="item" :value="item">{{ item }}</a-select-option></a-select></label><label class="field required"><span>学校名称</span><a-input v-model:value="editingEducation.school" placeholder="例如：北京大学" /></label><label class="field required"><span>专业</span><a-input v-model:value="editingEducation.major" placeholder="例如：计算机科学与技术" /></label><label class="field"><span>学校类型</span><a-select v-model:value="editingEducation.schoolType" popup-class-name="resume-select-dropdown"><a-select-option v-for="item in schoolTypeOptions" :key="item" :value="item">{{ item }}</a-select-option></a-select></label><label class="field field-full"><span>时间段</span><div class="date-range"><MonthPicker v-model="editingEducation.start" placeholder="选择入学时间" :min-year="now.getFullYear() - 59" :max-year="now.getFullYear()" /><span>至</span><MonthPicker v-model="editingEducation.end" placeholder="选择毕业时间" :min-year="now.getFullYear() - 59" :max-year="now.getFullYear() + 10" /></div></label><label class="field field-full"><span>在校经历 <small>选填</small></span><a-textarea v-model:value="editingEducation.experience" :maxlength="3000" :rows="4" show-count placeholder="例如：在校担任学生会部长，获得校级奖学金，主修课程包括……" /></label></div>
    </a-modal>
  </div>
</template>

<style scoped>
/* ==========================================================================
   在线简历页 —— 配色只用职位页那一套 token（值取自 index.vue 的 .recruit-page）：
   淡蓝承载结构与信息（图标、编号、选中态、分区眉标、条目标签），
   绿色只承载动作与完成（保存、添加、已完善、完成度）。
   这里原本是「橙+奶油基底」再叠一层「青色覆写」，两层都不是这套色，
   而且 --resume-orange 到后期已经指向青色、名字和值对不上，这次一并收成单层。
   ========================================================================== */
.online-resume-page {
  --job-ink: #1b2a3d;
  --job-muted: #5f7288;
  --job-blue: #2f6fb3;
  --job-blue-soft: #e8f1fb;
  --job-line: #d7e5f4;
  --job-green: #0c9653;
  --job-green-soft: #e6fcf1;
  --job-green-line: #bcebd2;
  /* 由上面派生的两个浅色，不新造色号 */
  --resume-wash: color-mix(in srgb, var(--job-blue-soft) 42%, #fff);
  --resume-placeholder: color-mix(in srgb, var(--job-muted) 58%, #fff);
  /* 「重点项目」标记用淡橙：用户指定的例外，破了上面「蓝承载信息、绿承载动作」的分工
     （同 index.vue 里 --job-salary 的记法）。取值沿用门户里已有的那个橙 #c96632，
     不新造色号。要还原成绿色：删掉下面两行，把 .entry-card-top strong em
     改回 var(--job-green-soft) / var(--job-green)。 */
  --resume-marker: #c96632;
  --resume-marker-soft: #fdeee3;
  display: grid;
  grid-template-columns: 232px minmax(0, 1fr);
  gap: 20px;
  align-items: start;
  color: var(--job-ink);
  font-size: 15px;
}

/* ---------- 侧栏：品牌 / 完成度 / 目录 ---------- */

.resume-index-panel {
  position: sticky;
  /* 76px = 顶栏 60px + 16px 间隙，和本页 .resume-section 的 scroll-margin-top
     以及 company/detail 页的侧栏用同一套基准。原先是 20px，钉住之后整块往上
     挪到了顶栏底下，顶栏不透明且 z-index 40，会把目录顶部盖掉约 40px。 */
  top: 76px;
  display: grid;
  gap: 16px;
  /* 视口不高时目录比可用高度还长，钉住后底部会够不着；
     和职位页 .search-detail-card 一样留出上下余量并允许内部滚动。 */
  max-height: calc(100vh - 96px);
  overflow: auto;
  padding: 18px 14px 14px;
  border: 1px solid var(--job-line);
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 10px 28px rgb(27 42 61 / 6%);
}

.completion-card {
  display: grid;
  padding: 12px 12px 11px;
  border-radius: 12px;
  background: var(--resume-wash);
}

.completion-card-top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  color: var(--job-muted);
  font-size: 13px;
}

.completion-card-top strong {
  color: var(--job-ink);
  font-size: 20px;
  font-variant-numeric: tabular-nums;
}

/* 七段对应七个模块：哪段是空的，就是哪块还没填。
   比一条抽象进度条多给一条信息 —— 完成度落在哪几块上。 */
.completion-segments {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 4px;
  margin: 10px 0 8px;
}

.completion-segments i {
  display: block;
  height: 6px;
  border-radius: 3px;
  background: var(--job-line);
  transition: background 200ms ease;
}

.completion-segments i.done {
  background: var(--job-green);
}

.completion-segments i.current {
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--job-blue) 38%, transparent);
}

.completion-card small {
  color: var(--job-muted);
  font-size: 12px;
}

.resume-index-nav {
  display: grid;
  gap: 2px;
}

.resume-index-nav > p {
  margin: 0 8px 6px;
  color: var(--job-muted);
  /* 和目录项标题（.nav-copy strong）同为 14px：字号打平，靠颜色和字重压成标签 */
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.06em;
}

.resume-index-nav button {
  display: flex;
  width: 100%;
  min-height: 46px;
  align-items: center;
  gap: 9px;
  padding: 6px 8px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: inherit;
  cursor: pointer;
  text-align: left;
  transition: background 180ms ease;
}

/* 悬停与选中必须能区分：同色的话分不清「鼠标划过」和「当前所在模块」 */
.resume-index-nav button:hover {
  background: var(--resume-wash);
}

.resume-index-nav button.active {
  background: var(--job-blue-soft);
  box-shadow: inset 3px 0 0 var(--job-blue);
}

/* 编号方块：目录里的 01…07 与各分区的「第 N 部分」是同一套序号，做成同一个记号 */
.nav-number {
  display: grid;
  width: 26px;
  height: 26px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 8px;
  background: #fff;
  box-shadow: inset 0 0 0 1px var(--job-line);
  color: var(--job-muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.resume-index-nav button.active .nav-number {
  background: var(--job-blue);
  box-shadow: none;
  color: #fff;
}

.nav-copy {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.nav-copy strong {
  font-size: 14px;
  font-weight: 600;
}

.nav-copy small {
  color: var(--job-muted);
  font-size: 12px;
}

.nav-done {
  margin-left: auto;
  color: var(--job-green);
  font-size: 14px;
}

.index-tip {
  display: flex;
  gap: 8px;
  padding: 12px 8px 0;
  border-top: 1px solid var(--job-line);
  color: var(--job-muted);
}

.index-tip :deep(svg) {
  flex: 0 0 auto;
  color: var(--job-blue);
  font-size: 15px;
}

.index-tip p {
  margin: 0;
  font-size: 12px;
  line-height: 1.65;
}

/* ---------- 主区：页头 / 提示条 ---------- */

.resume-editor {
  display: grid;
  gap: 16px;
  min-width: 0;
}

/* ---------- 工具条 ---------- */

.resume-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 46px;
  padding: 0 16px;
  border: 1px solid var(--job-line);
  border-radius: 12px;
  background: #fff;
}

.resume-toolbar strong {
  color: var(--job-ink);
  font-size: 15px;
  font-weight: 650;
}

.resume-toolbar-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 三个都是次要动作，用蓝色链接式按钮；绿色留给页尾那个主动作「保存在线简历」 */
.resume-toolbar-actions button {
  padding: 5px 8px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--job-blue);
  cursor: pointer;
  font-size: 14px;
  transition: background 160ms ease, color 160ms ease;
}

.resume-toolbar-actions button:hover {
  background: var(--job-blue-soft);
}

.resume-toolbar-actions button:focus-visible {
  outline: 2px solid var(--job-blue);
  outline-offset: 2px;
}

.resume-toolbar-divider {
  width: 1px;
  height: 14px;
  background: var(--job-line);
}

/* ---------- 只读预览 ---------- */

.resume-preview {
  display: grid;
  gap: 18px;
  color: var(--job-ink);
  font-size: 14px;
}

.resume-preview-head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--job-line);
}

.resume-preview-avatar {
  display: grid;
  width: 56px;
  height: 56px;
  flex: 0 0 auto;
  place-items: center;
  overflow: hidden;
  border-radius: 50%;
  background: var(--job-blue-soft);
  color: var(--job-blue);
  font-size: 24px;
}

.resume-preview-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.resume-preview-head h3 {
  margin: 0;
  font-size: 20px;
  font-weight: 650;
}

.resume-preview-head p {
  margin: 4px 0 0;
  color: var(--job-muted);
  font-size: 13px;
}

.resume-preview-block h4 {
  margin: 0 0 8px;
  padding-left: 9px;
  border-left: 3px solid var(--job-blue);
  color: var(--job-ink);
  font-size: 15px;
  font-weight: 650;
}

.resume-preview-text {
  margin: 0;
  color: var(--job-ink);
  line-height: 1.85;
  white-space: pre-line;
}

.resume-preview-text + .resume-preview-text {
  margin-top: 6px;
}

.resume-preview-text b {
  margin-right: 8px;
  color: var(--job-green);
  font-weight: 600;
}

.resume-preview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px 18px;
  margin: 0;
}

.resume-preview-grid > div {
  display: flex;
  gap: 8px;
}

.resume-preview-grid dt {
  flex: 0 0 auto;
  color: var(--job-muted);
}

.resume-preview-grid dd {
  margin: 0;
  color: var(--job-ink);
}

.resume-preview-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 10px 0 0;
}

.resume-preview-tags span {
  padding: 3px 9px;
  border-radius: 6px;
  background: var(--job-blue-soft);
  color: var(--job-blue);
  font-size: 12px;
}

.resume-preview-entry + .resume-preview-entry {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px dashed var(--job-line);
}

.resume-preview-entry-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
}

.resume-preview-entry-head strong {
  color: var(--job-ink);
  font-size: 14px;
  font-weight: 600;
}

.resume-preview-entry-head small {
  flex: 0 0 auto;
  color: var(--job-muted);
  font-size: 12px;
}

/* 保存是页面唯一的主动作，给绿色实心 */
.resume-editor :deep(.ant-btn-primary) {
  border-color: var(--job-green);
  background: var(--job-green);
  color: #fff !important;
  box-shadow: 0 6px 16px color-mix(in srgb, var(--job-green) 22%, transparent);
  font-weight: 600;
}

.resume-editor :deep(.ant-btn-primary:hover) {
  border-color: color-mix(in srgb, var(--job-green) 84%, #000);
  background: color-mix(in srgb, var(--job-green) 84%, #000);
  color: #fff !important;
}

.resume-loading {
  display: grid;
  gap: 16px;
  padding: 24px;
  border: 1px solid var(--job-line);
  border-radius: 14px;
  background: #fff;
}

/* ---------- 分区内的字段 ----------
   分区的开合外壳（标题行、状态标签、展开区）在 components/resume-section.vue 里，
   这里只负责插槽内的字段本身。 */

.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px 18px;
}

.field {
  display: grid;
  /* align-content 的默认值是 stretch：同一行里有个高的兄弟（比如头像那块 86px）时，
     本字段的输入框会跟着被拉高 —— 实测同一列里出现过 54/52/46/40 四种高度。
     钉成 start，控件一律回到 40px 基准。 */
  align-content: start;
  min-width: 0;
  gap: 7px;
  color: var(--job-ink);
  font-size: 14px;
}

.field > span {
  color: var(--job-ink);
  font-size: 14px;
  font-weight: 600;
}

/* 必填记号用蓝色而不是红色：这套配色里没有红（--job-salary 的红色是薪资专用例外），
   而必填是字段本身的属性、不是动作，所以不给绿色。 */
.field.required > span::before {
  margin-right: 4px;
  color: var(--job-blue);
  content: '*';
}

.field > span small {
  margin-left: 4px;
  color: var(--job-muted);
  font-size: 12px;
  font-weight: 400;
}

.field-full {
  grid-column: 1 / -1;
}

.resume-editor :deep(.ant-input),
.resume-editor :deep(.ant-select-selector) {
  min-height: 40px;
  border-color: var(--job-line) !important;
  border-radius: 9px !important;
  background: #fff !important;
  color: var(--job-ink);
  font-size: 15px;
  box-shadow: none !important;
}

.resume-editor :deep(.ant-input:hover),
.resume-editor :deep(.ant-select-selector:hover) {
  border-color: color-mix(in srgb, var(--job-blue) 55%, #fff) !important;
}

/* 上一条的 box-shadow:none 会把 AntD 自带的聚焦反馈一起打掉，
   这里补一个可见的聚焦光圈，键盘操作时能看清焦点在哪 */
.resume-editor :deep(.ant-input:focus),
.resume-editor :deep(.ant-select-focused .ant-select-selector) {
  border-color: var(--job-blue) !important;
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--job-blue) 14%, transparent) !important;
}

.resume-editor :deep(textarea.ant-input) {
  min-height: 100px;
  padding: 10px 12px;
  line-height: 1.7;
}

.resume-editor :deep(.ant-select) {
  width: 100%;
}

/* AntD 给选中项的行高是按它默认的 32px 控件算的（30px），本页把下拉抬到了 40px，
   结果文字在框里偏上（实测上方 6px、下方 14px）。这里把行高改成 36px 让文字居中。
   期望城市那个内嵌 cascader 只有 32px 高，得排除掉，否则文字会被顶出框。 */
.resume-editor :deep(.ant-select-single:not(.expected-city-cascader) .ant-select-selector .ant-select-selection-item),
.resume-editor :deep(.ant-select-single:not(.expected-city-cascader) .ant-select-selector .ant-select-selection-placeholder) {
  line-height: 36px;
}

.resume-editor :deep(.ant-select-selection-placeholder),
.resume-editor :deep(.ant-input::placeholder) {
  color: var(--resume-placeholder);
}

/* 头像挂在分区标题行最右（与「已完善」同排），不占字段区一列 ——
   之前单独成列时圆圈悬在字段右侧、和上下标签都对不齐，很突兀；
   挪走后字段区恢复整宽，七个分区的字段宽度也终于一致了。 */
.resume-personal-avatar {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* 头像本身是个上传按钮：整块可点，悬停浮出相机图标。
   圆形与顶栏那个 28px 头像圈保持同一种形制。 */
.resume-avatar-trigger {
  position: relative;
  display: block;
  width: 56px;
  height: 56px;
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--job-line);
  border-radius: 50%;
  background: #fff;
  cursor: pointer;
}

.resume-avatar-trigger:focus-visible {
  outline: 2px solid var(--job-blue);
  outline-offset: 2px;
}

.resume-personal-avatar img,
.resume-personal-avatar-placeholder {
  display: grid;
  width: 100%;
  height: 100%;
  place-items: center;
  background: var(--job-blue-soft);
  color: var(--job-blue);
  object-fit: cover;
  font-size: 24px;
}

.resume-avatar-mask {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgb(27 42 61 / 52%);
  color: #fff;
  font-size: 18px;
  opacity: 0;
  transition: opacity 180ms ease;
}

.resume-avatar-trigger:hover .resume-avatar-mask,
.resume-avatar-trigger:focus-visible .resume-avatar-mask {
  opacity: 1;
}

.resume-avatar-trigger[aria-busy='true'] .resume-avatar-mask {
  opacity: 1;
}

.resume-personal-avatar small {
  color: var(--job-muted);
  font-size: 12px;
  line-height: 1.45;
}

.resume-avatar-remove {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 7px 2px 5px;
  border: 1px solid var(--job-line);
  border-radius: 6px;
  background: #fff;
  color: var(--job-muted);
  cursor: pointer;
  font-size: 12px;
  transition: border-color 160ms ease, color 160ms ease;
}

.resume-avatar-remove:hover {
  border-color: var(--job-blue);
  color: var(--job-blue);
}

/* ---------- 户籍地 / 期望城市 ---------- */

.resume-editor :deep(.domicile-area-cascader .ant-select-selector) {
  min-height: 40px;
  border-color: var(--job-line) !important;
  border-radius: 9px !important;
  background: #fff !important;
  color: var(--job-ink);
}

.resume-editor :deep(.domicile-area-cascader:hover .ant-select-selector),
.resume-editor :deep(.domicile-area-cascader.ant-select-focused .ant-select-selector) {
  border-color: var(--job-blue) !important;
}

.resume-editor :deep(.domicile-area-cascader .ant-select-selection-item) {
  color: var(--job-ink);
}

.resume-editor :deep(.domicile-area-cascader .ant-select-selection-placeholder) {
  color: var(--resume-placeholder);
}

.expected-cities-picker {
  display: flex;
  min-height: 40px;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  padding: 4px 8px;
  border: 1px solid var(--job-line);
  border-radius: 9px;
  background: #fff;
  transition: border-color 180ms ease, box-shadow 180ms ease;
}

.expected-cities-picker:focus-within {
  border-color: var(--job-blue);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--job-blue) 14%, transparent);
}

.expected-city-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.expected-city-tag {
  display: inline-flex;
  max-width: 100%;
  align-items: center;
  gap: 4px;
  padding: 3px 7px 3px 9px;
  border: 1px solid var(--job-line);
  border-radius: 6px;
  background: var(--job-blue-soft);
  color: var(--job-blue);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  line-height: 20px;
  transition: border-color 160ms ease;
}

.expected-city-tag > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.expected-city-tag :deep(svg) {
  flex: 0 0 auto;
  color: color-mix(in srgb, var(--job-blue) 65%, #fff);
  font-size: 12px;
}

.expected-city-tag:hover {
  border-color: var(--job-blue);
}

.expected-city-tag:hover :deep(svg) {
  color: var(--job-blue);
}

.resume-editor :deep(.expected-city-cascader) {
  width: auto;
  min-width: 190px;
  flex: 1 1 190px;
}

.resume-editor :deep(.expected-city-cascader .ant-select-selector) {
  min-height: 30px;
  padding: 0 !important;
  border: 0 !important;
  border-radius: 0 !important;
  background: transparent !important;
  box-shadow: none !important;
}

.resume-editor :deep(.expected-city-cascader .ant-select-selection-placeholder) {
  color: var(--resume-placeholder);
}

.resume-editor :deep(.expected-city-cascader .ant-select-selection-search-input) {
  color: var(--job-ink);
}

.expectation-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

/* ---------- 经历类条目 ---------- */

.add-button {
  height: 32px;
  padding: 0 8px;
  border-radius: 8px;
  background: transparent;
  /* 灰色次要动作，悬停才亮成淡蓝 —— 和本页其它可点元素（条目操作按钮、目录项、
     分区标题上的「修改」）同一套反馈，绿只留给页尾的主动作「保存在线简历」 */
  color: var(--job-muted);
  font-size: 13px;
  font-weight: 600;
}

.add-button:hover {
  background: var(--job-blue-soft);
  color: var(--job-blue);
}

.add-button :deep(svg) {
  margin-right: 4px;
}

.empty-module {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 24px 20px;
  border: 1px dashed var(--job-line);
  border-radius: 12px;
  background: var(--resume-wash);
  color: var(--job-muted);
  text-align: center;
}

.empty-icon {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 12px;
  background: #fff;
  box-shadow: inset 0 0 0 1px var(--job-line);
  color: var(--job-blue);
  font-size: 18px;
}

.empty-module strong {
  color: var(--job-ink);
  font-size: 15px;
}

.empty-module p {
  margin: 0 0 4px;
  color: var(--job-muted);
  font-size: 13px;
}

/* 空态里的「添加第一段经历」是引导动作，用绿色描边而不是实心，
   免得和页头/页尾的「保存」抢同一个视觉层级 */
.resume-editor :deep(.ant-btn-background-ghost) {
  border-color: var(--job-green-line);
  background: #fff;
  color: var(--job-green) !important;
  font-weight: 600;
}

.resume-editor :deep(.ant-btn-background-ghost:hover) {
  border-color: var(--job-green);
  background: var(--job-green-soft);
  color: var(--job-green) !important;
}

.entry-list {
  display: grid;
  gap: 10px;
}

.entry-card {
  padding: 15px 16px;
  border: 1px solid var(--job-line);
  border-radius: 12px;
  background: #fff;
  color: var(--job-ink);
  transition: border-color 180ms ease, box-shadow 180ms ease;
}

.entry-card:hover {
  border-color: color-mix(in srgb, var(--job-blue) 40%, #fff);
  box-shadow: 0 6px 16px rgb(27 42 61 / 6%);
}

.entry-card-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.entry-card-top > div:first-child {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.entry-card-top strong {
  color: var(--job-ink);
  font-size: 16px;
  font-weight: 650;
}

.entry-card-top strong em {
  margin-left: 5px;
  padding: 2px 6px;
  border-radius: 5px;
  background: var(--resume-marker-soft);
  color: var(--resume-marker);
  font-size: 12px;
  font-style: normal;
  font-weight: 600;
}

.entry-card-top span {
  color: var(--job-muted);
  font-size: 14px;
}

.entry-card-top span i {
  font-style: normal;
}

.entry-card-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.entry-card-actions small {
  margin-right: 4px;
  color: var(--job-muted);
  font-size: 13px;
}

.entry-card-actions button {
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--job-muted);
  cursor: pointer;
  transition: background 160ms ease, color 160ms ease;
}

.entry-card-actions button:hover {
  background: var(--job-blue-soft);
  color: var(--job-blue);
}

.entry-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.entry-meta span {
  padding: 3px 8px;
  border-radius: 6px;
  background: var(--job-blue-soft);
  color: var(--job-blue);
  font-size: 12px;
}

.entry-description,
.entry-achievement {
  margin: 12px 0 0;
  color: var(--job-ink);
  font-size: 14px;
  line-height: 1.75;
  white-space: pre-line;
}

.entry-achievement {
  padding-top: 10px;
  border-top: 1px solid var(--job-line);
}

.entry-achievement b {
  margin-right: 8px;
  color: var(--job-green);
  font-weight: 600;
}

.entry-link {
  display: block;
  margin-top: 9px;
  overflow: hidden;
  color: var(--job-blue);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.skill-suggestions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.skill-suggestions > span {
  color: var(--job-muted);
  font-size: 13px;
}

/* 参考技能是「一键加进正文」的动作，做成胶囊并在悬停时转绿 */
.skill-suggestions button {
  padding: 5px 11px;
  border: 1px solid var(--job-line);
  border-radius: 999px;
  background: #fff;
  color: var(--job-ink);
  cursor: pointer;
  font-size: 13px;
  transition: border-color 160ms ease, background 160ms ease, color 160ms ease;
}

.skill-suggestions button:hover {
  border-color: var(--job-green);
  background: var(--job-green-soft);
  color: var(--job-green);
}

.skill-suggestions button :deep(svg) {
  margin-right: 2px;
  font-size: 12px;
}

.resume-bottom-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 4px 4px 14px;
  color: var(--job-muted);
  font-size: 13px;
}

.resume-bottom-actions > span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.resume-bottom-actions :deep(svg) {
  color: var(--job-green);
  font-size: 14px;
}

/* ==========================================================================
   以下内容被 teleport 到 body（弹窗、下拉、气泡），DOM 上不再是本页的后代，
   拿不到 .online-resume-page 上那份 token。var() 解析失败会让整条声明作废
   （不是回退到默认色），所以必须在它们各自的根元素上再声明一份。
   ========================================================================== */

:global(.resume-attachments-modal .ant-modal-content),
:global(.resume-upload-modal .ant-modal-content),
:global(.resume-version-modal .ant-modal-content),
:global(.resume-generate-modal .ant-modal-content),
:global(.resume-preview-modal .ant-modal-content),
:global(.resume-select-dropdown),
:global(.recruit-filter-dropdown),
:global(.resume-attachment-actions) {
  --job-ink: #1b2a3d;
  --job-muted: #5f7288;
  --job-blue: #2f6fb3;
  --job-blue-soft: #e8f1fb;
  --job-line: #d7e5f4;
  --job-green: #0c9653;
  --job-green-soft: #e6fcf1;
  --job-green-line: #bcebd2;
  --resume-wash: color-mix(in srgb, var(--job-blue-soft) 42%, #fff);
  --resume-placeholder: color-mix(in srgb, var(--job-muted) 58%, #fff);
}

/* ---------- 附件管理弹窗 ---------- */

.attachments-modal-body {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.attachments-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.attachments-column {
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--job-line);
  border-radius: 12px;
  background: #fff;
}

.attachments-column > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  color: var(--job-ink);
  font-size: 14px;
  font-weight: 600;
}

.attachments-column > header small {
  color: var(--job-muted);
  font-size: 12px;
  font-weight: 400;
}

.attachments-summary {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: 4px 8px;
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--resume-wash);
}

.attachments-summary span {
  color: var(--job-ink);
  font-size: 14px;
  font-weight: 600;
}

.attachments-summary strong {
  color: var(--job-blue);
  font-size: 22px;
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.attachments-summary small {
  grid-column: 1 / -1;
  color: var(--job-muted);
  font-size: 12px;
  line-height: 1.6;
}

.attachments-loading {
  display: grid;
  gap: 14px;
}

.attachments-empty {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 18px 8px 20px;
  border: 1px dashed var(--job-line);
  border-radius: 12px;
  background: var(--resume-wash);
  text-align: center;
}

.attachments-empty > span {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 12px;
  background: #fff;
  box-shadow: inset 0 0 0 1px var(--job-line);
  color: var(--job-blue);
  font-size: 18px;
}

.attachments-empty p {
  margin: 0 0 4px;
  color: var(--job-muted);
  font-size: 13px;
  line-height: 1.6;
}

.attachments-empty :deep(.ant-btn) {
  height: 32px;
  padding-inline: 11px;
  font-size: 13px;
}

.attachments-list {
  display: grid;
  gap: 10px;
}

.attachment-item {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) 24px;
  align-items: center;
  gap: 9px;
  padding: 10px 7px;
  border-bottom: 1px solid var(--job-line);
}

.attachment-item:last-child {
  border-bottom: 0;
}

.attachment-thumb {
  width: 36px;
  height: 42px;
  border-radius: 8px;
  background: var(--job-blue-soft);
  object-fit: cover;
}

.attachment-file-icon {
  display: grid;
  width: 36px;
  height: 42px;
  place-items: center;
  border-radius: 8px;
  background: var(--job-blue-soft);
  color: var(--job-blue);
  font-size: 18px;
}

.attachment-copy {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.attachment-copy strong,
.attachment-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-copy strong {
  color: var(--job-ink);
  font-size: 14px;
  font-weight: 600;
}

.attachment-copy small {
  color: var(--job-muted);
  font-size: 12px;
}

.attachment-copy .attachment-warn {
  color: var(--job-blue);
  line-height: 1.45;
  white-space: normal;
}

.attachment-preview {
  display: grid;
  width: 24px;
  height: 28px;
  place-items: center;
  padding: 0;
  color: var(--job-muted);
}

.attachment-preview:hover {
  background: var(--job-blue-soft);
  color: var(--job-blue);
}

/* 两个附件入口按「推荐程度」分层：从在线简历生成是主推路径，给绿色；
   上传已有文件是备选路径，给中性的淡蓝。 */
.attachment-upload-cta,
.attachment-generate-cta {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 13px 12px;
  border-radius: 12px;
  color: inherit;
  cursor: pointer;
  text-align: left;
  transition: border-color 180ms ease, box-shadow 180ms ease, transform 180ms ease;
}

.attachment-upload-cta {
  border: 1px solid var(--job-line);
  background: var(--resume-wash);
}

.attachment-upload-cta:hover {
  border-color: color-mix(in srgb, var(--job-blue) 45%, #fff);
  box-shadow: 0 8px 18px rgb(27 42 61 / 7%);
  transform: translateY(-1px);
}

.attachment-generate-cta {
  border: 1px solid var(--job-green-line);
  background: var(--job-green-soft);
}

.attachment-generate-cta:hover {
  border-color: var(--job-green);
  box-shadow: 0 8px 18px color-mix(in srgb, var(--job-green) 14%, transparent);
  transform: translateY(-1px);
}

.attachment-upload-cta-icon {
  display: grid;
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 10px;
  background: #fff;
  box-shadow: inset 0 0 0 1px var(--job-line);
  color: var(--job-blue);
  font-size: 15px;
}

.attachment-generate-cta .attachment-upload-cta-icon {
  background: #fff;
  box-shadow: inset 0 0 0 1px var(--job-green-line);
  color: var(--job-green);
}

.attachment-upload-cta > span:nth-child(2),
.attachment-generate-cta > span:nth-child(2) {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.attachment-upload-cta strong,
.attachment-generate-cta strong {
  color: var(--job-ink);
  font-size: 15px;
  font-weight: 600;
}

.attachment-upload-cta small,
.attachment-generate-cta small {
  color: var(--job-muted);
  font-size: 12px;
}

.attachment-upload-cta-arrow {
  margin-left: auto;
  color: color-mix(in srgb, var(--job-blue) 55%, #fff);
  font-size: 14px;
}

.attachment-generate-cta .attachment-upload-cta-arrow {
  color: color-mix(in srgb, var(--job-green) 55%, #fff);
}

/* ---------- 简历上传弹窗 ---------- */

.attachment-upload-form {
  display: grid;
  gap: 16px;
  padding: 4px 0 10px;
}

.attachment-upload-help {
  margin: 0;
  padding: 10px 12px;
  border: 1px solid var(--job-line);
  border-radius: 10px;
  background: var(--resume-wash);
  color: var(--job-ink);
  font-size: 13px;
  line-height: 1.6;
}

.attachment-file-picker {
  display: grid;
  min-height: 116px;
  justify-items: center;
  align-content: center;
  gap: 5px;
  padding: 15px;
  border: 1px dashed var(--job-green-line);
  border-radius: 12px;
  background: var(--job-green-soft);
  color: var(--job-green);
  cursor: pointer;
  text-align: center;
  transition: border-color 180ms ease, background 180ms ease;
}

.attachment-file-picker:hover,
.attachment-file-picker.has-file {
  border-color: var(--job-green);
  background: color-mix(in srgb, var(--job-green-soft) 78%, #fff);
}

.attachment-file-picker input {
  display: none;
}

.attachment-file-picker > svg {
  font-size: 25px;
}

.attachment-file-picker strong {
  max-width: 90%;
  overflow: hidden;
  color: var(--job-ink);
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-file-picker small {
  color: var(--job-muted);
  font-size: 12px;
}

.modal-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px 18px;
  padding: 8px 0 16px;
}

.modal-fields .field-full,
.modal-checkbox {
  grid-column: 1 / -1;
}

.date-range {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 9px;
}

.date-range > span {
  color: var(--job-muted);
  font-size: 13px;
}

.modal-checkbox {
  color: var(--job-ink);
  font-size: 14px;
}

/* 「制作附件简历」弹窗的内容排版；token 由上面 .ant-modal-content 那组声明 */
.generated-resume-form {
  display: grid;
  gap: 14px;
  padding: 8px 0;
}

.generated-resume-form p {
  margin: 0;
  color: var(--job-muted);
  font-size: 13px;
  line-height: 1.7;
}

/* ---------- 简历版本弹窗 ---------- */

.version-panel {
  display: grid;
  gap: 16px;
}

.version-panel-help {
  margin: 0;
  padding: 12px 14px;
  border: 1px solid var(--job-line);
  border-radius: 10px;
  background: var(--resume-wash);
  color: var(--job-muted);
  font-size: 13px;
  line-height: 1.7;
}

.version-panel-actions {
  display: flex;
  gap: 10px;
}

.version-panel-empty {
  padding: 26px 12px;
  border: 1px dashed var(--job-line);
  border-radius: 12px;
  background: var(--resume-wash);
  color: var(--job-muted);
  font-size: 13px;
  text-align: center;
}

.version-list {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.version-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 12px 14px;
  border: 1px solid var(--job-line);
  border-radius: 12px;
  background: #fff;
}

.version-item-main {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.version-item-main strong {
  color: var(--job-blue);
  font-size: 15px;
}

.version-item-summary {
  overflow: hidden;
  color: var(--job-muted);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.version-item-meta {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 10px;
  color: var(--job-muted);
  font-size: 12px;
}

/* 失效是中性状态、不是警告，所以不用琥珀色，走灰底灰字 */
.version-item-invalid {
  padding: 1px 7px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--job-muted) 12%, #fff);
  color: var(--job-muted);
}

/* ---------- 弹窗外壳 ----------
   这些弹窗连内容带外壳都被 teleport 到 body，DOM 上不再是 .online-resume-page 的后代，
   .resume-editor 上的 :deep() 规则够不到它们，所以外壳要在这里用 :global 重写一遍。
   四个弹窗共用同一套观感，避免其中一个走 AntD 默认样式、看起来像别的产品。 */

:global(.resume-attachments-modal .ant-modal-content),
:global(.resume-upload-modal .ant-modal-content),
:global(.resume-version-modal .ant-modal-content),
:global(.resume-generate-modal .ant-modal-content),
:global(.resume-preview-modal .ant-modal-content) {
  overflow: hidden;
  border: 1px solid var(--job-line);
  border-radius: 16px;
  background: #fff !important;
  box-shadow: 0 20px 56px rgb(27 42 61 / 18%);
}

:global(.resume-attachments-modal .ant-modal-header),
:global(.resume-upload-modal .ant-modal-header),
:global(.resume-version-modal .ant-modal-header),
:global(.resume-generate-modal .ant-modal-header),
:global(.resume-preview-modal .ant-modal-header) {
  margin-bottom: 0;
  padding: 20px 24px 14px;
  border-bottom: 1px solid var(--job-line);
  background: #fff !important;
}

:global(.resume-attachments-modal .ant-modal-title),
:global(.resume-upload-modal .ant-modal-title),
:global(.resume-version-modal .ant-modal-title),
:global(.resume-generate-modal .ant-modal-title),
:global(.resume-preview-modal .ant-modal-title) {
  color: var(--job-ink) !important;
  font-size: 20px;
  font-weight: 650;
}

:global(.resume-attachments-modal .ant-modal-close),
:global(.resume-upload-modal .ant-modal-close),
:global(.resume-version-modal .ant-modal-close),
:global(.resume-generate-modal .ant-modal-close),
:global(.resume-preview-modal .ant-modal-close) {
  color: var(--job-muted) !important;
}

:global(.resume-attachments-modal .ant-modal-close:hover),
:global(.resume-upload-modal .ant-modal-close:hover),
:global(.resume-version-modal .ant-modal-close:hover),
:global(.resume-generate-modal .ant-modal-close:hover),
:global(.resume-preview-modal .ant-modal-close:hover) {
  background: var(--job-blue-soft) !important;
  color: var(--job-blue) !important;
}

:global(.resume-attachments-modal .ant-modal-body),
:global(.resume-upload-modal .ant-modal-body),
:global(.resume-version-modal .ant-modal-body),
:global(.resume-generate-modal .ant-modal-body),
:global(.resume-preview-modal .ant-modal-body) {
  padding: 18px 24px 24px;
  background: #fff !important;
}

/* 简历全文比一屏还长（实测内容高 1880px），限高 + 弹窗内部滚动，
   标题栏始终留在视野里，不用整页跟着滚。 */
:global(.resume-preview-modal .ant-modal-body) {
  /* 220 = AntD 弹窗距顶 100 + 距底 24 + 本弹窗标题栏约 86，留够就不必再滚整页 */
  max-height: calc(100vh - 220px);
  overflow-y: auto;
  overscroll-behavior: contain;
}

:global(.resume-upload-modal .ant-modal-body) {
  padding-bottom: 8px;
}

:global(.resume-upload-modal .ant-modal-footer),
:global(.resume-version-modal .ant-modal-footer),
:global(.resume-generate-modal .ant-modal-footer) {
  margin-top: 0;
  padding: 14px 24px 20px;
  border-top: 1px solid var(--job-line);
  background: var(--resume-wash) !important;
}

/* AntD 按钮默认主色是 #1677ff，不在这套配色里，弹窗里的按钮逐个改过来 */
:global(.resume-upload-modal .ant-modal-footer .ant-btn-default),
:global(.resume-version-modal .ant-btn-default),
:global(.resume-generate-modal .ant-modal-footer .ant-btn-default) {
  border-color: var(--job-line) !important;
  background: #fff !important;
  color: var(--job-ink) !important;
}

:global(.resume-upload-modal .ant-modal-footer .ant-btn-default:hover),
:global(.resume-version-modal .ant-btn-default:hover),
:global(.resume-generate-modal .ant-modal-footer .ant-btn-default:hover) {
  border-color: var(--job-blue) !important;
  background: var(--job-blue-soft) !important;
  color: var(--job-blue) !important;
}

:global(.resume-upload-modal .ant-modal-footer .ant-btn-primary),
:global(.resume-version-modal .ant-btn-primary),
:global(.resume-generate-modal .ant-modal-footer .ant-btn-primary) {
  border-color: var(--job-green) !important;
  background: var(--job-green) !important;
  color: #fff !important;
  font-weight: 600;
  box-shadow: 0 6px 16px color-mix(in srgb, var(--job-green) 22%, transparent);
}

:global(.resume-upload-modal .ant-modal-footer .ant-btn-primary:hover),
:global(.resume-version-modal .ant-btn-primary:hover),
:global(.resume-generate-modal .ant-modal-footer .ant-btn-primary:hover) {
  border-color: color-mix(in srgb, var(--job-green) 84%, #000) !important;
  background: color-mix(in srgb, var(--job-green) 84%, #000) !important;
  color: #fff !important;
}

/* 附件管理空态里的引导按钮，跟页内空态用同一档绿色描边 */
:global(.resume-attachments-modal .ant-btn-background-ghost) {
  border-color: var(--job-green-line) !important;
  background: #fff !important;
  color: var(--job-green) !important;
  font-weight: 600;
}

:global(.resume-attachments-modal .ant-btn-background-ghost:hover) {
  border-color: var(--job-green) !important;
  background: var(--job-green-soft) !important;
  color: var(--job-green) !important;
}

:global(.resume-upload-modal .ant-btn),
:global(.resume-upload-modal .ant-input),
:global(.resume-upload-modal .ant-select-selector),
:global(.resume-upload-modal .ant-checkbox-wrapper) {
  font-size: 15px !important;
}

:global(.resume-upload-modal .attachment-upload-form .field > span) {
  color: var(--job-ink) !important;
}

:global(.resume-upload-modal .attachment-upload-form .field > span small) {
  color: var(--job-muted) !important;
}

:global(.resume-upload-modal .attachment-upload-form .ant-input) {
  border-color: var(--job-line) !important;
  border-radius: 9px !important;
  background: #fff !important;
  color: var(--job-ink) !important;
  box-shadow: none !important;
}

:global(.resume-upload-modal .attachment-upload-form .ant-input:hover),
:global(.resume-upload-modal .attachment-upload-form .ant-input:focus) {
  border-color: var(--job-blue) !important;
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--job-blue) 14%, transparent) !important;
}

:global(.resume-upload-modal .attachment-upload-form .ant-input::placeholder) {
  color: var(--resume-placeholder) !important;
}

:global(.resume-upload-modal .attachment-file-picker) {
  border-color: var(--job-green-line) !important;
  background: var(--job-green-soft) !important;
  color: var(--job-green) !important;
}

:global(.resume-upload-modal .attachment-file-picker:hover),
:global(.resume-upload-modal .attachment-file-picker.has-file) {
  border-color: var(--job-green) !important;
  background: color-mix(in srgb, var(--job-green-soft) 78%, #fff) !important;
}

:global(.resume-upload-modal .attachment-file-picker strong) {
  color: var(--job-ink) !important;
}

:global(.resume-upload-modal .attachment-file-picker small) {
  color: var(--job-muted) !important;
}

/* ---------- 下拉与浮层（同样是 teleport 到 body 的） ---------- */

:global(.resume-select-dropdown.ant-select-dropdown),
:global(.resume-select-dropdown .ant-select-dropdown) {
  padding: 6px !important;
  border: 1px solid var(--job-line) !important;
  border-radius: 12px !important;
  background: #fff !important;
  box-shadow: 0 14px 32px rgb(27 42 61 / 14%) !important;
}

:global(.resume-select-dropdown .ant-select-item) {
  min-height: 36px;
  padding: 8px 10px;
  border-radius: 8px;
  background: transparent !important;
  color: var(--job-ink) !important;
  font-size: 15px !important;
  line-height: 20px;
}

:global(.resume-select-dropdown .ant-select-item-option-active) {
  background: var(--job-blue-soft) !important;
  color: var(--job-blue) !important;
}

:global(.resume-select-dropdown .ant-select-item-option-selected) {
  background: var(--job-blue-soft) !important;
  color: var(--job-blue) !important;
  font-weight: 600;
}

:global(.resume-select-dropdown .ant-select-item-option-selected.ant-select-item-option-active) {
  background: color-mix(in srgb, var(--job-blue-soft) 80%, var(--job-blue)) !important;
}

:global(.resume-select-dropdown .ant-select-item-option-state),
:global(.resume-select-dropdown .ant-select-selection-search-input) {
  color: var(--job-blue) !important;
}

:global(.resume-select-dropdown .ant-select-item-empty) {
  background: #fff !important;
  color: var(--job-muted) !important;
}

:global(.resume-attachment-actions.ant-dropdown),
:global(.resume-attachment-actions .ant-dropdown-menu) {
  border: 1px solid var(--job-line) !important;
  border-radius: 12px !important;
  background: #fff !important;
  box-shadow: 0 14px 32px rgb(27 42 61 / 14%) !important;
}

:global(.resume-attachment-actions .ant-dropdown-menu-item) {
  background: #fff !important;
  color: var(--job-ink) !important;
  font-size: 14px !important;
}

:global(.resume-attachment-actions .ant-dropdown-menu-item:hover) {
  background: var(--job-blue-soft) !important;
  color: var(--job-blue) !important;
}

:global(.recruit-filter-dropdown.ant-cascader-menus),
:global(.recruit-filter-dropdown .ant-cascader-menus),
:global(.recruit-filter-dropdown .ant-cascader-menu) {
  background: #fff !important;
  color: var(--job-ink) !important;
}

:global(.recruit-filter-dropdown .ant-cascader-menu-item) {
  background: #fff !important;
  color: var(--job-ink) !important;
}

:global(.recruit-filter-dropdown .ant-cascader-menu-item:hover),
:global(.recruit-filter-dropdown .ant-cascader-menu-item-active) {
  background: var(--job-blue-soft) !important;
  color: var(--job-blue) !important;
}

/* ---------- 响应式 ---------- */

@media (max-width: 1180px) {
  .online-resume-page {
    grid-template-columns: 196px minmax(0, 1fr);
    gap: 16px;
  }
}

@media (max-width: 760px) {
  .online-resume-page {
    display: block;
  }

  .resume-index-panel {
    position: static;
    margin-bottom: 16px;
    padding: 14px;
  }

  /* 窄屏目录改成横向滚动条，完成度分段条跟着收窄 */
  .resume-index-nav {
    display: flex;
    overflow-x: auto;
    gap: 4px;
    padding-bottom: 2px;
  }

  .resume-index-nav > p,
  .index-tip {
    display: none;
  }

  .resume-index-nav button {
    min-width: 126px;
    min-height: 45px;
  }

  .nav-copy small,
  .nav-done {
    display: none;
  }

  .field-grid,
  .expectation-grid,
  .modal-fields {
    grid-template-columns: 1fr;
  }

  .field-full,
  .modal-fields .field-full,
  .modal-checkbox {
    grid-column: auto;
  }

  .attachments-columns {
    grid-template-columns: 1fr;
  }

  .entry-card-top {
    gap: 10px;
    flex-direction: column;
  }

  .entry-card-actions {
    width: 100%;
    justify-content: space-between;
  }

  .entry-card-actions small {
    margin-right: auto;
  }

  .resume-bottom-actions {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (prefers-reduced-motion: reduce) {
  .resume-index-nav button,
  .completion-segments i,
  .entry-card,
  .skill-suggestions button,
  .attachment-upload-cta,
  .attachment-generate-cta,
  .attachment-file-picker,
  .resume-avatar-mask,
  .resume-avatar-remove {
    transition: none;
  }
}
</style>
