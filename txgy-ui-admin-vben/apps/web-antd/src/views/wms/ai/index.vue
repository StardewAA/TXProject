<script setup lang="ts">
/**
 * WMS AI 智能助手 —— 独立页面版（含历史对话）
 * 路由：/wms/ai-center
 * 与右下角悬浮球共用同一套后端接口（/wms/ai/recognize、/wms/ai/recognize-file）
 */
import type { WmsAiApi } from '#/api/wms/ai';
import type { WmsMerchantApi } from '#/api/wms/md/merchant';
import type { WmsWarehouseApi } from '#/api/wms/md/warehouse';
import type {
  AiAttachment,
  AiChatMessage,
  AiConversation,
} from './composables/useConversations';

import { computed, nextTick, onMounted, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';
import { formatDateTime } from '@vben/utils';
import { useUserStore } from '@vben/stores';

import { Button, message, Spin } from 'ant-design-vue';

import {
  recognize as apiRecognize,
  recognizeFile as apiRecognizeFile,
} from '#/api/wms/ai';
import { getMerchantSimpleList } from '#/api/wms/md/merchant';
import { getWarehouseSimpleList } from '#/api/wms/md/warehouse';

import WmsHomeInventoryCharts from '../home/modules/wms-home-inventory-charts.vue';
import WmsHomeOrderSummaryCards from '../home/modules/wms-home-order-summary-cards.vue';
import WmsHomeOrderTrendChart from '../home/modules/wms-home-order-trend-chart.vue';
import {
  createConversation,
  currentConversationStorageKey,
  deriveTitle,
  hasResolvedUser,
  loadConversations,
  newSessionId,
  saveConversations,
} from './composables/useConversations';
import ConversationSidebar from './modules/ConversationSidebar.vue';
import OrderConfirmCard from './modules/OrderConfirmCard.vue';

defineOptions({ name: 'WmsAiCenter' });

const conversations = ref<AiConversation[]>([]);
const activeId = ref<null | string>(null);

/** 仓库/往来企业精简列表（供待确认卡片选择） */
const warehouses = ref<WmsWarehouseApi.Warehouse[]>([]);
const merchants = ref<WmsMerchantApi.Merchant[]>([]);

const input = ref('');
const sending = ref(false);
const images = ref<AiAttachment[]>([]);
const docs = ref<AiAttachment[]>([]);
const scrollRef = ref<HTMLElement | null>(null);

const WELCOME_SUB = '用自然语言描述你要做的事，我会自动识别并生成单据';

const userStore = useUserStore();
const welcomeTitle = computed(() => {
  const name = userStore.userInfo?.nickname || userStore.userInfo?.username;
  return name
    ? `欢迎 ${name}，我是仓库智能助手`
    : '你好，我是仓库智能助手';
});

/** 输入框下方的能力提示条（示例用公司主业的防腐保温材料，避免出现无关品类） */
const QUICK_TIPS = [
  '采购入库 岩棉板 20 件 到 金岩原辅材料库',
  '查询金岩原辅材料库库存余额',
  '新建商品：岩棉板，单位立方米，规格 1200×600×50',
  '出库发货 岩棉板 5 件 从金岩成品库',
];

const messages = ref<AiChatMessage[]>([]);
const activeConversation = ref<AiConversation | null>(null);

function uid() {
  return `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
}

function welcome(): AiChatMessage {
  return { id: uid(), role: 'ai', text: WELCOME_SUB, welcome: true };
}

/** 是否处于「空会话」引导态（只有欢迎语，没有任何真实消息） */
const showHero = computed(() => messages.value.every((m) => m.welcome));

// ============ 首页可视化图表（空会话时展示，进入对话后收起） ============
const orderSummaryCardsRef =
  ref<InstanceType<typeof WmsHomeOrderSummaryCards>>();
const orderTrendChartRef = ref<InstanceType<typeof WmsHomeOrderTrendChart>>();
const inventoryChartsRef = ref<InstanceType<typeof WmsHomeInventoryCharts>>();
const statTime = ref('');

/** 空会话首页输入框是否处于聚焦态；聚焦时隐藏图表，让界面更沉浸 */
const heroInputFocused = ref(false);

function handleHeroFocus() {
  heroInputFocused.value = true;
}

function handleHeroBlur(e: FocusEvent) {
  const target = e.relatedTarget as HTMLElement | null;
  const container = document.querySelector('.hero-input-container');
  if (container && target && container.contains(target)) {
    return;
  }
  heroInputFocused.value = false;
}

async function loadCharts() {
  try {
    await Promise.all([
      orderSummaryCardsRef.value?.load(),
      orderTrendChartRef.value?.load(),
      inventoryChartsRef.value?.load(),
    ]);
    statTime.value = formatDateTime(new Date());
  } catch {
    // 图表加载失败不影响对话主流程
  }
}

// 空会话态 <-> 对话态切换时，重新加载图表数据
watch(showHero, (v) => {
  if (v) void nextTick(loadCharts);
});

function applyTip(tip: string) {
  input.value = tip;
}

function actionText(a?: string) {
  return { IN: '入库', MOVE: '移库', OUT: '出库' }[a || ''] || a || '';
}

/** 判断 AI 回复是否为权限拒绝提示 */
function isPermissionDenied(text?: string): boolean {
  if (!text) return false;
  return /您没有.*权限|需要权限|无权访问|越权|无权限|权限不足/.test(text);
}

/**
 * 把后端 AiItem 归一化成确认卡片/建单需要的扁平结构。
 *
 * 后端 AiItem 的形状是 { sku: {id, name, itemName, skuName, unit, candidates…}, quantity, price }，
 * 而确认卡片与 /wms/ai/create-order 读的是扁平字段（name/spec/unit/itemId/skuId/candidates）。
 * 不做这次转换，建单时会因 skuId 为空而报「未匹配到系统商品」。
 */
function normalizeItems(items?: WmsAiApi.ItemVO[]): WmsAiApi.ItemVO[] {
  return (items || []).map((raw: any) => {
    const sku = raw?.sku || {};
    return {
      ...raw,
      candidates: sku.candidates || raw.candidates || [],
      itemId: sku.itemId ?? raw.itemId,
      name: sku.name || sku.itemName || raw.name || '',
      price: raw.price ?? sku.price ?? undefined,
      resolved: sku.resolved ?? raw.resolved ?? false,
      skuId: sku.id ?? raw.skuId,
      spec: sku.skuName || raw.spec,
      unit: sku.unit || raw.unit,
    } as WmsAiApi.ItemVO;
  });
}

/** 当前会话的 sessionId（保证多轮上下文延续） */
function sid() {
  return activeConversation.value?.sessionId || '';
}

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

/**
 * 拼接 reply 与 tips，并去掉重复内容。
 *
 * 后端曾经把同一段说明同时写进 reply 和 tips，两个字段又都会被展示，
 * 结果同一段话出现两遍。这里按「规范化后的文本」去重，保证只显示一次。
 */
function dedupeReplyAndTips(reply?: string, tips?: string[]) {
  const norm = (s: string) => s.replace(/\s+/g, ' ').trim();
  const out: string[] = [];
  const seen = new Set<string>();
  for (const part of [reply, ...(tips || [])]) {
    if (!part) continue;
    const key = norm(part);
    if (!key || seen.has(key)) continue;
    seen.add(key);
    out.push(part);
  }
  return out.join('\n');
}

/** 把当前消息写回会话并持久化 */
function persist() {
  const c = activeConversation.value;
  if (!c) return;
  c.messages = messages.value
    .filter((m) => !m.welcome)
    .map((m) => ({ ...m }));
  c.updateTime = Date.now();
  // 用首条用户消息生成标题
  if (!c.title || c.title === '新对话') {
    const firstUser = c.messages.find((m) => m.role === 'user' && m.text);
    if (firstUser) c.title = deriveTitle(firstUser.text);
  }
  saveConversations(conversations.value);
}

// ==================== 会话管理 ====================

function selectConversation(c: AiConversation) {
  activeConversation.value = c;
  activeId.value = c.id;
  messages.value =
    c.messages.length > 0 ? [welcome(), ...c.messages] : [welcome()];
  input.value = '';
  images.value = [];
  docs.value = [];
  heroInputFocused.value = false;
  void nextTick(scrollToBottom);
}

function handleCreate() {
  const c = createConversation();
  conversations.value.unshift(c);
  saveConversations(conversations.value);
  selectConversation(c);
}

function handleRename(c: AiConversation, title: string) {
  c.title = title;
  c.updateTime = Date.now();
  saveConversations(conversations.value);
  message.success('重命名成功');
}

function handleDelete(c: AiConversation) {
  conversations.value = conversations.value.filter((x) => x.id !== c.id);
  saveConversations(conversations.value);
  if (activeId.value === c.id) {
    if (conversations.value.length > 0 && conversations.value[0]) {
      selectConversation(conversations.value[0]);
    } else {
      handleCreate();
    }
  }
  message.success('对话已删除');
}

function handleTop(c: AiConversation) {
  c.pinned = !c.pinned;
  saveConversations(conversations.value);
}

function handleClear() {
  conversations.value = conversations.value.filter((c) => c.pinned);
  saveConversations(conversations.value);
  if (conversations.value.length > 0 && conversations.value[0]) {
    selectConversation(conversations.value[0]);
  } else {
    handleCreate();
  }
  message.success('已清空未置顶对话');
}

// ==================== 附件 ====================

/** 附件上限：图片/文件各最多 6 个，单文件不超过 10MB */
const MAX_ATTACHMENTS = 6;
const MAX_FILE_SIZE = 10 * 1024 * 1024;

function pickFile(accept: string, target: typeof images) {
  const el = document.createElement('input');
  el.type = 'file';
  el.multiple = true;
  el.accept = accept;
  el.addEventListener('change', () => {
    const files = Array.from(el.files || []);
    // 剩余可选数 = 上限 - 已入列（FileReader 异步读取，未完成的先按已占用算，防止一次多选绕过上限）
    let allowed = MAX_ATTACHMENTS - target.value.length;
    let overCount = false;
    for (const f of files) {
      if (f.size > MAX_FILE_SIZE) {
        message.warning(`「${f.name}」超过 10MB 上限，已跳过`);
        continue;
      }
      if (allowed <= 0) {
        overCount = true;
        break;
      }
      allowed--;
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
    if (overCount) {
      message.warning(`附件最多 ${MAX_ATTACHMENTS} 个，超出的已忽略`);
    }
    // 重置 value，允许用户删除后重新选择同一文件
    el.value = '';
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

/** 在浏览器里打开附件（图片放大 / 文件预览） */
function openAttachment(url?: string) {
  if (!url) return;
  window.open(url, '_blank');
}

/** 触发文件下载；对相对路径会 Blob 拉取后再下载，避免被 Vue Router 拦截 */
async function downloadFile(url?: string, filename?: string) {
  if (!url) return;

  // data: 或绝对 http(s) 链接直接用 <a download> 触发
  if (url.startsWith('data:') || /^https?:\/\//i.test(url)) {
    triggerLink(url, filename);
    return;
  }

  // 相对路径：先fetch成blob再生成object URL，避免SPA路由拦截
  try {
    const res = await fetch(url, { credentials: 'include' });
    if (!res.ok) throw new Error(`下载失败 ${res.status}`);
    const blob = await res.blob();
    triggerLink(URL.createObjectURL(blob), filename);
  } catch {
    message.error('文件打开失败，请重试');
  }
}

function triggerLink(url: string, filename?: string) {
  const a = document.createElement('a');
  a.href = url;
  if (filename) a.download = filename;
  a.target = '_blank';
  a.style.display = 'none';
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
}

// ==================== 发送 ====================

async function send() {
  const text = input.value.trim();
  if (sending.value) return;
  if (!text && images.value.length === 0 && docs.value.length === 0) return;

  const attachedImages = [...images.value];
  const attachedDocs = [...docs.value];

  push({
    id: uid(),
    role: 'user',
    text,
    images: attachedImages,
    docs: attachedDocs,
  });

  input.value = '';
  images.value = [];
  docs.value = [];
  sending.value = true;
  persist();

  try {
    const payload: WmsAiApi.RecognizeReqVO = {
      images: attachedImages.map((i) => i.url),
      sessionId: sid(),
      text,
    };

    let resp: WmsAiApi.RecognizeRespVO;
    if (attachedDocs.length > 0) {
      payload.files = attachedDocs.map((d) => ({ name: d.name, url: d.url }));
      resp = await apiRecognizeFile(payload);
    } else {
      resp = await apiRecognize(payload);
    }

    // reply 与 tips 依次拼接展示。
    // 后端历史实现里存在「同一段文字既放 reply 又放 tips」的情况（会导致整段显示两遍），
    // 这里做一次去重兜底：丢掉与 reply 完全相同、或与前一条 tip 重复的项。
    const aiText = dedupeReplyAndTips(resp.reply, resp.tips);
    push({
      action: resp.action,
      buildable: resp.buildable,
      id: uid(),
      items: normalizeItems(resp.items),
      merchant: resp.merchant?.name,
      merchantId: resp.merchant?.id,
      permission: isPermissionDenied(aiText),
      role: 'ai',
      text: aiText,
      type: resp.type,
      // 后端 warehouse/sourceWarehouse/targetWarehouse 都是 { id, name, resolved } 对象，
      // 这里摊平成确认卡片使用的「名称 + 编号」，否则 id 会丢，卡片只能让用户手选。
      warehouse: resp.warehouse?.name || resp.sourceWarehouse?.name,
      warehouseId: resp.warehouse?.id ?? resp.sourceWarehouse?.id,
      // 移库专用：移入仓库（其余动作后端不返回）
      targetWarehouse: resp.targetWarehouse?.name,
      targetWarehouseId: resp.targetWarehouse?.id,
    });
    persist();
  } catch (error: any) {
    push({
      id: uid(),
      role: 'ai',
      text: `识别出错了：${error?.message || '请稍后重试'}`,
    });
    persist();
  } finally {
    sending.value = false;
    void nextTick(scrollToBottom);
  }
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    void send();
  }
}

// ==================== 初始化 ====================

/** 把某个用户的会话列表装载进来（已装载过相同 key 则不重复处理） */
let loadedKey = '';
function applyLoadedConversations() {
  const list = loadConversations();
  if (list.length > 0) {
    conversations.value = list;
    const first =
      [...list].sort((a, b) => b.updateTime - a.updateTime)[0] ||
      createConversation();
    selectConversation(first);
  } else {
    handleCreate();
  }
  if (showHero.value) void nextTick(loadCharts);
}

onMounted(() => {
  // watch 的 immediate 在 setup 阶段可能已经按用户 key 装载过一次，这里避免重复装载
  if (!loadedKey) {
    applyLoadedConversations();
  }
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

/**
 * 会话按「租户 + 用户」隔离（见 useConversations.storageKey）。
 *
 * 刷新页面时 userInfo 只在内存、要等 /auth/get-permission-info 回来才有值；
 * 若挂载阶段拿不到用户身份，会话会落到全局旧 key 上，切换账号后就会看到别人的历史。
 * 因此监听存储 key：一旦能确定真实身份（或换成另一个账号），重新加载该用户自己的会话。
 */
watch(
  () => currentConversationStorageKey(),
  (key) => {
    if (!hasResolvedUser()) {
      return;
    }
    if (key === loadedKey) {
      return;
    }
    loadedKey = key;
    applyLoadedConversations();
  },
  { immediate: true },
);

defineExpose({ handleCreate, newSessionId });
</script>

<template>
  <div class="flex h-full max-h-screen overflow-hidden bg-gray-50">
    <!-- 左侧历史对话 -->
    <ConversationSidebar
      :active-id="activeId"
      :conversations="conversations"
      @clear="handleClear"
      @create="handleCreate"
      @delete="handleDelete"
      @rename="handleRename"
      @select="selectConversation"
      @top="handleTop"
    />

    <!-- 右侧聊天区 -->
    <div class="flex h-full min-h-0 min-w-0 flex-1 flex-col">
      <!-- 顶部标题栏（空会话展示欢迎页时隐藏，让欢迎语+输入框成为视觉焦点） -->
      <div
        v-if="!showHero"
        class="flex items-center justify-between border-b border-gray-200 bg-white px-4 py-3"
      >
        <div class="flex items-center gap-2">
          <span
            class="flex size-8 items-center justify-center rounded-full bg-blue-50"
          >
            <IconifyIcon class="size-5 text-blue-500" icon="lucide:bot" />
          </span>
          <div>
            <div class="text-base font-semibold text-gray-800">
              {{ activeConversation?.title || 'WMS AI 智能助手' }}
            </div>
            <div class="text-xs text-gray-400">
              自然语言出入库 · 智能建档 · 库存查询
            </div>
          </div>
        </div>
        <Button size="small" @click="handleCreate">
          <IconifyIcon class="mr-1 inline size-4" icon="lucide:plus" />
          新会话
        </Button>
      </div>

      <!-- 消息区 -->
      <div ref="scrollRef" class="min-h-0 flex-1 overflow-y-auto px-4 py-4">
        <!-- 空会话：欢迎语 + WMS 首页可视化图表 -->
        <div
          v-if="showHero"
          class="mx-auto flex h-full max-w-6xl flex-col transition-all duration-500"
        >
          <div
            class="flex flex-1 flex-col justify-center transition-all duration-500"
          >
            <div class="mx-auto w-full max-w-2xl text-center">
              <div
                class="mx-auto mb-4 flex size-12 items-center justify-center rounded-2xl bg-blue-50"
              >
                <IconifyIcon class="size-7 text-blue-500" icon="lucide:bot" />
              </div>
              <div class="text-xl font-semibold text-gray-800">
                {{ welcomeTitle }}
              </div>
              <div class="mt-2 text-sm text-gray-400">{{ WELCOME_SUB }}</div>
            </div>

            <!-- 输入区（空会话时紧随欢迎语居中展示，开始对话后固定到底部） -->
            <div
              class="hero-input-container mx-auto mt-6 w-full max-w-3xl px-4 transition-all duration-500"
            >
              <!-- 已选附件 -->
              <div
                v-if="images.length || docs.length"
                class="mb-2 flex flex-wrap gap-2"
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

              <!-- 大输入框 -->
              <div
                class="rounded-xl border border-gray-200 bg-white shadow-sm transition focus-within:border-blue-400"
              >
                <textarea
                  v-model="input"
                  :disabled="sending"
                  class="max-h-56 min-h-[120px] w-full resize-none rounded-t-xl border-0 px-4 py-3 text-sm outline-none"
                  placeholder="请输入你想咨询的内容，例如：采购入库 岩棉板 20 件 到 金岩原辅材料库"
                  rows="3"
                  @blur="handleHeroBlur"
                  @focus="handleHeroFocus"
                  @keydown="onKeydown"
                ></textarea>
                <div class="flex items-center justify-between px-3 pb-2">
                  <div class="flex items-center gap-2">
                    <Button
                      :disabled="sending"
                      class="!flex !items-center !border-blue-300 !bg-blue-50 !font-medium !text-blue-600 hover:!border-blue-500 hover:!bg-blue-100"
                      size="small"
                      title="拍照或上传图片，AI 识别清单"
                      @click="pickImage"
                    >
                      <IconifyIcon
                        class="mr-1 size-4"
                        icon="lucide:camera"
                      />
                      图片识别
                    </Button>
                    <Button
                      :disabled="sending"
                      class="!flex !items-center !border-amber-300 !bg-amber-50 !font-medium !text-amber-600 hover:!border-amber-500 hover:!bg-amber-100"
                      size="small"
                      title="上传 Excel / CSV / PDF 清单文件"
                      @click="pickDoc"
                    >
                      <IconifyIcon
                        class="mr-1 size-4"
                        icon="lucide:paperclip"
                      />
                      文件识别
                    </Button>
                  </div>
                  <Button :loading="sending" type="primary" @click="send">
                    发送
                  </Button>
                </div>
              </div>
            </div>

            <!-- 操作提示条（欢迎页居中） -->
            <div
              v-show="!heroInputFocused"
              class="mt-3 flex flex-wrap items-center justify-center gap-2"
            >
              <span class="text-xs text-gray-400">试试：</span>
              <button
                v-for="tip in QUICK_TIPS"
                :key="tip"
                class="rounded-full border border-gray-200 bg-white px-3 py-1 text-xs text-gray-500 transition hover:border-blue-300 hover:text-blue-500"
                type="button"
                @click="applyTip(tip)"
              >
                {{ tip }}
              </button>
            </div>
          </div>

          <!-- 首页可视化：统计卡片 + 图表（开始对话后自动收起；聚焦输入框时隐藏） -->
          <div
            v-show="!heroInputFocused"
            class="mx-auto mt-6 flex w-full max-w-6xl flex-col gap-2 pb-2"
          >
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

        <div v-else class="mx-auto max-w-5xl space-y-4">
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
                  : msg.permission
                    ? 'w-[92%] border border-amber-200 bg-amber-50 text-amber-900'
                    : 'w-[92%] border border-gray-200 bg-white text-gray-700'
              "
            >
              <!-- 权限提示图标 -->
              <div
                v-if="msg.role === 'ai' && msg.permission"
                class="mb-1.5 flex items-center gap-1.5 text-xs font-medium text-amber-700"
              >
                <IconifyIcon class="size-4" icon="lucide:shield-alert" />
                <span>权限提示</span>
              </div>

              <!-- 附件（已发送的图片/文件可点击预览/下载） -->
              <div v-if="msg.images?.length" class="mb-2 flex flex-wrap gap-2">
                <a
                  v-for="(img, i) in msg.images"
                  :key="i"
                  href="#"
                  class="block cursor-pointer"
                  title="点击查看原图"
                  @click.prevent.stop="openAttachment(img.url)"
                >
                  <img
                    :src="img.url"
                    class="h-20 w-20 rounded object-cover transition hover:opacity-90"
                  />
                </a>
              </div>
              <div v-if="msg.docs?.length" class="mb-2 space-y-1">
                <a
                  v-for="(doc, i) in msg.docs"
                  :key="i"
                  href="#"
                  class="flex cursor-pointer items-center gap-1 rounded bg-black/5 px-2 py-1 text-xs transition hover:bg-black/10"
                  title="点击打开/下载文件"
                  @click.prevent.stop="downloadFile(doc.url, doc.name)"
                >
                  <IconifyIcon class="size-3.5" icon="lucide:file-text" />
                  <span class="max-w-40 truncate">{{ doc.name }}</span>
                  <span class="opacity-60">{{ humanSize(doc.size) }}</span>
                </a>
              </div>

              <div class="whitespace-pre-wrap break-words">{{ msg.text }}</div>

              <!-- 待确认单据卡片：可选仓库/供应商/单据类型，明细可编辑 -->
              <OrderConfirmCard
                v-if="msg.role === 'ai' && msg.buildable"
                class="mt-2"
                :merchants="merchants"
                :msg="msg"
                :session-id="sid()"
                :warehouses="warehouses"
                @created="persist"
              />

              <div v-if="msg.orderNo" class="mt-2 text-xs text-green-600">
                已创建单据：{{ msg.orderNo }}
              </div>
            </div>
          </div>

          <div
            v-if="sending"
            class="flex items-center gap-2 text-sm text-gray-400"
          >
            <Spin size="small" />
            <span>AI 正在识别处理中，请稍候…</span>
          </div>
        </div>
      </div>

      <!-- 输入区（对话态固定底部；空会话时输入框在欢迎页中部展示） -->
      <div v-if="!showHero" class="bg-gray-50 px-4 pb-4 pt-2">
        <div class="mx-auto max-w-5xl">
          <!-- 已选附件 -->
          <div
            v-if="images.length || docs.length"
            class="mb-2 flex flex-wrap gap-2"
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

          <!-- 大输入框 -->
          <div
            class="rounded-xl border border-gray-200 bg-white shadow-sm transition focus-within:border-blue-400"
          >
            <textarea
              v-model="input"
              :disabled="sending"
              class="max-h-40 min-h-[76px] w-full resize-none rounded-t-xl border-0 px-4 py-3 text-sm outline-none"
              placeholder="请输入你想咨询的内容，例如：采购入库 岩棉板 20 件 到 金岩原辅材料库"
              rows="2"
              @keydown="onKeydown"
            ></textarea>
            <div class="flex items-center justify-between px-3 pb-2">
              <div class="flex items-center gap-2">
                <Button
                  :disabled="sending"
                  class="!flex !items-center !border-blue-300 !bg-blue-50 !font-medium !text-blue-600 hover:!border-blue-500 hover:!bg-blue-100"
                  size="small"
                  title="拍照或上传图片，AI 识别清单"
                  @click="pickImage"
                >
                  <IconifyIcon class="mr-1 size-4" icon="lucide:camera" />
                  图片识别
                </Button>
                <Button
                  :disabled="sending"
                  class="!flex !items-center !border-amber-300 !bg-amber-50 !font-medium !text-amber-600 hover:!border-amber-500 hover:!bg-amber-100"
                  size="small"
                  title="上传 Excel / CSV / PDF 清单文件"
                  @click="pickDoc"
                >
                  <IconifyIcon class="mr-1 size-4" icon="lucide:paperclip" />
                  文件识别
                </Button>
              </div>
              <Button :loading="sending" type="primary" @click="send">
                发送
              </Button>
            </div>
          </div>

          <!-- 操作提示条 -->
          <div class="mt-2 flex flex-wrap items-center gap-2">
            <span class="text-xs text-gray-400">试试：</span>
            <button
              v-for="tip in QUICK_TIPS"
              :key="tip"
              class="rounded-full border border-gray-200 bg-white px-3 py-1 text-xs text-gray-500 transition hover:border-blue-300 hover:text-blue-500"
              type="button"
              @click="applyTip(tip)"
            >
              {{ tip }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
