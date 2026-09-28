<!-- 账号密码登录 accountLogin  -->
<template>
  <view>
    <!-- 标题栏 -->
    <view class="head-box ss-m-b-60 ss-flex-col">
      <view class="ss-flex ss-m-b-20">
        <view v-if="!platformLogin" class="head-title-active head-title-line" @tap="showAuthModal('smsLogin')">
          短信登录
        </view>
        <view class="head-title ss-m-r-40 head-title-animation">账号登录</view>
      </view>
      <view class="head-subtitle">{{ platformLogin ? '平台统一账号登录，进入后可浏览已开放租户商城' : loginNameEnabled ? '使用登录名或已绑定的原手机号登录' : '使用原手机号和密码登录' }}</view>
      <view class="register-link" @tap="showAuthModal('register')">没有账号？立即注册</view>
      <view v-if="authError" @tap="retryAuth">{{ authError }}</view>
      <view v-else-if="!authAvailable">认证服务准备中或暂未开放</view>
    </view>

    <!-- 表单项 -->
    <uni-forms
      ref="accountLoginRef"
      v-model="state.model"
      :rules="state.rules"
      validateTrigger="bind"
      labelWidth="140"
      labelAlign="center"
    >
      <uni-forms-item name="account" label="账号">
        <uni-easyinput :placeholder="loginNameEnabled ? '登录名 / 原手机号' : '请输入原手机号'" v-model="state.model.account" maxlength="20" :inputBorder="false">
          <template v-slot:right>
            <button v-if="!platformLogin" class="ss-reset-button forgot-btn" @tap="showAuthModal('resetPassword')">
              忘记密码
            </button>
          </template>
        </uni-easyinput>
      </uni-forms-item>

      <uni-forms-item name="password" label="密码">
        <uni-easyinput
          type="password"
          placeholder="请输入密码"
          v-model="state.model.password"
          :inputBorder="false"
        >
          <template v-slot:right>
            <button class="ss-reset-button login-btn-start" :disabled="!authAvailable || submitting" @tap="accountLoginSubmit">登录</button>
          </template>
        </uni-easyinput>
      </uni-forms-item>
    </uni-forms>
  </view>
</template>

<script setup>
  import { ref, reactive, unref } from 'vue';
  import sheep from '@/sheep';
  import { showAuthModal, closeAuthModal } from '@/sheep/hooks/useModal';
  import { useMallAuthForm, mallLoginPassword as password } from '@/sheep/hooks/useMallAuthForm';
  import { normalizeLoginName } from '@/sheep/helper/image-registration.mjs';

  const accountLoginRef = ref(null);

  const emits = defineEmits(['onConfirm']);

  const props = defineProps({
    agreeStatus: {
      type: [Boolean, null],
      default: null,
    },
  });

  // 数据
  const state = reactive({
    model: {
      account: '', // 登录名或原手机号
      password: '', // 密码
    },
    rules: {
      account: { rules: [{ required: true, errorMessage: '请输入账号' }, { validateFunction: (rule, value, data, callback) => {
        if (/^1[3-9]\d{9}$/.test(value?.trim())) return true;
        if (loginNameEnabled.value) {
          try { normalizeLoginName(value); return true; } catch (error) { callback(error.message); return false; }
        }
        callback('当前商城请使用原手机号登录'); return false;
      } }] },
      password,
    },
  });

  const { error: authError, available: authAvailable, loginNameEnabled, platformLogin, submitting, refresh: retryAuth,
    submit: submitAuth } = useMallAuthForm(state.model, 'PASSWORD_LOGIN');

  // 账号登录
  async function accountLoginSubmit() {
    // 表单验证
    const validate = await unref(accountLoginRef)
      .validate()
      .catch(() => null);
    if (!validate) return;

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
      const result = await submitAuth('login', { ...state.model });
      if (result?.code === 0) {
        closeAuthModal();
      }
    } catch (error) {
      // request 层已展示错误提示，避免网络异常导致授权弹窗报错。
    }
  }
</script>

<style lang="scss" scoped>
  @import '../index.scss';

  .register-link {
    margin-top: 18rpx;
    color: var(--ui-BG-Main);
    font-size: 26rpx;
    text-align: right;
  }
</style>
