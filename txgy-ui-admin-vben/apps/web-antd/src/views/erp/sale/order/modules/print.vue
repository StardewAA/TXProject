<script lang="ts" setup>
import type { ErpSaleOrderApi } from '#/api/erp/sale/order';

import { computed, nextTick, ref } from 'vue';

import { DICT_TYPE } from '@vben/constants';
import { getDictLabel } from '@vben/hooks';
import {
  erpNumberFormatter,
  erpPriceInputFormatter,
  formatDate,
  formatDateTime,
} from '@vben/utils';

import { message } from 'ant-design-vue';

import {
  getSaleOrder,
  getSaleOrderItemListByOrderId,
} from '#/api/erp/sale/order';

interface PrintRow extends ErpSaleOrderApi.SaleOrderItem {
  totalProductPrice: number;
  totalTaxPrice: number;
  totalPrice: number;
}

interface PrintSaleOrder extends ErpSaleOrderApi.SaleOrder {
  customerName?: string;
  creatorName?: string;
}

defineOptions({ name: 'ErpSaleOrderPrint' });

const printData = ref<Partial<PrintSaleOrder>>({});
const printing = ref(false);

function toAmount(value?: null | number) {
  return Number(value ?? 0);
}

function formatAmount(value?: null | number) {
  return erpPriceInputFormatter(toAmount(value)) || '0.00';
}

function formatQuantity(value?: null | number) {
  return value === undefined || value === null
    ? '-'
    : erpNumberFormatter(value, 2);
}

const printRows = computed<PrintRow[]>(() =>
  (printData.value.items || []).map((item) => {
    const totalProductPrice = toAmount(
      item.totalProductPrice ?? item.totalPrice ??
        toAmount(item.productPrice) * toAmount(item.count),
    );
    const totalTaxPrice = toAmount(item.taxPrice);
    return {
      ...item,
      totalProductPrice,
      totalTaxPrice,
      totalPrice: totalProductPrice + totalTaxPrice,
    };
  }),
);

const summary = computed(() => {
  const result = { count: 0, productPrice: 0, taxPrice: 0, totalPrice: 0 };
  for (const row of printRows.value) {
    result.count += toAmount(row.count);
    result.productPrice += row.totalProductPrice;
    result.taxPrice += row.totalTaxPrice;
    result.totalPrice += row.totalPrice;
  }
  return result;
});

function getStatusLabel(value?: number) {
  return value === undefined || value === null
    ? '-'
    : getDictLabel(DICT_TYPE.ERP_AUDIT_STATUS, value) || '-';
}

async function waitForPrintReady() {
  await nextTick();
  await nextTick();
  await new Promise<void>((resolve) => {
    requestAnimationFrame(() => {
      requestAnimationFrame(() => resolve());
    });
  });
}

function removePrintMode() {
  document.body.classList.remove('erp-sale-order-printing');
}

/** 加载订单详情后调用浏览器打印，可在打印窗口选择另存为 PDF */
async function print(row: ErpSaleOrderApi.SaleOrder) {
  if (printing.value || !row.id) {
    return;
  }
  printing.value = true;
  try {
    printData.value = {};
    await nextTick();
    const order = await getSaleOrder(row.id);
    const items =
      order.items || (await getSaleOrderItemListByOrderId(row.id));
    printData.value = {
      ...row,
      ...order,
      customerName: order.customerName || row.customerName,
      creatorName: order.creatorName || row.creatorName,
      items,
    };
    await waitForPrintReady();
    document.body.classList.add('erp-sale-order-printing');
    window.addEventListener('afterprint', removePrintMode, { once: true });
    window.print();
  } catch {
    removePrintMode();
    message.error('销售订单数据加载失败，无法导出 PDF');
  } finally {
    printing.value = false;
  }
}

defineExpose({ print });
</script>

