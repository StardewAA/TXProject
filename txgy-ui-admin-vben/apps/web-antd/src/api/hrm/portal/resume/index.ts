import { requestClient } from '#/api/request';

export namespace HrmPortalResumeApi {
  export interface Resume {
    id: number;
    title: string;
    resumeType: number;
    sourceType?: number;
    fileName?: string;
    fileUrl?: string;
    avatarUrl?: string;
    content?: string;
    remark?: string;
    createTime?: string;
  }

  export interface UploadReq {
    title: string;
    fileName: string;
    fileUrl: string;
    remark?: string;
    content?: string;
    avatarUrl?: string;
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

export function uploadGeneratedResume(data: HrmPortalResumeApi.UploadReq) {
  return requestClient.post<number>('/hrm/portal/resume/upload-generated', data);
}

export const RESUME_SOURCE_UPLOAD = 1;
export const RESUME_SOURCE_GENERATED = 2;

export function isGeneratedResume(resume: HrmPortalResumeApi.Resume) {
  return resume.resumeType === 1 && resume.sourceType === RESUME_SOURCE_GENERATED;
}

export function isUploadedResume(resume: HrmPortalResumeApi.Resume) {
  return resume.resumeType === 1 && !isGeneratedResume(resume);
}

export function isOnlineResume(resume: HrmPortalResumeApi.Resume) {
  return resume.resumeType === 2 && resume.title === RECRUIT_PROFILE_TITLE;
}

export function findOnlineResume(resumes: HrmPortalResumeApi.Resume[]) {
  return resumes.find(isOnlineResume);
}

export function createPortalResume(data: HrmPortalResumeApi.CreateReq) {
  return requestClient.post<number>('/hrm/portal/resume/create', data);
}

export function deletePortalResume(id: number) {
  return requestClient.delete<boolean>(`/hrm/portal/resume/delete?id=${id}`);
}

export const RECRUIT_PROFILE_TITLE = '求职资料（系统）';

export function getRecruitProfileResume() {
  return getPortalResumeList().then((list) =>
    list.find(
      (resume) =>
        resume.resumeType === 2 && resume.title === RECRUIT_PROFILE_TITLE,
    ),
  );
}

export function isRecruitProfileComplete(resume?: HrmPortalResumeApi.Resume) {
  if (!resume?.content) return false;
  try {
    const profile = JSON.parse(resume.content) as { completedAt?: unknown };
    return Boolean(profile && typeof profile === 'object' && profile.completedAt);
  } catch {
    return false;
  }
}

export function parseRecruitProfileName(content?: string) {
  if (!content) return '';
  try {
    const parsed = JSON.parse(content) as { name?: unknown };
    return typeof parsed?.name === 'string' ? parsed.name.trim() : '';
  } catch {
    return '';
  }
}

export function getRecruitProfileName() {
  return getRecruitProfileResume().then((resume) =>
    parseRecruitProfileName(resume?.content),
  );
}
