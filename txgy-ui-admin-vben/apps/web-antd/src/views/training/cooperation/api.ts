import type { PageParam, PageResult } from '@vben/request';

import { createDemoStore } from '../demo-page';

/**
 * 校企合作与创新大赛（模块八，挂载于 VR 虚拟仿真实操训练菜单 41008 下）数据层
 *
 * 说明：后端校企合作/创新大赛接口尚未交付，本文件基于 demo-page.ts 提供本地内存演示数据，
 * 页面只依赖本文件导出函数即可运行、操作、演示。后端接口就绪后，
 * 仅需将各函数实现替换为 requestClient 请求（签名与返回值保持不变），页面无需改动。
 */

export namespace TrainingCooperationApi {
  /** 合作院校 */
  export interface School {
    id?: number;
    code?: string;
    name?: string;
    /** 院校类别：1 职业本科 2 高职 3 技师学院 4 中职 5 职教集团 */
    category?: number;
    /** 合作模式：1 定向培养 2 共建专业 3 实习实训基地 4 现代学徒制 5 产业学院 */
    coopMode?: number;
    province?: string;
    city?: string;
    /** 合作层级/标签，如 双高院校/省示范 */
    levelTag?: string;
    contactPerson?: string;
    contactMobile?: string;
    studentCount?: number;
    openClassCount?: number;
    /** 0 待审核 1 合作中 2 已停用 3 已驳回 */
    status?: number;
    applyTime?: string;
    auditTime?: string;
    remark?: string;
    createTime?: string;
  }

  /** 校企定向培训班 */
  export interface TrainClass {
    id?: number;
    code?: string;
    name?: string;
    schoolId?: number;
    schoolName?: string;
    /** 1 定向培养班 2 订单班 3 现代学徒班 4 实习实训班 */
    type?: number;
    coursePackage?: string;
    courseCount?: number;
    studentCount?: number;
    headTeacher?: string;
    planStart?: string;
    planEnd?: string;
    /** 0 筹备中 1 培训中 2 已结业 3 已取消 */
    status?: number;
    remark?: string;
    createTime?: string;
  }

  /** 校企学员培训档案（档案互通，脱敏展示） */
  export interface Archive {
    id?: number;
    archiveNo?: string;
    studentNo?: string;
    studentName?: string;
    gender?: string;
    schoolId?: number;
    schoolName?: string;
    major?: string;
    /** 0 在读 1 应届实习 2 已就业 */
    stage?: number;
    /** 技能等级：0 未定级 1 初级工 ... 5 高级技师 */
    skillLevel?: number;
    trainedHours?: number;
    courseCount?: number;
    /** VR 实操正式考核是否达标 */
    vrQualified?: boolean;
    certNames?: string;
    /** 手机号脱敏后展示 */
    mobileMasked?: string;
    archivedTime?: string;
    updateTime?: string;
  }

  /** 院校人才输出（人才库） */
  export interface Talent {
    id?: number;
    studentNo?: string;
    studentName?: string;
    schoolId?: number;
    schoolName?: string;
    major?: string;
    /** 技能等级：0 未定级 1 初级工 ... 5 高级技师 */
    skillLevel?: number;
    trainedCourseCount?: number;
    certNames?: string;
    /** 综合能力评分（人才画像用，脱敏导出） */
    score?: number;
    /** 0 可对接 1 洽谈中 2 已录用 */
    status?: number;
    expectPost?: string;
    updateTime?: string;
  }

  /** 创新大赛 */
  export interface Contest {
    id?: number;
    code?: string;
    name?: string;
    /** 1 创新大赛 2 技能比武 3 工艺技术赛 */
    type?: number;
    theme?: string;
    orgName?: string;
    contestDate?: string;
    signStart?: string;
    signEnd?: string;
    /** 参赛对象范围说明 */
    targetRange?: string;
    judges?: string;
    /** 0 草稿 1 报名中 2 评审中 3 已公示 4 已结束 5 已取消 */
    status?: number;
    note?: string;
    createTime?: string;
  }

