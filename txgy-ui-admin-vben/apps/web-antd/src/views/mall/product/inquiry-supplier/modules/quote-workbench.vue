<script setup lang="ts">
import type { Dayjs } from 'dayjs';

import type { SupplierInquiryApi } from '#/api/mall/product/inquiry-supplier';

import { computed, reactive, ref, watch } from 'vue';

import { useAccess } from '@vben/access';
import { useAccessStore } from '@vben/stores';
import { fenToYuan } from '@vben/utils';

import {
  Alert,
  Button,
  DatePicker,
  Descriptions,
  DescriptionsItem,
  Drawer,
  Empty,
  Input,
  InputNumber,
  List,
  ListItem,
  message,
  Modal,
  Select,
  SelectOption,
  Space,
  Spin,
  Tag,
} from 'ant-design-vue';
import dayjs from 'dayjs';

import {
  getMyQuote,
  getOpportunity,
  getQuote,
  getQuoteMessages,
  getQuoteSkuOptions,
  saveQuote,
  sendQuoteMessage,
  submitQuote,
  withdrawQuote,
} from '#/api/mall/product/inquiry-supplier';

import {
  INQUIRY_MESSAGE_PERMISSION,
  INQUIRY_QUOTE_PERMISSION,
  makeRequestKey,
  money,
  quoteActions,
  quoteStatus,
  validateQuoteSave,
  yuanToCents,
} from '../model';

interface EditorLine {
  count: number;
  inquiryItemId: number;
  name: string;
  quantity: number;
  skuId?: number;
  specification?: string;
  unit?: string;
  unitPriceYuan: number;
}

const props = withDefaults(defineProps<{
  communicationOnly?: boolean;
  inquiryId?: number;
  open: boolean;
  quoteId?: number;
  shopId?: number;
}>(), {
  communicationOnly: false,
  inquiryId: undefined,
  quoteId: undefined,
  shopId: undefined,
});

const emit = defineEmits<{
  saved: [];
  'update:open': [value: boolean];
}>();

const access = useAccessStore();
const { hasAccessByCodes } = useAccess();
const detail = ref<SupplierInquiryApi.Detail>();
const quote = ref<null | SupplierInquiryApi.Quote>();
const skus = ref<SupplierInquiryApi.QuoteSku[]>([]);
const messages = ref<SupplierInquiryApi.Message[]>([]);
const loading = ref(false);
const saving = ref(false);
const sending = ref(false);
const error = ref('');
const messagesError = ref(false);
const pendingMessage = ref<{ clientMessageId: string; content: string; quoteId: number }>();
const draftMessage = ref('');
const requestKey = ref(makeRequestKey('quote-save'));
let generation = 0;

const editor = reactive({
  deliveryDate: undefined as Dayjs | undefined,
  freightYuan: 0,
  lines: [] as EditorLine[],
  remark: '',
  validUntil: undefined as Dayjs | undefined,
});

const crossTenant = computed(() => access.visitTenantId != null
  && String(access.visitTenantId) !== String(access.tenantId));
const permissions = computed(() => ({
  message: hasAccessByCodes([INQUIRY_MESSAGE_PERMISSION]) && !crossTenant.value,
  quote: hasAccessByCodes([INQUIRY_QUOTE_PERMISSION]) && !crossTenant.value,
}));
const actions = computed(() => quoteActions(
  quote.value,
  permissions.value,
  detail.value?.order.canQuote === true,
));
const editable = computed(() => !props.communicationOnly
  && (actions.value.create || actions.value.edit));
const displayCents = (value: unknown) => {
  const amount = Number(value);
  return Number.isFinite(amount) && amount >= 0 ? Math.round(amount * 100) : 0;
};
const total = computed(() => editor.lines.reduce(
  (sum, row) => sum + displayCents(row.unitPriceYuan) * Math.max(0, Number(row.count) || 0),
  displayCents(editor.freightYuan),
));

function close() {
  generation += 1;
  emit('update:open', false);
}

