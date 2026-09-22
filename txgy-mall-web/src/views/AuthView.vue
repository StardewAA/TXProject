<script setup lang="ts">
import { computed, reactive, ref, toRef, watch, onBeforeUnmount, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import { useAuthChallenge } from '@/composables/useAuthChallenge'
import { useImageRegistration } from '@/composables/useImageRegistration'
import { getRegistrationOptions } from '@/api/mall-auth'
import { StaleSessionError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'
import { entryTenantId, getRegistrationEntryPath } from '@/utils/tenant-context'
import { safeReturnPath } from '@/utils/tenant-navigation.mjs'
import { normalizeLoginName } from '@/utils/image-registration.mjs'
import type { RegistrationOptions } from '@/utils/registration-choice.mjs'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const cartStore = useCartStore()
const registrationEntryPath = getRegistrationEntryPath()
const isRegister = computed(() => route.name === 'register')
const isPlatformLogin = computed(() => route.name === 'platform-login')
const selectedRegistrationTarget = computed(() => isRegister.value && selectedRegistrationTenant.value
  ? selectedRegistrationTenant.value : undefined)
const usesTenantEntry = computed(() => !isPlatformLogin.value
  && (!isRegister.value || Boolean(selectedRegistrationTarget.value)))
const form = reactive({ account: '', loginName: '', contactMobile: '', password: '', confirmPassword: '', code: '', agree: false })
const memberIdentity = ref<'buyer' | 'supplier'>('buyer')
const registrationOptions = ref<RegistrationOptions | null>(null)
const registrationOptionsError = ref('')
const selectedRegistrationTenant = ref('')
const purpose = computed(() => isRegister.value ? 'REGISTER' as const : 'PASSWORD_LOGIN' as const)
const { entry, entryError, newProtocol, available: entryAvailable, refresh, requireEntry,
  registrationTarget } = useAuthChallenge(toRef(form, 'account'), purpose, () => { form.code = '' }, usesTenantEntry,
  selectedRegistrationTarget)
const imageRegistration = computed(() => isRegister.value && entryAvailable.value && entry.value?.registrationMethod === 'image-v1')
const currentRegistrationTenant = computed(() => registrationOptions.value?.tenants
  .find(item => item.tenantId === selectedRegistrationTenant.value))
const registrationNeedsTenantChoice = computed(() => Boolean(isRegister.value
  && (!selectedRegistrationTenant.value || !entryAvailable.value || !imageRegistration.value)))
const available = computed(() => isPlatformLogin.value || (entryAvailable.value && (!isRegister.value || imageRegistration.value)))
const { image: captchaImage, error: captchaError, loading: captchaLoading, countdown: captchaCountdown,
  expiresIn: captchaExpiresIn, refresh: refreshCaptcha, payload: imagePayload } = useImageRegistration(toRef(form, 'loginName'), registrationTarget, imageRegistration, () => { form.code = '' })
let revision = 0, alive = true
const submitting = ref(false)
watch([() => ({ ...form }), memberIdentity, isRegister, entry], () => { revision++ }, { flush: 'sync' })
onBeforeUnmount(() => { alive = false; revision++ })
onMounted(async () => {
  if (!isRegister.value) return
  try {
    const options = await getRegistrationOptions()
    if (!alive) return
    registrationOptions.value = options
    const requested = String(route.query.registrationTenant || '')
    if (options.enabled && requested && options.tenants.some(item => item.tenantId === requested)) {
      selectedRegistrationTenant.value = requested
    } else if (options.enabled && options.tenants.some(item => item.tenantId === entryTenantId)) {
      selectedRegistrationTenant.value = entryTenantId
    }
  } catch (error) {
    if (alive) registrationOptionsError.value = error instanceof Error ? error.message : '注册租户目录加载失败，请重试'
  }
})

async function submit() {
  if (submitting.value) return
  const minimumPassword = isRegister.value ? 6 : 4
  if (form.password.length < minimumPassword || form.password.length > 16) return ElMessage.warning(`密码长度需要为 ${minimumPassword}-16 位`)
  const rev = revision
  const current = () => alive && rev === revision
  submitting.value = true
  try {
    if (isRegister.value) {
      requireEntry()
      if (!imageRegistration.value) throw new Error('当前商城暂未开放图片验证码注册，请稍后重试')
      normalizeLoginName(form.loginName)
      if (form.password !== form.confirmPassword) return ElMessage.warning('两次输入的密码不一致')
      if (form.contactMobile.trim() && !/^1[3-9]\d{9}$/.test(form.contactMobile.trim())) return ElMessage.warning('请输入正确的联系手机号，或留空')
      if (!form.agree) return ElMessage.warning('请先同意用户协议和隐私政策')
      const result = await authStore.registerImage({ ...imagePayload(form.code), password: form.password, confirmPassword: form.confirmPassword,
        ...(form.contactMobile.trim() ? { contactMobile: form.contactMobile.trim() } : {}), agree: true, memberIdentity: memberIdentity.value }, current)
      if (!current()) return
      ElMessage.success('账号注册成功，请完善企业信息并提交审核')
      if (result?.entryTenantId) {
        window.location.assign(`/enterprise/profile?tenantId=${encodeURIComponent(result.entryTenantId)}`)
        return
      }
      await router.replace('/enterprise/profile')
      return
    }
    if (isPlatformLogin.value) {
      await authStore.platformLogin({ account: form.account.trim(), password: form.password }, current)
    } else {
      const config = requireEntry()
      if (config.loginNameEnabled === true) {
        await authStore.accountLogin({ account: form.account.trim(), password: form.password, tenantId: config.tenantId }, current)
      } else {
        if (!/^1[3-9]\d{9}$/.test(form.account.trim())) return ElMessage.warning('当前商城仅支持老账号手机号登录')
        await authStore.login({ mobile: form.account.trim(), password: form.password, ...(newProtocol.value ? { tenantId: config.tenantId } : {}) }, newProtocol.value, current)
      }
    }
    if (!current()) return
    ElMessage.success('登录成功')
    await cartStore.fetchCart()
    if (!current()) return
    const redirect = safeReturnPath(route.query.redirect, entryTenantId, isPlatformLogin.value ? '/' : '/member')
    await router.replace(redirect)
  } catch (error) { if (current() && !(error instanceof StaleSessionError)) ElMessage.warning(error instanceof Error ? error.message : '认证失败') }
  finally { submitting.value = false }
}
</script>

<template>
  <div class="auth-page">
    <div :class="['container', 'auth-grid', { 'is-register': isRegister }]">
      <section class="auth-message"><span>工业材料采购平台</span><h1>账户跨端，业务数据同步</h1><p>PC 端与移动端在同一商城内共用会员、购物车、地址、订单及售后数据。</p><ul><li>账号归属以服务端确认为准</li><li>服务端数据作为唯一真实来源</li><li>交易全流程可查询、可追踪</li></ul></section>
      <section class="auth-card panel">
        <h2>{{ isRegister ? '注册商城账号' : isPlatformLogin ? '平台账号登录' : '会员登录' }}</h2>
        <p>{{ isRegister ? '注册后可在 PC 与移动端使用同一账号' : isPlatformLogin ? '登录后可跨租户浏览，账号归属由平台安全确认' : '使用登录名和密码登录，老账号也可使用原手机号' }}</p>
        <el-alert v-if="registrationOptionsError" :title="registrationOptionsError" type="error" :closable="false" />
        <template v-if="isRegister && registrationOptions?.enabled">
          <el-alert title="平台账号注册需先选择企业归属租户；选择不会自动授予企业采购或经营权限" type="warning" :closable="false" />
          <div class="registration-target">
            <el-select v-model="selectedRegistrationTenant" filterable placeholder="请选择注册归属租户" aria-label="注册归属租户">
              <el-option v-for="tenant in registrationOptions?.tenants || []" :key="tenant.tenantId" :label="tenant.name" :value="tenant.tenantId" />
            </el-select>
          </div>
        </template>
        <el-alert v-if="!isPlatformLogin && entryError" :title="entryError" type="error" :closable="false"><el-button @click="refresh">重试</el-button></el-alert>
        <el-alert v-else-if="!isPlatformLogin && entry && (!entry.available || !entry.registrationAvailable)" title="当前商城暂未开放注册，请重新选择租户" type="warning" :closable="false" />
        <el-alert v-else-if="isRegister && currentRegistrationTenant && entryAvailable" :title="`注册归属：${currentRegistrationTenant.name}`" type="info" :closable="false" />
        <el-form :class="{ 'register-form': isRegister }" label-position="top" :disabled="submitting || registrationNeedsTenantChoice" @submit.prevent="submit">
          <el-form-item v-if="isRegister" class="form-span-2 identity-field" label="会员身份" required><el-radio-group v-model="memberIdentity"><el-radio value="buyer">我是采购商</el-radio><el-radio value="supplier">我是供应商</el-radio></el-radio-group></el-form-item>
          <el-form-item v-if="isRegister" label="登录名" required><div class="code-field"><el-input v-model="form.loginName" maxlength="20" autocomplete="username" size="large" placeholder="6-20 位，以字母开头" /><small>可使用字母、数字和下划线，不区分字母大小写。</small></div></el-form-item>
          <el-form-item v-else label="登录名 / 老账号手机号"><el-input v-model="form.account" maxlength="20" autocomplete="username" size="large" placeholder="请输入登录名或老账号手机号" /></el-form-item>
          <el-form-item v-if="isRegister" label="联系手机号（选填）"><div class="code-field"><el-input v-model="form.contactMobile" maxlength="11" type="tel" autocomplete="tel" size="large" placeholder="可留空" /><small>仅作为联系信息，不用于登录或找回密码。</small></div></el-form-item>
          <el-form-item label="密码" required><el-input v-model="form.password" type="password" show-password :autocomplete="isRegister ? 'new-password' : 'current-password'" maxlength="16" size="large" :placeholder="isRegister ? '请输入 6-16 位密码' : '请输入密码'" @keyup.enter="submit" /></el-form-item>
          <el-form-item v-if="isRegister" label="确认密码" required><el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" maxlength="16" size="large" placeholder="请再次输入密码" @keyup.enter="submit" /></el-form-item>
          <el-form-item v-if="isRegister" class="form-span-2" label="图片验证码" required>
            <div class="code-field">
              <div class="captcha-row"><el-input v-model="form.code" maxlength="5" autocomplete="off" size="large" placeholder="5 位字母或数字" /><button v-if="captchaImage" type="button" class="captcha-image" :disabled="submitting || captchaLoading || captchaCountdown > 0" aria-label="刷新图片验证码" @click="refreshCaptcha"><img :src="captchaImage" alt="注册图片验证码" /></button><el-button class="captcha-refresh" :disabled="!imageRegistration || captchaLoading || captchaCountdown > 0" :loading="captchaLoading" @click="refreshCaptcha">{{ captchaCountdown ? `${captchaCountdown} 秒后可刷新` : captchaImage ? '换一张' : '获取验证码' }}</el-button></div>
              <small v-if="captchaError" role="alert">{{ captchaError }}</small><small v-else-if="captchaImage && !captchaExpiresIn">验证码已过期，请刷新图片。</small><small v-else>填写登录名后获取图片，验证码不区分大小写。</small>
            </div>
          </el-form-item>
          <el-checkbox v-if="isRegister" v-model="form.agree" class="agreement">我已阅读并同意用户协议和隐私政策</el-checkbox>
          <el-button class="submit-button" type="primary" size="large" :disabled="!available" :loading="submitting" @click="submit">{{ isRegister ? '注册并登录' : '登录' }}</el-button>
          <div v-if="!isRegister" class="forgot-link"><MallLink to="/forgot-password">忘记密码？</MallLink></div>
        </el-form>
        <div class="auth-switch"><span>{{ isRegister ? '已有账号？' : '还没有账号？' }}</span><MallLink v-if="isRegister" to="/login">直接登录</MallLink><a v-else :href="registrationEntryPath">免费注册</a></div>
        <div v-if="!isRegister" class="enterprise-access"><span>需要企业采购服务？</span><a :href="registrationEntryPath">注册并申请企业入驻</a><small>已有账号登录后可在会员中心提交企业资料。</small></div>
      </section>
    </div>
  </div>
</template>

<style scoped lang="scss">
.auth-page { padding: 36px 0; overflow: hidden; background: #f4f6f8; }
.auth-grid { min-height: 500px; overflow: hidden; display: grid; grid-template-columns: 1fr 470px; gap: 100px; align-items: center; background: linear-gradient(120deg, #27343d 0%, #3f4f56 48%, #e5e8e5 48%, #f2f4f2 100%); }
.auth-grid.is-register { min-height: 520px; grid-template-columns: minmax(260px, .68fr) minmax(640px, 1.32fr); gap: 42px; }
.auth-message { color: white; padding: 30px; }.auth-message span { color: #f1a46d; letter-spacing: 3px; }.auth-message h1 { margin: 18px 0; font-size: 38px; }.auth-message p { color: #cbd2d6; line-height: 1.8; }.auth-message li { margin: 12px 0; color: #dfe4e6; }
.auth-card { padding: 34px 40px; }.auth-card h2 { margin: 0; font-size: 26px; }.auth-card > p { margin: 10px 0 24px; color: var(--muted); }.code-row { width: 100%; display: grid; grid-template-columns: 1fr 130px; gap: 10px; }.agreement { margin: 0 0 18px; }.submit-button { width: 100%; margin-top: 6px; }.forgot-link { margin-top: 12px; text-align: right; }.forgot-link a { color: var(--brand); }.auth-switch { margin-top: 22px; text-align: center; color: var(--muted); }.auth-switch a { margin-left: 8px; color: var(--brand); }
.enterprise-access{display:flex;align-items:center;justify-content:center;gap:8px;flex-wrap:wrap;margin-top:14px;padding-top:14px;border-top:1px solid var(--line);color:var(--muted);font-size:13px}.enterprise-access a{color:var(--brand);font-weight:700}.enterprise-access small{width:100%;text-align:center;color:#87949a}
.is-register .auth-card { padding: 24px 30px; }
.is-register .auth-card > p { margin-bottom: 14px; }
.is-register .auth-message h1 { margin-block: 14px; font-size: 32px; line-height: 1.25; }
.is-register .auth-message li { margin: 8px 0; }
.register-form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); column-gap: 18px; align-items: start; }
.register-form .form-span-2,
.register-form .agreement,
.register-form .submit-button { grid-column: 1 / -1; }
.register-form :deep(.el-form-item) { margin-bottom: 12px; }
.register-form :deep(.el-form-item__label) { height: auto; margin-bottom: 4px; padding: 0; line-height: 20px; }
.register-form .identity-field :deep(.el-form-item__content) { min-height: 34px; }
.register-form .agreement { margin: 0 0 12px; }
.register-form .submit-button { margin-top: 0; }
.code-field { width: 100%; }.code-field small { display: block; margin-top: 8px; color: #b05a35; }
.captcha-row { display: flex; align-items: center; gap: 10px; }.captcha-row .el-input { flex: 1; min-width: 0; }.captcha-image { flex: 0 0 130px; height: 48px; padding: 0; border: 1px solid #d9e0dc; border-radius: 4px; background: white; cursor: pointer; }.captcha-image:disabled { cursor: wait; }.captcha-image img { width: 100%; height: 100%; object-fit: contain; }.captcha-refresh { margin-top: 8px; }
.register-form .code-field small { margin-top: 5px; font-size: 12px; line-height: 1.35; }
.register-form .captcha-image { height: 40px; }
.register-form .captcha-refresh { flex: 0 0 auto; height: 40px; margin-top: 0; }
.registration-target { margin: 12px 0; }
@media (max-width: 1180px) {
  .auth-grid.is-register { grid-template-columns: 230px minmax(0, 1fr); gap: 24px; }
  .is-register .auth-message { padding-inline: 22px; }
  .is-register .auth-message h1 { font-size: 28px; }
}
@media (max-height: 900px) {
  .auth-page { padding: 26px 0; }
  .auth-grid { min-height: 460px; }
  .auth-message { padding-block: 18px; }
  .auth-grid.is-register { min-height: 480px; }
  .is-register .auth-card { padding-block: 20px; }
}
</style>
