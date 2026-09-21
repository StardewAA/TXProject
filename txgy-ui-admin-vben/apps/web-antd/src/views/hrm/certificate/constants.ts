/** 证件状态（对齐后端 HrmCertificateStatusEnum） */
export const HrmCertificateStatus = {
  VALID: 1, // 有效
  EXPIRING: 2, // 即将到期
  EXPIRED: 3, // 过期
  CANCELLED: 4, // 注销
} as const;

/** 证件状态选项 */
export const HrmCertificateStatusOptions = [
  { label: '有效', value: HrmCertificateStatus.VALID },
  { label: '即将到期', value: HrmCertificateStatus.EXPIRING },
  { label: '过期', value: HrmCertificateStatus.EXPIRED },
  { label: '注销', value: HrmCertificateStatus.CANCELLED },
] as const;

/** 证件状态对应的标签颜色 */
export const HrmCertificateStatusColor: Record<number, string> = {
  [HrmCertificateStatus.VALID]: 'success',
  [HrmCertificateStatus.EXPIRING]: 'warning',
  [HrmCertificateStatus.EXPIRED]: 'error',
  [HrmCertificateStatus.CANCELLED]: 'default',
};

/** 续证/复审类型（对齐后端 HrmCertificateRenewalTypeEnum） */
export const HrmCertificateRenewalType = {
  RENEW: 1, // 续证
  REVIEW: 2, // 复审
} as const;

/** 续证/复审类型选项 */
export const HrmCertificateRenewalTypeOptions = [
  { label: '续证', value: HrmCertificateRenewalType.RENEW },
  { label: '复审', value: HrmCertificateRenewalType.REVIEW },
] as const;

/** 证件预警类型（对齐后端 HrmCertificateWarningTypeEnum） */
export const HrmCertificateWarningType = {
  EXPIRING: 1, // 证件到期
  REVIEW: 2, // 证件复审
} as const;

/** 证件预警类型选项 */
export const HrmCertificateWarningTypeOptions = [
  { label: '证件到期', value: HrmCertificateWarningType.EXPIRING },
  { label: '证件复审', value: HrmCertificateWarningType.REVIEW },
] as const;

/** 证件预警处理状态（对齐后端 HrmCertificateWarningStatusEnum） */
export const HrmCertificateWarningStatus = {
  PENDING: 0, // 待处理
  HANDLED: 1, // 已处理
  IGNORED: 2, // 已忽略
} as const;

/** 证件预警处理状态选项 */
export const HrmCertificateWarningStatusOptions = [
  { label: '待处理', value: HrmCertificateWarningStatus.PENDING },
  { label: '已处理', value: HrmCertificateWarningStatus.HANDLED },
  { label: '已忽略', value: HrmCertificateWarningStatus.IGNORED },
] as const;

/** 证件预警处理状态对应的标签颜色 */
export const HrmCertificateWarningStatusColor: Record<number, string> = {
  [HrmCertificateWarningStatus.PENDING]: 'warning',
  [HrmCertificateWarningStatus.HANDLED]: 'success',
  [HrmCertificateWarningStatus.IGNORED]: 'default',
};

/** 证件预警通知状态（对齐后端 HrmCertificateWarningNotifyStatusEnum） */
export const HrmCertificateWarningNotifyStatus = {
  PENDING: 0, // 待通知
  SUCCESS: 1, // 已通知
  FAILED: 2, // 通知失败
} as const;

/** 证件预警通知状态对应的文案 */
export const HrmCertificateWarningNotifyStatusLabel: Record<number, string> = {
  [HrmCertificateWarningNotifyStatus.PENDING]: '待通知',
  [HrmCertificateWarningNotifyStatus.SUCCESS]: '已通知',
  [HrmCertificateWarningNotifyStatus.FAILED]: '通知失败',
};

/** 证件类型未配置提前预警天数时的兜底值（对齐后端 HrmCertificateStatusCalculator.DEFAULT_ADVANCE_DAYS） */
export const HrmCertificateDefaultAdvanceDays = 30;
