<script lang="ts" setup>
import { IconifyIcon } from '@vben/icons';

import TemplateThumb from './template-thumb.vue';
import { RESUME_TEMPLATES } from './resume-templates';

/**
 * 简历模板选择面板（左栏「简历模板」页）。
 *
 * 版式对齐参考站：3 列网格，缩略图外框 2px 透明描边、悬停与选中都变成
 * rgba(120,179,62,.6)；名字 14px，默认 #526045，悬停/选中转深；底部固定
 * 「暂时跳过 / 确认选择」。
 */

defineProps<{
  /** 当前选中的模板 key；直接反映在中栏预览上，所以选中即生效 */
  selected: string;
}>();

const emit = defineEmits<{
  /** 确认用这套模板继续（切到简历编辑页） */
  confirm: [];
  select: [key: string];
  /** 不挑模板，用默认的继续 */
  skip: [];
}>();
</script>

<template>
  <div class="tpl-panel">
    <div class="tpl-scroll">
      <div class="tpl-grid">
        <button
          v-for="item in RESUME_TEMPLATES"
          :key="item.key"
          type="button"
          class="tpl-card"
          :class="{ 'is-active': item.key === selected }"
          :aria-pressed="item.key === selected"
          @click="emit('select', item.key)"
        >
          <span class="thumb-box">
            <TemplateThumb :template="item" />
          </span>
          <span class="tpl-name">{{ item.name }}</span>
        </button>
      </div>
    </div>

    <footer class="tpl-foot">
      <button type="button" class="btn-ghost" @click="emit('skip')">暂时跳过</button>
      <button type="button" class="btn-solid" @click="emit('confirm')">
        <IconifyIcon icon="lucide:check" /> 确认选择
      </button>
    </footer>
  </div>
</template>

<style scoped>
.tpl-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.tpl-scroll {
  flex: 1;
  min-height: 0;
  padding: 12px 12px 4px;
  overflow-y: auto;
}

.tpl-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px 8px;
}

.tpl-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.thumb-box {
  display: block;
  width: 100%;
  border: 2px solid transparent;
  border-radius: 9px;
  background: #f5f5f4;
  overflow: hidden;
  transition: border-color 0.2s ease-in-out;
}

.tpl-card:hover .thumb-box,
.tpl-card.is-active .thumb-box {
  border-color: rgb(120 179 62 / 60%);
}

.tpl-name {
  color: #526045;
  font-size: 14px;
  line-height: 1.4;
  transition: color 0.2s ease-in-out;
}

.tpl-card:hover .tpl-name,
.tpl-card.is-active .tpl-name {
  color: #1c2c1b;
}

.tpl-foot {
  display: flex;
  flex-shrink: 0;
  justify-content: center;
  gap: 12px;
  padding: 12px 14px 14px;
  border-top: 1px solid #ececec;
  background: #fff;
}

.btn-ghost,
.btn-solid {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  min-width: 104px;
  height: 38px;
  padding: 0 20px;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: 0.2s ease-in-out;
}

.btn-ghost {
  border: 2px solid #f5f5f5;
  background: #fff;
  color: #000;
}

.btn-ghost:hover {
  background: #f6f6f6;
}

.btn-solid {
  border: 0;
  background: #141414;
  color: #fff;
}

.btn-solid:hover {
  background: #2e2e2e;
}

@media (max-width: 420px) {
  .tpl-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
