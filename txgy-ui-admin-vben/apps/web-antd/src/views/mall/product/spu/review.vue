<script setup lang="ts">
import type { MallShopApi } from '#/api/mall/product/shop';
import type { MallSpuApi } from '#/api/mall/product/spu';

import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router';

import { useAccess } from '@vben/access';
import { confirm, Page } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import { Alert, Button, Card, Descriptions, Empty, Image, Input, Spin, Table, Tag } from 'ant-design-vue';

import { getShopList } from '#/api/mall/product/shop';
import { decideSpuReview, getSpu, getSpuReviewHistory, getSpuReviewPolicy } from '#/api/mall/product/spu';
import { getPropertyList, SkuList } from '#/views/mall/product/spu/components';

import { canDecideReview, currentSubmission, parseReviewSnapshot, reviewActionText, reviewStatusText, snapshotChanges } from './review-model';

const route = useRoute();
const router = useRouter();
const { hasAccessByCodes } = useAccess();
const userStore = useUserStore();
const product = ref<MallSpuApi.Spu>();
const policy = ref<MallSpuApi.ReviewPolicy>();
const history = ref<MallSpuApi.ReviewRecord[]>([]);
const shops = ref<MallShopApi.Shop[]>([]);
const selectedRecordId = ref<number>();
const loading = ref(false);
const busy = ref(false);
const error = ref('');
const decisionMessage = ref('');
const mustRefresh = ref(false);
const reason = ref('');
const activeTab = ref('info');

const submitted = computed(() => currentSubmission(product.value, history.value));
const isOwnSubmission = computed(() => submitted.value?.actorType === 2
  && submitted.value.actorId != null
  && String(submitted.value.actorId) === String(userStore.userInfo?.id));
const selectedRecord = computed(() => history.value.find((item) => item.id === selectedRecordId.value)
  ?? submitted.value ?? history.value.find((item) => item.action === 'SUBMIT') ?? history.value[0]);
const snapshot = computed(() => parseReviewSnapshot(selectedRecord.value?.snapshot));
const previousRecord = computed(() => history.value.find((item) => item.action === 'SUBMIT'
  && item.id < (selectedRecord.value?.id ?? 0) && item.submissionId !== selectedRecord.value?.submissionId));
const changes = computed(() => snapshot.value
  ? snapshotChanges(snapshot.value, parseReviewSnapshot(previousRecord.value?.snapshot)) : []);
const snapshotShop = computed(() => shops.value.find((item) => item.id === snapshot.value?.shopId));
const propertyList = computed(() => getPropertyList(snapshot.value ?? {}));
const snapshotForSku = computed<MallSpuApi.Spu>(() => ({
  ...snapshot.value,
  skus: snapshot.value?.skus?.map((item) => ({
    ...item,
    price: Number(item.price ?? 0) / 100,
    marketPrice: Number(item.marketPrice ?? 0) / 100,
    costPrice: Number(item.costPrice ?? 0) / 100,
    firstBrokeragePrice: Number(item.firstBrokeragePrice ?? 0) / 100,
    secondBrokeragePrice: Number(item.secondBrokeragePrice ?? 0) / 100,
  })),
}));
const canReview = computed(() => !busy.value && !loading.value && !error.value && !mustRefresh.value
  && !isOwnSubmission.value
  && hasAccessByCodes(['product:spu:review'])
  && selectedRecord.value?.id === submitted.value?.id
  && canDecideReview(product.value, submitted.value, policy.value));

function dateText(value: string | number) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN', { hour12: false });
}

