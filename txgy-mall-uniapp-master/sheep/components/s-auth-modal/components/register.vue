<!-- 服务端能力决定注册方式，图形挑战不用于短信登录或找回密码。 -->
<template>
  <view>
    <view class="head-box ss-m-b-60">
      <view class="ss-flex ss-m-b-20">
        <view class="head-title head-title-line head-title-animation">注册账号</view>
        <view class="head-title-active ss-m-r-40" @tap="showAuthModal('accountLogin')">
          账号登录
        </view>
      </view>
      <view class="head-subtitle">注册成功后完善企业资料，审核通过后取得企业权限</view>
      <view v-if="authError" @tap="retryAuth">{{ authError }}</view>
      <view v-else-if="!imageRegistration">注册暂未开放，请等待商城升级完成</view>
      <view v-else-if="!authAvailable">认证服务准备中或暂未开放</view>
    </view>

    <uni-forms
      v-if="imageRegistration"
      ref="registerRef"
      v-model="state.model"
      :rules="state.rules"
      validateTrigger="bind"
      labelWidth="140"
      labelAlign="center"
    >
      <view v-if="registrationOptions?.enabled" class="registration-tenant">
        <view>归属租户（可不选）</view>
        <picker :range="tenantNames" :value="tenantIndex" :disabled="submitting" @change="selectTenant">
          <view class="tenant-picker">{{ selectedTenant && tenantIndex === 0 ? '原租户已不可注册，请重新选择' : tenantNames[tenantIndex] }} ▾</view>
        </picker>
        <view class="tenant-notice">{{ choiceNotice }}</view>
        <view v-if="choiceError" class="tenant-error">{{ choiceError }}</view>
        <s-tenant-entry v-if="choiceError && registrationTarget" :target="registrationTarget" purpose="register" />
      </view>
      <uni-forms-item name="loginName" label="登录名">
        <uni-easyinput v-model="state.model.loginName" maxlength="20" placeholder="6-20位，字母开头，可用数字/下划线" :inputBorder="false" :disabled="submitting" />
      </uni-forms-item>
      <uni-forms-item name="contactMobile" label="联系手机">
        <uni-easyinput v-model="state.model.contactMobile" type="number" maxlength="11" placeholder="可选，仅用于联系" :inputBorder="false" />
        <view class="field-notice">此号码未验证，不能用于登录或找回密码</view>
      </uni-forms-item>
      <uni-forms-item name="nickname" label="昵称">
        <uni-easyinput v-model="state.model.nickname" maxlength="30" placeholder="可选" :inputBorder="false" />
      </uni-forms-item>
      <uni-forms-item name="memberIdentity" label="会员身份">
        <radio-group @change="state.model.memberIdentity = $event.detail.value">
          <label><radio value="buyer" :checked="state.model.memberIdentity === 'buyer'" />采购商</label>
          <label><radio value="supplier" :checked="state.model.memberIdentity === 'supplier'" />供应商</label>
        </radio-group>
      </uni-forms-item>
      <uni-forms-item name="password" label="密码">
        <uni-easyinput v-model="state.model.password" type="password" maxlength="16" placeholder="请输入6-16位密码" :inputBorder="false" />
      </uni-forms-item>
      <uni-forms-item name="confirmPassword" label="确认密码">
        <uni-easyinput v-model="state.model.confirmPassword" type="password" maxlength="16" placeholder="请再次输入密码" :inputBorder="false" />
      </uni-forms-item>
      <uni-forms-item name="imageCode" label="图片验证码">
        <view class="captcha-controls">
          <image v-if="imageChallenge && imageExpiresIn > 0" :src="imageChallenge.imageDataUrl" class="captcha-image" mode="aspectFit" @tap="loadImage" />
          <button class="ss-reset-button code-btn code-btn-start" :disabled="!authAvailable || submitting || imageSending || imageCountdown > 0" @tap="loadImage">
            {{ imageSending ? '加载中' : imageCountdown ? imageCountdown + ' 秒' : imageChallenge ? '换一张' : '获取图片' }}
          </button>
        </view>
        <view class="field-notice">{{ imageChallenge && !imageExpiresIn ? '图片已过期，请换一张' : '先填写登录名，再获取图片；点击图片可刷新' }}</view>
        <uni-easyinput v-model="state.model.imageCode" maxlength="5" placeholder="请输入图片中的5位字符" :inputBorder="false" />
      </uni-forms-item>
      <button class="ss-reset-button register-submit" :disabled="!authAvailable || submitting || imageSending" @tap="submit">{{ submitting ? '注册中' : '注册并完善企业资料' }}</button>
    </uni-forms>
  </view>
