<script setup lang="ts">
/**
 * 待确认单据卡片（AI 识别结果的结构化预览）
 * 可选择：单据类型、仓库、供应商/客户；明细行可编辑数量/单价、点选同名多规格候选
 */
import type { WmsAiApi } from '#/api/wms/ai';
import type { WmsMerchantApi } from '#/api/wms/md/merchant';
import type { WmsWarehouseApi } from '#/api/wms/md/warehouse';

import type { AiChatMessage } from '../composables/useConversations';

import { computed, ref } from 'vue';

import {
  Alert,
  Button,
  Divider,
  Input,
  InputNumber,
  message,
  Select,
  Tag,
} from 'ant-design-vue';

import { createOrder as apiCreateOrder } from '#/api/wms/ai';

const props = defineProps<{
  merchants: WmsMerchantApi.Merchant[];
  msg: AiChatMessage;
  sessionId?: string;
  warehouses: WmsWarehouseApi.Warehouse[];
}>();

const emit = defineEmits<{ created: [] }>();

/** 入库/出库单据类型（与后端枚举一致） */
const RECEIPT_TYPE_OPTIONS = [
  { value: 100, label: '生产入库' },
  { value: 101, label: '采购入库' },
  { value: 102, label: '退货入库' },
  { value: 103, label: '归还入库' },
];
const SHIPMENT_TYPE_OPTIONS = [
  { value: 200, label: '退货出库' },
  { value: 201, label: '销售出库' },
  { value: 202, label: '生产出库' },
];

/** 动作 → 中文名（IN 入库 / OUT 出库 / MOVE 移库 / CHECK 盘库） */
const actionText = computed(
  () =>
    ({ CHECK: '盘库', IN: '入库', MOVE: '移库', OUT: '出库' })[
      props.msg.action || ''
    ] || '单据',
);

/** 是否为「可移出/移入」的移库：需要两个仓库 */
const isMove = computed(() => props.msg.action === 'MOVE');

/** 入库/出库才有单据子类型下拉 */
const showTypeSelect = computed(
  () => props.msg.action === 'IN' || props.msg.action === 'OUT',
);

/** 仓库标题：按动作区分语义，避免用户填错方向 */
const warehouseLabel = computed(() =>
  ({ CHECK: '盘库仓库', IN: '目标仓库', OUT: '来源仓库' })[
    props.msg.action || ''
  ] || '仓库',
);

const typeOptions = computed(() =>
  props.msg.action === 'OUT' ? SHIPMENT_TYPE_OPTIONS : RECEIPT_TYPE_OPTIONS,
);

const showMerchant = computed(
  () => props.msg.action === 'IN' || props.msg.action === 'OUT',
);
const merchantLabel = computed(() =>
  props.msg.action === 'IN' ? '供应商' : '客户',
);

const warehouseOptions = computed(() =>
  props.warehouses
    .filter((w) => w.id)
    .map((w) => ({ label: w.name || `仓库${w.id}`, value: w.id! })),
);

const merchantOptions = computed(() =>
  props.merchants
    .filter((m) => m.id)
    .map((m) => ({ label: m.name || `企业${m.id}`, value: m.id! })),
);

/** 候选下拉选项 */
function optionsOf(it: WmsAiApi.ItemVO) {
  return (it.candidates || [])
    .filter((c) => c.itemId || c.skuId)
    .map((c) => ({
      value: (c.skuId ?? c.itemId)!,
      label: [c.name, c.spec, c.unit ? `单位 ${c.unit}` : '']
        .filter(Boolean)
        .join(' · '),
    }));
}

/** 选中候选，回填主数据编号 */
function pickCandidate(it: WmsAiApi.ItemVO, val: number) {
  const hit = (it.candidates || []).find((c) => (c.skuId ?? c.itemId) === val);
  if (!hit) return;
  it.skuId = hit.skuId;
  it.itemId = hit.itemId;
  it.resolved = true;
  if (!it.spec && hit.spec) it.spec = hit.spec;
  if (!it.unit && hit.unit) it.unit = hit.unit;
  if ((!it.price || it.price <= 0) && hit.price && hit.price > 0) {
    it.price = hit.price;
  }
}