let requestNumber = 0;
async function loadReview() {
  if (busy.value) return;
  const request = ++requestNumber;
  loading.value = true;
  error.value = '';
  mustRefresh.value = true;
  product.value = undefined;
  policy.value = undefined;
  history.value = [];
  selectedRecordId.value = undefined;
  const id = Number(route.params.id);
  if (!Number.isSafeInteger(id) || id <= 0) {
    error.value = '商品编号无效';
    loading.value = false;
    return;
  }
  try {
    const [nextPolicy, nextProduct, records] = await Promise.all([
      getSpuReviewPolicy(), getSpu(id), getSpuReviewHistory(id),
    ]);
    if (request !== requestNumber) return;
    policy.value = nextPolicy;
    product.value = nextProduct;
    history.value = records.filter((item) => item.spuId === id).sort((left, right) => right.id - left.id);
    mustRefresh.value = false;
  } catch {
    if (request === requestNumber) error.value = '商品、审核策略或历史加载失败，审核已禁用。请检查权限或刷新重试。';
  } finally {
    if (request === requestNumber) loading.value = false;
  }
}

async function decide(approved: boolean) {
  if (!canReview.value || !product.value?.id || !submitted.value?.submissionId) return;
  const comment = reason.value.trim();
  if (!approved && !comment) {
    decisionMessage.value = '驳回必须填写具体原因，便于销售人员修改。';
    return;
  }
  if (comment.length > 1000) {
    decisionMessage.value = '审核意见不能超过 1000 字。';
    return;
  }
  const payload: MallSpuApi.ReviewDecision = {
    id: product.value.id,
    expectedVersion: submitted.value.contentVersion,
    submissionId: submitted.value.submissionId,
    approved,
    reason: comment,
  };
  busy.value = true;
  decisionMessage.value = '';
  try {
    try {
      await confirm(approved
        ? `确认通过 V${payload.expectedVersion} 的本次提交？审核通过后仍下架，由销售人员主动上架。`
        : `确认驳回 V${payload.expectedVersion} 的本次提交？`);
    } catch {
      return;
    }
    await decideSpuReview(payload);
    decisionMessage.value = approved ? '审核通过，商品仍下架，等待销售人员主动上架。' : '已驳回，意见已记录并供销售人员查看。';
    reason.value = '';
    busy.value = false;
    await loadReview();
  } catch {
    // 可能是版本已变/他人已处理/响应丢失，保留意见但禁止盲目再次提交。
    mustRefresh.value = true;
    decisionMessage.value = '本次审核未能确认成功，可能已由他人处理或商家撤回。意见已保留，请刷新核对最新提交后再操作。';
  } finally {
    busy.value = false;
  }
}

watch(() => route.params.id, () => {
  reason.value = '';
  decisionMessage.value = '';
  shops.value = [];
  void loadReview();
  const id = route.params.id;
  void getShopList().then((data) => { if (route.params.id === id) shops.value = data; }).catch(() => {});
}, { immediate: true });
onBeforeUnmount(() => { ++requestNumber; });
onBeforeRouteLeave(() => !busy.value);
onBeforeRouteUpdate(() => !busy.value);
</script>

