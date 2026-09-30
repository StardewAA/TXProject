<script setup>
import { ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { loginApi, registerApi } from '../api/auth';

const route = useRoute();
const router = useRouter();

const mode = ref('login');
const username = ref('');
const nickname = ref('');
const password = ref('');
const confirmPassword = ref('');
const loading = ref(false);
const errorMsg = ref('');

async function submit() {
  errorMsg.value = '';
  if (mode.value === 'register') {
    if (!nickname.value.trim()) {
      errorMsg.value = '请输入昵称';
      return;
    }
    if (password.value !== confirmPassword.value) {
      errorMsg.value = '两次输入的密码不一致';
      return;
    }
  }
  if (!username.value.trim() || !password.value) {
    errorMsg.value = '请输入用户名和密码';
    return;
  }

  loading.value = true;
  try {
    if (mode.value === 'register') {
      await registerApi({
        username: username.value.trim(),
        nickname: nickname.value.trim(),
        password: password.value,
      });
    }
    const result = await loginApi({
      username: username.value.trim(),
      password: password.value,
    });
    localStorage.setItem('cms_access_token', result.accessToken);
    localStorage.setItem('cms_refresh_token', result.refreshToken);
    const redirect =
      typeof route.query.redirect === 'string' ? route.query.redirect : '/';
    router.replace(redirect);
  } catch (error) {
    errorMsg.value = error.message || '登录失败';
  } finally {
    loading.value = false;
  }
}

function switchMode(nextMode) {
  mode.value = nextMode;
  errorMsg.value = '';
}
</script>

<template>
  <section class="login-page">
    <div class="login-card">
      <h1>防腐保温行业门户</h1>
      <p class="subtitle">请先登录或注册用户账号</p>

      <div class="mode-tabs">
        <button
          :class="{ active: mode === 'login' }"
          type="button"
          @click="switchMode('login')"
        >
          用户登录
        </button>
        <button
          :class="{ active: mode === 'register' }"
          type="button"
          @click="switchMode('register')"
        >
          用户注册
        </button>
      </div>

      <div class="form">
        <label>
          用户名
          <input v-model="username" autocomplete="username" />
        </label>
        <label v-if="mode === 'register'">
          昵称
          <input v-model="nickname" />
        </label>
        <label>
          密码
          <input v-model="password" autocomplete="current-password" type="password" />
        </label>
        <label v-if="mode === 'register'">
          确认密码
          <input v-model="confirmPassword" type="password" />
        </label>

        <p v-if="errorMsg" class="error">{{ errorMsg }}</p>
        <button
          class="submit"
          :disabled="loading"
          type="button"
          @click="submit"
        >
          {{ loading ? '处理中...' : mode === 'login' ? '登录' : '注册并登录' }}
        </button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 140px);
}

.login-card {
  width: min(420px, 100%);
  padding: 34px 36px;
  border: 1px solid #dbeafe;
  border-radius: 24px;
  background:
    linear-gradient(120deg, rgb(37 99 235 / 10%), transparent 60%),
    #fff;
}

h1 {
  margin: 0;
  font-size: 26px;
}

.subtitle {
  margin: 8px 0 22px;
  color: #64748b;
}

.mode-tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  padding: 6px;
  border-radius: 16px;
  background: #f1f5f9;
}

.mode-tabs button {
  min-height: 44px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  cursor: pointer;
}

.mode-tabs button.active {
  background: #fff;
  color: #1d4ed8;
  font-weight: 700;
}

.form {
  display: grid;
  margin-top: 20px;
  gap: 14px;
}

label {
  display: grid;
  gap: 6px;
  color: #334155;
  font-size: 14px;
}

input {
  height: 46px;
  padding: 0 12px;
  border: 1px solid #cbd5e1;
  border-radius: 12px;
  font-size: 15px;
}

.error {
  margin: 0;
  color: #dc2626;
  font-size: 13px;
}

.submit {
  height: 48px;
  border: 0;
  border-radius: 14px;
  background: #1d4ed8;
  color: #fff;
  cursor: pointer;
  font-size: 16px;
  font-weight: 700;
}

.submit:disabled {
  opacity: 0.6;
}
</style>
