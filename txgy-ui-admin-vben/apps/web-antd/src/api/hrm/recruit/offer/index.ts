import { requestClient } from '#/api/request';

export namespace HrmRecruitOfferApi {
  export interface SaveReq {
    applicationId: number;
    postId: number;
    salaryAmount?: number;
    salaryUnit?: number;
    plannedOnboardDate?: string;
    validUntil?: string;
    remark?: string;
  }

  export interface Offer {
    id: number;
    applicationId: number;
    offerNo?: string;
    approvalStatus?: string;
    salaryAmount?: number;
    salaryUnit?: number;
    postId?: number;
    plannedOnboardDate?: string;
    validUntil?: string;
    processInstanceId?: string;
    approvalVersion?: number;
    employeeId?: number;
    entryPostId?: number;
    contractId?: number;
    sentTime?: string;
    confirmTime?: string;
    remark?: string;
  }
}

export function createRecruitOffer(data: HrmRecruitOfferApi.SaveReq) {
  return requestClient.post<number>('/hrm/recruit/offer/create', data);
}

export function submitRecruitOffer(id: number) {
  return requestClient.put<boolean>(`/hrm/recruit/offer/submit?id=${id}`);
}

/** Offer 人工审批（通过 / 驳回） */
export function approveRecruitOffer(
  id: number,
  approved: boolean,
  reason?: string,
) {
  return requestClient.put<boolean>('/hrm/recruit/offer/approve', null, {
    params: { id, approved, reason },
  });
}

export function getRecruitOffer(id: number) {
  return requestClient.get<HrmRecruitOfferApi.Offer>('/hrm/recruit/offer/get', {
    params: { id },
  });
}

export function sendRecruitOffer(id: number) {
  return requestClient.put<boolean>(`/hrm/recruit/offer/send?id=${id}`);
}

export function withdrawRecruitOffer(id: number, reason?: string) {
  return requestClient.put<boolean>('/hrm/recruit/offer/withdraw', undefined, {
    params: { id, reason },
  });
}

/** Offer 确认入职（转为员工：自动建档、生成工号、绑定并生成劳务合同、通知员工签署） */
export function onboardRecruitOffer(data: {
  offerId: number;
  type: number;
  entryTime: number;
  /** 用工形式编号（hrm_employment_type.id），必填：决定合同模板与计薪方式 */
  employmentTypeId: number;
  entryStatus?: number;
  status?: number;
}) {
  return requestClient.put<number>('/hrm/recruit/offer/onboard', data);
}

/** 重新生成劳务合同文件并再次发送待签署通知 */
export function resendContractNotice(offerId: number) {
  return requestClient.put<boolean>('/hrm/recruit/offer/resend-contract-notice', null, {
    params: { offerId },
  });
}

/** 重新发送 Offer 录用通知邮件（带录用通知书 PDF 附件） */
export function resendOfferNotice(offerId: number) {
  return requestClient.put<boolean>('/hrm/recruit/offer/resend-offer-notice', null, {
    params: { offerId },
  });
}
