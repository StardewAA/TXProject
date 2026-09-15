<script lang="ts" setup>
import type { VbenFormSchema } from '@vben/common-ui';

import type { AuthApi } from '#/api/core/auth';

import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { AuthenticationLogin, Verification, z } from '@vben/common-ui';
import { isCaptchaEnable, isTenantEnable } from '@vben/hooks';
import { $t } from '@vben/locales';
import { useAccessStore, useUserStore } from '@vben/stores';
import { preferences } from '@vben/preferences';

import {
  checkCaptcha,
  getCaptcha,
  getTenantByWebsite,
  getTenantSimpleList,
  socialAuthRedirect,
} from '#/api/core/auth';
import { useAuthStore } from '#/store';
import { hasBackendAccess, type LoginType } from './login-access';

defineOptions({ name: 'Login' });

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const accessStore = useAccessStore();
const userStore = useUserStore();
const tenantEnable = isTenantEnable();
const captchaEnable = isCaptchaEnable();

const loginType = ref<LoginType>('enterprise');
const loginRef = ref();
const verifyRef = ref();
const captchaType = 'blockPuzzle';

const tenantList = ref<AuthApi.TenantResult[]>([]);

function handleLoginTypeChange(type: LoginType) {
  loginType.value = type;
  if (type === 'personal') {
    // 个人账号固定使用后端保留的 tenant_id=0，不能沿用登录页初始化的企业租户。
    loginRef.value?.getFormApi().setFieldValue('tenantId', undefined);
  }
}

/** 获取租户列表，并默认选中当前域名对应的租户。 */
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
      tenantId = websiteTenant.id;
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
    console.error('获取租户列表失败:', error);
  }
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

  return redirect.startsWith('/portal/') ? redirect : '/portal/home';
}

async function handleLogin(values: any) {
  if (captchaEnable) {
    verifyRef.value.show();
    return;
  }
  await authStore.authLogin(
    'username',
    { ...values, loginType: loginType.value },
    async () => {
      await router.replace(resolveRedirectPath());
    },
  );
}

async function handleVerifySuccess({ captchaVerification }: any) {
  try {
    await authStore.authLogin(
      'username',
      {
        ...(await loginRef.value.getFormApi().getValues()),
        captchaVerification,
        loginType: loginType.value,
      },
      async () => {
        await router.replace(resolveRedirectPath());
      },
    );
  } catch (error) {
    console.error('登录验证失败:', error);
  }
}

const redirect = route.query?.redirect;
async function handleThirdLogin(type: number) {
  if (type <= 0) {
    return;
  }
  try {
    const redirectUri = `${location.origin}/auth/social-login?${encodeURIComponent(
      `type=${type}&redirect=${redirect || '/'}`,
    )}`;
    window.location.href = await socialAuthRedirect(type, redirectUri);
  } catch (error) {
    console.error('第三方登录处理失败:', error);
  }
}

onMounted(fetchTenantList);

const formSchema = computed((): VbenFormSchema[] => {
  const schema: VbenFormSchema[] = [];

  if (loginType.value === 'enterprise') {
    schema.push({
      component: 'VbenSelect',
      componentProps: {
        options: tenantList.value.map((item) => ({
          label: item.name,
          value: item.id.toString(),
        })),
        placeholder: $t('authentication.tenantTip'),
      },
      fieldName: 'tenantId',
      label: $t('authentication.tenant'),
      rules: z.string().min(1, { message: $t('authentication.tenantTip') }),
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
        .min(1, { message: $t('authentication.usernameTip') })
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
        .min(1, { message: $t('authentication.passwordTip') })
        .default(import.meta.env.VITE_APP_DEFAULT_PASSWORD),
    },
  );

  return schema;
});
</script>

<template>
  <div class="login-container">
    <div class="login-type-tabs" role="tablist" aria-label="登录类型">
      <button
        v-for="item in [
          { key: 'enterprise', label: '企业登录' },
          { key: 'personal', label: '个人登录' },
        ]"
        :key="item.key"
        :aria-selected="loginType === item.key"
        class="login-type-tab"
        role="tab"
        type="button"
        @click="handleLoginTypeChange(item.key as LoginType)"
      >
        {{ item.label }}
      </button>
    </div>

    <AuthenticationLogin
      ref="loginRef"
      :form-schema="formSchema"
      :loading="authStore.loginLoading"
      @submit="handleLogin"
      @third-login="handleThirdLogin"
    />

    <Verification
      v-if="captchaEnable"
      ref="verifyRef"
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
