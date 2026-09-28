<!--
  入口/栏目配置编辑器（entry_urls_json 的结构化编辑，替代平铺 JSON 文本域）：
  每栏目标签行卡片，字段列展示：栏目名 / 列表解析器 / 最大页数 / 正文解析器 / 回溯窗口 / 列表 URL；
  六个标准字段排在同一行（栏目名·列表解析器·最大页数·正文解析器·增量回溯天数·首跑全量回溯天数），
  其后是单独成行的 列表 URL 与 补充参数 JSON —— 同排布局要吃掉约 1070px 可用宽度，
  依赖承载它的编辑弹窗宽度（source/modules/form.vue 的 w-[1180px]），缩窄弹窗会回落成 3 列 / 2 列。
  额外字段（如 QYZB 的 siteId/categoryId/pageSize 等平台参数）以「补充参数 JSON」行内保留，
  保存时与标准字段合并写回原串 —— 不裁剪任何未知字段（04-契约 §3 栏目对象可带平台参数）。
  绑定：v-model 传 entry_urls_json 原文（字符串）；组件内部解析为数组、编辑后序列化回写。
-->
<script lang="ts" setup>
import type { EntryUrlItem } from '../../shared';

import { ref, watch } from 'vue';

import { Button, Input, InputNumber } from 'ant-design-vue';

import { formatEntryUrls, parseEntryUrls } from '../../shared';

const props = defineProps<{ modelValue?: string }>();
const emit = defineEmits<{ 'update:modelValue': [value: string] }>();

/** 标准字段（其余一律视作平台侧补充参数，原样保留） */
const STANDARD_KEYS = ['name', 'parser', 'listUrl', 'url', 'maxPages', 'detailParser', 'lookbackDays', 'fullLookbackDays'];

const rows = ref<EntryUrlItem[]>(parseEntryUrls(props.modelValue));
/** 每行补充参数的可编辑 JSON 文本（与 rows 同步） */
const extraTexts = ref<string[]>(rows.value.map((r) => extraJsonOf(r)));

watch(
  () => props.modelValue,
  (v) => {
    rows.value = parseEntryUrls(v);
    extraTexts.value = rows.value.map((r) => extraJsonOf(r));
  },
);

/** 提取非标准字段 → 紧凑 JSON（空则返回 ''） */
function extraJsonOf(item: EntryUrlItem): string {
  const extra: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(item)) {
    if (!STANDARD_KEYS.includes(k) && v !== undefined && v !== null && v !== '') {
      extra[k] = v;
    }
  }
  return Object.keys(extra).length ? JSON.stringify(extra) : '';
}

function emitChange() {
  emit('update:modelValue', formatEntryUrls(rows.value));
}

function addRow() {
  rows.value.push({} as EntryUrlItem);
  extraTexts.value.push('');
}

function removeRow(i: number) {
  rows.value.splice(i, 1);
  extraTexts.value.splice(i, 1);
  emitChange();
}

/** 补充参数 JSON 输入改动 → 合并回该行对象（非法 JSON 保留文本不落行，待修正） */
function applyExtra(i: number, text: string) {
  extraTexts.value[i] = text;
  const item = rows.value[i];
  if (!item) return;
  // 先清掉既有非标准字段，再按新 JSON 合并
  for (const k of Object.keys(item)) {
    if (!STANDARD_KEYS.includes(k)) delete item[k];
  }
  if (!text || !text.trim()) {
    emitChange();
    return;
  }
  try {
    const parsed = JSON.parse(text) as Record<string, unknown>;
    for (const [k, v] of Object.entries(parsed)) {
      if (!STANDARD_KEYS.includes(k)) item[k] = v;
    }
  } catch {
    /* 非法 JSON 不改行对象，等保存校验兜底提示 */
  }
  emitChange();
}

/** 行内标准字段变化后同步 extraTexts（非标准字段可能来自初始数据） */
function touchExtra(i: number) {
  const item = rows.value[i];
  if (item) extraTexts.value[i] = extraJsonOf(item);
}
</script>

