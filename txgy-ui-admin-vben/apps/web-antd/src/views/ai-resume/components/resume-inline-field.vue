<script lang="ts" setup>
import { nextTick, ref, watch } from 'vue';

/**
 * 中栏简历里「点一下就能改」的字段。
 *
 * <p><b>不用 contenteditable</b>：它和 Vue 的虚拟 DOM patch 会互相打架 ——
 * 光标位置会在重渲染时被冲掉，输入快一点就丢字；而且没法表达「空值占位」，
 * 空字段点开是一片空白，用户不知道这儿原本该填什么。</p>
 *
 * <p>改成「激活时换成原生 input/textarea」：值走 v-model 写回草稿，
 * 非激活时渲染成原本的标签（h1 / strong / p / span），所以文档版式一点没变。</p>
 *
 * <p><b>控件刻意零装饰</b>：字号、字体、行高、颜色全部 inherit，只多一条浅绿下划线。
 * 目的是让它在静息态与只读文档完全无法区分，编辑态也仍然「像文档」而不是「像表单」——
 * 否则中栏就变成第二个编辑面板了。</p>
 */

const props = withDefaults(
  defineProps<{
    /** 当前正在编辑的字段路径。由文档统一持有，保证同一时刻只有一个输入框 */
    active?: string;
    /** 交互开关：列表页的只读预览不传，此时点击无反应 */
    editable?: boolean;
    multiline?: boolean;
    /** 本字段的全局唯一路径，与 active 比对 */
    path?: string;
    /** 空值时显示的灰字，沿用原有空态文案 */
    placeholder?: string;
    /** 非编辑态用什么标签渲染，保持原有字号与字重 */
    tag?: string;
    modelValue?: string;
  }>(),
  {
    active: '',
    editable: false,
    multiline: false,
    path: '',
    placeholder: '',
    tag: 'span',
  },
);

const emit = defineEmits<{
  activate: [path: string];
  leave: [];
  'update:modelValue': [value: string];
}>();

const inputEl = ref<HTMLInputElement | HTMLTextAreaElement>();

/** 编辑期的草稿值：Esc 要能还原，所以不能直接改 modelValue */
const draft = ref('');

const isEditing = ref(false);

watch(
  () => props.active,
  (active) => {
    const shouldEdit = Boolean(props.path) && active === props.path;
    if (shouldEdit === isEditing.value) return;
    isEditing.value = shouldEdit;
    if (shouldEdit) {
      draft.value = props.modelValue || '';
      // 等它真的进了 DOM 再聚焦，否则 ref 还是空的
      void nextTick(() => {
        inputEl.value?.focus();
        inputEl.value?.select();
      });
    }
  },
  /*
   * immediate 不能省。
   * 多数字段是「先点、再把 active 改成自己」，靠变化触发；
   * 但技能是整块编辑 —— 组件在 activeField 已经是 'skills' 之后才被 v-if 挂载出来，
   * 此时 active 没有「变化」可等，不加 immediate 就永远停在只读态（点了没反应）。
   */
  { immediate: true },
);

function activate() {
  if (!props.editable) return;
  emit('activate', props.path);
}

function commit() {
  if (!isEditing.value) return;
  if (draft.value !== (props.modelValue || '')) {
    emit('update:modelValue', draft.value);
  }
  isEditing.value = false;
  emit('leave');
}

function cancel() {
  isEditing.value = false;
  emit('leave');
}

/**
 * 单行回车即提交；多行留给回车换行。
 * Esc 一律还原 —— 与「输入框里 Esc 取消」的通行直觉一致。
 */
function onEnter(event: KeyboardEvent) {
  if (props.multiline) return;
  event.preventDefault();
  commit();
}
</script>

<template>
  <textarea
    v-if="isEditing && multiline"
    ref="inputEl"
    v-model="draft"
    class="inline-input is-multiline"
    rows="3"
    @blur="commit"
    @keydown.esc="cancel"
  ></textarea>
  <input
    v-else-if="isEditing"
    ref="inputEl"
    v-model="draft"
    class="inline-input"
    type="text"
    @blur="commit"
    @keydown.enter="onEnter"
    @keydown.esc="cancel"
  />
  <component
    :is="tag"
    v-else
    class="inline-view"
    :class="{ 'is-editable': editable, 'is-empty': !modelValue }"
    @click="activate"
  >
    {{ modelValue || placeholder }}
  </component>
</template>

<style scoped>
/*
 * 静息态与只读文档完全一致；只有悬停时才透出「这儿能改」的线索（一条浅绿下划线）。
 * 不做常驻的输入框描边 —— 满屏虚线框会让预览变成表单。
 */
.inline-view.is-editable {
  cursor: text;
  border-bottom: 1px dashed transparent;
  transition:
    background 0.15s ease-in-out,
    border-color 0.15s ease-in-out;
}

.inline-view.is-editable:hover {
  border-bottom-color: #f0c48f;
  background: #fffaf4;
}

/* 空值灰字沿用文档原有的空态观感 */
.inline-view.is-empty {
  color: #b9b9b9;
}

.inline-input {
  display: block;
  box-sizing: border-box;
  width: 100%;
  padding: 0;
  border: 0;
  border-bottom: 1px solid #e0913f;
  background: #fffaf4;
  color: inherit;
  font: inherit;
  line-height: inherit;
  outline: none;
  text-align: inherit;
}

.inline-input.is-multiline {
  resize: vertical;
}
</style>
