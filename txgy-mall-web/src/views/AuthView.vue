<script setup lang="ts">
import { computed, reactive, ref, toRef, watch, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import { useAuthChallenge } from '@/composables/useAuthChallenge'
import { useImageRegistration } from '@/composables/useImageRegistration'
import { StaleSessionError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'
import TenantEntrySwitch from '@/components/TenantEntrySwitch.vue'
import { entryTenantId } from '@/utils/tenant-context'
import { safeReturnPath } from '@/utils/tenant-navigation.mjs'
import { normalizeLoginName } from '@/utils/image-registration.mjs'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const cartStore = useCartStore()
const isRegister = computed(() => route.name === 'register')
const form = reactive({ account: '', loginName: '', contactMobile: '', password: '', confirmPassword: '', code: '', agree: false })
const memberIdentity = ref<'buyer' | 'supplier'>('buyer')
const purpose = computed(() => isRegister.value ? 'REGISTER' as const : 'PASSWORD_LOGIN' as const)
const { entry, entryError, newProtocol, available: entryAvailable, refresh, requireEntry,
  registrationOptions, selectedTenant, registrationTarget, choiceNotice, choiceError } = useAuthChallenge(toRef(form, 'account'), purpose, () => { form.code = '' })
const imageRegistration = computed(() => isRegister.value && entryAvailable.value && entry.value?.registrationMethod === 'image-v1')
const available = computed(() => entryAvailable.value && (!isRegister.value || imageRegistration.value))
const { image: captchaImage, error: captchaError, loading: captchaLoading, countdown: captchaCountdown,
  expiresIn: captchaExpiresIn, refresh: refreshCaptcha, payload: imagePayload } = useImageRegistration(toRef(form, 'loginName'), registrationTarget, imageRegistration, () => { form.code = '' })
let revision = 0, alive = true
const submitting = ref(false)
watch([() => ({ ...form }), memberIdentity, isRegister, entry, selectedTenant, registrationOptions], () => { revision++ }, { flush: 'sync' })
onBeforeUnmount(() => { alive = false; revision++ })

async function submit() {
  if (submitting.value) return
  const minimumPassword = isRegister.value ? 6 : 4
  if (form.password.length < minimumPassword || form.password.length > 16) return ElMessage.warning(`密码长度需要为 ${minimumPassword}-16 位`)
  const rev = revision
  const current = () => alive && rev === revision
  submitting.value = true
  try {
    const config = requireEntry()
    if (isRegister.value) {
      if (!imageRegistration.value) throw new Error('当前商城暂未开放图片验证码注册，请稍后重试')
      normalizeLoginName(form.loginName)
      if (form.password !== form.confirmPassword) return ElMessage.warning('两次输入的密码不一致')
      if (form.contactMobile.trim() && !/^1[3-9]\d{9}$/.test(form.contactMobile.trim())) return ElMessage.warning('请输入正确的联系手机号，或留空')
      if (!form.agree) return ElMessage.warning('请先同意用户协议和隐私政策')
      await authStore.registerImage({ ...imagePayload(form.code), password: form.password, confirmPassword: form.confirmPassword,
        ...(form.contactMobile.trim() ? { contactMobile: form.contactMobile.trim() } : {}), agree: true, memberIdentity: memberIdentity.value }, current)
      if (!current()) return
      ElMessage.success('账号注册成功，请完善企业信息并提交审核')
      await router.replace('/enterprise/profile')
      return
    }
    if (config.loginNameEnabled === true) {
      await authStore.accountLogin({ account: form.account.trim(), password: form.password, tenantId: config.tenantId }, current)
    } else {
      if (!/^1[3-9]\d{9}$/.test(form.account.trim())) return ElMessage.warning('当前商城仅支持老账号手机号登录')
      await authStore.login({ mobile: form.account.trim(), password: form.password, ...(newProtocol.value ? { tenantId: config.tenantId } : {}) }, newProtocol.value, current)
    }
    if (!current()) return
    ElMessage.success('登录成功')
    await cartStore.fetchCart()
    if (!current()) return
    const redirect = safeReturnPath(route.query.redirect, entryTenantId)
    await router.replace(redirect)
  } catch (error) { if (current() && !(error instanceof StaleSessionError)) ElMessage.warning(error instanceof Error ? error.message : '认证失败') }
  finally { submitting.value = false }
}
</script>

<template>
  <div class="auth-page">
    <div class="container auth-grid">
      <section class="auth-message"><span>工业材料采购平台</span><h1>账户跨端，业务数据同步</h1><p>PC 端与移动端在同一商城内共用会员、购物车、地址、订单及售后数据。</p><ul><li>账号归属以服务端确认为准</li><li>服务端数据作为唯一真实来源</li><li>交易全流程可查询、可追踪</li></ul></section>
      <section class="auth-card panel">
        <h2>{{ isRegister ? '注册商城账号' : '会员登录' }}</h2>
        <TenantEntrySwitch v-if="isRegister && choiceError && registrationTarget" :target="registrationTarget" purpose="register" />
        <p>{{ isRegister ? '注册后可在 PC 与移动端使用同一账号' : '使用登录名和密码登录，老账号也可使用原手机号' }}</p>
        <el-alert v-if="entryError" :title="entryError" type="error" :closable="false"><el-button @click="refresh">重试</el-button></el-alert>
        <el-alert v-else-if="entry && !available" title="当前商城暂未开放此认证方式" type="warning" :closable="false" />
        <p v-if="isRegister">登录名在当前商城内唯一，图片验证码仅用于本次注册。</p>
        <el-form label-position="top" :disabled="submitting" @submit.prevent="submit">
          <el-form-item v-if="isRegister && registrationOptions?.enabled" label="归属租户（可不选）">
            <el-select v-model="selectedTenant" clearable filterable placeholder="不选：平台自营公共商城" aria-label="归属租户">
              <el-option v-for="tenant in registrationOptions.tenants" :key="tenant.tenantId" :label="tenant.name" :value="tenant.tenantId" />
            </el-select>
            <p class="tenant-notice">{{ choiceNotice }}</p>
            <el-alert v-if="choiceError" :title="choiceError" type="warning" :closable="false" />
          </el-form-item>
          <el-form-item v-if="isRegister" label="会员身份" required><el-radio-group v-model="memberIdentity"><el-radio value="buyer">我是采购商</el-radio><el-radio value="supplier">我是供应商</el-radio></el-radio-group></el-form-item>
          <el-form-item v-if="isRegister" label="登录名" required><div class="code-field"><el-input v-model="form.loginName" maxlength="20" autocomplete="username" size="large" placeholder="6-20 位，以字母开头" /><small>可使用字母、数字和下划线，不区分字母大小写。</small></div></el-form-item>
          <el-form-item v-else label="登录名 / 老账号手机号"><el-input v-model="form.account" maxlength="20" autocomplete="username" size="large" placeholder="请输入登录名或老账号手机号" /></el-form-item>
          <el-form-item label="密码" required><el-input v-model="form.password" type="password" show-password :autocomplete="isRegister ? 'new-password' : 'current-password'" maxlength="16" size="large" :placeholder="isRegister ? '请输入 6-16 位密码' : '请输入密码'" @keyup.enter="submit" /></el-form-item>
          <el-form-item v-if="isRegister" label="确认密码" required><el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" maxlength="16" size="large" placeholder="请再次输入密码" @keyup.enter="submit" /></el-form-item>
          <el-form-item v-if="isRegister" label="图片验证码" required>
            <div class="code-field">
              <div class="captcha-row"><el-input v-model="form.code" maxlength="5" autocomplete="off" size="large" placeholder="5 位字母或数字" /><button v-if="captchaImage" type="button" class="captcha-image" :disabled="submitting || captchaLoading || captchaCountdown > 0" aria-label="刷新图片验证码" @click="refreshCaptcha"><img :src="captchaImage" alt="注册图片验证码" /></button></div>
              <el-button class="captcha-refresh" :disabled="!imageRegistration || captchaLoading || captchaCountdown > 0" :loading="captchaLoading" @click="refreshCaptcha">{{ captchaCountdown ? `${captchaCountdown} 秒后可刷新` : captchaImage ? '看不清，换一张' : '获取图片验证码' }}</el-button>
              <small v-if="captchaError" role="alert">{{ captchaError }}</small><small v-else-if="captchaImage && !captchaExpiresIn">验证码已过期，请刷新图片。</small><small v-else>填写登录名后获取图片，验证码不区分大小写。</small>
            </div>
          </el-form-item>
          <el-form-item v-if="isRegister" label="联系手机号（选填）"><div class="code-field"><el-input v-model="form.contactMobile" maxlength="11" type="tel" autocomplete="tel" size="large" placeholder="可留空" /><small>仅作为未验证联系信息，不用于登录或找回密码。</small></div></el-form-item>
          <el-checkbox v-if="isRegister" v-model="form.agree" class="agreement">我已阅读并同意用户协议和隐私政策</el-checkbox>
          <el-button class="submit-button" type="primary" size="large" :disabled="!available" :loading="submitting" @click="submit">{{ isRegister ? '注册并登录' : '登录' }}</el-button>
          <div v-if="!isRegister" class="forgot-link"><MallLink to="/forgot-password">忘记密码？</MallLink></div>
        </el-form>
        <div class="auth-switch"><span>{{ isRegister ? '已有账号？' : '还没有账号？' }}</span><MallLink :to="isRegister ? '/login' : '/register'">{{ isRegister ? '直接登录' : '免费注册' }}</MallLink></div>
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
.captcha-row { display: flex; align-items: center; gap: 10px; }.captcha-row .el-input { flex: 1; min-width: 0; }.captcha-image { flex: 0 0 130px; height: 48px; padding: 0; border: 1px solid #d9e0dc; border-radius: 4px; background: white; cursor: pointer; }.captcha-image:disabled { cursor: wait; }.captcha-image img { width: 100%; height: 100%; object-fit: contain; }.captcha-refresh { margin-top: 8px; }
.tenant-notice { font-size: 13px; line-height: 1.7; color: #52616a; }
</style>
