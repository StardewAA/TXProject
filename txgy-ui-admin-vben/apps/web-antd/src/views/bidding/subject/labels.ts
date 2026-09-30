/* 主体域共享文案（subjectAuth「平台授权管理」+ subjectProfile「主体档案」两页此前**各写一份**，
 * 且其中一词已分叉：一处译"授权已撤销"、一处译"授权已停用"）。
 *
 * 依据（以后端为准，不靠喜好）：
 *   · 原因码值域：SubjectEligibilityRespVO.reasonCodes = NOT_AUTHORIZED / AUTH_DISABLED / PROFILE_MISSING / PROFILE_INCOMPLETE；
 *   · AUTH_DISABLED 的中文口径见后端 ErrorCodeConstants.SUBJECT_NOT_AUTHORIZED：
 *     "主体未经平台授权**或授权已停用**，无法执行该操作" ⇒ 统一为「授权已停用」；
 *   · 必须与「撤销授权」区分：撤销是 subjectAuth 页的一个**动作**（PUT /bidding/subject-auth/status），
 *     而 AUTH_DISABLED 是**状态**。把状态译成"已撤销"，会让用户以为有人刚执行过撤销动作（口径混淆）。
 */

/** 资格门拒绝原因码 → 中文（与后端 reasonCodes 值域一一对应） */
export const ELIGIBLE_REASON_LABEL: Record<string, string> = {
  NOT_AUTHORIZED: '未授权',
  AUTH_DISABLED: '授权已停用',
  PROFILE_MISSING: '档案缺失',
  PROFILE_INCOMPLETE: '档案不完善',
};

/** 原因码数组 → "未授权、档案不完善"。未登记的 code 原样透出（便于发现后端新增值域） */
export function eligibleReasonText(codes?: (null | string)[] | null): string {
  return (codes || []).map((r) => ELIGIBLE_REASON_LABEL[r as string] || r).join('、');
}
