<script lang="ts" setup>
import type { VbenFormSchema } from '@vben/common-ui';

import { computed, h, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { AuthenticationRegister, Verification, z } from '@vben/common-ui';
import { isCaptchaEnable } from '@vben/hooks';
import { $t } from '@vben/locales';
import { useAccessStore } from '@vben/stores';

import { checkCaptcha, getCaptcha } from '#/api/core/auth';
import { useAuthStore } from '#/store';

defineOptions({ name: 'Register' });

const router = useRouter();
const accessStore = useAccessStore();
const authStore = useAuthStore();
const captchaEnable = isCaptchaEnable();

const registerRef = ref();
const verifyRef = ref();
const captchaType = 'blockPuzzle';

function clearPersonalTenantContext() {
  accessStore.setTenantId(null);
  accessStore.clearVisitTenantId();
}

async function handleRegister(values: any) {
  clearPersonalTenantContext();
  if (captchaEnable) {
    verifyRef.value.show();
    return;
  }
  await authStore.authLogin(
    'register',
    { ...values, registerType: 'personal' },
    async () => {
      await router.replace('/portal/home');
    },
  );
}

async function handleVerifySuccess({ captchaVerification }: any) {
  try {
    const values = await registerRef.value.getFormApi().getValues();
    clearPersonalTenantContext();
    await authStore.authLogin(
      'register',
      {
        ...values,
        captchaVerification,
        registerType: 'personal',
      },
      async () => {
        await router.replace('/portal/home');
      },
    );
  } catch (error) {
    console.error('注册验证失败:', error);
  }
}

onMounted(clearPersonalTenantContext);

const formSchema = computed((): VbenFormSchema[] => {
  return [
    {
      component: 'VbenInput',
      componentProps: { placeholder: $t('authentication.usernameTip') },
      fieldName: 'username',
      label: $t('authentication.username'),
      rules: z.string().min(1, { message: $t('authentication.usernameTip') }),
    },
    {
      component: 'VbenInput',
      componentProps: { placeholder: $t('authentication.nicknameTip') },
      fieldName: 'nickname',
      label: $t('authentication.nickname'),
      rules: z.string().min(1, { message: $t('authentication.nicknameTip') }),
    },
    {
      component: 'VbenInputPassword',
      componentProps: {
        passwordStrength: true,
        placeholder: $t('authentication.password'),
      },
      fieldName: 'password',
      label: $t('authentication.password'),
      renderComponentContent() {
        return {
          strengthText: () => $t('authentication.passwordStrength'),
        };
      },
      rules: z.string().min(1, { message: $t('authentication.passwordTip') }),
    },
    {
      component: 'VbenInputPassword',
      componentProps: {
        placeholder: $t('authentication.confirmPassword'),
      },
      dependencies: {
        rules(values) {
          const { password } = values;
          return z
            .string({ required_error: $t('authentication.passwordTip') })
            .min(1, { message: $t('authentication.passwordTip') })
            .refine((value) => value === password, {
              message: $t('authentication.confirmPasswordTip'),
            });
        },
        triggerFields: ['password'],
      },
      fieldName: 'confirmPassword',
      label: $t('authentication.confirmPassword'),
    },
    {
      component: 'VbenCheckbox',
      fieldName: 'agreePolicy',
      renderComponentContent: () => ({
        default: () =>
          h('span', [
            $t('authentication.agree'),
            h(
              'a',
              {
                class: 'vben-link ml-1',
                href: '',
                onClick: (event: Event) => event.preventDefault(),
              },
              `${$t('authentication.privacyPolicy')} & ${$t('authentication.terms')}`,
            ),
          ]),
      }),
      rules: z.boolean().refine((value) => !!value, {
        message: $t('authentication.agreeTip'),
      }),
    },
  ];
});
</script>

<template>
  <div class="register-container">
    <AuthenticationRegister
      ref="registerRef"
      :form-schema="formSchema"
      :loading="authStore.loginLoading"
      @submit="handleRegister"
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
.register-container {
  width: 100%;
}
</style>
