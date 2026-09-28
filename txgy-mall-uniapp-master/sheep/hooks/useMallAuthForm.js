import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue';
import request from '@/sheep/request';
import $store from '@/sheep/store';
import { tenantId } from '@/sheep/config';
import { tenantTransport, ensureTenantReady, getControlledTenantId, captureTenantOperation, isTenantOperationCurrent, verifyPlatformSession } from '@/sheep/helper/tenant-runtime';
import { assertAuthAvailable, createChallengeState } from '@/sheep/helper/auth-challenge.mjs';
import { targetAuth } from '@/sheep/api/tenant-auth';
import { parseRegistrationOptions, resolveRegistrationChoice, assertRegistrationContext, registrationChoiceNotice } from '@/sheep/helper/registration-choice.mjs';
import { createImageRegistrationState, createCaptchaSession, normalizeLoginName } from '@/sheep/helper/image-registration.mjs';
import { isPlatformAccountAddress, isPlatformPortalAddress } from '@/sheep/helper/tenant-navigation.mjs';

export const mallAuthPassword = { rules: [
  { required: true, errorMessage: '请输入密码' },
  { minLength: 6, maxLength: 16, errorMessage: '密码需要为 6-16 位' },
] };
export const mallLoginPassword = { rules: [
  { required: true, errorMessage: '请输入密码' },
  { minLength: 4, maxLength: 16, errorMessage: '密码需要为 4-16 位' },
] };

