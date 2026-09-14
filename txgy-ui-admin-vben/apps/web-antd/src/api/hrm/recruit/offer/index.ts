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
}

export function createRecruitOffer(data: HrmRecruitOfferApi.SaveReq) {
  return requestClient.post<number>('/hrm/recruit/offer/create', data);
}

export function submitRecruitOffer(id: number) {
  return requestClient.put<boolean>(`/hrm/recruit/offer/submit?id=${id}`);
}