<template>
  <div class="eu-wrap">
    <div v-for="(r, i) in rows" :key="i" class="eu-card">
      <div class="eu-card-h">
        <span class="eu-idx">栏目 {{ i + 1 }}</span>
        <span v-if="extraTexts[i]" class="eu-flag" title="含平台侧补充参数（siteId/categoryId 等），保存时原样保留">补充参数</span>
        <Button type="text" danger size="small" @click="removeRow(i)">删除</Button>
      </div>
      <!-- 六个标准字段同排（一行），替代原「前四个一行 + 回溯两项单独一行」的两段式布局 -->
      <div class="eu-grid eu-grid-6">
        <div class="eu-f">
          <div class="eu-l">栏目名称</div>
          <Input v-model:value="(r as any).name" placeholder="如：招标公告" @change="touchExtra(i)" />
        </div>
        <div class="eu-f">
          <div class="eu-l">列表解析器</div>
          <Input v-model:value="(r as any).parser" placeholder="如 GX_E_BIDDING" @change="touchExtra(i)" />
        </div>
        <div class="eu-f">
          <div class="eu-l">最大页数</div>
          <InputNumber v-model:value="(r as any).maxPages" :min="1" class="w-full" @change="touchExtra(i)" />
        </div>
        <div class="eu-f">
          <div class="eu-l">正文解析器</div>
          <Input v-model:value="(r as any).detailParser" placeholder="如 PDF_AI / HTML_AI" @change="touchExtra(i)" />
        </div>
        <div class="eu-f">
          <div class="eu-l">增量回溯天数</div>
          <InputNumber v-model:value="(r as any).lookbackDays" :min="0" class="w-full" placeholder="缺省按全局" @change="touchExtra(i)" />
        </div>
        <div class="eu-f">
          <div class="eu-l">首跑全量回溯天数</div>
          <InputNumber v-model:value="(r as any).fullLookbackDays" :min="0" class="w-full" placeholder="缺省回退全局 60" @change="touchExtra(i)" />
        </div>
      </div>
      <div class="eu-grid eu-grid-1">
        <div class="eu-f">
          <div class="eu-l">列表 URL（listUrl）</div>
          <Input v-model:value="(r as any).listUrl" placeholder="https://...；参数化栏目（siteId/categoryId 驱动）可留空，填下方补充参数" @change="touchExtra(i)" />
        </div>
      </div>
      <div class="eu-grid eu-grid-1">
        <div class="eu-f">
          <div class="eu-l">补充参数（JSON，可选）</div>
          <Input
            :value="extraTexts[i]"
            placeholder='平台侧参数，如 {"siteId":"725","categoryId":"203","pageSize":10}'
            @update:value="applyExtra(i, $event)"
          />
        </div>
      </div>
    </div>
    <Button type="dashed" block class="eu-add" @click="addRow">+ 添加栏目</Button>
  </div>
</template>

<style scoped>
.eu-wrap { display: flex; flex-direction: column; gap: 10px; }
.eu-card { border: 1px solid hsl(var(--border)); border-radius: var(--radius); padding: 10px 12px; background: hsl(var(--background)); }
.eu-card-h { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.eu-idx { font-size: 13px; font-weight: 700; color: var(--ind-ink-text, #1f2937); }
.eu-flag { font-size: 11px; color: #d46b08; border: 1px solid #d46b08; border-radius: 99px; padding: 0 6px; line-height: 16px; }
.eu-grid { display: grid; gap: 8px; margin-top: 6px; }
/* 六个标准字段同排：可用宽度取决于编辑弹窗宽度——form.vue 里是 w-[1180px]，
   去掉内容区/卡片内边距与 5 个列间距后每列约 179px；弹窗贴着视口上限（max-w-[calc(100vw-20px)]）
   在窄屏被压小时，按 3 列 / 2 列回落，避免把输入框挤到无法读写。 */
.eu-grid-6 { grid-template-columns: repeat(6, minmax(0, 1fr)); }
@media (max-width: 1100px) { .eu-grid-6 { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 700px) { .eu-grid-6 { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
.eu-grid-1 { grid-template-columns: 1fr; }
.eu-l { font-size: 12px; color: var(--ind-weak-text, #6b7280); margin-bottom: 2px; }
.eu-add { margin-top: 2px; }
</style>
