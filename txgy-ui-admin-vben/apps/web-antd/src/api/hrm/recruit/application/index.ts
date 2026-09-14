import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace HrmRecruitApplicationApi {
  export interface Application {
    id: number;
    candidateId?: number;
    postId?: number;
    postName?: string;
    resumeId?: number;
    status?: string;
    statusName?: string;
    statusUpdateTime?: string;
    eliminateReason?: string;
    withdrawReason?: string;
    createTime?: string;
  }
}

export function getRecruitApplicationPage(params: PageParam & { candidateId: number }) {
  return requestClient.get<PageResult<HrmRecruitApplicationApi.Application>>(
    '/hrm/recruit/application/page',
    { params },
  );
}

export function startRecruitApplicationScreening(id: number) {
  return requestClient.put<boolean>('/hrm/recruit/application/start-screening', { id });
}

export function passRecruitApplicationScreening(id: number) {
  return requestClient.put<boolean>('/hrm/recruit/application/pass-screening', { id });
}

export function rejectRecruitApplicationResume(id: number, reason: string) {
  return requestClient.put<boolean>('/hrm/recruit/application/reject-resume', { id, reason });
}

export function enterRecruitApplicationInterview(id: number) {
  return requestClient.put<boolean>('/hrm/recruit/application/enter-interview', { id });
}
