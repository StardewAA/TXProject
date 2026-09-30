<script lang="ts" setup>
import type { MallSpuApi } from '#/api/mall/product/spu';
import type {
  PropertyAndValues,
  RuleConfig,
} from '#/views/mall/product/spu/components';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useAccess } from '@vben/access';
import { Page, useVbenModal } from '@vben/common-ui';
import { useTabs } from '@vben/hooks';
import { convertToInteger, formatToFraction } from '@vben/utils';

import { Alert, Button, Card, message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { createSpu, getSpu, getSpuReviewPolicy, updateSpu } from '#/api/mall/product/spu';

import { canOperateProduct, reviewStatusText } from '../review-model';
import { getPropertyList, SkuList } from '#/views/mall/product/spu/components';

import {
  useDeliveryFormSchema,
  useDescriptionFormSchema,
  useInfoFormSchema,
  useOtherFormSchema,
  useSkuFormSchema,
} from './data';
import ProductAttributes from './modules/product-attributes.vue';
import ProductPropertyAddForm from './modules/product-property-add-form.vue';

const spuId = ref<number>();
const { params, name } = useRoute();
const router = useRouter();
const { closeCurrentTab } = useTabs();
const activeTabName = ref('info');
const formLoading = ref(false); // 表单的加载中：1）修改时的数据加载；2）提交的按钮禁用
// 防止保存请求尚未完成时重复点击，造成重复创建商品
const submitting = ref(false);
const { hasAccessByCodes } = useAccess();
const policy = ref<MallSpuApi.ReviewPolicy>();
const policyError = ref(false);
const originalShopId = ref<number>();
const policyLoading = ref(true);
const isDetail = computed(() => name === 'ProductSpuDetail' || policyLoading.value
  || !canOperateProduct(
    policy.value,
    originalShopId.value,
    hasAccessByCodes(['product:spu:review']),
  )
  || (spuId.value ? !hasAccessByCodes(['product:spu:update']) : !hasAccessByCodes(['product:spu:create']))
  || formData.value.reviewStatus === 10);
const initializingForm = ref(false); // 详情回填时不触发 SKU 重置逻辑
const skuListRef = ref(); // 商品属性列表 Ref

const formData = ref<MallSpuApi.Spu>({
  name: '',
  categoryId: undefined,
  keyword: '',
  picUrl: '',
  sliderPicUrls: [],
  introduction: '',
  deliveryTypes: [],
  deliveryTemplateId: undefined,
  brandId: undefined,
  specType: false,
  subCommissionType: false,
  skus: [
    {
      name: '', // SKU 名称，提交时会自动使用 SPU 名称
      price: 0,
      marketPrice: 0,
      costPrice: 0,
      barCode: '',
      picUrl: '',
      stock: 0,
      weight: 0,
      volume: 0,
      firstBrokeragePrice: 0,
      secondBrokeragePrice: 0,
    },
  ],
  description: '',
  sort: 0,
  giveIntegral: 0,
  virtualSalesCount: 0,
}); // spu 表单数据
// 新增商品尚未形成 WMS 主数据，期初库存必须始终可填写。
// 只有已经保存且实际绑定 WMS 的存量商品，库存才按 WMS 权威数据只读展示。
const stockReadonly = computed(() => Boolean(spuId.value) && (formData.value.skus ?? []).some(
  (sku) => Number(sku.wmsSkuId ?? 0) > 0 || Number(sku.wmsWarehouseId ?? 0) > 0,
));
const propertyList = ref<PropertyAndValues[]>([]); // 商品属性列表
const ruleConfig: RuleConfig[] = [
  {
    name: 'stock',
    rule: (arg: number) => arg >= 0,
    message: '商品期初库存不能小于 0 ！！！',
  },
  {
    name: 'price',
    rule: (arg: number) => arg >= 0.01,
    message: '商品销售价格必须大于等于 0.01 元！！！',
  },
  {
    name: 'marketPrice',
    rule: (arg: number) => arg >= 0.01,
    message: '商品市场价格必须大于等于 0.01 元！！！',
  },
  {
    name: 'costPrice',
    rule: (arg: number) => arg >= 0.01,
    message: '商品成本价格必须大于等于 0.00 元！！！',
  },
]; // sku 相关属性校验规则

const [InfoForm, infoFormApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    formItemClass: 'col-span-2',
    labelWidth: 120,
  },
  layout: 'horizontal',
  schema: useInfoFormSchema(),
  showDefaultActions: false,
});

