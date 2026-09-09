import { requestClient } from '#/api/request';

export namespace HrmPortalResumeApi {
  export interface Resume {
    id: number;
    title: string;
    resumeType: number;
    fileName?: string;
    fileUrl?: string;
    content?: string;
    remark?: string;
    createTime?: string;
  }

  export interface UploadReq {
    title: string;
    fileName: string;
    fileUrl: string;
    remark?: string;
  }

  export interface CreateReq {
    title: string;
    content?: string;
    remark?: string;
  }
}

export function getPortalResumeList() {
  return requestClient.request<HrmPortalResumeApi.Resume[]>(
    '/hrm/portal/resume/list',
    { method: 'GET' },
  );
}

export function uploadPortalResume(data: HrmPortalResumeApi.UploadReq) {
  return requestClient.post<number>('/hrm/portal/resume/upload', data);
}

export function createPortalResume(data: HrmPortalResumeApi.CreateReq) {
  return requestClient.post<number>('/hrm/portal/resume/create', data);
}
