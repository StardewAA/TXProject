import type { AuthPermissionInfo } from '@vben/types';

import { baseRequestClient, requestClient } from '#/api/request';

export namespace AuthApi {
  /** 登录接口参数 */
  export interface LoginParams {
    /** 企业登录时使用的租户编号 */
    tenantId?: number | string;
    /** 登录入口类型，后端可据此区分企业/个人账号 */
    loginType?: 'enterprise' | 'personal';
    /** 内部字段：true 时登录失败不弹全局错误提示（个人登录租户回退重试用），不会发送给后端 */
    silentError?: boolean;
    password?: string;
    username?: string;
    captchaVerification?: string;
    // 绑定社交登录时，需要传递如下参数
    socialType?: number;
    socialCode?: string;
    socialState?: string;
  }

  /** 登录接口返回值 */
  export interface LoginResult {
    accessToken: string;
    refreshToken: string;
    userId: number;
    expiresTime: number;
    /** 账号实际所属租户编号（后端 AuthLoginRespVO.tenantId） */
    tenantId?: number;
  }

  /** 租户信息返回值 */
  export interface TenantResult {
    id: number;
    name: string;
  }

  /** 手机验证码获取接口参数 */
  export interface SmsCodeParams {
    mobile: string;
    scene: number;
  }

  /** 手机验证码登录接口参数 */
  export interface SmsLoginParams {
    mobile: string;
    code: string;
  }

  /** 注册接口参数 */
  export interface RegisterParams {
    username: string;
    password: string;
    captchaVerification: string;
    /** 注册入口类型；公开注册仅支持个人账号 */
    registerType?: 'personal';
  }

  /** 重置密码接口参数 */
  export interface ResetPasswordParams {
    password: string;
    mobile: string;
    code: string;
  }

  /** 社交快捷登录接口参数 */
  export interface SocialLoginParams {
    type: number;
    code: string;
    state: string;
  }
}

/** 登录 */
export async function loginApi(data: AuthApi.LoginParams) {
  const { silentError, ...payload } = data;
  return requestClient.post<AuthApi.LoginResult>('/system/auth/login', payload, {
    headers: {
      isEncrypt: false,
      ...(silentError ? { 'x-skip-error-toast': 'true' } : {}),
    },
  });
}

/** 刷新 accessToken */
export async function refreshTokenApi(refreshToken: string) {
  return baseRequestClient.post(
    `/system/auth/refresh-token?refreshToken=${refreshToken}`,
  );
}

/** 退出登录 */
export async function logoutApi(accessToken: string) {
  return baseRequestClient.post(
    '/system/auth/logout',
    {},
    {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    },
  );
}

/** 获取权限信息 */
export async function getAuthPermissionInfoApi() {
  return requestClient.get<AuthPermissionInfo>(
    '/system/auth/get-permission-info',
  );
}

/** 获取租户列表 */
export async function getTenantSimpleList() {
  const response = await baseRequestClient.get<{
    data?: AuthApi.TenantResult[];
  }>(`/system/tenant/simple-list`);
  const data = response?.data?.data ?? response?.data;
  return Array.isArray(data) ? data : [];
}

/** 使用租户域名，获得租户信息 */
export async function getTenantByWebsite(website: string) {
  const response = await baseRequestClient.get<{
    data?: null | AuthApi.TenantResult;
  }>(`/system/tenant/get-by-website?website=${website}`);
  const data = response?.data?.data ?? response?.data;
  return data && typeof data === 'object' && 'id' in data ? data : null;
}

/** 使用租户名，获得租户编号（登录页手动输入租户名时使用） */
export async function getTenantIdByName(name: string) {
  return requestClient.get<null | number>(
    `/system/tenant/get-id-by-name?name=${encodeURIComponent(name)}`,
  );
}

/** 获取验证码 */
export async function getCaptcha(data: any) {
  return baseRequestClient.post('/system/captcha/get', data);
}

/** 校验验证码 */
export async function checkCaptcha(data: any) {
  return baseRequestClient.post('/system/captcha/check', data);
}

/** 获取登录验证码 */
export async function sendSmsCode(data: AuthApi.SmsCodeParams) {
  return requestClient.post('/system/auth/send-sms-code', data);
}

/** 短信验证码登录 */
export async function smsLogin(data: AuthApi.SmsLoginParams) {
  return requestClient.post('/system/auth/sms-login', data);
}

/** 注册 */
export async function register(data: AuthApi.RegisterParams) {
  return requestClient.post('/system/auth/register', data);
}

/** 通过短信重置密码 */
export async function smsResetPassword(data: AuthApi.ResetPasswordParams) {
  return requestClient.post('/system/auth/reset-password', data);
}

/** 社交授权的跳转 */
export async function socialAuthRedirect(type: number, redirectUri: string) {
  return requestClient.get('/system/auth/social-auth-redirect', {
    params: {
      type,
      redirectUri,
    },
  });
}

/** 社交快捷登录 */
export async function socialLogin(data: AuthApi.SocialLoginParams) {
  return requestClient.post<AuthApi.LoginResult>(
    '/system/auth/social-login',
    data,
  );
}
