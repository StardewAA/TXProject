import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

/** 契约来源：01-接口逐一说明与设计依据 §6（6.1 主体档案 T 轨 / 6.2 平台授权与经办用户 G 轨）+ 04-全链路接口说明 §6 + 06-接口完全对照 §4；业务口径见 07-主体域前端交付-业务口径说明（企业主体 + 经办人，无个人档案/个人投标资格）。 */
export namespace SubjectApi {
  export type SubjectTypeCode = 'BIDDER' | 'TENDERER';
  export const SUBJECT_TYPE_LABEL: Record<string, string> = {
    BIDDER: '投标人',
    TENDERER: '招标人',
  };

  /** 主体档案（SubjectProfileRespVO） */
  export interface Profile {
    id?: number | string;
    subjectType?: string;
    subjectName?: string;
    uscc?: string;
    legalPersonName?: string;
    legalPersonTypeCode?: string;
    regionCode?: string;
    industryCode?: string;
    regCapital?: number;
    regCapCurrency?: string;
    contactName?: string;
    contactPhone?: string;
    licenseFileId?: number | string;
    licenseFileName?: string;
    extJson?: string;
    complete?: boolean;
    missingRequiredCodes?: string[];
    missingRequiredNames?: string[];
    completeTime?: number;
    /** 创建时间（epoch 毫秒；后端 LocalDateTime → 数字，类型兼容 number|string） */
    createTime?: number;
    updateTime?: number;
  }

  /** 资格（SubjectEligibilityRespVO） */
  export interface Eligibility {
    tenantId?: number | string;
    tenantName?: string;
    subjectType?: string;
    authorized?: boolean;
    authStatus?: 'DISABLED' | 'ENABLED' | 'NOT_AUTHORIZED';
    profileComplete?: boolean;
    eligible?: boolean;
    reasonCodes?: string[];
    missingRequiredNames?: string[];
    authorizeTime?: number;
    disableTime?: number;
    message?: string;
  }

  /** 授权行（SubjectAuthRespVO） */
  export interface AuthRow {
    id?: number | string;
    tenantId?: number | string;
    tenantName?: string;
    subjectType?: string;
    authStatus?: 'DISABLED' | 'ENABLED';
    profileExists?: boolean;
    profileComplete?: boolean;
    missingRequiredCodes?: string[];
    missingRequiredNames?: string[];
    authorizeTime?: number;
    disableTime?: number;
    remark?: string;
    /** 创建时间（epoch 毫秒；后端 LocalDateTime → 数字，类型兼容 number|string） */
    createTime?: number;
  }

  export interface AuthPageParams extends PageParam {
    tenantId?: number | string;
    subjectType?: string;
    authStatus?: string;
    tenantName?: string;
  }

  export interface AuthSave {
    tenantId: number | string;
    tenantName?: string;
    subjectType: string;
    remark?: string;
  }

  /** 经办用户（SubjectUserRespVO） */
  export interface UserRow {
    id?: number | string;
    tenantId?: number | string;
    subjectType?: string;
    userId?: number | string;
    userNickname?: string;
    userRole?: 'CONTACT' | 'OPERATOR';
    enabled?: boolean;
    remark?: string;
    /** 创建时间（epoch 毫秒；后端 LocalDateTime → 数字，类型兼容 number|string） */
    createTime?: number;
  }

  export interface UserPageParams extends PageParam {
    tenantId: number | string;
    subjectType?: string;
    userRole?: string;
    enabled?: boolean;
  }

  export interface UserBind {
    tenantId: number | string;
    subjectType: string;
    userId: number | string;
    userRole?: string;
    remark?: string;
  }
}

// ===== 企业侧档案自维护（subject/profile）=====

/** 契约：GET /bidding/subject/profile/get —— 本企业主体档案详情（含完善度/缺失必填项，未建档返回 null）；权限 bidding:subject:query；依据 04-全链路 §6.1.1 / 07 口径 §3
 * 契约《主体档案合并一租户一档》：**subjectType 可选** —— 不传=本租户唯一档案（投标/招标共用同一份）；传=按该角色核算/标记。
 *
 * ⚠️ 历史（勿复活）：后端未落地"一租户一档"时，这里带过一套**兼容重试**（先按契约不传参 → 命中「请求参数缺失:subjectType」
 *  再带 TENDERER 重试 + X-Silent-Error 静默）。后端已落地（提交 93cb6b7a2：get/eligibility 改 required=false、
 *  getOwnProfile 改查本租户唯一档案、save 改按租户 upsert）⇒ 不传参直接成功，兼容分支已整体删除：
 *  留着就是**永不命中的死代码**，而且它曾因把静默头当第三个参数传给 `get(url, config)`（该签名只收两参）而导致
 *  每次进页面先发一个注定 400 的请求、控制台刷两条假错误。
 *  核对清单：docs/电子证照前前端契约/后端变更请求-主体接口subjectType去必填-落地核对清单-2026-09-28.md §五 */
