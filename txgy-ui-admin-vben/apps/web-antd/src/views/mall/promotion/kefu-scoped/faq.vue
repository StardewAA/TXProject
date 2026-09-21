<script setup lang="ts">
import type { FaqDocument, FaqEntry } from '#/api/mall/promotion/kefu-scoped';
import { computed, ref, watch } from 'vue';
import { Page } from '@vben/common-ui';
import { Alert, Button, Input, InputNumber, Popconfirm, Select, Switch, Tag } from 'ant-design-vue';
import { getScopedFaq, publishScopedFaq, saveScopedFaq } from '#/api/mall/promotion/kefu-scoped';
import { FAQ_PERMISSION, kefuError, sameScope, validateFaq, validVersion } from './model';
import { useScopedView } from './use-scoped-view';

defineOptions({ name: 'MallScopedKefuFaq' });
type EditableFaq = FaqEntry & { keywordText: string };
const document = ref<FaqDocument>(), entries = ref<EditableFaq[]>([]), baseline = ref('');
const loading = ref(false), busy = ref(false), loaded = ref(false), error = ref(''), notice = ref(''), reason = ref('');
function clear() { document.value = undefined; entries.value = []; baseline.value = ''; loading.value = false; busy.value = false; loaded.value = false; error.value = ''; notice.value = ''; reason.value = ''; }
const context = useScopedView(FAQ_PERMISSION, clear);
const listFence = context.fence(), writeFence = context.fence();
const scopeOptions = [{ label: '平台客服（仅本租户）', value: 'PLATFORM' }, { label: '店铺客服', value: 'SHOP' }];
const normalized = computed<FaqEntry[]>(() => entries.value.map((entry) => ({
  id: entry.id.trim(), question: entry.question.trim(), answer: entry.answer.trim(), enabled: entry.enabled,
  keywords: entry.keywordText.split(/[,，\n]/).map((word) => word.trim()).filter(Boolean),
})));
const dirty = computed(() => JSON.stringify(normalized.value) !== baseline.value);
const formError = computed(() => validateFaq(normalized.value));
function apply(data: FaqDocument) {
  if (!context.query.value || !sameScope(data, context.query.value) || !validVersion(data.version) || !Array.isArray(data.entries) || !Array.isArray(data.publishedEntries)) throw new Error('Invalid document');
  document.value = data;
  entries.value = data.entries.map((entry) => ({ ...entry, keywords: [...entry.keywords], keywordText: entry.keywords.join('，') }));
  baseline.value = JSON.stringify(normalized.value); loaded.value = true;
}
async function load() {
  if (!context.ready.value || !context.query.value || busy.value) return;
  const request = listFence.begin(); loading.value = true; loaded.value = false; error.value = ''; notice.value = '';
  try { const data = await getScopedFaq({ ...context.query.value }, request.signal); if (request.current()) apply(data); }
  catch (cause) { if (request.current()) { document.value = undefined; entries.value = []; error.value = kefuError(cause); } }
  finally { if (request.current()) loading.value = false; }
}
function add() {
  if (!loaded.value || busy.value || entries.value.length >= 30) return;
  entries.value.push({ id: `faq_${crypto.randomUUID().slice(0, 8)}`, question: '', answer: '', keywords: [], keywordText: '', enabled: true });
}
async function save() {
  if (!context.ready.value || !context.query.value || !document.value || !loaded.value || busy.value || formError.value) return;
  const request = writeFence.begin(); busy.value = true; error.value = ''; notice.value = '';
  try {
    const data = await saveScopedFaq({ ...context.query.value, version: document.value.version, entries: normalized.value.map((entry) => ({ ...entry, keywords: [...entry.keywords] })) }, request.signal);
    if (!request.current()) return;
    apply(data); notice.value = '草稿已保存，尚未对顾客发布。请核对答案后单独发布。';
  } catch (cause) { if (request.current()) error.value = kefuError(cause); }
  finally { if (request.current()) busy.value = false; }
}
async function publish() {
  if (!context.ready.value || !context.query.value || !document.value || !loaded.value || busy.value || dirty.value || !reason.value.trim() || reason.value.trim().length > 500 || formError.value) return;
  const request = writeFence.begin(); busy.value = true; error.value = ''; notice.value = '';
  try {
    const data = await publishScopedFaq({ ...context.query.value, version: document.value.version, reason: reason.value.trim() }, request.signal);
    if (!request.current()) return;
    apply(data); reason.value = ''; notice.value = '固定 FAQ 已发布，仅作用于当前租户的所选客服范围。';
  } catch (cause) { if (request.current()) error.value = kefuError(cause); }
  finally { if (request.current()) busy.value = false; }
}
watch(context.ready, (ready) => { if (ready) void load(); });
</script>

