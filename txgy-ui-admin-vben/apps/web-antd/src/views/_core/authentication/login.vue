<script lang="ts" setup>
import type { VbenFormSchema } from '@vben/common-ui';

import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { AuthenticationLogin, Verification, z } from '@vben/common-ui';
import { isCaptchaEnable, isTenantEnable } from '@vben/hooks';
import { $t } from '@vben/locales';
import { useAccessStore } from '@vben/stores';
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
  if (!tenantEnable) {
    return;
  }
  try {
    const websiteTenant = await getTenantByWebsite(window.location.hostname);
    if (websiteTenant?.id) {
      accessStore.setTenantId(websiteTenant.id);
      loginRef.value
        .getFormApi()
        .setFieldValue('tenantName', websiteTenant.name);
    }
  } catch (error) {
    console.error('获取租户信息失败:', error);
  }
}

/**
 * 根据手动输入的租户名，解析出租户编号。
 * 成功返回 true 并写入 accessStore；失败返回 false（已做提示）。
 */
async function resolveTenantId(tenantName: string): Promise<boolean> {
  // 登录页始终展示租户输入框，因此这里必须强制解析租户名并写入 accessStore，
  // 否则会沿用 localStorage 中上一次登录遗留的租户编号，导致登录到错误租户
  const name = tenantName?.trim();
  if (!name) {
    message.warning($t('authentication.tenantInputTip'));
    return false;
  }
  try {
    const tenantId = await getTenantIdByName(name);
    if (!tenantId) {
      message.error($t('authentication.tenantError'));
      return false;
    }
    accessStore.setTenantId(tenantId);
    return true;
  } catch (error) {
    console.error('解析租户编号失败:', error);
    return false;
  }
}

/** 校验租户后执行登录 */
async function doLogin(values: any) {
  if (!(await resolveTenantId(values?.tenantName))) {
    return;
  }
  // tenantName 仅用于解析租户编号，不随登录参数提交后端
  const { tenantName, ...loginParams } = values;
  await authStore.authLogin('username', loginParams);
}

/** 处理登录 */
async function handleLogin(values: any) {
  // 如果开启验证码，则先验证验证码
  if (captchaEnable) {
    verifyRef.value.show();
    return;
  }
  await doLogin(values);
}

/** 验证码通过，执行登录 */
async function handleVerifySuccess({ captchaVerification }: any) {
  try {
    const values = await loginRef.value.getFormApi().getValues();
    await doLogin({ ...values, captchaVerification });
  } catch (error) {
    console.error('Error in handleLogin:', error);
  }
}

/** 处理第三方登录 */
const redirect = query?.redirect;
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

    // 进行跳转
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
      rules: z.string().min(1, { message: $t('authentication.tenantInputTip') }),
    },
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
