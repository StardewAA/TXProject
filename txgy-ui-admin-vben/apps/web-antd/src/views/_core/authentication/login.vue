<script lang="ts" setup>
import type { VbenFormSchema } from '@vben/common-ui';

import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { AuthenticationLogin, Verification, z } from '@vben/common-ui';
import { isCaptchaEnable, isTenantEnable } from '@vben/hooks';
import { $t } from '@vben/locales';
import { useAccessStore, useUserStore } from '@vben/stores';
import { preferences } from '@vben/preferences';
import { message } from 'ant-design-vue';

import {
  checkCaptcha,
  getCaptcha,
  getTenantByWebsite,
  getTenantIdByName,
  socialAuthRedirect,
} from '#/api/core/auth';
import { useAuthStore } from '#/store';

defineOptions({ name: 'Login' });

const { query } = useRoute();
const authStore = useAuthStore();
const accessStore = useAccessStore();
const userStore = useUserStore();

const tenantEnable = isTenantEnable();
// 临时关闭登录验证码（后端接口正常，但前端 Verification 组件渲染异常）
const captchaEnable = false;

const loginRef = ref();
const verifyRef = ref();

const captchaType = 'blockPuzzle'; // 验证码类型：'blockPuzzle' | 'clickWord'

/**
 * 页面初始化：根据当前域名自动识别租户。
 * 域名绑定租户时自动填入租户名；否则留空，由用户手动输入。
 */
async function fetchTenantInfo() {
   await fetchTenantList();
}
function handleLoginTypeChange(type: LoginType) {
  const previousType = loginType.value;
  loginType.value = type;

  if (type === 'personal') {
    loginRef.value?.getFormApi().setFieldValue('tenantId', undefined);
    return;
  }

  if (previousType !== 'enterprise') {
    void nextTick(() => fetchTenantList());
  }
}

async function fetchTenantList() {
  if (!tenantEnable) {
    return;
  }

  try {
    const websiteTenantPromise = getTenantByWebsite(window.location.hostname);
    tenantList.value = await getTenantSimpleList();

    let tenantId: null | number = null;
    const websiteTenant = await websiteTenantPromise;

    if (websiteTenant?.id) {
      accessStore.setTenantId(websiteTenant.id);
      loginRef.value
        .getFormApi()
        .setFieldValue('tenantName', websiteTenant.name);
    }

    if (!tenantId && accessStore.tenantId) {
      tenantId = accessStore.tenantId;
    }

    if (!tenantId && tenantList.value?.[0]?.id) {
      tenantId = tenantList.value[0].id;
    }

    if (loginType.value === 'personal') {
      return;
    }

    loginRef.value
      ?.getFormApi()
      .setFieldValue('tenantId', tenantId?.toString());
  } catch (error) {
    console.error('获取租户信息失败:', error);
  }
}

async function ensureLoginTenant(values: Record<string, any>) {
  if (!tenantEnable || loginType.value === 'personal') {
    return;
  }

  if (!tenantList.value.length) {
    await fetchTenantList();
  }

  const selectedTenantId = Number(
    values.tenantId || accessStore.tenantId || tenantList.value[0]?.id,
  );

  if (!Number.isInteger(selectedTenantId) || selectedTenantId <= 0) {
    message.error('无法确定登录租户，请刷新页面后重新登录。');
    throw new Error('Login tenant is unavailable');
  }

  accessStore.setTenantId(selectedTenantId);
}

function resolveRedirectPath() {
  const redirect =
    typeof route.query.redirect === 'string'
      ? decodeURIComponent(route.query.redirect)
      : '';

  const userInfo = userStore.userInfo;

  if (
    hasBackendAccess({
      loginType: loginType.value,
      user: userInfo,
      roles: userStore.userRoles,
      menus: accessStore.accessMenus,
      permissions: accessStore.accessCodes,
    })
  ) {
    const backendRedirect =
      redirect && !redirect.startsWith('/portal/') ? redirect : '';

    const configuredHomePath = userInfo?.homePath;
    const enterpriseHomePath =
      configuredHomePath && !configuredHomePath.startsWith('/portal/')
        ? configuredHomePath
        : '';

    return (
      backendRedirect || enterpriseHomePath || preferences.app.defaultHomePath
    );
  }
  // tenantName 仅用于解析租户编号，不随登录参数提交后端
  const { tenantName, ...loginParams } = values;
  await authStore.authLogin('username', loginParams);
}