  /** 报名与作品提交 */
  export interface Submission {
    id?: number;
    contestId?: number;
    contestName?: string;
    studentNo?: string;
    studentName?: string;
    schoolName?: string;
    isTeam?: boolean;
    teamName?: string;
    workTitle?: string;
    workType?: string;
    workDesc?: string;
    /** 0 已报名 1 已交作品 2 已入围 3 未入围 4 报名已驳回 */
    status?: number;
    submitTime?: string;
    auditTime?: string;
  }

  /** 评审·公示与奖励（获奖公示条目） */
  export interface Award {
    id?: number;
    contestId?: number;
    contestName?: string;
    workTitle?: string;
    studentName?: string;
    schoolName?: string;
    avgScore?: number;
    /** 1 一等奖 2 二等奖 3 三等奖 4 优秀奖 */
    level?: number;
    judgeDetail?: string;
    /** 0 未公示 1 已公示 */
    published?: number;
    /** 电子证书：0 待发放 1 已发放 */
    certStatus?: number;
    updateTime?: string;
  }

  /** 大赛成果库 */
  export interface Work {
    id?: number;
    title?: string;
    /** 1 参赛作品 2 专利 3 论文 4 工艺工法 */
    type?: number;
    contestName?: string;
    owner?: string;
    schoolName?: string;
    awardLevel?: number;
    year?: string;
    views?: number;
    summary?: string;
    sizeText?: string;
    archivedTime?: string;
  }
}

// ============ 81.1 合作院校管理 ============

const SCHOOL_SEED: TrainingCooperationApi.School[] = [
  {
    id: 1, code: 'SDHG', name: '山东化工职业学院', category: 2, coopMode: 3, province: '山东', city: '潍坊',
    levelTag: '双高院校', contactPerson: '王建军', contactMobile: '13806360001', studentCount: 126, openClassCount: 4,
    status: 1, applyTime: '2026-03-10 10:00:00', auditTime: '2026-03-12 14:30:00',
    remark: '共建防腐保温实习实训基地，每年输送 2 期定向班', createTime: '2026-03-10 10:00:00',
  },
  {
    id: 2, code: 'LZSH', name: '兰州石化职业技术大学', category: 1, coopMode: 4, province: '甘肃', city: '兰州',
    levelTag: '职业本科', contactPerson: '马丽', contactMobile: '13909410002', studentCount: 88, openClassCount: 3,
    status: 1, applyTime: '2026-03-15 09:00:00', auditTime: '2026-03-18 11:00:00',
    remark: '现代学徒制试点院校（材料防腐与防护专业）', createTime: '2026-03-15 09:00:00',
  },
  {
    id: 3, code: 'SCXH', name: '四川化工职业技术学院', category: 2, coopMode: 1, province: '四川', city: '泸州',
    levelTag: '省示范', contactPerson: '张利民', contactMobile: '13908130003', studentCount: 0, openClassCount: 0,
    status: 0, applyTime: '2026-08-20 15:20:00',
    remark: '拟共建定向培养班：管道防腐专业 30 人', createTime: '2026-08-20 15:20:00',
  },
  {
    id: 4, code: 'HRB', name: '哈尔滨职业技术学院', category: 2, coopMode: 2, province: '黑龙江', city: '哈尔滨',
    levelTag: '省示范', contactPerson: '刘洋', contactMobile: '13604510004', studentCount: 45, openClassCount: 1,
    status: 2, applyTime: '2025-11-02 10:00:00', auditTime: '2025-11-05 16:00:00',
    remark: '2026 年度合作到期，暂停新任务下发', createTime: '2025-11-02 10:00:00',
  },
  {
    id: 5, code: 'HBJC', name: '河北建材职业技术学院', category: 2, coopMode: 5, province: '河北', city: '秦皇岛',
    levelTag: '双高院校', contactPerson: '赵雪梅', contactMobile: '13933500005', studentCount: 210, openClassCount: 6,
    status: 1, applyTime: '2026-05-12 09:30:00', auditTime: '2026-05-14 10:00:00',
    remark: '产业学院共建：保温工程与防腐技术方向', createTime: '2026-05-12 09:30:00',
  },
  {
    id: 6, code: 'SDNY', name: '山东能源职业学院', category: 2, coopMode: 1, province: '山东', city: '济宁',
    levelTag: '', contactPerson: '孙倩', contactMobile: '13705370006', studentCount: 0, openClassCount: 0,
    status: 3, applyTime: '2026-07-01 14:00:00', auditTime: '2026-07-03 09:00:00',
    remark: '专业方向与防腐保温行业不符，建议后续共建专业后再申请', createTime: '2026-07-01 14:00:00',
  },
];

