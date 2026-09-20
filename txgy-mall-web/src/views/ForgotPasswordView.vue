<script setup lang="ts">
import { reactive, ref, toRef, watch, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

import { memberApi } from '@/api/mall'
import { challengeRequest } from '@/api/mall-auth'
import { useAuthChallenge } from '@/composables/useAuthChallenge'
import { getTenantContext } from '@/utils/tenant-context'
import { tokenStorage } from '@/api/http'

const router = useRouter()
const form = reactive({ mobile: '', code: '', password: '', confirmPassword: '' })
const loading = ref(false)
const purpose = ref<'RESET_PASSWORD'>('RESET_PASSWORD')
const { entry, entryError, countdown, sending, newProtocol, available, refresh, send, payload, requireEntry } = useAuthChallenge(toRef(form, 'mobile'), purpose, () => { form.code = '' })
let revision = 0, alive = true
watch([() => ({ ...form }), entry], () => { revision++ }, { flush: 'sync' })
onBeforeUnmount(() => { alive = false; revision++ })

async function sendCode() {
  if (!/^1\d{10}$/.test(form.mobile)) return ElMessage.warning('请输入正确手机号')
  try { if (await send()) ElMessage.success('验证码请求已提交；实际送达以手机收到为准') }
  catch (error) { if (alive) ElMessage.warning(error instanceof Error ? error.message : '验证码发送失败') }
}

async function submit() {
  if (loading.value) return
  if (!/^1\d{10}$/.test(form.mobile) || !/^\d{4,6}$/.test(form.code)) return ElMessage.warning('请完整填写手机号和验证码')
  if (form.password.length < 6 || form.password.length > 16) return ElMessage.warning('密码长度需要为 6-16 位')
  if (form.password !== form.confirmPassword) return ElMessage.warning('两次密码输入不一致')
  loading.value = true
  const rev = revision, epoch = getTenantContext().epoch
  try {
    requireEntry()
    const data = { mobile: form.mobile, code: form.code, password: form.password, ...payload(form.code) }
    if (newProtocol.value) await challengeRequest('reset-password', data)
    else await memberApi.resetPassword(data)
    if (!alive || rev !== revision || epoch !== getTenantContext().epoch) return
    // Only clear this initiating browser session; never clear a newer login.
    tokenStorage.clear(); window.dispatchEvent(new Event('mall:unauthorized'))
    ElMessage.success('密码已重置，请重新登录'); await router.replace('/login')
  } catch (error) { if (alive && rev === revision) ElMessage.warning(error instanceof Error ? error.message : '密码重置失败') }
  finally { loading.value = false }
}
</script>

<template><div class="simple-auth"><section class="panel"><h1>重置密码</h1><p>验证当前商城账号的手机号后设置新密码</p><el-alert v-if="entryError" :title="entryError" type="error" :closable="false"><el-button @click="refresh">重试</el-button></el-alert><el-alert v-else-if="entry && !available" title="当前商城暂未开放密码重置" type="warning" :closable="false" /><el-form label-position="top" :disabled="loading"><el-form-item label="手机号"><el-input v-model="form.mobile" maxlength="11" size="large" /></el-form-item><el-form-item label="验证码"><div class="code"><el-input v-model="form.code" maxlength="6" size="large" /><el-button size="large" :disabled="!available || countdown > 0 || sending" :loading="sending" @click="sendCode">{{ countdown ? `${countdown}s` : '发送验证码' }}</el-button></div></el-form-item><el-form-item label="新密码"><el-input v-model="form.password" type="password" show-password size="large" /></el-form-item><el-form-item label="确认新密码"><el-input v-model="form.confirmPassword" type="password" show-password size="large" /></el-form-item><el-button type="primary" size="large" :disabled="!available" :loading="loading" class="submit" @click="submit">确认重置</el-button></el-form></section></div></template>

<style scoped lang="scss">.simple-auth { min-height: 610px; display: grid; place-items: center; background: linear-gradient(135deg, #e6eae8, #f7f8f7); }.simple-auth section { width: 480px; padding: 34px 40px; }.simple-auth h1 { margin: 0; }.simple-auth p { margin: 10px 0 24px; color: var(--muted); }.code { width: 100%; display: grid; grid-template-columns: 1fr 130px; gap: 10px; }.submit { width: 100%; }</style>
