<script lang="ts" setup>
/**
 * 【XX 单据预览】通用弹窗（Vben Admin 组件，支持在线填写 + 导出）
 *
 * 【新增模块：空白单据预览（可填写）】
 * 1. Vben Admin 的 useVbenModal：右上角自带关闭 ×；底部「关闭」「下载 Word 文件」
 * 2. 左右灰色侧边 + 中间 A4 白色单据主体，标题蓝色加粗，正文黑色
 * 3. 所有字段/表格单元格都是【可输入的下划线框】：不填 = 空白模板；填写后导出的 Word 同步带上内容
 * 4. 预览结构与 exportBlankDoc 导出的 Word 一一对应；样式做了 @media print 适配
 */
import { reactive, ref } from 'vue';

import { ElMessage } from 'element-plus';
import { useVbenModal } from '@vben/common-ui';

import {
  exportBlankDoc,
  type BlankDocFormState,
  type BlankDocSchema,
} from './blankDocSchema';

/** 打开时可选传入的预填充状态（导入解析场景使用） */
export interface DocPreviewOpenOptions {
  schema: BlankDocSchema;
  state?: BlankDocFormState;
}

/** 手动保存兜底链接：万一浏览器/下载器没开始下载，用户点这个也能存文件 */
const fallbackHref = ref('');
const fallbackName = ref('');

/**
 * 【修复下载】把 Blob 转 DataURL 后用 <a download> 触发浏览器原生下载。
 * 原先用 file-saver 的 blob: 链接会被迅雷等外部下载器劫持，
 * 而它们无法保存 blob 任务 → 表面"下载完成"实际找不到文件；
 * DataURL 属于页面内联数据，不经过下载器，直接落盘到浏览器默认下载目录。
 * 同时保留一个手动保存链接作为兜底入口。
 */
function downloadBlob(blob: Blob, fileName: string) {
  const reader = new FileReader();
  reader.onload = () => {
    const url = reader.result as string;
    // 兜底链接：显示在弹窗内，右键"另存为"也能拿到文件
    fallbackHref.value = url;
    fallbackName.value = fileName;
    // 自动触发浏览器下载
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    a.remove();
    ElMessage.success(`已开始下载：${fileName}（若未弹出请用弹窗内的手动保存链接）`);
  };
  reader.onerror = () => {
    ElMessage.error('文件生成失败，请重试。');
  };
  reader.readAsDataURL(blob);
}

/** 当前预览的单据模板 */
const schema = ref<BlankDocSchema | null>(null);
/** 用户填写内容（与 Schema 结构对应） */
const form = reactive<BlankDocFormState>({
  subtitle: [],
  fields: {},
  tables: {},
});

const [Modal, modalApi] = useVbenModal({
  centered: true,
  draggable: false,
  footer: true,
  confirmText: '下载 Word 文件',
  cancelText: '关闭',
  showCancelButton: true,
  showConfirmButton: true,
  // 确认按钮即「下载 Word 文件」：导出时把已填写内容一并写入 Word
  async onConfirm() {
    await handleDownload();
  },
});

/** 下载 Word：带上当前填写的内容（未填写的字段仍为空白下划线） */
async function handleDownload() {
  if (!schema.value) return;
  const blob = await exportBlankDoc(schema.value, {
    subtitle: [...form.subtitle],
    fields: { ...form.fields },
    tables: JSON.parse(JSON.stringify(form.tables)) as Record<
      string,
      string[][]
    >,
  });
  downloadBlob(blob, schema.value.fileName);
}

/** 打开预览：按 Schema 重置；传入 state 时用解析出的数据预填充（【新增：导入解析】） */
function open(next: BlankDocSchema, state?: BlankDocFormState) {
  schema.value = next;
  // 副标题：优先用解析值，缺失留空
  form.subtitle = next.subtitleFields.map(
    (label, i) => state?.subtitle?.[i] ?? state?.fields?.[label] ?? '',
  );
  // 基础信息：解析值优先，缺失留空（绝不编造）
  form.fields = {};
  for (const label of next.fields) {
    form.fields[label] = state?.fields?.[label] ?? '';
  }
  // 表格：数据行以解析结果为准（解析出行数多于预留行时自动扩展），不足补空行到预留行数
  form.tables = {};
  for (const table of next.tables) {
    const parsed = state?.tables?.[table.title] ?? [];
    const width = table.headers.length;
    const normalized = parsed
      .filter((r) => Array.isArray(r))
      .map((r) => table.headers.map((_, ci) => r[ci] ?? ''));
    while (normalized.length < table.blankRows) {
      normalized.push(table.headers.map(() => ''));
    }
    form.tables[table.title] = normalized.map((r) => r.slice(0, width));
  }
  fallbackHref.value = '';
  fallbackName.value = '';
  modalApi.setState({ title: `${next.title}预览`, loading: false });
  modalApi.open();
}

defineExpose({ open });
</script>

