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
    latestEntryTime?: string;
    description?: string;
    emergencyLevel?: number;
    status?: number;
    createTime?: string;
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
    resumeFileName: string;
    resumeFileUrl: string;
    resumeId?: number;
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
