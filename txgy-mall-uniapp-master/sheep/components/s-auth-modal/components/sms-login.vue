<!-- 短信登录 - smsLogin  -->
<template>
  <view>
    <!-- 标题栏 -->
    <view class="head-box ss-m-b-60">
      <view class="ss-flex ss-m-b-20">
        <view class="head-title head-title-line head-title-animation">短信登录</view>
        <view class="head-title-active ss-m-r-40" @tap="showAuthModal('accountLogin')">
          账号登录
        </view>
      </view>
      <view class="head-subtitle">{{ newProtocol ? '短信登录仅限已有账号，新用户请先注册' : '未注册的手机号，验证后自动注册账号' }}</view>
      <view v-if="authError" @tap="retryAuth">{{ authError }}</view>
      <view v-else-if="!authAvailable">认证服务准备中或暂未开放</view>
    </view>

    <!-- 表单项 -->
    <uni-forms
      ref="smsLoginRef"
      v-model="state.model"
      :rules="state.rules"
      validateTrigger="bind"
      labelWidth="140"
      labelAlign="center"
    >
      <uni-forms-item name="mobile" label="手机号">
        <uni-easyinput
          placeholder="请输入手机号"
          v-model="state.model.mobile"
          :inputBorder="false"
          type="number"
        >
          <template v-slot:right>
            <button
              class="ss-reset-button code-btn code-btn-start"
              :disabled="!authAvailable || sending || countdown > 0 || props.agreeStatus === false"
              :class="{ 'code-btn-end': state.isMobileEnd || props.agreeStatus === false }"
              @tap="checkAgreementAndGetSmsCode"
            >
              {{ codeText }}
            </button>
          </template>
        </uni-easyinput>
      </uni-forms-item>

      <uni-forms-item name="code" label="验证码">
        <uni-easyinput
          placeholder="请输入验证码"
          v-model="state.model.code"
          :inputBorder="false"
          type="number"
          maxlength="6"
        >
          <template v-slot:right>
            <button class="ss-reset-button login-btn-start" :disabled="!authAvailable || submitting" @tap="smsLoginSubmit"> 登录 </button>
          </template>
        </uni-easyinput>
      </uni-forms-item>
    </uni-forms>
  </view>
</template>

<script setup>
  import { ref, reactive, unref } from 'vue';
  import sheep from '@/sheep';
  import { code, mobile } from '@/sheep/validate/form';
  import { showAuthModal, closeAuthModal } from '@/sheep/hooks/useModal';
  import { useMallAuthForm } from '@/sheep/hooks/useMallAuthForm';

  const smsLoginRef = ref(null);

  const emits = defineEmits(['onConfirm']);

  const props = defineProps({
    agreeStatus: {
      type: [Boolean, null],
      default: null,
    },
  });

  // 数据
  const state = reactive({
    isMobileEnd: false, // 手机号输入完毕
    codeText: '获取验证码',
    model: {
      mobile: '', // 手机号
      code: '', // 验证码
    },
    rules: {
      code,
      mobile,
    },
  });

  const { error: authError, available: authAvailable, newProtocol, codeText, countdown, sending, submitting,
    refresh: retryAuth, send: sendAuthCode, submit: submitAuth } = useMallAuthForm(state.model, 'SMS_LOGIN');

  // 检查协议并获取验证码
  function checkAgreementAndGetSmsCode() {
    if (props.agreeStatus !== true) {
      emits('onConfirm', true);
      if (props.agreeStatus === false) {
        sheep.$helper.toast('您已拒绝协议，无法发送验证码');
      } else {
        sheep.$helper.toast('请选择是否同意协议');
      }
      return;
    }
    sendAuthCode();
  }

  // 短信登录
  async function smsLoginSubmit() {
    // 参数校验
    const validate = await unref(smsLoginRef)
      .validate()
      .catch(() => null);
    if (!validate) {
      return;
    }
    // 检查协议状态
    if (props.agreeStatus !== true) {
      emits('onConfirm', true);
      if (props.agreeStatus === false) {
        sheep.$helper.toast('您已拒绝协议，无法继续登录');
      } else {
        sheep.$helper.toast('请选择是否同意协议');
      }
      return;
    }
    // 提交数据
    try {
      const result = await submitAuth('sms-login', { ...state.model });
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
