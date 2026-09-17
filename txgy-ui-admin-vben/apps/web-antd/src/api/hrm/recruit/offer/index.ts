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
