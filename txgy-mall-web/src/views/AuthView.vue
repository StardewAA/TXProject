<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import { authApi } from '@/api/mall'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const cartStore = useCartStore()
const isRegister = computed(() => route.name === 'register')
const form = reactive({ mobile: '', nickname: '', password: '', confirmPassword: '', code: '', agree: false })
const sending = ref(false)
const memberIdentity = ref<'buyer' | 'supplier'>('buyer')
const countdown = ref(0)
const smsTestCode = String(import.meta.env.VITE_SMS_TEST_CODE || '').trim()

async function sendCode() {
  if (!/^1\d{10}$/.test(form.mobile)) return ElMessage.warning('请输入正确的手机号')
  sending.value = true
  try {
    await authApi.sendSmsCode(form.mobile, 1)
    if (smsTestCode) {
      form.code = smsTestCode
      ElMessage.success(`本地联调验证码为 ${smsTestCode}，已自动填写`)
    } else {
      ElMessage.success('验证码已发送，请查看手机短信')
    }
    countdown.value = 60
    const timer = window.setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) window.clearInterval(timer)
    }, 1000)
  } finally { sending.value = false }
}

async function submit() {
  if (!/^1\d{10}$/.test(form.mobile)) return ElMessage.warning('请输入正确的手机号')
  if (form.password.length < 6 || form.password.length > 16) return ElMessage.warning('密码长度需要为 6-16 位')
  if (isRegister.value) {
    if (!form.nickname.trim()) return ElMessage.warning('请输入用户昵称')
    if (form.password !== form.confirmPassword) return ElMessage.warning('两次输入的密码不一致')
    if (!form.code) return ElMessage.warning('请输入短信验证码')
    if (!form.agree) return ElMessage.warning('请先同意用户协议和隐私政策')
    await authStore.register({ mobile: form.mobile, nickname: form.nickname.trim(), password: form.password, code: form.code, agree: true, memberIdentity: memberIdentity.value })
    ElMessage.success('账号注册成功，请完善企业信息并提交审核')
    await router.replace('/enterprise/profile')
    return
  } else {
    await authStore.login({ mobile: form.mobile, password: form.password })
    ElMessage.success('登录成功')
  }
  await cartStore.fetchCart()
  const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') ? route.query.redirect : '/member'
  await router.replace(redirect)
}
</script>

<template>
  <div class="auth-page">
    <div class="container auth-grid">
      <section class="auth-message"><span>工业材料采购平台</span><h1>账户跨端，业务数据同步</h1><p>PC 端与移动端共用同一会员、购物车、地址、订单及售后数据。</p><ul><li>商城公共租户统一为 1</li><li>服务端数据作为唯一真实来源</li><li>交易全流程可查询、可追踪</li></ul></section>
      <section class="auth-card panel">
        <h2>{{ isRegister ? '注册商城账号' : '会员登录' }}</h2>
        <p>{{ isRegister ? '注册后可在 PC 与移动端使用同一账号' : '使用移动端相同的手机号和密码登录' }}</p>
        <el-form label-position="top" @submit.prevent="submit">
          <el-form-item v-if="isRegister" label="会员身份" required><el-radio-group v-model="memberIdentity"><el-radio value="buyer">我是采购商</el-radio><el-radio value="supplier">我是供应商</el-radio></el-radio-group></el-form-item>
          <el-form-item label="手机号"><el-input v-model="form.mobile" maxlength="11" size="large" placeholder="请输入手机号" /></el-form-item>
          <el-form-item v-if="isRegister" label="用户昵称"><el-input v-model="form.nickname" maxlength="30" size="large" placeholder="请输入用户昵称" /></el-form-item>
          <el-form-item v-if="isRegister" label="短信验证码"><div class="code-field"><div class="code-row"><el-input v-model="form.code" maxlength="6" size="large" placeholder="请输入验证码" /><el-button size="large" :disabled="countdown > 0" :loading="sending" @click="sendCode">{{ countdown ? `${countdown}s` : '发送验证码' }}</el-button></div><small v-if="smsTestCode">本地联调环境固定验证码：{{ smsTestCode }}；正式环境将发送真实短信。</small></div></el-form-item>
          <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password size="large" placeholder="请输入密码" @keyup.enter="submit" /></el-form-item>
          <el-form-item v-if="isRegister" label="确认密码"><el-input v-model="form.confirmPassword" type="password" show-password size="large" placeholder="请再次输入密码" @keyup.enter="submit" /></el-form-item>
          <el-checkbox v-if="isRegister" v-model="form.agree" class="agreement">我已阅读并同意用户协议和隐私政策</el-checkbox>
          <el-button class="submit-button" type="primary" size="large" :loading="authStore.loading" @click="submit">{{ isRegister ? '注册并登录' : '登录' }}</el-button>
          <div v-if="!isRegister" class="forgot-link"><RouterLink to="/forgot-password">忘记密码？</RouterLink></div>
        </el-form>
        <div class="auth-switch"><span>{{ isRegister ? '已有账号？' : '还没有账号？' }}</span><RouterLink :to="isRegister ? '/login' : '/register'">{{ isRegister ? '直接登录' : '免费注册' }}</RouterLink></div>
      </section>
    </div>
  </div>
</template>

<style scoped lang="scss">
.auth-page { min-height: 620px; padding: 58px 0; background: linear-gradient(120deg, #27343d 0%, #3f4f56 48%, #e5e8e5 48%, #f2f4f2 100%); }
.auth-grid { display: grid; grid-template-columns: 1fr 470px; gap: 100px; align-items: center; }
.auth-message { color: white; padding: 30px; }.auth-message span { color: #f1a46d; letter-spacing: 3px; }.auth-message h1 { margin: 18px 0; font-size: 38px; }.auth-message p { color: #cbd2d6; line-height: 1.8; }.auth-message li { margin: 12px 0; color: #dfe4e6; }
.auth-card { padding: 34px 40px; }.auth-card h2 { margin: 0; font-size: 26px; }.auth-card > p { margin: 10px 0 24px; color: var(--muted); }.code-row { width: 100%; display: grid; grid-template-columns: 1fr 130px; gap: 10px; }.agreement { margin: 0 0 18px; }.submit-button { width: 100%; margin-top: 6px; }.forgot-link { margin-top: 12px; text-align: right; }.forgot-link a { color: var(--brand); }.auth-switch { margin-top: 22px; text-align: center; color: var(--muted); }.auth-switch a { margin-left: 8px; color: var(--brand); }
.code-field { width: 100%; }.code-field small { display: block; margin-top: 8px; color: #b05a35; }
</style>
