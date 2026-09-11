<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import WangEditor from 'wangeditor';

const props = withDefaults(
  defineProps<{
    height?: number;
    modelValue: string;
  }>(),
  { height: 360 },
);
const emit = defineEmits<{ 'update:modelValue': [value: string] }>();

const toolbarElement = ref<HTMLElement>();
const textElement = ref<HTMLElement>();
let editor: null | WangEditor = null;
let synchronizing = false;

function synchronizeValue(value: string) {
  if (!editor || editor.txt.html() === value) return;
  synchronizing = true;
  editor.txt.html(value || '<p><br></p>');
  synchronizing = false;
}

onMounted(async () => {
  await nextTick();
  if (!toolbarElement.value || !textElement.value) return;
  editor = new WangEditor(toolbarElement.value, textElement.value);
  editor.config.height = props.height;
  editor.config.zIndex = 100;
  editor.config.placeholder = '粘贴或编辑完整原文，保留条号、标题、列表与段落。';
  editor.config.pasteFilterStyle = false;
  editor.config.pasteIgnoreImg = true;
  editor.config.menus = [
    'head',
    'bold',
    'fontSize',
    'fontName',
    'italic',
    'underline',
    'strikeThrough',
    'foreColor',
    'backColor',
    'list',
    'justify',
    'quote',
    'table',
    'undo',
    'redo',
  ];
  editor.config.onchange = (html: string) => {
    if (!synchronizing) emit('update:modelValue', html);
  };
  editor.create();
  synchronizeValue(props.modelValue);
});

watch(
  () => props.modelValue,
  (value) => synchronizeValue(value),
);

onBeforeUnmount(() => {
  editor?.destroy();
  editor = null;
});
</script>

<template>
  <div class="cms-wang-editor">
    <div ref="toolbarElement" class="editor-toolbar"></div>
    <div ref="textElement" class="editor-text"></div>
  </div>
</template>

<style scoped>
.cms-wang-editor {
  overflow: hidden;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  background: #fff;
}
.editor-toolbar {
  border-bottom: 1px solid #e5e7eb;
}
.cms-wang-editor :deep(.w-e-toolbar),
.cms-wang-editor :deep(.w-e-text-container) {
  border: 0 !important;
}
.cms-wang-editor :deep(.w-e-text) {
  color: #1f2937;
  line-height: 1.75;
}
</style>