const schoolStore = createDemoStore(SCHOOL_SEED);

function matchSchool(row: TrainingCooperationApi.School, form: Record<string, any>) {
  if (form.name && !(row.name ?? '').includes(form.name)) return false;
  if (form.category != null && form.category !== '' && row.category !== Number(form.category)) return false;
  if (form.status != null && form.status !== '' && row.status !== Number(form.status)) return false;
  return true;
}

export async function getSchoolPage(params: PageParam & Record<string, any>) {
  return schoolStore.page(params, matchSchool) as PageResult<TrainingCooperationApi.School>;
}

export async function createSchool(data: Partial<TrainingCooperationApi.School>) {
  return schoolStore.add({ ...data, status: 0, applyTime: new Date().toISOString().slice(0, 19).replace('T', ' ') });
}

export async function updateSchool(data: Partial<TrainingCooperationApi.School>) {
  schoolStore.update(data as TrainingCooperationApi.School);
  return true;
}

export async function deleteSchool(id: number) {
  schoolStore.remove(id);
  return true;
}

/** 入驻审核：pass=true 通过(1)，false 驳回(3) */
export async function auditSchool(id: number, pass: boolean) {
  schoolStore.patch(id, {
    status: pass ? 1 : 3,
    auditTime: new Date().toISOString().slice(0, 19).replace('T', ' '),
  });
  return true;
}

/** 合作启停：合作中(1) 与 已停用(2) 间切换 */
export async function toggleSchoolStatus(id: number) {
  const target = schoolStore.list().find((item) => item.id === id);
  schoolStore.patch(id, { status: target?.status === 2 ? 1 : 2 });
  return true;
}

// ============ 81.2 校企定向培训班管理 ============

const TRAIN_CLASS_SEED: TrainingCooperationApi.TrainClass[] = [
  {
    id: 1, code: 'TD2026-SDHG-01', name: '防腐保温定向培养班（2026 届）', schoolId: 1, schoolName: '山东化工职业学院',
    type: 1, coursePackage: '防腐保温基础 + 喷砂除锈实操 + 保温层施工', courseCount: 12, studentCount: 38,
    headTeacher: '于老师', planStart: '2026-04-01', planEnd: '2026-09-30', status: 1,
    remark: '毕业直通入职，考核合格发放联合认证报名资格', createTime: '2026-03-20 10:00:00',
  },
  {
    id: 2, code: 'DD2026-LZSH-02', name: '金属腐蚀检测订单班', schoolId: 2, schoolName: '兰州石化职业技术大学',
    type: 2, coursePackage: '腐蚀检测技术 + 标准与法规 + 质检案例', courseCount: 8, studentCount: 26,
    headTeacher: '陈老师', planStart: '2026-05-10', planEnd: '2026-10-20', status: 1,
    remark: '', createTime: '2026-05-01 09:00:00',
  },
  {
    id: 3, code: 'SX2026-HBJC-03', name: '保温施工现代学徒班（第 2 期）', schoolId: 5, schoolName: '河北建材职业技术学院',
    type: 3, coursePackage: '保温材料认知 + 管道保温工艺 + 现场安全', courseCount: 15, studentCount: 42,
    headTeacher: '郑老师', planStart: '2026-08-01', planEnd: '2027-01-31', status: 0,
    remark: '筹备中，待配置 VR 实训任务', createTime: '2026-08-15 14:00:00',
  },
  {
    id: 4, code: 'SX2025-HRB-01', name: '防腐涂层施工实习实训班', schoolId: 4, schoolName: '哈尔滨职业技术学院',
    type: 4, coursePackage: '涂层施工工艺 + 安全操作规程', courseCount: 6, studentCount: 45,
    headTeacher: '周老师', planStart: '2025-12-01', planEnd: '2026-04-30', status: 2,
    remark: '已结业，档案归档', createTime: '2025-11-20 10:00:00',
  },
];

const trainClassStore = createDemoStore(TRAIN_CLASS_SEED);

