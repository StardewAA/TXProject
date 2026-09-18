<script setup lang="ts">
import { computed } from 'vue';

import { IconifyIcon } from '@vben/icons';

/* 编号类字段的"一键复制"单元（招投标域共用）
 *
 * 为什么需要它：证书编号 / 注册号 / 统一社会信用代码 / file_id / 项目编号 这类字段，
 * 用户的下一步动作几乎都是"拿去别处核验或对账"，而此前只能靠肉眼抄。
 *
 * 口径：
 *  ① 复制失败必须**说出来**——navigator.clipboard 仅在安全上下文（https / localhost）可用，
 *     本系统存在 http 访问场景，故退回到 execCommand("copy")；两条路都失败时提示"请手动选中复制"，
 *     绝不静默（点击没反应是比复制失败更差的体验）。
 *  ② 空值渲染为 "—" 且不显示复制按钮（避免复制到一个破折号）。
 *  ③ 该组件只负责展示与复制，不参与取值/字典翻译——值域仍由页面决定。
 */
import { message } from 'ant-design-vue';

const props = withDefaults(
  defineProps<{
    /** 要复制的文本（雪花 Long 可能是 number 或 string） */
    label?: string;
    text?: null | number | string;
  }>(),
  { label: '内容', text: undefined },
);

const display = computed(() => {
  const v = props.text;
  return v === null || v === undefined || v === '' ? '—' : String(v);
});
const empty = computed(() => display.value === '—');

async function copy() {
  if (empty.value) return;
  const value = display.value;
  let ok = false;
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(value);
      ok = true;
    }
  } catch {
    ok = false; // 非安全上下文或用户拒绝授权 → 走下面的退路
  }
  if (!ok) {
    try {
      const ta = document.createElement('textarea');
      ta.value = value;
      ta.setAttribute('readonly', 'readonly');
      ta.style.position = 'fixed';
      ta.style.top = '-1000px';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.select();
      ok = document.execCommand('copy');
      document.body.removeChild(ta);
    } catch {
      ok = false;
    }
  }
  if (ok) message.success(`${props.label}已复制`);
  else message.warning('复制失败：请手动选中文本复制');
}
</script>

<template>
  <span class="copy-text">
    <span class="copy-text__val" :title="display">{{ display }}</span>
    <button
      v-if="!empty"
      :aria-label="`复制${label}`"
      class="copy-text__btn"
      type="button"
      @click.stop="copy"
    >
      <IconifyIcon class="copy-text__icon" icon="lucide:copy" />
    </button>
  </span>
</template>

<style scoped>
.copy-text {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
}

.copy-text__val {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.copy-text__btn {
  flex: none;
  padding: 0 2px;
  font-size: 12px;
  line-height: 1;
  color: rgb(0 0 0 / 45%);
  cursor: pointer;
  background: none;
  border: 0;
}

.copy-text__icon {
  font-size: 13px;
}

.copy-text__btn:hover {
  color: var(--ant-color-primary, #1677ff);
}
</style>
