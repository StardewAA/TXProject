import { requestClient } from '#/api/request';

export namespace HrmRecruitResumeApi {
  export interface RecruitResume {
    id?: number;
    candidateId: number;
    fileName: string;
    fileUrl?: string;
    versionNo?: number;
    status?: number;
    remark?: string;
    legacyIndex?: number;
    createTime?: Date;
  }
}

export function getRecruitResumeList(candidateId: number) {
  return requestClient.get<HrmRecruitResumeApi.RecruitResume[]>(
    '/hrm/recruit/resume/list',
    { params: { candidateId } },
  );
}

export function createRecruitResume(data: HrmRecruitResumeApi.RecruitResume) {
  return requestClient.post<number>('/hrm/recruit/resume/create', data);
}

export function getRecruitResumePreview(id: number) {
  return requestClient.get<HrmRecruitResumeApi.RecruitResume>(
    `/hrm/recruit/resume/preview?id=${id}`,
  );
}

export function getRecruitResumeDownload(id: number) {
  return requestClient.get<HrmRecruitResumeApi.RecruitResume>(
    `/hrm/recruit/resume/download?id=${id}`,
  );
}

/** 获得历史简历附件预览授权地址 */
export function getRecruitLegacyResumePreview(candidateId: number, index: number) {
  return requestClient.get<HrmRecruitResumeApi.RecruitResume>(
    '/hrm/recruit/resume/legacy-preview',
    { params: { candidateId, index } },
  );
}

/** 获得历史简历附件下载授权地址 */
export function getRecruitLegacyResumeDownload(candidateId: number, index: number) {
  return requestClient.get<HrmRecruitResumeApi.RecruitResume>(
    '/hrm/recruit/resume/legacy-download',
    { params: { candidateId, index } },
  );
}

/**
 * 保存简历文本（扫描件 OCR 识别结果 / 人工粘贴）。
 *
 * 保存后：入职建档会按规则自动补全员工档案字段（无需 AI）。
 */
export function saveRecruitResumeText(
  id: number,
  content: string,
  source: 'manual' | 'ocr' = 'ocr',
) {
  return requestClient.put<boolean>('/hrm/recruit/resume/save-text', {
    content,
    id,
    source,
  });
}