function matchTrainClass(row: TrainingCooperationApi.TrainClass, form: Record<string, any>) {
  if (form.name && !(row.name ?? '').includes(form.name)) return false;
  if (form.schoolName && !(row.schoolName ?? '').includes(form.schoolName)) return false;
  if (form.type != null && form.type !== '' && row.type !== Number(form.type)) return false;
  if (form.status != null && form.status !== '' && row.status !== Number(form.status)) return false;
  return true;
}

export async function getTrainClassPage(params: PageParam & Record<string, any>) {
  return trainClassStore.page(params, matchTrainClass) as PageResult<TrainingCooperationApi.TrainClass>;
}

export async function createTrainClass(data: Partial<TrainingCooperationApi.TrainClass>) {
  return trainClassStore.add({ ...data, status: 0 });
}

export async function updateTrainClass(data: Partial<TrainingCooperationApi.TrainClass>) {
  trainClassStore.update(data as TrainingCooperationApi.TrainClass);
  return true;
}

export async function deleteTrainClass(id: number) {
  trainClassStore.remove(id);
  return true;
}

/** 开班（筹备 → 培训中），同时给学员下发课程包/VR 实操任务 */
export async function startTrainClass(id: number) {
  trainClassStore.patch(id, { status: 1 });
  return true;
}

/** 结业（培训中 → 已结业） */
export async function finishTrainClass(id: number) {
  trainClassStore.patch(id, { status: 2 });
  return true;
}

/** 取消（非结业状态 → 已取消） */
export async function cancelTrainClass(id: number) {
  trainClassStore.patch(id, { status: 3 });
  return true;
}

// ============ 81.3 校企学员档案互通 ============

const ARCHIVE_SEED: TrainingCooperationApi.Archive[] = [
  {
    id: 1, archiveNo: 'DA2026-0001', studentNo: '2022020118', studentName: '李明远', gender: '男',
    schoolId: 1, schoolName: '山东化工职业学院', major: '材料防腐技术',     stage: 1, skillLevel: 1,
    trainedHours: 420, courseCount: 12, vrQualified: true, certNames: '初级工(防腐蚀工)、联合认证(基础级)',
    mobileMasked: '138****2108', archivedTime: '2026-08-30 10:00:00', updateTime: '2026-08-30 10:00:00',
  },
  {
    id: 2, archiveNo: 'DA2026-0002', studentNo: '2021030442', studentName: '王梓涵', gender: '女',
    schoolId: 1, schoolName: '山东化工职业学院', major: '材料防腐技术', stage: 2, skillLevel: 2,
    trainedHours: 560, courseCount: 15, vrQualified: true, certNames: '中级工(防腐蚀工)、安全员证',
    mobileMasked: '159****6623', archivedTime: '2026-07-12 15:30:00', updateTime: '2026-07-12 15:30:00',
  },
  {
    id: 3, archiveNo: 'DA2026-0003', studentNo: '2022030917', studentName: '赵一凡', gender: '男',
    schoolId: 2, schoolName: '兰州石化职业技术大学', major: '腐蚀与防护', stage: 1, skillLevel: 0,
    trainedHours: 380, courseCount: 8, vrQualified: false, certNames: '未定级',
    mobileMasked: '137****0091', archivedTime: '2026-08-25 09:20:00', updateTime: '2026-08-25 09:20:00',
  },
  {
    id: 4, archiveNo: 'DA2026-0004', studentNo: '2021030588', studentName: '孙悦', gender: '女',
    schoolId: 2, schoolName: '兰州石化职业技术大学', major: '材料成型与控制技术', stage: 2, skillLevel: 2,
    trainedHours: 460, courseCount: 11, vrQualified: true, certNames: '中级工(防腐保温工)',
    mobileMasked: '158****3305', archivedTime: '2026-06-18 11:00:00', updateTime: '2026-06-18 11:00:00',
  },
  {
    id: 5, archiveNo: 'DA2026-0005', studentNo: '2022040772', studentName: '周正阳', gender: '男',
    schoolId: 5, schoolName: '河北建材职业技术学院', major: '建筑工程技术', stage: 0, skillLevel: 0,
    trainedHours: 210, courseCount: 5, vrQualified: false, certNames: '',
    mobileMasked: '186****5520', archivedTime: '2026-09-01 16:40:00', updateTime: '2026-09-01 16:40:00',
  },
];

