<script lang="ts" setup>
import type { FormType } from '../data';

import type { VxeTableInstance } from '#/adapter/vxe-table';
import type { WmsItemSkuApi } from '#/api/wms/md/item/sku';
import type { WmsReceiptOrderApi } from '#/api/wms/order/receipt';
import type { WmsReceiptOrderDetailApi } from '#/api/wms/order/receipt/detail';

import { computed, nextTick, ref } from 'vue';

import { confirm, useVbenModal } from '@vben/common-ui';
import { OrderStatusEnum, OrderUpdateStatusList } from '@vben/constants';
import { isEqual } from '@vben/utils';

import { Alert, Input, InputNumber, message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { TableAction, VxeColumn, VxeTable } from '#/adapter/vxe-table';
import {
  cancelReceiptOrder,
  submitReceiptApproval,
  createReceiptOrder,
  getReceiptOrder,
  getReceiptOrderDetailListByOrderId,
  updateReceiptOrder,
} from '#/api/wms/order/receipt';
import { $t } from '#/locales';
import { ImageUpload } from '#/components/upload';
import { WmsItemSkuSelect } from '#/views/wms/md/item/sku/components';
import {
  dividePrice,
  multiplyPrice,
  PRICE_PRECISION,
  QUANTITY_PRECISION,
} from '#/views/wms/utils/format';
import { generateOrderNo } from '#/views/wms/utils/order';

import { getDetailFooter, useFormSchema } from '../data';

interface DetailRow extends WmsReceiptOrderDetailApi.ReceiptOrderDetail {
  seq: number;
}

defineOptions({ name: 'WmsReceiptOrderForm' });

const emit = defineEmits<{
  success: [];
}>();

const formData = ref<WmsReceiptOrderApi.ReceiptOrder>({});
const formType = ref<FormType>('create');
const originalSubmitData = ref<WmsReceiptOrderApi.ReceiptOrder>();
const details = ref<DetailRow[]>([]);
const detailTableRef = ref<VxeTableInstance>();
const skuSelectRef = ref<InstanceType<typeof WmsItemSkuSelect>>();
let detailSeq = 0; // 明细行可能还没有后端 id，使用本地序号作为 VXE 行操作的稳定标识

const getTitle = computed(() => {
  return formType.value === 'update'
    ? $t('ui.actionTitle.edit', ['入库单'])
    : $t('ui.actionTitle.create', ['入库单']);
});

const sourceLocked = computed(() => details.value.some((row) => !!row.sourceSnapshot));
const isPrepareOrder = computed(() => {
  return (
    !formData.value?.id ||
    (formData.value.status !== undefined &&
      OrderUpdateStatusList.includes(formData.value.status) &&
      ![1, 2].includes(formData.value.approvalStatus ?? -1))
  );
});
const isSavedPrepareOrder = computed(() => {
  return (
    !!formData.value?.id &&
    formData.value.status !== undefined &&
    OrderUpdateStatusList.includes(formData.value.status)
  );
});

function acceptanceError(row: DetailRow) {
  const {
    actualQuantity: actual,
    qualifiedQuantity: qualified,
    unqualifiedQuantity: unqualified,
  } = row;
  if (actual == null || qualified == null || unqualified == null)
    return '请填写实收、合格及不合格数量';
  if (
    ![actual, qualified, unqualified].every(Number.isFinite) ||
    actual < 0 ||
    qualified < 0 ||
    unqualified < 0 ||
    actual > (row.quantity ?? 0) ||
    Math.abs(actual - qualified - unqualified) > 0.000001
  )
    return '数量须满足：申请 ≥ 实收 = 合格 + 不合格，且均不小于零';
  if (row.qualityRequiredSnapshot !== false && !row.qualityImages?.length)
    return '需上传本次验收的质检图片（未确定免检的明细也需上传）';
  return '';
}
const qualityWarning = computed(() => {
  const index = details.value.findIndex((row) => acceptanceError(row));
  return index < 0 ? '' : `第 ${index + 1} 行：${acceptanceError(details.value[index]!)}`;
});
const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    labelWidth: 100,
  },
  layout: 'horizontal',
  schema: useFormSchema(),
  showDefaultActions: false,
  wrapperClass: 'grid-cols-1 md:grid-cols-2 xl:grid-cols-3',
});

/** 标准化明细行，补齐本地序号和金额 */
function normalizeDetail(detail: WmsReceiptOrderDetailApi.ReceiptOrderDetail): DetailRow {
  detailSeq += 1;
  return {
    ...detail,
    seq: detailSeq,
    qualityImages: detail.qualityImages ?? [],
    totalPrice: detail.sourceSnapshot
      ? detail.totalPrice
      : detail.totalPrice ?? multiplyPrice(detail.quantity, detail.price),
  };
}