<template>
  <Page title="店铺范围客服 · 固定 FAQ" description="草稿保存与对外发布分开；已发布 FAQ 优先回答，未命中时由 AI 客服补充回答。">
    <section class="scoped-panel bg-card">
      <Alert type="info" show-icon message="只对所选平台/店铺范围发布。不要在问答中放入账号、订单隐私、密钥或未经核实的价格与配送承诺。" />
      <div class="scoped-toolbar">
        <strong>{{ context.label.value }}</strong>
        <Select v-model:value="context.scope.value" :options="scopeOptions" :disabled="busy" style="width: 220px" aria-label="客服范围" />
        <InputNumber v-if="context.scope.value === 'SHOP'" v-model:value="context.shopId.value" :min="1" :precision="0" :disabled="busy" placeholder="店铺编号" aria-label="店铺编号" />
        <Button :loading="context.capabilityLoading.value" @click="context.refreshCapability">核实开通状态</Button>
      </div>
      <Alert v-if="context.blocked.value" type="warning" show-icon :message="context.blocked.value" />
      <template v-if="context.ready.value">
        <div class="scoped-toolbar">
          <Popconfirm v-if="dirty && loaded" title="刷新将丢弃本地未保存草稿，是否继续？" @confirm="load"><Button :disabled="busy">重新读取 FAQ</Button></Popconfirm>
          <Button v-else :loading="loading" :disabled="busy" @click="load">读取 FAQ</Button>
          <Button :disabled="!loaded || busy || entries.length >= 30" @click="add">添加问题</Button>
          <Tag>草稿版本 {{ document?.version ?? '—' }}</Tag><Tag>已发布版本 {{ document?.publishedVersion ?? '—' }}</Tag>
          <Tag v-if="dirty && loaded" color="orange">有未保存修改</Tag>
        </div>
        <Alert v-if="error" type="error" show-icon :message="error" />
        <Alert v-if="notice" type="success" show-icon :message="notice" />
        <p v-if="!loaded" class="muted">{{ loading ? '正在读取 FAQ…' : '尚未成功读取，不会按空内容覆盖已有 FAQ。' }}</p>
        <template v-else>
          <p v-if="!entries.length" class="muted">此范围暂无草稿问题，可添加后保存并发布。</p>
          <article v-for="(entry, index) in entries" :key="index" class="faq-editor">
            <div class="scoped-toolbar"><h3>问题 {{ index + 1 }}</h3><label>启用 <Switch v-model:checked="entry.enabled" :disabled="busy" /></label><Popconfirm title="确认从草稿删除？保存并发布前不会影响当前顾客答案。" @confirm="entries.splice(index, 1)"><Button size="small" danger :disabled="busy">删除草稿项</Button></Popconfirm></div>
            <label>唯一标识<Input v-model:value="entry.id" :maxlength="40" :disabled="busy" placeholder="字母、数字、下划线或连字符，最多 40 位" /></label>
            <label>问题<Input v-model:value="entry.question" :maxlength="100" :disabled="busy" placeholder="最多 100 字" /></label>
            <label>关键词<Input v-model:value="entry.keywordText" :disabled="busy" placeholder="逗号分隔，最多 8 个，每个 2–30 字；可留空" /></label>
            <label>固定答案<Input.TextArea v-model:value="entry.answer" :maxlength="1000" :rows="4" :disabled="busy" placeholder="最多 1000 字，只展示纯文本" /></label>
          </article>
          <Alert v-if="formError" type="warning" show-icon :message="formError" />
          <div class="scoped-toolbar"><Button type="primary" :loading="busy" :disabled="!!formError || !dirty" @click="save">保存草稿</Button><span class="muted">保存不等于发布，不覆盖其他版本。</span></div>
          <section class="faq-editor">
            <h3>发布当前已保存草稿</h3>
            <label>发布原因（必填）<Input.TextArea v-model:value="reason" :maxlength="500" :rows="2" :disabled="busy" placeholder="记录本次更新原因" /></label>
            <p class="muted">{{ dirty ? '请先保存草稿，再确认发布。' : `发布后启用 ${normalized.filter((entry) => entry.enabled).length} 条固定问答；未启用问题不会参与匹配。` }}</p>
            <Popconfirm title="确认将此范围已保存的草稿发布给顾客？" :disabled="busy || dirty || !!formError || !reason.trim()" @confirm="publish"><Button :disabled="busy || dirty || !!formError || !reason.trim()">确认发布</Button></Popconfirm>
          </section>
          <details class="faq-editor"><summary>当前已发布问答（{{ document?.publishedEntries.length || 0 }}）</summary><article v-for="entry in document?.publishedEntries" :key="entry.id"><strong>{{ entry.question }}</strong><p class="answer">{{ entry.answer }}</p></article></details>
        </template>
      </template>
    </section>
  </Page>
</template>

<style scoped>
.scoped-panel{padding:20px;display:grid;gap:16px;border-radius:8px}.scoped-toolbar{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.faq-editor{display:grid;gap:12px;border:1px solid #8884;border-radius:8px;padding:18px}.faq-editor h3{font-size:16px;font-weight:600}.faq-editor label{display:grid;gap:6px}.muted{color:#7b8493;font-size:13px}.answer{white-space:pre-wrap;overflow-wrap:anywhere;margin-top:8px}
</style>
