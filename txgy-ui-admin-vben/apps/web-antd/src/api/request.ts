/**
 * 该文件可自行根据业务逻辑进行调整
 */
import type { RequestClientOptions } from '@vben/request';

import { isTenantEnable, useAppConfig } from '@vben/hooks';
import { preferences } from '@vben/preferences';
import {
  authenticateResponseInterceptor,
  defaultResponseInterceptor,
  errorMessageResponseInterceptor,
  RequestClient,
} from '@vben/request';
import { useAccessStore } from '@vben/stores';
import { createApiEncrypt } from '@vben/utils';

import { message } from 'ant-design-vue';
import { formatWmsErrorMessage } from '#/api/wms/error-message';

import { useAuthStore } from '#/store';

import { refreshTokenApi } from './core';

const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);
const tenantEnable = isTenantEnable();
const apiEncrypt = createApiEncrypt(import.meta.env);
const TENANT_ACCESS_DENIED_MESSAGE = '您无权访问该租户的数据';

function createRequestClient(baseURL: string, options?: RequestClientOptions) {
  const client = new RequestClient({
    ...options,
    baseURL,
  });
  let reAuthing = false;
  let tenantReAuthenticationInProgress = false;

  /**
   * 重新认证逻辑
   */
  async function doReAuthenticate() {
    console.warn('Access token or refresh token is invalid or expired. ');
    const accessStore = useAccessStore();
    const authStore = useAuthStore();
    accessStore.setAccessToken(null);
    if (
      preferences.app.loginExpiredMode === 'modal' &&
      accessStore.isAccessChecked
    ) {
      accessStore.setLoginExpired(true);
    } else {
      await authStore.logout();
    }
  }

  /**
   * 旧会话的 token 租户与当前选择的租户不一致时，清除整个会话，
   * 避免并发请求持续弹出租户越权提示。
   */
  async function doTenantReAuthenticate() {
    const accessStore = useAccessStore();
    if (tenantReAuthenticationInProgress || !accessStore.accessToken) {
      return;
    }
    tenantReAuthenticationInProgress = true;
    try {
      accessStore.setAccessToken(null);
      accessStore.setRefreshToken(null);
      await useAuthStore().logout();
    } finally {
      tenantReAuthenticationInProgress = false;
    }
  }

  /**
   * 刷新token逻辑
   */
  async function doRefreshToken() {
    const accessStore = useAccessStore();
    const refreshToken = accessStore.refreshToken as string;
    if (!refreshToken) {
      throw new Error('Refresh token is null!');
    }
    const resp = await refreshTokenApi(refreshToken);
    const newToken = resp?.data?.data?.accessToken;
    // add by 芋艿：这里一定要抛出 resp.data，从而触发 authenticateResponseInterceptor 中，刷新令牌失败！！！
    if (!newToken) {
      throw resp.data;
    }
    accessStore.setAccessToken(newToken);
    return newToken;
  }

  function formatToken(token: null | string) {
    return token ? `Bearer ${token}` : null;
  }

  /**
   * 是否为个人注册请求：个人注册固定落在后端保留的 tenant_id=0，
   * 不允许携带企业租户（tenant-id）或访问租户（visit-tenant-id）。
   * 注：该函数定义在合并中丢失（调用方为 !148 引入），此处按注册接口路径补回。
   */
  function isPersonalRegister(config: { url?: string }) {
    const url = (config?.url || '').toLowerCase();
    return url.includes('/system/auth/register');
  }

  // 请求头处理
  client.addRequestInterceptor({
    fulfilled: async (config) => {
      const accessStore = useAccessStore();

      config.headers.Authorization = formatToken(accessStore.accessToken);
      config.headers['Accept-Language'] = preferences.app.locale;
      // 租户编号：登录时由登录页根据手动输入的租户名解析并写入 accessStore；
      // 未设置时回退到 .env 的默认租户（VITE_APP_DEFAULT_TENANT_ID），保证多租户头不缺失
      const tenantId =
        accessStore.tenantId ??
        Number(import.meta.env.VITE_APP_DEFAULT_TENANT_ID || 1);
      config.headers['tenant-id'] = String(tenantId);
      config.headers['visit-tenant-id'] = String(tenantId);
      if (isPersonalRegister(config)) {
        // 个人注册固定落在后端保留的 tenant_id=0，不允许携带企业租户或访问租户。
        config.headers['tenant-id'] = undefined;
        config.headers['visit-tenant-id'] = undefined;
      }

      // 是否 API 加密
      if ((config.headers || {}).isEncrypt) {
        try {
          // 加密请求数据
          if (config.data) {
            config.data = apiEncrypt.encryptRequest(config.data);
            // 设置加密标识头
            config.headers[apiEncrypt.getEncryptHeader()] = 'true';
          }
        } catch (error) {
          console.error('请求数据加密失败:', error);
          throw error;
        }
      }
      return config;
    },
  });

  // API 解密响应拦截器
  client.addResponseInterceptor({
    fulfilled: (response) => {
      // 检查是否需要解密响应数据
      const encryptHeader = apiEncrypt.getEncryptHeader();
      const isEncryptResponse =
        response.headers[encryptHeader] === 'true' ||
        response.headers[encryptHeader.toLowerCase()] === 'true';
      if (isEncryptResponse && typeof response.data === 'string') {
        try {
          // 解密响应数据
          response.data = apiEncrypt.decryptResponse(response.data);
        } catch (error) {
          console.error('响应数据解密失败:', error);
          throw new Error(`响应数据解密失败: ${(error as Error).message}`, {
            cause: error,
          });
        }
      }
      return response;
    },
  });

  // 租户错配不是普通业务报错：清理旧会话并返回登录页，让新 token 绑定正确租户。
  client.addResponseInterceptor({
    fulfilled: async (response) => {
      const responseData = response.data as any;
      if (
        responseData?.code === 403 &&
        responseData?.msg === TENANT_ACCESS_DENIED_MESSAGE
      ) {
        await doTenantReAuthenticate();
      }
      return response;
    },
  });

  // add by 芋艿：对应 https://t.zsxq.com/SHqWw 反馈
  // 处理 Blob 响应中的业务错误（如 401）：后端把「账号未登录」包成 HTTP 200 + body {code: 401, msg: ...}，
  // download 强制 responseType: 'blob' 后被 axios 包成 application/json 的 Blob，defaultResponseInterceptor 走
  // responseReturn === 'body' 分支直接返回，绕过了 authenticateResponseInterceptor 的 401 token 刷新；
  // 这里把这种 Blob 解析回 JSON，再以 axios 风格抛出，让后续拦截器接管
  client.addResponseInterceptor({
    fulfilled: async (response) => {
      const blob = response.data;
      if (!(blob instanceof Blob)) {
        return response;
      }
      // Blob.type 在部分环境可能为空或大小写不一，叠加 response header 一起判断更稳
      const blobType = (blob.type || '').toLowerCase();
      const headerType = String(
        response.headers?.['content-type'] ??
          response.headers?.['Content-Type'] ??
          '',
      ).toLowerCase();
      if (
        !blobType.includes('application/json') &&
        !headerType.includes('application/json')
      ) {
        return response;
      }
      let parsed: any;
      try {
        parsed = JSON.parse(await blob.text());
      } catch {
        return response;
      }
      if (parsed && parsed.code !== undefined && parsed.code !== 0) {
        response.data = parsed;
        throw Object.assign(new Error(parsed.msg ?? 'Request failed'), {
          config: response.config,
          response,
          data: parsed,
          isAxiosError: true,
        });
      }
      return response;
    },
  });

  // 处理返回的响应数据格式
  client.addResponseInterceptor(
    defaultResponseInterceptor({
      codeField: 'code',
      dataField: 'data',
      successCode: 0,
    }),
  );

  // token过期的处理
  client.addResponseInterceptor(
    authenticateResponseInterceptor({
      client,
      doReAuthenticate,
      doRefreshToken,
      enableRefreshToken: preferences.app.enableRefreshToken,
      formatToken,
    }),
  );

  // 通用的错误处理,如果没有进入上面的错误处理逻辑，就会进入这里
  client.addResponseInterceptor(
    errorMessageResponseInterceptor((msg: string, error) => {
      // 这里可以根据业务进行定制,你可以拿到 error 内的信息进行定制化处理，根据不同的 code 做不同的提示，而不是直接使用 message.error 提示 msg
      // 当前mock接口返回的错误字段是 error 或者 message
      const responseData = error?.response?.data ?? {};
      // 请求可携带 x-skip-error-toast 头静默失败（如个人登录的租户回退重试：第一次失败属预期，由调用方自行处理）
      if (error?.config?.headers?.['x-skip-error-toast'] === 'true') {
        return;
      }
      const errorMessage =
        responseData?.error ?? responseData?.message ?? responseData.msg ?? '';
      // add by 芋艿：特殊：避免 401 “账号未登录”，重复提示。因为，此时会跳转到登录界面，只需提示一次！！！
      if (error?.data?.code === 401) {
        return;
      }
      // 租户错配已由上面的拦截器统一清理会话，不再对并发请求重复弹窗。
      if (
        (error?.data?.code === 403 || responseData?.code === 403) &&
        (error?.data?.msg === TENANT_ACCESS_DENIED_MESSAGE ||
          responseData?.msg === TENANT_ACCESS_DENIED_MESSAGE)
      ) {
        return;
      }
      // 如果没有错误信息，则会根据状态码进行提示
      const displayMessage = formatWmsErrorMessage(errorMessage || msg);
      if (displayMessage.includes('请选择匹配的业务仓库')) {
        message.error({ content: displayMessage, key: 'wms-warehouse-type-mismatch' });
      } else {
        message.error(displayMessage);
      }
    }),
  );

  return client;
}

export const requestClient = createRequestClient(apiURL, {
  responseReturn: 'data',
});

export const baseRequestClient = new RequestClient({ baseURL: apiURL });
baseRequestClient.addRequestInterceptor({
  fulfilled: (config) => {
    const accessStore = useAccessStore();
    // 租户编号：优先取登录页解析后的租户，未设置时回退默认租户，避免多租户头缺失
    const tenantId =
      accessStore.tenantId ??
      Number(import.meta.env.VITE_APP_DEFAULT_TENANT_ID || 1);
    config.headers['tenant-id'] = String(tenantId);
    config.headers['visit-tenant-id'] = String(tenantId);
    return config;
  },
});
