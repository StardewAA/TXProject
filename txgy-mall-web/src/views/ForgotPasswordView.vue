<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

import { authApi, memberApi } from '@/api/mall'

const router = useRouter()
const form = reactive({ mobile: '', code: '', password: '', confirmPassword: '' })
const countdown = ref(0)
const loading = ref(false)

async function sendCode() {
  if (!/^1\d{10}$/.test(form.mobile)) return ElMessage.warning('请输入正确手机号')
  await authApi.sendSmsCode(form.mobile, 4)
  countdown.value = 60
  const timer = window.setInterval(() => { countdown.value--; if (countdown.value <= 0) window.clearInterval(timer) }, 1000)
}

async function submit() {
  if (!/^1\d{10}$/.test(form.mobile) || !/^\d{4,6}$/.test(form.code)) return ElMessage.warning('请完整填写手机号和验证码')
  if (form.password.length < 4 || form.password.length > 16) return ElMessage.warning('密码长度需要为 4-16 位')
  if (form.password !== form.confirmPassword) return ElMessage.warning('两次密码输入不一致')
  loading.value = true
  try { await memberApi.resetPassword({ mobile: form.mobile, code: form.code, password: form.password }); ElMessage.success('密码已重置，请重新登录'); await router.replace('/login') } finally { loading.value = false }
}
</script>

<template><div class="simple-auth"><section class="panel"><h1>重置密码</h1><p>验证码场景使用会员忘记密码（scene=4）</p><el-form label-position="top"><el-form-item label="手机号"><el-input v-model="form.mobile" maxlength="11" size="large" /></el-form-item><el-form-item label="验证码"><div class="code"><el-input v-model="form.code" maxlength="6" size="large" /><el-button size="large" :disabled="countdown > 0" @click="sendCode">{{ countdown ? `${countdown}s` : '发送验证码' }}</el-button></div></el-form-item><el-form-item label="新密码"><el-input v-model="form.password" type="password" show-password size="large" /></el-form-item><el-form-item label="确认新密码"><el-input v-model="form.confirmPassword" type="password" show-password size="large" /></el-form-item><el-button type="primary" size="large" :loading="loading" class="submit" @click="submit">确认重置</el-button></el-form></section></div></template>

<style scoped lang="scss">.simple-auth { min-height: 610px; display: grid; place-items: center; background: linear-gradient(135deg, #e6eae8, #f7f8f7); }.simple-auth section { width: 480px; padding: 34px 40px; }.simple-auth h1 { margin: 0; }.simple-auth p { margin: 10px 0 24px; color: var(--muted); }.code { width: 100%; display: grid; grid-template-columns: 1fr 130px; gap: 10px; }.submit { width: 100%; }</style>
