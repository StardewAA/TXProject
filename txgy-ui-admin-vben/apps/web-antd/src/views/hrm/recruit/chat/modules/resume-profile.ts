/**
 * 求职者在线简历（`content` JSON）的只读解析。
 *
 * 归一化口径与门户端 `views/portal/recruit/online-resume.vue` 的 `normalizeProfile` 对齐：
 * 线上同时存在「早期扁平结构」（company / school / education 等字段平铺）与
 * 「当前数组结构」（experiences / projects / educationExperiences），两种都要读得出来，
 * 否则老简历在招聘方这边会显示成空白。
 *
 * 与编辑态的关键差异：这里不给学历、全日制等字段兜默认值 —— 招聘方看到的必须是
 * 求职者真实填写的内容，缺项显示「未填写」，不能凭空补一个「本科」。
 */

export interface ResumeEducationExperience {
  degree: string;
  end: string;
  experience: string;
  major: string;
  school: string;
  schoolType: string;
  start: string;
}

export interface ResumeProfile {
  avatar: string;
  birthMonth: string;
  domicile: string;
  educationExperiences: ResumeEducationExperience[];
  email: string;
  employmentStatus: string;
  expectedCities: string[];
  expectedPostName: string;
  experiences: ResumeWorkExperience[];
  industry: string;
  jobType: string;
  mobile: string;
  name: string;
  projects: ResumeProjectExperience[];
  salary: string;
  skills: string;
  strengths: string;
}

export interface ResumeProjectExperience {
  achievements: string;
  description: string;
  end: string;
  highlight: boolean;
  link: string;
  name: string;
  role: string;
  start: string;
}

export interface ResumeWorkExperience {
  achievements: string;
  company: string;
  department: string;
  description: string;
  end: string;
  industry: string;
  position: string;
  start: string;
}

export const jobTypeOptions = [
  { label: '不限', value: 'all' },
  { label: '全职', value: 'fulltime' },
  { label: '实习', value: 'internship' },
  { label: '兼职', value: 'parttime' },
];

export const salaryOptions = [
  { label: '不限', value: 'all' },
  { label: '3000 元以下', value: 'below-3k' },
  { label: '3000-5000 元', value: '3k-5k' },
  { label: '5000-10000 元', value: '5k-10k' },
  { label: '10000-20000 元', value: '10k-20k' },
  { label: '20000-50000 元', value: '20k-50k' },
  { label: '50000 元以上', value: 'above-50k' },
];

function asString(value: unknown) {
  if (typeof value === 'string') return value;
  return value === null || value === undefined ? '' : String(value);
}

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object'
    ? (value as Record<string, unknown>)
    : {};
}

/** 兼容 `2000年3月` 这类早期写法，统一成 `2000-03` */
function normalizeBirthMonth(value: unknown) {
  const birthMonth = asString(value).trim();
  const match = birthMonth.match(/^(\d{4})\s*(?:年|[-/.])\s*(\d{1,2})\s*月?$/);
  if (!match) return birthMonth;
  const month = Number(match[2]);
  return month >= 1 && month <= 12
    ? `${match[1]}-${String(month).padStart(2, '0')}`
    : birthMonth;
}

function normalizeWorkExperience(value: unknown): ResumeWorkExperience {
  const item = asRecord(value);
  return {
    achievements: asString(item.achievements),
    company: asString(item.company),
    department: asString(item.department),
    description: asString(item.description || item.content),
    end: asString(item.end || item.jobEnd),
    industry: asString(item.industry),
    position: asString(item.position),
    start: asString(item.start || item.jobStart),
  };
}

function normalizeProjectExperience(value: unknown): ResumeProjectExperience {
  const item = asRecord(value);
  return {
    achievements: asString(item.achievements),
    description: asString(item.description),
    end: asString(item.end),
    highlight: Boolean(item.highlight),
    link: asString(item.link),
    name: asString(item.name),
    role: asString(item.role),
    start: asString(item.start),
  };
}