const archiveStore = createDemoStore(ARCHIVE_SEED);

function matchArchive(row: TrainingCooperationApi.Archive, form: Record<string, any>) {
  if (form.studentName && !(row.studentName ?? '').includes(form.studentName)) return false;
  if (form.schoolName && !(row.schoolName ?? '').includes(form.schoolName)) return false;
  if (form.stage != null && form.stage !== '' && row.stage !== Number(form.stage)) return false;
  return true;
}

export async function getArchivePage(params: PageParam & Record<string, any>) {
  return archiveStore.page(params, matchArchive) as PageResult<TrainingCooperationApi.Archive>;
}

export async function getArchiveDetail(id: number) {
  return archiveStore.list().find((item) => item.id === id);
}

// ============ 81.4 校企人才输出（院校人才库） ============

const TALENT_SEED: TrainingCooperationApi.Talent[] = [
  {
    id: 1, studentNo: '2022020118', studentName: '李明远', schoolId: 1, schoolName: '山东化工职业学院',
    major: '材料防腐技术', skillLevel: 1, trainedCourseCount: 12, certNames: '防腐蚀工初级',
    score: 86, status: 0, expectPost: '防腐施工员', updateTime: '2026-08-30 10:00:00',
  },
  {
    id: 2, studentNo: '2021030442', studentName: '王梓涵', schoolId: 1, schoolName: '山东化工职业学院',
    major: '材料防腐技术', skillLevel: 2, trainedCourseCount: 15, certNames: '防腐蚀工中级、安全员证',
    score: 92, status: 1, expectPost: '质检技术员', updateTime: '2026-08-02 09:00:00',
  },
  {
    id: 3, studentNo: '2022030917', studentName: '赵一凡', schoolId: 2, schoolName: '兰州石化职业技术大学',
    major: '腐蚀与防护', skillLevel: 0, trainedCourseCount: 8, certNames: '',
    score: 74, status: 0, expectPost: '检测助理', updateTime: '2026-08-25 09:20:00',
  },
  {
    id: 4, studentNo: '2021030588', studentName: '孙悦', schoolId: 2, schoolName: '兰州石化职业技术大学',
    major: '材料成型与控制技术', skillLevel: 2, trainedCourseCount: 11, certNames: '防腐保温工中级',
    score: 89, status: 2, expectPost: '工艺技术员', updateTime: '2026-06-20 14:00:00',
  },
  {
    id: 5, studentNo: '2022040772', studentName: '周正阳', schoolId: 5, schoolName: '河北建材职业技术学院',
    major: '建筑工程技术', skillLevel: 0, trainedCourseCount: 5, certNames: '',
    score: 68, status: 0, expectPost: '保温施工员（实习）', updateTime: '2026-09-01 16:40:00',
  },
];

const talentStore = createDemoStore(TALENT_SEED);

function matchTalent(row: TrainingCooperationApi.Talent, form: Record<string, any>) {
  if (form.studentName && !(row.studentName ?? '').includes(form.studentName)) return false;
  if (form.schoolName && !(row.schoolName ?? '').includes(form.schoolName)) return false;
  if (form.skillLevel != null && form.skillLevel !== '' && row.skillLevel !== Number(form.skillLevel)) return false;
  if (form.status != null && form.status !== '' && row.status !== Number(form.status)) return false;
  return true;
}

export async function getTalentPage(params: PageParam & Record<string, any>) {
  return talentStore.page(params, matchTalent) as PageResult<TrainingCooperationApi.Talent>;
}

/** 人才对接状态流转：可对接(0) → 洽谈中(1) → 已录用(2)，已录用可退回洽谈 */
export async function updateTalentStatus(id: number, status: number) {
  talentStore.patch(id, {
    status,
    updateTime: new Date().toISOString().slice(0, 19).replace('T', ' '),
  });
  return true;
}

// ============ 82.1 大赛基础管理 ============

