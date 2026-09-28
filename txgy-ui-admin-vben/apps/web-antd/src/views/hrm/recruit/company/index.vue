<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';

import { Empty, message } from 'ant-design-vue';

import { getRecruitCompanyProfile, type HrmRecruitCompanyApi, saveRecruitCompanyProfile, uploadRecruitCompanyImage } from '#/api/hrm/recruit/company';
import { MapDialog } from '#/components/map';
import { ImageUpload } from '#/components/upload';

const loading = ref(false);
const saving = ref(false);
const mapDialogRef = ref<InstanceType<typeof MapDialog>>();
const form = reactive<HrmRecruitCompanyApi.Company>({ name: '', logo: '' });
const products = ref<HrmRecruitCompanyApi.Product[]>([]);
const album = ref<string[]>([]);
const financingStageOptions = ['不融资', '股权融资', '上市融资'] as const;

function companyImageUpload(category: 'album' | 'logo' | 'product') {
  return (file: File, onUploadProgress?: Parameters<typeof uploadRecruitCompanyImage>[2]) =>
    uploadRecruitCompanyImage(file, category, onUploadProgress);
}

function addProduct() {
  if (products.value.length >= 10) return;
  products.value = [...products.value, { name: '', intro: '', image: '' }];
}

function removeProduct(index: number) {
  products.value = products.value.filter((_, itemIndex) => itemIndex !== index);
}

function parseImageList(value?: string) {
  if (!value) return [];
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed)
      ? parsed.filter((item): item is string => typeof item === 'string' && item.trim().length > 0)
      : [];
  } catch {
    return [];
  }
}

function chooseMapLocation() {
  mapDialogRef.value?.open(form.longitude, form.latitude);
}

function handleMapConfirm(point: { address: string; latitude: string; longitude: string; }) {
  form.longitude = Number(point.longitude);
  form.latitude = Number(point.latitude);
  if (!form.address?.trim() && point.address) form.address = point.address;
}

async function load() {
  loading.value = true;
  try {
    const result = await getRecruitCompanyProfile();
    if (result) Object.assign(form, result);
    try {
      products.value = result?.products ? JSON.parse(result.products) : [];
      if (!Array.isArray(products.value)) products.value = [];
    } catch {
      products.value = [];
    }
    album.value = parseImageList(result?.album);
  } finally {
    loading.value = false;
  }
}

async function save() {
  if (!form.name.trim()) {
    message.warning('请填写企业名称');
    return;
  }
  saving.value = true;
  try {
    await saveRecruitCompanyProfile({
      ...form,
      products: JSON.stringify(products.value.filter((item) => item.name.trim())),
      album: JSON.stringify(album.value),
    });
    message.success('企业信息已保存');
    await load();
  } finally {
    saving.value = false;
  }
}

onMounted(() => void load());
</script>

