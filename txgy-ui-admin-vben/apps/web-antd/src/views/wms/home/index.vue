<script lang="ts" setup>
import type { WmsAiApi } from '#/api/wms/ai';
import type { WmsMerchantApi } from '#/api/wms/md/merchant';
import type { WmsWarehouseApi } from '#/api/wms/md/warehouse';

import { computed, nextTick, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';
import { formatDateTime } from '@vben/utils';

import { Button, Spin } from 'ant-design-vue';

import { recognize as apiRecognize, recognizeFile as apiRecognizeFile } from '#/api/wms/ai';
import { getMerchantSimpleList } from '#/api/wms/md/merchant';
import { getWarehouseSimpleList } from '#/api/wms/md/warehouse';

import WmsHomeInventoryCharts from './modules/wms-home-inventory-charts.vue';
import WmsHomeOrderSummaryCards from './modules/wms-home-order-summary-cards.vue';
import WmsHomeOrderTrendChart from './modules/wms-home-order-trend-chart.vue';
import type { AiAttachment, AiChatMessage } from '../ai/composables/useConversations';
import OrderConfirmCard from '../ai/modules/OrderConfirmCard.vue';

defineOptions({ name: 'WmsHome' });

const loading = ref(false);
const statTime = ref(formatDateTime(new Date()));
const orderSummaryCardsRef =
  ref<InstanceType<typeof WmsHomeOrderSummaryCards>>();
const orderTrendChartRef = ref<InstanceType<typeof WmsHomeOrderTrendChart>>();
const inventoryChartsRef = ref<InstanceType<typeof WmsHomeInventoryCharts>>();

async function refresh() {
  loading.value = true;
  try {
    await Promise.all([
      orderSummaryCardsRef.value?.load(),
      orderTrendChartRef.value?.load(),
      inventoryChartsRef.value?.load(),
    ]);
    statTime.value = formatDateTime(new Date());
  } finally {
    loading.value = false;
  }
}

// ============ WMS AI 识别对话 ============
const chatInProgress = ref(false); // 是否已进入对话态（控制图表收起）
const chatLoading = ref(false); // 识别中
const prompt = ref('');
const images = ref<AiAttachment[]>([]);
const docs = ref<AiAttachment[]>([]);
const messages = ref<AiChatMessage[]>([]);
const scrollRef = ref<HTMLElement | null>(null);

/** 仓库/往来企业精简列表（供待确认卡片选择） */
const warehouses = ref<WmsWarehouseApi.Warehouse[]>([]);
const merchants = ref<WmsMerchantApi.Merchant[]>([]);

/** 当前会话 sessionId（保证多轮上下文延续） */
const sessionId = ref('');

function newSid() {
  return `${Date.now()}_${Math.random().toString(36).slice(2, 10)}`;
}

function uid() {
  return `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
}

const showMessageList = computed(() => chatInProgress.value);

function scrollToBottom() {
  requestAnimationFrame(() => {
    const el = scrollRef.value;
    if (el) el.scrollTop = el.scrollHeight;
  });
}

function push(msg: AiChatMessage) {
  messages.value.push(msg);
  void nextTick(scrollToBottom);
}

/** 新建对话：清空消息，回到首页态（图表重新显示） */
function handleNewConversation() {
  messages.value = [];
  prompt.value = '';
  images.value = [];
  docs.value = [];
  sessionId.value = newSid();
  chatInProgress.value = false;
  refresh();
}

/** 点击输入框：进入对话态，收起图表 */
function handlePromptFocus() {
  if (!chatInProgress.value) {
    chatInProgress.value = true;
    void nextTick(scrollToBottom);
  }
}

// ============ 附件 ============
function pickFile(accept: string, target: typeof images) {
  const el = document.createElement('input');
  el.type = 'file';
  el.multiple = true;
  el.accept = accept;
  el.addEventListener('change', () => {
    for (const f of Array.from(el.files || [])) {
      const fr = new FileReader();
      fr.addEventListener('load', () => {
        target.value.push({
          name: f.name,
          size: f.size,
          url: fr.result as string,
        });
      });
      fr.readAsDataURL(f);
    }
  });
  el.click();
}

function pickImage() {
  pickFile('image/*', images);
}

function pickDoc() {
  pickFile('.xlsx,.xls,.csv,.txt,.pdf', docs);
}

function humanSize(n?: number) {
  if (!n) return '';
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / 1024 / 1024).toFixed(1)} MB`;
}

// ============ 发送 ============
async function handleSend() {
  const text = prompt.value.trim();
  if (chatLoading.value) return;
  if (!text && images.value.length === 0 && docs.value.length === 0) {
    return;
  }

  const attachedImages = [...images.value];
  const attachedDocs = [...docs.value];

  push({
    id: uid(),
    role: 'user',
    text,
    images: attachedImages,
    docs: attachedDocs,
  });

  prompt.value = '';
  images.value = [];
  docs.value = [];
  chatInProgress.value = true;
  chatLoading.value = true;

  try {
    const payload: WmsAiApi.RecognizeReqVO = {
      images: attachedImages.map((i) => i.url),
      sessionId: sessionId.value,
      text,
    };

    let resp: WmsAiApi.RecognizeRespVO;
    if (attachedDocs.length > 0) {
      payload.files = attachedDocs.map((d) => ({ name: d.name, url: d.url }));
      resp = await apiRecognizeFile(payload);
    } else {
      resp = await apiRecognize(payload);
    }

    push({
      action: resp.action,
      buildable: resp.buildable,
      id: uid(),
      items: resp.items,
      merchant: resp.merchant,
      merchantId: resp.merchantId,
      role: 'ai',
      text: [resp.reply, ...(resp.tips || [])].filter(Boolean).join('\n'),
      type: resp.type,
      warehouse: resp.warehouse,
      warehouseId: resp.warehouseId,
    });
  } catch (error: any) {
    push({
      id: uid(),
      role: 'ai',
      text: `识别出错了：${error?.message || '请稍后重试'}`,
    });
  } finally {
    chatLoading.value = false;
    void nextTick(scrollToBottom);
  }
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    void handleSend();
  }
}

