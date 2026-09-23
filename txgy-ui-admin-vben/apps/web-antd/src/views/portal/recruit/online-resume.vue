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

import { Cascader, message, Modal } from 'ant-design-vue';

import {
  getPortalRecruitPostTypeList,
  type HrmPortalRecruitApi,
} from '#/api/hrm/portal/recruit';
import { getHrmRecruitFilePreviewUrl } from '#/api/hrm/portal/recruit/file-url';
import {
  createPortalResume,
  deletePortalResume,
  getPortalResumeList,
  RECRUIT_PROFILE_TITLE,
  type HrmPortalResumeApi,
  uploadPortalResume,
} from '#/api/hrm/portal/resume';
import { uploadFile } from '#/api/infra/file';
import { requestClient } from '#/api/request';
import { getAreaTree, type SystemAreaApi } from '#/api/system/area';
import { AreaCascader } from '#/components/area';

import MonthPicker from './components/month-picker.vue';
import ResumeAiPanel from './components/resume-ai-panel.vue';
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
/** 附件管理面板已从在线简历页移到「简历」下拉入口，按需弹出 */
const attachmentsModalOpen = ref(false);
const uploadInput = ref<HTMLInputElement>();
const selectedUploadFile = ref<File>();
const uploadForm = reactive({ title: '', remark: '' });
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
const uploadedAttachments = computed(() => visibleAttachments.value.filter((item) => item.sourceType !== 2));
const generatedAttachments = computed(() => visibleAttachments.value.filter((item) => item.sourceType === 2));

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

