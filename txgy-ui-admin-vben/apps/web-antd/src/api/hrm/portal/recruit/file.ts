import { requestClient } from '#/api/request';

export type HrmRecruitFileCategory = 'avatar' | 'chat' | 'resume' | 'resume-avatar';

export function uploadHrmRecruitFile(file: globalThis.File, category: HrmRecruitFileCategory) {
  return requestClient.upload<string>('/hrm/portal/recruit/file/upload', {
    file,
    category,
  });
}

export function getHrmRecruitLocalFileUrl(fileUrl?: string) {
  return fileUrl || '';
}