const CONTEST_SEED: TrainingCooperationApi.Contest[] = [
  {
    id: 1, code: 'DS2026-001', name: '全国防腐保温施工技术创新大赛 2026', type: 1, orgName: '云培训学院大赛组委会',
    theme: '保温层预制安装工艺创新', contestDate: '2026-10-18', signStart: '2026-09-01', signEnd: '2026-09-30',
    targetRange: '合作院校在校生与企业在职人员，个人/团队均可', judges: '韩工、陈教授、刘工',
    status: 1, note: '报名进行中，同步开启 VR 实操作品通道', createTime: '2026-08-28 10:00:00',
  },
  {
    id: 2, code: 'DS2026-002', name: '喷砂除锈安全作业技能比武', type: 2, orgName: '山东化工职业学院校企联合办',
    theme: '高危作业规范与速度', contestDate: '2026-11-06', signStart: '2026-10-08', signEnd: '2026-10-25',
    targetRange: '企业一线技工、学徒（限额 120 人）', judges: '王工、李工',
    status: 0, note: '', createTime: '2026-09-01 09:00:00',
  },
  {
    id: 3, code: 'DS2025-018', name: '防腐保温质检技术大比武 2025', type: 3, orgName: '河北建材职业技术学院',
    theme: '补口补伤质量缺陷识别', contestDate: '2025-12-20', signStart: '2025-11-01', signEnd: '2025-11-30',
    targetRange: '合作院校在校生', judges: '赵工、孙教授、钱工',
    status: 4, note: '已结束，成果归档至成果库', createTime: '2025-10-20 15:00:00',
  },
  {
    id: 4, code: 'DS2026-003', name: '管道保温施工工艺创新大赛（预热）', type: 1, orgName: '云培训学院大赛组委会',
    theme: '新材料、新工艺应用', contestDate: '2026-12-12', signStart: '2026-11-01', signEnd: '2026-11-20',
    targetRange: '合作院校在校生', judges: '',
    status: 0, note: '草稿状态，可编辑基础信息', createTime: '2026-09-02 11:00:00',
  },
];

const contestStore = createDemoStore(CONTEST_SEED);

function matchContest(row: TrainingCooperationApi.Contest, form: Record<string, any>) {
  if (form.name && !(row.name ?? '').includes(form.name)) return false;
  if (form.type != null && form.type !== '' && row.type !== Number(form.type)) return false;
  if (form.status != null && form.status !== '' && row.status !== Number(form.status)) return false;
  return true;
}

export async function getContestPage(params: PageParam & Record<string, any>) {
  return contestStore.page(params, matchContest) as PageResult<TrainingCooperationApi.Contest>;
}

export async function createContest(data: Partial<TrainingCooperationApi.Contest>) {
  return contestStore.add({ ...data, status: 0 });
}

export async function updateContest(data: Partial<TrainingCooperationApi.Contest>) {
  contestStore.update(data as TrainingCooperationApi.Contest);
  return true;
}

export async function deleteContest(id: number) {
  contestStore.remove(id);
  return true;
}

/**
 * 大赛状态流转：
 * - start  草稿(0) → 报名中(1)（开启报名开关）
 * - signOff 报名中(1) → 评审中(2)（关闭报名）
 * - publish 评审中(2) → 已公示(3)（自动生成获奖名单）
 * - finish 已公示(3) → 已结束(4)
 * - cancel 任意非终态 → 已取消(5)
 */
export async function transitionContest(id: number, action: string) {
  const target = contestStore.list().find((item) => item.id === id);
  if (!target) return false;
  const map: Record<string, number> = {
    start: 1,
    signOff: 2,
    publish: 3,
    finish: 4,
    cancel: 5,
  };
  const next = map[action];
  if (next == null) return false;
  contestStore.patch(id, { status: next });
  return true;
}

// ============ 82.2 报名与作品提交 ============