async function extractResumeText(file: File) {
  const extension = file.name.split('.').pop()?.toLowerCase();
  if (extension === 'docx') {
    const mammoth = await import('mammoth');
    const result = await mammoth.extractRawText({ arrayBuffer: await file.arrayBuffer() });
    return result.value.trim();
  }
  if (extension === 'pdf') {
    const pdfjs = await import('pdfjs-dist');
    const pdf = await pdfjs.getDocument({ data: new Uint8Array(await file.arrayBuffer()) }).promise;
    const pages: string[] = [];
    for (let pageNo = 1; pageNo <= pdf.numPages; pageNo += 1) {
      const page = await pdf.getPage(pageNo);
      const text = await page.getTextContent();
      pages.push(text.items.map((item: any) => item.str || '').join(' '));
    }
    return pages.join('\n').trim();
  }
  return (await file.text()).trim();
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
    const content = await extractResumeText(file);
    await uploadPortalResume({
      title: uploadForm.title.trim(),
      fileName: file.name,
      fileUrl,
      remark: uploadForm.remark.trim() || undefined,
      content: content || undefined,
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

/**
 * 二级导航是 sticky 的，模块顶部必须停靠在它下方，否则会被导航盖住一部分。
 * 高度随断点变化（窄屏会换行变高），所以运行时量取，不写死。
 */
function getSectionScrollOffset() {
  const bar = document.querySelector('.recruit-subnav');
  const height = bar ? bar.getBoundingClientRect().height : 0;
  return height + 16;
}

function selectSection(id: ResumeSectionId) {
  activeSection.value = id;
  const target = document.getElementById(`resume-section-${id}`);
  if (!target) return;
  // 不用 scrollIntoView：它的落点由 CSS scroll-margin-top 决定，无法跟随
  // 吸顶导航的实际高度，导致模块顶部被遮挡。
  const top = target.getBoundingClientRect().top + window.scrollY - getSectionScrollOffset();
  window.scrollTo({ top: Math.max(0, top), behavior: 'smooth' });
}

/**
 * 根据右侧模块在页面中的位置同步左侧目录，避免只在点击目录时更新高亮。
 * 判定锚点必须与 selectSection 的落点一致（同一个吸顶偏移量），否则高亮
 * 会和实际可见的模块错位。
 */
function syncActiveSectionFromScroll() {
  if (sectionSyncFrame !== undefined) return;
  sectionSyncFrame = window.requestAnimationFrame(() => {
    sectionSyncFrame = undefined;
    const anchor = getSectionScrollOffset() + 1;
    let current: ResumeSectionId = sectionItems[0].id;
    for (const item of sectionItems) {
      const section = document.getElementById(`resume-section-${item.id}`);
      if (section && section.getBoundingClientRect().top <= anchor) current = item.id;
    }
    // 最后一个模块在页面滚到底时也到不了锚点位置，会导致它永远高亮不到，
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
        <div><h1>把你的职业故事，写给下一个机会</h1><p class="resume-subtitle">完善在线简历，投递岗位时无需重复填写资料。</p></div>
        <div class="header-save-area"><span :class="['save-state', { saved: lastSavedAt }] "><i></i>{{ profileStatusLabel }}</span><a-button type="primary" :loading="submitting" @click="saveResume">保存简历</a-button></div>
      </header>

      <div v-if="loading" class="resume-loading"><a-skeleton active :paragraph="{ rows: 4 }" /><a-skeleton active :paragraph="{ rows: 5 }" /></div>

      <template v-else>
        <div class="resume-notice"><IconifyIcon icon="lucide:info" /><span>以下内容会用于在线投递和招聘沟通。带 <em>*</em> 的项目建议优先完成，邮箱为选填项。</span></div>

        <section id="resume-section-personal" class="resume-section">
          <div class="section-title-row"><div class="section-title"><span class="section-icon"><IconifyIcon icon="lucide:user-round" /></span><div><p>第一部分</p><h2>个人信息</h2><span>让招聘方快速了解你，并在合适的时候联系你。</span></div></div><span class="section-status" :class="{ done: completionItems.personal }">{{ completionItems.personal ? '已完善' : '待完善' }}</span></div>
          <div class="field-grid personal-grid">
            <label class="field required"><span>姓名</span><a-input v-model:value="form.name" maxlength="30" placeholder="请输入真实姓名" /></label>
            <label class="field required"><span>出生年月</span><MonthPicker v-model="form.birthMonth" placeholder="请选择出生年月" :min-year="birthMonthMinYear" :max-year="birthMonthMaxYear" /></label>
            <label class="field required"><span>电话</span><a-input v-model:value="form.mobile" maxlength="20" placeholder="请输入常用联系电话" /></label>
            <label class="field"><span>邮箱 <small>选填</small></span><a-input v-model:value="form.email" maxlength="80" placeholder="用于接收招聘方联系邮件" /></label>
            <label class="field required"><span>我的身份</span><a-select :value="form.identity" popup-class-name="resume-select-dropdown" placeholder="请选择身份" @change="setIdentity"><a-select-option value="worker">职场人</a-select-option><a-select-option value="student">学生</a-select-option></a-select></label>
            <label class="field required"><span>当前求职状态</span><a-select v-model:value="form.employmentStatus" popup-class-name="resume-select-dropdown" placeholder="请选择当前状态"><a-select-option v-for="item in statusOptions" :key="item" :value="item">{{ item }}</a-select-option></a-select></label>
            <label class="field required personal-wide"><span>户籍地</span><AreaCascader v-model="domicileAreaId" allow-clear class="domicile-area-cascader" :change-on-select="true" placeholder="请选择户籍所在地区" popup-class-name="recruit-filter-dropdown" :selectable-levels="[1, 2, 3]" show-search @update:model-value="handleDomicileChange" /></label>
          </div>
        </section>

        <section id="resume-section-strengths" class="resume-section">
          <div class="section-title-row"><div class="section-title"><span class="section-icon"><IconifyIcon icon="lucide:sparkles" /></span><div><p>第二部分</p><h2>个人优势</h2><span>用几句话说清楚，你最擅长解决什么问题。</span></div></div><span class="section-status" :class="{ done: completionItems.strengths }">{{ completionItems.strengths ? '已完善' : '待完善' }}</span></div>
          <label class="field field-full"><span>个人优势 <small>建议 3-5 句话</small></span><a-textarea v-model:value="form.strengths" :maxlength="500" :rows="5" show-count placeholder="例如：5 年防腐保温工程经验，熟悉施工组织、质量验收与现场协调，能够独立推进项目从开工到交付。" /></label>
        </section>

        <section id="resume-section-expectation" class="resume-section">
          <div class="section-title-row"><div class="section-title"><span class="section-icon"><IconifyIcon icon="lucide:compass" /></span><div><p>第三部分</p><h2>期望职位</h2><span>明确你的下一站，帮助岗位匹配更精准。</span></div></div><span class="section-status" :class="{ done: completionItems.expectation }">{{ completionItems.expectation ? '已完善' : '待完善' }}</span></div>
          <div class="field-grid expectation-grid">
            <label class="field"><span>求职类型</span><a-select v-model:value="form.jobType" popup-class-name="resume-select-dropdown"><a-select-option v-for="item in jobTypeOptions" :key="item.value" :value="item.value">{{ item.label }}</a-select-option></a-select></label>
            <label class="field"><span>期望职位</span><a-select v-model:value="positionValue" popup-class-name="resume-select-dropdown" show-search placeholder="请选择期望职位" @change="handlePositionChange"><a-select-option v-for="item in positionOptions" :key="item.id ?? item.name" :value="String(item.id ?? item.name)">{{ item.name }}</a-select-option></a-select></label>
            <label class="field"><span>期望行业</span><a-select v-model:value="form.industry" popup-class-name="resume-select-dropdown" show-search placeholder="请选择期望行业"><a-select-option v-for="item in industryOptions" :key="item" :value="item">{{ item }}</a-select-option></a-select></label>
            <label class="field"><span>薪资要求</span><a-select v-model:value="form.salary" popup-class-name="resume-select-dropdown"><a-select-option v-for="item in salaryOptions" :key="item.value" :value="item.value">{{ item.label }}</a-select-option></a-select></label>
            <div class="field field-full"><span>期望工作城市 <small>最多选择 3 个城市</small></span><div class="expected-cities-picker"><div v-if="form.expectedCities.length" class="expected-city-tags"><button v-for="(city, index) in form.expectedCities" :key="`${city}-${index}`" class="expected-city-tag" type="button" @click="removeExpectedCity(index)"><span>{{ city }}</span><IconifyIcon icon="lucide:x" /></button></div><Cascader v-model:value="expectedCityPickerPath" class="expected-city-cascader" :change-on-select="true" :disabled="form.expectedCities.length >= 3" :field-names="areaFieldNames" :options="expectedCityAreaOptions" :placeholder="form.expectedCities.length >= 3 ? '已选择 3 个城市' : '请选择期望工作的城市'" popup-class-name="recruit-filter-dropdown" :open="expectedCityPickerOpen" :search-value="expectedCitySearchValue" show-search @change="handleExpectedCityPathChange" @search="handleExpectedCitySearch" @dropdown-visible-change="handleExpectedCityPopupVisibleChange" /></div></div>
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
          <span>已上传</span>
          <strong>{{ visibleAttachments.length }}</strong>
          <small>支持常见文档格式 · 单个不超过 10 兆</small>
        </div>

        <div v-if="loading" class="attachments-loading"><a-skeleton active :paragraph="{ rows: 4 }" /></div>
        <div v-else class="attachments-columns">
          <section class="attachments-column">
            <header><strong>已上传附件</strong><small>{{ uploadedAttachments.length }}/5</small></header>
            <div v-if="!uploadedAttachments.length" class="attachments-empty"><span><IconifyIcon icon="lucide:file-plus-2" /></span><p>还没有上传附件简历</p><a-button type="primary" ghost @click="openUploadModal">上传附件</a-button></div>
            <div v-else class="attachments-list">
            <article v-for="resume in uploadedAttachments" :key="resume.id" class="attachment-item">
            <span :class="['attachment-file-icon', `is-${getAttachmentExtension(resume)}`]"><IconifyIcon icon="lucide:file-text" /></span>
            <div class="attachment-copy">
              <strong :title="resume.title">{{ resume.title }}</strong>
              <small :title="resume.fileName || resume.title">{{ resume.fileName || '附件简历' }}</small>
              <small>{{ formatAttachmentDate(resume.createTime) }}</small>
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
            <div v-if="!generatedAttachments.length" class="attachments-empty"><span><IconifyIcon icon="lucide:sparkles" /></span><p>在附件简历制作中生成后会显示在这里</p></div>
            <div v-else class="attachments-list">
              <article v-for="resume in generatedAttachments" :key="resume.id" class="attachment-item">
                <span :class="['attachment-file-icon', `is-${getAttachmentExtension(resume)}`]"><IconifyIcon icon="lucide:file-check-2" /></span>
                <div class="attachment-copy"><strong :title="resume.title">{{ resume.title }}</strong><small>{{ resume.fileName || '制作生成附件' }}</small><small>{{ formatAttachmentDate(resume.createTime) }}</small></div>
                <a-button class="attachment-preview" type="text" aria-label="删除" @click="deleteAttachment(resume)"><IconifyIcon icon="lucide:trash-2" /></a-button>
              </article>
            </div>
          </section>
        </div>

        <button class="attachment-upload-cta" type="button" @click="openUploadModal">
          <span class="attachment-upload-cta-icon"><IconifyIcon icon="lucide:upload-cloud" /></span>
          <span><strong>添加附件简历</strong><small>让招聘方更快了解你</small></span>
          <IconifyIcon class="attachment-upload-cta-arrow" icon="lucide:arrow-up-right" />
        </button>
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
 .online-resume-page { --resume-ink: #111827; --resume-muted: #374151; --resume-line: #f1e4da; --resume-soft: #fff8f1; --resume-orange: #e47b32; --resume-orange-deep: #c45d1d; display: grid; grid-template-columns: 224px minmax(0, 1fr); gap: 24px; align-items: start; color: var(--resume-ink); font-size: 16px; }
.resume-index-panel { position: sticky; top: 20px; display: grid; gap: 20px; padding: 20px 14px 16px; border: 1px solid var(--resume-line); border-radius: 16px; background: #fff; box-shadow: 0 12px 32px rgb(180 104 54 / 6%); }
.resume-index-brand { display: flex; align-items: center; gap: 10px; padding: 2px 8px; }
 .brand-mark { display: grid; width: 34px; height: 34px; place-items: center; border-radius: 10px; background: var(--resume-orange); color: #fff; font-size: 19px; font-weight: 700; }
.resume-index-brand strong, .resume-index-brand small { display: block; }
 .resume-index-brand strong { font-size: 18px; }
  .resume-index-brand small { margin-top: 3px; color: #111827; font-size: 14px; letter-spacing: .08em; }
.completion-card { padding: 13px 12px; border-radius: 10px; background: var(--resume-soft); }
  .completion-card-top { display: flex; align-items: center; justify-content: space-between; color: var(--resume-muted); font-size: 16px; }
 .completion-card-top strong { color: var(--resume-orange-deep); font-size: 20px; }
.completion-track { height: 5px; margin: 10px 0 7px; overflow: hidden; border-radius: 3px; background: #f3e9e1; }
.completion-track i { display: block; height: 100%; border-radius: inherit; background: var(--resume-orange); transition: width .25s ease; }
  .completion-card small { color: #111827; font-size: 14px; }
  .resume-index-nav > p { margin: 0 10px 4px; color: #111827; font-size: 14px; font-weight: 700; letter-spacing: .08em; }
.resume-index-nav { display: grid; gap: 2px; }
.resume-index-nav button { display: flex; width: 100%; min-height: 51px; align-items: center; gap: 8px; padding: 7px 8px; border: 0; border-radius: 9px; background: transparent; color: inherit; cursor: pointer; text-align: left; transition: background .18s ease, transform .18s ease; }
.resume-index-nav button:hover, .resume-index-nav button.active { background: #fff7f0; transform: translateX(2px); }
  .nav-number { width: 22px; color: #111827; font-size: 14px; }
.resume-index-nav button.active .nav-number { color: var(--resume-orange); font-weight: 700; }
.nav-copy { display: grid; min-width: 0; gap: 3px; }
  .nav-copy strong { font-size: 16px; font-weight: 600; }
  .nav-copy small { color: #111827; font-size: 14px; }
.nav-done { margin-left: auto; color: var(--resume-orange); font-size: 14px; }
 .index-tip { display: flex; gap: 8px; padding: 12px 10px; border-top: 1px solid #f5eae2; color: #111827; }
.index-tip :deep(svg) { flex: 0 0 auto; color: var(--resume-orange); font-size: 15px; }
  .index-tip p { margin: 0; font-size: 14px; line-height: 1.6; }
.resume-editor { display: grid; gap: 16px; min-width: 0; }
.resume-editor-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; padding: 7px 4px 5px; }
  .resume-editor-header h1 { margin: 0; color: #111827; font-size: 30px; letter-spacing: -.04em; line-height: 1.25; }
 .resume-subtitle { margin: 8px 0 0; color: var(--resume-muted); font-size: 15px; }
.header-save-area { display: flex; flex-shrink: 0; align-items: center; gap: 14px; }
  .save-state { display: inline-flex; align-items: center; gap: 6px; color: #111827; font-size: 14px; white-space: nowrap; }
.save-state i { width: 6px; height: 6px; border-radius: 50%; background: #c5b8b1; }
.save-state.saved i { background: #6eae89; }
.resume-editor :deep(.ant-btn-primary) { border-color: var(--resume-orange); background: var(--resume-orange); color: #fff !important; box-shadow: 0 7px 16px rgb(228 123 50 / 18%); }
.resume-editor :deep(.ant-btn-primary:hover) { border-color: var(--resume-orange-deep); background: var(--resume-orange-deep); color: #fff !important; }
  .resume-notice { display: flex; align-items: center; gap: 9px; min-height: 42px; padding: 0 15px; border: 1px solid #f6e6d8; border-radius: 9px; background: #fffdfb; color: #111827; font-size: 15px; }
.resume-notice :deep(svg) { color: var(--resume-orange); font-size: 16px; }
.resume-notice em { color: var(--resume-orange-deep); font-style: normal; }
.resume-loading { display: grid; gap: 16px; padding: 24px; border: 1px solid var(--resume-line); border-radius: 12px; background: #fff; }
.resume-section { scroll-margin-top: 76px; padding: 22px 24px 25px; border: 1px solid var(--resume-line); border-radius: 13px; background: #fff; box-shadow: 0 4px 15px rgb(180 104 54 / 3%); }
.section-title-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 22px; }
.section-title { display: flex; align-items: flex-start; gap: 12px; }
.section-icon { display: grid; width: 35px; height: 35px; flex: 0 0 auto; place-items: center; border-radius: 10px; background: var(--resume-soft); color: var(--resume-orange); font-size: 17px; }
  .section-title p { margin: 0 0 4px; color: #111827; font-size: 14px; font-weight: 700; letter-spacing: .08em; }
  .section-title h2 { margin: 0; color: #111827; font-size: 22px; letter-spacing: -.02em; }
  .section-title h2 + span { display: block; margin-top: 5px; color: #111827; font-size: 15px; }
  .section-status { padding: 5px 9px; border-radius: 5px; color: #111827; background: #fbf8f5; font-size: 14px; white-space: nowrap; }
.section-status.done { color: #628e74; background: #f2faf5; }
.field-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px 20px; }
  .field { display: grid; min-width: 0; gap: 8px; color: #111827; font-size: 16px; }
 .field > span { color: #111827; font-weight: 600; }
.field > span::before { content: ''; }
.field.required > span::before { margin-right: 4px; color: var(--resume-orange); content: '*'; }
  .field > span small { margin-left: 4px; color: #111827; font-size: 14px; font-weight: 400; }
.field-full, .personal-wide { grid-column: 1 / -1; }
  .resume-editor :deep(.ant-input), .resume-editor :deep(.ant-select-selector) { min-height: 40px; border-color: #eee1d8 !important; border-radius: 7px !important; background: #fffdfb !important; color: #111827; font-size: 16px; box-shadow: none !important; }
.resume-editor :deep(.ant-input:hover), .resume-editor :deep(.ant-input:focus), .resume-editor :deep(.ant-select-focused .ant-select-selector), .resume-editor :deep(.ant-select-selector:hover) { border-color: #e9b78e !important; }
.resume-editor :deep(textarea.ant-input) { min-height: 104px; padding: 11px 12px; line-height: 1.7; }
.resume-editor :deep(.ant-select) { width: 100%; }
.resume-editor :deep(.ant-select-selection-placeholder), .resume-editor :deep(.ant-input::placeholder) { color: #c1b1a8; }
.resume-editor :deep(.domicile-area-cascader .ant-select-selector) { min-height: 40px; border-color: #cfe4e8 !important; border-radius: 7px !important; background: #fff !important; color: #123f49; }
.resume-editor :deep(.domicile-area-cascader:hover .ant-select-selector), .resume-editor :deep(.domicile-area-cascader.ant-select-focused .ant-select-selector) { border-color: #74cbd2 !important; }
.resume-editor :deep(.domicile-area-cascader .ant-select-selection-item) { color: #123f49; }
.resume-editor :deep(.domicile-area-cascader .ant-select-selection-placeholder) { color: #86a6ae; }
.expected-cities-picker { display: flex; min-height: 40px; align-items: center; flex-wrap: wrap; gap: 6px; padding: 4px 8px; border: 1px solid #cfe4e8; border-radius: 7px; background: #fff; transition: border-color .18s ease, box-shadow .18s ease; }
.expected-cities-picker:focus-within { border-color: #74cbd2; box-shadow: 0 0 0 2px rgb(116 203 210 / 14%); }
.expected-city-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.expected-city-tag { display: inline-flex; max-width: 100%; align-items: center; gap: 4px; padding: 4px 7px 4px 9px; border: 1px solid #dbe8ee; border-radius: 5px; background: #f4f8fa; color: #34515c; cursor: pointer; font: inherit; font-size: 13px; line-height: 20px; }
.expected-city-tag > span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.expected-city-tag :deep(svg) { flex: 0 0 auto; color: #86a6ae; font-size: 13px; }
.expected-city-tag:hover { border-color: #74cbd2; background: #e8f7f8; color: #0f777d; }
.expected-city-tag:hover :deep(svg) { color: #0f8f8c; }
.resume-editor :deep(.expected-city-cascader) { width: auto; min-width: 190px; flex: 1 1 190px; }
.resume-editor :deep(.expected-city-cascader .ant-select-selector) { min-height: 30px; padding: 0 !important; border: 0 !important; border-radius: 0 !important; background: transparent !important; box-shadow: none !important; }
.resume-editor :deep(.expected-city-cascader .ant-select-selection-placeholder) { color: #86a6ae; }
.resume-editor :deep(.expected-city-cascader .ant-select-selection-search-input) { color: #123f49; }
.expectation-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .add-button { height: 32px; padding: 0 5px; color: var(--resume-orange-deep); font-size: 16px; }
.add-button:hover { color: var(--resume-orange); }
.add-button :deep(svg) { margin-right: 3px; }
 .empty-module { display: grid; justify-items: center; gap: 8px; padding: 26px 20px; border: 1px dashed #f0ded1; border-radius: 9px; background: #fffdfb; text-align: center; color: #111827; }
.empty-icon { display: grid; width: 36px; height: 36px; place-items: center; border-radius: 50%; background: var(--resume-soft); color: #da9360; font-size: 17px; }
  .empty-module strong { color: #111827; font-size: 17px; }
  .empty-module p { margin: 0 0 5px; color: #111827; font-size: 15px; }
.resume-editor :deep(.ant-btn-background-ghost) { border-color: #0f8f8c; color: #fff !important; background: linear-gradient(135deg, #0f9f9b, #2f80ed); }
.entry-list { display: grid; gap: 10px; }
 .entry-card { padding: 16px 17px; border: 1px solid #f0e4db; border-radius: 9px; background: #fffdfa; color: #111827; }
.entry-card:hover { border-color: #efc19e; }
.entry-card-top { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; }
.entry-card-top > div:first-child { display: grid; gap: 5px; min-width: 0; }
  .entry-card-top strong { color: #111827; font-size: 17px; }
  .entry-card-top strong em { margin-left: 5px; padding: 3px 5px; border-radius: 4px; color: #bc681f; background: #fff1e3; font-size: 13px; font-style: normal; font-weight: 500; }
  .entry-card-top span { color: #111827; font-size: 15px; }
.entry-card-top span i { font-style: normal; }
.entry-card-actions { display: flex; flex: 0 0 auto; align-items: center; gap: 8px; }
  .entry-card-actions small { margin-right: 4px; color: #111827; font-size: 14px; }
 .entry-card-actions button { display: grid; width: 25px; height: 25px; place-items: center; border: 0; border-radius: 5px; background: transparent; color: #111827; cursor: pointer; }
.entry-card-actions button:hover { background: var(--resume-soft); color: var(--resume-orange-deep); }
.entry-meta { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; }
  .entry-meta span { padding: 4px 8px; border-radius: 4px; color: #111827; background: #fff5ed; font-size: 14px; }
  .entry-description, .entry-achievement { margin: 12px 0 0; color: #111827; font-size: 15px; line-height: 1.75; white-space: pre-line; }
.entry-achievement { padding-top: 10px; border-top: 1px solid #f4eae4; }
.entry-achievement b { margin-right: 8px; color: #bd6a2a; font-weight: 600; }
  .entry-link { display: block; margin-top: 9px; overflow: hidden; color: var(--resume-orange-deep); font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.skill-suggestions { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-top: 13px; }
  .skill-suggestions > span { color: #111827; font-size: 14px; }
  .skill-suggestions button { padding: 5px 9px; border: 1px solid #f0e1d7; border-radius: 5px; background: #fff; color: #111827; cursor: pointer; font-size: 14px; }
.skill-suggestions button:hover { border-color: #edbd99; color: var(--resume-orange-deep); }
 .skill-suggestions button :deep(svg) { margin-right: 2px; font-size: 12px; }
  .resume-bottom-actions { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 3px 4px 14px; color: #111827; font-size: 14px; }
 .resume-bottom-actions > span { display: inline-flex; align-items: center; gap: 5px; }
 .resume-bottom-actions :deep(svg) { color: #75a184; font-size: 14px; }
.attachments-modal-body { display: grid; gap: 16px; min-width: 0; }
.attachments-columns { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.attachments-column { min-width: 0; padding: 14px; border: 1px solid var(--resume-line); border-radius: 10px; background: #fff; }
.attachments-column > header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; color: #123f49; }
.attachments-column > header small { color: #8497a3; }
 .attachments-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; padding: 0 4px 14px; border-bottom: 1px solid #f5eae2; }
  .attachments-header h2 { margin: 0; color: #111827; font-size: 22px; }
 .attachments-add { display: grid; width: 32px; height: 32px; place-items: center; padding: 0; border-radius: 8px; color: var(--resume-orange-deep); }
 .attachments-add:hover { background: var(--resume-soft); color: var(--resume-orange); }
 .attachments-add :deep(svg) { font-size: 20px; }
 .attachments-summary { display: grid; grid-template-columns: 1fr auto; align-items: center; gap: 4px 8px; padding: 12px; border-radius: 10px; background: var(--resume-soft); }
   .attachments-summary span { color: #111827; font-size: 15px; }
  .attachments-summary strong { color: var(--resume-orange-deep); font-size: 22px; line-height: 1; }
   .attachments-summary small { grid-column: 1 / -1; color: #111827; font-size: 14px; line-height: 1.5; }
 .attachments-loading { display: grid; gap: 14px; }
 .attachments-empty { display: grid; justify-items: center; gap: 8px; padding: 18px 8px 20px; border: 1px dashed #f0ded1; border-radius: 9px; background: #fffdfb; text-align: center; }
 .attachments-empty > span { display: grid; width: 38px; height: 38px; place-items: center; border-radius: 50%; background: var(--resume-soft); color: #da9360; font-size: 18px; }
   .attachments-empty strong { color: #111827; font-size: 17px; }
   .attachments-empty p { margin: 0 0 4px; color: #111827; font-size: 15px; line-height: 1.6; }
   .attachments-empty :deep(.ant-btn) { height: 32px; padding-inline: 11px; font-size: 15px; }
 .attachments-list { display: grid; gap: 10px; }
 .attachment-item { display: grid; grid-template-columns: 36px minmax(0, 1fr) 24px; align-items: center; gap: 9px; padding: 10px 7px; border-bottom: 1px solid #f5eae2; }
 .attachment-item:last-child { border-bottom: 0; }
 .attachment-file-icon { display: grid; width: 36px; height: 42px; place-items: center; border-radius: 8px; color: #fff; background: #d9824a; font-size: 18px; }
 .attachment-file-icon.is-pdf { background: #ed6262; }
 .attachment-file-icon.is-doc, .attachment-file-icon.is-docx { background: #5e91df; }
 .attachment-copy { display: grid; min-width: 0; gap: 3px; }
 .attachment-copy strong, .attachment-copy small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
   .attachment-copy strong { color: #111827; font-size: 15px; font-weight: 600; }
   .attachment-copy small { color: #111827; font-size: 14px; }
 .attachment-preview { display: grid; width: 24px; height: 28px; place-items: center; padding: 0; color: #b8a194; }
 .attachment-preview:hover { color: var(--resume-orange-deep); background: var(--resume-soft); }
 .attachment-upload-cta { display: flex; align-items: center; gap: 9px; width: 100%; padding: 13px 10px; border: 1px solid #f2ddcd; border-radius: 11px; background: linear-gradient(135deg, #fff9f2, #fff5ea); color: inherit; cursor: pointer; text-align: left; transition: border-color .18s ease, transform .18s ease, box-shadow .18s ease; }
 .attachment-upload-cta:hover { border-color: #efbd96; box-shadow: 0 8px 18px rgb(180 104 54 / 8%); transform: translateY(-1px); }
 .attachment-upload-cta-icon { display: grid; width: 31px; height: 31px; flex: 0 0 auto; place-items: center; border-radius: 9px; background: #ffe9d4; color: var(--resume-orange-deep); font-size: 15px; }
 .attachment-upload-cta > span:nth-child(2) { display: grid; min-width: 0; gap: 3px; }
   .attachment-upload-cta strong { color: #111827; font-size: 16px; }
   .attachment-upload-cta small { color: #111827; font-size: 14px; }
 .attachment-upload-cta-arrow { margin-left: auto; color: #d69261; font-size: 14px; }
 .attachment-upload-form { display: grid; gap: 16px; padding: 4px 0 10px; }
   .attachment-upload-help { margin: 0; padding: 10px 12px; border-radius: 7px; background: #fff8f1; color: #111827; font-size: 15px; line-height: 1.6; }
 .attachment-file-picker { display: grid; min-height: 116px; justify-items: center; align-content: center; gap: 5px; padding: 15px; border: 1px dashed #efbb92; border-radius: 9px; background: #fffaf5; color: var(--resume-orange); cursor: pointer; text-align: center; }
 .attachment-file-picker:hover, .attachment-file-picker.has-file { border-color: var(--resume-orange); background: var(--resume-soft); }
 .attachment-file-picker input { display: none; }
 .attachment-file-picker > svg { font-size: 25px; }
   .attachment-file-picker strong { max-width: 90%; overflow: hidden; color: #111827; font-size: 16px; text-overflow: ellipsis; white-space: nowrap; }
   .attachment-file-picker small { color: #111827; font-size: 14px; }
 .modal-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px 20px; padding: 8px 0 16px; }
.modal-fields .field-full, .modal-checkbox { grid-column: 1 / -1; }
.date-range { display: grid; grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr); align-items: center; gap: 9px; }
  .date-range > span { color: #111827; font-size: 15px; }
  .modal-checkbox { color: #111827; font-size: 15px; }
 @media (max-width: 1180px) { .online-resume-page { grid-template-columns: 190px minmax(0, 1fr); gap: 16px; } .resume-attachments-panel { position: static; } }
 @media (max-width: 1000px) { .resume-editor-header { align-items: flex-start; flex-direction: column; } .header-save-area { width: 100%; justify-content: space-between; } }
  @media (max-width: 760px) { .online-resume-page { display: block; } .resume-index-panel { position: static; margin-bottom: 16px; padding: 14px; } .resume-attachments-panel { margin-top: 16px; } .resume-index-nav { display: flex; overflow-x: auto; gap: 4px; padding-bottom: 2px; } .resume-index-nav > p, .index-tip { display: none; } .resume-index-nav button { min-width: 126px; min-height: 45px; } .nav-copy small, .nav-done { display: none; } .resume-editor-header h1 { font-size: 25px; } .resume-section { padding: 18px 16px 20px; } .field-grid, .expectation-grid, .modal-fields { grid-template-columns: 1fr; } .field-full, .personal-wide, .modal-fields .field-full, .modal-checkbox { grid-column: auto; } .section-title-row { align-items: flex-start; } .section-title h2 + span { max-width: 240px; line-height: 1.5; } .entry-card-top { gap: 10px; flex-direction: column; } .entry-card-actions { width: 100%; justify-content: space-between; } .entry-card-actions small { margin-right: auto; } .resume-bottom-actions { align-items: flex-start; flex-direction: column; } }
@media (prefers-reduced-motion: reduce) { .resume-index-nav button, .completion-track i { transition: none; } }

.online-resume-page {
  --resume-ink: #111827;
  --resume-muted: #374151;
  --resume-line: #d7e9ed;
  --resume-soft: #e8f7f8;
  --resume-orange: #0f8f8c;
  --resume-orange-deep: #0f777d;
  color: var(--resume-ink);
}

.resume-index-panel,
.resume-section,
.resume-attachments-panel { border-color: var(--resume-line); box-shadow: 0 12px 32px rgb(18 63 73 / 6%); }
.brand-mark { background: #0f8f8c; }
.completion-card,
.section-icon,
.attachments-summary,
.empty-icon { background: #e8f7f8; }
.completion-track { background: #e6f1f3; }
/* 悬停态与选中态必须能区分：之前两者同色，用户分不清「鼠标划过」和「当前所在模块」，
   容易误判成目录没跟随。选中额外加一条左侧强调条。 */
.resume-index-nav button:hover { background: #f2f8fa; }
.resume-index-nav button.active { background: #eaf3ff; box-shadow: inset 3px 0 0 #0f8f8c; }
.resume-index-nav button.active .nav-number,
.nav-done { color: #0f8f8c; }
.resume-editor-header h1,
.section-title h2,
.attachments-header h2 { color: #123f49; }
.resume-notice { border-color: #c8e7eb; background: #f5fbfc; color: #111827; }
.resume-editor :deep(.ant-btn-primary) { border-color: #0f8f8c; background: linear-gradient(135deg, #0f9f9b, #2f80ed); color: #fff !important; box-shadow: 0 7px 16px rgb(15 143 140 / 18%); }
.resume-editor :deep(.ant-btn-primary:hover) { border-color: #0b777c; background: linear-gradient(135deg, #0b8587, #2563c7); color: #fff !important; }
.resume-editor :deep(.ant-input),
.resume-editor :deep(.ant-select-selector) { border-color: #cfe4e8 !important; background: #fff !important; color: #111827; }
.resume-editor :deep(.ant-input:hover),
.resume-editor :deep(.ant-input:focus),
.resume-editor :deep(.ant-select-focused .ant-select-selector),
.resume-editor :deep(.ant-select-selector:hover) { border-color: #74cbd2 !important; }
.resume-editor :deep(.ant-select-selection-placeholder),
.resume-editor :deep(.ant-input::placeholder) { color: #86a6ae; }
.section-status.done { color: #0f777d; background: #e8f7f8; }
.empty-module,
.attachments-empty { border-color: #b9dfe5; background: #f5fbfc; }
.entry-card:hover { border-color: #8bd6dc; }
.attachment-file-icon,
.attachment-file-icon.is-pdf,
.attachment-file-icon.is-doc,
.attachment-file-icon.is-docx { background: #2f80ed; }
.attachment-upload-cta { border-color: #b9dfe5; background: linear-gradient(135deg, #f5fbfc, #eaf3ff); }
.attachment-upload-cta:hover { border-color: #74cbd2; box-shadow: 0 8px 18px rgb(15 143 140 / 8%); }
.attachment-upload-cta-icon,
.attachment-file-picker { color: #0f8f8c; background: #e8f7f8; }
.attachment-file-picker { border-color: #74cbd2; }
.attachment-file-picker:hover,
.attachment-file-picker.has-file { border-color: #0f8f8c; background: #e8f7f8; }
.online-resume-page .resume-index-nav > p,
.online-resume-page .section-title p { color: #111827; }
.online-resume-page .section-status { color: #111827; background: #f5fbfc; }
.online-resume-page .section-status.done { color: #0f777d; background: #e8f7f8; }
.online-resume-page .save-state,
.online-resume-page .entry-card-top small { color: #111827; }
.online-resume-page .empty-module strong,
.online-resume-page .attachments-empty strong { color: #123f49; }

/* 附件管理弹窗被 teleport 到 body，脱离门户的浅色主题，需要显式指定浅色，
   与同页的简历上传弹窗保持同一套观感。 */
:global(.resume-attachments-modal .ant-modal-content) {
  overflow: hidden;
  border: 1px solid #c8e7eb;
  border-radius: 16px;
  background: #fff !important;
  box-shadow: 0 20px 56px rgb(18 63 73 / 18%);
}

:global(.resume-attachments-modal .ant-modal-header) {
  margin-bottom: 0;
  padding: 20px 24px 14px;
  border-bottom: 1px solid #e6f1f3;
  background: #fff !important;
}

:global(.resume-attachments-modal .ant-modal-title) {
  color: #123f49 !important;
  font-size: 22px;
  font-weight: 650;
}

:global(.resume-attachments-modal .ant-modal-close) {
  color: #5f7f87 !important;
}

:global(.resume-attachments-modal .ant-modal-close:hover) {
  color: #0f8f8c !important;
  background: #e8f7f8 !important;
}

:global(.resume-attachments-modal .ant-modal-body) {
  padding: 18px 24px 24px;
  background: #fff !important;
}

:global(.resume-upload-modal .ant-modal-content) {
  overflow: hidden;
  border: 1px solid #c8e7eb;
  border-radius: 16px;
  background: #fff !important;
  box-shadow: 0 20px 56px rgb(18 63 73 / 18%);
}

:global(.resume-upload-modal .ant-modal-header) {
  margin-bottom: 0;
  padding: 20px 24px 14px;
  border-bottom: 1px solid #e6f1f3;
  background: #fff !important;
}

:global(.resume-upload-modal .ant-modal-title) {
  color: #123f49 !important;
  font-size: 22px;
  font-weight: 650;
}

:global(.resume-upload-modal .ant-modal-close) {
  color: #5f7f87 !important;
}

:global(.resume-upload-modal .ant-modal-close:hover) {
  color: #0f8f8c !important;
  background: #e8f7f8 !important;
}

:global(.resume-upload-modal .ant-modal-body) {
  padding: 18px 24px 8px;
  background: #fff !important;
}

:global(.resume-upload-modal .ant-modal-footer) {
  margin-top: 0;
  padding: 14px 24px 20px;
  border-top: 1px solid #e6f1f3;
  background: #f8fcfd !important;
}

:global(.resume-upload-modal .attachment-upload-help) {
  border: 1px solid #c8e7eb;
  background: #f5fbfc !important;
  color: #111827 !important;
}

:global(.resume-upload-modal .attachment-upload-form .field > span) {
  color: #123f49 !important;
}

:global(.resume-upload-modal .ant-btn),
:global(.resume-upload-modal .ant-input),
:global(.resume-upload-modal .ant-select-selector),
:global(.resume-upload-modal .ant-checkbox-wrapper) {
  font-size: 16px !important;
}

:global(.resume-upload-modal .attachment-upload-form .field > span small) {
  color: #7593a0 !important;
}

:global(.resume-upload-modal .attachment-upload-form .ant-input) {
  border-color: #cfe4e8 !important;
  border-radius: 8px !important;
  background: #fff !important;
  color: #123f49 !important;
  box-shadow: none !important;
}

:global(.resume-upload-modal .attachment-upload-form .ant-input:hover),
:global(.resume-upload-modal .attachment-upload-form .ant-input:focus) {
  border-color: #74cbd2 !important;
}

:global(.resume-upload-modal .attachment-upload-form .ant-input::placeholder) {
  color: #86a6ae !important;
}

:global(.resume-upload-modal .attachment-file-picker) {
  border-color: #74cbd2 !important;
  background: #e8f7f8 !important;
  color: #0f8f8c !important;
}

:global(.resume-upload-modal .attachment-file-picker:hover),
:global(.resume-upload-modal .attachment-file-picker.has-file) {
  border-color: #0f8f8c !important;
  background: #e8f7f8 !important;
}

:global(.resume-upload-modal .attachment-file-picker strong) {
  color: #123f49 !important;
}

:global(.resume-upload-modal .attachment-file-picker small) {
  color: #7593a0 !important;
}

:global(.resume-upload-modal .ant-modal-footer .ant-btn-default) {
  border-color: #b9dfe5 !important;
  background: #fff !important;
  color: #2f5d73 !important;
}

:global(.resume-upload-modal .ant-modal-footer .ant-btn-default:hover) {
  border-color: #74cbd2 !important;
  background: #e8f7f8 !important;
  color: #0f777d !important;
}

:global(.resume-upload-modal .ant-modal-footer .ant-btn-primary) {
  border-color: #0f8f8c !important;
  background: linear-gradient(135deg, #0f9f9b, #2f80ed) !important;
  color: #fff !important;
  box-shadow: 0 7px 16px rgb(15 143 140 / 18%);
}

:global(.resume-upload-modal .ant-modal-footer .ant-btn-primary:hover) {
  border-color: #0b777c !important;
  background: linear-gradient(135deg, #0b8587, #2563c7) !important;
  color: #fff !important;
}

:global(.resume-select-dropdown.ant-select-dropdown),
:global(.resume-select-dropdown .ant-select-dropdown) {
  padding: 6px !important;
  border: 1px solid #cfe4e8 !important;
  border-radius: 10px !important;
  background: #fff !important;
  box-shadow: 0 14px 30px rgb(18 63 73 / 14%) !important;
}

:global(.resume-select-dropdown .ant-select-item) {
  min-height: 36px;
  padding: 8px 10px;
  border-radius: 6px;
  color: #123f49 !important;
  background: #fff !important;
  font-size: 16px !important;
  line-height: 20px;
}

:global(.resume-select-dropdown .ant-select-item-option-active) {
  color: #0f777d !important;
  background: #f0f8ff !important;
}

:global(.resume-select-dropdown .ant-select-item-option-selected) {
  color: #0f777d !important;
  background: #e8f7f8 !important;
  font-weight: 600;
}

:global(.resume-select-dropdown .ant-select-item-option-selected.ant-select-item-option-active) {
  background: #dff3f5 !important;
}

:global(.resume-select-dropdown .ant-select-item-option-state),
:global(.resume-select-dropdown .ant-select-selection-search-input) {
  color: #0f8f8c !important;
}

:global(.resume-select-dropdown .ant-select-item-empty) {
  color: #7b929a !important;
  background: #fff !important;
}

:global(.resume-attachment-actions.ant-dropdown),
:global(.resume-attachment-actions .ant-dropdown-menu) {
  border: 1px solid #cfe4e8 !important;
  border-radius: 10px !important;
  background: #fff !important;
  box-shadow: 0 14px 30px rgb(18 63 73 / 14%) !important;
}

:global(.resume-attachment-actions .ant-dropdown-menu-item) {
  color: #123f49 !important;
  background: #fff !important;
  font-size: 16px !important;
}

:global(.resume-attachment-actions .ant-dropdown-menu-item:hover) {
  color: #0f777d !important;
  background: #f0f8ff !important;
}

:global(.recruit-filter-dropdown.ant-cascader-menus),
:global(.recruit-filter-dropdown .ant-cascader-menus),
:global(.recruit-filter-dropdown .ant-cascader-menu) {
  color: #334155 !important;
  background: #fff !important;
}

:global(.recruit-filter-dropdown .ant-cascader-menu-item) {
  color: #334155 !important;
  background: #fff !important;
}

:global(.recruit-filter-dropdown .ant-cascader-menu-item:hover),
:global(.recruit-filter-dropdown .ant-cascader-menu-item-active) {
  color: #1d4ed8 !important;
  background: #eff6ff !important;
}

/* ===== 简历版本面板 ===== */

.version-panel {
  display: grid;
  gap: 16px;
}

.version-panel-help {
  margin: 0;
  padding: 12px 14px;
  border-radius: 10px;
  color: #475569;
  background: #f4fbf9;
  font-size: 13px;
  line-height: 1.7;
}

.version-panel-actions {
  display: flex;
  gap: 10px;
}

.version-panel-empty {
  padding: 26px 12px;
  border: 1px dashed #d8e6e5;
  border-radius: 10px;
  color: #7d9299;
  background: #fbfdfd;
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
  border: 1px solid #e2edec;
  border-radius: 10px;
  background: #fff;
}

.version-item-main {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.version-item-main strong {
  color: #0f766e;
  font-size: 15px;
}

.version-item-summary {
  overflow: hidden;
  color: #475569;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.version-item-meta {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 10px;
  color: #8497a3;
  font-size: 12px;
}

.version-item-invalid {
  padding: 1px 7px;
  border-radius: 999px;
  color: #b45309;
  background: #fef3c7;
}
</style>