/** 处理登录 */
async function handleLogin(values: any) {
  await ensureLoginTenant(values);

  if (captchaEnable) {
    verifyRef.value.show();
    return;
  }

  await authStore.authLogin(
    'username',
    {
      ...values,
      loginType: loginType.value,
    },
    async () => {
      await router.replace(resolveRedirectPath());
    },
  );
}

async function handleVerifySuccess({
  captchaVerification,
}: {
  captchaVerification: string;
}) {
  try {
    const values = await loginRef.value.getFormApi().getValues();

    await ensureLoginTenant(values);

    await authStore.authLogin(
      'username',
      {
        ...values,
        captchaVerification,
        loginType: loginType.value,
      },
      async () => {
        await router.replace(resolveRedirectPath());
      },
    );
  } catch (error) {
    console.error('Error in handleLogin:', error);
  }
}

const redirect = route.query?.redirect;

async function handleThirdLogin(type: number) {
  if (type <= 0) {
    return;
  }

  try {
    // 计算 redirectUri
    // tricky: type、redirect 需要先 encode 一次，否则钉钉回调会丢失。配合 social-login.vue#getUrlValue() 使用
    const redirectUri = `${
      location.origin
    }/auth/social-login?${encodeURIComponent(
      `type=${type}&redirect=${redirect || '/'}`,
    )}`;

    window.location.href = await socialAuthRedirect(type, redirectUri);
  } catch (error) {
    console.error('第三方登录处理失败:', error);
  }
}

/** 组件挂载时根据域名识别租户 */
onMounted(() => {
  fetchTenantInfo();
});

const formSchema = computed((): VbenFormSchema[] => {
  return [
    {
      component: 'VbenInput',
      componentProps: {
        placeholder: $t('authentication.tenantInputTip'),
      },
      fieldName: 'tenantName',
      label: $t('authentication.tenant'),
      rules: z.string().min(1, {
        message: $t('authentication.tenantTip'),
      }),
      dependencies: {
        triggerFields: ['tenantId'],
        if: tenantEnable,
      },
    });
  }

  schema.push(
    {
      component: 'VbenInput',
      componentProps: {
        placeholder: $t('authentication.usernameTip'),
      },
      fieldName: 'username',
      label: $t('authentication.username'),
      rules: z
        .string()
        .min(1, {
          message: $t('authentication.usernameTip'),
        })
        .default(import.meta.env.VITE_APP_DEFAULT_USERNAME),
    },
    {
      component: 'VbenInputPassword',
      componentProps: {
        placeholder: $t('authentication.passwordTip'),
      },
      fieldName: 'password',
      label: $t('authentication.password'),
      rules: z
        .string()
        .min(1, {
          message: $t('authentication.passwordTip'),
        })
        .default(import.meta.env.VITE_APP_DEFAULT_PASSWORD),
    },
  ];
});
</script>

<template>
  <div>
    <AuthenticationLogin
      ref="loginRef"
      :form-schema="formSchema"
      :loading="authStore.loginLoading"
      @submit="handleLogin"
      @third-login="handleThirdLogin"
    />
    <Verification
      ref="verifyRef"
      v-if="captchaEnable"
      :captcha-type="captchaType"
      :check-captcha-api="checkCaptcha"
      :get-captcha-api="getCaptcha"
      :img-size="{ width: '400px', height: '200px' }"
      mode="pop"
      @on-success="handleVerifySuccess"
    />
  </div>
</template>

<style scoped>
.login-container {
  width: 100%;
}

.login-type-tabs {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px;
  margin-bottom: 1.5rem;
  padding: 4px;
  border: 1px solid hsl(var(--border));
  border-radius: 10px;
  background: hsl(var(--muted) / 40%);
}

.login-type-tab {
  min-height: 40px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: hsl(var(--muted-foreground));
  cursor: pointer;
  font-size: 0.95rem;
  transition:
    background-color 160ms ease,
    color 160ms ease,
    box-shadow 160ms ease;
}

.login-type-tab[aria-selected='true'] {
  background: hsl(var(--background));
  box-shadow: 0 1px 3px hsl(var(--foreground) / 12%);
  color: hsl(var(--foreground));
  font-weight: 600;
}

.login-type-tab:focus-visible {
  outline: 2px solid hsl(var(--ring));
  outline-offset: 1px;
}
</style>