function rowValue(it: WmsAiApi.ItemVO) {
  return it.skuId ?? it.itemId;
}

function amountOf(it: WmsAiApi.ItemVO) {
  return (it.quantity || 0) * (it.price || 0);
}

const totalAmount = computed(() =>
  (props.msg.items || []).reduce((sum, it) => sum + amountOf(it), 0),
);

/** 还缺什么才能建单 */
const missingList = computed(() => {
  const list: string[] = [];
  if (!props.msg.warehouseId) {
    list.push(warehouseLabel.value);
  }
  // 移库必须同时指定移出仓与移入仓，且不能相同
  if (isMove.value) {
    if (!props.msg.targetWarehouseId) {
      list.push('移入仓库');
    } else if (props.msg.targetWarehouseId === props.msg.warehouseId) {
      list.push('移入仓库不能与移出仓库相同');
    }
  }
  for (const it of props.msg.items || []) {
    if (!it.resolved && (it.candidates?.length || 0) > 0) {
      list.push(`「${it.name}」待选规格`);
    } else if (!it.quantity || it.quantity <= 0) {
      list.push(`「${it.name}」数量无效`);
    }
  }
  return list;
});

const canSubmit = computed(
  () => (props.msg.items?.length ?? 0) > 0 && missingList.value.length === 0,
);

const creating = ref(false);

async function handleConfirm() {
  if (creating.value) return;
  creating.value = true;
  const hide = message.loading({ content: '正在生成单据...', duration: 0 });
  try {
    const resp = await apiCreateOrder({
      action: props.msg.action as string,
      items: (props.msg.items || []).map((it) => ({
        name: it.name,
        spec: it.spec,
        unit: it.unit,
        quantity: it.quantity,
        price: it.price,
        weight: it.weight,
        resolved: it.resolved,
        itemId: it.itemId,
        skuId: it.skuId,
      })),
      merchantId: props.msg.merchantId,
      remark: props.msg.remark || undefined,
      sessionId: props.sessionId,
      type: props.msg.type,
      warehouseId: props.msg.warehouseId,
      // 移库专用：移入仓库（其余动作后端忽略）
      targetWarehouseId: isMove.value ? props.msg.targetWarehouseId : undefined,
    });
    props.msg.orderNo = resp.orderNo;
    props.msg.orderId = resp.orderId;
    props.msg.buildable = false;
    message.success(`已创建单据 ${resp.orderNo || ''}`);
    emit('created');
  } finally {
    hide();
    creating.value = false;
  }
}
</script>

