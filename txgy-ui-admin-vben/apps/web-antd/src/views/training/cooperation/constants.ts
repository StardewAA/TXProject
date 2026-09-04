/**
 * 校企合作与创新大赛（模块八，挂载于 VR 虚拟仿真实操训练菜单下）展示字典
 * 业务依据：《模块八校企合作与创新大赛初步搭建方案》81 校企合作管理 / 82 创新大赛专区
 */

// ============ 81 校企合作管理 ============

/** 合作院校审核/合作状态 */
export const SCHOOL_STATUS_OPTIONS = [
  { label: '待审核', value: 0, color: 'warning' },
  { label: '合作中', value: 1, color: 'success' },
  { label: '已停用', value: 2, color: 'default' },
  { label: '已驳回', value: 3, color: 'error' },
];

export function schoolStatusText(status?: number) {
  return SCHOOL_STATUS_OPTIONS.find((item) => item.value === status)?.label;
}

export function schoolStatusColor(status?: number) {
  return SCHOOL_STATUS_OPTIONS.find((item) => item.value === status)?.color;
}

/** 院校类别 */
export const SCHOOL_CATEGORY_OPTIONS = [
  { label: '职业本科', value: 1 },
  { label: '高职院校', value: 2 },
  { label: '技师学院', value: 3 },
  { label: '中职院校', value: 4 },
  { label: '职教集团', value: 5 },
];

export const SCHOOL_CATEGORY_TEXT: Record<number, string> = {
  1: '职业本科',
  2: '高职院校',
  3: '技师学院',
  4: '中职院校',
  5: '职教集团',
};

/** 合作模式 */
export const COOP_MODE_OPTIONS = [
  { label: '定向培养', value: 1 },
  { label: '共建专业', value: 2 },
  { label: '实习实训基地', value: 3 },
  { label: '现代学徒制', value: 4 },
  { label: '产业学院', value: 5 },
];

export const COOP_MODE_TEXT: Record<number, string> = {
  1: '定向培养',
  2: '共建专业',
  3: '实习实训基地',
  4: '现代学徒制',
  5: '产业学院',
};

/** 校企定向培训班类型 */
export const TRAIN_CLASS_TYPE_OPTIONS = [
  { label: '定向培养班', value: 1 },
  { label: '订单班', value: 2 },
  { label: '现代学徒班', value: 3 },
  { label: '实习实训班', value: 4 },
];

export const TRAIN_CLASS_TYPE_TEXT: Record<number, string> = {
  1: '定向培养班',
  2: '订单班',
  3: '现代学徒班',
  4: '实习实训班',
};

/** 校企定向培训班状态 */
export const TRAIN_CLASS_STATUS_OPTIONS = [
  { label: '筹备中', value: 0, color: 'default' },
  { label: '培训中', value: 1, color: 'processing' },
  { label: '已结业', value: 2, color: 'success' },
  { label: '已取消', value: 3, color: 'error' },
];

export function trainClassStatusText(status?: number) {
  return TRAIN_CLASS_STATUS_OPTIONS.find((item) => item.value === status)?.label;
}

export function trainClassStatusColor(status?: number) {
  return TRAIN_CLASS_STATUS_OPTIONS.find((item) => item.value === status)?.color;
}

/** 校企学员档案 - 学员阶段 */
export const STUDENT_STAGE_OPTIONS = [
  { label: '在读', value: 0 },
  { label: '应届实习', value: 1 },
  { label: '已就业', value: 2 },
];

export const STUDENT_STAGE_TEXT: Record<number, string> = {
  0: '在读',
  1: '应届实习',
  2: '已就业',
};

/** 人才输出状态 */
export const TALENT_STATUS_OPTIONS = [
  { label: '可对接', value: 0, color: 'success' },
  { label: '洽谈中', value: 1, color: 'processing' },
  { label: '已录用', value: 2, color: 'default' },
];

