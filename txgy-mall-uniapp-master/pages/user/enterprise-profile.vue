<template>
  <s-layout title="企业资料与资质审核">
    <view class="enterprise-profile">
      <view class="heading">完善企业信息</view>
      <view class="notice">注册账号 → 提交企业资料 → 后台审核。普通客户归属不代表企业采购或销售权限。</view>
      <view v-if="error" class="error">{{ error }}</view>
      <button size="mini" :disabled="busy || loading" @tap="load">{{ loading ? '读取中' : '刷新审核状态' }}</button>
      <view v-if="loaded" class="notice">{{ statusText }}</view>
      <view v-if="loaded">
        <view v-for="field in fields" :key="field.key" class="field">
          <view>{{ field.label }}{{ field.required ? ' *' : '' }}</view>
          <input v-model="form[field.key]" :maxlength="field.max" :disabled="!editable || busy" :placeholder="'请输入' + field.label" />
        </view>
        <view class="field">营业执照（PNG/JPEG，最大1MB，仅本人及授权审核人员可见） *</view>
        <image v-if="form.licenseData" :src="form.licenseData" mode="aspectFit" class="license" />
        <button v-if="editable" size="mini" :disabled="busy" @tap="license">选择营业执照图片</button>
        <view class="field">企业介绍<textarea v-model="form.introduction" maxlength="1024" :disabled="!editable || busy" placeholder="主营产品与企业介绍（选填）" /></view>
        <button v-if="editable" type="primary" :disabled="busy" @tap="submit">{{ busy ? '提交中' : form.status === 2 ? '修改并重新提交' : '提交后台审核' }}</button>
      </view>
      <view class="notice">平台审核不代表外部工商认证。供应商审核通过后，还须关联店铺才能经营；销售管理可在 PC 销售中心办理。</view>
    </view>
  </s-layout>
</template>
<script setup>
import { computed, reactive, ref, watch, onBeforeUnmount } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import sheep from '@/sheep';
import EnterpriseApi from '@/sheep/api/member/enterprise';
import { captureTenantOperation, isTenantOperationCurrent } from '@/sheep/helper/tenant-runtime';
import { emptyEnterpriseApplication, enterpriseApplicationPayload } from '@/sheep/helper/enterprise-application.mjs';
const form = reactive(emptyEnterpriseApplication()), loaded = ref(false), loading = ref(false), busy = ref(false), error = ref(''), identity = ref('');
let alive = true, revision = 0, ownerOperation;
const ownerId = ref('');
const fields = [ { key:'name', label:'企业名称', max:100, required:true }, { key:'creditCode', label:'统一社会信用代码', max:18, required:true },
  { key:'contactName', label:'联系人', max:32, required:true }, { key:'contactMobile', label:'联系电话', max:11, required:true }, { key:'address', label:'企业地址', max:255, required:true } ];
const editable = computed(() => loaded.value && !loading.value && ownerId.value === String(sheep.$store('user').userInfo.id) && ['buyer','supplier'].includes(identity.value) && (form.status === undefined || form.status === 2));
const statusText = computed(() => !identity.value ? '原有账号如需开通企业身份，请联系平台，原有购物功能不受影响。'
  : form.status === 0 ? '已提交后台审核，尚未认证成功。' : form.status === 1 ? '企业注册、资质认证成功，企业采购权限以后台授权为准。'
  : form.status === 2 ? '审核未通过：' + (form.auditRemark || '请补充企业资料') : '请填写企业资料后提交审核。');
function reset() { Object.keys(form).forEach(key => delete form[key]); Object.assign(form, emptyEnterpriseApplication()); loaded.value = false; identity.value = ''; ownerId.value = ''; ownerOperation = undefined; }
async function load() {
  if (busy.value) return;
  const rev = ++revision, operation = captureTenantOperation(); loading.value = true; reset(); error.value = '';
  const current = () => alive && revision === rev && isTenantOperationCurrent(operation);
  try {
    const profile = await sheep.$store('user').getInfo();
    if (!current()) return;
    if (!profile) throw new Error('会员信息暂无法读取，请重新登录或重试');
    const response = await EnterpriseApi.mine();
    if (!current()) return;
    if (response?.code !== 0) throw new Error(response?.msg || '申请信息读取失败');
    if (response.data) Object.assign(form, response.data);
    identity.value = profile.memberIdentity || ''; ownerId.value = String(profile.id); ownerOperation = operation; loaded.value = true;
  } catch (reason) { if (current()) error.value = reason.message || '加载失败，请重试'; }
  finally { if (alive && revision === rev) loading.value = false; }
}
function license() {
  if (!editable.value || busy.value) return;
  // #ifdef H5
  const rev = revision, operation = captureTenantOperation();
  const current = () => alive && revision === rev && isTenantOperationCurrent(operation) && editable.value && !busy.value;
  uni.chooseImage({ count:1, extension:['.png','.jpg','.jpeg'], success(result) {
    if (!current()) return;
    const file = result.tempFiles?.[0];
    if (!(file instanceof Blob) || !['image/png','image/jpeg'].includes(file.type) || file.size > 1048576) { error.value = '请选择1MB以内PNG或JPEG营业执照'; return; }
    const reader = new FileReader(); reader.onload = () => { if (current()) { form.licenseData = String(reader.result); error.value = ''; } };
    reader.onerror = () => { if (current()) error.value = '图片读取失败，请重新选择'; }; reader.readAsDataURL(file);
  } });
  // #endif
  // #ifndef H5
  uni.showToast({ title:'请使用 H5 或 PC 端上传企业营业执照', icon:'none' });
  // #endif
}
async function submit() {
  if (!editable.value || busy.value) return;
  if (!ownerOperation || !isTenantOperationCurrent(ownerOperation)) { sessionChanged(); return; }
  const operation = captureTenantOperation(), rev = revision;
  const current = () => alive && revision === rev && isTenantOperationCurrent(operation);
  let payload;
  try { payload = enterpriseApplicationPayload(form); } catch (reason) { error.value = reason.message; return; }
  busy.value = true; error.value = '';
  try {
    const response = await EnterpriseApi.apply(payload);
    if (!current()) return;
    if (response?.code !== 0) throw new Error(response?.msg || '提交失败，请重试');
    form.status = 0; uni.showToast({ title:'已提交后台审核', icon:'none' });
  } catch (reason) { if (current()) error.value = reason.message || '提交失败，请重试'; }
  finally { if (alive && rev === revision) busy.value = false; }
}
function sessionChanged() { revision++; reset(); error.value = '账号已变化，请重新读取企业资料'; loading.value = false; busy.value = false; }
watch(() => sheep.$store('user').userInfo.id, id => { if (ownerId.value && ownerId.value !== String(id)) sessionChanged(); }, { flush:'sync' });
uni.$on('mall:session-changed', sessionChanged);
onShow(load);
onBeforeUnmount(() => { alive = false; revision++; reset(); uni.$off('mall:session-changed', sessionChanged); });
</script>
<style scoped>.enterprise-profile{margin:24rpx;padding:28rpx;background:#fff;border-radius:16rpx}.heading{font-size:36rpx;font-weight:700}.notice{font-size:25rpx;line-height:1.8;color:#61736f;margin:20rpx 0}.error{color:#b42318;margin:20rpx 0}.field{margin:24rpx 0;font-size:28rpx}.field input,.field textarea{margin-top:12rpx;padding:18rpx;background:#f3f7f6;border-radius:8rpx}.license{width:100%;height:280rpx}button{margin-top:20rpx}</style>
