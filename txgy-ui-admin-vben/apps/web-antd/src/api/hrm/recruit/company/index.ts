import { requestClient } from '#/api/request';

export namespace HrmRecruitCompanyApi {
  export interface Profile {
    companyName?: string;
    companyNameCustomized?: boolean;
    companyType?: string;
    legalRepresentative?: string;
    establishDate?: string;
    businessStatus?: string;
    registeredCapital?: string;
    registeredAddress?: string;
    region?: string;
    unifiedSocialCreditCode?: string;
    industry?: string;
    businessScope?: string;
    introduction?: string;
  }
}

export function getRecruitCompanyProfile() {
  return requestClient.get<HrmRecruitCompanyApi.Profile>('/hrm/recruit/company/get');
}

export function saveRecruitCompanyProfile(data: HrmRecruitCompanyApi.Profile) {
  return requestClient.put<boolean>('/hrm/recruit/company/save', data);
}

export function getPortalRecruitCompanyProfile(tenantId: number) {
  return requestClient.get<HrmRecruitCompanyApi.Profile>('/hrm/portal/recruit/company-profile/get', {
    params: { tenantId },
  });
}
