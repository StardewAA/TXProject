/**
 * 平台审查任务中心接口（菜单 60005600「平台审查中心」/ component bidding/review-center/index）
 *
 * 契约出处（待后端实现，接口 404 时页面提示"待后端实现"）：
 *   docs/电子证照前前端契约/后端变更请求-平台审查任务中心-2026-09-20.md
 *   · GET  /bidding/review-task/page    分页（租户自动限定本企业；平台跨企业可筛选）
 *   · GET  /bidding/review-task/get     详情
 *   · POST /bidding/review-task/approve 审查通过（乐观锁 version 回传）
 *   · POST /bidding/review-task/reject  审查驳回（reason 必填留痕）
 *
 * 口径：
 *   · 状态机：PENDING 待审查 → APPROVED 已通过 / REJECTED 已驳回（驳回后企业修正可重新触发新任务）；
 *   · 触发：企业录入证照/资质（建档接口）时由后端自动生成审查任务，前端无需单独创建；
 *   · 租户隔离：page 接口按当前登录租户过滤，本页仅展示本企业任务（只读）；
 *     平台（超管）通过 bidding:review:approve / bidding:review:reject 权限码看到跨企业数据并可操作；
 *   · LocalDateTime（submitTime/reviewTime）→ epoch 毫秒，展示时过 fmtDateTime；
 *   · 写接口统一 X-Silent-Error，失败由页面按 §0.4 结构自呈现；
 *   · 状态/业务类型标签走 dictLabel 优先、契约中文兜底（review_task_status / review_biz_type）。
 */
import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace ReviewTaskApi {
  /** 审查任务状态：PENDING 待审查 / APPROVED 已通过 / REJECTED 已驳回 */
  export type Status = 'APPROVED' | 'PENDING' | 'REJECTED';

  /** 审查任务分页入参：租户视角后端自动限定本企业；平台视角可按企业/类型/状态/时间筛选 */
  export interface PageReq extends PageParam {
    /** 企业名称 / 统一社会信用代码模糊（仅平台筛选有效） */
    keyword?: string;
    /** 业务类型：QUALIFICATION 企业资质 / CERTIFICATE 证照 / PERSON_CERT 员工证书 */
    bizType?: string;
    /** 任务状态：PENDING / APPROVED / REJECTED */
    status?: string;
    /** 提交（录入）时间范围起，epoch 毫秒 */
    beginTime?: number;
    /** 提交（录入）时间范围止，epoch 毫秒 */
    endTime?: number;
  }

  /** 审查任务行（**含企业名称与统一社会信用代码**，一眼区分来源企业） */
  export interface Row {
    id: number;
    /** 乐观锁：通过/驳回时必须原样回传 */
    version?: number;
    /** 业务类型：QUALIFICATION 企业资质 / CERTIFICATE 证照 / PERSON_CERT 员工证书 */
    bizType?: string;
    /** 关联业务主键（资质 id / 证照 id） */
    bizId?: number;
    /** 提交企业 id（快照） */
    entId?: number;
    /** 提交企业名称（快照） */
    entName?: string;
    /** 提交企业统一社会信用代码（快照） */
    entUscc?: string;
    /** 关联证书编号 / 资质证书编号（快照） */
    refNo?: string;
    /** 任务产生（企业录入）时间，epoch 毫秒 */
    submitTime?: number;
    /** 任务状态：PENDING / APPROVED / REJECTED */
    status?: string;
    /** 审查人（通过/驳回后回填） */
    reviewer?: string;
    /** 审查时间（通过/驳回后回填），epoch 毫秒 */
    reviewTime?: number;
    /** 驳回原因（REJECTED 必填留痕，租户可见） */
    reason?: string;
  }

  /** 审查任务详情：行字段 + 录入备注 */
  export interface Detail extends Row {
    /** 提交时的备注（如有） */
    remark?: string;
  }

  /** 审查任务统计（租户视角自动限定本企业）：一眼看出哪些已审/未审/今日新增 */
  export interface Stats {
    /** 待审查 */
    pending: number;
    /** 今日新增（含今日提交的待审查任务） */
    todayNew: number;
    /** 已通过 */
    approved: number;
    /** 已驳回 */
    rejected: number;
  }

  /** 审查动作（通过/驳回）：乐观锁 version 必填；驳回 reason 必填 */
  export interface ActionReq {
    taskId: number;
    version: number;
    /** 驳回原因（必填，将写入 reason 并展示给租户） */
    reason?: string;
  }
}

/** 写接口统一静默：错误由调用方按 §0.4 呈现（code/msg/traceId/fieldErrors） */
const SILENT = { headers: { 'X-Silent-Error': 'true' } } as const;

/* ========== 查询 ========== */
/** 审查任务统计：待审查 / 今日新增 / 已通过 / 已驳回（租户自动限定本企业） */
export function getReviewTaskStats() {
  return requestClient.get<ReviewTaskApi.Stats>('/bidding/review-task/stats');
}

/** 审查任务分页：租户自动限定本企业（只读）；平台跨企业可筛选 */
export function getReviewTaskPage(params: ReviewTaskApi.PageReq) {
  return requestClient.get<PageResult<ReviewTaskApi.Row>>('/bidding/review-task/page', { params });
}

/** 审查任务详情 */
export function getReviewTaskDetail(id: number | string) {
  return requestClient.get<ReviewTaskApi.Detail>('/bidding/review-task/get', { params: { id } });
}

/* ========== 写入（仅超管） ========== */
/** 审查通过：通过后该证照/资质方可被其他接口引用 */
export function approveReviewTask(payload: ReviewTaskApi.ActionReq) {
  return requestClient.post<boolean>('/bidding/review-task/approve', payload, SILENT);
}

/** 审查驳回：reason 必填，驳回理由展示给租户 */
export function rejectReviewTask(payload: ReviewTaskApi.ActionReq) {
  return requestClient.post<boolean>('/bidding/review-task/reject', payload, SILENT);
}