const [SkuForm, skuFormApi] = useVbenForm({
  commonConfig: {
    labelWidth: 120,
  },
  layout: 'horizontal',
  schema: useSkuFormSchema(propertyList.value, isDetail.value),
  showDefaultActions: false,
  handleValuesChange: (values, fieldsChanged) => {
    if (initializingForm.value || isDetail.value) {
      return;
    }
    if (
      fieldsChanged.includes('subCommissionType') &&
      values.subCommissionType !== formData.value.subCommissionType
    ) {
      formData.value.subCommissionType = values.subCommissionType;
      handleChangeSubCommissionType();
    }
    if (
      fieldsChanged.includes('specType') &&
      values.specType !== formData.value.specType
    ) {
      formData.value.specType = values.specType;
      handleChangeSpec();
    }
  },
});

const [ProductPropertyAddFormModal, productPropertyAddFormApi] = useVbenModal({
  connectedComponent: ProductPropertyAddForm,
  destroyOnClose: true,
});

const [DeliveryForm, deliveryFormApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    formItemClass: 'col-span-2',
    labelWidth: 120,
  },
  layout: 'horizontal',
  schema: useDeliveryFormSchema(),
  showDefaultActions: false,
});

const [DescriptionForm, descriptionFormApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    formItemClass: 'col-span-2',
    labelWidth: 120,
  },
  layout: 'vertical',
  schema: useDescriptionFormSchema(),
  showDefaultActions: false,
});

const [OtherForm, otherFormApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    formItemClass: 'col-span-2',
    labelWidth: 120,
  },
  layout: 'horizontal',
  schema: useOtherFormSchema(),
  showDefaultActions: false,
});

/** tab 切换 */
function handleTabChange(key: string) {
  activeTabName.value = key;
}

/** 提交表单 */
async function handleSubmit() {
  if (submitting.value || isDetail.value) {
    return;
  }
  submitting.value = true;
  let submitted = false;
  try {
    const values: MallSpuApi.Spu = await infoFormApi
      .merge(skuFormApi)
      .merge(deliveryFormApi)
      .merge(descriptionFormApi)
      .merge(otherFormApi)
      .submitAllForm(true);
    // 源店与目标店双重校验；试点店铺不能通过旧链接或更换所属店铺绕过只读策略。
    if (!canOperateProduct(
      policy.value,
      originalShopId.value,
      hasAccessByCodes(['product:spu:review']),
    )
      || !canOperateProduct(
        policy.value,
        values.shopId,
        hasAccessByCodes(['product:spu:review']),
      )
      || !values.shopId) {
      message.warning('当前后台账号没有该店铺经营权限，请核对企业、店铺授权和角色权限。');
      return;
    }
    if (spuId.value) {
      if (!Number.isSafeInteger(formData.value.contentVersion)) {
        message.error('缺少商品版本，请刷新详情后重试，不能使用旧页面保存。');
        return;
      }
      values.expectedVersion = formData.value.contentVersion;
    }
    // 校验商品名称不能为空（用于 SKU name）
    if (!values.name || values.name.trim() === '') {
      message.error('商品名称不能为空');
      return;
    }
    try {
      // 校验 sku
      skuListRef.value.validateSku();
    } catch {
      message.error('【库存价格】不完善，请填写相关信息');
      return;
    }
    // 金额转换：元转分
    values.skus = formData.value.skus!.map((item) => ({
      ...item,
      name: values.name,
      price: convertToInteger(item.price),
      marketPrice: convertToInteger(item.marketPrice),
      costPrice: convertToInteger(item.costPrice),
      firstBrokeragePrice: convertToInteger(item.firstBrokeragePrice),
      secondBrokeragePrice: convertToInteger(item.secondBrokeragePrice),
    }));
    // 处理轮播图列表：上传组件可能返回对象或字符串，统一处理成字符串数组
    const newSliderPicUrls: any[] = [];
    values.sliderPicUrls!.forEach((item: any) => {
      // 如果是前端选的图
      typeof item === 'object'
        ? newSliderPicUrls.push(item.url)
        : newSliderPicUrls.push(item);
    });
    values.sliderPicUrls = newSliderPicUrls;

    // 提交数据；成功后关闭当前页，避免用户误以为未提交而再次创建
    await (spuId.value ? updateSpu(values) : createSpu(values));
    submitted = true;
    message.success('已保存为草稿，需提交审核；通过后由有权限的经营人员主动上架。');
    await closeCurrentTab();
    await router.push('/mall/product/spu');
  } finally {
    // 成功后保持锁定，即使标签页关闭/跳转异常也不能再次创建；失败时允许修正后重试
    if (!submitted) {
      submitting.value = false;
    }
  }
}