/** 根据商品 SKU 构建新的入库明细 */
function buildDetail(sku: WmsItemSkuApi.ItemSku): DetailRow {
  return normalizeDetail({
    id: undefined,
    itemCode: sku.itemCode,
    itemId: sku.itemId,
    itemName: sku.itemName,
    price: undefined,
    quantity: undefined,
    skuCode: sku.code,
    skuId: sku.id,
    skuName: sku.name,
    totalPrice: undefined,
    unit: sku.unit,
  });
}

/** 设置入库明细 */
function setDetails(list?: WmsReceiptOrderDetailApi.ReceiptOrderDetail[]) {
  detailSeq = 0;
  details.value = (list || []).map((detail) => normalizeDetail(detail));
  void refreshDetailFooter();
}

/** 刷新明细合计行 */
async function refreshDetailFooter() {
  await nextTick();
  await detailTableRef.value?.updateFooter();
}

/** 获取已选择的 SKU 编号，避免重复选择 */
function getSelectedSkuIds() {
  return details.value.map((detail) => detail.skuId).filter((id): id is number => !!id);
}

/** 添加商品明细 */
async function handleAddDetail() {
  const values = (await formApi.getValues()) as WmsReceiptOrderApi.ReceiptOrder;
  if (!values.warehouseId) {
    message.warning('请先选择仓库');
    return;
  }
  skuSelectRef.value?.open(getSelectedSkuIds());
}

/** 选择商品 SKU */
function handleSelectSku(skus: WmsItemSkuApi.ItemSku[]) {
  if (skus.length === 0) {
    return;
  }
  const selectedSkuIds = new Set(getSelectedSkuIds());
  let changed = false;
  for (const sku of skus) {
    if (!sku.id || selectedSkuIds.has(sku.id)) {
      continue;
    }
    details.value.push(buildDetail(sku));
    selectedSkuIds.add(sku.id);
    changed = true;
  }
  if (changed) {
    void refreshDetailFooter();
  }
}

/** 删除商品明细 */
function handleDeleteDetail(row: DetailRow) {
  const index = details.value.findIndex((detail) => detail.seq === row.seq);
  if (index !== -1) {
    details.value.splice(index, 1);
    void refreshDetailFooter();
  }
}

/** 明细数量变化 */
function handleDetailQuantityChange(detail: DetailRow) {
  if (detail.price !== undefined && detail.price !== null) {
    detail.totalPrice = multiplyPrice(detail.quantity, detail.price);
    void refreshDetailFooter();
    return;
  }
  detail.price = dividePrice(detail.totalPrice, detail.quantity);
  void refreshDetailFooter();
}

/** 明细单价变化 */
function handleDetailPriceChange(detail: DetailRow) {
  detail.totalPrice = multiplyPrice(detail.quantity, detail.price);
  void refreshDetailFooter();
}

/** 明细金额变化 */
function handleDetailTotalPriceChange(detail: DetailRow) {
  detail.price = dividePrice(detail.totalPrice, detail.quantity);
  void refreshDetailFooter();
}

/** 校验商品明细 */
function validateDetails(required = false) {
  if (details.value.length === 0) {
    if (required) {
      message.error('至少包含一条入库明细');
      return false;
    }
    return true;
  }
  for (let index = 0; index < details.value.length; index += 1) {
    const detail = details.value[index]!;
    if (!detail.skuId) {
      message.error(`第 ${index + 1} 行明细请选择商品规格`);
      return false;
    }
    if (!detail.quantity || detail.quantity <= 0) {
      message.error(`第 ${index + 1} 行明细入库数量必须大于 0`);
      return false;
    }
  }
  return true;
}

/** 构建提交用的明细数据 */
function buildSubmitDetails() {
  return details.value.map((row) => {
    const { seq: _seq, ...detail } = row;
    return detail;
  });
}

/** 构建提交用的单据数据 */
async function buildSubmitData(): Promise<WmsReceiptOrderApi.ReceiptOrder> {
  const values = (await formApi.getValues()) as WmsReceiptOrderApi.ReceiptOrder;
  const {
    details: _details,
    totalPrice: _totalPrice,
    totalQuantity: _totalQuantity,
    ...order
  } = formData.value;
  return {
    ...order,
    ...(sourceLocked.value ? {} : values),
    details: buildSubmitDetails(),
  };
}