export function fetchSubjectProfile(subjectType?: string) {
  return requestClient.get<SubjectApi.Profile>('/bidding/subject/profile/get', {
    params: subjectType ? { subjectType } : {},
  });
}
/** 契约：POST /bidding/subject/profile/save —— 新建/更新本企业主体档案（USCC 按 GB32100-2015 强校验，实时核算完善度）；权限 bidding:subject:update；依据 04-全链路 §6.1.2 / 07 口径 §3、§5 */
export function saveSubjectProfile(data: Record<string, unknown>) {
  return requestClient.post<number | string>('/bidding/subject/profile/save', data);
}
/** 契约：DELETE /bidding/subject/profile/license —— 清除本租户档案的营业执照影像（license_file_id + license_file_name 置空，其余字段不动）；权限 bidding:subject:update。
 * 为什么需要专用端点（契约《后端变更请求-主体档案影像移除与更换-2026-09-23》§2 B1）：
 *   save 走 MyBatis-Plus updateById（默认 NOT_NULL 策略），传 null 不写库；而前端 numOrUndef 把空串变成 undefined
 *   后 JSON.stringify 直接丢字段 —— 「缺省=不动」与「显式清空」在同一个 save 里无法区分（实测：返回 code=0 但库里原值还在）。
 * ⇒ 清空语义只能由专用端点承担。该端点**后端待实施**，未上线前本函数会拿到 404（调用方按"未上线"如实提示，不做本地假清空）。
 * 传 X-Silent-Error 关闭全局错误红条：404 由调用方按契约口径明确提示（避免同时弹两条）；其他错误同样由调用方统一提示。 */
export function deleteSubjectLicense() {
  return requestClient.delete<boolean>('/bidding/subject/profile/license', {
    headers: { 'X-Silent-Error': 'true' },
  });
}
/** 契约：GET /bidding/subject/profile/eligibility —— 本企业资格状态（授权且档案完善双条件 + 缺失原因）；权限 bidding:subject:query；依据 04-全链路 §6.1.3 / 07 口径 §7
 * 契约《主体档案合并一租户一档》：subjectType 可选 —— 不传=按本租户唯一档案核算（投标/招标共用）；传=按该角色授权判定。
 * （兼容重试已随后端落地删除，原因见上方 fetchSubjectProfile 注释） */
export function fetchSubjectEligibility(subjectType?: string) {
  return requestClient.get<SubjectApi.Eligibility>('/bidding/subject/profile/eligibility', {
    params: subjectType ? { subjectType } : {},
  });
}

// ===== 平台侧授权管理（subject-auth）=====
/** 契约：GET /bidding/subject-auth/page —— 平台侧已授权主体分页（行内含档案完善度，识别已授权未完善）；权限 bidding:subject-auth:query；依据 04-全链路 §6.2.1 / 06 对照 §4 */
export function fetchSubjectAuthPage(params: SubjectApi.AuthPageParams) {
  return requestClient.get<PageResult<SubjectApi.AuthRow>>('/bidding/subject-auth/page', { params });
}
/** 契约：POST /bidding/subject-auth/authorize —— 平台授权企业主体（同主体重复 ENABLED 报错，停用历史可复用启用）；权限 bidding:subject-auth:update；依据 04-全链路 §6.2.2 / 07 口径 §3 */
export function authorizeSubject(data: SubjectApi.AuthSave) {
  return requestClient.post<number | string>('/bidding/subject-auth/authorize', data);
}
/** 契约：PUT /bidding/subject-auth/status —— 授权状态流转（DISABLED 停用/撤销留痕，ENABLED 重新启用）；权限 bidding:subject-auth:update；依据 04-全链路 §6.2.3 / 07 口径 §5 */
export function updateSubjectAuthStatus(id: number | string, authStatus: string) {
  return requestClient.put<boolean>('/bidding/subject-auth/status', undefined, { params: { id, authStatus } });
}
/** 契约：GET /bidding/subject-auth/eligibility —— 指定企业资格状态（平台侧任意租户查询，投标/发布前置检查用）；权限 bidding:subject-auth:query；依据 04-全链路 §6.2.4 */
export function fetchSubjectAuthEligibility(tenantId: number | string, subjectType: string) {
  return requestClient.get<SubjectApi.Eligibility>('/bidding/subject-auth/eligibility', { params: { tenantId, subjectType } });
}
/** 契约：GET /bidding/subject-auth/user/page —— 主体经办用户分页（绑定在企业主体下的自然人账号，仅查询展示）；权限 bidding:subject-auth:query；依据 04-全链路 §6.2.5 / 07 口径 §3 */
export function fetchSubjectUserPage(params: SubjectApi.UserPageParams) {
  return requestClient.get<PageResult<SubjectApi.UserRow>>('/bidding/subject-auth/user/page', { params });
}
/** 契约：POST /bidding/subject-auth/user/bind —— 绑定经办用户（AdminUser 校验 + 昵称快照，重复绑定报错）；权限 bidding:subject-auth:update；依据 04-全链路 §6.2.6 / 07 口径 §7 */
export function bindSubjectUser(data: SubjectApi.UserBind) {
  return requestClient.post<number | string>('/bidding/subject-auth/user/bind', data);
}
/** 契约：DELETE /bidding/subject-auth/user/unbind —— 解绑经办用户（逻辑删除停用留痕，不删企业档案与历史）；权限 bidding:subject-auth:update；依据 04-全链路 §6.2.7 / 07 口径 §5、§7 */
export function unbindSubjectUser(id: number | string) {
  return requestClient.delete<boolean>('/bidding/subject-auth/user/unbind', { params: { id } });
}
