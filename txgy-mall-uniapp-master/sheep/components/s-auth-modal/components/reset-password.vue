<!-- 重置密码（未登录时）  -->
<template>
  <view>
    <!-- 标题栏 -->
    <view class="head-box ss-m-b-60">
      <view class="head-title ss-m-b-20">重置密码</view>
      <view class="head-subtitle">仅已验证并绑定的手机号可使用短信重置。新注册填写的联系号码不能用于找回；无法验证时请联系平台申请人工核验。</view>
      <view v-if="authError" @tap="retryAuth">{{ authError }}</view>
      <view v-else-if="!authAvailable">认证服务准备中或暂未开放</view>
    </view>

    <!-- 表单项 -->
    <uni-forms
      ref="resetPasswordRef"
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
          type="number"
          :inputBorder="false"
        >
          <template v-slot:right>
            <button
              class="ss-reset-button code-btn code-btn-start"
              :disabled="!authAvailable || sending || countdown > 0"
              :class="{ 'code-btn-end': state.isMobileEnd }"
              @tap="sendAuthCode"
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
          type="number"
          maxlength="6"
          :inputBorder="false"
        />
      </uni-forms-item>

      <uni-forms-item name="password" label="密码">
        <uni-easyinput
          type="password"
          placeholder="请输入密码"
          v-model="state.model.password"
          :inputBorder="false"
        >
          <template v-slot:right>
            <button class="ss-reset-button login-btn-start" :disabled="!authAvailable || submitting" @tap="resetPasswordSubmit">
              确认
            </button>
          </template>
        </uni-easyinput>
      </uni-forms-item>
    </uni-forms>

    <button v-if="!isLogin" class="ss-reset-button type-btn" @tap="showAuthModal('accountLogin')">
      返回登录
    </button>
  </view>
</template>

<script setup>
  import { computed, ref, reactive, unref } from 'vue';
  import sheep from '@/sheep';
  import { code, mobile } from '@/sheep/validate/form';
  import { showAuthModal } from '@/sheep/hooks/useModal';
  import { useMallAuthForm, mallAuthPassword as password } from '@/sheep/hooks/useMallAuthForm';

  const resetPasswordRef = ref(null);
  const isLogin = computed(() => sheep.$store('user').isLogin);

  // 数据
  const state = reactive({
    isMobileEnd: false, // 手机号输入完毕
    model: {
      mobile: '', // 手机号
      code: '', // 验证码
      password: '', // 密码
    },
    rules: {
      code,
      mobile,
      password,
    },
  });

  const { error: authError, available: authAvailable, codeText, countdown, sending, submitting,
    refresh: retryAuth, send: sendAuthCode, submit: submitAuth } = useMallAuthForm(state.model, 'RESET_PASSWORD');

  // 重置密码
  const resetPasswordSubmit = async () => {
    // 参数校验
    const validate = await unref(resetPasswordRef)
      .validate()
      .catch(() => null);
    if (!validate) {
      return;
    }
    // 发起请求
    const result = await submitAuth('reset-password', { ...state.model });
    if (result?.code !== 0) {
      return;
    }
    // 成功后，用户重新登录
    showAuthModal('accountLogin')
  };
</script>

<style lang="scss" scoped>
  @import '../index.scss';
</style>