function resetEditor(current: null | SupplierInquiryApi.Quote | undefined) {
  const inquiryItems = detail.value?.items ?? [];
  const quoted = current?.items ?? [];
  editor.freightYuan = Number(current?.freightPrice ?? 0) / 100;
  editor.deliveryDate = current?.deliveryDate ? dayjs(current.deliveryDate) : undefined;
  editor.validUntil = current?.validUntil ? dayjs(current.validUntil) : undefined;
  editor.remark = current?.remark ?? '';
  editor.lines = inquiryItems.map((item, index) => {
    const value = quoted.find((row) => row.inquiryItemId === item.id) ?? quoted[index];
    return {
      count: value?.count ?? (Number.isInteger(item.quantity) ? item.quantity : 1),
      inquiryItemId: item.id,
      name: item.name,
      quantity: item.quantity,
      skuId: value?.skuId,
      specification: item.specification,
      unit: item.unit,
      unitPriceYuan: Number(value?.unitPrice ?? 0) / 100,
    };
  });
}

async function loadMessages(current: null | SupplierInquiryApi.Quote | undefined, ticket: number) {
  messages.value = [];
  messagesError.value = false;
  if (!current?.id || !permissions.value.message || current.canMessage !== true) return;
  try {
    const rows: SupplierInquiryApi.Message[] = [];
    let afterId = 0;
    for (let page = 0; page < 20; page += 1) {
      const batch = await getQuoteMessages(current.id, afterId, 100);
      if (ticket !== generation) return;
      rows.push(...batch);
      if (batch.length < 100) {
        messages.value = rows;
        return;
      }
      const next = batch.at(-1)?.id;
      if (!next || next <= afterId) throw new Error('沟通记录分页游标无效');
      afterId = next;
    }
    throw new Error('沟通记录过多，请联系平台归档');
  } catch {
    if (ticket === generation) messagesError.value = true;
  }
}

async function load() {
  const ticket = ++generation;
  detail.value = undefined;
  quote.value = undefined;
  skus.value = [];
  messages.value = [];
  error.value = '';
  if (!props.open) return;
  if (crossTenant.value) {
    error.value = '供应经营不支持跨租户代操作，请切回后台账号所属租户。';
    return;
  }
  if (!props.shopId || (!props.inquiryId && !props.quoteId)) {
    error.value = '询价单或授权店铺信息缺失，请返回列表重试。';
    return;
  }
  loading.value = true;
  try {
    const current = props.quoteId
      ? await getQuote(props.quoteId)
      : await getMyQuote(props.inquiryId!, props.shopId);
    if (ticket !== generation) return;
    if (current && current.shopId !== props.shopId) throw new Error('报价店铺范围已变化');
    const inquiryId = current?.inquiryId ?? props.inquiryId!;
    const inquiry = await getOpportunity(inquiryId, props.shopId);
    if (ticket !== generation) return;
    detail.value = inquiry;
    quote.value = current;
    resetEditor(current);
    requestKey.value = makeRequestKey('quote-save');
    if (permissions.value.quote && (!current || current.canEdit === true)) {
      skus.value = await getQuoteSkuOptions(props.shopId);
      if (ticket !== generation) return;
    }
    await loadMessages(current, ticket);
  } catch (cause) {
    if (ticket === generation) {
      console.error('[supplier-inquiry] quote workbench load failed', cause);
      error.value = '报价工作台加载失败。请核对后台权限、企业和店铺授权后重试。';
    }
  } finally {
    if (ticket === generation) loading.value = false;
  }
}

function buildSaveRequest() {
  if (!detail.value || !props.shopId) throw new Error('询价单或报价店铺无效');
  return validateQuoteSave({
    ...(quote.value?.id ? { id: quote.value.id } : {}),
    deliveryDate: editor.deliveryDate?.valueOf() ?? 0,
    freightPrice: yuanToCents(editor.freightYuan, '运费'),
    inquiryId: detail.value.order.id,
    items: editor.lines.map((row) => ({
      count: row.count,
      inquiryItemId: row.inquiryItemId,
      skuId: row.skuId ?? 0,
      unitPrice: yuanToCents(row.unitPriceYuan, '含税单价'),
    })),
    remark: editor.remark.trim(),
    requestKey: requestKey.value,
    shopId: props.shopId,
    validUntil: editor.validUntil?.valueOf() ?? 0,
    version: quote.value?.version ?? 0,
  });
}

async function save() {
  if (!editable.value || saving.value) return;
  saving.value = true;
  try {
    const id = await saveQuote(buildSaveRequest());
    const fresh = await getQuote(id);
    quote.value = fresh;
    resetEditor(fresh);
    requestKey.value = makeRequestKey('quote-save');
    message.success('报价草稿已保存');
    emit('saved');
  } catch (cause) {
    message.error(cause instanceof Error ? cause.message : '报价保存失败，请重试');
  } finally {
    saving.value = false;
  }
}

