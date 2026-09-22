import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace MemberPlatformAccountReviewApi {
  export type Id = number | string;

  export interface LinkPageParams extends PageParam {
    legacyMemberId?: Id;
    linkStatus?: string;
    platformAccountId?: Id;
    reviewNo?: string;
    sourceTenantId?: Id;
  }

  export interface LinkRow {
    conflictReason?: string;
    createTime?: string;
    identityReviewNo?: string;
    identityVerifiedAt?: string;
    identityVerifiedBy?: string;
    legacyMemberId: Id;
    linkId: Id;
    linkOrigin?: string;
    linkStatus: string;
    maskedLoginName?: string;
    maskedMobile?: string;
    memberStatus?: number;
    platformAccountId?: Id;
    platformNo?: string;
    sourceTenantId: Id;
    updateTime?: string;
  }

  export interface MergeRequest {
    linkIds: Id[];
    reviewNo: string;
    reviewNote: string;
    targetPlatformAccountId: Id;
  }

  export interface MergeResponse {
    idempotentReplay: boolean;
    readyLinkCount: number;
    reviewNo: string;
    targetPlatformAccountId: Id;
    verifiedAt: string;
  }
}

export function getPlatformAccountLinkPage(
  params: MemberPlatformAccountReviewApi.LinkPageParams,
) {
  return requestClient.get<
    PageResult<MemberPlatformAccountReviewApi.LinkRow>
  >('/member/platform-account-review/page', { params });
}

export function mergePlatformAccountLinks(
  data: MemberPlatformAccountReviewApi.MergeRequest,
) {
  return requestClient.post<MemberPlatformAccountReviewApi.MergeResponse>(
    '/member/platform-account-review/merge',
    data,
  );
}
