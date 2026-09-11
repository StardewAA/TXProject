import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

/**
 * 云培训学院 - 基础数据字典 API
 *
 * 对应《数据字典与一级子模块关联》：
 * - 岗位工种 training_job
 * - 工种资格证 training_job_cert
 * - 学习地图 training_learning_map
 */
export namespace TrainingBaseDataApi {
  /** 岗位工种 */
  export interface Job {
    id?: number;
    /** 工种编码：OP-* 操作 / TC-* 技术 / QC-* 质检检测 / MG-* 管理 / SP-* 支持 */
    code?: string;
    /** 工种名称 */
    name?: string;
    /** 序列编码：OP/TC/QC/MG/SP */
    seqCode?: string;
    /** 序列名称 */
    seqName?: string;
    /** 业务类目（文档章节号） */
    bizCategory?: string;
    /** 学习域/课程组（原始文本，如 A,B01-B03 / 全域） */
    learnDomain?: string;
    /** 规范化学习域/课程组编码（关联课程分类 code；* 表示全域） */
    learnDomainCodes?: string;
    /** 关键技能 */
    keySkills?: string;
    /** 最高成长等级（关联成长等级字典 code） */
    maxLevel?: string;
    /** 最高成长等级名称（后端翻译） */
    maxLevelName?: string;
    sort?: number;
    /** 状态：active 启用 / disabled 停用 */
    status?: string;
    remark?: string;
    /** 资格证数量 */
    certCount?: number;
    /** 学习地图课程数 */
    mapCourseCount?: number;
    createTime?: string;
  }

  /** 工种资格证要求 */
  export interface JobCert {
    id?: number;
    /** 工种编码（关联岗位工种） */
    jobCode?: string;
    jobName?: string;
    /** 资格证名称 */
    certName?: string;
    /** 是否必须持证上岗 */
    isRequired?: boolean;
    /** 有效期（月），空表示长期有效 */
    validMonths?: number;
    sort?: number;
    remark?: string;
    createTime?: string;
  }

  /** 学习地图（工种 + 阶段 + 课程） */
  export interface LearningMap {
    id?: number;
    jobCode?: string;
    jobName?: string;
    /** 成长阶段：entry/core/advanced/technician */
    stage?: string;
    stageName?: string;
    stageNo?: number;
    /** 课程编码（关联课程分类 L3 课程级） */
    courseCode?: string;
    courseName?: string;
    /** 是否必修 */
    isRequired?: boolean;
    sort?: number;
    remark?: string;
    createTime?: string;
  }

  /** 学习地图阶段分组视图 */
  export interface LearningMapStage {
    stage?: string;
    stageName?: string;
    stageNo?: number;
    courseCount?: number;
    courses?: LearningMap[];
  }

  /** 岗位工种全景详情 */
  export interface JobDetail {
    job?: Job;
    certs?: JobCert[];
    stages?: LearningMapStage[];
  }
}

// ==================== 岗位工种 ====================

/** 岗位工种分页 */
export function getJobPage(params: PageParam & Record<string, any>) {
  return requestClient.get<PageResult<TrainingBaseDataApi.Job>>(
    '/training/job/page',
    { params },
  );
}

/** 岗位工种全量列表（下拉） */
export function getJobList() {
  return requestClient.get<TrainingBaseDataApi.Job[]>('/training/job/list');
}

/** 岗位工种详情 */
export function getJob(id: number) {
  return requestClient.get<TrainingBaseDataApi.Job>(
    `/training/job/get?id=${id}`,
  );
}

/** 岗位工种全景详情（含资格证与学习地图四阶段） */
export function getJobDetail(code: string) {
  return requestClient.get<TrainingBaseDataApi.JobDetail>(
    `/training/job/detail?code=${encodeURIComponent(code)}`,
  );
}

/** 新增岗位工种 */
export function createJob(data: Partial<TrainingBaseDataApi.Job>) {
  return requestClient.post('/training/job/create', data);
}

/** 修改岗位工种 */
export function updateJob(data: Partial<TrainingBaseDataApi.Job>) {
  return requestClient.put('/training/job/update', data);
}

/** 删除岗位工种（存在资格证/学习地图引用时后端拦截） */
export function deleteJob(id: number) {
  return requestClient.delete(`/training/job/delete?id=${id}`);
}

// ==================== 工种资格证 ====================

/** 工种资格证分页 */
export function getJobCertPage(params: PageParam & Record<string, any>) {
  return requestClient.get<PageResult<TrainingBaseDataApi.JobCert>>(
    '/training/job-cert/page',
    { params },
  );
}

/** 按工种查询资格证清单 */
export function getJobCertListByJob(jobCode: string) {
  return requestClient.get<TrainingBaseDataApi.JobCert[]>(
    `/training/job-cert/list-by-job?jobCode=${encodeURIComponent(jobCode)}`,
  );
}

/** 新增工种资格证 */
export function createJobCert(data: Partial<TrainingBaseDataApi.JobCert>) {
  return requestClient.post('/training/job-cert/create', data);
}

/** 修改工种资格证 */
export function updateJobCert(data: Partial<TrainingBaseDataApi.JobCert>) {
  return requestClient.put('/training/job-cert/update', data);
}

/** 删除工种资格证 */
export function deleteJobCert(id: number) {
  return requestClient.delete(`/training/job-cert/delete?id=${id}`);
}

// ==================== 学习地图 ====================

/** 学习地图分页 */
export function getLearningMapPage(params: PageParam & Record<string, any>) {
  return requestClient.get<PageResult<TrainingBaseDataApi.LearningMap>>(
    '/training/learning-map/page',
    { params },
  );
}

/** 按工种查询学习地图课程清单 */
export function getLearningMapListByJob(jobCode: string) {
  return requestClient.get<TrainingBaseDataApi.LearningMap[]>(
    `/training/learning-map/list-by-job?jobCode=${encodeURIComponent(jobCode)}`,
  );
}

/** 按工种查询学习地图四阶段分组视图 */
export function getLearningMapStages(jobCode: string) {
  return requestClient.get<TrainingBaseDataApi.LearningMapStage[]>(
    `/training/learning-map/stages?jobCode=${encodeURIComponent(jobCode)}`,
  );
}

/** 新增学习地图课程 */
export function createLearningMap(
  data: Partial<TrainingBaseDataApi.LearningMap>,
) {
  return requestClient.post('/training/learning-map/create', data);
}

/** 修改学习地图课程 */
export function updateLearningMap(
  data: Partial<TrainingBaseDataApi.LearningMap>,
) {
  return requestClient.put('/training/learning-map/update', data);
}

/** 删除学习地图课程 */
export function deleteLearningMap(id: number) {
  return requestClient.delete(`/training/learning-map/delete?id=${id}`);
}