// ============ 初始化 ============
onMounted(() => {
  sessionId.value = newSid();
  refresh();
  // 拉取仓库/往来企业精简列表，供待确认单据卡片选择（失败静默，不影响主流程）
  getWarehouseSimpleList()
    .then((data) => {
      warehouses.value = data || [];
    })
    .catch(() => {});
  getMerchantSimpleList()
    .then((data) => {
      merchants.value = data || [];
    })
    .catch(() => {});
});
</script>

<template>
  <Page>
    <div class="flex h-full flex-col gap-2">
      <!-- 顶部：AI 标题 + 新建对话 -->
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-3">
          <IconifyIcon
            icon="lucide:sparkles"
            class="size-6 text-primary"
          />
          <span class="text-xl font-semibold">智能仓储助手</span>
        </div>
        <Button @click="handleNewConversation">
          <IconifyIcon icon="lucide:plus" class="mr-1 size-4" />
          新建对话
        </Button>
      </div>

      <!-- AI 输入框 -->
      <div
        class="rounded-xl border border-border bg-card p-2 shadow-sm"
        :class="{ 'border-primary': chatInProgress }"
      >
        <textarea
          v-model="prompt"
          class="box-border h-20 w-full resize-none overflow-auto rounded-md bg-transparent p-2 focus:outline-none"
          placeholder="用一句话描述出入库，例如：采购入库 岩棉板 20 件 到 金岩原辅材料库（Enter 发送，Shift+Enter 换行）"
          @focus="handlePromptFocus"
          @keydown="onKeydown"
        ></textarea>
        <div class="flex items-center justify-between px-1 pb-1 pt-0">
          <div class="flex items-center gap-2">
            <Button
              :disabled="chatLoading"
              class="!flex !items-center !border-blue-300 !bg-blue-50 !font-medium !text-blue-600 hover:!border-blue-500 hover:!bg-blue-100"
              size="small"
              title="拍照或上传图片，AI 识别清单"
              @click="pickImage"
            >
              <IconifyIcon class="mr-1 size-4" icon="lucide:camera" />
              图片识别
            </Button>
            <Button
              :disabled="chatLoading"
              class="!flex !items-center !border-amber-300 !bg-amber-50 !font-medium !text-amber-600 hover:!border-amber-500 hover:!bg-amber-100"
              size="small"
              title="上传 Excel / CSV / PDF 清单文件"
              @click="pickDoc"
            >
              <IconifyIcon class="mr-1 size-4" icon="lucide:paperclip" />
              文件识别
            </Button>
          </div>
          <Button
            type="primary"
            :loading="chatLoading"
            @click="handleSend"
          >
            <IconifyIcon icon="lucide:send-horizontal" class="mr-1 size-4" />
            发送
          </Button>
        </div>
      </div>

      <!-- 已选附件预览 -->
      <div
        v-if="images.length || docs.length"
        class="flex flex-wrap gap-2"
      >
        <div
          v-for="(img, i) in images"
          :key="`i${i}`"
          class="group relative h-14 w-14 overflow-hidden rounded border border-gray-200 bg-white"
        >
          <img :src="img.url" class="h-full w-full object-cover" />
          <span
            class="absolute right-0 top-0 hidden cursor-pointer bg-black/50 px-1 text-xs text-white group-hover:block"
            @click="images.splice(i, 1)"
          >
            ×
          </span>
        </div>
        <div
          v-for="(doc, i) in docs"
          :key="`d${i}`"
          class="group relative flex h-14 items-center gap-1 rounded border border-gray-200 bg-white px-2 text-xs"
        >
          <IconifyIcon class="size-4" icon="lucide:file-text" />
          <span class="max-w-24 truncate">{{ doc.name }}</span>
          <span
            class="absolute right-0 top-0 hidden cursor-pointer bg-black/50 px-1 text-white group-hover:block"
            @click="docs.splice(i, 1)"
          >
            ×
          </span>
        </div>
      </div>

      <!-- 对话消息列表（对话态显示，替代图表区） -->
      <div
        v-if="showMessageList"
        ref="scrollRef"
        class="min-h-0 flex-1 overflow-y-auto rounded-xl border border-border bg-card p-4"
      >
        <!-- 空对话引导 -->
        <div
          v-if="messages.length === 0"
          class="flex h-full flex-col items-center justify-center text-center"
        >
          <IconifyIcon
            icon="lucide:bot"
            class="mb-3 size-10 text-primary"
          />
          <div class="text-sm text-gray-500">
            用自然语言描述你要做的事，我会自动识别并生成单据
          </div>
          <div class="mt-2 text-xs text-gray-400">
            例：采购入库 岩棉板 20 件 到 金岩原辅材料库 · 查询金岩原辅材料库库存余额
          </div>
        </div>

        <div v-else class="space-y-4">
          <div
            v-for="msg in messages"
            :key="msg.id"
            class="flex gap-2"
            :class="msg.role === 'user' ? 'justify-end' : 'justify-start'"
          >
            <div
              class="rounded-lg px-3 py-2 text-sm leading-relaxed"
              :class="
                msg.role === 'user'
                  ? 'max-w-[76%] bg-blue-500 text-white'
                  : 'w-[92%] border border-gray-200 bg-white text-gray-700'
              "
            >
              <!-- 附件 -->
              <div v-if="msg.images?.length" class="mb-2 flex flex-wrap gap-2">
                <img
                  v-for="(img, i) in msg.images"
                  :key="i"
                  :src="img.url"
                  class="h-20 w-20 rounded object-cover"
                />
              </div>
              <div v-if="msg.docs?.length" class="mb-2 space-y-1">
                <div
                  v-for="(doc, i) in msg.docs"
                  :key="i"
                  class="flex items-center gap-1 rounded bg-black/5 px-2 py-1 text-xs"
                >
                  <IconifyIcon class="size-3.5" icon="lucide:file-text" />
                  <span class="max-w-40 truncate">{{ doc.name }}</span>
                  <span class="opacity-60">{{ humanSize(doc.size) }}</span>
                </div>
              </div>

              <div class="whitespace-pre-wrap break-words">{{ msg.text }}</div>

              <!-- 待确认单据卡片：可选仓库/供应商/单据类型，明细可编辑 -->
              <OrderConfirmCard
                v-if="msg.role === 'ai' && msg.buildable"
                class="mt-2"
                :merchants="merchants"
                :msg="msg"
                :session-id="sessionId"
                :warehouses="warehouses"
                @created="refresh"
              />

              <div v-if="msg.orderNo" class="mt-2 text-xs text-green-600">
                已创建单据：{{ msg.orderNo }}
              </div>
            </div>
          </div>

          <div
            v-if="chatLoading"
            class="flex items-center gap-2 text-sm text-gray-400"
          >
            <Spin size="small" />
            <span>AI 正在识别处理中，请稍候…</span>
          </div>
        </div>
      </div>

      <!-- 可视化图表区（非对话态显示） -->
      <div v-else class="min-h-0 flex-1 overflow-y-auto">
        <div class="flex flex-col gap-2">
          <WmsHomeOrderSummaryCards ref="orderSummaryCardsRef" />
          <div class="grid grid-cols-5 gap-4 max-xl:grid-cols-1">
            <div class="col-span-2 max-xl:col-span-1">
              <WmsHomeOrderTrendChart ref="orderTrendChartRef" />
            </div>
            <div class="col-span-3 max-xl:col-span-1">
              <WmsHomeInventoryCharts ref="inventoryChartsRef" />
            </div>
          </div>
          <div class="text-center text-sm text-muted-foreground">
            统计时间：{{ statTime }}
          </div>
        </div>
      </div>
    </div>
  </Page>
</template>