<template>
  <Teleport to="body">
    <div
      id="erpSaleOrderPrint"
      class="erp-sale-order-print pointer-events-none fixed left-0 top-0 z-[-1] w-full bg-white px-2 text-[#303133] opacity-0"
    >
      <header class="mb-5 border-b-2 border-solid border-[#1f2937] pb-3">
        <div class="flex items-end justify-between gap-4">
          <div>
            <div class="text-xs tracking-[0.2em] text-[#6b7280]">天信管业</div>
            <h1 class="m-0 mt-1 text-center text-[24px] font-bold tracking-[0.12em]">
              销售订单
            </h1>
          </div>
          <div class="text-right text-xs leading-6 text-[#4b5563]">
            <div>单据编号：{{ printData.no || '-' }}</div>
            <div>打印时间：{{ formatDateTime(new Date()) || '-' }}</div>
          </div>
        </div>
      </header>

      <section class="mb-4 grid grid-cols-3 gap-x-8 gap-y-2 text-[13px] leading-6">
        <div><span class="label">客户：</span>{{ printData.customerName || '-' }}</div>
        <div><span class="label">订单日期：</span>{{ formatDate(printData.orderTime, 'YYYY-MM-DD') || '-' }}</div>
        <div><span class="label">订单状态：</span>{{ getStatusLabel(printData.status) }}</div>
        <div><span class="label">收款账户编号：</span>{{ printData.accountId || '-' }}</div>
        <div><span class="label">合计数量：</span>{{ formatQuantity(printData.totalCount) }}</div>
        <div><span class="label">创建时间：</span>{{ formatDateTime(printData.createTime) || '-' }}</div>
      </section>

      <table class="w-full border-collapse text-[11px] leading-[1.45]">
        <thead>
          <tr>
            <th class="w-[5%] border border-solid border-[#9ca3af] bg-[#f3f4f6] p-2 text-center font-bold">序号</th>
            <th class="w-[29%] border border-solid border-[#9ca3af] bg-[#f3f4f6] p-2 text-left font-bold">商品名称 / 条码</th>
            <th class="w-[7%] border border-solid border-[#9ca3af] bg-[#f3f4f6] p-2 text-center font-bold">单位</th>
            <th class="w-[8%] border border-solid border-[#9ca3af] bg-[#f3f4f6] p-2 text-right font-bold">数量</th>
            <th class="w-[12%] border border-solid border-[#9ca3af] bg-[#f3f4f6] p-2 text-right font-bold">单价(元)</th>
            <th class="w-[12%] border border-solid border-[#9ca3af] bg-[#f3f4f6] p-2 text-right font-bold">金额(元)</th>
            <th class="w-[8%] border border-solid border-[#9ca3af] bg-[#f3f4f6] p-2 text-right font-bold">税率</th>
            <th class="w-[9%] border border-solid border-[#9ca3af] bg-[#f3f4f6] p-2 text-right font-bold">税额(元)</th>
            <th class="w-[10%] border border-solid border-[#9ca3af] bg-[#f3f4f6] p-2 text-right font-bold">含税金额(元)</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(item, index) in printRows" :key="item.id || index">
            <td class="border border-solid border-[#d1d5db] p-2 text-center">{{ index + 1 }}</td>
            <td class="border border-solid border-[#d1d5db] p-2">
              <div class="font-medium">{{ item.productName || '-' }}</div>
              <div v-if="item.productBarCode" class="text-[10px] text-[#6b7280]">条码：{{ item.productBarCode }}</div>
              <div v-if="item.remark" class="text-[10px] text-[#6b7280]">备注：{{ item.remark }}</div>
            </td>
            <td class="border border-solid border-[#d1d5db] p-2 text-center">{{ item.productUnitName || '-' }}</td>
            <td class="border border-solid border-[#d1d5db] p-2 text-right">{{ formatQuantity(item.count) }}</td>
            <td class="border border-solid border-[#d1d5db] p-2 text-right">{{ formatAmount(item.productPrice) }}</td>
            <td class="border border-solid border-[#d1d5db] p-2 text-right">{{ formatAmount(item.totalProductPrice) }}</td>
            <td class="border border-solid border-[#d1d5db] p-2 text-right">{{ item.taxPercent ?? 0 }}%</td>
            <td class="border border-solid border-[#d1d5db] p-2 text-right">{{ formatAmount(item.totalTaxPrice) }}</td>
            <td class="border border-solid border-[#d1d5db] p-2 text-right">{{ formatAmount(item.totalPrice) }}</td>
          </tr>
          <tr v-if="printRows.length > 0">
            <td colspan="3" class="border border-solid border-[#9ca3af] bg-[#f3f4f6] p-2 text-right font-bold">合计</td>
            <td class="border border-solid border-[#9ca3af] bg-[#f3f4f6] p-2 text-right font-bold">{{ formatQuantity(summary.count) }}</td>
            <td class="border border-solid border-[#9ca3af] bg-[#f3f4f6] p-2"></td>
            <td class="border border-solid border-[#9ca3af] bg-[#f3f4f6] p-2 text-right font-bold">{{ formatAmount(summary.productPrice) }}</td>
            <td class="border border-solid border-[#9ca3af] bg-[#f3f4f6] p-2"></td>
            <td class="border border-solid border-[#9ca3af] bg-[#f3f4f6] p-2 text-right font-bold">{{ formatAmount(summary.taxPrice) }}</td>
            <td class="border border-solid border-[#9ca3af] bg-[#f3f4f6] p-2 text-right font-bold">{{ formatAmount(summary.totalPrice) }}</td>
          </tr>
          <tr v-if="printRows.length === 0">
            <td colspan="9" class="border border-solid border-[#d1d5db] p-4 text-center">暂无商品明细</td>
          </tr>
        </tbody>
      </table>

      <section class="mt-4 grid grid-cols-2 gap-6 border-t border-solid border-[#9ca3af] pt-3 text-[13px] leading-7">
        <div>
          <div>备注：{{ printData.remark || '-' }}</div>
          <div>制单人：{{ printData.creatorName || '-' }}</div>
        </div>
        <div class="text-right">
          <div>产品金额：{{ formatAmount(printData.totalProductPrice ?? summary.productPrice) }} 元</div>
          <div>税额：{{ formatAmount(summary.taxPrice) }} 元</div>
          <div v-if="printData.discountPrice">优惠金额：{{ formatAmount(printData.discountPrice) }} 元</div>
          <div v-if="printData.depositPrice">订金：{{ formatAmount(printData.depositPrice) }} 元</div>
          <div class="mt-1 text-base font-bold">应收合计：{{ formatAmount(printData.totalPrice ?? summary.totalPrice) }} 元</div>
        </div>
      </section>

      <footer class="mt-8 grid grid-cols-3 gap-8 border-t border-solid border-[#d1d5db] pt-3 text-[12px] text-[#4b5563]">
        <div>客户确认：________________</div>
        <div>销售确认：________________</div>
        <div>日期：________________</div>
      </footer>
    </div>
  </Teleport>
</template>

<style scoped>
.label {
  color: #6b7280;
}

@page {
  size: A4 portrait;
  margin: 10mm 12mm;
}

@media print {
  :global(body.erp-sale-order-printing) {
    padding: 0 !important;
    margin: 0 !important;
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }

  :global(body.erp-sale-order-printing *) {
    visibility: hidden !important;
  }

  :global(body.erp-sale-order-printing .erp-sale-order-print),
  :global(body.erp-sale-order-printing .erp-sale-order-print *) {
    visibility: visible !important;
  }

  :global(body.erp-sale-order-printing .erp-sale-order-print) {
    position: absolute;
    top: 0;
    left: 0;
    z-index: auto;
    box-sizing: border-box;
    width: 100%;
    padding: 0 !important;
    margin: 0 !important;
    pointer-events: auto;
    opacity: 1;
  }

  thead {
    display: table-header-group;
  }

  tr,
  section,
  footer {
    break-inside: avoid;
    page-break-inside: avoid;
  }
}
</style>