/** 完成入库 */
async function handleFormComplete() {
  const { valid } = await formApi.validate();
  if (!valid || !validateDetails(true) || !formData.value?.id || !isPrepareOrder.value) {
    return;
  }
  if (qualityWarning.value) {
    message.error(qualityWarning.value);
    return;
  }
  await confirm('确认提交 BPM 审批？审批通过后按合格数量自动入库。');
  modalApi.lock();
  try {
    const data = await buildSubmitData();
    if (!isEqual(data, originalSubmitData.value)) {
      await updateReceiptOrder(data);
    }
    await submitReceiptApproval(formData.value.id);
    await modalApi.close();
    emit('success');
    message.success('已提交 BPM 审批');
  } finally {
    modalApi.unlock();
  }
}

/** 作废入库单 */
async function handleFormCancel() {
  if (!formData.value?.id) {
    return;
  }
  await confirm('确认作废该入库单？作废后不可恢复。');
  modalApi.lock();
  try {
    await cancelReceiptOrder(formData.value.id);
    await modalApi.close();
    emit('success');
    message.success('作废成功');
  } finally {
    modalApi.unlock();
  }
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid || !validateDetails(false) || !isPrepareOrder.value) {
      return;
    }
    modalApi.lock();
    // 提交表单
    const data = await buildSubmitData();
    try {
      await (formType.value === 'update' ? updateReceiptOrder(data) : createReceiptOrder(data));
      // 关闭并提示
      await modalApi.close();
      emit('success');
      message.success($t('ui.actionMessage.operationSuccess'));
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      formData.value = {};
      originalSubmitData.value = undefined;
      setDetails([]);
      return;
    }
    const data = modalApi.getData<{ formType: FormType; id?: number }>();
    formType.value = data.formType;
    if (data?.id) {
      modalApi.lock();
      try {
        // 加载数据
        const order = await getReceiptOrder(data.id);
        const orderDetails = order.details || (await getReceiptOrderDetailListByOrderId(data.id));
        formData.value = { ...order, details: orderDetails };
        setDetails(orderDetails);
        // 设置到 values
        await formApi.setValues(formData.value);
        formApi.setState({
          schema: useFormSchema().map((field) => ({
            ...field,
            componentProps: {
              ...field.componentProps,
              disabled: sourceLocked.value || !isPrepareOrder.value,
            },
          })),
        });
        await nextTick();
        originalSubmitData.value = await buildSubmitData();
      } finally {
        modalApi.unlock();
      }
      return;
    }
    // 初始化新增表单
    formData.value = {
      details: [],
      no: generateOrderNo('RK'),
      status: OrderStatusEnum.PREPARE,
    };
    setDetails([]);
    await formApi.setValues(formData.value);
    formApi.setState({
      schema: useFormSchema().map((field) => ({
        ...field,
        componentProps: {
          ...field.componentProps,
          disabled: sourceLocked.value || !isPrepareOrder.value,
        },
      })),
    });
    await nextTick();
    originalSubmitData.value = await buildSubmitData();
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-[96vw] max-w-[1800px]" :show-confirm-button="isPrepareOrder">
    <div class="mx-4">
      <div :class="{ 'pointer-events-none opacity-70': sourceLocked || !isPrepareOrder }">
        <Form />
      </div>
      <div class="mt-4">
        <p v-if="sourceLocked" class="mb-3 text-sm text-gray-500">
          来源单据的商品、申请数量、单价及金额由 ERP/MES 提供，在 WMS 中只读；未提供单价时显示为空。仓库在此填写本单验收数量及质检凭证。
        </p>
        <div class="mb-3 flex items-center justify-between">
          <span class="text-sm font-semibold">入库明细</span>
          <TableAction
            :actions="[
              {
                label: '添加商品',
                disabled: sourceLocked || !isPrepareOrder,
                onClick: handleAddDetail,
                type: 'primary',
              },
            ]"
          />
        </div>
        <VxeTable
          ref="detailTableRef"
          :data="details"
          border
          empty-text="暂无商品明细"
          :footer-method="getDetailFooter"
          :show-overflow="false"
          :scroll-y="{ enabled: false }"
          show-footer
          size="small"
        >
          <VxeColumn title="商品信息" min-width="220">
            <template #default="{ row }">
              <div class="break-words">{{ row.itemName || '-' }}</div>
              <div v-if="row.itemCode" class="break-all text-xs text-gray-500">
                商品编号：{{ row.itemCode }}
              </div>
            </template>
          </VxeColumn>
          <VxeColumn title="规格信息" min-width="220">
            <template #default="{ row }">
              <div class="break-words">{{ row.skuName || '-' }}</div>
              <div v-if="row.skuCode" class="break-all text-xs text-gray-500">
                规格编号：{{ row.skuCode }}
              </div>
            </template>
          </VxeColumn>
          <VxeColumn field="quantity" title="申请数量" width="150">
            <template #default="{ row }">
              <InputNumber
                v-model:value="row.quantity"
                :disabled="sourceLocked || !isPrepareOrder"
                :controls="false"
                :min="0"
                :precision="QUANTITY_PRECISION"
                class="!w-full"
                placeholder="数量"
                @change="handleDetailQuantityChange(row)"
              />
            </template>
          </VxeColumn>
          <VxeColumn title="本单验收" min-width="230">
            <template #default="{ row }">
              <div class="space-y-2 py-2">
                <div>
                  实收
                  <InputNumber
                    v-model:value="row.actualQuantity"
                    :disabled="!isPrepareOrder"
                    :min="0"
                    :max="row.quantity"
                    :precision="QUANTITY_PRECISION"
                  />
                </div>
                <div>
                  合格
                  <InputNumber
                    v-model:value="row.qualifiedQuantity"
                    :disabled="!isPrepareOrder"
                    :min="0"
                    :max="row.actualQuantity"
                    :precision="QUANTITY_PRECISION"
                  />
                </div>
                <div>
                  不合格
                  <InputNumber
                    v-model:value="row.unqualifiedQuantity"
                    :disabled="!isPrepareOrder"
                    :min="0"
                    :max="row.actualQuantity"
                    :precision="QUANTITY_PRECISION"
                  />
                </div>
              </div>
            </template>
          </VxeColumn>
          <VxeColumn title="本单质检凭证" min-width="280">
            <template #default="{ row }">
              <div>{{ row.qualityRequiredSnapshot === false ? '免检' : '需质检图片' }}</div>
              <ImageUpload
                v-model="row.qualityImages"
                :disabled="!isPrepareOrder"
                :max-number="9"
                multiple
              />
              <Input.TextArea
                v-model:value="row.qualityRemark"
                :disabled="!isPrepareOrder"
                placeholder="本次验收说明"
                :maxlength="500"
              />
            </template>
          </VxeColumn>
          <VxeColumn field="price" title="单价(元)" width="150">
            <template #default="{ row }">
              <InputNumber
                v-model:value="row.price"
                :disabled="sourceLocked || !isPrepareOrder"
                :controls="false"
                :min="0"
                :precision="PRICE_PRECISION"
                class="!w-full"
                :placeholder="sourceLocked ? '来源未提供' : '单价'"
                @change="handleDetailPriceChange(row)"
              />
            </template>
          </VxeColumn>
          <VxeColumn field="totalPrice" title="金额(元)" width="150">
            <template #default="{ row }">
              <InputNumber
                v-model:value="row.totalPrice"
                :disabled="sourceLocked || !isPrepareOrder"
                :controls="false"
                :min="0"
                :precision="PRICE_PRECISION"
                class="!w-full"
                :placeholder="sourceLocked ? '来源未提供' : '金额'"
                @change="handleDetailTotalPriceChange(row)"
              />
            </template>
          </VxeColumn>
          <VxeColumn title="操作" align="center" fixed="right" width="90">
            <template #default="{ row }">
              <TableAction
                :actions="[
                  {
                    label: '删除',
                    disabled: sourceLocked || !isPrepareOrder,
                    type: 'link',
                    danger: true,
                    onClick: handleDeleteDetail.bind(null, row),
                  },
                ]"
              />
            </template>
          </VxeColumn>
        </VxeTable>
      </div>
      <Alert
        v-if="qualityWarning"
        class="mt-3"
        type="warning"
        show-icon
        :message="qualityWarning"
      />
    </div>
    <template #prepend-footer>
      <div v-if="isSavedPrepareOrder && isPrepareOrder" class="flex flex-auto items-center gap-2">
        <TableAction
          :actions="[
            {
              label: '提交 BPM 审批',
              type: 'primary',
              auth: ['wms:receipt-order:submit-approval'],
              disabled: !!qualityWarning,
              onClick: handleFormComplete,
            },
            {
              label: '作废',
              type: 'primary',
              danger: true,
              auth: ['wms:receipt-order:cancel'],
              onClick: handleFormCancel,
            },
          ]"
        />
      </div>
    </template>
    <WmsItemSkuSelect ref="skuSelectRef" @change="handleSelectSku" />
  </Modal>
</template>

<style scoped>
:deep(.quality-warning-row) {
  background: #fffbe6;
}
</style>
