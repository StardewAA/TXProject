<script setup lang="ts">
import { computed, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

/**
 * AI 对话 - 知识引用组件
 *
 * 交互逻辑：
 * 1. 默认只显示「文档分类标签按钮」，如：生产工单(2条)、物料产品(1条)
 * 2. 点击标签 → 在按钮下方展开折叠面板，展示该分类下全部完整原始知识详情
 * 3. 再次点击同一标签 → 收起面板
 * 4. 多个标签可同时展开，互不影响（独立记录展开状态）
 * 5. 无详情数据时按钮置灰不可点击；面板内容过长时内部滚动
 *
 * 数据来源：AI 对话接口返回的消息元数据 item.segments
 * （每条包含 id / documentId / documentName / content 完整原始内容），
 * 组件内部按 documentName 聚合成分类标签，无需后端改动。
 */
interface KnowledgeSegment {
  /** 分段编号 */
  id: number;
  /** 分段完整原始内容 */
  content: string;
  /** 所属文档（分类）编号 */
  documentId: number;
  /** 所属文档（分类）名称，如：生产工单 / 物料产品 / 生产任务 / 知识库说明 */
  documentName: string;
}

/** 解析出的结构化字段 */
interface Field {
  label: string;
  value: string;
}

/** 聚合后的文档分类 */
interface DocumentGroup {
  icon: string;
  id: number;
  segments: KnowledgeSegment[];
  title: string;
}

const props = defineProps<{
  segments?: KnowledgeSegment[];
}>();

/** 当前展开的分类面板（documentId 列表），多个可同时展开 */
const expandedIds = ref<number[]>([]);

/**
 * 文档分类图标映射：
 * 按 documentName 匹配对应的业务图标，未匹配到使用默认文件图标
 */
const documentIconMap: Record<string, string> = {
  知识库说明: 'lucide:info',
  生产工单: 'lucide:factory',
  生产任务: 'lucide:clipboard-list',
  生产报工: 'lucide:hard-hat',
  物料产品: 'lucide:package',
  车间产线: 'lucide:warehouse',
  客户供应商: 'lucide:users',
  质量检验: 'lucide:badge-check',
  设备管理: 'lucide:wrench',
  排班班组: 'lucide:calendar-clock',
  仓储库存: 'lucide:boxes',
};

/** 按文档（分类）聚合 segments，作为标签按钮的数据源 */
const documentList = computed<DocumentGroup[]>(() => {
  if (!props.segments || props.segments.length === 0) {
    return [];
  }

  const docMap = new Map<number, DocumentGroup>();
  props.segments.forEach((segment) => {
    let doc = docMap.get(segment.documentId);
    if (!doc) {
      doc = {
        id: segment.documentId,
        title: segment.documentName || '未知文档',
        icon: documentIconMap[segment.documentName || ''] || 'lucide:file-text',
        segments: [],
      };
      docMap.set(segment.documentId, doc);
    }
    doc.segments.push(segment);
  });
  return Array.from(docMap.values());
});

/** 判断分类面板是否展开 */
function isExpanded(docId: number): boolean {
  return expandedIds.value.indexOf(docId) !== -1;
}

/** 切换分类面板展开/收起状态（同一分类再次点击即收起） */
function togglePanel(docId: number) {
  if (isExpanded(docId)) {
    expandedIds.value = expandedIds.value.filter((id) => id !== docId);
  } else {
    expandedIds.value = [...expandedIds.value, docId];
  }
}

/** 当前展开的文档列表（按展开顺序渲染面板） */
const expandedDocuments = computed<DocumentGroup[]>(() =>
  documentList.value.filter((doc) => isExpanded(doc.id)),
);

/**
 * 将分段原始内容解析为「字段 - 值」结构化键值对，用于表格化展示。
 *
 * 知识分段的原始内容由后端知识库生成，格式形如：
 *   "生产工单【MO202503120008】博世螺丝刀：产品【保温管】，计划数量 10000，
 *    开工时间 2025-03-15 08:00:00，状态【已确认】"
 *
 * 解析规则（逐级尝试，均失败则跳过该片段）：
 * 1. 开头主实体："分类【编号】名称" → 两行字段
 * 2. "字段【值】" 模式，如：状态【已确认】
 * 3. "字段：值" 模式
 * 4. "字段 值" 模式（中文逗号分隔后），如：计划数量 10000
 */
function parseFields(content: string): Field[] {
  const fields: Field[] = [];
  if (!content) {
    return fields;
  }

  // 1. 解析开头主实体，如 "生产工单【MO...】博世螺丝刀..."
  const head = content.match(/^([^\u3010:：]{1,20})【([^】]+)】([^:：]*)/);
  let body = content;
  if (head) {
    fields.push({ label: head[1].trim(), value: head[2].trim() });
    const name = head[3].trim().replace(/^[，,、:：]/, '');
    if (name && name !== '：' && name !== ':') {
      fields.push({ label: '名称', value: name });
    }
    body = content.slice(head[0].length);
  }

  // 2. 剩余部分按中文逗号/分号切分，逐段识别键值对
  const parts = body.split(/[，,；;、]/);
  for (const raw of parts) {
    const s = raw.trim();
    if (!s) {
      continue;
    }
    let m = s.match(/^(.{1,14}?)【([^】]+)】$/);
    if (m) {
      fields.push({ label: m[1].trim(), value: m[2].trim() });
      continue;
    }
    m = s.match(/^(.{1,14}?)(?:：|:)\s*(.+)$/);
    if (m) {
      fields.push({ label: m[1].trim(), value: m[2].trim() });
      continue;
    }
    m = s.match(/^(.{2,10}?)\s+(\S.*)$/);
    if (m) {
      fields.push({ label: m[1].trim(), value: m[2].trim() });
    }
  }
  return fields;
}

/** 判断字段值是否为「时间/日期」，用于对齐展示（可按需扩展） */
function isDateTime(value: string): boolean {
  return /^\d{4}-\d{2}-\d{2}/.test(value);
}
</script>

<template>
  <!-- 知识引用区域：仅当存在知识分段时渲染 -->
  <div
    v-if="segments && segments.length > 0"
    class="knowledge-ref mt-2 rounded-lg bg-gray-50 p-2 dark:bg-gray-800/60"
  >
    <!-- 区域标题 -->
    <div class="mb-2 flex items-center text-sm text-gray-400">
      <IconifyIcon icon="lucide:file-text" class="mr-1" />
      知识引用
    </div>

    <!-- 分类标签按钮行：默认只显示按钮，不显示明细 -->
    <div class="flex flex-wrap gap-2">
      <button
        v-for="doc in documentList"
        :key="doc.id"
        :disabled="doc.segments.length === 0"
        :class="[
          'flex items-center gap-1.5 rounded-lg border px-3 py-1.5 text-sm transition-all',
          'border-gray-200 bg-card hover:bg-blue-50 dark:border-gray-600 dark:hover:bg-blue-500/10',
          isExpanded(doc.id)
            ? 'border-blue-400 bg-blue-50 text-blue-600 dark:border-blue-500 dark:bg-blue-500/15 dark:text-blue-400'
            : 'text-gray-600 dark:text-gray-300',
          doc.segments.length === 0
            ? 'cursor-not-allowed opacity-50' // 边界：无详情数据时置灰不可点击
            : 'cursor-pointer',
        ]"
        @click="doc.segments.length > 0 && togglePanel(doc.id)"
      >
        <IconifyIcon :icon="doc.icon" class="text-base" />
        <span>{{ doc.title }}</span>
        <span class="text-xs text-gray-400">
          （{{ doc.segments.length }} 条）
        </span>
        <!-- 展开/收起箭头指示 -->
        <IconifyIcon
          :icon="isExpanded(doc.id) ? 'lucide:chevron-up' : 'lucide:chevron-down'"
          class="text-xs"
        />
      </button>
    </div>

    <!-- 折叠详情面板：每个已展开的分类渲染一个面板，互相独立 -->
    <div
      v-for="doc in expandedDocuments"
      :key="'panel-' + doc.id"
      class="mt-2 overflow-hidden rounded-lg border border-gray-200 dark:border-gray-600"
    >
      <!-- 面板标题栏 -->
      <div
        class="flex items-center justify-between border-b border-gray-200 bg-white px-3 py-2 dark:border-gray-600 dark:bg-gray-700"
      >
        <div
          class="flex items-center gap-1.5 text-sm font-medium text-gray-700 dark:text-gray-200"
        >
          <IconifyIcon :icon="doc.icon" />
          {{ doc.title }} · 知识详情
        </div>
        <button
          class="flex items-center text-xs text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
          @click="togglePanel(doc.id)"
        >
          <IconifyIcon icon="lucide:x" class="mr-0.5" />
          收起
        </button>
      </div>

      <!-- 面板内容：浅色背景、内部滚动（内容过长时最大高度约视口一半） -->
      <div
        class="knowledge-panel max-h-[55vh] overflow-y-auto bg-white p-3 dark:bg-gray-900"
      >
        <div
          v-for="(segment, idx) in doc.segments"
          :key="segment.id"
          class="mb-3 rounded-lg border border-gray-100 bg-gray-50/60 p-3 last:mb-0 dark:border-gray-700 dark:bg-gray-800/50"
        >
          <!-- 条目头：序号 + 分段编号 -->
          <div class="mb-2 flex items-center text-xs text-gray-400">
            <span class="rounded-sm bg-gray-200 px-1.5 py-0.5 dark:bg-gray-700">
              第 {{ idx + 1 }} 条
            </span>
            <span class="ml-2">分段编号：{{ segment.id }}</span>
          </div>

          <!-- 结构化字段网格：解析「字段 - 值」键值对，两列排版 -->
          <div
            v-if="parseFields(segment.content).length > 0"
            class="grid grid-cols-1 gap-x-4 gap-y-1.5 md:grid-cols-2"
          >
            <div
              v-for="(field, fIdx) in parseFields(segment.content)"
              :key="fIdx"
              class="flex min-w-0 items-baseline gap-1 text-sm"
            >
              <span class="shrink-0 text-gray-400">{{ field.label }}：</span>
              <span
                :class="[
                  'min-w-0 break-all font-medium',
                  isDateTime(field.value)
                    ? 'text-gray-500 dark:text-gray-400'
                    : 'text-gray-700 dark:text-gray-200',
                ]"
              >
                {{ field.value }}
              </span>
            </div>
          </div>

          <!-- 原始内容兜底：无法结构化解析时，直接完整展示原文 -->
          <div
            v-else
            class="whitespace-pre-wrap break-all text-sm leading-relaxed text-gray-600 dark:text-gray-300"
          >
            {{ segment.content }}
          </div>

          <!-- 完整原始内容（始终展示，保证“全部完整原始知识详情”） -->
          <div
            class="mt-2 border-t border-dashed border-gray-200 pt-2 dark:border-gray-700"
          >
            <div class="mb-1 text-xs text-gray-400">原始内容</div>
            <div
              class="max-h-32 overflow-y-auto whitespace-pre-wrap break-all rounded bg-white p-2 text-xs leading-relaxed text-gray-500 dark:bg-gray-900 dark:text-gray-400"
            >
              {{ segment.content }}
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 面板内部滚动条美化（深浅主题自适应） */
.knowledge-panel::-webkit-scrollbar {
  width: 6px;
}

.knowledge-panel::-webkit-scrollbar-thumb {
  border-radius: 3px;
  background: rgb(0 0 0 / 15%);
}

.dark .knowledge-panel::-webkit-scrollbar-thumb {
  background: rgb(255 255 255 / 20%);
}
</style>
