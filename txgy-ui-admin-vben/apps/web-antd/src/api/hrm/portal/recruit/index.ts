import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace HrmPortalRecruitApi {
  export interface Company {
    id: number;
    name: string;
  }

  export interface Post {
    id: number;
    tenantId?: number;
    postName: string;
    postTypeId?: number;
    postTypeName?: string;
    jobNature?: number;
    areaId?: number;
    areaName?: string;
    workAddress?: string;
    longitude?: number;
    latitude?: number;
    deptId?: number;
    deptName?: string;
    companyName?: string;
    companyShortName?: string;
    companyLogo?: string;
    /** 招聘咨询 HR：优先岗位负责人，未配置时为岗位创建人 */
    ownerUserId?: number;
    ownerUserName?: string;
    ownerUserAvatar?: string;
    recruitNum?: number;
    workTime?: number;
    educationRequire?: number;
    minSalary?: number;
    maxSalary?: number;
    salaryUnit?: number;
    minAge?: number;
    maxAge?: number;
    /** 最晚入职时间。后端为 LocalDateTime，JSON 里是毫秒时间戳（数字）。 */
    latestEntryTime?: number | string;
    description?: string;
    emergencyLevel?: number;
    status?: number;
    /** 创建时间，同样是毫秒时间戳（数字）。 */
    createTime?: number | string;
  }

  /** 员工端岗位分页查询参数。后端会强制过滤为已发布状态。 */
  export interface PostPageParams extends PageParam {
    postName?: string;
    postTypeId?: number;
    jobNature?: number;
    areaId?: number;
    workTime?: number;
    educationRequire?: number;
    salaryMin?: number;
    salaryMax?: number;
    salaryNegotiable?: boolean;
  }

  export interface PostType {
    id: number;
    name: string;
    parentId?: number;
    sort?: number;
  }

  export interface Application {
    id: number;
    postId: number;
    postName?: string;
    status: string;
    statusName?: string;
    statusUpdateTime?: string;
    createTime?: string;
    candidateName?: string;
    mobileMasked?: string;
    email?: string;
    resumeFileName?: string;
    resumeFileUrl?: string;
    ownerUserId?: number;
    ownerUserName?: string;
    ownerUserAvatar?: string;
    interviews?: Interview[];
    offer?: Offer;
  }

  export interface Interview {
    id: number;
    type?: number;
    stageNumber?: number;
    interviewTime?: string;
    address?: string;
    remark?: string;
    result?: number;
  }

  export interface Offer {
    id: number;
    offerNo?: string;
    approvalStatus?: string;
    salaryAmount?: number;
    salaryUnit?: number;
    plannedOnboardDate?: string;
    validUntil?: string;
    sentTime?: string;
    confirmTime?: string;
  }

  export interface ApplyReq {
    postId: number;
    name: string;
    mobile: string;
    sex: number;
    age?: number;
    email?: string;
    workTime?: number;
    education: number;
    graduateSchool?: string;
    latestWorkPlace?: string;
    /** 附件简历文件名；使用在线简历版本投递时可不传 */
    resumeFileName?: string;
    /** 附件简历地址；使用在线简历版本投递时可不传 */
    resumeFileUrl?: string;
    /** 已保存的个人简历编号（附件简历或在线简历草稿） */
    resumeId?: number;
    /** 已固化的个人简历版本编号，优先于 resumeId */
    resumeVersionId?: number;
    resumeRemark?: string;
    sourceRemark?: string;
  }
}

export function getPortalRecruitPostPage(
  params: HrmPortalRecruitApi.PostPageParams,
) {
  return requestClient.get<PageResult<HrmPortalRecruitApi.Post>>(
    '/hrm/portal/recruit/post/page',
    { params },
  );
}

export function getPortalRecruitCompanyList() {
  return requestClient.get<HrmPortalRecruitApi.Company[]>(
    '/system/tenant/simple-list',
  );
}

export function getPortalRecruitPost(id: number) {
  return requestClient.get<HrmPortalRecruitApi.Post>(
    '/hrm/portal/recruit/post/get',
    { params: { id } },
  );
}

export function getPortalRecruitPostTypeList() {
  return requestClient.get<HrmPortalRecruitApi.PostType[]>(
    '/hrm/portal/recruit/post-type/list',
  );
}

export function createPortalRecruitApplication(
  data: HrmPortalRecruitApi.ApplyReq,
) {
  return requestClient.post<number>(
    '/hrm/portal/recruit/application/create',
    data,
  );
}

export function getPortalRecruitApplicationPage(
  params: PageParam & { status?: string },
) {
  return requestClient.request<PageResult<HrmPortalRecruitApi.Application>>(
    '/hrm/portal/recruit/application/page',
    { method: 'GET', params },
  );
}

export function getPortalRecruitApplication(id: number) {
  return requestClient.get<HrmPortalRecruitApi.Application>(
    '/hrm/portal/recruit/application/get',
    { params: { id } },
  );
}

export function withdrawPortalRecruitApplication(id: number, reason?: string) {
  return requestClient.put<boolean>(
    '/hrm/portal/recruit/application/withdraw',
    { id, reason },
  );
}

export function acceptPortalRecruitOffer(id: number) {
  return requestClient.put<boolean>('/hrm/portal/recruit/offer/accept', undefined, {
    params: { id },
  });
}

export function rejectPortalRecruitOffer(id: number, reason: string) {
  return requestClient.put<boolean>('/hrm/portal/recruit/offer/reject', undefined, {
    params: { id, reason },
  });
}
