<script lang="ts" setup>
import type { VbenFormSchema } from '@vben/common-ui';

import type { AuthApi } from '#/api/core/auth';

import { computed, h, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { AuthenticationRegister, Verification, z } from '@vben/common-ui';
import { isCaptchaEnable, isTenantEnable } from '@vben/hooks';
import { $t } from '@vben/locales';
import { useAccessStore } from '@vben/stores';

import {
  checkCaptcha,
  getCaptcha,
  getTenantByWebsite,
  getTenantSimpleList,
} from '#/api/core/auth';
import { useAuthStore } from '#/store';

defineOptions({ name: 'Register' });

type RegisterType = 'enterprise' | 'personal';

const router = useRouter();
const accessStore = useAccessStore();
const authStore = useAuthStore();
const tenantEnable = isTenantEnable();
const captchaEnable = isCaptchaEnable();

const registerType = ref<RegisterType>('personal');
const registerRef = ref();
const verifyRef = ref();
const captchaType = 'blockPuzzle';
const tenantList = ref<AuthApi.TenantResult[]>([]);

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
    accessStore.setTenantId(tenantId);
    registerRef.value
      ?.getFormApi()
      .setFieldValue('tenantId', tenantId?.toString());
  } catch (error) {
    console.error('获取租户列表失败:', error);
  }
}

async function handleRegister(values: any) {
  if (captchaEnable) {
    verifyRef.value.show();
    return;
  }
  await authStore.authLogin(
    'register',
    { ...values, registerType: registerType.value },
    async () => {
      await router.replace('/portal/home');
    },
  );
}

async function handleVerifySuccess({ captchaVerification }: any) {
  try {
    await authStore.authLogin(
      'register',
      {
        ...(await registerRef.value.getFormApi().getValues()),
        captchaVerification,
        registerType: registerType.value,
      },
      async () => {
        await router.replace('/portal/home');
      },
    );
  } catch (error) {
    console.error('注册验证失败:', error);
  }
}

onMounted(fetchTenantList);

const formSchema = computed((): VbenFormSchema[] => {
  const schema: VbenFormSchema[] = [];
  if (registerType.value === 'enterprise') {
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
        trigger(values) {
          if (values.tenantId) {
            accessStore.setTenantId(Number(values.tenantId));
          }
        },
      },
    });
  }

  schema.push(
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
  );

  return schema;
});
</script>

<template>
  <div class="register-container">
    <div class="register-type-tabs" role="tablist" aria-label="注册类型">
      <button
        v-for="item in [
          { key: 'personal', label: '个人注册' },
          { key: 'enterprise', label: '企业注册' },
        ]"
        :key="item.key"
        :aria-selected="registerType === item.key"
        class="register-type-tab"
        role="tab"
        type="button"
        @click="registerType = item.key as RegisterType"
      >
        {{ item.label }}
      </button>
    </div>

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
.register-type-tabs {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px;
  margin-bottom: 1.5rem;
  padding: 4px;
  border: 1px solid hsl(var(--border));
  border-radius: 10px;
  background: hsl(var(--muted) / 40%);
}
.register-type-tab {
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
.register-type-tab[aria-selected='true'] {
  background: hsl(var(--background));
  box-shadow: 0 1px 3px hsl(var(--foreground) / 12%);
  color: hsl(var(--foreground));
  font-weight: 600;
}
.register-type-tab:focus-visible {
  outline: 2px solid hsl(var(--ring));
  outline-offset: 1px;
}
</style>