<template>
  <div class="mt-2 rounded-lg border border-blue-200 bg-blue-50/50 p-3">
    <!-- 标题行：动作 + 单据类型 -->
    <div class="mb-3 flex flex-wrap items-center gap-2">
      <Tag color="blue" class="!mb-0">待确认{{ actionText }}单</Tag>
      <Select
        v-if="showTypeSelect"
        v-model:value="msg.type"
        :options="typeOptions"
        placeholder="单据类型"
        size="small"
        style="width: 140px"
      />
      <span class="text-xs text-gray-400">
        共 {{ msg.items?.length || 0 }} 行明细
      </span>
    </div>

    <!-- 仓库：移库需要「移出仓 + 移入仓」两个；其余动作一个 -->
    <div class="mb-2 flex items-center gap-2">
      <span class="w-20 flex-none text-xs text-gray-500">
        {{ isMove ? '移出仓库' : warehouseLabel }}
      </span>
      <Select
        v-model:value="msg.warehouseId"
        :options="warehouseOptions"
        :placeholder="msg.warehouse ? `识别到：${msg.warehouse}，请确认` : '请选择仓库'"
        show-search
        option-filter-prop="label"
        size="small"
        style="flex: 1"
      />
    </div>
    <div v-if="isMove" class="mb-2 flex items-center gap-2">
      <span class="w-20 flex-none text-xs text-gray-500">移入仓库</span>
      <Select
        v-model:value="msg.targetWarehouseId"
        :options="warehouseOptions"
        :placeholder="
          msg.targetWarehouse
            ? `识别到：${msg.targetWarehouse}，请确认`
            : '请选择移入仓库'
        "
        show-search
        option-filter-prop="label"
        size="small"
        style="flex: 1"
      />
    </div>

    <!-- 供应商/客户 -->
    <div v-if="showMerchant" class="mb-2 flex items-center gap-2">
      <span class="w-20 flex-none text-xs text-gray-500">
        {{ merchantLabel }}
      </span>
      <Select
        v-model:value="msg.merchantId"
        :options="merchantOptions"
        :placeholder="
          msg.merchant ? `识别到：${msg.merchant}，可修改` : '选择或不填（可选）'
        "
        allow-clear
        show-search
        option-filter-prop="label"
        size="small"
        style="flex: 1"
      />
    </div>

    <Divider class="!my-2">
      <span class="text-xs text-gray-400">
        商品明细（{{ msg.items?.length || 0 }} 行）
      </span>
    </Divider>

    <!-- 明细行 -->
    <div class="max-h-72 space-y-2 overflow-y-auto pr-1">
      <div
        v-for="(it, idx) in msg.items"
        :key="idx"
        class="rounded-md border border-gray-100 bg-white p-2"
      >
        <div class="flex items-center justify-between gap-2">
          <div class="min-w-0 flex-1">
            <div class="flex items-center gap-1">
              <span class="truncate text-sm font-medium text-gray-700">
                {{ it.name }}
              </span>
              <span v-if="it.spec" class="text-xs text-gray-400">
                {{ it.spec }}
              </span>
              <Tag v-if="!it.resolved && it.candidates?.length" color="orange" class="!mb-0 !text-[10px]">
                待选规格
              </Tag>
            </div>
            <div v-if="it.candidates?.length" class="mt-1">
              <Select
                :value="rowValue(it)"
                :options="optionsOf(it)"
                placeholder="点选正确规格"
                size="small"
                style="width: 100%"
                @change="
                  (val: any) => {
                    pickCandidate(it, val);
                  }
                "
              />
            </div>
          </div>
          <div class="flex flex-none items-center gap-1">
            <InputNumber
              :value="it.quantity"
              :min="0.001"
              :precision="3"
              placeholder="数量"
              size="small"
              style="width: 90px"
              @change="
                (val: any) => {
                  it.quantity = val && val > 0 ? Number(val) : undefined;
                }
              "
            />
            <InputNumber
              :value="it.price"
              :min="0"
              :precision="2"
              placeholder="单价"
              size="small"
              style="width: 90px"
              @change="
                (val: any) => {
                  it.price = val && val > 0 ? Number(val) : undefined;
                }
              "
            />
            <span class="w-20 flex-none text-right text-xs text-gray-400">
              ¥{{ amountOf(it).toFixed(2) }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 合计 -->
    <div class="mt-2 text-right text-xs text-gray-500">
      金额合计：
      <span class="text-sm font-semibold text-gray-700">
        ¥{{ totalAmount.toFixed(2) }}
      </span>
    </div>

    <!-- 缺项提示 -->
    <Alert
      v-if="missingList.length"
      class="mt-2"
      :message="`还差 ${missingList.length} 项：` + missingList.join('；')"
      type="warning"
      show-icon
    />

    <!-- 备注 + 确认 -->
    <div class="mt-2 flex items-center gap-2">
      <Input
        v-model:value="msg.remark"
        placeholder="备注（可空）"
        size="small"
        style="flex: 1"
      />
      <Button
        type="primary"
        size="small"
        :disabled="!canSubmit"
        :loading="creating"
        @click="handleConfirm"
      >
        确认创建单据
      </Button>
    </div>
    <div v-if="!canSubmit" class="mt-1 text-right text-xs text-gray-400">
      补全上方缺项后即可创建
    </div>
  </div>
</template>
