import type { PageParam, PageResult } from '@vben/request';

import type { AxiosProgressEvent } from '#/api/infra/file';

import { requestClient } from '#/api/request';

export namespace HrmRecruitCompanyApi {
  export interface Product {
    name: string;
    intro?: string;
    image?: string;
  }

  export interface Company {
    id?: number;
    tenantId?: number;
    name: string;
    shortName?: string;
    logo?: string;
    industry?: string;
    scale?: string;
    financingStage?: '上市融资' | '不融资' | '股权融资';
    nature?: string;
    summary?: string;
    products?: string;
    album?: string;
    address?: string;
    areaId?: number;
    longitude?: number;
    latitude?: number;
    areaName?: string;
    website?: string;
    establishedDate?: string;
    registeredCapital?: string;
    creditCode?: string;
    legalPerson?: string;
    dataSource?: string;
    dataSourceUrl?: string;
    verified?: boolean;
    status?: number;
    postCount?: number;
  }

  export interface PortalCompanyPostParams extends PageParam {
    tenantId: number;
  }
}

export function getRecruitCompanyProfile() {
  return requestClient.get<HrmRecruitCompanyApi.Company>(
    '/hrm/recruit/company/get',
  );
}

export function saveRecruitCompanyProfile(data: HrmRecruitCompanyApi.Company) {
  return requestClient.post<number>('/hrm/recruit/company/save', data);
}

export function uploadRecruitCompanyImage(
  file: File,
  category: 'album' | 'logo' | 'product',
  onUploadProgress?: AxiosProgressEvent,
) {
  return requestClient.upload<string>(
    '/hrm/recruit/company/upload',
    { file, category },
    { onUploadProgress },
  );
}

export function getPortalRecruitCompany(tenantId: number) {
  return requestClient.get<HrmRecruitCompanyApi.Company>(
    '/hrm/portal/recruit/company/get',
    { params: { tenantId } },
  );
}

export function getPortalRecruitCompanyPostPage(
  params: HrmRecruitCompanyApi.PortalCompanyPostParams,
) {
  return requestClient.get<PageResult<any>>(
    '/hrm/portal/recruit/company/post/page',
    { params },
  );
}