/** 获得详情 */
async function getDetail() {
  setFormsReadonly();
  // 将 SKU 的属性，整理成 PropertyAndValues 数组
  propertyList.value = getPropertyList(formData.value);
  formLoading.value = true;
  try {
    const res = await getSpu(spuId.value!);
    originalShopId.value = res.shopId;
    // 金额转换：分转元
    res.skus = res.skus?.map((item) => ({
      ...item,
      price: Number(formatToFraction(item.price)),
      marketPrice: Number(formatToFraction(item.marketPrice)),
      costPrice: Number(formatToFraction(item.costPrice)),
      firstBrokeragePrice: Number(formatToFraction(item.firstBrokeragePrice)),
      secondBrokeragePrice: Number(
        formatToFraction(item.secondBrokeragePrice),
      ),
    }));
    initializingForm.value = true;
    formData.value = res;
    // 将 SKU 的属性，整理成 PropertyAndValues 数组
    propertyList.value = getPropertyList(formData.value);
    // 初始化各表单值
    void infoFormApi.setValues(res);
    void skuFormApi.setValues(res);
    void deliveryFormApi.setValues(res);
    void descriptionFormApi.setValues(res);
    void otherFormApi.setValues(res);
    await nextTick();
    await nextTick();
  } finally {
    initializingForm.value = false;
    formLoading.value = false;
  }
}

// =========== sku form 逻辑 ===========

/** 打开属性添加表单 */
function openPropertyAddForm() {
  if (!isDetail.value) productPropertyAddFormApi.open();
}

/** 调用 SkuList generateTableData 方法*/
function generateSkus(propertyList: PropertyAndValues[]) {
  if (!isDetail.value) skuListRef.value.generateTableData(propertyList);
}

/** 分销类型 */
function handleChangeSubCommissionType() {
  if (isDetail.value) return;
  // 默认为零，类型切换后也要重置为零
  for (const item of formData.value.skus!) {
    item.firstBrokeragePrice = 0;
    item.secondBrokeragePrice = 0;
  }
}

/** 选择规格 */
function handleChangeSpec() {
  if (isDetail.value) return;
  // 重置商品属性列表
  propertyList.value = [];
  // 重置 sku 列表
  formData.value.skus = [
    {
      name: '', // SKU 名称，提交时会自动使用 SPU 名称
      price: 0,
      marketPrice: 0,
      costPrice: 0,
      barCode: '',
      picUrl: '',
      stock: 0,
      weight: 0,
      volume: 0,
      firstBrokeragePrice: 0,
      secondBrokeragePrice: 0,
    },
  ];
}

/** 监听 sku form schema 变化，更新表单 */
watch(
  propertyList,
  () => {
    skuFormApi.updateSchema(
      useSkuFormSchema(propertyList.value, isDetail.value),
    );
  },
  { deep: true },
);

function setFormsReadonly() {
  for (const api of [infoFormApi, skuFormApi, deliveryFormApi, descriptionFormApi, otherFormApi]) api.setDisabled(isDetail.value);
  skuFormApi.updateSchema(useSkuFormSchema(propertyList.value, isDetail.value));
}
watch(isDetail, () => setFormsReadonly());
setFormsReadonly();