const SUBMISSION_SEED: TrainingCooperationApi.Submission[] = [
  {
    id: 1, contestId: 1, contestName: '全国防腐保温施工技术创新大赛 2026', studentNo: '2022020118',
    studentName: '李明远', schoolName: '山东化工职业学院', isTeam: false, teamName: '',
    workTitle: '保温层预制安装升降式作业平台', workType: '创新设计', workDesc: '减少高处作业风险，提升预制安装效率 30%。',
    status: 2, submitTime: '2026-09-05 10:20:00', auditTime: '2026-09-08 14:00:00',
  },
  {
    id: 2, contestId: 1, contestName: '全国防腐保温施工技术创新大赛 2026', studentNo: '2021030442',
    studentName: '王梓涵', schoolName: '山东化工职业学院', isTeam: true, teamName: '尖峰质检队',
    workTitle: '补口防腐质量缺陷 AI 视觉识别方案', workType: '工艺方案', workDesc: '基于机器视觉的补口缺陷自动识别。',
    status: 1, submitTime: '2026-09-06 15:30:00',
  },
  {
    id: 3, contestId: 1, contestName: '全国防腐保温施工技术创新大赛 2026', studentNo: '2022030917',
    studentName: '赵一凡', schoolName: '兰州石化职业技术大学', isTeam: false, teamName: '',
    workTitle: '低温环境保温层防潮施工方法', workType: '工艺方案', workDesc: '解决低温环境下防潮层施工难题。',
    status: 0, submitTime: '2026-09-07 09:00:00',
  },
  {
    id: 4, contestId: 3, contestName: '防腐保温质检技术大比武 2025', studentNo: '2021030588',
    studentName: '孙悦', schoolName: '兰州石化职业技术大学', isTeam: false, teamName: '',
    workTitle: '补口补伤缺陷识别实操', workType: '实操作品', workDesc: '现场实操记录与缺陷识别报告。',
    status: 3, submitTime: '2025-11-10 16:00:00', auditTime: '2025-11-15 10:00:00',
  },
];

const submissionStore = createDemoStore(SUBMISSION_SEED);

function matchSubmission(row: TrainingCooperationApi.Submission, form: Record<string, any>) {
  if (form.contestId != null && form.contestId !== '' && row.contestId !== Number(form.contestId)) return false;
  if (form.contestName && !(row.contestName ?? '').includes(form.contestName)) return false;
  if (form.studentName && !(row.studentName ?? '').includes(form.studentName)) return false;
  if (form.status != null && form.status !== '' && row.status !== Number(form.status)) return false;
  return true;
}

export async function getSubmissionPage(params: PageParam & Record<string, any>) {
  return submissionStore.page(params, matchSubmission) as PageResult<TrainingCooperationApi.Submission>;
}

export async function getSubmissionDetail(id: number) {
  return submissionStore.list().find((item) => item.id === id);
}

/** 报名审核：pass=true 确认入围(2)；false 未入围(3) */
export async function auditSubmission(id: number, pass: boolean) {
  submissionStore.patch(id, {
    status: pass ? 2 : 3,
    auditTime: new Date().toISOString().slice(0, 19).replace('T', ' '),
  });
  return true;
}

/** 驳回报名（报名信息不合格，状态 4） */
export async function rejectSubmission(id: number) {
  submissionStore.patch(id, {
    status: 4,
    auditTime: new Date().toISOString().slice(0, 19).replace('T', ' '),
  });
  return true;
}

// ============ 82.3 评审 / 82.4 公示与奖励 ============

const AWARD_SEED: TrainingCooperationApi.Award[] = [
  {
    id: 1, contestId: 3, contestName: '防腐保温质检技术大比武 2025', workTitle: '补口补伤缺陷识别实操',
    studentName: '孙悦', schoolName: '兰州石化职业技术大学', avgScore: 93.5, level: 1,
    judgeDetail: '赵工 95 / 孙教授 92 / 钱工 93.5', published: 1, certStatus: 1,
    updateTime: '2026-01-10 10:00:00',
  },
  {
    id: 2, contestId: 3, contestName: '防腐保温质检技术大比武 2025', workTitle: '涂层厚度检测实操',
    studentName: '吴海', schoolName: '山东化工职业学院', avgScore: 88.2, level: 2,
    judgeDetail: '赵工 87 / 孙教授 89.5 / 钱工 88', published: 1, certStatus: 1,
    updateTime: '2026-01-10 10:00:00',
  },
  {
    id: 3, contestId: 1, contestName: '全国防腐保温施工技术创新大赛 2026', workTitle: '保温层预制安装升降式作业平台',
    studentName: '李明远', schoolName: '山东化工职业学院', avgScore: 90.6, level: 2,
    judgeDetail: '韩工 91 / 陈教授 90 / 刘工 90.8', published: 0, certStatus: 0,
    updateTime: '2026-09-10 09:00:00',
  },
];

