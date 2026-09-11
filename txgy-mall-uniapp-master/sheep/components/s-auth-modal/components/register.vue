<!-- 手机号注册 -->
<template>
  <view>
    <view class="head-box ss-m-b-60">
      <view class="ss-flex ss-m-b-20">
        <view class="head-title head-title-line head-title-animation">注册账号</view>
        <view class="head-title-active ss-m-r-40" @tap="showAuthModal('accountLogin')">
          账号登录
        </view>
      </view>
      <view class="head-subtitle">注册成功后将自动登录商城</view>
    </view>

    <uni-forms
      ref="registerRef"
      v-model="state.model"
      :rules="state.rules"
      validateTrigger="bind"
      labelWidth="140"
      labelAlign="center"
    >
      <uni-forms-item name="mobile" label="手机号">
        <uni-easyinput v-model="state.model.mobile" placeholder="请输入手机号" :inputBorder="false" type="number" />
      </uni-forms-item>
      <uni-forms-item name="code" label="验证码">
        <uni-easyinput v-model="state.model.code" placeholder="请输入验证码" :inputBorder="false" type="number" maxlength="6">
          <template #right>
            <button
              class="ss-reset-button code-btn code-btn-start"
              :disabled="props.agreeStatus !== true"
              @tap="sendCode"
            >
              {{ getSmsTimer('register') }}
            </button>
          </template>
        </uni-easyinput>
      </uni-forms-item>
      <uni-forms-item name="nickname" label="昵称">
        <uni-easyinput v-model="state.model.nickname" placeholder="请输入昵称" :inputBorder="false" />
      </uni-forms-item>
      <uni-forms-item name="password" label="密码">
        <uni-easyinput v-model="state.model.password" type="password" placeholder="请输入密码" :inputBorder="false">
          <template #right>
            <button class="ss-reset-button login-btn-start" @tap="submit">注册</button>
          </template>
        </uni-easyinput>
      </uni-forms-item>
    </uni-forms>
  </view>
</template>

<script setup>
  import { reactive, ref, unref } from 'vue';
  import sheep from '@/sheep';
  import { code, mobile, password } from '@/sheep/validate/form';
  import { closeAuthModal, getSmsCode, getSmsTimer, showAuthModal } from '@/sheep/hooks/useModal';
  import AuthUtil from '@/sheep/api/member/auth';

  const registerRef = ref(null);
  const emits = defineEmits(['onConfirm']);
  const props = defineProps({
    agreeStatus: { type: [Boolean, null], default: null },
  });

  const state = reactive({
    model: { mobile: '', code: '', nickname: '', password: '' },
    rules: {
      mobile,
      code,
      nickname: { rules: [{ required: true, errorMessage: '请输入昵称' }] },
      password,
    },
  });

  function sendCode() {
    if (props.agreeStatus !== true) {
      emits('onConfirm', true);
      sheep.$helper.toast(props.agreeStatus === false ? '您已拒绝协议，无法发送验证码' : '请选择是否同意协议');
      return;
    }
    getSmsCode('register', state.model.mobile);
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
      const result = await AuthUtil.register({ ...state.model, agree: true });
      if (result?.code === 0) {
        closeAuthModal();
      }
    } catch (error) {
      // request 层已展示错误提示。
    }
  }
</script>

<style lang="scss" scoped>
  @import '../index.scss';
</style>
