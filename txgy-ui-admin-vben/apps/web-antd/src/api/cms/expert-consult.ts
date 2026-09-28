import { requestClient } from '#/api/request';

export interface CmsExpert {
  auditRemark?: string;
  auditStatus: 0 | 1 | 2;
  company?: string;
  createTime: string;
  enabled: boolean;
  expertName: string;
  fixedPrice: number;
  id: number;
  introduction?: string;
  orderCount: number;
  professionalTitle?: string;
  rating: number;
  serviceFields: string;
  userId: number;
}

export interface ConsultOrder {
  cancelReason?: string;
  completedTime?: string;
  createTime: string;
  expertId: number;
  expertName: string;
  expertUserId: number;
  expireTime: string;
  id: number;
  imGroupId?: number;
  orderNo: string;
  paidTime?: string;
  payOrderId?: number;
  price: number;
  question: string;
  requesterMobile?: string;
  requesterName?: string;
  serviceSubject: string;
  status: number;
  userId: number;
  version: number;
}

export interface ExpertSettlement {
  commissionAmount: number;
  commissionRate: number;
  expertName: string;
  id: number;
  orderAmount: number;
  orderId: number;
  orderNo: string;
  proofNo?: string;
  proofUrl?: string;
  remark?: string;
  settledTime?: string;
  settlementAmount: number;
  status: 0 | 1 | 2;
}

export const listAvailableExperts = () =>
  requestClient.get<CmsExpert[]>('/cms/expert-consult/expert/available');

export const getMyExpert = () =>
  requestClient.get<CmsExpert | null>('/cms/expert-consult/expert/me');

export const listExperts = (auditStatus?: number) =>
  requestClient.get<CmsExpert[]>('/cms/expert-consult/expert/list', {
    params: { auditStatus },
  });

export const inviteExpert = (data: {
  company?: string;
  expertUserId: number;
  expertName: string;
  fixedPrice: number;
  introduction?: string;
  professionalTitle?: string;
  serviceFields: string;
}) => requestClient.post<CmsExpert>('/cms/expert-consult/expert/invite', data);

export const createConsultOrder = (data: {
  expertId: number;
  question: string;
  subject: string;
}) =>
  requestClient.post<ConsultOrder>('/cms/expert-consult/order/create', data);

export const listMyConsultOrders = () =>
  requestClient.get<ConsultOrder[]>('/cms/expert-consult/order/my');

export const listExpertConsultRequests = () =>
  requestClient.get<ConsultOrder[]>(
    '/cms/expert-consult/order/expert-requests',
  );

export const getExpertConsultContact = (orderId: number) =>
  requestClient.get<{
    imGroupId?: number;
    imReady: boolean;
    orderId: number;
    requesterMobile?: string;
    requesterName?: string;
    requesterUserId: number;
  }>('/cms/expert-consult/order/contact', { params: { orderId } });

export const listConsultOrders = () =>
  requestClient.get<ConsultOrder[]>('/cms/expert-consult/order/list');

export const actConsultOrder = (
  orderId: number,
  action: 'ACCEPT' | 'CANCEL' | 'COMPLETE' | 'REJECT' | 'REQUEST_COMPLETE' | 'START',
  reason?: string,
) =>
  requestClient.post<ConsultOrder>(
    '/cms/expert-consult/order/action',
    { action, reason },
    { params: { orderId } },
  );

export const evaluateConsultOrder = (
  orderId: number,
  rating: number,
  content?: string,
) =>
  requestClient.post(
    '/cms/expert-consult/order/evaluate',
    { content, rating },
    { params: { orderId } },
  );

export const listExpertSettlements = (status?: number) =>
  requestClient.get<ExpertSettlement[]>(
    '/cms/expert-consult/settlement/list',
    { params: { status } },
  );

export const settleExpertSettlement = (
  settlementId: number,
  data: { proofNo: string; proofUrl: string; remark?: string },
) =>
  requestClient.post<ExpertSettlement>(
    '/cms/expert-consult/settlement/settle',
    data,
    { params: { settlementId } },
  );

export const rejectExpertSettlement = (
  settlementId: number,
  remark: string,
) =>
  requestClient.post<ExpertSettlement>(
    '/cms/expert-consult/settlement/reject',
    { remark },
    { params: { settlementId } },
  );


export interface CmsExpertApplication {
  applyChannel: string;
  applyTime?: string;
  auditRemark?: string;
  auditStatus: 0 | 1 | 2;
  company?: string;
  createTime: string;
  enabled: boolean;
  expertLevel: string;
  expertName: string;
  fixedPrice: number;
  id: number;
  introduction?: string;
  phone?: string;
  professionalTitle?: string;
  serviceFields: string;
  userId: number;
  verifyResult?: string;
  yearsOfExperience?: number;
}

/** 专家入驻申请（实名核验通过即自动上架） */
export const applyExpert = (data: {
  company?: string;
  expertName: string;
  fixedPrice?: number;
  idCard: string;
  introduction?: string;
  phone: string;
  professionalTitle?: string;
  serviceFields: string;
  yearsOfExperience?: number;
}) => requestClient.post<CmsExpertApplication>('/cms/expert-consult/expert/apply', data);

/** 我的专家入驻申请 */
export const getMyExpertApplication = () =>
  requestClient.get<CmsExpertApplication>('/cms/expert-consult/expert/apply/me');

/** 管理端：专家入驻申请列表 */
export const listExpertApplications = (auditStatus?: number) =>
  requestClient.get<CmsExpertApplication[]>('/cms/expert-consult/expert/apply/list', {
    params: { auditStatus },
  });

/** 管理端：审核通过 / 驳回 */
export const auditExpertApplication = (params: {
  id: number;
  pass: boolean;
  remark?: string;
}) =>
  requestClient.post<CmsExpertApplication>(
    '/cms/expert-consult/expert/apply/audit',
    null,
    { params },
  );