// Used only by visible H5 auth forms; old social/native integrations remain separately gated.
export function useMallAuthForm(model, purpose) {
  const entry = ref(null), error = ref(''), sending = ref(false), submitting = ref(false), tick = ref(Date.now());
  const state = createChallengeState();
  const imageState = createImageRegistrationState();
  const imageChallenge = ref(null), imageSending = ref(false);
  let captchaSession = '';
  const registrationOptions = ref(null), selectedTenant = ref('');
  const platformLogin = purpose === 'PASSWORD_LOGIN' && typeof window !== 'undefined'
    && (isPlatformPortalAddress(window.location?.href || '')
      || isPlatformAccountAddress(window.location?.href || '', tenantId));
  // #ifdef H5
  if (typeof window !== 'undefined') selectedTenant.value = new URLSearchParams(window.location.search).get('registrationTenant') || '';
  // #endif
  const registrationChoice = computed(() => resolveRegistrationChoice(registrationOptions.value, selectedTenant.value, getControlledTenantId()));
  const choiceNotice = computed(() => { try { return registrationChoiceNotice(registrationChoice.value); } catch (reason) { return reason.message; } });
  const choiceError = computed(() => {
    if (purpose !== 'REGISTER' || !registrationOptions.value?.enabled) return '';
    try { assertRegistrationContext(registrationChoice.value, getControlledTenantId()); return ''; } catch (reason) { return reason.message; }
  });
  let alive = true, revision = 0, loadRevision = 0;
  const registrationTarget = computed(() => { try { return registrationChoice.value.tenantId; } catch { return ''; } });
  const binding = () => ({ tenantId: purpose === 'REGISTER' ? registrationChoice.value.tenantId : entry.value?.tenantId || getControlledTenantId(), mobile: model.mobile,
    purpose, protocol: entry.value?.protocol || 'legacy' });
  const newProtocol = computed(() => entry.value?.protocol === 'challenge-v1');
  // Image registration and account login are independent of the legacy SMS protocol switch.
  const imageRegistration = computed(() => entry.value?.registrationMethod === 'image-v1');
  const loginNameEnabled = computed(() => platformLogin || entry.value?.loginNameEnabled === true);
  const imageCountdown = computed(() => { void tick.value; return imageState.remaining(); });
  const imageExpiresIn = computed(() => { void tick.value; return imageState.expiresIn(); });
  const available = computed(() => Boolean(!error.value && !choiceError.value && entry.value?.available
    && (purpose !== 'REGISTER' || (entry.value.registrationAvailable && imageRegistration.value))));
  const countdown = computed(() => { void tick.value; return state.remaining(model.mobile); });
  const codeText = computed(() => countdown.value ? countdown.value + ' 秒' : '获取验证码');
  const toast = message => uni.showToast({ title: message, icon: 'none' });
  watch(() => [model.mobile, entry.value, selectedTenant.value, registrationOptions.value], () => {
    revision++; state.invalidate(); model.code = ''; sending.value = false;
  }, { flush: 'sync' });
  watch(() => [model.loginName, entry.value, selectedTenant.value, registrationOptions.value], () => {
    imageState.invalidate(); imageChallenge.value = null; model.imageCode = '';
  }, { flush: 'sync' });
  watch(model, () => { revision++; }, { deep: true, flush: 'sync' });
  async function refresh() {
    if (!alive) return;
    const rev = ++loadRevision; entry.value = null; registrationOptions.value = null; error.value = '';
    try {
      if (platformLogin) {
        if (alive && rev === loadRevision) entry.value = Object.freeze({ protocol: 'legacy',
          tenantId: getControlledTenantId(), available: true, registrationAvailable: false,
          tenantSelection: false, loginNameEnabled: true, platformLogin: true });
        return;
      }
      await ensureTenantReady();
      const parsed = await targetAuth.discover(getControlledTenantId(), () => alive && rev === loadRevision);
      if ((parsed.registrationMethod !== undefined && !['image-v1', 'sms'].includes(parsed.registrationMethod))
          || (parsed.loginNameEnabled !== undefined && typeof parsed.loginNameEnabled !== 'boolean')) {
        throw new Error('商城认证能力配置无效');
      }
      let options = null;
      if (purpose === 'REGISTER' && (parsed.protocol === 'challenge-v1' || parsed.registrationMethod === 'image-v1')) {
        let response;
        try { response = await tenantTransport('/member/mall-auth/registration-options'); }
        catch (reason) { if (reason?.statusCode === 404) response = { code: 404 }; else throw reason; }
        options = parseRegistrationOptions(response?.code === 404 ? { enabled: false, tenants: [], crossTenantReady: false, selectionRequired: false }
          : response?.code === 0 ? response.data : null);
      }
      if (alive && rev === loadRevision) { registrationOptions.value = options; entry.value = parsed; }
    } catch {
      if (alive && rev === loadRevision) error.value = '认证配置加载失败，点击重试；不会自动切回旧接口';
    }
  }
  async function transport(endpoint, data, legacyUrl, method = 'POST', current = () => alive) {
    if (entry.value.protocol === 'challenge-v1' || endpoint === 'register-image' || endpoint === 'account-login') {
      return { code: 0, data: await targetAuth.execute(endpoint, data, current) };
    }
    return request({ url: legacyUrl, method, data, custom: { deferAuthToken: true, isToken: false, showSuccess: false, showError: false } });
  }
  async function send() {
    if (!alive) return;
    let ticket;
    const operation = captureTenantOperation();
    try {
      assertAuthAvailable(entry.value, purpose);
      if (error.value) throw new Error(error.value);
      if (purpose === 'REGISTER') throw new Error(imageRegistration.value ? '当前注册请获取图片验证码' : '注册暂未开放，请等待商城升级完成');
      if (purpose === 'REGISTER') assertRegistrationContext(registrationChoice.value, getControlledTenantId());
      if (!/^1\d{10}$/.test(model.mobile)) throw new Error('请输入正确的手机号');
      ticket = state.begin(binding()); sending.value = true;
      const data = newProtocol.value ? { tenantId: ticket.tenantId, mobile: ticket.mobile, purpose }
        : { mobile: ticket.mobile, scene: purpose === 'RESET_PASSWORD' ? 4 : 1 };
      const response = await transport('send-code', data, '/member/auth/send-sms-code', 'POST',
        () => alive && state.current(ticket) && isTenantOperationCurrent(operation));
      if (!alive || !state.current(ticket) || !isTenantOperationCurrent(operation)) return;
      if (response?.code !== 0) throw new Error(response?.msg || '验证码发送失败，请重试');
      if (state.accept(ticket, response.data)) { tick.value = Date.now(); toast('验证码请求已提交，送达以手机收到为准'); }
    } catch (reason) { if (alive && (!ticket || state.current(ticket))) toast(reason.message || '验证码发送失败'); }
    finally { if (ticket) { state.finish(ticket); if (state.current(ticket)) sending.value = false; } }
  }
  function imageBinding() {
    return { tenantId: registrationChoice.value.tenantId, loginName: normalizeLoginName(model.loginName), captchaSession };
  }
  async function loadImage() {
    if (!alive || imageSending.value || submitting.value) return;
    let ticket;
    const operation = captureTenantOperation();
    try {
      assertAuthAvailable(entry.value, 'REGISTER');
      if (purpose !== 'REGISTER' || !imageRegistration.value) throw new Error('当前商城暂未开放图片验证码注册');
      if (error.value) throw new Error(error.value);
      assertRegistrationContext(registrationChoice.value, getControlledTenantId());
      if (!captchaSession) captchaSession = createCaptchaSession();
      ticket = imageState.begin(imageBinding());
      imageSending.value = true; imageChallenge.value = null; model.imageCode = '';
      const current = () => alive && imageState.current(ticket) && isTenantOperationCurrent(operation);
      const result = await targetAuth.execute('image-challenge', ticket, current);
      if (current() && imageState.accept(ticket, result)) { imageChallenge.value = result; tick.value = Date.now(); }
    } catch (reason) {
      if (alive && isTenantOperationCurrent(operation) && (!ticket || imageState.current(ticket))) toast(reason.message || '图片加载失败，请重试');
    } finally { if (ticket) imageState.finish(ticket); imageSending.value = false; }
  }
  async function submit(kind, fields) {
    if (!alive || submitting.value) return null;
    const operation = captureTenantOperation(), rev = revision;
    const current = () => alive && rev === revision && isTenantOperationCurrent(operation);
    submitting.value = true;
    try {
      assertAuthAvailable(entry.value, purpose);
      if (error.value) throw new Error(error.value);
      if (kind === 'register' && !imageRegistration.value) throw new Error('注册暂未开放，请等待商城升级完成');
      if (purpose === 'REGISTER') assertRegistrationContext(registrationChoice.value, getControlledTenantId());
      const useImage = kind === 'register' && imageRegistration.value;
      const useAccount = kind === 'login' && loginNameEnabled.value;
      const usePlatform = kind === 'login' && platformLogin;
      const data = useImage ? {} : useAccount ? { account: String(fields.account || '').trim().toLowerCase() }
        : { mobile: fields.mobile ?? fields.account };
      if (kind !== 'sms-login') {
        const minLength = kind === 'login' ? 4 : 6;
        if (typeof fields.password !== 'string' || fields.password.length < minLength || fields.password.length > 16) throw new Error(`密码需要为 ${minLength}-16 位`);
        data.password = fields.password;
      }
      if (useImage) {
        Object.assign(data, imageState.payload(imageBinding(), fields.imageCode), {
          confirmPassword: fields.confirmPassword, contactMobile: fields.contactMobile,
          nickname: fields.nickname?.trim(), agree: fields.agree, memberIdentity: fields.memberIdentity,
        });
      } else if (kind !== 'login') { data.code = fields.code; Object.assign(data, state.payload(binding(), fields.code)); }
      if (!usePlatform && (newProtocol.value || useImage || useAccount)) data.tenantId = binding().tenantId;
      if (kind === 'register' && !useImage) {
        data.nickname = fields.nickname?.trim(); data.agree = fields.agree; data.memberIdentity = fields.memberIdentity;
        if (!data.nickname || data.nickname.length > 30 || data.agree !== true || !['buyer', 'supplier'].includes(data.memberIdentity)) throw new Error('请完善昵称、会员身份并同意协议');
      }
      const legacyUrl = kind === 'reset-password' ? '/member/user/reset-password' : '/member/auth/' + kind;
      const endpoint = useImage ? 'register-image' : useAccount ? 'account-login' : kind;
      let result;
      if (usePlatform) {
        const response = await tenantTransport('/member/mall-auth/platform-login', {
          method: 'POST',
          header: { terminal: '20', 'Content-Type': 'application/json' },
          data: { account: data.account, password: data.password },
        });
        if (!current()) return null;
        if (response?.code !== 0 || !response.data) throw new Error('平台账号或密码不正确');
        const identity = await verifyPlatformSession(response.data, current);
        result = { code: 0, data: identity };
      } else {
        result = await transport(endpoint, data, legacyUrl, kind === 'reset-password' ? 'PUT' : 'POST', current);
      }
      if (!current()) return null;
      if (result?.code !== 0) throw new Error(result?.msg || '认证失败，请重试');
      if (kind === 'reset-password') {
        $store('user').logout(); toast('密码已重置，请重新登录');
      } else {
        const identity = result.data;
        if (usePlatform) {
          $store('user').setPlatformToken(identity.accessToken, identity.refreshToken, identity);
        } else if (!identity?.accessToken || !identity.refreshToken || !/^[1-9]\d*$/.test(String(identity.userId))
            || ((newProtocol.value || useImage || useAccount) && String(identity.tenantId) !== entry.value.tenantId)
            || (identity.tenantId != null && String(identity.tenantId) !== getControlledTenantId())) {
          throw new Error('认证返回的会员身份不匹配');
        } else {
          $store('user').setToken(identity.accessToken, identity.refreshToken, identity.tenantId ? identity : undefined);
        }
        toast(kind === 'register' ? '注册成功' : '登录成功');
      }
      return result;
    } catch (reason) {
      if (current()) {
        toast(reason.message || '认证失败，请重试');
        if (kind === 'register' && imageRegistration.value) { imageState.invalidate(); imageChallenge.value = null; model.imageCode = ''; }
      }
      return null;
    }
    finally { submitting.value = false; }
  }
  const timer = setInterval(() => { tick.value = Date.now(); }, 1000);
  onMounted(refresh);
  onBeforeUnmount(() => { alive = false; revision++; loadRevision++; state.invalidate(); imageState.invalidate(); clearInterval(timer); });
  return { entry, error, newProtocol, available, countdown, codeText, sending, submitting, refresh, send, submit,
    imageRegistration, loginNameEnabled, imageChallenge, imageSending, imageCountdown, imageExpiresIn, loadImage,
    registrationOptions, selectedTenant, registrationChoice, registrationTarget, choiceNotice, choiceError,
    platformLogin };
}