<template>
  <Modal class="mes-doc-modal">
    <!-- 手动保存兜底：迅雷等下载器劫持导致未落盘时，用户从这里也能拿到文件 -->
    <div v-if="fallbackHref" class="mes-doc-dialog__fallback">
      若点击下载后仍找不到文件（被迅雷等下载器接管），
      <a :href="fallbackHref" :download="fallbackName">
        请点此手动保存「{{ fallbackName }}」
      </a>
      （或右键链接 → 目标另存为）
    </div>

    <!-- 灰色侧边 + 居中 A4 白纸 -->
    <div class="doc-preview">
      <div class="doc-page">
        <!-- 大标题：蓝色加粗 -->
        <h1 class="doc-page__title">{{ schema?.title }}</h1>

        <!-- 副标题：可输入（不填则导出为空白下划线） -->
        <div class="doc-page__subtitle">
          <span
            v-for="(label, si) in schema?.subtitleFields ?? []"
            :key="label"
          >
            {{ label }}：
            <input
              v-model="form.subtitle[si]"
              class="doc-input doc-input--sm"
              type="text"
            />
          </span>
        </div>

        <!-- 基础信息：标签 + 可输入下划线 -->
        <section class="doc-page__fields">
          <p v-for="label in schema?.fields ?? []" :key="label" class="doc-field">
            <span class="doc-field__label">{{ label }}：</span>
            <input
              v-model="form.fields[label]"
              class="doc-input doc-input--lg"
              type="text"
            />
          </p>
        </section>

        <!-- 业务表格：固定表头 + 可输入空行 -->
        <section v-for="table in schema?.tables ?? []" :key="table.title">
          <h2 class="doc-page__section-title">{{ table.title }}</h2>
          <table class="doc-table">
            <thead>
              <tr>
                <th v-for="h in table.headers" :key="h">{{ h }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, ri) in form.tables[table.title]" :key="ri">
                <td v-for="(h, ci) in table.headers" :key="`${ri}-${ci}`">
                  <input
                    v-model="row[ci]"
                    class="doc-cell-input"
                    type="text"
                  />
                </td>
              </tr>
            </tbody>
          </table>
        </section>
      </div>
    </div>
  </Modal>
</template>

<style scoped>
/* 手动保存兜底条：黄色提示，醒目 */
.mes-doc-dialog__fallback {
  margin-bottom: 10px;
  padding: 8px 12px;
  font-size: 13px;
  color: #8a6414;
  background: #fdf6e3;
  border: 1px solid #f0dcb4;
  border-radius: 4px;
}

.mes-doc-dialog__fallback a {
  color: #1677ff;
  font-weight: 600;
}

/* 灰色侧边：弹窗内容区铺浅灰，白纸居中 */
.doc-preview {
  display: flex;
  justify-content: center;
  padding: 16px;
  background: #f0f2f5;
}

/* A4 白纸：宽 794px ≈ 210mm @96dpi */
.doc-page {
  width: 100%;
  max-width: 794px;
  min-height: 1000px;
  padding: 48px 56px;
  color: #000;
  background: #fff;
  box-shadow: 0 2px 12px rgb(0 0 0 / 12%);
}

/* 大标题：蓝色加粗居中 */
.doc-page__title {
  margin: 0 0 8px;
  font-size: 28px;
  font-weight: 700;
  color: #1677ff;
  text-align: center;
}

/* 副标题行 */
.doc-page__subtitle {
  display: flex;
  gap: 48px;
  justify-content: center;
  margin-bottom: 32px;
  font-size: 14px;
  color: #000;
}

/* 基础信息 */
.doc-page__fields {
  margin-bottom: 8px;
}

.doc-field {
  display: flex;
  align-items: flex-end;
  margin: 0 0 14px;
  font-size: 14px;
  line-height: 1.8;
}

.doc-field__label {
  flex-shrink: 0;
  font-weight: 600;
  color: #000;
}

/* 可输入的下划线框：无外框只留下划线，与空白模板视觉一致 */
.doc-input {
  border: none;
  border-bottom: 1px solid #333;
  border-radius: 0;
  color: #000;
  background: transparent;
  font-size: 14px;
  outline: none;
  padding: 0 4px;
}

.doc-input--sm {
  width: 130px;
}

.doc-input--lg {
  flex: 1 1 auto;
  min-width: 220px;
}

.doc-input:focus {
  border-bottom-color: #1677ff;
}

/* 二级标题 */
.doc-page__section-title {
  margin: 28px 0 10px;
  font-size: 16px;
  font-weight: 700;
  color: #000;
}

/* 表格：细边框 + 单元格内输入 */
.doc-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  color: #000;
  table-layout: fixed;
}

.doc-table th,
.doc-table td {
  height: 34px;
  padding: 0 4px;
  border: 1px solid #999;
}

.doc-table th {
  font-weight: 600;
  text-align: center;
  background: #f5f7fa;
}

.doc-cell-input {
  width: 100%;
  border: none;
  color: #000;
  background: transparent;
  font-size: 13px;
  text-align: center;
  outline: none;
}

/* ========== 打印适配：隐藏灰底，白纸铺满 A4；下划线与填写内容原样打印 ========== */
@media print {
  .doc-preview {
    display: block;
    padding: 0;
    background: #fff;
  }

  .doc-page {
    max-width: none;
    min-height: auto;
    padding: 0;
    box-shadow: none;
  }

  .doc-input,
  .doc-cell-input {
    border-bottom: 1px solid #333;
  }
}
</style>