<template>
  <div class="company-admin" :aria-busy="loading">
    <a-card title="招聘企业信息" :loading="loading">
      <template #extra><a-button type="primary" :loading="saving" @click="save">保存</a-button></template>
      <div class="company-form-grid">
        <label>企业对外名称<a-input v-model:value="form.name" :maxlength="255" /></label>
        <label>企业简称<a-input v-model:value="form.shortName" :maxlength="128" /></label>
        <label>所属行业<a-input v-model:value="form.industry" :maxlength="128" /></label>
        <label>企业规模<a-input v-model:value="form.scale" placeholder="如：50-99 人" /></label>
        <label>融资阶段<a-select v-model:value="form.financingStage" allow-clear placeholder="请选择融资阶段"><a-select-option v-for="stage in financingStageOptions" :key="stage" :value="stage">{{ stage }}</a-select-option></a-select></label>
        <label>企业性质<a-input v-model:value="form.nature" placeholder="如：民营、国有" /></label>
        <label>企业 Logo
          <ImageUpload
            v-model:value="form.logo"
            :api="companyImageUpload('logo')"
            directory="hrm/recruit/company/logo"
            :max-number="1"
            :max-size="5"
            :show-description="false"
          />
        </label>
        <label class="wide">企业简介<a-textarea v-model:value="form.summary" :maxlength="2000" :rows="5" show-count /></label>
        <label class="wide">企业地址<div class="address-row"><a-input v-model:value="form.address" :maxlength="255" /><a-button @click="chooseMapLocation">地图选点</a-button></div><small v-if="form.longitude != null && form.latitude != null">BD09：{{ form.longitude }}, {{ form.latitude }}</small></label>
        <label>企业官网<a-input v-model:value="form.website" :maxlength="255" /></label>
      </div>
      <section class="products-section">
        <div class="section-heading"><h3>产品与服务</h3><a-button :disabled="products.length >= 10" @click="addProduct">添加条目</a-button></div>
        <div v-for="(product, index) in products" :key="index" class="product-row">
          <a-input v-model:value="product.name" placeholder="名称" />
          <a-input v-model:value="product.intro" placeholder="介绍" />
          <ImageUpload
            v-model:value="product.image"
            :api="companyImageUpload('product')"
            directory="hrm/recruit/company/product"
            :max-number="1"
            :max-size="5"
            :show-description="false"
          />
          <a-button danger @click="removeProduct(index)">删除</a-button>
        </div>
        <Empty v-if="!products.length" description="暂无产品与服务条目" />
      </section>
      <section class="products-section album-section">
        <div class="section-heading">
          <div>
            <h3>公司相册</h3>
            <span>上传企业环境、团队活动、生产现场等图片，最多 12 张</span>
          </div>
        </div>
        <ImageUpload
          v-model:value="album"
          :api="companyImageUpload('album')"
          directory="hrm/recruit/company/album"
          :max-number="12"
          :max-size="5"
          multiple
        />
        <p v-if="!album.length" class="upload-tip">暂未上传公司相册图片</p>
      </section>
      <section class="products-section">
        <div class="section-heading"><h3>工商信息（查不到请留空）</h3><span>不会自动生成或推断工商数据</span></div>
        <div class="company-form-grid">
          <label>成立日期<a-input v-model:value="form.establishedDate" type="date" /></label>
          <label>注册资本<a-input v-model:value="form.registeredCapital" /></label>
          <label>统一社会信用代码<a-input v-model:value="form.creditCode" /></label>
          <label>法定代表人<a-input v-model:value="form.legalPerson" /></label>
          <label>信息来源<a-input v-model:value="form.dataSource" /></label>
          <label>来源链接<a-input v-model:value="form.dataSourceUrl" /></label>
        </div>
      </section>
    </a-card>
    <MapDialog ref="mapDialogRef" @confirm="handleMapConfirm" />
  </div>
</template>

<style scoped>
.company-admin{padding:16px}.company-form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px 24px}.company-form-grid label{display:grid;gap:8px;color:#496b7a;font-size:13px}.company-form-grid .wide{grid-column:1/-1}.address-row{display:flex;gap:8px}.address-row :deep(.ant-input){flex:1}.company-form-grid small{color:#7593a0}.products-section{margin-top:28px}.section-heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:14px}.section-heading h3{margin:0;color:#123f49;font-size:16px}.section-heading span{color:#7593a0;font-size:12px}.product-row{display:grid;grid-template-columns:1fr 2fr 132px auto;align-items:start;gap:8px;margin:10px 0}.product-row :deep(.ant-upload-picture-card-wrapper){width:132px}.album-section .section-heading{align-items:flex-start}.album-section .section-heading>div{display:grid;gap:5px}.upload-tip{margin:8px 0 0;color:#7593a0;font-size:12px}@media(max-width:720px){.company-form-grid{grid-template-columns:1fr}.company-form-grid .wide{grid-column:auto}.product-row{grid-template-columns:1fr}.product-row :deep(.ant-upload-picture-card-wrapper){width:auto}.company-admin{padding:8px}}
</style>