export function talentStatusColor(status?: number) {
  return TALENT_STATUS_OPTIONS.find((item) => item.value === status)?.color;
}

/** 职业技能等级（联合认证参考） */
export const SKILL_LEVEL_OPTIONS = [
  { label: '未定级', value: 0 },
  { label: '初级工', value: 1 },
  { label: '中级工', value: 2 },
  { label: '高级工', value: 3 },
  { label: '技师', value: 4 },
  { label: '高级技师', value: 5 },
];

export const SKILL_LEVEL_TEXT: Record<number, string> = {
  0: '未定级',
  1: '初级工',
  2: '中级工',
  3: '高级工',
  4: '技师',
  5: '高级技师',
};

// ============ 82 创新大赛专区 ============

/** 大赛状态（含报名开关联动） */
export const CONTEST_STATUS_OPTIONS = [
  { label: '草稿', value: 0, color: 'default' },
  { label: '报名中', value: 1, color: 'processing' },
  { label: '评审中', value: 2, color: 'cyan' },
  { label: '已公示', value: 3, color: 'success' },
  { label: '已结束', value: 4, color: 'default' },
  { label: '已取消', value: 5, color: 'error' },
];

export function contestStatusText(status?: number) {
  return CONTEST_STATUS_OPTIONS.find((item) => item.value === status)?.label;
}

export function contestStatusColor(status?: number) {
  return CONTEST_STATUS_OPTIONS.find((item) => item.value === status)?.color;
}

/** 大赛类型 */
export const CONTEST_TYPE_OPTIONS = [
  { label: '创新大赛', value: 1 },
  { label: '技能比武', value: 2 },
  { label: '工艺技术赛', value: 3 },
];

export const CONTEST_TYPE_TEXT: Record<number, string> = {
  1: '创新大赛',
  2: '技能比武',
  3: '工艺技术赛',
};

/** 报名/作品状态 */
export const SUBMIT_STATUS_OPTIONS = [
  { label: '已报名', value: 0, color: 'default' },
  { label: '已交作品', value: 1, color: 'processing' },
  { label: '已入围', value: 2, color: 'success' },
  { label: '未入围', value: 3, color: 'error' },
  { label: '报名已驳回', value: 4, color: 'warning' },
];

export function submitStatusColor(status?: number) {
  return SUBMIT_STATUS_OPTIONS.find((item) => item.value === status)?.color;
}

/** 获奖等级 */
export const AWARD_LEVEL_OPTIONS = [
  { label: '一等奖', value: 1 },
  { label: '二等奖', value: 2 },
  { label: '三等奖', value: 3 },
  { label: '优秀奖', value: 4 },
];

export const AWARD_LEVEL_TEXT: Record<number, string> = {
  1: '一等奖',
  2: '二等奖',
  3: '三等奖',
  4: '优秀奖',
};

/** 评审状态 */
export const REVIEW_STATUS_OPTIONS = [
  { label: '待打分', value: 0, color: 'warning' },
  { label: '已打分', value: 1, color: 'success' },
];

export const REVIEW_STATUS_TEXT: Record<number, string> = {
  0: '待打分',
  1: '已打分',
};

/** 电子证书状态 */
export const CERT_STATUS_OPTIONS = [
  { label: '待发放', value: 0, color: 'warning' },
  { label: '已发放', value: 1, color: 'success' },
];

export const CERT_STATUS_TEXT: Record<number, string> = {
  0: '待发放',
  1: '已发放',
};

/** 成果库-成果形态 */
export const WORK_TYPE_OPTIONS = [
  { label: '参赛作品', value: 1 },
  { label: '专利', value: 2 },
  { label: '论文', value: 3 },
  { label: '工艺工法', value: 4 },
];

export const WORK_TYPE_TEXT: Record<number, string> = {
  1: '参赛作品',
  2: '专利',
  3: '论文',
  4: '工艺工法',
};
