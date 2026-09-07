import type { PageParam, PageResult } from '@vben/request';
import { requestClient } from '#/api/request';
export namespace MallEnterpriseApi { export interface Enterprise { id?: number; name: string; creditCode: string; type?: number; licenseUrl?: string; contactName?: string; contactMobile?: string; address?: string; introduction?: string; auditStatus?: number; auditRemark?: string; createTime?: string; } }
export function getEnterprisePage(params: PageParam & Partial<MallEnterpriseApi.Enterprise>) { return requestClient.get<PageResult<MallEnterpriseApi.Enterprise>>('/product/enterprise/page', { params }); }
export function createEnterprise(data: MallEnterpriseApi.Enterprise) { return requestClient.post('/product/enterprise/create', data); }
export function updateEnterprise(data: MallEnterpriseApi.Enterprise) { return requestClient.put('/product/enterprise/update', data); }
export function deleteEnterprise(id: number) { return requestClient.delete(`/product/enterprise/delete?id=${id}`); }
export function getEnterprise(id: number) { return requestClient.get<MallEnterpriseApi.Enterprise>(`/product/enterprise/get?id=${id}`); }
export function auditEnterprise(id: number, status: number, remark?: string) { return requestClient.put(`/product/enterprise/audit?id=${id}&status=${status}${remark ? `&remark=${encodeURIComponent(remark)}` : ''}`); }
export function getApprovedEnterpriseList() { return requestClient.get<MallEnterpriseApi.Enterprise[]>('/product/enterprise/list-approved'); }