</template>

<script setup>
  import { reactive, ref, unref, computed } from 'vue';
  import sheep from '@/sheep';
  import { closeAuthModal, showAuthModal } from '@/sheep/hooks/useModal';
  import { useMallAuthForm, mallAuthPassword as password } from '@/sheep/hooks/useMallAuthForm';
  import { normalizeLoginName } from '@/sheep/helper/image-registration.mjs';

  const registerRef = ref(null);
  const emits = defineEmits(['onConfirm']);
  const props = defineProps({
    agreeStatus: { type: [Boolean, null], default: null },
  });

  const state = reactive({
    model: { loginName: '', contactMobile: '', imageCode: '', nickname: '', password: '', confirmPassword: '', memberIdentity: 'buyer' },
    rules: {
      loginName: { rules: [{ validateFunction: (rule, value, data, callback) => {
        try { normalizeLoginName(value); return true; } catch (error) { callback(error.message); return false; }
      } }] },
      contactMobile: { rules: [{ validateFunction: (rule, value) => !value || /^1[3-9]\d{9}$/.test(value), errorMessage: '请输入正确的联系手机号' }] },
      nickname: { rules: [{ maxLength: 30, errorMessage: '昵称不能超过30位' }] },
      password,
      confirmPassword: { rules: [{ required: true, errorMessage: '请再次输入密码' }, { validateFunction: (rule, value) => value === state.model.password, errorMessage: '两次输入的密码不一致' }] },
      imageCode: { rules: [{ required: true, errorMessage: '请输入图片验证码' }, { pattern: /^[a-z0-9]{5}$/i, errorMessage: '请输入图片中的5位字符' }] },
    },
  });

  const { error: authError, available: authAvailable, submitting,
    refresh: retryAuth, submit: submitAuth,
    imageRegistration, imageChallenge, imageSending, imageCountdown, imageExpiresIn, loadImage,
    registrationOptions, selectedTenant, registrationTarget, choiceNotice, choiceError } = useMallAuthForm(state.model, 'REGISTER');
  const tenantNames = computed(() => ['不选：平台自营公共商城', ...(registrationOptions.value?.tenants || []).map(item => item.name)]);
  const tenantIndex = computed(() => selectedTenant.value ? 1 + (registrationOptions.value?.tenants || []).findIndex(item => item.tenantId === selectedTenant.value) : 0);
  function selectTenant(event) {
    const index = Number(event.detail.value);
    if (Number.isInteger(index) && index >= 0 && index < tenantNames.value.length) selectedTenant.value = index ? registrationOptions.value.tenants[index - 1].tenantId : '';
  }

  async function submit() {
    const valid = await unref(registerRef).validate().catch(() => null);
    if (!valid) return;
    if (props.agreeStatus !== true) {
      emits('onConfirm', true);
      sheep.$helper.toast(props.agreeStatus === false ? '您已拒绝协议，无法继续注册' : '请选择是否同意协议');
      return;
    }
    try {
      const result = await submitAuth('register', { ...state.model, agree: true });
      if (result?.code === 0) {
        closeAuthModal();
        sheep.$router.go('/pages/user/enterprise-profile');
      }
    } catch (error) {
      // request 层已展示错误提示。
    }
  }
</script>

<style lang="scss" scoped>
  @import '../index.scss';
  .registration-tenant { padding: 16rpx 24rpx; margin-bottom: 20rpx; background: #f4f8f7; border-radius: 12rpx; }
  .tenant-picker { padding: 18rpx 0; color: #0f766e; }
  .tenant-notice { font-size: 24rpx; line-height: 1.6; color: #52616a; }
  .tenant-error { font-size: 24rpx; color: #a34d18; line-height: 1.6; margin-top: 10rpx; }
  .field-notice { padding: 4rpx 0 12rpx; font-size: 22rpx; line-height: 1.5; color: #65736f; }
  .captcha-controls { display: flex; align-items: center; gap: 18rpx; min-height: 84rpx; }
  .captcha-image { width: 280rpx; height: 88rpx; border: 1rpx solid #dbe4e1; border-radius: 8rpx; }
  .register-submit { width: 100%; margin-top: 22rpx; padding: 18rpx; color: #fff; font-size: 28rpx; border-radius: 12rpx; background: var(--ui-BG-Main); }
  .register-submit[disabled] { opacity: .55; }
</style>
