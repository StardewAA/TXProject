import { requestClient } from '#/api/request';

export namespace HrmRecruitFunnelApi {
  /** 招聘漏斗（按职位） */
  export interface Funnel {
    postId?: number;
    postName?: string;
    applicationCount?: number;
    screeningPassedCount?: number;
    interviewCount?: number;
    finalPassedCount?: number;
    offerSentCount?: number;
    onboardedCount?: number;
    /** 筛选通过率（%，相对投递） */
    screeningRate?: number;
    /** 面试进入率（%，相对筛选通过） */
    interviewRate?: number;
    /** Offer 发放率（%，相对终面通过） */
    offerRate?: number;
    /** 入职转化率（%，相对投递） */
    onboardRate?: number;
    /** 平均入职耗时（天） */
    avgOnboardDays?: number;
  }
}

/** 获得招聘漏斗（按职位，默认最近 6 个月） */
export function getRecruitFunnelList(months?: number) {
  return requestClient.get<HrmRecruitFunnelApi.Funnel[]>(
    '/hrm/recruit/funnel/list',
    { params: { months } },
  );
}