async function submit() {
  if (!quote.value?.id || !actions.value.submit || saving.value) return;
  Modal.confirm({
    title: '确认提交报价？',
    content: '提交后采购企业可以查看并采纳；如需修改，应先按规则撤回。',
    async onOk() {
      saving.value = true;
      try {
        await submitQuote(quote.value!.id!, quote.value!.version);
        quote.value = await getQuote(quote.value!.id!);
        resetEditor(quote.value);
        message.success('报价已提交');
        emit('saved');
      } finally {
        saving.value = false;
      }
    },
  });
}

async function withdraw() {
  if (!quote.value?.id || !actions.value.withdraw || saving.value) return;
  Modal.confirm({
    title: '确认撤回报价？',
    content: '撤回后采购企业不能继续采纳；可修改后重新提交。',
    async onOk() {
      saving.value = true;
      try {
        await withdrawQuote(quote.value!.id!, quote.value!.version);
        quote.value = await getQuote(quote.value!.id!);
        resetEditor(quote.value);
        message.success('报价已撤回');
        emit('saved');
      } finally {
        saving.value = false;
      }
    },
  });
}

async function sendMessage() {
  const content = draftMessage.value.trim();
  if (!quote.value?.id || !actions.value.message || !content || sending.value) return;
  const payload = pendingMessage.value?.quoteId === quote.value.id
    && pendingMessage.value.content === content
    ? pendingMessage.value
    : { clientMessageId: makeRequestKey('quote-message'), content, quoteId: quote.value.id };
  pendingMessage.value = payload;
  sending.value = true;
  try {
    await sendQuoteMessage(payload);
    draftMessage.value = '';
    pendingMessage.value = undefined;
    await loadMessages(quote.value, generation);
  } catch {
    message.error('消息发送未确认成功；原消息标识已保留，可直接重试。');
  } finally {
    sending.value = false;
  }
}

watch(() => [props.open, props.inquiryId, props.quoteId, props.shopId], () => {
  void load();
}, { flush: 'post' });
</script>

