<script lang="ts" setup>
/**
 * MES 助手 - Word 文档预览弹窗
 *
 * 【新增模块 2】弹窗预览组件
 * 1. el-dialog 承载，标题为单据名称；右上角自带关闭 + 底部「关闭」「下载 Word 文件」按钮
 * 2. 预览用 docx-preview 的 renderAsync：把 docx Blob 渲染成 HTML 塞进容器
 * 3. 下载用 file-saver 保存同一个 Blob，避免重复生成
 * 4. 弹窗关闭时清空容器，释放内存
 */
import { nextTick, onBeforeUnmount, ref, watch } from 'vue';

// Element Plus 样式（按需组件 + 全量基础样式，保证 el-dialog / el-button 正常渲染）
import 'element-plus/dist/index.css';
import { ElButton, ElDialog, ElMessage } from 'element-plus';
import { renderAsync } from 'docx-preview';
import { saveAs } from 'file-saver';

import {
  createDocxBlob,
  MES_DOC_META,
  type MesDocType,
} from './mesDocTemplates';

const props = defineProps<{
  /** 是否显示（v-model:visible） */
  visible: boolean;
  /** 单据类型，null 时不渲染 */
  docType: MesDocType | null;
}>();

const emit = defineEmits<{ 'update:visible': [boolean] }>();

/** docx 预览容器 */
const containerRef = ref<HTMLDivElement>();
/** 生成中标记 */
const loading = ref(false);
/** 当前文档的 Blob（预览与下载共用） */
const docxBlob = ref<Blob>();

/** 生成 Blob 并渲染预览 */
async function renderDoc() {
  if (!props.docType) return;
  loading.value = true;
  try {
    docxBlob.value = await createDocxBlob(props.docType);
    await nextTick();
    if (containerRef.value) {
      containerRef.value.innerHTML = ''; // 切换单据时先清空
      await renderAsync(docxBlob.value, containerRef.value);
    }
  } catch (e) {
    ElMessage.error('文档生成失败，请稍后重试。');
  } finally {
    loading.value = false;
  }
}

/** 弹窗打开或单据类型变化时重新渲染 */
watch(
  () => [props.visible, props.docType],
  () => {
    if (props.visible && props.docType) renderDoc();
  },
);

/** 下载 Word 文件 */
function handleDownload() {
  if (!docxBlob.value || !props.docType) {
    ElMessage.warning('文档尚未生成完成，请稍候。');
    return;
  }
  saveAs(docxBlob.value, MES_DOC_META[props.docType].fileName);
}

/** 关闭：通知父组件并清空预览容器 */
function handleClose() {
  emit('update:visible', false);
  if (containerRef.value) containerRef.value.innerHTML = '';
}

onBeforeUnmount(() => {
  if (containerRef.value) containerRef.value.innerHTML = '';
});
</script>

<template>
  <ElDialog
    :model-value="visible"
    :title="docType ? `${MES_DOC_META[docType].label}预览` : '文档预览'"
    width="72%"
    top="6vh"
    :close-on-click-modal="false"
    append-to-body
    destroy-on-close
    @update:model-value="(v: boolean) => emit('update:visible', v)"
    @closed="handleClose"
  >
    <!-- 生成中提示 -->
    <div v-if="loading" class="mes-doc-dialog__loading">正在生成文档…</div>
    <!-- docx-preview 渲染容器 -->
    <div ref="containerRef" class="mes-doc-dialog__preview" />

    <template #footer>
      <ElButton @click="handleClose">关闭</ElButton>
      <ElButton type="primary" :disabled="loading" @click="handleDownload">
        下载 Word 文件
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
/* 预览区：固定高度内部滚动，浅灰底衬托白色"纸张" */
.mes-doc-dialog__preview {
  height: 60vh;
  padding: 12px;
  overflow: auto;
  background: #f5f5f5;
  border-radius: 4px;
}

.mes-doc-dialog__loading {
  padding: 24px 0;
  font-size: 13px;
  color: #909399;
  text-align: center;
}
</style>
