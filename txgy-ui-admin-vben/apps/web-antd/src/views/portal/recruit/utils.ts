import type { HrmPortalRecruitApi } from '#/api/hrm/portal/recruit';

export const UNKNOWN_LOCATION = '工作地点待确认';

export function formatRecruitLocation(post?: HrmPortalRecruitApi.Post) {
  if (!post) return UNKNOWN_LOCATION;
  return post.areaName || (post.areaId ? `地区 ${post.areaId}` : UNKNOWN_LOCATION);
}

export function resolveRecruitCompanyName(
  post: Pick<HrmPortalRecruitApi.Post, 'companyName' | 'companyShortName' | 'tenantId'> | undefined,
  tenantNames: Record<number, string>,
) {
  if (!post) return '招聘企业待确认';
  return (
    post.companyName?.trim() ||
    post.companyShortName?.trim() ||
    (post.tenantId != null ? tenantNames[post.tenantId] : undefined) ||
    '招聘企业待确认'
  );
}

export function openRecruitLocationMap(location: string) {
  if (!location || location === UNKNOWN_LOCATION) return;
  window.open(
    `https://map.baidu.com/search/${encodeURIComponent(location)}`,
    '_blank',
    'noopener,noreferrer',
  );
}