<template>
  <Page>
    <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
      <h2 class="text-lg font-semibold">商品审核 · #{{ route.params.id }}</h2>
      <div class="flex gap-2">
        <Button :disabled="busy || loading" @click="loadReview">刷新最新提交</Button>
        <Button :disabled="busy" @click="router.push('/mall/product/spu')">返回商品列表</Button>
      </div>
    </div>
    <Alert class="mb-3" show-icon type="info" message="审核对象是商家提交时的固定内容快照，不会用当前库存或最新商品详情替换。" description="通过审核不自动上架；后台不可修改经营资料。商家撤回或重新提交后，本页旧版本不能继续审批。" />
    <Alert v-if="error" class="mb-3" type="error" show-icon :message="error" />
    <Alert v-if="decisionMessage" class="mb-3" :type="mustRefresh ? 'warning' : 'info'" show-icon :message="decisionMessage" />
    <Spin :spinning="loading">
      <Card v-if="product" class="mb-3">
        <Descriptions :column="3" size="small">
          <Descriptions.Item label="当前商品">{{ product.name }}</Descriptions.Item>
          <Descriptions.Item label="审核状态"><Tag :color="product.reviewStatus === 10 ? 'orange' : 'default'">{{ reviewStatusText(product.reviewStatus) }}</Tag></Descriptions.Item>
          <Descriptions.Item label="当前 / 批准版本">{{ product.contentVersion ?? '历史' }} / {{ product.approvedVersion ?? '未批准' }}</Descriptions.Item>
          <Descriptions.Item label="提交编号" :span="2">{{ product.submissionId ?? '尚未送审' }}</Descriptions.Item>
          <Descriptions.Item label="最近意见">{{ product.reviewRemark || '—' }}</Descriptions.Item>
        </Descriptions>
        <Alert v-if="product.reviewStatus == null" type="warning" class="mt-3" message="此商品属于历史过渡，不代表审核通过。当前未修改在售商品按过渡规则保留，后续修改、复制、恢复必须审核。" />
      </Card>

      <Card v-if="snapshot && selectedRecord" class="mb-3"
        :title="`固定快照 V${selectedRecord.contentVersion} · ${reviewActionText(selectedRecord.action)}`"
        :tab-list="[{ key: 'info', tab: '基础资料' }, { key: 'sku', tab: '规格价格' }, { key: 'delivery', tab: '配送设置' }, { key: 'description', tab: '图文详情' }, { key: 'other', tab: '其它资料' }]"
        :active-tab-key="activeTab" @tab-change="(key) => activeTab = key">
        <template v-if="activeTab === 'info'">
          <Descriptions :column="2" bordered size="small">
            <Descriptions.Item label="商品名称">{{ snapshot.name }}</Descriptions.Item>
            <Descriptions.Item label="所属店铺">{{ snapshotShop?.name ?? `店铺 #${snapshot.shopId ?? '未关联'}` }}</Descriptions.Item>
            <Descriptions.Item label="企业归属">{{ snapshotShop?.enterpriseName ?? '请在企业 / 店铺档案核对' }}</Descriptions.Item>
            <Descriptions.Item label="分类 / 品牌编号">{{ snapshot.categoryId ?? '—' }} / {{ snapshot.brandId ?? '—' }}</Descriptions.Item>
            <Descriptions.Item label="关键词">{{ snapshot.keyword || '—' }}</Descriptions.Item>
            <Descriptions.Item label="简介">{{ snapshot.introduction || '—' }}</Descriptions.Item>
          </Descriptions>
          <p class="my-3 text-sm text-gray-500">店铺与企业名称为当前档案辅助信息；提交时店铺编号为 {{ snapshot.shopId ?? '未关联' }}。</p>
          <div class="flex flex-wrap gap-3">
            <Image v-if="snapshot.picUrl" :src="snapshot.picUrl" :width="120" />
            <Image v-for="(url, index) in snapshot.sliderPicUrls ?? []" :key="index" :src="url" :width="100" />
          </div>
        </template>
        <template v-else-if="activeTab === 'sku'">
          <p class="mb-3 text-sm text-gray-500">以下价格单位为元；审核快照不含实时库存，审核操作不会覆盖库存。</p>
          <SkuList :key="selectedRecord.id" :is-detail="true" :hide-dynamic-stock="true" :prop-form-data="snapshotForSku" :property-list="propertyList" />
        </template>
        <Descriptions v-else-if="activeTab === 'delivery'" :column="2" bordered>
          <Descriptions.Item label="配送方式">{{ snapshot.deliveryTypes?.map((type) => type === 1 ? '快递物流' : type === 2 ? '到店自提' : `类型 ${type}`).join('、') || '未配置' }}</Descriptions.Item>
          <Descriptions.Item label="运费模板编号">{{ snapshot.deliveryTemplateId ?? '未配置' }}</Descriptions.Item>
        </Descriptions>
        <div v-else-if="activeTab === 'description'" class="review-description" v-dompurify-html="snapshot.description || '<p>暂无图文描述</p>'"></div>
        <Descriptions v-else :column="2" bordered>
          <Descriptions.Item label="排序">{{ snapshot.sort ?? '—' }}</Descriptions.Item>
          <Descriptions.Item label="赠送积分">{{ snapshot.giveIntegral ?? '—' }}</Descriptions.Item>
          <Descriptions.Item label="虚拟销量">{{ snapshot.virtualSalesCount ?? '—' }}</Descriptions.Item>
          <Descriptions.Item label="分销配置">{{ snapshot.subCommissionType ? '单独设置' : '默认设置' }}</Descriptions.Item>
        </Descriptions>
      </Card>
      <Card v-else-if="!loading" class="mb-3"><Empty description="暂无有效的提交快照，不能进行审核。历史商品内容可从商品详情只读查看。" /></Card>

      <Card v-if="snapshot" class="mb-3" :title="previousRecord ? `与前次提交 V${previousRecord.contentVersion} 的差异` : '首次记录的经营内容（无前次提交）'">
        <Table :data-source="changes" row-key="key" size="small" :pagination="false" :scroll="{ x: 700 }"
          :columns="[{ title: '字段', dataIndex: 'label', width: 150 }, { title: '前次提交', dataIndex: 'before' }, { title: '当前查看快照', dataIndex: 'after' }]">
          <template #bodyCell="{ column, text }"><pre v-if="column.dataIndex !== 'label'" class="snapshot-value">{{ text }}</pre><template v-else>{{ text }}</template></template>
          <template #emptyText>经营内容与前次提交一致</template>
        </Table>
      </Card>

      <Card class="mb-3" title="审核记录（不可变历史）">
        <Table :data-source="history" row-key="id" size="small" :pagination="{ pageSize: 8 }" :scroll="{ x: 900 }"
          :columns="[{ title: '时间', key: 'time', width: 170 }, { title: '动作', key: 'action', width: 120 }, { title: '版本', dataIndex: 'contentVersion', width: 80 }, { title: '身份 / 操作人', key: 'actor', width: 150 }, { title: '意见', dataIndex: 'remark' }, { title: '快照', key: 'view', width: 100 }]">
          <template #bodyCell="{ column, record, text }">
            <template v-if="column.key === 'time'">{{ dateText(record.createTime) }}</template>
            <template v-else-if="column.key === 'action'">{{ reviewActionText(record.action) }}</template>
            <template v-else-if="column.key === 'actor'">{{ record.actorType === 1 ? '会员' : record.actorType === 2 ? '后台账号' : `类型 ${record.actorType}` }} / {{ record.actorId ?? '—' }}</template>
            <Button v-else-if="column.key === 'view'" type="link" :disabled="busy" @click="selectedRecordId = record.id">查看快照</Button>
            <template v-else>{{ text }}</template>
          </template>
        </Table>
      </Card>

      <Card v-access:code="['product:spu:review']" title="本次审核意见">
        <Alert v-if="isOwnSubmission" class="mb-3" type="warning" message="这是当前后台账号提交的商品，不能自行审核。请由其他获独立审核权限的人员处理。" />
        <Alert v-if="selectedRecord?.id !== submitted?.id" class="mb-3" type="warning" message="当前查看的是历史快照；请返回本次提交后再审核。">
          <template #action><Button v-if="submitted" size="small" :disabled="busy" @click="selectedRecordId = submitted.id">查看本次提交</Button></template>
        </Alert>
        <Input.TextArea v-model:value="reason" :disabled="busy" :maxlength="1000" :rows="4" show-count placeholder="驳回必填：请明确需修改的内容。通过意见选填。" />
        <div class="mt-5 flex gap-3">
          <Button type="primary" :disabled="!canReview" :loading="busy" @click="decide(true)">审核通过（仍下架）</Button>
          <Button danger :disabled="!canReview" :loading="busy" @click="decide(false)">驳回并反馈原因</Button>
        </div>
        <p v-if="!canReview" class="mt-3 text-sm text-gray-500">仅加载成功、当前待审版本且有独立审核权限时可以操作。失败后请先刷新核对；填写的意见不会自动清空。</p>
      </Card>
    </Spin>
  </Page>
</template>

<style scoped>
.snapshot-value { margin: 0; max-height: 220px; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; font-size: 12px; }
.review-description { overflow-wrap: anywhere; }
.review-description :deep(img) { max-width: 100%; height: auto; }
</style>
