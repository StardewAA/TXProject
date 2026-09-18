import { requestClient } from '#/api/request';

/**
 * AI 聊天角色权限配置 API
 *
 * 对应后端 AiChatRolePermissionController（/ai/chat-role-permission/**），
 * 用于【控制台 - 聊天角色】的权限配置，以及 AI 对话页展示当前用户可查询范围。
 */
export namespace AiChatPermissionApi {
  /** MES 业务域（后端枚举 AiMesBizTypeEnum） */
  export interface BizType {
    type: string; // 业务域编码，如 PRO_WORK_ORDER
    name: string; // 业务域名称，如 生产工单
    toolName: string; // 对应的 AI 工具名
  }

  /** 单个业务域的权限配置 */
  export interface Permission {
    id?: number;
    roleId?: number;
    bizType: string; // 业务域编码
    bizName?: string; // 业务域名称
    canQuery?: boolean; // 是否允许查询
    canSensitive?: boolean; // 是否允许访问敏感生产数据
    billTypes?: string; // 可查询的单据类型（JSON 数组字符串）
    dataScope?: string; // 数据范围：ALL / DEPT / SELF
    remark?: string;
  }

  /** 角色权限配置（查询返回） */
  export interface RolePermissionResult {
    permissions: Permission[];
    userIds: number[];
  }

  /** 角色权限配置（保存提交） */
  export interface RolePermissionSaveReq {
    roleId: number;
    permissions: Permission[];
    userIds?: number[];
  }

  /** 当前用户的权限上下文 */
  export interface PermissionContext {
    userId: number;
    tenantId: number;
    roleIds: number[];
    roleNames: string[];
    allowedBizNames: string[];
    sensitiveBizNames: string[];
    dataScope: string;
    hasAnyPermission: boolean;
  }
}

/** 获得角色的权限配置（业务域权限 + 绑定用户） */
export function getRolePermission(roleId: number) {
  return requestClient.get<AiChatPermissionApi.RolePermissionResult>(
    `/ai/chat-role-permission/get?roleId=${roleId}`,
  );
}

/** 保存角色的权限配置（整体覆盖） */
export function updateRolePermission(
  data: AiChatPermissionApi.RolePermissionSaveReq,
) {
  return requestClient.put('/ai/chat-role-permission/update', data);
}

/** 获得全部 MES 业务域（权限配置页渲染用） */
export function getBizTypes() {
  return requestClient.get<AiChatPermissionApi.BizType[]>(
    '/ai/chat-role-permission/biz-types',
  );
}

/** 获得当前用户的 AI 权限上下文（对话页展示「我能查什么」） */
export function getMyPermission() {
  return requestClient.get<AiChatPermissionApi.PermissionContext>(
    '/ai/chat-role-permission/my',
  );
}
