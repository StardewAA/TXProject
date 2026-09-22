import { requestClient } from '#/api/request';

/**
 * 个人简历版本接口。
 *
 * 版本是把在线简历草稿固化后的不可变快照，AI 分析与投递都以固定版本为输入，
 * 避免后续编辑影响历史结论与历史投递。
 */
export namespace HrmPortalResumeVersionApi {
  export interface Version {
    id: number;
    versionNo: number;
    parentVersionId?: number;
    sourceResumeId?: number;
    sourcePostId?: number;
    /** 1 手动固化，2 确认优化建议 */
    createType: number;
    changeSummary?: string;
    schemaVersion?: string;
    /** 0 有效，1 已失效 */
    status: number;
    createTime?: string;
    /** 仅单个查询返回 */
    content?: string;
  }

  export interface CreateReq {
    sourceResumeId?: number;
    parentVersionId?: number;
    sourcePostId?: number;
    createType?: number;
    changeSummary?: string;
    content?: string;
    idempotencyKey: string;
  }
}

export function getPortalResumeVersionList(sourceResumeId?: number) {
  return requestClient.request<HrmPortalResumeVersionApi.Version[]>(
    '/hrm/portal/resume-version/list',
    {
      method: 'GET',
      params: sourceResumeId ? { sourceResumeId } : undefined,
    },
  );
}

export function getPortalResumeVersion(id: number) {
  return requestClient.request<HrmPortalResumeVersionApi.Version>(
    '/hrm/portal/resume-version/get',
    { method: 'GET', params: { id } },
  );
}

export function createPortalResumeVersion(
  data: HrmPortalResumeVersionApi.CreateReq,
) {
  return requestClient.post<number>(
    '/hrm/portal/resume-version/create',
    data,
  );
}

/** 生成幂等键：同一次用户操作复用同一个键，重复提交不会生成多个版本 */
export function buildResumeVersionIdempotencyKey(seed: string) {
  const random =
    typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  return `${seed}-${random}`.slice(0, 64);
}