/** 初始化：策略加载失败继续只读，经营范围以服务端返回为准。 */
onMounted(async () => {
  spuId.value = params.id ? Number(params.id) : undefined;
  try {
    policy.value = await getSpuReviewPolicy();
    infoFormApi.updateSchema(useInfoFormSchema(policy.value.adminWriteLockedShopIds ?? []));
  } catch {
    policyError.value = true;
  }
  try {
    if (spuId.value) await getDetail();
  } finally {
    policyLoading.value = false;
    setFormsReadonly();
  }
});
</script>

<template>
  <div>
    <ProductPropertyAddFormModal v-if="!isDetail" :property-list="propertyList" />

    <Page auto-content-height>
      <Alert class="mb-3" show-icon type="info"
        :message="isDetail ? '当前商品仅供查看。商品经营范围由后台企业与店铺授权决定，待审内容须撤回后修改。' : '当前账号可在获授权店铺维护商品；保存后成为草稿并下架，必须送审，通过后才能重新上架。'"
        description="审核状态与销售状态相互独立。平台经营账号与审核账号应分离，提交人员不能审核自己的提交。" />
      <Alert v-if="policyError" class="mb-3" show-icon type="warning" message="策略加载失败，已保持只读，请刷新后重试。" />
      <div class="mb-3">{{ reviewStatusText(formData.reviewStatus) }} · 内容版本 {{ formData.contentVersion ?? '历史' }}</div>
      <Card
        class="h-full w-full"
        :loading="formLoading"
        :tab-list="[
          {
            key: 'info',
            tab: '基础设置',
          },
          {
            key: 'sku',
            tab: '价格库存',
          },
          {
            key: 'delivery',
            tab: '物流设置',
          },
          {
            key: 'description',
            tab: '商品详情',
          },
          {
            key: 'other',
            tab: '其它设置',
          },
        ]"
        :active-tab-key="activeTabName"
        @tab-change="handleTabChange"
      >
        <template #tabBarExtraContent>
          <Button
            type="primary"
            v-if="!isDetail"
            :loading="submitting"
            :disabled="submitting"
            @click="handleSubmit"
          >
            保存草稿并重新审核
          </Button>
          <Button type="default" v-else @click="() => closeCurrentTab()">
            返回列表
          </Button>
        </template>

        <InfoForm class="w-3/5" v-show="activeTabName === 'info'" />
        <SkuForm class="w-full" v-show="activeTabName === 'sku'">
          <template #singleSkuList>
            <SkuList
              ref="skuListRef"
              class="w-full"
              :readonly-stock="stockReadonly"
              :is-detail="isDetail"
              :prop-form-data="formData"
              :property-list="propertyList"
              :rule-config="ruleConfig"
            />
          </template>
          <template #productAttributes>
            <div>
              <Button v-if="!isDetail" class="mb-10px mr-15px" @click="openPropertyAddForm">
                添加属性
              </Button>
              <ProductAttributes
                :is-detail="isDetail"
                :property-list="propertyList"
                @success="generateSkus"
              />
            </div>
          </template>
          <template #batchSkuList>
            <SkuList
              :is-batch="true"
              :readonly-stock="stockReadonly"
              :is-detail="isDetail"
              :prop-form-data="formData"
              :property-list="propertyList"
            />
          </template>
          <template #multiSkuList>
            <SkuList
              ref="skuListRef"
              :readonly-stock="stockReadonly"
              :is-detail="isDetail"
              :prop-form-data="formData"
              :property-list="propertyList"
              :rule-config="ruleConfig"
            />
          </template>
        </SkuForm>
        <DeliveryForm class="w-3/5" v-show="activeTabName === 'delivery'" />
        <DescriptionForm
          class="w-3/5"
          v-show="activeTabName === 'description'"
        />
        <OtherForm class="w-3/5" v-show="activeTabName === 'other'" />
      </Card>
    </Page>
  </div>
</template>
<style lang="scss" scoped>
:deep(.ant-tabs-tab-btn) {
  font-size: 14px !important;
}
</style>