function normalizeEducationExperience(
  value: unknown,
): ResumeEducationExperience {
  const item = asRecord(value);
  return {
    degree: asString(item.degree || item.education),
    end: asString(item.end || item.educationEnd),
    experience: asString(item.experience || item.inSchoolExperience),
    major: asString(item.major),
    school: asString(item.school),
    schoolType: asString(item.schoolType),
    start: asString(item.start || item.educationStart),
  };
}

export function parseResumeProfile(content?: string): ResumeProfile {
  const empty: ResumeProfile = {
    avatar: '',
    birthMonth: '',
    domicile: '',
    educationExperiences: [],
    email: '',
    employmentStatus: '',
    expectedCities: [],
    expectedPostName: '',
    experiences: [],
    industry: '',
    jobType: '',
    mobile: '',
    name: '',
    projects: [],
    salary: '',
    skills: '',
    strengths: '',
  };
  if (!content) return empty;

  let parsed: unknown;
  try {
    parsed = JSON.parse(content);
  } catch {
    return empty;
  }
  const value = asRecord(parsed);
  if (!Object.keys(value).length) return empty;

  // 早期版本没有 experiences 数组，工作经历只有一个公司的三件套，合成一条读出来
  const legacyExperiences: ResumeWorkExperience[] =
    value.company || value.position || value.jobStart || value.workStart
      ? [
          normalizeWorkExperience({
            company: value.company,
            end: value.jobEnd,
            position: value.position,
            start: value.jobStart || value.workStart,
          }),
        ]
      : [];
  const legacyEducations: ResumeEducationExperience[] =
    value.school || value.major || value.education
      ? [normalizeEducationExperience(value)]
      : [];

  const expectedCities = Array.isArray(value.expectedCities)
    ? value.expectedCities.map(asString).filter(Boolean).slice(0, 3)
    : asString(value.city)
        .split(/[,，、]/)
        .map((city) => city.trim())
        .filter(Boolean)
        .slice(0, 3);

  return {
    avatar: asString(value.avatar),
    birthMonth: normalizeBirthMonth(value.birthMonth),
    domicile: asString(value.domicile),
    educationExperiences: Array.isArray(value.educationExperiences)
      ? value.educationExperiences.map(normalizeEducationExperience)
      : legacyEducations,
    email: asString(value.email),
    employmentStatus: asString(value.employmentStatus),
    expectedCities,
    expectedPostName: asString(value.expectedPostName || value.postTypeName),
    experiences: Array.isArray(value.experiences)
      ? value.experiences.map(normalizeWorkExperience)
      : legacyExperiences,
    industry: asString(value.industry || value.expectedIndustry),
    jobType: asString(value.jobType),
    mobile: asString(value.mobile || value.phone),
    name: asString(value.name),
    projects: Array.isArray(value.projects)
      ? value.projects.map(normalizeProjectExperience)
      : [],
    salary: asString(value.salary),
    skills: asString(value.skills),
    strengths: asString(value.strengths),
  };
}

export function jobTypeLabel(value: string) {
  return jobTypeOptions.find((item) => item.value === value)?.label || '—';
}

export function salaryLabel(value: string) {
  return salaryOptions.find((item) => item.value === value)?.label || '—';
}

export function formatPeriod(start: string, end: string) {
  if (!start && !end) return '时间待补充';
  return `${start || '—'} 至 ${end || '至今'}`;
}

/** 简历是否填过内容，用于区分「空简历」与「每一项都未填写」 */
export function hasResumeContent(profile: ResumeProfile) {
  return Boolean(
    profile.name ||
      profile.mobile ||
      profile.email ||
      profile.strengths ||
      profile.expectedPostName ||
      profile.industry ||
      profile.skills ||
      profile.experiences.length ||
      profile.projects.length ||
      profile.educationExperiences.length,
  );
}