const awardStore = createDemoStore(AWARD_SEED);

function matchAward(row: TrainingCooperationApi.Award, form: Record<string, any>) {
  if (form.contestName && !(row.contestName ?? '').includes(form.contestName)) return false;
  if (form.studentName && !(row.studentName ?? '').includes(form.studentName)) return false;
  if (form.published != null && form.published !== '' && row.published !== Number(form.published)) return false;
  return true;
}

export async function getAwardPage(params: PageParam & Record<string, any>) {
  return awardStore.page(params, matchAward) as PageResult<TrainingCooperationApi.Award>;
}

/** 登记评审分数（评审留痕，demo 保存均分与评委明细） */
export async function saveAwardScore(id: number, data: { avgScore: number; judgeDetail: string; level: number; }) {
  awardStore.patch(id, data);
  return true;
}

/** 公示获奖名单（不可撤回） */
export async function publishAward(id: number) {
  awardStore.patch(id, { published: 1 });
  return true;
}

/** 发放电子证书 */
export async function issueAwardCert(id: number) {
  awardStore.patch(id, { certStatus: 1 });
  return true;
}

/** 删除（草拟评审数据可删，已公示不可删） */
export async function deleteAward(id: number) {
  const target = awardStore.list().find((item) => item.id === id);
  if (target?.published === 1) {
    return false;
  }
  awardStore.remove(id);
  return true;
}

// ============ 82.5 大赛成果库 ============

const WORK_SEED: TrainingCooperationApi.Work[] = [
  {
    id: 1, title: '保温层预制安装升降式作业平台', type: 1, contestName: '全国防腐保温施工技术创新大赛 2026',
    owner: '李明远', schoolName: '山东化工职业学院', awardLevel: 2, year: '2026',
    views: 328, summary: '通过升降式平台实现预制安装一次到位，减少高处作业风险，安装效率提升 30%。',
    sizeText: '6.8 MB', archivedTime: '2026-09-12 10:00:00',
  },
  {
    id: 2, title: '补口防腐质量缺陷 AI 视觉识别方案', type: 1, contestName: '全国防腐保温施工技术创新大赛 2026',
    owner: '王梓涵（尖峰质检队）', schoolName: '山东化工职业学院', awardLevel: 3, year: '2026',
    views: 216, summary: '基于机器视觉的补口缺陷自动识别，识别准确率 91%。',
    sizeText: '12.4 MB', archivedTime: '2026-09-12 10:00:00',
  },
  {
    id: 3, title: '低温环境保温层防潮施工方法', type: 4, contestName: '防腐保温质检技术大比武 2025',
    owner: '孙悦', schoolName: '兰州石化职业技术大学', awardLevel: 1, year: '2025',
    views: 452, summary: '提出低温环境防潮层施工工艺参数，已在西北项目试应用。',
    sizeText: '3.2 MB', archivedTime: '2026-01-15 09:00:00',
  },
  {
    id: 4, title: '一种可调式保温管支撑装置', type: 2, contestName: '',
    owner: '河北建材职业技术学院-陈宇', schoolName: '河北建材职业技术学院', awardLevel: 1, year: '2026',
    views: 180, summary: '实用新型专利（已受理），用于管道保温施工快速支撑定位。',
    sizeText: '1.1 MB', archivedTime: '2026-07-20 11:00:00',
  },
];

const workStore = createDemoStore(WORK_SEED);

function matchWork(row: TrainingCooperationApi.Work, form: Record<string, any>) {
  if (form.title && !(row.title ?? '').includes(form.title)) return false;
  if (form.schoolName && !(row.schoolName ?? '').includes(form.schoolName)) return false;
  if (form.type != null && form.type !== '' && row.type !== Number(form.type)) return false;
  return true;
}

export async function getWorkPage(params: PageParam & Record<string, any>) {
  return workStore.page(params, matchWork) as PageResult<TrainingCooperationApi.Work>;
}

export async function getWorkDetail(id: number) {
  return workStore.list().find((item) => item.id === id);
}