<template>
  <Drawer
    :open="open"
    :width="1120"
    destroy-on-close
    title="供应商报价工作台"
    @close="close"
  >
    <Spin :spinning="loading">
      <Alert v-if="error" type="error" show-icon :message="error">
        <template #action><Button size="small" @click="load">重试</Button></template>
      </Alert>
      <template v-else-if="detail">
        <Alert
          class="mb-4"
          show-icon
          type="info"
          message="报价只允许使用当前后台账号获授权店铺的已审核 SKU；采购方公开信息不包含私人联系方式。"
        />
        <Descriptions bordered :column="3" size="small">
          <DescriptionsItem label="询价单号">{{ detail.order.no || detail.order.id }}</DescriptionsItem>
          <DescriptionsItem label="采购企业">{{ detail.order.enterpriseName || '已认证采购企业' }}</DescriptionsItem>
          <DescriptionsItem label="报价状态">
            <Tag>{{ quote ? quoteStatus[quote.status] || '未知状态' : '尚未报价' }}</Tag>
          </DescriptionsItem>
          <DescriptionsItem label="标题" :span="2">{{ detail.order.title }}</DescriptionsItem>
          <DescriptionsItem label="交付区域">{{ detail.order.deliveryArea || '以订单为准' }}</DescriptionsItem>
          <DescriptionsItem label="采购说明" :span="3">{{ detail.remark || '无' }}</DescriptionsItem>
        </Descriptions>

        <section class="mt-5">
          <h3>逐项报价</h3>
          <div class="quote-table">
            <div class="quote-row quote-head">
              <span>采购物料</span><span>本店铺 SKU</span><span>包装件数</span><span>含税单价（元）</span>
            </div>
            <div v-for="line in editor.lines" :key="line.inquiryItemId" class="quote-row">
              <div>
                <strong>{{ line.name }}</strong>
                <p>{{ line.specification || '规格待沟通' }} · {{ line.quantity }} {{ line.unit }}</p>
              </div>
              <Select v-model:value="line.skuId" show-search option-filter-prop="label" :disabled="!editable" placeholder="选择已审核 SKU">
                <SelectOption
                  v-for="sku in skus"
                  :key="sku.id"
                  :value="sku.id"
                  :label="`${sku.productName} ${sku.specification || sku.barCode || ''}`"
                >
                  {{ sku.productName }} · {{ sku.specification || sku.barCode || `SKU ${sku.id}` }} · ¥{{ fenToYuan(sku.price) }}
                </SelectOption>
              </Select>
              <InputNumber v-model:value="line.count" :disabled="!editable" :min="1" :precision="0" />
              <InputNumber v-model:value="line.unitPriceYuan" :disabled="!editable" :min="0.01" :precision="2" />
            </div>
          </div>
        </section>

        <section class="quote-form mt-5">
          <label>预计运费（元）<InputNumber v-model:value="editor.freightYuan" :disabled="!editable" :min="0" :precision="2" /></label>
          <label>报价有效期<DatePicker v-model:value="editor.validUntil" :disabled="!editable" show-time /></label>
          <label>预计交货日期<DatePicker v-model:value="editor.deliveryDate" :disabled="!editable" /></label>
          <label class="remark">报价说明<Input.TextArea v-model:value="editor.remark" :disabled="!editable" :maxlength="1000" show-count /></label>
          <p class="total">报价合计：<strong>¥{{ money(total) }}</strong></p>
          <Space class="commands">
            <Button v-if="editable" :loading="saving" @click="save">保存报价草稿</Button>
            <Button v-if="actions.submit && !communicationOnly" type="primary" :loading="saving" @click="submit">提交报价</Button>
            <Button v-if="actions.withdraw && !communicationOnly" danger :loading="saving" @click="withdraw">撤回报价</Button>
          </Space>
        </section>

        <section v-if="quote?.id" class="conversation mt-5">
          <h3>报价沟通</h3>
          <Alert v-if="messagesError" class="mb-3" type="error" show-icon message="沟通记录加载失败；不会显示其他店铺或报价的消息。">
            <template #action><Button size="small" @click="loadMessages(quote, generation)">重试</Button></template>
          </Alert>
          <List v-if="messages.length" bordered :data-source="messages">
            <template #renderItem="{ item }">
              <ListItem>
                <div class="message-line">
                  <Tag :color="item.senderRole === 'SUPPLIER' ? 'blue' : 'green'">{{ item.senderRole === 'SUPPLIER' ? '供应方' : '采购方' }}</Tag>
                  <span>{{ item.content }}</span>
                  <small>{{ item.createTime ? new Date(item.createTime).toLocaleString('zh-CN', { hour12: false }) : '' }}</small>
                </div>
              </ListItem>
            </template>
          </List>
          <Empty v-else-if="!messagesError" description="暂无沟通记录" />
          <div v-if="actions.message" class="message-editor">
            <Input.TextArea v-model:value="draftMessage" :maxlength="1000" show-count placeholder="仅沟通当前询价与报价，不要发送账号密码等敏感信息" />
            <Button type="primary" :loading="sending" :disabled="!draftMessage.trim()" @click="sendMessage">发送</Button>
          </div>
          <Alert v-else type="warning" show-icon message="当前报价状态或账号权限不允许继续沟通。" />
        </section>
      </template>
    </Spin>
  </Drawer>
</template>

<style scoped>
.quote-table { border: 1px solid var(--ant-color-border, #d9d9d9); border-radius: 6px; overflow: hidden; }
.quote-row { display: grid; grid-template-columns: minmax(220px, 1.2fr) minmax(280px, 1.5fr) 130px 150px; gap: 12px; align-items: center; padding: 12px; border-top: 1px solid #f0f0f0; }
.quote-row:first-child { border-top: 0; }
.quote-row p { margin: 4px 0 0; color: #64748b; font-size: 12px; }
.quote-head { background: #fafafa; color: #475569; font-weight: 600; }
.quote-form { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; padding: 18px; border: 1px solid #f0f0f0; border-radius: 6px; }
.quote-form label { display: grid; gap: 6px; color: #475569; }
.quote-form :deep(.ant-picker), .quote-form :deep(.ant-input-number) { width: 100%; }
.quote-form .remark, .quote-form .total, .quote-form .commands { grid-column: 1 / -1; }
.quote-form .total { margin: 0; text-align: right; font-size: 16px; }
.quote-form .total strong { color: #cf3c23; font-size: 24px; }
.quote-form .commands { justify-content: flex-end; }
.message-line { display: grid; width: 100%; grid-template-columns: auto 1fr auto; gap: 10px; align-items: start; }
.message-line small { color: #94a3b8; }
.message-editor { display: grid; grid-template-columns: 1fr auto; gap: 12px; margin-top: 14px; align-items: end; }
@media (max-width: 900px) {
  .quote-row { grid-template-columns: 1fr 1fr; }
  .quote-form { grid-template-columns: 1fr; }
}
</style>
